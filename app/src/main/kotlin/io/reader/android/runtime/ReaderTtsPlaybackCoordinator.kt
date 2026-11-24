package io.reader.android.runtime

import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal interface ReaderTtsPlaybackCommands {
    fun queuePlay(plan: ReaderTtsSlicePlan, startSliceIndex: Int = 0): ReaderTtsQueueSnapshot
    fun queuePause(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot
    fun queueResume(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot
    fun queueStop(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot
    fun queueSetRate(chapter: ReaderTtsChapterRef, rate: Int): ReaderTtsQueueSetRateResult
    fun queueReportCallback(chapter: ReaderTtsChapterRef, sliceIndex: Int, status: ReaderTtsSliceStatus,
        callbackId: String, failurePolicy: ReaderTtsFailurePolicy = ReaderTtsFailurePolicy.STOP,
        failureLimit: Int = 3): ReaderTtsQueueCallbackResult
}

internal interface ReaderTtsPlaybackEngine {
    val ready: Boolean
    fun setListener(listener: Listener)
    fun initialize()
    fun speak(utteranceId: String, text: String)
    fun setRate(rate: Int)
    fun stop()
    fun shutdown()

    interface Listener {
        fun onReady(success: Boolean)
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onError(utteranceId: String)
    }
}

enum class ReaderTtsPlaybackStatus { IDLE, PREPARING, PLAYING, PAUSED, COMPLETED, STOPPED, FAILED }

/** Half-open Unicode scalar range in the original chapter, as returned by Core. */
data class ReaderTtsHighlight(val scalarStart: Int, val scalarEnd: Int)

data class ReaderTtsPlaybackState(
    val status: ReaderTtsPlaybackStatus,
    val chapter: ReaderTtsChapterRef? = null,
    val currentSliceIndex: Int? = null,
    val totalSlices: Int = 0,
    val error: String? = null,
    val highlight: ReaderTtsHighlight? = null,
)

private data class ReaderTtsPlaybackToken(
    val sessionId: String,
    val generation: Long,
    val chapter: ReaderTtsChapterRef,
    val sliceIndex: Int,
    val utteranceId: String,
) {
    fun callbackId(event: String) = "$sessionId:$generation:$sliceIndex:$event"
}

/**
 * Pure host playback coordinator. Core calls run through coreExecutor; engine
 * calls are delegated to the injected engine, whose Android implementation
 * marshals every API call to the main thread.
 */
internal class ReaderTtsPlaybackCoordinator(
    private val commands: ReaderTtsPlaybackCommands,
    private val engine: ReaderTtsPlaybackEngine,
    private val eventExecutor: Executor = Executor { it.run() },
    private val coreExecutor: Executor = Executor { it.run() },
    private val onStateChanged: (ReaderTtsPlaybackState) -> Unit = {},
) : ReaderTtsPlaybackEngine.Listener {
    private val generation = AtomicLong(0)
    private val sessionId = UUID.randomUUID().toString()
    private var plan: ReaderTtsSlicePlan? = null
    private var currentIndex: Int? = null
    private var initialSlice: Int? = null
    private var initialCharSkip = 0
    private var queueOpen = false
    private var activeToken: ReaderTtsPlaybackToken? = null
    private val handledCallbacks = mutableSetOf<String>()
    @Volatile var state: ReaderTtsPlaybackState = ReaderTtsPlaybackState(ReaderTtsPlaybackStatus.IDLE)
        private set
    private val retired = AtomicBoolean(false)

    init { engine.setListener(this) }

    @Synchronized
    fun start(nextPlan: ReaderTtsSlicePlan, startSliceIndex: Int = 0) {
        startQueue(nextPlan, startSliceIndex, 0)
    }

    @Synchronized
    fun startAtVisibleScalar(nextPlan: ReaderTtsSlicePlan, source: String, scalar: Int) {
        if (retired.get()) return
        require(scalar in 0..nextPlan.sourceCharCount) { "TTS_VISIBLE_SCALAR" }
        val first = nextPlan.slices.firstOrNull { it.charEnd > scalar }
        if (first == null) {
            plan = nextPlan
            publish(ReaderTtsPlaybackStatus.COMPLETED, null, null)
            return
        }
        var spokenScalarsToSkip = (scalar - first.charStart).coerceAtLeast(0)
        if (spokenScalarsToSkip > 0) {
            val start = source.offsetByCodePoints(0, first.charStart)
            val end = source.offsetByCodePoints(start, spokenScalarsToSkip)
            // Core keeps image placeholders in original coordinates but omits
            // them from speech text. Keep the Core plan and crop only the engine input.
            for (offset in start until end) if (source[offset] == '\uFFFC') spokenScalarsToSkip--
        }
        startQueue(nextPlan, first.index, spokenScalarsToSkip)
    }

    private fun startQueue(nextPlan: ReaderTtsSlicePlan, startSliceIndex: Int, charSkip: Int) {
        if (retired.get()) return
        require(startSliceIndex in nextPlan.slices.indices) { "TTS_START_SLICE_INDEX" }
        require(!queueOpen) { "TTS_QUEUE_ALREADY_OPEN" }
        retireToken()
        val snapshot = try { core { commands.queuePlay(nextPlan, startSliceIndex) } } catch (error: Throwable) {
            fail(error); throw error
        }
        plan = nextPlan
        queueOpen = true
        check(snapshot.totalSlices == nextPlan.slices.size) { "TTS_QUEUE_PLAN_BINDING" }
        check(snapshot.currentSliceIndex == startSliceIndex) { "TTS_QUEUE_START_BINDING" }
        if (retired.get()) return
        currentIndex = startSliceIndex
        initialSlice = startSliceIndex
        initialCharSkip = charSkip
        publish(ReaderTtsPlaybackStatus.PREPARING, currentIndex, null)
        if (engine.ready) speakCurrent() else engine.initialize()
    }

    @Synchronized
    fun pause() {
        if (retired.get()) return
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        check(state.status == ReaderTtsPlaybackStatus.PLAYING || state.status == ReaderTtsPlaybackStatus.PREPARING) { "TTS_PAUSE_STATE" }
        retireToken(); engine.stop()
        val snapshot = try { core { commands.queuePause(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        currentIndex = snapshot.currentSliceIndex ?: currentIndex
        publish(ReaderTtsPlaybackStatus.PAUSED, currentIndex, null)
    }

    @Synchronized
    fun resume() {
        if (retired.get()) return
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        check(state.status == ReaderTtsPlaybackStatus.PAUSED) { "TTS_RESUME_STATE" }
        val snapshot = try { core { commands.queueResume(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        currentIndex = snapshot.currentSliceIndex ?: error("TTS_RESUME_CURSOR")
        publish(ReaderTtsPlaybackStatus.PREPARING, currentIndex, null)
        if (engine.ready) speakCurrent() else engine.initialize()
    }

    @Synchronized
    fun stop() {
        if (retired.get()) return
        val active = plan ?: return
        if (!queueOpen) return
        retireToken(); engine.stop()
        try { core { commands.queueStop(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        queueOpen = false
        publish(ReaderTtsPlaybackStatus.STOPPED, currentIndex, null)
    }

    @Synchronized
    fun setRate(rate: Int): ReaderTtsQueueSetRateResult {
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        val result = core { commands.queueSetRate(active.chapter, rate) }
        engine.setRate(result.rate)
        return result
    }

    /** Stop platform output right away while the Core turn finishes on its worker. */
    fun retireEngine() {
        if (!retired.compareAndSet(false, true)) return
        engine.stop()
        engine.shutdown()
    }

    @Synchronized
    fun close() {
        retireEngine()
        retireToken()
        val active = plan ?: return
        plan = null
        try {
            if (queueOpen) core { commands.queueStop(active.chapter) }
        } finally {
            queueOpen = false
            publish(ReaderTtsPlaybackStatus.STOPPED, null, null)
        }
    }

    override fun onReady(success: Boolean) = dispatch {
        synchronized(this) {
            if (retired.get() || state.status != ReaderTtsPlaybackStatus.PREPARING) return@synchronized
            if (!success) { fail(IllegalStateException("TTS_ENGINE_UNAVAILABLE")); return@synchronized }
            try { speakCurrent() } catch (error: Throwable) { fail(error) }
        }
    }

    override fun onStart(id: String) = dispatch { handleStart(id) }
    override fun onDone(id: String) = dispatch { handleTerminal(id, ReaderTtsSliceStatus.DONE, "done") }
    override fun onError(id: String) = dispatch { handleTerminal(id, ReaderTtsSliceStatus.FAILED, "error") }

    private fun handleStart(id: String) {
        synchronized(this) {
            if (retired.get()) return
            val token = activeToken ?: return
            if (token.utteranceId != id || !handledCallbacks.add(token.callbackId("start"))) return
            try {
                val result = core { commands.queueReportCallback(token.chapter, token.sliceIndex,
                    ReaderTtsSliceStatus.SPEAKING, token.callbackId("start")) }
                if (!retired.get() && result.callbackDisposition == ReaderTtsCallbackDisposition.APPLIED)
                    publish(ReaderTtsPlaybackStatus.PLAYING, currentIndex, null)
            } catch (error: Throwable) { fail(error) }
        }
    }

    private fun handleTerminal(id: String, status: ReaderTtsSliceStatus, event: String) {
        synchronized(this) {
            if (retired.get()) return
            val token = activeToken ?: return
            val callbackId = token.callbackId(event)
            if (token.utteranceId != id || !handledCallbacks.add(callbackId)) return
            try {
                val result = core { commands.queueReportCallback(token.chapter, token.sliceIndex, status, callbackId) }
                if (result.callbackDisposition != ReaderTtsCallbackDisposition.APPLIED) return
                if (status == ReaderTtsSliceStatus.FAILED && result.failureAction == ReaderTtsFailureAction.STOP) {
                    queueOpen = false
                    retireToken(); publish(ReaderTtsPlaybackStatus.FAILED, currentIndex, "TTS_ENGINE_ERROR"); return
                }
                val next = result.snapshot.currentSliceIndex
                if (result.snapshot.state == ReaderTtsQueueState.COMPLETED || next == null) {
                    queueOpen = false
                    retireToken(); publish(ReaderTtsPlaybackStatus.COMPLETED, null, null); return
                }
                if (retired.get()) return
                check(next != token.sliceIndex) { "TTS_QUEUE_DID_NOT_ADVANCE" }
                currentIndex = next; retireToken(); publish(ReaderTtsPlaybackStatus.PREPARING, next, null); speakCurrent()
            } catch (error: Throwable) { fail(error) }
        }
    }

    private fun speakCurrent() {
        if (retired.get()) return
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        val index = checkNotNull(currentIndex) { "TTS_CURSOR_MISSING" }
        val generationValue = generation.incrementAndGet()
        val id = "reader-tts-$sessionId-$generationValue-$index"
        activeToken = ReaderTtsPlaybackToken(sessionId, generationValue, active.chapter, index, id)
        // Core has accepted the queue transition, but the platform engine has
        // not confirmed vocalization yet. PLAYING is published from onStart.
        publish(ReaderTtsPlaybackStatus.PREPARING, index, null)
        val text = active.slices[index].text
        val start = if (initialSlice == index) ReaderTextCoordinates.utf16Offset(text, initialCharSkip) else 0
        engine.speak(id, text.substring(start))
    }

    private fun retireToken() { activeToken = null; generation.incrementAndGet() }

    private fun fail(error: Throwable) {
        retireToken(); publish(ReaderTtsPlaybackStatus.FAILED, currentIndex, error.message ?: error::class.java.simpleName)
    }

    private fun publish(status: ReaderTtsPlaybackStatus, index: Int?, error: String?) {
        val active = plan
        val highlight = if (status == ReaderTtsPlaybackStatus.PLAYING && index != null)
            active?.slices?.get(index)?.let { ReaderTtsHighlight(it.charStart, it.charEnd) } else null
        state = ReaderTtsPlaybackState(status, active?.chapter, index, active?.slices?.size ?: 0, error, highlight)
        onStateChanged(state)
    }

    private fun dispatch(block: () -> Unit) { eventExecutor.execute(block) }

    private fun <T> core(block: () -> T): T {
        val task = FutureTask(block)
        coreExecutor.execute(task)
        return task.get()
    }
}
