package io.reader.android.runtime.http

import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset

/** Explicit Host budgets, not Core wire limits or a total-process memory claim. */
internal data class ReaderHttpLimits(
    val maximumTimeoutMs: Long = 300_000,
    val defaultTimeoutMs: Long = 30_000,
    val maximumRequestBytes: Long = 8L * 1024 * 1024,
    val maximumResponseBytes: Long = 32L * 1024 * 1024,
    val maximumHeaderBytes: Long = 64L * 1024,
    val maximumRedirects: Int = 20,
    val maximumAttempts: Int = 3,
    val maximumConcurrent: Int = 2,
    val maximumOwnedOperations: Int = 8,
    val maximumCookieSessions: Int = 64,
    val maximumCookiesPerSession: Int = 256,
    val maximumCookieBytesPerSession: Long = 64L * 1024,
) {
    init {
        require(defaultTimeoutMs in 1..maximumTimeoutMs && maximumTimeoutMs <= 300_000)
        require(maximumRequestBytes in 1..Int.MAX_VALUE.toLong() && maximumResponseBytes in 1..Int.MAX_VALUE.toLong())
        require(maximumHeaderBytes > 0 && maximumRedirects in 0..20 && maximumAttempts in 1..3)
        require(maximumConcurrent in 1..4 && maximumOwnedOperations in maximumConcurrent..16 && maximumCookieSessions in 1..128)
        require(maximumCookiesPerSession in 1..512 && maximumCookieBytesPerSession in 1..1024L * 1024)
    }
}

internal class ReaderHttpFailure(val code: String, message: String, val retryable: Boolean = false) : IOException(message)

internal data class ReaderHttpDescriptor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
    val charset: Charset,
    val timeoutMs: Long,
    val followRedirects: Boolean,
    val maximumRedirects: Int,
    val maximumAttempts: Int,
    val backoffMillis: Long,
    val useCookieJar: Boolean,
    val sessionId: String?,
) {
    fun request(): Request = Request.Builder().url(url).headers(headers).method(method, body).build()

    companion object {
        fun decode(raw: String, limits: ReaderHttpLimits): ReaderHttpDescriptor {
            val value = JSONObject(raw)
            exactKeys(value, setOf("timeoutMs", "url", "method", "headers", "body", "charset", "followRedirects",
                "maxRedirects", "retry", "usePlatformCookieJar", "session", "responseAsset", "diagnostic"))
            val url = string(value.get("url")).toHttpUrlOrNull() ?: invalid("HTTP_URL_REQUIRED")
            val method = if (value.has("method")) string(value.get("method")) else "GET"
            val timeout = number(value, "timeoutMs") ?: limits.defaultTimeoutMs
            if (timeout !in 1..0xffff_ffffL) invalid("HTTP_TIMEOUT_INVALID")
            if (timeout > limits.maximumTimeoutMs) exhausted("HTTP_TIMEOUT_BUDGET")
            val charset = Charset.forName(optionalString(value, "charset") ?: "UTF-8")
            val headers = Headers.Builder()
            val rawHeaders = if (value.has("headers")) value.getJSONObject("headers") else JSONObject()
            var headerBytes = 0L
            for (name in rawHeaders.keys()) {
                val values = when (val field = rawHeaders.get(name)) {
                    is String -> listOf(field)
                    is JSONArray -> (0 until field.length()).map { string(field.get(it), empty = true) }
                    else -> invalid("HTTP_HEADER_VALUE_INVALID")
                }
                for (field in values) {
                    headerBytes += name.toByteArray(Charsets.UTF_8).size + field.toByteArray(Charsets.UTF_8).size + 4
                    if (headerBytes > limits.maximumHeaderBytes) exhausted("HTTP_REQUEST_HEADER_BUDGET")
                    headers.add(name, field)
                }
            }
            // Core's canonical GET descriptors carry an explicit empty body string.
            // Preserve empty bodies for methods that require one, but do not hand
            // OkHttp a request body for GET/HEAD-style methods.
            val rawBody = value.opt("body")
            val body = body(rawBody, charset, headers, limits).takeUnless {
                it != null && rawBody is String && rawBody.isEmpty() &&
                    method in setOf("GET", "HEAD", "DELETE", "OPTIONS", "TRACE")
            }
            val actualBody = body ?: if (method in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT", "QUERY"))
                ByteArray(0).toRequestBody() else null
            if (actualBody != null && actualBody.contentLength() > limits.maximumRequestBytes) exhausted("HTTP_REQUEST_BODY_BUDGET")
            val redirects = number(value, "maxRedirects") ?: 10L
            if (redirects !in 0..0xffff_ffffL) invalid("HTTP_REDIRECT_LIMIT_INVALID")
            if (redirects > limits.maximumRedirects) exhausted("HTTP_REDIRECT_BUDGET")
            val retry = optionalObject(value, "retry")
            retry?.let { exactKeys(it, setOf("maxAttempts", "backoffMillis")) }
            val attempts = retry?.let { integer(it.get("maxAttempts")) } ?: 1L
            if (attempts <= 0) invalid("HTTP_ATTEMPTS_INVALID")
            if (attempts > limits.maximumAttempts) exhausted("HTTP_ATTEMPT_BUDGET")
            val backoff = retry?.let { number(it, "backoffMillis") } ?: 150L
            if (backoff !in 0..2_000) exhausted("HTTP_BACKOFF_BUDGET")
            val session = optionalObject(value, "session")?.let {
                exactKeys(it, setOf("id")); string(it.get("id"))
            }
            optionalObject(value, "diagnostic")?.let {
                exactKeys(it, setOf("traceId", "requestId", "sourceId", "stage"))
                string(it.get("traceId")); string(it.get("sourceId"))
                if (integer(it.get("requestId")) <= 0 || string(it.get("stage")) !in setOf("L2", "L3", "L4", "L5"))
                    invalid("HTTP_DIAGNOSTIC_INVALID")
            }
            if (boolean(value, "responseAsset", false)) invalid("HTTP_RESPONSE_ASSET_NOT_NEGOTIATED")
            return ReaderHttpDescriptor(url, method, headers.build(), actualBody, charset, timeout,
                boolean(value, "followRedirects", true), redirects.toInt(), attempts.toInt(), backoff,
                boolean(value, "usePlatformCookieJar", false), session).also { it.request() }
        }

        private fun body(value: Any?, charset: Charset, headers: Headers.Builder, limits: ReaderHttpLimits): RequestBody? = when (value) {
            null, JSONObject.NULL -> null
            is String -> {
                val bytes = value.toByteArray(charset)
                if (bytes.size > limits.maximumRequestBytes) exhausted("HTTP_REQUEST_BODY_BUDGET")
                bytes.toRequestBody(headers.get("Content-Type")?.toMediaTypeOrNull())
            }
            is JSONObject -> {
                exactKeys(value, setOf("fields", "files"))
                if (!value.has("fields") && !value.has("files")) invalid("HTTP_BODY_SHAPE_INVALID")
                val fields = if (value.has("fields")) value.getJSONArray("fields") else JSONArray()
                var fieldBytes = 0L
                val pairs = (0 until fields.length()).map { i ->
                    val pair = fields.getJSONArray(i)
                    if (pair.length() != 2) invalid("HTTP_FORM_PAIR_INVALID")
                    val name = string(pair.get(0), empty = true); val content = string(pair.get(1), empty = true)
                    fieldBytes += name.toByteArray(charset).size + content.toByteArray(charset).size
                    if (fieldBytes > limits.maximumRequestBytes) exhausted("HTTP_REQUEST_BODY_BUDGET")
                    name to content
                }
                val result = if (!value.has("files")) FormBody.Builder(charset).apply {
                    pairs.forEach { (name, content) -> add(name, content) }
                }.build() else MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                    pairs.forEach { (name, content) -> addFormDataPart(name, content) }
                    val files = value.getJSONArray("files")
                    var bytesCount = fieldBytes
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        exactKeys(file, setOf("fieldName", "filename", "contentType", "data", "filePath"))
                        if (!file.isNull("filePath")) invalid("HTTP_UPLOAD_FILE_AUTHORITY_REQUIRED")
                        val data = if (file.has("data")) file.getJSONArray("data") else JSONArray()
                        bytesCount += data.length()
                        if (bytesCount > limits.maximumRequestBytes) exhausted("HTTP_REQUEST_BODY_BUDGET")
                        val bytes = ByteArray(data.length()) { index ->
                            val item = integer(data.get(index))
                            if (item !in 0..255) invalid("HTTP_UPLOAD_BYTE_INVALID")
                            item.toByte()
                        }
                        val type = string(file.get("contentType")).toMediaTypeOrNull() ?: invalid("HTTP_UPLOAD_MEDIA_TYPE_INVALID")
                        addFormDataPart(string(file.get("fieldName"), empty = true), string(file.get("filename"), empty = true), bytes.toRequestBody(type))
                    }
                }.build()
                headers.set("Content-Type", result.contentType().toString())
                result
            }
            else -> invalid("HTTP_BODY_SHAPE_INVALID")
        }

        private fun exactKeys(value: JSONObject, allowed: Set<String>) {
            if (value.keys().asSequence().any { it !in allowed }) invalid("HTTP_UNKNOWN_FIELD")
        }
        private fun string(value: Any, empty: Boolean = false): String =
            (value as? String ?: invalid("HTTP_STRING_REQUIRED")).also { if (!empty && it.isBlank()) invalid("HTTP_NONBLANK_STRING_REQUIRED") }
        private fun integer(value: Any): Long = when (value) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> invalid("HTTP_EXACT_INTEGER_REQUIRED")
        }
        private fun number(value: JSONObject, key: String): Long? = if (value.isNull(key)) null else integer(value.get(key))
        private fun optionalString(value: JSONObject, key: String): String? = if (value.isNull(key)) null else string(value.get(key))
        private fun optionalObject(value: JSONObject, key: String): JSONObject? = if (value.isNull(key)) null else value.getJSONObject(key)
        private fun boolean(value: JSONObject, key: String, fallback: Boolean): Boolean =
            if (value.isNull(key)) fallback else value.get(key) as? Boolean ?: invalid("HTTP_BOOLEAN_REQUIRED")
        private fun invalid(message: String): Nothing = throw ReaderHttpFailure("INVALID_PARAMS", message)
        private fun exhausted(message: String): Nothing = throw ReaderHttpFailure("RESOURCE_EXHAUSTED", message)
    }
}
