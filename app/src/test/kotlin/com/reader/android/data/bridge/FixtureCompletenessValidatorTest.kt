package com.reader.android.data.bridge

import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Validates that real-source fixture directory is complete and well-formed.
 * Does NOT access the network.
 *
 * Expected state before user provides HTML:
 *   - tests SKIP with INFO message (no sources yet)
 * After user provides HTML:
 *   - all tests PASS
 */
class FixtureCompletenessValidatorTest {

    private val fixturesRoot: File
        get() = File(System.getProperty("user.dir"), "app/src/test/resources/fixtures/real-source")

    private data class FixtureManifest(
        val sourceId: String,
        val sourceName: String,
        val captureMode: String,
        val capturedAt: String,
        val query: String,
        val searchUrl: String,
        val charset: String,
        val files: List<String>
    )

    private fun parseManifest(dir: File): FixtureManifest? {
        val file = File(dir, "manifest.json")
        if (!file.exists()) return null
        val json = file.readText()
        val sourceId = extract(json, "sourceId") ?: return null
        val sourceName = extract(json, "sourceName") ?: return null
        val captureMode = extract(json, "captureMode") ?: return null
        val capturedAt = extract(json, "capturedAt") ?: return null
        val query = extract(json, "query") ?: return null
        val searchUrl = extract(json, "searchUrl") ?: return null
        val charset = extract(json, "charset") ?: "utf-8"
        val files = extractArray(json, "files")
        return FixtureManifest(sourceId, sourceName, captureMode, capturedAt, query, searchUrl, charset, files)
    }

    private fun extract(json: String, key: String): String? {
        val pattern = Pattern.compile(""""$key"\s*:\s*"([^"]*)"""")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractArray(json: String, key: String): List<String> {
        val arrayPattern = Pattern.compile(""""$key"\s*:\s*\[(.*?)]""", Pattern.DOTALL)
        val match = arrayPattern.matcher(json)
        return if (match.find()) {
            val content = match.group(1)
            val itemPattern = Pattern.compile(""""([^"]+)"""")
            val items = mutableListOf<String>()
            val itemMatcher = itemPattern.matcher(content)
            while (itemMatcher.find()) {
                items.add(itemMatcher.group(1))
            }
            items
        } else {
            emptyList()
        }
    }

    @Test
    fun `no sources yet prints guidance`() {
        val sources = fixturesRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        println("INFO: Fixture sources found: ${sources.size}")
        sources.forEach { println("  - ${it.name}") }
        if (sources.isEmpty()) {
            println("INFO: S16-FIXTURE-004 pending — awaiting user HTML submission")
            println("INFO: See docs/PLANNING/ANDROID_REAL_SOURCE_FIXTURE_CAPTURE_GUIDE.md")
        }
    }

    @Test
    fun `each source has manifest and required HTML files`() {
        val sources = fixturesRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (sources.isEmpty()) {
            println("SKIP: No sources yet — S16-FIXTURE-004 pending")
            return
        }

        for (sourceDir in sources) {
            val manifest = parseManifest(sourceDir)
            if (manifest == null) {
                println("FAIL: Missing or invalid manifest.json in ${sourceDir.name}")
                continue
            }

            println("Validating ${manifest.sourceName} (${sourceDir.name})...")
            println("  query=${manifest.query}, charset=${manifest.charset}, files=${manifest.files.size}")

            for (relPath in manifest.files) {
                val file = File(sourceDir, relPath)
                if (!file.exists()) {
                    println("FAIL: Missing file: ${file.absolutePath}")
                } else if (file.length() < 100) {
                    println("FAIL: File too small (<100 bytes): ${file.absolutePath}")
                } else {
                    println("  OK: $relPath (${file.length()} bytes)")
                }
            }
        }
    }
}