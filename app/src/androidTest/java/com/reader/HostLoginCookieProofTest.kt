package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieScope
import com.reader.android.data.adapter.CookieStore
import com.reader.api.ReaderCoreClient
import com.reader.host.CookieGetHandler
import com.reader.host.CookieSetHandler
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HttpExecuteHandler
import com.reader.host.OkHttpHostTransport
import kotlinx.coroutines.runBlocking
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
 * Android Host-side proof for the `login_cookie` lane: verifies the three
 * capability handlers (`http.execute`, `cookie.set`, `cookie.get`) wired in
 * [ReaderCoreClient.init] / [ReaderCoreClient.initForTest] honour their JSON
 * contracts end-to-end. Uses MockWebServer + an in-memory [CookieStore] so
 * nothing touches the real WebView CookieManager.
 *
 * JNI / Core-event routing is already proven by [CoreEndToEndTest]; this test
 * isolates the handler layer by invoking handlers directly.
 */
@RunWith(AndroidJUnit4::class)
class HostLoginCookieProofTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cookieStore: CookieStore

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        // In-memory CookieStore to avoid polluting the on-device WebView CookieManager.
        cookieStore = InMemoryCookieStore()
    }

    @After
    fun tearDown() {
        ReaderCoreClient.resetForTest()
        mockWebServer.shutdown()
    }

    /**
     * Proof 1: `http.execute` returns `finalUrl` (the post-redirect URL).
     * MockWebServer enqueues 302 -> /final -> 200 "final page". OkHttp follows
     * the redirect automatically; the `host.complete` result must carry
     * `finalUrl` ending in `/final`, status 200, and the final body.
     */
    @Test
    fun httpExecuteReturnsFinalUrlAfterRedirect() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/final")
                .setBody("")
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("final page")
        )

        val mockBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val fetch = OkHttpHostTransport(OkHttpHostTransport.defaultClient(null))
        val handler = HttpExecuteHandler(fetch)

        val request = HostRequest(
            1L,
            1001L,
            HttpExecuteHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "$mockBaseUrl/redirect")
                put("method", "GET")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue("http.execute must complete, got: ${reply.kind()}", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("result must have finalUrl", result.has("finalUrl"))
        val finalUrl = result.getString("finalUrl")
        assertTrue("finalUrl must end with /final, got: $finalUrl", finalUrl.endsWith("/final"))
        assertEquals("status must be 200", 200, result.getInt("status"))
        assertEquals("body must be final page", "final page", result.getString("body"))
    }

    /**
     * Proof 2: `http.execute` auto-captures `Set-Cookie` headers into
     * `result.cookies`. MockWebServer returns 200 + a Set-Cookie header; the
     * `host.complete` result must contain a parsed cookie with the right
     * name/value.
     */
    @Test
    fun httpExecuteCapturesSetCookieHeader() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("ok")
                .addHeader("Set-Cookie", "session=abc123; Path=/; HttpOnly")
        )

        val mockBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val fetch = OkHttpHostTransport(OkHttpHostTransport.defaultClient(null))
        val handler = HttpExecuteHandler(fetch)

        val request = HostRequest(
            1L,
            1002L,
            HttpExecuteHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "$mockBaseUrl/")
                put("method", "GET")
            }.toString()
        )

        val reply = handler.handle(request)
        assertTrue("http.execute must complete", reply.isComplete())
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertTrue("result must have cookies", result.has("cookies"))
        val cookies = result.getJSONArray("cookies")
        assertEquals("must capture 1 cookie", 1, cookies.length())
        val cookie = cookies.getJSONObject(0)
        assertEquals("cookie name", "session", cookie.getString("name"))
        assertEquals("cookie value", "abc123", cookie.getString("value"))
    }

    /**
     * Proof 3: `cookie.set` + `cookie.get` round-trip through the same
     * [CookieStore]. Set a cookie via `cookie.set`, then read it back via
     * `cookie.get` for the same URL; the returned cookie must match.
     */
    @Test
    fun cookieSetAndGetRoundTrip() = runBlocking {
        // 1. cookie.set
        val setHandler = CookieSetHandler(cookieStore)
        val setRequest = HostRequest(
            1L,
            2001L,
            CookieSetHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/login")
                put("cookie", JSONObject().apply {
                    put("name", "sid")
                    put("value", "session-token-xyz")
                    put("domain", "example.test")
                    put("path", "/")
                    put("secure", true)
                    put("httpOnly", true)
                })
            }.toString()
        )
        val setReply = setHandler.handle(setRequest)
        assertTrue("cookie.set must complete, got: ${setReply.kind()}", setReply.isComplete())
        val setResult = JSONObject((setReply as HostReply.Complete).resultJson())
        assertTrue("cookie.set must return stored=true", setResult.getBoolean("stored"))

        // 2. cookie.get -- read back for the same URL
        val getHandler = CookieGetHandler(cookieStore)
        val getRequest = HostRequest(
            1L,
            2002L,
            CookieGetHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.test/login")
            }.toString()
        )
        val getReply = getHandler.handle(getRequest)
        assertTrue("cookie.get must complete", getReply.isComplete())
        val getResult = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue("cookie.get must return cookies array", getResult.has("cookies"))
        val cookies = getResult.getJSONArray("cookies")
        assertTrue("must get at least 1 cookie", cookies.length() > 0)
        var foundSid = false
        for (i in 0 until cookies.length()) {
            val c = cookies.getJSONObject(i)
            if (c.getString("name") == "sid") {
                assertEquals("sid value", "session-token-xyz", c.getString("value"))
                assertEquals("sid domain", "example.test", c.getString("domain"))
                foundSid = true
            }
        }
        assertTrue("cookie.get must return the sid cookie set by cookie.set", foundSid)
    }
}

/**
 * In-memory [CookieStore] for tests. Avoids touching the on-device WebView
 * CookieManager (AndroidCookieManagerStore), keeping tests hermetic.
 */
private class InMemoryCookieStore : CookieStore {
    private val store = mutableMapOf<String, MutableList<CookieRecord>>()

    override suspend fun get(sourceUrl: String): CookieScope {
        val records = store[sourceUrl] ?: emptyList()
        return CookieScope(sourceUrl, records)
    }

    override suspend fun save(sourceUrl: String, cookies: List<CookieRecord>) {
        val list = store.getOrPut(sourceUrl) { mutableListOf() }
        for (c in cookies) {
            val idx = list.indexOfFirst { it.name == c.name }
            if (idx >= 0) list[idx] = c else list.add(c)
        }
    }

    override suspend fun clear(sourceUrl: String) {
        store.remove(sourceUrl)
    }

    override suspend fun clearAll() {
        store.clear()
    }
}
