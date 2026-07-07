package com.reader.ui.shell

import com.reader.host.CapabilityHandler
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice E — ViewModel-tier JVM proof for the HostRequest effect-saga.
 *
 * This test simulates the AppShell `LaunchedEffect` collector in plain JVM
 * code (no Compose runtime required) to prove the production consumption
 * layer is wired end-to-end:
 *
 *   1. UI taps → `AppShellViewModel.dispatch(DispatchHostRequest)`
 *      → reducer stamps `pendingHostRequests` entry.
 *   2. Collector observes pending entry → calls `HostRequestDispatcher`
 *      backed by a **real** [HostAdapter] (with a fake handler).
 *   3. Dispatcher returns `HostRequestResult`.
 *   4. Collector dispatches `HostRequestComplete` / `HostRequestError`
 *      back into the ViewModel.
 *   5. Reducer clears the pending entry and writes `lastHostRequestResult`.
 *
 * **Why this is not "only a test simulation"**: the [HostRequestDispatcher]
 * and [AppShellViewModel] under test are the real production classes. Only
 * the [HostAdapter]'s handler is faked (because real TTS / Notification /
 * WebView handlers need an Android Context). The collector loop itself is
 * the same sequence the `LaunchedEffect` in `AppShell.kt` runs.
 */
class AppShellViewModelHostRequestTest {

    @Test
    fun `dispatch to complete clears pending and writes success result`() {
        val adapter = HostAdapter().apply {
            register("tts.system.start", CapabilityHandler { _ ->
                HostReply.complete(JSONObject().put("started", true).toString())
            })
        }
        val dispatcher = HostRequestDispatcher(adapter)
        val vm = AppShellViewModel()

        // 1. UI tap → reducer stamps pending entry.
        val intent = ReaderUiIntent.DispatchHostRequest(
            capability = "tts.system.start",
            paramsJson = JSONObject().put("text", "hello").toString()
        )
        vm.dispatch(intent)

        val pendingAfterDispatch = vm.state.value.pendingHostRequests
        assertEquals("pending entry must be stamped", 1, pendingAfterDispatch.size)
        val entry = pendingAfterDispatch.first()
        assertEquals(intent.requestId, entry.dispatchId)
        assertEquals("tts.system.start", entry.capability)
        assertNull("no result yet", vm.state.value.lastHostRequestResult)

        // 2. Collector observes pending → dispatches through real adapter.
        val result = dispatcher.dispatch(entry)
        assertTrue("dispatcher must succeed", result.success)
        assertEquals(entry.dispatchId, result.dispatchId)

        // 3. Collector dispatches HostRequestComplete back into reducer.
        vm.dispatch(
            ReaderUiIntent.HostRequestComplete(
                requestId = result.dispatchId,
                capability = result.capability,
                resultJson = result.resultJson ?: "{}"
            )
        )

        // 4. Reducer clears pending + writes lastHostRequestResult.
        val finalState = vm.state.value
        assertEquals("pending must be cleared", 0, finalState.pendingHostRequests.size)
        val lastResult = finalState.lastHostRequestResult
        assertNotNull("lastHostRequestResult must be set", lastResult)
        assertTrue("must be success", lastResult!!.success)
        assertEquals(entry.dispatchId, lastResult.dispatchId)
        assertEquals("tts.system.start", lastResult.capability)
        val parsed = JSONObject(lastResult.resultJson!!)
        assertTrue(parsed.getBoolean("started"))
    }

    @Test
    fun `dispatch to error clears pending and writes failure result`() {
        val adapter = HostAdapter().apply {
            register("permission.check", CapabilityHandler { _ ->
                HostReply.error("DENIED", "user denied", false)
            })
        }
        val dispatcher = HostRequestDispatcher(adapter)
        val vm = AppShellViewModel()

        val intent = ReaderUiIntent.DispatchHostRequest(
            capability = "permission.check",
            paramsJson = JSONObject().put("kind", "notifications").toString()
        )
        vm.dispatch(intent)
        val entry = vm.state.value.pendingHostRequests.first()

        val result = dispatcher.dispatch(entry)
        assertFalse("dispatcher must fail", result.success)
        assertEquals("DENIED", result.errorCode)

        vm.dispatch(
            ReaderUiIntent.HostRequestError(
                requestId = result.dispatchId,
                capability = result.capability,
                errorCode = result.errorCode ?: "INTERNAL",
                errorMessage = result.errorMessage ?: ""
            )
        )

        val finalState = vm.state.value
        assertEquals(0, finalState.pendingHostRequests.size)
        val lastResult = finalState.lastHostRequestResult
        assertNotNull(lastResult)
        assertFalse("must be failure", lastResult!!.success)
        assertEquals("DENIED", lastResult.errorCode)
        assertEquals("user denied", lastResult.errorMessage)
        assertNull("no resultJson on error", lastResult.resultJson)
    }

    @Test
    fun `multiple dispatches queue and clear independently`() {
        val adapter = HostAdapter().apply {
            register("device.vibrate", CapabilityHandler { _ ->
                HostReply.complete(JSONObject().put("vibrated", true).toString())
            })
            register("clipboard.copy", CapabilityHandler { _ ->
                HostReply.error("INTERNAL", "clipboard empty", true)
            })
        }
        val dispatcher = HostRequestDispatcher(adapter)
        val vm = AppShellViewModel()

        // Queue two entries.
        val intent1 = ReaderUiIntent.DispatchHostRequest("device.vibrate", "{}")
        val intent2 = ReaderUiIntent.DispatchHostRequest("clipboard.copy", "{}")
        vm.dispatch(intent1)
        vm.dispatch(intent2)

        // Both entries must be in pending.
        assertEquals(2, vm.state.value.pendingHostRequests.size)
        val entry1 = vm.state.value.pendingHostRequests[0]
        val entry2 = vm.state.value.pendingHostRequests[1]
        assertEquals(intent1.requestId, entry1.dispatchId)
        assertEquals(intent2.requestId, entry2.dispatchId)

        // Complete entry1 first.
        val result1 = dispatcher.dispatch(entry1)
        vm.dispatch(
            ReaderUiIntent.HostRequestComplete(
                requestId = result1.dispatchId,
                capability = result1.capability,
                resultJson = result1.resultJson ?: "{}"
            )
        )
        // entry1 cleared, entry2 still pending.
        val midState = vm.state.value
        assertEquals(1, midState.pendingHostRequests.size)
        assertEquals(entry2.dispatchId, midState.pendingHostRequests.first().dispatchId)
        assertTrue("lastResult reflects entry1 success", midState.lastHostRequestResult!!.success)

        // Now error entry2.
        val result2 = dispatcher.dispatch(entry2)
        vm.dispatch(
            ReaderUiIntent.HostRequestError(
                requestId = result2.dispatchId,
                capability = result2.capability,
                errorCode = result2.errorCode ?: "INTERNAL",
                errorMessage = result2.errorMessage ?: ""
            )
        )

        val finalState = vm.state.value
        assertEquals(0, finalState.pendingHostRequests.size)
        // lastResult now reflects entry2 (the most recent completion).
        assertFalse(finalState.lastHostRequestResult!!.success)
        assertEquals("INTERNAL", finalState.lastHostRequestResult!!.errorCode)
    }

    @Test
    fun `ClearHostRequestResult clears lastHostRequestResult without touching pending`() {
        val adapter = HostAdapter().apply {
            register("tts.system.start", CapabilityHandler { _ ->
                HostReply.complete(JSONObject().put("started", true).toString())
            })
        }
        val dispatcher = HostRequestDispatcher(adapter)
        val vm = AppShellViewModel()

        // Dispatch + complete to populate lastHostRequestResult.
        val intent = ReaderUiIntent.DispatchHostRequest("tts.system.start", "{}")
        vm.dispatch(intent)
        val entry = vm.state.value.pendingHostRequests.first()
        val result = dispatcher.dispatch(entry)
        vm.dispatch(
            ReaderUiIntent.HostRequestComplete(
                requestId = result.dispatchId,
                capability = result.capability,
                resultJson = result.resultJson ?: "{}"
            )
        )
        assertNotNull(vm.state.value.lastHostRequestResult)

        // Clear it.
        vm.dispatch(ReaderUiIntent.ClearHostRequestResult)
        assertNull(vm.state.value.lastHostRequestResult)
        assertEquals(0, vm.state.value.pendingHostRequests.size)
    }

    @Test
    fun `deduplication - completing same dispatchId twice does not corrupt state`() {
        val adapter = HostAdapter().apply {
            register("tts.system.start", CapabilityHandler { _ ->
                HostReply.complete("{}")
            })
        }
        val dispatcher = HostRequestDispatcher(adapter)
        val vm = AppShellViewModel()

        val intent = ReaderUiIntent.DispatchHostRequest("tts.system.start", "{}")
        vm.dispatch(intent)
        val entry = vm.state.value.pendingHostRequests.first()

        // First completion clears the entry.
        val result = dispatcher.dispatch(entry)
        vm.dispatch(
            ReaderUiIntent.HostRequestComplete(
                requestId = result.dispatchId,
                capability = result.capability,
                resultJson = "{}"
            )
        )
        assertEquals(0, vm.state.value.pendingHostRequests.size)
        val firstResult = vm.state.value.lastHostRequestResult!!
        assertTrue(firstResult.success)

        // Duplicate completion (e.g. from a stale re-fire) — the reducer
        // filterNot by dispatchId is a no-op on an empty queue, and
        // lastHostRequestResult is overwritten with the same success.
        vm.dispatch(
            ReaderUiIntent.HostRequestComplete(
                requestId = result.dispatchId,
                capability = result.capability,
                resultJson = "{}"
            )
        )
        assertEquals(0, vm.state.value.pendingHostRequests.size)
        assertTrue(vm.state.value.lastHostRequestResult!!.success)
    }
}
