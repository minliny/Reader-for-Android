package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderQuickActionOverlayStructureTest {

    private val overlaySource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/reader/components/ReaderQuickActionOverlay.kt")
            )
        )
    }

    @Test
    fun `quick action overlay provides three overlay composables`() {
        listOf("ReaderSearchOverlay", "ReaderAutoScrollOverlay", "ReaderReplaceOverlay").forEach { name ->
            assertTrue("Must expose $name", "fun $name(" in overlaySource)
        }
    }

    @Test
    fun `quick action overlays use reader theme tokens`() {
        listOf("ReaderTheme.colors", "ReaderTheme.spacing", "ReaderTheme.shapes", "ReaderTheme.typography").forEach { token ->
            assertTrue("Overlays must use $token", token in overlaySource)
        }
    }

    @Test
    fun `replace overlay shows current book rules only not global`() {
        assertTrue(
            "Replace overlay must show current book name context",
            "当前书籍" in overlaySource
        )
        assertTrue(
            "Replace overlay must state current-book-only rule",
            "仅显示当前书籍匹配到的替换规则" in overlaySource
        )
        // Must NOT show global rule library
        listOf("全局规则", "全局替换", "所有书籍").forEach { forbidden ->
            assertTrue(
                "Replace overlay must not show global '$forbidden'",
                forbidden !in overlaySource
            )
        }
    }

    @Test
    fun `search overlay has result navigation`() {
        listOf("上一个结果", "下一个结果", "onPrevResult", "onNextResult").forEach { token ->
            assertTrue("Search overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `search overlay supports query and result list`() {
        listOf("onQueryChange", "onClear", "SearchMatch", "results").forEach { token ->
            assertTrue("Search overlay must support $token", token in overlaySource)
        }
    }

    @Test
    fun `auto scroll overlay has speed and mode controls`() {
        listOf("AutoScrollSpeed", "AutoScrollMode", "onSpeedChange", "onModeChange").forEach { token ->
            assertTrue("AutoScroll overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `auto scroll overlay has play pause stop controls`() {
        listOf("onStart", "onPause", "onStop", "开始", "暂停", "停止").forEach { token ->
            assertTrue("AutoScroll overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `auto scroll mode has three options`() {
        listOf("Scroll", "PageFlip", "ContinuousScroll", "滚动", "点击翻页", "连续滚动").forEach { token ->
            assertTrue("AutoScroll mode must include $token", token in overlaySource)
        }
    }

    @Test
    fun `replace overlay has apply cancel restore buttons`() {
        listOf("onApply", "onCancel", "onRestore", "应用", "取消", "恢复原文").forEach { token ->
            assertTrue("Replace overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `replace overlay manages toggleable rules`() {
        listOf("ReplaceRule", "onRuleToggle", "enabled").forEach { token ->
            assertTrue("Replace overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `quick action overlays do not reintroduce stitch old tokens`() {
        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec", "#" + "eae1da", "#" + "f5ece6", "#" + "efe7e0", "#" + "8b5000",
            "Web" + "View",
            "normalized-" + "html"
        ).forEach { forbidden ->
            assertTrue(
                "Overlays must not reintroduce $forbidden",
                forbidden !in overlaySource
            )
        }
    }

    @Test
    fun `quick action overlays do not include night mode dialog`() {
        listOf("夜间模式" + "弹窗", "NightModeDialog", "AlertDialog", "Dialog(").forEach { forbidden ->
            assertTrue(
                "Quick action overlays must not include $forbidden",
                forbidden !in overlaySource
            )
        }
    }

    @Test
    fun `quick action overlays do not include global settings`() {
        listOf("WebDAV", "书源管理", "RSS", "全局设置").forEach { forbidden ->
            assertTrue(
                "Quick action overlays must not include $forbidden",
                forbidden !in overlaySource
            )
        }
    }
}
