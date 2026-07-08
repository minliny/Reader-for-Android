package com.reader.host

import org.json.JSONObject

/**
 * Executes sync.* planner output through existing webdav.* and backup.* Host
 * capability handlers. This closes the loop: Core plans → Android executes.
 *
 * All execution goes through [HostAdapter.dispatch] so it reuses the same
 * retry/auth/error-mapping path as direct webdav.* calls.
 */
class SyncPlanExecutor(private val adapter: HostAdapter) {

    /**
     * Execute sync.webdav.plan output: dispatch each HostHttpRequest descriptor
     * to the appropriate webdav.* handler based on HTTP method.
     *
     * Maps: PUT→webdav.upload, GET→webdav.download, DELETE→webdav.delete,
     * MKCOL→webdav.mkdir, PROPFIND→webdav.list
     */
    fun executeWebDavPlan(requests: List<JSONObject>): List<WebDavExecutionResult> {
        return requests.map { req ->
            val method = req.optString("method", "GET").uppercase()
            val url = req.optString("url", "")
            val body = req.optString("body", "")
            val headers = req.optJSONObject("headers") ?: JSONObject()
            val capability = when (method) {
                "PUT" -> "webdav.upload"
                "GET" -> "webdav.download"
                "DELETE" -> "webdav.delete"
                "MKCOL" -> "webdav.mkdir"
                "PROPFIND" -> "webdav.list"
                else -> null
            } ?: return@map WebDavExecutionResult(url, method, false, "unsupported method: $method")

            val params = JSONObject().apply {
                put("url", url)
                if (body.isNotEmpty()) put("body", body)
                if (headers.length() > 0) put("headers", headers)
            }
            val request = HostRequest(1L, 1L, capability, params.toString())
            val reply = adapter.dispatch(request)
            val success = reply.isComplete()
            val statusCode = if (success) {
                val result = JSONObject((reply as HostReply.Complete).resultJson())
                result.optInt("statusCode", 0)
            } else 0
            WebDavExecutionResult(url, method, success, "HTTP $statusCode")
        }
    }

    /**
     * Execute sync.backup.retention output: delete each path via webdav.delete.
     */
    fun executeRetention(pathsToDelete: List<String>): List<WebDavExecutionResult> {
        return pathsToDelete.map { path ->
            val params = JSONObject().put("url", path)
            val request = HostRequest(1L, 1L, "webdav.delete", params.toString())
            val reply = adapter.dispatch(request)
            WebDavExecutionResult(
                url = path,
                method = "DELETE",
                success = reply.isComplete(),
                detail = if (reply.isComplete()) "deleted" else "failed"
            )
        }
    }
}

data class WebDavExecutionResult(
    val url: String,
    val method: String,
    val success: Boolean,
    val detail: String
)
