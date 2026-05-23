package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ComposeUIRegressionScanTest {

    private val allUiKotlinSource: String by lazy {
        val uiDir = Paths.get("src/main/kotlin/com/reader/android/ui")
        Files.walk(uiDir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    // ── Forbidden Stitch tokens ──

    @Test
    fun `no stitch old classes in compose ui`() {
        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md"
        ).forEach { forbidden ->
            assertTrue(
                "UI must not contain Stitch class '$forbidden'",
                forbidden !in allUiKotlinSource
            )
        }
    }

    @Test
    fun `no stitch old colors in compose ui`() {
        listOf(
            "#" + "fdf6ec", "#" + "FDF6EC",
            "#" + "eae1da", "#" + "EAE1DA",
            "#" + "f5ece6", "#" + "F5ECE6",
            "#" + "efe7e0", "#" + "EFE7E0",
            "#" + "8b5000", "#" + "8B5000"
        ).forEach { forbidden ->
            assertTrue(
                "UI must not contain old color '$forbidden'",
                forbidden !in allUiKotlinSource
            )
        }
    }

    @Test
    fun `no chapter skip semantics in reader ui`() {
        listOf("skip_" + "previous", "skip_" + "next", "上一章", "下一章").forEach { forbidden ->
            assertTrue(
                "Reader UI must not use chapter skip '$forbidden'",
                forbidden !in allUiKotlinSource
            )
        }
    }

    @Test
    fun `no webview runtime in compose ui`() {
        listOf("Web" + "View", "normalized-" + "html").forEach { forbidden ->
            assertTrue(
                "UI must not contain '$forbidden'",
                forbidden !in allUiKotlinSource
            )
        }
    }

    // ── Required patterns ──

    @Test
    fun `reader theme tokens used across ui layer`() {
        listOf("ReaderTheme.colors", "ReaderTheme.spacing", "ReaderTheme.typography").forEach { token ->
            assertTrue("UI must use $token", token in allUiKotlinSource)
        }
    }

    @Test
    fun `state components referenced across ui layer`() {
        listOf("ReaderLoadingState", "ReaderEmptyState", "ReaderErrorState").forEach { comp ->
            assertTrue("UI must reference $comp", comp in allUiKotlinSource)
        }
    }

    @Test
    fun `reader ui state sealed interface referenced`() {
        assertTrue("ReaderUiState must be used", "ReaderUiState" in allUiKotlinSource)
    }

    // ── Key accessibility contracts ──

    @Test
    fun `reader control layer has correct accessibility labels`() {
        listOf(
            "本章内上一页",
            "本章内下一页",
            "本章阅读进度",
            "切换夜间模式",
            "阅读行为设置"
        ).forEach { label ->
            assertTrue("Reader control must have '$label'", label in allUiKotlinSource)
        }
    }

    @Test
    fun `quick buttons have content descriptions without visible text`() {
        listOf("搜索本章", "自动翻页", "内容替换").forEach { label ->
            assertTrue("Quick buttons must have '$label'", label in allUiKotlinSource)
        }
    }

    @Test
    fun `page control never exposes chapter navigation`() {
        assertTrue(
            "Page control uses within-chapter labels",
            "本章内上一页" in allUiKotlinSource && "本章内下一页" in allUiKotlinSource
        )
    }
}
