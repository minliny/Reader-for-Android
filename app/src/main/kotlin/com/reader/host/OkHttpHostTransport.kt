package com.reader.host

import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * OkHttp-backed [HttpFetch] implementation: executes Core's [HttpRequest]
 * descriptors via OkHttp and returns [HttpResponse] in the shape that
 * [HttpExecuteHandler] encodes into a `host.complete` command.
 *
 * Per charter §10.3 / Core-Host boundary: the adapter never opens a socket
 * itself — TLS / network policy / cookies stay here on the host side. The
 * Core side produces request descriptors; this transport performs the actual
 * network fetch.
 *
 * **Cancel support**: when [registry] is non-null, each in-flight OkHttp
 * [Call] is registered under a host-assigned `requestTag` (format
 * `http-<n>`) before execution and removed in a `finally` block. The tag
 * is stamped onto the returned [HttpResponse] so Core can pass it back via
 * `http.cancel` to abort the request. When [registry] is null (e.g. JVM
 * tests with a fake transport), no tracking happens and `requestTag` is
 * null — cancellation is a no-op.
 */
class OkHttpHostTransport(
    private val client: OkHttpClient = defaultClient(null),
    private val registry: HttpCallRegistry? = null
) : HttpFetch {

    private val tagCounter = AtomicLong(0)

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
        val call: Call = client.newCall(builder.build())
        val requestTag: String? = registry?.let {
            val tag = "http-${tagCounter.incrementAndGet()}"
            it.register(tag, call)
            tag
        }
        try {
            call.execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val headers = resp.headers.toMultimap()
                    .mapValues { it.value.joinToString(", ") }
                val finalUrl = resp.request.url.toString()
                return HttpResponse(resp.code, body, headers, finalUrl, requestTag)
            }
        } finally {
            registry?.complete(requestTag!!)
        }
    }

    companion object {
        fun defaultClient(cookieJar: CookieJar? = null): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
            if (cookieJar != null) {
                builder.cookieJar(cookieJar)
            }
            return builder.build()
        }
    }
}
