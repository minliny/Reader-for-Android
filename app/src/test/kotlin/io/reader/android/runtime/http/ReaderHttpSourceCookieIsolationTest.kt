package io.reader.android.runtime.http

import io.reader.android.runtime.ReaderCoreEventActor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.Closeable
import java.net.InetAddress
import java.net.Proxy
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual loopback HTTP and the production CookieManager path. Source-only; NOT RUN. */
class ReaderHttpSourceCookieIsolationTest {
    @get:Rule val deadline: Timeout = Timeout.seconds(40)

    @Test fun clearingOneSourceRuntimeKeepsOtherCookiesAndRefusesAnInFlightCall(): Unit = runBlocking {
        Fixture().use { fixture ->
            val arrived = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            fixture.server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    "/set/alpha" -> cookie("alpha")
                    "/set/beta" -> cookie("beta")
                    "/set/other-runtime" -> cookie("other-runtime")
                    "/held" -> {
                        arrived.complete(Unit) // Real server received this request; no synthetic Host state.
                        check(release.await(5, TimeUnit.SECONDS)) { "LOOPBACK_RELEASE_NOT_OBSERVED" }
                        MockResponse(body = "held-complete")
                    }
                    else -> MockResponse(body = "cookie-observation")
                }
                private fun cookie(value: String) = MockResponse.Builder().code(200)
                    .addHeader("Set-Cookie", "reader_session=$value; Path=/").body("cookie-set").build()
            }
            var requestId = 100L
            suspend fun exchange(runtime: String, source: String, path: String, expectedCookie: String?) {
                val original = envelope(++requestId, fixture.server.url(path).toString(), runtime, source)
                fixture.complete(fixture.host.execute(original))
                fixture.assertCookie(fixture.takeRequest(), path, expectedCookie)
                fixture.assertDrained()
            }
            // Same origin, same cookie name; each identity must start with an independent jar.
            exchange(RUNTIME_A, SOURCE_A, "/set/alpha", null)
            exchange(RUNTIME_A, SOURCE_B, "/set/beta", null)
            exchange(RUNTIME_B, SOURCE_A, "/set/other-runtime", null)
            assertEquals(3, fixture.host.snapshot().cookieSessions)

            val held = envelope(++requestId, fixture.server.url("/held").toString(), RUNTIME_A, SOURCE_A)
            val inFlight = async { fixture.host.execute(held) }
            var primary: Throwable? = null
            try {
                withTimeout(5_000) { arrived.await() }
                fixture.assertCookie(fixture.takeRequest(), "/held", "alpha")
                val snapshot = fixture.host.snapshot()
                val running = snapshot.executing.single()
                assertEquals(snapshot.admitted.single(), running)
                assertEquals(held.runtimeSession, running.runtime)
                assertEquals(held.requestId, running.requestId)
                assertEquals(held.requestToken, running.token)
                assertEquals(held.operationId, running.operationId)
                assertFalse(inFlight.isCompleted)
                // Refusal for the active jar and both other exact keys must not clear any jar.
                assertFalse(fixture.host.clearSourceCookieSession(RUNTIME_A, SOURCE_A))
                assertFalse(fixture.host.clearSourceCookieSession(RUNTIME_A, SOURCE_B))
                assertFalse(fixture.host.clearSourceCookieSession(RUNTIME_B, SOURCE_A))
                release.countDown()
                fixture.complete(withTimeout(5_000) { inFlight.await() })
                fixture.assertDrained()
            } catch (error: Throwable) {
                primary = error
                throw error
            } finally {
                release.countDown()
                if (!inFlight.isCompleted) {
                    try {
                        fixture.host.beginClose()
                        withContext(NonCancellable) { withTimeout(5_000) { inFlight.cancelAndJoin() } }
                    } catch (cleanup: Throwable) {
                        if (primary == null) throw cleanup else primary.addSuppressed(cleanup)
                    }
                }
            }
            // These are actual origin requests after rejection, not observations of private maps.
            exchange(RUNTIME_A, SOURCE_A, "/after-refused/alpha", "alpha")
            exchange(RUNTIME_A, SOURCE_B, "/after-refused/beta", "beta")
            exchange(RUNTIME_B, SOURCE_A, "/after-refused/other-runtime", "other-runtime")
            assertEquals(3, fixture.host.snapshot().cookieSessions)

            assertTrue(fixture.host.clearSourceCookieSession(RUNTIME_A, SOURCE_A))
            assertEquals(2, fixture.host.snapshot().cookieSessions)
            exchange(RUNTIME_A, SOURCE_A, "/after-clear/alpha", null)
            exchange(RUNTIME_A, SOURCE_B, "/after-clear/beta", "beta")
            exchange(RUNTIME_B, SOURCE_A, "/after-clear/other-runtime", "other-runtime")
            assertEquals(10, fixture.server.requestCount) // Three set + held + three refusal + three clear observations.
        }
    }

    private fun envelope(id: Long, url: String, runtime: String, source: String) = ReaderCoreEventActor.HostEnvelope(
        runtimeSession = runtime, requestId = id, requestToken = UUID(0, 1_000 + id).toString(), operationId = 2_000 + id,
        capability = "http.execute", parametersJson = JSONObject().put("url", url).put("timeoutMs", 15_000)
            .put("followRedirects", false).put("maxRedirects", 0)
            .put("retry", JSONObject().put("maxAttempts", 1).put("backoffMillis", 0))
            .put("usePlatformCookieJar", true).put("session", JSONObject().put("id", source)).toString(),
    )

    private class Fixture : Closeable {
        val server = MockWebServer()
        val transport = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).fastFallback(false)
            .protocols(listOf(Protocol.HTTP_1_1)).build()
        val host = ReaderHttpHost(transport = transport)
        init { server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0) }
        fun complete(reply: ReaderCoreEventActor.HostReply) {
            assertTrue("Expected actual HTTP Complete, got $reply", reply is ReaderCoreEventActor.HostReply.Complete)
            assertEquals(200, JSONObject((reply as ReaderCoreEventActor.HostReply.Complete).resultJson).getInt("status"))
        }
        fun takeRequest(): RecordedRequest = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "LOOPBACK_REQUEST_NOT_OBSERVED" }
        fun assertCookie(request: RecordedRequest, path: String, value: String?) {
            assertEquals(path, request.url.encodedPath)
            assertEquals(if (value == null) emptyList<String>() else listOf("reader_session=$value"), request.headers.values("Cookie"))
        }
        fun assertDrained() {
            val snapshot = host.snapshot()
            assertTrue(snapshot.admitted.isEmpty()); assertTrue(snapshot.executing.isEmpty())
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
        val RUNTIME_A = UUID(0, 700).toString()
        val RUNTIME_B = UUID(0, 701).toString()
        const val SOURCE_A = "source-a"
        const val SOURCE_B = "source-b"
    }
}
