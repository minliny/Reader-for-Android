package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGroupManagementUiStateMappingTest {

    @Test
    fun `fixture maps group management page contract`() {
        val state = BookshelfGroupManagementMapper.fromFixture()

        assertEquals("分组管理", state.title)
        assertEquals("新建", state.addLabel)
        assertEquals(4, state.groups.size)
        assertTrue(state.groups.single { it.id == "all" }.system)
        assertFalse(state.groups.single { it.id == "all" }.canDelete)
        assertEquals("未分组", state.groups.single { it.id == "ungrouped" }.title)
        assertFalse(state.groups.single { it.id == "ungrouped" }.canDelete)
        assertEquals("输入分组名称", state.dialog.inputPlaceholder)
        assertTrue(state.deleteConfirm.message.contains("不会删除书籍"))
    }

    @Test
    fun `dialog states preserve values and modal visibility`() {
        val newGroup = BookshelfGroupManagementMapper.newGroup()
        val rename = BookshelfGroupManagementMapper.rename()
        val loading = BookshelfGroupManagementMapper.loading()
        val error = BookshelfGroupManagementMapper.error("保存失败，保留原输入。")

        assertEquals(BookshelfGroupManagementDisplayState.New, newGroup.displayState)
        assertTrue(newGroup.hasDialog)
        assertEquals("", newGroup.dialogValue)
        assertEquals(BookshelfGroupManagementDisplayState.Rename, rename.displayState)
        assertEquals("长篇追读", rename.dialogValue)
        assertEquals(BookshelfGroupManagementDisplayState.Loading, loading.displayState)
        assertEquals("正在加载", loading.feedback!!.title)
        assertEquals(BookshelfGroupManagementDisplayState.Error, error.displayState)
        assertEquals("保存失败，保留原输入。", error.feedback!!.message)
    }

    @Test
    fun `delete empty and label mapping states keep constraints`() {
        val delete = BookshelfGroupManagementMapper.delete()
        val empty = BookshelfGroupManagementMapper.empty()
        val labels = BookshelfGroupManagementMapper.fromGroupLabels(listOf("全部", "资料", "未分组"))

        assertEquals(BookshelfGroupManagementDisplayState.Delete, delete.displayState)
        assertTrue(delete.hasDialog)
        assertEquals("删除分组", delete.deleteConfirm.confirmLabel)
        assertEquals(BookshelfGroupManagementDisplayState.Empty, empty.displayState)
        assertEquals(listOf("all", "ungrouped"), empty.visibleGroups.map { it.id })
        assertEquals(listOf("全部", "资料", "未分组"), labels.groups.map { it.title })
        assertFalse(labels.groups.single { it.title == "未分组" }.canDelete)
    }
}
