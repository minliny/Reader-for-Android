package com.reader.android.ui.reader

enum class ContentSearchDisplayState {
    Default,
    Loading,
    Empty,
    Error,
    Offline
}

data class ContentSearchInputUiModel(
    val label: String,
    val query: String,
    val placeholder: String,
    val resultCount: String,
    val clearLabel: String
)

data class ContentSearchPanelUiModel(
    val title: String,
    val bookTitle: String,
    val resultCount: String,
    val tip: String
)

data class ContentSearchFilterUiModel(
    val label: String,
    val active: Boolean
)

data class ContentSearchResultUiModel(
    val title: String,
    val meta: String,
    val progressLabel: String,
    val excerpt: String
)

data class ContentSearchUiState(
    val status: ReadingTocBookmarkStatusUiModel,
    val search: ContentSearchInputUiModel,
    val reading: ReaderShellTextUiModel,
    val panel: ContentSearchPanelUiModel,
    val filters: List<ContentSearchFilterUiModel>,
    val results: List<ContentSearchResultUiModel>,
    val loading: ReaderShellFeedbackUiModel,
    val empty: ReaderShellFeedbackUiModel,
    val error: ReaderShellFeedbackUiModel,
    val offline: ReaderShellFeedbackUiModel,
    val displayState: ContentSearchDisplayState = ContentSearchDisplayState.Default
) {
    val activeFilter: ContentSearchFilterUiModel get() =
        filters.firstOrNull { it.active } ?: filters.first()
}

object ContentSearchMapper {
    fun fromFixture(): ContentSearchUiState =
        ContentSearchUiState(
            status = ReadingTocBookmarkStatusUiModel(
                left = "长夜余火 · 第 32 章",
                time = "10:30"
            ),
            search = ContentSearchInputUiModel(
                label = "搜索本书内容",
                query = "雨夜",
                placeholder = "输入关键词",
                resultCount = "18 处",
                clearLabel = "清空"
            ),
            reading = ReaderShellTextUiModel(
                title = "",
                paragraphs = listOf(
                    "雨，下了一整夜。",
                    "城市像是被泼了一盆凉水，街灯昏黄，水面反着光，连呼吸都带着潮气。",
                    "他站在巷口，手里攥着那封信。纸已经被雨水润湿，边角微微卷起，字迹却依旧清晰。"
                )
            ),
            panel = ContentSearchPanelUiModel(
                title = "本书内容搜索",
                bookTitle = "长夜余火",
                resultCount = "18 处",
                tip = "点击结果跳转并高亮"
            ),
            filters = listOf(
                ContentSearchFilterUiModel("全部", active = false),
                ContentSearchFilterUiModel("章节名", active = false),
                ContentSearchFilterUiModel("正文", active = true),
                ContentSearchFilterUiModel("书签", active = false)
            ),
            results = listOf(
                ContentSearchResultUiModel(
                    title = "第 32 章 雨夜",
                    meta = "第 3 段 · 约 38%",
                    progressLabel = "当前章节",
                    excerpt = "雨夜的风格外冷，仿佛能把人骨头里的热意都吹散。他把衣领竖起，沿着巷子往前走去。"
                ),
                ContentSearchResultUiModel(
                    title = "第 48 章 巷口",
                    meta = "第 7 段 · 约 56%",
                    progressLabel = "已缓存",
                    excerpt = "他站在巷口，看着远处的灯影在雨夜里微微摇晃，像是随时都会熄灭。"
                ),
                ContentSearchResultUiModel(
                    title = "第 51 章 旧日回声",
                    meta = "第 12 段 · 约 72%",
                    progressLabel = "书签附近",
                    excerpt = "那封信的墨迹已经有些模糊，但在雨夜的光里，她仿佛还能看见当年的字迹。"
                ),
                ContentSearchResultUiModel(
                    title = "第 68 章 城市",
                    meta = "第 5 段 · 约 21%",
                    progressLabel = "章节中段",
                    excerpt = "雨夜的城市总是安静得过分，只有雨声在屋檐下不断敲打。"
                )
            ),
            loading = ReaderShellFeedbackUiModel(
                title = "正在加载",
                message = "正在检索本书正文，请稍候。"
            ),
            empty = ReaderShellFeedbackUiModel(
                title = "无匹配结果",
                message = "当前关键词没有命中正文，换个词或切换到全部范围再搜索。",
                primaryAction = "清空关键词"
            ),
            error = ReaderShellFeedbackUiModel(
                title = "搜索失败，请重试",
                message = "本地索引暂时不可用，已保留关键词和当前阅读位置。",
                primaryAction = "重试"
            ),
            offline = ReaderShellFeedbackUiModel(
                title = "网络不可用，请稍后重试",
                message = "本地缓存正文仍可搜索，在线章节暂不刷新。",
                primaryAction = "知道了"
            )
        )

    fun loading(): ContentSearchUiState =
        fromFixture().copy(displayState = ContentSearchDisplayState.Loading)

    fun empty(): ContentSearchUiState =
        fromFixture().copy(displayState = ContentSearchDisplayState.Empty)

    fun error(): ContentSearchUiState =
        fromFixture().copy(displayState = ContentSearchDisplayState.Error)

    fun offline(): ContentSearchUiState =
        fromFixture().copy(displayState = ContentSearchDisplayState.Offline)
}
