package com.reader.android.ui.discover

enum class DiscoveryHomeDisplayState {
    Default,
    Subscription,
    Loading,
    Empty,
    Error,
    Offline
}

data class DiscoveryStatusUiModel(
    val time: String,
    val battery: String
)

data class DiscoveryTopBarUiModel(
    val title: String
)

data class DiscoverySearchUiModel(
    val placeholder: String
)

data class DiscoverySourceTypeUiModel(
    val label: String,
    val type: String,
    val active: Boolean
)

data class DiscoveryCurrentSourceUiModel(
    val title: String,
    val meta: String,
    val status: String,
    val actionLabel: String,
    val iconLabel: String
)

data class DiscoveryCategoryUiModel(
    val label: String,
    val active: Boolean
)

data class DiscoveryBookUiModel(
    val title: String,
    val author: String,
    val source: String,
    val desc: String,
    val cover: String,
    val actionLabel: String,
    val inBookshelf: Boolean
)

data class DiscoveryContentUiModel(
    val title: String,
    val refreshLabel: String,
    val featured: List<DiscoveryBookUiModel>
)

data class DiscoverySourceStatusUiModel(
    val sourceCount: String,
    val availableCount: String,
    val updatedAt: String,
    val actionLabel: String
)

data class DiscoveryRankingItemUiModel(
    val rank: Int,
    val title: String,
    val author: String,
    val source: String,
    val cover: String,
    val state: String,
    val tone: String
)

data class DiscoveryRankingUiModel(
    val title: String,
    val moreLabel: String,
    val items: List<DiscoveryRankingItemUiModel>
)

data class DiscoveryFeedbackUiModel(
    val title: String,
    val copy: String,
    val primaryAction: String? = null,
    val secondaryAction: String? = null
)

data class DiscoveryHomeFeedbackSetUiModel(
    val loading: DiscoveryFeedbackUiModel,
    val empty: DiscoveryFeedbackUiModel,
    val error: DiscoveryFeedbackUiModel,
    val offline: DiscoveryFeedbackUiModel
)

data class DiscoveryHomeUiState(
    val status: DiscoveryStatusUiModel,
    val topBar: DiscoveryTopBarUiModel,
    val search: DiscoverySearchUiModel,
    val sourceTypes: List<DiscoverySourceTypeUiModel>,
    val currentSource: DiscoveryCurrentSourceUiModel,
    val categories: List<DiscoveryCategoryUiModel>,
    val categoryMoreLabel: String,
    val content: DiscoveryContentUiModel,
    val statusBar: DiscoverySourceStatusUiModel,
    val ranking: DiscoveryRankingUiModel,
    val feedback: DiscoveryHomeFeedbackSetUiModel,
    val bottomNavLabels: List<String>,
    val displayState: DiscoveryHomeDisplayState = DiscoveryHomeDisplayState.Default
)

object DiscoveryHomeMapper {
    fun fromFixture(): DiscoveryHomeUiState =
        DiscoveryHomeUiState(
            status = DiscoveryStatusUiModel(
                time = "10:30",
                battery = "82%"
            ),
            topBar = DiscoveryTopBarUiModel("发现"),
            search = DiscoverySearchUiModel("搜索书名、作者、关键词"),
            sourceTypes = listOf(
                DiscoverySourceTypeUiModel("全部", "all", false),
                DiscoverySourceTypeUiModel("书源", "book-source", true),
                DiscoverySourceTypeUiModel("订阅", "subscription", false)
            ),
            currentSource = DiscoveryCurrentSourceUiModel(
                title = "玄幻书源组",
                meta = "12 个来源",
                status = "分类随来源变化",
                actionLabel = "切换来源",
                iconLabel = "信息源"
            ),
            categories = listOf("全部", "玄幻", "都市", "排行", "新书", "完结").map { label ->
                DiscoveryCategoryUiModel(label = label, active = label == "玄幻")
            },
            categoryMoreLabel = "更多分类",
            content = DiscoveryContentUiModel(
                title = "玄幻书源组 · 玄幻",
                refreshLabel = "刷新",
                featured = listOf(
                    DiscoveryBookUiModel(
                        title = "道诡异仙",
                        author = "狐尾的笔",
                        source = "笔趣阁",
                        desc = "诡异修仙，踏破红尘问长生。",
                        cover = "../../书架/bookshelf-cover-assets/mystery-lord.png",
                        actionLabel = "阅读",
                        inBookshelf = true
                    ),
                    DiscoveryBookUiModel(
                        title = "大奉打更人",
                        author = "卖报小郎君",
                        source = "起点中文网",
                        desc = "打更人出世，大奉风云再起。",
                        cover = "../../书架/bookshelf-cover-assets/bright-moon.png",
                        actionLabel = "加入书架",
                        inBookshelf = false
                    )
                )
            ),
            statusBar = DiscoverySourceStatusUiModel(
                sourceCount = "12 个来源",
                availableCount = "8 个可用",
                updatedAt = "刚刚更新",
                actionLabel = "检测"
            ),
            ranking = DiscoveryRankingUiModel(
                title = "来源榜单更新",
                moreLabel = "更多",
                items = listOf(
                    DiscoveryRankingItemUiModel(
                        rank = 1,
                        title = "诡秘之主",
                        author = "爱潜水的乌贼",
                        source = "起点中文网",
                        cover = "../../书架/bookshelf-cover-assets/mystery-lord.png",
                        state = "连载中",
                        tone = "orange"
                    ),
                    DiscoveryRankingItemUiModel(
                        rank = 2,
                        title = "三体",
                        author = "刘慈欣",
                        source = "三体宇宙",
                        cover = "../../书架/bookshelf-cover-assets/three-body.png",
                        state = "完结",
                        tone = "muted"
                    ),
                    DiscoveryRankingItemUiModel(
                        rank = 3,
                        title = "凡人修仙传",
                        author = "忘语",
                        source = "笔趣阁",
                        cover = "../../书架/bookshelf-cover-assets/renjian-cihua.png",
                        state = "新上榜",
                        tone = "green"
                    )
                )
            ),
            feedback = DiscoveryHomeFeedbackSetUiModel(
                loading = DiscoveryFeedbackUiModel(
                    title = "正在切换来源类型",
                    copy = "保留当前来源和分类，只刷新内容区。"
                ),
                empty = DiscoveryFeedbackUiModel(
                    title = "当前来源暂无内容",
                    copy = "可以刷新，或切换到其他来源。",
                    primaryAction = "刷新",
                    secondaryAction = "去管理来源"
                ),
                error = DiscoveryFeedbackUiModel(
                    title = "来源加载失败",
                    copy = "来源仍保留在当前上下文，可以重试或切换来源。",
                    primaryAction = "重试",
                    secondaryAction = "切换来源"
                ),
                offline = DiscoveryFeedbackUiModel(
                    title = "网络不可用",
                    copy = "离线时仍可浏览已缓存内容，联网后刷新来源。",
                    primaryAction = "重试",
                    secondaryAction = "查看缓存"
                )
            ),
            bottomNavLabels = listOf("书架", "发现", "RSS", "设置")
        )

    fun subscription(): DiscoveryHomeUiState =
        fromFixture().copy(
            displayState = DiscoveryHomeDisplayState.Subscription,
            sourceTypes = listOf(
                DiscoverySourceTypeUiModel("全部", "all", false),
                DiscoverySourceTypeUiModel("书源", "book-source", false),
                DiscoverySourceTypeUiModel("订阅", "subscription", true)
            ),
            currentSource = fromFixture().currentSource.copy(
                title = "订阅源聚合",
                meta = "4 个订阅",
                status = "分类由订阅源提供"
            ),
            categories = listOf("全部", "未读", "收藏", "稍后读", "书单").map { label ->
                DiscoveryCategoryUiModel(label = label, active = label == "未读")
            },
            content = fromFixture().content.copy(title = "订阅源聚合 · 未读")
        )

    fun loading(): DiscoveryHomeUiState =
        fromFixture().copy(displayState = DiscoveryHomeDisplayState.Loading)

    fun empty(): DiscoveryHomeUiState =
        fromFixture().copy(
            displayState = DiscoveryHomeDisplayState.Empty,
            content = fromFixture().content.copy(featured = emptyList()),
            ranking = fromFixture().ranking.copy(items = emptyList())
        )

    fun error(): DiscoveryHomeUiState =
        fromFixture().copy(displayState = DiscoveryHomeDisplayState.Error)

    fun offline(): DiscoveryHomeUiState =
        fromFixture().copy(displayState = DiscoveryHomeDisplayState.Offline)
}
