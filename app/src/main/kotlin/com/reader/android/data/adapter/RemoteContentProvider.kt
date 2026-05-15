package com.reader.android.data.adapter

enum class ContentMode { FULL_DOWNLOAD, STREAM, CHAPTER_ON_DEMAND }

data class RemoteContentProvider(
    val sourceUrl: String,
    val mode: ContentMode = ContentMode.CHAPTER_ON_DEMAND,
    val cacheKeyPrefix: String = "remote"
) {
    fun cacheKey(contentUrl: String): String = "$cacheKeyPrefix:$sourceUrl:$contentUrl"
}
