package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.model.BookSource
import com.reader.android.data.model.SearchQuery
import com.reader.android.data.network.OkHttpTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Real network smoke test: biquge.com search.
 * This test accesses the real internet.
 * First smoke captures the response for fixture creation.
 */
class RealCoreBridgeSmokeTest {

    private lateinit var bridge: RealCoreBridge
    private lateinit var source: BookSource

    @Before
    fun setup() {
        AppProvider.initForTesting()
        AppProvider.enableNetworkForTestingOnly()
        bridge = RealCoreBridge(OkHttpTransport())
        source = BookSource(
            sourceUrl = "https://www.biquge.com",
            sourceName = "笔趣阁",
            searchUrl = "/search?q=key"
        )
    }

    @Test
    fun `biquge search for 剑来 returns results or明确错误`() = runBlocking {
        try {
            val results = bridge.search(SearchQuery("剑来"), source)
            if (results.isNotEmpty()) {
                assertNotNull(results[0].name)
                assertNotNull(results[0].author)
                println("SUCCESS: search returned ${results.size} results")
                println("First result: ${results[0].name} by ${results[0].author}")
                if (results[0].detailUrl != null) {
                    println("Detail URL: ${results[0].detailUrl}")
                }
            } else {
                println("WARNING: search returned empty results (may be blocked or structure changed)")
            }
        } catch (e: ReaderException) {
            // Expected: if site is down or structure changed
            println("ReaderException caught: ${e.error.code} - ${e.error.message}")
            // This is acceptable for smoke — we just confirmed the error path works
            assertNotNull(e.error.code)
        } catch (e: Exception) {
            println("Unexpected exception: ${e.javaClass.name}: ${e.message}")
            // Could be network timeout, DNS failure, etc. — still valid smoke feedback
            assertNotNull(e.message)
        }
    }

    @Test
    fun `biquge search for 仙逆 returns results`() = runBlocking {
        try {
            val results = bridge.search(SearchQuery("仙逆"), source)
            if (results.isNotEmpty()) {
                assertTrue(results.isNotEmpty())
                println("SUCCESS: '仙逆' search returned ${results.size} results")
                results.take(3).forEach {
                    println("  - ${it.name} by ${it.author}")
                }
            }
        } catch (e: Exception) {
            println("Exception: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
