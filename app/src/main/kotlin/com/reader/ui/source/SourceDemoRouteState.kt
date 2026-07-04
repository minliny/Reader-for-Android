package com.reader.ui.source

object SourceDemoRouteIds {
    const val SourceManagement = "source-management"
    const val SourceImportPreview = "source-import-preview"
    const val SourceImportOptions = "source-import-options"
    const val SourceBatch = "source-batch"
    const val SourceGroups = "source-groups"
    const val SourceDetail = "source-detail"
    const val SourceDetect = "source-detect"
    const val SourceRuleEdit = "source-rule-edit"
    const val SourceDebug = "source-debug"
    const val SourceDebugSearchResult = "source-debug-search-result"
    const val SourceDebugDetailResult = "source-debug-detail-result"
    const val SourceDebugCatalogResult = "source-debug-catalog-result"
    const val SourceDebugContentLog = "source-debug-content-log"
    const val SourceEditDebug = "source-edit-debug"
    const val SourceLogs = "source-logs"
    const val SourceCodeView = "source-code-view"
    const val SourceDeleteConfirm = "source-delete-confirm"

    val requestedRouteIds = listOf(
        SourceImportOptions,
        SourceBatch,
        SourceGroups,
        SourceDetail,
        SourceDetect,
        SourceRuleEdit,
        SourceDebug,
        SourceDebugSearchResult,
        SourceDebugDetailResult,
        SourceDebugCatalogResult,
        SourceDebugContentLog,
        SourceEditDebug,
        SourceLogs,
        SourceCodeView,
        SourceDeleteConfirm
    )
}

enum class SourceDemoRouteFamily {
    Management,
    RuleEdit,
    Debug
}

enum class SourceDemoTone {
    Good,
    Warn,
    Muted,
    Info
}

enum class SourceDemoIcon {
    Activity,
    Add,
    Check,
    Close,
    Cloud,
    Code,
    Copy,
    Download,
    Edit,
    File,
    Folder,
    Log,
    More,
    Refresh,
    Search,
    Source,
    Trash,
    Upload,
    Warning
}

data class SourceDemoBadge(
    val label: String,
    val tone: SourceDemoTone
)

data class SourceDemoAction(
    val label: String,
    val routeId: String? = null,
    val icon: SourceDemoIcon? = null,
    val primary: Boolean = false,
    val danger: Boolean = false,
    val replace: Boolean = false,
    val actionKey: String? = null,
    val routeBack: Boolean = false,
    val ariaLabel: String? = null
)

data class SourceDemoRow(
    val title: String,
    val meta: String,
    val badge: SourceDemoBadge? = null,
    val routeId: String? = null,
    val icon: SourceDemoIcon? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false
)

data class SourceDemoKeyValue(
    val label: String,
    val value: String
)

sealed interface SourceDemoRouteState {
    val routeId: String
    val title: String
    val family: SourceDemoRouteFamily
    val actions: List<SourceDemoAction>
    val trailingAction: SourceDemoAction?
}

enum class SourceManagementPage {
    ImportOptions,
    Batch,
    Groups,
    Detail,
    Logs,
    DeleteConfirm
}

data class SourceManagementRouteState(
    override val routeId: String,
    val page: SourceManagementPage,
    override val title: String,
    val sourceRows: List<SourceDemoRow> = sourceDemoRows(),
    val groupRows: List<SourceDemoRow> = emptyList(),
    val moduleRows: List<SourceDemoRow> = emptyList(),
    val infoRows: List<SourceDemoKeyValue> = emptyList(),
    val logRows: List<SourceDemoRow> = emptyList(),
    val sheetActions: List<SourceDemoAction> = emptyList(),
    val dialogActions: List<SourceDemoAction> = emptyList(),
    override val actions: List<SourceDemoAction> = emptyList(),
    override val trailingAction: SourceDemoAction? = null
) : SourceDemoRouteState {
    override val family = SourceDemoRouteFamily.Management
}

enum class SourceRuleEditPage {
    RuleEdit,
    EditDebug
}

data class SourceRuleSection(
    val title: String,
    val rows: List<SourceDemoKeyValue>
)

data class SourceRuleEditRouteState(
    override val routeId: String,
    val page: SourceRuleEditPage,
    override val title: String = "规则编辑",
    val sections: List<SourceRuleSection> = sourceRuleSections(),
    val overviewRows: List<SourceDemoKeyValue> = listOf(
        SourceDemoKeyValue("当前模块", "正文"),
        SourceDemoKeyValue("最近调测", "失败 · 0 字"),
        SourceDemoKeyValue("规则版本", "v3")
    ),
    override val actions: List<SourceDemoAction> = listOf(
        SourceDemoAction("保存规则", actionKey = "save-rule"),
        SourceDemoAction("调测当前模块", routeId = SourceDemoRouteIds.SourceDebug, primary = true)
    ),
    override val trailingAction: SourceDemoAction? = SourceDemoAction("保存", actionKey = "save-rule")
) : SourceDemoRouteState {
    override val family = SourceDemoRouteFamily.RuleEdit
}

enum class SourceDebugPage {
    Detect,
    ContentDebug,
    SearchResult,
    DetailResult,
    CatalogResult,
    ContentLog,
    CodeView
}

data class SourceDebugModule(
    val key: String,
    val title: String,
    val meta: String,
    val badge: SourceDemoBadge,
    val routeId: String
)

data class SourceDebugCase(
    val key: String,
    val title: String,
    val inputLabel: String,
    val inputValue: String,
    val ruleLabel: String,
    val ruleValue: String,
    val result: String,
    val badge: SourceDemoBadge
)

data class SourceDebugRouteState(
    override val routeId: String,
    val page: SourceDebugPage,
    override val title: String,
    val contextTitle: String,
    val contextMeta: String,
    val contextBadge: SourceDemoBadge,
    val activeModuleKey: String,
    val inputs: List<SourceDemoKeyValue> = emptyList(),
    val request: String? = null,
    val parsedRows: List<SourceDemoKeyValue> = emptyList(),
    val logs: List<SourceDemoRow> = emptyList(),
    val codeLines: List<String> = emptyList(),
    val detectSteps: List<SourceDemoRow> = emptyList(),
    val suggestionTitle: String? = null,
    val suggestionText: String? = null,
    val suggestionMeta: String? = null,
    val segmentActions: List<SourceDemoAction> = sourceDebugSegmentActions(routeId),
    override val actions: List<SourceDemoAction> = emptyList(),
    override val trailingAction: SourceDemoAction? = null
) : SourceDemoRouteState {
    override val family = SourceDemoRouteFamily.Debug
}

fun sourceDemoRouteIds(): Set<String> = SourceDemoRouteIds.requestedRouteIds.toSet()

fun sourceDemoRouteState(routeId: String): SourceDemoRouteState? = when (routeId) {
    SourceDemoRouteIds.SourceImportOptions -> sourceImportOptionsRouteState()
    SourceDemoRouteIds.SourceBatch -> sourceBatchRouteState()
    SourceDemoRouteIds.SourceGroups -> sourceGroupsRouteState()
    SourceDemoRouteIds.SourceDetail -> sourceDetailRouteState()
    SourceDemoRouteIds.SourceDetect -> sourceDetectRouteState()
    SourceDemoRouteIds.SourceRuleEdit -> sourceRuleEditRouteState(routeId)
    SourceDemoRouteIds.SourceDebug -> sourceContentDebugRouteState()
    SourceDemoRouteIds.SourceDebugSearchResult,
    SourceDemoRouteIds.SourceDebugDetailResult,
    SourceDemoRouteIds.SourceDebugCatalogResult -> sourceDebugResultRouteState(routeId)
    SourceDemoRouteIds.SourceDebugContentLog -> sourceDebugContentLogRouteState()
    SourceDemoRouteIds.SourceEditDebug -> sourceRuleEditRouteState(routeId)
    SourceDemoRouteIds.SourceLogs -> sourceLogsRouteState()
    SourceDemoRouteIds.SourceCodeView -> sourceCodeViewRouteState()
    SourceDemoRouteIds.SourceDeleteConfirm -> sourceDeleteConfirmRouteState()
    else -> null
}

fun sourceImportOptionsRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceImportOptions,
    page = SourceManagementPage.ImportOptions,
    title = "书源管理",
    sheetActions = listOf(
        SourceDemoAction("网络导入", routeId = SourceDemoRouteIds.SourceImportPreview, icon = SourceDemoIcon.Cloud, actionKey = "import-url"),
        SourceDemoAction("本地导入", routeId = SourceDemoRouteIds.SourceImportPreview, icon = SourceDemoIcon.Folder, actionKey = "import-local"),
        SourceDemoAction("剪贴板导入", routeId = SourceDemoRouteIds.SourceImportPreview, icon = SourceDemoIcon.File, actionKey = "import-clipboard"),
        SourceDemoAction("手动新建", routeId = SourceDemoRouteIds.SourceRuleEdit, icon = SourceDemoIcon.Edit, actionKey = "create-manual"),
        SourceDemoAction("取消", routeId = SourceDemoRouteIds.SourceManagement, replace = true, routeBack = true)
    ),
    actions = sourceHomeActions()
)

fun sourceBatchRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceBatch,
    page = SourceManagementPage.Batch,
    title = "已选 3 个",
    actions = listOf(
        SourceDemoAction("启用", icon = SourceDemoIcon.Check, actionKey = "enable-selected"),
        SourceDemoAction("禁用", icon = SourceDemoIcon.Close, actionKey = "disable-selected"),
        SourceDemoAction("检测", icon = SourceDemoIcon.Activity, actionKey = "detect-selected"),
        SourceDemoAction("分组", routeId = SourceDemoRouteIds.SourceGroups, icon = SourceDemoIcon.Folder, actionKey = "group-selected"),
        SourceDemoAction(
            "删除",
            routeId = SourceDemoRouteIds.SourceDeleteConfirm,
            icon = SourceDemoIcon.Trash,
            danger = true,
            actionKey = "delete-selected",
            ariaLabel = "删除已选 3 个书源"
        )
    )
)

fun sourceGroupsRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceGroups,
    page = SourceManagementPage.Groups,
    title = "分组管理",
    groupRows = listOf(
        SourceDemoRow("全部分组", "12 个书源", icon = SourceDemoIcon.Folder),
        SourceDemoRow("玄幻书源", "4 个书源", badge = SourceDemoBadge("当前筛选", SourceDemoTone.Info), icon = SourceDemoIcon.Folder),
        SourceDemoRow("起点导入", "3 个书源", icon = SourceDemoIcon.Folder),
        SourceDemoRow("测试书源", "3 个书源", icon = SourceDemoIcon.Folder),
        SourceDemoRow("自定义", "2 个书源", icon = SourceDemoIcon.Folder),
        SourceDemoRow("未分组", "1 个书源", icon = SourceDemoIcon.Folder)
    ),
    actions = listOf(
        SourceDemoAction("批量移动", actionKey = "move-groups"),
        SourceDemoAction("新增分组", primary = true, actionKey = "create-group")
    ),
    trailingAction = SourceDemoAction("新增", actionKey = "create-group")
)

fun sourceDetailRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceDetail,
    page = SourceManagementPage.Detail,
    title = "书源详情",
    moduleRows = listOf(
        SourceDemoRow("站点", "可访问", badge = SourceDemoBadge("可访问", SourceDemoTone.Good)),
        SourceDemoRow("搜索", "正常", badge = SourceDemoBadge("正常", SourceDemoTone.Good)),
        SourceDemoRow("详情", "正常", badge = SourceDemoBadge("正常", SourceDemoTone.Good)),
        SourceDemoRow("目录", "正常", badge = SourceDemoBadge("正常", SourceDemoTone.Good)),
        SourceDemoRow("正文", "异常", badge = SourceDemoBadge("异常", SourceDemoTone.Warn)),
        SourceDemoRow("登录", "未启用", badge = SourceDemoBadge("未启用", SourceDemoTone.Muted))
    ),
    infoRows = listOf(
        SourceDemoKeyValue("请求方式", "GET · UTF-8"),
        SourceDemoKeyValue("并发限制", "2 个请求"),
        SourceDemoKeyValue("Cookie", "未启用"),
        SourceDemoKeyValue("更新时间", "今天 10:12")
    ),
    actions = listOf(
        SourceDemoAction("检测此源", routeId = SourceDemoRouteIds.SourceDetect),
        SourceDemoAction("编辑规则", routeId = SourceDemoRouteIds.SourceRuleEdit, primary = true),
        SourceDemoAction("删除", routeId = SourceDemoRouteIds.SourceDeleteConfirm, danger = true)
    )
)

fun sourceLogsRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceLogs,
    page = SourceManagementPage.Logs,
    title = "错误日志",
    logRows = listOf(
        SourceDemoRow("笔趣阁 · ERROR", "10:30 · 正文 · 正文规则返回空内容", routeId = SourceDemoRouteIds.SourceDebugContentLog, badge = SourceDemoBadge("错误", SourceDemoTone.Warn)),
        SourceDemoRow("旧规则源 · ERROR", "10:22 · 搜索 · HTTP 403", routeId = SourceDemoRouteIds.SourceDebug, badge = SourceDemoBadge("错误", SourceDemoTone.Warn)),
        SourceDemoRow("本地导入源 · WARN", "09:50 · 目录 · 尚未检测", routeId = SourceDemoRouteIds.SourceDebug, badge = SourceDemoBadge("警告", SourceDemoTone.Muted)),
        SourceDemoRow("失效示例源 · ERROR", "昨天 · 详情 · 详情页 URL 为空", routeId = SourceDemoRouteIds.SourceDebug, badge = SourceDemoBadge("错误", SourceDemoTone.Warn))
    ),
    actions = listOf(
        SourceDemoAction("复制全部", actionKey = "copy-all-logs"),
        SourceDemoAction("重新检测异常", primary = true, actionKey = "detect-error-sources")
    ),
    trailingAction = SourceDemoAction("清空", routeId = SourceDemoRouteIds.SourceDeleteConfirm, danger = true)
)

fun sourceDeleteConfirmRouteState() = SourceManagementRouteState(
    routeId = SourceDemoRouteIds.SourceDeleteConfirm,
    page = SourceManagementPage.DeleteConfirm,
    title = "已选 3 个",
    dialogActions = listOf(
        SourceDemoAction("取消", routeId = SourceDemoRouteIds.SourceBatch, routeBack = true),
        SourceDemoAction(
            "删除",
            routeId = SourceDemoRouteIds.SourceManagement,
            danger = true,
            replace = true,
            actionKey = "delete-confirm"
        )
    )
)

fun sourceRuleEditRouteState(routeId: String) = SourceRuleEditRouteState(
    routeId = routeId,
    page = if (routeId == SourceDemoRouteIds.SourceEditDebug) SourceRuleEditPage.EditDebug else SourceRuleEditPage.RuleEdit
)

fun sourceDetectRouteState() = SourceDebugRouteState(
    routeId = SourceDemoRouteIds.SourceDetect,
    page = SourceDebugPage.Detect,
    title = "书源检测",
    contextTitle = "笔趣阁",
    contextMeta = "检测对象 · biquge.example",
    contextBadge = SourceDemoBadge("异常", SourceDemoTone.Warn),
    activeModuleKey = "content",
    detectSteps = listOf(
        SourceDemoRow("站点访问", "200 OK · 126ms", badge = SourceDemoBadge("通过", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceCodeView),
        SourceDemoRow("搜索规则", "关键词“斗破苍穹”返回 12 条", badge = SourceDemoBadge("通过", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDebugSearchResult),
        SourceDemoRow("详情规则", "书名、作者、封面、简介均解析成功", badge = SourceDemoBadge("通过", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDebugDetailResult),
        SourceDemoRow("目录规则", "解析 812 章，章节 URL 有效", badge = SourceDemoBadge("通过", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDebugCatalogResult),
        SourceDemoRow("正文规则", "“#content@text”返回空内容", badge = SourceDemoBadge("失败", SourceDemoTone.Warn), routeId = SourceDemoRouteIds.SourceDebug)
    ),
    suggestionTitle = "失败定位",
    suggestionText = "正文请求成功，但正文选择器没有匹配到有效文本。",
    suggestionMeta = "下一步应进入正文模块调测，比较原始 HTML 与当前正文规则。",
    actions = listOf(
        SourceDemoAction("重新检测", actionKey = "rerun-detect"),
        SourceDemoAction("编辑正文规则", routeId = SourceDemoRouteIds.SourceRuleEdit, primary = true)
    )
)

fun sourceContentDebugRouteState() = SourceDebugRouteState(
    routeId = SourceDemoRouteIds.SourceDebug,
    page = SourceDebugPage.ContentDebug,
    title = "书源调测",
    contextTitle = "正文模块调测",
    contextMeta = "笔趣阁 · 第 128 章 风雨夜",
    contextBadge = SourceDemoBadge("失败", SourceDemoTone.Warn),
    activeModuleKey = "content",
    inputs = listOf(
        SourceDemoKeyValue("章节 URL", "/book/123/128.html"),
        SourceDemoKeyValue("正文规则", "#content@text")
    ),
    request = "GET https://biquge.example/book/123/128.html · 200 OK · 412ms",
    parsedRows = listOf(
        SourceDemoKeyValue("章节标题", "第 128 章 风雨夜"),
        SourceDemoKeyValue("正文长度", "0 字"),
        SourceDemoKeyValue("匹配节点", "0 个"),
        SourceDemoKeyValue("错误原因", "正文选择器未命中")
    ),
    suggestionTitle = "修复建议",
    suggestionText = "原始页面正文可能在“.chapter-content”容器内，当前“#content”无匹配。",
    suggestionMeta = "可尝试将正文内容规则改为“.chapter-content@text”后重新调测。",
    actions = listOf(
        SourceDemoAction("重新调测", actionKey = "rerun-debug"),
        SourceDemoAction("回到编辑", routeId = SourceDemoRouteIds.SourceRuleEdit, primary = true)
    )
)

fun sourceDebugResultRouteState(routeId: String): SourceDebugRouteState? {
    val page = sourceDebugResultPages()[routeId] ?: return null
    return SourceDebugRouteState(
        routeId = routeId,
        page = page.page,
        title = "书源调测",
        contextTitle = page.contextTitle,
        contextMeta = page.contextMeta,
        contextBadge = page.badge,
        activeModuleKey = page.activeModuleKey,
        inputs = page.inputs,
        request = page.request,
        parsedRows = page.parsedRows,
        suggestionTitle = page.suggestionTitle,
        suggestionText = page.suggestionText,
        actions = listOf(
            SourceDemoAction("重新调测", actionKey = "rerun-debug"),
            SourceDemoAction("回到编辑", routeId = SourceDemoRouteIds.SourceRuleEdit, primary = true)
        )
    )
}

fun sourceDebugContentLogRouteState() = SourceDebugRouteState(
    routeId = SourceDemoRouteIds.SourceDebugContentLog,
    page = SourceDebugPage.ContentLog,
    title = "书源调测",
    contextTitle = "正文模块日志",
    contextMeta = "笔趣阁 · 第 128 章 风雨夜",
    contextBadge = SourceDemoBadge("失败", SourceDemoTone.Warn),
    activeModuleKey = "content",
    inputs = listOf(
        SourceDemoKeyValue("章节 URL", "/book/123/128.html"),
        SourceDemoKeyValue("正文规则", "#content@text")
    ),
    request = "GET https://biquge.example/book/123/128.html · 200 OK · 412ms",
    logs = listOf(
        SourceDemoRow("10:30:18.120 · 请求章节 HTML", "GET /book/123/128.html · 200 OK · 412ms", badge = SourceDemoBadge("记录", SourceDemoTone.Muted)),
        SourceDemoRow("10:30:18.204 · 执行正文规则", "#content@text · 匹配节点 0 个", badge = SourceDemoBadge("记录", SourceDemoTone.Muted)),
        SourceDemoRow("10:30:18.226 · 执行净化规则", "未进入净化阶段，正文为空", badge = SourceDemoBadge("记录", SourceDemoTone.Muted)),
        SourceDemoRow("10:30:18.240 · 返回错误", "正文内容为空，建议检查选择器或源码结构", badge = SourceDemoBadge("错误", SourceDemoTone.Warn))
    ),
    suggestionTitle = "定位结果",
    suggestionText = "请求成功但正文选择器无匹配，源码中正文位于“.chapter-content”容器。",
    suggestionMeta = "可复制日志后回到规则编辑，将正文规则改为“.chapter-content@text”。",
    actions = listOf(
        SourceDemoAction("复制日志", actionKey = "copy-content-log"),
        SourceDemoAction("回到解析", routeId = SourceDemoRouteIds.SourceDebug),
        SourceDemoAction("回到编辑", routeId = SourceDemoRouteIds.SourceRuleEdit, primary = true)
    ),
    segmentActions = sourceDebugSegmentActions(SourceDemoRouteIds.SourceDebug),
    trailingAction = SourceDemoAction("复制", actionKey = "copy-content-log")
)

fun sourceCodeViewRouteState() = SourceDebugRouteState(
    routeId = SourceDemoRouteIds.SourceCodeView,
    page = SourceDebugPage.CodeView,
    title = "书源调测",
    contextTitle = "源码查看",
    contextMeta = "正文模块 · 当前请求返回",
    contextBadge = SourceDemoBadge("200 OK", SourceDemoTone.Good),
    activeModuleKey = "content",
    inputs = listOf(
        SourceDemoKeyValue("章节 URL", "/book/123/128.html"),
        SourceDemoKeyValue("正文规则", "#content@text")
    ),
    request = "GET https://biquge.example/book/123/128.html · 200 OK · 412ms",
    codeLines = listOf(
        "<html>",
        "  <body>",
        "    <h1 class=\"chapter-title\">第 128 章 风雨夜</h1>",
        "    <main class=\"chapter-content\">",
        "      <p>雨声在檐下连成一片，旧街的灯光被水汽晕开。</p>",
        "      <p>他把地图折回怀里，终于确认了下一处坐标。</p>",
        "    </main>",
        "    <a class=\"next\" href=\"/book/123/129.html\">下一章</a>",
        "  </body>",
        "</html>"
    ),
    actions = listOf(
        SourceDemoAction("重新请求", actionKey = "reload-source-code"),
        SourceDemoAction("回到调测", routeId = SourceDemoRouteIds.SourceDebug, primary = true)
    ),
    segmentActions = sourceDebugSegmentActions(SourceDemoRouteIds.SourceDebug),
    trailingAction = SourceDemoAction("复制", actionKey = "copy-source-code")
)

fun sourceDebugModules(): List<SourceDebugModule> = listOf(
    SourceDebugModule("search", "搜索", "关键词 -> 结果列表", SourceDemoBadge("通过", SourceDemoTone.Good), SourceDemoRouteIds.SourceDebugSearchResult),
    SourceDebugModule("detail", "详情", "详情 URL -> 书籍字段", SourceDemoBadge("通过", SourceDemoTone.Good), SourceDemoRouteIds.SourceDebugDetailResult),
    SourceDebugModule("catalog", "目录", "目录 URL -> 章节列表", SourceDemoBadge("通过", SourceDemoTone.Good), SourceDemoRouteIds.SourceDebugCatalogResult),
    SourceDebugModule("content", "正文", "章节 URL -> 正文文本", SourceDemoBadge("失败", SourceDemoTone.Warn), SourceDemoRouteIds.SourceDebug)
)

fun sourceDebugCases(): List<SourceDebugCase> = listOf(
    SourceDebugCase("search", "搜索调测", "输入关键词", "斗破苍穹", "结果列表规则", ".book-list > li", "返回 12 条 · 书名/作者/详情 URL 有效", SourceDemoBadge("通过", SourceDemoTone.Good)),
    SourceDebugCase("detail", "详情调测", "详情 URL", "/book/123/", "字段规则", "h1@text / .author@text", "书名、作者、封面、简介解析成功", SourceDemoBadge("通过", SourceDemoTone.Good)),
    SourceDebugCase("catalog", "目录调测", "目录 URL", "/book/123/catalog", "章节列表规则", ".chapter-list a", "解析 812 章 · 首尾章节 URL 有效", SourceDemoBadge("通过", SourceDemoTone.Good)),
    SourceDebugCase("content", "正文调测", "章节 URL", "/book/123/128.html", "正文内容规则", "#content@text", "正文长度 0 字 · 匹配节点 0 个", SourceDemoBadge("失败", SourceDemoTone.Warn))
)

fun sourceDebugSegmentActions(resultRouteId: String): List<SourceDemoAction> = listOf(
    SourceDemoAction("解析结果", routeId = resultRouteId),
    SourceDemoAction("源码", routeId = SourceDemoRouteIds.SourceCodeView),
    SourceDemoAction("日志", routeId = SourceDemoRouteIds.SourceDebugContentLog)
)

private data class SourceDebugResultPage(
    val page: SourceDebugPage,
    val activeModuleKey: String,
    val contextTitle: String,
    val contextMeta: String,
    val badge: SourceDemoBadge,
    val request: String,
    val inputs: List<SourceDemoKeyValue>,
    val parsedRows: List<SourceDemoKeyValue>,
    val suggestionTitle: String,
    val suggestionText: String
)

private fun sourceDebugResultPages() = mapOf(
    SourceDemoRouteIds.SourceDebugSearchResult to SourceDebugResultPage(
        page = SourceDebugPage.SearchResult,
        activeModuleKey = "search",
        contextTitle = "搜索模块调测",
        contextMeta = "笔趣阁 · 关键词 斗破苍穹",
        badge = SourceDemoBadge("通过", SourceDemoTone.Good),
        request = "GET https://biquge.example/search?q=斗破苍穹 · 200 OK · 286ms",
        inputs = listOf(SourceDemoKeyValue("关键词", "斗破苍穹"), SourceDemoKeyValue("结果规则", ".book-list > li")),
        parsedRows = listOf(
            SourceDemoKeyValue("命中数量", "12 条"),
            SourceDemoKeyValue("书名字段", ".title@text · 12/12"),
            SourceDemoKeyValue("作者字段", ".author@text · 12/12"),
            SourceDemoKeyValue("详情 URL", "12/12 有效")
        ),
        suggestionTitle = "搜索结果有效",
        suggestionText = "结果列表、书名、作者和详情 URL 均可用于下一步详情调测。"
    ),
    SourceDemoRouteIds.SourceDebugDetailResult to SourceDebugResultPage(
        page = SourceDebugPage.DetailResult,
        activeModuleKey = "detail",
        contextTitle = "详情模块调测",
        contextMeta = "笔趣阁 · /book/123/",
        badge = SourceDemoBadge("通过", SourceDemoTone.Good),
        request = "GET https://biquge.example/book/123/ · 200 OK · 318ms",
        inputs = listOf(SourceDemoKeyValue("详情 URL", "/book/123/"), SourceDemoKeyValue("字段规则", "h1@text / .author@text")),
        parsedRows = listOf(
            SourceDemoKeyValue("书名", "斗破苍穹"),
            SourceDemoKeyValue("作者", "天蚕土豆"),
            SourceDemoKeyValue("封面", "cover.jpg · 200 OK"),
            SourceDemoKeyValue("简介", "186 字")
        ),
        suggestionTitle = "详情字段有效",
        suggestionText = "书名、作者、封面、简介均已解析，可继续目录模块调测。"
    ),
    SourceDemoRouteIds.SourceDebugCatalogResult to SourceDebugResultPage(
        page = SourceDebugPage.CatalogResult,
        activeModuleKey = "catalog",
        contextTitle = "目录模块调测",
        contextMeta = "笔趣阁 · /book/123/catalog",
        badge = SourceDemoBadge("通过", SourceDemoTone.Good),
        request = "GET https://biquge.example/book/123/catalog · 200 OK · 366ms",
        inputs = listOf(SourceDemoKeyValue("目录 URL", "/book/123/catalog"), SourceDemoKeyValue("章节规则", ".chapter-list a")),
        parsedRows = listOf(
            SourceDemoKeyValue("章节数量", "812 章"),
            SourceDemoKeyValue("首章", "第 1 章 陨落的天才"),
            SourceDemoKeyValue("末章", "第 812 章 大结局"),
            SourceDemoKeyValue("URL 有效", "812/812")
        ),
        suggestionTitle = "目录字段有效",
        suggestionText = "章节名和章节 URL 已匹配，下一步应调测正文内容规则。"
    )
)

private fun sourceHomeActions() = listOf(
    SourceDemoAction("批量管理", routeId = SourceDemoRouteIds.SourceBatch),
    SourceDemoAction("新增书源", routeId = SourceDemoRouteIds.SourceImportOptions, primary = true)
)

private fun sourceDemoRows() = listOf(
    SourceDemoRow("起点中文网", "qidian.com · 起点导入", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("笔趣阁", "biquge.example · 玄幻书源", SourceDemoBadge("异常", SourceDemoTone.Warn), routeId = SourceDemoRouteIds.SourceDetail, enabled = true, selected = true),
    SourceDemoRow("本地导入源", "本地文件导入 · 自定义", SourceDemoBadge("未检测", SourceDemoTone.Muted), routeId = SourceDemoRouteIds.SourceDetail, enabled = false),
    SourceDemoRow("测试书源", "test.example · 测试书源", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("轻小说文库", "lightnovel.example · 测试书源", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("旧规则源", "old.example · 自定义", SourceDemoBadge("异常", SourceDemoTone.Warn), routeId = SourceDemoRouteIds.SourceDetail, enabled = true, selected = true),
    SourceDemoRow("飞卢小说网", "faloo.com · 玄幻书源", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("晋江文学城", "jjwx.example · 起点导入", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("纵横中文网", "zongheng.com · 玄幻书源", SourceDemoBadge("未检测", SourceDemoTone.Muted), routeId = SourceDemoRouteIds.SourceDetail, enabled = false),
    SourceDemoRow("豆瓣阅读", "read.douban.com · 自定义", SourceDemoBadge("可用", SourceDemoTone.Good), routeId = SourceDemoRouteIds.SourceDetail, enabled = true),
    SourceDemoRow("失效示例源", "dead.example · 测试书源", SourceDemoBadge("异常", SourceDemoTone.Warn), routeId = SourceDemoRouteIds.SourceDetail, enabled = false, selected = true)
)

private fun sourceRuleSections() = listOf(
    SourceRuleSection(
        "基础配置",
        listOf(
            SourceDemoKeyValue("书源名称", "笔趣阁"),
            SourceDemoKeyValue("书源地址", "https://biquge.example"),
            SourceDemoKeyValue("书源分组", "玄幻书源"),
            SourceDemoKeyValue("启用状态", "已启用")
        )
    ),
    SourceRuleSection(
        "请求配置",
        listOf(
            SourceDemoKeyValue("请求方式", "GET"),
            SourceDemoKeyValue("字符编码", "UTF-8"),
            SourceDemoKeyValue("请求头", "User-Agent / Referer"),
            SourceDemoKeyValue("Cookie", "未启用")
        )
    ),
    SourceRuleSection(
        "解析规则",
        listOf(
            SourceDemoKeyValue("正文页 URL", "{{chapterUrl}}"),
            SourceDemoKeyValue("章节标题", ".chapter-title@text"),
            SourceDemoKeyValue("正文内容", "#content@text"),
            SourceDemoKeyValue("下一页", ".next@href")
        )
    ),
    SourceRuleSection(
        "后处理",
        listOf(
            SourceDemoKeyValue("内容过滤", ".ad, script, style"),
            SourceDemoKeyValue("段落处理", "保留段落换行"),
            SourceDemoKeyValue("净化规则", "去除空行"),
            SourceDemoKeyValue("失败回退", "尝试正文备用规则")
        )
    )
)
