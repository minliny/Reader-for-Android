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

    private val settingsScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingSettingsDesignScreen.kt")))
    }

    private val settingsStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingSettingsDesignUiState.kt")))
    }

    private val autoPageScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/AutoPageDesignScreen.kt")))
    }

    private val autoPageStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/AutoPageDesignUiState.kt")))
    }

    private val contentSearchScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ContentSearchDesignScreen.kt")))
    }

    private val contentSearchStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ContentSearchDesignUiState.kt")))
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
            "ReadingAloudErrorPreview",
            "ReadingSettingsDefaultPreview",
            "ReadingSettingsSubpagePreview",
            "ReadingSettingsLoadingPreview",
            "ReadingSettingsErrorPreview",
            "AutoPageDefaultPreview",
            "AutoPageRunningPreview",
            "AutoPagePausedPreview",
            "AutoPageErrorPreview",
            "ContentSearchDefaultPreview",
            "ContentSearchLoadingPreview",
            "ContentSearchEmptyPreview",
            "ContentSearchErrorPreview",
            "ContentSearchOfflinePreview"
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
            "ReadingAloudMapper.error",
            "ReadingSettingsMapper.fromFixture",
            "ReadingSettingsMapper.subpage",
            "ReadingSettingsMapper.loading",
            "ReadingSettingsMapper.error",
            "AutoPageMapper.fromFixture",
            "AutoPageMapper.running",
            "AutoPageMapper.paused",
            "AutoPageMapper.error",
            "ContentSearchMapper.fromFixture",
            "ContentSearchMapper.loading",
            "ContentSearchMapper.empty",
            "ContentSearchMapper.error",
            "ContentSearchMapper.offline"
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

    @Test
    fun `settings screen keeps readershell hosts without main tab navigation`() {
        listOf(
            "ReadingSettingsScreen",
            "ReadingSettingsSurface",
            "ReadingSettingsOverlay",
            "ReadingSettingsTopBar",
            "ReadingSettingsHome",
            "ReadingSettingsSubpage",
            "ReadingSettingsFeedback",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Settings screen source must include $token", token in settingsScreenSource)
        }
        listOf("ReaderMainTabShell", "BottomNavigation", "ReaderBottomItem").forEach { token ->
            assertTrue("Settings screen source must not include $token", token !in settingsScreenSource)
        }
    }

    @Test
    fun `settings state model keeps frontend input contract text`() {
        listOf(
            "ReadingSettingsDisplayState",
            "ReadingSettingsMapper",
            "fun subpage()",
            "fun loading()",
            "fun error()",
            "阅读设置",
            "预设管理",
            "屏幕与显示",
            "翻页与手势",
            "阅读辅助",
            "进度与信息",
            "高级设置",
            "恢复默认阅读设置",
            "不会删除书籍或阅读进度",
            "保存失败，已保留当前阅读设置"
        ).forEach { token ->
            assertTrue("Settings state source must include $token", token in settingsStateSource)
        }
    }

    @Test
    fun `auto page screen keeps readershell slots with empty module nav host`() {
        listOf(
            "AutoPageScreen",
            "AutoPageReadingSurface",
            "AutoPageOverlay",
            "AutoPageBottomSheet",
            "AutoPageRunningCapsule",
            "AutoPageSpeedCard",
            "AutoPageOptionsCard",
            "AutoPageFeedbackPanel",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Auto page screen source must include $token", token in autoPageScreenSource)
        }
        assertTrue("Auto page must not render module navigation items", "ReaderBottomItem" !in autoPageScreenSource)
        assertTrue("Auto page module nav host must not reuse module nav buttons", "AutoPageModuleNav" !in autoPageScreenSource)
    }

    @Test
    fun `auto page state model keeps frontend input contract text`() {
        listOf(
            "AutoPageDisplayState",
            "AutoPageMapper",
            "fun running()",
            "fun paused()",
            "fun error()",
            "自动翻页",
            "开始自动翻页",
            "暂停",
            "停止",
            "翻页速度",
            "屏幕常亮",
            "到章末停止",
            "音量键调速",
            "当前章节暂时无法继续翻页",
            "已停止自动翻页并保留阅读位置"
        ).forEach { token ->
            assertTrue("Auto page state source must include $token", token in autoPageStateSource)
        }
    }

    @Test
    fun `content search screen keeps readershell slots with empty module nav host`() {
        listOf(
            "ContentSearchScreen",
            "ContentSearchReadingSurface",
            "ContentSearchOverlay",
            "ContentSearchBottomSheet",
            "ContentSearchInput",
            "ContentSearchResultRow",
            "ContentSearchFeedbackPanel",
            "ContentSearchOfflineBanner",
            "HighlightedText",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Content search screen source must include $token", token in contentSearchScreenSource)
        }
        assertTrue("Content search must not render module navigation items", "ReaderBottomItem" !in contentSearchScreenSource)
        assertTrue("Content search must not import global book search package", "com.reader.android.ui.search" !in contentSearchScreenSource)
    }

    @Test
    fun `content search state model keeps frontend input contract text`() {
        listOf(
            "ContentSearchDisplayState",
            "ContentSearchMapper",
            "fun loading()",
            "fun empty()",
            "fun error()",
            "fun offline()",
            "搜索本书内容",
            "输入关键词",
            "本书内容搜索",
            "无匹配结果",
            "搜索失败，请重试",
            "本地索引暂时不可用",
            "已保留关键词和当前阅读位置",
            "本地缓存正文仍可搜索",
            "点击结果跳转并高亮"
        ).forEach { token ->
            assertTrue("Content search state source must include $token", token in contentSearchStateSource)
        }
    }
}
