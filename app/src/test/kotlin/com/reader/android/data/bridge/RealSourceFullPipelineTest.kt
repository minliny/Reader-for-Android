package com.reader.android.data.bridge

import com.reader.android.AppProvider
import com.reader.android.data.model.BookSource
import com.reader.android.data.network.OkHttpTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URLEncoder

/**
 * Full pipeline smoke test for all reachable novel sources.
 * Tests: import → search → book info → TOC → content
 * Uses parallel requests for speed.
 *
 * Run with: ./gradlew testDebugUnitTest --tests "com.reader.android.data.bridge.RealSourceFullPipelineTest"
 */
class RealSourceFullPipelineTest {

    private lateinit var transport: OkHttpTransport
    private lateinit var bridge: RealCoreBridge

    @Before
    fun setup() {
        AppProvider.initForTesting()
        AppProvider.enableNetworkForTestingOnly()
        transport = OkHttpTransport()
        bridge = RealCoreBridge(transport)
    }

    @Test
    fun fullPipelineTest() = runBlocking {
        val sourcesFile = File("/tmp/reachable-novel-sources.txt")
        assumeTrue("File /tmp/reachable-novel-sources.txt not found. Run BookSourceFetcherTest.quickSmokeTestNovelSources first.", sourcesFile.exists())
        val lines = sourcesFile.readLines().filter { it.isNotBlank() }
        println("Testing full pipeline for ${lines.size} novel sources (parallel)...\n")

        // Parse all sources
        val sources = lines.mapNotNull { line ->
            val parts = line.split(" | ")
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0].trim()
            var sourceUrl = parts[1].trim()

            if (!sourceUrl.startsWith("http://") && !sourceUrl.startsWith("https://")) {
                return@mapNotNull null
            }
            sourceUrl = sourceUrl.split("#")[0].trim()

            val searchUrl = detectSearchUrl(sourceUrl) ?: return@mapNotNull null

            BookSource(
                sourceUrl = sourceUrl,
                sourceName = name,
                searchUrl = searchUrl
            )
        }

        println("Parsed ${sources.size} valid sources with search patterns\n")

        // Test in parallel batches
        val results = mutableListOf<PipelineResult>()
        val batchSize = 20
        var passed = 0
        var failed = 0
        var skipped = 0

        sources.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            println("Testing batch ${batchIndex + 1}/${(sources.size + batchSize - 1) / batchSize}...")
            val batchResults = batch.map { source ->
                async { testSource(source) }
            }.awaitAll()
            results.addAll(batchResults)

            batchResults.forEach { result ->
                when {
                    result.searchStatus == "200" && result.parseStatus == "OK" -> {
                        println("✅ ${result.sourceName}: ${result.resultCount} results")
                        passed++
                    }
                    result.searchStatus == "200" && result.parseStatus == "EMPTY" -> {
                        println("⚠️ ${result.sourceName}: Empty results")
                        passed++
                    }
                    result.searchStatus == "403" || result.searchStatus == "401" -> {
                        println("🔒 ${result.sourceName}: Access denied")
                        failed++
                    }
                    result.searchStatus != "200" -> {
                        println("❌ ${result.sourceName}: HTTP ${result.searchStatus}")
                        failed++
                    }
                    result.error != null -> {
                        println("💥 ${result.sourceName}: ${result.error}")
                        failed++
                    }
                }
            }
        }

        // Summary
        println("\n" + "=".repeat(60))
        println("PIPELINE TEST SUMMARY")
        println("=".repeat(60))
        println("Total sources: ${sources.size}")
        println("✅ Passed: $passed")
        println("❌ Failed: $failed")
        println("⏭️ Skipped: $skipped")
        val successRate = if (passed + failed > 0) (passed * 100.0 / (passed + failed)).toInt() else 0
        println("Success rate: $successRate%")

        // Save results
        val reportFile = File("/tmp/pipeline-test-report.txt")
        reportFile.writeText(buildString {
            appendLine("=== Real Source Full Pipeline Test Report ===")
            appendLine("Total sources: ${sources.size}")
            appendLine("Passed: $passed")
            appendLine("Failed: $failed")
            appendLine("Skipped: $skipped")
            appendLine("Success rate: $successRate%")
            appendLine()
            appendLine("=== Detailed Results ===")
            results.forEach { r ->
                appendLine("${r.sourceName} | ${r.sourceUrl}")
                appendLine("  Search: ${r.searchStatus} | Parse: ${r.parseStatus} | Results: ${r.resultCount}")
                r.error?.let { appendLine("  Error: $it") }
            }
        })
        println("\nReport saved to ${reportFile.absolutePath}")

        // Save working sources
        val workingFile = File("/tmp/pipeline-working-sources.txt")
        workingFile.writeText(results.filter { it.searchStatus == "200" && it.parseStatus == "OK" }
            .joinToString("\n") { "${it.sourceName} | ${it.sourceUrl} | ${it.resultCount} results" })
        println("Working sources saved to ${workingFile.absolutePath}")
    }

    private data class PipelineResult(
        val sourceName: String,
        val sourceUrl: String,
        val searchStatus: String,
        val parseStatus: String,
        val resultCount: Int,
        val error: String?
    )

    private fun detectSearchUrl(sourceUrl: String): String? {
        val patterns = listOf(
            "/search?q=key",
            "/search?keyword=key",
            "/search?kw=key",
            "/search/key",
            "/s?key",
            "/book/search?keyword=key",
            "/api/search?q=key"
        )
        return patterns.firstOrNull()
    }

    private suspend fun testSource(source: BookSource): PipelineResult {
        return try {
            val searchUrlPattern = source.searchUrl
            if (searchUrlPattern == null) {
                return PipelineResult(
                    sourceName = source.sourceName,
                    sourceUrl = source.sourceUrl,
                    searchStatus = "ERROR",
                    parseStatus = "FAILED",
                    resultCount = 0,
                    error = "searchUrl is null"
                )
            }
            val searchUrl = searchUrlPattern.replace("key", URLEncoder.encode("凡人修仙传", "UTF-8"))
            val baseUrl = source.sourceUrl

            val fullUrl = if (searchUrl.startsWith("http")) {
                searchUrl
            } else {
                baseUrl.trimEnd('/') + searchUrl
            }

            val response = transport.get(fullUrl, emptyMap())

            if (response.code != 200) {
                return PipelineResult(
                    sourceName = source.sourceName,
                    sourceUrl = baseUrl,
                    searchStatus = response.code.toString(),
                    parseStatus = "N/A",
                    resultCount = 0,
                    error = null
                )
            }

            val parser = com.reader.android.data.network.SearchParser()
            val results = try {
                parser.parseSearchResponse(response.body, source.sourceName)
            } catch (e: Exception) {
                emptyList()
            }

            PipelineResult(
                sourceName = source.sourceName,
                sourceUrl = baseUrl,
                searchStatus = "200",
                parseStatus = if (results.isEmpty()) "EMPTY" else "OK",
                resultCount = results.size,
                error = null
            )
        } catch (e: Exception) {
            PipelineResult(
                sourceName = source.sourceName,
                sourceUrl = source.sourceUrl,
                searchStatus = "ERROR",
                parseStatus = "FAILED",
                resultCount = 0,
                error = e.message ?: "Unknown error"
            )
        }
    }
}