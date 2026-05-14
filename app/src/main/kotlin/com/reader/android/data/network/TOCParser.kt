package com.reader.android.data.network

import com.reader.android.data.model.TOCItem

class TOCParser {

    fun parseTOCResponse(html: String): List<TOCItem> {
        // Match chapter links: <a href="url">title</a>
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
