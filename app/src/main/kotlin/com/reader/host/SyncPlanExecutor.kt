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
            } ?: return@map WebDavExecutionResult(
                url = url,
                method = method,
                success = false,
                detail = "unsupported method: $method",
                statusCode = 0
            )

            val params = JSONObject().apply {
                put("url", url)
                if (body.isNotEmpty()) put("body", body)
                if (headers.length() > 0) put("headers", headers)
            }
            val request = HostRequest(1L, 1L, capability, params.toString())
            val reply = adapter.dispatch(request)
            // reply.isComplete() 只代表 transport 成功（handler 返回了结构化 JSON），
            // 不代表业务成功。HostReply.error(...)（如 NOT_CONFIGURED / 参数非法）
            // 时 isComplete() 为 false，直接判为失败。
            if (!reply.isComplete()) {
                val err = reply as HostReply.Error
                return@map WebDavExecutionResult(
                    url = url,
                    method = method,
                    success = false,
                    detail = "host error: ${err.code()} - ${err.message()}",
                    statusCode = 0
                )
            }
            val result = JSONObject((reply as HostReply.Complete).resultJson())
            val statusCode = result.optInt("statusCode", 0)
            // 业务级成败字段：不同 HTTP 方法对应 handler 返回的不同布尔字段
            // （PUT→uploaded, GET→downloaded, DELETE→deleted, MKCOL→created, PROPFIND→success）。
            val businessField = when (method) {
                "PUT" -> "uploaded"
                "GET" -> "downloaded"
                "DELETE" -> "deleted"
                "MKCOL" -> "created"
                "PROPFIND" -> "success"
                else -> "success"
            }
            val businessSuccess = result.optBoolean(businessField, false)
            val detail = if (businessSuccess) {
                "HTTP $statusCode"
            } else {
                // 业务失败时优先带上 handler 提供的 message，便于 UI 展示真实原因
                val msg = result.optString("message", "")
                if (msg.isNotEmpty()) "HTTP $statusCode: $msg" else "HTTP $statusCode"
            }
            WebDavExecutionResult(url, method, businessSuccess, detail, statusCode)
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
            // 与 executeWebDavPlan 一致：transport 完成 ≠ 业务成功，
            // 必须解析 webdav.delete 返回的 deleted 字段。
            if (!reply.isComplete()) {
                val err = reply as HostReply.Error
                return@map WebDavExecutionResult(
                    url = path,
                    method = "DELETE",
                    success = false,
                    detail = "host error: ${err.code()} - ${err.message()}",
                    statusCode = 0
                )
            }
            val result = JSONObject((reply as HostReply.Complete).resultJson())
            val statusCode = result.optInt("statusCode", 0)
            val deleted = result.optBoolean("deleted", false)
            val detail = if (deleted) "HTTP $statusCode" else {
                val msg = result.optString("message", "")
                if (msg.isNotEmpty()) "HTTP $statusCode: $msg" else "HTTP $statusCode"
            }
            WebDavExecutionResult(path, "DELETE", deleted, detail, statusCode)
        }
    }
}

data class WebDavExecutionResult(
    val url: String,
    val method: String,
    val success: Boolean,
    val detail: String,
    val statusCode: Int = 0
)
