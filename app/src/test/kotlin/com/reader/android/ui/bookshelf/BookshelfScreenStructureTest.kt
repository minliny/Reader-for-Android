package com.reader.android.ui.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class BookshelfScreenStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfScreen.kt")
            )
        )
    }

    @Test
    fun `bookshelf screen uses reader theme`() {
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderEmptyState").forEach { token ->
            assertTrue("BookshelfScreen must use $token", token in screenSource)
        }
    }

    @Test
    fun `bookshelf screen preserves onSearchClick callback`() {
        assertTrue(
            "BookshelfScreen must expose onSearchClick callback",
            "onSearchClick" in screenSource
        )
    }

    @Test
    fun `bookshelf screen has accessibility semantics`() {
        listOf("contentDescription", "onSearchClick", "搜索").forEach { token ->
            assertTrue("BookshelfScreen must provide $token", token in screenSource)
        }
    }

    @Test
    fun `bookshelf screen does not reintroduce stitch old tokens`() {
        listOf(
            "bg-surface-container",
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md",
            "#fdf6ec",
            "#eae1da",
            "#f5ece6",
            "#efe7e0",
            "#8b5000",
            "MaterialTheme",
            "TopAppBar",
            "Scaffold",
            "WebView",
            "normalized-html"
        ).forEach { forbidden ->
            assertTrue(
                "BookshelfScreen must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }

    @Test
    fun `bookshelf screen empty state provides user guidance`() {
        listOf("书架为空", "点击右下角按钮搜索书籍").forEach { text ->
            assertTrue("BookshelfScreen empty state must include '$text'", text in screenSource)
        }
    }
}
