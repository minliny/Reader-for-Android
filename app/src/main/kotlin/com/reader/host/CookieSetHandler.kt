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
        val scopeKey = params.optString("url", "").takeIf { it.isNotBlank() }
            ?: params.optString("sessionId", "").takeIf { it.isNotBlank() }
        if (scopeKey == null) {
            return HostReply.error("INTERNAL", "cookie.set requires non-empty url or sessionId", false)
        }
        val cookieValue = params.opt("cookie")
        val record = when (cookieValue) {
            is JSONObject -> cookieValue.toCookieRecord()
            is String -> parseCookieHeader(cookieValue)
            else -> null
        }
        if (record == null) {
            return HostReply.error("INTERNAL", "cookie.set requires cookie object or header string", false)
        }
        if (record.name.isEmpty()) {
            return HostReply.error("INTERNAL", "cookie.set requires non-empty cookie name", false)
        }
        try {
            runBlocking { cookieStore.save(scopeKey, listOf(record)) }
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

private fun JSONObject.toCookieRecord(): CookieRecord = CookieRecord(
    name = optString("name", ""),
    value = optString("value", ""),
    domain = optString("domain", ""),
    path = optString("path", "/"),
    secure = optBoolean("secure", false),
    httpOnly = optBoolean("httpOnly", false),
    expiresAt = optExpiresAt()
)

private fun JSONObject.optExpiresAt(): Long? {
    if (!has("expiresAt") || isNull("expiresAt")) return null
    return when (val raw = opt("expiresAt")) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }
}

private fun parseCookieHeader(header: String): CookieRecord? {
    val parts = header.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    val first = parts.firstOrNull() ?: return null
    val nameValue = first.split("=", limit = 2)
    val name = nameValue.getOrNull(0).orEmpty()
    val value = nameValue.getOrNull(1).orEmpty()
    if (name.isBlank()) return null
    var domain = ""
    var path = "/"
    var secure = false
    var httpOnly = false
    var expiresAt: Long? = null
    parts.drop(1).forEach { part ->
        val attr = part.split("=", limit = 2)
        when (attr[0].lowercase()) {
            "domain" -> domain = attr.getOrNull(1).orEmpty()
            "path" -> path = attr.getOrNull(1).orEmpty().ifBlank { "/" }
            "secure" -> secure = true
            "httponly" -> httpOnly = true
            "expires", "expiresat", "max-age" -> expiresAt = attr.getOrNull(1)?.toLongOrNull()
        }
    }
    return CookieRecord(name, value, domain, path, secure, httpOnly, expiresAt)
}
