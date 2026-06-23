package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupMarkupParserAdapterTest {

    private val adapter = JsoupMarkupParserAdapter()

    @Test
    fun `extract html css text and attribute without leaking raw url in preview`() {
        val html = """
            <article class="chapter">
              <p>第一段正文</p>
              <a class="next" href="https://example.invalid/book/2">下一章</a>
            </article>
        """.trimIndent()

        val text = adapter.selectHtmlText(html, "article.chapter p")
        val href = adapter.selectHtmlAttribute(html, "a.next", "href")

        assertEquals(listOf("第一段正文"), text.values)
        assertEquals(listOf("https://example.invalid/book/2"), href.values)
        assertTrue(href.redactedPreview.contains("url:REDACTED"))
        assertFalse(href.redactedPreview.contains("example.invalid/book/2"))
    }

    @Test
    fun `bounded html and xml xpath map to deterministic selectors`() {
        val html = "<html><body><article><p>正文</p></article></body></html>"
        val xml = "<feed><entry><title>更新</title></entry></feed>"

        assertEquals(listOf("正文"), adapter.selectHtmlXPathText(html, "//article/p").values)
        assertEquals(listOf("更新"), adapter.selectXmlXPathText(xml, "//feed/entry/title").values)
    }

    @Test
    fun `script and style content is suppressed from text evidence`() {
        val html = """
            <html><head><style>.x{}</style><script>token='secret'</script></head>
            <body><p>可见正文</p></body></html>
        """.trimIndent()

        val text = adapter.textWithoutScriptOrStyle(html)

        assertTrue(text.contains("可见正文"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains(".x"))
    }
}
