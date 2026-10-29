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
    fun kind(value: JSONObject): ReaderOnlineContentKind {
        if (!value.has("contentKind")) return ReaderOnlineContentKind.TEXT
        if (value.isNull("contentKind")) return ReaderOnlineContentKind.UNKNOWN
        return when (string(value, "contentKind")) {
            "text" -> ReaderOnlineContentKind.TEXT
            "manga" -> ReaderOnlineContentKind.MANGA
            "audio" -> ReaderOnlineContentKind.AUDIO
            else -> error("ONLINE_CONTENT_KIND")
        }
    }
    fun json(value: JSONObject): String = value.toString().also {
        check(it.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "ONLINE_RESULT_BYTE_BUDGET" }
    }
    fun scope(value: JSONObject, source: ReaderOnlineSourceSelection) {
        val returnedVersion = when {
            !value.has("sourceVersion") -> null
            value.isNull("sourceVersion") -> error("ONLINE_SOURCE_VERSION_CHANGED")
            else -> string(value, "sourceVersion")
        }
        check(string(value, "sourceId") == source.sourceId && (returnedVersion == null || returnedVersion == source.sourceVersion)) {
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
        val contentKind = kind(value)
        check(contentKind != ReaderOnlineContentKind.UNKNOWN) { "ONLINE_CONTENT_KIND" }
        return ReaderOnlineSearchPage(session, page, Collections.unmodifiableList(result), contentKind,
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
