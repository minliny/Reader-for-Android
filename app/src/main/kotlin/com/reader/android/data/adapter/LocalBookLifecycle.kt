package com.reader.android.data.adapter

enum class ReimportStrategy {
    REPLACE,
    MERGE_KEEP_PROGRESS,
    SKIP_IF_EXISTS
}

object LocalBookLifecycle {

    fun shouldReimport(existing: Boolean, strategy: ReimportStrategy): Boolean {
        return when (strategy) {
            ReimportStrategy.SKIP_IF_EXISTS -> !existing
            else -> true
        }
    }

    fun shouldKeepProgress(strategy: ReimportStrategy): Boolean {
        return strategy == ReimportStrategy.MERGE_KEEP_PROGRESS
    }

    fun getUrlsToCleanup(sourceUri: String, chapterCount: Int): List<String> {
        return (0 until chapterCount).map { LocalBookProgressMapper.chapterUri(sourceUri, it) }
    }

    fun getProgressId(sourceUri: String): String = sourceUri
}
