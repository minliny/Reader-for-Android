package io.reader.android.runtime.http

import java.net.URI
import java.util.Locale

/** Selected fixed-1fe HttpTransportPolicy decisions, adapted to Kotlin/min24 APIs.
 * Source ranges and hash are recorded in SOURCE.json. HTTP encoding, parsing,
 * multipart, sockets and TLS remain OkHttp/platform implementations. */
internal object ReaderCoreHttpPolicy {
    data class RedirectDecision(val method: String, val keepBody: Boolean)

    fun redirectMethod(status: Int, method: String, hasBody: Boolean): RedirectDecision {
        val upper = method.uppercase(Locale.ROOT)
        val becomesGet = (status == 303 && upper != "HEAD") ||
            ((status == 301 || status == 302) && upper == "POST")
        return if (becomesGet) RedirectDecision("GET", false) else RedirectDecision(method, hasBody)
    }

    fun resolveLocation(currentUrl: String, location: String): String = URI.create(currentUrl).resolve(location).toString()

    fun allowNextRedirect(follow: Boolean, maximum: Int, observed: Int): Boolean = follow && observed < maximum

    fun redirectHeaderNames(names: Set<String>, sameOrigin: Boolean): Set<String> =
        if (sameOrigin) names else names.filterTo(linkedSetOf()) {
            it.trim().lowercase(Locale.ROOT) !in setOf("authorization", "proxy-authorization", "cookie", "origin")
        }

    fun mergeCookieHeader(explicit: String?, jar: String?): String =
        listOfNotNull(explicit?.trim()?.takeIf { it.isNotEmpty() }, jar?.trim()?.takeIf { it.isNotEmpty() }).joinToString("; ")

    fun retryBackoffMillis(base: Long, attempt: Long, maximum: Long, remaining: Long): Long =
        minOf(Math.multiplyExact(base, attempt), maximum, remaining)
}
