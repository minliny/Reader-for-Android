package com.reader.android.ui.settings

enum class PrivacyPermissionsDisplayState {
    Default,
    Confirm,
    Loading,
    Error,
    Permission
}

enum class PrivacyPermissionsRowType {
    Link,
    Switch
}

data class PrivacyPermissionsTopBarUiModel(
    val title: String,
    val backLabel: String
)

data class PrivacyPermissionsRowUiModel(
    val type: PrivacyPermissionsRowType,
    val icon: String,
    val title: String,
    val meta: String,
    val value: String = "",
    val status: String = "",
    val statusTone: String = "",
    val actionLabel: String = "",
    val enabled: Boolean = false
)

data class PrivacyPermissionsSectionUiModel(
    val title: String,
    val rows: List<PrivacyPermissionsRowUiModel>
)

data class PrivacyPermissionsDangerUiModel(
    val title: String,
    val meta: String,
    val confirmTitle: String,
    val copy: String,
    val cancelLabel: String,
    val confirmLabel: String
)

data class PrivacyPermissionsToastUiModel(
    val success: String,
    val error: String,
    val permission: String
)

data class PrivacyPermissionsFeedbackUiModel(
    val title: String,
    val message: String,
    val primaryAction: String? = null
)

data class PrivacyPermissionsUiState(
    val topBar: PrivacyPermissionsTopBarUiModel,
    val sections: List<PrivacyPermissionsSectionUiModel>,
    val danger: PrivacyPermissionsDangerUiModel,
    val toast: PrivacyPermissionsToastUiModel,
    val loading: PrivacyPermissionsFeedbackUiModel,
    val error: PrivacyPermissionsFeedbackUiModel,
    val permission: PrivacyPermissionsFeedbackUiModel,
    val displayState: PrivacyPermissionsDisplayState = PrivacyPermissionsDisplayState.Default
)

object PrivacyPermissionsMapper {
    fun fromFixture(): PrivacyPermissionsUiState =
        PrivacyPermissionsUiState(
            topBar = PrivacyPermissionsTopBarUiModel(
                title = "隐私与权限",
                backLabel = "返回"
            ),
            sections = listOf(
                PrivacyPermissionsSectionUiModel(
                    title = "系统权限",
                    rows = listOf(
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "folder",
                            title = "文件访问",
                            meta = "访问设备上的文件和媒体内容",
                            status = "已授权",
                            statusTone = "good",
                            value = "已允许"
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "bell",
                            title = "通知权限",
                            meta = "接收系统通知和消息提醒",
                            status = "未授权",
                            statusTone = "warn",
                            actionLabel = "去设置"
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "battery",
                            title = "电池优化",
                            meta = "后台运行与电池使用受系统管理",
                            status = "受系统管理",
                            statusTone = "system"
                        )
                    )
                ),
                PrivacyPermissionsSectionUiModel(
                    title = "隐私设置",
                    rows = listOf(
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Switch,
                            icon = "eyeOff",
                            title = "隐私开关",
                            meta = "不记录最近阅读与搜索历史",
                            enabled = false
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Switch,
                            icon = "clock",
                            title = "保存搜索历史",
                            meta = "记录搜索关键词以便快速访问",
                            enabled = true
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Switch,
                            icon = "bug",
                            title = "发送崩溃日志",
                            meta = "仅在你主动确认后发送",
                            enabled = false
                        )
                    )
                ),
                PrivacyPermissionsSectionUiModel(
                    title = "数据与说明",
                    rows = listOf(
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "info",
                            title = "网络访问说明",
                            meta = "说明网络访问用途和可用边界"
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "file",
                            title = "本地数据说明",
                            meta = "了解本地数据的存储与使用"
                        ),
                        PrivacyPermissionsRowUiModel(
                            type = PrivacyPermissionsRowType.Link,
                            icon = "shield",
                            title = "隐私说明",
                            meta = "查看隐私政策与相关条款"
                        )
                    )
                )
            ),
            danger = PrivacyPermissionsDangerUiModel(
                title = "清除隐私数据",
                meta = "清除所有隐私相关数据与记录",
                confirmTitle = "清除隐私数据？",
                copy = "清除后将移除搜索历史、阅读痕迹和本地隐私记录。",
                cancelLabel = "取消",
                confirmLabel = "确认清除"
            ),
            toast = PrivacyPermissionsToastUiModel(
                success = "保存成功",
                error = "操作失败，请重试",
                permission = "需要系统权限"
            ),
            loading = PrivacyPermissionsFeedbackUiModel(
                title = "权限加载态",
                message = "读取系统权限状态时保留页面结构。"
            ),
            error = PrivacyPermissionsFeedbackUiModel(
                title = "权限读取失败态",
                message = "读取失败保留入口并提供可恢复反馈。",
                primaryAction = "重试"
            ),
            permission = PrivacyPermissionsFeedbackUiModel(
                title = "系统权限态",
                message = "未授权项提供去设置入口，不能进入空白页。",
                primaryAction = "去设置"
            )
        )

    fun confirm(): PrivacyPermissionsUiState =
        fromFixture().copy(displayState = PrivacyPermissionsDisplayState.Confirm)

    fun loading(): PrivacyPermissionsUiState =
        fromFixture().copy(displayState = PrivacyPermissionsDisplayState.Loading)

    fun error(): PrivacyPermissionsUiState =
        fromFixture().copy(displayState = PrivacyPermissionsDisplayState.Error)

    fun permission(): PrivacyPermissionsUiState =
        fromFixture().copy(displayState = PrivacyPermissionsDisplayState.Permission)
}
