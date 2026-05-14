package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubInventoryTest {

    @Test
    fun `EpubManifestItem holds all fields`() {
        val item = EpubManifestItem("cover", "image/cover.jpg", "image/jpeg", "cover-image")
        assertEquals("cover", item.id)
        assertEquals("image/cover.jpg", item.href)
        assertEquals("cover-image", item.properties)
    }

    @Test
    fun `EpubSpineItem with linear attribute`() {
        val spine = EpubSpineItem("ch1", linear = true)
        assertTrue(spine.linear)
        assertEquals("ch1", spine.idref)
    }

    @Test
    fun `EpubMetadata with title and creator`() {
        val meta = EpubMetadata(title = "My Book", creator = "Author Name", language = "zh")
        assertEquals("My Book", meta.title)
        assertEquals("Author Name", meta.creator)
    }

    @Test
    fun `EpubInventory assembles metadata manifest and spine`() {
        val inventory = EpubInventory(
            metadata = EpubMetadata(title = "Test"),
            manifest = listOf(EpubManifestItem("ncx", "toc.ncx", "application/x-dtbncx+xml")),
            spine = listOf(EpubSpineItem("ch1")),
            rootFilePath = "OEBPS/content.opf"
        )
        assertEquals(1, inventory.manifest.size)
        assertEquals(1, inventory.spine.size)
        assertEquals("OEBPS/content.opf", inventory.rootFilePath)
    }

    @Test
    fun `parse container XML extracts rootfile path`() {
        val xml = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
        val parser = EpubContainerParser()
        assertEquals("OEBPS/content.opf", parser.parseContainerXml(xml))
    }

    @Test
    fun `parse invalid container XML returns null`() {
        val parser = EpubContainerParser()
        assertNull(parser.parseContainerXml("<html>not epub</html>"))
    }
}
