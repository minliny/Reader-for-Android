package com.reader.android.ui.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class WebDavRssBoundaryGuardTest {

    private val stateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/sync/WebDavRssUiState.kt")))
    }

    private val screenSource: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/discover/DiscoverScreen.kt",
            "src/main/kotlin/com/reader/android/ui/discover/RssScreens.kt",
            "src/main/kotlin/com/reader/android/ui/settings/WebDavAndBackupScreens.kt",
            "src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    @Test
    fun `mapper does not call network core parser or repository`() {
        val forbidden = listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "Http" + "Client",
            ".execute(",
            ".get(",
            "Room",
            "DataStore"
        )

        forbidden.forEach { token ->
            assertTrue("State adapter must not contain $token", token !in stateSource)
        }
    }

    @Test
    fun `screens are state driven for webdav rss discover backup and sync`() {
        listOf(
            "DiscoverUiState",
            "RssListUiState",
            "RssSubscriptionUiState",
            "WebDavConfigUiState",
            "BackupSettingsUiState",
            "ProgressSyncStatusUiState",
            "RemoteWebDavBooksUiState",
            "DiscoverRssWebDavMapper"
        ).forEach { token ->
            assertTrue("Slice 15 screen source must contain $token", token in screenSource)
        }
    }

    @Test
    fun `event contracts are defined without real implementation`() {
        listOf(
            "onConfigureWebDav",
            "onTestWebDavConnection",
            "onDisconnectWebDav",
            "onToggleBackup",
            "onRunBackupNow",
            "onToggleProgressSync",
            "onResolveSyncConflict",
            "onOpenRemoteBook",
            "onRefreshRss",
            "onOpenRssArticle",
            "onSubscribeRss",
            "onUnsubscribeRss"
        ).forEach { token ->
            assertTrue("Event contract must include $token", token in stateSource)
        }
    }

    @Test
    fun `slice 15 does not reintroduce legacy ui or normalized html runtime`() {
        val forbidden = listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec",
            "#" + "eae1da",
            "#" + "f5ece6",
            "#" + "efe7e0",
            "#" + "8b5000",
            "Web" + "View",
            "normalized-" + "html",
            "skip_" + "previous",
            "skip_" + "next"
        )

        forbidden.forEach { token ->
            assertTrue("Slice 15 source must not contain $token", token !in (screenSource + stateSource))
        }
    }
}
