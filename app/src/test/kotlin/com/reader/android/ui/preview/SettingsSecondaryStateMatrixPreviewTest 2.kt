package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SettingsSecondaryStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt")))
    }

    @Test
    fun `settings secondary compose previews expose webdav backup sync and remote book states`() {
        listOf(
            "SettingsWebDavNotConfiguredPreview",
            "SettingsWebDavConfiguredPreview",
            "SettingsWebDavAuthErrorPreview",
            "SettingsBackupEnabledPreview",
            "SettingsBackupDisabledPreview",
            "SettingsProgressSyncRunningPreview",
            "SettingsProgressSyncConflictPreview",
            "SettingsProgressSyncOfflinePreview",
            "SettingsRemoteBooksDefaultPreview",
            "SettingsRemoteBooksLoadingPreview",
            "SettingsRemoteBooksEmptyPreview",
            "SettingsRemoteBooksErrorPreview"
        ).forEach { token ->
            assertTrue("Settings secondary preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `settings secondary previews use sync state mappers and explicit state variants`() {
        listOf(
            "DiscoverRssWebDavMapper.webDavNotConfigured",
            "DiscoverRssWebDavMapper.webDavConfigured",
            "DiscoverRssWebDavMapper.webDavAuthError",
            "DiscoverRssWebDavMapper.backup(enabled = true)",
            "DiscoverRssWebDavMapper.backup(enabled = false)",
            "ProgressSyncState.Running",
            "ProgressSyncState.Conflict",
            "ProgressSyncState.Offline",
            "DiscoverRssWebDavMapper.remoteBooks",
            "RemoteWebDavBooksUiState(books = emptyList(), isLoading = true)",
            "RemoteWebDavBooksUiState(books = emptyList())",
            "SyncErrorUiState"
        ).forEach { token ->
            assertTrue("Settings secondary preview source must use $token", token in previewSource)
        }
    }
}
