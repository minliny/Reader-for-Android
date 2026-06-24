package com.reader.android.data.network

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Core `HTTPClient.send`-aligned host adapter. Wraps [OkHttpClient] and maps
 * Reader-Core's `HTTPRequest` → OkHttp `Request`, OkHttp `Response` →
 * [CoreHttpResponse]. Failures are wrapped in [NetworkException] carrying a
 * [ReaderErrorSnapshot] so the bridge layer can convert to
 * [com.reader.android.data.bridge.ReaderException] without `data.network`
 * depending on `data.bridge` (which would create a package cycle, since
 * `data.bridge.RealCoreBridge` already imports `data.network`).
 *
 * NOTE: the response type is named [CoreHttpResponse] (not `HTTPResponse`) to
 * avoid a case-only collision with the existing [HttpResponse] on
 * case-insensitive filesystems (macOS APFS), where `HTTPResponse.class` and
 * `HttpResponse.class` resolve to the same file.
 *
 * This is the thin host adapter sketched in `docs/design/HTTP_ADAPTER_DESIGN.md`.
 * It is gated behind [com.reader.android.AppProvider.isNetworkAllowed] at the
 * bridge layer — this class performs no authorization on its own.
 */
class OkHttpAdapter(
    private val client: OkHttpClient = OkHttpTransport.defaultClient()
) {

    suspend fun send(request: CoreHttpRequest): CoreHttpResponse {
        val okRequest = request.toOkHttpRequest()
        return try {
            val response = client.newCall(okRequest).execute()
            response.toCoreHttpResponse()
        } catch (e: IOException) {
            throw NetworkException(NetworkErrorMapper.map(e, request.sourceName), e)
        }
    }
}

/** Core-aligned request model (mirrors Reader-Core `HTTPRequest`). */
data class CoreHttpRequest(
    val url: String,
    val method: CoreHttpMethod = CoreHttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: PostRequestBody? = null,
    val sourceName: String? = null,
    val failureStage: String = "CONTENT"
)

enum class CoreHttpMethod { GET, POST }

/** Core-aligned response model (mirrors Reader-Core `HTTPResponse`). */
data class CoreHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val requestUrl: String
)

/** Snapshot of a mapped network error, decoupled from the bridge error type. */
data class ReaderErrorSnapshot(
    val codeName: String,
    val stageName: String,
    val message: String?,
    val sourceName: String?
)

/** Exception thrown by [OkHttpAdapter]; carries a [ReaderErrorSnapshot]. */
class NetworkException(val error: ReaderErrorSnapshot, cause: Throwable) : IOException(cause)

private fun CoreHttpRequest.toOkHttpRequest(): Request {
    val builder = Request.Builder().url(url)
    headers.forEach { (key, value) -> builder.addHeader(key, value) }
    when (method) {
        CoreHttpMethod.GET -> builder.get()
        CoreHttpMethod.POST -> builder.post(body?.toOkHttpRequestBody() ?: emptyRequestBody())
    }
    return builder.build()
}

private fun emptyRequestBody(): RequestBody = "".toRequestBody("text/plain".toMediaTypeOrNull())

private fun okhttp3.Response.toCoreHttpResponse(): CoreHttpResponse = CoreHttpResponse(
    statusCode = code,
    headers = headers.toMultimap(),
    body = body?.string() ?: "",
    requestUrl = request.url.toString()
)

/**
 * Maps OkHttp/Java network failures into a [ReaderErrorSnapshot]. Mirrors the
 * Swift mapping in `docs/design/HTTP_ADAPTER_DESIGN.md` Section 7. The bridge
 * layer converts the snapshot into its [com.reader.android.data.bridge.ReaderError].
 */
object NetworkErrorMapper {

    fun map(e: Throwable, sourceName: String?): ReaderErrorSnapshot {
        val codeName = when (e) {
            is SocketTimeoutException -> "TIMEOUT"
            is UnknownHostException -> "NETWORK"
            is SSLException -> "NETWORK"
            is IOException -> "NETWORK"
            else -> "UNKNOWN"
        }
        return ReaderErrorSnapshot(
            codeName = codeName,
            stageName = "CONTENT",
            message = e.message,
            sourceName = sourceName
        )
    }
}
