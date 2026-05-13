package com.reader.android.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HttpClient(
    private val client: OkHttpClient = defaultClient()
) {
    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()
        return HttpResponse(
            code = response.code,
            body = response.body?.string() ?: "",
            headers = response.headers.toMultimap(),
            requestUrl = url
        )
    }
}

data class HttpResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, List<String>>,
    val requestUrl: String
)
