package com.reader.android.data.network

import com.reader.android.data.adapter.JsoupMarkupParserAdapter
import com.reader.android.data.model.BookSourceRule
import com.reader.android.data.model.SearchResultItem
import java.util.regex.Pattern

class SearchParser(
    private val markup: JsoupMarkupParserAdapter = JsoupMarkupParserAdapter()
) {

    fun parseSearchResponse(html: String, sourceName: String): List<SearchResultItem> =
        parseSearchResponse(html, sourceName, rule = null)

    /**
     * When [rule] is present, evaluate it through the jsoup rule engine. Each
     * row field map is mapped to a [SearchResultItem] by field name
     * (name, author, detailUrl, coverUrl, kind, wordCount, latestChapter, intro).
     * Falls back to the regex path otherwise.
     */
    fun parseSearchResponse(
        html: String,
        sourceName: String,
        rule: BookSourceRule?
    ): List<SearchResultItem> {
        if (rule != null && rule.fields.isNotEmpty()) {
            val rows = markup.evaluateRule(html, rule)
            if (rows.isNotEmpty()) {
                return rows.mapNotNull { row ->
                    val name = row["name"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SearchResultItem(
                        name = name,
                        author = row["author"]?.ifBlank { null } ?: "未知",
                        coverUrl = row["coverUrl"]?.ifBlank { null },
                        detailUrl = row["detailUrl"]?.ifBlank { null },
                        kind = row["kind"]?.ifBlank { null },
                        wordCount = row["wordCount"]?.ifBlank { null },
                        latestChapter = row["latestChapter"]?.ifBlank { null },
                        intro = row["intro"]?.ifBlank { null },
                        sourceName = sourceName
                    )
                }
            }
        }
        return parseWithRegex(html, sourceName)
    }

    private fun parseWithRegex(html: String, sourceName: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()

        // Attempt to find book list items in common Chinese novel site formats
        val itemPatterns = listOf(
            // biquges123.com: <a href="/34811" title="凡人修仙传">...<div class="hot_name">凡人修仙传</div>...<div class="author">忘语</div>
            Regex(
                """<a[^>]*href="([^"]*)"[^>]*>.*?<div[^>]*class="hot_name"[^>]*>\s*<mark>\s*([^<]+)\s*</mark>\s*</div>.*?<div[^>]*class="author"[^>]*>\s*([^<]+)\s*</div>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ),
            // xingxingxsw.com: <div class="book-item"><a href="/xiaoshuo/ID.html"><h3 class="book-title">NAME</h3></a><span class="author">AUTHOR</span>
            Regex(
                """<a[^>]*href="(/xiaoshuo/[^"]+)"[^>]*>\s*<h3[^>]*class="book-title"[^>]*>([^<]+)</h3>\s*</a>.*?<span[^>]*class="author"[^>]*>([^<]+)</span>""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ),
            // <li> or <div> with book info: name, author, chapter
            Regex(
                """<a[^>]*href="([^"]*)"[^>]*>\s*([^<]{2,40})\s*</a>.*?作者[：:]\s*([^<\n]{1,30})""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ),
            // <dt><a>title</a></dt><dd>author/chapter
            Regex(
                """<a[^>]*href="([^"]*)"[^>]*>\s*([^<]{2,40})\s*</a>\s*</dt>.*?<dd[^>]*>\s*([^<]{1,30})""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            )
        )

        for (pattern in itemPatterns) {
            val matches = pattern.findAll(html).take(20)
            for (match in matches) {
                val detailUrl = match.groupValues[1].trim()
                val name = match.groupValues[2].trim()
                val meta = match.groupValues[3].trim()
                results.add(
                    SearchResultItem(
                        name = name,
                        author = meta,
                        detailUrl = detailUrl,
                        sourceName = sourceName
                    )
                )
            }
            if (results.isNotEmpty()) break
        }

        return results
    }
}
