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
 * Phase 2 real-executor proof for `media.download`.
 *
 * Verifies [MediaDownloadCapabilityHandler] with the default
 * [com.reader.host.OkHttpMediaDownloadExecutor] (real OkHttp) downloads a
 * real public blob, computes SHA-256, and returns a structured
 * `host.complete`. Network failures (`.invalid` TLD, RFC 6761) must map to a
 * structured `INTERNAL` error (not a thrown exception).
 *
 * This is an executor-tier proof against real network I/O — NOT an App-level
 * proof (no Core command chain, no UI). `canEnterMainline` remains false.
 */
@RunWith(AndroidJUnit4::class)
class HostMediaRealDownloadProofTest {

    /**
     * Download `https://www.w3.org/Icons/w3c_main.png` (stable W3C asset,
     * ~2.6KB PNG). Asserts 200, non-empty body, image/png content-type,
     * 64-char hex SHA-256, and `media-` prefixed resourceId.
     */
    @Test
    fun realDownloadReturnsStatusCodeAndSha256() {
        val adapter = HostAdapter()
        adapter.register(
            MediaDownloadCapabilityHandler.CAPABILITY,
            MediaDownloadCapabilityHandler()
        )

        val request = HostRequest(
            1L,
            5001L,
            MediaDownloadCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://www.w3.org/Icons/w3c_main.png")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "real media.download must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals("statusCode must be 200", 200, result.getInt("statusCode"))
        assertTrue(
            "byteLength must be > 0, got: ${result.optLong("byteLength")}",
            result.optLong("byteLength") > 0
        )
        assertTrue(
            "contentType must start with image/png, got: ${result.optString("contentType")}",
            result.optString("contentType").startsWith("image/png")
        )
        val sha256 = result.optString("sha256", "")
        assertEquals(
            "sha256 must be 64-char hex, got: $sha256",
            64,
            sha256.length
        )
        assertTrue(
            "sha256 must be lowercase hex, got: $sha256",
            sha256.matches(Regex("^[0-9a-f]{64}$"))
        )
        assertTrue(
            "resourceId must start with media-, got: ${result.optString("resourceId")}",
            result.optString("resourceId").startsWith("media-")
        )
        assertTrue(
            "finalUrl must be non-empty",
            result.optString("finalUrl").isNotEmpty()
        )
    }

    /**
     * `.invalid` TLD (RFC 6761) must fail DNS resolution → IOException →
     * structured `INTERNAL` error (retryable = true). Must NOT throw.
     */
    @Test
    fun invalidUrlReturnsStructuredInternalError() {
        val adapter = HostAdapter()
        adapter.register(
            MediaDownloadCapabilityHandler.CAPABILITY,
            MediaDownloadCapabilityHandler()
        )

        val request = HostRequest(
            1L,
            5002L,
            MediaDownloadCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://nonexistent.invalid/media.bin")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "invalid URL must error (not throw), got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be INTERNAL for network failure",
            "INTERNAL",
            error.code()
        )
        assertTrue(
            "network failure should be retryable (transient), got retryable=${error.retryable()}",
            error.retryable()
        )
    }
}
