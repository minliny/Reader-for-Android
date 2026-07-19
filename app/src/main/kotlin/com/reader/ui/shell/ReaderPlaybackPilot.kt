package com.reader.ui.shell

import com.reader.android.AppProvider
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.repository.ConfirmedReadingProgress
import com.reader.api.Book
import com.reader.api.Chapter
import com.reader.api.ReaderCoreClient
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIPageLayout
import io.reader.ui.runtime.ReaderUIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

internal const val READER_AUTO_PAGE_DEFAULT_INTERVAL_MS: Int = 5_000

internal val READER_UI_RUNTIME_PLAYBACK_PILOT_EVENTS: Set<String> = linkedSetOf(
    "reader.page.next",
    "reader.page.prev",
    "reader.tts.start",
    "reader.tts.stop",
    "reader.autoPage.start",
    "reader.autoPage.stop"
)

internal sealed interface ReaderPlaybackPilotDispatch {
    data object NotEnabled : ReaderPlaybackPilotDispatch
    data class Applied(
        val event: String,
        val state: ReaderUIState,
        val effects: List<ReaderUIEffect>,
        val cancelledCorrelationIds: List<String>
    ) : ReaderPlaybackPilotDispatch
    data object FailedClosed : ReaderPlaybackPilotDispatch
}

/** Snapshot of Core-owned reader data captured at paired-Pilot admission. */
internal data class ReaderPlaybackReaderSnapshot(
    val sourceId: String,
    val bookId: String,
    val book: Book,
    val chapter: Chapter,
    val content: String,
    val contentGeneration: Long,
    val canonicalLocation: ReaderBookOpenCanonicalLocation,
    val selectedChapterPosition: Int,
    val chapters: List<Chapter>
) {
    companion object {
        fun from(state: ReaderBookOpenDomainState): ReaderPlaybackReaderSnapshot? {
            val book = state.book ?: return null
            val chapter = state.chapters.getOrNull(state.selectedChapterPosition) ?: return null
            val location = state.canonicalLocation ?: return null
            if (!state.contentLoaded || !state.locationResolved) return null
            if (location.bookId != state.bookId || location.chapterIndex != chapter.index) return null
            return ReaderPlaybackReaderSnapshot(
                sourceId = state.sourceId,
                bookId = state.bookId,
                book = book,
                chapter = chapter,
                content = state.content,
                contentGeneration = state.contentGeneration,
                canonicalLocation = location,
                selectedChapterPosition = state.selectedChapterPosition,
                chapters = state.chapters.toList()
            )
        }
    }
}

/** Real Compose text/layout proposal; no character-budget pagination is accepted. */
internal data class ReaderPlaybackPageMeasurement(
    val correlationId: String,
    val contentGeneration: Long,
    val contentLength: Int,
    val direction: String,
    val anchor: String,
    val targetPageIndex: Int,
    val chapterIndex: Int,
    val chapterOffset: Int,
    val chapterProgress: Double,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val fontScale: Double
) {
    fun toRuntimeLayout(): ReaderUIPageLayout = ReaderUIPageLayout(
        anchor = anchor,
        targetPageIndex = targetPageIndex,
        chapterIndex = chapterIndex,
        chapterOffset = chapterOffset,
        chapterProgress = chapterProgress,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        fontScale = fontScale
    )
}

internal data class ReaderPlaybackTtsSlice(
    val index: Int,
    val text: String,
    val charStart: Int,
    val charEnd: Int,
    val paragraphIndex: Int
)

internal data class ReaderPlaybackTtsPlan(
    val chapterSourceId: String,
    val chapterBookId: String,
    val chapterIndex: Int,
    val strategy: String,
    val slices: List<ReaderPlaybackTtsSlice>,
    val sourceCharCount: Int,
    val rawPlan: JSONObject
)

internal data class ReaderPlaybackTtsSnapshot(
    val state: String,
    val currentSliceIndex: Int?,
    val totalSlices: Int,
    val completedSlices: Int,
    val chapterSourceId: String,
    val chapterBookId: String,
    val chapterIndex: Int
)

/**
 * Canonical location waiting for Core's durable `reading.progress.update`
 * acknowledgement.  It is correlation-scoped and deliberately separate from
 * [ReaderPlaybackDomainState.committedLocation]: location resolution alone
 * must never move the visible page.
 */
internal data class ReaderPlaybackPendingProgress(
    val correlationId: String,
    val sourceId: String,
    val bookId: String,
    val pageIndex: Int,
    val chapterPosition: Int,
    val totalChapters: Int,
    val bookName: String,
    val author: String?,
    val chapterTitle: String,
    val chapterUrl: String,
    val location: ReaderBookOpenCanonicalLocation,
    val updatedAt: Long
)

internal data class ReaderPlaybackDomainState(
    val reader: ReaderPlaybackReaderSnapshot? = null,
    val pageCorrelationId: String? = null,
    val pageDirection: String? = null,
    val pageSource: String? = null,
    val pageGeneration: Int? = null,
    val committedPageIndex: Int = 0,
    val committedLocation: ReaderBookOpenCanonicalLocation? = null,
    val pendingProgress: ReaderPlaybackPendingProgress? = null,
    val ttsCorrelationId: String? = null,
    val ttsPlan: ReaderPlaybackTtsPlan? = null,
    val ttsSnapshot: ReaderPlaybackTtsSnapshot? = null,
    val activeUtteranceId: String? = null,
    val autoPageCorrelationId: String? = null,
    val autoPageGeneration: Int? = null,
    val timerArmed: Boolean = false,
    val activeSession: String? = null,
    val stage: String? = null,
    val error: String? = null,
    val coreCommandCount: Long = 0,
    val speechStartCount: Long = 0,
    val timerArmCount: Long = 0,
    /** Deterministic executor evidence; entries are start:/terminal:effect. */
    val effectTrace: List<String> = emptyList()
)

internal class ReaderPlaybackDomainStore {
    private val mutable = MutableStateFlow(ReaderPlaybackDomainState())
    val state: StateFlow<ReaderPlaybackDomainState> = mutable.asStateFlow()

    @Synchronized
    fun bindReader(snapshot: ReaderPlaybackReaderSnapshot) {
        val current = mutable.value
        mutable.value = current.copy(
            reader = snapshot,
            committedLocation = current.committedLocation ?: snapshot.canonicalLocation,
            error = null
        )
    }

    @Synchronized
    fun syncRuntime(runtime: ReaderUIState) {
        val current = mutable.value
        val nextTtsCorrelationId = runtime.ttsTransaction?.correlationId
        mutable.value = current.copy(
            pageCorrelationId = runtime.pageTransaction?.correlationId,
            pageDirection = runtime.pageTransaction?.direction,
            pageSource = runtime.pageTransaction?.source,
            pageGeneration = runtime.pageTransaction?.generation,
            ttsCorrelationId = nextTtsCorrelationId,
            ttsPlan = if (current.ttsCorrelationId == nextTtsCorrelationId) current.ttsPlan else null,
            ttsSnapshot = if (current.ttsCorrelationId == nextTtsCorrelationId) current.ttsSnapshot else null,
            activeUtteranceId = if (current.ttsCorrelationId == nextTtsCorrelationId) {
                current.activeUtteranceId
            } else null,
            autoPageCorrelationId = runtime.autoPageTransaction?.correlationId,
            autoPageGeneration = runtime.autoPageTransaction?.generation,
            timerArmed = runtime.autoPageTransaction?.timerArmed == true,
            activeSession = runtime.activeSession,
            error = runtime.error ?: if (
                runtime.pageTransaction == null && runtime.ttsTransaction == null &&
                runtime.autoPageTransaction == null && runtime.activeSession == null
            ) null else current.error,
            stage = runtime.pageTransaction?.stage
                ?: runtime.ttsTransaction?.stage
                ?: runtime.autoPageTransaction?.let { "auto-page" }
        )
    }

    @Synchronized
    fun recordPlan(correlationId: String, plan: ReaderPlaybackTtsPlan): Boolean {
        if (mutable.value.ttsCorrelationId != correlationId) return false
        mutable.value = mutable.value.copy(ttsPlan = plan, stage = "tts.queue.plan")
        return true
    }

    @Synchronized
    fun recordTtsSnapshot(correlationId: String, snapshot: ReaderPlaybackTtsSnapshot): Boolean {
        if (mutable.value.ttsCorrelationId != correlationId) return false
        mutable.value = mutable.value.copy(ttsSnapshot = snapshot)
        return true
    }

    @Synchronized
    fun recordUtterance(correlationId: String, utteranceId: String?): Boolean {
        if (mutable.value.ttsCorrelationId != correlationId) return false
        mutable.value = mutable.value.copy(activeUtteranceId = utteranceId)
        return true
    }

    @Synchronized
    fun commitPage(
        correlationId: String,
        confirmed: ReaderPlaybackPendingProgress
    ): Boolean {
        val current = mutable.value
        if (
            current.pageCorrelationId != correlationId ||
            current.pendingProgress != confirmed ||
            confirmed.correlationId != correlationId
        ) return false
        mutable.value = mutable.value.copy(
            committedPageIndex = confirmed.pageIndex,
            committedLocation = confirmed.location,
            pendingProgress = null,
            pageCorrelationId = null,
            pageDirection = null,
            pageSource = null,
            pageGeneration = null,
            error = null,
            stage = "reader.progress.persisted"
        )
        return true
    }

    @Synchronized
    fun recordPendingProgress(pending: ReaderPlaybackPendingProgress): Boolean {
        if (mutable.value.pageCorrelationId != pending.correlationId) return false
        mutable.value = mutable.value.copy(
            pendingProgress = pending,
            stage = "persisting-progress"
        )
        return true
    }

    @Synchronized
    fun discardPendingProgress(correlationId: String): Boolean {
        val current = mutable.value
        if (current.pendingProgress?.correlationId != correlationId) return false
        mutable.value = current.copy(pendingProgress = null)
        return true
    }

    @Synchronized
    fun fail(correlationId: String?, message: String) {
        val current = mutable.value
        if (correlationId != null && correlationId !in listOf(
                current.pageCorrelationId,
                current.ttsCorrelationId,
                current.autoPageCorrelationId
            )) return
        mutable.value = current.copy(error = message, stage = "failed")
    }

    @Synchronized
    fun incrementCore() {
        mutable.value = mutable.value.copy(coreCommandCount = mutable.value.coreCommandCount + 1)
    }

    @Synchronized
    fun incrementSpeech() {
        mutable.value = mutable.value.copy(speechStartCount = mutable.value.speechStartCount + 1)
    }

    @Synchronized
    fun incrementTimer() {
        mutable.value = mutable.value.copy(timerArmCount = mutable.value.timerArmCount + 1)
    }

    @Synchronized
    fun recordEffect(marker: String) {
        mutable.value = mutable.value.copy(effectTrace = mutable.value.effectTrace + marker)
    }
}

internal data class ReaderPlaybackRuntimeAdvance(
    val accepted: Boolean,
    val state: ReaderUIState,
    val effects: List<ReaderUIEffect> = emptyList(),
    val cancelledCorrelationIds: List<String> = emptyList()
)

internal interface ReaderPlaybackRuntimeDriver {
    val playbackRuntimeState: ReaderUIState
    fun providePageLayout(correlationId: String, layout: ReaderUIPageLayout): ReaderPlaybackRuntimeAdvance
    fun acceptPageLocationResult(
        correlationId: String,
        canonicalLocation: String? = null,
        pageIndex: Int? = null,
        error: String? = null
    ): ReaderPlaybackRuntimeAdvance
    fun acceptPageProgressResult(
        correlationId: String,
        stored: Boolean? = null,
        error: String? = null
    ): ReaderPlaybackRuntimeAdvance
    fun acceptTTSCoreResult(coreType: String, correlationId: String, error: String? = null): ReaderPlaybackRuntimeAdvance
    fun acceptTTSSystemStart(correlationId: String, error: String? = null): ReaderPlaybackRuntimeAdvance
    fun acceptAutoPageTimerFired(correlationId: String, generation: Int): ReaderPlaybackRuntimeAdvance
    fun stopTTS(correlationId: String? = null): ReaderPlaybackRuntimeAdvance
    fun stopAutoPage(correlationId: String? = null): ReaderPlaybackRuntimeAdvance
    fun suspendAutoPageForBackground(correlationId: String? = null): ReaderPlaybackRuntimeAdvance
    fun cancelPageStep(correlationId: String): ReaderPlaybackRuntimeAdvance
}

internal interface ReaderPlaybackCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderCorePlaybackCommandClient {
    fun beginCommand(method: String, params: JSONObject): ReaderPlaybackCommandHandle
}

internal fun interface ReaderPlaybackProgressMirror {
    suspend fun mirror(progress: ReaderPlaybackPendingProgress)
}

private object ProductionReaderPlaybackProgressMirror : ReaderPlaybackProgressMirror {
    override suspend fun mirror(progress: ReaderPlaybackPendingProgress) {
        if (!AppProvider.isInitialized) return
        AppProvider.readingProgressRepository.mirrorConfirmedProgress(
            ConfirmedReadingProgress(
                sourceId = progress.sourceId,
                bookId = progress.bookId,
                bookName = progress.bookName,
                author = progress.author,
                chapterUrl = progress.chapterUrl,
                chapterTitle = progress.chapterTitle,
                chapterPosition = progress.chapterPosition,
                totalChapters = progress.totalChapters,
                pageIndex = progress.pageIndex,
                chapterOffset = progress.location.chapterOffset,
                chapterProgress = progress.location.chapterProgress,
                locationRevision = progress.location.locationRevision,
                updatedAt = progress.updatedAt
            )
        )
    }
}

internal class ProductionReaderCorePlaybackCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderCorePlaybackCommandClient {
    override fun beginCommand(method: String, params: JSONObject): ReaderPlaybackCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderPlaybackCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

internal interface ReaderPlaybackSpeechHandle {
    val utteranceId: String
    suspend fun awaitStarted()
    suspend fun awaitCompletion()
    fun cancel(): Boolean
}

internal interface ReaderPlaybackSpeechEngine {
    suspend fun init(): Result<Unit>
    fun begin(utterance: TtsUtterance): ReaderPlaybackSpeechHandle
    suspend fun stop()
}

internal class AndroidReaderPlaybackSpeechEngine : ReaderPlaybackSpeechEngine {
    override suspend fun init(): Result<Unit> {
        val result = AppProvider.ttsEngine.init()
        return if (result.success) Result.success(Unit) else {
            Result.failure(IllegalStateException(result.errorMessage ?: "TTS init failed"))
        }
    }

    override fun begin(utterance: TtsUtterance): ReaderPlaybackSpeechHandle {
        val handle = AppProvider.ttsEngine.beginUtterance(utterance)
        return object : ReaderPlaybackSpeechHandle {
            override val utteranceId: String get() = handle.utteranceId
            override suspend fun awaitStarted() = handle.awaitStarted()
            override suspend fun awaitCompletion() = handle.awaitCompletion()
            override fun cancel(): Boolean = handle.cancel()
        }
    }

    override suspend fun stop() = AppProvider.ttsEngine.stop()
}

/** Sole Core/Host executor for the default-off paired playback Pilot. */
internal open class ReaderPlaybackEffectExecutor(
    private val domainStore: ReaderPlaybackDomainStore,
    private val runtime: ReaderPlaybackRuntimeDriver,
    private val commandClientFactory: () -> ReaderCorePlaybackCommandClient = {
        ProductionReaderCorePlaybackCommandClient()
    },
    private val speechEngineFactory: () -> ReaderPlaybackSpeechEngine = {
        AndroidReaderPlaybackSpeechEngine()
    },
    private val progressMirror: ReaderPlaybackProgressMirror = ProductionReaderPlaybackProgressMirror,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) {
    private val issuedEffects = mutableSetOf<String>()
    private val handles = mutableMapOf<String, ReaderPlaybackCommandHandle>()
    private val timerJobs = mutableMapOf<String, Job>()
    private val speechHandles = mutableMapOf<String, ReaderPlaybackSpeechHandle>()
    private val ttsContexts = mutableMapOf<String, ReaderPlaybackReaderSnapshot>()
    private val speechGenerations = mutableMapOf<String, Long>()
    private val generationCounter = AtomicLong(0L)
    private val progressTimestamp = AtomicLong(0L)
    /** FIFO tail: every Runtime effect array is awaited in contract order. */
    private var serialTail: Job? = null
    private var projectionSink: ((ReaderUIState, ReaderPlaybackDomainState) -> Unit)? = null
    private val speechEngine: ReaderPlaybackSpeechEngine by lazy(speechEngineFactory)

    @Synchronized
    fun setProjectionSink(sink: (ReaderUIState, ReaderPlaybackDomainState) -> Unit) {
        projectionSink = sink
    }

    internal fun canAdmit(intent: ReaderUiIntent, bookOpen: ReaderBookOpenDomainState): Boolean = when (intent) {
        is ReaderUiIntent.TurnPageNext,
        is ReaderUiIntent.TurnPagePrev,
        is ReaderUiIntent.StartTtsSession,
        is ReaderUiIntent.StartAutoPageSession -> ReaderPlaybackReaderSnapshot.from(bookOpen) != null
        ReaderUiIntent.StopSession -> true
        else -> false
    }

    @Synchronized
    open fun applyTransition(
        event: String,
        state: ReaderUIState,
        effects: List<ReaderUIEffect>,
        cancelledCorrelationIds: List<String>,
        bookOpen: ReaderBookOpenDomainState,
        scope: CoroutineScope
    ) {
        // Invalidate request/speech callbacks immediately so a superseded
        // predecessor can leave await(); terminal Host/Core teardown remains
        // ordered inside the serial queue below.
        cancelledCorrelationIds.forEach(::invalidateCorrelation)
        val readerSnapshot = ReaderPlaybackReaderSnapshot.from(bookOpen)
        enqueueSerial(scope) {
            readerSnapshot?.let(domainStore::bindReader)
            domainStore.syncRuntime(state)
            val activeTts = state.ttsTransaction?.correlationId
            if (activeTts != null) {
                domainStore.state.value.reader?.let { ttsContexts[activeTts] = it }
            }
            for (effect in effects) processEffect(effect, scope)
            notifyProjection(state)
        }
    }

    @Synchronized
    open fun onPageLayoutReady(measurement: ReaderPlaybackPageMeasurement, scope: CoroutineScope) {
        enqueueSerial(scope) {
            val domain = domainStore.state.value
            val reader = domain.reader ?: return@enqueueSerial
            if (
                domain.pageCorrelationId != measurement.correlationId ||
                domain.pageDirection != measurement.direction ||
                reader.contentGeneration != measurement.contentGeneration ||
                reader.content.length != measurement.contentLength ||
                reader.chapter.index != measurement.chapterIndex ||
                measurement.viewportWidth <= 0 || measurement.viewportHeight <= 0 ||
                measurement.chapterOffset !in 0..reader.content.length ||
                measurement.chapterProgress !in 0.0..1.0 ||
                measurement.anchor.isBlank()
            ) return@enqueueSerial
            val advance = try {
                runtime.providePageLayout(measurement.correlationId, measurement.toRuntimeLayout())
            } catch (error: Exception) {
                domainStore.fail(measurement.correlationId, error.message ?: "INVALID_PAGE_LAYOUT")
                return@enqueueSerial
            }
            if (advance.accepted) processAdvance(advance, scope)
        }
    }

    @Synchronized
    fun onAppBackgrounded(scope: CoroutineScope) {
        val correlationId = domainStore.state.value.autoPageCorrelationId ?: return
        enqueueSerial(scope) {
            processAdvance(runtime.suspendAutoPageForBackground(correlationId), scope)
        }
    }

    @Synchronized
    fun teardownForReaderExit(scope: CoroutineScope) {
        // Read Runtime truth, not the asynchronously projected DomainStore:
        // exit/replacement may arrive immediately after admission, before the
        // serial executor has projected that transaction.
        val before = runtime.playbackRuntimeState
        val pageCorrelationId = before.pageTransaction?.correlationId
        // Core progress is a write-through commit boundary.  Reader UI rejects
        // cancellation in this stage, so Host must leave the command and its
        // correlation alive until the terminal callback arrives.
        if (before.pageTransaction?.stage == "persisting-progress") return
        val ttsCorrelationId = before.ttsTransaction?.correlationId
        val autoPageCorrelationId = before.autoPageTransaction?.correlationId
        listOfNotNull(pageCorrelationId, ttsCorrelationId, autoPageCorrelationId)
            .forEach(::invalidateCorrelation)
        // Clear the shared Runtime ledgers synchronously so a following
        // book.open dispatch cannot absorb playback teardown effects into its
        // single-Core-effect admission boundary. The resulting teardown
        // effects themselves remain FIFO/terminal in the serial executor.
        val advances = buildList {
            pageCorrelationId?.let { add(runtime.cancelPageStep(it)) }
            ttsCorrelationId?.let { add(runtime.stopTTS(it)) }
            autoPageCorrelationId?.let { add(runtime.stopAutoPage(it)) }
        }
        enqueueSerial(scope) {
            advances.forEach { processAdvance(it, scope) }
            timerJobs.values.forEach(Job::cancel)
            timerJobs.clear()
        }
    }

    @Synchronized
    open fun cancelAll(scope: CoroutineScope? = null) {
        val committingCorrelation = domainStore.state.value.pendingProgress?.correlationId
        handles.entries.toList().forEach { (key, handle) ->
            if (committingCorrelation == null || !key.startsWith("$committingCorrelation:reader.progress.update:")) {
                handle.cancel()
                handles.remove(key)
            }
        }
        speechHandles.values.forEach(ReaderPlaybackSpeechHandle::cancel)
        speechHandles.clear()
        timerJobs.values.forEach(Job::cancel)
        timerJobs.clear()
        speechGenerations.keys.toList().forEach(::invalidateCorrelation)
        if (committingCorrelation == null) {
            serialTail?.cancel()
            serialTail = null
        }
        scope?.launch { runCatching { speechEngine.stop() } }
    }

    internal suspend fun awaitSerialIdle() {
        while (true) {
            val tail = synchronized(this) { serialTail }
            tail?.join()
            if (synchronized(this) { serialTail === tail }) return
        }
    }

    @Synchronized
    fun afterSerialTeardown(scope: CoroutineScope, action: () -> Unit) {
        enqueueSerial(scope) { action() }
    }

    private suspend fun processAdvance(advance: ReaderPlaybackRuntimeAdvance, scope: CoroutineScope) {
        if (!advance.accepted) return
        val latestRuntime = runtime.playbackRuntimeState
        if (advance.state != latestRuntime) {
            // A lifecycle/stop transition may have been queued while an
            // irreversible Core progress write was awaiting its result. Do
            // not replay stale successor effects (notably auto-page rearm)
            // after that newer Runtime state has already won.
            domainStore.syncRuntime(latestRuntime)
            notifyProjection(latestRuntime)
            return
        }
        advance.cancelledCorrelationIds.forEach(::invalidateCorrelation)
        domainStore.syncRuntime(advance.state)
        for (effect in advance.effects) processEffect(effect, scope)
        notifyProjection(advance.state)
    }

    private suspend fun processEffect(effect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = effect.correlationId ?: return
        val key = "$correlationId:${effect.kind}:${effect.type}:${effect.payload["generation"].orEmpty()}"
        synchronized(this) {
            if (effect.type == "timer.foreground.arm") {
                // One-shot rearm intentionally repeats the same contract DTO
                // after a page commit. It is a duplicate only while that
                // timerId still has a live Job.
                val timerId = effect.payload["timerId"] ?: correlationId
                if (timerJobs.containsKey(timerId)) return
            } else if (!issuedEffects.add(key)) {
                return
            }
        }
        domainStore.recordEffect("start:${effect.type}:$correlationId")
        try {
            when (effect.kind) {
                ReaderUIEffectKind.CORE -> processCoreEffect(effect, scope)
                ReaderUIEffectKind.HOST -> when (effect.type) {
                    "tts.system.start" -> startSpeech(correlationId, scope)
                    "tts.system.stop" -> stopSpeech(correlationId)
                    "timer.foreground.arm" -> armTimer(effect, scope)
                    "timer.foreground.cancel" -> cancelTimer(effect)
                    else -> domainStore.fail(correlationId, "PLAYBACK_UNSUPPORTED_HOST_EFFECT:${effect.type}")
                }
            }
        } finally {
            domainStore.recordEffect("terminal:${effect.type}:$correlationId")
        }
    }

    private suspend fun processCoreEffect(effect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = requireNotNull(effect.correlationId)
        val command = try {
            buildCoreCommand(effect)
        } catch (error: Exception) {
            handleCoreFailure(effect, error.message ?: "PLAYBACK_INVALID_EFFECT", scope)
            return
        }
        val result = try {
            awaitCore(correlationId, effect.type, command.first, command.second)
        } catch (error: Exception) {
            if (effect.type == "tts.queue.stop") ttsContexts.remove(correlationId)
            handleCoreFailure(effect, error.message ?: "PLAYBACK_CORE_FAILURE", scope)
            return
        }
        if (!isCorrelationCurrent(effect)) return
        when (effect.type) {
            "reader.location.resolve" -> completePageLocation(effect, result, scope)
            "reader.progress.update" -> completePageProgress(effect, result, scope)
            "tts.queue.plan" -> completeTtsPlan(correlationId, result, scope)
            "tts.queue.start" -> completeTtsQueueStart(correlationId, result, scope)
            "tts.queue.stop" -> ttsContexts.remove(correlationId)
            else -> handleCoreFailure(effect, "PLAYBACK_UNSUPPORTED_CORE_EFFECT:${effect.type}", scope)
        }
    }

    private suspend fun awaitCore(
        correlationId: String,
        operation: String,
        method: String,
        params: JSONObject
    ): JSONObject {
        val handle = commandClientFactory().beginCommand(method, params)
        val key = "$correlationId:$operation:${handle.requestId}"
        synchronized(this) { handles[key] = handle }
        domainStore.incrementCore()
        return try {
            handle.await()
        } finally {
            synchronized(this) { if (handles[key] === handle) handles.remove(key) }
        }
    }

    private fun buildCoreCommand(effect: ReaderUIEffect): Pair<String, JSONObject> {
        val correlationId = requireNotNull(effect.correlationId)
        val reader = ttsContexts[correlationId] ?: domainStore.state.value.reader
            ?: error("playback requires a Core reader snapshot")
        return when (effect.type) {
            "reader.location.resolve" -> "reader.location.resolve" to JSONObject().apply {
                put("sourceId", reader.sourceId)
                put("bookId", reader.bookId)
                put("chapterIndex", effect.payload.requiredInt("chapterIndex"))
                put("chapterTitle", reader.chapter.title)
                put("anchor", JSONObject().apply {
                    put("chapterOffset", effect.payload.requiredInt("chapterOffset"))
                    put("chapterProgress", effect.payload.requiredDouble("chapterProgress"))
                })
                put("layout", JSONObject().apply {
                    put("viewportWidth", effect.payload.requiredInt("viewportWidth"))
                    put("viewportHeight", effect.payload.requiredInt("viewportHeight"))
                    put("fontScale", effect.payload.requiredDouble("fontScale"))
                    put("pageIndex", effect.payload.requiredInt("targetPageIndex"))
                })
            }
            "reader.progress.update" -> {
                val pending = domainStore.state.value.pendingProgress
                    ?.takeIf { it.correlationId == correlationId }
                    ?: error("reader.progress.update requires matching pending canonical location")
                "reading.progress.update" to JSONObject().apply {
                    put("sourceId", pending.sourceId)
                    put("bookId", pending.bookId)
                    put("chapterIndex", pending.location.chapterIndex)
                    put("chapterOffset", pending.location.chapterOffset)
                    put("chapterProgress", pending.location.chapterProgress)
                    put("locationRevision", pending.location.locationRevision)
                    put("updatedAt", pending.updatedAt)
                }
            }
            "tts.queue.plan" -> "tts.slice" to JSONObject().apply {
                put("chapter", reader.toTtsChapterJson())
                put("content", reader.content)
                put("strategy", "paragraph")
            }
            "tts.queue.start" -> {
                val plan = domainStore.state.value.ttsPlan
                    ?.takeIf { domainStore.state.value.ttsCorrelationId == correlationId }
                    ?: error("tts.queue.start requires the matching typed plan")
                "tts.queue.play" to JSONObject().apply {
                    put("plan", JSONObject(plan.rawPlan.toString()))
                    put("startSliceIndex", 0)
                }
            }
            "tts.queue.stop" -> "tts.queue.stop" to JSONObject().put("chapter", reader.toTtsChapterJson())
            else -> error("unsupported playback Core effect: ${effect.type}")
        }
    }

    private suspend fun completePageLocation(effect: ReaderUIEffect, result: JSONObject, scope: CoroutineScope) {
        val correlationId = requireNotNull(effect.correlationId)
        val reader = domainStore.state.value.reader ?: return
        val expectedChapterIndex = effect.payload.requiredInt("chapterIndex")
        val location = try {
            parseCanonicalLocation(result, reader.bookId, expectedChapterIndex)
        } catch (error: Exception) {
            handleCoreFailure(effect, "PAGE_INVALID_LOCATION:${error.message}", scope)
            return
        }
        val pageIndex = effect.payload.requiredInt("targetPageIndex")
        val canonical = "${location.bookId}:${location.chapterIndex}:${location.chapterOffset}:${location.locationRevision}"
        val advance = runtime.acceptPageLocationResult(
            correlationId = correlationId,
            canonicalLocation = canonical,
            pageIndex = pageIndex
        )
        if (!advance.accepted) return
        val pending = ReaderPlaybackPendingProgress(
            correlationId = correlationId,
            sourceId = reader.sourceId,
            bookId = reader.bookId,
            pageIndex = pageIndex,
            chapterPosition = reader.selectedChapterPosition,
            totalChapters = reader.chapters.size,
            bookName = reader.book.name,
            author = reader.book.author.takeIf(String::isNotBlank),
            chapterTitle = reader.chapter.title,
            chapterUrl = reader.chapter.url,
            location = location,
            updatedAt = nextProgressTimestamp()
        )
        if (!domainStore.recordPendingProgress(pending)) return
        // Re-enter behind the current location effect so its terminal trace is
        // recorded before the progress write begins.
        enqueueSerial(scope) { processAdvance(advance, scope) }
    }

    private suspend fun completePageProgress(effect: ReaderUIEffect, result: JSONObject, scope: CoroutineScope) {
        val correlationId = requireNotNull(effect.correlationId)
        val pending = domainStore.state.value.pendingProgress
            ?.takeIf { it.correlationId == correlationId }
            ?: return
        try {
            validateStoredProgress(result, pending)
        } catch (error: Exception) {
            handleCoreFailure(effect, "PAGE_INVALID_PROGRESS:${error.message}", scope)
            return
        }
        val advance = runtime.acceptPageProgressResult(correlationId, stored = true)
        if (!advance.accepted) return
        if (!domainStore.commitPage(correlationId, pending)) return
        // Core is the canonical write. Room is a lossy UX/recent-books mirror
        // and must never turn a confirmed Core success into a failed page step.
        runCatching { progressMirror.mirror(pending) }
        // Auto-page rearm follows the terminal progress trace and can only run
        // after the canonical + visible commit above.
        enqueueSerial(scope) { processAdvance(advance, scope) }
    }

    private suspend fun completeTtsPlan(correlationId: String, result: JSONObject, scope: CoroutineScope) {
        val reader = ttsContexts[correlationId] ?: return
        val plan = try {
            parseTtsPlan(result, reader)
        } catch (error: Exception) {
            processAdvance(runtime.acceptTTSCoreResult("tts.queue.plan", correlationId, error.message), scope)
            return
        }
        if (!domainStore.recordPlan(correlationId, plan)) return
        processAdvance(runtime.acceptTTSCoreResult("tts.queue.plan", correlationId), scope)
    }

    private suspend fun completeTtsQueueStart(correlationId: String, result: JSONObject, scope: CoroutineScope) {
        val reader = ttsContexts[correlationId] ?: return
        val snapshot = try {
            parseTtsSnapshot(result, reader)
        } catch (error: Exception) {
            processAdvance(runtime.acceptTTSCoreResult("tts.queue.start", correlationId, error.message), scope)
            return
        }
        if (!domainStore.recordTtsSnapshot(correlationId, snapshot)) return
        processAdvance(runtime.acceptTTSCoreResult("tts.queue.start", correlationId), scope)
    }

    private suspend fun startSpeech(correlationId: String, scope: CoroutineScope) {
        val domain = domainStore.state.value
        val plan = domain.ttsPlan?.takeIf { domain.ttsCorrelationId == correlationId } ?: run {
            processAdvance(runtime.acceptTTSSystemStart(correlationId, "TTS_PLAN_MISSING"), scope)
            return
        }
        val snapshot = domain.ttsSnapshot ?: run {
            processAdvance(runtime.acceptTTSSystemStart(correlationId, "TTS_SNAPSHOT_MISSING"), scope)
            return
        }
        val sliceIndex = snapshot.currentSliceIndex ?: 0
        val slice = plan.slices.getOrNull(sliceIndex) ?: run {
            processAdvance(runtime.acceptTTSSystemStart(correlationId, "TTS_SLICE_MISSING"), scope)
            return
        }
        val speechGeneration = generationCounter.incrementAndGet()
        speechGenerations[correlationId] = speechGeneration
        val utteranceId = "$correlationId:slice:${slice.index}:$speechGeneration"
        val init = speechEngine.init()
        if (init.isFailure) {
            processAdvance(
                runtime.acceptTTSSystemStart(
                    correlationId,
                    init.exceptionOrNull()?.message ?: "TTS_INIT_FAILED"
                ),
                scope
            )
            return
        }
        if (!isSpeechCurrent(correlationId, speechGeneration)) return
        val handle = try {
            speechEngine.begin(TtsUtterance(text = slice.text, utteranceId = utteranceId))
        } catch (error: Exception) {
            processAdvance(runtime.acceptTTSSystemStart(correlationId, error.message), scope)
            return
        }
        synchronized(this) { speechHandles[correlationId] = handle }
        domainStore.recordUtterance(correlationId, utteranceId)
        domainStore.incrementSpeech()
        try {
            handle.awaitStarted()
        } catch (error: Exception) {
            if (isSpeechCurrent(correlationId, speechGeneration)) {
                processAdvance(runtime.acceptTTSSystemStart(correlationId, error.message), scope)
            }
            return
        }
        if (!isSpeechCurrent(correlationId, speechGeneration)) return
        processAdvance(runtime.acceptTTSSystemStart(correlationId), scope)
        // Completion is the only asynchronous continuation allowed outside
        // the serial effect queue. It re-enters the same queue before any Core
        // report-status/next command is issued.
        scope.launch {
            try {
                handle.awaitCompletion()
                enqueueSerial(scope) {
                    if (isSpeechCurrent(correlationId, speechGeneration)) {
                        onUtteranceDone(correlationId, slice.index, speechGeneration, scope)
                    }
                }
            } catch (error: Exception) {
                if (isSpeechCurrent(correlationId, speechGeneration)) {
                    enqueueSerial(scope) {
                        if (isSpeechCurrent(correlationId, speechGeneration)) {
                            onUtteranceFailed(correlationId, slice.index, error, scope)
                        }
                    }
                }
            } finally {
                synchronized(this@ReaderPlaybackEffectExecutor) {
                    if (speechHandles[correlationId] === handle) speechHandles.remove(correlationId)
                }
            }
        }
    }

    private suspend fun onUtteranceDone(
        correlationId: String,
        sliceIndex: Int,
        speechGeneration: Long,
        scope: CoroutineScope
    ) {
        val reader = ttsContexts[correlationId] ?: return
        val report = awaitCore(
            correlationId,
            "tts.queue.report-status:$sliceIndex:$speechGeneration",
            "tts.queue.report-status",
            JSONObject()
                .put("chapter", reader.toTtsChapterJson())
                .put("sliceIndex", sliceIndex)
                .put("status", "done")
        )
        parseTtsSnapshot(report, reader)
        if (!isSpeechCurrent(correlationId, speechGeneration)) return
        val next = awaitCore(
            correlationId,
            "tts.queue.next:$sliceIndex:$speechGeneration",
            "tts.queue.next",
            JSONObject().put("chapter", reader.toTtsChapterJson())
        )
        val snapshot = parseTtsSnapshot(next, reader)
        if (!isSpeechCurrent(correlationId, speechGeneration)) return
        domainStore.recordTtsSnapshot(correlationId, snapshot)
        domainStore.recordUtterance(correlationId, null)
        if (snapshot.state == "completed" || snapshot.currentSliceIndex == null) {
            processAdvance(runtime.stopTTS(correlationId), scope)
        } else {
            // The runtime transaction remains `playing`; this is an internal,
            // correlation-scoped continuation, never a second UI start event.
            startSpeech(correlationId, scope)
        }
    }

    private suspend fun onUtteranceFailed(
        correlationId: String,
        sliceIndex: Int,
        error: Exception,
        scope: CoroutineScope
    ) {
        val reader = ttsContexts[correlationId]
        if (reader != null) {
            runCatching {
                awaitCore(
                    correlationId,
                    "tts.queue.report-status.failed:$sliceIndex",
                    "tts.queue.report-status",
                    JSONObject()
                        .put("chapter", reader.toTtsChapterJson())
                        .put("sliceIndex", sliceIndex)
                        .put("status", "failed")
                )
            }
        }
        domainStore.fail(correlationId, error.message ?: "TTS_SPEECH_FAILED")
        processAdvance(runtime.stopTTS(correlationId), scope)
    }

    private suspend fun stopSpeech(correlationId: String) {
        invalidateSpeech(correlationId)
        runCatching { speechEngine.stop() }
    }

    private fun armTimer(effect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = requireNotNull(effect.correlationId)
        val timerId = effect.payload["timerId"] ?: return
        val delayMs = effect.payload.requiredLong("delayMs")
        val generation = effect.payload.requiredInt("generation")
        if (effect.payload["oneShot"] != "true" || effect.payload["foregroundOnly"] != "true") {
            domainStore.fail(correlationId, "AUTO_PAGE_TIMER_POLICY_INVALID")
            return
        }
        synchronized(this) { timerJobs.remove(timerId)?.cancel() }
        domainStore.incrementTimer()
        val job = scope.launch {
            delay(delayMs)
            synchronized(this@ReaderPlaybackEffectExecutor) { timerJobs.remove(timerId) }
            enqueueSerial(scope) {
                val advance = runtime.acceptAutoPageTimerFired(correlationId, generation)
                if (advance.accepted) processAdvance(advance, scope)
            }
        }
        synchronized(this) { timerJobs[timerId] = job }
    }

    private fun cancelTimer(effect: ReaderUIEffect) {
        val timerId = effect.payload["timerId"] ?: effect.correlationId ?: return
        synchronized(this) { timerJobs.remove(timerId)?.cancel() }
    }

    private suspend fun handleCoreFailure(effect: ReaderUIEffect, message: String, scope: CoroutineScope) {
        val correlationId = effect.correlationId ?: return
        domainStore.fail(correlationId, message)
        val advance = when (effect.type) {
            "reader.location.resolve" -> runtime.acceptPageLocationResult(correlationId, error = message)
            "reader.progress.update" -> runtime.acceptPageProgressResult(correlationId, error = message)
            "tts.queue.plan", "tts.queue.start" -> runtime.acceptTTSCoreResult(effect.type, correlationId, message)
            else -> null
        }
        if (effect.type == "reader.progress.update") {
            domainStore.discardPendingProgress(correlationId)
        }
        if (advance != null) processAdvance(advance, scope)
    }

    private fun isCorrelationCurrent(effect: ReaderUIEffect): Boolean {
        val correlationId = effect.correlationId ?: return false
        val domain = domainStore.state.value
        return when (effect.type) {
            "reader.location.resolve" -> domain.pageCorrelationId == correlationId
            "reader.progress.update" -> domain.pageCorrelationId == correlationId &&
                domain.pendingProgress?.correlationId == correlationId
            "tts.queue.plan", "tts.queue.start" -> domain.ttsCorrelationId == correlationId
            "tts.queue.stop" -> correlationId in ttsContexts
            else -> false
        }
    }

    @Synchronized
    private fun invalidateCorrelation(correlationId: String) {
        handles.entries.filter { it.key.startsWith("$correlationId:") }.forEach { (key, handle) ->
            if (key.startsWith("$correlationId:reader.progress.update:")) return@forEach
            handle.cancel()
            handles.remove(key)
        }
        timerJobs.remove(correlationId)?.cancel()
        invalidateSpeech(correlationId)
    }

    @Synchronized
    private fun invalidateSpeech(correlationId: String) {
        speechGenerations[correlationId] = generationCounter.incrementAndGet()
        speechHandles.remove(correlationId)?.cancel()
        domainStore.recordUtterance(correlationId, null)
    }

    private fun isSpeechCurrent(correlationId: String, generation: Long): Boolean =
        synchronized(this) {
            speechGenerations[correlationId] == generation &&
                domainStore.state.value.ttsCorrelationId == correlationId
        }

    private fun notifyProjection(runtimeState: ReaderUIState) {
        projectionSink?.invoke(runtimeState, domainStore.state.value)
    }

    @Synchronized
    private fun enqueueSerial(scope: CoroutineScope, block: suspend () -> Unit) {
        val predecessor = serialTail
        serialTail = scope.launch {
            predecessor?.join()
            block()
        }
    }

    private fun parseTtsPlan(result: JSONObject, reader: ReaderPlaybackReaderSnapshot): ReaderPlaybackTtsPlan {
        val plan = result.optJSONObject("plan") ?: error("tts.slice missing plan")
        val chapter = plan.optJSONObject("chapter") ?: error("tts.slice missing plan.chapter")
        val sourceId = chapter.requiredString("sourceId")
        val bookId = chapter.requiredString("bookId")
        val chapterIndex = chapter.requiredNonNegativeInt("chapterIndex")
        require(sourceId == reader.sourceId && bookId == reader.bookId && chapterIndex == reader.chapter.index) {
            "tts.slice chapter identity drift"
        }
        val slicesJson = plan.optJSONArray("slices") ?: error("tts.slice missing slices")
        require(slicesJson.length() > 0) { "tts.slice returned an empty plan" }
        val slices = (0 until slicesJson.length()).map { position ->
            val slice = slicesJson.getJSONObject(position)
            ReaderPlaybackTtsSlice(
                index = slice.requiredNonNegativeInt("index"),
                text = slice.requiredString("text"),
                charStart = slice.requiredNonNegativeInt("charStart"),
                charEnd = slice.requiredNonNegativeInt("charEnd"),
                paragraphIndex = slice.requiredNonNegativeInt("paragraphIndex")
            ).also {
                require(it.index == position && it.charEnd >= it.charStart && it.charEnd <= reader.content.length) {
                    "tts.slice returned invalid slice offsets/order"
                }
            }
        }
        return ReaderPlaybackTtsPlan(
            chapterSourceId = sourceId,
            chapterBookId = bookId,
            chapterIndex = chapterIndex,
            strategy = plan.requiredString("strategy"),
            slices = slices,
            sourceCharCount = plan.requiredNonNegativeInt("sourceCharCount"),
            rawPlan = JSONObject(plan.toString())
        ).also {
            require(it.sourceCharCount == reader.content.codePointCount(0, reader.content.length)) {
                "tts.slice sourceCharCount drift"
            }
        }
    }

    private fun parseTtsSnapshot(result: JSONObject, reader: ReaderPlaybackReaderSnapshot): ReaderPlaybackTtsSnapshot {
        val snapshot = result.optJSONObject("snapshot") ?: error("tts.queue result missing snapshot")
        val chapter = snapshot.optJSONObject("chapter") ?: error("tts.queue snapshot missing chapter")
        val sourceId = chapter.requiredString("sourceId")
        val bookId = chapter.requiredString("bookId")
        val chapterIndex = chapter.requiredNonNegativeInt("chapterIndex")
        require(sourceId == reader.sourceId && bookId == reader.bookId && chapterIndex == reader.chapter.index) {
            "tts.queue snapshot identity drift"
        }
        val state = snapshot.requiredString("state")
        require(state in setOf("idle", "playing", "paused", "completed", "stopped")) {
            "tts.queue snapshot state invalid"
        }
        val currentSlice = if (snapshot.isNull("currentSliceIndex")) null else {
            snapshot.requiredNonNegativeInt("currentSliceIndex")
        }
        val total = snapshot.requiredNonNegativeInt("totalSlices")
        val completed = snapshot.requiredNonNegativeInt("completedSlices")
        require(completed <= total && (currentSlice == null || currentSlice < total)) {
            "tts.queue snapshot counts invalid"
        }
        return ReaderPlaybackTtsSnapshot(
            state,
            currentSlice,
            total,
            completed,
            sourceId,
            bookId,
            chapterIndex
        )
    }

    private fun parseCanonicalLocation(
        result: JSONObject,
        expectedBookId: String,
        expectedChapterIndex: Int
    ): ReaderBookOpenCanonicalLocation {
        require(result.optBoolean("resolved", false)) { "reader.location.resolve resolved must be true" }
        val canonical = result.optJSONObject("canonicalLocation")
            ?: error("reader.location.resolve missing canonicalLocation")
        val bookId = canonical.requiredString("bookId")
        val chapterIndex = canonical.requiredNonNegativeInt("chapterIndex")
        val chapterOffset = canonical.requiredNonNegativeLong("chapterOffset")
        val chapterProgress = canonical.requiredProgress("chapterProgress")
        val revision = canonical.requiredString("locationRevision")
        val resolverVersion = result.requiredString("resolverVersion")
        val reflow = result.optJSONObject("reflow") ?: error("reader.location.resolve missing reflow")
        require(bookId == expectedBookId && chapterIndex == expectedChapterIndex) {
            "reader.location.resolve identity drift"
        }
        require(reflow.has("layoutIndependent")) { "reader.location.resolve missing layoutIndependent" }
        return ReaderBookOpenCanonicalLocation(
            bookId,
            chapterIndex,
            chapterOffset,
            chapterProgress,
            revision,
            resolverVersion,
            reflow.requiredString("strategy"),
            reflow.requiredString("primaryAnchor"),
            reflow.requiredString("fallbackAnchor"),
            reflow.getBoolean("layoutIndependent")
        )
    }

    private fun validateStoredProgress(
        result: JSONObject,
        pending: ReaderPlaybackPendingProgress
    ) {
        require(result.optBoolean("stored", false)) { "reading.progress.update stored must be true" }
        require(result.requiredString("sourceId") == pending.sourceId) { "sourceId drift" }
        require(result.requiredString("bookId") == pending.bookId) { "bookId drift" }
        require(result.requiredNonNegativeLong("updatedAt") == pending.updatedAt) { "updatedAt stale/current-row drift" }
        require(result.requiredNonNegativeInt("chapterIndex") == pending.location.chapterIndex) { "chapterIndex drift" }
        require(result.requiredNonNegativeLong("chapterOffset") == pending.location.chapterOffset) { "chapterOffset drift" }
        require(result.requiredProgress("chapterProgress") == pending.location.chapterProgress) { "chapterProgress drift" }
        require(result.requiredString("locationRevision") == pending.location.locationRevision) {
            "locationRevision drift"
        }
    }

    private fun nextProgressTimestamp(): Long {
        while (true) {
            val previous = progressTimestamp.get()
            val next = maxOf(epochSeconds(), previous + 1L, 1L)
            if (progressTimestamp.compareAndSet(previous, next)) return next
        }
    }

    private fun ReaderPlaybackReaderSnapshot.toTtsChapterJson(): JSONObject = JSONObject().apply {
        put("sourceId", sourceId)
        put("bookId", bookId)
        put("chapterIndex", chapter.index)
        put("chapterTitle", chapter.title)
        put("chapterUrl", chapter.url)
    }

    private fun Map<String, String>.requiredInt(name: String): Int =
        get(name)?.toIntOrNull() ?: error("playback missing/invalid $name")

    private fun Map<String, String>.requiredLong(name: String): Long =
        get(name)?.toLongOrNull() ?: error("playback missing/invalid $name")

    private fun Map<String, String>.requiredDouble(name: String): Double =
        get(name)?.toDoubleOrNull()?.takeIf(Double::isFinite)
            ?: error("playback missing/invalid $name")

    private fun JSONObject.requiredString(name: String): String =
        optString(name).takeIf(String::isNotBlank) ?: error("missing/blank $name")

    private fun JSONObject.requiredNonNegativeInt(name: String): Int {
        if (!has(name)) error("missing $name")
        return getInt(name).also { require(it >= 0) { "$name must be >= 0" } }
    }

    private fun JSONObject.requiredNonNegativeLong(name: String): Long {
        if (!has(name)) error("missing $name")
        return getLong(name).also { require(it >= 0L) { "$name must be >= 0" } }
    }

    private fun JSONObject.requiredProgress(name: String): Double {
        if (!has(name)) error("missing $name")
        return getDouble(name).also { require(it.isFinite() && it in 0.0..1.0) }
    }
}
