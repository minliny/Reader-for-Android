package com.reader.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun `parseSearchResult extracts books from Core JSON`() {
        val json = JSONObject(
            """{"books":[{"bookUrl":"http://ex.com/1","name":"Test Book",""" +
                """"author":"Author","coverUrl":"http://ex.com/cover.jpg",""" +
                """"intro":"Intro","lastChapter":"Ch10","kind":"Novel",""" +
                """"origin":"TestSource"}]}"""
        )
        val books = BookApi.parseSearchResult(json)
        assertEquals(1, books.size)
        val b = books[0]
        assertEquals("http://ex.com/1", b.bookUrl)
        assertEquals("Test Book", b.name)
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
    fun `parseBookDetail extracts book from nested object`() {
        val json = JSONObject(
            """{"book":{"bookUrl":"http://ex.com/1","name":"Title",""" +
                """"author":"A","coverUrl":"c","intro":"i","kind":"k",""" +
                """"wordCount":"1000","latestChapterTitle":"L","origin":"O"}}"""
        )
        val book = BookApi.parseBookDetail(json)
        assertEquals("http://ex.com/1", book.bookUrl)
        assertEquals("Title", book.name)
        assertEquals("A", book.author)
        assertEquals("c", book.coverUrl)
        assertEquals("i", book.intro)
        assertEquals("k", book.kind)
        assertEquals("1000", book.wordCount)
        assertEquals("L", book.latestChapterTitle)
        assertEquals("O", book.origin)
    }

    @Test
    fun `parseBookDetail unwraps flat object when book field absent`() {
        val json = JSONObject(
            """{"bookUrl":"http://ex.com/2","name":"Flat","author":"B"}"""
        )
        val book = BookApi.parseBookDetail(json)
        assertEquals("http://ex.com/2", book.bookUrl)
        assertEquals("Flat", book.name)
        assertEquals("B", book.author)
    }

    @Test
    fun `parseTocResult extracts chapters from Core JSON`() {
        val json = JSONObject(
            """{"chapters":[{"title":"Ch1","url":"http://ex.com/ch1","index":0},""" +
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
            """{"chapters":[{"title":"Ch1","url":"http://ex.com/ch1"}]}"""
        )
        val chapters = BookApi.parseTocResult(json)
        assertEquals(1, chapters.size)
        assertEquals(0, chapters[0].index)
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
