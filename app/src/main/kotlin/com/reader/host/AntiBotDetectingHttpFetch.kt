package com.reader.host

/**
 * 阶段 5 — [HttpFetch] decorator that runs [AntiBotChallengeDetector] on
 * every response and rejects challenge pages before they reach Core.
 *
 * **Why this exists**: `anti_bot.execute` is a host-private capability (Core
 * never emits it directly — see [AntiBotCapabilityHandler] kdoc). The Host
 * is responsible for detecting anti-bot markers in `http.execute` responses
 * and re-routing to the challenge handler. Before this decorator, the
 * http.execute path returned challenge pages (503 + jschl, reCAPTCHA, etc.)
 * as if they were normal content, so Core would try to parse a Cloudflare
 * interstitial as book content and fail in confusing ways.
 *
 * **Detection contract**: after [delegate] returns an [HttpResponse], the
 * detector inspects `status + body`. If a challenge is detected:
 *  - [AntiBotChallengeType.CLOUDFLARE_JS]
 *  - [AntiBotChallengeType.SLIDER_CAPTCHA]
 *  - [AntiBotChallengeType.RECAPTCHA_V2]
 *  - [AntiBotChallengeType.UNSUPPORTED] (403 with no known markers)
 *
 * the decorator throws [AntiBotChallengeRequiredException] so [HttpExecuteHandler]
 * maps it to a retryable `INTERNAL` error with the challenge diagnostics in
 * the message. Core's book-source engine can then surface "challenge
 * required" to the user instead of silently swallowing the page.
 *
 * When the detector classifies the response as [AntiBotChallengeType.NONE]
 * (clean 200, 2xx, redirects, etc.), the original [HttpResponse] is returned
 * untouched — zero overhead for the common path.
 *
 * **Proof tier**: production wiring — wraps the real [OkHttpHostTransport]
 * in [com.reader.api.ReaderCoreClient.buildHostRuntime]. JVM proof uses a
 * fake [HttpFetch] that returns canned challenge pages; device proof uses
 * MockWebServer to serve a 503+jschl page and asserts the decorator rejects
 * it.
 *
 * @param delegate The underlying [HttpFetch] (typically [OkHttpHostTransport]).
 * @param detector The challenge detector. Defaults to the production
 *   [AntiBotChallengeDetector] — override in tests.
 */
class AntiBotDetectingHttpFetch(
    private val delegate: HttpFetch,
    private val detector: AntiBotChallengeDetector = AntiBotChallengeDetector()
) : HttpFetch {

    @Throws(Exception::class)
    override fun fetch(request: HttpRequest): HttpResponse {
        val response = delegate.fetch(request)

        // Build the AntiBotHttpResponse shape the detector expects. We pass
        // the request URL as the challenge URL (finalUrl may differ after
        // redirects, but detection only reads statusCode + body, so this is
        // sufficient).
        val antiBotResponse = AntiBotHttpResponse(
            statusCode = response.status(),
            body = response.body(),
            headers = response.headers() ?: emptyMap(),
            finalUrl = response.finalUrl() ?: request.url()
        )
        val detection = detector.detect(antiBotResponse, request.url(), cookieJarId = null)

        if (detection.challengeType != AntiBotChallengeType.NONE) {
            throw AntiBotChallengeRequiredException(
                challengeType = detection.challengeType,
                url = request.url(),
                statusCode = response.status()
            )
        }
        return response
    }
}

/**
 * Thrown by [AntiBotDetectingHttpFetch] when an http.execute response
 * contains anti-bot challenge markers. Caught by [HttpExecuteHandler] and
 * mapped to a retryable `INTERNAL` host error whose message carries the
 * challenge diagnostics (type + url + statusCode) so Core / the user can
 * distinguish "challenge required" from a generic transport failure.
 *
 * The exception is deliberately NOT a [HostReply] — the decorator sits
 * below the handler, so it communicates via exceptions. The handler's
 * `catch (e: Exception)` block maps it to `HostReply.error("INTERNAL",
 * "http.execute fetch failed: ...", true)`.
 */
class AntiBotChallengeRequiredException(
    val challengeType: AntiBotChallengeType,
    val url: String,
    val statusCode: Int
) : RuntimeException(
    "anti-bot challenge required: type=${challengeType.asString()} url=$url statusCode=$statusCode"
)
