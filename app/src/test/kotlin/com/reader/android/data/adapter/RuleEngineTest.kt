package com.reader.android.data.adapter

import com.reader.android.data.model.RuleExtraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val adapter = JsoupMarkupParserAdapter()

    @Test
    fun `expression parser reads list selector and css text fields`() {
        val rule = RuleExpression.parse(
            """
            list@div.book-item
            name@h3.book-title
            author@span.author
            detailUrl@a.book-link||href
            """.trimIndent()
        )
        assertEquals("div.book-item", rule.listSelector)
        assertEquals(3, rule.fields.size)
        assertEquals("name", rule.fields[0].name)
        assertEquals(RuleExtraction.TEXT, rule.fields[0].extraction)
        assertEquals("author", rule.fields[1].name)
        assertEquals("detailUrl", rule.fields[2].name)
        assertEquals(RuleExtraction.ATTRIBUTE, rule.fields[2].extraction)
        assertEquals("href", rule.fields[2].attribute)
    }

    @Test
    fun `expression parser supports html extraction mode`() {
        val rule = RuleExpression.parse("content@div.chapter-content||html")
        assertEquals(RuleExtraction.HTML, rule.fields.first().extraction)
        assertNull(rule.listSelector)
    }

    @Test
    fun `evaluateRule maps list rows to fields`() {
        val html = """
            <div class="results">
              <div class="book-item">
                <a class="book-link" href="/b/1"><h3 class="book-title">深空信号</h3></a>
                <span class="author">艾伦</span>
              </div>
              <div class="book-item">
                <a class="book-link" href="/b/2"><h3 class="book-title">寂静航线</h3></a>
                <span class="author">索菲亚</span>
              </div>
            </div>
        """.trimIndent()
        val rule = RuleExpression.parse(
            """
            list@div.book-item
            name@h3.book-title
            author@span.author
            detailUrl@a.book-link||href
            """.trimIndent()
        )

        val rows = adapter.evaluateRule(html, rule)
        assertEquals(2, rows.size)
        assertEquals("深空信号", rows[0]["name"])
        assertEquals("艾伦", rows[0]["author"])
        assertEquals("/b/1", rows[0]["detailUrl"])
        assertEquals("寂静航线", rows[1]["name"])
    }

    @Test
    fun `evaluateRule without list selector evaluates whole document fields`() {
        val html = """
            <article>
              <h1 class="title">第一章</h1>
              <div class="intro">简介内容</div>
            </article>
        """.trimIndent()
        val rule = RuleExpression.parse(
            """
            name@h1.title
            intro@div.intro
            """.trimIndent()
        )

        val rows = adapter.evaluateRule(html, rule)
        assertEquals(1, rows.size)
        assertEquals("第一章", rows[0]["name"])
        assertEquals("简介内容", rows[0]["intro"])
    }

    @Test
    fun `evaluateRule returns blank for missing field rather than throwing`() {
        val html = "<div><span>only</span></div>"
        val rule = RuleExpression.parse("name@h1.missing\nauthor@span")
        val rows = adapter.evaluateRule(html, rule)
        assertEquals(1, rows.size)
        assertTrue(rows[0]["name"].isNullOrBlank())
        assertEquals("only", rows[0]["author"])
    }
}
