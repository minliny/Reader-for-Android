package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderBottomFunctionOverlayStructureTest {

    private val overlaySource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/reader/components/ReaderBottomFunctionOverlay.kt")
            )
        )
    }

    @Test
    fun `bottom function overlay provides four overlay composables`() {
        listOf(
            "ReaderDirectoryOverlay",
            "ReaderTtsOverlay",
            "ReaderAppearanceOverlay",
            "ReaderSettingsOverlay"
        ).forEach { name ->
            assertTrue("Must expose $name", "fun $name(" in overlaySource)
        }
    }

    @Test
    fun `bottom function overlays use reader theme tokens`() {
        listOf("ReaderTheme.colors", "ReaderTheme.spacing", "ReaderTheme.shapes").forEach { token ->
            assertTrue("Overlays must use $token", token in overlaySource)
        }
    }

    @Test
    fun `settings overlay is reading behavior only not global`() {
        assertTrue(
            "Settings overlay must show 阅读行为",
            "阅读行为" in overlaySource
        )
        listOf("WebDAV", "书源", "RSS", "全局设置", "book source").forEach { forbidden ->
            assertTrue(
                "Settings overlay must not include '$forbidden'",
                forbidden !in overlaySource
            )
        }
    }

    @Test
    fun `settings overlay supports reading behavior items and switches`() {
        listOf(
            "AppSettingItem",     // data class for text-value items
            "AppSwitchItem",      // data class for switch items
            "items",              // items parameter
            "switches"            // switches parameter
        ).forEach { token ->
            assertTrue("Settings overlay must support $token", token in overlaySource)
        }
    }

    @Test
    fun `settings overlay has switch toggles`() {
        listOf("AppSwitchItem", "onSwitchChange").forEach { token ->
            assertTrue("Settings overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `directory overlay has toc and bookmark tabs`() {
        listOf("目录", "书签", "TocEntry", "tocEntries", "onTocEntryClick").forEach { token ->
            assertTrue("Directory overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `directory overlay has current reading indicator`() {
        listOf("isCurrent", "ReaderIconToken.CurrentLocation", "当前阅读位置", "当前阅读章节").forEach { token ->
            assertTrue("Directory overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `directory overlay has bookmark and level support`() {
        listOf("hasBookmark", "level").forEach { token ->
            assertTrue("Directory overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `tts overlay has no chapter navigation semantics`() {
        listOf("上一章", "下一章", "skip_" + "previous", "skip_" + "next", "onPrevChapter", "onNextChapter").forEach { forbidden ->
            assertTrue(
                "TTS overlay must not use '$forbidden'",
                forbidden !in overlaySource
            )
        }
    }

    @Test
    fun `tts overlay has playback controls and parameters`() {
        listOf(
            "isPlaying", "onPause", "onStop",
            "语速", "音量", "定时关闭", "朗读音色",
            "onSpeedChange", "onVolumeChange",
            "currentTime", "totalTime", "progress"
        ).forEach { token ->
            assertTrue("TTS overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `appearance overlay has three visual groups`() {
        listOf("文字", "段落", "界面", "字体", "字号", "字距", "繁简", "缩进", "行距", "段距", "翻页动画", "主题").forEach { token ->
            assertTrue("Appearance overlay must have $token", token in overlaySource)
        }
    }

    @Test
    fun `bottom function overlays do not reintroduce stitch old tokens`() {
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
}
