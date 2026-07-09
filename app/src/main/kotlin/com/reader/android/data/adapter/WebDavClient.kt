package com.reader.android.data.adapter

data class WebDavRequest(
    val url: String,
    val method: WebDavMethod,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)

enum class WebDavMethod { PROPFIND, GET, PUT, DELETE, MKCOL }

data class WebDavResponse(
    val statusCode: Int,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)

interface WebDavClient {
    suspend fun execute(request: WebDavRequest): WebDavResponse
}

class FakeWebDavClient : WebDavClient {
    private val files = mutableMapOf<String, String>()
    // 强制响应状态码覆盖表：用于在 JVM 测试中模拟服务端业务失败（如 500/404）。
    // 命中时直接返回该状态码，不产生任何副作用（PUT 不写入、DELETE 不移除），
    // 与真实 WebDAV 服务端在 4xx/5xx 时不改变资源状态的行为一致。
    private val forcedStatus = mutableMapOf<String, Int>()

    fun putContent(path: String, content: String) { files[path] = content }

    /** 强制下一次访问 [url] 时返回 [statusCode]，用于模拟业务失败（如 500/404）。 */
    fun forceStatusCode(url: String, statusCode: Int) {
        forcedStatus[url] = statusCode
    }

    fun clearForcedStatus() {
        forcedStatus.clear()
    }

    override suspend fun execute(request: WebDavRequest): WebDavResponse {
        forcedStatus[request.url]?.let { return WebDavResponse(it) }
        return when (request.method) {
            WebDavMethod.PROPFIND -> WebDavResponse(207, files.keys.joinToString("\n") { it })
            WebDavMethod.GET -> {
                val content = files[request.url] ?: return WebDavResponse(404)
                WebDavResponse(200, content)
            }
            WebDavMethod.PUT -> {
                request.body?.let { files[request.url] = it }
                WebDavResponse(201)
            }
            WebDavMethod.DELETE -> {
                files.remove(request.url)
                WebDavResponse(204)
            }
            WebDavMethod.MKCOL -> WebDavResponse(201)
        }
    }
}
