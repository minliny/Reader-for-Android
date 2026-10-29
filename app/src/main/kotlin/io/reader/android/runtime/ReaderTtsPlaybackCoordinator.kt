package io.reader.android.runtime

import android.util.Log
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
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

data class ReaderTtsPlaybackState(
    val status: ReaderTtsPlaybackStatus,
    val chapter: ReaderTtsChapterRef? = null,
    val currentSliceIndex: Int? = null,
    val totalSlices: Int = 0,
    val error: String? = null,
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
    private var activeToken: ReaderTtsPlaybackToken? = null
    private val handledCallbacks = mutableSetOf<String>()
    @Volatile var state: ReaderTtsPlaybackState = ReaderTtsPlaybackState(ReaderTtsPlaybackStatus.IDLE)
        private set
    @Volatile private var closed = false

    init { engine.setListener(this) }

    @Synchronized
    fun start(nextPlan: ReaderTtsSlicePlan, startSliceIndex: Int = 0) {
        require(!closed) { "TTS_COORDINATOR_CLOSED" }
        require(startSliceIndex in nextPlan.slices.indices) { "TTS_START_SLICE_INDEX" }
        retireToken()
        val snapshot = try { core { commands.queuePlay(nextPlan, startSliceIndex) } } catch (error: Throwable) {
            fail(error); throw error
        }
        check(snapshot.totalSlices == nextPlan.slices.size) { "TTS_QUEUE_PLAN_BINDING" }
        check(snapshot.currentSliceIndex == startSliceIndex) { "TTS_QUEUE_START_BINDING" }
        plan = nextPlan; currentIndex = startSliceIndex
        publish(ReaderTtsPlaybackStatus.PREPARING, currentIndex, null)
        Log.i("ReaderTts", "Coordinator start chapter=${nextPlan.chapter.bookId}/${nextPlan.chapter.chapterIndex} slices=${nextPlan.slices.size} engineReady=${engine.ready}")
        if (engine.ready) speakCurrent() else engine.initialize()
    }

    @Synchronized
    fun pause() {
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        check(state.status == ReaderTtsPlaybackStatus.PLAYING || state.status == ReaderTtsPlaybackStatus.PREPARING) { "TTS_PAUSE_STATE" }
        retireToken(); engine.stop()
        val snapshot = try { core { commands.queuePause(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        currentIndex = snapshot.currentSliceIndex ?: currentIndex
        publish(ReaderTtsPlaybackStatus.PAUSED, currentIndex, null)
    }

    @Synchronized
    fun resume() {
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        check(state.status == ReaderTtsPlaybackStatus.PAUSED) { "TTS_RESUME_STATE" }
        val snapshot = try { core { commands.queueResume(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        currentIndex = snapshot.currentSliceIndex ?: error("TTS_RESUME_CURSOR")
        publish(ReaderTtsPlaybackStatus.PREPARING, currentIndex, null)
        if (engine.ready) speakCurrent() else engine.initialize()
    }

    @Synchronized
    fun stop() {
        val active = plan ?: return
        retireToken(); engine.stop()
        try { core { commands.queueStop(active.chapter) } } catch (error: Throwable) { fail(error); throw error }
        publish(ReaderTtsPlaybackStatus.STOPPED, currentIndex, null)
    }

    @Synchronized
    fun setRate(rate: Int): ReaderTtsQueueSetRateResult {
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        val result = core { commands.queueSetRate(active.chapter, rate) }
        engine.setRate(result.rate)
        return result
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true; retireToken(); engine.stop(); engine.shutdown()
        publish(ReaderTtsPlaybackStatus.STOPPED, currentIndex, null)
    }

    override fun onReady(success: Boolean) = dispatch {
        synchronized(this) {
            Log.i("ReaderTts", "Coordinator onReady success=$success status=${state.status}")
            if (closed || state.status != ReaderTtsPlaybackStatus.PREPARING) return@synchronized
            if (!success) { fail(IllegalStateException("TTS_ENGINE_UNAVAILABLE")); return@synchronized }
            try { speakCurrent() } catch (error: Throwable) { fail(error) }
        }
    }

    override fun onStart(id: String) = dispatch { handleStart(id) }
    override fun onDone(id: String) = dispatch { handleTerminal(id, ReaderTtsSliceStatus.DONE, "done") }
    override fun onError(id: String) = dispatch { handleTerminal(id, ReaderTtsSliceStatus.FAILED, "error") }

    private fun handleStart(id: String) {
        synchronized(this) {
            val token = activeToken ?: return
            if (token.utteranceId != id || !handledCallbacks.add(token.callbackId("start"))) return
            try {
                core { commands.queueReportCallback(token.chapter, token.sliceIndex, ReaderTtsSliceStatus.SPEAKING, token.callbackId("start")) }
                publish(ReaderTtsPlaybackStatus.PLAYING, currentIndex, null)
            } catch (error: Throwable) { fail(error) }
        }
    }

    private fun handleTerminal(id: String, status: ReaderTtsSliceStatus, event: String) {
        synchronized(this) {
            val token = activeToken ?: return
            val callbackId = token.callbackId(event)
            if (token.utteranceId != id || !handledCallbacks.add(callbackId)) return
            try {
                val result = core { commands.queueReportCallback(token.chapter, token.sliceIndex, status, callbackId) }
                if (status == ReaderTtsSliceStatus.FAILED && result.failureAction == ReaderTtsFailureAction.STOP) {
                    retireToken(); publish(ReaderTtsPlaybackStatus.FAILED, currentIndex, "TTS_ENGINE_ERROR"); return
                }
                val next = result.snapshot.currentSliceIndex
                if (result.snapshot.state == ReaderTtsQueueState.COMPLETED || next == null) {
                    retireToken(); publish(ReaderTtsPlaybackStatus.COMPLETED, null, null); return
                }
                check(next != token.sliceIndex) { "TTS_QUEUE_DID_NOT_ADVANCE" }
                currentIndex = next; retireToken(); publish(ReaderTtsPlaybackStatus.PREPARING, next, null); speakCurrent()
            } catch (error: Throwable) { fail(error) }
        }
    }

    private fun speakCurrent() {
        val active = checkNotNull(plan) { "TTS_PLAN_MISSING" }
        val index = checkNotNull(currentIndex) { "TTS_CURSOR_MISSING" }
        val generationValue = generation.incrementAndGet()
        val id = "reader-tts-$sessionId-$generationValue-$index"
        activeToken = ReaderTtsPlaybackToken(sessionId, generationValue, active.chapter, index, id)
        // Core has accepted the queue transition, but the platform engine has
        // not confirmed vocalization yet. PLAYING is published from onStart.
        publish(ReaderTtsPlaybackStatus.PREPARING, index, null)
        Log.i("ReaderTts", "Coordinator speak slice=$index id=$id")
        engine.speak(id, active.slices[index].text)
    }

    private fun retireToken() { activeToken = null; generation.incrementAndGet() }

    private fun fail(error: Throwable) {
        retireToken(); publish(ReaderTtsPlaybackStatus.FAILED, currentIndex, error.message ?: error::class.java.simpleName)
    }

    private fun publish(status: ReaderTtsPlaybackStatus, index: Int?, error: String?) {
        val active = plan
        state = ReaderTtsPlaybackState(status, active?.chapter, index, active?.slices?.size ?: 0, error)
        onStateChanged(state)
    }

    private fun dispatch(block: () -> Unit) { eventExecutor.execute(block) }

    private fun <T> core(block: () -> T): T {
        val task = FutureTask(block)
        coreExecutor.execute(task)
        return task.get()
    }
}
