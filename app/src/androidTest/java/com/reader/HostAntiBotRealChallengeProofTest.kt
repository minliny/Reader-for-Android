package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.AntiBotCapabilityHandler
import com.reader.host.AntiBotChallengeHandler
import com.reader.host.AntiBotHttpResponse
import com.reader.host.HostAdapter
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.host.StubAntiBotExecutor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 real-executor proof for the `anti_bot` lane.
 *
 * Verifies [AntiBotCapabilityHandler] with the default
 * [com.reader.host.OkHttpAntiBotExecutor] (real OkHttp + UA/Referer injection)
 * fetches a real public endpoint and returns `host.complete`. Network failures
 * map to structured `INTERNAL` errors. Also re-verifies the fail-closed
 * detector classifies 503+jschl / 403 / 200 correctly (detector regression
 * alongside the real executor — the detector is the fail-closed safety net
 * that prevents the host from silently swallowing challenge pages).
 *
 * This is an executor-tier proof, NOT an App-level proof.
 * `canEnterMainline` remains false.
 */
@RunWith(AndroidJUnit4::class)
class HostAntiBotRealChallengeProofTest {

    /**
     * Real fetch `https://example.com/` (IANA-maintained, returns 200 to any
     * UA). Asserts 2xx, non-empty body, non-empty finalUrl. Proves
     * [OkHttpAntiBotExecutor] does real TLS + HTTP + UA injection on-device.
     */
    @Test
    fun realFetchReturnsCompletedResult() {
        val adapter = HostAdapter()
        adapter.register(
            AntiBotCapabilityHandler.CAPABILITY,
            AntiBotCapabilityHandler()
        )

        val request = HostRequest(
            1L,
            5001L,
            AntiBotCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://example.com/")
            }.toString()
        )

        val reply = adapter.dispatch(request)
        assertTrue(
            "real anti_bot.fetch must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        val status = result.optInt("statusCode")
        assertTrue(
            "statusCode must be 2xx, got: $status",
            status in 200..299
        )
        assertTrue(
            "body must be non-empty",
            result.optString("body").isNotEmpty()
        )
        assertTrue(
            "finalUrl must be non-empty",
            result.optString("finalUrl").isNotEmpty()
        )
    }

    /**
     * `.invalid` TLD → DNS failure → structured `INTERNAL` error (retryable).
     * Must NOT throw.
     */
    @Test
    fun invalidUrlReturnsStructuredInternalError() {
        val adapter = HostAdapter()
        adapter.register(
            AntiBotCapabilityHandler.CAPABILITY,
            AntiBotCapabilityHandler()
        )

        val request = HostRequest(
            1L,
            5002L,
            AntiBotCapabilityHandler.CAPABILITY,
            JSONObject().apply {
                put("url", "https://nonexistent.invalid/")
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
            "network failure should be retryable",
            error.retryable()
        )
    }

    /**
     * Fail-closed detector regression: 503 + jschl markers →
     * `CHALLENGE_REQUIRED` with `challengeType = cloudflare_js`. Uses
     * [StubAntiBotExecutor] so the detector is exercised deterministically
     * (not dependent on finding a real Cloudflare-protected URL).
     */
    @Test
    fun cloudflareJsChallengeReturnsChallengeRequired() {
        val handler = AntiBotChallengeHandler(
            StubAntiBotExecutor(
                response = AntiBotHttpResponse(
                    statusCode = 503,
                    body = "<html><body>jschl answer; cf-browser-verification</body></html>",
                    headers = mapOf("Content-Type" to "text/html"),
                    finalUrl = "https://protected.test/"
                )
            )
        )
        val reply = handler.handle(
            url = "https://protected.test/",
            headers = emptyMap(),
            cookieJarId = "jar-1"
        )
        assertTrue("cloudflare_js must error, got: ${reply.kind()}", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("error code must be CHALLENGE_REQUIRED", "CHALLENGE_REQUIRED", error.code())
        assertFalse(
            "CHALLENGE_REQUIRED must not be retryable (fail-closed)",
            error.retryable()
        )
        val diagnostic = JSONObject(error.message())
        assertEquals("diagnostic code", "CHALLENGE_REQUIRED", diagnostic.getString("code"))
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "challengeType must be cloudflare_js",
            "cloudflare_js",
            details.getString("challengeType")
        )
        assertEquals("lane must be anti_bot", "anti_bot", details.getString("lane"))
        assertEquals("cookieJarId must be preserved", "jar-1", details.getString("cookieJarId"))
    }

    /**
     * Fail-closed detector regression: 403 with no known markers →
     * `CHALLENGE_REQUIRED` with `challengeType = unsupported`.
     */
    @Test
    fun unsupported403ReturnsChallengeRequired() {
        val handler = AntiBotChallengeHandler(
            StubAntiBotExecutor(
                response = AntiBotHttpResponse(
                    statusCode = 403,
                    body = "<html><body>Forbidden</body></html>",
                    headers = emptyMap(),
                    finalUrl = "https://blocked.test/"
                )
            )
        )
        val reply = handler.handle(
            url = "https://blocked.test/",
            headers = emptyMap(),
            cookieJarId = null
        )
        assertTrue("unsupported 403 must error", reply.isError())
        val error = reply as HostReply.Error
        assertEquals("error code must be CHALLENGE_REQUIRED", "CHALLENGE_REQUIRED", error.code())
        val diagnostic = JSONObject(error.message())
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "challengeType must be unsupported",
            "unsupported",
            details.getString("challengeType")
        )
    }
}
