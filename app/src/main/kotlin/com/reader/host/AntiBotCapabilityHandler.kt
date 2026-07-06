package com.reader.host

import org.json.JSONObject

/**
 * [CapabilityHandler] adapter for the `anti_bot` lane. Wraps
 * [AntiBotChallengeHandler] (a utility class that does NOT implement
 * [CapabilityHandler]) so it can be registered in [HostAdapter] and
 * dispatched via the standard
 * `host.request -> HostAdapter.dispatch -> handler -> host.complete` round-trip.
 *
 * **Host-private capability**: Core does not define an `anti_bot.*`
 * [com.reader.host.HostCapability] equivalent — anti-bot is a `HostLane` that
 * augments `http.execute` (see `crates/reader-contract/src/host.rs` lines
 * 370-436). This adapter registers under the host-private capability
 * [CAPABILITY] = `"anti_bot.execute"` so the Host can re-dispatch intercepted
 * `http.execute` requests through the anti-bot handler when it detects
 * anti-bot markers. Core never emits this capability directly.
 *
 * **Params** (parsed from [HostRequest.paramsJson]):
 * - `url` (required, non-blank) — target URL to fetch + classify.
 * - `headers` (optional, object) — request headers (UA, Accept, etc.).
 * - `cookieJarId` (optional, string) — per-source cookie jar id.
 *
 * The wrapped [AntiBotChallengeHandler.handle] already maps executor failures
 * (including [NotImplementedError] from [OkHttpAntiBotExecutor]) to structured
 * `host.error` replies, so this adapter only parses params and delegates.
 *
 * **Proof tier**: handler/router — mirrors [WebViewEvaluateJavaScriptHandler].
 */
class AntiBotCapabilityHandler(
    private val handler: AntiBotChallengeHandler =
        AntiBotChallengeHandler(OkHttpAntiBotExecutor())
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(
                INTERNAL,
                "invalid $CAPABILITY params: ${e.message}",
                false
            )
        }

        // --- url (required, non-blank) ---
        val url = params.optString("url", "")
        if (url.isBlank()) {
            return HostReply.error(
                INTERNAL,
                "$CAPABILITY requires non-blank url",
                false
            )
        }

        // --- headers (optional, default empty) ---
        val headers: Map<String, String> = params.optJSONObject("headers")?.let { headersObj ->
            val map = mutableMapOf<String, String>()
            for (key in headersObj.keys()) {
                map[key] = headersObj.optString(key, "")
            }
            map
        } ?: emptyMap()

        // --- cookieJarId (optional, non-blank if present) ---
        val cookieJarId: String? =
            if (params.has("cookieJarId") && !params.isNull("cookieJarId")) {
                params.optString("cookieJarId", "").takeIf { it.isNotBlank() }
            } else {
                null
            }

        return handler.handle(url, headers, cookieJarId)
    }

    companion object {
        /**
         * Host-private capability string for the anti_bot lane. Core does not
         * emit this; the Host dispatches to it internally when re-routing
         * `http.execute` requests that hit anti-bot markers.
         */
        const val CAPABILITY = "anti_bot.execute"

        private const val INTERNAL = "INTERNAL"
    }
}
