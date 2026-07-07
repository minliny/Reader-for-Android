/**
 * `media.download` capability handler: bridges Core's media-download requests
 * to a host-owned [MediaDownloadExecutor]. Core (Rust) produces a
 * `HostMediaDownloadRequest` descriptor; the Host executes the HTTP fetch
 * (range GET / HEAD probe / cache lookup) and returns a
 * `HostMediaDownloadResponse` with file path + integrity metadata.
 *
 * This file implements the Android Host side of the `media.download` lane
 * (item 5b). Core never opens a socket (red line 4) — it emits a
 * `host.request` for `media.download` and the Host's [MediaDownloadExecutor]
 * performs the real HTTP I/O (OkHttp on-device).
 *
 * **Proof tier**: handler/router — mirrors the `anti_bot` proof level
 * ([AntiBotChallengeHandler] / [StubAntiBotExecutor]). The handler parses +
 * validates the request, delegates to [MediaDownloadExecutor], and serializes
 * the response with camelCase keys matching Core's
 * `HostMediaDownloadResponse` (see `crates/reader-contract/src/host.rs`).
 * Execution is isolated via [StubMediaDownloadExecutor] so nothing touches the
 * network.
 *
 * **Device-headless/App tier**: real HTTP download (OkHttp range GET, HEAD
 * probe, sha256 hashing, cache-keyed persistence, 304 redirect handling) is
 * pending device proof and is NOT claimed here. [OkHttpMediaDownloadExecutor]
 * currently throws [NotImplementedError] in its init (fail-closed).
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for the `media.download` lane.
 *
 * Like [AntiBotChallengeHandler], this is a utility class that takes plain
 * `Map<String, Any?>` params and returns a plain `Map<String, Any?>` response
 * (camelCase JSON keys). The host adapter (see [com.reader.host.HostAdapter])
 * is responsible for JSON to Map conversion at the dispatch boundary.
 */
package com.reader.host

import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Parses + validates a `media.download` request and delegates to
 * [MediaDownloadExecutor]. Validation is fail-closed: invalid params throw
 * [IllegalArgumentException] so the host adapter can map them to a
 * `host.error` with code `INTERNAL` (Core fails closed on bad descriptors
 * rather than emitting a half-formed request).
 *
 * Request param keys (camelCase, matching Core's `HostMediaDownloadRequest`):
 * - `url` (required, non-blank, http/https scheme)
 * - `method` (optional, "GET" | "HEAD", default "GET")
 * - `headers` (optional, `Map<String, String>`, default empty)
 * - `rangeStart` (optional, u64 — bytes offset start)
 * - `rangeEnd` (optional, u64 — requires `rangeStart`, must be >= `rangeStart`)
 * - `ifNoneMatch` (optional, ETag for conditional GET -> 304)
 * - `ifModifiedSince` (optional, HTTP-date for conditional GET -> 304)
 * - `cacheKey` (optional, opaque key the executor may use for cache lookup)
 * - `savePath` (optional, host filesystem path for the downloaded blob)
 * - `maxBytes` (optional, u64 — hard byte ceiling, must be > 0)
 * - `sessionId` (optional, per-session grouping for cancellation/cleanup)
 * - `timeoutMillis` (optional, u64 — must be > 0)
 *
 * Response keys (camelCase, matching Core's `HostMediaDownloadResponse`):
 * `resourceId`, `tempPath`, `statusCode`, `contentType`, `contentLength`,
 * `etag`, `byteLength`, `sha256`, `fromCache`, `finalUrl`.
 *
 * @param executor Host-owned downloader ([StubMediaDownloadExecutor] for proof,
 *   [OkHttpMediaDownloadExecutor] for production — currently fail-closed).
 */
class MediaDownloadHandler(
    private val executor: MediaDownloadExecutor
) {

    /**
     * Parse + validate [params], call [executor], and return the response as a
     * camelCase-keyed map. Throws [IllegalArgumentException] on any validation
     * failure (bad scheme, unsupported method, range inversion, etc.).
     */
    fun handle(params: Map<String, Any?>): Map<String, Any?> {
        // --- url (required, non-blank, http/https scheme) ---
        val url = params["url"] as? String
        require(!url.isNullOrBlank()) { "$CAPABILITY requires non-blank url" }
        require(
            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true)
        ) { "$CAPABILITY url must use http or https scheme, got: $url" }

        // --- method (optional, default GET, GET/HEAD only) ---
        val rawMethod = (params["method"] as? String)?.takeIf { it.isNotBlank() }
        val method = rawMethod ?: "GET"
        require(method == "GET" || method == "HEAD") {
            "$CAPABILITY method must be GET or HEAD, got: $method"
        }

        // --- headers (optional, default empty) ---
        @Suppress("UNCHECKED_CAST")
        val headers: Map<String, String> =
            (params["headers"] as? Map<String, String>) ?: emptyMap()

        // --- rangeStart / rangeEnd (optional; rangeEnd requires rangeStart + >= rangeStart) ---
        val rangeStart: Long? = (params["rangeStart"] as? Number)?.toLong()
        val rangeEnd: Long? = (params["rangeEnd"] as? Number)?.toLong()
        if (rangeEnd != null) {
            require(rangeStart != null) {
                "$CAPABILITY rangeEnd requires rangeStart"
            }
            require(rangeEnd >= rangeStart) {
                "$CAPABILITY rangeEnd ($rangeEnd) must be >= rangeStart ($rangeStart)"
            }
        }

        // --- ifNoneMatch / ifModifiedSince (optional) ---
        val ifNoneMatch = params["ifNoneMatch"] as? String
        val ifModifiedSince = params["ifModifiedSince"] as? String

        // --- cacheKey / savePath (optional) ---
        val cacheKey = params["cacheKey"] as? String
        val savePath = params["savePath"] as? String

        // --- maxBytes (optional, must be > 0 if present) ---
        val maxBytes: Long? = (params["maxBytes"] as? Number)?.toLong()
        if (maxBytes != null) {
            require(maxBytes > 0) {
                "$CAPABILITY maxBytes must be greater than 0, got: $maxBytes"
            }
        }

        // --- sessionId (optional) ---
        val sessionId = params["sessionId"] as? String

        // --- timeoutMillis (optional, must be > 0 if present) ---
        val timeoutMillis: Long? = (params["timeoutMillis"] as? Number)?.toLong()
        if (timeoutMillis != null) {
            require(timeoutMillis > 0) {
                "$CAPABILITY timeoutMillis must be greater than 0, got: $timeoutMillis"
            }
        }

        val request = MediaDownloadRequest(
            url = url,
            method = method,
            headers = headers,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            ifNoneMatch = ifNoneMatch,
            ifModifiedSince = ifModifiedSince,
            cacheKey = cacheKey,
            savePath = savePath,
            maxBytes = maxBytes,
            sessionId = sessionId,
            timeoutMillis = timeoutMillis
        )

        val result: MediaDownloadResult = executor.execute(request)

        return mapOf(
            "resourceId" to result.resourceId,
            "tempPath" to result.tempPath,
            "statusCode" to result.statusCode,
            "contentType" to result.contentType,
            "contentLength" to result.contentLength,
            "etag" to result.etag,
            "byteLength" to result.byteLength,
            "sha256" to result.sha256,
            "fromCache" to result.fromCache,
            "finalUrl" to result.finalUrl
        )
    }

    companion object {
        /** Capability string matching Core's `HostCapability::MediaDownload`. */
        const val CAPABILITY = "media.download"
    }
}

/**
 * Mirrors Core's `HostMediaDownloadRequest` (see
 * `crates/reader-contract/src/host.rs`). All fields are pre-validated by
 * [MediaDownloadHandler.handle] before reaching the executor.
 *
 * @property url Target URL (http/https, non-blank).
 * @property method HTTP method — "GET" (default) or "HEAD" (metadata probe).
 * @property headers Request headers (UA, Accept, Range, etc.).
 * @property rangeStart Optional Range request start byte (inclusive). When
 *   present with `rangeEnd`, executor should emit `Range: bytes=start-end`.
 * @property rangeEnd Optional Range request end byte (inclusive). Requires
 *   `rangeStart` and must be >= `rangeStart` (validated upstream).
 * @property ifNoneMatch Optional `If-None-Match` ETag for conditional GET.
 * @property ifModifiedSince Optional `If-Modified-Since` HTTP-date.
 * @property cacheKey Opaque cache key the executor may consult for a 304
 *   short-circuit (returns `fromCache = true`).
 * @property savePath Host filesystem path for the downloaded blob; if null
 *   the executor chooses a temp path (returned as `tempPath`).
 * @property maxBytes Hard byte ceiling; executor should abort on overflow.
 * @property sessionId Per-session grouping for cancellation/cleanup.
 * @property timeoutMillis Per-request timeout; executor enforces.
 */
data class MediaDownloadRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val rangeStart: Long? = null,
    val rangeEnd: Long? = null,
    val ifNoneMatch: String? = null,
    val ifModifiedSince: String? = null,
    val cacheKey: String? = null,
    val savePath: String? = null,
    val maxBytes: Long? = null,
    val sessionId: String? = null,
    val timeoutMillis: Long? = null
)

/**
 * Mirrors Core's `HostMediaDownloadResponse` (see
 * `crates/reader-contract/src/host.rs`). All fields are populated by the
 * [MediaDownloadExecutor] implementation.
 *
 * @property resourceId Stable id for the downloaded resource (e.g. sha256
 *   prefix or cache key). Required so Core can reference the blob in later
 *   `media.playback` / `media.release` calls.
 * @property tempPath Host filesystem path where the blob was saved (null for
 *   HEAD probes and 304 cache hits).
 * @property statusCode HTTP status code: 200 (full GET), 206 (partial
 *   content / range), or 304 (not modified / cache hit).
 * @property contentType Response `Content-Type` header value.
 * @property contentLength Response `Content-Length` header value (declared).
 * @property etag Response `ETag` header value (for future conditional GETs).
 * @property byteLength Actual bytes written to `tempPath` (0 for HEAD/304).
 * @property sha256 sha256 hex digest of the downloaded bytes (null for
 *   HEAD/304 or if the executor skipped hashing).
 * @property fromCache True when the response was served from cache (304).
 * @property finalUrl Final URL after any HTTP redirects.
 */
data class MediaDownloadResult(
    val resourceId: String,
    val tempPath: String? = null,
    val statusCode: Int,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val etag: String? = null,
    val byteLength: Long = 0,
    val sha256: String? = null,
    val fromCache: Boolean = false,
    val finalUrl: String? = null
)

/**
 * Host-owned media downloader. Implementations:
 * - [StubMediaDownloadExecutor] — canned results for handler/router proof tests.
 * - [OkHttpMediaDownloadExecutor] — production, backed by OkHttp (currently
 *   [NotImplementedError] in init, fail-closed).
 *
 * Non-suspend: the alpha proof does not bridge to async platform APIs. Real
 * OkHttp execution may switch to suspend when streaming downloads + progress
 * callbacks land in the beta.
 */
interface MediaDownloadExecutor {
    /**
     * Execute [request] and return a [MediaDownloadResult]. Implementations
     * should perform real HTTP I/O (or, for [StubMediaDownloadExecutor],
     * return a canned response) and never throw on expected HTTP status codes
     * (200/206/304/4xx) — only on transport-level failures.
     */
    fun execute(request: MediaDownloadRequest): MediaDownloadResult
}

/**
 * Test stub: returns a canned [MediaDownloadResult] for every request, and
 * captures each request into [capturedParams] so handler/router proofs can
 * verify the handler parsed + forwarded fields correctly. Used by
 * `HostMediaDownloadProofTest` to isolate the handler tier from real HTTP.
 *
 * @param response The canned response returned for every request.
 * @param capturedParams MutableList that receives each request in call order;
 *   defaults to a fresh mutableListOf so tests can inspect calls without
 *   constructing one explicitly.
 */
class StubMediaDownloadExecutor(
    private val response: MediaDownloadResult,
    private val capturedParams: MutableList<MediaDownloadRequest> = mutableListOf()
) : MediaDownloadExecutor {
    override fun execute(request: MediaDownloadRequest): MediaDownloadResult {
        capturedParams.add(request)
        return response
    }
}

/**
 * Production executor backed by OkHttp. Performs real HTTP I/O (range GET,
 * HEAD probe, sha256 hashing, maxBytes enforcement, conditional GET via
 * If-None-Match / If-Modified-Since). Network-level failures throw
 * [IOException] so the host adapter can map them to a structured `INTERNAL`
 * error.
 *
 * Device-headless/App tier proof (real blob download L1-L5 against Legado
 * audio sources with cache-keyed persistence + 304 handling) is pending
 * device proof. The handler/router tier is proven via [StubMediaDownloadExecutor].
 */
class OkHttpMediaDownloadExecutor(
    private val client: OkHttpClient = OkHttpHostTransport.defaultClient()
) : MediaDownloadExecutor {

    /**
     * Execute [request] via OkHttp. Performs real HTTP I/O (range GET / HEAD
     * probe), downloads bytes to memory, computes SHA-256, and returns a
     * [MediaDownloadResult]. Network-level failures throw [IOException] so
     * the host adapter can map them to a structured `INTERNAL` error.
     *
     * HTTP status codes (200/206/304/4xx/5xx) are returned as-is in
     * [MediaDownloadResult.statusCode] — only transport failures throw.
     */
    override fun execute(request: MediaDownloadRequest): MediaDownloadResult {
        val httpUrl = request.url.toHttpUrl()
        val builder = Request.Builder().url(httpUrl)

        // Apply caller-supplied headers first so they can override defaults.
        for ((key, value) in request.headers) {
            builder.header(key, value)
        }

        // Range request: bytes=start-end (or bytes=start- for open-ended).
        if (request.rangeStart != null) {
            val range = if (request.rangeEnd != null) {
                "bytes=${request.rangeStart}-${request.rangeEnd}"
            } else {
                "bytes=${request.rangeStart}-"
            }
            builder.header("Range", range)
        }

        // Conditional GET headers.
        request.ifNoneMatch?.let { builder.header("If-None-Match", it) }
        request.ifModifiedSince?.let { builder.header("If-Modified-Since", it) }

        when (request.method.uppercase()) {
            "GET" -> { /* default: no body */ }
            "HEAD" -> builder.head()
            else -> builder.method(request.method, null)
        }

        client.newCall(builder.build()).execute().use { resp ->
            val contentType = resp.header("Content-Type")
            val contentLength = resp.header("Content-Length")?.toLongOrNull()
            val etag = resp.header("ETag")
            val finalUrl = resp.request.url.toString()

            // HEAD probe: no body to download.
            if (request.method.uppercase() == "HEAD") {
                return MediaDownloadResult(
                    resourceId = "media-head-${resp.code}",
                    tempPath = null,
                    statusCode = resp.code,
                    contentType = contentType,
                    contentLength = contentLength,
                    etag = etag,
                    byteLength = 0L,
                    sha256 = null,
                    fromCache = resp.code == 304,
                    finalUrl = finalUrl
                )
            }

            // GET: read bytes, enforce maxBytes, compute SHA-256.
            val source = resp.body ?: throw IOException("response body is null")
            val maxBytes = request.maxBytes ?: Long.MAX_VALUE
            val sink = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0L
            source.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IOException(
                            "media.download exceeded maxBytes ($maxBytes) at $total bytes"
                        )
                    }
                    sink.write(buffer, 0, read)
                }
            }

            val bytes = sink.toByteArray()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val sha256Hex = digest.joinToString("") { "%02x".format(it) }
            val resourceId = "media-${sha256Hex.substring(0, 16)}"

            return MediaDownloadResult(
                resourceId = resourceId,
                tempPath = null,
                statusCode = resp.code,
                contentType = contentType,
                contentLength = contentLength,
                etag = etag,
                byteLength = total,
                sha256 = sha256Hex,
                fromCache = resp.code == 304,
                finalUrl = finalUrl
            )
        }
    }
}
