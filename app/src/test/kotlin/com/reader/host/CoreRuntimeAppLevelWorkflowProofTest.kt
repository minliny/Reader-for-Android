package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.AndroidTtsAdapter
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.PermissionRuntimeAdapter
import com.reader.android.data.adapter.TtsInitResult
import com.reader.android.data.adapter.TtsPlaybackState
import com.reader.android.data.adapter.TtsUtterance
import com.reader.android.data.adapter.WebDavCredentialStore
import com.reader.ui.shell.HostRequestDispatch
import com.reader.ui.shell.HostRequestResult
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.ReaderUiReducer
import com.reader.ui.shell.ReaderUiState
import com.reader.ui.shell.PermissionKind
import com.reader.ui.shell.PermissionStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice E — App-level workflow proof (JVM tier).
 *
 * Validates the end-to-end effect-saga chain:
 *
 *   UI tap → ReaderUiIntent.DispatchHostRequest
 *         → ReaderUiReducer → state.pendingHostRequests
 *         → HostAdapter.dispatch (simulated UI layer)
 *         → HostReply (complete / error)
 *         → ReaderUiIntent.HostRequestComplete / HostRequestError
 *         → ReaderUiReducer → state.lastHostRequestResult
 *
 * This is the JVM proof; the instrumented proof (real Core → JNI poll →
 * host.request → handler → host.complete round-trip) requires a device
 * and lives in `AppLevelReadingChainProofTest`.
 *
 * **What is proven**:
 *  - The Reducer's `pendingHostRequests` queue correctly feeds the
 *    HostAdapter (the UI layer's wiring is testable without Android).
 *  - Each host capability (tts / permission / cache / file / etc.)
 *    returns a well-formed reply that the Reducer can fold into
 *    `lastHostRequestResult`.
 *  - Error propagation: a handler `INTERNAL` error surfaces as
 *    `HostRequestResult(success=false)`.
 */
class CoreRuntimeAppLevelWorkflowProofTest {

    /**
     * Fake [WebDavCredentialStore.CredentialKeystore] so the JVM test does
     * not touch AndroidKeystore.
     */
    private class FakeKeystore : WebDavCredentialStore.CredentialKeystore {
        private val secrets = mutableMapOf<String, String>()
        override fun save(identifier: String, secret: String) =
            AndroidEncryptedCredentialRecord(identifier, "fake", secret, "", "").also {
                secrets[identifier] = secret
            }
        override fun load(record: AndroidEncryptedCredentialRecord) = secrets[record.identifier] ?: ""
        override fun revoke(record: AndroidEncryptedCredentialRecord) = secrets.remove(record.identifier) != null
    }

    private fun makeFacade(): HostFacade = HostFacade(
        context = null,
        tts = FakeAndroidTtsAdapter(),
        permission = FakePermissionRuntimeAdapter(
            mapOf(PermissionKind.NOTIFICATIONS to PermissionStatus.GRANTED)
        ),
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(FakeKeystore()),
        downloadCache = null
    )

    /**
     * Simulates the UI layer's effect-saga observer: drains
     * [ReaderUiState.pendingHostRequests] by dispatching each entry through
     * [HostAdapter.dispatch] and returning the corresponding completion /
     * error intents.
     */
    private fun drainPendingHostRequests(
        state: ReaderUiState,
        adapter: HostAdapter
    ): List<ReaderUiIntent> = state.pendingHostRequests.map { dispatch ->
        val opId = (dispatch.dispatchId.hashCode().toLong() and Long.MAX_VALUE) + 1L
        val request = HostRequest(1L, opId, dispatch.capability, dispatch.paramsJson)
        val reply = adapter.dispatch(request)
        if (reply.isComplete()) {
            ReaderUiIntent.HostRequestComplete(
                requestId = dispatch.dispatchId,
                capability = dispatch.capability,
                resultJson = (reply as HostReply.Complete).resultJson()
            )
        } else {
            val err = reply as HostReply.Error
            ReaderUiIntent.HostRequestError(
                requestId = dispatch.dispatchId,
                capability = dispatch.capability,
                errorCode = err.code(),
                errorMessage = err.message()
            )
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Workflow proofs: each exercises a different capability family
    // ───────────────────────────────────────────────────────────────────────

    @Test
    fun `workflow_tts_start_completes_through_reducer_and_adapter`() {
        val facade = makeFacade()
        val adapter = HostAdapter()
        adapter.register(TtsSystemStartHandler.CAPABILITY, TtsSystemStartHandler(facade))

        var state = ReaderUiState()
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "tts.system.start",
            paramsJson = JSONObject().put("text", "proof").toString()
        ))
        assertEquals("pending should have 1 entry", 1, state.pendingHostRequests.size)

        val completions = drainPendingHostRequests(state, adapter)
        assertEquals("should produce 1 completion", 1, completions.size)

        state = completions.fold(state) { s, intent -> ReaderUiReducer.reduce(s, intent) }
        assertTrue("pending should be empty", state.pendingHostRequests.isEmpty())
        assertNotNull(state.lastHostRequestResult)
        assertTrue("result should be success", state.lastHostRequestResult!!.success)
        assertTrue(state.lastHostRequestResult!!.resultJson!!.contains("started"))
    }

    @Test
    fun `workflow_permission_check_completes_with_granted`() {
        val facade = makeFacade()
        val adapter = HostAdapter()
        adapter.register(PermissionCheckHandler.CAPABILITY, PermissionCheckHandler(facade))

        var state = ReaderUiState()
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "permission.check",
            paramsJson = JSONObject().put("scope", "notifications").toString()
        ))

        val completions = drainPendingHostRequests(state, adapter)
        state = completions.fold(state) { s, intent -> ReaderUiReducer.reduce(s, intent) }

        assertTrue(state.lastHostRequestResult!!.success)
        assertTrue(JSONObject(state.lastHostRequestResult!!.resultJson!!).getBoolean("granted"))
    }

    @Test
    fun `workflow_file_write_and_read_round_trips_through_reducer`() {
        val fs = InMemoryHostFileSystem()
        val adapter = HostAdapter()
        adapter.register(FileWriteHandler.CAPABILITY, FileWriteHandler(fs))
        adapter.register(FileReadHandler.CAPABILITY, FileReadHandler(fs))

        var state = ReaderUiState()
        // Write
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "file.write",
            paramsJson = JSONObject().apply {
                put("path", "proof/ch1.txt"); put("content", "workflow proof")
            }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue("write should succeed", state.lastHostRequestResult!!.success)

        // Clear result then read
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.ClearHostRequestResult)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "file.read",
            paramsJson = JSONObject().apply { put("path", "proof/ch1.txt") }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }

        assertTrue("read should succeed", state.lastHostRequestResult!!.success)
        assertTrue(state.lastHostRequestResult!!.resultJson!!.contains("workflow proof"))
    }

    @Test
    fun `workflow_cache_put_and_get_round_trips_through_reducer`() {
        val cache = DefaultHostCache()
        val adapter = HostAdapter()
        adapter.register(CachePutHandler.CAPABILITY, CachePutHandler(cache))
        adapter.register(CacheGetHandler.CAPABILITY, CacheGetHandler(cache))

        var state = ReaderUiState()
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "cache.put",
            paramsJson = JSONObject().apply { put("namespace", "workflow"); put("key", "wf-k"); put("value", "wf-v") }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue(state.lastHostRequestResult!!.success)

        state = ReaderUiReducer.reduce(state, ReaderUiIntent.ClearHostRequestResult)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "cache.get",
            paramsJson = JSONObject().apply { put("namespace", "workflow"); put("key", "wf-k") }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue(state.lastHostRequestResult!!.resultJson!!.contains("wf-v"))
    }

    @Test
    fun `workflow_clipboard_copy_errors_gracefully_when_context_not_wired`() {
        val facade = makeFacade() // context = null
        val adapter = HostAdapter()
        adapter.register(ClipboardCopyHandler.CAPABILITY, ClipboardCopyHandler(facade))

        var state = ReaderUiState()
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "clipboard.copy",
            paramsJson = JSONObject().apply { put("text", "copy") }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }

        assertFalse("should be an error", state.lastHostRequestResult!!.success)
        assertEquals("INTERNAL", state.lastHostRequestResult!!.errorCode)
    }

    @Test
    fun `workflow_time_now_and_log_emit_complete_successfully`() {
        val logger = CapturingHostLogger()
        val adapter = HostAdapter()
        adapter.register(TimeNowHandler.CAPABILITY, TimeNowHandler())
        adapter.register(LogEmitHandler.CAPABILITY, LogEmitHandler(logger))

        var state = ReaderUiState()
        // time.now
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "time.now", paramsJson = "{}"
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue(state.lastHostRequestResult!!.success)
        assertTrue(state.lastHostRequestResult!!.resultJson!!.contains("unixMillis"))

        // log.emit
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.ClearHostRequestResult)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            capability = "log.emit",
            paramsJson = JSONObject().apply { put("level", "info"); put("message", "workflow") }.toString()
        ))
        state = drainPendingHostRequests(state, adapter).fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue(state.lastHostRequestResult!!.success)
        assertEquals(1, logger.entries.size)
    }

    @Test
    fun `workflow_multiple_dispatches_drain_in_order`() {
        val cache = DefaultHostCache()
        val adapter = HostAdapter()
        adapter.register(CachePutHandler.CAPABILITY, CachePutHandler(cache))
        adapter.register(TimeNowHandler.CAPABILITY, TimeNowHandler())
        adapter.register(LogEmitHandler.CAPABILITY, LogEmitHandler(CapturingHostLogger()))

        var state = ReaderUiState()
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest(
            "cache.put",
            JSONObject().apply { put("namespace", "workflow"); put("key", "multi"); put("value", "ok") }.toString()
        ))
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest("time.now", "{}"))
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DispatchHostRequest("log.emit", "{}"))
        assertEquals(3, state.pendingHostRequests.size)

        val completions = drainPendingHostRequests(state, adapter)
        assertEquals(3, completions.size)
        state = completions.fold(state) { s, i -> ReaderUiReducer.reduce(s, i) }
        assertTrue("all should drain", state.pendingHostRequests.isEmpty())
    }
}
