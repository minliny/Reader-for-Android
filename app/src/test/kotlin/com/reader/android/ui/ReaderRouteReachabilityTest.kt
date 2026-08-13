package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderRouteReachabilityTest {

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    @Test
    fun `core screens are reachable from route host`() {
        listOf(
            "BookshelfScreen",
            "BookSourceScreen",
            "ReaderScreen",
            "SourceSwitchFlowScreen",
            "SettingsScreen",
            "SettingsRootScreen",
            "onRssManagementClick",
            "MineScreen",
            "SearchScreen",
            "BookDetailScreen",
            "TOCScreen",
            "SourceDetailScreen",
            "SourceEditScreen",
            "SourceImportScreen",
            "DiscoverScreen",
            "RssHomeScreen",
            "RssListScreen",
            "RSS_SUBSCRIPTION",
            "SOURCE_SWITCH",
            "WebDavConfigScreen"
        ).forEach { screen ->
            assertTrue("$screen must be referenced by route host", screen in routeHostSource)
        }
    }

    @Test
    fun `rss backup and sync routes use state driven screens`() {
        listOf(
            "RssDetailScreen",
            "RssSubscriptionManagementScreen",
            "BackupSettingsScreen",
            "ProgressSyncStatusScreen",
            "RemoteWebDavBooksScreen",
            "深链待处理"
        ).forEach { token ->
            assertTrue("Route host must render $token", token in routeHostSource)
        }
    }

    @Test
    fun `state routes expose error offline and permission rendering`() {
        listOf("ReaderErrorState", "ReaderOfflineState", "ReaderPermissionRequiredState").forEach { state ->
            assertTrue("Route host must expose $state", state in routeHostSource)
        }
    }
}
