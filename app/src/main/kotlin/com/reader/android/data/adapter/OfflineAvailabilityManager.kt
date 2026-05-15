package com.reader.android.data.adapter

enum class EvictionPolicy { LRU, FIFO, TIME_BASED }

data class CacheManifestEntry(
    val cacheKey: String,
    val contentUrl: String,
    val sizeBytes: Long,
    val cachedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

class OfflineAvailabilityManager {
    private val entries = mutableMapOf<String, CacheManifestEntry>()
    var policy: EvictionPolicy = EvictionPolicy.TIME_BASED
    var maxEntries: Int = 50

    fun markCached(key: String, url: String, size: Long) {
        entries[key] = CacheManifestEntry(key, url, size)
    }

    fun isAvailable(key: String): Boolean = key in entries

    fun touch(key: String) {
        entries[key] = entries[key]?.copy(lastAccessedAt = System.currentTimeMillis()) ?: return
    }

    fun evict(): List<String> {
        if (entries.size <= maxEntries) return emptyList()
        val toRemove = when (policy) {
            EvictionPolicy.LRU -> entries.values.sortedBy { it.lastAccessedAt }
            EvictionPolicy.FIFO -> entries.values.sortedBy { it.cachedAt }
            EvictionPolicy.TIME_BASED -> entries.values.sortedBy { it.cachedAt }
        }
        val excess = toRemove.take(entries.size - maxEntries)
        excess.forEach { entries.remove(it.cacheKey) }
        return excess.map { it.cacheKey }
    }

    fun size(): Int = entries.size
}
