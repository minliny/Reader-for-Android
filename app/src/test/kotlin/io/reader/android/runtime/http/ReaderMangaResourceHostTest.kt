package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ReaderMangaResourceHostTest {
    private val host = ReaderMangaResourceHost(ReaderHttpHost())

    @Test fun inputParserBindsTransferResourceRequestAndMaxBytes() {
        val parsed = host.parseInput(inputJson(8L * 1024 * 1024))
        assertEquals("input", parsed.stage)
        assertEquals("transfer-1", parsed.transferId)
        assertEquals("resource-1", parsed.resourceRef)
        assertEquals(8L * 1024 * 1024, parsed.maxBytes)
        assertEquals("https://image.test/1.jpg", parsed.request.getString("url"))
    }

    @Test fun inputParserRejectsOversizedBudgetAndUnknownFields() {
        try {
            host.parseInput(inputJson(ReaderMangaResourceHost.MAX_BYTES + 1))
            throw AssertionError("oversized budget was admitted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("maxBytes"))
        }
        val unknown = JSONObject(inputJson(1024)).put("extra", true)
        try {
            host.parseInput(unknown.toString())
            throw AssertionError("unknown field was admitted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("unknown"))
        }
    }

    @Test fun binaryResultAcceptsImageStatusAndSplitsImmutableChunks() {
        val rawBytes = ByteArray(ReaderMangaResourceHost.CHUNK_BYTES + 3) { (it % 251).toByte() }
        val result = JSONObject()
            .put("status", 206)
            .put("headers", JSONObject().put("Content-Type", "image/jpeg"))
            .put("bodyBase64", Base64.getEncoder().encodeToString(rawBytes))
        val reply = host.parseHttpResult(result.toString(), rawBytes.size.toLong())
        assertTrue(reply is ReaderCoreEventActor.HostReply.AssetTransfer)
        reply as ReaderCoreEventActor.HostReply.AssetTransfer
        assertEquals(2, reply.chunks.size)
        assertEquals(ReaderMangaResourceHost.CHUNK_BYTES, reply.chunks.first().size)
        assertEquals(3, reply.chunks.last().size)
        assertEquals("image/jpeg", reply.contentType)
    }

    @Test fun binaryResultRejectsNonImageStatusMissingBodyAndMaxBytes() {
        val body = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val nonImage = JSONObject().put("status", 200)
            .put("headers", JSONObject().put("Content-Type", "text/plain"))
            .put("bodyBase64", body)
        assertError(host.parseHttpResult(nonImage.toString(), 3), "MANGA_IMAGE_CONTENT_TYPE_REQUIRED")

        val status = JSONObject().put("status", 503)
        val statusReply = host.parseHttpResult(status.toString(), 3)
        assertError(statusReply, "MANGA_HTTP_STATUS:503")
        assertTrue((statusReply as ReaderCoreEventActor.HostReply.Error).retryable)

        val tooLarge = JSONObject().put("status", 200)
            .put("headers", JSONObject().put("Content-Type", "image/png"))
            .put("bodyBase64", body)
        assertError(host.parseHttpResult(tooLarge.toString(), 2), "MANGA_IMAGE_MAX_BYTES")
    }

    @Test fun outputStageIsExplicitlyClosedWithoutDecodeOrCache() = runBlocking {
        val reply = host.execute(ReaderCoreEventActor.HostEnvelope(
            runtimeSession = "runtime", requestId = 1, requestToken = "token", operationId = 2,
            capability = ReaderMangaResourceHost.CAPABILITY,
            parametersJson = JSONObject().put("stage", "output").put("assetId", 1).toString(),
        ))
        assertError(reply, "manga resource output/decode is not implemented in this slice")
        assertEquals("UNKNOWN_METHOD", (reply as ReaderCoreEventActor.HostReply.Error).code)
    }

    private fun assertError(reply: ReaderCoreEventActor.HostReply, message: String) {
        assertTrue(reply is ReaderCoreEventActor.HostReply.Error)
        assertEquals(message, (reply as ReaderCoreEventActor.HostReply.Error).message)
    }

    private fun inputJson(maxBytes: Long): String = JSONObject()
        .put("stage", "input")
        .put("transferId", "transfer-1")
        .put("resourceRef", "resource-1")
        .put("maxBytes", maxBytes)
        .put("inspectGraphics", false)
        .put("request", JSONObject().put("url", "https://image.test/1.jpg")
            .put("method", "GET").put("headers", JSONObject())
            .put("timeoutMs", 10_000).put("followRedirects", false)
            .put("maxRedirects", 0).put("retry", JSONObject().put("maxAttempts", 1).put("backoffMillis", 0)))
        .toString()
}
