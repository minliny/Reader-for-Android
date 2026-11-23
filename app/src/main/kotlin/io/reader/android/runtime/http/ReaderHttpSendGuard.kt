package io.reader.android.runtime.http

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Private object identity, not a protocol ID. Each explicit Core attempt/hop gets a new object. */
internal class ReaderHttpHopPermit(val work: ReaderHttpWork) {
    private val entered = AtomicBoolean(false)
    @Volatile var received: ReaderHttpReceivedResponse? = null
        private set
    fun enter() {
        if (!entered.compareAndSet(false, true)) throw ReaderHttpImplicitFollowupBlocked(this)
        work.requireActive()
    }
    fun capture(response: ReaderHttpReceivedResponse) {
        check(received == null); work.whileCurrent { received = response }
    }
}

internal class ReaderHttpImplicitFollowupBlocked(val permit: ReaderHttpHopPermit) : IOException("HTTP_IMPLICIT_FOLLOWUP_BLOCKED")

/** Runs after connection establishment but before CallServer sends the origin request.
 * It does not claim to prevent DNS/TCP/TLS/proxy CONNECT before this public hook. */
internal class ReaderHttpSendGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val permit = checkNotNull(request.tag(ReaderHttpHopPermit::class.java))
        check(request.tag(ReaderHttpWork::class.java) === permit.work)
        permit.enter() // A library 503/421 same-request follow-up cannot call proceed twice.
        return chain.proceed(request).use { response ->
            ReaderHttpResponse.checkHeaders(response.headers, permit.work.limits)
            val bytes = ReaderHttpResponse.readBounded(response.body.source(), permit.work)
            val received = ReaderHttpReceivedResponse(
                response.newBuilder().body(ByteArray(0).toResponseBody(response.body.contentType())).build(), bytes)
            permit.capture(received)
            // OkHttp may inspect/close this in-memory body. The live body was read exactly once.
            response.newBuilder().body(bytes.toResponseBody(response.body.contentType())).build()
        }
    }
}

internal data class ReaderHttpReceivedResponse(val response: Response, val encodedBytes: ByteArray)
