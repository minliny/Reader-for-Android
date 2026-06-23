package com.reader.android.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Smoke test to fetch all book sources from the import API and save to file.
 */
class BookSourceFetcherTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Test
    fun fetchAllBookSources() {
        assumeLiveSourceSmokeEnabled()
        val url = "https://shuyuan-api.yiove.com/import/book-source-collection/a8adf570-e115-4b99-87e3-ccaf298ae361"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val file = File("/tmp/book-sources-all.json")
            file.writeText(body)
            println("Saved ${body.length} bytes to ${file.absolutePath}")
        }
    }

    @Test
    fun parseAndFilterNovelSources() {
        assumeLiveSourceSmokeEnabled()
        val file = File("/tmp/book-sources-all.json")
        if (!file.exists()) {
            println("File not found. Run fetchAllBookSources first.")
            return
        }

        val json = file.readText()
        val arr = JSONArray(json)
        val total = arr.length()
        println("Total sources: $total")

        // bookSourceType: 0=小说, 1=漫画, 2=音频, etc.
        val novelSources = mutableListOf<Triple<String, String, Int>>() // name to url to type
        val comicSources = mutableListOf<Triple<String, String, Int>>()
        val audioSources = mutableListOf<Triple<String, String, Int>>()
        val otherSources = mutableListOf<Triple<String, String, Int>>()
        val noUrlSources = mutableListOf<String>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("bookSourceName", "unnamed")
            val url = obj.optString("bookSourceUrl", "")
            val type = obj.optInt("bookSourceType", -1)

            when (type) {
                0 -> novelSources.add(Triple(name, url, type))
                1 -> comicSources.add(Triple(name, url, type))
                2 -> audioSources.add(Triple(name, url, type))
                else -> otherSources.add(Triple(name, url, type))
            }

            if (url.isBlank()) {
                noUrlSources.add(name)
            }
        }

        println("\n=== Novel Sources (bookSourceType=0): ${novelSources.size} ===")

        // Save all sources to separate files
        val novelFile = File("/tmp/novel-sources.txt")
        novelFile.writeText(novelSources.joinToString("\n") { "${it.first} | ${it.second} | type=${it.third}" })
        println("Saved ${novelSources.size} novel sources to ${novelFile.absolutePath}")

        // Filter valid HTTP URLs for testing
        val validHttpSources = novelSources.filter {
            it.second.startsWith("http://") || it.second.startsWith("https://")
        }
        val validFile = File("/tmp/novel-sources-http.txt")
        validFile.writeText(validHttpSources.joinToString("\n") { "${it.first} | ${it.second}" })
        println("Saved ${validHttpSources.size} HTTP-valid novel sources to ${validFile.absolutePath}")

        println("\n=== Summary ===")
        println("Total sources: $total")
        println("Novel sources (type=0): ${novelSources.size}")
        println("Comic sources (type=1): ${comicSources.size}")
        println("Audio sources (type=2): ${audioSources.size}")
        println("Other sources: ${otherSources.size}")
        println("Sources with blank URL: ${noUrlSources.size}")
        println("Novel sources with valid HTTP URL: ${validHttpSources.size}")
    }

    @Test
    fun smokeTestNovelSources() {
        assumeLiveSourceSmokeEnabled()
        val file = File("/tmp/novel-sources-http.txt")
        if (!file.exists()) {
            println("File not found. Run parseAndFilterNovelSources first.")
            return
        }

        val lines = file.readLines().filter { it.isNotBlank() }
        println("Testing ${lines.size} novel sources with HTTP URLs...\n")

        val results = mutableListOf<SmokeResult>()

        for (line in lines) {
            val parts = line.split(" | ")
            if (parts.size < 2) continue
            val name = parts[0].trim()
            var url = parts[1].trim()

            // Clean URL - remove anchors and fragments
            url = url.split("#")[0].trim()
            if (url.isBlank()) continue

            val result = testUrl(name, url)
            results.add(result)

            val statusIcon = when (result.status) {
                "200" -> "✅"
                "403" -> "🔒"
                "404" -> "❌"
                "TIMEOUT" -> "⏰"
                "ERROR" -> "💥"
                else -> "❓"
            }
            println("$statusIcon ${result.name}: ${result.status} - ${result.message.take(60)}")
        }

        // Summary
        val success = results.count { it.status == "200" }
        val blocked = results.count { it.status == "403" }
        val notFound = results.count { it.status == "404" }
        val timeout = results.count { it.status == "TIMEOUT" }
        val error = results.count { it.status == "ERROR" }

        println("\n=== Summary ===")
        println("Total tested: ${results.size}")
        println("✅ 200 OK: $success")
        println("🔒 403 Forbidden: $blocked")
        println("❌ 404 Not Found: $notFound")
        println("⏰ Timeout: $timeout")
        println("💥 Error: $error")

        // Save detailed results
        val reportFile = File("/tmp/smoke-test-report.txt")
        reportFile.writeText(buildString {
            appendLine("=== Book Source Smoke Test Report ===")
            appendLine("Total tested: ${results.size}")
            appendLine("✅ 200 OK: $success")
            appendLine("🔒 403 Forbidden: $blocked")
            appendLine("❌ 404 Not Found: $notFound")
            appendLine("⏰ Timeout: $timeout")
            appendLine("💥 Error: $error")
            appendLine()
            appendLine("=== Detailed Results ===")
            results.forEach { r ->
                appendLine("${r.name} | ${r.url} | ${r.status} | ${r.message}")
            }
        })
        println("\nReport saved to ${reportFile.absolutePath}")

        // Save working sources
        val workingFile = File("/tmp/working-novel-sources.txt")
        workingFile.writeText(results.filter { it.status == "200" }.joinToString("\n") { "${it.name} | ${it.url}" })
        println("Working sources saved to ${workingFile.absolutePath}")
    }

    private fun assumeLiveSourceSmokeEnabled() {
        assumeTrue(
            "Live source smoke is opt-in; set READER_ANDROID_LIVE_SOURCE_SMOKE=1 after confirming authorization.",
            System.getenv("READER_ANDROID_LIVE_SOURCE_SMOKE") == "1"
        )
    }

    private data class SmokeResult(
        val name: String,
        val url: String,
        val status: String,
        val message: String
    )

    private fun testUrl(name: String, url: String): SmokeResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .head() // Just check headers
                .build()

            client.newCall(request).execute().use { response ->
                SmokeResult(
                    name = name,
                    url = url,
                    status = response.code.toString(),
                    message = response.message
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            SmokeResult(name, url, "TIMEOUT", e.message ?: "timeout")
        } catch (e: java.net.UnknownHostException) {
            SmokeResult(name, url, "ERROR", "Unknown host: ${e.message}")
        } catch (e: Exception) {
            SmokeResult(name, url, "ERROR", e.message ?: "unknown error")
        }
    }

    @Test
    fun quickSmokeTestNovelSources() {
        assumeLiveSourceSmokeEnabled()
        val file = File("/tmp/novel-sources-http.txt")
        if (!file.exists()) {
            println("File not found. Run parseAndFilterNovelSources first.")
            return
        }

        val lines = file.readLines().filter { it.isNotBlank() }
        println("Quick testing ${lines.size} novel sources (TCP connect with 3s timeout)...\n")

        val results = mutableListOf<SmokeResult>()

        for (line in lines) {
            val parts = line.split(" | ")
            if (parts.size < 2) continue
            val name = parts[0].trim()
            var url = parts[1].trim()

            // Clean URL - remove anchors and fragments
            url = url.split("#")[0].trim()
            if (url.isBlank()) continue

            val result = quickTestUrl(name, url)
            results.add(result)

            val statusIcon = when (result.status) {
                "OPEN" -> "✅"
                "REFUSED" -> "🚫"
                "TIMEOUT" -> "⏰"
                "UNKNOWN" -> "💥"
                else -> "❓"
            }
            println("$statusIcon $name: ${result.status} - ${result.message}")
        }

        // Summary
        val open = results.count { it.status == "OPEN" }
        val refused = results.count { it.status == "REFUSED" }
        val timeout = results.count { it.status == "TIMEOUT" }
        val unknown = results.count { it.status == "UNKNOWN" }

        println("\n=== Summary ===")
        println("Total tested: ${results.size}")
        println("✅ TCP Open: $open")
        println("🚫 Connection Refused: $refused")
        println("⏰ Timeout: $timeout")
        println("💥 Unknown Error: $unknown")

        // Save detailed results
        val reportFile = File("/tmp/quick-smoke-test-report.txt")
        reportFile.writeText(buildString {
            appendLine("=== Book Source Quick Smoke Test Report ===")
            appendLine("Total tested: ${results.size}")
            appendLine("✅ TCP Open: $open")
            appendLine("🚫 Connection Refused: $refused")
            appendLine("⏰ Timeout: $timeout")
            appendLine("💥 Unknown Error: $unknown")
            appendLine()
            appendLine("=== Detailed Results ===")
            results.forEach { r ->
                appendLine("${r.name} | ${r.url} | ${r.status} | ${r.message}")
            }
        })
        println("\nReport saved to ${reportFile.absolutePath}")

        // Save reachable sources
        val reachableFile = File("/tmp/reachable-novel-sources.txt")
        reachableFile.writeText(results.filter { it.status == "OPEN" }.joinToString("\n") { "${it.name} | ${it.url}" })
        println("Reachable sources saved to ${reachableFile.absolutePath}")
    }

    private fun quickTestUrl(name: String, url: String): SmokeResult {
        return try {
            // Handle URLs with non-ASCII characters by using IDN encoding
            val parsed = java.net.URL(url)
            val host = try {
                // Try to encode the host using IDN (internationalized domain names)
                java.net.IDN.toASCII(parsed.host)
            } catch (e: Exception) {
                // If IDN encoding fails (e.g., URL like "你觉得还有网址?"), treat as unknown
                return SmokeResult(name, url, "UNKNOWN", "Invalid host: ${e.message}")
            }
            val port = parsed.port.coerceAtLeast(if (parsed.protocol == "https") 443 else 80)

            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 3000)
            socket.close()

            SmokeResult(name, url, "OPEN", "TCP connection successful")
        } catch (e: java.net.ConnectException) {
            SmokeResult(name, url, "REFUSED", e.message ?: "connection refused")
        } catch (e: java.net.SocketTimeoutException) {
            SmokeResult(name, url, "TIMEOUT", e.message ?: "timeout")
        } catch (e: java.net.UnknownHostException) {
            SmokeResult(name, url, "ERROR", "Unknown host: ${e.message}")
        } catch (e: Exception) {
            SmokeResult(name, url, "UNKNOWN", e.message ?: "unknown error")
        }
    }
}
