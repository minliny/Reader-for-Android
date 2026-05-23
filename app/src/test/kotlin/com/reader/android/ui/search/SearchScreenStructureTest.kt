package com.reader.android.ui.search

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SearchScreenStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/search/SearchScreen.kt")
            )
        )
    }

    @Test
    fun `search screen uses reader theme and components`() {
        listOf(
            "ReaderTheme",
            "ReaderAppTopBar",
            "ReaderSearchBox",
            "SearchResultItemCard"
        ).forEach { token ->
            assertTrue("SearchScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `search screen uses state components for all states`() {
        listOf("ReaderLoadingState", "ReaderEmptyState", "ReaderErrorState").forEach { token ->
            assertTrue("SearchScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `search screen preserves viewmodel fake real boundary`() {
        listOf("SearchViewModel", "useRealHttp", "FakeCoreBridge", "SearchParser", "HttpClient").forEach { token ->
            assertTrue("SearchScreen must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `search screen has accessibility semantics via components`() {
        listOf(
            "ReaderSearchBox",   // includes contentDescription internally
            "SearchResultItemCard", // includes contentDescription internally
            "ReaderErrorState",  // includes contentDescription internally
            "ReaderEmptyState"   // includes contentDescription internally
        ).forEach { token ->
            assertTrue("SearchScreen must use $token for semantics", token in screenSource)
        }
    }

    @Test
    fun `search screen does not reintroduce stitch old tokens`() {
        listOf(
            "bg-surface-container",
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md",
            "#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000",
            "MaterialTheme",
            "Scaffold",
            "TopAppBar",
            "CircularProgressIndicator",
            "WebView",
            "normalized-html"
        ).forEach { forbidden ->
            assertTrue(
                "SearchScreen must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }

    @Test
    fun `search screen handles all four ui states`() {
        listOf(
            "isSearching",     // loading state
            "error != null",   // error state
            "results.isEmpty", // empty state
            "results.isNotEmpty" // results state
        ).forEach { stateToken ->
            assertTrue("SearchScreen must handle state: $stateToken", stateToken in screenSource)
        }
    }

    @Test
    fun `search screen handles retry on error`() {
        listOf("onRetryClick", "viewModel.search()").forEach { token ->
            assertTrue("SearchScreen must provide $token", token in screenSource)
        }
    }
}
