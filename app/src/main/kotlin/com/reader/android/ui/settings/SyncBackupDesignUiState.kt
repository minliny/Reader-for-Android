package com.reader.android.ui.settings

enum class SyncBackupDisplayState {
    Default,
    Confirm,
    Loading,
    Empty,
    Error,
    Offline,
    Permission
}

enum class SyncBackupRowType {
    Link,
    Switch,
    Select
}

data class SyncBackupTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class SyncBackupRowUiModel(
    val type: SyncBackupRowType,
    val icon: String,
    val title: String,
    val meta: String,
    val value: String = "",
    val actionLabel: String = "",
    val enabled: Boolean = false,
    val status: String = "",
    val statusTone: String = ""
)

data class SyncBackupSectionUiModel(
    val title: String,
    val rows: List<SyncBackupRowUiModel>
)

data class BackupRecordUiModel(
    val icon: String,
    val title: String,
    val meta: String,
    val status: String,
    val tone: String
)

data class RestoreConfirmUiModel(
    val title: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class SyncBackupEmptyUiModel(
    val title: String,
    val copy: String,
    val primaryAction: String
)

data class SyncBackupToastUiModel(
    val success: String,
    val error: String,
    val offline: String,
    val permission: String
)

data class SyncBackupFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class SyncBackupUiState(
    val topBar: SyncBackupTopBarUiModel,
    val sections: List<SyncBackupSectionUiModel>,
    val records: List<BackupRecordUiModel>,
    val confirm: RestoreConfirmUiModel,
    val empty: SyncBackupEmptyUiModel,
    val toast: SyncBackupToastUiModel,
    val loading: SyncBackupFeedbackUiModel,
    val error: SyncBackupFeedbackUiModel,
    val offline: SyncBackupFeedbackUiModel,
    val permission: SyncBackupFeedbackUiModel,
    val displayState: SyncBackupDisplayState = SyncBackupDisplayState.Default
) {
    val localBackupRows: List<SyncBackupRowUiModel> get() = sections.first { it.title == "本地备份" }.rows
    val webDavRows: List<SyncBackupRowUiModel> get() = sections.first { it.title == "WebDAV" }.rows
    val syncStatusRows: List<SyncBackupRowUiModel> get() = sections.first { it.title == "同步状态" }.rows
}

object SyncBackupMapper {
    fun fromFixture(): SyncBackupUiState =
        SyncBackupUiState(
            topBar = SyncBackupTopBarUiModel(
                title = "同步与备份",
                backLabel = "返回"
            ),
            sections = listOf(
                SyncBackupSectionUiModel(
                    title = "本地备份",
                    rows = listOf(
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "clock",
                            title = "立即备份",
                            meta = "手动备份所有数据到本地",
                            value = "今天 10:30"
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Switch,
                            icon = "clock",
                            title = "自动备份",
                            meta = "每天保留最近 7 份备份",
                            enabled = true
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Select,
                            icon = "folder",
                            title = "备份位置",
                            meta = "选择本地备份文件存储位置",
                            value = "内部存储"
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "upload",
                            title = "导出数据",
                            meta = "将数据导出为文件到本地"
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "download",
                            title = "恢复备份",
                            meta = "从备份文件导入数据并恢复"
                        )
                    )
                ),
                SyncBackupSectionUiModel(
                    title = "WebDAV",
                    rows = listOf(
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "source",
                            title = "WebDAV 未配置",
                            meta = "配置 WebDAV 以同步数据",
                            actionLabel = "去配置"
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Switch,
                            icon = "refresh",
                            title = "自动同步阅读进度",
                            meta = "通过 WebDAV 自动同步阅读进度",
                            enabled = false
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Select,
                            icon = "warning",
                            title = "同步冲突处理",
                            meta = "当出现数据冲突时如何处理",
                            value = "询问我"
                        )
                    )
                ),
                SyncBackupSectionUiModel(
                    title = "同步状态",
                    rows = listOf(
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "refresh",
                            title = "上次同步：尚未同步",
                            meta = "暂无同步记录",
                            actionLabel = "立即同步"
                        ),
                        SyncBackupRowUiModel(
                            type = SyncBackupRowType.Link,
                            icon = "warning",
                            title = "同步失败",
                            meta = "WebDAV 未配置时无法自动同步",
                            status = "待配置",
                            statusTone = "warn"
                        )
                    )
                )
            ),
            records = listOf(
                BackupRecordUiModel(
                    icon = "file",
                    title = "备份记录 2026-06-20 10:30",
                    meta = "本地备份 · 12.8 MB",
                    status = "可恢复",
                    tone = "good"
                ),
                BackupRecordUiModel(
                    icon = "file",
                    title = "备份记录 2026-06-19 22:10",
                    meta = "本地备份 · 12.4 MB",
                    status = "已校验",
                    tone = "info"
                )
            ),
            confirm = RestoreConfirmUiModel(
                title = "恢复备份？",
                copy = "恢复备份会覆盖当前书架、阅读进度和设置，请确认已完成当前数据备份。",
                cancelLabel = "取消",
                confirmLabel = "确认恢复"
            ),
            empty = SyncBackupEmptyUiModel(
                title = "暂无备份记录",
                copy = "立即备份后会在这里显示备份记录。",
                primaryAction = "立即备份"
            ),
            toast = SyncBackupToastUiModel(
                success = "保存成功",
                error = "同步失败",
                offline = "网络不可用，请稍后重试",
                permission = "需要文件访问权限"
            ),
            loading = SyncBackupFeedbackUiModel(
                title = "备份读取态",
                message = "读取备份记录时保留页面骨架。"
            ),
            error = SyncBackupFeedbackUiModel(
                title = "同步失败态",
                message = "同步失败保留上下文并显示失败提示。",
                primaryAction = "重试"
            ),
            offline = SyncBackupFeedbackUiModel(
                title = "离线同步态",
                message = "离线时阻断 WebDAV 同步，不影响本地备份查看。",
                primaryAction = "使用本地备份"
            ),
            permission = SyncBackupFeedbackUiModel(
                title = "文件权限态",
                message = "导出、恢复备份需要文件访问权限。",
                primaryAction = "去授权"
            )
        )

    fun confirm(): SyncBackupUiState =
        fromFixture().copy(displayState = SyncBackupDisplayState.Confirm)

    fun loading(): SyncBackupUiState =
        fromFixture().copy(displayState = SyncBackupDisplayState.Loading)

    fun empty(): SyncBackupUiState =
        fromFixture().copy(
            displayState = SyncBackupDisplayState.Empty,
            records = emptyList()
        )

    fun error(): SyncBackupUiState =
        fromFixture().copy(displayState = SyncBackupDisplayState.Error)

    fun offline(): SyncBackupUiState =
        fromFixture().copy(displayState = SyncBackupDisplayState.Offline)

    fun permission(): SyncBackupUiState =
        fromFixture().copy(displayState = SyncBackupDisplayState.Permission)
}
