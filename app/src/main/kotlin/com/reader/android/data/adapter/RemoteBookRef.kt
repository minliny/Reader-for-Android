package com.reader.android.data.adapter

data class RemoteBookRef(
    val path: String,
    val name: String,
    val format: String = "txt",
    val sizeBytes: Long = 0,
    val lastModified: Long = 0
)

enum class DownloadPolicy {
    FULL_DOWNLOAD,
    STREAM_CHAPTERS,
    CACHE_ON_ACCESS
}

data class RemoteBookCachePolicy(
    val policy: DownloadPolicy = DownloadPolicy.CACHE_ON_ACCESS,
    val maxCacheChapters: Int = 20,
    val evictAfterRead: Boolean = false
)
