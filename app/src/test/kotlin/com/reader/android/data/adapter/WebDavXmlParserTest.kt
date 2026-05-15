package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavXmlParserTest {
    private val parser = WebDavXmlParser()

    @Test
    fun `parse MultiStatus with files and collections`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
            <d:response>
                <d:href>/files/</d:href>
                <d:propstat><d:status>HTTP/1.1 200 OK</d:status>
                <d:prop><d:displayname>files</d:displayname>
                <d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
            </d:response>
            <d:response>
                <d:href>/files/book.txt</d:href>
                <d:propstat><d:status>HTTP/1.1 200 OK</d:status>
                <d:prop><d:displayname>book.txt</d:displayname>
                <d:getcontentlength>1024</d:getcontentlength></d:prop></d:propstat>
            </d:response>
            </d:multistatus>
        """.trimIndent()
        val resources = parser.parseMultiStatus(xml)
        assertEquals(2, resources.size)
        assertEquals("/files/", resources[0].href)
        assertTrue(resources[0].isCollection)
        assertEquals("/files/book.txt", resources[1].href)
        assertEquals(1024, resources[1].contentLength)
    }

    @Test
    fun `parse empty MultiStatus returns empty`() {
        val resources = parser.parseMultiStatus("<d:multistatus></d:multistatus>")
        assertTrue(resources.isEmpty())
    }

    @Test
    fun `parse non-XML returns empty`() {
        assertTrue(parser.parseMultiStatus("not xml").isEmpty())
    }
}
