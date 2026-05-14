package com.reader.android.data.network

import com.reader.android.data.model.BookInfo

class BookInfoParser {

    fun parseBookInfoResponse(html: String, sourceName: String): BookInfo? {
        val name = extract(html, Regex("""<h1[^>]*>\s*([^<]{2,40})\s*</h1>"""))
            ?: extract(html, Regex("""<meta\s+property="og:title"\s+content="([^"]+)""""))
            ?: return null

        val author = extract(html, Regex("""作者[：:]\s*<a[^>]*>\s*([^<]+)\s*</a>"""))
            ?: extract(html, Regex("""作者[：:]\s*([^<\n]{1,20})"""))

        val intro = extract(html, Regex("""<div[^>]*class="[^"]*intro[^"]*"[^>]*>\s*([^<]{20,200})"""))
            ?: extract(html, Regex("""简介[：:]\s*<p>\s*([^<]+)"""))

        val kind = extract(html, Regex("""分类[：:]\s*<a[^>]*>\s*([^<]+)\s*</a>"""))
            ?: extract(html, Regex("""类型[：:]\s*([^<]{1,10})"""))

        val tocUrl = extract(html, Regex("""<a[^>]*href="([^"]*)"[^>]*>\s*目录\s*</a>"""))
            ?: extract(html, Regex("""<a[^>]*href="([^"]*)"[^>]*>\s*章节列表\s*</a>"""))

        val latestChapter = extract(html, Regex("""最新章节[：:]\s*<a[^>]*>\s*([^<]+)\s*</a>"""))

        return BookInfo(
            name = name,
            author = author ?: "未知",
            intro = intro,
            kind = kind,
            tocUrl = tocUrl,
            latestChapter = latestChapter,
            origin = sourceName
        )
    }

    private fun extract(html: String, regex: Regex): String? {
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()
    }
}
