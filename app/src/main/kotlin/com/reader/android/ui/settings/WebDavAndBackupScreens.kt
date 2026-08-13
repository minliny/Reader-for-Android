package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderListItem
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSecondaryButton
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.ReaderSettingsSwitchRow
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.sync.BackupSettingsUiState
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.ProgressSyncState
import com.reader.android.ui.sync.ProgressSyncStatusUiState
import com.reader.android.ui.sync.RemoteWebDavBooksUiState
import com.reader.android.ui.sync.WebDavAuthState
import com.reader.android.ui.sync.WebDavConfigUiState
import com.reader.android.ui.sync.WebDavSyncState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun WebDavConfigScreen(
    initialServer: String = "",
    initialUsername: String = "",
    webDavState: WebDavConfigUiState = if (initialServer.isBlank()) {
        DiscoverRssWebDavMapper.webDavNotConfigured()
    } else {
        DiscoverRssWebDavMapper.webDavConfigured()
    },
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onLogin: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var server by remember { mutableStateOf(initialServer) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }

    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "WebDAV 配置",
                onNavigateBack = onBack,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ReaderTheme.colors.controlInk,
                    unfocusedTextColor = ReaderTheme.colors.controlInk,
                    focusedContainerColor = ReaderTheme.colors.metaBg,
                    unfocusedContainerColor = ReaderTheme.colors.metaBg,
                    focusedBorderColor = ReaderTheme.colors.primary,
                    unfocusedBorderColor = ReaderTheme.colors.controlBorder,
                    cursorColor = ReaderTheme.colors.primary
                )

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("服务器地址", color = ReaderTheme.colors.bodyText) },
                    singleLine = true,
                    textStyle = ReaderTheme.typography.bookTitle,
                    colors = fieldColors,
                    shape = ReaderTheme.shapes.chip
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("用户名", color = ReaderTheme.colors.bodyText) },
                    singleLine = true,
                    textStyle = ReaderTheme.typography.bookTitle,
                    colors = fieldColors,
                    shape = ReaderTheme.shapes.chip
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("密码", color = ReaderTheme.colors.bodyText) },
                    singleLine = true,
                    textStyle = ReaderTheme.typography.bookTitle,
                    colors = fieldColors,
                    shape = ReaderTheme.shapes.chip,
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))

                ReaderPrimaryButton(
                    text = "登录并测试",
                    onClick = { onLogin(server, username, password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = webDavState.canConnect || server.isNotBlank(),
                    contentDescription = "登录并测试"
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                ReaderSettingsRow(
                    title = if (webDavState.account.isConfigured) "账号状态" else "配置状态",
                    subtitle = webDavState.notice,
                    trailing = {
                        ReaderChip(
                            text = webDavState.authState.label(),
                            selected = webDavState.authState == WebDavAuthState.Configured
                        )
                    }
                )
                webDavState.error?.let { error ->
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                    ReaderErrorState(title = error.title, message = error.message, modifier = Modifier.fillMaxWidth())
                }
            }
            }
        }
    }
}
}

@Composable
fun BackupSettingsScreen(
    autoBackupEnabled: Boolean = true,
    webDavBackupEnabled: Boolean = true,
    backupState: BackupSettingsUiState = DiscoverRssWebDavMapper.backup(autoBackupEnabled && webDavBackupEnabled),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onAutoBackupToggle: (Boolean) -> Unit = {},
    onWebDavBackupClick: () -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
        Column(modifier = modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "备份设置",
                onNavigateBack = onBack,
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多",
                        onClick = onMoreClick
                    )
                }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(ReaderTheme.spacing.screenPadding)
            ) {
                ReaderSettingsSwitchRow(
                    title = "自动备份",
                    checked = backupState.autoBackupEnabled,
                    onCheckedChange = onAutoBackupToggle
                )
                ReaderSettingsRow(
                    title = "备份到 WebDAV",
                    subtitle = backupState.notice,
                    trailing = {
                        ReaderChip(
                            text = if (backupState.webDavBackupEnabled) "已开启" else "已关闭",
                            selected = backupState.webDavBackupEnabled
                        )
                    },
                    onClick = onWebDavBackupClick
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
                ReaderSecondaryButton(
                    text = "立即备份",
                    onClick = onWebDavBackupClick,
                    enabled = backupState.webDavBackupEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    contentDescription = "立即备份"
                )
                Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
                Text(
                    "上次备份：${backupState.lastBackupTime}",
                    color = ReaderTheme.colors.bodyText,
                    style = ReaderTheme.typography.bookMeta
                )
            }
            }
        }
    }
}
}

@Composable
fun ProgressSyncStatusScreen(
    progressSyncState: ProgressSyncStatusUiState = DiscoverRssWebDavMapper.progressSync(),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onToggleProgressSync: (Boolean) -> Unit = {},
    onResolveSyncConflict: (String) -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
                Column(modifier = modifier.fillMaxSize()) {
                    ReaderAppTopBar(
                        title = "进度同步",
                        onNavigateBack = onBack,
                        actions = {
                            ReaderIconButton(
                                icon = ReaderIconToken.More.asImageVector(),
                                contentDescription = "更多",
                                onClick = onMoreClick
                            )
                        }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(ReaderTheme.spacing.screenPadding)
                    ) {
                        ReaderSettingsSwitchRow(
                            title = "进度同步",
                            subtitle = progressSyncState.syncState.label(),
                            checked = progressSyncState.isEnabled,
                            onCheckedChange = onToggleProgressSync
                        )
                        ReaderSettingsRow(
                            title = "上次同步",
                            subtitle = progressSyncState.lastSyncTime,
                            trailing = {
                                ReaderChip(
                                    text = progressSyncState.syncState.label(),
                                    selected = progressSyncState.syncState == ProgressSyncState.Success
                                )
                            }
                        )
                        if (progressSyncState.syncState == ProgressSyncState.Running) {
                            ReaderLoadingState(message = "正在同步", modifier = Modifier.fillMaxWidth())
                        }
                        progressSyncState.error?.let { error ->
                            ReaderErrorState(title = error.title, message = error.message, modifier = Modifier.fillMaxWidth())
                        }
                        if (progressSyncState.conflictCount > 0) {
                            ReaderSecondaryButton(
                                text = "处理 ${progressSyncState.conflictCount} 个冲突",
                                onClick = { onResolveSyncConflict("local") },
                                modifier = Modifier.fillMaxWidth(),
                                contentDescription = "处理同步冲突"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteWebDavBooksScreen(
    remoteState: RemoteWebDavBooksUiState = DiscoverRssWebDavMapper.remoteBooks(),
    modifier: Modifier = Modifier,
    uiState: ReaderUiState? = null,
    onBack: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onOpenRemoteBook: (String) -> Unit = {}
) {
    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> ReaderLoadingState(modifier = Modifier.fillMaxSize())
            is ReaderUiState.Error -> ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize())
            else -> {
                Column(modifier = modifier.fillMaxSize()) {
                    ReaderAppTopBar(
                        title = "远程书籍",
                        onNavigateBack = onBack,
                        actions = {
                            ReaderIconButton(
                                icon = ReaderIconToken.More.asImageVector(),
                                contentDescription = "更多",
                                onClick = onMoreClick
                            )
                        }
                    )
                    when {
                        remoteState.isLoading -> ReaderLoadingState(message = "正在读取远程书籍", modifier = Modifier.weight(1f))
                        remoteState.error != null -> ReaderErrorState(title = remoteState.error.title, message = remoteState.error.message, modifier = Modifier.weight(1f))
                        remoteState.isEmpty -> ReaderEmptyState(title = "暂无远程书籍", message = remoteState.emptyMessage, modifier = Modifier.weight(1f))
                        else -> LazyColumn(modifier = Modifier.weight(1f)) {
                            items(remoteState.books, key = { it.id }) { book ->
                                ReaderListItem(
                                    title = book.title,
                                    subtitle = "${book.path} · ${book.sizeLabel} · ${book.syncState.label()}",
                                    onClick = { onOpenRemoteBook(book.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WebDavAuthState.label(): String =
    when (this) {
        WebDavAuthState.NotConfigured -> "未配置"
        WebDavAuthState.Configured -> "已配置"
        WebDavAuthState.AuthError -> "授权失败"
        WebDavAuthState.Offline -> "离线"
    }

private fun WebDavSyncState.label(): String =
    when (this) {
        WebDavSyncState.Idle -> "待同步"
        WebDavSyncState.Running -> "同步中"
        WebDavSyncState.Success -> "已同步"
        WebDavSyncState.Failure -> "同步失败"
    }

private fun ProgressSyncState.label(): String =
    when (this) {
        ProgressSyncState.Disabled -> "已关闭"
        ProgressSyncState.Idle -> "待同步"
        ProgressSyncState.Running -> "同步中"
        ProgressSyncState.Success -> "同步成功"
        ProgressSyncState.Failure -> "同步失败"
        ProgressSyncState.Conflict -> "存在冲突"
        ProgressSyncState.Offline -> "离线"
    }
