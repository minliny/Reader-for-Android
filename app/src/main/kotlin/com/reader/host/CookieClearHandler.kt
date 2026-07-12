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
 * Returns canonical `host.complete` with `{cleared: true}`.
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
        val scope = sequenceOf("url", "domain", "sessionId")
            .map { params.optString(it, "") }
            .firstOrNull { it.isNotBlank() }
        try {
            if (scope != null) {
                runBlocking { cookieStore.clear(scope) }
            } else {
                runBlocking { cookieStore.clearAll() }
            }
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "$CAPABILITY failed: ${e.message}", true)
        }
        val result = JSONObject()
        result.put("cleared", true)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cookie.clear"
        private const val INTERNAL = "INTERNAL"
    }
}
