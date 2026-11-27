package io.reader.rebuild.host.network

import com.reader.core.protocol.M2HostErrorCode
import com.reader.core.protocol.M2HostErrorDiagnostics
import com.reader.core.protocol.M2HostErrorParams
import com.reader.core.protocol.M2HostErrorPhase
import com.reader.core.protocol.ReaderCoreError
import com.reader.core.protocol.ReaderErrorCode
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class NetworkHostFailure(
    val code: M2HostErrorCode,
    val phase: M2HostErrorPhase,
    override val message: String,
    val retryable: Boolean = false,
    val status: Int? = null,
    val category: String? = null,
) : IOException(message) {
    fun reply(operationId: Long): M2HostErrorParams {
        val errorCode = when (code) {
            M2HostErrorCode.TIMEOUT -> ReaderErrorCode.TIMEOUT
            M2HostErrorCode.CANCELLED -> ReaderErrorCode.CANCELLED
            else -> ReaderErrorCode.INTERNAL
        }
        val details = buildJsonObject {
            status?.let { put("status", JsonPrimitive(it)) }
            category?.let { put("category", JsonPrimitive(it)) }
        }
        return M2HostErrorParams(operationId, ReaderCoreError(errorCode, message, retryable),
            M2HostErrorDiagnostics(code, phase, message, details.takeUnless { it.isEmpty() }))
    }

    companion object {
        fun classified(error: Exception): NetworkHostFailure = when (error) {
            is NetworkHostFailure -> error
            is TimeoutCancellationException, is InterruptedIOException ->
                NetworkHostFailure(M2HostErrorCode.TIMEOUT, M2HostErrorPhase.TRANSPORT, "Network operation timed out", true)
            is CancellationException -> NetworkHostFailure(M2HostErrorCode.CANCELLED, M2HostErrorPhase.RUNTIME, "Operation cancelled")
            is SSLException -> NetworkHostFailure(M2HostErrorCode.TLS_ERROR, M2HostErrorPhase.TRANSPORT, "TLS validation failed")
            is SecurityException -> denied("System refused the operation")
            is UnknownHostException, is ConnectException ->
                NetworkHostFailure(M2HostErrorCode.NETWORK_ERROR, M2HostErrorPhase.TRANSPORT,
                    "Network connection unavailable", true, category = "NETWORK_ENVIRONMENT")
            is IOException ->
                NetworkHostFailure(M2HostErrorCode.NETWORK_ERROR, M2HostErrorPhase.TRANSPORT, "Network operation failed", true)
            is IllegalArgumentException -> invalid("Invalid network request", M2HostErrorPhase.REQUEST)
            else -> NetworkHostFailure(M2HostErrorCode.INTERNAL, M2HostErrorPhase.RUNTIME, "Host operation failed")
        }

        fun http(status: Int) = NetworkHostFailure(M2HostErrorCode.HTTP_ERROR, M2HostErrorPhase.RESPONSE,
            "status=$status", status == 408 || status == 429 || status >= 500, status, "SOURCE_HTTP_FAILED")

        fun invalid(message: String, phase: M2HostErrorPhase = M2HostErrorPhase.RESPONSE) =
            NetworkHostFailure(M2HostErrorCode.INVALID_RESPONSE, phase, message)

        fun denied(message: String) =
            NetworkHostFailure(M2HostErrorCode.PERMISSION_DENIED, M2HostErrorPhase.REQUEST, message)
    }
}

internal fun httpUrl(value: String): HttpUrl =
    value.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }
        ?: throw NetworkHostFailure.invalid("Invalid HTTP URL", M2HostErrorPhase.REQUEST)

internal fun HttpUrl.sameOrigin(other: HttpUrl): Boolean = scheme == other.scheme && host == other.host && port == other.port
