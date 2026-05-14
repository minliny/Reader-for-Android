package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestHeadersTest {

    @Test
    fun `toMap includes user agent by default`() {
        val headers = RequestHeaders()
        val map = headers.toMap()
        assertTrue(map["User-Agent"]?.contains("Mozilla") == true)
    }

    @Test
    fun `toMap includes cookie when set`() {
        val headers = RequestHeaders.withCookie("session=abc; token=xyz")
        assertEquals("session=abc; token=xyz", headers.toMap()["Cookie"])
    }

    @Test
    fun `toMap includes custom headers`() {
        val headers = RequestHeaders(customHeaders = mapOf("Referer" to "http://source.com"))
        assertEquals("http://source.com", headers.toMap()["Referer"])
    }

    @Test
    fun `custom UA overrides default`() {
        val headers = RequestHeaders.withUA("ReaderApp/1.0")
        assertEquals("ReaderApp/1.0", headers.toMap()["User-Agent"])
    }

    @Test
    fun `null cookie header omitted from map`() {
        val headers = RequestHeaders()
        val map = headers.toMap()
        assertTrue(!map.containsKey("Cookie"))
    }

    @Test
    fun `custom headers override User-Agent when key matches`() {
        val headers = RequestHeaders(customHeaders = mapOf("User-Agent" to "Custom/1.0"))
        assertEquals("Custom/1.0", headers.toMap()["User-Agent"])
    }
}
