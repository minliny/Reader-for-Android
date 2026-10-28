package io.reader.android.runtime

import com.reader.core.ReaderCoreRuntime
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** The single poll owner for this runtime. Calls run on ReaderLibrary's IO dispatcher. */
internal interface CoreTransport : AutoCloseable {
    val session: String
    fun request(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 30_000,
        beforeSend: (Long) -> Unit = {},
        terminal: ((JSONObject) -> Unit)? = null,
    ): JSONObject
    /** Poll only existing work; this never sends a new business command. */
    fun drainOriginalTerminal(requestId: Long, timeoutMs: Long = 1_000)
}

class CoreCommandException(val method: String, val error: JSONObject) : Exception(
    "${error.optString("code", "INTERNAL")}: ${error.optString("message")}",
)

class PendingOperationException(val operationId: String, val stage: String) : Exception(
    "OPERATION_PENDING: $operationId/$stage",
)

class CoreReadTimeoutException(val method: String) : Exception("READ_TIMEOUT: $method")

internal class NativeCoreTransport(private val data: File, private val cache: File) : CoreTransport {
    override val session: String = UUID.randomUUID().toString()
    private var core: ReaderCoreRuntime? = null
    private var closed = false
    private var nextId = 1_000L
    private val originalTerminals = mutableMapOf<Long, (JSONObject) -> Unit>()

    @Synchronized
    override fun request(
        method: String,
        params: JSONObject,
        timeoutMs: Long,
        beforeSend: (Long) -> Unit,
        terminal: ((JSONObject) -> Unit)?,
    ): JSONObject {
        check(!closed) { "RUNTIME_CLOSED" }
        val runtime = core ?: run {
            check(data.mkdirs() || data.isDirectory)
            check(cache.mkdirs() || cache.isDirectory)
            ReaderCoreRuntime(JSONObject().put("dataDirectory", data.absolutePath)
                .put("cacheDirectory", cache.absolutePath).toString()).also { core = it }
        }
        val id = nextId++
        // The caller's immutable intent is durable before JNI can admit it.
        beforeSend(id)
        if (terminal != null) originalTerminals[id] = terminal
        runtime.sendCommand(method, id, params.toString())
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val wire = runtime.pollEvent(20, TimeUnit.MILLISECONDS) ?: continue
            val event = JSONObject(wire)
            check(event.getInt("protocolVersion") == 1) { "UNSUPPORTED_EVENT_PROTOCOL" }
            val eventId = event.getLong("requestId")
            when (event.getString("type")) {
                "host.request" -> {
                    // A1 is local-only. No no-op handler can report a false success.
                    val operationId = event.getLong("operationId")
                    runtime.sendHostError(operationId, "INTERNAL",
                        "Android capability is not available in this product slice", false, nextId++)
                }
                "result", "error" -> {
                    // Keep timed-out original callbacks alive so a later command can
                    // durably consume their exact ACK; never substitute a fresh read.
                    originalTerminals[eventId]?.let { callback ->
                        callback(event)
                        originalTerminals.remove(eventId)
                    }
                    if (eventId == id) return result(method, event)
                }
            }
        }
        // Mutating callers keep the journal and original callback. Do not cancel
        // or resend a potentially admitted write just to manufacture a result.
        if (terminal == null) throw CoreReadTimeoutException(method)
        throw PendingOperationException("$session:$id", method)
    }

    @Synchronized
    override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) {
        check(!closed) { "RUNTIME_CLOSED" }
        require(timeoutMs in 0..1_000)
        val runtime = core ?: return
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        do {
            val wire = runtime.pollEvent(20, TimeUnit.MILLISECONDS) ?: continue
            val event = JSONObject(wire)
            check(event.getInt("protocolVersion") == 1) { "UNSUPPORTED_EVENT_PROTOCOL" }
            val eventId = event.getLong("requestId")
            when (event.getString("type")) {
                "host.request" -> runtime.sendHostError(event.getLong("operationId"), "INTERNAL",
                    "Android capability is not available in this product slice", false, nextId++)
                "result", "error" -> {
                    originalTerminals[eventId]?.let { callback ->
                        callback(event)
                        originalTerminals.remove(eventId)
                    }
                    if (eventId == requestId) return
                }
            }
        } while (System.nanoTime() < deadline)
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        core?.close()
        core = null
        originalTerminals.clear()
    }

    companion object {
        fun result(method: String, event: JSONObject): JSONObject {
            if (event.getString("type") == "error") {
                throw CoreCommandException(method, event.getJSONObject("error"))
            }
            check(event.getString("type") == "result") { "EXPECTED_TERMINAL" }
            return event.getJSONObject("data")
        }
    }
}
