package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderShellStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt")))
    }

    private val tocBookmarkScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignScreen.kt")))
    }

    private val tocBookmarkStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignUiState.kt")))
    }

    private val appearanceScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingAppearanceDesignScreen.kt")))
    }

    private val appearanceStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingAppearanceDesignUiState.kt")))
    }

    private val aloudScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingAloudDesignScreen.kt")))
    }

    private val aloudStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingAloudDesignUiState.kt")))
    }

    @Test
    fun `reader shell compose previews expose reading entry and immersive state matrices`() {
        listOf(
            "ReadingEntryDefaultPreview",
            "ReadingEntryLoadingPreview",
            "ReadingEntryErrorPreview",
            "ReadingEntryOfflinePreview",
            "ImmersiveReadingDefaultPreview",
            "ImmersiveReadingLoadingPreview",
            "ImmersiveReadingErrorPreview",
            "ImmersiveReadingOfflinePreview",
            "ReadingTocBookmarkDefaultPreview",
            "ReadingTocBookmarkBookmarkPreview",
            "ReadingTocBookmarkSearchPreview",
            "ReadingTocBookmarkEmptyPreview",
            "ReadingTocBookmarkLoadingPreview",
            "ReadingTocBookmarkErrorPreview",
            "ReadingTocBookmarkMoreMenuPreview",
            "ReadingAppearanceDefaultPreview",
            "ReadingAppearanceFontPreview",
            "ReadingAppearanceThemePreview",
            "ReadingAppearanceEditPreview",
            "ReadingAppearanceLoadingPreview",
            "ReadingAppearanceErrorPreview",
            "ReadingAloudDefaultPreview",
            "ReadingAloudRunningPreview",
            "ReadingAloudPausedPreview",
            "ReadingAloudErrorPreview"
        ).forEach { token ->
            assertTrue("Reader shell preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `reader shell previews use design state mappers`() {
        listOf(
            "ReadingEntryMapper.fromFixture",
            "ReadingEntryMapper.loading",
            "ReadingEntryMapper.error",
            "ReadingEntryMapper.offline",
            "ImmersiveReadingMapper.fromFixture",
            "ImmersiveReadingMapper.loading",
            "ImmersiveReadingMapper.error",
            "ImmersiveReadingMapper.offline",
            "ReadingTocBookmarkMapper.fromFixture",
            "ReadingTocBookmarkMapper.bookmark",
            "ReadingTocBookmarkMapper.search",
            "ReadingTocBookmarkMapper.empty",
            "ReadingTocBookmarkMapper.loading",
            "ReadingTocBookmarkMapper.error",
            "ReadingTocBookmarkMapper.moreMenu",
            "ReadingAppearanceMapper.fromFixture",
            "ReadingAppearanceMapper.font",
            "ReadingAppearanceMapper.theme",
            "ReadingAppearanceMapper.edit",
            "ReadingAppearanceMapper.loading",
            "ReadingAppearanceMapper.error",
            "ReadingAloudMapper.fromFixture",
            "ReadingAloudMapper.running",
            "ReadingAloudMapper.paused",
            "ReadingAloudMapper.error"
        ).forEach { token ->
            assertTrue("Reader shell preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `toc bookmark screen keeps readershell slots separate`() {
        listOf(
            "ReadingTocBookmarkScreen",
            "TocBookmarkReadingSurface",
            "TocBookmarkOverlay",
            "TocBookmarkBottomSheet",
            "TocBookmarkModuleNav",
            "TocBookmarkBrightnessPanel",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Toc bookmark screen source must include $token", token in tocBookmarkScreenSource)
        }
    }

    @Test
    fun `toc bookmark state model keeps frontend input contract text`() {
        listOf(
            "ReadingTocBookmarkDisplayState",
            "ReadingTocBookmarkMapper",
            "fun bookmark()",
            "fun search()",
            "fun empty()",
            "fun loading()",
            "fun error()",
            "fun moreMenu()",
            "当前 · 38%",
            "暂无书签",
            "加载失败，请重试",
            "已保留当前阅读位置",
            "缓存当前卷",
            "只看未读"
        ).forEach { token ->
            assertTrue("Toc bookmark state source must include $token", token in tocBookmarkStateSource)
        }
    }

    @Test
    fun `appearance screen keeps readershell slots and visible appearance controls`() {
        listOf(
            "ReadingAppearanceScreen",
            "AppearanceReadingSurface",
            "AppearanceOverlay",
            "AppearanceBottomSheet",
            "AppearanceModuleNav",
            "AppearanceBrightnessPanel",
            "AppearanceFontStepper",
            "AppearanceThemeSwatches",
            "AppearanceEditThemePanel",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Appearance screen source must include $token", token in appearanceScreenSource)
        }
    }

    @Test
    fun `appearance state model keeps frontend input contract text`() {
        listOf(
            "ReadingAppearanceDisplayState",
            "ReadingAppearanceMapper",
            "fun font()",
            "fun theme()",
            "fun edit()",
            "fun loading()",
            "fun error()",
            "字号",
            "行距",
            "主题",
            "字体",
            "翻页动画",
            "编辑主题",
            "加载失败，请重试",
            "已保留当前阅读外观"
        ).forEach { token ->
            assertTrue("Appearance state source must include $token", token in appearanceStateSource)
        }
    }

    @Test
    fun `aloud screen keeps readershell slots and tts controls`() {
        listOf(
            "ReadingAloudScreen",
            "AloudReadingSurface",
            "AloudOverlay",
            "AloudBottomSheet",
            "AloudModuleNav",
            "AloudBrightnessPanel",
            "AloudRunningCapsule",
            "AloudTransport",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Aloud screen source must include $token", token in aloudScreenSource)
        }
    }

    @Test
    fun `aloud state model keeps frontend input contract text`() {
        listOf(
            "ReadingAloudDisplayState",
            "ReadingAloudMapper",
            "fun running()",
            "fun paused()",
            "fun error()",
            "正在朗读",
            "朗读已暂停",
            "系统 TTS 暂不可用",
            "已保留当前阅读位置",
            "清晰女声",
            "15 分钟"
        ).forEach { token ->
            assertTrue("Aloud state source must include $token", token in aloudStateSource)
        }
    }
}
