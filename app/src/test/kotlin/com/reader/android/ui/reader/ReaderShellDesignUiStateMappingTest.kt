package com.reader.android.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderShellDesignUiStateMappingTest {

    @Test
    fun `reading entry fixture exposes frontend input copy and actions`() {
        val state = ReadingEntryMapper.fromFixture()

        assertEquals("雨夜", state.reading.title)
        assertEquals("阅读入口", state.dock.title)
        assertEquals("从书架继续阅读", state.dock.source)
        assertTrue(state.dock.context.contains("保留滚动位置和筛选条件"))
        assertEquals(listOf("继续阅读", "开始阅读"), state.dock.actions.map { it.label })
        assertEquals("正在打开", state.loading.title)
        assertEquals("内容加载异常", state.error.title)
        assertEquals("更换来源", state.error.primaryAction)
        assertEquals("重试", state.error.secondaryAction)
        assertEquals("网络不可用，请稍后重试", state.offline.title)
        assertEquals("更换来源需联网", state.offline.disabledAction)
    }

    @Test
    fun `reading entry state helpers keep display state only change`() {
        assertEquals(ReadingEntryDisplayState.Loading, ReadingEntryMapper.loading().displayState)
        assertEquals(ReadingEntryDisplayState.Error, ReadingEntryMapper.error().displayState)
        assertEquals(ReadingEntryDisplayState.Offline, ReadingEntryMapper.offline().displayState)
        assertEquals(ReadingEntryMapper.fromFixture().dock.actions, ReadingEntryMapper.error().dock.actions)
    }

    @Test
    fun `immersive reading fixture keeps weak info and tap zones`() {
        val state = ImmersiveReadingMapper.fromFixture()

        assertEquals("雨夜", state.reading.title)
        assertEquals("38%", state.info.progress)
        assertEquals("第 32 章", state.info.chapterOnly)
        assertFalse("Immersive chapter info must not include total chapter count", "/" in state.info.chapterOnly)
        assertEquals(listOf("previous", "menu", "next"), state.zones.map { it.type })
        assertEquals("当前章节信息只显示当前章节，不显示总章节数", state.chapterOnlyRule)
        assertEquals("正在加载", state.loading.title)
        assertEquals("加载失败，请重试", state.error.title)
        assertEquals("网络不可用，请稍后重试", state.offline.title)
    }

    @Test
    fun `immersive reading state helpers keep display state only change`() {
        assertEquals(ImmersiveReadingDisplayState.Loading, ImmersiveReadingMapper.loading().displayState)
        assertEquals(ImmersiveReadingDisplayState.Error, ImmersiveReadingMapper.error().displayState)
        assertEquals(ImmersiveReadingDisplayState.Offline, ImmersiveReadingMapper.offline().displayState)
        assertEquals(ImmersiveReadingMapper.fromFixture().zones, ImmersiveReadingMapper.offline().zones)
    }
}
