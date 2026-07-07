package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.host.MediaDownloadHandler
import com.reader.host.MediaDownloadRequest
import com.reader.host.MediaDownloadResult
import com.reader.host.StubMediaDownloadExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Host-side proof for the `media.download` lane (item 5b): verifies
 * [MediaDownloadHandler] honours the Core contract
 * (`HostMediaDownloadRequest` / `HostMediaDownloadResponse` defined in
 * `crates/reader-contract/src/host.rs`) end-to-end at the handler/router tier.
 *
 * **Proof tier**: handler/router — mirrors [HostAntiBotProofTest]'s proof
 * level. The handler is invoked directly with a `Map<String, Any?>` params
 * map; execution is isolated via [StubMediaDownloadExecutor] so nothing
 * touches the network. JNI / Core-event routing is already proven by
 * [CoreEndToEndTest].
 *
 * **Executor/App tier**: real OkHttp blob download is covered by
 * [HostMediaRealDownloadProofTest] and [HostMediaMultiResourceProofTest].
 * Full App-level media source-chain proof with persistent cache lifecycle is a
 * separate acceptance gate.
 *
 * **Mirrors**: iOS/HarmonyOS proof structure for the `media.download` lane.
 *
 * Test coverage (8 cases):
 * 1. [fullGetDownloadReturnsResponse] — full GET 200 happy path.
 * 2. [rangeDownloadWithRangeStartAndRangeEnd] — range GET 206 + capture.
 * 3. [headProbeReturnsMetadataOnly] — HEAD probe returns metadata, no body.
 * 4. [cachedResponseReturnsFromCacheTrue] — 304 cache hit, fromCache=true.
 * 5. [rejectsNonHttpScheme] — file:// scheme rejected.
 * 6. [rejectsPostMethod] — POST method rejected.
 * 7. [rejectsRangeEndWithoutRangeStart] — rangeEnd without rangeStart rejected.
 * 8. [rejectsRangeEndBelowRangeStart] — rangeEnd < rangeStart rejected.
 */
@RunWith(AndroidJUnit4::class)
class HostMediaDownloadProofTest {

    /**
     * Proof 1: full GET download — handler accepts a bare URL (default method
     * GET), delegates to the executor, and returns a map with `resourceId`,
     * `statusCode = 200`, `byteLength`, and `fromCache = false`.
     */
    @Test
    fun fullGetDownloadReturnsResponse() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(
                resourceId = "res-001",
                tempPath = "/tmp/res-001.bin",
                statusCode = 200,
                contentType = "audio/mpeg",
                contentLength = 1024L,
                etag = "\"etag-001\"",
                byteLength = 1024L,
                sha256 = "abc123def456",
                fromCache = false,
                finalUrl = "https://example.test/audio.mp3"
            )
        )
        val handler = MediaDownloadHandler(executor)

        val result = handler.handle(
            mapOf("url" to "https://example.test/audio.mp3")
        )

        assertEquals(
            "resourceId must be res-001",
            "res-001",
            result["resourceId"]
        )
        assertEquals(
            "statusCode must be 200 for full GET",
            200,
            result["statusCode"]
        )
        assertEquals(
            "contentType must be audio/mpeg",
            "audio/mpeg",
            result["contentType"]
        )
        assertEquals(
            "byteLength must be 1024",
            1024L,
            result["byteLength"]
        )
        assertEquals(
            "sha256 must match canned value",
            "abc123def456",
            result["sha256"]
        )
        assertFalse(
            "fromCache must be false for fresh GET",
            result["fromCache"] as Boolean
        )
    }

    /**
     * Proof 2: range GET — handler accepts `rangeStart` + `rangeEnd`, the stub
     * captures the parsed request, and the handler returns `statusCode = 206`.
     * Verifies the handler forwarded range info to the executor by inspecting
     * `capturedParams[0]`.
     */
    @Test
    fun rangeDownloadWithRangeStartAndRangeEnd() {
        val captured = mutableListOf<MediaDownloadRequest>()
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(
                resourceId = "res-002",
                tempPath = "/tmp/res-002.bin",
                statusCode = 206,
                contentType = "audio/mpeg",
                contentLength = 7168L,
                byteLength = 7168L,
                fromCache = false,
                finalUrl = "https://example.test/audio.mp3"
            ),
            capturedParams = captured
        )
        val handler = MediaDownloadHandler(executor)

        val result = handler.handle(
            mapOf(
                "url" to "https://example.test/audio.mp3",
                "rangeStart" to 1024L,
                "rangeEnd" to 8191L
            )
        )

        assertEquals(
            "statusCode must be 206 for range GET",
            206,
            result["statusCode"]
        )
        assertEquals(
            "byteLength must be 7168 (8191 - 1024 + 1)",
            7168L,
            result["byteLength"]
        )
        assertEquals(
            "handler must forward exactly one request to executor",
            1,
            captured.size
        )
        assertEquals(
            "captured rangeStart must be 1024",
            1024L,
            captured[0].rangeStart
        )
        assertEquals(
            "captured rangeEnd must be 8191",
            8191L,
            captured[0].rangeEnd
        )
        assertEquals(
            "captured method must default to GET",
            "GET",
            captured[0].method
        )
    }

    /**
     * Proof 3: HEAD probe — `method = "HEAD"` returns metadata only (no body
     * written), so `tempPath` must be null while `statusCode` and
     * `contentLength` are populated.
     */
    @Test
    fun headProbeReturnsMetadataOnly() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(
                resourceId = "res-003",
                tempPath = null,
                statusCode = 200,
                contentType = "audio/mpeg",
                contentLength = 5242880L,
                byteLength = 0L,
                fromCache = false,
                finalUrl = "https://example.test/audio.mp3"
            )
        )
        val handler = MediaDownloadHandler(executor)

        val result = handler.handle(
            mapOf(
                "url" to "https://example.test/audio.mp3",
                "method" to "HEAD"
            )
        )

        assertEquals(
            "statusCode must be 200 for HEAD probe",
            200,
            result["statusCode"]
        )
        assertNull(
            "tempPath must be null for HEAD probe (no body written)",
            result["tempPath"]
        )
        assertEquals(
            "contentLength must carry declared size",
            5242880L,
            result["contentLength"]
        )
        assertEquals(
            "byteLength must be 0 for HEAD probe",
            0L,
            result["byteLength"]
        )
    }

    /**
     * Proof 4: cached 304 — when `cacheKey` is set and the executor returns
     * `fromCache = true` + `statusCode = 304`, the handler returns
     * `fromCache = true`, `statusCode = 304`, and `byteLength = 0`.
     */
    @Test
    fun cachedResponseReturnsFromCacheTrue() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(
                resourceId = "res-004",
                tempPath = null,
                statusCode = 304,
                contentType = null,
                contentLength = null,
                byteLength = 0L,
                fromCache = true,
                finalUrl = "https://example.test/audio.mp3"
            )
        )
        val handler = MediaDownloadHandler(executor)

        val result = handler.handle(
            mapOf(
                "url" to "https://example.test/audio.mp3",
                "cacheKey" to "audio-cache-key-1",
                "ifNoneMatch" to "\"etag-001\""
            )
        )

        assertTrue(
            "fromCache must be true for 304 cache hit",
            result["fromCache"] as Boolean
        )
        assertEquals(
            "statusCode must be 304 for cache hit",
            304,
            result["statusCode"]
        )
        assertEquals(
            "byteLength must be 0 for 304 (no body transferred)",
            0L,
            result["byteLength"]
        )
    }

    /**
     * Validation: `url = "file:///etc/passwd"` is rejected with
     * [IllegalArgumentException] mentioning "http or https scheme" — Core's
     * `HostMediaDownloadRequest::validate` (host.rs) forbids non-http(s)
     * schemes, and the host handler mirrors that fail-closed.
     */
    @Test
    fun rejectsNonHttpScheme() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(resourceId = "should-not-reach", statusCode = 200)
        )
        val handler = MediaDownloadHandler(executor)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            handler.handle(mapOf("url" to "file:///etc/passwd"))
        }
        val message = exception.message ?: ""
        assertTrue(
            "error must mention http or https scheme, got: $message",
            message.contains("http or https scheme")
        )
    }

    /**
     * Validation: `method = "POST"` is rejected with
     * [IllegalArgumentException] mentioning "must be GET or HEAD" — Core only
     * allows GET (full / range download) and HEAD (metadata probe).
     */
    @Test
    fun rejectsPostMethod() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(resourceId = "should-not-reach", statusCode = 200)
        )
        val handler = MediaDownloadHandler(executor)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            handler.handle(
                mapOf(
                    "url" to "https://example.test/audio.mp3",
                    "method" to "POST"
                )
            )
        }
        val message = exception.message ?: ""
        assertTrue(
            "error must mention 'must be GET or HEAD', got: $message",
            message.contains("must be GET or HEAD")
        )
    }

    /**
     * Validation: `rangeEnd` without `rangeStart` is rejected with
     * [IllegalArgumentException] mentioning "rangeEnd requires rangeStart" —
     * a range end without a start is meaningless and forbidden by Core's
     * `HostMediaDownloadRequest::validate`.
     */
    @Test
    fun rejectsRangeEndWithoutRangeStart() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(resourceId = "should-not-reach", statusCode = 200)
        )
        val handler = MediaDownloadHandler(executor)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            handler.handle(
                mapOf(
                    "url" to "https://example.test/audio.mp3",
                    "rangeEnd" to 1024L
                )
            )
        }
        val message = exception.message ?: ""
        assertTrue(
            "error must mention 'rangeEnd requires rangeStart', got: $message",
            message.contains("rangeEnd requires rangeStart")
        )
    }

    /**
     * Validation: `rangeEnd (1024) < rangeStart (2048)` is rejected with
     * [IllegalArgumentException] mentioning
     * "rangeEnd (1024) must be >= rangeStart (2048)" — Core's validate
     * requires `range_end >= range_start` and the handler mirrors the exact
     * diagnostic so Core can surface it without re-validation.
     */
    @Test
    fun rejectsRangeEndBelowRangeStart() {
        val executor = StubMediaDownloadExecutor(
            response = MediaDownloadResult(resourceId = "should-not-reach", statusCode = 200)
        )
        val handler = MediaDownloadHandler(executor)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            handler.handle(
                mapOf(
                    "url" to "https://example.test/audio.mp3",
                    "rangeStart" to 2048L,
                    "rangeEnd" to 1024L
                )
            )
        }
        val message = exception.message ?: ""
        assertTrue(
            "error must mention 'rangeEnd (1024) must be >= rangeStart (2048)', got: $message",
            message.contains("rangeEnd (1024) must be >= rangeStart (2048)")
        )
    }
}
