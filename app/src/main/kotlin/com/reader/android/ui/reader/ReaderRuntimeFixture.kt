package com.reader.android.ui.reader

import com.reader.android.ui.reader.components.BrightnessDock

object ReaderRuntimeFixture {

    val sampleBook = ReaderBookUiModel(
        bookTitle = "深空信号",
        sourceName = "本地书籍",
        tocUrl = "/toc/sample"
    )

    val sampleChapter = ReaderChapterUiModel(
        chapterTitle = "第一章：阿长与《山海经》",
        chapterIndex = 0,
        totalChapters = 42
    )

    val sampleContent = ReaderContentUiModel(
        text = "通讯台的指示灯已经闪烁了三个标准时。在这片被称为寂静航线的星域里，通常除了背景辐射的白噪音，什么也听不到。\n\n" +
            "这不是随机的脉冲星信号，领航员索菲亚走到他身后，指着屏幕边缘的傅里叶变换图谱，看这里的周期性间断，它有明显的结构，像是一种原始的二进制。\n\n" +
            "空间站外，恒星的光芒被一层稀薄的星云物质过滤，呈现出一种黯淡的铁锈红。\n\n" +
            "三短，一长静默……索菲亚的脸色变了，在旧地球时代的航海通讯里，这是……\n\n" +
            "求救信号。艾伦打断了她，手指在控制台的触控板上快速滑过。",
        prevPageAvailable = false,
        nextPageAvailable = true
    )

    val sampleReplaceRules = listOf(
        ReaderReplaceRuleUiModel(
            name = "去除章节尾广告",
            pattern = "请收藏本站.*\$",
            replacement = "空",
            scope = "当前书籍",
            enabled = true
        ),
        ReaderReplaceRuleUiModel(
            name = "清理发布页提示",
            pattern = "本章由.*?整理",
            replacement = "空",
            scope = "当前章节",
            enabled = true
        ),
        ReaderReplaceRuleUiModel(
            name = "统一省略号",
            pattern = "\\.{3,}",
            replacement = "……",
            scope = "正文内容",
            enabled = false
        )
    )

    val sampleTocEntries = listOf(
        ReaderTocEntryUiModel("第一章：阿长与《山海经》", level = 1, isCurrent = false, hasBookmark = false, progress = 1f),
        ReaderTocEntryUiModel("深空信号", level = 2, isCurrent = true, hasBookmark = true, progress = 0.25f),
        ReaderTocEntryUiModel("第二节：寂静航线", level = 2, isCurrent = false, hasBookmark = false, progress = 0f),
        ReaderTocEntryUiModel("未知频段", level = 3, isCurrent = false, hasBookmark = false, progress = 0f),
        ReaderTocEntryUiModel("求救信号", level = 4, isCurrent = false, hasBookmark = true, progress = 0f)
    )

    val searchResults = listOf(
        ReaderSearchResultUiModel("未知频段的跳动波形", "盯着那个代表未知频段的跳动信号波形"),
        ReaderSearchResultUiModel("不是随机的脉冲星信号", "这不是随机的脉冲星信号，领航员索菲亚说"),
        ReaderSearchResultUiModel("求救信号", "求救信号。艾伦打断了她")
    )

    fun createBaseControlVisible(): ReaderRuntimeUiState = ReaderRuntimeUiState(
        book = sampleBook,
        chapter = sampleChapter,
        content = sampleContent,
        pageProgress = ReaderPageProgressUiModel(0.25f),
        controlLayerState = ReaderControlLayerState.BaseControlVisible,
        brightnessDockState = ReaderBrightnessDockState(BrightnessDock.Left)
    )

    fun createSearchOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.QuickActionOverlay(ReaderOverlayType.SEARCH),
        searchState = ReaderSearchUiModel(
            query = "信号",
            resultCount = 3,
            results = searchResults,
            currentIndex = 0
        )
    )

    fun createAutoScrollOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.QuickActionOverlay(ReaderOverlayType.AUTO_SCROLL),
        autoScrollState = ReaderAutoScrollState.RUNNING
    )

    fun createReplaceOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.QuickActionOverlay(ReaderOverlayType.REPLACE),
        replaceRules = sampleReplaceRules
    )

    fun createDirectoryOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.BottomFunctionOverlay(ReaderOverlayType.DIRECTORY),
        tocBookmarkState = ReaderTocBookmarkState(
            entries = sampleTocEntries,
            volumeInfo = "第一本 / 第一卷",
            activeTab = "目录"
        )
    )

    fun createTtsOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.BottomFunctionOverlay(ReaderOverlayType.TTS),
        ttsState = ReaderTtsState.PLAYING
    )

    fun createAppearanceOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.BottomFunctionOverlay(ReaderOverlayType.APPEARANCE)
    )

    fun createSettingsOverlay(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        controlLayerState = ReaderControlLayerState.BottomFunctionOverlay(ReaderOverlayType.SETTINGS)
    )

    fun createNightState(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        isNightMode = true
    )

    fun createBrightnessRightDock(): ReaderRuntimeUiState = createBaseControlVisible().copy(
        brightnessDockState = ReaderBrightnessDockState(BrightnessDock.Right)
    )

    fun createLoadingState(): ReaderRuntimeUiState = ReaderRuntimeUiState(
        book = sampleBook,
        chapter = sampleChapter,
        isLoading = true
    )

    fun createErrorState(): ReaderRuntimeUiState = ReaderRuntimeUiState(
        book = sampleBook,
        chapter = sampleChapter,
        error = "网络连接失败，请重试"
    )

    /** All 9 reader control states for testing. */
    val allNineStates: List<ReaderRuntimeUiState> = listOf(
        createBaseControlVisible(),
        createSearchOverlay(),
        createAutoScrollOverlay(),
        createReplaceOverlay(),
        createDirectoryOverlay(),
        createTtsOverlay(),
        createAppearanceOverlay(),
        createSettingsOverlay(),
        createNightState()
    )
}
