package com.reader.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagementDesignUiStateMappingTest {

    @Test
    fun `default fixture exposes source list search filters actions and status metrics`() {
        val state = SourceManagementDesignMapper.fromFixture()

        assertEquals(SourceManagementDesignDisplayState.Default, state.displayState)
        assertEquals("书源管理", state.topBar.title)
        assertEquals("返回", state.topBar.backLabel)
        assertEquals(listOf("个书源", "个启用", "个异常", "刚刚检测"), state.metrics.map { it.label })
        assertEquals("搜索框：搜索书源名称或域名", state.searchBox.placeholder)
        assertEquals(listOf("全部", "已启用", "异常", "未检测", "自定义"), state.filters.map { it.label })
        assertEquals(listOf("全部分组", "玄幻书源", "起点导入", "测试书源"), state.groups.map { it.label })
        assertEquals("书源列表", state.sourceListTitle)

        val actions = state.sections.single { it.title == "批量操作" }.rows
        assertEquals(listOf("检测", "详情", "编辑", "错误日志", "启用开关"), actions.map { it.title })
        assertEquals("开始检测", actions.first { it.title == "检测" }.actionLabel)
        assertTrue(actions.first { it.title == "启用开关" }.enabled)

        assertEquals(listOf("起点中文网", "笔趣阁", "本地导入源", "测试书源"), state.sources.map { it.title })
        assertEquals(listOf("可用", "异常", "未检测", "可用"), state.sources.map { it.status })
        assertEquals("新增", state.fab.label)
        assertEquals("SourceEditForm · 新增书源", state.form.title)
        assertEquals(listOf("书源名称", "域名", "分组"), state.form.fields.map { it.label })
        assertEquals("LogPanel · 错误日志", state.log.title)
        assertEquals(listOf("ERROR", "WARN"), state.log.items.map { it.level })
    }

    @Test
    fun `state variants keep source context and recovery actions`() {
        val base = SourceManagementDesignMapper.fromFixture()
        val edit = SourceManagementDesignMapper.edit()
        val log = SourceManagementDesignMapper.log()
        val confirm = SourceManagementDesignMapper.confirm()
        val loading = SourceManagementDesignMapper.loading()
        val empty = SourceManagementDesignMapper.empty()
        val error = SourceManagementDesignMapper.error()
        val offline = SourceManagementDesignMapper.offline()
        val permission = SourceManagementDesignMapper.permission()

        assertEquals(SourceManagementDesignDisplayState.Edit, edit.displayState)
        assertEquals(base.form, edit.form)

        assertEquals(SourceManagementDesignDisplayState.Log, log.displayState)
        assertEquals("笔趣阁目录解析失败，返回字段缺失。", log.log.items.first().copy)

        assertEquals(SourceManagementDesignDisplayState.Confirm, confirm.displayState)
        assertEquals("禁用书源？", confirm.confirm.title)
        assertEquals("确认禁用", confirm.confirm.confirmLabel)

        assertEquals(SourceManagementDesignDisplayState.Loading, loading.displayState)
        assertEquals("书源加载态", loading.loading.title)

        assertEquals(SourceManagementDesignDisplayState.Empty, empty.displayState)
        assertEquals("暂无书源", empty.empty.title)
        assertEquals("新增书源", empty.empty.primaryAction)
        assertTrue(empty.sources.isEmpty())

        assertEquals(SourceManagementDesignDisplayState.Error, error.displayState)
        assertEquals("加载失败态", error.error.title)
        assertEquals("重试", error.error.primaryAction)

        assertEquals(SourceManagementDesignDisplayState.Offline, offline.displayState)
        assertEquals("离线检测态", offline.offline.title)
        assertEquals("查看缓存", offline.offline.primaryAction)
        assertEquals(base.sources, offline.sources)

        assertEquals(SourceManagementDesignDisplayState.Permission, permission.displayState)
        assertEquals("网络权限态", permission.permission.title)
        assertEquals("去授权", permission.permission.primaryAction)
        assertEquals("需要网络权限", permission.toast.permission)
    }

    @Test
    fun `source management stays scoped to source administration and explicit error logs`() {
        val state = SourceManagementDesignMapper.fromFixture()
        val visibleText = (
            listOf(state.topBar.title, state.searchBox.placeholder, state.sourceListTitle, state.fab.label, state.form.title, state.log.title, state.confirm.copy) +
                state.metrics.flatMap { metric -> listOf(metric.label, metric.value) } +
                state.filters.map { it.label } +
                state.groups.map { it.label } +
                state.sections.flatMap { section ->
                    listOf(section.title) + section.rows.flatMap { row ->
                        listOf(row.title, row.meta, row.actionLabel)
                    }
                } +
                state.sources.flatMap { source -> listOf(source.title, source.meta, source.status) } +
                state.log.items.flatMap { item -> listOf(item.level, item.copy) }
            ).joinToString(" ")

        listOf("书源列表", "搜索框", "启用开关", "检测", "详情", "新增", "编辑", "错误日志").forEach { token ->
            assertTrue("Source management fixture must expose $token", token in visibleText)
        }
        assertTrue("Detection failure must remain visible in log panel", "目录解析失败" in visibleText)
        listOf("账号", "会员", "社区", "推荐流", "广告", "登录入口").forEach { forbidden ->
            assertFalse("Source management fixture must not expose $forbidden", forbidden in visibleText)
        }
    }
}
