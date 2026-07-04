package com.reader.ui.restore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreUiStateTest {
    @Test
    fun `confirm route mirrors canonical restore summary and scopes`() {
        val page = restorePageForRoute(RestoreRouteIds.Confirm)

        assertEquals("恢复确认", page.title)
        assertEquals("待确认", page.badge.label)
        assertEquals(DefaultRestoreRecord, page.record)
        assertEquals(listOf("bookshelf", "progress", "settings", "sources"), page.scopes.map { it.key })
        assertEquals("书架与分组、阅读进度、阅读与 App 设置、书源配置", page.summaryRows[1].value)
        assertEquals("128 本书 · 12 个分组 · 96 条阅读进度 等 4 项", page.summaryRows[2].value)
        assertEquals("覆盖提醒", page.warningTitle)
    }

    @Test
    fun `progress route exposes progress meter and stages`() {
        val page = restorePageForRoute(RestoreRouteIds.Progress)

        assertEquals("恢复进度", page.title)
        assertEquals("进行中", page.badge.label)
        assertEquals(0.68f, page.progress ?: 0f, 0.001f)
        assertEquals(4, page.stages.size)
        assertTrue(page.stages[0].done)
        assertTrue(page.stages[2].active)
        assertEquals("查看结果", page.actions.last().label)
    }

    @Test
    fun `conflict route defaults to three demo conflicts`() {
        val page = restorePageForRoute(RestoreRouteIds.Conflict)

        assertEquals("恢复冲突", page.title)
        assertEquals("3 项冲突", page.badge.label)
        assertEquals(3, page.conflicts.size)
        assertEquals("分组：玄幻连载", page.conflicts.first().title)
        assertEquals("应用选择", page.actions.last().label)
    }

    @Test
    fun `result route exposes result summary and detail rows`() {
        val page = restorePageForRoute(RestoreRouteIds.Result)

        assertEquals("恢复结果", page.title)
        assertEquals("部分成功", page.badge.label)
        assertEquals("128 本", page.summaryRows[0].value)
        assertEquals("1 条", page.summaryRows[3].value)
        assertEquals(3, page.resultItems.size)
        assertEquals("跳过", page.resultItems.last().badge.label)
    }

    @Test
    fun `unknown route falls back to confirm`() {
        val page = restorePageForRoute("restore-missing")

        assertEquals(RestoreRouteIds.Confirm, page.routeId)
        assertEquals("恢复确认", page.title)
    }

    @Test
    fun `scope toggle filters available scopes and keeps at least one selection`() {
        val state = RestoreUiState(
            availableScopeKeys = listOf("progress", "sources", "unknown"),
            selectedScopeKeys = listOf("progress")
        )

        assertEquals(listOf("progress", "sources"), restoreAvailableScopeKeys(state))
        assertEquals(listOf("progress"), restoreSelectedScopeKeys(state))
        assertEquals(state, state.toggleScope("progress"))

        val added = state.toggleScope("sources")
        assertEquals(listOf("progress", "sources"), restoreSelectedScopeKeys(added))

        val removed = added.toggleScope("progress")
        assertEquals(listOf("sources"), restoreSelectedScopeKeys(removed))
        assertFalse("unknown" in restoreAvailableScopeKeys(removed))
    }
}
