package com.reader.android.data.repository

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.data.storage.ReadingProgressDao

/**
 * A fully validated Core `reading.progress.update` success projected into the
 * Android read model. Instances are created only after the Host has matched
 * the complete returned Core row to its correlation-scoped pending location.
 */
internal data class ConfirmedReadingProgress(
    val sourceId: String,
    val bookId: String,
    val bookName: String,
    val author: String?,
    val chapterUrl: String,
    val chapterTitle: String,
    /** TOC list position used by the legacy Android reader surface. */
    val chapterPosition: Int,
    val totalChapters: Int,
    val pageIndex: Int,
    val chapterOffset: Long,
    val chapterProgress: Double,
    val locationRevision: String,
    /** Unix seconds echoed by Core's confirmed current row. */
    val updatedAt: Long
) {
    init {
        require(sourceId.isNotBlank())
        require(bookId.isNotBlank())
        require(chapterPosition >= 0)
        require(totalChapters > 0 && chapterPosition < totalChapters)
        require(pageIndex >= 0)
        require(chapterOffset >= 0L)
        require(chapterProgress.isFinite() && chapterProgress in 0.0..1.0)
        require(locationRevision.isNotBlank())
        require(updatedAt > 0L)
    }
}

/**
 * Room-backed Android projection of Core-owned reading progress.
 *
 * Core is the only canonical writer. This repository never constructs or
 * sends a Core DTO and never falls back to Room after a Core failure. Room is
 * intentionally a lossy mirror for recent-books and legacy restore surfaces;
 * it is updated only from [ConfirmedReadingProgress].
 */
class ReadingProgressRepository(private val dao: ReadingProgressDao) {

    suspend fun getProgress(bookUrl: String): ReadingProgress? = dao.getByUrl(bookUrl)

    suspend fun getRecent(limit: Int = 20): List<ReadingProgress> = dao.getRecent(limit)

    suspend fun getAll(): List<ReadingProgress> = dao.getAll()

    internal suspend fun mirrorConfirmedProgress(progress: ConfirmedReadingProgress) {
        dao.upsert(
            ReadingProgress(
                bookUrl = progress.bookId,
                bookName = progress.bookName,
                author = progress.author,
                currentChapterUrl = progress.chapterUrl,
                currentChapterTitle = progress.chapterTitle,
                chapterIndex = progress.chapterPosition,
                totalChapters = progress.totalChapters,
                page = progress.pageIndex,
                scrollPosition = progress.chapterProgress.toFloat(),
                lastReadTime = Math.multiplyExact(progress.updatedAt, 1_000L)
            )
        )
    }

    /** Delete only the Android projection; canonical Core storage is untouched. */
    suspend fun deleteLocalMirror(bookUrl: String) = dao.delete(bookUrl)
}
