package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.settings.AboutFeedbackMapper
import com.reader.android.ui.settings.AboutFeedbackScreen
import com.reader.android.ui.settings.BackupSettingsScreen
import com.reader.android.ui.settings.BookshelfSearchSettingsMapper
import com.reader.android.ui.settings.BookshelfSearchSettingsScreen
import com.reader.android.ui.settings.CacheManagementMapper
import com.reader.android.ui.settings.CacheManagementScreen
import com.reader.android.ui.settings.GeneralSettingsMapper
import com.reader.android.ui.settings.GeneralSettingsScreen
import com.reader.android.ui.settings.PrivacyPermissionsMapper
import com.reader.android.ui.settings.PrivacyPermissionsScreen
import com.reader.android.ui.settings.ProgressSyncStatusScreen
import com.reader.android.ui.settings.RemoteWebDavBooksScreen
import com.reader.android.ui.settings.SyncBackupMapper
import com.reader.android.ui.settings.SyncBackupScreen
import com.reader.android.ui.settings.WebDavConfigScreen
import com.reader.android.ui.sync.DiscoverRssWebDavMapper
import com.reader.android.ui.sync.ProgressSyncState
import com.reader.android.ui.sync.RemoteWebDavBooksUiState
import com.reader.android.ui.sync.SyncErrorUiState

private const val SettingsPreviewWidth = 390
private const val SettingsPreviewHeight = 844

@Preview(name = "Settings Secondary / General / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsGeneralDefaultPreview() {
    GeneralSettingsScreen(state = GeneralSettingsMapper.fromFixture())
}

@Preview(name = "Settings Secondary / General / Option Sheet", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsGeneralOptionSheetPreview() {
    GeneralSettingsScreen(state = GeneralSettingsMapper.optionSheet())
}

@Preview(name = "Settings Secondary / General / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsGeneralLoadingPreview() {
    GeneralSettingsScreen(state = GeneralSettingsMapper.loading())
}

@Preview(name = "Settings Secondary / General / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsGeneralErrorPreview() {
    GeneralSettingsScreen(state = GeneralSettingsMapper.error())
}

@Preview(name = "Settings Secondary / General / Permission", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsGeneralPermissionPreview() {
    GeneralSettingsScreen(state = GeneralSettingsMapper.permission())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchDefaultPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.fromFixture())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Option Sheet", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchOptionSheetPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.optionSheet())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Confirm", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchConfirmPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.confirm())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchLoadingPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.loading())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchErrorPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.error())
}

@Preview(name = "Settings Secondary / Bookshelf Search / Permission", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsBookshelfSearchPermissionPreview() {
    BookshelfSearchSettingsScreen(state = BookshelfSearchSettingsMapper.permission())
}

@Preview(name = "Settings Secondary / Privacy Permissions / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsPrivacyPermissionsDefaultPreview() {
    PrivacyPermissionsScreen(state = PrivacyPermissionsMapper.fromFixture())
}

@Preview(name = "Settings Secondary / Privacy Permissions / Confirm", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsPrivacyPermissionsConfirmPreview() {
    PrivacyPermissionsScreen(state = PrivacyPermissionsMapper.confirm())
}

@Preview(name = "Settings Secondary / Privacy Permissions / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsPrivacyPermissionsLoadingPreview() {
    PrivacyPermissionsScreen(state = PrivacyPermissionsMapper.loading())
}

@Preview(name = "Settings Secondary / Privacy Permissions / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsPrivacyPermissionsErrorPreview() {
    PrivacyPermissionsScreen(state = PrivacyPermissionsMapper.error())
}

@Preview(name = "Settings Secondary / Privacy Permissions / Permission", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsPrivacyPermissionsPermissionPreview() {
    PrivacyPermissionsScreen(state = PrivacyPermissionsMapper.permission())
}

@Preview(name = "Settings Secondary / Cache Management / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsCacheManagementDefaultPreview() {
    CacheManagementScreen(state = CacheManagementMapper.fromFixture())
}

@Preview(name = "Settings Secondary / Cache Management / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsCacheManagementLoadingPreview() {
    CacheManagementScreen(state = CacheManagementMapper.loading())
}

@Preview(name = "Settings Secondary / Cache Management / Empty", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsCacheManagementEmptyPreview() {
    CacheManagementScreen(state = CacheManagementMapper.empty())
}

@Preview(name = "Settings Secondary / Cache Management / Confirm", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsCacheManagementConfirmPreview() {
    CacheManagementScreen(state = CacheManagementMapper.confirm())
}

@Preview(name = "Settings Secondary / Cache Management / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsCacheManagementErrorPreview() {
    CacheManagementScreen(state = CacheManagementMapper.error())
}

@Preview(name = "Settings Secondary / About Feedback / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsAboutFeedbackDefaultPreview() {
    AboutFeedbackScreen(state = AboutFeedbackMapper.fromFixture())
}

@Preview(name = "Settings Secondary / About Feedback / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsAboutFeedbackLoadingPreview() {
    AboutFeedbackScreen(state = AboutFeedbackMapper.loading())
}

@Preview(name = "Settings Secondary / About Feedback / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsAboutFeedbackErrorPreview() {
    AboutFeedbackScreen(state = AboutFeedbackMapper.error())
}

@Preview(name = "Settings Secondary / About Feedback / Confirm", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsAboutFeedbackConfirmPreview() {
    AboutFeedbackScreen(state = AboutFeedbackMapper.confirm())
}

@Preview(name = "Settings Secondary / About Feedback / Offline", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsAboutFeedbackOfflinePreview() {
    AboutFeedbackScreen(state = AboutFeedbackMapper.offline())
}

@Preview(name = "Settings Secondary / Sync Backup / Default", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupDefaultPreview() {
    SyncBackupScreen(state = SyncBackupMapper.fromFixture())
}

@Preview(name = "Settings Secondary / Sync Backup / Confirm", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupConfirmPreview() {
    SyncBackupScreen(state = SyncBackupMapper.confirm())
}

@Preview(name = "Settings Secondary / Sync Backup / Loading", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupLoadingPreview() {
    SyncBackupScreen(state = SyncBackupMapper.loading())
}

@Preview(name = "Settings Secondary / Sync Backup / Empty", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupEmptyPreview() {
    SyncBackupScreen(state = SyncBackupMapper.empty())
}

@Preview(name = "Settings Secondary / Sync Backup / Error", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupErrorPreview() {
    SyncBackupScreen(state = SyncBackupMapper.error())
}

@Preview(name = "Settings Secondary / Sync Backup / Offline", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupOfflinePreview() {
    SyncBackupScreen(state = SyncBackupMapper.offline())
}

@Preview(name = "Settings Secondary / Sync Backup / Permission", widthDp = SettingsPreviewWidth, heightDp = SettingsPreviewHeight, showBackground = true)
@Composable
fun SettingsSyncBackupPermissionPreview() {
    SyncBackupScreen(state = SyncBackupMapper.permission())
}

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
