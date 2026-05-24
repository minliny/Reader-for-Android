package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlBaselineRegressionTest {

    private val allReaderSources: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui/reader")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    private val allSources: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    // ── Reader bottom bar labels ──

    @Test
    fun `reader bottom bar remains correct labels`() {
        listOf("目录", "朗读", "界面设置", "阅读行为设置").forEach { label ->
            assertTrue("Reader bottom bar must keep '$label'", label in allReaderSources)
        }
    }

    // ── Quick action buttons ──

    @Test
    fun `quick actions have no visible text labels`() {
        val quickCircleSection = allReaderSources.substringAfter("private fun ReaderQuickCircle")
            .take(600)
        assertFalse("Quick circle must not contain Text composable",
            "Text(" in quickCircleSection)
    }

    @Test
    fun `quick actions have content descriptions`() {
        listOf("搜索本章", "自动翻页", "内容替换", "切换夜间模式").forEach { label ->
            assertTrue("Quick action must have contentDescription '$label'", label in allReaderSources)
        }
    }

    // ── Page control semantics ──

    @Test
    fun `page control uses within chapter semantics`() {
        listOf("本章内上一页", "本章内下一页", "本章阅读进度").forEach { label ->
            assertTrue("Page control must use '$label'", label in allReaderSources)
        }
    }

    @Test
    fun `page control never uses chapter skip semantics`() {
        listOf("上一章", "下一章", "skip_previous", "skip_next").forEach { forbidden ->
            assertFalse("Page control must not use '$forbidden'", forbidden in allReaderSources)
        }
    }

    // ── Night mode ──

    @Test
    fun `night mode is quick button not dialog`() {
        listOf("DarkMode", "onNightModeClick").forEach { token ->
            assertTrue("Night mode must be quick button: $token", token in allReaderSources)
        }
        assertFalse("Night mode must not be Dialog", "Dialog" in allReaderSources)
        assertFalse("Night mode must not be AlertDialog", "AlertDialog" in allReaderSources)
    }

    // ── Settings overlay is reading behavior only ──

    @Test
    fun `settings overlay does not include global modules`() {
        val settingsSection = allReaderSources.substringAfter("ReaderSettingsOverlay").take(5000)
        listOf("WebDAV", "RSS", "关于", "备份", "书源管理", "全局设置").forEach { forbidden ->
            assertFalse("Settings must not contain '$forbidden'", forbidden in settingsSection)
        }
    }

    @Test
    fun `settings overlay shows reading behavior label`() {
        assertTrue("Settings overlay must show 阅读行为", "阅读行为" in allReaderSources)
    }

    // ── App main tab bar ──

    @Test
    fun `app main tab bar remains correct four tabs`() {
        val navSource = String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/AppNavigation.kt"
        )))
        listOf("书架", "发现", "书源", "我的").forEach { tab ->
            assertTrue("App main tab bar must have '$tab'", tab in navSource)
        }
        listOf("设置", "WebDAV", "RSS").forEach { forbidden ->
            assertFalse("App main tab bar must not have '$forbidden'",
                forbidden in navSource.split("appScreens").first())
        }
    }

    // ── Replace overlay is current book only ──

    @Test
    fun `replace overlay shows current book rules only`() {
        assertTrue("Replace must show current book name", "当前书籍" in allReaderSources)
        assertTrue("Replace must state current-book-only",
            "仅显示当前书籍匹配到的替换规则" in allReaderSources)
    }

    // ── No Stitch old tokens ──

    @Test
    fun `no stitch old color tokens`() {
        listOf(
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md"
        ).forEach { forbidden ->
            assertFalse("Must not reintroduce $forbidden", forbidden in allReaderSources)
        }
    }

    @Test
    fun `no stitch hardcoded hex colors`() {
        listOf("#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000").forEach { hex ->
            assertFalse("Must not reintroduce $hex", hex in allReaderSources)
        }
    }

    // ── No WebView runtime ──

    @Test
    fun `no webview runtime`() {
        val combined = allSources
        assertFalse("Must not contain WebView", "WebView" in combined)
        assertFalse("Must not contain normalized-html", "normalized-html" in combined)
    }

    // ── Brightness dock enum ──

    @Test
    fun `brightness dock enum has left and right`() {
        assertTrue("Must have BrightnessDock enum", "enum class BrightnessDock" in allReaderSources)
        listOf("Left", "Right").forEach { dock ->
            assertTrue("BrightnessDock must include $dock", dock in allReaderSources)
        }
    }

    // ── Brightness includes value and change callback ──

    @Test
    fun `brightness has value and change callback`() {
        assertTrue("Must accept brightnessValue",
            "brightnessValue:" in allReaderSources)
        assertTrue("Must accept onBrightnessChange",
            "onBrightnessChange:" in allReaderSources)
    }

    // ── Callback slots ──

    @Test
    fun `reader control base exposes all callback slots`() {
        val expectedCallbacks = listOf(
            "onBackClick",
            "onRefreshClick",
            "onSourceChangeClick",
            "onMoreClick",
            "onSearchClick",
            "onAutoScrollClick",
            "onReplaceClick",
            "onNightModeClick",
            "onPrevPageClick",
            "onNextPageClick",
            "onDirectoryClick",
            "onTtsClick",
            "onAppearanceClick",
            "onSettingsClick",
            "onBrightnessChange"
        )
        expectedCallbacks.forEach { callback ->
            assertTrue("ReaderControlBase must expose $callback", callback in allReaderSources)
        }
    }

    // ── TocEntry now includes progress ──

    @Test
    fun `toc entry data class has all required fields`() {
        val tocSection = allReaderSources.substringAfter("data class TocEntry(").take(300)
        listOf("title", "level", "isCurrent", "hasBookmark", "progress").forEach { field ->
            assertTrue("TocEntry must have $field", field in tocSection)
        }
    }
}
