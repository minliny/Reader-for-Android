package io.reader.android.runtime.http

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import io.reader.android.runtime.ReaderCoreEventActor
import io.reader.android.runtime.ReaderMangaResourceStore
import io.reader.android.runtime.ReaderMangaTransferIdentity
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64

/**
 * Minimal manga input boundary. It downloads encoded bytes through the existing
 * HTTP owner and hands immutable chunks to the event actor; decode, metadata,
 * cache and UI remain separate capabilities.
 */
internal class ReaderMangaResourceHost(
    private val httpHost: ReaderHttpHost,
    private val resourceStore: ReaderMangaResourceStore,
    private val readImageBounds: (ByteArray) -> Pair<Int, Int> = ::platformImageBounds,
) {
    suspend fun execute(envelope: ReaderCoreEventActor.HostEnvelope): ReaderCoreEventActor.HostReply {
        if (envelope.capability != CAPABILITY) return ReaderCoreEventActor.HostReply.Error(
            "UNKNOWN_METHOD", "Android manga resource transfer is not available for this capability", false,
        )
        val raw = try { JSONObject(envelope.parametersJson) } catch (_: org.json.JSONException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_RESOURCE_INPUT_INVALID", false)
        }
        val stage = raw.optString("stage", "")
        if (stage == STAGE_INSPECT) return inspect(raw)
        if (stage == STAGE_OUTPUT) return output(raw)
        if (stage != STAGE_INPUT) return ReaderCoreEventActor.HostReply.Error(
            "UNKNOWN_METHOD", "manga resource output/decode is not implemented in this slice", false,
        )
        val input = try {
            parseInput(raw)
        } catch (error: IllegalArgumentException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", error.message ?: "MANGA_RESOURCE_INPUT_INVALID", false)
        } catch (error: org.json.JSONException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_RESOURCE_INPUT_INVALID", false)
        }
        val httpReply = httpHost.execute(envelope.copy(
            capability = "http.execute",
            parametersJson = input.request.toString(),
        ))
        return when (httpReply) {
            is ReaderCoreEventActor.HostReply.Error -> httpReply
            is ReaderCoreEventActor.HostReply.AssetTransfer -> ReaderCoreEventActor.HostReply.Error(
                "INTERNAL", "MANGA_HTTP_NESTED_ASSET_UNEXPECTED", false,
            )
            is ReaderCoreEventActor.HostReply.AssetConsume -> ReaderCoreEventActor.HostReply.Error(
                "INTERNAL", "MANGA_HTTP_NESTED_ASSET_UNEXPECTED", false,
            )
            is ReaderCoreEventActor.HostReply.Complete -> {
                val transfer = parseHttpResult(httpReply.resultJson, input.maxBytes)
                if (transfer !is ReaderCoreEventActor.HostReply.AssetTransfer) return transfer
                val identity = try { ReaderMangaTransferIdentity.decode(input.transferId, input.resourceRef) }
                catch (_: IllegalArgumentException) {
                    return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_TRANSFER_IDENTITY", false)
                }
                val dimensions = if (input.inspectGraphics) {
                    try { imageDimensions(transfer.chunks) }
                    catch (error: IllegalArgumentException) {
                        return if (error.message == ReaderMangaBitmapPolicy.BUDGET_ERROR) outputArgumentFailure(error)
                        else ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_IMAGE_DIMENSIONS", false)
                    }
                } else null
                val staged = try { resourceStore.stage(identity, transfer.chunks, transfer.contentType) }
                catch (_: IllegalArgumentException) {
                    return ReaderCoreEventActor.HostReply.Error("RESOURCE_EXHAUSTED", "MANGA_RESOURCE_STAGE", false)
                }
                transfer.copy(width = dimensions?.first, height = dimensions?.second,
                    onCommitted = { resourceStore.commit(staged) })
            }
        }
    }

    /** Bounds only: Core needs dimensions before its graphicsPlan continuation; pixels stay Host-owned. */
    private fun imageDimensions(chunks: List<ByteArray>): Pair<Int, Int> {
        val encoded = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk -> chunk.copyInto(encoded, offset); offset += chunk.size }
        return readImageBounds(encoded).also { ReaderMangaBitmapPolicy.graphicsDimensions(it.first, it.second) }
    }

    private fun inspect(value: JSONObject): ReaderCoreEventActor.HostReply {
        return try {
            exactKeys(value, setOf("stage", "transferId", "sha256", "bytes", "maxBytes"))
            val transferId = nonBlank(value.getString("transferId"), "transferId", 128)
            val bytes = exactLong(value.get("bytes"), "bytes")
            require(bytes in 1..MAX_BYTES) { "bytes must be in 1..16 MiB" }
            val sha256 = nonBlank(value.getString("sha256"), "sha256", 64)
            require(sha256.matches(Regex("[0-9a-f]{64}"))) { "sha256 must be lowercase hex" }
            val resource = resourceStore.readByTransferId(transferId, sha256, bytes)
            ReaderCoreEventActor.HostReply.AssetTransfer(
                resource.encoded.asList().chunked(CHUNK_BYTES).map { it.toByteArray() },
                contentType = resource.contentType,
            )
        } catch (error: IllegalArgumentException) {
            ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", error.message ?: "MANGA_RESOURCE_INSPECT_INVALID", false)
        } catch (error: IllegalStateException) {
            ReaderCoreEventActor.HostReply.Error("INTERNAL", error.message ?: "MANGA_RESOURCE_INSPECT_READ", false)
        } catch (_: java.io.IOException) {
            ReaderCoreEventActor.HostReply.Error("INTERNAL", "MANGA_RESOURCE_INSPECT_READ", false)
        }
    }

    private fun output(value: JSONObject): ReaderCoreEventActor.HostReply {
        return try {
            exactKeys(value, setOf("stage", "graphicsPlan", "transferId", "resourceRef", "assetId", "operationId", "bytes"))
            val transferId = nonBlank(value.getString("transferId"), "transferId", 128)
            val resourceRef = nonBlank(value.getString("resourceRef"), "resourceRef", 512)
            ReaderMangaTransferIdentity.decode(transferId, resourceRef)
            val assetId = exactLong(value.get("assetId"), "assetId")
            val operationId = exactLong(value.get("operationId"), "operationId")
            val bytes = exactLong(value.get("bytes"), "bytes")
            require(assetId > 0 && operationId > 0 && bytes in 1..MAX_BYTES) { "MANGA_OUTPUT_IDENTITY" }
            val graphicsPlan = if (!value.has("graphicsPlan") || value.isNull("graphicsPlan")) null
                else validateGraphicsPlanShape(value.getJSONObject("graphicsPlan"))
            graphicsPlan?.let { ReaderMangaBitmapPolicy.graphics(it.width, it.height, bytes, it.strips.maxOf { strip -> strip.height }) }
            ReaderCoreEventActor.HostReply.AssetConsume(assetId, operationId, bytes) { encoded ->
                // This callback executes after execute() returns. Keep typed budget errors on this boundary too.
                try {
                    require(encoded.size.toLong() == bytes) { "MANGA_OUTPUT_BYTES" }
                    val bitmap = if (graphicsPlan == null) decodeOrdinary(encoded) else decodeGraphicsPlan(encoded, graphicsPlan)
                    try {
                        val preparedBytes = if (graphicsPlan == null) {
                            resourceStore.putDecodedByTransferId(transferId, encoded)
                            bytes
                        } else {
                            val reservation = ReaderMangaBitmapPolicy.graphics(graphicsPlan.width, graphicsPlan.height,
                                bytes, graphicsPlan.strips.maxOf { it.height })
                            val compressed = BoundedBitmapOutput(reservation.maximumOutputBytes)
                            val written = try { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, compressed) }
                                catch (error: RuntimeException) { compressed.requireWithinBudget(); throw error }
                            compressed.requireWithinBudget()
                            check(written) { "MANGA_GRAPHICS_PLAN_COMPRESS" }
                            resourceStore.putDecodedByTransferId(transferId, compressed.toByteArray())
                            compressed.size().toLong()
                        }
                        require(preparedBytes in 1..MAX_PREPARED_BYTES) { "MANGA_IMAGE_PIXEL_BUDGET" }
                        ReaderCoreEventActor.HostReply.Complete(JSONObject()
                            .put("consumed", true).put("assetId", assetId).put("bytes", bytes)
                            .put("preparedBytes", preparedBytes).toString())
                    } finally { bitmap.recycle() }
                } catch (error: IllegalArgumentException) { outputArgumentFailure(error) }
                catch (error: IllegalStateException) {
                    ReaderCoreEventActor.HostReply.Error("INTERNAL", error.message ?: "MANGA_OUTPUT_DECODE", false)
                }
            }
        } catch (error: IllegalArgumentException) { outputArgumentFailure(error) }
        catch (_: org.json.JSONException) {
            ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_OUTPUT_INVALID", false)
        }
        catch (error: IllegalStateException) {
            ReaderCoreEventActor.HostReply.Error("INTERNAL", error.message ?: "MANGA_OUTPUT_DECODE", false)
        }
    }

    private fun outputArgumentFailure(error: IllegalArgumentException) = ReaderCoreEventActor.HostReply.Error(
        if (error.message == ReaderMangaBitmapPolicy.BUDGET_ERROR) "RESOURCE_EXHAUSTED" else "INVALID_PARAMS",
        error.message ?: "MANGA_OUTPUT_INVALID", false,
    )

    private fun decodeOrdinary(encoded: ByteArray): Bitmap {
        val (width, height) = readImageBounds(encoded)
        ReaderMangaBitmapPolicy.ordinary(width, height, encoded.size.toLong())
        val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false })
            ?: error("MANGA_IMAGE_DECODE")
        try {
            require(bitmap.width == width && bitmap.height == height) { "MANGA_IMAGE_DECODE_DIMENSIONS" }
            ReaderMangaBitmapPolicy.ordinaryAllocation(width, height, encoded.size.toLong(), bitmap.allocationByteCount.toLong())
            return bitmap
        } catch (failure: Throwable) { bitmap.recycle(); throw failure }
    }

    private data class GraphicsStrip(val sourceY: Int, val targetY: Int, val height: Int)
    private data class GraphicsPlan(val width: Int, val height: Int, val strips: List<GraphicsStrip>)

    private fun decodeGraphicsPlan(encoded: ByteArray, plan: GraphicsPlan): Bitmap {
        val reservation = ReaderMangaBitmapPolicy.graphics(plan.width, plan.height, encoded.size.toLong(), plan.strips.maxOf { it.height })
        val actual = readImageBounds(encoded)
        require(actual.first == plan.width && actual.second == plan.height) { "MANGA_GRAPHICS_PLAN_DIMENSIONS" }
        // No target exists if opening the native decoder fails. Any later failure releases both owners.
        val decoder = BitmapRegionDecoder.newInstance(encoded, 0, encoded.size, false)
        var target: Bitmap? = null
        try {
            require(decoder.width == plan.width && decoder.height == plan.height) { "MANGA_GRAPHICS_PLAN_DIMENSIONS" }
            val bitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.RGB_565)
            target = bitmap
            ReaderMangaBitmapPolicy.requireAllocation(bitmap.allocationByteCount.toLong(), reservation.targetBytes)
            val canvas = Canvas(bitmap); val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            for (strip in plan.strips) {
                val piece = decoder.decodeRegion(Rect(0, strip.sourceY, plan.width, strip.sourceY + strip.height),
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
                    ?: error("MANGA_GRAPHICS_PLAN_DECODE")
                try {
                    require(piece.width == plan.width && piece.height == strip.height) { "MANGA_GRAPHICS_PLAN_PIECE" }
                    ReaderMangaBitmapPolicy.requireAllocation(piece.allocationByteCount.toLong(), reservation.stripBytes)
                    canvas.drawBitmap(piece, 0f, strip.targetY.toFloat(), paint)
                } finally { piece.recycle() }
            }
            return bitmap
        } catch (failure: Throwable) {
            target?.recycle()
            throw failure
        } finally { decoder.recycle() }
    }

    private fun validateGraphicsPlanShape(plan: JSONObject): GraphicsPlan {
        exactKeys(plan, setOf("__readerMangaGraphicsPlan", "toJSON", "width", "height", "format", "quality", "pixelFormat", "strips"))
        require(plan.get("__readerMangaGraphicsPlan") == true && plan.isNull("toJSON")) { "MANGA_GRAPHICS_PLAN_MARKER" }
        val width = boundedInteger(plan.get("width"), "width")
        val height = boundedInteger(plan.get("height"), "height")
        ReaderMangaBitmapPolicy.graphicsDimensions(width, height)
        require(plan.getString("format") == "jpeg" && exactLong(plan.get("quality"), "quality") == 90L &&
            plan.getString("pixelFormat") == "rgb565") { "MANGA_GRAPHICS_PLAN_FORMAT" }
        val raw = plan.getJSONArray("strips")
        require(raw.length() in 1..64) { "MANGA_GRAPHICS_PLAN_STRIPS" }
        val strips = (0 until raw.length()).map { index ->
            val strip = raw.getJSONObject(index)
            exactKeys(strip, setOf("sourceY", "targetY", "height"))
            val sourceY = boundedInteger(strip.get("sourceY"), "sourceY")
            val targetY = boundedInteger(strip.get("targetY"), "targetY")
            val stripHeight = boundedInteger(strip.get("height"), "height")
            require(sourceY >= 0 && targetY >= 0 && stripHeight > 0 &&
                sourceY.toLong() + stripHeight <= height.toLong() && targetY.toLong() + stripHeight <= height.toLong()) {
                "MANGA_GRAPHICS_PLAN_BOUNDS"
            }
            GraphicsStrip(sourceY, targetY, stripHeight)
        }
        // At most 64 strips: validate exact coverage without arrays sized by an untrusted image height.
        fun contiguous(ordered: List<GraphicsStrip>, start: (GraphicsStrip) -> Int): Boolean {
            var end = 0L
            for (strip in ordered) {
                if (start(strip).toLong() != end) return false
                end += strip.height.toLong()
            }
            return end == height.toLong()
        }
        require(contiguous(strips.sortedBy { it.sourceY }) { it.sourceY } &&
            contiguous(strips.sortedBy { it.targetY }) { it.targetY }) { "MANGA_GRAPHICS_PLAN_GAP" }
        return GraphicsPlan(width, height, strips)
    }

    private fun boundedInteger(value: Any, field: String): Int = exactLong(value, field).also {
        require(it in 0..Int.MAX_VALUE.toLong()) { "$field is outside the integer range" }
    }.toInt()

    private class BoundedBitmapOutput(private val maximum: Int) : ByteArrayOutputStream() {
        private var exceeded = false
        fun requireWithinBudget() { require(!exceeded) { ReaderMangaBitmapPolicy.BUDGET_ERROR } }
        private fun admit(length: Int) {
            try { ReaderMangaBitmapPolicy.requireOutputSize(size(), length, maximum) }
            catch (error: IllegalArgumentException) { exceeded = true; throw error }
        }
        override fun write(value: Int) {
            admit(1)
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            admit(length)
            super.write(bytes, offset, length)
        }
    }

    internal data class ParsedHttpResult(val status: Int, val contentType: String, val bytes: ByteArray)

    internal fun parseInput(raw: String): ParsedInput {
        return parseInput(JSONObject(raw))
    }

    private fun parseInput(value: JSONObject): ParsedInput {
        exactKeys(value, setOf("stage", "transferId", "resourceRef", "request", "maxBytes", "inspectGraphics"))
        val stage = value.getString("stage")
        val transferId = nonBlank(value.getString("transferId"), "transferId", 128)
        val resourceRef = nonBlank(value.getString("resourceRef"), "resourceRef", 512)
        val request = value.getJSONObject("request")
        val maxBytes = exactLong(value.get("maxBytes"), "maxBytes")
        require(maxBytes in 1..MAX_BYTES) { "maxBytes must be in 1..16 MiB" }
        if (value.has("inspectGraphics") && value.get("inspectGraphics") !is Boolean) {
            throw IllegalArgumentException("inspectGraphics must be boolean")
        }
        return ParsedInput(stage, transferId, resourceRef, request, maxBytes,
            inspectGraphics = value.optBoolean("inspectGraphics", false))
    }

    internal fun parseHttpResult(raw: String, maxBytes: Long): ReaderCoreEventActor.HostReply {
        val value = try { JSONObject(raw) } catch (_: org.json.JSONException) {
            return ReaderCoreEventActor.HostReply.Error("INTERNAL", "MANGA_HTTP_RESULT_INVALID", false)
        }
        val status = value.opt("status")
        if (status !is Number || status.toLong() !in 200..299) {
            val code = status?.toString() ?: "missing"
            val retryable = status is Number && (status.toInt() == 408 || status.toInt() == 429 || status.toInt() >= 500)
            return ReaderCoreEventActor.HostReply.Error("INTERNAL", "MANGA_HTTP_STATUS:$code", retryable)
        }
        val headers = value.optJSONObject("headers")
        val headerContentType = headers?.keys()?.asSequence()?.firstOrNull { it.equals("Content-Type", true) }
            ?.let { headers.optString(it, "") }
        val contentType = value.optString("contentType", headerContentType ?: "")
            .substringBefore(';').trim().lowercase()
        if (!contentType.startsWith("image/")) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_IMAGE_CONTENT_TYPE_REQUIRED", false)
        }
        val encoded = value.opt("bodyBase64")
        if (encoded !is String || encoded.isEmpty()) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_IMAGE_BODY_MISSING", false)
        }
        val bytes = try { encoded.decodeBase64()?.toByteArray() } catch (_: IllegalArgumentException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_IMAGE_BASE64_INVALID", false)
        } ?: return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_IMAGE_BASE64_INVALID", false)
        if (bytes.isEmpty() || bytes.size.toLong() > maxBytes) {
            return ReaderCoreEventActor.HostReply.Error("RESOURCE_EXHAUSTED", "MANGA_IMAGE_MAX_BYTES", false)
        }
        val chunks = bytes.asList().chunked(CHUNK_BYTES).map { it.toByteArray() }
        return ReaderCoreEventActor.HostReply.AssetTransfer(chunks, contentType = contentType)
    }

    internal data class ParsedInput(
        val stage: String,
        val transferId: String,
        val resourceRef: String,
        val request: JSONObject,
        val maxBytes: Long,
        val inspectGraphics: Boolean,
    )

    private fun exactKeys(value: JSONObject, allowed: Set<String>) {
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key in allowed) { "unknown manga resource field: $key" }
        }
    }

    private fun nonBlank(value: String, field: String, maxLength: Int): String {
        require(value.isNotBlank() && value.length <= maxLength) { "$field is blank or oversized" }
        return value
    }

    private fun exactLong(value: Any, field: String): Long = when (value) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        else -> throw IllegalArgumentException("$field must be an integer")
    }

    companion object {
        private fun platformImageBounds(encoded: ByteArray): Pair<Int, Int> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "MANGA_IMAGE_DIMENSIONS" }
            return bounds.outWidth to bounds.outHeight
        }
        internal const val CAPABILITY = "manga.resource.transfer"
        internal const val STAGE_INPUT = "input"
        internal const val STAGE_INSPECT = "inspectInput"
        internal const val STAGE_OUTPUT = "output"
        internal const val CHUNK_BYTES = 1024 * 1024
        internal const val MAX_BYTES = 16L * 1024 * 1024
        internal const val MAX_PREPARED_BYTES = 16L * 1024 * 1024
    }
}
