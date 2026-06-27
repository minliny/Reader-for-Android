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
        val params = JSONObject().apply {
            put("sourceId", sourceId)
            put("keyword", key)
            put("page", page)
        }
        val result = client.sendAndAwait("book.search", params)
        return parseSearchResult(result)
    }

    suspend fun detail(sourceId: String, book: Book): Book {
        val params = JSONObject().apply {
            put("sourceId", sourceId)
            put("bookUrl", book.bookUrl)
            put("book", JSONObject().apply {
                put("bookId", book.bookUrl)
                put("title", book.name)
                put("author", book.author)
            })
        }
        val result = client.sendAndAwait("book.detail", params)
        return parseBookDetail(result)
    }

    suspend fun toc(sourceId: String, book: Book, tocUrl: String = book.tocUrl): List<Chapter> {
        val params = JSONObject().apply {
            put("sourceId", sourceId)
            put("bookId", book.bookUrl)
            if (tocUrl.isNotEmpty()) {
                put("tocUrl", tocUrl)
            }
        }
        val result = client.sendAndAwait("book.toc", params)
        return parseTocResult(result)
    }

    suspend fun content(sourceId: String, book: Book, chapter: Chapter): String {
        val params = JSONObject().apply {
            put("sourceId", sourceId)
            put("bookId", book.bookUrl)
            put("chapterTitle", chapter.title)
            put("chapterUrl", chapter.url)
        }
        val result = client.sendAndAwait("chapter.content", params)
        return parseContentResult(result)
    }

    internal companion object {
        internal fun parseSearchResult(data: JSONObject): List<SearchBook> {
            val arr = data.optJSONArray("books") ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val b = arr.getJSONObject(i)
                SearchBook(
                    bookUrl = b.optString("bookId"),
                    name = b.optString("title"),
                    author = b.optString("author"),
                    coverUrl = b.optString("coverUrl"),
                    intro = b.optString("intro"),
                    lastChapter = b.optString("lastChapter"),
                    kind = b.optString("kind"),
                    origin = b.optString("origin")
                )
            }
        }

        internal fun parseBookDetail(data: JSONObject): Book {
            val b = data.optJSONObject("book") ?: data
            return Book(
                bookUrl = b.optString("bookId"),
                tocUrl = b.optString("tocUrl"),
                name = b.optString("title"),
                author = b.optString("author"),
                coverUrl = b.optString("coverUrl"),
                intro = b.optString("intro"),
                kind = b.optString("kind"),
                wordCount = b.optString("wordCount"),
                latestChapterTitle = b.optString("lastChapter"),
                origin = b.optString("origin")
            )
        }

        internal fun parseTocResult(data: JSONObject): List<Chapter> {
            val arr = data.optJSONArray("toc") ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val c = arr.getJSONObject(i)
                Chapter(
                    title = c.optString("title"),
                    url = c.optString("url"),
                    index = c.optInt("index", i),
                    isVip = c.optBoolean("isVip", false),
                    isPay = c.optBoolean("isPay", false)
                )
            }
        }

        internal fun parseContentResult(data: JSONObject): String {
            return data.optString("content")
        }
    }
}

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
    val origin: String = ""
)

data class SearchBook(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String,
    val intro: String,
    val lastChapter: String,
    val kind: String,
    val origin: String
)

data class Chapter(
    val title: String,
    val url: String,
    val index: Int,
    val isVip: Boolean = false,
    val isPay: Boolean = false
)
