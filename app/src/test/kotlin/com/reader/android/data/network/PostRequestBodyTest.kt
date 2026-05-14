package com.reader.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRequestBodyTest {

    @Test
    fun `form urlencoded body encodes fields`() {
        val body = PostRequestBody.formUrlEncoded(mapOf("key" to "测试", "page" to "1"))
        val encoded = body.toBodyString()
        assertTrue(encoded.contains("key="))
        assertTrue(encoded.contains("page=1"))
    }

    @Test
    fun `JSON body wraps fields in curly braces`() {
        val body = PostRequestBody.json(mapOf("key" to "value"))
        val json = body.toBodyString()
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"key\":\"value\""))
    }

    @Test
    fun `raw body ignores fields`() {
        val body = PostRequestBody.raw(PostContentType.TEXT_PLAIN, "raw content")
        assertEquals("raw content", body.toBodyString())
    }

    @Test
    fun `empty fields produce minimal valid body`() {
        val body = PostRequestBody.formUrlEncoded(emptyMap())
        assertEquals("", body.toBodyString())
    }

    @Test
    fun `PostContentType has 3 values`() {
        assertEquals(3, PostContentType.entries.size)
    }

    @Test
    fun `content type mime strings are correct`() {
        assertEquals("application/x-www-form-urlencoded", PostContentType.FORM_URLENCODED.mimeType)
        assertEquals("application/json", PostContentType.JSON.mimeType)
        assertEquals("text/plain", PostContentType.TEXT_PLAIN.mimeType)
    }
}
