package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject

/** Source-only Core manga adapter. Host image transfer, decode, cache and zoom are separate. */
internal class ReaderMangaService(private val transport: CoreTransport) {
    fun pagesExtract(request: ReaderMangaExtractRequest): ReaderMangaPagesExtract {
        check(request.chapter.sourceId.isNotBlank()) { "MANGA_SOURCE_ID" }
        check(request.chapter.bookId.isNotBlank()) { "MANGA_BOOK_ID" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterTitle", request.chapterTitle)
            .put("chapterResponse", request.chapterResponse)
            .put("chapterRequest", JSONObject.NULL)
            .put("source", JSONObject.NULL)
            .put("chapterUrl", request.chapterUrl ?: JSONObject.NULL)
            .put("variables", JSONObject().also { values -> request.variables.forEach { (key, value) -> values.put(key, value) } })
        return ReaderMangaPolicy.pagesExtract(transport.request("manga.pages.extract", params), request.chapter)
    }

    fun chapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData {
        check(request.chapter.sourceId.isNotBlank()) { "MANGA_SOURCE_ID" }
        check(request.chapter.bookId.isNotBlank()) { "MANGA_BOOK_ID" }
        check(request.chapterIndex >= 0) { "MANGA_CHAPTER_INDEX" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterIndex", request.chapterIndex)
            .put("policy", request.policy.wire)
        request.expectedManifestVersion?.let { params.put("expectedManifestVersion", it) }
        request.preparationRevision?.let { params.put("preparationRevision", it) }
        return ReaderMangaPolicy.chapterData(transport.request("manga.chapter.get", params), request)
    }

    fun pagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow {
        check(request.chapter.sourceId.isNotBlank() && request.chapter.bookId.isNotBlank() && request.chapter.chapterId.isNotBlank()) {
            "MANGA_CHAPTER_IDENTITY"
        }
        check(request.manifestVersion.isNotBlank()) { "MANGA_MANIFEST_VERSION" }
        check(request.start >= 0 && request.limit in 1..32) { "MANGA_WINDOW_RANGE" }
        val params = JSONObject()
            .put("sourceId", request.chapter.sourceId)
            .put("bookId", request.chapter.bookId)
            .put("chapterId", request.chapter.chapterId)
            .put("manifestVersion", request.manifestVersion)
            .put("start", request.start)
            .put("limit", request.limit)
        return ReaderMangaPolicy.pageWindow(transport.request("manga.pages.window", params), request)
    }
}
