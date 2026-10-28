package io.reader.android.runtime

import org.json.JSONObject

/** Synchronous facade. Native dispatch/poll/ACK belong exclusively to the Application event actor. */
internal interface CoreTransport : AutoCloseable {
    val session: String
    fun request(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 30_000,
        beforeSend: (Long) -> Unit = {},
        terminal: ((JSONObject) -> Unit)? = null,
    ): JSONObject
    /** Drain the exact existing actor route only; never sends a new business command or polls Native. */
    fun drainOriginalTerminal(requestId: Long, timeoutMs: Long = 1_000)
}

class CoreCommandException(val method: String, val error: JSONObject) : Exception(
    "${error.optString("code", "INTERNAL")}: ${error.optString("message")}",
)

class PendingOperationException(val operationId: String, val stage: String) : Exception(
    "OPERATION_PENDING: $operationId/$stage",
)

class CoreReadTimeoutException(val method: String) : Exception("READ_TIMEOUT: $method")

/** Decode only the supplied original terminal; never performs I/O or infers an earlier write's outcome. */
internal object CoreTerminal {
    fun result(method: String, event: JSONObject): JSONObject {
        if (event.getString("type") == "error") throw CoreCommandException(method, event.getJSONObject("error"))
        check(event.getString("type") == "result") { "EXPECTED_TERMINAL" }
        return event.getJSONObject("data")
    }
}
