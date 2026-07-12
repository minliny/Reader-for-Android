package com.reader.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wrapper smoke for [BookApi]'s JSON parsing logic.
 *
 * Validates that Core JSON result shapes (assumed from
 * `protocol/fixtures/conformance/host/` and Legado WebBook output mapping)
 * parse into the expected Kotlin data classes. This is NOT a device proof:
 * it exercises pure-JVM JSON parsing only — no JNI `.so`, no Core runtime,
 * no host.request routing. Device proof is Task 12 via
 * `connectedDebugAndroidTest`.
 */
class BookApiJsonParseTest {

    @Test
    fun `location resolve params match Core ReaderLocationResolveParams exactly`() {
        val params = BookApi.buildLocationResolveParams(
            bookId = "book-1",
            chapterIndex = 2,
            chapterOffset = 128L,
            chapterProgress = 0.5,
            viewportWidth = 390,
            viewportHeight = 844,
            fontScale = 1.0
        )

        assertEquals(4, params.length())
        assertEquals("book-1", params.getString("bookId"))
        assertEquals(2, params.getInt("chapterIndex"))
        assertFalse("Core uses camelCase and deny_unknown_fields", params.has("book_id"))

        val anchor = params.getJSONObject("anchor")
        assertEquals(2, anchor.length())
        assertEquals(128L, anchor.getLong("chapterOffset"))
        assertEquals(0.5, anchor.getDouble("chapterProgress"), 0.0)

        val layout = params.getJSONObject("layout")
        assertEquals(3, layout.length())
        assertEquals(390, layout.getInt("viewportWidth"))
        assertEquals(844, layout.getInt("viewportHeight"))
        assertEquals(1.0, layout.getDouble("fontScale"), 0.0)
    }

    @Test
    fun `location resolve params reject values invalid for Core unsigned and bounded DTOs`() {
        val invalidBuilders = listOf<() -> Unit>(
            { BookApi.buildLocationResolveParams("", 0, 0, 0.0, 390, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", -1, 0, 0.0, 390, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, -1, 0.0, 390, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, 0, 1.1, 390, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, 0, Double.NaN, 390, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, 0, 0.5, 0, 844, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, 0, 0.5, 390, 0, 1.0) },
            { BookApi.buildLocationResolveParams("b", 0, 0, 0.5, 390, 844, 0.0) }
        )

        invalidBuilders.forEach { build ->
            var rejected = false
            try {
                build()
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue("invalid Core payload value must be rejected before dispatch", rejected)
        }
    }

    @Test
    fun `parseSearchResult extracts books from Core JSON`() {
        val json = JSONObject(
            """{"books":[{"bookId":"http://ex.com/1","title":"Test Book",""" +
                """"author":"Author","coverUrl":"http://ex.com/cover.jpg",""" +
                """"intro":"Intro","lastChapter":"Ch10","kind":"Novel",""" +
                """"origin":"TestSource"}]}"""
        )
        val books = BookApi.parseSearchResult(json)
        assertEquals(1, books.size)
        val b = books[0]
        assertEquals("http://ex.com/1", b.bookUrl)
        assertEquals("Test Book", b.name)
        // Core returns bookId/title; Android data class maps to bookUrl/name
        assertEquals("Author", b.author)
        assertEquals("http://ex.com/cover.jpg", b.coverUrl)
        assertEquals("Intro", b.intro)
        assertEquals("Ch10", b.lastChapter)
        assertEquals("Novel", b.kind)
        assertEquals("TestSource", b.origin)
    }

    @Test
    fun `parseSearchResult returns empty when books array absent`() {
        val json = JSONObject("""{"data":"no books here"}""")
        val books = BookApi.parseSearchResult(json)
        assertTrue(books.isEmpty())
    }

    @Test
    fun `typed search result retains root sourceId without confusing origin label`() {
        val result = BookApi.parseBookSearchResult(
            JSONObject(
                """{"sourceId":"source-42","books":[""" +
                    """{"bookId":"book-1","title":"One","author":"A"},""" +
                    """{"bookId":"book-2","title":"Two","author":"B","origin":"Display Source"}]}"""
            )
        )

        assertEquals("source-42", result.sourceId)
        assertEquals(listOf("source-42", "source-42"), result.books.map { it.sourceId })
        assertEquals("source-42", result.books[0].origin)
        assertEquals("Display Source", result.books[1].origin)
    }

    @Test
    fun `parseBookDetail extracts book from nested object`() {
        val json = JSONObject(
            """{"book":{"bookId":"http://ex.com/1","title":"Title",""" +
                """"author":"A","coverUrl":"c","intro":"i","kind":"k",""" +
                """"lastChapter":"L"}}"""
        )
        val book = BookApi.parseBookDetail(json)
        assertEquals("http://ex.com/1", book.bookUrl)
        assertEquals("Title", book.name)
        assertEquals("A", book.author)
        assertEquals("c", book.coverUrl)
        assertEquals("i", book.intro)
        assertEquals("k", book.kind)
        // Core Book struct has no wordCount/latestChapterTitle/origin fields
        assertEquals("L", book.latestChapterTitle)
    }

    @Test
    fun `parseBookDetail unwraps flat object when book field absent`() {
        val json = JSONObject(
            """{"bookId":"http://ex.com/2","title":"Flat","author":"B"}"""
        )
        val book = BookApi.parseBookDetail(json)
        assertEquals("http://ex.com/2", book.bookUrl)
        assertEquals("Flat", book.name)
        assertEquals("B", book.author)
    }

    @Test
    fun `typed detail retains root tocUrl variables and sourceId`() {
        val result = BookApi.parseBookDetailResult(
            JSONObject(
                """{"sourceId":"source-42","tocUrl":"https://example.test/toc",""" +
                    """"variables":{"token":"abc","volume":"7"},"book":{"bookId":"book-1",""" +
                    """"title":"Title","author":"A"}}"""
            )
        )

        assertEquals("source-42", result.sourceId)
        assertEquals("https://example.test/toc", result.tocUrl)
        assertEquals(mapOf("token" to "abc", "volume" to "7"), result.variables)
        assertEquals(result.tocUrl, result.book.tocUrl)
        assertEquals("source-42", result.book.origin)
        assertEquals("source-42", result.book.sourceId)
        assertEquals(result.variables, result.book.variables)
    }

    @Test
    fun `parseTocResult extracts chapters from Core JSON`() {
        val json = JSONObject(
            """{"toc":[{"title":"Ch1","url":"http://ex.com/ch1","index":0},""" +
                """{"title":"Ch2","url":"http://ex.com/ch2","index":1}]}"""
        )
        val chapters = BookApi.parseTocResult(json)
        assertEquals(2, chapters.size)
        assertEquals("Ch1", chapters[0].title)
        assertEquals("http://ex.com/ch1", chapters[0].url)
        assertEquals(0, chapters[0].index)
        assertEquals("Ch2", chapters[1].title)
        assertEquals("http://ex.com/ch2", chapters[1].url)
        assertEquals(1, chapters[1].index)
    }

    @Test
    fun `parseTocResult defaults index to array position when missing`() {
        val json = JSONObject(
            """{"toc":[{"title":"Ch1","url":"http://ex.com/ch1"}]}"""
        )
        val chapters = BookApi.parseTocResult(json)
        assertEquals(1, chapters.size)
        assertEquals(0, chapters[0].index)
    }

    @Test
    fun `typed toc retains root identities and per-entry variables`() {
        val result = BookApi.parseBookTocResult(
            JSONObject(
                """{"sourceId":"source-42","bookId":"book-1","toc":[{"index":3,""" +
                    """"title":"Chapter Four","url":"https://example.test/ch4",""" +
                    """"variables":{"chapterToken":"t4","volume":"2"}}]}"""
            )
        )

        assertEquals("source-42", result.sourceId)
        assertEquals("book-1", result.bookId)
        assertEquals(1, result.chapters.size)
        assertEquals(3, result.chapters.single().index)
        assertEquals(
            mapOf("chapterToken" to "t4", "volume" to "2"),
            result.chapters.single().variables
        )
    }

    @Test
    fun `toc and content builders preserve variables and chapter index`() {
        val book = Book(
            bookUrl = "book-1",
            tocUrl = "https://example.test/toc",
            name = "Title",
            variables = mapOf("volume" to "2", "token" to "abc", "shared" to "detail")
        )
        val chapter = Chapter(
            title = "Chapter Four",
            url = "https://example.test/ch4",
            index = 3,
            variables = mapOf("chapterToken" to "t4", "shared" to "chapter")
        )

        val tocParams = BookApi.buildTocParams("source-42", book)
        assertEquals("source-42", tocParams.getString("sourceId"))
        assertEquals("book-1", tocParams.getString("bookId"))
        assertEquals("https://example.test/toc", tocParams.getString("tocUrl"))
        assertEquals("abc", tocParams.getJSONObject("variables").getString("token"))
        assertEquals("2", tocParams.getJSONObject("variables").getString("volume"))

        val contentParams = BookApi.buildContentParams("source-42", book, chapter)
        assertEquals(3, contentParams.getInt("chapterIndex"))
        assertEquals("t4", contentParams.getJSONObject("variables").getString("chapterToken"))
        assertEquals("abc", contentParams.getJSONObject("variables").getString("token"))
        assertEquals("chapter", contentParams.getJSONObject("variables").getString("shared"))
    }

    @Test
    fun `parseContentResult extracts content string`() {
        val json = JSONObject("""{"content":"Chapter content here"}""")
        assertEquals("Chapter content here", BookApi.parseContentResult(json))
    }

    @Test
    fun `parseContentResult returns empty when content absent`() {
        val json = JSONObject("""{"other":"field"}""")
        assertEquals("", BookApi.parseContentResult(json))
    }
}
