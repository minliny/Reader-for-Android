package com.reader.host

import com.reader.android.data.adapter.CookieStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * `cookie.clear` capability handler. Core sends `{url?}`:
 *  - When `url` is non-empty, clears cookies for that URL only
 *    ([CookieStore.clear]).
 *  - When `url` is absent/empty, clears all cookies
 *    ([CookieStore.clearAll]).
 *
 * Returns `host.complete` with `{cleared: true, scope: "<url>" | "all"}`.
 */
class CookieClearHandler(
    private val cookieStore: CookieStore
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val url = params.optString("url", "")
        try {
            if (url.isNotEmpty()) {
                runBlocking { cookieStore.clear(url) }
            } else {
                runBlocking { cookieStore.clearAll() }
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "$CAPABILITY failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("cleared", true)
        result.put("scope", if (url.isNotEmpty()) url else "all")
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cookie.clear"
        private const val INTERNAL = "INTERNAL"
    }
}
