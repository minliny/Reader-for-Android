package com.reader.android.data.adapter

data class EpubManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String? = null
)

data class EpubSpineItem(
    val idref: String,
    val linear: Boolean = true
)

data class EpubMetadata(
    val title: String? = null,
    val creator: String? = null,
    val language: String? = null,
    val identifier: String? = null,
    val publisher: String? = null,
    val date: String? = null,
    val coverId: String? = null
)

data class EpubInventory(
    val metadata: EpubMetadata? = null,
    val manifest: List<EpubManifestItem> = emptyList(),
    val spine: List<EpubSpineItem> = emptyList(),
    val rootFilePath: String? = null
)

class EpubContainerParser {
    fun parseContainerXml(xml: String): String? {
        val pattern = Regex("""<rootfile[^>]*full-path="([^"]+)"[^>]*/>""")
        return pattern.find(xml)?.groupValues?.get(1)
    }
}
