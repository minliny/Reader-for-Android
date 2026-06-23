package com.reader.android.ui.bookshelf

enum class BookshelfGroupManagementDisplayState {
    Default,
    New,
    Rename,
    Delete,
    Empty,
    Loading,
    Error
}

data class BookshelfGroupRowUiModel(
    val id: String,
    val title: String,
    val count: Int,
    val meta: String,
    val system: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canReorder: Boolean
)

data class BookshelfGroupDialogUiModel(
    val newTitle: String = "新建分组",
    val renameTitle: String = "重命名",
    val inputPlaceholder: String = "输入分组名称",
    val helper: String,
    val cancelLabel: String = "取消",
    val saveLabel: String = "保存",
    val savingLabel: String = "保存中",
    val renameValue: String = "长篇追读"
)

data class BookshelfGroupDeleteConfirmUiModel(
    val title: String = "删除分组",
    val message: String,
    val cancelLabel: String = "取消",
    val confirmLabel: String = "删除分组",
    val loadingLabel: String = "删除中"
)

data class BookshelfGroupFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String? = null
)

data class BookshelfGroupManagementUiState(
    val title: String = "分组管理",
    val backLabel: String = "返回",
    val addLabel: String = "新建",
    val groups: List<BookshelfGroupRowUiModel>,
    val dialog: BookshelfGroupDialogUiModel,
    val deleteConfirm: BookshelfGroupDeleteConfirmUiModel,
    val displayState: BookshelfGroupManagementDisplayState = BookshelfGroupManagementDisplayState.Default,
    val dialogValue: String = "",
    val feedback: BookshelfGroupFeedbackUiModel? = null,
    val toastMessage: String? = null
) {
    val visibleGroups: List<BookshelfGroupRowUiModel> get() =
        if (displayState == BookshelfGroupManagementDisplayState.Empty) {
            groups.filter { it.id == "all" || it.id == "ungrouped" }
        } else {
            groups
        }

    val hasDialog: Boolean get() =
        displayState == BookshelfGroupManagementDisplayState.New ||
            displayState == BookshelfGroupManagementDisplayState.Rename ||
            displayState == BookshelfGroupManagementDisplayState.Loading ||
            displayState == BookshelfGroupManagementDisplayState.Error ||
            displayState == BookshelfGroupManagementDisplayState.Delete
}

object BookshelfGroupManagementMapper {
    fun fromFixture(): BookshelfGroupManagementUiState =
        BookshelfGroupManagementUiState(
            groups = BookshelfGroupManagementFixture.groups,
            dialog = BookshelfGroupManagementFixture.dialog,
            deleteConfirm = BookshelfGroupManagementFixture.deleteConfirm
        )

    fun newGroup(): BookshelfGroupManagementUiState =
        fromFixture().copy(
            displayState = BookshelfGroupManagementDisplayState.New,
            dialogValue = ""
        )

    fun rename(): BookshelfGroupManagementUiState =
        fromFixture().copy(
            displayState = BookshelfGroupManagementDisplayState.Rename,
            dialogValue = BookshelfGroupManagementFixture.dialog.renameValue
        )

    fun delete(): BookshelfGroupManagementUiState =
        fromFixture().copy(displayState = BookshelfGroupManagementDisplayState.Delete)

    fun empty(): BookshelfGroupManagementUiState =
        fromFixture().copy(
            displayState = BookshelfGroupManagementDisplayState.Empty,
            feedback = BookshelfGroupManagementFixture.emptyFeedback
        )

    fun loading(): BookshelfGroupManagementUiState =
        rename().copy(
            displayState = BookshelfGroupManagementDisplayState.Loading,
            feedback = BookshelfGroupManagementFixture.loadingFeedback
        )

    fun error(message: String = BookshelfGroupManagementFixture.errorFeedback.message): BookshelfGroupManagementUiState =
        rename().copy(
            displayState = BookshelfGroupManagementDisplayState.Error,
            feedback = BookshelfGroupManagementFixture.errorFeedback.copy(message = message)
        )

    fun fromGroupLabels(labels: List<String>): BookshelfGroupManagementUiState =
        fromFixture().copy(
            groups = labels.mapIndexed { index, label ->
                BookshelfGroupRowUiModel(
                    id = label,
                    title = label,
                    count = 0,
                    meta = if (index == 0) "系统聚合入口，不可编辑" else "0 本",
                    system = index == 0,
                    canRename = index != 0,
                    canDelete = index != 0 && label != "未分组",
                    canReorder = index != 0
                )
            }
        )
}

object BookshelfGroupManagementFixture {
    val groups = listOf(
        BookshelfGroupRowUiModel(
            id = "all",
            title = "全部",
            count = 12,
            meta = "系统聚合入口，不可编辑",
            system = true,
            canRename = false,
            canDelete = false,
            canReorder = false
        ),
        BookshelfGroupRowUiModel(
            id = "long",
            title = "长篇追读",
            count = 5,
            meta = "5 本",
            system = false,
            canRename = true,
            canDelete = true,
            canReorder = true
        ),
        BookshelfGroupRowUiModel(
            id = "data",
            title = "资料",
            count = 3,
            meta = "3 本",
            system = false,
            canRename = true,
            canDelete = true,
            canReorder = true
        ),
        BookshelfGroupRowUiModel(
            id = "ungrouped",
            title = "未分组",
            count = 2,
            meta = "2 本 · 不可删除",
            system = true,
            canRename = true,
            canDelete = false,
            canReorder = true
        )
    )

    val dialog = BookshelfGroupDialogUiModel(
        helper = "分组名称为空时保存不可用；重复时提示分组名称已存在。"
    )

    val deleteConfirm = BookshelfGroupDeleteConfirmUiModel(
        message = "分组内 5 本书将移入未分组，不会删除书籍。"
    )

    val emptyFeedback = BookshelfGroupFeedbackUiModel(
        title = "暂无自定义分组",
        message = "当前只保留系统分组，可以新建分组后再管理书籍。",
        primaryAction = "新建分组"
    )

    val loadingFeedback = BookshelfGroupFeedbackUiModel(
        title = "正在加载",
        message = "正在保存分组调整，请勿重复操作。",
        primaryAction = "保存中"
    )

    val errorFeedback = BookshelfGroupFeedbackUiModel(
        title = "操作失败，请重试",
        message = "保存失败，已保留原分组顺序和名称。",
        primaryAction = "重试",
        secondaryAction = "取消"
    )

    const val successToast = "保存成功"
}
