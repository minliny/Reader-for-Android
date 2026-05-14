package com.reader.android.data.network

import java.util.regex.Pattern

data class RssItem(
    val title: String,
    val link: String,
    val description: String? = null,
    val author: String? = null,
    val pubDate: String? = null,
    val guid: String? = null
)

data class RssFeed(
    val title: String,
    val link: String,
    val description: String? = null,
    val items: List<RssItem> = emptyList()
)

data class RssSubscription(
    val feedUrl: String,
    val title: String = "",
    val lastUpdated: Long = 0,
    val lastItemGuid: String? = null
)

class RssParser {
    fun parse(xml: String): RssFeed? {
        val channelPattern = Regex("""<channel[^>]*>(.+?)</channel>""", RegexOption.DOT_MATCHES_ALL)
        val channelMatch = channelPattern.find(xml) ?: return null
        val channelContent = channelMatch.groupValues[1]

        val title = extractTag(channelContent, "title")
        val link = extractTag(channelContent, "link")
        val description = extractTag(channelContent, "description")

        val items = mutableListOf<RssItem>()
        val itemPattern = Regex("""<item[^>]*>(.+?)</item>""", RegexOption.DOT_MATCHES_ALL)
        itemPattern.findAll(channelContent).forEach { match ->
            val itemXml = match.groupValues[1]
            items.add(RssItem(
                title = extractTag(itemXml, "title") ?: "",
                link = extractTag(itemXml, "link") ?: "",
                description = extractTag(itemXml, "description"),
                author = extractTag(itemXml, "author"),
                pubDate = extractTag(itemXml, "pubDate"),
                guid = extractTag(itemXml, "guid")
            ))
        }

        return RssFeed(
            title = title ?: "",
            link = link ?: "",
            description = description,
            items = items
        )
    }

    private fun extractTag(xml: String, tag: String): String? {
        val cdataPattern = Regex("""<$tag[^>]*><!\[CDATA\[(.+?)]]></$tag>""", RegexOption.DOT_MATCHES_ALL)
        cdataPattern.find(xml)?.let { return it.groupValues[1].trim() }
        val normalPattern = Regex("""<$tag[^>]*>(.+?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return normalPattern.find(xml)?.groupValues?.get(1)?.trim()
    }
}
