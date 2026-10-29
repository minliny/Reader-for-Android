package io.reader.android.runtime.http

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ByteString.Companion.toByteString
import okio.GzipSource
import okio.InflaterSource
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.zip.Inflater

/** Captures the only wire response inside the same Call's retry/deadline scope. */
internal object ReaderHttpResponse {
    fun checkHeaders(headers: Headers, limits: ReaderHttpLimits, failure: String = "HTTP_RESPONSE_HEADER_BUDGET") {
        var count = 0L
        for (i in 0 until headers.size) {
            count += headers.name(i).toByteArray(Charsets.UTF_8).size + headers.value(i).toByteArray(Charsets.UTF_8).size + 4
            if (count > limits.maximumHeaderBytes) throw ReaderHttpFailure("RESOURCE_EXHAUSTED", failure)
        }
    }

    fun headersJson(headers: Headers): JSONObject = JSONObject().apply {
        for ((name, values) in headers.toMultimap()) put(name, if (values.size == 1) values.single() else JSONArray(values))
    }

    fun readBounded(source: BufferedSource, work: ReaderHttpWork): ByteArray {
        val buffer = Buffer()
        var count = 0L
        while (true) {
            work.requireActive()
            val read = source.read(buffer, minOf(8_192L, work.limits.maximumResponseBytes - count + 1))
            if (read == -1L) break
            count += read
            if (count > work.limits.maximumResponseBytes) throw ReaderHttpFailure("RESOURCE_EXHAUSTED", "HTTP_RESPONSE_BODY_BUDGET")
        }
        return buffer.readByteArray()
    }

    fun capture(received: ReaderHttpReceivedResponse, redirects: JSONArray, work: ReaderHttpWork) {
        val response = received.response
        var bytes = received.encodedBytes
        val encodings = response.headers.values("Content-Encoding").flatMap { it.split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() && it != "identity" }
        if (encodings.size > 4) throw ReaderHttpFailure("RESOURCE_EXHAUSTED", "HTTP_CONTENT_ENCODING_BUDGET")
        // Only the captured memory is decoded. Okio/platform owns the actual codecs.
        if (bytes.isNotEmpty()) for (encoding in encodings.asReversed()) {
            val input = Buffer().write(bytes)
            val decoded = when (encoding) {
                "gzip", "x-gzip" -> GzipSource(input).buffer()
                "deflate" -> InflaterSource(input, Inflater()).buffer()
                else -> throw ReaderHttpFailure("INVALID_PARAMS", "HTTP_CONTENT_ENCODING_NOT_SUPPORTED")
            }
            bytes = decoded.use { readBounded(it, work) }
        }
        work.requireActive()
        val mediaType = response.header("Content-Type")?.toMediaTypeOrNull()
        val binary = mediaType?.let {
            it.type in setOf("image", "audio", "video", "font") || it.toString().substringBefore(';').lowercase(Locale.ROOT) in
                setOf("application/octet-stream", "application/zip", "application/pdf")
        } ?: false
        val hint = mediaType?.charset() ?: work.descriptor.charset
        // Use OkHttp's mature BOM-aware decoder. The hint is the declared
        // header/fallback; it is not a claim to have separately detected a BOM.
        val decodingType = mediaType?.takeIf { it.charset() != null } ?: "text/plain; charset=${hint.name()}".toMediaType()
        val result = JSONObject().put("status", response.code).put("headers", headersJson(response.headers))
            .put("finalUrl", response.request.url.toString()).put("redirects", redirects)
            .put("charsetHint", hint.name()).put("body", if (binary) "" else bytes.toResponseBody(decodingType).use { it.string() })
        if (binary && bytes.isNotEmpty()) result.put("bodyBase64", bytes.toByteString().base64())
        work.descriptor.sessionId?.let { result.put("session", JSONObject().put("id", it)) }
        work.publish(result.toString())
    }
}
