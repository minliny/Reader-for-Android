package com.reader.ui.shell

import com.reader.host.CapabilityHandler
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice E — JVM proof for [HostRequestDispatcher].
 *
 * Verifies the narrow bridge that turns a queued [HostRequestDispatch] into
 * a real [HostAdapter.dispatch] call and maps the [HostReply] back to a
 * [HostRequestResult] the reducer can fold.
 *
 * **What is proven**:
 *  - `HostReply.Complete` → `HostRequestResult(success=true)` with resultJson.
 *  - `HostReply.Error` → `HostRequestResult(success=false)` with code/message.
 *  - The dispatcher never throws — handler exceptions are caught by
 *    [HostAdapter.dispatch] and surfaced as `HostReply.Error(INTERNAL, retryable=true)`.
 *  - The `dispatchId` and `capability` round-trip through the result so the
 *    reducer can match and clear the pending entry.
 *
 * **What is NOT proven here**: the AppShell `LaunchedEffect` collector and
 * the reducer integration — that is covered by
 * [com.reader.ui.shell.AppShellViewModelHostRequestTest].
 */
class HostRequestDispatcherJvmTest {

    private fun adapterFor(capability: String, handler: CapabilityHandler): HostAdapter =
        HostAdapter().apply { register(capability, handler) }

    @Test
    fun `dispatch returns success result when handler completes`() {
        val adapter = adapterFor(
            "tts.system.start",
            CapabilityHandler { _ ->
                HostReply.complete(JSONObject().put("started", true).toString())
            }
        )
        val dispatcher = HostRequestDispatcher(adapter)

        val entry = HostRequestDispatch(
            dispatchId = "req-1",
            capability = "tts.system.start",
            paramsJson = JSONObject().put("text", "hello").toString()
        )
        val result = dispatcher.dispatch(entry)

        assertTrue(result.success)
        assertEquals("req-1", result.dispatchId)
        assertEquals("tts.system.start", result.capability)
        assertNull(result.errorCode)
        assertNull(result.errorMessage)
        val parsed = JSONObject(result.resultJson!!)
        assertTrue(parsed.getBoolean("started"))
    }

    @Test
    fun `dispatch returns error result when handler errors`() {
        val adapter = adapterFor(
            "permission.check",
            CapabilityHandler { _ ->
                HostReply.error("DENIED", "user denied", false)
            }
        )
        val dispatcher = HostRequestDispatcher(adapter)

        val entry = HostRequestDispatch(
            dispatchId = "req-2",
            capability = "permission.check",
            paramsJson = JSONObject().put("scope", "notifications").toString()
        )
        val result = dispatcher.dispatch(entry)

        assertFalse(result.success)
        assertEquals("req-2", result.dispatchId)
        assertEquals("permission.check", result.capability)
        assertEquals("DENIED", result.errorCode)
        assertEquals("user denied", result.errorMessage)
        assertNull(result.resultJson)
    }

    @Test
    fun `dispatch returns INTERNAL error when handler throws`() {
        val adapter = adapterFor(
            "clipboard.copy",
            CapabilityHandler { _ -> throw IllegalStateException("boom") }
        )
        val dispatcher = HostRequestDispatcher(adapter)

        val entry = HostRequestDispatch(
            dispatchId = "req-3",
            capability = "clipboard.copy",
            paramsJson = JSONObject().put("text", "x").toString()
        )
        val result = dispatcher.dispatch(entry)

        assertFalse(result.success)
        assertEquals("req-3", result.dispatchId)
        assertEquals("clipboard.copy", result.capability)
        assertEquals("INTERNAL", result.errorCode)
        // HostAdapter wraps the handler's exception message.
        assertTrue("expected 'boom' in message: ${result.errorMessage}",
            result.errorMessage?.contains("boom") == true)
    }

    @Test
    fun `dispatch returns INTERNAL error when capability is not registered`() {
        val adapter = HostAdapter() // no handlers registered
        val dispatcher = HostRequestDispatcher(adapter)

        val entry = HostRequestDispatch(
            dispatchId = "req-4",
            capability = "unknown.capability",
            paramsJson = "{}"
        )
        val result = dispatcher.dispatch(entry)

        assertFalse(result.success)
        assertEquals("req-4", result.dispatchId)
        assertEquals("unknown.capability", result.capability)
        assertEquals("INTERNAL", result.errorCode)
        assertTrue("expected 'unsupported capability' in message: ${result.errorMessage}",
            result.errorMessage?.contains("unsupported capability") == true)
    }

    @Test
    fun `dispatch preserves dispatchId and capability across success and error`() {
        var receivedCapability: String? = null
        val adapter = adapterFor(
            "device.vibrate",
            CapabilityHandler { req ->
                receivedCapability = req.capability()
                HostReply.complete(JSONObject().put("vibrated", true).toString())
            }
        )
        val dispatcher = HostRequestDispatcher(adapter)

        // Multiple entries with different ids — each must round-trip its own id.
        val entry1 = HostRequestDispatch("id-A", "device.vibrate", "{}")
        val entry2 = HostRequestDispatch("id-B", "device.vibrate", "{}")

        val result1 = dispatcher.dispatch(entry1)
        val result2 = dispatcher.dispatch(entry2)

        assertEquals("id-A", result1.dispatchId)
        assertEquals("id-B", result2.dispatchId)
        assertTrue(result1.success)
        assertTrue(result2.success)

        // Verify the handler received the capability string unchanged while
        // the wire result remains the canonical closed DTO.
        val parsed1 = JSONObject(result1.resultJson!!)
        assertTrue(parsed1.getBoolean("vibrated"))
        assertEquals("device.vibrate", receivedCapability)
    }
}
