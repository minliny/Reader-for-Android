package com.reader.api

import android.content.Context
import android.webkit.WebView
import com.reader.android.AppProvider
import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieStore
import com.reader.core.ReaderCoreRuntime
import com.reader.host.AntiBotCapabilityHandler
import com.reader.host.AntiBotDetectingHttpFetch
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.BackgroundCancelHandler
import com.reader.host.BackgroundScheduleHandler
import com.reader.host.CacheGetHandler
import com.reader.host.CachePutHandler
import com.reader.host.CookieClearHandler
import com.reader.host.CookieGetHandler
import com.reader.host.CookieSetHandler
import com.reader.host.CredentialDeleteHandler
import com.reader.host.CredentialGetHandler
import com.reader.host.CredentialResolveHandler
import com.reader.host.CredentialSetHandler
import com.reader.host.DefaultHostCache
import com.reader.host.DefaultHostFileSystem
import com.reader.host.DefaultHostLogger
import com.reader.host.FileReadHandler
import com.reader.host.FileWriteHandler
import com.reader.host.FileDeleteHandler
import com.reader.host.HostCache
import com.reader.host.HostCachePersistenceAdapter
import com.reader.host.HostFileSystem
import com.reader.host.HostLogger
import com.reader.host.HostPersistence
import com.reader.host.HostRuntime
import com.reader.host.HostSmokeEchoHandler
import com.reader.host.HostTransport
import com.reader.host.HttpCancelHandler
import com.reader.host.HttpCallRegistry
import com.reader.host.HttpExecuteHandler
import com.reader.host.HttpFetch
import com.reader.host.LogEmitHandler
import com.reader.host.MediaDownloadCapabilityHandler
import com.reader.host.MediaDownloadHandler
import com.reader.host.OkHttpHostTransport
import com.reader.host.OkHttpMediaDownloadExecutor
import com.reader.host.PersistenceGetHandler
import com.reader.host.PersistencePutHandler
import com.reader.host.ReaderCoreHostTransport
import com.reader.host.SharedPreferencesHostPersistence
import com.reader.host.SourceGetVariableHandler
import com.reader.host.SourceLoginHeaderMapHandler
import com.reader.host.SourceRssContext
import com.reader.host.SourceSetVariableHandler
import com.reader.host.SourceVariableStore
import com.reader.host.SystemInfoHandler
import com.reader.host.registerHandlers as registerSourceRssHandlers
import com.reader.host.TimeNowHandler
import com.reader.host.WebDavCredentialProvider
import com.reader.host.WebViewEvaluateJavaScriptHandler
import com.reader.host.WebViewExecutor
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

    /**
     * Slice A — WebView real binding: rebind the `webview.evaluateJavaScript`
     * capability to a real [WebView] (typically the one created in
     * `MainActivity.onCreate`). Until this is called the registered executor
     * has no [WebView] and dispatch fails closed with
     * [com.reader.host.WebViewExecutorError.RequiresUiContext] —
     * [com.reader.host.HostReply.error] code `REQUIRES_UI_CONTEXT`.
     *
     * Pass `null` to revert to the fail-closed state (used by tests/dev
     * tear-down). The rebind is atomic: subsequent Core dispatches see the
     * new executor; the previous executor is no longer referenced by the
     * host runtime.
     */
    fun bindWebViewExecutor(webView: WebView?) {
        val executor: WebViewExecutor = AndroidWebViewExecutor(webView)
        hostRuntime.register(
            WebViewEvaluateJavaScriptHandler.CAPABILITY,
            WebViewEvaluateJavaScriptHandler(executor)
        )
    }

    /**
     * Slice E — Exposes the host runtime's registered [HostAdapter] so the
     * UI layer (AppShell effect collector via [HostRequestDispatcher]) can
     * dispatch `pendingHostRequests` through the same handler set the Core
     * poll thread uses. Avoids re-registering handlers on a separate adapter.
     */
    fun hostAdapter(): com.reader.host.HostAdapter = hostRuntime.adapter()

    fun close() {
        hostRuntime.stop()
        runtime.close()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L

        @Volatile
        private var INSTANCE: ReaderCoreClient? = null

        fun init(configJson: String = "{}"): ReaderCoreClient = init(context = null, configJson)

        /**
         * Slice B — Core Runtime capability registration. When [context] is
         * non-null (production), the file/cache/persistence handlers are
         * backed by real Android storage ([DefaultHostFileSystem] rooted at
         * `context.filesDir`). When null (JVM test), they fall back to
         * in-memory doubles.
         */
        fun init(context: Context?, configJson: String = "{}"): ReaderCoreClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val runtime = ReaderCoreRuntime(configJson)
                    val transport = ReaderCoreHostTransport(runtime)
                    val cookieStore: CookieStore = AppProvider.cookieStore
                    val fs: HostFileSystem = context?.let {
                        DefaultHostFileSystem(it.filesDir)
                    } ?: com.reader.host.InMemoryHostFileSystem()
                    val cache: HostCache = DefaultHostCache()
                    val persistence: HostPersistence = context?.let {
                        SharedPreferencesHostPersistence(
                            it.getSharedPreferences("reader_core_host_persistence", Context.MODE_PRIVATE)
                        )
                    } ?: HostCachePersistenceAdapter(cache)
                    val logger: HostLogger = DefaultHostLogger()
                    val hostRuntime = buildHostRuntime(
                        transport, cookieStore, fs, cache, persistence, logger, context
                    )
                    ReaderCoreClient(runtime, hostRuntime).also { INSTANCE = it }
                }
            }
        }

        /**
         * Build the [HostRuntime] with all capabilities registered. Extracted
         * from [init] so JVM tests can exercise the real registration code
         * path without loading the native `.so` (which [ReaderCoreRuntime]
         * requires). Production [init] calls this with a
         * [ReaderCoreHostTransport]; tests inject a fake [HostTransport] and
         * pass [context] = null to skip [com.reader.host.HostFacade] (whose
         * TTS/Notification/Permission adapters need a real Android Context).
         *
         * The returned [HostRuntime] is started (poll thread running).
         *
         * `credential.resolve` is registered only when [context] is non-null
         * (production / instrumented), bridged to [WebDavCredentialProvider]
         * over [AppProvider.webDavCredentialStore]. JVM tests pass
         * [context] = null and therefore do not register it — the capability
         * registry JVM test asserts this gap-state for context=null.
         */
        @JvmSynthetic
        internal fun buildHostRuntime(
            transport: HostTransport,
            cookieStore: CookieStore,
            fs: HostFileSystem,
            cache: HostCache,
            persistence: HostPersistence,
            logger: HostLogger,
            context: Context?
        ): HostRuntime {
            val cookieJar = CookieStoreJar(cookieStore)
            val httpCallRegistry = HttpCallRegistry()
            val sourceVariableStore = SourceVariableStore(persistence)
            var hostRuntimeBuilder = HostRuntime.over(transport)
                .register(
                    HttpExecuteHandler.CAPABILITY,
                    HttpExecuteHandler(
                        // 阶段 5 — Wrap the OkHttp transport in
                        // AntiBotDetectingHttpFetch so http.execute responses
                        // are inspected for anti-bot markers (503+jschl,
                        // reCAPTCHA, slider, etc.) before reaching Core. When
                        // a challenge is detected, the decorator throws
                        // AntiBotChallengeRequiredException, which the handler
                        // maps to a retryable INTERNAL error with challenge
                        // diagnostics in the message. Clean responses pass
                        // through untouched (zero overhead on the happy path).
                        AntiBotDetectingHttpFetch(
                            OkHttpHostTransport(
                                OkHttpHostTransport.defaultClient(cookieJar),
                                httpCallRegistry
                            )
                        )
                    )
                )
                .register(HttpCancelHandler.CAPABILITY, HttpCancelHandler(httpCallRegistry))
                .register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))
                .register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
                .register(CookieClearHandler.CAPABILITY, CookieClearHandler(cookieStore))
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
                    MediaDownloadCapabilityHandler(
                        // 阶段 5 — pass rootDir so savePath lands on disk.
                        // When context is null (JVM test), rootDir stays null
                        // and savePath is ignored (bytes stay in memory).
                        handlerProvider = {
                            MediaDownloadHandler(
                                OkHttpMediaDownloadExecutor(
                                    rootDir = context?.filesDir
                                )
                            )
                        }
                    )
                )
                .register(HostSmokeEchoHandler.CAPABILITY, HostSmokeEchoHandler())
                // ── Slice B: Core Runtime capabilities ──
                .register(FileReadHandler.CAPABILITY, FileReadHandler(fs))
                .register(FileWriteHandler.CAPABILITY, FileWriteHandler(fs))
                .register(FileDeleteHandler.CAPABILITY, FileDeleteHandler(fs))
                .register(CacheGetHandler.CAPABILITY, CacheGetHandler(cache))
                .register(CachePutHandler.CAPABILITY, CachePutHandler(cache))
                .register(PersistenceGetHandler.CAPABILITY, PersistenceGetHandler(persistence))
                .register(PersistencePutHandler.CAPABILITY, PersistencePutHandler(persistence))
                .register(
                    SourceGetVariableHandler.CAPABILITY,
                    SourceGetVariableHandler(sourceVariableStore)
                )
                .register(
                    SourceSetVariableHandler.CAPABILITY,
                    SourceSetVariableHandler(sourceVariableStore)
                )
                .register(
                    SourceLoginHeaderMapHandler.CAPABILITY,
                    SourceLoginHeaderMapHandler()
                )
                .register(LogEmitHandler.CAPABILITY, LogEmitHandler(logger))
                .register(TimeNowHandler.CAPABILITY, TimeNowHandler())
                .register(SystemInfoHandler.CAPABILITY, SystemInfoHandler())
            // ── P1-5: Source / RSS capability handlers ──
            // Pure-JVM (no Android Context) so registered on both paths
            // (JVM tests + production). Production wires the real DataStore +
            // Room-backed repositories via AppProvider; JVM tests pass a fake
            // SourceRssContext via buildHostRuntimeForTest.
            val sourceRssContext = if (context != null) {
                SourceRssContext(
                    bookSourceRepository = AppProvider.bookSourceRepository,
                    subscriptionRepository = AppProvider.subscriptionRepository
                )
            } else {
                com.reader.host.fakeSourceRssContext()
            }
            hostRuntimeBuilder = sourceRssContext.registerSourceRssHandlers(hostRuntimeBuilder)
            // ── Slice C: HostFacade — UI-facing capabilities ──
            // Only wire when context is available (production /
            // instrumented). JVM tests inject handlers manually.
            if (context != null) {
                // Slice C — production wiring: real system TTS
                // ([AndroidTtsEngine] backed by android.speech.tts).
                // JVM tests inject FakeAndroidTtsAdapter manually
                // because TextToSpeech requires a real Context.
                // P1-4: the TtsSessionController wraps the engine and adds
                // paragraph queue, multi-chapter progression, AudioFocus/
                // BecomingNoisy recovery, and progress writeback.
                val facade = com.reader.host.HostFacade(
                    context = context,
                    tts = AppProvider.ttsEngine,
                    permission = AppProvider.permissionRuntimeAdapter,
                    notification = com.reader.android.data.adapter.AndroidNotificationRuntimeAdapter(context),
                    webDav = null,
                    credentials = AppProvider.webDavCredentialStore,
                    downloadCache = null,
                    ttsSessionController = AppProvider.ttsSessionController
                )
                hostRuntimeBuilder = facade.registerHandlers(hostRuntimeBuilder)
                // ── credential.resolve (GAP-D-01 closed) ──
                // Bridge credential.resolve to WebDavCredentialStore via
                // WebDavCredentialProvider. Only registered when context is
                // non-null (production / instrumented) because the store is
                // AppProvider.webDavCredentialStore. JVM tests stay unregistered
                // and continue to assert the gap-state for context=null.
                hostRuntimeBuilder = hostRuntimeBuilder.register(
                    CredentialResolveHandler.CAPABILITY,
                    CredentialResolveHandler(WebDavCredentialProvider(AppProvider.webDavCredentialStore))
                )
            }
            return hostRuntimeBuilder.start()
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
