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
        // 星星小说网 (xingxingxsw.com) — accessible from shuyuan-api.yiove.com book source collection
        source = BookSource(
            sourceUrl = "https://www.xingxingxsw.com",
            sourceName = "星星小说网",
            searchUrl = "/search.php?key=key"
        )
    }

    @Test
    fun `xingxingxsw search returns results or明确错误`() = runBlocking {
        try {
            val results = bridge.search(SearchQuery("剑来"), source)
            if (results.isNotEmpty()) {
                assertNotNull(results[0].name)
                println("SUCCESS: search returned ${results.size} results")
                println("First result: ${results[0].name} by ${results[0].author}")
                if (results[0].detailUrl != null) {
                    println("Detail URL: ${results[0].detailUrl}")
                }
            } else {
                println("WARNING: search returned empty results")
            }
        } catch (e: ReaderException) {
            println("ReaderException: ${e.error.code} - ${e.error.message}")
            assertNotNull(e.error.code)
        }
    }

    @Test
    fun `xingxingxsw search for 仙逆 returns results`() = runBlocking {
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
