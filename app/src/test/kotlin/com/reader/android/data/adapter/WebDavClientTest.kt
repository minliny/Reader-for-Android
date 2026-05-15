package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {
    private val client = FakeWebDavClient()

    @Test
    fun `PUT then GET round trip`() = runBlocking {
        client.putContent("/backup/data.json", """{"version":1}""")
        val response = client.execute(WebDavRequest("/backup/data.json", WebDavMethod.GET))
        assertEquals(200, response.statusCode)
        assertEquals("""{"version":1}""", response.body)
    }

    @Test
    fun `PROPFIND lists files`() = runBlocking {
        client.putContent("/a.txt", "a")
        client.putContent("/b.txt", "b")
        val response = client.execute(WebDavRequest("/", WebDavMethod.PROPFIND))
        assertEquals(207, response.statusCode)
        assertTrue(response.body!!.contains("/a.txt"))
    }

    @Test
    fun `DELETE removes file`() = runBlocking {
        client.putContent("/tmp.txt", "temp")
        client.execute(WebDavRequest("/tmp.txt", WebDavMethod.DELETE))
        val response = client.execute(WebDavRequest("/tmp.txt", WebDavMethod.GET))
        assertEquals(404, response.statusCode)
    }

    @Test
    fun `MKCOL returns 201`() = runBlocking {
        val response = client.execute(WebDavRequest("/newdir", WebDavMethod.MKCOL))
        assertEquals(201, response.statusCode)
    }

    @Test
    fun `GET non-existent returns 404`() = runBlocking {
        val response = client.execute(WebDavRequest("/nonexistent", WebDavMethod.GET))
        assertEquals(404, response.statusCode)
    }
}
