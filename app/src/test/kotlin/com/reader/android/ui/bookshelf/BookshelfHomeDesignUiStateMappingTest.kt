package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfHomeDesignUiStateMappingTest {

    @Test
    fun `bookshelf home mapper exposes main tab state matrix`() {
        val states = listOf(
            BookshelfHomeMapper.fromFixture(),
            BookshelfHomeMapper.filtering(),
            BookshelfHomeMapper.loading(),
            BookshelfHomeMapper.empty()
        )

        assertEquals(
            listOf(
                BookshelfHomeDisplayState.Default,
                BookshelfHomeDisplayState.Filtering,
                BookshelfHomeDisplayState.Loading,
                BookshelfHomeDisplayState.Empty
            ),
            states.map { it.displayState }
        )
        states.forEach { state ->
            assertEquals("书架", state.topBar.title)
            assertEquals(listOf("书架", "发现", "RSS", "设置"), state.bottomNavLabels)
            assertTrue("Groups must stay available in every state", state.groups.size >= 4)
        }
    }

    @Test
    fun `bookshelf home default state maps fixture content`() {
        val state = BookshelfHomeMapper.fromFixture()

        assertEquals("10:30", state.status.time)
        assertEquals("82%", state.status.battery)
        assertEquals("长夜余火", state.continueReading?.title)
        assertEquals("38%", state.continueReading?.progressLabel)
        assertEquals(listOf("诡秘之主", "明朝那些事儿"), state.recentUpdates.map { it.title })
        assertEquals(6, state.books.size)
        assertTrue(state.books.any { it.title == "Android 札记" && it.author == "本地文档" })
    }

    @Test
    fun `bookshelf home filtering and empty states keep acceptance copy`() {
        val filtering = BookshelfHomeMapper.filtering()
        val empty = BookshelfHomeMapper.empty()

        assertEquals("追更", filtering.groups.single { it.active }.label)
        assertTrue(filtering.books.all { it.title in listOf("长夜余火", "诡秘之主", "明朝那些事儿") })
        assertEquals(null, empty.continueReading)
        assertTrue(empty.books.isEmpty())
        assertEquals("搜索书籍", empty.feedback.empty.primaryAction)
        assertEquals("导入本地书", empty.feedback.empty.secondaryAction)
    }
}
