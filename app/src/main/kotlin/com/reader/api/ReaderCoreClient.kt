package com.reader.api

import com.reader.android.AppProvider
import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieStore
import com.reader.core.ReaderCoreRuntime
import com.reader.host.AntiBotCapabilityHandler
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.CookieGetHandler
import com.reader.host.CookieSetHandler
import com.reader.host.HostRuntime
import com.reader.host.HttpExecuteHandler
import com.reader.host.HttpFetch
import com.reader.host.MediaDownloadCapabilityHandler
import com.reader.host.OkHttpHostTransport
import com.reader.host.ReaderCoreHostTransport
import com.reader.host.WebViewEvaluateJavaScriptHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONObject

/**
 * App-level singleton wrapping [ReaderCoreRuntime] + [HostRuntime].
 *
 * Uses the polling-based Core protocol: [HostRuntime] owns a daemon poll
 * thread that demultiplexes every Core event off [ReaderCoreHostTransport]:
 *  - `host.request` -> dispatched via [HostAdapter] (e.g. [HttpExecuteHandler]
 *    backed by [OkHttpHostTransport]) and replied with `host.complete` /
 *    `host.error` through the same transport.
 *  - `result` / `error` -> completes the matching [HostRuntime.sendAndAwait]
 *    future.
 *
 * This is the wiring layer, NOT a device proof: JNI `.so` must load on a real
 * device for the poll thread to receive real events. Device proof is Task 12.
 */
class ReaderCoreClient private constructor(
    private val runtime: ReaderCoreRuntime,
    private val hostRuntime: HostRuntime
) {

    /**
     * Send a Core command and await its result/error event, routed by
     * [HostRuntime]'s own poll thread. Blocks the calling coroutine on
     * [Dispatchers.IO] until the matching event arrives or [timeoutMillis]
     * elapses.
     */
    suspend fun sendAndAwait(
        method: String,
        params: JSONObject,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): JSONObject = withContext(Dispatchers.IO) {
        val result = hostRuntime.sendAndAwait(method, params.toString(), timeoutMillis)
        when {
            result.isSuccess() -> JSONObject(result.dataJson())
            result.isError() -> throw CoreException(result.errorJson())
            result.isTimeout() -> throw CoreTimeoutException(
                "Timeout waiting for Core response (method=$method)"
            )
            else -> throw CoreException(
                "{\"code\":\"INTERNAL\",\"message\":\"" +
                    "unknown result kind: ${result.kind()}\"}"
            )
        }
    }

    fun cancel(requestId: Long) {
        runtime.cancel(requestId)
    }

    fun close() {
        hostRuntime.stop()
        runtime.close()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L

        @Volatile
        private var INSTANCE: ReaderCoreClient? = null

        fun init(configJson: String = "{}"): ReaderCoreClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val runtime = ReaderCoreRuntime(configJson)
                    val transport = ReaderCoreHostTransport(runtime)
                    val cookieStore: CookieStore = AppProvider.cookieStore
                    val cookieJar = CookieStoreJar(cookieStore)
                    val hostRuntime = HostRuntime.over(transport)
                        .register(
                            HttpExecuteHandler.CAPABILITY,
                            HttpExecuteHandler(OkHttpHostTransport(OkHttpHostTransport.defaultClient(cookieJar)))
                        )
                        .register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))
                        .register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
                        .register(
                            WebViewEvaluateJavaScriptHandler.CAPABILITY,
                            WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(null))
                        )
                        .register(
                            AntiBotCapabilityHandler.CAPABILITY,
                            AntiBotCapabilityHandler()
                        )
                        .register(
                            MediaDownloadCapabilityHandler.CAPABILITY,
                            MediaDownloadCapabilityHandler()
                        )
                        .start()
                    ReaderCoreClient(runtime, hostRuntime).also { INSTANCE = it }
                }
            }
        }

        fun get(): ReaderCoreClient = INSTANCE
            ?: error("ReaderCoreClient not initialized. Call init() first.")

        /**
         * Test-only entry point: injects a custom [HttpFetch] so instrumented
         * tests can route Core's `http.execute` requests through MockWebServer
         * instead of the real network. Mirrors [init] but swaps the host-side
         * HTTP transport. Production code must keep using [init].
         */
        @JvmStatic
        fun initForTest(httpFetch: HttpFetch, configJson: String = "{}"): ReaderCoreClient =
            initForTest(httpFetch, cookieStore = null, configJson)

        /**
         * Test-only entry point: injects a custom [HttpFetch] and optional
         * [CookieStore]. When [cookieStore] is non-null, registers
         * `cookie.get` / `cookie.set` handlers backed by it, mirroring the
         * production wiring in [init]. Use this for login_cookie lane proof.
         */
        @JvmStatic
        fun initForTest(
            httpFetch: HttpFetch,
            cookieStore: CookieStore?,
            configJson: String = "{}"
        ): ReaderCoreClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val runtime = ReaderCoreRuntime(configJson)
                    val transport = ReaderCoreHostTransport(runtime)
                    var hostRuntime = HostRuntime.over(transport)
                        .register(HttpExecuteHandler.CAPABILITY, HttpExecuteHandler(httpFetch))
                    if (cookieStore != null) {
                        hostRuntime = hostRuntime
                            .register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))
                            .register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
                    }
                    ReaderCoreClient(runtime, hostRuntime.start()).also { INSTANCE = it }
                }
            }
        }

        @Synchronized
        fun resetForTest() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

class CoreException(val errorJson: String) : RuntimeException(errorJson)

class CoreTimeoutException(message: String) : RuntimeException(message)

/**
 * Adapts the suspend [CookieStore] to OkHttp's synchronous [CookieJar].
 * OkHttp calls [loadForRequest] / [saveFromResponse] on its dispatcher
 * threads; we bridge with [runBlocking] so the AndroidCookieManagerStore
 * (which reads/writes android.webkit.CookieManager) stays on the same
 * cookie source as the WebView host.
 */
private class CookieStoreJar(private val store: CookieStore) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val records = cookies.map { c ->
            CookieRecord(
                name = c.name,
                value = c.value,
                domain = c.domain,
                path = c.path,
                secure = c.secure,
                httpOnly = c.httpOnly,
                expiresAt = if (c.persistent) c.expiresAt else null
            )
        }
        runBlocking { store.save(url.toString(), records) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val scope = runBlocking { store.get(url.toString()) }
        return scope.cookies.map { r ->
            Cookie.Builder()
                .name(r.name)
                .value(r.value)
                .domain(r.domain.ifEmpty { url.host })
                .path(r.path.ifEmpty { "/" })
                .apply {
                    if (r.secure) secure()
                    if (r.httpOnly) httpOnly()
                    if (r.expiresAt != null) expiresAt(r.expiresAt)
                }
                .build()
        }
    }
}
