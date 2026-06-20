package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSortFilterUiStateMappingTest {

    @Test
    fun `fixture maps to default sort filter state`() {
        val state = BookshelfSortFilterMapper.fromFixture()

        assertEquals("9:41", state.statusTime)
        assertEquals("书架", state.backdropTitle)
        assertEquals("排序与筛选", state.sheetTitle)
        assertEquals("重置", state.resetLabel)
        assertEquals("应用", state.applyLabel)
        assertEquals(BookshelfSortFilterDisplayState.Default, state.displayState)
        assertFalse(state.isFeedbackState)
        assertTrue(state.groups.any { it.label == "全部" && it.active })
        assertEquals(3, state.backdropBooks.size)
        assertTrue(state.sections.any { it.title == "排序方式" && it.mode == BookshelfSortFilterSectionMode.Single })
        assertTrue(state.sections.any { it.title == "筛选条件" && it.mode == BookshelfSortFilterSectionMode.Multi })
    }

    @Test
    fun `selected empty and error states are expressible`() {
        val selected = BookshelfSortFilterMapper.selected()
        val empty = BookshelfSortFilterMapper.empty()
        val error = BookshelfSortFilterMapper.error("操作失败，请重试")

        assertEquals(BookshelfSortFilterDisplayState.Selected, selected.displayState)
        assertEquals("保存成功", selected.toastMessage)
        assertTrue(selected.sections.first { it.title == "排序方式" }.options.any { it.type == "recent-update" && it.active })
        assertTrue(selected.sections.first { it.title == "排序顺序" }.options.any { it.type == "asc" && it.active })
        assertTrue(selected.sections.first { it.title == "筛选条件" }.options.any { it.type == "local" && it.active })

        assertEquals(BookshelfSortFilterDisplayState.Empty, empty.displayState)
        assertTrue(empty.isFeedbackState)
        assertEquals("筛选后没有书籍", empty.feedback!!.title)
        assertEquals("当前条件没有匹配结果，可以重置后重新筛选。", empty.feedback!!.message)

        assertEquals(BookshelfSortFilterDisplayState.Error, error.displayState)
        assertTrue(error.isFeedbackState)
        assertEquals("操作失败，请重试", error.feedback!!.title)
        assertEquals("操作失败，请重试", error.feedback!!.message)
    }

    @Test
    fun `bookshelf state maps through existing group filter sort logic`() {
        val bookshelfState = BookshelfMapper.fakeFallback(BookshelfLayoutMode.List).copy(
            selectedGroup = "UI Fixture",
            availableGroups = listOf("全部", "UI Fixture")
        )

        val state = BookshelfSortFilterMapper.fromBookshelfState(
            bookshelfState = bookshelfState,
            sortBy = BookshelfGroupFilterSort.SortBy.TITLE
        )

        assertTrue(state.groups.any { it.label == "UI Fixture" && it.active })
        assertEquals(bookshelfState.books.size, state.backdropBooks.size)
        assertTrue(state.sections.first { it.title == "排序方式" }.options.any { it.label == "书名" && it.active })
    }
}
