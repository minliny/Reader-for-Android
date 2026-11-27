package io.reader.rebuild.features.reading

import com.reader.core.protocol.M3HttpTtsBuildRequestParams
import com.reader.core.protocol.M3TtsChapterRef
import com.reader.core.protocol.M3TtsFailurePolicy
import com.reader.core.protocol.M3TtsQueueNextParams
import com.reader.core.protocol.M3TtsQueuePauseParams
import com.reader.core.protocol.M3TtsQueuePlayParams
import com.reader.core.protocol.M3TtsQueuePrevParams
import com.reader.core.protocol.M3TtsQueueReportCallbackParams
import com.reader.core.protocol.M3TtsQueueResumeParams
import com.reader.core.protocol.M3TtsQueueSetRateParams
import com.reader.core.protocol.M3TtsQueueSnapshot
import com.reader.core.protocol.M3TtsQueueState
import com.reader.core.protocol.M3TtsQueueStopParams
import com.reader.core.protocol.M3TtsSlice
import com.reader.core.protocol.M3TtsSliceParams
import com.reader.core.protocol.M3TtsSlicePlan
import com.reader.core.protocol.M3TtsSliceStatus
import com.reader.core.protocol.M3TtsSlicingStrategy
import com.reader.core.protocol.ReaderM3
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.host.media.SpeechAction
import io.reader.rebuild.host.media.SpeechAndMediaHost
import io.reader.rebuild.host.media.SpeechPhysicalEvent
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface ReaderSpeechEngine {
    data class System(val locale: Locale? = null) : ReaderSpeechEngine
    data class Http(val id: Long) : ReaderSpeechEngine
}

internal data class SpeechChapter(
    val identity: M3TtsChapterRef,
    val content: String,
    val bodyVersion: String,
    val processingVersion: String,
)

internal data class SpeechHighlight(
    val identity: M3TtsChapterRef,
    val bodyVersion: String,
    val processingVersion: String,
    val start: Long,
    val endExclusive: Long,
)

/** The chapter owner holds the Core queue and its physical session independently of display panels. */
internal class SpeechSessionCoordinator(private val core: CoreRuntimeActor, private val physical: SpeechAndMediaHost) {
    private data class Session(
        val owner: UUID,
        val chapter: SpeechChapter,
        val plan: M3TtsSlicePlan,
        val engine: ReaderSpeechEngine,
        var ratePercent: Long,
    )

    private data class Slice(val owner: UUID, val token: UUID, val core: M3TtsSlice, val coordinates: TextScalarMap)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Mutex()
    private val current = MutableStateFlow<M3TtsQueueSnapshot?>(null)
    private val word = MutableStateFlow<SpeechHighlight?>(null)
    private val errors = MutableStateFlow<Throwable?>(null)
    private var desired: UUID? = null
    private var session: Session? = null
    private var slice: Slice? = null
    private var playback: Job? = null
    private var closed = false

    val snapshot = current.asStateFlow()
    val highlight = word.asStateFlow()
    val failure = errors.asStateFlow()

    suspend fun start(chapter: SpeechChapter, title: String, scalar: Long,
                      engine: ReaderSpeechEngine = ReaderSpeechEngine.System(), ratePercent: Long = 100) =
        withContext(Dispatchers.Main.immediate) {
            check(!closed)
            val coordinates = TextScalarMap(chapter.content)
            require(chapter.bodyVersion.isNotBlank() && chapter.processingVersion.isNotBlank() &&
                coordinates.hasCharacter(scalar) && ratePercent in 50..200)
            val owner = UUID.randomUUID()
            desired = owner
            abandonPhysical()
            commands.withLock {
                stopCore()
                checkDesired(owner)
                errors.value = null
                val plan = core.call(ReaderM3.ttsSlice,
                    M3TtsSliceParams(chapter.identity, chapter.content, M3TtsSlicingStrategy.SENTENCE)).plan
                checkDesired(owner)
                verifyPlan(chapter, coordinates, plan)
                val index = plan.slices.first { scalar < it.charEnd || it == plan.slices.last() }.index
                val result = core.call(ReaderM3.ttsQueuePlay, M3TtsQueuePlayParams(plan, index)).snapshot
                session = Session(owner, chapter, plan, engine, ratePercent)
                try {
                    checkDesired(owner)
                    current.value = result
                    physical.begin(owner, title, plan.chapter.chapterTitle, ratePercent) { action ->
                        scope.launch { guarded(owner) { control(owner, action) } }
                    }
                    checkDesired(owner)
                    if (ratePercent != 100L) {
                        current.value = core.call(ReaderM3.ttsQueueSetRate, M3TtsQueueSetRateParams(plan.chapter, ratePercent)).snapshot
                        checkDesired(owner)
                    }
                    playCurrent()
                } catch (error: Throwable) {
                    withContext(NonCancellable) {
                        if (desired == owner) desired = null
                        abandonPhysical()
                        runCatching { stopCore() }.exceptionOrNull()?.let(error::addSuppressed)
                    }
                    throw error
                }
            }
        }

    /** Reflow is a projection read. Chapter and both versions still have to match. */
    fun currentHighlight(identity: M3TtsChapterRef, bodyVersion: String, processingVersion: String): SpeechHighlight? =
        word.value?.takeIf { sameChapter(it.identity, identity) && it.bodyVersion == bodyVersion &&
            it.processingVersion == processingVersion }

    suspend fun send(action: SpeechAction) = withContext(Dispatchers.Main.immediate) {
        val owner = desired ?: return@withContext
        control(owner, action)
    }

    suspend fun retire() = withContext(Dispatchers.Main.immediate + NonCancellable) {
        desired = null
        abandonPhysical()
        commands.withLock { stopCore() }
    }

    suspend fun close() = withContext(Dispatchers.Main.immediate + NonCancellable) {
        closed = true
        try { retire() } finally { scope.cancel() }
    }

    private suspend fun control(owner: UUID, action: SpeechAction) {
        if (desired != owner) return
        if (action == SpeechAction.Stop) {
            retire()
            return
        }
        commands.withLock {
            val active = session?.takeIf { it.owner == owner && desired == owner } ?: return@withLock
            val chapter = active.plan.chapter
            when (action) {
                SpeechAction.Pause -> if (current.value?.state == M3TtsQueueState.PLAYING) {
                    playback?.cancelAndJoin()
                    physical.pause(owner)
                    val reply = core.call(ReaderM3.ttsQueuePause, M3TtsQueuePauseParams(chapter)).snapshot
                    if (desired == owner) current.value = reply
                }
                SpeechAction.Play -> if (current.value?.state == M3TtsQueueState.PAUSED) {
                    val reply = core.call(ReaderM3.ttsQueueResume, M3TtsQueueResumeParams(chapter)).snapshot
                    checkDesired(owner)
                    current.value = reply
                    if (!physical.resume(owner)) playCurrent()
                }
                SpeechAction.Next -> replace(owner, core.call(ReaderM3.ttsQueueNext, M3TtsQueueNextParams(chapter)).snapshot)
                SpeechAction.Previous -> replace(owner, core.call(ReaderM3.ttsQueuePrev, M3TtsQueuePrevParams(chapter)).snapshot)
                is SpeechAction.Rate -> {
                    require(action.percent in 50..200)
                    val reply = core.call(ReaderM3.ttsQueueSetRate, M3TtsQueueSetRateParams(chapter, action.percent))
                    checkDesired(owner)
                    active.ratePercent = reply.rate
                    current.value = reply.snapshot
                    physical.setRate(owner, reply.rate)
                }
                SpeechAction.Stop -> Unit
            }
        }
    }

    private suspend fun replace(owner: UUID, reply: M3TtsQueueSnapshot) {
        checkDesired(owner)
        current.value = reply
        playCurrent()
    }

    private suspend fun playCurrent() {
        playback?.cancelAndJoin()
        playback = null
        val active = session ?: return
        checkDesired(active.owner)
        physical.stopSlice(active.owner)
        slice = null
        word.value = null
        val state = current.value ?: return
        val selected = active.plan.slices.firstOrNull { it.index == state.currentSliceIndex }
        if (state.state != M3TtsQueueState.PLAYING || selected == null) {
            if (state.state in setOf(M3TtsQueueState.COMPLETED, M3TtsQueueState.STOPPED)) physical.retire(active.owner)
            return
        }
        physical.showQueue(active.owner, selected.index, state.totalSlices)
        val run = Slice(active.owner, UUID.randomUUID(), selected, TextScalarMap(selected.text, selected.charStart))
        slice = run
        playback = scope.launch {
            try {
                when (val engine = active.engine) {
                    is ReaderSpeechEngine.System ->
                        physical.systemSlice(active.owner, run.token, selected.text, engine.locale, ::receive)
                    is ReaderSpeechEngine.Http -> {
                        val plan = core.call(ReaderM3.httpTtsBuildRequest,
                            M3HttpTtsBuildRequestParams(engine.id, selected.text, active.ratePercent))
                        checkDesired(active.owner)
                        physical.httpSlice(active.owner, run.token, plan, ::receive)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                if (desired == active.owner && slice == run) {
                    errors.value = failure
                    receive(run.token, SpeechPhysicalEvent.Failed)
                }
            }
        }
    }

    private fun receive(token: UUID, event: SpeechPhysicalEvent) {
        val active = session ?: return
        val run = slice?.takeIf { it.token == token && desired == it.owner } ?: return
        if (current.value?.state != M3TtsQueueState.PLAYING) return
        val chapter = active.chapter
        when (event) {
            is SpeechPhysicalEvent.Range -> {
                val start = run.coordinates.scalar(event.start) ?: return
                val end = run.coordinates.scalar(event.end) ?: return
                if (start < end) word.value = SpeechHighlight(chapter.identity, chapter.bodyVersion, chapter.processingVersion, start, end)
            }
            SpeechPhysicalEvent.Started -> if (active.engine is ReaderSpeechEngine.Http) {
                word.value = SpeechHighlight(chapter.identity, chapter.bodyVersion, chapter.processingVersion,
                    run.core.charStart, run.core.charEnd)
            }
            SpeechPhysicalEvent.Finished, SpeechPhysicalEvent.Failed -> scope.launch {
                guarded(active.owner) {
                    commands.withLock {
                        if (desired != active.owner || slice?.token != token || current.value?.state != M3TtsQueueState.PLAYING) return@withLock
                        val status = if (event == SpeechPhysicalEvent.Finished) M3TtsSliceStatus.DONE else M3TtsSliceStatus.FAILED
                        if (status == M3TtsSliceStatus.FAILED && errors.value == null) {
                            errors.value = IllegalStateException("Speech playback failed")
                        }
                        val response = core.call(ReaderM3.ttsQueueReportCallback, M3TtsQueueReportCallbackParams(
                            chapter = active.plan.chapter, sliceIndex = run.core.index, status = status,
                            callbackId = token.toString(), failurePolicy = M3TtsFailurePolicy.STOP))
                        replace(active.owner, response.snapshot)
                    }
                }
            }
        }
    }

    private suspend fun guarded(owner: UUID, action: suspend () -> Unit) {
        try { action() } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            if (desired == owner) {
                errors.value = failure
                runCatching { retire() }.exceptionOrNull()?.let(failure::addSuppressed)
            }
        }
    }

    private suspend fun abandonPhysical() {
        val old = session
        slice = null
        word.value = null
        playback?.cancelAndJoin()
        playback = null
        if (old != null) physical.retire(old.owner)
    }

    private suspend fun stopCore() {
        val stopping = session ?: return
        core.call(ReaderM3.ttsQueueStop, M3TtsQueueStopParams(stopping.plan.chapter))
        session = null
        current.value = null
    }

    private fun verifyPlan(chapter: SpeechChapter, coordinates: TextScalarMap, plan: M3TtsSlicePlan) {
        check(sameChapter(plan.chapter, chapter.identity) && plan.sourceCharCount == coordinates.chapterEnd &&
            plan.slices.isNotEmpty())
        var previous = 0L
        for ((index, item) in plan.slices.withIndex()) {
            check(item.index == index.toLong() && item.charStart >= previous && item.charStart < item.charEnd &&
                coordinates.substring(item.charStart, item.charEnd) == item.text) { "Unproven speech coordinates" }
            previous = item.charEnd
        }
    }

    private fun sameChapter(left: M3TtsChapterRef, right: M3TtsChapterRef): Boolean =
        left.sourceId == right.sourceId && left.bookId == right.bookId && left.chapterIndex == right.chapterIndex

    private fun checkDesired(owner: UUID) {
        if (desired != owner) throw CancellationException("Speech owner retired")
    }
}
