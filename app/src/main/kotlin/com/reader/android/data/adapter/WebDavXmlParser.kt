package com.reader.android.data.adapter

data class WebDavResource(
    val href: String,
    val status: String? = null,
    val displayName: String? = null,
    val contentLength: Long = 0,
    val lastModified: String? = null,
    val isCollection: Boolean = false
)

class WebDavXmlParser {
    fun parseMultiStatus(xml: String): List<WebDavResource> {
        val resources = mutableListOf<WebDavResource>()
        val responsePattern = Regex("""<d:response>(.+?)</d:response>""", RegexOption.DOT_MATCHES_ALL)
        responsePattern.findAll(xml).forEach { match ->
            val respXml = match.groupValues[1]
            val href = extract(respXml, "d:href")
            val status = extract(respXml, "d:status")
            val displayName = extract(respXml, "d:displayname")
            val contentLength = extract(respXml, "d:getcontentlength")?.toLongOrNull() ?: 0
            val lastModified = extract(respXml, "d:getlastmodified")
            val isCollection = respXml.contains("<d:collection/>") || extract(respXml, "d:resourcetype")?.contains("collection") == true
            if (href != null) {
                resources.add(WebDavResource(href, status, displayName, contentLength, lastModified, isCollection))
            }
        }
        return resources
    }

    private fun extract(xml: String, tag: String): String? {
        return Regex("""<$tag[^>]*>(.+?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()
    }
}
