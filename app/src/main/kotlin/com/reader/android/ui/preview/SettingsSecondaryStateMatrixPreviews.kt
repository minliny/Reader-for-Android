package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.settings.BackupSettingsScreen
import com.reader.android.ui.settings.ProgressSyncStatusScreen
import com.reader.android.ui.settings.RemoteWebDavBooksScreen
import com.reader.android.ui.settings.WebDavConfigScreen
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.ProgressSyncState
import com.reader.android.ui.sync.RemoteWebDavBooksUiState
import com.reader.android.ui.sync.SyncErrorUiState

private const val SettingsPreviewWidth = 390
private const val SettingsPreviewHeight = 844

@Preview(name = "Settings Secondary / WebDAV / Not Configured", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsWebDavNotConfiguredPreview() {
    WebDavConfigScreen(webDavState = DiscoverRssWebDavMapper.webDavNotConfigured())
}

@Preview(name = "Settings Secondary / WebDAV / Configured", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsWebDavConfiguredPreview() {
    WebDavConfigScreen(
        initialServer = "https://dav.fixture.local",
        initialUsername = "fixture-user",
        webDavState = DiscoverRssWebDavMapper.webDavConfigured()
    )
}

@Preview(name = "Settings Secondary / WebDAV / Auth Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsWebDavAuthErrorPreview() {
    WebDavConfigScreen(
        initialServer = "https://dav.fixture.local",
        initialUsername = "fixture-user",
        webDavState = DiscoverRssWebDavMapper.webDavAuthError()
    )
}

@Preview(name = "Settings Secondary / Backup / Enabled", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBackupEnabledPreview() {
    BackupSettingsScreen(backupState = DiscoverRssWebDavMapper.backup(enabled = true))
}

@Preview(name = "Settings Secondary / Backup / Disabled", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBackupDisabledPreview() {
    BackupSettingsScreen(
        autoBackupEnabled = false,
        webDavBackupEnabled = false,
        backupState = DiscoverRssWebDavMapper.backup(enabled = false)
    )
}

@Preview(name = "Settings Secondary / Progress Sync / Running", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsProgressSyncRunningPreview() {
    ProgressSyncStatusScreen(progressSyncState = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Running))
}

@Preview(name = "Settings Secondary / Progress Sync / Conflict", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsProgressSyncConflictPreview() {
    ProgressSyncStatusScreen(progressSyncState = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Conflict))
}

@Preview(name = "Settings Secondary / Progress Sync / Offline", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsProgressSyncOfflinePreview() {
    ProgressSyncStatusScreen(progressSyncState = DiscoverRssWebDavMapper.progressSync(ProgressSyncState.Offline))
}

@Preview(name = "Settings Secondary / Remote Books / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsRemoteBooksDefaultPreview() {
    RemoteWebDavBooksScreen(remoteState = DiscoverRssWebDavMapper.remoteBooks())
}

@Preview(name = "Settings Secondary / Remote Books / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsRemoteBooksLoadingPreview() {
    RemoteWebDavBooksScreen(remoteState = RemoteWebDavBooksUiState(books = emptyList(), isLoading = true))
}

@Preview(name = "Settings Secondary / Remote Books / Empty", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsRemoteBooksEmptyPreview() {
    RemoteWebDavBooksScreen(remoteState = RemoteWebDavBooksUiState(books = emptyList()))
}

@Preview(name = "Settings Secondary / Remote Books / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsRemoteBooksErrorPreview() {
    RemoteWebDavBooksScreen(
        remoteState = RemoteWebDavBooksUiState(
            books = emptyList(),
            error = SyncErrorUiState("远程书籍读取失败", "请检查 WebDAV 连接后重试")
        )
    )
}
