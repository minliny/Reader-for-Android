package com.reader.android.data.network

import com.reader.android.data.model.ContentPage

class ContentParser {

    fun parseContentResponse(html: String): ContentPage? {
        // Extract text from common content containers
        val content = extract(html, Regex("""<div[^>]*id="content"[^>]*>\s*([\s\S]+?)</div>"""))
            ?: extract(html, Regex("""<div[^>]*class="[^"]*content[^"]*"[^>]*>\s*([\s\S]+?)</div>"""))
            ?: extract(html, Regex("""<article[^>]*>\s*([\s\S]+?)</article>"""))
            ?: return null

        val title = extract(html, Regex("""<h1[^>]*>\s*([^<]{1,60})\s*</h1>"""))

        val nextPageUrl = extract(html, Regex("""<a[^>]*href="([^"]*)"[^>]*>\s*下一[章节页]\s*</a>"""))

        val cleanContent = content
            .replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("""&nbsp;"""), " ")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        return ContentPage(
            content = cleanContent,
            title = title,
            nextPageUrl = nextPageUrl
        )
    }

    private fun extract(html: String, regex: Regex): String? {
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()
    }
}
