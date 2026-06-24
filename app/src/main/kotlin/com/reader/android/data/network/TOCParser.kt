package com.reader.android.data.network

import com.reader.android.data.adapter.JsoupMarkupParserAdapter
import com.reader.android.data.model.BookSourceRule
import com.reader.android.data.model.TOCItem

class TOCParser(
    private val markup: JsoupMarkupParserAdapter = JsoupMarkupParserAdapter()
) {

    fun parseTOCResponse(html: String): List<TOCItem> =
        parseTOCResponse(html, rule = null)

    /**
     * When [rule] is present (with a list selector), evaluate each row to a
     * [TOCItem] using the `title` and `url` fields. Falls back to regex.
     */
    fun parseTOCResponse(html: String, rule: BookSourceRule?): List<TOCItem> {
        if (rule != null && rule.listSelector != null && rule.fields.isNotEmpty()) {
            val rows = markup.evaluateRule(html, rule)
            if (rows.isNotEmpty()) {
                return rows.mapNotNull { row ->
                    val title = row["title"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val url = row["url"]?.ifBlank { "" } ?: ""
                    TOCItem(title = title, url = url)
                }
            }
        }
        return parseWithRegex(html)
    }

    private fun parseWithRegex(html: String): List<TOCItem> {
        // Match chapter links: <a href="url">title</a> or <a href="url" title="title">display</a>
        val chapterRegex = Regex(
            """<a[^>]*href="([^"]*)"[^>]*>\s*([^<]{1,60})\s*</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        val chapters = mutableListOf<TOCItem>()
        var currentVolumeTitle: String? = null
        var volumeChildren = mutableListOf<TOCItem>()

        val volumeRegex = Regex(
            """<h[2-4][^>]*>\s*([^<]{2,30})\s*</h[2-4]>"""
        )

        volumeRegex.findAll(html).forEach { vMatch ->
            // Flush previous volume
            if (volumeChildren.isNotEmpty()) {
                chapters.add(
                    TOCItem(
                        title = currentVolumeTitle ?: "正文",
                        url = "",
                        children = volumeChildren.toList()
                    )
                )
                volumeChildren.clear()
            }
            currentVolumeTitle = vMatch.groupValues[1].trim()
        }

        chapterRegex.findAll(html).take(200).forEach { match ->
            val url = match.groupValues[1].trim()
            val title = match.groupValues[2].trim()
            if (url.isNotBlank() && title.length > 1) {
                volumeChildren.add(TOCItem(title = title, url = url))
            }
        }

        // Flush remaining
        if (volumeChildren.isNotEmpty()) {
            chapters.add(
                TOCItem(
                    title = currentVolumeTitle ?: "正文",
                    url = "",
                    children = volumeChildren.toList()
                )
            )
        }

        if (chapters.isEmpty()) return volumeChildren.map { it }
        return chapters
    }
}
