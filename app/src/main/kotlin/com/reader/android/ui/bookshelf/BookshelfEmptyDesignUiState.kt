package com.reader.android.ui.bookshelf

enum class BookshelfEmptyDisplayState {
    Default,
    AllEmpty,
    Loading,
    Error,
    Offline,
    Permission
}

data class BookshelfEmptyGroupUiModel(
    val label: String,
    val active: Boolean
)

data class BookshelfEmptyStateUiModel(
    val title: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String,
    val hint: String
)

data class BookshelfEmptyDesignUiState(
    val statusTime: String = "9:41",
    val title: String = "书架",
    val groups: List<BookshelfEmptyGroupUiModel>,
    val state: BookshelfEmptyStateUiModel,
    val displayState: BookshelfEmptyDisplayState = BookshelfEmptyDisplayState.Default
)

object BookshelfEmptyDesignMapper {
    fun currentGroupEmpty(): BookshelfEmptyDesignUiState =
        BookshelfEmptyDesignUiState(
            groups = BookshelfEmptyDesignFixture.groups,
            state = BookshelfEmptyDesignFixture.currentGroupEmpty
        )

    fun allEmpty(): BookshelfEmptyDesignUiState =
        currentGroupEmpty().copy(
            displayState = BookshelfEmptyDisplayState.AllEmpty,
            groups = BookshelfEmptyDesignFixture.groups.map { group ->
                group.copy(active = group.label == "全部")
            },
            state = BookshelfEmptyDesignFixture.allEmpty
        )

    fun loading(): BookshelfEmptyDesignUiState =
        currentGroupEmpty().copy(
            displayState = BookshelfEmptyDisplayState.Loading,
            state = BookshelfEmptyDesignFixture.loading
        )

    fun error(): BookshelfEmptyDesignUiState =
        currentGroupEmpty().copy(
            displayState = BookshelfEmptyDisplayState.Error,
            state = BookshelfEmptyDesignFixture.error
        )

    fun offline(): BookshelfEmptyDesignUiState =
        currentGroupEmpty().copy(
            displayState = BookshelfEmptyDisplayState.Offline,
            state = BookshelfEmptyDesignFixture.offline
        )

    fun permission(): BookshelfEmptyDesignUiState =
        currentGroupEmpty().copy(
            displayState = BookshelfEmptyDisplayState.Permission,
            state = BookshelfEmptyDesignFixture.permission
        )
}

object BookshelfEmptyDesignFixture {
    val groups = listOf(
        BookshelfEmptyGroupUiModel("全部", active = false),
        BookshelfEmptyGroupUiModel("长篇追读", active = true),
        BookshelfEmptyGroupUiModel("资料", active = false),
        BookshelfEmptyGroupUiModel("未分组", active = false)
    )

    val currentGroupEmpty = BookshelfEmptyStateUiModel(
        title = "当前分组没有书籍",
        message = "可以切换分组，或把书籍移动到这个分组",
        primaryAction = "搜索书籍",
        secondaryAction = "管理分组",
        hint = "全书架为空变体：书架还是空的 / 导入本地书"
    )

    val allEmpty = BookshelfEmptyStateUiModel(
        title = "书架暂无书籍",
        message = "添加书籍后会出现在这里，也可以先导入本地书",
        primaryAction = "添加书籍",
        secondaryAction = "导入本地书",
        hint = "本地导入不是 P0 强制流程，点击后进入本地书导入页"
    )

    val loading = BookshelfEmptyStateUiModel(
        title = "正在加载",
        message = "正在检查书架数据，常用入口和底部导航保持可用",
        primaryAction = "",
        secondaryAction = "",
        hint = "首次进入可使用骨架屏，局部刷新不清空整页"
    )

    val error = BookshelfEmptyStateUiModel(
        title = "加载失败，请重试",
        message = "书架数据读取失败，保留当前分组和返回入口",
        primaryAction = "重试",
        secondaryAction = "换个分组看看",
        hint = "失败时保留用户当前上下文"
    )

    val offline = BookshelfEmptyStateUiModel(
        title = "网络不可用，请稍后重试",
        message = "联网搜索暂不可用，本地导入和切换分组仍可使用",
        primaryAction = "导入本地书",
        secondaryAction = "换个分组看看",
        hint = "网络不可用只阻断依赖网络的动作"
    )

    val permission = BookshelfEmptyStateUiModel(
        title = "导入前再请求权限",
        message = "选择本地书时说明用途，不提前请求全盘权限",
        primaryAction = "选择本地书",
        secondaryAction = "取消",
        hint = "未授权不得进入空白页"
    )
}
