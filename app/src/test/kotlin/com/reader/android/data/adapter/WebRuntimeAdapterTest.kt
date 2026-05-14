package com.reader.android.data.adapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRuntimeAdapterTest {

    private val adapter = FakeWebRuntimeAdapter()

    @Test
    fun `loadHtml echoes back the HTML successfully`() = runBlocking {
        val response = adapter.loadHtml("http://test.com", "<html>test</html>")
        assertTrue(response.success)
        assertEquals("<html>test</html>", response.html)
        assertEquals("http://test.com", response.url)
    }

    @Test
    fun `evaluateJs returns default HTML when request has no html`() = runBlocking {
        val request = WebRuntimeRequest(
            url = "http://test.com",
            jsCode = "document.title",
            sourceUrl = "http://source.com"
        )
        val response = adapter.evaluateJs(request)
        assertTrue(response.success)
        assertTrue(response.html.contains("JS executed"))
    }

    @Test
    fun `evaluateJs returns provided html`() = runBlocking {
        val request = WebRuntimeRequest(
            url = "http://test.com",
            html = "<html><body>content</body></html>",
            jsCode = "parse()",
            sourceUrl = "http://source.com"
        )
        val response = adapter.evaluateJs(request)
        assertEquals("<html><body>content</body></html>", response.html)
        assertTrue(response.success)
    }

    @Test
    fun `WebRuntimeRequest holds all fields`() {
        val req = WebRuntimeRequest(
            url = "u", html = "h", jsCode = "j", sourceUrl = "s"
        )
        assertEquals("u", req.url)
        assertEquals("h", req.html)
        assertEquals("j", req.jsCode)
        assertEquals("s", req.sourceUrl)
    }

    @Test
    fun `WebRuntimeResponse with error has success false`() {
        val resp = WebRuntimeResponse(
            html = "", url = "u", success = false, errorMessage = "timeout"
        )
        assertFalse(resp.success)
        assertEquals("timeout", resp.errorMessage)
    }
}
