package com.reader.api

import org.json.JSONObject

/**
 * Async facade for the book-pipeline Core methods. Mirrors the Legado
 * `WebBook` await signatures:
 *  - `book.search`      <-> `searchBookAwait`
 *  - `book.detail`      <-> `getBookInfoAwait`
 *  - `book.toc`         <-> `getChapterListAwait`
 *  - `chapter.content`  <-> `getBookContentAwait`
 *
 * Each method blocks its calling coroutine on [ReaderCoreClient.sendAndAwait]
 * until Core returns the matching `result`/`error` event, then maps the JSON
 * to Kotlin data classes.
 */
class BookApi(private val client: ReaderCoreClient) {

    suspend fun search(sourceId: String, key: String, page: Int = 1): List<SearchBook> {
        return searchResult(sourceId, key, page).books
    }

    suspend fun searchResult(
        sourceId: String,
        key: String,
        page: Int = 1
    ): BookSearchResult {
        val params = JSONObject().apply {
            put("sourceId", sourceId)
            put("keyword", key)
            put("page", page)
        }
        val result = client.sendAndAwait("book.search", params)
        return parseBookSearchResult(result)
    }

    suspend fun detail(sourceId: String, book: Book): Book {
        return detailResult(sourceId, book).book
    }

    suspend fun detailResult(sourceId: String, book: Book): BookDetailResult {
        val params = buildDetailParams(sourceId, book)
        val result = client.sendAndAwait("book.detail", params)
        return parseBookDetailResult(result)
    }

    suspend fun toc(
        sourceId: String,
        book: Book,
        tocUrl: String = book.tocUrl,
        variables: Map<String, String> = book.variables
    ): List<Chapter> {
        return tocResult(sourceId, book, tocUrl, variables).chapters
    }

    suspend fun tocResult(
        sourceId: String,
        book: Book,
        tocUrl: String = book.tocUrl,
        variables: Map<String, String> = book.variables
    ): BookTocResult {
        val params = buildTocParams(sourceId, book, tocUrl, variables)
        val result = client.sendAndAwait("book.toc", params)
        return parseBookTocResult(result)
    }

    suspend fun content(
        sourceId: String,
        book: Book,
        chapter: Chapter,
        variables: Map<String, String> = mergeVariables(book.variables, chapter.variables)
    ): String {
        val params = buildContentParams(sourceId, book, chapter, variables)
        val result = client.sendAndAwait("chapter.content", params)
        return parseContentResult(result)
    }

    /**
     * reader.location.resolve — compute canonical location from chapter anchor + layout.
     * Pure function, no host bus (core-bridge-mapping.md §4.5).
     * Returns { canonicalLocation: { ... }, resolverVersion, resolved, reflow: { ... } }.
     */
    suspend fun resolveLocation(
        bookId: String,
        chapterIndex: Int,
        chapterOffset: Long,
        chapterProgress: Double,
        viewportWidth: Int,
        viewportHeight: Int,
        fontScale: Double
    ): JSONObject {
        return client.sendAndAwait(
            "reader.location.resolve",
            buildLocationResolveParams(
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterOffset = chapterOffset,
                chapterProgress = chapterProgress,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                fontScale = fontScale
            )
        )
    }

    internal companion object {
        internal fun buildDetailParams(sourceId: String, book: Book): JSONObject =
            JSONObject().apply {
                put("sourceId", sourceId)
                put("bookUrl", book.bookUrl)
                put("book", JSONObject().apply {
                    put("bookId", book.bookUrl)
                    put("title", book.name)
                    put("author", book.author)
                })
            }

        internal fun buildTocParams(
            sourceId: String,
            book: Book,
            tocUrl: String = book.tocUrl,
            variables: Map<String, String> = book.variables
        ): JSONObject = JSONObject().apply {
            put("sourceId", sourceId)
            put("bookId", book.bookUrl)
            if (tocUrl.isNotEmpty()) put("tocUrl", tocUrl)
            if (variables.isNotEmpty()) put("variables", variables.toJsonObject())
        }

        internal fun buildContentParams(
            sourceId: String,
            book: Book,
            chapter: Chapter,
            variables: Map<String, String> = mergeVariables(book.variables, chapter.variables)
        ): JSONObject = JSONObject().apply {
            put("sourceId", sourceId)
            put("bookId", book.bookUrl)
            put("chapterTitle", chapter.title)
            put("chapterUrl", chapter.url)
            put("chapterIndex", chapter.index)
            if (variables.isNotEmpty()) put("variables", variables.toJsonObject())
        }

        /**
         * The detail and TOC stages can both contribute Legado variables. A
         * chapter-specific value wins over the book-detail value, matching the
         * Core contract's continuation rule for `chapter.content`.
         */
        internal fun mergeVariables(
            detailVariables: Map<String, String>,
            chapterVariables: Map<String, String>
        ): Map<String, String> = detailVariables + chapterVariables

        /**
         * Builds the exact camelCase DTO consumed by Core's
         * `ReaderLocationResolveParams` (`deny_unknown_fields`). Validating
         * unsigned/bounded wire values here prevents platform integers from
         * becoming opaque Core deserialization failures.
         */
        internal fun buildLocationResolveParams(
            bookId: String,
            chapterIndex: Int,
            chapterOffset: Long,
            chapterProgress: Double,
            viewportWidth: Int,
            viewportHeight: Int,
            fontScale: Double
        ): JSONObject {
            require(bookId.isNotBlank()) { "bookId must be non-blank" }
            require(chapterIndex >= 0) { "chapterIndex must be >= 0" }
            require(chapterOffset >= 0L) { "chapterOffset must be >= 0" }
            require(chapterProgress.isFinite() && chapterProgress in 0.0..1.0) {
                "chapterProgress must be finite and between 0 and 1"
            }
            require(viewportWidth > 0) { "viewportWidth must be > 0" }
            require(viewportHeight > 0) { "viewportHeight must be > 0" }
            require(fontScale.isFinite() && fontScale > 0.0) {
                "fontScale must be finite and > 0"
            }

            val anchor = JSONObject().apply {
                put("chapterOffset", chapterOffset)
                put("chapterProgress", chapterProgress)
            }
            val layout = JSONObject().apply {
                put("viewportWidth", viewportWidth)
                put("viewportHeight", viewportHeight)
                put("fontScale", fontScale)
            }
            val params = JSONObject().apply {
                put("bookId", bookId)
                put("chapterIndex", chapterIndex)
                put("anchor", anchor)
                put("layout", layout)
            }
            return params
        }

        internal fun parseBookSearchResult(data: JSONObject): BookSearchResult {
            val sourceId = data.optString("sourceId")
            val arr = data.optJSONArray("books")
                ?: return BookSearchResult(sourceId = sourceId, books = emptyList())
            val books = (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                val origin = b.optString("origin").ifBlank { sourceId }
                SearchBook(
                    bookUrl = b.optString("bookId"),
                    name = b.optString("title"),
                    author = b.optString("author"),
                    coverUrl = b.optString("coverUrl"),
                    intro = b.optString("intro"),
                    lastChapter = b.optString("lastChapter"),
                    kind = b.optString("kind"),
                    origin = origin,
                    sourceId = sourceId
                )
            }
            return BookSearchResult(sourceId = sourceId, books = books)
        }

        internal fun parseSearchResult(data: JSONObject): List<SearchBook> =
            parseBookSearchResult(data).books

        internal fun parseBookDetailResult(data: JSONObject): BookDetailResult {
            val b = data.optJSONObject("book") ?: data
            val sourceId = data.optString("sourceId")
            // Core BookDetailData keeps tocUrl and variables at the result root,
            // not inside `book`. Retain the nested fallback for older fixtures.
            val tocUrl = data.optString("tocUrl").ifBlank { b.optString("tocUrl") }
            val variables = data.optJSONObject("variables").toStringMap()
            val book = Book(
                bookUrl = b.optString("bookId"),
                tocUrl = tocUrl,
                name = b.optString("title"),
                author = b.optString("author"),
                coverUrl = b.optString("coverUrl"),
                intro = b.optString("intro"),
                kind = b.optString("kind"),
                wordCount = b.optString("wordCount"),
                latestChapterTitle = b.optString("lastChapter"),
                origin = b.optString("origin").ifBlank { sourceId },
                sourceId = sourceId,
                variables = variables
            )
            return BookDetailResult(
                sourceId = sourceId,
                book = book,
                tocUrl = tocUrl,
                variables = variables
            )
        }

        internal fun parseBookDetail(data: JSONObject): Book =
            parseBookDetailResult(data).book

        internal fun parseBookTocResult(data: JSONObject): BookTocResult {
            val sourceId = data.optString("sourceId")
            val bookId = data.optString("bookId")
            val arr = data.optJSONArray("toc")
                ?: return BookTocResult(
                    sourceId = sourceId,
                    bookId = bookId,
                    chapters = emptyList()
                )
            val chapters = (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Chapter(
                    title = c.optString("title"),
                    url = c.optString("url"),
                    index = c.optInt("index", i),
                    isVip = c.optBoolean("isVip", false),
                    isPay = c.optBoolean("isPay", false),
                    variables = c.optJSONObject("variables").toStringMap()
                )
            }
            return BookTocResult(
                sourceId = sourceId,
                bookId = bookId,
                chapters = chapters
            )
        }

        internal fun parseTocResult(data: JSONObject): List<Chapter> =
            parseBookTocResult(data).chapters

        internal fun parseContentResult(data: JSONObject): String {
            return data.optString("content")
        }

        private fun Map<String, String>.toJsonObject(): JSONObject = JSONObject().apply {
            toSortedMap().forEach { (key, value) -> put(key, value) }
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return keys().asSequence().toList().sorted().associateWith { key ->
                val value = opt(key)
                if (value == null || value == JSONObject.NULL) "" else value.toString()
            }
        }
    }
}

data class BookSearchResult(
    val sourceId: String,
    val books: List<SearchBook>
)

data class BookDetailResult(
    val sourceId: String,
    val book: Book,
    val tocUrl: String,
    val variables: Map<String, String>
)

data class BookTocResult(
    val sourceId: String,
    val bookId: String,
    val chapters: List<Chapter>
)

data class Book(
    val bookUrl: String,
    val tocUrl: String = "",
    val name: String,
    val author: String = "",
    val coverUrl: String = "",
    val intro: String = "",
    val kind: String = "",
    val wordCount: String = "",
    val latestChapterTitle: String = "",
    /** Display metadata only; use [sourceId] for Core identities. */
    val origin: String = "",
    /** Root source identity from Core (not a source display label). */
    val sourceId: String = "",
    /** Variables emitted by `book.detail`, carried to TOC/content calls. */
    val variables: Map<String, String> = emptyMap()
)

data class SearchBook(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val lastChapter: String,
    val kind: String,
    val origin: String,
    /** Root book.search sourceId; origin may only be a display label. */
    val sourceId: String = ""
)

data class Chapter(
    val title: String,
    val url: String,
    val index: Int,
    val isVip: Boolean = false,
    val isPay: Boolean = false,
    /** Legado variables captured for this TOC entry. */
    val variables: Map<String, String> = emptyMap()
)
