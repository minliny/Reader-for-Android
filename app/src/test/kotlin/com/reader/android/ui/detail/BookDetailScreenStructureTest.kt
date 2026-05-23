package com.reader.android.ui.detail

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class BookDetailScreenStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/detail/BookDetailScreen.kt")
            )
        )
    }

    @Test
    fun `book detail screen uses reader theme and components`() {
        listOf(
            "ReaderTheme",
            "ReaderAppTopBar",
            "ReaderLoadingState",
            "ReaderCard",
            "ReaderPrimaryButton"
        ).forEach { token ->
            assertTrue("BookDetailScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `book detail screen preserves viewmodel fake real boundary`() {
        listOf(
            "BookDetailViewModel",
            "useRealHttp",
            "FakeCoreBridge",
            "BookInfoParser",
            "HttpClient"
        ).forEach { token ->
            assertTrue("BookDetailScreen must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `book detail screen preserves callback interface`() {
        listOf("onBack", "onTOC", "detailUrl").forEach { token ->
            assertTrue("BookDetailScreen must expose $token", token in screenSource)
        }
    }

    @Test
    fun `book detail screen has accessibility via components`() {
        listOf(
            "contentDescription",
            "onNavigateBack",
            "查看目录"
        ).forEach { token ->
            assertTrue("BookDetailScreen must provide $token", token in screenSource)
        }
    }

    @Test
    fun `book detail screen handles loading and content states`() {
        listOf("isLoading", "bookInfo != null", "ReaderLoadingState").forEach { token ->
            assertTrue("BookDetailScreen must handle state: $token", token in screenSource)
        }
    }

    @Test
    fun `book detail screen displays book metadata fields`() {
        listOf(
            "author",
            "kind",
            "wordCount",
            "latestChapter",
            "updateTime",
            "intro",
            "tocUrl"
        ).forEach { field ->
            assertTrue("BookDetailScreen must display $field", field in screenSource)
        }
    }

    @Test
    fun `book detail screen does not reintroduce stitch old tokens`() {
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
            "CardDefaults",
            "ExperimentalMaterial3Api",
            "WebView",
            "normalized-html"
        ).forEach { forbidden ->
            assertTrue(
                "BookDetailScreen must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }
}
