package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileListingTest {
    private val parser = RemoteFileListingParser()

    @Test
    fun `parse directory listing sorts dirs first then by name`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/books/</d:href><d:propstat><d:prop>
            <d:displayname>books</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            <d:response><d:href>/a.txt</d:href><d:propstat><d:prop>
            <d:displayname>a.txt</d:displayname><d:getcontentlength>100</d:getcontentlength></d:prop></d:propstat></d:response>
            </d:multistatus>
        """.trimIndent()
        val entries = parser.parse(xml)
        assertEquals(2, entries.size)
        assertTrue(entries[0].isDirectory) // dirs first
        assertEquals("a.txt", entries[1].displayName)
    }

    @Test
    fun `extension extracts from display name`() {
        val entry = RemoteFileEntry("/f.txt", "file.txt", false)
        assertEquals("txt", entry.extension)
    }

    @Test
    fun `parse empty listing returns empty`() {
        assertTrue(parser.parse("<d:multistatus></d:multistatus>").isEmpty())
    }
}
