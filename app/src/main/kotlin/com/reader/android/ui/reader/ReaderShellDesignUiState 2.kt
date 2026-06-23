package com.reader.android.ui.reader

enum class ReadingEntryDisplayState {
    Default,
    Loading,
    Error,
    Offline
}

enum class ImmersiveReadingDisplayState {
    Default,
    Loading,
    Error,
    Offline
}

data class ReaderShellTextUiModel(
    val title: String,
    val paragraphs: List<String>
)

data class ReadingEntryStatusUiModel(
    val left: String,
    val time: String,
    val progress: String,
    val chapterLabel: String
)

data class ReadingEntryActionUiModel(
    val title: String,
    val meta: String,
    val label: String,
    val primary: Boolean
)

data class ReadingEntryDockUiModel(
    val title: String,
    val source: String,
    val context: String,
    val backLabel: String,
    val actions: List<ReadingEntryActionUiModel>
)

data class ReaderShellFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null,
    val secondaryAction: String? = null,
    val disabledAction: String? = null
)

data class ReadingEntryUiState(
    val status: ReadingEntryStatusUiModel,
    val reading: ReaderShellTextUiModel,
    val dock: ReadingEntryDockUiModel,
    val loading: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val offline: ReaderShellFeedbackUiModel,
    val displayState: ReadingEntryDisplayState = ReadingEntryDisplayState.Default
)

data class ImmersiveInfoUiModel(
    val topLeft: String,
    val time: String,
    val progress: String,
    val chapterOnly: String
)

data class ImmersiveTapZoneUiModel(
    val type: String,
    val label: String
)

data class ImmersiveReadingUiState(
    val info: ImmersiveInfoUiModel,
    val reading: ReaderShellTextUiModel,
    val zones: List<ImmersiveTapZoneUiModel>,
    val loading: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val offline: ReaderShellFeedbackUiModel,
    val chapterOnlyRule: String,
    val displayState: ImmersiveReadingDisplayState = ImmersiveReadingDisplayState.Default
)

object ReaderShellDesignFixture {
    val paragraphs = listOf(
        "雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。",
        "我站在窗前，手里握着一杯早已凉透的茶。茶叶沉在杯底，像一些沉默的往事，不肯浮上来，也不肯下去。",
        "今天的谈话比预想的要短，对方没有给出任何明确的答复，只说会再考虑。这样的说辞我听得太多了，里面的分量，也轻得几乎可以忽略不计。",
        "我走到书桌前，打开抽屉，里面躺着一个牛皮纸信封。没有署名，也没有日期，只有一句话：如果你看到这封信，说明你已经走到这一步。",
        "我盯着那行字看了很久，心里没有预想中的波澜，反而生出一种奇异的平静。也许有些选择，从一开始就注定要在某个雨夜到来。",
        "窗外的雨还在下，敲打着屋檐，敲打着树叶，也敲打着这座城市里无数个未眠的人。而我知道，从明天起，有些事就再也回不去了。"
    )

    val reading = ReaderShellTextUiModel(
        title = "雨夜",
        paragraphs = paragraphs
    )
}

object ReadingEntryMapper {
    fun fromFixture(): ReadingEntryUiState =
        ReadingEntryUiState(
            status = ReadingEntryStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30",
                progress = "38%",
                chapterLabel = "第 32 / 128 章"
            ),
            reading = ReaderShellDesignFixture.reading,
            dock = ReadingEntryDockUiModel(
                title = "阅读入口",
                source = "从书架继续阅读",
                context = "长篇追读 · 保留滚动位置和筛选条件",
                backLabel = "返回书架",
                actions = listOf(
                    ReadingEntryActionUiModel(
                        title = "继续阅读",
                        meta = "恢复到第 32 章 雨夜 · 38%",
                        label = "继续阅读",
                        primary = true
                    ),
                    ReadingEntryActionUiModel(
                        title = "开始阅读",
                        meta = "从章节开头重新打开",
                        label = "开始阅读",
                        primary = false
                    )
                )
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在打开",
                message = "正在恢复第 32 章雨夜和阅读位置，请稍候。"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "内容加载异常",
                message = "打开失败，请重试；如果当前来源失效，可以更换来源后继续。",
                primaryAction = "更换来源",
                secondaryAction = "重试"
            ),
            offline = ReaderShellFeedbackUiModel(
                title = "网络不可用，请稍后重试",
                message = "已找到本地缓存，可先继续阅读缓存章节；更换来源需要联网。",
                primaryAction = "继续阅读缓存",
                disabledAction = "更换来源需联网"
            )
        )

    fun loading(): ReadingEntryUiState =
        fromFixture().copy(displayState = ReadingEntryDisplayState.Loading)

    fun error(): ReadingEntryUiState =
        fromFixture().copy(displayState = ReadingEntryDisplayState.Error)

    fun offline(): ReadingEntryUiState =
        fromFixture().copy(displayState = ReadingEntryDisplayState.Offline)
}

object ImmersiveReadingMapper {
    fun fromFixture(): ImmersiveReadingUiState =
        ImmersiveReadingUiState(
            info = ImmersiveInfoUiModel(
                topLeft = "长夜余火 · 第 32 章",
                time = "10:30",
                progress = "38%",
                chapterOnly = "第 32 章"
            ),
            reading = ReaderShellDesignFixture.reading,
            zones = listOf(
                ImmersiveTapZoneUiModel("previous", "上一页热区"),
                ImmersiveTapZoneUiModel("menu", "点击中心呼出控制层"),
                ImmersiveTapZoneUiModel("next", "下一页热区")
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在加载当前章节，保留阅读背景和弱信息层。"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "加载失败，请重试",
                message = "章节内容读取失败，返回来源页后仍保留阅读位置。",
                primaryAction = "重试"
            ),
            offline = ReaderShellFeedbackUiModel(
                title = "网络不可用，请稍后重试",
                message = "已显示本地缓存章节，联网后再刷新后续内容。",
                primaryAction = "继续阅读缓存"
            ),
            chapterOnlyRule = "当前章节信息只显示当前章节，不显示总章节数"
        )

    fun loading(): ImmersiveReadingUiState =
        fromFixture().copy(displayState = ImmersiveReadingDisplayState.Loading)

    fun error(): ImmersiveReadingUiState =
        fromFixture().copy(displayState = ImmersiveReadingDisplayState.Error)

    fun offline(): ImmersiveReadingUiState =
        fromFixture().copy(displayState = ImmersiveReadingDisplayState.Offline)
}
