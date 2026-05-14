package com.reader.android.data.adapter

class OpfParser {
    fun parse(xml: String): EpubMetadata {
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

    private fun extract(xml: String, regex: Regex): String? =
        regex.find(xml)?.groupValues?.get(1)?.trim()

    private fun extractCoverId(xml: String): String? {
        // <item id="cover" properties="cover-image" .../>
        val coverItem = Regex("""<item[^>]*properties="[^"]*cover-image[^"]*"[^>]*/>""")
            .find(xml)?.value ?: return null
        return Regex("""id="([^"]+)"""").find(coverItem)?.groupValues?.get(1)
    }
}
