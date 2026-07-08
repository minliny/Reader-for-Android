package com.reader.android.data.repository

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.data.storage.ReadingProgressDao
import com.reader.api.Chapter

/**
 * P0-3: Reading progress persistence repository. Wraps [ReadingProgressDao] and handles
 * the field mapping between the in-memory reader state (`bookUrl`, `chapterIndex`, `page`,
 * `progress`) and the Room entity ([ReadingProgress]).
 *
 * `scrollPosition` in the entity stores the `progress` float (0f..1f) — the naming differs
 * but the semantics match (fractional reading position within the chapter).
 *
 * Process-restart recovery: [getProgress] reads from Room, so re-entering a book after
 * the process was killed restores the last-saved `chapterIndex` / `page` / `progress`.
 */
class ReadingProgressRepository(private val dao: ReadingProgressDao) {

    suspend fun getProgress(bookUrl: String): ReadingProgress? = dao.getByUrl(bookUrl)

    /**
     * Persist the current reading position. Callers should pass the latest known
     * `chapterIndex` / `page` / `progress` (from [com.reader.ui.shell.ReaderContext])
     * plus the loaded [Chapter] / `totalChapters` so the entity fields are complete.
     */
    suspend fun saveProgress(
        bookUrl: String,
        bookName: String,
        chapterIndex: Int,
        page: Int,
        progress: Float,
        chapter: Chapter?,
        totalChapters: Int,
        author: String? = null
    ) {
        dao.upsert(
            ReadingProgress(
                bookUrl = bookUrl,
                bookName = bookName,
                author = author,
                currentChapterUrl = chapter?.url ?: "",
                currentChapterTitle = chapter?.title ?: "",
                chapterIndex = chapterIndex,
                totalChapters = totalChapters,
                page = page,
                scrollPosition = progress,
                lastReadTime = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(bookUrl: String) = dao.delete(bookUrl)
}
