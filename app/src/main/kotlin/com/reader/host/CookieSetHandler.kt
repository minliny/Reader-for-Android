package com.reader.host

import com.reader.android.data.adapter.CookieRecord
import com.reader.android.data.adapter.CookieStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * `cookie.set` capability handler: bridges Core's cookie write requests
 * to the host-owned [CookieStore]. Core sends `{url, cookie: {name, value,
 * domain?, path?, secure?, httpOnly?, expiresAt?}}`; this handler persists
 * the cookie via [CookieStore.save] and returns `host.complete` with
 * `{stored: true}`.
 */
class CookieSetHandler(
    private val cookieStore: CookieStore
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error("INTERNAL", "invalid cookie.set params: ${e.message}", false)
        }
        val url = params.optString("url", "")
        if (url.isEmpty()) {
            return HostReply.error("INTERNAL", "cookie.set requires non-empty url", false)
        }
        val cookieObj = params.optJSONObject("cookie")
        if (cookieObj == null) {
            return HostReply.error("INTERNAL", "cookie.set requires cookie object", false)
        }
        val name = cookieObj.optString("name", "")
        val value = cookieObj.optString("value", "")
        if (name.isEmpty()) {
            return HostReply.error("INTERNAL", "cookie.set requires non-empty cookie name", false)
        }
        val record = CookieRecord(
            name = name,
            value = value,
            domain = cookieObj.optString("domain", ""),
            path = cookieObj.optString("path", "/"),
            secure = cookieObj.optBoolean("secure", false),
            httpOnly = cookieObj.optBoolean("httpOnly", false),
            expiresAt = if (cookieObj.has("expiresAt")) cookieObj.optLong("expiresAt") else null
        )
        try {
            runBlocking { cookieStore.save(url, listOf(record)) }
        } catch (e: Exception) {
            return HostReply.error("INTERNAL", "cookie.set failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("stored", true)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cookie.set"
    }
}
