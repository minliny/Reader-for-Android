package com.reader.android.data.repository

import com.reader.android.data.storage.ReadingProgress
import com.reader.android.data.storage.ReadingProgressDao
import com.reader.api.Chapter
import com.reader.api.ReaderCoreClient
import org.json.JSONObject

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
 *
 * C1: [saveProgress] delegates to Core `reading.progress.update` (backed by Core's own
 * storage via `put_progress`) first; the Room-backed DAO stays as the fallback path used
 * when the Core bridge is unavailable (e.g. JVM unit tests where [ReaderCoreClient] is
 * not initialized) or the Core call fails.
 */
class ReadingProgressRepository(private val dao: ReadingProgressDao) {

    suspend fun getProgress(bookUrl: String): ReadingProgress? = dao.getByUrl(bookUrl)

    /**
     * P0-3: Returns the most-recently-read books (by `lastReadTime` DESC). Used by the
     * "continue reading" / "recent" surface on the bookshelf. Derived from the same
     * `reading_progress` table as [getProgress] — no separate recent-reading entity.
     */
    suspend fun getRecent(limit: Int = 20): List<ReadingProgress> = dao.getRecent(limit)

    suspend fun getAll(): List<ReadingProgress> = dao.getAll()

    /**
     * Persist the current reading position. Callers should pass the latest known
     * `chapterIndex` / `page` / `progress` (from [com.reader.ui.shell.ReaderContext])
     * plus the loaded [Chapter] / `totalChapters` so the entity fields are complete.
     *
     * C1: Tries Core `reading.progress.update` first; falls back to the Room-backed
     * DAO when the Core bridge is unavailable (JVM tests / Core not initialized).
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
        val entity = ReadingProgress(
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
        // C1: Core has landed `reading.progress.update` (reader-storage put_progress).
        // Try Core first; fall back to Room on any bridge failure (e.g. JVM tests
        // where ReaderCoreClient is not initialized).
        try {
            val params = buildCoreParams(entity)
            ReaderCoreClient.get().sendAndAwait(
                CORE_METHOD, params, CORE_TIMEOUT_MILLIS
            )
            return
        } catch (e: Exception) {
            // Core bridge unavailable — fall back to Room below.
        }
        dao.upsert(entity)
    }

    suspend fun delete(bookUrl: String) = dao.delete(bookUrl)

    private fun buildCoreParams(entity: ReadingProgress): JSONObject = JSONObject().apply {
        put("bookId", entity.bookUrl)
        put("bookName", entity.bookName)
        if (entity.author != null) put("author", entity.author)
        put("chapterUrl", entity.currentChapterUrl)
        put("chapterTitle", entity.currentChapterTitle)
        put("chapterIndex", entity.chapterIndex)
        put("totalChapters", entity.totalChapters)
        put("page", entity.page)
        put("progress", entity.scrollPosition.toDouble())
        put("lastReadTime", entity.lastReadTime)
    }

    companion object {
        private const val CORE_METHOD = "reading.progress.update"
        private const val CORE_TIMEOUT_MILLIS = 10_000L
    }
}
