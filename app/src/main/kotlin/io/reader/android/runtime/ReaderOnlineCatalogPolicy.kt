package io.reader.android.runtime

import org.json.JSONObject
import java.util.Collections

/** Finite SDK DTO/identity checks only. No URL resolution, HTML parsing, content rules or numeric matcher. */
internal object ReaderOnlineCatalogPolicy {
    const val MAX_JSON_BYTES = 4 * 1024 * 1024
    const val MAX_PAGE_BOOKS = 1_000
    fun string(value: JSONObject, name: String): String = value.get(name) as? String ?: error("ONLINE_STRING:$name")
    fun optional(value: JSONObject, name: String): String? =
        if (!value.has(name) || value.isNull(name)) null else string(value, name)
    fun kind(value: JSONObject): ReaderOnlineContentKind = when (optional(value, "contentKind")) {
        "text" -> ReaderOnlineContentKind.TEXT
        "manga" -> ReaderOnlineContentKind.MANGA
        "audio" -> ReaderOnlineContentKind.AUDIO
        null -> ReaderOnlineContentKind.UNKNOWN
        else -> error("ONLINE_CONTENT_KIND")
    }
    fun json(value: JSONObject): String = value.toString().also {
        check(it.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "ONLINE_RESULT_BYTE_BUDGET" }
    }
    fun scope(value: JSONObject, source: ReaderOnlineSourceSelection) {
        check(string(value, "sourceId") == source.sourceId && string(value, "sourceVersion") == source.sourceVersion) {
            "ONLINE_SOURCE_VERSION_CHANGED"
        }
    }
    fun page(session: ReaderOnlineSearchSession, page: Int, value: JSONObject): ReaderOnlineSearchPage {
        require(page in 1..20)
        scope(value, session.source)
        val rows = value.getJSONArray("books")
        check(rows.length() <= MAX_PAGE_BOOKS) { "ONLINE_PAGE_BOOK_BUDGET" }
        val result = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = string(row, "bookId"); val title = string(row, "title")
            check(id.isNotBlank() && title.isNotBlank()) { "ONLINE_BOOK_IDENTITY" }
            ReaderOnlineBook(ShelfBookKey(session.source.sourceId, id), title, optional(row, "author"),
                optional(row, "coverUrl"), optional(row, "intro"), json(row))
        }
        // Preserve Core order, duplicates and all extra candidate fields. No Host deduplication/sort.
        return ReaderOnlineSearchPage(session, page, Collections.unmodifiableList(result), kind(value),
            if (result.isEmpty()) false else null, json(value))
    }
    fun detail(source: ReaderOnlineSourceSelection, selected: ReaderOnlineBook, value: JSONObject): ReaderOnlineDetailPreview {
        scope(value, source)
        val book = value.getJSONObject("book")
        check(selected.key.sourceId == source.sourceId && string(book, "bookId") == selected.key.bookId) { "ONLINE_DETAIL_IDENTITY" }
        val variables = value.optJSONObject("variables") ?: JSONObject().also {
            check(!value.has("variables")) { "ONLINE_VARIABLES_OBJECT" }
        }
        val retained = variables.keys().asSequence().associateWith { string(variables, it) }
        return ReaderOnlineDetailPreview(selected.key, source.sourceVersion, kind(value), string(book, "title"),
            string(book, "author"), optional(book, "coverUrl"), optional(book, "intro"), optional(book, "kind"),
            optional(book, "lastChapter"), optional(value, "tocUrl"), Collections.unmodifiableMap(retained), json(book), json(value))
    }
}
