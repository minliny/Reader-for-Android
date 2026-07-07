package com.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reader.api.CoreException
import com.reader.api.CoreTimeoutException
import com.reader.api.ReaderCoreClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2 App-level reading-chain proof.
 *
 * Verifies the Core command chain `source.import → book.search` round-trips
 * through [ReaderCoreClient.sendAndAwait] (JNI .so loaded, poll thread
 * running, all 6 host handlers registered with real executors). w3.org is NOT
 * a real book source, so search returns empty — this proves the chain
 * *round-trips* (Core responds, no timeout), NOT that real L1-L5 book parsing
 * succeeds.
 *
 * `book.detail` / `book.toc` / `chapter.content` are conditionally skipped
 * (search yields no bookUrl) — they require a real book source and are
 * deferred to Phase 5 (App-level proof with Activity + UI + user-visible
 * reading page).
 *
 * This is a Core commands chain proof, NOT a UI/reducer/ViewState
 * integration. `canEnterMainline` remains false.
 */
@RunWith(AndroidJUnit4::class)
class AppLevelReadingChainProofTest {

    private lateinit var client: ReaderCoreClient

    @Before
    fun setUp() {
        // Production wiring: JNI .so + poll thread + all 6 real host handlers
        // (http.execute, cookie.get/set, webview [REQUIRES_UI_CONTEXT],
        // anti_bot, media.download).
        client = ReaderCoreClient.init()
    }

    @After
    fun tearDown() {
        ReaderCoreClient.resetForTest()
    }

    /**
     * Minimal W3C source descriptor. Uses `$.none` JSONPath rules so Core's
     * parser accepts the source but yields no books — sufficient to prove the
     * command chain round-trips without depending on a real book source.
     */
    private fun minimalW3cSource(): JSONObject = JSONObject().apply {
        put("bookSourceUrl", "https://www.w3.org/")
        put("bookSourceName", "W3C Proof")
        put("sourceType", 0)
        put("enabled", true)
        put("searchUrl", "https://www.w3.org/?s={{key}}")
        put("ruleSearch", JSONObject().apply {
            put("bookList", "$.none")
            put("name", "$.none")
            put("bookUrl", "$.none")
        })
        put("ruleBookInfo", JSONObject().apply {
            put("name", "$.none")
            put("tocUrl", "")
        })
        put("ruleToc", JSONObject().apply {
            put("chapterList", "$.none")
            put("title", "$.none")
            put("chapterUrl", "$.none")
        })
        put("ruleContent", JSONObject().apply {
            put("content", "$.none")
        })
    }

    /**
     * [ReaderCoreClient.init] must succeed: JNI .so loads, poll thread starts,
     * all 6 host handlers register without crashing.
     */
    @Test
    fun initLoadsJniAndStartsPollThread() {
        assertNotNull("ReaderCoreClient.init() must return an instance", client)
    }

    /**
     * `source.import` with a minimal W3C source descriptor must round-trip
     * (Core responds, no [CoreTimeoutException]). The response may carry a
     * sourceId (success) or an error (CoreException) — both prove the chain
     * closed. Timeout is the only failure.
     */
    @Test
    fun sourceImportRoundTripsWithoutTimeout() = runBlocking {
        try {
            val response = client.sendAndAwait(
                "source.import",
                minimalW3cSource(),
                timeoutMillis = 30_000L
            )
            assertNotNull("source.import response must be non-null", response)
        } catch (e: CoreTimeoutException) {
            fail("source.import must not time out (chain must round-trip). ${e.message}")
        } catch (e: CoreException) {
            // Core responded with an error — chain closed, allowed.
        }
    }

    /**
     * `book.search` must round-trip (Core responds, no timeout). w3.org is not
     * a real book source, so results will be empty — the proof is that Core
     * acknowledges the command, not that books are found.
     */
    @Test
    fun bookSearchRoundTripsWithoutTimeout() = runBlocking {
        // Import first to obtain a sourceId (tolerate CoreException if import
        // rejects the minimal source — search still proves round-trip).
        var sourceId: String? = null
        try {
            val importResp = client.sendAndAwait(
                "source.import",
                minimalW3cSource(),
                timeoutMillis = 30_000L
            )
            sourceId = importResp.optString("sourceId").takeIf { it.isNotBlank() }
        } catch (e: CoreTimeoutException) {
            fail("source.import (precondition) must not time out. ${e.message}")
        } catch (e: CoreException) {
            // Import rejected — search still proves round-trip.
        }

        try {
            val searchParams = JSONObject().apply {
                sourceId?.let { put("sourceId", it) }
                put("source", minimalW3cSource())
                put("keyword", "test")
            }
            val response = client.sendAndAwait(
                "book.search",
                searchParams,
                timeoutMillis = 30_000L
            )
            assertNotNull("book.search response must be non-null", response)
        } catch (e: CoreTimeoutException) {
            fail("book.search must not time out (chain must round-trip). ${e.message}")
        } catch (e: CoreException) {
            // Core responded with an error — chain closed, allowed.
        }
    }

    /**
     * The minimal reading chain (source.import → book.search) must round-trip
     * end-to-end without timeout. This is the "chain closed" proof.
     *
     * Uses block body (not `= runBlocking`) so the method returns Unit — JUnit 4
     * rejects test methods whose last expression yields a non-void type
     * (`sendAndAwait` returns JSONObject).
     */
    @Test
    fun readingChainRoundTripsWithoutTimeout() {
        runBlocking {
            // source.import
            try {
                client.sendAndAwait("source.import", minimalW3cSource(), timeoutMillis = 30_000L)
            } catch (e: CoreTimeoutException) {
                fail("source.import must not time out (chain must round-trip). ${e.message}")
            } catch (e: CoreException) {
                // Core responded — chain closed.
            }

            // book.search
            try {
                val searchParams = JSONObject().apply {
                    put("source", minimalW3cSource())
                    put("keyword", "test")
                }
                client.sendAndAwait("book.search", searchParams, timeoutMillis = 30_000L)
            } catch (e: CoreTimeoutException) {
                fail("book.search must not time out (chain must round-trip). ${e.message}")
            } catch (e: CoreException) {
                // Core responded — chain closed.
            }
        }
    }
}
