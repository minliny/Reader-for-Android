package com.reader.ui.restore

object RestoreRouteIds {
    const val Confirm = "restore-confirm"
    const val Progress = "restore-progress"
    const val Conflict = "restore-conflict"
    const val Result = "restore-result"
    const val SyncBackup = "sync-backup"

    val flowRoutes = setOf(Confirm, Progress, Conflict, Result)

    fun normalize(routeId: String): String = if (routeId in flowRoutes) routeId else Confirm
}

data class RestoreUiState(
    val selectedRestoreRecord: String = DefaultRestoreRecord,
    val availableScopeKeys: List<String> = restoreDefaultScopeKeys(),
    val selectedScopeKeys: List<String> = availableScopeKeys,
    val conflictChoices: Map<String, RestoreConflictChoice> = emptyMap()
) {
    fun toggleScope(key: String): RestoreUiState {
        val available = restoreAvailableScopeKeys(this)
        if (key !in available) return this

        val selected = restoreSelectedScopeKeys(this)
        val next = if (key in selected) {
            selected.filterNot { it == key }
        } else {
            selected + key
        }
        return if (next.isEmpty()) this else copy(selectedScopeKeys = next)
    }

    fun chooseConflict(conflictId: String, choice: RestoreConflictChoice): RestoreUiState =
        copy(conflictChoices = conflictChoices + (conflictId to choice))
}

data class RestoreScopeSpec(
    val key: String,
    val title: String,
    val meta: String,
    val impact: String
)

data class RestoreBadge(
    val label: String,
    val tone: RestoreTone
)

data class RestoreSummaryRow(
    val label: String,
    val value: String
)

data class RestoreStageItem(
    val title: String,
    val meta: String,
    val badge: RestoreBadge,
    val progress: Float,
    val active: Boolean = false,
    val done: Boolean = false
)

data class RestoreConflictItem(
    val id: String,
    val title: String,
    val meta: String,
    val localLabel: String,
    val remoteLabel: String
)

data class RestoreResultItem(
    val title: String,
    val meta: String,
    val badge: RestoreBadge
)

data class RestoreAction(
    val label: String,
    val routeId: String? = null,
    val primary: Boolean = false
)

data class RestorePageState(
    val routeId: String,
    val title: String,
    val record: String,
    val badge: RestoreBadge,
    val heroTitle: String,
    val heroBody: String,
    val summaryRows: List<RestoreSummaryRow> = emptyList(),
    val scopes: List<RestoreScopeSpec> = emptyList(),
    val selectedScopeKeys: List<String> = emptyList(),
    val warningTitle: String? = null,
    val warningBody: String? = null,
    val progress: Float? = null,
    val stages: List<RestoreStageItem> = emptyList(),
    val conflicts: List<RestoreConflictItem> = emptyList(),
    val resultItems: List<RestoreResultItem> = emptyList(),
    val actions: List<RestoreAction> = emptyList()
)

enum class RestoreTone { Good, Warn, Info, Muted }

enum class RestoreConflictChoice { Local, Remote }

const val DefaultRestoreRecord = "WebDAV · 2026-06-23 08:00 · 完整备份"

val RestoreScopeCatalog = listOf(
    RestoreScopeSpec("bookshelf", "书架与分组", "恢复书架书籍、分组和排序", "128 本书 · 12 个分组"),
    RestoreScopeSpec("progress", "阅读进度", "恢复章节位置和阅读进度", "96 条阅读进度"),
    RestoreScopeSpec("settings", "阅读与 App 设置", "恢复主题、排版和通用设置", "主题、排版、通用设置"),
    RestoreScopeSpec("sources", "书源配置", "恢复书源、分组和启用状态", "12 个书源 · 4 个分组")
)

fun restoreDefaultScopeKeys(): List<String> = RestoreScopeCatalog.map { it.key }

fun restoreAvailableScopeKeys(state: RestoreUiState): List<String> {
    val available = state.availableScopeKeys.ifEmpty { restoreDefaultScopeKeys() }
    return available.filter { key -> RestoreScopeCatalog.any { it.key == key } }
}

fun restoreSelectedScopeKeys(state: RestoreUiState): List<String> {
    val available = restoreAvailableScopeKeys(state)
    val selected = state.selectedScopeKeys.ifEmpty { available }
    return selected.filter { it in available }
}

fun restoreScopeLabel(keys: List<String>): String =
    restoreScopesForKeys(keys)
        .joinToString("、") { it.title }

fun restoreScopeImpact(keys: List<String>): String {
    val impacts = restoreScopesForKeys(keys).map { it.impact }
    return if (impacts.size > 2) {
        "${impacts.take(2).joinToString(" · ")} 等 ${impacts.size} 项"
    } else {
        impacts.joinToString(" · ")
    }
}

fun restorePageForRoute(routeId: String, state: RestoreUiState = RestoreUiState()): RestorePageState {
    val normalizedRouteId = RestoreRouteIds.normalize(routeId)
    val selectedScopeKeys = restoreSelectedScopeKeys(state)
    val scopeRows = listOf(
        RestoreSummaryRow("备份来源", state.selectedRestoreRecord),
        RestoreSummaryRow("恢复范围", restoreScopeLabel(selectedScopeKeys)),
        RestoreSummaryRow("预计影响", restoreScopeImpact(selectedScopeKeys)),
        RestoreSummaryRow("可回退点", "恢复前自动生成本地快照")
    )

    return when (normalizedRouteId) {
        RestoreRouteIds.Progress -> RestorePageState(
            routeId = normalizedRouteId,
            title = "恢复进度",
            record = state.selectedRestoreRecord,
            badge = RestoreBadge("进行中", RestoreTone.Warn),
            heroTitle = "正在恢复",
            heroBody = "当前正在合并书架和阅读进度。离开页面不会中断恢复，完成后会进入结果状态。",
            progress = 0.68f,
            stages = listOf(
                RestoreStageItem("下载备份", "12.8 MB · WebDAV", RestoreBadge("完成", RestoreTone.Good), 1f, done = true),
                RestoreStageItem("校验文件", "manifest、hash、版本兼容", RestoreBadge("完成", RestoreTone.Good), 1f, done = true),
                RestoreStageItem("合并数据", "书架 128 本 · 进度 96 条", RestoreBadge("进行中", RestoreTone.Warn), 0.68f, active = true),
                RestoreStageItem("写入设置", "等待合并完成", RestoreBadge("等待", RestoreTone.Muted), 0f)
            ),
            actions = listOf(
                RestoreAction("处理冲突", RestoreRouteIds.Conflict),
                RestoreAction("查看结果", RestoreRouteIds.Result, primary = true)
            )
        )

        RestoreRouteIds.Conflict -> RestorePageState(
            routeId = normalizedRouteId,
            title = "恢复冲突",
            record = state.selectedRestoreRecord,
            badge = RestoreBadge("3 项冲突", RestoreTone.Warn),
            heroTitle = "选择冲突处理方式",
            heroBody = "以下项目本地和备份均有更新。请选择保留本地或使用备份，选择后恢复会继续。",
            conflicts = listOf(
                RestoreConflictItem("group-fantasy", "分组：玄幻连载", "本地 42 本 · 远程 46 本", "保留本地", "使用备份"),
                RestoreConflictItem("progress-long-night", "阅读进度：长夜余火", "本地第 32 章 · 远程第 35 章", "本地进度", "远程进度"),
                RestoreConflictItem("setting-light-theme", "阅读设置：浅色主题", "本地字号 18 · 远程字号 17", "本机设置", "备份设置")
            ),
            actions = listOf(
                RestoreAction("返回进度", RestoreRouteIds.Progress),
                RestoreAction("应用选择", RestoreRouteIds.Result, primary = true)
            )
        )

        RestoreRouteIds.Result -> RestorePageState(
            routeId = normalizedRouteId,
            title = "恢复结果",
            record = state.selectedRestoreRecord,
            badge = RestoreBadge("部分成功", RestoreTone.Warn),
            heroTitle = "恢复完成",
            heroBody = "书架、分组和阅读进度已恢复。1 条书源配置因版本不兼容被跳过，可在日志中查看详情。",
            summaryRows = listOf(
                RestoreSummaryRow("恢复书籍", "128 本"),
                RestoreSummaryRow("恢复分组", "12 个"),
                RestoreSummaryRow("恢复进度", "96 条"),
                RestoreSummaryRow("跳过项目", "1 条")
            ),
            resultItems = listOf(
                RestoreResultItem("书架与分组", "已恢复 128 本书和 12 个分组", RestoreBadge("成功", RestoreTone.Good)),
                RestoreResultItem("阅读进度", "已恢复 96 条进度记录", RestoreBadge("成功", RestoreTone.Good)),
                RestoreResultItem("书源配置", "1 条旧版规则字段不兼容", RestoreBadge("跳过", RestoreTone.Warn))
            ),
            actions = listOf(
                RestoreAction("查看日志"),
                RestoreAction("返回同步页", RestoreRouteIds.SyncBackup, primary = true)
            )
        )

        else -> RestorePageState(
            routeId = RestoreRouteIds.Confirm,
            title = "恢复确认",
            record = state.selectedRestoreRecord,
            badge = RestoreBadge("待确认", RestoreTone.Warn),
            heroTitle = "确认恢复数据",
            heroBody = "将使用选中的备份覆盖本机同类数据。恢复前会创建本地快照，取消不会改变当前数据。",
            summaryRows = scopeRows,
            scopes = RestoreScopeCatalog.filter { it.key in restoreAvailableScopeKeys(state) },
            selectedScopeKeys = selectedScopeKeys,
            warningTitle = "覆盖提醒",
            warningBody = "冲突项会在恢复过程中单独确认，不会静默覆盖。",
            actions = listOf(
                RestoreAction("取消", RestoreRouteIds.SyncBackup),
                RestoreAction("开始恢复", RestoreRouteIds.Progress, primary = true)
            )
        )
    }
}

private fun restoreScopesForKeys(keys: List<String>): List<RestoreScopeSpec> {
    val selectedKeys = keys.ifEmpty { restoreDefaultScopeKeys() }
    return RestoreScopeCatalog.filter { it.key in selectedKeys }
}
