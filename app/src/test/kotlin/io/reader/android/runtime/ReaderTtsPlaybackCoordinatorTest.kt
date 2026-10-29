package io.reader.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsPlaybackCoordinatorTest {
    private val chapter = ReaderTtsChapterRef("src-a", "book-a", 2)
    private val plan = ReaderTtsSlicePlan(chapter, ReaderTtsSlicingStrategy.PARAGRAPH, listOf(
        ReaderTtsSlice(0, "第一段", 0, 3, 0), ReaderTtsSlice(1, "第二段", 3, 6, 1),
    ), 6)

    @Test
    fun initStartDoneAndDuplicateOrStaleCallbacksDoNotAdvanceTwice() {
        val commands = FakeCommands(plan)
        val engine = FakeEngine(ready = false)
        val coordinator = ReaderTtsPlaybackCoordinator(commands, engine)
        coordinator.start(plan)
        assertEquals(ReaderTtsPlaybackStatus.PREPARING, coordinator.state.status)
        engine.emitReady(true)
        val first = engine.spoken.single()
        engine.emitStart(first)
        engine.emitStart(first)
        engine.emitDone(first)
        engine.emitDone(first)
        assertEquals(2, engine.spoken.size)
        assertEquals(1, commands.reported.count { it.status == ReaderTtsSliceStatus.DONE })
        engine.emitDone(first)
        assertEquals(2, engine.spoken.size)
    }

    @Test
    fun pauseResumeReplaysCurrentSliceAndRetiresOldUtterance() {
        val commands = FakeCommands(plan)
        val engine = FakeEngine(ready = true)
        val coordinator = ReaderTtsPlaybackCoordinator(commands, engine)
        coordinator.start(plan)
        val first = engine.spoken.single()
        coordinator.pause()
        assertEquals(ReaderTtsPlaybackStatus.PAUSED, coordinator.state.status)
        engine.emitDone(first)
        assertEquals(1, engine.spoken.size)
        coordinator.resume()
        assertEquals(2, engine.spoken.size)
        assertTrue(engine.spoken[0] != engine.spoken[1])
        engine.emitStart(engine.spoken[1])
        assertEquals(ReaderTtsPlaybackStatus.PLAYING, coordinator.state.status)
    }

    @Test
    fun stopAndEngineFailurePreventLateCallbacksFromTouchingCore() {
        val commands = FakeCommands(plan)
        val engine = FakeEngine(ready = true)
        val coordinator = ReaderTtsPlaybackCoordinator(commands, engine)
        coordinator.start(plan)
        val first = engine.spoken.single()
        coordinator.stop()
        engine.emitDone(first)
        assertEquals(ReaderTtsPlaybackStatus.STOPPED, coordinator.state.status)
        assertEquals(0, commands.reported.size)

        val failingEngine = FakeEngine(ready = false)
        val failingCommands = FakeCommands(plan)
        val failed = ReaderTtsPlaybackCoordinator(failingCommands, failingEngine)
        failed.start(plan)
        failingEngine.emitReady(false)
        assertEquals(ReaderTtsPlaybackStatus.FAILED, failed.state.status)
        assertTrue(failingEngine.spoken.isEmpty())
    }

    @Test
    fun failedCallbackUsesCoreFailureActionToAdvance() {
        val commands = FakeCommands(plan, failureAction = ReaderTtsFailureAction.SKIP)
        val engine = FakeEngine(ready = true)
        val coordinator = ReaderTtsPlaybackCoordinator(commands, engine)
        coordinator.start(plan)
        val first = engine.spoken.single()
        engine.emitError(first)
        assertEquals(2, engine.spoken.size)
        engine.emitStart(engine.spoken[1])
        assertEquals(ReaderTtsPlaybackStatus.PLAYING, coordinator.state.status)
    }

    private class FakeEngine(override var ready: Boolean) : ReaderTtsPlaybackEngine {
        private lateinit var listener: ReaderTtsPlaybackEngine.Listener
        val spoken = mutableListOf<String>()
        var stopped = 0
        var shutdowns = 0
        override fun setListener(listener: ReaderTtsPlaybackEngine.Listener) { this.listener = listener }
        override fun initialize() {}
        override fun speak(utteranceId: String, text: String) { spoken += utteranceId }
        override fun setRate(rate: Int) {}
        override fun stop() { stopped++ }
        override fun shutdown() { shutdowns++ }
        fun emitReady(success: Boolean) { ready = success; listener.onReady(success) }
        fun emitStart(id: String) { listener.onStart(id) }
        fun emitDone(id: String) { listener.onDone(id) }
        fun emitError(id: String) { listener.onError(id) }
    }

    private class FakeCommands(
        private val plan: ReaderTtsSlicePlan,
        private val failureAction: ReaderTtsFailureAction = ReaderTtsFailureAction.STOP,
    ) : ReaderTtsPlaybackCommands {
        data class Report(val status: ReaderTtsSliceStatus, val callbackId: String)
        val reported = mutableListOf<Report>()
        private var index = 0
        private var stopped = false
        override fun queuePlay(plan: ReaderTtsSlicePlan, startSliceIndex: Int): ReaderTtsQueueSnapshot {
            index = startSliceIndex; stopped = false; return snapshot(ReaderTtsQueueState.PLAYING)
        }
        override fun queuePause(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot = snapshot(ReaderTtsQueueState.PAUSED)
        override fun queueResume(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot = snapshot(ReaderTtsQueueState.PLAYING)
        override fun queueStop(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot { stopped = true; return snapshot(ReaderTtsQueueState.STOPPED) }
        override fun queueSetRate(chapter: ReaderTtsChapterRef, rate: Int) = ReaderTtsQueueSetRateResult(snapshot(ReaderTtsQueueState.PLAYING), rate)
        override fun queueReportCallback(chapter: ReaderTtsChapterRef, sliceIndex: Int, status: ReaderTtsSliceStatus,
            callbackId: String, failurePolicy: ReaderTtsFailurePolicy, failureLimit: Int): ReaderTtsQueueCallbackResult {
            reported += Report(status, callbackId)
            if (status == ReaderTtsSliceStatus.DONE || (status == ReaderTtsSliceStatus.FAILED && failureAction == ReaderTtsFailureAction.SKIP)) index++
            val state = if (index >= plan.slices.size) ReaderTtsQueueState.COMPLETED else if (stopped) ReaderTtsQueueState.STOPPED else ReaderTtsQueueState.PLAYING
            return ReaderTtsQueueCallbackResult(snapshot(state), ReaderTtsCallbackDisposition.APPLIED,
                if (status == ReaderTtsSliceStatus.FAILED) failureAction else null)
        }
        private fun snapshot(state: ReaderTtsQueueState): ReaderTtsQueueSnapshot = ReaderTtsQueueSnapshot(
            plan.chapter, state, if (state == ReaderTtsQueueState.COMPLETED) null else index,
            plan.slices.size, if (state == ReaderTtsQueueState.COMPLETED) plan.slices.size else index,
            emptyList(),
        )
    }
}
