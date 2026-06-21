package com.reader.android.ui.bookshelf

enum class BookshelfActionSheetDisplayState {
    Default,
    Danger,
    Loading,
    Error
}

enum class BookshelfActionTone {
    Normal,
    Danger
}

data class BookshelfActionBookUiModel(
    val title: String,
    val author: String,
    val chapter: String,
    val progress: Float,
    val coverLabel: String,
    val selected: Boolean = false
)

data class BookshelfActionItemUiModel(
    val type: String,
    val title: String,
    val copy: String,
    val tone: BookshelfActionTone
)

data class BookshelfActionConfirmUiModel(
    val title: String,
    val message: String,
    val cancelLabel: String,
    val confirmLabel: String,
    val loadingLabel: String
)

data class BookshelfActionFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String
)

data class BookshelfActionSheetDesignUiState(
    val statusTime: String = "9:41",
    val backdropTitle: String = "书架",
    val groups: List<BookshelfEmptyGroupUiModel>,
    val backdropBooks: List<BookshelfActionBookUiModel>,
    val selectedBook: BookshelfActionBookUiModel,
    val actions: List<BookshelfActionItemUiModel>,
    val confirm: BookshelfActionConfirmUiModel,
    val displayState: BookshelfActionSheetDisplayState = BookshelfActionSheetDisplayState.Default,
    val feedback: BookshelfActionFeedbackUiModel? = null
)

object BookshelfActionSheetMapper {
    fun fromFixture(): BookshelfActionSheetDesignUiState =
        BookshelfActionSheetDesignUiState(
            groups = BookshelfActionSheetFixture.groups,
            backdropBooks = BookshelfActionSheetFixture.backdropBooks,
            selectedBook = BookshelfActionSheetFixture.selectedBook,
            actions = BookshelfActionSheetFixture.actions,
            confirm = BookshelfActionSheetFixture.confirm
        )

    fun danger(): BookshelfActionSheetDesignUiState =
        fromFixture().copy(displayState = BookshelfActionSheetDisplayState.Danger)

    fun loading(): BookshelfActionSheetDesignUiState =
        fromFixture().copy(
            displayState = BookshelfActionSheetDisplayState.Loading,
            feedback = BookshelfActionSheetFixture.loadingFeedback
        )

    fun error(): BookshelfActionSheetDesignUiState =
        fromFixture().copy(
            displayState = BookshelfActionSheetDisplayState.Error,
            feedback = BookshelfActionSheetFixture.errorFeedback
        )
}

object BookshelfActionSheetFixture {
    val groups = listOf(
        BookshelfEmptyGroupUiModel("全部", active = true),
        BookshelfEmptyGroupUiModel("长篇追读", active = false),
        BookshelfEmptyGroupUiModel("资料", active = false),
        BookshelfEmptyGroupUiModel("未分组", active = false)
    )

    val selectedBook = BookshelfActionBookUiModel(
        title = "深空信号",
        author = "林间",
        chapter = "第三章 回声",
        progress = 0.42f,
        coverLabel = "深空信号",
        selected = true
    )

    val backdropBooks = listOf(
        selectedBook,
        BookshelfActionBookUiModel(
            title = "纸上群山",
            author = "南溪",
            chapter = "第一章 雨线",
            progress = 0.16f,
            coverLabel = "纸上群山"
        ),
        BookshelfActionBookUiModel(
            title = "雨线手记",
            author = "苏叶",
            chapter = "第二章 归档",
            progress = 0.63f,
            coverLabel = "雨线手记"
        )
    )

    val actions = listOf(
        BookshelfActionItemUiModel(
            type = "edit",
            title = "修改",
            copy = "进入书籍详情，修改分组、来源、缓存等配置",
            tone = BookshelfActionTone.Normal
        ),
        BookshelfActionItemUiModel(
            type = "delete",
            title = "删除",
            copy = "从书架移除，不会删除本地文件或网络来源",
            tone = BookshelfActionTone.Danger
        )
    )

    val confirm = BookshelfActionConfirmUiModel(
        title = "删除书架记录？",
        message = "从书架移除，不会删除本地文件或网络来源",
        cancelLabel = "取消",
        confirmLabel = "确认移除",
        loadingLabel = "移除中"
    )

    val loadingFeedback = BookshelfActionFeedbackUiModel(
        title = "正在加载",
        message = "正在移除书架记录，请勿重复点击确认移除。",
        primaryAction = "移除中",
        secondaryAction = "取消"
    )

    val errorFeedback = BookshelfActionFeedbackUiModel(
        title = "操作失败，请重试",
        message = "移除书架失败，已保留当前书架、筛选条件和这本书的上下文。",
        primaryAction = "重试",
        secondaryAction = "取消"
    )
}
