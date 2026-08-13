package com.reader.android.ui.settings

enum class BookshelfSearchSettingsDisplayState {
    Default,
    OptionSheet,
    Confirm,
    Loading,
    Error,
    Permission
}

enum class BookshelfSearchSettingsRowType {
    Segment,
    Stepper,
    Select,
    Switch
}

data class BookshelfSearchSettingsTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class BookshelfSearchSettingsRowUiModel(
    val type: BookshelfSearchSettingsRowType,
    val icon: String,
    val title: String,
    val value: String = "",
    val options: List<String> = emptyList(),
    val meta: String = "",
    val enabled: Boolean = false,
    val minLabel: String = "",
    val maxLabel: String = ""
)

data class BookshelfSearchSettingsSectionUiModel(
    val title: String,
    val rows: List<BookshelfSearchSettingsRowUiModel>
)

data class BookshelfSearchPreviewBookUiModel(
    val title: String,
    val meta: String,
    val update: String,
    val badge: String,
    val cover: String
)

data class BookshelfSearchPreviewUiModel(
    val coverTitle: String,
    val listTitle: String,
    val books: List<BookshelfSearchPreviewBookUiModel>
)

data class BookshelfSearchDangerUiModel(
    val title: String,
    val confirmTitle: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class BookshelfSearchSettingsToastUiModel(
    val success: String,
    val error: String,
    val permission: String
)

data class BookshelfSearchSettingsFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class BookshelfSearchSettingsUiState(
    val topBar: BookshelfSearchSettingsTopBarUiModel,
    val bookshelf: BookshelfSearchSettingsSectionUiModel,
    val preview: BookshelfSearchPreviewUiModel,
    val search: BookshelfSearchSettingsSectionUiModel,
    val danger: BookshelfSearchDangerUiModel,
    val toast: BookshelfSearchSettingsToastUiModel,
    val loading: BookshelfSearchSettingsFeedbackUiModel,
    val error: BookshelfSearchSettingsFeedbackUiModel,
    val permission: BookshelfSearchSettingsFeedbackUiModel,
    val displayState: BookshelfSearchSettingsDisplayState = BookshelfSearchSettingsDisplayState.Default
) {
    val searchRangeRow: BookshelfSearchSettingsRowUiModel get() =
        search.rows.first { it.title == "搜索范围" }
}

object BookshelfSearchSettingsMapper {
    fun fromFixture(): BookshelfSearchSettingsUiState =
        BookshelfSearchSettingsUiState(
            topBar = BookshelfSearchSettingsTopBarUiModel(
                title = "书架与搜索",
                backLabel = "返回"
            ),
            bookshelf = BookshelfSearchSettingsSectionUiModel(
                title = "书架",
                rows = listOf(
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Segment,
                        icon = "grid",
                        title = "默认展示",
                        value = "封面",
                        options = listOf("封面", "列表")
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Stepper,
                        icon = "columns",
                        title = "封面列数",
                        value = "3列",
                        minLabel = "-",
                        maxLabel = "+"
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Select,
                        icon = "folder",
                        title = "默认分组",
                        value = "全部",
                        options = listOf("全部", "长篇追读", "资料", "未分组")
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Switch,
                        icon = "badge",
                        title = "显示更新标记",
                        meta = "在书籍封面上显示更新标记",
                        enabled = true
                    )
                )
            ),
            preview = BookshelfSearchPreviewUiModel(
                coverTitle = "封面模式预览",
                listTitle = "列表模式预览",
                books = listOf(
                    BookshelfSearchPreviewBookUiModel(
                        title = "长夜余火",
                        meta = "第 32 章",
                        update = "10:30 更新",
                        badge = "1",
                        cover = "../../../02-主标签页/书架/bookshelf-cover-assets/long-night.png"
                    ),
                    BookshelfSearchPreviewBookUiModel(
                        title = "大国科技",
                        meta = "未读",
                        update = "昨天",
                        badge = "1",
                        cover = "../../../02-主标签页/书架/bookshelf-cover-assets/android-notes.png"
                    ),
                    BookshelfSearchPreviewBookUiModel(
                        title = "星海征途",
                        meta = "第 128 章",
                        update = "05-18 更新",
                        badge = "1",
                        cover = "../../../02-主标签页/书架/bookshelf-cover-assets/three-body.png"
                    )
                )
            ),
            search = BookshelfSearchSettingsSectionUiModel(
                title = "搜索",
                rows = listOf(
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Select,
                        icon = "search",
                        title = "搜索范围",
                        value = "全局",
                        options = listOf("当前分组", "书架", "全局")
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Select,
                        icon = "sort",
                        title = "结果排序",
                        value = "相关度",
                        options = listOf("相关度", "最近阅读", "最近更新")
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Switch,
                        icon = "people",
                        title = "合并同名同作者",
                        meta = "搜索结果合并相同书名和作者的作品",
                        enabled = true
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Switch,
                        icon = "clock",
                        title = "搜索历史",
                        meta = "记录搜索关键词以便快速访问",
                        enabled = true
                    ),
                    BookshelfSearchSettingsRowUiModel(
                        type = BookshelfSearchSettingsRowType.Select,
                        icon = "list",
                        title = "搜索历史数量",
                        meta = "设置保存的搜索历史条数上限",
                        value = "20条",
                        options = listOf("10条", "20条", "50条")
                    )
                )
            ),
            danger = BookshelfSearchDangerUiModel(
                title = "清空搜索历史",
                confirmTitle = "清空搜索历史？",
                copy = "清空后无法恢复，已保存的搜索关键词会被移除。",
                cancelLabel = "取消",
                confirmLabel = "确认清空"
            ),
            toast = BookshelfSearchSettingsToastUiModel(
                success = "保存成功",
                error = "操作失败，请重试",
                permission = "需要存储权限"
            ),
            loading = BookshelfSearchSettingsFeedbackUiModel(
                title = "正在加载",
                message = "正在读取书架与搜索设置，请稍候。"
            ),
            error = BookshelfSearchSettingsFeedbackUiModel(
                title = "加载失败，请重试",
                message = "设置读取失败，已保留本地已知配置。",
                primaryAction = "重试"
            ),
            permission = BookshelfSearchSettingsFeedbackUiModel(
                title = "需要权限",
                message = "保存搜索历史需要本地存储权限，授权后才能继续。",
                primaryAction = "去设置"
            )
        )

    fun optionSheet(): BookshelfSearchSettingsUiState =
        fromFixture().copy(displayState = BookshelfSearchSettingsDisplayState.OptionSheet)

    fun confirm(): BookshelfSearchSettingsUiState =
        fromFixture().copy(displayState = BookshelfSearchSettingsDisplayState.Confirm)

    fun loading(): BookshelfSearchSettingsUiState =
        fromFixture().copy(displayState = BookshelfSearchSettingsDisplayState.Loading)

    fun error(): BookshelfSearchSettingsUiState =
        fromFixture().copy(displayState = BookshelfSearchSettingsDisplayState.Error)

    fun permission(): BookshelfSearchSettingsUiState =
        fromFixture().copy(displayState = BookshelfSearchSettingsDisplayState.Permission)
}
