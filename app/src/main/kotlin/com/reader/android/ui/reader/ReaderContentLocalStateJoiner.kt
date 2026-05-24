package com.reader.android.ui.reader

/**
 * Joins cache/offline state into content state.
 * Uses [ReaderCacheLocalStateAdapter] for cache queries.
 */
data class ReaderContentLocalStateJoiner(
    val cache: ReaderCacheLocalStateAdapter = ReaderCacheLocalStateAdapter.Empty
) {
    fun cacheStatus(contentUrl: String): ReaderCacheLocalStateAdapter.CacheStatus =
        cache.statusFor(contentUrl)

    fun isOfflineAvailable(contentUrl: String): Boolean =
        cache.isOfflineAvailable(contentUrl)

    /** Enrich content state with cache information. */
    fun enrich(request: ContentRequest): Map<String, Any> = mapOf(
        "isOfflineAvailable" to isOfflineAvailable(request.chapterUrl),
        "cacheStatus" to cacheStatus(request.chapterUrl).name
    )
}
