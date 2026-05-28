package com.reader.android.data.network

import com.reader.android.data.model.SearchResultItem
import java.util.regex.Pattern

class SearchParser {

    fun parseSearchResponse(html: String, sourceName: String): List<SearchResultItem> {
        val results = mutableListOf<SearchResultItem>()

        // Attempt to find book list items in common Chinese novel site formats
        val itemPatterns = listOf(
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
