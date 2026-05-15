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

    fun putContent(path: String, content: String) { files[path] = content }

    override suspend fun execute(request: WebDavRequest): WebDavResponse {
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
