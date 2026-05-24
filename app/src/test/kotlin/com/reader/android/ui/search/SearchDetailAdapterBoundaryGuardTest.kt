package com.reader.android.ui.search

import com.reader.android.ui.detail.BookDetailAdapterShell
import com.reader.android.ui.detail.BookDetailFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SearchDetailAdapterBoundaryGuardTest {

    // ── Fake/real boundary ──

    @Test
    fun `search adapter shell default mode is fake`() {
        assertTrue(SearchAdapterShell.isFakeMode)
    }

    @Test
    fun `book detail adapter shell default mode is fake`() {
        assertTrue(BookDetailAdapterShell.isFakeMode)
    }

    @Test
    fun `search adapter shell integration level is NEEDS_ADAPTER`() {
        assertEquals("NEEDS_ADAPTER", SearchAdapterShell.integrationLevel)
    }

    @Test
    fun `book detail adapter shell integration level is NEEDS_ADAPTER`() {
        assertEquals("NEEDS_ADAPTER", BookDetailAdapterShell.integrationLevel)
    }

    // ── No network access ──

    @Test
    fun `search adapter shell calls core bridge through public facade only`() {
        val searchShellSource = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt"
        )))
        assertFalse("Must not call HttpClient", "HttpClient" in searchShellSource)
        assertFalse("Must not call SearchParser", "SearchParser" in searchShellSource)
        // bridge.search() is the public CoreBridge facade — allowed
        assertTrue("Must call CoreBridge.search public facade",
            "bridge.search" in searchShellSource)
    }

    @Test
    fun `book detail adapter shell does not call reader core bridge or parser`() {
        val detailShellSource = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt"
        )))
        assertFalse("Must not call HttpClient",
            "HttpClient" in detailShellSource)
        assertFalse("Must not call BookInfoParser",
            "BookInfoParser" in detailShellSource)
        assertFalse("Must not call bridge.getBookInfo",
            "bridge.getBookInfo" in detailShellSource)
    }

    // ── No secrets leaked ──

    @Test
    fun `search adapter shell does not contain real secrets`() {
        val searchShellSource = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt"
        )))
        listOf("http://", "https://", "token", "password", "api_key").forEach { secret ->
            assertFalse("Search adapter shell must not contain $secret",
                secret in searchShellSource)
        }
    }

    @Test
    fun `book detail adapter shell does not contain real secrets`() {
        val detailShellSource = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt"
        )))
        listOf("http://", "https://", "token", "password", "api_key").forEach { secret ->
            assertFalse("BookDetail adapter shell must not contain $secret",
                secret in detailShellSource)
        }
    }

    // ── Regression guards ──

    @Test
    fun `no webview runtime in search or detail`() {
        val searchDir = Paths.get("src/main/kotlin/com/reader/android/ui/search")
        val detailDir = Paths.get("src/main/kotlin/com/reader/android/ui/detail")
        val combined = (Files.walk(searchDir).toList() + Files.walk(detailDir).toList())
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .joinToString("\n") { String(Files.readAllBytes(it)) }
        assertFalse("Must not contain WebView", "WebView" in combined)
        assertFalse("Must not contain normalized-html", "normalized-html" in combined)
    }

    @Test
    fun `no stitch old tokens in search or detail`() {
        val searchDir = Paths.get("src/main/kotlin/com/reader/android/ui/search")
        val detailDir = Paths.get("src/main/kotlin/com/reader/android/ui/detail")
        val combined = (Files.walk(searchDir).toList() + Files.walk(detailDir).toList())
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .joinToString("\n") { String(Files.readAllBytes(it)) }
        listOf("bg-surface-container", "text-on-surface", "#fdf6ec", "#8b5000").forEach { token ->
            assertFalse("Must not contain stitch token $token", token in combined)
        }
    }

    // ── Fixture data validation ──

    @Test
    fun `search fixture results have distinct ids`() {
        val ids = SearchFixture.results.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `book detail fixture has valid toc preview`() {
        val toc = BookDetailFixture.tocPreview
        assertTrue(toc.chapterCount > 0)
        assertTrue(toc.firstChapterTitle.isNotBlank())
        assertTrue(toc.latestChapterTitle.isNotBlank())
    }
}
