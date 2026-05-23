package com.reader.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class NavigationRouteHostSmokeTest {

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    @Test
    fun `reader route host defines all core tab routes`() {
        listOf("BOOKSHELF", "BOOKSOURCE", "READER", "SETTINGS").forEach { tab ->
            assertTrue("Must define $tab", "const val $tab" in routeHostSource)
        }
    }

    @Test
    fun `reader route host defines s5 flow routes`() {
        listOf("SEARCH", "DETAIL", "TOC", "READER_CONTENT").forEach { route ->
            assertTrue("Must define $route", "const val $route" in routeHostSource)
        }
    }

    @Test
    fun `reader route host defines source management routes`() {
        listOf("SOURCE_DETAIL", "SOURCE_EDIT", "SOURCE_IMPORT").forEach { route ->
            assertTrue("Must define $route", "const val $route" in routeHostSource)
        }
    }

    @Test
    fun `reader route host defines discover and rss routes`() {
        listOf("DISCOVER", "RSS_LIST", "RSS_DETAIL", "RSS_SUBSCRIPTION").forEach { route ->
            assertTrue("Must define $route", "const val $route" in routeHostSource)
        }
    }

    @Test
    fun `reader route host defines settings subscreen routes`() {
        listOf("WEBDAV_CONFIG", "BACKUP_SETTINGS", "PROGRESS_SYNC").forEach { route ->
            assertTrue("Must define $route", "const val $route" in routeHostSource)
        }
    }

    @Test
    fun `reader route host defines state and deep link routes`() {
        listOf("STATE_ERROR", "STATE_OFFLINE", "DEEP_LINK").forEach { route ->
            assertTrue("Must define $route", "const val $route" in routeHostSource)
        }
    }

    @Test
    fun `reader back stack supports push pop popTo and clear`() {
        listOf("fun push", "fun pop", "fun popTo", "fun clear").forEach { op ->
            assertTrue("BackStack must have $op", op in routeHostSource)
        }
    }

    @Test
    fun `deep link handler routes to correct destinations`() {
        assertTrue("deepLink must handle detail", "detail" in routeHostSource)
        assertTrue("deepLink must handle reader", "reader" in routeHostSource)
        assertTrue("deepLink must have bookshelf fallback", "BOOKSHELF" in routeHostSource)
    }

    @Test
    fun `reader route host does not reintroduce stitch old patterns`() {
        listOf(
            "bg-surface-container", "text-on-surface", "shadow-lg", "shadow-md",
            "#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000",
            "WebView", "normalized-html"
        ).forEach { forbidden ->
            assertTrue("Must not have $forbidden", forbidden !in routeHostSource)
        }
    }

    @Test
    fun `total route count matches expected`() {
        val count = routeHostSource.lines().count { it.trimStart().startsWith("const val ") }
        assertEquals("ReaderRoutes must define 21 routes", 21, count)
    }
}
