package com.reader.android.data.storage

data class PrefetchStrategy(
    val chaptersAhead: Int = 2,
    val chaptersBehind: Int = 0,
    val enabled: Boolean = true,
    val wifiOnly: Boolean = false,
    val maxCacheChapters: Int = 20
) {
    init {
        require(chaptersAhead in 0..10) { "chaptersAhead must be 0..10" }
        require(maxCacheChapters in 1..100) { "maxCacheChapters must be 1..100" }
    }

    companion object {
        val DEFAULT = PrefetchStrategy()
        val AGGRESSIVE = PrefetchStrategy(chaptersAhead = 5, maxCacheChapters = 50)
        val OFF = PrefetchStrategy(enabled = false)
    }
}
