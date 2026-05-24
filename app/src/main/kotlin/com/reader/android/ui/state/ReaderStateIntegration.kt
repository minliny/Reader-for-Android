package com.reader.android.ui.state

enum class ReaderGlobalStateKey {
    Loading,
    Empty,
    Error,
    Offline,
    Disabled,
    PermissionRequired,
    LocalFileError,
    NetworkSourceError,
    ImportSuccess,
    ImportFailure,
    WebDavAuthError,
    SyncConflict
}

enum class ReaderModuleKey(val label: String) {
    Bookshelf("书架"),
    Search("搜索"),
    BookDetail("书籍详情"),
    Reader("阅读页"),
    SourceManagement("书源"),
    DiscoverRss("发现/RSS"),
    WebDavSync("WebDAV/同步"),
    MineSettings("我的/设置")
}

data class ReaderStatePageUiModel(
    val key: ReaderGlobalStateKey,
    val title: String,
    val message: String,
    val actionLabel: String? = null,
    val retryable: Boolean = false
)

data class ReaderModuleStateCoverage(
    val module: ReaderModuleKey,
    val states: List<ReaderGlobalStateKey>
) {
    fun covers(state: ReaderGlobalStateKey): Boolean = state in states
}

object ReaderStateMapper {
    val globalStateKeys: List<ReaderGlobalStateKey> = ReaderGlobalStateKey.entries

    fun toPageState(state: ReaderUiState): ReaderStatePageUiModel =
        when (state) {
            ReaderUiState.Loading -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.Loading,
                title = "加载中",
                message = "正在准备内容"
            )
            ReaderUiState.Empty -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.Empty,
                title = "暂无内容",
                message = "当前没有可显示的项目"
            )
            is ReaderUiState.Error -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.Error,
                title = "加载失败",
                message = state.message,
                actionLabel = if (state.retryable) "重试" else null,
                retryable = state.retryable
            )
            ReaderUiState.Offline -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.Offline,
                title = "当前离线",
                message = "恢复网络后可继续刷新",
                actionLabel = "重试",
                retryable = true
            )
            is ReaderUiState.Disabled -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.Disabled,
                title = "已停用",
                message = state.reason
            )
            is ReaderUiState.PermissionRequired -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.PermissionRequired,
                title = "需要权限",
                message = state.permission,
                actionLabel = "授权"
            )
            is ReaderUiState.LocalFileError -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.LocalFileError,
                title = "本地文件异常",
                message = state.message
            )
            is ReaderUiState.NetworkSourceError -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.NetworkSourceError,
                title = "书源异常",
                message = "${state.sourceId}: ${state.message}",
                actionLabel = "查看书源"
            )
            is ReaderUiState.ImportSuccess -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.ImportSuccess,
                title = "导入成功",
                message = state.targetId
            )
            is ReaderUiState.ImportFailure -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.ImportFailure,
                title = "导入失败",
                message = state.message,
                actionLabel = "重新导入",
                retryable = true
            )
            ReaderUiState.WebDavAuthError -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.WebDavAuthError,
                title = "WebDAV 授权失败",
                message = "请检查账号或应用密码",
                actionLabel = "重新配置"
            )
            is ReaderUiState.SyncConflict -> ReaderStatePageUiModel(
                key = ReaderGlobalStateKey.SyncConflict,
                title = "同步冲突",
                message = "本地 ${state.localVersion} / 远程 ${state.remoteVersion}",
                actionLabel = "处理冲突"
            )
        }

    fun moduleCoverage(): List<ReaderModuleStateCoverage> =
        listOf(
            ReaderModuleStateCoverage(
                ReaderModuleKey.Bookshelf,
                listOf(
                    ReaderGlobalStateKey.Loading,
                    ReaderGlobalStateKey.Empty,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.Offline,
                    ReaderGlobalStateKey.LocalFileError
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.Search,
                listOf(
                    ReaderGlobalStateKey.Loading,
                    ReaderGlobalStateKey.Empty,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.NetworkSourceError,
                    ReaderGlobalStateKey.Offline
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.BookDetail,
                listOf(
                    ReaderGlobalStateKey.Loading,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.NetworkSourceError,
                    ReaderGlobalStateKey.Offline
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.Reader,
                listOf(
                    ReaderGlobalStateKey.Loading,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.Offline,
                    ReaderGlobalStateKey.LocalFileError
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.SourceManagement,
                listOf(
                    ReaderGlobalStateKey.Disabled,
                    ReaderGlobalStateKey.ImportSuccess,
                    ReaderGlobalStateKey.ImportFailure,
                    ReaderGlobalStateKey.NetworkSourceError,
                    ReaderGlobalStateKey.Error
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.DiscoverRss,
                listOf(
                    ReaderGlobalStateKey.Loading,
                    ReaderGlobalStateKey.Empty,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.Offline,
                    ReaderGlobalStateKey.Disabled
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.WebDavSync,
                listOf(
                    ReaderGlobalStateKey.WebDavAuthError,
                    ReaderGlobalStateKey.SyncConflict,
                    ReaderGlobalStateKey.Offline,
                    ReaderGlobalStateKey.Error,
                    ReaderGlobalStateKey.Empty
                )
            ),
            ReaderModuleStateCoverage(
                ReaderModuleKey.MineSettings,
                listOf(
                    ReaderGlobalStateKey.PermissionRequired,
                    ReaderGlobalStateKey.WebDavAuthError,
                    ReaderGlobalStateKey.SyncConflict,
                    ReaderGlobalStateKey.Disabled,
                    ReaderGlobalStateKey.Error
                )
            )
        )
}

object ReaderStateFixtures {
    val globalStates: List<ReaderUiState> = listOf(
        ReaderUiState.Loading,
        ReaderUiState.Empty,
        ReaderUiState.Error("UI fixture error"),
        ReaderUiState.Offline,
        ReaderUiState.Disabled("UI fixture disabled"),
        ReaderUiState.PermissionRequired("READ_EXTERNAL_STORAGE"),
        ReaderUiState.LocalFileError("本地文件无法读取"),
        ReaderUiState.NetworkSourceError("fixture-source", "书源返回异常"),
        ReaderUiState.ImportSuccess("fixture-import"),
        ReaderUiState.ImportFailure("JSON 格式错误"),
        ReaderUiState.WebDavAuthError,
        ReaderUiState.SyncConflict("local-fixture", "remote-fixture")
    )

    val pageStates: List<ReaderStatePageUiModel> =
        globalStates.map(ReaderStateMapper::toPageState)

    val moduleCoverage: List<ReaderModuleStateCoverage> =
        ReaderStateMapper.moduleCoverage()
}
