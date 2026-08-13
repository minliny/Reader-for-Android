package com.reader.android.ui.toc

import com.reader.android.data.model.TOCItem
import com.reader.android.ui.adapter.ReaderIntegrationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDirectoryUiStateMappingTest {

    @Test
    fun `fixture maps to default book directory state`() {
        val state = BookDirectoryUiStateMapper.fromFixture()

        assertEquals("目录", state.topBarTitle)
        assertEquals("长夜余火", state.summary.title)
        assertEquals("当前来源：起点中文网", state.summary.sourceLabel)
        assertEquals("共 1862 章", state.summary.chapterCount)
        assertEquals("第 32 章 雨夜", state.currentChapter.title)
        assertEquals(38, state.currentChapter.progress)
        assertTrue(state.hasChapterList)
        assertTrue(state.chapters.any { it.isNew })
        assertTrue(state.chapters.any { it.status == BookDirectoryChapterStatus.Read })
        assertEquals(ReaderIntegrationLevel.NEEDS_ADAPTER, state.boundaryLevel)
        assertFalse(state.allowRealDataIntegration)
    }

    @Test
    fun `loading empty and error states are expressible`() {
        val loading = BookDirectoryUiStateMapper.loading()
        val empty = BookDirectoryUiStateMapper.empty()
        val error = BookDirectoryUiStateMapper.error("目录加载失败，请重试")

        assertEquals(BookDirectoryDisplayState.Loading, loading.displayState)
        assertEquals(BookDirectoryDisplayState.Empty, empty.displayState)
        assertEquals(BookDirectoryDisplayState.Error, error.displayState)
        assertFalse(loading.hasChapterList)
        assertEquals("正在加载目录", loading.feedback!!.title)
        assertEquals("暂无目录", empty.feedback!!.title)
        assertEquals("目录加载失败", error.feedback!!.title)
        assertEquals("目录加载失败，请重试", error.feedback!!.message)
    }

    @Test
    fun `core toc items flatten into directory chapters`() {
        val state = BookDirectoryUiStateMapper.fromTocItems(
            listOf(
                TOCItem("第一卷", "", children = listOf(
                    TOCItem("第一章 雨线", "fixture-chapter-1"),
                    TOCItem("第二章 星河", "fixture-chapter-2")
                )),
                TOCItem("第三章 夜路", "fixture-chapter-3")
            ),
            title = "纸上群山",
            sourceLabel = "当前来源：UI Fixture",
            currentChapterTitle = "第二章 星河"
        )

        assertEquals("纸上群山", state.summary.title)
        assertEquals("共 3 章", state.summary.chapterCount)
        assertEquals("第二章 星河", state.currentChapter.title)
        assertEquals(3, state.chapters.size)
        assertEquals("第一章 雨线", state.chapters.first().title)
        assertEquals("fixture-chapter-3", state.chapters.last().target)
    }
}
