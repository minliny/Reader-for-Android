package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfActionSheetDesignUiStateMappingTest {

    @Test
    fun `book action sheet mapper exposes default danger loading and error states`() {
        val states = listOf(
            BookshelfActionSheetMapper.fromFixture(),
            BookshelfActionSheetMapper.danger(),
            BookshelfActionSheetMapper.loading(),
            BookshelfActionSheetMapper.error()
        )

        assertEquals(
            listOf(
                BookshelfActionSheetDisplayState.Default,
                BookshelfActionSheetDisplayState.Danger,
                BookshelfActionSheetDisplayState.Loading,
                BookshelfActionSheetDisplayState.Error
            ),
            states.map { it.displayState }
        )
        states.forEach { state ->
            assertEquals("书架", state.backdropTitle)
            assertEquals("深空信号", state.selectedBook.title)
            assertTrue("Backdrop must preserve selected book context", state.backdropBooks.any { it.selected })
        }
    }

    @Test
    fun `book action sheet limits actions and keeps explicit danger copy`() {
        val state = BookshelfActionSheetMapper.fromFixture()

        assertEquals(2, state.actions.size)
        assertEquals(listOf("修改", "删除"), state.actions.map { it.title })
        assertEquals(BookshelfActionTone.Danger, state.actions.single { it.type == "delete" }.tone)
        assertTrue(state.actions.single { it.type == "delete" }.copy.contains("不会删除本地文件或网络来源"))
        assertEquals("确认移除", state.confirm.confirmLabel)
        assertFalse("Confirm action must not fall back to vague copy", state.confirm.confirmLabel == "确定")
    }

    @Test
    fun `book action sheet loading and error states preserve retry contract`() {
        val loading = BookshelfActionSheetMapper.loading()
        val error = BookshelfActionSheetMapper.error()

        assertEquals("正在移除书架记录，请勿重复点击确认移除。", loading.feedback?.message)
        assertEquals("重试", error.feedback?.primaryAction)
        assertEquals("取消", error.feedback?.secondaryAction)
        assertTrue(error.feedback?.message.orEmpty().contains("保留当前书架"))
    }
}
