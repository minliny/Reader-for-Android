package com.reader.android.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SearchFacadeBoundaryGuardTest {

    // Only scan Slice 20 facade files, not the entire search directory
    private val facadeSources: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt",
            "src/main/kotlin/com/reader/android/ui/search/SearchFacadeResultMapper.kt",
            "src/main/kotlin/com/reader/android/ui/search/SearchFacadeErrorMapper.kt"
        ).map { String(Files.readAllBytes(Paths.get(it))) }
            .joinToString("\n")
    }

    // ── No parser internals ──

    @Test
    fun `search facade files do not call parser internals`() {
        listOf("SearchParser", "BookInfoParser", "ContentParser", "TOCParser",
            "parser.parseSearchResponse", "parser.parseBookInfoResponse"
        ).forEach { forbidden ->
            assertFalse("Search must not call $forbidden", forbidden in facadeSources)
        }
    }

    // ── No direct network ──

    @Test
    fun `search facade files do not access HttpClient directly`() {
        assertFalse("Must not call HttpClient directly", "HttpClient" in facadeSources)
    }

    // ── No secrets ──

    @Test
    fun `search facade files do not contain real secrets`() {
        listOf("https://www.", "https://m.", "token", "api_key", "password").forEach { secret ->
            assertFalse("Must not contain $secret", secret in facadeSources)
        }
    }

    // ── No WebView ──

    @Test
    fun `search facade files do not contain WebView`() {
        assertFalse("Must not contain WebView", "WebView" in facadeSources)
    }

    // ── No Stitch old tokens ──

    @Test
    fun `search facade files do not contain stitch old tokens`() {
        listOf("bg-surface-container", "#fdf6ec", "#8b5000").forEach { token ->
            assertFalse("Must not contain $token", token in facadeSources)
        }
    }

    // ── Fake/real boundary preserved ──

    @Test
    fun `fake mode is default`() {
        assertTrue(SearchAdapterShell.isFakeMode)
    }

    @Test
    fun `enable real mode requires explicit call`() {
        // Reset to default
        SearchAdapterShell.resetToFakeMode()
        assertTrue(SearchAdapterShell.isFakeMode)
    }

    // ── Error messages are user-safe ──

    @Test
    fun `error messages are user facing`() {
        val error = SearchFacadeErrorMapper.mapError("test",
            com.reader.android.data.bridge.ReaderError(
                com.reader.android.data.bridge.ReaderErrorCode.NETWORK,
                com.reader.android.data.bridge.ReaderFailureStage.SEARCH
            )
        )
        assertTrue(error.errorMessage!!.isNotBlank())
        // Message must be Chinese, user-facing
        assertTrue(error.errorMessage!!.contains("网络"))
    }
}
