package com.reader.android.ui.reader

/**
 * In-memory cache state adapter.
 * TODO: connect to Room ChapterCacheManager when DI/runtime is available.
 */
data class ReaderCacheLocalStateAdapter(
    val cachedUrls: Set<String> = emptySet(),
    val staleThresholdMs: Long = 7 * 24 * 60 * 60 * 1000L
) {
    enum class CacheStatus { CACHED, NOT_CACHED, STALE }

    fun statusFor(contentUrl: String): CacheStatus = when {
        contentUrl.isBlank() -> CacheStatus.NOT_CACHED
        contentUrl in cachedUrls -> CacheStatus.CACHED
        else -> CacheStatus.NOT_CACHED
    }

    fun isOfflineAvailable(contentUrl: String): Boolean =
        statusFor(contentUrl) == CacheStatus.CACHED

    companion object {
        val Empty = ReaderCacheLocalStateAdapter()
    }
}
