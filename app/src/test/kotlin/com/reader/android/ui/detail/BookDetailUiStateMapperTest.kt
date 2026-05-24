package com.reader.android.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailUiStateMapperTest {

    @Test
    fun `fromFixture produces detail state`() {
        val state = BookDetailUiStateMapper.fromFixture()
        assertTrue(state.hasContent)
        assertEquals("纸上群山", state.detail!!.title)
    }

    @Test
    fun `loading produces loading state`() {
        val state = BookDetailUiStateMapper.loading()
        assertTrue(state.isLoading)
    }

    @Test
    fun `empty produces empty state`() {
        val state = BookDetailUiStateMapper.empty()
        assertEquals("暂无书籍详情", state.emptyMessage)
    }

    @Test
    fun `error produces error state`() {
        val state = BookDetailUiStateMapper.error("网络不可用")
        assertNotNull(state.errorMessage)
        assertEquals("网络不可用", state.errorMessage)
    }

    @Test
    fun `fixture toc preview has correct data`() {
        val state = BookDetailUiStateMapper.fromFixture()
        val toc = state.detail!!.tocPreview
        assertEquals(12, toc.chapterCount)
        assertEquals("第一章 雨线", toc.firstChapterTitle)
        assertEquals("第十二章 星河", toc.latestChapterTitle)
    }
}
