package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpfParserTest {
    private val parser = OpfParser()

    @Test
    fun `parse dc metadata from OPF`() {
        val xml = """
            <package xmlns="http://www.idpf.org/2007/opf">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>My Novel</dc:title>
                <dc:creator>Author Name</dc:creator>
                <dc:language>zh</dc:language>
                <dc:identifier>urn:isbn:123456</dc:identifier>
                <dc:date>2026-01-01</dc:date>
            </metadata>
            </package>
        """.trimIndent()
        val meta = parser.parse(xml)
        assertEquals("My Novel", meta.title)
        assertEquals("Author Name", meta.creator)
        assertEquals("zh", meta.language)
        assertEquals("2026-01-01", meta.date)
    }

    @Test
    fun `extract cover image id from manifest`() {
        val xml = """
            <package><manifest>
                <item id="cover-img" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            </manifest></package>
        """.trimIndent()
        val meta = parser.parse(xml)
        assertEquals("cover-img", meta.coverId)
    }

    @Test
    fun `parse minimal OPF returns empty fields`() {
        val meta = parser.parse("<package><metadata></metadata></package>")
        assertNull(meta.title)
        assertNull(meta.creator)
    }
}
