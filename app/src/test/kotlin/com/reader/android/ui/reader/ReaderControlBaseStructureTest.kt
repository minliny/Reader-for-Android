package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlBaseStructureTest {

    private val baseSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt")
            )
        )
    }

    @Test
    fun `reader control base uses reader theme tokens`() {
        listOf(
            "ReaderTheme.colors",
            "ReaderTheme.spacing",
            "ReaderTheme.shapes",
            "ReaderTheme.typography"
        ).forEach { token ->
            assertTrue("ReaderControlBase must use $token", token in baseSource)
        }
    }

    @Test
    fun `reader control base bottom bar uses correct height 68dp`() {
        assertTrue(
            "Bottom bar must use bottomBarHeight token",
            "bottomBarHeight" in baseSource
        )
    }

    @Test
    fun `reader control base quick buttons have no visible text labels`() {
        // ReaderQuickCircle must NOT contain Text composable — icons only
        val quickCircleStart = baseSource.indexOf("private fun ReaderQuickCircle")
        val nextFun = baseSource.indexOf("private fun", quickCircleStart + 1)
        val quickCircleBody = if (nextFun > 0) baseSource.substring(quickCircleStart, nextFun) else baseSource.substring(quickCircleStart)

        assertTrue(
            "Quick circle buttons must not have visible text labels",
            "Text(" !in quickCircleBody
        )
    }

    @Test
    fun `reader control base quick buttons have content description`() {
        listOf(
            "搜索本章",
            "自动翻页",
            "内容替换",
            "切换夜间模式"
        ).forEach { label ->
            assertTrue("Quick button must have contentDescription '$label'", label in baseSource)
        }
    }

    @Test
    fun `reader control base page control uses within chapter semantics`() {
        listOf("本章内上一页", "本章内下一页", "本章阅读进度").forEach { label ->
            assertTrue("Page control must use '$label'", label in baseSource)
        }
    }

    @Test
    fun `reader control base page control never uses chapter skip semantics`() {
        listOf("上一章", "下一章", "skip_previous", "skip_next").forEach { forbidden ->
            assertTrue(
                "Page control must not use '$forbidden'",
                forbidden !in baseSource
            )
        }
    }

    @Test
    fun `reader control base night mode is quick button not dialog`() {
        // Night mode is a quick action toggle, not an overlay/dialog
        listOf("DarkMode", "onNightModeClick").forEach { token ->
            assertTrue("Night mode must be quick button: $token", token in baseSource)
        }
        assertTrue("Night mode must not be dialog", "Dialog" !in baseSource)
        assertTrue("Night mode must not be AlertDialog", "AlertDialog" !in baseSource)
    }

    @Test
    fun `reader control base bottom bar has four labeled items`() {
        listOf("目录", "朗读", "界面设置", "阅读行为设置").forEach { label ->
            assertTrue("Bottom bar must have label '$label'", label in baseSource)
        }
    }

    @Test
    fun `reader control base bottom bar settings is reading behavior only`() {
        assertTrue(
            "Bottom bar settings must be reading behavior, not global",
            "阅读行为设置" in baseSource
        )
        listOf("全局设置", "WebDAV", "书源", "RSS").forEach { forbidden ->
            assertTrue(
                "Bottom bar must not include global setting '$forbidden'",
                forbidden !in baseSource
            )
        }
    }

    @Test
    fun `reader control base exposes brightness dock enum`() {
        assertTrue("BrightnessDock must exist", "enum class BrightnessDock" in baseSource)
        listOf("Left", "Right").forEach { dock ->
            assertTrue("BrightnessDock must include $dock", dock in baseSource)
        }
    }

    @Test
    fun `reader control base exposes all 16 callback slots`() {
        listOf(
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
            "onSettingsClick"
        ).forEach { callback ->
            assertTrue("ReaderControlBase must expose $callback", callback in baseSource)
        }
    }

    @Test
    fun `reader control base has brightness arrow direction logic`() {
        listOf("BrightnessDock.Left", "ChevronRight", "ChevronLeft", "移动亮度条").forEach { token ->
            assertTrue("Brightness must handle $token", token in baseSource)
        }
    }

    @Test
    fun `reader control base does not reintroduce stitch old tokens`() {
        listOf(
            "bg-surface-container",
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md",
            "#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000",
            "WebView",
            "normalized-html"
        ).forEach { forbidden ->
            assertTrue(
                "ReaderControlBase must not reintroduce $forbidden",
                forbidden !in baseSource
            )
        }
    }
}
