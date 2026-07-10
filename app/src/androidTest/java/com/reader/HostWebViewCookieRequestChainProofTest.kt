package com.reader

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.data.adapter.AndroidCookieManagerStore
import com.reader.host.AndroidWebViewExecutor
import com.reader.host.CookieGetHandler
import com.reader.host.CookieSetHandler
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HttpExecuteHandler
import com.reader.host.OkHttpHostTransport
import com.reader.host.WebViewEvaluateJavaScriptHandler
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B5 — WebView/CookieManager/HostRequest chain instrumented proof.
 *
 * Verifies the full chain is wired with REAL platform components (not mocks):
 *  1. `http.execute` (via [OkHttpHostTransport]) fetches a URL that returns a
 *     `Set-Cookie` header — [HttpExecuteHandler] parses the cookie from the
 *     response.
 *  2. `cookie.set` (via [AndroidCookieManagerStore]) persists the cookie to the
 *     real `android.webkit.CookieManager` — the same singleton the WebView
 *     reads from.
 *  3. `CookieManager.getInstance().getCookie(url)` returns the cookie — direct
 *     platform verification.
 *  4. `cookie.get` (via [AndroidCookieManagerStore]) reads the cookie back
 *     through the same CookieManager.
 *  5. `webview.evaluateJavaScript` (via [AndroidWebViewExecutor] bound to a
 *     real Activity-attached [WebView]) loads an HTML document with a matching
 *     `baseUrl` and evaluates `document.cookie` — the cookie set in step 2 is
 *     visible to the WebView because they share `CookieManager.getInstance()`.
 *
 * **What this proves**: the three host capabilities (`http.execute`,
 * `cookie.set/get`, `webview.evaluateJavaScript`) are not isolated stubs —
 * they share a single `CookieManager` instance, so a cookie captured from an
 * HTTP response and persisted via `cookie.set` is automatically visible to the
 * WebView's JavaScript context. This is the production wiring used by
 * [com.reader.api.ReaderCoreClient.init] (where `AppProvider.cookieStore` is
 * an `AndroidCookieManagerStore` and `WebView` uses the same CookieManager).
 *
 * **Proof tier**: real platform components (CookieManager, OkHttp, WebView,
 * ActivityScenario). MockWebServer is used only to provide a hermetic HTTP
 * endpoint that returns `Set-Cookie` — the cookie, CookieManager, and WebView
 * are all real.
 *
 * **Mirrors**: [HostHttpCookieRealNetworkProofTest] (http.execute + cookie
 * round-trip) + [HostWebViewRebindProofTest] (Activity-attached WebView) +
 * [HostLoginCookieProofTest] (MockWebServer + cookie handlers).
 */
@RunWith(AndroidJUnit4::class)
class HostWebViewCookieRequestChainProofTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cookieStore: AndroidCookieManagerStore

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        // Real AndroidCookieManagerStore — wraps CookieManager.getInstance(),
        // the same singleton the WebView reads from.
        cookieStore = AndroidCookieManagerStore()
        // Clean CookieManager state so each test starts fresh.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    /**
     * Simplified chain proof: `cookie.set` → CookieManager → `cookie.get`
     * round-trip through the real [AndroidCookieManagerStore].
     *
     * Proves that `cookie.set` writes to `CookieManager.getInstance()` and
     * `cookie.get` reads from the same instance — the foundation of the
     * WebView/CookieManager share.
     */
    @Test
    fun cookieSetGetRoundTripThroughAndroidCookieManager() {
        val adapter = HostAdapter()
        adapter.register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
        adapter.register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))

        val cookieUrl = "https://chain-proof.test/"

        // 1. cookie.set — persist via AndroidCookieManagerStore → CookieManager
        val setReply = adapter.dispatch(
            HostRequest(
                1L,
                8001L,
                CookieSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("url", cookieUrl)
                    put("cookie", JSONObject().apply {
                        put("name", "proof")
                        put("value", "round-trip-123")
                        put("domain", "chain-proof.test")
                        put("path", "/")
                    })
                }.toString()
            )
        )
        assertTrue(
            "cookie.set must complete, got: ${setReply.kind()}",
            setReply.isComplete()
        )

        // 2. Verify CookieManager.getInstance() has the cookie (platform-level)
        val cmCookie = CookieManager.getInstance().getCookie(cookieUrl)
        assertTrue(
            "CookieManager must contain proof cookie, got: $cmCookie",
            cmCookie?.contains("proof=round-trip-123") == true
        )

        // 3. cookie.get — read back through AndroidCookieManagerStore
        val getReply = adapter.dispatch(
            HostRequest(
                1L,
                8002L,
                CookieGetHandler.CAPABILITY,
                JSONObject().apply {
                    put("url", cookieUrl)
                }.toString()
            )
        )
        assertTrue(
            "cookie.get must complete, got: ${getReply.kind()}",
            getReply.isComplete()
        )
        val result = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue("cookies array must be present", result.has("cookies"))
        val cookies = result.getJSONArray("cookies")
        assertTrue(
            "must get at least 1 cookie, got: ${cookies.length()}",
            cookies.length() > 0
        )
        var found = false
        for (i in 0 until cookies.length()) {
            val c = cookies.getJSONObject(i)
            if (c.getString("name") == "proof") {
                assertEquals(
                    "cookie value must match",
                    "round-trip-123",
                    c.getString("value")
                )
                found = true
            }
        }
        assertTrue("cookie.get must return the proof cookie set by cookie.set", found)
    }

    /**
     * Full chain proof: `http.execute` → `cookie.set` → CookieManager →
     * `cookie.get` → WebView `document.cookie`.
     *
     * This is the B5 proof: all three host capabilities (http.execute,
     * cookie.set/get, webview.evaluateJavaScript) share a single
     * `CookieManager.getInstance()`. A cookie captured from an HTTP response
     * and persisted via `cookie.set` is visible to the WebView's JavaScript
     * context.
     */
    @Test
    fun fullChainHttpExecuteCookieSetCookieGetWebViewShare() {
        // ── Setup: MockWebServer returns Set-Cookie ──
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("Set-Cookie", "proof=chain-123; Path=/")
        )

        val mockUrl = mockWebServer.url("/").toString()
        // Use a stable HTTPS URL for the cookie scope so the WebView's
        // document.cookie reflects it (loadDataWithBaseURL with this baseUrl
        // makes the page's origin match the cookie's domain).
        val cookieUrl = "https://chain-proof.test/"

        val adapter = HostAdapter()
        adapter.register(
            HttpExecuteHandler.CAPABILITY,
            HttpExecuteHandler(OkHttpHostTransport(OkHttpHostTransport.defaultClient(null)))
        )
        adapter.register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
        adapter.register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))

        // ── Step 1: http.execute → MockWebServer returns Set-Cookie ──
        val httpReply = adapter.dispatch(
            HostRequest(
                1L,
                8003L,
                HttpExecuteHandler.CAPABILITY,
                JSONObject().apply {
                    put("url", mockUrl)
                    put("method", "GET")
                }.toString()
            )
        )
        assertTrue(
            "http.execute must complete, got: ${httpReply.kind()}",
            httpReply.isComplete()
        )
        val httpResult = JSONObject((httpReply as HostReply.Complete).resultJson())
        assertTrue(
            "http.execute result must have cookies (parsed from Set-Cookie)",
            httpResult.has("cookies")
        )
        val httpCookies = httpResult.getJSONArray("cookies")
        assertEquals("must capture 1 cookie", 1, httpCookies.length())
        val httpCookie = httpCookies.getJSONObject(0)
        val cookieName = httpCookie.getString("name")
        val cookieValue = httpCookie.getString("value")
        assertEquals("cookie name from Set-Cookie", "proof", cookieName)
        assertEquals("cookie value from Set-Cookie", "chain-123", cookieValue)

        // ── Step 2: cookie.set → persist to CookieManager.getInstance() ──
        val setReply = adapter.dispatch(
            HostRequest(
                1L,
                8004L,
                CookieSetHandler.CAPABILITY,
                JSONObject().apply {
                    put("url", cookieUrl)
                    put("cookie", JSONObject().apply {
                        put("name", cookieName)
                        put("value", cookieValue)
                        put("domain", "chain-proof.test")
                        put("path", "/")
                    })
                }.toString()
            )
        )
        assertTrue(
            "cookie.set must complete, got: ${setReply.kind()}",
            setReply.isComplete()
        )

        // ── Step 3: Verify CookieManager.getInstance() has the cookie ──
        val cmCookie = CookieManager.getInstance().getCookie(cookieUrl)
        assertTrue(
            "CookieManager must contain proof cookie after cookie.set, got: $cmCookie",
            cmCookie?.contains("proof=chain-123") == true
        )

        // ── Step 4: cookie.get → read back through AndroidCookieManagerStore ──
        val getReply = adapter.dispatch(
            HostRequest(
                1L,
                8005L,
                CookieGetHandler.CAPABILITY,
                JSONObject().apply {
                    put("url", cookieUrl)
                }.toString()
            )
        )
        assertTrue(
            "cookie.get must complete, got: ${getReply.kind()}",
            getReply.isComplete()
        )
        val getResult = JSONObject((getReply as HostReply.Complete).resultJson())
        val getCookies = getResult.getJSONArray("cookies")
        var found = false
        for (i in 0 until getCookies.length()) {
            val c = getCookies.getJSONObject(i)
            if (c.getString("name") == "proof") {
                assertEquals(
                    "cookie.get value must match what was set",
                    "chain-123",
                    c.getString("value")
                )
                found = true
            }
        }
        assertTrue("cookie.get must return the proof cookie", found)

        // ── Step 5: WebView shares the cookie via CookieManager ──
        // The WebView is created inside an ActivityScenario so its renderer
        // thread is alive. loadDataWithBaseURL with baseUrl = cookieUrl makes
        // the page's origin match the cookie's domain, so document.cookie
        // returns the cookie persisted by cookie.set.
        ActivityScenario.launch(WebViewHostActivity::class.java).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
                // Ensure the WebView's CookieManager accepts cookies (mirrors
                // AndroidWebViewRuntimeHost.prepareForRuntime).
                CookieManager.getInstance().setAcceptCookie(true)
            }

            val webviewAdapter = HostAdapter()
            webviewAdapter.register(
                WebViewEvaluateJavaScriptHandler.CAPABILITY,
                WebViewEvaluateJavaScriptHandler(AndroidWebViewExecutor(webView))
            )

            val webviewReply = webviewAdapter.dispatch(
                HostRequest(
                    1L,
                    8006L,
                    WebViewEvaluateJavaScriptHandler.CAPABILITY,
                    JSONObject().apply {
                        put("document", JSONObject().apply {
                            put("kind", "html")
                            put("body", "<html><body>chain-proof</body></html>")
                            put("baseUrl", cookieUrl)
                        })
                        put("javaScript", "document.cookie")
                    }.toString()
                )
            )
            assertTrue(
                "webview.evaluateJavaScript must complete, got: ${webviewReply.kind()}",
                webviewReply.isComplete()
            )
            val webviewResult = JSONObject(
                (webviewReply as HostReply.Complete).resultJson()
            )
            assertTrue(
                "result must contain 'value' field",
                webviewResult.has("value")
            )
            val docCookie = webviewResult.optString("value", "")
            assertTrue(
                "WebView document.cookie must contain the proof cookie " +
                    "(shared via CookieManager.getInstance()), got: $docCookie",
                docCookie.contains("proof=chain-123")
            )
        }
    }
}
