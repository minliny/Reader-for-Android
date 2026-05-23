package com.reader.android.ui.booksource

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SourceManagementUIStructureTest {

    private fun sourceOf(filename: String): String =
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/booksource/$filename")))

    // ── BookSourceScreen ──

    @Test
    fun `book source screen uses reader theme`() {
        val s = sourceOf("BookSourceScreen.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderEmptyState").forEach {
            assertTrue("BookSourceScreen must use $it", it in s)
        }
    }

    @Test
    fun `book source screen preserves viewmodel and repository`() {
        val s = sourceOf("BookSourceScreen.kt")
        listOf("SourceManagementViewModel", "BookSourceRepository", "FakeBookSourceRepository", "toggleEnabled", "delete").forEach {
            assertTrue("Must preserve $it", it in s)
        }
    }

    @Test
    fun `book source screen uses switch not checkbox`() {
        val s = sourceOf("BookSourceScreen.kt")
        assertTrue("Must use Switch", "Switch(" in s)
        assertTrue("Must not use Checkbox", "Checkbox(" !in s)
    }

    // ── SourceDetailScreen ──

    @Test
    fun `source detail screen uses reader components`() {
        val s = sourceOf("SourceDetailScreen.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderCard", "ReaderChip", "ReaderSectionHeader").forEach {
            assertTrue("SourceDetailScreen must use $it", it in s)
        }
    }

    @Test
    fun `source detail screen shows rule status`() {
        val s = sourceOf("SourceDetailScreen.kt")
        listOf("搜索规则", "目录规则", "正文规则", "SourceDetailData").forEach {
            assertTrue("Must have $it", it in s)
        }
    }

    // ── SourceEditScreen ──

    @Test
    fun `source edit screen has form fields and save`() {
        val s = sourceOf("SourceEditScreen.kt")
        listOf("书源名称", "书源 URL", "保存修改", "onSave").forEach {
            assertTrue("Must have $it", it in s)
        }
    }

    @Test
    fun `source edit screen uses reader components`() {
        val s = sourceOf("SourceEditScreen.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderPrimaryButton").forEach {
            assertTrue("Must use $it", it in s)
        }
    }

    // ── SourceImportScreen ──

    @Test
    fun `source import screen has import action`() {
        val s = sourceOf("SourceImportScreen.kt")
        listOf("导入书源", "选择导入方式", "onImportClick").forEach {
            assertTrue("Must have $it", it in s)
        }
    }

    @Test
    fun `source import screen uses reader components`() {
        val s = sourceOf("SourceImportScreen.kt")
        listOf("ReaderTheme", "ReaderAppTopBar", "ReaderPrimaryButton").forEach {
            assertTrue("Must use $it", it in s)
        }
    }

    // ── Shared regression ──

    @Test
    fun `source management screens do not reintroduce stitch old patterns`() {
        listOf("BookSourceScreen.kt", "SourceDetailScreen.kt", "SourceEditScreen.kt", "SourceImportScreen.kt").forEach { file ->
            val s = sourceOf(file)
            listOf(
                "bg-surface-container",
                "text-on-surface",
                "shadow-lg",
                "shadow-md",
                "#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000",
                "MaterialTheme.colorScheme",
                "Scaffold(",
                "TopAppBar(",
                "CardDefaults",
                "ExperimentalMaterial3Api",
                "WebView",
                "normalized-html"
            ).forEach { forbidden ->
                assertTrue("$file must not reintroduce $forbidden", forbidden !in s)
            }
        }
    }
}
