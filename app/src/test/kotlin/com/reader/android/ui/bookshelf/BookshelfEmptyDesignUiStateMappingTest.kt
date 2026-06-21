package com.reader.android.ui.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfEmptyDesignUiStateMappingTest {

    @Test
    fun `bookshelf empty mapper exposes all library shell states`() {
        val states = listOf(
            BookshelfEmptyDesignMapper.currentGroupEmpty(),
            BookshelfEmptyDesignMapper.allEmpty(),
            BookshelfEmptyDesignMapper.loading(),
            BookshelfEmptyDesignMapper.error(),
            BookshelfEmptyDesignMapper.offline(),
            BookshelfEmptyDesignMapper.permission()
        )

        assertEquals(
            listOf(
                BookshelfEmptyDisplayState.Default,
                BookshelfEmptyDisplayState.AllEmpty,
                BookshelfEmptyDisplayState.Loading,
                BookshelfEmptyDisplayState.Error,
                BookshelfEmptyDisplayState.Offline,
                BookshelfEmptyDisplayState.Permission
            ),
            states.map { it.displayState }
        )
        states.forEach { state ->
            assertEquals("书架", state.title)
            assertTrue("Each state must keep group chips available", state.groups.size >= 4)
            assertTrue("Each state must explain its reason", state.state.message.isNotBlank())
            assertTrue("Each state must keep a handoff hint", state.state.hint.isNotBlank())
        }
    }

    @Test
    fun `bookshelf empty states keep local import optional and permission scoped`() {
        val allEmpty = BookshelfEmptyDesignMapper.allEmpty()
        val permission = BookshelfEmptyDesignMapper.permission()

        assertEquals("导入本地书", allEmpty.state.secondaryAction)
        assertTrue(allEmpty.state.hint.contains("不是 P0 强制流程"))
        assertTrue(permission.state.message.contains("不提前请求全盘权限"))
        assertEquals("选择本地书", permission.state.primaryAction)
        assertEquals("取消", permission.state.secondaryAction)
    }
}
