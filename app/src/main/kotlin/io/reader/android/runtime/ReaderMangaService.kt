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
}
