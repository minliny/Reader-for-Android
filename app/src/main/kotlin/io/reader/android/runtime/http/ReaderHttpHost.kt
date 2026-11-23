package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.json.JSONException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** No Native access or alternate Host event loop. The existing actor owns the reply. */
internal class ReaderHttpHost(
    private val limits: ReaderHttpLimits = ReaderHttpLimits(),
    private val transport: OkHttpClient? = null,
) {
    internal data class Identity(val runtime: String, val requestId: Long, val token: String, val operationId: Long)
    internal data class CloseSnapshot(val closed: Boolean, val admitted: Set<Identity>, val executing: Set<Identity>, val cookieSessions: Int)
    private data class CookieKey(val runtime: String, val session: String)
    private val state = Any()
    private var closed = false
    private val admitted = linkedSetOf<Identity>()
    private val executing = linkedMapOf<Identity, ReaderHttpWork>()
    private val cookies = linkedMapOf<CookieKey, ReaderHttpCookieSession>()
    private val permits = Semaphore(limits.maximumConcurrent)
    private val client by lazy {
        // Internal trusted transport settings support TLS/DNS/observation; callers cannot replace policy.
        (transport?.newBuilder() ?: OkHttpClient.Builder()).apply { interceptors().clear(); networkInterceptors().clear() }
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).cache(null)
            .addInterceptor(ReaderHttpPolicyInterceptor()).addNetworkInterceptor(ReaderHttpSendGuard()).build()
    }

    suspend fun execute(envelope: ReaderCoreEventActor.HostEnvelope): ReaderCoreEventActor.HostReply {
        if (envelope.capability != "http.execute") return ReaderCoreEventActor.HostReply.Error(
            "UNKNOWN_METHOD", "Android capability is not available in this product slice", false)
        val identity = Identity(envelope.runtimeSession, envelope.requestId, envelope.requestToken, envelope.operationId)
        var reserved = false
        try {
            synchronized(state) {
                if (closed) throw ReaderHttpFailure("CANCELLED", "HTTP_OWNER_CLOSED")
                check(identity !in admitted) { "DUPLICATE_RUNNING_HTTP_OPERATION" }
                if (admitted.size >= limits.maximumOwnedOperations) throw ReaderHttpFailure("RESOURCE_EXHAUSTED", "HTTP_OWNED_OPERATION_BUDGET")
                admitted.add(identity); reserved = true
            }
            val descriptor = ReaderHttpDescriptor.decode(envelope.parametersJson, limits)
            val work = synchronized(state) {
                if (closed) throw ReaderHttpFailure("CANCELLED", "HTTP_OWNER_CLOSED")
                check(!executing.containsKey(identity)) { "DUPLICATE_RUNNING_HTTP_OPERATION" }
                val jar = if (!descriptor.useCookieJar) null else {
                    if (descriptor.sessionId == null) ReaderHttpCookieSession(limits) else {
                        val key = CookieKey(identity.runtime, descriptor.sessionId)
                        cookies[key] ?: run {
                            if (cookies.size >= limits.maximumCookieSessions) throw ReaderHttpFailure("RESOURCE_EXHAUSTED", "HTTP_COOKIE_SESSION_BUDGET")
                            ReaderHttpCookieSession(limits).also { cookies[key] = it }
                        }
                    }
                }
                ReaderHttpWork(descriptor, limits, jar).also { executing[identity] = it }
            }
            try {
                // Includes waiting for a transport permit, every hop, retry, and body capture.
                return withTimeoutOrNull(descriptor.timeoutMs) {
                    permits.withPermit {
                        work.requireActive()
                        val request = descriptor.request().newBuilder().tag(ReaderHttpWork::class.java, work).build()
                        val call = client.newCall(request)
                        call.timeout().timeout(work.remainingMillis().coerceAtLeast(1), TimeUnit.MILLISECONDS)
                        work.install(call)
                        awaitOwnedHttpExecution(cancel = work::cancel) {
                            call.execute().use {
                                work.requireActive()
                                ReaderCoreEventActor.HostReply.Complete(checkNotNull(work.fixedResult) { "HTTP_RESULT_NOT_CAPTURED" })
                            }
                        }
                    }
                } ?: ReaderCoreEventActor.HostReply.Error("TIMEOUT", "HTTP_OPERATION_TIMEOUT", true)
            } finally {
                // awaitOwnedHttpExecution keeps this record alive through actual execute/body finally.
                synchronized(state) { check(executing.remove(identity) === work) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled // Never turn observer cancellation into a new retryable Host result.
        } catch (failure: ReaderHttpFailure) {
            return ReaderCoreEventActor.HostReply.Error(failure.code, failure.message ?: "HTTP_OPERATION_FAILED", failure.retryable)
        } catch (_: JSONException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "HTTP_DESCRIPTOR_INVALID", false)
        } catch (_: IllegalArgumentException) {
            return ReaderCoreEventActor.HostReply.Error("INVALID_PARAMS", "HTTP_DESCRIPTOR_OR_REQUEST_INVALID", false)
        } catch (failure: IOException) {
            val timeout = failure is InterruptedIOException
            return ReaderCoreEventActor.HostReply.Error(if (timeout) "TIMEOUT" else "INTERNAL",
                if (timeout) "HTTP_TRANSPORT_TIMEOUT" else "HTTP_TRANSPORT_FAILED", retryableTransport(failure))
        } finally {
            if (reserved) synchronized(state) { check(admitted.remove(identity)) }
        }
    }

    /** Stop only this owner's real calls. Does not shut down a shared dispatcher or claim worker join. */
    fun beginClose(): CloseSnapshot {
        val active = synchronized(state) { closed = true; executing.values.toList() }
        active.forEach { it.cancel() }
        return snapshot()
    }

    /** Called only after App actor + HTTP quiescence is certified under its whole-turn lane.
     * Core source-fetch session.id is exactly sourceId. Never clear another session or global manager. */
    fun clearSourceCookieSession(runtime: String, sourceId: String): Boolean {
        require(sourceId.isNotBlank())
        val key = CookieKey(runtime, sourceId)
        val original = synchronized(state) {
            if (closed || admitted.isNotEmpty() || executing.isNotEmpty()) return false
            cookies[key]
        }
        original?.clear() // Platform CookieStore; no collector/Host state lock during store calls.
        return synchronized(state) {
            if (closed || admitted.isNotEmpty() || executing.isNotEmpty() || cookies[key] !== original) return false
            cookies.remove(key)
            true
        }
    }
    fun snapshot(): CloseSnapshot = synchronized(state) { CloseSnapshot(closed, admitted.toSet(), executing.keys.toSet(), cookies.size) }

    companion object {
        internal fun retryableTransport(error: IOException): Boolean = error !is ReaderHttpFailure &&
            (error is SocketException || error is SocketTimeoutException || error is EOFException || error is UnknownHostException)
    }
}

/** Cancellation returns only after this execute/body block has left its finally.
 * An uncooperative platform call remains an unresolved running Host job; no join is fabricated. */
internal suspend fun <T> awaitOwnedHttpExecution(cancel: () -> Unit, execute: () -> T): T = coroutineScope {
    val io = async(Dispatchers.IO) {
        try { Result.success(execute()) } catch (error: Exception) { Result.failure(error) }
    }
    try { io.await().getOrThrow() }
    catch (cancelled: CancellationException) {
        try { cancel() } catch (cleanup: Exception) { cancelled.addSuppressed(cleanup) }
        withContext(NonCancellable) { io.join() }
        throw cancelled
    }
}

internal class ReaderHttpCookieSession(limits: ReaderHttpLimits) {
    private val manager = CookieManager(ReaderHttpCookieAdmissionStore(CookieManager().cookieStore, limits), CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    @Synchronized fun clear() { manager.cookieStore.removeAll(); check(manager.cookieStore.cookies.isEmpty()) { "SOURCE_COOKIE_STORE_NOT_EMPTY" } }
    @Synchronized fun requestHeaders(url: java.net.URI, headers: Map<String, List<String>>): Map<String, List<String>> =
        manager.get(url, headers)
    @Synchronized fun responseHeaders(url: java.net.URI, headers: Map<String, List<String>>) { manager.put(url, headers) }
}

/** Only admission limits; matching, replacement and expiry remain the platform store. No eviction. */
private class ReaderHttpCookieAdmissionStore(private val delegate: CookieStore, private val limits: ReaderHttpLimits) : CookieStore by delegate {
    override fun add(uri: URI?, cookie: HttpCookie) {
        if (!cookie.hasExpired()) {
            val next = delegate.cookies.filterNot { it == cookie } + cookie
            val bytes = next.sumOf { item ->
                listOfNotNull(item.name, item.value, item.domain, item.path, item.comment, item.commentURL, item.portlist)
                    .sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
            }
            if (next.size > limits.maximumCookiesPerSession || bytes > limits.maximumCookieBytesPerSession)
                throw ReaderHttpFailure("RESOURCE_EXHAUSTED", "HTTP_COOKIE_SESSION_CONTENT_BUDGET")
        }
        delegate.add(uri, cookie)
    }
}

internal class ReaderHttpWork(
    val descriptor: ReaderHttpDescriptor,
    val limits: ReaderHttpLimits,
    val cookieSession: ReaderHttpCookieSession?,
) {
    private val state = Any()
    private val started = System.nanoTime()
    private var cancelled = false
    private var call: Call? = null
    private val cancelledSignal = CountDownLatch(1)
    @Volatile var fixedResult: String? = null
        private set

    fun install(value: Call) {
        val stop = synchronized(state) { check(call == null); call = value; cancelled }
        if (stop) value.cancel()
    }
    fun cancel() {
        val active = synchronized(state) { cancelled = true; call }
        cancelledSignal.countDown(); active?.cancel()
    }
    fun remainingMillis(): Long = TimeUnit.NANOSECONDS.toMillis(
        descriptor.timeoutMs * 1_000_000 - (System.nanoTime() - started)).coerceAtLeast(0)
    fun requireActive() = synchronized(state) {
        if (cancelled) throw ReaderHttpFailure("CANCELLED", "HTTP_ORIGINAL_CANCELLED")
        if (remainingMillis() <= 0 || call?.isCanceled() == true) throw SocketTimeoutException("HTTP_CALL_DEADLINE")
    }
    fun <T> whileCurrent(block: () -> T): T = synchronized(state) { requireActive(); block() }
    fun publish(result: String) = whileCurrent { check(fixedResult == null); fixedResult = result }
    fun backoff(attempt: Int) {
        requireActive()
        val delay = ReaderCoreHttpPolicy.retryBackoffMillis(descriptor.backoffMillis, attempt.toLong(), 2_000, remainingMillis())
        if (delay > 0) {
            try { cancelledSignal.await(delay, TimeUnit.MILLISECONDS) }
            catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt(); throw InterruptedIOException("HTTP_BACKOFF_INTERRUPTED")
            }
        }
        requireActive()
    }
}
