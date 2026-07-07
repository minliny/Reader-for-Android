package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.MediaDownloadCapabilityHandler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 multi-resource proof for `media.download`.
 *
 * Verifies [OkHttpMediaDownloadExecutor] handles multiple content types
 * (PNG image, HTML document) and enforces the error path on `.invalid` URLs.
 * Complements [HostMediaRealDownloadProofTest] by proving the executor is not
 * hard-coded to a single resource shape — it correctly parses Content-Type,
 * computes SHA-256 for arbitrary blobs, and returns structured errors for
 * unreachable hosts.
 *
 * This is an executor-tier proof, NOT an App-level proof.
 * `canEnterMainline` remains false.
 */
@RunWith(AndroidJUnit4::class)
class HostMediaMultiResourceProofTest {

    private fun newAdapter(): HostAdapter {
        val adapter = HostAdapter()
        adapter.register(
            MediaDownloadCapabilityHandler.CAPABILITY,
            MediaDownloadCapabilityHandler()
        )
        return adapter
    }

    private fun download(url: String): HostReply {
        val request = HostRequest(
            1L,
            5001L,
            MediaDownloadCapabilityHandler.CAPABILITY,
            JSONObject().apply { put("url", url) }.toString()
        )
        return newAdapter().dispatch(request)
    }

    /**
     * PNG image (~2.6KB): 200 + image/png + non-empty sha256.
     */
    @Test
    fun pngImageDownloadsSuccessfully() {
        val reply = download("https://www.w3.org/Icons/w3c_main.png")
        assertTrue(
            "PNG download must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("statusCode must be 200", 200, result.getInt("statusCode"))
        assertTrue(
            "contentType must start with image/png, got: ${result.optString("contentType")}",
            result.optString("contentType").startsWith("image/png")
        )
        assertTrue(
            "byteLength must be > 0",
            result.optLong("byteLength") > 0
        )
        assertEquals(
            "sha256 must be 64-char hex",
            64,
            result.optString("sha256", "").length
        )
    }

    /**
     * HTML document (W3C homepage): 200 + text/html + byteLength > 1000.
     */
    @Test
    fun htmlDocumentDownloadsSuccessfully() {
        val reply = download("https://www.w3.org/")
        assertTrue(
            "HTML download must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("statusCode must be 200", 200, result.getInt("statusCode"))
        assertTrue(
            "contentType must start with text/html, got: ${result.optString("contentType")}",
            result.optString("contentType").startsWith("text/html")
        )
        assertTrue(
            "HTML byteLength must be > 1000, got: ${result.optLong("byteLength")}",
            result.optLong("byteLength") > 1000
        )
    }

    /**
     * `.invalid` TLD → structured INTERNAL error (not throw).
     */
    @Test
    fun invalidUrlReturnsStructuredError() {
        val reply = download("https://nonexistent.invalid/resource.bin")
        assertTrue(
            "invalid URL must error (not throw), got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals("error code must be INTERNAL", "INTERNAL", error.code())
        assertTrue("network failure should be retryable", error.retryable())
    }
}
