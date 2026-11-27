package io.reader.rebuild.host.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.CookieManagerCompat
import androidx.webkit.Profile
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.reader.core.protocol.M2HostErrorCode
import com.reader.core.protocol.M2HostErrorPhase
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl

internal fun supportsIsolatedWebProfiles(): Boolean =
    WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

internal class IsolatedWebProfile @SuppressLint("SetJavaScriptEnabled", "RequiresFeature") constructor(
    context: Context,
    private val purpose: ReaderWebProfilePurpose,
) {
    private val name: String
    val view: WebView
    private val browserProfile: Profile
    private val manager: CookieManager
    private data class MirroredCookie(val origin: HttpUrl, val cookie: Cookie) {
        val key get() = Triple(cookie.name, cookie.domain, cookie.path)
    }
    private val mirrored = mutableMapOf<Triple<String, String, String>, MirroredCookie>()
    private var cookieWrite: CompletableDeferred<Boolean>? = null
    private var active = true

    init {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        if (!supportsIsolatedWebProfiles()) throw NetworkHostFailure(
            M2HostErrorCode.CAPABILITY_UNAVAILABLE, M2HostErrorPhase.RUNTIME, "Isolated WebView profiles are unavailable")
        name = ReaderWebProfilePool.acquire(purpose)
        view = WebView(context)
        WebViewCompat.setProfile(view, name)
        browserProfile = WebViewCompat.getProfile(view)
        manager = browserProfile.cookieManager
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        manager.setAcceptThirdPartyCookies(view, false)
    }

    suspend fun seed(cookies: List<Pair<String, String>>) = withTimeout(10_000) {
        val requested = cookies.map { (url, header) ->
            val origin = httpUrl(url)
            MirroredCookie(origin, Cookie.parse(origin, header) ?: throw NetworkHostFailure.invalid("Invalid scoped cookie"))
        }.associateBy(MirroredCookie::key)
        for (key in mirrored.keys.toSet() - requested.keys) {
            val obsolete = mirrored.getValue(key)
            val expired = obsolete.cookie.newBuilder().expiresAt(0).build()
            writeCookie(obsolete.origin, expired)
            mirrored.remove(key)
        }
        for ((key, wanted) in requested) {
            if (mirrored[key]?.cookie == wanted.cookie) continue
            writeCookie(wanted.origin, wanted.cookie)
            mirrored[key] = wanted
        }
    }

    private suspend fun writeCookie(origin: HttpUrl, cookie: Cookie) {
        if (!active) throw CancellationException()
        val write = CompletableDeferred<Boolean>()
        check(cookieWrite == null)
        cookieWrite = write
        try {
            manager.setCookie(origin.toString(), cookie.toString()) { write.complete(it) }
            if (!write.await()) throw NetworkHostFailure.denied("Browser refused a scoped cookie")
        } finally {
            cookieWrite = null
        }
    }

    @SuppressLint("RequiresFeature")
    fun read(origin: HttpUrl, prior: List<Cookie>): List<Cookie> {
        val observed = if (WebViewFeature.isFeatureSupported(WebViewFeature.GET_COOKIE_INFO)) {
            CookieManagerCompat.getCookieInfo(manager, origin.toString()).mapNotNull { Cookie.parse(origin, it) }
        } else {
            readCookieHeader(origin, prior)
        }
        mirrored.entries.removeAll { it.value.cookie.matches(origin) }
        observed.forEach { cookie -> MirroredCookie(origin, cookie).also { mirrored[it.key] = it } }
        return observed
    }

    private fun readCookieHeader(origin: HttpUrl, prior: List<Cookie>): List<Cookie> {
        // On older WebView versions only the name/value header is visible.
        // Preserve metadata of scoped cookies already known to this profile.
        val values = manager.getCookie(origin.toString()).orEmpty().split(";").mapNotNull { pair ->
            val field = pair.trim().split("=", limit = 2)
            if (field.size != 2) null else field[0] to field[1]
        }.toMap()
        val known = prior.filter { values.containsKey(it.name) }.map {
            it.newBuilder().value(values.getValue(it.name)).build()
        }
        val unknown = values.filterKeys { key -> prior.none { it.name == key } }
        return known + unknown.mapNotNull { (key, value) ->
            Cookie.parse(origin, "$key=$value; Path=/${if (origin.isHttps) "; Secure" else ""}")
        }
    }

    @SuppressLint("RequiresFeature")
    fun close() {
        if (!active) return
        active = false
        cookieWrite?.cancel()
        view.stopLoading()
        view.webViewClient = WebViewClient()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
        ReaderWebProfilePool.retire(purpose, name, manager, browserProfile.webStorage)
    }
}

internal open class HttpNavigationClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !allowed(request.url.toString())

    private fun allowed(url: String): Boolean =
        url == "about:blank" || runCatching { httpUrl(url) }.isSuccess
}

internal class PendingWebNavigation(private val view: WebView, private val inlineDocument: Boolean = false) {
    private val terminal = CompletableDeferred<Unit>()
    private var active = true

    init {
        view.webViewClient = object : HttpNavigationClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // A new evaluator owns this one inline document. The provider may
                // report its base URL while WebView.url reports its history entry.
                if (active && this@PendingWebNavigation.view === view && (inlineDocument || view.url == url)) {
                    terminal.complete(Unit)
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (active && request.isForMainFrame) terminal.completeExceptionally(
                    NetworkHostFailure(M2HostErrorCode.NETWORK_ERROR, M2HostErrorPhase.TRANSPORT, "WebView navigation failed", true))
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (active && request.isForMainFrame) terminal.completeExceptionally(NetworkHostFailure.http(response.statusCode))
            }
        }
    }

    suspend fun await() = terminal.await()
    fun retire() { active = false }
    fun cancel() {
        retire()
        terminal.cancel()
    }
}

internal suspend fun WebView.evaluate(script: String): String = suspendCancellableCoroutine { result ->
    try {
        evaluateJavascript(script) { value ->
            if (result.isActive) result.resume(value ?: "null")
        }
    } catch (error: Exception) {
        result.resumeWithException(error)
    }
}

internal data class LoginNavigation(
    val url: String,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = true,
    val progress: Float = 0f,
    val failed: Boolean = false,
    val retired: Boolean = false,
)

internal class SourceLoginSession(
    val sourceId: String,
    val generation: UUID,
    internal val site: IsolatedWebProfile,
    internal val jar: PersistentCookieJar,
    internal val requestedUrl: HttpUrl,
) {
    val view get() = site.view
    private var active = true
    private val loadState = MutableStateFlow(LoginNavigation(requestedUrl.toString()))
    val navigation = loadState.asStateFlow()
    internal val cookieLock = Mutex()
    internal var opening: PendingWebNavigation? = null

    suspend fun navigate(url: String) = withContext(Dispatchers.Main.immediate) {
        check(active) { "Login session retired" }
        view.loadUrl(httpUrl(url).toString())
    }

    fun goBack() { if (active && view.canGoBack()) view.goBack() }
    fun goForward() { if (active && view.canGoForward()) view.goForward() }
    fun refresh() { if (active) view.reload() }

    internal fun updateNavigation(
        url: String? = null,
        progress: Float = loadState.value.progress,
        loading: Boolean = loadState.value.loading,
        failed: Boolean = loadState.value.failed,
    ) {
        if (!active) return
        loadState.value = LoginNavigation(
            url ?: view.url ?: loadState.value.url,
            view.canGoBack(), view.canGoForward(), loading, progress, failed,
        )
    }

    fun retire() {
        active = false
        loadState.value = loadState.value.copy(loading = false, retired = true)
        opening?.cancel()
        site.close()
    }
}

internal class AndroidSourceLogins(private val context: Context) {
    private val sessions = mutableMapOf<String, SourceLoginSession>()

    suspend fun begin(sourceId: String, url: HttpUrl, jar: PersistentCookieJar, timeout: Long = 60_000): SourceLoginSession =
        withContext(Dispatchers.Main.immediate) {
            closeCurrent(sourceId)
            val site = IsolatedWebProfile(context, ReaderWebProfilePurpose.LOGIN)
            val session = SourceLoginSession(sourceId, UUID.randomUUID(), site, jar, url)
            sessions[sourceId] = session
            try {
                val navigation = PendingWebNavigation(site.view)
                session.opening = navigation
                try {
                    withTimeout(timeoutMillis(timeout)) {
                        session.cookieLock.withLock { site.seed(jar.entriesForWeb()) }
                        site.view.loadUrl(url.toString())
                        navigation.await()
                    }
                } finally {
                    navigation.retire()
                    session.opening = null
                }
                if (sessions[sourceId] !== session) throw kotlinx.coroutines.CancellationException()
                session.updateNavigation(progress = 1f, loading = false)
                site.view.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        if (sessions[sourceId] === session) session.updateNavigation(progress = newProgress / 100f)
                    }
                }
                site.view.webViewClient = object : HttpNavigationClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        if (sessions[sourceId] === session) {
                            session.updateNavigation(url, progress = 0f, loading = true, failed = false)
                        }
                    }

                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                        if (sessions[sourceId] === session) session.updateNavigation(url)
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (sessions[sourceId] === session && request.isForMainFrame) {
                            session.updateNavigation(loading = false, failed = true)
                        }
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (sessions[sourceId] === session && request.isForMainFrame) {
                            session.updateNavigation(loading = false, failed = true)
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (sessions[sourceId] === session && view.url == url) {
                            session.updateNavigation(url, progress = 1f, loading = false)
                            // Explicit sync and completion surface any I/O failure.
                            if (session.cookieLock.tryLock()) {
                                try { runCatching { importCookies(session) } } finally { session.cookieLock.unlock() }
                            }
                        }
                    }
                }
                session.cookieLock.withLock { importCookies(session) }
                session
            } catch (failure: Exception) {
                if (sessions[sourceId] === session) closeCurrent(sourceId)
                throw failure
            }
        }

    suspend fun pull(sourceId: String?) {
        if (sourceId != null) withContext(Dispatchers.Main.immediate) {
            val session = sessions[sourceId] ?: return@withContext
            session.cookieLock.withLock {
                if (sessions[sourceId] === session) importCookies(session)
            }
        }
    }

    suspend fun push(sourceId: String?, jar: PersistentCookieJar?) {
        if (sourceId != null && jar != null) withContext(Dispatchers.Main.immediate) {
            val session = sessions[sourceId]?.takeIf { it.jar === jar } ?: return@withContext
            session.cookieLock.withLock {
                if (sessions[sourceId] === session) session.site.seed(jar.entriesForWeb())
            }
        }
    }

    suspend fun finish(session: SourceLoginSession) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        session.cookieLock.withLock {
            if (sessions[session.sourceId] !== session) throw CancellationException()
            try { importCookies(session) } finally { closeCurrent(session.sourceId) }
        }
    }

    suspend fun revoke(sourceId: String) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        closeCurrent(sourceId)
    }

    suspend fun revoke(session: SourceLoginSession) = withContext(NonCancellable + Dispatchers.Main.immediate) {
        if (sessions[session.sourceId] === session) closeCurrent(session.sourceId)
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        sessions.keys.toList().forEach(::closeCurrent)
    }

    private fun importCookies(session: SourceLoginSession) {
        val current = session.view.url?.let { runCatching { httpUrl(it) }.getOrNull() } ?: return
        for (origin in setOf(session.requestedUrl, current)) {
            val prior = session.jar.loadForRequest(origin)
            session.jar.updateFromBrowser(origin, session.site.read(origin, prior))
        }
    }

    private fun closeCurrent(sourceId: String) {
        sessions.remove(sourceId)?.retire()
    }
}
