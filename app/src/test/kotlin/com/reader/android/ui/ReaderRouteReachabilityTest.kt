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
            "SettingsScreen",
            "SearchScreen",
            "BookDetailScreen",
            "TOCScreen",
            "SourceDetailScreen",
            "SourceEditScreen",
            "SourceImportScreen",
            "DiscoverScreen",
            "RssListScreen",
            "WebDavConfigScreen"
        ).forEach { screen ->
            assertTrue("$screen must be referenced by route host", screen in routeHostSource)
        }
    }

    @Test
    fun `routes without direct runtime data have explicit state stubs`() {
        listOf("RSS 详情", "RSS 订阅管理", "备份设置", "进度同步", "深链待处理").forEach { title ->
            assertTrue("Stub route must render $title", title in routeHostSource)
        }
    }

    @Test
    fun `state routes expose error offline and permission rendering`() {
        listOf("ReaderErrorState", "ReaderOfflineState", "ReaderPermissionRequiredState").forEach { state ->
            assertTrue("Route host must expose $state", state in routeHostSource)
        }
    }
}
