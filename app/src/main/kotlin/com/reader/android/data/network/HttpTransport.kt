package com.reader.android.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Abstract HTTP transport for [RealCoreBridge].
 * Production uses [OkHttpTransport]; tests inject [FixtureHttpTransport].
 */
interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
}

/** Production transport backed by OkHttp. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient()
) : HttpTransport {

    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
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

/**
 * Fixture transport for deterministic testing.
 * Register responses by URL, then inject into [RealCoreBridge].
 */
class FixtureHttpTransport : HttpTransport {

    private val responses = mutableMapOf<String, HttpResponse>()
    private var defaultResponse: HttpResponse = HttpResponse(
        code = 404, body = "", headers = emptyMap(), requestUrl = ""
    )

    fun register(url: String, response: HttpResponse) {
        responses[url] = response
    }

    fun registerAll(vararg pairs: Pair<String, HttpResponse>) {
        pairs.forEach { (url, resp) -> responses[url] = resp }
    }

    fun default(response: HttpResponse) {
        defaultResponse = response
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        return responses[url] ?: defaultResponse.copy(requestUrl = url)
    }

    companion object {
        fun successHtml(html: String, url: String = ""): HttpResponse = HttpResponse(
            code = 200,
            body = html,
            headers = mapOf("Content-Type" to listOf("text/html; charset=utf-8")),
            requestUrl = url
        )
    }
}
