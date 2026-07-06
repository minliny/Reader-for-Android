package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.AntiBotChallengeHandler
import com.reader.host.AntiBotChallengeType
import com.reader.host.AntiBotHttpResponse
import com.reader.host.HostReply
import com.reader.host.StubAntiBotExecutor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Host-side proof for the `anti_bot` lane (item 3b): verifies
 * [AntiBotChallengeHandler] honours the Core contract
 * (`HostErrorCode::ChallengeRequired` /
 * `HostErrorDiagnostics::challenge_required` defined in
 * `crates/reader-contract/src/host.rs` lines 587-708) end-to-end at the
 * handler/router tier.
 *
 * **Proof tier**: handler/router — mirrors [HostWebViewRenderProofTest]'s proof
 * level. The handler is invoked directly with a URL + headers + cookieJarId;
 * execution is isolated via [StubAntiBotExecutor] so nothing touches the
 * network. JNI / Core-event routing is already proven by [CoreEndToEndTest].
 *
 * **Device-headless/App tier**: real anti_bot source L1-L5 (Cloudflare JS
 * challenge solving, slider captcha, reCAPTCHA v2) is pending device proof and
 * is NOT claimed here. [com.reader.host.OkHttpAntiBotExecutor] currently throws
 * [NotImplementedError] (fail-closed).
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for the `anti_bot` lane.
 */
@RunWith(AndroidJUnit4::class)
class HostAntiBotProofTest {

    /**
     * Proof 1: clean 200 OK response — handler delegates to the executor,
     * detector returns [AntiBotChallengeType.NONE], and the handler returns
     * `host.complete` with `{ body, statusCode, finalUrl }`.
     */
    @Test
    fun cleanResponseReturnsCompletedResult() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 200,
                body = "<html><body>Hello World</body></html>",
                headers = mapOf("Content-Type" to "text/html"),
                finalUrl = "https://example.test/article"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://example.test/article",
            headers = mapOf("User-Agent" to "Reader/1.0"),
            cookieJarId = null
        )

        assertTrue(
            "clean response must complete, got: ${reply.kind()}",
            reply.isComplete()
        )
        val result = JSONObject((reply as HostReply.Complete).resultJson())
        assertEquals(
            "statusCode must be 200",
            200,
            result.getInt("statusCode")
        )
        assertTrue(
            "body must contain 'Hello World', got: ${result.getString("body")}",
            result.getString("body").contains("Hello World")
        )
        assertEquals(
            "finalUrl must match",
            "https://example.test/article",
            result.getString("finalUrl")
        )
    }

    /**
     * Proof 2: Cloudflare JS challenge — Stub returns 503 with Cloudflare
     * markers (`jschl` + `cf-browser-verification`), handler returns
     * `host.error` with code `CHALLENGE_REQUIRED` and `challengeType =
     * "cloudflare_js"` in the diagnostic details.
     */
    @Test
    fun cloudflareJsChallengeReturnsChallengeRequired() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 503,
                body = "<html><head><script>var jschl_answer=...</script>" +
                    "<noscript>cf-browser-verification</noscript></head></html>",
                headers = mapOf("Server" to "cloudflare"),
                finalUrl = "https://protected.test/"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://protected.test/",
            headers = emptyMap(),
            cookieJarId = null
        )

        assertTrue(
            "Cloudflare challenge must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be CHALLENGE_REQUIRED",
            "CHALLENGE_REQUIRED",
            error.code()
        )
        assertEquals(
            "CHALLENGE_REQUIRED must not be retryable (fail-closed signal)",
            false,
            error.retryable()
        )
        val diagnostic = JSONObject(error.message())
        assertEquals(
            "diagnostic code must be CHALLENGE_REQUIRED",
            "CHALLENGE_REQUIRED",
            diagnostic.getString("code")
        )
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "lane must be anti_bot",
            "anti_bot",
            details.getString("lane")
        )
        assertEquals(
            "challengeType must be cloudflare_js",
            "cloudflare_js",
            details.getString("challengeType")
        )
        assertEquals(
            "url must match the fetched URL",
            "https://protected.test/",
            details.getString("url")
        )
        assertEquals(
            "autoRetryable must be false",
            false,
            details.getBoolean("autoRetryable")
        )
    }

    /**
     * Proof 3: slider captcha — Stub returns 200 with slider captcha markers
     * (`slider` + `captcha`), handler returns `CHALLENGE_REQUIRED` with
     * `challengeType = "slider_captcha"`.
     */
    @Test
    fun sliderCaptchaReturnsChallengeRequired() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 200,
                body = "<html><body><div class='slider-captcha'>" +
                    "Please slide to verify</div></body></html>",
                headers = emptyMap(),
                finalUrl = "https://captcha.test/verify"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://captcha.test/verify",
            headers = emptyMap(),
            cookieJarId = null
        )

        assertTrue(
            "slider captcha must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be CHALLENGE_REQUIRED",
            "CHALLENGE_REQUIRED",
            error.code()
        )
        val diagnostic = JSONObject(error.message())
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "challengeType must be slider_captcha",
            "slider_captcha",
            details.getString("challengeType")
        )
        assertEquals(
            "lane must be anti_bot",
            "anti_bot",
            details.getString("lane")
        )
    }

    /**
     * Proof 4: reCAPTCHA v2 — Stub returns 200 with `g-recaptcha` marker,
     * handler returns `CHALLENGE_REQUIRED` with `challengeType =
     * "recaptcha_v2"`.
     */
    @Test
    fun recaptchaReturnsChallengeRequired() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 200,
                body = "<html><body><div class='g-recaptcha'" +
                    " data-sitekey='6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe'>" +
                    "</div></body></html>",
                headers = emptyMap(),
                finalUrl = "https://recaptcha.test/page"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://recaptcha.test/page",
            headers = emptyMap(),
            cookieJarId = null
        )

        assertTrue(
            "reCAPTCHA must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be CHALLENGE_REQUIRED",
            "CHALLENGE_REQUIRED",
            error.code()
        )
        val diagnostic = JSONObject(error.message())
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "challengeType must be recaptcha_v2",
            "recaptcha_v2",
            details.getString("challengeType")
        )
    }

    /**
     * Proof 5: cookieJarId preserved — handler called with
     * `cookieJarId = "source-abc-123"`, the error diagnostic details must
     * include this cookieJarId so Core can correlate the challenge with the
     * source's cookie jar lifecycle.
     */
    @Test
    fun cookieJarIdPreservedInErrorDetails() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 503,
                body = "<html>cf-browser-verification</html>",
                headers = emptyMap(),
                finalUrl = "https://protected.test/article"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://protected.test/article",
            headers = emptyMap(),
            cookieJarId = "source-abc-123"
        )

        assertTrue(
            "challenge with cookieJarId must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        val diagnostic = JSONObject(error.message())
        val details = diagnostic.getJSONObject("details")
        assertTrue(
            "details must include cookieJarId key",
            details.has("cookieJarId")
        )
        assertEquals(
            "cookieJarId must be preserved as source-abc-123",
            "source-abc-123",
            details.getString("cookieJarId")
        )
    }

    /**
     * Proof 6: unsupported challenge — Stub returns 403 with no known
     * challenge markers, handler returns `CHALLENGE_REQUIRED` with
     * `challengeType = "unsupported"` (Host cannot solve automatically).
     */
    @Test
    fun unsupportedChallengeReturnsChallengeRequired() {
        val executor = StubAntiBotExecutor(
            response = AntiBotHttpResponse(
                statusCode = 403,
                body = "<html><body>Access Denied</body></html>",
                headers = emptyMap(),
                finalUrl = "https://blocked.test/"
            )
        )
        val handler = AntiBotChallengeHandler(executor)

        val reply = handler.handle(
            url = "https://blocked.test/",
            headers = emptyMap(),
            cookieJarId = null
        )

        assertTrue(
            "403 with no markers must error, got: ${reply.kind()}",
            reply.isError()
        )
        val error = reply as HostReply.Error
        assertEquals(
            "error code must be CHALLENGE_REQUIRED",
            "CHALLENGE_REQUIRED",
            error.code()
        )
        val diagnostic = JSONObject(error.message())
        val details = diagnostic.getJSONObject("details")
        assertEquals(
            "challengeType must be unsupported",
            "unsupported",
            details.getString("challengeType")
        )
        assertNotNull(
            "diagnostic must have a message",
            diagnostic.optString("message")
        )
    }
}
