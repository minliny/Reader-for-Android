package com.reader.android.data.adapter

data class RemoteFileEntry(
    val href: String,
    val displayName: String,
    val isDirectory: Boolean,
    val contentLength: Long = 0,
    val lastModified: String? = null
) {
    val extension: String get() = displayName.substringAfterLast('.', "").lowercase()
}

class RemoteFileListingParser(private val xmlParser: WebDavXmlParser = WebDavXmlParser()) {
    fun parse(xml: String): List<RemoteFileEntry> {
        return xmlParser.parseMultiStatus(xml).map { res ->
            RemoteFileEntry(
                href = res.href,
                displayName = res.displayName ?: res.href.split("/").last(),
                isDirectory = res.isCollection,
                contentLength = res.contentLength,
                lastModified = res.lastModified
            )
        }.sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.displayName })
    }
}
