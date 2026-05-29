package com.reader.android.data.bridge

import com.reader.android.data.network.BookInfoParser
import com.reader.android.data.network.ContentParser
import com.reader.android.data.network.SearchParser
import com.reader.android.data.network.TOCParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Offline replay test: biquges123.com fixture replay without network access.
 * Validates Search -> Detail -> TOC -> Content pipeline using real captured HTML.
 * Does NOT access the network.
 */
class Biquges123OfflineReplayTest {

    private val fixturesRoot: File = File(
        "src/test/resources/fixtures/real-source/xingxingxsw-manual"
    ).normalize().absoluteFile

    private val manifest: Map<String, String?> by lazy {
        File(fixturesRoot, "manifest.json").let { file ->
            val json = file.readText()
            val sourceId: String? = extract(json, "sourceId")
            val sourceName: String? = extract(json, "sourceName")
            val query: String? = extract(json, "query")
            val charset: String? = extract(json, "charset") ?: "UTF-8"
            mapOf("sourceId" to sourceId, "sourceName" to sourceName, "query" to query, "charset" to charset)
        }
    }

    private fun extract(json: String, key: String): String? {
        val pattern = java.util.regex.Pattern.compile(""""$key"\s*:\s*"([^"]*)"""")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun readHtml(relativePath: String): String {
        val file = File(fixturesRoot, relativePath)
        assertTrue("File not found: ${file.absolutePath}", file.exists())
        assertTrue("File too small: ${file.absolutePath}", file.length() > 100)
        return file.readText()
    }

    // ===== Search replay =====

    @Test
    fun `search replay finds fanren xiuxian zhuan in results`() {
        val html = readHtml("search/search.html")
        val parser = SearchParser()
        val results = parser.parseSearchResponse(html, manifest["sourceName"] ?: "xin biquge")

        assertTrue("Search should return results for query '${manifest["query"] ?: ""}'", results.isNotEmpty())

        val fanrenResult = results.find { it.name.contains("凡人修仙传") && !it.name.contains("仙界篇") }
        assertNotNull("fanren xiuxian zhuan should be found in search results", fanrenResult)
        assertEquals("凡人修仙传", fanrenResult!!.name)
        assertEquals("忘语", fanrenResult.author)
        assertNotNull("detailUrl should not be null", fanrenResult.detailUrl)
        assertTrue("detailUrl should start with /", fanrenResult.detailUrl!!.startsWith("/"))
        println("SEARCH OK: found '${fanrenResult.name}' by ${fanrenResult.author}, detailUrl=${fanrenResult.detailUrl}")
        println("Total search results: ${results.size}")
        results.take(3).forEach { println("  - ${it.name} by ${it.author}") }
    }

    // ===== Detail replay =====

    @Test
    fun `detail replay extracts book info`() {
        val html = readHtml("detail/detail.html")
        val parser = BookInfoParser()
        val bookInfo = parser.parseBookInfoResponse(html, manifest["sourceName"] ?: "xin biquge")

        assertNotNull("BookInfo should not be null", bookInfo)
        assertNotNull("Book name should not be null", bookInfo!!.name)
        println("DETAIL OK: name=${bookInfo.name}, author=${bookInfo.author}")
        bookInfo.intro?.let { println("  intro (first 100 chars): ${it.take(100)}") }
    }

    // ===== TOC replay =====

    @Test
    fun `toc replay extracts chapter list with first chapter`() {
        val html = readHtml("toc/toc.html")
        val parser = TOCParser()
        val chapters = parser.parseTOCResponse(html)

        assertTrue("TOC should return chapters", chapters.isNotEmpty())

        val allChapters = chapters.flatMap { it.children }

        // TOCParser returns ALL <a> links found in HTML, including nav links that appear before
        // the actual chapter list. We need to find the first chapter whose URL looks like a
        // chapter URL (contains /number pattern) and whose title contains chapter markers.
        val firstChapter = allChapters.firstOrNull {
            (it.url.contains("/34811/") || it.url.matches(""".*/\d+$""".toRegex())) &&
            (it.title.contains("章") || it.title.contains("第"))
        }

        assertNotNull("First chapter should be found in TOC (actual entries: ${allChapters.size})", firstChapter)
        assertTrue("First chapter should contain 'di yi zhang' and 'shan bian xiao cun' but was '${firstChapter?.title}'",
            firstChapter?.title?.contains("第一章") == true && firstChapter.title.contains("山边小村"))
        assertTrue("First chapter URL should be /34811/1 but was '${firstChapter?.url}'",
            firstChapter?.url == "/34811/1" || firstChapter?.url?.endsWith("/34811/1") == true)

        println("TOC OK: first chapter='${firstChapter?.title}', url=${firstChapter?.url}")
        val totalChapters = chapters.sumOf { it.children.size }
        println("  Total chapters in TOC: $totalChapters")
        chapters.take(2).forEach { vol ->
            println("  Volume: ${vol.title} (${vol.children.size} chapters)")
        }
    }

    // ===== Content replay =====

    @Test
    fun `content replay extracts content with han li`() {
        val html = readHtml("content/chapter-001.html")
        val parser = ContentParser()
        val page = parser.parseContentResponse(html)

        assertNotNull("ContentPage should not be null", page)
        assertNotNull("Content text should not be null", page!!.content)
        assertTrue("Content should contain 'han li' but was: ${page.content.take(200)}",
            page.content.contains("韩立"))
        assertTrue("Content should contain 'er leng zi' (childhood name)",
            page.content.contains("二愣子") || page.content.contains("二愣"))
        println("CONTENT OK: title='${page.title}', content length=${page.content.length}")
        println("  First 200 chars: ${page.content.take(200).replace("\n", " ")}")
    }

    // ===== Error model replay =====

    @Test
    fun `error model empty HTML returns null content`() {
        val parser = ContentParser()
        val page = parser.parseContentResponse("<html><body><p>No content</p></body></html>")
        assertTrue("Empty content page should be null", page == null)
        println("ERROR MODEL OK: empty HTML -> null content page (no crash)")
    }

    @Test
    fun `error model TOCParser on empty HTML returns empty list`() {
        val parser = TOCParser()
        val chapters = parser.parseTOCResponse("")
        assertTrue("Empty HTML -> empty chapter list", chapters.isEmpty())
        println("ERROR MODEL OK: empty HTML -> empty TOC (no crash)")
    }

    @Test
    fun `error model SearchParser on empty HTML returns empty list`() {
        val parser = SearchParser()
        val results = parser.parseSearchResponse("", "test")
        assertTrue("Empty HTML -> empty results", results.isEmpty())
        println("ERROR MODEL OK: empty HTML -> empty results (no crash)")
    }
}