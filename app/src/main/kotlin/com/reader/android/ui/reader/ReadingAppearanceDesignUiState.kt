package com.reader.android.ui.reader

enum class ReadingAppearanceDisplayState {
    Default,
    Font,
    Theme,
    Edit,
    Loading,
    Error
}

data class ReadingAppearancePanelUiModel(
    val title: String,
    val subtitle: String,
    val moreLabel: String,
    val fontTitle: String,
    val fontPreviewLabel: String,
    val animationTitle: String,
    val animationPreview: String,
    val resetLabel: String,
    val saveLabel: String,
    val savingLabel: String
)

data class ReadingFontSizeUiModel(
    val title: String,
    val value: Int,
    val minLabel: String,
    val plusLabel: String
)

data class ReadingSegmentOptionUiModel(
    val label: String,
    val type: String,
    val active: Boolean
)

data class ReadingLineSpacingUiModel(
    val title: String,
    val options: List<ReadingSegmentOptionUiModel>
)

data class ReadingThemeSwatchUiModel(
    val label: String,
    val type: String,
    val background: String,
    val text: String,
    val active: Boolean
)

data class ReadingFontOptionUiModel(
    val label: String,
    val meta: String,
    val preview: String,
    val active: Boolean
)

data class ReadingEditThemeUiModel(
    val title: String,
    val backgroundLabel: String,
    val textLabel: String,
    val previewTitle: String,
    val previewCopy: String,
    val colors: List<ReadingThemeColorUiModel>
)

data class ReadingThemeColorUiModel(
    val label: String,
    val value: String,
    val active: Boolean
)

data class ReadingAppearanceUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val topControl: ReadingTopControlUiModel,
    val reading: ReaderShellTextUiModel,
    val panel: ReadingAppearancePanelUiModel,
    val fontSize: ReadingFontSizeUiModel,
    val lineSpacing: ReadingLineSpacingUiModel,
    val themes: List<ReadingThemeSwatchUiModel>,
    val fonts: List<ReadingFontOptionUiModel>,
    val animations: List<ReadingSegmentOptionUiModel>,
    val editTheme: ReadingEditThemeUiModel,
    val loading: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val moduleNav: List<ReadingModuleNavItemUiModel>,
    val brightness: ReadingBrightnessUiModel,
    val bottomReadout: ReadingBottomReadoutUiModel,
    val displayState: ReadingAppearanceDisplayState = ReadingAppearanceDisplayState.Default
) {
    val activeFont: ReadingFontOptionUiModel get() =
        fonts.firstOrNull { it.active } ?: fonts.first()

    val activeAnimation: ReadingSegmentOptionUiModel get() =
        animations.firstOrNull { it.active } ?: animations.first()
}

object ReadingAppearanceMapper {
    fun fromFixture(): ReadingAppearanceUiState =
        ReadingAppearanceUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            topControl = ReadingTopControlUiModel(
                bookTitle = "长夜余火",
                sourceLine = "起点中文网 · 第 32 章",
                sourceActionLabel = "换源"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。",
                    "“等你回来。”",
                    "没有署名，只有短短四个字。",
                    "他笑了笑，笑得很轻，像是自言自语。然后转身，沿着雨声走进夜里。"
                )
            ),
            panel = ReadingAppearancePanelUiModel(
                title = "界面",
                subtitle = "阅读外观",
                moreLabel = "更多外观 >",
                fontTitle = "字体",
                fontPreviewLabel = "预览",
                animationTitle = "翻页动画",
                animationPreview = "雨，下了一整夜。",
                resetLabel = "恢复默认",
                saveLabel = "保存主题",
                savingLabel = "保存中"
            ),
            fontSize = ReadingFontSizeUiModel(
                title = "字号",
                value = 18,
                minLabel = "-",
                plusLabel = "+"
            ),
            lineSpacing = ReadingLineSpacingUiModel(
                title = "行距",
                options = listOf(
                    ReadingSegmentOptionUiModel("紧凑", "compact", active = false),
                    ReadingSegmentOptionUiModel("标准", "standard", active = true),
                    ReadingSegmentOptionUiModel("舒展", "wide", active = false)
                )
            ),
            themes = listOf(
                ReadingThemeSwatchUiModel("纸张", "paper", "#fbf6ee", "#17130f", active = true),
                ReadingThemeSwatchUiModel("浅绿", "green", "#edf6ed", "#1c2a1d", active = false),
                ReadingThemeSwatchUiModel("夜间", "dark", "#1b1f1c", "#f7f0e8", active = false),
                ReadingThemeSwatchUiModel("暖黄", "warm", "#f1ddb4", "#2a2118", active = false)
            ),
            fonts = listOf(
                ReadingFontOptionUiModel("系统宋体", "当前字体", "雨，下了一整夜。", active = true),
                ReadingFontOptionUiModel("思源宋体", "适合长篇正文", "城市像是被泼了一盆凉水。", active = false),
                ReadingFontOptionUiModel("系统黑体", "清晰紧凑", "水面反着光。", active = false)
            ),
            animations = listOf(
                ReadingSegmentOptionUiModel("仿真滑动", "simulation", active = true),
                ReadingSegmentOptionUiModel("覆盖", "cover", active = false),
                ReadingSegmentOptionUiModel("无动画", "none", active = false)
            ),
            editTheme = ReadingEditThemeUiModel(
                title = "编辑主题",
                backgroundLabel = "背景",
                textLabel = "文字",
                previewTitle = "预览",
                previewCopy = "雨，下了一整夜。城市像是被泼了一盆凉水。",
                colors = listOf(
                    ReadingThemeColorUiModel("纸张", "#fbf6ee", active = true),
                    ReadingThemeColorUiModel("淡绿", "#edf6ed", active = false),
                    ReadingThemeColorUiModel("夜黑", "#1b1f1c", active = false),
                    ReadingThemeColorUiModel("暖黄", "#f1ddb4", active = false)
                )
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在读取字体和主题配置，请稍候。"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "加载失败，请重试",
                message = "字体列表加载失败，已保留当前阅读外观。",
                primaryAction = "重试"
            ),
            moduleNav = listOf(
                ReadingModuleNavItemUiModel("目录", "directory", active = false),
                ReadingModuleNavItemUiModel("朗读", "tts", active = false),
                ReadingModuleNavItemUiModel("界面", "appearance", active = true),
                ReadingModuleNavItemUiModel("设置", "settings", active = false)
            ),
            brightness = ReadingBrightnessUiModel(
                title = "亮度",
                value = 56,
                autoLabel = "A",
                modeLabel = "系统"
            ),
            bottomReadout = ReadingBottomReadoutUiModel(
                progress = "38%",
                chapter = "第 32/128 章"
            )
        )

    fun font(): ReadingAppearanceUiState =
        fromFixture().copy(displayState = ReadingAppearanceDisplayState.Font)

    fun theme(): ReadingAppearanceUiState =
        fromFixture().copy(displayState = ReadingAppearanceDisplayState.Theme)

    fun edit(): ReadingAppearanceUiState =
        fromFixture().copy(displayState = ReadingAppearanceDisplayState.Edit)

    fun loading(): ReadingAppearanceUiState =
        fromFixture().copy(displayState = ReadingAppearanceDisplayState.Loading)

    fun error(): ReadingAppearanceUiState =
        fromFixture().copy(displayState = ReadingAppearanceDisplayState.Error)
}
