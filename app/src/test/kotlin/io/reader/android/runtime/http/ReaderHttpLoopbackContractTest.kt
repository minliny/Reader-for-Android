package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real loopback only; not a source/book/Native test. Neither selector has been run. */
class ReaderHttpLoopbackContractTest {
    @get:Rule val deadline: Timeout = Timeout.seconds(40)

    @Test fun retryAfterZero503KeepsFirstResponseAndCancellationDrainsOriginalIo(): Unit = runBlocking {
        Fixture(tls = false).use { fixture ->
            val origin503 = AtomicInteger()
            val originCancel = AtomicInteger()
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/503" -> if (origin503.incrementAndGet() == 1) {
                        MockResponse.Builder().code(503).addHeader("Retry-After", "0")
                            .addHeader("Content-Type", "text/plain; charset=utf-8").body("first-503").build()
                    } else MockResponse(code = 200, body = "forbidden-second-origin")
                    "/cancel" -> {
                        originCancel.incrementAndGet()
                        // Mature server delay affects only body bytes, not the headers gate.
                        MockResponse.Builder().body("unread-delayed-body").bodyDelay(1, TimeUnit.MINUTES).build()
                    }
                    else -> MockResponse(code = 404)
                }
            }
            val first = complete(fixture.host.execute(envelope(11, fixture.server.url("/503").toString())))
            assertEquals(503, first.getInt("status"))
            assertEquals("first-503", first.getString("body"))
            val actualHeaders = first.getJSONObject("headers")
            val retryAfterName = actualHeaders.keys().asSequence().single { it.equals("Retry-After", true) }
            assertEquals("0", actualHeaders.getString(retryAfterName))
            assertEquals(1, origin503.get())
            assertEquals(1, fixture.server.requestCount)
            assertEquals("/503", fixture.takeRequest().url.encodedPath)
            assertEquals(1, fixture.events["/503"].sentHeaders.get())
            assertBlockedOriginal(fixture.events["/503"], 503, "first-503")
            fixture.assertDrained()

            // A second, explicitly distinct operation answers the actual-child-exit question.
            val originalCancel = envelope(12, fixture.server.url("/cancel").toString())
            val observer = launch { fixture.host.execute(originalCancel) }
            try {
                withTimeout(5_000) { fixture.events["/cancel"].headersReceived.await() }
                assertEquals("/cancel", fixture.takeRequest().url.encodedPath)
                assertFalse(observer.isCompleted)
                val running = fixture.host.snapshot().executing.single()
                assertEquals(originalCancel.runtimeSession, running.runtime)
                assertEquals(originalCancel.requestId, running.requestId)
                assertEquals(originalCancel.requestToken, running.token)
                assertEquals(originalCancel.operationId, running.operationId)
                withTimeout(5_000) { observer.cancelAndJoin() }
                assertTrue(observer.isCompleted)
                assertTrue(observer.isCancelled)
                // Host removes executing only after awaitOwnedHttpExecution joins the real IO child.
                fixture.assertDrained()
                withTimeout(5_000) { fixture.events["/cancel"].callTerminated.await() }
                assertEquals(1, fixture.events["/cancel"].cancelled.get())
                assertEquals(1, fixture.events["/cancel"].sentHeaders.get())
                assertEquals(1, originCancel.get())
                assertEquals(2, fixture.server.requestCount) // one503 + this explicit cancellation call
            } finally {
                if (!observer.isCompleted) {
                    fixture.host.beginClose()
                    withTimeout(5_000) { observer.cancelAndJoin() }
                }
            }
        }
    }

    @Test fun realHttp2Coalesced421KeepsFirstResponseAndBlocksSecondOriginSend(): Unit = runBlocking {
        Fixture(tls = true).use { fixture ->
            val origin421 = AtomicInteger()
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/warm" -> MockResponse(body = "warm-h2")
                    "/421" -> if (origin421.incrementAndGet() == 1) {
                        MockResponse(code = 421, body = "first-coalesced-421")
                    } else MockResponse(code = 200, body = "forbidden-second-origin")
                    else -> MockResponse(code = 404)
                }
            }
            val warmUrl = fixture.server.url("/warm").newBuilder().host(HOST_A).build()
            val targetUrl = fixture.server.url("/421").newBuilder().host(HOST_B).build()
            val warmReply = fixture.host.execute(envelope(21, warmUrl.toString()))
            qualified(warmReply is ReaderCoreEventActor.HostReply.Complete, "TLS warmup did not complete: $warmReply")
            val warm = complete(warmReply)
            qualified(warm.getInt("status") == 200, "TLS warmup status=${warm.getInt("status")}")
            val warmWire = fixture.takeRequest()
            val warmConnection = fixture.events["/warm"].connections.singleOrNull()
            qualified(warmWire.version == "HTTP/2" && warmConnection?.protocol() == Protocol.HTTP_2,
                "warmup did not negotiate actual HTTP/2")
            fixture.assertDrained()

            val actual = fixture.host.execute(envelope(22, targetUrl.toString()))
            val targetWire = fixture.takeRequest()
            val targetConnections = fixture.events["/421"].connections.toList()
            val firstTargetConnection = targetConnections.firstOrNull()
            // Standard event/server facts, not a mocked coalesced flag or an HTTP/1 status421.
            qualified(targetWire.version == "HTTP/2" && firstTargetConnection?.protocol() == Protocol.HTTP_2,
                "target did not negotiate actual HTTP/2")
            qualified(warmWire.connectionIndex == targetWire.connectionIndex &&
                firstTargetConnection === warmConnection && firstTargetConnection != null &&
                firstTargetConnection.route().address.url.host == HOST_A && targetWire.url.host == HOST_B,
                "target first send did not use the exact warm other-host connection")
            assertEquals(warmWire.exchangeIndex + 1, targetWire.exchangeIndex)
            assertNotNull(targetWire.handshake)

            val returned = complete(actual)
            assertEquals(421, returned.getInt("status"))
            assertEquals("first-coalesced-421", returned.getString("body"))
            assertEquals(targetUrl.toString(), returned.getString("finalUrl"))
            assertEquals(1, origin421.get())
            assertEquals(2, fixture.server.requestCount) // warmA + one coalescedB origin request
            val events = fixture.events["/421"]
            assertEquals(1, events.sentHeaders.get())
            assertBlockedOriginal(events, 421, "first-coalesced-421")
            // Connection establishment may precede the guard; it is not an extra origin send.
            assertTrue("the library follow-up must have reached a new connection before its guard rejection",
                targetConnections.size >= 2 && targetConnections.drop(1).any { it !== warmConnection })
            fixture.assertDrained()
        }
    }

    private fun assertBlockedOriginal(events: Events, status: Int, body: String) {
        val next = events.followUps.single()
        assertEquals(status, next.first)
        val failure = events.blocked.single()
        assertFalse(failure.second)
        assertSame(next.second, failure.first.permit)
        val permit = failure.first.permit
        assertEquals(1, permit.work.descriptor.maximumAttempts)
        assertEquals(status, checkNotNull(permit.received).response.code)
        assertEquals(body, checkNotNull(permit.received).encodedBytes.toString(Charsets.UTF_8))
    }

    private fun complete(reply: ReaderCoreEventActor.HostReply): JSONObject {
        assertTrue("Expected original Host Complete, got $reply", reply is ReaderCoreEventActor.HostReply.Complete)
        return JSONObject((reply as ReaderCoreEventActor.HostReply.Complete).resultJson)
    }

    private fun envelope(id: Long, url: String) = ReaderCoreEventActor.HostEnvelope(
        runtimeSession = UUID(0, 700).toString(), requestId = id,
        requestToken = UUID(0, 1_000 + id).toString(), operationId = 2_000 + id,
        capability = "http.execute", parametersJson = JSONObject().put("url", url)
            .put("timeoutMs", 15_000).put("followRedirects", false).put("maxRedirects", 0)
            .put("retry", JSONObject().put("maxAttempts", 1).put("backoffMillis", 0))
            .put("usePlatformCookieJar", false).toString(),
    )

    private fun qualified(condition: Boolean, reason: String) {
        if (!condition) throw AssertionError("NOT_QUALIFIED_REAL_H2: $reason; no421 contract PASS")
    }

    private class Events {
        val connections = CopyOnWriteArrayList<Connection>()
        val sentHeaders = AtomicInteger()
        val followUps = CopyOnWriteArrayList<Pair<Int, ReaderHttpHopPermit>>()
        val blocked = CopyOnWriteArrayList<Pair<ReaderHttpImplicitFollowupBlocked, Boolean>>()
        val headersReceived = CompletableDeferred<Unit>()
        val callTerminated = CompletableDeferred<Unit>()
        val cancelled = AtomicInteger()
    }

    private class EventLog : EventListener() {
        private val byPath = ConcurrentHashMap<String, Events>()
        operator fun get(path: String): Events = byPath.computeIfAbsent(path) { Events() }
        private fun events(call: Call) = get(call.request().url.encodedPath)
        override fun connectionAcquired(call: Call, connection: Connection) { events(call).connections.add(connection) }
        override fun requestHeadersStart(call: Call) { events(call).sentHeaders.incrementAndGet() }
        override fun responseHeadersEnd(call: Call, response: Response) { events(call).headersReceived.complete(Unit) }
        override fun followUpDecision(call: Call, networkResponse: Response, nextRequest: Request?) {
            nextRequest?.tag(ReaderHttpHopPermit::class.java)?.let { events(call).followUps.add(networkResponse.code to it) }
        }
        override fun retryDecision(call: Call, exception: IOException, retry: Boolean) {
            if (exception is ReaderHttpImplicitFollowupBlocked) events(call).blocked.add(exception to retry)
        }
        override fun callEnd(call: Call) { events(call).callTerminated.complete(Unit) }
        override fun callFailed(call: Call, ioe: IOException) { events(call).callTerminated.complete(Unit) }
        override fun canceled(call: Call) { events(call).cancelled.incrementAndGet() }
    }

    private class Fixture(tls: Boolean) : Closeable {
        val events = EventLog()
        val server = MockWebServer()
        val transport: OkHttpClient
        val host: ReaderHttpHost
        init {
            val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
            val builder = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).fastFallback(false)
                .eventListener(events).protocols(if (tls) listOf(Protocol.HTTP_2, Protocol.HTTP_1_1) else listOf(Protocol.HTTP_1_1))
            if (tls) {
                val root = HeldCertificate.Builder().certificateAuthority(0).commonName("Reader loopback test CA").build()
                val leaf = HeldCertificate.Builder().signedBy(root).commonName(HOST_A)
                    .addSubjectAlternativeName(HOST_A).addSubjectAlternativeName(HOST_B).build()
                val trust = HandshakeCertificates.Builder().addTrustedCertificate(root.certificate).build()
                server.useHttps(HandshakeCertificates.Builder().heldCertificate(leaf).build().sslSocketFactory())
                builder.sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).dns(Dns { name ->
                    if (name != HOST_A && name != HOST_B) throw UnknownHostException("loopback fixture refuses $name")
                    listOf(loopback)
                })
            }
            transport = builder.build()
            host = ReaderHttpHost(transport = transport)
            server.start(loopback, 0)
        }
        fun takeRequest(): RecordedRequest = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "LOOPBACK_REQUEST_NOT_OBSERVED" }
        fun assertDrained() {
            val state = host.snapshot()
            assertTrue(state.admitted.isEmpty())
            assertTrue(state.executing.isEmpty())
        }
        override fun close() {
            var primary: Throwable? = null
            for (action in listOf<() -> Unit>({ host.beginClose() }, { transport.connectionPool.evictAll() },
                { server.close() }, { transport.dispatcher.executorService.shutdown() })) {
                try { action() } catch (error: Throwable) {
                    if (primary == null) primary = error else primary.addSuppressed(error)
                }
            }
            primary?.let { throw it }
        }
    }

    private companion object {
        const val HOST_A = "a.reader-loopback.test"
        const val HOST_B = "b.reader-loopback.test"
    }
}
