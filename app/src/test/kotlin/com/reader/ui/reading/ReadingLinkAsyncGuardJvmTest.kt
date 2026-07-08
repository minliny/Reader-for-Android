package com.reader.ui.reading

import com.reader.ui.shell.AsyncResultStateValue
import com.reader.ui.shell.ReaderContext
import com.reader.ui.shell.ReaderEntry
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.ReaderUiReducer
import com.reader.ui.shell.ReaderUiState
import com.reader.ui.shell.RouteIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6 JVM proof — validates the reading-link closure between
 * [ImmersiveReadingViewModel]'s `onAsyncStateChange` callback and the
 * async-result guard (M5) in [ReaderUiReducer].
 *
 * The VM reports PENDING / COMPLETED / CANCELLED using
 * [ReaderContext.entryRequestId]; the AppShell wiring translates those
 * callbacks into [ReaderUiIntent.StartAsyncRequest] /
 * [CompleteAsyncRequest] / [CancelAsyncRequest]. The reducer's guard
 * ensures stale results (from a superseded entry) are DISCARDED rather
 * than overwriting the current surface.
 *
 * This test runs on the JVM using the `fixture://` source path, which
 * short-circuits `BookApi` / `ReaderCoreClient` and fires the callback
 * synchronously — no device or native Core required.
 */
class ReadingLinkAsyncGuardJvmTest {

    /**
     * VM fixture path fires PENDING → COMPLETED via the callback, carrying
     * the entryRequestId so the reducer can correlate the async request.
     */
    @Test
    fun `fixture entry fires pending then completed with entryRequestId`() {
        val ctx = fixtureContext(entryRequestId = "req-entry-1")
        val calls = mutableListOf<AsyncStateCall>()

        val vm = ImmersiveReadingViewModel(ctx, onAsyncStateChange = { requestId, state, value ->
            calls += AsyncStateCall(requestId, state, value)
        })

        // fixture:// path is synchronous: PENDING fires in init, then COMPLETED after content set.
        assertEquals(2, calls.size)
        assertEquals("req-entry-1", calls[0].requestId)
        assertEquals(AsyncResultStateValue.PENDING, calls[0].state)
        assertEquals(null, calls[0].value)
        assertEquals("req-entry-1", calls[1].requestId)
        assertEquals(AsyncResultStateValue.COMPLETED, calls[1].state)
        assertNotNull("completed value should be the chapter text", calls[1].value)
        assertTrue("content should be non-blank fixture text", (calls[1].value as String).isNotBlank())
        // VM content StateFlow should match the value reported to the callback.
        assertEquals(calls[1].value, vm.content.value)
    }

    /**
     * End-to-end closure: translating the VM's callback sequence into
     * reducer intents drives `ReaderUiState.asyncResult` from IDLE →
     * PENDING → COMPLETED with the chapter content as value.
     */
    @Test
    fun `callback sequence drives asyncResult idle to pending to completed`() {
        val ctx = fixtureContext(entryRequestId = "req-entry-2")
        val calls = mutableListOf<AsyncStateCall>()
        ImmersiveReadingViewModel(ctx, onAsyncStateChange = { requestId, state, value ->
            calls += AsyncStateCall(requestId, state, value)
        })

        // Start from a fresh state (simulating AppShell after EnterReaderFromAction).
        var state = ReaderUiState()
        assertEquals(AsyncResultStateValue.IDLE, state.asyncResult.state)

        // Replay the callback sequence through the reducer, mirroring AppShell's wiring.
        for (call in calls) {
            state = when (call.state) {
                AsyncResultStateValue.PENDING -> ReaderUiReducer.reduce(
                    state,
                    ReaderUiIntent.StartAsyncRequest(
                        fromRoute = RouteIds.IMMERSIVE_READING,
                        toRoute = RouteIds.IMMERSIVE_READING,
                        requestId = call.requestId
                    )
                )
                AsyncResultStateValue.COMPLETED -> ReaderUiReducer.reduce(
                    state,
                    ReaderUiIntent.CompleteAsyncRequest(
                        requestId = call.requestId,
                        value = call.value,
                        currentRoute = RouteIds.IMMERSIVE_READING
                    )
                )
                AsyncResultStateValue.CANCELLED -> ReaderUiReducer.reduce(
                    state,
                    ReaderUiIntent.CancelAsyncRequest(requestId = call.requestId)
                )
                else -> state
            }
        }

        assertEquals(AsyncResultStateValue.COMPLETED, state.asyncResult.state)
        assertEquals("req-entry-2", state.asyncResult.requestId)
        assertEquals(calls[1].value, state.asyncResult.value)
    }

    /**
     * Stale-result guard: entry A starts (PENDING), then entry B starts
     * (PENDING, supersedes A). When entry A's COMPLETED arrives late, the
     * reducer marks it DISCARDED — entry B's request is still current.
     */
    @Test
    fun `stale complete from earlier entry is discarded after newer entry starts`() {
        val reqA = "req-entry-a"
        val reqB = "req-entry-b"

        var state = ReaderUiState()

        // Entry A starts loading.
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = reqA
            )
        )
        assertEquals(AsyncResultStateValue.PENDING, state.asyncResult.state)
        assertEquals(reqA, state.asyncResult.requestId)

        // Entry B supersedes A before A completes.
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = reqB
            )
        )
        assertEquals(AsyncResultStateValue.PENDING, state.asyncResult.state)
        assertEquals(reqB, state.asyncResult.requestId)

        // Entry A's late COMPLETED arrives — should be DISCARDED, not COMPLETED.
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CompleteAsyncRequest(
                requestId = reqA,
                value = "stale-content-from-A",
                currentRoute = RouteIds.IMMERSIVE_READING
            )
        )
        assertEquals(AsyncResultStateValue.DISCARDED, state.asyncResult.state)
        // requestId stays as the current (B) request, not the stale (A) one.
        assertEquals(reqB, state.asyncResult.requestId)
    }

    /**
     * Cancel closure: a PENDING request followed by CancelAsyncRequest
     * (mirroring the VM's error / empty-chapters path) sets CANCELLED.
     */
    @Test
    fun `cancel after pending sets cancelled state`() {
        val req = "req-entry-cancel"
        var state = ReaderUiState()

        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = RouteIds.IMMERSIVE_READING,
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = req
            )
        )
        assertEquals(AsyncResultStateValue.PENDING, state.asyncResult.state)

        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CancelAsyncRequest(requestId = req)
        )
        assertEquals(AsyncResultStateValue.CANCELLED, state.asyncResult.state)
        assertEquals(req, state.asyncResult.requestId)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private data class AsyncStateCall(
        val requestId: String,
        val state: AsyncResultStateValue,
        val value: Any?
    )

    private fun fixtureContext(entryRequestId: String): ReaderContext = ReaderContext(
        sourceId = "fixture://demo",
        bookUrl = "fixture://demo/雨夜",
        bookName = "雨夜",
        entry = ReaderEntry.ACTION_TO_IMMERSIVE,
        entryRequestId = entryRequestId
    )
}
