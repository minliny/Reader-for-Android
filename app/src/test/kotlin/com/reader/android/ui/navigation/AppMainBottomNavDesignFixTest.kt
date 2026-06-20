package com.reader.android.ui.navigation

import com.reader.android.ui.ReaderRoutes
import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AppMainBottomNavDesignFixTest {

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    private val componentSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt")))
    }

    @Test
    fun `app main bottom nav has exactly four primary modules`() {
        assertEquals(listOf("书架", "发现", "RSS", "设置"), appScreens.map { it.label })
        assertEquals(listOf("bookshelf", "discover", "rss", "settings"), appScreens.map { it.route })
    }

    @Test
    fun `app main bottom nav excludes search reader source and mine`() {
        val labels = appScreens.map { it.label }
        val routes = appScreens.map { it.route }

        listOf("搜索", "阅读", "书源", "我的").forEach { forbidden ->
            assertFalse("$forbidden must not be a primary tab", forbidden in labels)
        }
        listOf(ReaderRoutes.SEARCH, ReaderRoutes.READER_CONTENT, ReaderRoutes.SOURCES, ReaderRoutes.MINE, ReaderRoutes.GLOBAL_SETTINGS).forEach { route ->
            assertFalse("$route must not be a primary tab", route in routes)
        }
    }

    @Test
    fun `app main bottom bar only renders on primary tab routes`() {
        assertTrue("Route host must derive main tab routes from appScreens", "appScreens.map { it.route }.toSet()" in routeHostSource)
        assertTrue("Route host must use formal ReaderMainTabShell", "ReaderMainTabShell(" in routeHostSource)
        assertTrue("Route host must hide main bottom bar outside primary tabs", "showMainNav = showMainBottomBar" in routeHostSource)
        assertFalse("Route host must not use temporary StitchBottomNav", "StitchBottomNav" in routeHostSource)
    }

    @Test
    fun `app main bottom nav selected state changes color without moving buttons`() {
        assertTrue("MainTabShell must own the shared tab bar", "fun ReaderMainTabShell(" in componentSource)
        assertTrue("Selected button must keep fixed width", ".width(64.dp)" in componentSource)
        assertTrue("Selected button must keep fixed height", ".height(44.dp)" in componentSource)
        assertTrue("Selected button must deepen background", "background(ReaderTheme.colors.primary)" in componentSource)
        assertTrue("Selected icon must invert color", "ReaderTheme.colors.paperBg" in componentSource)
    }
}
