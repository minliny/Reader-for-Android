package com.reader.host

import com.reader.android.data.adapter.CookieStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * `cookie.get` capability handler: bridges Core's cookie read requests
 * to the host-owned [CookieStore] (AndroidCookieManagerStore on-device,
 * FakeCookieStore in tests). Core sends `{url}`; this handler returns
 * `host.complete` with `{cookies: [{name, value, domain, path, ...}]}`.
 *
 * CookieStore is a suspend interface; OkHttp/HostAdapter dispatch is synchronous,
 * so we block on [runBlocking] — the poll thread already offloads dispatch to
 * an executor when configured.
 */
class CookieGetHandler(
    private val cookieStore: CookieStore
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error("INTERNAL", "invalid cookie.get params: ${e.message}", false)
        }
        val url = params.optString("url", "")
        if (url.isEmpty()) {
            return HostReply.error("INTERNAL", "cookie.get requires non-empty url", false)
        }
        val scope = try {
            runBlocking { cookieStore.get(url) }
        } catch (e: Exception) {
            return HostReply.error("INTERNAL", "cookie.get failed: ${e.message}", true)
        }
        val cookies = JSONArray()
        scope.cookies.forEach { record ->
            val cookie = JSONObject()
            cookie.put("name", record.name)
            cookie.put("value", record.value)
            cookie.put("domain", record.domain)
            cookie.put("path", record.path)
            if (record.secure) cookie.put("secure", true)
            if (record.httpOnly) cookie.put("httpOnly", true)
            if (record.expiresAt != null) cookie.put("expiresAt", record.expiresAt)
            cookies.put(cookie)
        }
        val result = JSONObject()
        result.put("cookies", cookies)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cookie.get"
    }
}
