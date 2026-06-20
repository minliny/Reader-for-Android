package com.reader.android.ui.navigation

import com.reader.android.ui.AppRouteGroups
import com.reader.android.ui.ReaderRoutes
import com.reader.android.ui.appScreens
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class AppRouteGroupingTest {

    private val readerBottomSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt")))
    }

    @Test
    fun `search and reader remain reachable but are not primary tabs`() {
        val primaryRoutes = appScreens.map { it.route }.toSet()

        assertTrue(ReaderRoutes.SEARCH in AppRouteGroups.bookshelf)
        assertTrue(ReaderRoutes.READER_CONTENT in AppRouteGroups.bookshelf)
        assertTrue(ReaderRoutes.SOURCE_SWITCH in AppRouteGroups.bookshelf)
        assertFalse(ReaderRoutes.SEARCH in primaryRoutes)
        assertFalse(ReaderRoutes.READER_CONTENT in primaryRoutes)
        assertFalse(ReaderRoutes.SOURCE_SWITCH in primaryRoutes)
    }

    @Test
    fun `discover rss and source management are grouped under correct modules`() {
        listOf(ReaderRoutes.DISCOVER).forEach {
            assertTrue("$it must belong to Discover", it in AppRouteGroups.discover)
        }
        listOf(ReaderRoutes.RSS, ReaderRoutes.RSS_LIST, ReaderRoutes.RSS_DETAIL, ReaderRoutes.RSS_SUBSCRIPTION).forEach {
            assertTrue("$it must belong to RSS", it in AppRouteGroups.rss)
        }
        listOf(ReaderRoutes.SOURCES, ReaderRoutes.SOURCE_DETAIL, ReaderRoutes.SOURCE_EDIT, ReaderRoutes.SOURCE_IMPORT).forEach {
            assertTrue("$it must belong to Settings", it in AppRouteGroups.settings)
        }
    }

    @Test
    fun `settings owns sync webdav backup source management remote books and about`() {
        listOf(
            ReaderRoutes.SETTINGS,
            ReaderRoutes.MINE,
            ReaderRoutes.SOURCES,
            ReaderRoutes.GLOBAL_SETTINGS,
            ReaderRoutes.WEBDAV_CONFIG,
            ReaderRoutes.BACKUP_SETTINGS,
            ReaderRoutes.PROGRESS_SYNC,
            ReaderRoutes.REMOTE_WEBDAV_BOOKS,
            ReaderRoutes.ABOUT
        ).forEach {
            assertTrue("$it must belong to Settings", it in AppRouteGroups.settings)
        }
    }

    @Test
    fun `reader control bottom bar remains reader only`() {
        listOf("目录", "朗读", "界面设置", "阅读行为设置").forEach { label ->
            assertTrue("Reader bottom bar must keep $label", label in readerBottomSource)
        }
        listOf("WebDAV", "书源管理", "RSS", "关于 Reader").forEach { forbidden ->
            assertFalse("Reader bottom bar must not contain $forbidden", forbidden in readerBottomSource)
        }
    }
}
