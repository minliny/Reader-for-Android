package com.reader.android.ui.reader

/**
 * In-memory cache read/write for chapter content.
 * TODO: connect to Room ChapterCacheManager/DAO when DI/runtime is available.
 */
class ReaderCacheSaveAdapter(
    private val cached: MutableMap<String, CachedContent> = mutableMapOf()
) {
    data class CachedContent(
        val contentUrl: String,
        val content: String,
        val title: String?,
        val cachedAt: Long = System.currentTimeMillis()
    )

    val adapter: ReaderCacheLocalStateAdapter
        get() = ReaderCacheLocalStateAdapter(cachedUrls = cached.keys)

    /** Check cache before calling CoreBridge. */
    fun read(url: String): CachedContent? {
        if (url.isBlank()) return null
        return cached[url]
    }

    /** Write cache after successful getContent. */
    fun write(url: String, content: String, title: String?) {
        if (url.isBlank()) return
        cached[url] = CachedContent(url, content, title)
    }

    /** Check if cache hit is available. */
    fun hasCache(url: String): Boolean = url.isNotBlank() && url in cached

    /** Evict single entry. */
    fun evict(url: String) {
        cached.remove(url)
    }

    fun clear() = cached.clear()
}
