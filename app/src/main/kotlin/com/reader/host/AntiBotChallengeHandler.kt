/**
 * Anti-bot challenge detection + handler. Alpha proof: handler/router layer
 * with [StubAntiBotExecutor]. Device-headless/App layer (real anti_bot source
 * L1-L5) deferred to beta.
 *
 * This file implements the Android Host side of the `anti_bot` lane (item 3b).
 * Core (Rust) produces request descriptors; the Host executes HTTP fetches and
 * classifies responses. When an unsolvable challenge is detected, the handler
 * returns a `CHALLENGE_REQUIRED` error so Core can mark the source as
 * `host_required` and skip retries — this is a fail-closed signal, not a
 * transient failure (see `HostErrorCode::ChallengeRequired` in
 * `crates/reader-contract/src/host.rs` lines 602-608 and
 * `HostErrorDiagnostics::challenge_required` at lines 691-708).
 *
 * **Proof tier**: handler/router — mirrors the `webview.evaluateJavaScript`
 * proof level ([WebViewEvaluateJavaScriptHandler] / [StubWebViewExecutor]).
 * The handler coordinates HTTP fetch + challenge detection; execution is
 * isolated via [StubAntiBotExecutor] so nothing touches the network.
 *
 * **Device-headless/App tier**: real anti_bot source L1-L5 (Cloudflare JS
 * challenge solving, slider captcha, reCAPTCHA v2) is pending device proof and
 * is NOT claimed here. [OkHttpAntiBotExecutor] currently throws
 * [NotImplementedError] (fail-closed).
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for the `anti_bot` lane.
 *
 * Unlike [CapabilityHandler], this is a utility class that coordinates HTTP
 * fetch + challenge detection. Anti-bot is a lane, not a capability — Core
 * dispatches `http.execute` requests that the Host may route through this
 * handler when it detects anti-bot markers.
 */
package com.reader.host

import org.json.JSONObject

/**
 * Coordinates HTTP fetch + anti-bot challenge detection for the `anti_bot`
 * lane. Fetches the URL via [executor], classifies the response via
 * [detector], and routes to `host.complete` (no challenge) or `host.error`
 * with `CHALLENGE_REQUIRED` (challenge detected).
 *
 * The error diagnostic is structured matching Core's `HostErrorDiagnostics`
 * contract (see `crates/reader-contract/src/host.rs` lines 626-708): the
 * [HostReply.Error] message carries a JSON object with `code`, `phase`,
 * `message`, and `details` (`lane`, `challengeType`, `url`, `autoRetryable`,
 * `cookieJarId`) so Core can deserialize it into `HostErrorDiagnostics`.
 *
 * @param executor Host-owned HTTP fetcher ([StubAntiBotExecutor] for proof,
 *   [OkHttpAntiBotExecutor] for production — currently fail-closed).
 * @param detector Response classifier (defaults to [AntiBotChallengeDetector]).
 */
class AntiBotChallengeHandler(
    private val executor: AntiBotExecutor,
    private val detector: AntiBotChallengeDetector = AntiBotChallengeDetector()
) {

    /**
     * Fetch [url] via the executor, classify the response, and route to
     * `host.complete` (no challenge) or `host.error` with `CHALLENGE_REQUIRED`.
     *
     * @param url Target URL to fetch.
     * @param headers Request headers (UA, Accept, etc.).
     * @param cookieJarId Per-source cookie jar id for session tracking; may be
     *   null. Preserved in error details so Core can correlate the challenge
     *   with the source's cookie jar lifecycle.
     * @return `host.complete` with `{ body, statusCode, finalUrl }` on clean
     *   response, or `host.error` with code `CHALLENGE_REQUIRED` and a JSON
     *   diagnostic in the message on challenge detection.
     */
    fun handle(
        url: String,
        headers: Map<String, String>,
        cookieJarId: String?
    ): HostReply {
        val response: AntiBotHttpResponse
        try {
            response = executor.fetch(url, headers, cookieJarId)
        } catch (e: NotImplementedError) {
            return HostReply.error(
                "NOT_IMPLEMENTED",
                "anti_bot executor not implemented: ${e.message}",
                false
            )
        } catch (e: Exception) {
            return HostReply.error(
                "INTERNAL",
                "anti_bot fetch failed: ${e.message}",
                true
            )
        }

        val detection = detector.detect(response, url, cookieJarId)

        if (detection.challengeType == AntiBotChallengeType.NONE) {
            val result = JSONObject()
            result.put("body", response.body)
            result.put("statusCode", response.statusCode)
            result.put("finalUrl", response.finalUrl)
            return HostReply.complete(result.toString())
        }

        // Challenge detected — build a CHALLENGE_REQUIRED diagnostic matching
        // Core's HostErrorDiagnostics contract (host.rs lines 626-708).
        val challengeTypeStr = detection.challengeType.asString()
        val details = JSONObject()
        details.put("lane", LANE)
        details.put("challengeType", challengeTypeStr)
        details.put("url", url)
        details.put("autoRetryable", false)
        if (cookieJarId != null) {
            details.put("cookieJarId", cookieJarId)
        }

        val diagnostic = JSONObject()
        diagnostic.put("code", ERROR_CODE)
        diagnostic.put("phase", "response")
        diagnostic.put(
            "message",
            "Host lane $LANE encountered an unsolvable challenge ($challengeTypeStr) at $url"
        )
        diagnostic.put("details", details)

        return HostReply.error(
            ERROR_CODE,
            diagnostic.toString(),
            false
        )
    }

    companion object {
        /** Lane identifier matching Core's `HostLane::AntiBot.as_str()` = `"anti_bot"`. */
        const val LANE = "anti_bot"
        /** Error code matching Core's `HostErrorCode::ChallengeRequired` serde form. */
        private const val ERROR_CODE = "CHALLENGE_REQUIRED"
    }
}

/**
 * Host-owned HTTP fetcher for the `anti_bot` lane. Implementations:
 * - [StubAntiBotExecutor] — canned responses for handler/router proof tests.
 * - [OkHttpAntiBotExecutor] — production, backed by OkHttp (currently
 *   [NotImplementedError], fail-closed).
 *
 * Non-suspend: the anti-bot lane does not bridge to async platform APIs in the
 * alpha proof. Production may switch to suspend when real challenge solving
 * (WebView-based Cloudflare JS challenge) is added.
 */
interface AntiBotExecutor {
    /**
     * Fetch [url] with [headers], optionally scoped to [cookieJarId].
     *
     * @return The HTTP response (status, body, headers, final URL after redirects).
     */
    fun fetch(
        url: String,
        headers: Map<String, String>,
        cookieJarId: String?
    ): AntiBotHttpResponse
}

/**
 * HTTP response returned by [AntiBotExecutor.fetch].
 *
 * @property statusCode HTTP status code (e.g. 200, 403, 503).
 * @property body Response body (HTML) used for challenge marker detection.
 * @property headers Response headers (case-insensitive lookup is the caller's
 *   responsibility; this class preserves insertion order).
 * @property finalUrl Final URL after any HTTP redirects.
 */
data class AntiBotHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>,
    val finalUrl: String
)

/**
 * Result of classifying an [AntiBotHttpResponse] for anti-bot challenges.
 *
 * @property challengeType Detected challenge type ([AntiBotChallengeType.NONE]
 *   if no challenge markers found).
 * @property challengeUrl URL where the challenge was encountered.
 * @property cookieJarId Per-source cookie jar id (may be null); preserved so
 *   Core can correlate the challenge with the source's session.
 */
data class AntiBotChallengeDetection(
    val challengeType: AntiBotChallengeType,
    val challengeUrl: String,
    val cookieJarId: String?
)

/**
 * Anti-bot challenge type. The string forms (`asString()`) match the
 * `challenge_type` free-form strings Core's `HostErrorDiagnostics::challenge_required`
 * builder accepts (host.rs lines 688-690).
 *
 * @property NONE No challenge detected — clean response.
 * @property CLOUDFLARE_JS Cloudflare JS challenge (503 + `jschl` or
 *   `cf-browser-verification` markers).
 * @property SLIDER_CAPTCHA Slider captcha (body contains `slider` + `captcha`).
 * @property RECAPTCHA_V2 Google reCAPTCHA v2 (body contains `recaptcha` or
 *   `g-recaptcha`).
 * @property UNSUPPORTED 403 with no known challenge markers — Host cannot
 *   solve automatically.
 */
enum class AntiBotChallengeType {
    NONE,
    CLOUDFLARE_JS,
    SLIDER_CAPTCHA,
    RECAPTCHA_V2,
    UNSUPPORTED;

    /**
     * Stable string form used in error diagnostics. Matches Core's
     * `challenge_type` parameter convention (snake_case).
     */
    fun asString(): String = when (this) {
        NONE -> "none"
        CLOUDFLARE_JS -> "cloudflare_js"
        SLIDER_CAPTCHA -> "slider_captcha"
        RECAPTCHA_V2 -> "recaptcha_v2"
        UNSUPPORTED -> "unsupported"
    }
}

/**
 * Classifies [AntiBotHttpResponse]s into [AntiBotChallengeType]s by checking
 * status code + body markers. Detection order (first match wins):
 *
 * 1. `statusCode == 503` AND (body contains `jschl` OR `cf-browser-verification`)
 *    → [CLOUDFLARE_JS][AntiBotChallengeType.CLOUDFLARE_JS]
 * 2. body contains `slider` AND `captcha`
 *    → [SLIDER_CAPTCHA][AntiBotChallengeType.SLIDER_CAPTCHA]
 * 3. body contains `recaptcha` OR `g-recaptcha`
 *    → [RECAPTCHA_V2][AntiBotChallengeType.RECAPTCHA_V2]
 * 4. `statusCode == 403` (none of the above matched)
 *    → [UNSUPPORTED][AntiBotChallengeType.UNSUPPORTED]
 * 5. otherwise → [NONE][AntiBotChallengeType.NONE]
 *
 * Body marker matching is case-insensitive so the detector tolerates
 * real-world casing variation in challenge pages.
 */
class AntiBotChallengeDetector {

    /**
     * Classify [response] fetched from [url] with optional [cookieJarId].
     *
     * @return An [AntiBotChallengeDetection] carrying the detected type and
     *   context. Never throws — unknown responses classify as [NONE] or
     *   [UNSUPPORTED] rather than erroring.
     */
    fun detect(
        response: AntiBotHttpResponse,
        url: String,
        cookieJarId: String?
    ): AntiBotChallengeDetection {
        val body = response.body
        val statusCode = response.statusCode

        val type = when {
            statusCode == 503 && (
                body.contains("jschl", ignoreCase = true) ||
                body.contains("cf-browser-verification", ignoreCase = true)
            ) -> AntiBotChallengeType.CLOUDFLARE_JS

            body.contains("slider", ignoreCase = true) &&
                body.contains("captcha", ignoreCase = true) -> AntiBotChallengeType.SLIDER_CAPTCHA

            body.contains("recaptcha", ignoreCase = true) ||
                body.contains("g-recaptcha", ignoreCase = true) -> AntiBotChallengeType.RECAPTCHA_V2

            statusCode == 403 -> AntiBotChallengeType.UNSUPPORTED

            else -> AntiBotChallengeType.NONE
        }

        return AntiBotChallengeDetection(
            challengeType = type,
            challengeUrl = url,
            cookieJarId = cookieJarId
        )
    }
}

/**
 * Test stub: returns canned [AntiBotHttpResponse]s, isolating the handler/router
 * tier from real HTTP. Used by `HostAntiBotProofTest`.
 *
 * Construct with either:
 * - `StubAntiBotExecutor(response = ...)` for a single default response
 *   returned for every URL.
 * - `StubAntiBotExecutor(responsesByUrl = mapOf(...))` for per-URL canned
 *   responses (falls back to [defaultResponse] when URL not in the map).
 *
 * Defaults to a clean 200 OK HTML response so unset fields don't accidentally
 * trigger challenge detection.
 */
class StubAntiBotExecutor(
    private val response: AntiBotHttpResponse = cleanResponse(),
    private val responsesByUrl: Map<String, AntiBotHttpResponse> = emptyMap()
) : AntiBotExecutor {
    override fun fetch(
        url: String,
        headers: Map<String, String>,
        cookieJarId: String?
    ): AntiBotHttpResponse {
        return responsesByUrl[url] ?: response
    }

    companion object {
        /** Default clean 200 OK HTML response. */
        fun cleanResponse(): AntiBotHttpResponse = AntiBotHttpResponse(
            statusCode = 200,
            body = "<html><body>Hello</body></html>",
            headers = mapOf("Content-Type" to "text/html"),
            finalUrl = "https://example.test/"
        )
    }
}

/**
 * Production executor backed by OkHttp. Real anti-bot HTTP fetch (with cookie
 * jar scoping, UA rotation, Cloudflare JS challenge solving via WebView) is
 * **not yet implemented** — this stub throws [NotImplementedError] so Core
 * fails closed when the Host has not advertised real anti_bot capability.
 *
 * Device-headless/App tier proof (real anti_bot source L1-L5) is pending
 * device proof. Until then the handler/router tier is proven via
 * [StubAntiBotExecutor].
 */
class OkHttpAntiBotExecutor : AntiBotExecutor {
    override fun fetch(
        url: String,
        headers: Map<String, String>,
        cookieJarId: String?
    ): AntiBotHttpResponse {
        throw NotImplementedError(
            "OkHttpAntiBotExecutor not implemented — alpha proof uses StubAntiBotExecutor"
        )
    }
}
