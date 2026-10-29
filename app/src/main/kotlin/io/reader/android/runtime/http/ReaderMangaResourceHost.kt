package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64

/**
 * Minimal manga input boundary. It downloads encoded bytes through the existing
 * HTTP owner and hands immutable chunks to the event actor; decode, metadata,
 * cache and UI remain separate capabilities.
 */
internal class ReaderMangaResourceHost(private val httpHost: ReaderHttpHost) {
    suspend fun execute(envelope: ReaderCoreEventActor.HostEnvelope): ReaderCoreEventActor.HostReply {
        if (envelope.capability != CAPABILITY) return ReaderCoreEventActor.HostReply.Error(
            "UNKNOWN_METHOD", "Android manga resource transfer is not available for this capability", false,
        )
        val input = try {
            parseInput(envelope.parametersJson)
        } catch (error: IllegalArgumentException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", error.message ?: "MANGA_RESOURCE_INPUT_INVALID", false)
        } catch (error: org.json.JSONException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "MANGA_RESOURCE_INPUT_INVALID", false)
        }
        if (input.stage != STAGE_INPUT) return ReaderCoreEventActor.HostReply.Error(
            "UNKNOWN_METHOD", "manga resource output/decode is not implemented in this slice", false,
        )
        val httpReply = httpHost.execute(envelope.copy(
            capability = "http.execute",
            parametersJson = input.request.toString(),
        ))
        return when (httpReply) {
            is ReaderCoreEventActor.HostReply.Error -> httpReply
            is ReaderCoreEventActor.HostReply.AssetTransfer -> ReaderCoreEventActor.HostReply.Error(
                "INTERNAL", "MANGA_HTTP_NESTED_ASSET_UNEXPECTED", false,
            )
            is ReaderCoreEventActor.HostReply.Complete -> parseHttpResult(httpReply.resultJson, input.maxBytes)
        }
    }

    internal data class ParsedHttpResult(val status: Int, val contentType: String, val bytes: ByteArray)

    internal fun parseInput(raw: String): ParsedInput {
        val value = JSONObject(raw)
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
        return ParsedInput(stage, transferId, resourceRef, request, maxBytes)
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
        internal const val CAPABILITY = "manga.resource.transfer"
        internal const val STAGE_INPUT = "input"
        internal const val CHUNK_BYTES = 1024 * 1024
        internal const val MAX_BYTES = 16L * 1024 * 1024
    }
}
