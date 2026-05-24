package com.reader.android.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailAdapterShellTest {

    @Test
    fun `detail loading returns loading state`() {
        val state = BookDetailAdapterShell.detailLoading()
        assertTrue(state.isLoading)
    }

    @Test
    fun `detail ready returns fixture detail`() {
        val state = BookDetailAdapterShell.detailReady()
        assertTrue(state.hasContent)
        assertNotNull(state.detail)
        assertEquals("纸上群山", state.detail!!.title)
    }

    @Test
    fun `detail error returns error state`() {
        val state = BookDetailAdapterShell.detailError("加载失败")
        assertNotNull(state.errorMessage)
        assertEquals("加载失败", state.errorMessage)
    }

    @Test
    fun `detail empty returns empty state`() {
        val state = BookDetailAdapterShell.detailEmpty()
        assertFalse(state.hasContent)
    }

    @Test
    fun `detail includes toc preview`() {
        val state = BookDetailAdapterShell.detailReady()
        val detail = state.detail!!
        assertNotNull(detail.tocPreview)
        assertEquals(12, detail.tocPreview.chapterCount)
        assertEquals("第一章 雨线", detail.tocPreview.firstChapterTitle)
    }

    @Test
    fun `detail includes bookshelf and cache status`() {
        val state = BookDetailAdapterShell.detailReady()
        val detail = state.detail!!
        assertTrue(detail.isInBookshelf)
        assertEquals("已缓存 12 章", detail.cacheStatus)
    }

    @Test
    fun `detail includes available actions`() {
        val state = BookDetailAdapterShell.detailReady()
        val detail = state.detail!!
        assertTrue(detail.availableActions.isNotEmpty())
        assertTrue(detail.availableActions.contains("开始阅读"))
    }

    @Test
    fun `detail adapter shell is in fake mode by default`() {
        assertTrue(BookDetailAdapterShell.isFakeMode)
    }

    @Test
    fun `detail adapter shell does not access network`() {
        val state = BookDetailAdapterShell.detailReady()
        assertFalse(state.allowRealDataIntegration)
    }
}
