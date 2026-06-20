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
}
