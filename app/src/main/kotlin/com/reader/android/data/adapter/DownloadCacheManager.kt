package com.reader.android.data.adapter

import com.reader.android.data.storage.ChapterCacheManager

class DownloadCacheManager(
    private val cache: ChapterCacheManager,
    private val provider: RemoteContentProvider
) {
    private val tempFiles = mutableSetOf<String>()

    suspend fun downloadAndCache(contentUrl: String, content: String, title: String? = null, nextUrl: String? = null) {
        val key = provider.cacheKey(contentUrl)
        cache.put(key, content, title, nextUrl)
    }

    suspend fun getCached(contentUrl: String) = cache.get(provider.cacheKey(contentUrl))

    fun markTemp(key: String) { tempFiles.add(key) }

    fun isTemp(key: String): Boolean = key in tempFiles

    suspend fun cleanupTemp() {
        tempFiles.forEach { key ->
            val cached = cache.get(key)
            if (cached != null) {
                // Temp files can be evicted immediately if not marked permanent
                tempFiles.remove(key)
            }
        }
    }
}
