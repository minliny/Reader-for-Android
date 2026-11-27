package io.reader.rebuild.host.network

import com.reader.core.ReaderCoreRuntime
import com.reader.core.protocol.M2HostHttpCookie
import com.reader.core.protocol.M2HostHttpRedirect
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody

internal const val HTTP_BYTES = 8L * 1024 * 1024
private val redirectsWithLocation = setOf(300, 301, 302, 303, 307, 308)
private val credentialHeaders = listOf("Authorization", "Cookie", "Proxy-Authorization")

internal class OkHttpNetwork {
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    fun close() {
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
    private data class NextHop<T>(val response: T? = null, val target: HttpUrl? = null, val next: Request? = null,
        val redirect: M2HostHttpRedirect? = null)

    suspend fun fetch(model: M2HostHttpRequest, jar: PersistentCookieJar?): M2HostHttpResponse {
        val request = request(model)
        val attempts = (model.retry?.maxAttempts ?: 1).coerceIn(1, 8).toInt()
        return withTimeout(timeoutMillis(model.timeoutMs)) {
            var lastFailure: NetworkHostFailure? = null
            repeat(attempts) { attempt ->
                try {
                    return@withTimeout exchange(request, jar, model.followRedirects ?: true,
                        (model.maxRedirects ?: 10).coerceIn(0, 20).toInt(), model.timeoutMs) { response, redirectTrace, cookies ->
                        if (response.code >= 400) throw NetworkHostFailure.http(response.code)
                        val data = response.body.readBounded(HTTP_BYTES)
                        val charset = runCatching { response.body.contentType()?.charset(Charsets.UTF_8) }.getOrNull()
                            ?: Charsets.UTF_8
                        val text = if (model.responseAsset == true) "" else String(data, charset)
                        M2HostHttpResponse(body = text, status = response.code.toLong(), headers = response.headers.wire(),
                            finalUrl = response.request.url.toString(), charsetHint = charset.name(),
                            bodyBase64 = if (model.responseAsset == true) Base64.getEncoder().encodeToString(data) else null,
                            session = model.session, redirects = redirectTrace, cookies = cookies.map(::wireCookie))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = NetworkHostFailure.classified(error)
                    if (!failure.retryable || attempt + 1 == attempts) throw failure
                    lastFailure = failure
                    delay((model.retry?.backoffMillis ?: 0).coerceIn(0, 60_000))
                }
            }
            throw checkNotNull(lastFailure)
        }
    }

    suspend fun <T : Any> exchange(
        initial: Request,
        jar: PersistentCookieJar?,
        follow: Boolean = true,
        redirectLimit: Int = 10,
        requestedTimeout: Long?,
        discard: (T) -> Unit = {},
        map: (Response, List<M2HostHttpRedirect>, List<Cookie>) -> T,
    ): T {
        var current = initial
        var allowCredentials = true
        val trace = mutableListOf<M2HostHttpRedirect>()
        val observedCookies = mutableListOf<Cookie>()
        while (true) {
            val cookiesAllowed = allowCredentials
            val request = if (cookiesAllowed && jar != null && current.header("Cookie") == null) {
                val header = jar.loadForRequest(current.url).joinToString("; ") { "${it.name}=${it.value}" }
                if (header.isEmpty()) current else current.newBuilder().header("Cookie", header).build()
            } else current
            val call = client.newCall(request)
            call.timeout().timeout(timeoutMillis(requestedTimeout), TimeUnit.MILLISECONDS)
            val outcome = call.runMapped(discard = { hop: NextHop<T> -> hop.response?.let(discard) }) { response ->
                val set = Cookie.parseAll(request.url, response.headers)
                if (cookiesAllowed) {
                    jar?.saveFromResponse(request.url, set)
                    observedCookies.addAll(set)
                }
                val location = response.headers["Location"]
                val target = location?.let(request.url::resolve)
                if (!follow || response.code !in redirectsWithLocation || target == null) {
                    return@runMapped NextHop(response = map(response, trace.toList(), observedCookies.toList()))
                }
                if (trace.size >= redirectLimit) throw NetworkHostFailure.http(response.code)
                if (target.username.isNotEmpty() || target.password.isNotEmpty()) {
                    throw NetworkHostFailure.denied("Redirect destination contains credentials")
                }
                // Continue the original request, not the transport-generated cookie header.
                val next = current.newBuilder().url(target)
                if (!request.url.sameOrigin(target)) credentialHeaders.forEach(next::removeHeader)
                val rewrite = response.code == 303 && current.method != "HEAD" ||
                    response.code in 300..302 && current.method == "POST"
                if (rewrite) next.get().removeHeader("Content-Type").removeHeader("Content-Length")
                NextHop(target = target, next = next.build(), redirect = M2HostHttpRedirect(response.code.toLong(),
                    request.url.toString(), target.toString(), response.headers.wire()))
            }
            outcome.response?.let { return it }
            val target = checkNotNull(outcome.target)
            allowCredentials = allowCredentials && current.url.sameOrigin(target)
            trace.add(checkNotNull(outcome.redirect))
            current = checkNotNull(outcome.next)
        }
    }

    private fun request(model: M2HostHttpRequest): Request {
        val builder = Request.Builder().url(httpUrl(model.url))
        model.headers?.forEach { (name, value) -> builder.header(name, value.headerValue()) }
        val verb = (model.method ?: "GET").uppercase()
        val demandBody = verb in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")
        val charset = model.charset ?: "UTF-8"
        val body = model.body?.takeIf { it.isNotEmpty() || demandBody }
        val bytes = body?.let {
            try {
                ReaderCoreRuntime.encodeText(it, charset, HTTP_BYTES)
            } catch (_: Exception) {
                throw NetworkHostFailure.invalid("Core refused request character encoding")
            }
        } ?: if (demandBody) ByteArray(0) else null
        val media = builder.build().header("Content-Type") ?: "text/plain; charset=$charset"
        return builder.method(verb, bytes?.toRequestBody(media.toMediaTypeOrNull())).build()
    }
}

internal fun timeoutMillis(timeout: Long?): Long = (timeout ?: 15_000).coerceIn(1, 120_000)

internal fun ResponseBody.readBounded(limit: Long): ByteArray {
    val bytes = ByteArrayOutputStream()
    forEachSegment(limit) { segment, size -> bytes.write(segment, 0, size) }
    return bytes.toByteArray()
}

internal fun ResponseBody.forEachSegment(limit: Long, consume: (ByteArray, Int) -> Unit): Long {
    if (contentLength() > limit) throw NetworkHostFailure.invalid("Response exceeds byte budget")
    return byteStream().use { source ->
        val segment = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = source.read(segment, 0, minOf(segment.size.toLong(), limit - total + 1).toInt())
            if (read < 0) break
            if (read > limit - total) throw NetworkHostFailure.invalid("Response exceeds byte budget")
            consume(segment, read)
            total += read
        }
        total
    }
}

private suspend fun <T> Call.runMapped(discard: (T) -> Unit, transform: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use(transform)
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                    return
                }
                continuation.resume(result) { _, lost, _ -> discard(lost) }
            }
        })
    }

internal fun JsonElement.headerValue(): String = when (this) {
    is JsonPrimitive -> if (isString) content else throw NetworkHostFailure.invalid("Invalid HTTP header value")
    is JsonArray -> joinToString(", ") { it.headerValue() }
    else -> throw NetworkHostFailure.invalid("Invalid HTTP header value")
}

private fun Headers.wire(): JsonObject = JsonObject(toMultimap().mapValues { (_, values) ->
    if (values.size == 1) JsonPrimitive(values.single()) else JsonArray(values.map(::JsonPrimitive))
})

private fun wireCookie(cookie: Cookie) = M2HostHttpCookie(cookie.name, cookie.value, cookie.domain, cookie.path,
    cookie.expiresAt.takeIf { cookie.persistent }?.let { Instant.ofEpochMilli(it).toString() },
    cookie.httpOnly, cookie.secure, cookie.sameSite)
