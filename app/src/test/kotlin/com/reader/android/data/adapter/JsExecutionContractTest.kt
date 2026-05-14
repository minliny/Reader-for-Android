package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsExecutionContractTest {

    @Test
    fun `JsRequest holds all fields`() {
        val req = JsRequest(
            code = "document.title",
            contextUrl = "http://book.com/ch1",
            sourceUrl = "http://source.com"
        )
        assertEquals("document.title", req.code)
        assertEquals("http://book.com/ch1", req.contextUrl)
        assertEquals("http://source.com", req.sourceUrl)
    }

    @Test
    fun `JsResponse success has null error`() {
        val resp = JsResponse(result = "title", success = true)
        assertTrue(resp.success)
        assertEquals("title", resp.result)
        assertNull(resp.error)
    }

    @Test
    fun `JsResponse failure has error details`() {
        val error = JsError(type = JsErrorType.RUNTIME, message = "undefined is not a function")
        val resp = JsResponse(result = null, success = false, error = error)
        assertFalse(resp.success)
        assertNull(resp.result)
        assertNotNull(resp.error)
        assertEquals(JsErrorType.RUNTIME, resp.error!!.type)
    }

    @Test
    fun `JsError with line number`() {
        val error = JsError(type = JsErrorType.SYNTAX, message = "Unexpected token", line = 42)
        assertEquals(42, error.line)
        assertEquals(JsErrorType.SYNTAX, error.type)
    }

    @Test
    fun `all 5 JsErrorType values are defined`() {
        assertEquals(5, JsErrorType.entries.size)
    }
}
