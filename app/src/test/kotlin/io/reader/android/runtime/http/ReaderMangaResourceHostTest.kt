package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import io.reader.android.runtime.ReaderMangaResourceStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ReaderMangaResourceHostTest {
    private val host = ReaderMangaResourceHost(ReaderHttpHost(), ReaderMangaResourceStore(tempDirectory()))

    companion object {
        private fun tempDirectory() = java.nio.file.Files.createTempDirectory("reader-manga-host-").toFile()
    }

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

    @Test fun outputStageAdmitsIdentityConsumerButDefersGraphicsPlans() = runBlocking {
        val reply = host.execute(ReaderCoreEventActor.HostEnvelope(
            runtimeSession = "runtime", requestId = 1, requestToken = "token", operationId = 2,
            capability = ReaderMangaResourceHost.CAPABILITY,
            parametersJson = JSONObject().put("stage", "output").put("graphicsPlan", JSONObject.NULL)
                .put("transferId", "rmt1:${"a".repeat(64)}").put("resourceRef", "resource-1")
                .put("assetId", 1).put("operationId", 2).put("bytes", 3).toString(),
        ))
        assertTrue(reply is ReaderCoreEventActor.HostReply.AssetConsume)
    }

    @Test fun graphicsPlanShapeRequiresCanonical14StripPartition() = runBlocking {
        val strips = org.json.JSONArray()
        for (index in 0 until 14) {
            val height = if (index == 13) 12 else 7
            strips.put(JSONObject().put("sourceY", index * 7).put("targetY", 103 - index * 7 - height).put("height", height))
        }
        val plan = JSONObject().put("__readerMangaGraphicsPlan", true).put("toJSON", JSONObject.NULL)
            .put("width", 4).put("height", 103).put("format", "jpeg").put("quality", 90)
            .put("pixelFormat", "rgb565").put("strips", strips)
        val reply = host.execute(ReaderCoreEventActor.HostEnvelope(
            runtimeSession = "runtime", requestId = 1, requestToken = "token", operationId = 2,
            capability = ReaderMangaResourceHost.CAPABILITY,
            parametersJson = JSONObject().put("stage", "output").put("graphicsPlan", plan)
                .put("transferId", "rmt1:${"a".repeat(64)}").put("resourceRef", "resource-1")
                .put("assetId", 1).put("operationId", 2).put("bytes", 3).toString(),
        ))
        assertTrue(reply is ReaderCoreEventActor.HostReply.AssetConsume)
    }

    @Test fun inspectInputStageRequiresACommittedResource() = runBlocking {
        val reply = host.execute(ReaderCoreEventActor.HostEnvelope(
            runtimeSession = "runtime", requestId = 1, requestToken = "token", operationId = 2,
            capability = ReaderMangaResourceHost.CAPABILITY,
            parametersJson = JSONObject().put("stage", "inspectInput")
                .put("transferId", "rmt1:${"a".repeat(64)}").put("sha256", "a".repeat(64)).put("bytes", 128).toString(),
        ))
        assertTrue(reply is ReaderCoreEventActor.HostReply.Error)
        assertEquals("INTERNAL", (reply as ReaderCoreEventActor.HostReply.Error).code)
    }

    @Test fun delayedOrdinaryConsumerReturnsSpecificBudgetErrorBeforeBitmapAllocation(): Unit = runBlocking {
        var boundsReads = 0
        val guarded = ReaderMangaResourceHost(ReaderHttpHost(), ReaderMangaResourceStore(tempDirectory())) {
            boundsReads++; 4096 to 32768
        }
        val reply = guarded.execute(outputEnvelope(null))
        assertTrue(reply is ReaderCoreEventActor.HostReply.AssetConsume)
        val result = (reply as ReaderCoreEventActor.HostReply.AssetConsume).consumer(byteArrayOf(1, 2, 3))
        assertError(result, ReaderMangaBitmapPolicy.BUDGET_ERROR)
        assertEquals("RESOURCE_EXHAUSTED", (result as ReaderCoreEventActor.HostReply.Error).code)
        assertEquals(1, boundsReads) // JVM BitmapFactory allocation is unavailable; reaching it would fail this test.
    }

    @Test fun graphicsWorkingBudgetRejectsBeforeAssetConsumerOrNativeDecoderExists(): Unit = runBlocking {
        val plan = graphicsPlan(4096, 4096)
        val reply = host.execute(outputEnvelope(plan))
        assertError(reply, ReaderMangaBitmapPolicy.BUDGET_ERROR)
        assertEquals("RESOURCE_EXHAUSTED", (reply as ReaderCoreEventActor.HostReply.Error).code)
        val hugeHeight = graphicsPlan(1, Int.MAX_VALUE)
        assertError(host.execute(outputEnvelope(hugeHeight)), ReaderMangaBitmapPolicy.BUDGET_ERROR)
    }

    @Test fun graphicsActualBoundsMustMatchThePlanBeforeOpeningNativeDecoder(): Unit = runBlocking {
        val directory = tempDirectory()
        var boundsReads = 0
        val guarded = ReaderMangaResourceHost(ReaderHttpHost(), ReaderMangaResourceStore(directory)) {
            boundsReads++; 4096 to 32768
        }
        val reply = guarded.execute(outputEnvelope(graphicsPlan(4, 103)))
        assertTrue(reply is ReaderCoreEventActor.HostReply.AssetConsume)
        val result = (reply as ReaderCoreEventActor.HostReply.AssetConsume).consumer(byteArrayOf(1, 2, 3))
        assertError(result, "MANGA_GRAPHICS_PLAN_DIMENSIONS")
        assertEquals("INVALID_PARAMS", (result as ReaderCoreEventActor.HostReply.Error).code)
        assertEquals(1, boundsReads)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun graphicsNumericFieldsRejectFractionsStringsTruncationAndStripEndOverflow(): Unit = runBlocking {
        val invalid = listOf(
            graphicsPlan(4, 103).put("width", 4.5),
            graphicsPlan(4, 103).put("width", "4"),
            graphicsPlan(4, 103).put("height", 4_294_967_399L),
            graphicsPlan(4, 103).put("quality", 90.5),
            graphicsPlan(4, 103).apply { getJSONArray("strips").getJSONObject(0).put("sourceY", Int.MAX_VALUE).put("height", 103) },
            graphicsPlan(4, 103).apply { getJSONArray("strips").getJSONObject(0).put("targetY", 0.5) },
        )
        invalid.forEach { plan ->
            val reply = host.execute(outputEnvelope(plan))
            assertTrue(reply is ReaderCoreEventActor.HostReply.Error)
            assertEquals("INVALID_PARAMS", (reply as ReaderCoreEventActor.HostReply.Error).code)
        }
    }

    @Test fun graphicsCoverageRejectsGapsAndOverlapsUsingOnlyBoundedStrips(): Unit = runBlocking {
        for (secondStart in listOf(1, 3)) {
            val plan = graphicsPlan(4, 4).put("strips", org.json.JSONArray()
                .put(JSONObject().put("sourceY", 0).put("targetY", 0).put("height", 2))
                .put(JSONObject().put("sourceY", secondStart).put("targetY", 2).put("height", if (secondStart == 3) 1 else 2)))
            assertError(host.execute(outputEnvelope(plan)), "MANGA_GRAPHICS_PLAN_GAP")
        }
    }

    private fun graphicsPlan(width: Int, height: Int): JSONObject = JSONObject()
        .put("__readerMangaGraphicsPlan", true).put("toJSON", JSONObject.NULL)
        .put("width", width).put("height", height).put("format", "jpeg").put("quality", 90)
        .put("pixelFormat", "rgb565").put("strips", org.json.JSONArray()
            .put(JSONObject().put("sourceY", 0).put("targetY", 0).put("height", height)))

    private fun outputEnvelope(plan: JSONObject?): ReaderCoreEventActor.HostEnvelope = ReaderCoreEventActor.HostEnvelope(
        runtimeSession = "runtime", requestId = 1, requestToken = "token", operationId = 2,
        capability = ReaderMangaResourceHost.CAPABILITY,
        parametersJson = JSONObject().put("stage", "output").put("graphicsPlan", plan ?: JSONObject.NULL)
            .put("transferId", "rmt1:${"a".repeat(64)}").put("resourceRef", "resource-1")
            .put("assetId", 1).put("operationId", 2).put("bytes", 3).toString(),
    )

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
