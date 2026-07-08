package com.reader.android.data.adapter

import java.net.URI

data class CookieRecord(
    val name: String,
    val value: String,
    val domain: String,
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expiresAt: Long? = null
)

data class CookieScope(
    val sourceUrl: String,
    val cookies: List<CookieRecord> = emptyList()
) {
    fun getCookieString(): String = cookies.joinToString("; ") { "${it.name}=${it.value}" }
}

interface CookieStore {
    suspend fun get(sourceUrl: String): CookieScope
    suspend fun save(sourceUrl: String, cookies: List<CookieRecord>)
    suspend fun clear(sourceUrl: String)
    suspend fun clearAll()
}

class FakeCookieStore : CookieStore {
    private val store = mutableMapOf<String, MutableList<CookieRecord>>()

    override suspend fun get(sourceUrl: String): CookieScope {
        val exact = store[sourceUrl]?.toList()
        if (exact != null) return CookieScope(sourceUrl, exact)

        val host = sourceUrl.hostOrDomain()
        if (host.isEmpty()) return CookieScope(sourceUrl)
        val matched = store
            .filterKeys { it.hostOrDomain() == host }
            .values
            .flatten()
        return CookieScope(sourceUrl, matched)
    }

    override suspend fun save(sourceUrl: String, cookies: List<CookieRecord>) {
        store.getOrPut(sourceUrl) { mutableListOf() }.let { existing ->
            cookies.forEach { newCookie ->
                existing.removeAll { it.name == newCookie.name && it.domain == newCookie.domain }
                existing.add(newCookie)
            }
        }
    }

    override suspend fun clear(sourceUrl: String) {
        if (store.remove(sourceUrl) != null) return
        val host = sourceUrl.hostOrDomain()
        if (host.isNotEmpty()) {
            store.keys.filter { it.hostOrDomain() == host }.forEach { store.remove(it) }
        }
    }

    override suspend fun clearAll() {
        store.clear()
    }

    private fun String.hostOrDomain(): String {
        return try {
            URI(this).host ?: this.removePrefix("http://").removePrefix("https://").substringBefore("/")
        } catch (_: Exception) {
            this.removePrefix("http://").removePrefix("https://").substringBefore("/")
        }
    }
}
