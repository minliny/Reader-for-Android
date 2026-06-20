package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SettingsSecondaryStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt")))
    }

    private val generalSettingsScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignScreen.kt")))
    }

    private val generalSettingsStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignUiState.kt")))
    }

    @Test
    fun `settings secondary compose previews expose general webdav backup sync and remote book states`() {
        listOf(
            "SettingsGeneralDefaultPreview",
            "SettingsGeneralOptionSheetPreview",
            "SettingsGeneralLoadingPreview",
            "SettingsGeneralErrorPreview",
            "SettingsGeneralPermissionPreview",
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
    fun `settings secondary previews use setting and sync state mappers and explicit state variants`() {
        listOf(
            "GeneralSettingsMapper.fromFixture",
            "GeneralSettingsMapper.optionSheet",
            "GeneralSettingsMapper.loading",
            "GeneralSettingsMapper.error",
            "GeneralSettingsMapper.permission",
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

    @Test
    fun `general settings screen keeps settingsshell slots and shared components`() {
        listOf(
            "GeneralSettingsScreen",
            "ReaderAppTopBar",
            "ReaderChip",
            "ReaderSwitch",
            "GeneralSettingsGroup",
            "GeneralSettingsRow",
            "GeneralSettingsOptionSheet",
            "GeneralSettingsToastHost",
            "GeneralSettingsConfirmDialog",
            "GeneralSettingsFeedback",
            "contentDescription = \"SettingsShell 通用设置\"",
            "contentDescription = \"settingsContent\"",
            "contentDescription = \"toastHost\"",
            "contentDescription = \"sheetHost\"",
            "contentDescription = \"dialogHost\"",
            "contentDescription = \"settingsStateHost\""
        ).forEach { token ->
            assertTrue("General settings screen source must include $token", token in generalSettingsScreenSource)
        }

        listOf(
            "ReaderMainTabBar",
            "ReaderMainTabShell",
            "import androidx.compose.material.icons"
        ).forEach { forbidden ->
            assertTrue("General settings screen must not include $forbidden", forbidden !in generalSettingsScreenSource)
        }
    }

    @Test
    fun `general settings state model keeps frontend input contract text`() {
        listOf(
            "GeneralSettingsDisplayState",
            "GeneralSettingsMapper",
            "fun optionSheet()",
            "fun loading()",
            "fun error()",
            "fun permission()",
            "通用设置",
            "启动时打开",
            "语言",
            "主题跟随系统",
            "崩溃日志",
            "恢复默认",
            "确认恢复",
            "保存成功",
            "需要系统权限"
        ).forEach { token ->
            assertTrue("General settings state source must include $token", token in generalSettingsStateSource)
        }
    }
}
