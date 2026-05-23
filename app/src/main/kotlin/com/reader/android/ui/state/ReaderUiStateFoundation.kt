package com.reader.android.ui.state

data class ReaderLoadingState(
    val message: String = "加载中"
)

data class ReaderEmptyState(
    val title: String,
    val message: String? = null
)

data class ReaderErrorState(
    val title: String = "加载失败",
    val message: String,
    val retryable: Boolean = true
)

data class ReaderOfflineState(
    val title: String = "当前离线",
    val message: String = "请检查网络后重试"
)

data class ReaderPermissionState(
    val permission: String,
    val message: String
)

data class ReaderActionState(
    val id: String,
    val label: String,
    val enabled: Boolean = true
)

data class ReaderListItemState(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val meta: String? = null,
    val selected: Boolean = false
)

data class ReaderListState(
    val items: List<ReaderListItemState> = emptyList(),
    val empty: ReaderEmptyState? = null,
    val loading: ReaderLoadingState? = null,
    val error: ReaderErrorState? = null
)

data class ReaderScreenState(
    val screenKey: String,
    val title: String,
    val uiState: ReaderUiState,
    val listState: ReaderListState = ReaderListState(),
    val actions: List<ReaderActionState> = emptyList(),
    val summary: String? = null,
    val blockedReason: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
