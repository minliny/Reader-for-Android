package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPrimaryButton
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.ReaderSettingsSwitchRow
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun WebDavConfigScreen(
    initialServer: String = "",
    initialUsername: String = "",
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
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
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
                    contentDescription = "登录并测试"
                )
            }
            }
        }
    }
}

@Composable
fun BackupSettingsScreen(
    autoBackupEnabled: Boolean = true,
    webDavBackupEnabled: Boolean = true,
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
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Filled.MoreVert, "更多", tint = ReaderTheme.colors.controlInk)
                    }
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
                    checked = autoBackupEnabled,
                    onCheckedChange = onAutoBackupToggle
                )
                ReaderSettingsRow(
                    title = "备份到 WebDAV",
                    trailing = {
                        ReaderChip(
                            text = if (webDavBackupEnabled) "已开启" else "已关闭",
                            selected = webDavBackupEnabled
                        )
                    },
                    onClick = onWebDavBackupClick
                )
            }
            }
        }
    }
}
}
}
