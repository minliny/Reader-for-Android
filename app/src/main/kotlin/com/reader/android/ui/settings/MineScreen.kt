package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.reader.android.BuildConfig
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderSettingsGroup
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun SettingsRootScreen(
    onSourceManagementClick: () -> Unit = {},
    onGlobalSettingsClick: () -> Unit = {},
    onWebDavClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onProgressSyncClick: () -> Unit = {},
    onRemoteBooksClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPrototypeGalleryClick: (() -> Unit)? = null
) {
    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(title = "设置")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSettingsGroup(title = "同步与备份") {
                    ReaderSettingsRow(
                        title = "WebDAV 配置",
                        subtitle = "配置远程同步账号与连接状态",
                        onClick = onWebDavClick
                    )
                    ReaderSettingsRow(
                        title = "备份设置",
                        subtitle = "管理本地与远程备份开关",
                        onClick = onBackupClick
                    )
                    ReaderSettingsRow(
                        title = "阅读进度同步",
                        subtitle = "查看同步状态与冲突",
                        onClick = onProgressSyncClick
                    )
                    ReaderSettingsRow(
                        title = "远程 WebDAV 书籍",
                        subtitle = "查看已同步的远程书籍",
                        onClick = onRemoteBooksClick
                    )
                }

                ReaderSettingsGroup(title = "阅读与应用设置") {
                    ReaderSettingsRow(
                        title = "书源管理",
                        subtitle = "管理书源、检测状态与导入配置",
                        onClick = onSourceManagementClick
                    )
                    ReaderSettingsRow(
                        title = "全局阅读设置",
                        subtitle = "字号、行距、页边距与夜间模式",
                        onClick = onGlobalSettingsClick
                    )
                    ReaderSettingsRow(
                        title = "缓存与下载",
                        subtitle = "本地缓存、下载和数据管理入口",
                        onClick = onGlobalSettingsClick
                    )
                    ReaderSettingsRow(
                        title = "外观设置",
                        subtitle = "主题、排版和应用外观",
                        onClick = onGlobalSettingsClick
                    )
                }

                ReaderSettingsGroup(title = "系统与关于") {
                    ReaderSettingsRow(
                        title = "关于版本",
                        subtitle = "版本信息、开源许可与更新入口",
                        onClick = onAboutClick
                    )
                    ReaderSettingsRow(
                        title = "隐私与权限",
                        subtitle = "权限说明、隐私与日志入口",
                        onClick = onAboutClick
                    )
                    if (BuildConfig.DEBUG && onPrototypeGalleryClick != null) {
                        ReaderSettingsRow(
                            title = "UI 原型预览",
                            subtitle = "打开 Reader UI Prototype Gallery",
                            onClick = onPrototypeGalleryClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MineScreen(
    onSourceManagementClick: () -> Unit = {},
    onGlobalSettingsClick: () -> Unit = {},
    onWebDavClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onProgressSyncClick: () -> Unit = {},
    onRemoteBooksClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPrototypeGalleryClick: (() -> Unit)? = null
) {
    SettingsRootScreen(
        onSourceManagementClick = onSourceManagementClick,
        onGlobalSettingsClick = onGlobalSettingsClick,
        onWebDavClick = onWebDavClick,
        onBackupClick = onBackupClick,
        onProgressSyncClick = onProgressSyncClick,
        onRemoteBooksClick = onRemoteBooksClick,
        onAboutClick = onAboutClick,
        onPrototypeGalleryClick = onPrototypeGalleryClick
    )
}
