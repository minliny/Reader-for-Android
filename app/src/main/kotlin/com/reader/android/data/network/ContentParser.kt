package com.reader.android.data.network

import com.reader.android.data.adapter.JsoupMarkupParserAdapter
import com.reader.android.data.model.BookSourceRule
import com.reader.android.data.model.ContentPage

class ContentParser(
    private val markup: JsoupMarkupParserAdapter = JsoupMarkupParserAdapter()
) {

    fun parseContentResponse(html: String): ContentPage? =
        parseContentResponse(html, rule = null)

    /**
     * When [rule] is present, evaluate the `content` (html extraction) and
     * optional `title` / `nextPageUrl` fields through the jsoup rule engine.
     * Falls back to regex.
     */
    fun parseContentResponse(html: String, rule: BookSourceRule?): ContentPage? {
        if (rule != null && rule.fields.isNotEmpty()) {
            val row = markup.evaluateRule(html, rule).firstOrNull() ?: return null
            val rawContent = row["content"]?.takeIf { it.isNotBlank() } ?: return null
            val title = row["title"]?.ifBlank { null }
            val nextPageUrl = row["nextPageUrl"]?.ifBlank { null }
            val cleanContent = cleanContent(rawContent)
            return ContentPage(content = cleanContent, title = title, nextPageUrl = nextPageUrl)
        }
        return parseWithRegex(html)
    }

    private fun parseWithRegex(html: String): ContentPage? {
        // Extract text from common content containers
        val content = extract(html, Regex("""<div[^>]*id="content"[^>]*>\s*([\s\S]+?)</div>"""))
            ?: extract(html, Regex("""<div[^>]*class="[^"]*content[^"]*"[^>]*>\s*([\s\S]+?)</div>"""))
            ?: extract(html, Regex("""<article[^>]*class="article"[^>]*>\s*([\s\S]+?)</article>"""))
            ?: extract(html, Regex("""<article[^>]*>\s*([\s\S]+?)</article>"""))
            ?: return null

        val title = extract(html, Regex("""<h1[^>]*>\s*([^<]{1,60})\s*</h1>"""))

        val nextPageUrl = extract(html, Regex("""<a[^>]*href="([^"]*)"[^>]*>\s*下一[章节页]\s*</a>"""))

        val cleanContent = cleanContent(content)
        return ContentPage(
            content = cleanContent,
            title = title,
            nextPageUrl = nextPageUrl
        )
    }

    private fun cleanContent(content: String): String = content
        .replace(Regex("""<br\s*/?>"""), "\n")
        .replace(Regex("""&nbsp;"""), " ")
        .replace(Regex("""<[^>]+>"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    private fun extract(html: String, regex: Regex): String? {
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()
    }
}
