package com.reader.android.data.adapter

class OpfParser {
    fun parseMetadata(xml: String): EpubMetadata {
        return EpubMetadata(
            title = extract(xml, Regex("""<dc:title[^>]*>(.+?)</dc:title>""")),
            creator = extract(xml, Regex("""<dc:creator[^>]*>(.+?)</dc:creator>""")),
            language = extract(xml, Regex("""<dc:language[^>]*>(.+?)</dc:language>""")),
            identifier = extract(xml, Regex("""<dc:identifier[^>]*>(.+?)</dc:identifier>""")),
            publisher = extract(xml, Regex("""<dc:publisher[^>]*>(.+?)</dc:publisher>""")),
            date = extract(xml, Regex("""<dc:date[^>]*>(.+?)</dc:date>""")),
            coverId = extractCoverId(xml)
        )
    }

    fun parseManifest(xml: String): List<EpubManifestItem> {
        val items = mutableListOf<EpubManifestItem>()
        val itemPattern = Regex("""<item[^>]+/>""")
        itemPattern.findAll(xml).forEach { match ->
            val itemXml = match.value
            val id = extractAttr(itemXml, "id")
            val href = extractAttr(itemXml, "href")
            val mediaType = extractAttr(itemXml, "media-type")
            val properties = extractAttr(itemXml, "properties")
            if (id != null && href != null && mediaType != null) {
                items.add(EpubManifestItem(id, href, mediaType, properties))
            }
        }
        return items
    }

    fun parseSpine(xml: String): List<EpubSpineItem> {
        val items = mutableListOf<EpubSpineItem>()
        val spinePattern = Regex("""<itemref[^>]+/>""")
        spinePattern.findAll(xml).forEach { match ->
            val itemXml = match.value
            val idref = extractAttr(itemXml, "idref")
            val linear = extractAttr(itemXml, "linear")?.let { it != "no" } ?: true
            if (idref != null) {
                items.add(EpubSpineItem(idref, linear))
            }
        }
        return items
    }

    fun resolveReadingOrder(manifest: List<EpubManifestItem>, spine: List<EpubSpineItem>): List<String> {
        val manifestMap = manifest.associateBy { it.id }
        return spine.mapNotNull { manifestMap[it.idref]?.href }
    }

    private fun extract(xml: String, regex: Regex): String? =
        regex.find(xml)?.groupValues?.get(1)?.trim()

    private fun extractAttr(xml: String, name: String): String? {
        return Regex("""$name="([^"]+)"""").find(xml)?.groupValues?.get(1)
    }

    private fun extractCoverId(xml: String): String? {
        val coverItem = Regex("""<item[^>]*properties="[^"]*cover-image[^"]*"[^>]*/>""")
            .find(xml)?.value ?: return null
        return extractAttr(coverItem, "id")
    }
}
