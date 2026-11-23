package io.reader.android.runtime.http

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One public application interceptor; OkHttp still executes every exchange.
 * It may proceed again only after closing the previous body, following fixed
 * Core decisions. It never calls an OkHttp internal API or starts another Call. */
internal class ReaderHttpPolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val work = checkNotNull(chain.request().tag(ReaderHttpWork::class.java))
        for (attempt in 1..work.descriptor.maximumAttempts) {
            var request = chain.request()
            val redirects = JSONArray()
            try {
                while (true) {
                    work.requireActive()
                    val remaining = work.remainingMillis().coerceAtLeast(1).toInt()
                    val transport = chain.withConnectTimeout(remaining, TimeUnit.MILLISECONDS)
                        .withReadTimeout(remaining, TimeUnit.MILLISECONDS).withWriteTimeout(remaining, TimeUnit.MILLISECONDS)
                    val permit = ReaderHttpHopPermit(work)
                    val permitted = withCookies(request, work).newBuilder().tag(ReaderHttpHopPermit::class.java, permit).build()
                    val observed = try { transport.proceed(permitted) }
                    catch (failure: IOException) {
                        if (failure is ReaderHttpImplicitFollowupBlocked) check(failure.permit === permit)
                        work.requireActive()
                        // A complete real response precedes any blocked library follow-up.
                        // Return that response to Core policy; never synthesize a successful status.
                        permit.received?.response ?: throw failure
                    }
                    observed.use { response ->
                        val received = checkNotNull(permit.received) { "HTTP_NETWORK_RESPONSE_NOT_CAPTURED" }
                        val actual = received.response
                        ReaderHttpResponse.checkHeaders(response.headers, work.limits)
                        work.whileCurrent {
                            work.cookieSession?.responseHeaders(actual.request.url.toUri(), actual.headers.toMultimap())
                        }
                        val location = actual.header("Location")
                        if (actual.code !in setOf(300, 301, 302, 303, 307, 308) || location == null ||
                            !ReaderCoreHttpPolicy.allowNextRedirect(work.descriptor.followRedirects,
                                work.descriptor.maximumRedirects, redirects.length())) {
                            // Stopping at the requested cap exposes the actual current 3xx.
                            ReaderHttpResponse.capture(received, redirects, work)
                            return actual.newBuilder().body(ByteArray(0).toResponseBody(actual.body.contentType())).build()
                        }
                        val next = ReaderCoreHttpPolicy.resolveLocation(actual.request.url.toString(), location)
                            .toHttpUrlOrNull() ?: throw ReaderHttpFailure("INVALID_PARAMS", "HTTP_REDIRECT_URL_INVALID")
                        val sameOrigin = request.url.scheme == next.scheme && request.url.host == next.host && request.url.port == next.port
                        val names = ReaderCoreHttpPolicy.redirectHeaderNames(request.headers.names(), sameOrigin)
                        val headers = request.headers.newBuilder()
                        request.headers.names().filter { it !in names }.forEach { headers.removeAll(it) }
                        val method = ReaderCoreHttpPolicy.redirectMethod(actual.code, request.method, request.body != null)
                        if (!method.keepBody && (request.body != null || method.method != request.method)) {
                            headers.removeAll("Content-Length").removeAll("Content-Type").removeAll("Transfer-Encoding")
                        }
                        redirects.put(JSONObject().put("status", actual.code)
                            .put("fromUrl", actual.request.url.toString()).put("toUrl", next.toString())
                            .put("headers", ReaderHttpResponse.headersJson(actual.headers)))
                        // Keep original explicit headers separate from per-hop jar injection.
                        request = request.newBuilder().url(next).headers(headers.build())
                            .method(method.method, request.body.takeIf { method.keepBody }).build()
                    }
                }
            } catch (failure: IOException) {
                work.requireActive()
                if (attempt == work.descriptor.maximumAttempts || !ReaderHttpHost.retryableTransport(failure)) throw failure
                work.backoff(attempt)
            }
        }
        error("HTTP_ATTEMPT_LOOP_EXHAUSTED")
    }

    private fun withCookies(request: Request, work: ReaderHttpWork): Request {
        val jar = work.cookieSession ?: return request
        val fromJar = work.whileCurrent { jar.requestHeaders(request.url.toUri(), request.headers.toMultimap()) }
            .entries.filter { it.key.equals("Cookie", true) }.flatMap { it.value }.joinToString("; ")
        val explicit = request.headers.values("Cookie").joinToString("; ")
        val merged = ReaderCoreHttpPolicy.mergeCookieHeader(explicit, fromJar)
        return request.newBuilder().removeHeader("Cookie").apply { if (merged.isNotEmpty()) header("Cookie", merged) }.build().also {
            ReaderHttpResponse.checkHeaders(it.headers, work.limits, "HTTP_REQUEST_HEADER_BUDGET")
        }
    }
}
