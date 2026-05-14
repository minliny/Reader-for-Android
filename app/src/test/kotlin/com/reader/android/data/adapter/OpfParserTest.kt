package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpfParserTest {
    private val parser = OpfParser()

    @Test
    fun `parse dc metadata from OPF`() {
        val xml = """
            <package><metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>My Novel</dc:title>
                <dc:creator>Author</dc:creator>
                <dc:language>zh</dc:language>
                <dc:date>2026-01-01</dc:date>
            </metadata></package>
        """.trimIndent()
        val meta = parser.parseMetadata(xml)
        assertEquals("My Novel", meta.title)
        assertEquals("Author", meta.creator)
    }

    @Test
    fun `extract cover image id from manifest`() {
        val xml = """
            <package><manifest>
                <item id="cover-img" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
            </manifest></package>
        """.trimIndent()
        assertEquals("cover-img", parser.parseMetadata(xml).coverId)
    }

    @Test
    fun `parse minimal OPF returns empty fields`() {
        val meta = parser.parseMetadata("<package><metadata></metadata></package>")
        assertNull(meta.title)
    }

    @Test
    fun `parse manifest items`() {
        val xml = """
            <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            </manifest>
        """.trimIndent()
        val items = parser.parseManifest(xml)
        assertEquals(2, items.size)
        assertEquals("toc.ncx", items[0].href)
    }

    @Test
    fun `parse spine reading order`() {
        val xml = """
            <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2" linear="no"/>
                <itemref idref="ch3"/>
            </spine>
        """.trimIndent()
        val spine = parser.parseSpine(xml)
        assertEquals(3, spine.size)
        assertEquals("ch1", spine[0].idref)
        assertTrue(spine[0].linear)
        assertTrue(!spine[1].linear)
    }

    @Test
    fun `resolve reading order from manifest and spine`() {
        val manifest = listOf(
            EpubManifestItem("ch1", "ch1.xhtml", "application/xhtml+xml"),
            EpubManifestItem("ch2", "ch2.xhtml", "application/xhtml+xml")
        )
        val spine = listOf(EpubSpineItem("ch1"), EpubSpineItem("ch2"))
        val order = parser.resolveReadingOrder(manifest, spine)
        assertEquals(listOf("ch1.xhtml", "ch2.xhtml"), order)
    }
}
