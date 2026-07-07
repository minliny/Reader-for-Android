package com.reader.host

import okhttp3.Call
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * `http.cancel` capability handler. Core sends `{requestTag}` or
 * `{requestId}`; this handler cancels the matching in-flight OkHttp call
 * (tracked via [HttpCallRegistry]) and returns `host.complete` with
 * `{cancelled: <bool>}`.
 *
 * The host owns the HTTP client and the call lifecycle; Core only knows
 * the logical request id. [HttpCallRegistry] maps the logical id to the
 * OkHttp [Call] so cancellation is O(1).
 */
class HttpCancelHandler(
    private val registry: HttpCallRegistry
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val tag = params.optString("requestTag", "")
            .ifEmpty { params.optString("requestId", "") }
        if (tag.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires requestTag or requestId", false)
        }
        val cancelled = registry.cancel(tag)
        val result = JSONObject()
        result.put("cancelled", cancelled)
        result.put("requestTag", tag)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "http.cancel"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * Tracks in-flight OkHttp calls by logical request tag so that
 * [HttpCancelHandler] can cancel them. [OkHttpHostTransport] registers
 * each call here before executing it; [cancel] is O(1).
 *
 * Thread-safe via [ConcurrentHashMap].
 */
class HttpCallRegistry {
    private val calls = ConcurrentHashMap<String, Call>()

    /** Register [call] under [tag]. Replaces any existing call for the tag. */
    fun register(tag: String, call: Call) {
        calls[tag] = call
    }

    /** Cancel and remove the call for [tag]. Returns true if a call was cancelled. */
    fun cancel(tag: String): Boolean {
        val call = calls.remove(tag) ?: return false
        return runCatching { call.cancel(); true }.getOrDefault(false)
    }

    /** Remove a completed call from the registry. */
    fun complete(tag: String) {
        calls.remove(tag)
    }

    /** Number of in-flight calls (for diagnostics). */
    fun size(): Int = calls.size
}
