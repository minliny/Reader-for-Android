package com.reader.android.ui.reader

enum class ContentReplacementDisplayState {
    Default,
    Edit,
    Empty,
    Loading,
    Error
}

data class ContentReplacementTopControlUiModel(
    val title: String,
    val bookTitle: String,
    val settingsLabel: String
)

data class ContentReplacementPanelUiModel(
    val title: String,
    val allRulesLabel: String,
    val enableTitle: String,
    val enableCopy: String,
    val tempCloseLabel: String,
    val addRuleLabel: String
)

data class ReplacementRuleUiModel(
    val title: String,
    val meta: String,
    val icon: String,
    val enabled: Boolean
)

data class ReplacementPreviewUiModel(
    val title: String,
    val beforeLabel: String,
    val beforeText: String,
    val afterLabel: String,
    val afterText: String
)

data class ReplacementFormUiModel(
    val beforeLabel: String,
    val beforeValue: String,
    val afterLabel: String,
    val afterValue: String,
    val testLabel: String,
    val saveLabel: String
)

data class ContentReplacementUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val topControl: ContentReplacementTopControlUiModel,
    val reading: ReaderShellTextUiModel,
    val panel: ContentReplacementPanelUiModel,
    val enabled: Boolean,
    val rules: List<ReplacementRuleUiModel>,
    val preview: ReplacementPreviewUiModel,
    val form: ReplacementFormUiModel,
    val loading: ReaderShellFeedbackUiModel,
    val empty: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val displayState: ContentReplacementDisplayState = ContentReplacementDisplayState.Default
)

object ContentReplacementMapper {
    fun fromFixture(): ContentReplacementUiState =
        ContentReplacementUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            topControl = ContentReplacementTopControlUiModel(
                title = "内容替换",
                bookTitle = "长夜余火",
                settingsLabel = "设置"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。"
                )
            ),
            panel = ContentReplacementPanelUiModel(
                title = "当前书规则",
                allRulesLabel = "全部规则",
                enableTitle = "启用内容替换",
                enableCopy = "仅影响当前阅读显示，不修改原文",
                tempCloseLabel = "临时关闭",
                addRuleLabel = "新增规则"
            ),
            enabled = true,
            rules = listOf(
                ReplacementRuleUiModel("屏蔽广告段落", "命中 12 处", "shield", enabled = true),
                ReplacementRuleUiModel("统一标点空格", "中文排版净化", "text", enabled = true),
                ReplacementRuleUiModel("替换错别字", "自定义词表", "typo", enabled = false)
            ),
            preview = ReplacementPreviewUiModel(
                title = "替换预览",
                beforeLabel = "原文",
                beforeText = "水面反着光",
                afterLabel = "显示",
                afterText = "水面映着光"
            ),
            form = ReplacementFormUiModel(
                beforeLabel = "替换前",
                beforeValue = "反着光",
                afterLabel = "替换后",
                afterValue = "映着光",
                testLabel = "测试",
                saveLabel = "保存"
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在读取当前书替换规则，请稍候。"
            ),
            empty = ReaderShellFeedbackUiModel(
                title = "暂无替换规则",
                message = "新增规则后只影响当前阅读显示，不会修改原始章节。",
                primaryAction = "新增规则"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "操作失败，请重试",
                message = "规则格式错误，已保留替换前和替换后内容。",
                primaryAction = "重试"
            )
        )

    fun edit(): ContentReplacementUiState =
        fromFixture().copy(displayState = ContentReplacementDisplayState.Edit)

    fun empty(): ContentReplacementUiState =
        fromFixture().copy(displayState = ContentReplacementDisplayState.Empty, rules = emptyList())

    fun loading(): ContentReplacementUiState =
        fromFixture().copy(displayState = ContentReplacementDisplayState.Loading)

    fun error(): ContentReplacementUiState =
        fromFixture().copy(displayState = ContentReplacementDisplayState.Error)
}
