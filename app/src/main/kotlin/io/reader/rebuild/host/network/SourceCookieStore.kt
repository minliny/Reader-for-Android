package io.reader.rebuild.host.network

import com.reader.core.protocol.M2HostCookieGetRequest
import com.reader.core.protocol.M2HostCookieGetResponse
import com.reader.core.protocol.M2HostCookieRecord
import com.reader.core.protocol.M2HostCookieSetRequest
import com.reader.core.protocol.M2HostCookieSetResponse
import com.reader.core.protocol.ReaderProtocolJson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class SourceCookieStore(private val directory: File) {
    private val jars = mutableMapOf<String, PersistentCookieJar>()

    @Synchronized fun profile(session: String?, url: HttpUrl? = null, domain: String? = null): PersistentCookieJar {
        val key = session?.takeIf(String::isNotBlank)?.let { "source:$it" }
            ?: (url?.host ?: domain)?.trimStart('.')?.lowercase()?.takeIf(String::isNotBlank)?.let { "domain:$it" }
            ?: throw NetworkHostFailure.invalid("Cookie scope is required")
        return jars.getOrPut(key) {
            val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).hex()
            PersistentCookieJar(File(directory, "$name.json"))
        }
    }

    @Synchronized fun clear(sourceId: String) {
        val jar = profile(sourceId)
        jar.clear()
        jars.remove("source:$sourceId")
    }

    fun get(request: M2HostCookieGetRequest): M2HostCookieGetResponse {
        val target = request.url?.let(::httpUrl)
        val profile = profile(request.sessionId, target, request.domain)
        val records = profile.records(target).filter { record ->
            (request.name == null || record.name == request.name) &&
                (request.domain == null || domainAllows(request.domain, record.domain ?: ""))
        }
        return M2HostCookieGetResponse(records)
    }

    fun set(request: M2HostCookieSetRequest): M2HostCookieSetResponse {
        val model = request.cookie
        val domain = model.domain ?: request.url?.let(::httpUrl)?.host
            ?: throw NetworkHostFailure.invalid("Cookie domain is required")
        val origin = request.url?.let(::httpUrl)
            ?: HttpUrl.Builder().scheme(if (model.secure == true) "https" else "http").host(domain.trimStart('.')).build()
        if (!domainAllows(origin.host, domain)) throw NetworkHostFailure.denied("Cookie domain does not match URL")
        val attributes = buildString {
            append(model.name).append('=').append(model.value.orEmpty())
            append("; Path=").append(model.path ?: "/")
            if (model.domain != null) append("; Domain=").append(domain.trimStart('.'))
            if (model.secure == true) append("; Secure")
            if (model.httpOnly == true) append("; HttpOnly")
            model.sameSite?.let { append("; SameSite=").append(it) }
        }
        val builder = Cookie.parse(origin, attributes)?.newBuilder()
            ?: throw NetworkHostFailure.invalid("Invalid cookie")
        model.expiresAt?.let { builder.expiresAt(Instant.parse(it).toEpochMilli()) }
        profile(request.sessionId, origin, domain).put(origin, builder.build(), model.sameSite)
        return M2HostCookieSetResponse(true)
    }
}

internal class PersistentCookieJar(private val file: File) : CookieJar {
    @Serializable
    private data class Persisted(val origin: String, val header: String, val sameSite: String? = null)
    private data class Key(val name: String, val domain: String, val path: String)
    private data class Entry(val origin: HttpUrl, val cookie: Cookie, val sameSite: String?)
    private val entries = linkedMapOf<Key, Entry>()
    private var retired = false

    init {
        if (file.isFile) {
            check(file.length() <= MAX_FILE) { "Cookie store exceeded size budget" }
            for (stored in ReaderProtocolJson.decodeFromString<List<Persisted>>(file.readText())) {
                val origin = httpUrl(stored.origin)
                Cookie.parse(origin, stored.header)?.let { cookie ->
                    if (cookie.expiresAt > System.currentTimeMillis()) entries[key(cookie)] = Entry(origin, cookie, stored.sameSite)
                }
            }
        }
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        visible(url).map(Entry::cookie).sortedByDescending { it.path.length }

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty() || retired) return
        cookies.forEach { apply(url, it, null) }
        persist()
    }

    @Synchronized fun put(url: HttpUrl, cookie: Cookie, sameSite: String? = null) {
        check(!retired)
        apply(url, cookie, sameSite)
        persist()
    }

    /** Chromium exposes only cookies matched to this URL. Never erase unobserved other scopes. */
    @Synchronized fun updateFromBrowser(url: HttpUrl, cookies: List<Cookie>) {
        if (retired) return
        val previous = visible(url)
        val new = cookies.map(::key).toSet()
        val removed = previous.map { key(it.cookie) }.filterNot(new::contains)
        val changed = cookies.filter { entries[key(it)]?.cookie != it }
        if (removed.isEmpty() && changed.isEmpty()) return
        removed.forEach(entries::remove)
        changed.forEach { apply(url, it, entries[key(it)]?.sameSite) }
        persist()
    }

    @Synchronized fun entriesForWeb(): List<Pair<String, String>> = visible(null).map {
        it.origin.redactAuthorityForCookie() to it.cookie.toString()
    }

    @Synchronized fun records(url: HttpUrl?): List<M2HostCookieRecord> = visible(url).map { entry ->
        val c = entry.cookie
        M2HostCookieRecord(c.name, c.value, c.domain, c.path,
            c.expiresAt.takeIf { c.persistent }?.let { Instant.ofEpochMilli(it).toString() },
            c.httpOnly, c.secure, c.sameSite ?: entry.sameSite)
    }

    @Synchronized fun clear() {
        if (file.exists()) Files.delete(file.toPath())
        entries.clear()
        retired = true
    }

    private fun visible(url: HttpUrl?): List<Entry> = if (retired) emptyList() else entries.values.filter {
        it.cookie.expiresAt > System.currentTimeMillis() && (url == null || it.cookie.matches(url))
    }

    private fun apply(url: HttpUrl, cookie: Cookie, sameSite: String?) {
        val identity = key(cookie)
        if (cookie.expiresAt <= System.currentTimeMillis()) entries.remove(identity)
        else if (cookie.toString().length <= 4_096) {
            entries.remove(identity)
            entries[identity] = Entry(url, cookie, cookie.sameSite ?: sameSite)
            entries.entries.removeAll { it.value.cookie.expiresAt <= System.currentTimeMillis() }
            while (entries.size > MAX_COOKIES) entries.remove(entries.keys.first())
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val previous = visible(null).map { Persisted(it.origin.redactAuthorityForCookie(), it.cookie.toString(), it.sameSite) }
        val data = ReaderProtocolJson.encodeToString(previous).toByteArray()
        check(data.size <= MAX_FILE) { "Cookie store exceeded size budget" }
        val temporary = File.createTempFile(".cookie-", ".partial", file.parentFile)
        try {
            FileOutputStream(temporary).use { it.write(data); it.fd.sync() }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun key(cookie: Cookie) = Key(cookie.name, cookie.domain, cookie.path)

    companion object {
        private const val MAX_FILE = 1024 * 1024
        private const val MAX_COOKIES = 128
    }
}

private fun HttpUrl.redactAuthorityForCookie(): String = newBuilder().encodedPath("/").query(null).fragment(null).build().toString()

internal fun domainAllows(host: String, domain: String): Boolean {
    val normalized = domain.trimStart('.').lowercase()
    return normalized.isNotEmpty() && (host.equals(normalized, ignoreCase = true) || host.lowercase().endsWith(".$normalized"))
}
