package com.reader.host

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [HttpFetch] implementation: executes Core's [HttpRequest]
 * descriptors via OkHttp and returns [HttpResponse] in the shape that
 * [HttpExecuteHandler] encodes into a `host.complete` command.
 *
 * Per charter §10.3 / Core-Host boundary: the adapter never opens a socket
 * itself — TLS / network policy / cookies stay here on the host side. The
 * Core side produces request descriptors; this transport performs the actual
 * network fetch.
 */
class OkHttpHostTransport(
    private val client: OkHttpClient = defaultClient()
) : HttpFetch {

    override fun fetch(request: HttpRequest): HttpResponse {
        val builder = Request.Builder().url(request.url())
        for ((k, v) in request.headers()) {
            if (v != null) builder.header(k, v)
        }
        when (request.method().uppercase()) {
            "GET" -> { /* default: no body */ }
            "POST" -> builder.post(
                (request.body() ?: "").toRequestBody(
                    request.headers()["Content-Type"]?.toMediaTypeOrNull()
                )
            )
            "PUT" -> builder.put(
                (request.body() ?: "").toRequestBody(
                    request.headers()["Content-Type"]?.toMediaTypeOrNull()
                )
            )
            "DELETE" -> builder.delete()
            else -> builder.method(request.method(), null)
        }
        client.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val headers = resp.headers.toMultimap()
                .mapValues { it.value.joinToString(", ") }
            return HttpResponse(resp.code, body, headers)
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
