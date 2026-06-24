package com.reader.android.data.network

import com.reader.android.data.adapter.CookieStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Abstract HTTP transport for [RealCoreBridge].
 * Production uses [OkHttpTransport]; tests inject [FixtureHttpTransport].
 */
interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
    suspend fun post(url: String, headers: Map<String, String>, body: PostRequestBody): HttpResponse
}

/** Production transport backed by OkHttp. */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(cookieJar = null)
) : HttpTransport {

    companion object {
        /** Build a default OkHttp client, optionally wiring a [cookieJar]. */
        fun defaultClient(cookieJar: CookieJar? = null): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
            if (cookieJar != null) builder.cookieJar(cookieJar)
            return builder.build()
        }

        /** Convenience: build a client whose cookies are backed by a [CookieStore]. */
        fun withCookieStore(cookieStore: CookieStore): OkHttpTransport {
            val jar = ScopedOkHttpCookieJar(cookieStore)
            return OkHttpTransport(defaultClient(cookieJar = jar))
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val request = buildRequest(url, headers, body = null)
        return execute(request, url)
    }

    override suspend fun post(url: String, headers: Map<String, String>, body: PostRequestBody): HttpResponse {
        val request = buildRequest(url, headers, body = body)
        return execute(request, url)
    }

    private fun buildRequest(url: String, headers: Map<String, String>, body: PostRequestBody?): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.addHeader(key, value) }
        if (body != null) {
            builder.post(body.toOkHttpRequestBody())
        }
        return builder.build()
    }

    private fun execute(request: Request, requestUrl: String): HttpResponse {
        val response = client.newCall(request).execute()
        return HttpResponse(
            code = response.code,
            body = response.body?.string() ?: "",
            headers = response.headers.toMultimap(),
            requestUrl = requestUrl
        )
    }
}

/**
 * Bridges OkHttp's [CookieJar] to the Android [CookieStore] (typically
 * [com.reader.android.data.adapter.AndroidCookieManagerStore]). This makes
 * OkHttp and the WebView share a single cookie source during a source run —
 * the "live mirror" required by the parity report.
 */
class ScopedOkHttpCookieJar(
    private val cookieStore: CookieStore
) : CookieJar {

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val scope = kotlinx.coroutines.runBlocking { cookieStore.get(url.toString()) }
        return scope.cookies.map { record ->
            val builder = Cookie.Builder()
                .name(record.name)
                .value(record.value)
                .domain(record.domain.ifBlank { url.host })
                .path(record.path.ifBlank { "/" })
            if (record.secure) builder.secure()
            if (record.httpOnly) builder.httpOnly()
            builder.build()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val records = cookies.map { cookie ->
            com.reader.android.data.adapter.CookieRecord(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                expiresAt = if (cookie.persistent) cookie.expiresAt else null
            )
        }
        if (records.isNotEmpty()) {
            kotlinx.coroutines.runBlocking { cookieStore.save(url.toString(), records) }
        }
    }
}

/**
 * Convert a [PostRequestBody] to an OkHttp [RequestBody], honoring the declared
 * [PostContentType]. Form and JSON bodies are built from [PostRequestBody.fields];
 * raw bodies use [PostRequestBody.rawBody] verbatim.
 */
fun PostRequestBody.toOkHttpRequestBody(): RequestBody {
    val mediaType = contentType.mimeType.toMediaTypeOrNull()
    return when (contentType) {
        PostContentType.FORM_URLENCODED, PostContentType.JSON -> toBodyString().toRequestBody(mediaType)
        PostContentType.TEXT_PLAIN -> (rawBody ?: toBodyString()).toRequestBody(mediaType)
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
    val postedBodies: MutableList<PostRequestBody> = mutableListOf()

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

    override suspend fun post(url: String, headers: Map<String, String>, body: PostRequestBody): HttpResponse {
        postedBodies.add(body)
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
