package com.reader.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段 5 — JVM proof for [AntiBotDetectingHttpFetch].
 *
 * Verifies the http.execute → anti_bot re-dispatch link that was previously
 * missing (Gap G1/G2). The decorator sits between [HttpExecuteHandler] and
 * the real [HttpFetch], inspecting every response for anti-bot markers:
 *
 *  - clean 200 → transparent passthrough (returns the original [HttpResponse])
 *  - 503 + `jschl` → throws [AntiBotChallengeRequiredException] (CLOUDFLARE_JS)
 *  - body contains `recaptcha` → throws (RECAPTCHA_V2)
 *  - body contains `slider` + `captcha` → throws (SLIDER_CAPTCHA)
 *  - 403 with no markers → throws (UNSUPPORTED)
 *
 * The exception is caught by [HttpExecuteHandler]'s `catch (e: Exception)`
 * block and mapped to a retryable `INTERNAL` host error whose message carries
 * the challenge diagnostics. This test proves the decorator's detection
 * contract without a real network — the [fakeFetch] returns canned pages.
 *
 * Device proof (MockWebServer) is a separate instrumented test.
 */
class AntiBotDetectingHttpFetchJvmTest {

    @Test
    fun `clean 200 passes through untouched`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(200, "<html><body>hello</body></html>"))
        )
        val response = fetch.fetch(HttpRequest("https://example.com/", "GET", emptyMap(), null))
        assertEquals(200, response.status())
        assertEquals("<html><body>hello</body></html>", response.body())
    }

    @Test
    fun `503 with jschl throws CLOUDFLARE_JS`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(503, "<html>jschl-answer</html>"))
        )
        val ex = assertThrows<AntiBotChallengeRequiredException> {
            fetch.fetch(HttpRequest("https://protected.test/", "GET", emptyMap(), null))
        }
        assertEquals(AntiBotChallengeType.CLOUDFLARE_JS, ex.challengeType)
        assertEquals(503, ex.statusCode)
        assertTrue("url must be in message: ${ex.message}", ex.message!!.contains("protected.test"))
    }

    @Test
    fun `reCAPTCHA body throws RECAPTCHA_V2`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(200, "<html><div class=\"g-recaptcha\"></div></html>"))
        )
        val ex = assertThrows<AntiBotChallengeRequiredException> {
            fetch.fetch(HttpRequest("https://captcha.test/", "GET", emptyMap(), null))
        }
        assertEquals(AntiBotChallengeType.RECAPTCHA_V2, ex.challengeType)
    }

    @Test
    fun `slider captcha body throws SLIDER_CAPTCHA`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(200, "<html>slider challenge captcha</html>"))
        )
        val ex = assertThrows<AntiBotChallengeRequiredException> {
            fetch.fetch(HttpRequest("https://slider.test/", "GET", emptyMap(), null))
        }
        assertEquals(AntiBotChallengeType.SLIDER_CAPTCHA, ex.challengeType)
    }

    @Test
    fun `403 with no markers throws UNSUPPORTED`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(403, "<html>forbidden</html>"))
        )
        val ex = assertThrows<AntiBotChallengeRequiredException> {
            fetch.fetch(HttpRequest("https://forbidden.test/", "GET", emptyMap(), null))
        }
        assertEquals(AntiBotChallengeType.UNSUPPORTED, ex.challengeType)
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun `302 redirect passes through untouched`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(302, "", mapOf("Location" to "https://other.test/")))
        )
        val response = fetch.fetch(HttpRequest("https://redirect.test/", "GET", emptyMap(), null))
        assertEquals(302, response.status())
    }

    @Test
    fun `exception message contains challenge type for Core diagnostics`() {
        val fetch = AntiBotDetectingHttpFetch(
            fakeFetch(HttpResponse(503, "<html>cf-browser-verification</html>"))
        )
        val ex = assertThrows<AntiBotChallengeRequiredException> {
            fetch.fetch(HttpRequest("https://cf.test/", "GET", emptyMap(), null))
        }
        // HttpExecuteHandler maps this to "http.execute fetch failed: <message>",
        // so the challenge type must be in the message for Core to distinguish
        // challenge-required from a generic transport failure.
        assertTrue(
            "message must contain 'cloudflare_js': ${ex.message}",
            ex.message!!.contains("cloudflare_js")
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun fakeFetch(response: HttpResponse): HttpFetch = object : HttpFetch {
        @Throws(Exception::class)
        override fun fetch(request: HttpRequest): HttpResponse = response
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
            error("expected ${T::class.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
    }
}
