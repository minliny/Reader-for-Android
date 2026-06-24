package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeReaderShellHostTest {

    private val host = ComposeReaderShellHost()

    @Test
    fun `txt content is paginated into bounded pages`() = runBlocking {
        val paragraphs = (1..200).joinToString("\n") { "段落$it 这是一段足够长的正文内容用于跨越多个分页边界。" }
        val result = host.render(RenderRequest(content = paragraphs, contentType = ReaderContentType.TXT))

        assertTrue(result.paginatedText.isNotEmpty())
        assertTrue("expected multiple pages for long content", result.paginatedText.size > 1)
        // No page exceeds roughly the budget (allow paragraph overflow)
        result.paginatedText.forEach { page -> assertTrue(page.length <= 1200) }
        assertNull(result.sanitizedHtml)
        assertNull("clean-room host leaves CFI slot null for future Readium", result.cfi)
    }

    @Test
    fun `html content is sanitized with scripts and iframes stripped`() = runBlocking {
        val html = """
            <article>
              <p>第一段<script>steal('cookie')</script></p>
              <iframe src="https://evil.example/track"></iframe>
              <p><a href="https://example.invalid/ch2">下一章</a></p>
            </article>
        """.trimIndent()
        val result = host.render(RenderRequest(content = html, contentType = ReaderContentType.HTML))

        assertNotNull(result.sanitizedHtml)
        val clean = result.sanitizedHtml!!
        assertFalse("script must be stripped", clean.contains("script"))
        assertFalse("iframe must be stripped", clean.contains("iframe"))
        assertFalse("cookie payload must be stripped", clean.contains("steal"))
        assertTrue("link text preserved", clean.contains("下一章"))
    }

    @Test
    fun `csp blocks remote resources and disables scripts by default`() = runBlocking {
        val result = host.render(RenderRequest(content = "文本", contentType = ReaderContentType.TXT))
        assertTrue(result.csp.contains("default-src 'none'"))
        assertTrue(result.csp.contains("script-src 'none'"))
        assertFalse("JS is disabled by default", result.jsEnabled)
    }

    @Test
    fun `empty content yields empty pagination without throwing`() = runBlocking {
        val result = host.render(RenderRequest(content = "", contentType = ReaderContentType.TXT))
        assertTrue(result.paginatedText.isEmpty())
    }

    @Test
    fun `html sanitized output is also paginated as readable text`() = runBlocking {
        val html = "<article><p>第一段</p><p>第二段</p></article>"
        val result = host.render(RenderRequest(content = html, contentType = ReaderContentType.HTML))
        assertTrue(result.paginatedText.isNotEmpty())
        // The paginated text comes from the sanitized body's text content
        assertTrue(result.paginatedText.joinToString(" ").contains("第一段"))
    }
}
