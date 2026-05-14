package com.reader.android.data.adapter

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
        return CookieScope(sourceUrl, store[sourceUrl]?.toList() ?: emptyList())
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
        store.remove(sourceUrl)
    }

    override suspend fun clearAll() {
        store.clear()
    }
}
