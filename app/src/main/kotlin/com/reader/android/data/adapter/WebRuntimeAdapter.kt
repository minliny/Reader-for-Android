package com.reader.android.data.adapter

data class WebRuntimeRequest(
    val url: String,
    val html: String? = null,
    val jsCode: String? = null,
    val sourceUrl: String
)

data class WebRuntimeResponse(
    val html: String,
    val url: String,
    val success: Boolean,
    val errorMessage: String? = null
)

interface WebRuntimeAdapter {
    suspend fun loadHtml(url: String, html: String): WebRuntimeResponse
    suspend fun evaluateJs(request: WebRuntimeRequest): WebRuntimeResponse
}

class FakeWebRuntimeAdapter : WebRuntimeAdapter {

    override suspend fun loadHtml(url: String, html: String): WebRuntimeResponse {
        return WebRuntimeResponse(
            html = html,
            url = url,
            success = true
        )
    }

    override suspend fun evaluateJs(request: WebRuntimeRequest): WebRuntimeResponse {
        return WebRuntimeResponse(
            html = request.html ?: "<html><body>JS executed</body></html>",
            url = request.url,
            success = true
        )
    }
}
