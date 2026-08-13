package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfLocalImportUiStateMappingTest {

    @Test
    fun `fixture maps local import default contract`() {
        val state = BookshelfLocalImportMapper.fromFixture()

        assertEquals("导入本地书", state.title)
        assertEquals("选择本地书籍", state.intro.title)
        assertEquals("选择文件", state.intro.primaryAction)
        assertTrue(state.intro.message.contains("系统文件选择器"))
        assertTrue(state.permission.message.contains("不会扫描全盘"))
        assertTrue(state.permission.message.contains("不需要“管理所有文件”权限"))
        assertEquals(listOf("TXT", "EPUB", "可多选"), state.intro.formats)
        assertEquals(3, state.flow.size)
        assertFalse(state.isResultState)
    }

    @Test
    fun `importing and picker cancelled states keep authorization boundary`() {
        val importing = BookshelfLocalImportMapper.importing()
        val cancelled = BookshelfLocalImportMapper.pickerCancelled()

        assertEquals(BookshelfLocalImportDisplayState.Importing, importing.displayState)
        assertEquals("正在解析：星海旧章.epub", importing.importing.currentFile)
        assertEquals(58, importing.importing.progress)
        assertTrue(importing.permission.footnote.contains("不作为错误处理"))
        assertEquals(BookshelfLocalImportDisplayState.PickerCancelled, cancelled.displayState)
        assertEquals("已取消选择", cancelled.cancelState.title)
        assertTrue(cancelled.cancelState.message.contains("不展示错误页"))
    }

    @Test
    fun `result states expose counts rows and failure reasons`() {
        val success = BookshelfLocalImportMapper.success()
        val partial = BookshelfLocalImportMapper.partialFailed()
        val failed = BookshelfLocalImportMapper.failed()

        assertEquals(BookshelfLocalImportDisplayState.Success, success.displayState)
        assertEquals("导入完成", success.summary.title)
        assertTrue(success.isResultState)
        assertTrue(success.results.all { it.tone == "success" || it.tone == "skip" })
        assertEquals(BookshelfLocalImportDisplayState.PartialFailed, partial.displayState)
        assertEquals("部分文件导入失败", partial.summary.title)
        assertTrue(partial.results.any { it.tone == "failed" && it.reason.isNotBlank() })
        assertEquals(BookshelfLocalImportDisplayState.Failed, failed.displayState)
        assertEquals("导入失败", failed.summary.title)
        assertTrue(failed.results.all { it.tone == "failed" })
        assertTrue(failed.results.all { it.reason.isNotBlank() })
    }
}
