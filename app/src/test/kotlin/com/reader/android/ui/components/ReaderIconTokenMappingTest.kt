package com.reader.android.ui.components

import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderIconTokenMappingTest {

    @Test
    fun `main tabs use semantic icon tokens`() {
        assertEquals(
            listOf(
                ReaderIconToken.Bookshelf,
                ReaderIconToken.Discover,
                ReaderIconToken.Rss,
                ReaderIconToken.Settings
            ),
            appScreens.map { it.iconToken }
        )
    }

    @Test
    fun `all declared icon tokens resolve to image vectors`() {
        ReaderIconToken.entries.forEach { token ->
            assertNotNull("${token.name} must resolve to an ImageVector", token.asImageVector())
        }
    }

    @Test
    fun `settings secondary semantic icons are available`() {
        listOf(
            ReaderIconToken.Folder,
            ReaderIconToken.Badge,
            ReaderIconToken.Sort,
            ReaderIconToken.People,
            ReaderIconToken.Clock,
            ReaderIconToken.List,
            ReaderIconToken.Trash,
            ReaderIconToken.Bell,
            ReaderIconToken.Battery,
            ReaderIconToken.EyeOff,
            ReaderIconToken.Info,
            ReaderIconToken.Shield,
            ReaderIconToken.Bug,
            ReaderIconToken.Storage,
            ReaderIconToken.Download,
            ReaderIconToken.Image,
            ReaderIconToken.Check,
            ReaderIconToken.Link,
            ReaderIconToken.Message,
            ReaderIconToken.Code,
            ReaderIconToken.Help,
            ReaderIconToken.Upload,
            ReaderIconToken.Edit
        ).forEach { token ->
            assertNotNull("${token.name} must support settings secondary pages", token.asImageVector())
        }
    }
}
