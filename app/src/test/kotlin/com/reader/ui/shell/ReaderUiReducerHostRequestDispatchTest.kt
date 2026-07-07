package com.reader.ui.shell

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the Slice D Reducer HostRequest dispatch mechanism.
 *
 * Verifies the effect-saga pattern:
 *  1. `DispatchHostRequest` stamps a [HostRequestDispatch] into
 *     [ReaderUiState.pendingHostRequests].
 *  2. `HostRequestComplete` removes the entry and writes
 *     [HostRequestResult] with `success=true`.
 *  3. `HostRequestError` removes the entry and writes
 *     [HostRequestResult] with `success=false`.
 *  4. `ClearHostRequestResult` clears the last result.
 *  5. Multiple dispatches queue in order; completions remove by id.
 */
class ReaderUiReducerHostRequestDispatchTest {

    @Test
    fun `DispatchHostRequest adds entry to pendingHostRequests`() {
        val state = ReaderUiState()
        val intent = ReaderUiIntent.DispatchHostRequest(
            capability = "tts.system.start",
            paramsJson = JSONObject().put("text", "hello").toString()
        )
        val newState = ReaderUiReducer.reduce(state, intent)
        assertEquals(1, newState.pendingHostRequests.size)
        val dispatch = newState.pendingHostRequests[0]
        assertEquals(intent.requestId, dispatch.dispatchId)
        assertEquals("tts.system.start", dispatch.capability)
        assertTrue(dispatch.paramsJson.contains("hello"))
    }

    @Test
    fun `HostRequestComplete removes entry and writes success result`() {
        val dispatchIntent = ReaderUiIntent.DispatchHostRequest(
            capability = "permission.check",
            paramsJson = JSONObject().put("kind", "notifications").toString()
        )
        val state = ReaderUiReducer.reduce(ReaderUiState(), dispatchIntent)
        assertEquals(1, state.pendingHostRequests.size)

        val completeIntent = ReaderUiIntent.HostRequestComplete(
            requestId = dispatchIntent.requestId,
            capability = "permission.check",
            resultJson = JSONObject().put("status", "GRANTED").toString()
        )
        val newState = ReaderUiReducer.reduce(state, completeIntent)
        assertTrue("pending should be empty", newState.pendingHostRequests.isEmpty())
        assertNotNull(newState.lastHostRequestResult)
        assertTrue(newState.lastHostRequestResult!!.success)
        assertEquals("permission.check", newState.lastHostRequestResult!!.capability)
        assertTrue(newState.lastHostRequestResult!!.resultJson!!.contains("GRANTED"))
    }

    @Test
    fun `HostRequestError removes entry and writes failure result`() {
        val dispatchIntent = ReaderUiIntent.DispatchHostRequest(
            capability = "clipboard.copy",
            paramsJson = JSONObject().put("text", "copy me").toString()
        )
        val state = ReaderUiReducer.reduce(ReaderUiState(), dispatchIntent)

        val errorIntent = ReaderUiIntent.HostRequestError(
            requestId = dispatchIntent.requestId,
            capability = "clipboard.copy",
            errorCode = "INTERNAL",
            errorMessage = "context not wired"
        )
        val newState = ReaderUiReducer.reduce(state, errorIntent)
        assertTrue("pending should be empty", newState.pendingHostRequests.isEmpty())
        assertNotNull(newState.lastHostRequestResult)
        assertFalse(newState.lastHostRequestResult!!.success)
        assertEquals("INTERNAL", newState.lastHostRequestResult!!.errorCode)
        assertTrue(newState.lastHostRequestResult!!.errorMessage!!.contains("context"))
    }

    @Test
    fun `ClearHostRequestResult nulls lastHostRequestResult`() {
        val dispatchIntent = ReaderUiIntent.DispatchHostRequest(
            capability = "device.vibrate",
            paramsJson = "{}"
        )
        var state = ReaderUiReducer.reduce(ReaderUiState(), dispatchIntent)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.HostRequestComplete(
            requestId = dispatchIntent.requestId,
            capability = "device.vibrate",
            resultJson = "{}"
        ))
        assertNotNull(state.lastHostRequestResult)

        state = ReaderUiReducer.reduce(state, ReaderUiIntent.ClearHostRequestResult)
        assertNull(state.lastHostRequestResult)
    }

    @Test
    fun `multiple dispatches queue in order`() {
        val state = ReaderUiState()
        val i1 = ReaderUiIntent.DispatchHostRequest("tts.system.start", "{}")
        val i2 = ReaderUiIntent.DispatchHostRequest("tts.system.stop", "{}")
        val i3 = ReaderUiIntent.DispatchHostRequest("tts.system.pause", "{}")
        var s = ReaderUiReducer.reduce(state, i1)
        s = ReaderUiReducer.reduce(s, i2)
        s = ReaderUiReducer.reduce(s, i3)
        assertEquals(3, s.pendingHostRequests.size)
        assertEquals("tts.system.start", s.pendingHostRequests[0].capability)
        assertEquals("tts.system.stop", s.pendingHostRequests[1].capability)
        assertEquals("tts.system.pause", s.pendingHostRequests[2].capability)
    }

    @Test
    fun `completion removes only the matching entry`() {
        val i1 = ReaderUiIntent.DispatchHostRequest("tts.system.start", "{}")
        val i2 = ReaderUiIntent.DispatchHostRequest("permission.check", "{}")
        var s = ReaderUiReducer.reduce(ReaderUiState(), i1)
        s = ReaderUiReducer.reduce(s, i2)
        assertEquals(2, s.pendingHostRequests.size)

        s = ReaderUiReducer.reduce(s, ReaderUiIntent.HostRequestComplete(
            requestId = i1.requestId,
            capability = "tts.system.start",
            resultJson = "{}"
        ))
        assertEquals(1, s.pendingHostRequests.size)
        assertEquals("permission.check", s.pendingHostRequests[0].capability)
    }
}
