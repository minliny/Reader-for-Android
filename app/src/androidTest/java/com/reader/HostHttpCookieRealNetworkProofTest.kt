package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieScope
import com.reader.android.data.adapter.CookieStore
import com.reader.host.CookieGetHandler
import com.reader.host.CookieSetHandler
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.HttpExecuteHandler
import com.reader.host.OkHttpHostTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 real-network proof for the `http.execute` + `login_cookie` lanes.
 *
 * Verifies [OkHttpHostTransport] (real OkHttp, already production-ready)
 * against real public endpoints, and verifies the `cookie.set` → `cookie.get`
 * round-trip through a real [CookieStore]. Network failures map to structured
 * errors (not thrown exceptions).
 *
 * This is an executor-tier proof, NOT an App-level proof.
 * `canEnterMainline` remains false.
 */
@RunWith(AndroidJUnit4::class)
class HostHttpCookieRealNetworkProofTest {

    /**
     * Real GET `https://www.w3.org/Icons/w3c_main.png` → 2xx + non-empty
     * finalUrl. Proves [OkHttpHostTransport] does real TLS + HTTP on-device.
     */
    @Test
    fun realHttpGetReturns2xxAndFinalUrl() {
        val transport = OkHttpHostTransport(OkHttpHostTransport.defaultClient())
        val adapter = HostAdapter()
        adapter.register(HttpExecuteHandler.CAPABILITY, HttpExecuteHandler(transport))

        val request = HostRequest(
            1L,
            5001L,
            HttpExecuteHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://www.w3.org/Icons/w3c_main.png")
                put("method", "GET")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "real http.execute must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        val status = result.optInt("status")
        assertTrue(
            "status must be 2xx, got: $status",
            status in 200..299
        )
        assertTrue(
            "finalUrl must be non-empty, got: ${result.optString("finalUrl")}",
            result.optString("finalUrl").isNotEmpty()
        )
    }

    /**
     * `.invalid` TLD → DNS failure → structured error. Must NOT throw.
     */
    @Test
    fun invalidUrlReturnsStructuredError() {
        val transport = OkHttpHostTransport(OkHttpHostTransport.defaultClient())
        val adapter = HostAdapter()
        adapter.register(HttpExecuteHandler.CAPABILITY, HttpExecuteHandler(transport))

        val request = HostRequest(
            1L,
            5002L,
            HttpExecuteHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://nonexistent.invalid/")
                put("method", "GET")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "invalid URL must error (not throw), got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be INTERNAL for network failure",
            "INTERNAL",
            error.code()
        )
    }

    /**
     * `cookie.set` writes a cookie to the `https://www.w3.org/` scope, then
     * `cookie.get` reads it back. Proves the cookie lane round-trips through a
     * real [CookieStore] (handler tier — the cookie is test-set, not
     * server-set, to keep the proof hermetic).
     */
    @Test
    fun cookieSetGetRoundTrip() = runBlocking {
        val cookieStore = RealNetworkProofCookieStore()
        val adapter = HostAdapter()
        adapter.register(CookieSetHandler.CAPABILITY, CookieSetHandler(cookieStore))
        adapter.register(CookieGetHandler.CAPABILITY, CookieGetHandler(cookieStore))

        val setRequest = HostRequest(
            1L,
            5003L,
            CookieSetHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://www.w3.org/")
                put("cookie", JSONObject().apply {
                    put("name", "proof")
                    put("value", "w3c-123")
                    put("domain", "www.w3.org")
                    put("path", "/")
                    put("secure", true)
                    put("httpOnly", false)
                })
            }.toString()
        )
        val setReply = adapter.dispatch(setRequest)
        assertTrue(
            "cookie.set must complete, got: ${setReply.kind()}",
            setReply.isComplete()
        )

        val getRequest = HostRequest(
            2L,
            5004L,
            CookieGetHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://www.w3.org/")
            }.toString()
        )
        val getReply = adapter.dispatch(getRequest)
        assertTrue(
            "cookie.get must complete, got: ${getReply.kind()}",
            getReply.isComplete()
        )
        val result = JSONObject((getReply as HostReply.Complete).resultJson())
        assertTrue(
            "cookies array must be present",
            result.has("cookies")
        )
        val cookies = result.getJSONArray("cookies")
        assertTrue(
            "must get at least 1 cookie, got: ${cookies.length()}",
            cookies.length() > 0
        )
        var found = false
        for (i in 0 until cookies.length()) {
            val c = cookies.getJSONObject(i)
            if (c.getString("name") == "proof") {
                assertEquals("cookie value must match", "w3c-123", c.getString("value"))
                assertEquals("cookie domain must match", "www.w3.org", c.getString("domain"))
                found = true
            }
        }
        assertTrue("cookie.get must return the proof cookie set by cookie.set", found)
    }
}

/**
 * In-memory [CookieStore] for hermetic cookie-lane proof. Mirrors the
 * `HostLoginCookieProofTest` pattern — does not touch the on-device WebView
 * CookieManager. Renamed to avoid a same-package redeclaration conflict with
 * `HostLoginCookieProofTest`'s private `InMemoryCookieStore`.
 */
private class RealNetworkProofCookieStore : CookieStore {
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
