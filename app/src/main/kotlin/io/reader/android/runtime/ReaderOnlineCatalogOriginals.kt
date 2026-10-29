package io.reader.android.runtime

import org.json.JSONObject
import java.util.UUID

/** App gate-confined warm facts. Does not own Native, poll, ACK, journal or replay. */
internal class ReaderOnlineCatalogOriginals(
    private val transport: CoreTransport,
    private val assertHeld: () -> Unit,
    private val beforeNewStage: () -> Unit = {},
) {
    private class Call(val id: String, val method: String, val sourceId: String, val runtime: String, val params: String) {
        var requestId: Long? = null
        var terminal: String? = null
        var transportAcknowledged = false
        fun observation() = ReaderOnlineOriginalObservation(id, method, sourceId, runtime, requestId, params, terminal,
            when {
                terminal == null -> ReaderOnlineOriginalState.NO_TERMINAL
                !transportAcknowledged -> ReaderOnlineOriginalState.TERMINAL_PROTOCOL_UNCONFIRMED
                JSONObject(terminal!!).getString("type") == "result" -> ReaderOnlineOriginalState.ACKNOWLEDGED_RESULT
                else -> ReaderOnlineOriginalState.ACKNOWLEDGED_ERROR
            })
    }
    private val calls = linkedMapOf<String, Call>()
    /** Bounded acknowledged observations remain inspectable after the one-slot
     * current-call route advances to the next online-window request. */
    private val recent = linkedMapOf<String, ReaderOnlineOriginalObservation>()
    fun pending(): List<ReaderOnlineOriginalObservation> {
        assertHeld()
        return calls.values.filter { !it.transportAcknowledged }.map { it.observation() }
    }
    fun observe(id: String): ReaderOnlineOriginalObservation {
        assertHeld()
        return checkNotNull(calls[id]) { "ONLINE_ORIGINAL_NOT_RETAINED" }.observation()
    }
    fun request(method: String, sourceId: String, params: JSONObject): JSONObject {
        assertHeld()
        check(method in setOf("runtime.setHostCapabilities", "book.search", "book.detail") || method in ReaderOnlineReadingPolicy.METHODS) { "ONLINE_METHOD_NOT_ADMITTED" }
        pending().firstOrNull()?.let { throw ReaderOnlineOriginalPending(it) }
        beforeNewStage()
        // Retain all unresolved records; discard only acknowledged observational history before admitting more.
        while (calls.isNotEmpty()) {
            val key = calls.entries.firstOrNull { it.value.transportAcknowledged }?.key ?: error("ONLINE_ORIGINAL_CAPACITY")
            calls.remove(key)
        }
        val call = Call(UUID.randomUUID().toString(), method, sourceId, transport.session, ReaderOnlineCatalogPolicy.json(params))
        calls[call.id] = call
        try {
            transport.request(method, JSONObject(call.params), timeoutMs = 60_000,
                beforeSend = { call.requestId = it }, terminal = { event ->
                    assertHeld()
                    check(event.getLong("requestId") == call.requestId && event.getInt("protocolVersion") == 1 &&
                        event.getString("type") in setOf("result", "error")) { "ONLINE_ORIGINAL_TERMINAL_IDENTITY" }
                    val encoded = ReaderOnlineCatalogPolicy.json(event)
                    check(call.terminal == null || call.terminal == encoded) { "ONLINE_ORIGINAL_TERMINAL_CHANGED" }
                    call.terminal = encoded // Parsed serialization, never called byte-exact native wire.
                })
            call.transportAcknowledged = true
        } catch (error: Exception) {
            // The bridge throws CoreCommandException only AFTER consuming/ACKing that original error.
            if (error is CoreCommandException && call.terminal != null) {
                call.transportAcknowledged = true
                throw error
            }
            if (error.coreProtocolPending() != null) throw error // Preserve typed protocol identity/cause first.
            throw ReaderOnlineOriginalPending(call.observation(), error)
        }
        check(call.runtime == transport.session && call.requestId != null && call.terminal != null) { "ONLINE_ORIGINAL_UNCONFIRMED" }
        recent[method] = call.observation()
        return CoreTerminal.result(method, JSONObject(checkNotNull(call.terminal)))
    }
}
