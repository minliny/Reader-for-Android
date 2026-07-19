package com.reader.ui.demo

import com.reader.ui.shell.MainTab
import com.reader.ui.shell.ReaderRoute
import com.reader.ui.shell.RouteIds
import io.reader.ui.contract.ComponentType
import io.reader.ui.contract.RouteId
import io.reader.ui.contract.RouteShell
import kotlinx.serialization.ExperimentalSerializationApi

data class DemoRoutePage(
    val id: String,
    val title: String,
    val shell: RouteShell,
    val body: List<String>,
    val actions: List<DemoRouteAction> = emptyList()
)

data class DemoRouteAction(
    val label: String,
    val targetRoute: String,
    val onStart: (() -> Unit)? = null
)

/** Native Compose renderer families for explicit post-2.4 contract additions. */
enum class DemoRouteRenderer {
    ReaderWorkspaceState,
    ReaderReplacementState,
    SourceSwitchState,
    ReaderContentState,
    LocalImportState,
    /** Reader-UI 3.0 structure is present, but its planned actions stay fail-closed. */
    CapabilityClosureStructure
}

private data class DemoRouteAddition(
    val page: DemoRoutePage,
    val renderer: DemoRouteRenderer,
    val componentTypes: List<ComponentType> = emptyList()
)

private fun action(label: String, targetRoute: String): DemoRouteAction =
    DemoRouteAction(label = label, targetRoute = targetRoute)

private fun addition(
    id: String,
    title: String,
    shell: RouteShell,
    renderer: DemoRouteRenderer,
    body: List<String>,
    actions: List<DemoRouteAction>,
    componentTypes: List<ComponentType> = emptyList()
): DemoRouteAddition = DemoRouteAddition(
    page = DemoRoutePage(
        id = id,
        title = title,
        shell = shell,
        body = body,
        actions = actions
    ),
    renderer = renderer,
    componentTypes = componentTypes
)

private fun capabilityAddition(
    id: String,
    title: String,
    shell: RouteShell,
    componentTypes: List<ComponentType>,
    summary: String
): DemoRouteAddition = addition(
    id = id,
    title = title,
    shell = shell,
    renderer = DemoRouteRenderer.CapabilityClosureStructure,
    body = listOf(
        summary,
        "Reader-UI 3.0 顶层结构：${componentTypes.joinToString(" + ") { it.name }}。",
        "交互动作：planned / fail-closed；Host 接管前不创建业务回调。"
    ),
    actions = emptyList(),
    componentTypes = componentTypes
)

@OptIn(ExperimentalSerializationApi::class)
private fun RouteId.contractSerialName(): String =
    RouteId.serializer().descriptor.getElementName(ordinal)

object DemoRouteRegistry {
    private val authoredPages: List<DemoRoutePage> = listOf(
        DemoRoutePage(id = "discover-control", title = "发现控制层（Discover Control）", shell = RouteShell.MainTabShell, body = listOf("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover"), DemoRouteAction("优书网 默认 · 120ms", "discover-control"), DemoRouteAction("起点导入 正版 · 180ms", "discover-switching-source"), DemoRouteAction("轻小说文库 需登录", "discover-control"), DemoRouteAction("本地聚合源 维护中", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"))),
        DemoRoutePage(id = "discover-sort", title = "发现排序选择（Discover Sort）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 范围 关键词 男频 女频 排序 人气 更新 收藏 完本 字数 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"), DemoRouteAction("关键词", "discover-filter-keyword"), DemoRouteAction("男频", "discover-filter-male"))),
        DemoRoutePage(id = "discover-entry-ranking", title = "发现入口：排行榜（Discover Entry Ranking）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-bestseller", title = "发现入口：畅销（Discover Entry Bestseller）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-category", title = "发现入口：分类（Discover Entry Category）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 分类 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-finished", title = "发现入口：完本（Discover Entry Finished）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 完本 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-latest", title = "发现入口：最新（Discover Entry Latest）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 最新 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-new", title = "发现入口：新书（Discover Entry New）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-entry-booklist", title = "发现入口：书单（Discover Entry Booklist）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 书单 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-filter-keyword", title = "发现筛选：关键词（Discover Filter Keyword）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 关键词 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-filter-male", title = "发现筛选：男频（Discover Filter Male）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-filter-female", title = "发现筛选：女频（Discover Filter Female）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 女频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-sort-popularity", title = "发现排序：人气（Discover Sort Popularity）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-sort-update", title = "发现排序：更新（Discover Sort Update）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 更新 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-sort-collection", title = "发现排序：收藏（Discover Sort Collection）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 收藏 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-sort-finished", title = "发现排序：完本（Discover Sort Finished）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 完本 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-sort-words", title = "发现排序：字数（Discover Sort Words）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 字数 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-no-results", title = "发现无结果（Discover No Results）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 当前条件没有发现结果 可以重置筛选、切换入口，或刷新当前书源。", "重置筛选", "切换入口"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"), DemoRouteAction("重置筛选", "discover"), DemoRouteAction("切换入口", "discover-control"))),
        DemoRoutePage(id = "discover-loading", title = "发现加载中（Discover Loading）", shell = RouteShell.MainTabShell, body = listOf("优书网 默认分组 · 已启用发现 · 120ms", "排行榜", "分类", "完本", "最新", "书单", "筛选 男频 · 人气"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-refreshing", title = "发现刷新中（Discover Refreshing）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 正在刷新当前列表 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-infinite-loading", title = "发现继续加载（Discover Infinite Loading）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。", "旧日回响", "离线书库 · 奇幻 · 连载", "最新：第 18 章", "旧日钟声从废墟里传回，缓存章节仍可打开。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-page-two", title = "发现第二屏（Discover Loaded More）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。", "旧日回响", "离线书库 · 奇幻 · 连载", "最新：第 18 章", "旧日钟声从废墟里传回，缓存章节仍可打开。", "回到顶部"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"), DemoRouteAction("回到顶部", "discover"))),
        DemoRoutePage(id = "discover-cache-confirm", title = "清除发现缓存（Discover Cache Confirm）", shell = RouteShell.MainTabShell, body = listOf("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。", "清除发现缓存？", "将清除优书网的发现入口缓存，不影响书架和阅读进度。", "确认清除"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover"), DemoRouteAction("优书网 默认 · 120ms", "discover-control"), DemoRouteAction("起点导入 正版 · 180ms", "discover-switching-source"), DemoRouteAction("轻小说文库 需登录", "discover-control"), DemoRouteAction("本地聚合源 维护中", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"))),
        DemoRoutePage(id = "discover-cache-toast", title = "发现缓存已清除（Discover Cache Toast）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 已清除优书网发现缓存 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-login-return", title = "发现登录返回（Discover Login Return）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 默认分组 · 已启用发现 · 120ms 排行榜 分类 完本 最新 书单 筛选 男频 · 人气 应用 登录成功，正在刷新当前发现入口 排行榜 长夜余火 爱潜水的乌贼 · 科幻 · 连载 最新：第 32 章 雨夜 雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"), DemoRouteAction("最新", "discover-entry-latest"), DemoRouteAction("书单", "discover-entry-booklist"))),
        DemoRoutePage(id = "discover-switching-source", title = "发现切换书源中（Discover Switching Source）", shell = RouteShell.MainTabShell, body = listOf("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover"), DemoRouteAction("优书网 默认 · 120ms", "discover-control"), DemoRouteAction("起点导入 正在解析入口", "discover-switching-source"), DemoRouteAction("轻小说文库 需登录", "discover-control"), DemoRouteAction("本地聚合源 维护中", "discover-control"), DemoRouteAction("排行榜", "discover-entry-ranking"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("完本", "discover-entry-finished"))),
        DemoRoutePage(id = "discover-switched-source", title = "发现已切换书源（Discover Switched Source）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 起点导入 正版 · 已启用发现 · 180ms 畅销 分类 新书 完本 筛选 男频 · 更新 应用 畅销 诡秘之主 爱潜水的乌贼 · 奇幻 · 完本 最新：番外已整理 克莱恩在迷雾中醒来，新的线索沿着塔罗会延伸。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 18 章", "城市被写在纸页上，所有路口都藏着旧书源的暗号。", "灯塔与雾", "书源同步 · 悬疑 · 连载", "最新：第 51 章", "雾气吞没海岸线，灯塔的记录仍在夜里闪烁。", "群星之间", "本地导入 · 科幻 · 连载", "最新：第 12 章", "星舰穿过静默航道，旧文明的坐标重新亮起。"), actions = listOf(DemoRouteAction("起点导入 正版 · 已启用发现 · 180ms", "discover-control"), DemoRouteAction("畅销", "discover-entry-bestseller"), DemoRouteAction("分类", "discover-entry-category"), DemoRouteAction("新书", "discover-entry-new"), DemoRouteAction("完本", "discover-entry-finished"))),
        DemoRoutePage(id = "discover-entry-error", title = "发现入口解析失败（Discover Entry Error）", shell = RouteShell.MainTabShell, body = listOf("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 默认分组 · 已启用发现 · 120ms", "discover"), DemoRouteAction("优书网 默认 · 120ms", "discover-control"), DemoRouteAction("起点导入 正版 · 180ms", "discover-switching-source"), DemoRouteAction("轻小说文库 需登录", "discover-control"), DemoRouteAction("本地聚合源 维护中", "discover-control"), DemoRouteAction("重试", "discover-control"), DemoRouteAction("编辑源", "discover-rule-test"), DemoRouteAction("男频", "discover-filter-male"))),
        DemoRoutePage(id = "discover-empty", title = "发现空状态（Discover Empty）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 当前没有启用发现的书源 启用发现后，可以在这里浏览书源提供的排行榜、分类和书单。", "去书源管理", "导入书源"), actions = listOf(DemoRouteAction("去书源管理", "source-management"), DemoRouteAction("导入书源", "source-import-options"))),
        DemoRoutePage(id = "discover-error", title = "发现错误状态（Discover Error）", shell = RouteShell.MainTabShell, body = listOf("82% 发现 优书网 排行榜 · 解析失败 发现入口解析失败 当前入口返回异常，已保留上一批缓存结果。你可以重试、刷新入口、编辑源或切换书源。", "重试", "切换书源", "编辑源", "长夜余火", "爱潜水的乌贼 · 科幻 · 连载", "最新：第 32 章 雨夜", "雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", "诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", "三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", "明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", "纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。"), actions = listOf(DemoRouteAction("优书网 排行榜 · 解析失败", "discover-control"), DemoRouteAction("重试", "discover-refreshing"), DemoRouteAction("切换书源", "discover-control"), DemoRouteAction("编辑源", "discover-rule-test"))),
        DemoRoutePage(id = "discover-source-login", title = "书源登录（Discover Source Login）", shell = RouteShell.LibraryShell, body = listOf("82% 书源登录 轻小说文库 该书源的发现入口需要登录态，登录后返回当前入口并刷新列表。", "登录状态", "未登录 · 最近检测 10:32", "适用范围", "发现入口、详情页、目录页", "Cookie 保存", "仅保存在本机书源配置中", "打开网页登录", "保存登录信息", "重新检测", "发现页后，当前书源和当前入口保持不变，只刷新内容列表。", "控制层", "完成刷新"), actions = listOf(DemoRouteAction("打开网页登录", "discover-login-return"), DemoRouteAction("保存登录信息", "discover-login-return"), DemoRouteAction("重新检测", "discover-control"), DemoRouteAction("控制层", "discover-control"), DemoRouteAction("完成刷新", "discover-login-return"))),
        DemoRoutePage(id = "discover-rule-test", title = "发现规则测试（Discover Rule Test）", shell = RouteShell.SettingsShell, body = listOf("82% 发现规则测试 完成 优书网 正在编辑：发现规则", "基本", "详情", "目录", "正文", "高级", "@js: 首页入口 + 分类入口", ".result-list li", ".book-title@text", ".author@text", ".tag@text", ".intro@text", ".last@text", "img@src", "a@href", "测试输入", "https://example.com/rank/allvisit_1.html", "<li class=\"book\">长夜余火</li>", "测试入口", "测试结果", "生成 5 个入口", "排行榜、分类、完本、最新、书单", "解析到 18 本书", "首条：长夜余火 · 爱潜水的乌贼"), actions = listOf()),
        DemoRoutePage(id = "discover-source-bulk", title = "发现源批量管理（Discover Source Bulk）", shell = RouteShell.SettingsShell, body = listOf("82% 发现源管理 完成 发现源管理 选择启用发现的书源，批量启用、禁用或刷新入口。", "已选 3 个", "全选", "已启用发现", "有发现未启用", "需登录", "异常", "优书网", "默认分组 · 120ms · 已启用发现", "起点导入", "正版 · 180ms · 已启用发现", "轻小说文库", "需登录 · 发现可用", "本地聚合源", "维护中 · 暂停发现", "失效示例源", "解析失败 · exploreUrl 异常", "启用", "禁用"), actions = listOf()),
        DemoRoutePage(id = "rss-source-feed", title = "RSS 单源条目（RSS Source Feed）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源条目（RSS Source Feed）"), actions = listOf(DemoRouteAction("编辑源", "rss-source-edit"), DemoRouteAction("记录", "rss-read-record"), DemoRouteAction("调试", "rss-source-debug"), DemoRouteAction("源操作", "rss-source-actions"))),
        DemoRoutePage(id = "rss-source-category-releases", title = "RSS 单源分类：Releases（RSS Source Category Releases）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Releases（RSS Source Category Releases）"), actions = listOf(DemoRouteAction("编辑源", "rss-source-edit"), DemoRouteAction("记录", "rss-read-record"), DemoRouteAction("调试", "rss-source-debug"), DemoRouteAction("源操作", "rss-source-actions"))),
        DemoRoutePage(id = "rss-source-category-issues", title = "RSS 单源分类：Issues（RSS Source Category Issues）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Issues（RSS Source Category Issues）"), actions = listOf(DemoRouteAction("编辑源", "rss-source-edit"), DemoRouteAction("记录", "rss-read-record"), DemoRouteAction("调试", "rss-source-debug"), DemoRouteAction("源操作", "rss-source-actions"))),
        DemoRoutePage(id = "rss-source-category-discussions", title = "RSS 单源分类：Discussions（RSS Source Category Discussions）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Discussions（RSS Source Category Discussions）"), actions = listOf(DemoRouteAction("编辑源", "rss-source-edit"), DemoRouteAction("记录", "rss-read-record"), DemoRouteAction("调试", "rss-source-debug"), DemoRouteAction("源操作", "rss-source-actions"))),
        DemoRoutePage(id = "rss-favorite-groups", title = "RSS 收藏分组（RSS Favorite Groups）", shell = RouteShell.LibraryShell, body = listOf("收藏分组", "默认分组 2 条收藏 · 首页显示", "开源项目 1 条收藏 · 自动归类", "社区 1 条收藏 · 手动归类", "新增分组", "排序"), actions = listOf(DemoRouteAction("新增分组", "rss-favorite-group-edit"), DemoRouteAction("排序", "rss-favorite-group-edit"))),
        DemoRoutePage(id = "rss-favorite-group-edit", title = "RSS 收藏分组编辑（RSS Favorite Group Edit）", shell = RouteShell.LibraryShell, body = listOf("82% 编辑收藏分组 收藏分组 分组名称 默认分组", "收藏分组", "首页显示", "开启", "排序方式", "最近收藏优先", "包含条目", "Reader UI 前端输入件更新说明、阅读器路线图讨论摘要"), actions = listOf()),
        DemoRoutePage(id = "rss-favorite-clear", title = "RSS 清空收藏分组（RSS Clear Favorite Group）", shell = RouteShell.LibraryShell, body = listOf("82% 清空收藏分组 清空默认分组收藏？ 仅移除当前收藏分组里的条目，文章本身和订阅源不会删除。", "确认清空"), actions = listOf(DemoRouteAction("确认清空", "rss-starred"))),
        DemoRoutePage(id = "rss-empty", title = "RSS 空状态（RSS Empty）", shell = RouteShell.LibraryShell, body = listOf("82% RSS 空状态 暂无未读订阅 当前订阅源没有新的未读条目。你可以查看全部、管理订阅源或手动刷新。日常空状态仍保留 RSS 主导航上下文。", "查看全部", "订阅管理"), actions = listOf(DemoRouteAction("查看全部", "rss-all"), DemoRouteAction("订阅管理", "rss-subscription-management"))),
        DemoRoutePage(id = "rss-error", title = "RSS 错误状态（RSS Error）", shell = RouteShell.LibraryShell, body = listOf("82% RSS 错误 订阅刷新失败 2 个订阅源刷新失败，已保留最近缓存条目。可以稍后重试、查看错误源，或进入订阅源管理修复登录态和规则。", "书源维护公告", "登录态失效 · 需要重新登录", "本地系统通知", "源已暂停 · 不参与自动刷新", "重试刷新", "订阅管理"), actions = listOf(DemoRouteAction("重试刷新", "rss-refreshing"), DemoRouteAction("订阅管理", "rss-subscription-management"))),
        DemoRoutePage(id = "book-detail", title = "书籍详情（Book Detail）", shell = RouteShell.LibraryShell, body = listOf("书籍详情（Book Detail）"), actions = listOf()),
        DemoRoutePage(id = "book-directory", title = "书籍目录（Book Directory）", shell = RouteShell.LibraryShell, body = listOf("书籍目录", "长夜余火", "爱潜水的乌贼 · 共 7 章", "目录", "书签"), actions = listOf()),
        DemoRoutePage(id = "bookshelf-empty", title = "书架空状态（Bookshelf Empty）", shell = RouteShell.MainTabShell, body = listOf("82% 书架 我的书架 书架还是空的 添加网络书籍或导入本地文件后，会在这里显示继续阅读和书架内容。", "搜索书籍 按书名、作者或关键词查找", "导入本地书 添加本机文件到书架", "去发现", "书架设置", "当前 Tab：书架", "书架更多操作", "批量管理 选择多本书后移动或删除", "分组管理 编辑书架分组与归属", "本地书导入 导入本地文件到书架"), actions = listOf(DemoRouteAction("搜索书籍 按书名、作者或关键词查找", "book-search"), DemoRouteAction("导入本地书 添加本机文件到书架", "local-import"), DemoRouteAction("去发现", "discover"), DemoRouteAction("书架设置", "bookshelf-search-settings"), DemoRouteAction("批量管理 选择多本书后移动或删除", "book-batch-management"), DemoRouteAction("分组管理 编辑书架分组与归属", "group-management"), DemoRouteAction("本地书导入 导入本地文件到书架", "local-import"))),
        DemoRoutePage(id = "sort-filter", title = "书架排序筛选弹层（Bookshelf Sort Filter Popover）", shell = RouteShell.MainTabShell, body = listOf("长夜余火", "爱潜水的乌贼 · 第 32 章 雨夜", "多选", "分支", "书籍详情", "删除", "书架更多操作", "批量管理 选择多本书后移动或删除", "分组管理 编辑书架分组与归属", "本地书导入 导入本地文件到书架"), actions = listOf(DemoRouteAction("阅读", "immersive-reading"), DemoRouteAction("多选", "book-batch-management"), DemoRouteAction("分支", "group-management"), DemoRouteAction("书籍详情", "book-detail"), DemoRouteAction("批量管理 选择多本书后移动或删除", "book-batch-management"), DemoRouteAction("分组管理 编辑书架分组与归属", "group-management"), DemoRouteAction("本地书导入 导入本地文件到书架", "local-import"))),
        DemoRoutePage(id = "reader", title = "阅读控制层（Reader Control Layer）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("自动翻页", "auto-page"), DemoRouteAction("替换", "content-replacement"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "toc-bookmarks", title = "目录与书签（TOC and Bookmarks）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "reader-appearance", title = "阅读外观（Reading Appearance）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "tts", title = "朗读（Read Aloud）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "reader-settings", title = "阅读设置（Reading Settings）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "reader-full-directory", title = "目录大半屏控制窗（Expanded Directory Panel）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("收起", "toc-bookmarks"))),
        DemoRoutePage(id = "reader-full-tts", title = "朗读大半屏控制窗（Expanded TTS Panel）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("收起", "tts"))),
        DemoRoutePage(id = "reader-full-appearance", title = "界面大半屏控制窗（Expanded Appearance Panel）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("收起", "reader-appearance"))),
        DemoRoutePage(id = "reader-full-settings", title = "阅读设置大半屏控制窗（Expanded Reading Settings Panel）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("收起", "reader-settings"))),
        DemoRoutePage(id = "reader-book-cache", title = "书籍缓存（Book Cache）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"))),
        DemoRoutePage(id = "reader-debug-info", title = "调试信息（Debug Info）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"))),
        DemoRoutePage(id = "auto-page", title = "自动翻页（Auto Page）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "content-search", title = "内容搜索（Content Search）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("第 32 章 雨夜 雨夜的风格外冷 · 当前结果 1/2", "immersive-reading"), DemoRouteAction("第 33 章 灯塔 雨夜之后，远处灯塔亮起 · 结果 2/2", "immersive-reading"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "content-replacement", title = "内容替换（Content Replacement）", shell = RouteShell.ReaderShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"))),
        DemoRoutePage(id = "source-switch", title = "换源（Source Switching）", shell = RouteShell.FlowShell, body = listOf("雨夜", "雨声在窗外连成一片，像 无数细小的针 ，密密地刺在玻璃上，汇成一层朦胧的水幕，将城市的灯光晕成模糊的光团。", "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰，像某个 迟到许久的答案 终于抵达。", "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没，只留下 短暂而摇晃的光 。", "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来，塞进抽屉最深处。", "可真正看到信上那行字时，他才发现那些被压下去的情绪并没有消失，只是一直在暗处积蓄，等着这一刻重新涌上来。", "远处的灯光像被雾气揉碎，只剩下一团模糊的暖色。也许有些选择，从一开始就注定要在 某个雨夜 到来。", "楼下传来轻微的脚步声，先是停在单元门外，随后沿着湿滑的台阶一点点靠近，每一步都像敲在他心口。", "他没有立刻开门，而是把信重新摊平，用指腹抹过那几个被水痕晕开的字，确认自己没有看错。", "门铃响起时，窗外的雷声恰好滚过天际。屋内短暂地亮了一瞬，墙上的旧照片也在那道白光里变得清晰。", "照片里的人站在同一场雨中，笑得毫无防备，仿佛后来所有的分离、沉默和追问都还没有发生。", "那张照片的边缘已经泛黄，角落压着一枚旧车票，目的地早已看不清，只剩日期仍然固执地留在纸上。", "窗外的雨还在下，敲打着屋檐，也敲打着这座城市里无数个未眠的人。", "他终于走向门口，手搭上门把时又停了半秒。那半秒里，过去几年的画面像被翻乱的书页，从眼前一页页掠过。", "门外的人没有再按铃，只是安静地等着。隔着一扇门，他听见对方压得很低的呼吸声，也听见自己越来越快的心跳。", "门开后，走廊里的冷风和雨气一起涌进来。来人摘下帽檐，脸上带着疲惫，却仍旧把那句迟到的话完整地说了出来。", "他没有回答，只是侧过身，让出一条狭窄的路。雨夜仍在继续，而故事终于从这一页，翻到了下一页。", "屋里的灯光落在两人之间，照见地面上缓慢扩散的水迹，也照见那些被时间藏起来的犹豫。", "他们在玄关站了很久，谁都没有先坐下。雨声填满沉默，也把那些准备好的解释一点点冲淡。", "来人从口袋里取出另一张折起的纸，纸面被保护得很好，没有水痕，只有折缝处泛着浅浅的白。", "他接过那张纸，指尖碰到对方冰冷的手背，才发现这个人并不像想象中那样笃定，甚至比自己更疲惫。", "纸上没有长篇解释，只有几行地址和一个日期。那是他们最后一次分别后的第三天，也是他以为一切已经结束的那天。", "客厅里的钟慢慢走过半点，声音清晰得近乎刺耳。他抬起头，发现对方一直看着窗外，没有催促，也没有辩解。", "那些年错过的消息、没能抵达的车站、被人转交又遗失的信件，像雨水一样从不同方向汇到脚边。"), actions = listOf(DemoRouteAction("换源", "source-switch"), DemoRouteAction("自动翻页", "auto-page"), DemoRouteAction("替换", "content-replacement"), DemoRouteAction("目录", "toc-bookmarks"), DemoRouteAction("朗读", "tts"), DemoRouteAction("界面", "reader-appearance"), DemoRouteAction("确认换源", "reader"))),
        DemoRoutePage(id = "restore-confirm", title = "恢复确认（Restore Confirm）", shell = RouteShell.SettingsShell, body = listOf("82% 恢复确认 恢复确认 WebDAV · 2026-06-23 08:00 · 完整备份 确认恢复数据 将使用选中的备份覆盖本机同类数据。恢复前会创建本地快照，取消不会改变当前数据。", "WebDAV · 2026-06-23 08:00 · 完整备份", "书架与分组、阅读进度、阅读与 App 设置、书源配置", "128 本书 · 12 个分组 · 96 条阅读进度 等 4 项", "恢复前自动生成本地快照", "选择恢复范围", "只显示当前备份包含的数据类型。至少保留一项，开始恢复前可在这里调整。", "书架与分组 恢复书架书籍、分组和排序", "阅读进度 恢复章节位置和阅读进度", "阅读与 App 设置 恢复主题、排版和通用设置", "书源配置 恢复书源、分组和启用状态", "覆盖提醒", "冲突项会在恢复过程中单独确认，不会静默覆盖。", "开始恢复"), actions = listOf(DemoRouteAction("开始恢复", "restore-progress"))),
        DemoRoutePage(id = "restore-progress", title = "恢复进度（Restore Progress）", shell = RouteShell.SettingsShell, body = listOf("82% 恢复进度 恢复进度 WebDAV · 2026-06-23 08:00 · 完整备份 正在恢复 当前正在合并书架和阅读进度。离开页面不会中断恢复，完成后会进入结果状态。", "下载备份", "12.8 MB · WebDAV", "校验文件", "manifest、hash、版本兼容", "合并数据", "书架 128 本 · 进度 96 条", "写入设置", "等待合并完成", "处理冲突", "查看结果"), actions = listOf(DemoRouteAction("处理冲突", "restore-conflict"), DemoRouteAction("查看结果", "restore-result"))),
        DemoRoutePage(id = "restore-conflict", title = "恢复冲突（Restore Conflict）", shell = RouteShell.SettingsShell, body = listOf("82% 恢复冲突 恢复冲突 WebDAV · 2026-06-23 08:00 · 完整备份 选择冲突处理方式 以下项目本地和备份均有更新。请选择保留本地或使用备份，选择后恢复会继续。", "分组：玄幻连载", "本地 42 本 · 远程 46 本", "保留本地", "使用备份", "阅读进度：长夜余火", "本地第 32 章 · 远程第 35 章", "本地进度", "远程进度", "阅读设置：浅色主题", "本地字号 18 · 远程字号 17", "本机设置", "备份设置", "进度", "应用选择"), actions = listOf(DemoRouteAction("进度", "restore-progress"), DemoRouteAction("应用选择", "restore-result"))),
        DemoRoutePage(id = "restore-result", title = "恢复结果（Restore Result）", shell = RouteShell.SettingsShell, body = listOf("82% 恢复结果 恢复结果 WebDAV · 2026-06-23 08:00 · 完整备份 恢复完成 书架、分组和阅读进度已恢复。1 条书源配置因版本不兼容被跳过，可在日志中查看详情。", "128 本", "12 个", "96 条", "1 条", "书架与分组", "已恢复 128 本书和 12 个分组", "阅读进度", "已恢复 96 条进度记录", "书源配置", "1 条旧版规则字段不兼容", "查看日志", "同步页"), actions = listOf(DemoRouteAction("同步页", "sync-backup"))),
        DemoRoutePage(id = "source-import-options", title = "添加书源（Add Source）", shell = RouteShell.SettingsShell, body = listOf("82% 书源管理 搜索书源名称或域名 12 个书源 · 8 个启用 · 4 个异常 · 10:30 检测", "筛选 全部 · 全部分组", "起点中文网", "qidian.com · 起点导入", "检测", "笔趣阁", "biquge.example · 玄幻书源", "本地导入源", "本地文件导入 · 自定义", "测试书源", "test.example · 测试书源", "轻小说文库", "lightnovel.example · 测试书源", "旧规则源", "old.example · 自定义", "飞卢小说网", "faloo.com · 玄幻书源", "晋江文学城", "jjwx.example · 起点导入", "纵横中文网", "zongheng.com · 玄幻书源", "豆瓣阅读", "read.douban.com · 自定义", "失效示例源"), actions = listOf(DemoRouteAction("检测", "source-detect"), DemoRouteAction("批量管理", "source-batch"), DemoRouteAction("新增书源", "source-import-options"), DemoRouteAction("网络导入 从 URL 拉取书源包", "source-import-preview"), DemoRouteAction("本地导入 选择本地 JSON 或 TXT 文件", "source-import-preview"), DemoRouteAction("剪贴板导入 解析剪贴板中的书源内容", "source-import-preview"), DemoRouteAction("手动新建 进入空白书源编辑页", "source-rule-edit"))),
        DemoRoutePage(id = "source-batch", title = "批量管理（Batch Source Management）", shell = RouteShell.SettingsShell, body = listOf("82% 已选 3 个 取消 已选 3 个 全选 搜索书源名称或域名 12 个书源 · 8 个启用 · 4 个异常 · 10:30 检测", "筛选 全部 · 全部分组", "起点中文网", "qidian.com · 起点导入", "笔趣阁", "biquge.example · 玄幻书源", "本地导入源", "本地文件导入 · 自定义", "测试书源", "test.example · 测试书源", "轻小说文库", "lightnovel.example · 测试书源", "旧规则源", "old.example · 自定义", "飞卢小说网", "faloo.com · 玄幻书源", "晋江文学城", "jjwx.example · 起点导入", "纵横中文网", "zongheng.com · 玄幻书源", "豆瓣阅读", "read.douban.com · 自定义", "失效示例源", "dead.example · 测试书源"), actions = listOf(DemoRouteAction("分组", "source-groups"), DemoRouteAction("删除", "source-delete-confirm"))),
        DemoRoutePage(id = "source-groups", title = "分组管理（Source Groups）", shell = RouteShell.SettingsShell, body = listOf("82% 分组管理 新增 分组用于筛选和批量整理书源，删除分组不会删除书源。", "全部分组", "12 个书源", "玄幻书源", "4 个书源", "当前筛选", "起点导入", "3 个书源", "测试书源", "自定义", "2 个书源", "未分组", "1 个书源", "批量移动", "新增分组"), actions = listOf()),
        DemoRoutePage(id = "source-detail", title = "书源详情（Source Detail）", shell = RouteShell.SettingsShell, body = listOf("82% 书源详情 笔趣阁 biquge.example · 玄幻书源 异常 · 最近检测 10:30 · 规则版本 3", "站点", "详情", "目录", "正文", "登录", "最近检测结果", "搜索、详情、目录均可解析；正文模块失败。", "失败规则：正文内容规则“#content@text”返回空内容。建议进入规则编辑后调测正文模块。", "请求方式", "并发限制", "Cookie", "更新时间", "复制书源", "导出书源", "检测此源", "编辑规则", "删除"), actions = listOf(DemoRouteAction("检测此源", "source-detect"), DemoRouteAction("编辑规则", "source-rule-edit"), DemoRouteAction("删除", "source-delete-confirm"))),
        DemoRoutePage(id = "source-detect", title = "书源检测（Source Detection）", shell = RouteShell.SettingsShell, body = listOf("下一步应进入正文模块调测，比较原始 HTML 与当前正文规则。", "重新检测", "编辑正文规则"), actions = listOf(DemoRouteAction("源码", "source-code-view"), DemoRouteAction("调测", "source-debug-search-result"), DemoRouteAction("调测", "source-debug-detail-result"), DemoRouteAction("调测", "source-debug-catalog-result"), DemoRouteAction("调测", "source-debug"), DemoRouteAction("编辑正文规则", "source-rule-edit"))),
        DemoRoutePage(id = "source-rule-edit", title = "规则编辑（Source Rule Edit）", shell = RouteShell.SettingsShell, body = listOf("规则修改后先调测当前模块，确认解析结果正常后再保存。", "保存规则", "调测当前模块"), actions = listOf(DemoRouteAction("笔趣阁", "source-debug"), DemoRouteAction("https://biquge.example", "source-debug"), DemoRouteAction("玄幻书源", "source-debug"), DemoRouteAction("已启用", "source-debug"), DemoRouteAction("GET", "source-debug"), DemoRouteAction("UTF-8", "source-debug"), DemoRouteAction("User-Agent / Referer", "source-debug"), DemoRouteAction("未启用", "source-debug"))),
        DemoRoutePage(id = "source-debug", title = "书源调测（Source Debug）", shell = RouteShell.SettingsShell, body = listOf("可尝试将正文内容规则改为“.chapter-content@text”后重新调测。", "重新调测", "回到编辑"), actions = listOf(DemoRouteAction("搜索 关键词 -> 结果列表", "source-debug-search-result"), DemoRouteAction("详情 详情 URL -> 书籍字段", "source-debug-detail-result"), DemoRouteAction("目录 目录 URL -> 章节列表", "source-debug-catalog-result"), DemoRouteAction("正文 章节 URL -> 正文文本", "source-debug"), DemoRouteAction("解析结果", "source-debug"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到编辑", "source-rule-edit"))),
        DemoRoutePage(id = "source-debug-search-result", title = "搜索调测结果（Source Debug Search Result）", shell = RouteShell.SettingsShell, body = listOf("重新调测", "回到编辑"), actions = listOf(DemoRouteAction("搜索 关键词 -> 结果列表", "source-debug-search-result"), DemoRouteAction("详情 详情 URL -> 书籍字段", "source-debug-detail-result"), DemoRouteAction("目录 目录 URL -> 章节列表", "source-debug-catalog-result"), DemoRouteAction("正文 章节 URL -> 正文文本", "source-debug"), DemoRouteAction("解析结果", "source-debug-search-result"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到编辑", "source-rule-edit"))),
        DemoRoutePage(id = "source-debug-detail-result", title = "详情调测结果（Source Debug Detail Result）", shell = RouteShell.SettingsShell, body = listOf("重新调测", "回到编辑"), actions = listOf(DemoRouteAction("搜索 关键词 -> 结果列表", "source-debug-search-result"), DemoRouteAction("详情 详情 URL -> 书籍字段", "source-debug-detail-result"), DemoRouteAction("目录 目录 URL -> 章节列表", "source-debug-catalog-result"), DemoRouteAction("正文 章节 URL -> 正文文本", "source-debug"), DemoRouteAction("解析结果", "source-debug-detail-result"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到编辑", "source-rule-edit"))),
        DemoRoutePage(id = "source-debug-catalog-result", title = "目录调测结果（Source Debug Catalog Result）", shell = RouteShell.SettingsShell, body = listOf("重新调测", "回到编辑"), actions = listOf(DemoRouteAction("搜索 关键词 -> 结果列表", "source-debug-search-result"), DemoRouteAction("详情 详情 URL -> 书籍字段", "source-debug-detail-result"), DemoRouteAction("目录 目录 URL -> 章节列表", "source-debug-catalog-result"), DemoRouteAction("正文 章节 URL -> 正文文本", "source-debug"), DemoRouteAction("解析结果", "source-debug-catalog-result"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到编辑", "source-rule-edit"))),
        DemoRoutePage(id = "source-debug-content-log", title = "正文调测日志（Source Debug Content Log）", shell = RouteShell.SettingsShell, body = listOf("可复制日志后回到规则编辑，将正文规则改为“.chapter-content@text”。", "复制日志", "回到解析", "回到编辑"), actions = listOf(DemoRouteAction("搜索 关键词 -> 结果列表", "source-debug-search-result"), DemoRouteAction("详情 详情 URL -> 书籍字段", "source-debug-detail-result"), DemoRouteAction("目录 目录 URL -> 章节列表", "source-debug-catalog-result"), DemoRouteAction("正文 章节 URL -> 正文文本", "source-debug"), DemoRouteAction("解析结果", "source-debug"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到解析", "source-debug"))),
        DemoRoutePage(id = "source-edit-debug", title = "规则编辑（Source Rule Edit）", shell = RouteShell.SettingsShell, body = listOf("规则修改后先调测当前模块，确认解析结果正常后再保存。", "保存规则", "调测当前模块"), actions = listOf(DemoRouteAction("笔趣阁", "source-debug"), DemoRouteAction("https://biquge.example", "source-debug"), DemoRouteAction("玄幻书源", "source-debug"), DemoRouteAction("已启用", "source-debug"), DemoRouteAction("GET", "source-debug"), DemoRouteAction("UTF-8", "source-debug"), DemoRouteAction("User-Agent / Referer", "source-debug"), DemoRouteAction("未启用", "source-debug"))),
        DemoRoutePage(id = "source-logs", title = "错误日志（Source Error Logs）", shell = RouteShell.SettingsShell, body = listOf("82% 错误日志 清空 全部 异常 警告 今日 搜索书源或错误内容 4 条异常 · 1 条警告", "笔趣阁 · ERROR", "10:30 · 正文 · 正文规则返回空内容", "旧规则源 · ERROR", "10:22 · 搜索 · HTTP 403", "本地导入源 · WARN", "09:50 · 目录 · 尚未检测", "失效示例源 · ERROR", "昨天 · 详情 · 详情页 URL 为空", "复制全部", "重新检测异常"), actions = listOf(DemoRouteAction("清空", "source-delete-confirm"))),
        DemoRoutePage(id = "source-code-view", title = "源码查看（Source Code View）", shell = RouteShell.SettingsShell, body = listOf("书源调测", "复制", "源码查看", "正文模块 · 当前请求返回", "/book/123/128.html", "#content@text", "解析结果", "源码", "日志", "重新请求", "回到调测"), actions = listOf(DemoRouteAction("解析结果", "source-debug"), DemoRouteAction("源码", "source-code-view"), DemoRouteAction("日志", "source-debug-content-log"), DemoRouteAction("回到调测", "source-debug"))),
        DemoRoutePage(id = "source-delete-confirm", title = "删除书源（Delete Sources）", shell = RouteShell.SettingsShell, body = listOf("82% 已选 3 个 取消 已选 3 个 全选 搜索书源名称或域名 12 个书源 · 8 个启用 · 4 个异常 · 10:30 检测", "筛选 全部 · 全部分组", "起点中文网", "qidian.com · 起点导入", "笔趣阁", "biquge.example · 玄幻书源", "本地导入源", "本地文件导入 · 自定义", "测试书源", "test.example · 测试书源", "轻小说文库", "lightnovel.example · 测试书源", "旧规则源", "old.example · 自定义", "飞卢小说网", "faloo.com · 玄幻书源", "晋江文学城", "jjwx.example · 起点导入", "纵横中文网", "zongheng.com · 玄幻书源", "豆瓣阅读", "read.douban.com · 自定义", "失效示例源", "dead.example · 测试书源"), actions = listOf(DemoRouteAction("删除", "source-management"))),
        DemoRoutePage(id = "bookshelf", title = "书架（Bookshelf）", shell = RouteShell.MainTabShell, body = listOf("书架（Bookshelf）")),
        DemoRoutePage(id = "discover", title = "发现（Discover）", shell = RouteShell.MainTabShell, body = listOf("发现（Discover）")),
        DemoRoutePage(id = "rss", title = "RSS", shell = RouteShell.MainTabShell, body = listOf("RSS")),
        DemoRoutePage(id = "rss-all", title = "RSS 全部条目（RSS All Items）", shell = RouteShell.LibraryShell, body = listOf("RSS 全部条目（RSS All Items）")),
        DemoRoutePage(id = "rss-starred", title = "RSS 收藏（RSS Starred）", shell = RouteShell.LibraryShell, body = listOf("RSS 收藏（RSS Starred）")),
        DemoRoutePage(id = "rss-refreshing", title = "RSS 刷新中（RSS Refreshing）", shell = RouteShell.LibraryShell, body = listOf("RSS 刷新中（RSS Refreshing）")),
        DemoRoutePage(id = "rss-search", title = "RSS 搜索（RSS Search）", shell = RouteShell.LibraryShell, body = listOf("RSS 搜索（RSS Search）")),
        DemoRoutePage(id = "rss-detail", title = "RSS 阅读（RSS Reader）", shell = RouteShell.LibraryShell, body = listOf("RSS 阅读（RSS Reader）")),
        DemoRoutePage(id = "rss-original", title = "RSS 原文页面（RSS Original Page）", shell = RouteShell.LibraryShell, body = listOf("RSS 原文页面（RSS Original Page）")),
        DemoRoutePage(id = "rss-original-browser", title = "RSS 系统浏览器打开（RSS Open In Browser）", shell = RouteShell.LibraryShell, body = listOf("RSS 系统浏览器打开（RSS Open In Browser）")),
        DemoRoutePage(id = "rss-subscription-management", title = "RSS 订阅管理（RSS Subscription Management）", shell = RouteShell.LibraryShell, body = listOf("RSS 订阅管理（RSS Subscription Management）")),
        DemoRoutePage(id = "rss-source-actions", title = "RSS 源操作（RSS Source Actions）", shell = RouteShell.LibraryShell, body = listOf("RSS 源操作（RSS Source Actions）")),
        DemoRoutePage(id = "rss-source-edit", title = "RSS 源编辑（RSS Source Edit）", shell = RouteShell.LibraryShell, body = listOf("RSS 源编辑（RSS Source Edit）")),
        DemoRoutePage(id = "rss-source-debug", title = "RSS 规则调试（RSS Source Debug）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则调试（RSS Source Debug）")),
        DemoRoutePage(id = "rss-source-vars", title = "RSS 源变量（RSS Source Variables）", shell = RouteShell.LibraryShell, body = listOf("RSS 源变量（RSS Source Variables）")),
        DemoRoutePage(id = "rss-source-login", title = "RSS 源登录（RSS Source Login）", shell = RouteShell.LibraryShell, body = listOf("RSS 源登录（RSS Source Login）")),
        DemoRoutePage(id = "rss-source-login-web", title = "RSS 网页登录（RSS Source Web Login）", shell = RouteShell.LibraryShell, body = listOf("RSS 网页登录（RSS Source Web Login）")),
        DemoRoutePage(id = "rss-source-login-cookie", title = "RSS Cookie 提取（RSS Source Login Cookie）", shell = RouteShell.LibraryShell, body = listOf("RSS Cookie 提取（RSS Source Login Cookie）")),
        DemoRoutePage(id = "rss-source-login-clear", title = "RSS 清除登录（RSS Source Login Clear）", shell = RouteShell.LibraryShell, body = listOf("RSS 清除登录（RSS Source Login Clear）")),
        DemoRoutePage(id = "rss-source-groups", title = "RSS 分组管理（RSS Source Groups）", shell = RouteShell.LibraryShell, body = listOf("RSS 分组管理（RSS Source Groups）")),
        DemoRoutePage(id = "rss-source-group-edit", title = "RSS 分组编辑（RSS Source Group Edit）", shell = RouteShell.LibraryShell, body = listOf("RSS 分组编辑（RSS Source Group Edit）")),
        DemoRoutePage(id = "rss-source-batch", title = "RSS 批量管理（RSS Source Batch）", shell = RouteShell.LibraryShell, body = listOf("RSS 批量管理（RSS Source Batch）")),
        DemoRoutePage(id = "rss-source-export", title = "RSS 导出订阅源（RSS Source Export）", shell = RouteShell.LibraryShell, body = listOf("RSS 导出订阅源（RSS Source Export）")),
        DemoRoutePage(id = "rss-source-export-detail", title = "RSS 导出预览（RSS Source Export Detail）", shell = RouteShell.LibraryShell, body = listOf("RSS 导出预览（RSS Source Export Detail）")),
        DemoRoutePage(id = "rss-source-export-result", title = "RSS 导出完成（RSS Source Export Result）", shell = RouteShell.LibraryShell, body = listOf("RSS 导出完成（RSS Source Export Result）")),
        DemoRoutePage(id = "rss-source-pin", title = "RSS 置顶确认（RSS Source Pin）", shell = RouteShell.LibraryShell, body = listOf("RSS 置顶确认（RSS Source Pin）")),
        DemoRoutePage(id = "rss-source-disable", title = "RSS 禁用确认（RSS Source Disable）", shell = RouteShell.LibraryShell, body = listOf("RSS 禁用确认（RSS Source Disable）")),
        DemoRoutePage(id = "rss-source-batch-disable", title = "RSS 批量禁用确认（RSS Source Batch Disable）", shell = RouteShell.LibraryShell, body = listOf("RSS 批量禁用确认（RSS Source Batch Disable）")),
        DemoRoutePage(id = "rss-source-import", title = "RSS 源导入（RSS Source Import）", shell = RouteShell.LibraryShell, body = listOf("RSS 源导入（RSS Source Import）")),
        DemoRoutePage(id = "rss-source-import-detail", title = "RSS 源导入详情（RSS Source Import Detail）", shell = RouteShell.LibraryShell, body = listOf("RSS 源导入详情（RSS Source Import Detail）")),
        DemoRoutePage(id = "rss-source-import-result", title = "RSS 源导入完成（RSS Source Import Result）", shell = RouteShell.LibraryShell, body = listOf("RSS 源导入完成（RSS Source Import Result）")),
        DemoRoutePage(id = "rss-read-record", title = "RSS 阅读记录（RSS Read Record）", shell = RouteShell.LibraryShell, body = listOf("RSS 阅读记录（RSS Read Record）")),
        DemoRoutePage(id = "rss-record-clear", title = "RSS 清空阅读记录（RSS Clear Read Record）", shell = RouteShell.LibraryShell, body = listOf("RSS 清空阅读记录（RSS Clear Read Record）")),
        DemoRoutePage(id = "rss-rule-subscription", title = "RSS 规则订阅（RSS Rule Subscription）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则订阅（RSS Rule Subscription）")),
        DemoRoutePage(id = "rss-rule-subscription-detail", title = "RSS 规则订阅详情（RSS Rule Subscription Detail）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则订阅详情（RSS Rule Subscription Detail）")),
        DemoRoutePage(id = "rss-rule-subscription-edit", title = "RSS 规则订阅编辑（RSS Rule Subscription Edit）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则订阅编辑（RSS Rule Subscription Edit）")),
        DemoRoutePage(id = "rss-rule-subscription-test", title = "RSS 规则订阅测试（RSS Rule Subscription Test）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则订阅测试（RSS Rule Subscription Test）")),
        DemoRoutePage(id = "rss-rule-subscription-apply", title = "RSS 应用订阅更新（RSS Rule Subscription Apply）", shell = RouteShell.LibraryShell, body = listOf("RSS 应用订阅更新（RSS Rule Subscription Apply）")),
        DemoRoutePage(id = "settings", title = "设置首页（Settings Home）", shell = RouteShell.MainTabShell, body = listOf("设置首页（Settings Home）")),
        DemoRoutePage(id = "book-search", title = "书籍搜索（Book Search）", shell = RouteShell.LibraryShell, body = listOf("书籍搜索（Book Search）")),
        DemoRoutePage(id = "book-batch-management", title = "书籍批量管理（Book Batch Management）", shell = RouteShell.LibraryShell, body = listOf("书籍批量管理（Book Batch Management）")),
        DemoRoutePage(id = "group-management", title = "分组管理（Group Management）", shell = RouteShell.LibraryShell, body = listOf("分组管理（Group Management）")),
        DemoRoutePage(id = "local-import", title = "本地书导入（Local Import）", shell = RouteShell.LibraryShell, body = listOf("本地书导入（Local Import）")),
        DemoRoutePage(id = "immersive-reading", title = "沉浸阅读（Immersive Reading）", shell = RouteShell.ReaderShell, body = listOf("沉浸阅读（Immersive Reading）")),
        DemoRoutePage(id = "settings-general", title = "通用设置（General Settings）", shell = RouteShell.SettingsShell, body = listOf("通用设置（General Settings）")),
        DemoRoutePage(id = "settings-developer", title = "开发模式（Developer Mode）", shell = RouteShell.SettingsShell, body = listOf("开发模式（Developer Mode）")),
        DemoRoutePage(id = "bookshelf-search-settings", title = "书架与搜索设置（Bookshelf and Search Settings）", shell = RouteShell.SettingsShell, body = listOf("书架与搜索设置（Bookshelf and Search Settings）")),
        DemoRoutePage(id = "about-feedback", title = "关于与反馈（About and Feedback）", shell = RouteShell.SettingsShell, body = listOf("关于与反馈（About and Feedback）")),
        DemoRoutePage(id = "sync-backup", title = "同步与备份（Sync and Backup）", shell = RouteShell.SettingsShell, body = listOf("同步与备份（Sync and Backup）")),
        DemoRoutePage(id = "webdav-config", title = "WebDAV 配置（WebDAV Config）", shell = RouteShell.SettingsShell, body = listOf("WebDAV 配置（WebDAV Config）")),
        DemoRoutePage(id = "source-management", title = "书源管理（Source Management）", shell = RouteShell.SettingsShell, body = listOf("书源管理（Source Management）")),
        DemoRoutePage(id = "source-import-preview", title = "导入书源（Import Sources）", shell = RouteShell.SettingsShell, body = listOf("导入书源（Import Sources）")),
        DemoRoutePage(id = "bookshelf-cover-mode", title = "书架封面模式（Bookshelf Cover Mode）", shell = RouteShell.MainTabShell, body = listOf("书架封面模式（Bookshelf Cover Mode）")),
        DemoRoutePage(id = "bookshelf-list-mode", title = "书架列表模式（Bookshelf List Mode）", shell = RouteShell.MainTabShell, body = listOf("书架列表模式（Bookshelf List Mode）")),
        DemoRoutePage(id = "bookshelf-book-more-menu", title = "书籍更多操作（Bookshelf Book More Menu）", shell = RouteShell.MainTabShell, body = listOf("书籍更多操作（Bookshelf Book More Menu）")),
        DemoRoutePage(id = "bookshelf-group-management", title = "书架分组管理（Bookshelf Group Management）", shell = RouteShell.LibraryShell, body = listOf("书架分组管理（Bookshelf Group Management）")),
        DemoRoutePage(id = "search-home", title = "搜索首页（Search Home）", shell = RouteShell.LibraryShell, body = listOf("搜索首页（Search Home）")),
        DemoRoutePage(id = "search-results", title = "搜索结果（Search Results）", shell = RouteShell.LibraryShell, body = listOf("搜索结果（Search Results）")),
        DemoRoutePage(id = "search-loading", title = "搜索加载中（Search Loading）", shell = RouteShell.LibraryShell, body = listOf("搜索加载中（Search Loading）")),
        DemoRoutePage(id = "search-empty", title = "搜索空状态（Search Empty）", shell = RouteShell.LibraryShell, body = listOf("搜索空状态（Search Empty）")),
        DemoRoutePage(id = "search-error", title = "搜索错误状态（Search Error）", shell = RouteShell.LibraryShell, body = listOf("搜索错误状态（Search Error）")),
        DemoRoutePage(id = "book-detail-toc-preview", title = "书籍详情目录预览（Book Detail TOC Preview）", shell = RouteShell.LibraryShell, body = listOf("书籍详情目录预览（Book Detail TOC Preview）")),
        DemoRoutePage(id = "reader_content", title = "阅读正文（Reader Content）", shell = RouteShell.ReaderShell, body = listOf("阅读正文（Reader Content）")),
        DemoRoutePage(id = "reader-appearance-overlay-v2", title = "阅读外观覆盖层 V2（Reader Appearance Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("阅读外观覆盖层 V2（Reader Appearance Overlay V2）")),
        DemoRoutePage(id = "reader-directory-overlay-v2", title = "目录覆盖层 V2（Reader Directory Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("目录覆盖层 V2（Reader Directory Overlay V2）")),
        DemoRoutePage(id = "reader-tts-overlay-v2", title = "朗读覆盖层 V2（Reader TTS Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("朗读覆盖层 V2（Reader TTS Overlay V2）")),
        DemoRoutePage(id = "reader-settings-overlay-v2", title = "阅读设置覆盖层 V2（Reader Settings Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("阅读设置覆盖层 V2（Reader Settings Overlay V2）")),
        DemoRoutePage(id = "reader-full-font", title = "字体完整设置（Reader Full Font）", shell = RouteShell.ReaderShell, body = listOf("字体完整设置（Reader Full Font）")),
        DemoRoutePage(id = "reader-full-theme", title = "主题完整设置（Reader Full Theme）", shell = RouteShell.ReaderShell, body = listOf("主题完整设置（Reader Full Theme）")),
        DemoRoutePage(id = "reader-full-theme-edit", title = "自定义主题编辑（Reader Theme Edit）", shell = RouteShell.ReaderShell, body = listOf("自定义主题编辑（Reader Theme Edit）")),
        DemoRoutePage(id = "reader-full-layout", title = "版式完整设置（Reader Full Layout）", shell = RouteShell.ReaderShell, body = listOf("版式完整设置（Reader Full Layout）")),
        DemoRoutePage(id = "reader-full-page-turn", title = "翻页完整设置（Reader Page Turn）", shell = RouteShell.ReaderShell, body = listOf("翻页完整设置（Reader Page Turn）")),
        DemoRoutePage(id = "reader-auto-scroll-overlay-v2", title = "自动翻页覆盖层 V2（Reader Auto Scroll Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("自动翻页覆盖层 V2（Reader Auto Scroll Overlay V2）")),
        DemoRoutePage(id = "reader-search-overlay-v2", title = "内容搜索覆盖层 V2（Reader Search Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("内容搜索覆盖层 V2（Reader Search Overlay V2）")),
        DemoRoutePage(id = "reader-replace-overlay-v2", title = "内容替换覆盖层 V2（Reader Replace Overlay V2）", shell = RouteShell.ReaderShell, body = listOf("内容替换覆盖层 V2（Reader Replace Overlay V2）")),
        DemoRoutePage(id = "source-switch-results", title = "换源结果（Source Switch Results）", shell = RouteShell.FlowShell, body = listOf("换源结果（Source Switch Results）")),
        DemoRoutePage(id = "reader-night-state-v2", title = "阅读夜间状态 V2（Reader Night State V2）", shell = RouteShell.ReaderShell, body = listOf("阅读夜间状态 V2（Reader Night State V2）")),
        DemoRoutePage(id = "discover-home", title = "发现首页（Discover Home）", shell = RouteShell.MainTabShell, body = listOf("发现首页（Discover Home）")),
        DemoRoutePage(id = "discover-entry-source", title = "发现入口：书源（Discover Entry Source）", shell = RouteShell.MainTabShell, body = listOf("发现入口：书源（Discover Entry Source）")),
        DemoRoutePage(id = "discover-filter-source-type", title = "发现筛选：源类型（Discover Filter Source Type）", shell = RouteShell.MainTabShell, body = listOf("发现筛选：源类型（Discover Filter Source Type）")),
        DemoRoutePage(id = "discover-filter-category", title = "发现筛选：分类（Discover Filter Category）", shell = RouteShell.MainTabShell, body = listOf("发现筛选：分类（Discover Filter Category）")),
        DemoRoutePage(id = "discover-cache-empty", title = "发现缓存空状态（Discover Cache Empty）", shell = RouteShell.MainTabShell, body = listOf("发现缓存空状态（Discover Cache Empty）")),
        DemoRoutePage(id = "discover-cache-stale", title = "发现缓存过期（Discover Cache Stale）", shell = RouteShell.MainTabShell, body = listOf("发现缓存过期（Discover Cache Stale）")),
        DemoRoutePage(id = "discover-cache-fresh", title = "发现缓存新鲜（Discover Cache Fresh）", shell = RouteShell.MainTabShell, body = listOf("发现缓存新鲜（Discover Cache Fresh）")),
        DemoRoutePage(id = "rss-source-category-novel", title = "RSS 单源分类：Novel（RSS Source Category Novel）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Novel（RSS Source Category Novel）")),
        DemoRoutePage(id = "rss-source-category-tech", title = "RSS 单源分类：Tech（RSS Source Category Tech）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Tech（RSS Source Category Tech）")),
        DemoRoutePage(id = "rss-source-category-booklist", title = "RSS 单源分类：Booklist（RSS Source Category Booklist）", shell = RouteShell.LibraryShell, body = listOf("RSS 单源分类：Booklist（RSS Source Category Booklist）")),
        DemoRoutePage(id = "rss-source-add", title = "RSS 源新增（RSS Source Add）", shell = RouteShell.LibraryShell, body = listOf("RSS 源新增（RSS Source Add）")),
        DemoRoutePage(id = "rss-source-delete-confirm", title = "RSS 源删除确认（RSS Source Delete Confirm）", shell = RouteShell.LibraryShell, body = listOf("RSS 源删除确认（RSS Source Delete Confirm）")),
        DemoRoutePage(id = "rss-rule-subscription-create", title = "RSS 规则订阅创建（RSS Rule Subscription Create）", shell = RouteShell.LibraryShell, body = listOf("RSS 规则订阅创建（RSS Rule Subscription Create）")),
        DemoRoutePage(id = "rss-favorite-add", title = "RSS 添加收藏（RSS Favorite Add）", shell = RouteShell.LibraryShell, body = listOf("RSS 添加收藏（RSS Favorite Add）")),
        DemoRoutePage(id = "rss-favorite-remove", title = "RSS 移除收藏（RSS Favorite Remove）", shell = RouteShell.LibraryShell, body = listOf("RSS 移除收藏（RSS Favorite Remove）")),
        DemoRoutePage(id = "global-settings", title = "全局设置（Global Settings）", shell = RouteShell.SettingsShell, body = listOf("全局设置（Global Settings）")),
        DemoRoutePage(id = "restore-scopes", title = "恢复范围（Restore Scopes）", shell = RouteShell.SettingsShell, body = listOf("恢复范围（Restore Scopes）")),
        DemoRoutePage(id = "restore-preview", title = "恢复预览（Restore Preview）", shell = RouteShell.SettingsShell, body = listOf("恢复预览（Restore Preview）")),
        DemoRoutePage(id = "restore-running", title = "恢复运行中（Restore Running）", shell = RouteShell.SettingsShell, body = listOf("恢复运行中（Restore Running）")),
        DemoRoutePage(id = "source-edit", title = "书源编辑（Source Edit）", shell = RouteShell.SettingsShell, body = listOf("书源编辑（Source Edit）")),
        DemoRoutePage(id = "source-add", title = "新增书源（Source Add）", shell = RouteShell.SettingsShell, body = listOf("新增书源（Source Add）")),
        DemoRoutePage(id = "source-debug-running", title = "书源调测运行中（Source Debug Running）", shell = RouteShell.SettingsShell, body = listOf("书源调测运行中（Source Debug Running）")),
        DemoRoutePage(id = "source-debug-result", title = "书源调测结果（Source Debug Result）", shell = RouteShell.SettingsShell, body = listOf("书源调测结果（Source Debug Result）")),
        DemoRoutePage(id = "source-test-result", title = "书源测试结果（Source Test Result）", shell = RouteShell.SettingsShell, body = listOf("书源测试结果（Source Test Result）")),
        DemoRoutePage(id = "source-settings-entry", title = "书源设置入口（Source Settings Entry）", shell = RouteShell.SettingsShell, body = listOf("书源设置入口（Source Settings Entry）")),
        DemoRoutePage(id = "sync-settings-entry", title = "同步设置入口（Sync Settings Entry）", shell = RouteShell.SettingsShell, body = listOf("同步设置入口（Sync Settings Entry）")),
        DemoRoutePage(id = "reading-settings-entry", title = "阅读设置入口（Reading Settings Entry）", shell = RouteShell.SettingsShell, body = listOf("阅读设置入口（Reading Settings Entry）")),
        DemoRoutePage(id = "app-shell", title = "应用壳（App Shell）", shell = RouteShell.MainTabShell, body = listOf("应用壳（App Shell）")),
        DemoRoutePage(id = "main-tabs", title = "主标签（Main Tabs）", shell = RouteShell.MainTabShell, body = listOf("主标签（Main Tabs）")),
        DemoRoutePage(id = "global-loading", title = "全局加载中（Global Loading）", shell = RouteShell.SettingsShell, body = listOf("全局加载中（Global Loading）")),
        DemoRoutePage(id = "global-empty", title = "全局空状态（Global Empty）", shell = RouteShell.SettingsShell, body = listOf("全局空状态（Global Empty）")),
        DemoRoutePage(id = "global-error", title = "全局错误状态（Global Error）", shell = RouteShell.SettingsShell, body = listOf("全局错误状态（Global Error）")),
        DemoRoutePage(id = "offline-state", title = "离线状态（Offline State）", shell = RouteShell.SettingsShell, body = listOf("离线状态（Offline State）")),
        DemoRoutePage(id = "permission-required", title = "权限需要（Permission Required）", shell = RouteShell.SettingsShell, body = listOf("权限需要（Permission Required）")),
        DemoRoutePage(id = "progress-sync-status", title = "进度同步状态（Progress Sync Status）", shell = RouteShell.SettingsShell, body = listOf("进度同步状态（Progress Sync Status）")),
        DemoRoutePage(id = "sync-error", title = "同步错误（Sync Error）", shell = RouteShell.SettingsShell, body = listOf("同步错误（Sync Error）")),
        DemoRoutePage(id = "backup-settings", title = "备份设置（Backup Settings）", shell = RouteShell.SettingsShell, body = listOf("备份设置（Backup Settings）")),
        DemoRoutePage(id = "progress-sync", title = "进度同步（Progress Sync）", shell = RouteShell.SettingsShell, body = listOf("进度同步（Progress Sync）")),
        DemoRoutePage(id = "remote-webdav-books", title = "远程 WebDAV 书籍（Remote WebDAV Books）", shell = RouteShell.SettingsShell, body = listOf("远程 WebDAV 书籍（Remote WebDAV Books）")),
        DemoRoutePage(id = "about", title = "关于（About）", shell = RouteShell.SettingsShell, body = listOf("关于（About）")),
        DemoRoutePage(id = "about-version", title = "关于版本（About Version）", shell = RouteShell.SettingsShell, body = listOf("关于版本（About Version）")),
        DemoRoutePage(id = "state-error", title = "状态错误（State Error）", shell = RouteShell.SettingsShell, body = listOf("状态错误（State Error）")),
        DemoRoutePage(id = "state-offline", title = "状态离线（State Offline）", shell = RouteShell.SettingsShell, body = listOf("状态离线（State Offline）")),
        DemoRoutePage(id = "control-layer-base-v2", title = "阅读控制层基线 V2（Control Layer Base V2）", shell = RouteShell.ReaderShell, body = listOf("阅读控制层基线 V2（Control Layer Base V2）")),
    )

    /**
     * The 2.5 contract additions are explicit native rendering plans, not generic placeholders.
     * Each entry records the generated route wire id, canonical shell/state copy, navigation
     * targets and the Android renderer family that consumes it.
     */
    private val contract25Additions: List<DemoRouteAddition> = listOf(
        addition(
            id = "reader-font-import-confirm",
            title = "字体导入确认（Reader Font Import Confirm）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("确认导入所选字体；应用后阅读排版会重新计算。"),
            actions = listOf(action("取消", "reader-full-font"), action("确认导入", "reader-full-font"))
        ),
        addition(
            id = "reader-font-delete-confirm",
            title = "字体删除确认（Reader Font Delete Confirm）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("删除字体后不可恢复；正在使用该字体的主题会回退到默认字体。"),
            actions = listOf(action("取消", "reader-full-font"), action("确认删除", "reader-full-font"))
        ),
        addition(
            id = "reader-font-fallback",
            title = "字体失效回退（Reader Font Fallback）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("当前字体文件不可用，已安全回退到系统字体并保留排版参数。"),
            actions = listOf(action("返回字体管理", "reader-full-font"))
        ),
        addition(
            id = "reader-theme-new",
            title = "新建主题（Reader Theme New）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("从当前阅读背景、文字颜色和亮度参数创建自定义主题。"),
            actions = listOf(action("返回", "reader-full-theme"), action("从编辑器创建", "reader-full-theme-edit"))
        ),
        addition(
            id = "reader-theme-delete-confirm",
            title = "主题删除确认（Reader Theme Delete Confirm）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("确认删除自定义主题；当前阅读会切换到内置主题。"),
            actions = listOf(action("取消", "reader-full-theme"), action("确认删除", "reader-full-theme"))
        ),
        addition(
            id = "reader-typography-reset-confirm",
            title = "排版恢复默认确认（Reader Typography Reset Confirm）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderWorkspaceState,
            body = listOf("字号、行距、段距和页边距将恢复默认值。"),
            actions = listOf(action("取消", "reader-full-layout"), action("恢复默认", "reader-full-layout"))
        ),
        addition(
            id = "reader-replace-delete-confirm",
            title = "删除替换规则确认（Replace Delete Confirm）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderReplacementState,
            body = listOf("删除后不可恢复；其他替换规则和原始正文不会被修改。"),
            actions = listOf(action("取消", "content-replacement"), action("确认删除", "content-replacement"))
        ),
        addition(
            id = "reader-replace-apply-result",
            title = "替换规则应用结果（Replace Apply Result）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderReplacementState,
            body = listOf("已按规则顺序应用到当前正文，可返回管理或继续阅读。"),
            actions = listOf(action("返回规则管理", "content-replacement"), action("继续阅读", "immersive-reading"))
        ),
        addition(
            id = "reader-replace-import-export",
            title = "替换规则导入导出（Replace Import Export）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderReplacementState,
            body = listOf("以 JSON 预览、导入或导出替换规则，并在提交前校验规则格式。"),
            actions = listOf(action("返回规则管理", "content-replacement"))
        ),
        addition(
            id = "reader-replace-page",
            title = "内容替换规则管理（Reader Replace Page）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderReplacementState,
            body = listOf("管理规则顺序、启用状态、作用范围，并预览原文与替换后正文。"),
            actions = listOf(action("详细预览", "reader-replace-preview"), action("返回阅读", "immersive-reading"))
        ),
        addition(
            id = "reader-replace-preview",
            title = "替换规则预览（Replace Preview）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderReplacementState,
            body = listOf("并排对比原文与替换后正文，展示本次应用的规则列表。"),
            actions = listOf(action("返回规则管理", "content-replacement"), action("继续阅读", "immersive-reading"))
        ),
        addition(
            id = "source-switch-empty",
            title = "换源空结果（Source Switch Empty）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("没有找到可用候选书源；可重新加载或返回阅读。"),
            actions = listOf(action("重新加载", "source-switch"), action("返回阅读", "reader"))
        ),
        addition(
            id = "source-switch-error",
            title = "换源加载失败（Source Switch Error）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("候选书源加载失败，请检查网络或书源状态。"),
            actions = listOf(action("重试加载", "source-switch"), action("返回阅读", "reader"))
        ),
        addition(
            id = "source-switch-timeout",
            title = "换源超时（Source Switch Timeout）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("候选书源请求超时，可能是网络延迟或来源响应过慢。"),
            actions = listOf(action("重试加载", "source-switch"), action("返回阅读", "reader"))
        ),
        addition(
            id = "source-switch-loading",
            title = "换源切换中（Source Switch Loading）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("正在重新拉取目录与正文，当前阅读位置保持不变。"),
            actions = listOf(action("取消切换", "source-switch-rollback"))
        ),
        addition(
            id = "source-switch-rollback",
            title = "换源失败回滚（Source Switch Rollback）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("目标书源切换失败，已回滚到原书源并保留阅读进度。"),
            actions = listOf(action("重新选择书源", "source-switch"), action("返回阅读", "reader"))
        ),
        addition(
            id = "source-switch-preview",
            title = "换源预览（Source Switch Preview）",
            shell = RouteShell.FlowShell,
            renderer = DemoRouteRenderer.SourceSwitchState,
            body = listOf("预览候选书源的最新章节、正文片段和延迟后再确认切换。"),
            actions = listOf(action("返回列表", "source-switch"), action("确认换源", "source-switch-loading"))
        ),
        addition(
            id = "reader-toc-loading",
            title = "目录加载中（TOC Loading）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("正在从书源拉取章节列表。"),
            actions = listOf(action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-toc-offline",
            title = "目录离线（TOC Offline）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("当前网络不可用，无法更新目录。"),
            actions = listOf(action("重试", "toc-bookmarks"), action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-toc-error",
            title = "目录解析错误（TOC Error）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("书源返回的章节列表无法解析，可重试或更换书源。"),
            actions = listOf(action("重试", "toc-bookmarks"), action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-content-loading",
            title = "正文加载中（Content Loading）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("正在加载正文，并保持当前章节上下文。"),
            actions = listOf(action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-content-offline",
            title = "正文离线（Content Offline）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("当前网络不可用，无法拉取本章正文。"),
            actions = listOf(action("重试", "immersive-reading"), action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-content-error",
            title = "正文解析错误（Content Error）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("本章正文解析失败，可能是编码或来源异常。"),
            actions = listOf(action("重试", "immersive-reading"), action("返回控制层", "reader"))
        ),
        addition(
            id = "reader-page-boundary-first",
            title = "首章首页边界（Page Boundary First）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("已是第一章，没有更早的章节。"),
            actions = listOf(action("返回控制层", "reader"), action("继续阅读", "immersive-reading"))
        ),
        addition(
            id = "reader-page-boundary-last",
            title = "末章末页边界（Page Boundary Last）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("已是最后一章，没有更多正文。"),
            actions = listOf(action("返回控制层", "reader"), action("回到首页", "immersive-reading"))
        ),
        addition(
            id = "reader-progress-restore",
            title = "阅读进度恢复（Progress Restore）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("已恢复到上次阅读章节、字符锚点和分页签名。"),
            actions = listOf(action("继续阅读", "immersive-reading"), action("从控制层开始", "reader"))
        ),
        addition(
            id = "reader-background-restore",
            title = "后台恢复（Background Restore）",
            shell = RouteShell.ReaderShell,
            renderer = DemoRouteRenderer.ReaderContentState,
            body = listOf("应用从后台恢复；可重载正文并保留当前位置。"),
            actions = listOf(action("立即重载", "immersive-reading"), action("返回控制层", "reader"))
        ),
        addition(
            id = "import-permission-denied",
            title = "导入权限拒绝（Import Permission Denied）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("存储权限被拒绝，需要授权读取所选 EPUB / TXT 文件。"),
            actions = listOf(action("退出导入", "bookshelf"), action("去设置开启", "local-import"))
        ),
        addition(
            id = "import-format-unsupported",
            title = "导入格式不支持（Import Format Unsupported）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("所选文件格式不受支持；当前支持 EPUB 与 TXT。"),
            actions = listOf(action("取消", "bookshelf"), action("重新选择", "local-import"))
        ),
        addition(
            id = "import-empty-file",
            title = "导入空文件（Import Empty File）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("文件为空或不可读，请检查文件内容与访问权限。"),
            actions = listOf(action("取消", "bookshelf"), action("重新选择", "local-import"))
        ),
        addition(
            id = "import-parsing",
            title = "导入解析中（Import Parsing）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("正在读取文件、解析元数据并识别章节结构。"),
            actions = listOf(action("取消导入", "local-import"), action("下一步", "import-duplicate"))
        ),
        addition(
            id = "import-duplicate",
            title = "导入重复项（Import Duplicate）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("逐项选择保留原书、覆盖或跳过重复书籍。"),
            actions = listOf(action("上一步", "import-parsing"), action("下一步", "import-conflict-resolve"))
        ),
        addition(
            id = "import-conflict-resolve",
            title = "导入冲突处理（Import Conflict Resolve）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("对本地文件与库内书籍的差异选择覆盖、跳过或保留两份。"),
            actions = listOf(action("上一步", "import-duplicate"), action("应用并导入", "import-parsing"))
        ),
        addition(
            id = "import-partial-success",
            title = "导入部分成功（Import Partial Success）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("3 本成功、1 本失败；可重试失败项或查看完整结果。"),
            actions = listOf(action("查看详情", "import-result-detail"), action("返回书架", "bookshelf"))
        ),
        addition(
            id = "import-result-detail",
            title = "导入结果详情（Import Result Detail）",
            shell = RouteShell.LibraryShell,
            renderer = DemoRouteRenderer.LocalImportState,
            body = listOf("按成功、失败和跳过分组展示本次导入明细。"),
            actions = listOf(action("再次导入", "local-import"), action("返回书架", "bookshelf"))
        )
    )

    /**
     * Reader-UI 3.0 capability-closure routes. These entries intentionally describe only the
     * canonical top-level native structure. Their generated bindings are evidence, not Android
     * callbacks, so every page remains read-only until a concrete Host owner is admitted.
     */
    private val contract30Additions: List<DemoRouteAddition> = listOf(
        capabilityAddition(
            id = "onboarding-welcome",
            title = "首次使用（Onboarding Welcome）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.AppShellStructure),
            summary = "展示首次使用入口与能力说明。"
        ),
        capabilityAddition(
            id = "onboarding-capability-setup",
            title = "能力与权限设置（Onboarding Capability Setup）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.PermissionRequiredPage),
            summary = "展示首次启动需要确认的能力与权限。"
        ),
        capabilityAddition(
            id = "permission-recovery",
            title = "权限恢复（Permission Recovery）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.PermissionRequiredPage),
            summary = "展示权限缺失后的恢复路径与状态。"
        ),
        capabilityAddition(
            id = "local-format-support",
            title = "本地格式支持（Local Format Support）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.LocalBookImportPage),
            summary = "展示本地文件格式支持范围与导入入口。"
        ),
        capabilityAddition(
            id = "pdf-reader",
            title = "PDF 阅读（PDF Reader）",
            shell = RouteShell.ReaderShell,
            componentTypes = listOf(ComponentType.ReaderBase, ComponentType.ReaderTopArea),
            summary = "展示 PDF 阅读容器与阅读顶部区域。"
        ),
        capabilityAddition(
            id = "manga-reader",
            title = "漫画阅读（Manga Reader）",
            shell = RouteShell.ReaderShell,
            componentTypes = listOf(ComponentType.ReaderBase, ComponentType.ReaderTopArea),
            summary = "展示漫画阅读容器与阅读顶部区域。"
        ),
        capabilityAddition(
            id = "http-tts-management",
            title = "HTTP TTS 管理（HTTP TTS Management）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SettingsGeneralPage),
            summary = "展示 HTTP TTS 配置列表和管理状态。"
        ),
        capabilityAddition(
            id = "http-tts-editor",
            title = "HTTP TTS 编辑（HTTP TTS Editor）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SourceFormPage),
            summary = "展示 HTTP TTS 表单结构；保存和测试尚未接入 Host。"
        ),
        capabilityAddition(
            id = "http-tts-test",
            title = "HTTP TTS 测试（HTTP TTS Test）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.GlobalStatePage),
            summary = "展示 HTTP TTS 测试状态。"
        ),
        capabilityAddition(
            id = "content-edit",
            title = "正文编辑（Content Edit）",
            shell = RouteShell.ReaderShell,
            componentTypes = listOf(ComponentType.ReaderTopArea, ComponentType.ReaderReplacePanel),
            summary = "展示正文编辑顶部区域与替换面板。"
        ),
        capabilityAddition(
            id = "book-cover-change",
            title = "更换封面（Change Book Cover）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SourceFormPage),
            summary = "展示本地封面地址编辑结构。"
        ),
        capabilityAddition(
            id = "book-cover-search",
            title = "搜索封面（Search Book Cover）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SearchResultsPage),
            summary = "展示封面搜索结果结构。"
        ),
        capabilityAddition(
            id = "chapter-reviews",
            title = "章节评论（Chapter Reviews）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.List),
            summary = "展示章节评论列表结构。"
        ),
        capabilityAddition(
            id = "bookmarks-manager",
            title = "书签管理（Bookmarks Manager）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.ReaderDirectoryPanel),
            summary = "展示书签管理和目录面板结构。"
        ),
        capabilityAddition(
            id = "download-queue",
            title = "下载队列（Download Queue）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.ReaderBookCachePage),
            summary = "展示缓存下载队列结构。"
        ),
        capabilityAddition(
            id = "download-task-detail",
            title = "下载任务（Download Task Detail）",
            shell = RouteShell.LibraryShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.ReaderBookCachePage),
            summary = "展示单个下载任务的状态结构。"
        ),
        capabilityAddition(
            id = "storage-management",
            title = "存储管理（Storage Management）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.GlobalSettingsPage),
            summary = "展示存储占用和清理管理结构。"
        ),
        capabilityAddition(
            id = "webview-login",
            title = "网页登录（WebView Login）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.WebView, ComponentType.Button),
            summary = "展示网页登录容器和只读取消动作证据。"
        ),
        capabilityAddition(
            id = "webview-captcha",
            title = "人机验证（WebView Captcha）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.WebView),
            summary = "展示验证码 WebView 容器。"
        ),
        capabilityAddition(
            id = "webview-challenge",
            title = "验证恢复（WebView Challenge Recovery）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.GlobalStatePage),
            summary = "展示网页验证中断后的恢复状态。"
        ),
        capabilityAddition(
            id = "webview-cookie-return",
            title = "Cookie 回传（WebView Cookie Return）",
            shell = RouteShell.FlowShell,
            componentTypes = listOf(ComponentType.GlobalStatePage),
            summary = "展示 Cookie 回传结果状态。"
        ),
        capabilityAddition(
            id = "settings-tts",
            title = "朗读设置（TTS Settings）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SettingsGeneralPage),
            summary = "展示朗读能力设置入口。"
        ),
        capabilityAddition(
            id = "settings-storage",
            title = "存储设置（Storage Settings）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SettingsGeneralPage),
            summary = "展示存储能力设置入口。"
        ),
        capabilityAddition(
            id = "settings-accessibility",
            title = "无障碍设置（Accessibility Settings）",
            shell = RouteShell.SettingsShell,
            componentTypes = listOf(ComponentType.BackTopBar, ComponentType.SettingsGeneralPage),
            summary = "展示无障碍与减弱动效设置入口。"
        )
    )

    private val explicitRendererByRoute: Map<String, DemoRouteRenderer> =
        (contract25Additions + contract30Additions).associate { it.page.id to it.renderer }

    private val allAuthoredPages: List<DemoRoutePage> =
        authoredPages + contract25Additions.map { it.page } + contract30Additions.map { it.page }

    private val authoredPageById: Map<String, DemoRoutePage> = allAuthoredPages.associateBy { it.id }

    /**
     * Generated [RouteId] is the exact membership and ordering authority. A future contract
     * addition now fails with a precise missing-route error instead of silently rendering a
     * catch-all page; duplicated or stale Android-only routes fail for the same reason.
     */
    val pages: List<DemoRoutePage> = run {
        require(authoredPageById.size == allAuthoredPages.size) {
            "Duplicate DemoRoutePage ids: ${allAuthoredPages.groupBy { it.id }.filterValues { it.size > 1 }.keys}"
        }
        val generatedIds = RouteId.entries.map { it.contractSerialName() }
        val generatedSet = generatedIds.toSet()
        require(authoredPageById.keys == generatedSet) {
            val missing = generatedSet - authoredPageById.keys
            val stale = authoredPageById.keys - generatedSet
            "Android demo registry must exactly match generated RouteId; missing=$missing stale=$stale"
        }
        generatedIds.map { id -> checkNotNull(authoredPageById[id]) }
    }

    val contract25RouteIds: Set<String> = contract25Additions.mapTo(linkedSetOf()) { it.page.id }

    val contract30RouteIds: Set<String> = contract30Additions.mapTo(linkedSetOf()) { it.page.id }

    internal val capabilityClosureStructures: Map<String, List<ComponentType>> =
        contract30Additions.associate { it.page.id to it.componentTypes }

    fun rendererFor(routeId: String): DemoRouteRenderer? = explicitRendererByRoute[routeId]

    val routeIds: Set<String> = pages.map { it.id }.toSet()

    val bookStateRouteIds: Set<String> = setOf(
        "book-detail",
        "book-directory",
        "bookshelf-empty",
        "sort-filter",
        "bookshelf-cover-mode",
        "bookshelf-list-mode",
        "bookshelf-book-more-menu"
    )

    val rssStateRouteIds: Set<String> = setOf(
        "rss-source-feed",
        "rss-source-category-releases",
        "rss-source-category-issues",
        "rss-source-category-discussions",
        "rss-favorite-groups",
        "rss-favorite-group-edit",
        "rss-favorite-clear",
        "rss-empty",
        "rss-error"
    )

    val restoreStateRouteIds: Set<String> = setOf(
        "restore-confirm",
        "restore-progress",
        "restore-conflict",
        "restore-result"
    )

    val discoverStateRouteIds: Set<String> = setOf(
        "discover-control",
        "discover-sort",
        "discover-entry-ranking",
        "discover-entry-bestseller",
        "discover-entry-category",
        "discover-entry-finished",
        "discover-entry-latest",
        "discover-entry-new",
        "discover-entry-booklist",
        "discover-filter-keyword",
        "discover-filter-male",
        "discover-filter-female",
        "discover-sort-popularity",
        "discover-sort-update",
        "discover-sort-collection",
        "discover-sort-finished",
        "discover-sort-words",
        "discover-no-results",
        "discover-loading",
        "discover-refreshing",
        "discover-infinite-loading",
        "discover-page-two",
        "discover-cache-confirm",
        "discover-cache-toast",
        "discover-login-return",
        "discover-switching-source",
        "discover-switched-source",
        "discover-entry-error",
        "discover-empty",
        "discover-error",
        "discover-source-login",
        "discover-rule-test",
        "discover-source-bulk"
    )

    val sourceStateRouteIds: Set<String> = setOf(
        "source-import-options",
        "source-batch",
        "source-groups",
        "source-detect",
        "source-rule-edit",
        "source-debug",
        "source-debug-search-result",
        "source-debug-detail-result",
        "source-debug-catalog-result",
        "source-debug-content-log",
        "source-edit-debug",
        "source-logs",
        "source-code-view",
        "source-delete-confirm"
    )

    fun page(routeId: String): DemoRoutePage? = pages.firstOrNull { it.id == routeId }

    fun shellFor(routeId: String): RouteShell? = pages.firstOrNull { it.id == routeId }?.shell

    fun routeFor(routeId: String): ReaderRoute = when (routeId) {
        MainTab.BOOKSHELF.routeId -> ReaderRoute.TabShell(MainTab.BOOKSHELF)
        MainTab.DISCOVER.routeId -> ReaderRoute.TabShell(MainTab.DISCOVER)
        MainTab.RSS.routeId -> ReaderRoute.TabShell(MainTab.RSS)
        MainTab.SETTINGS.routeId -> ReaderRoute.TabShell(MainTab.SETTINGS)
        RouteIds.READER_CONTROL,
        RouteIds.READER_TOC_BOOKMARKS,
        RouteIds.READER_APPEARANCE,
        RouteIds.READER_TTS,
        RouteIds.READER_AUTO_PAGE,
        RouteIds.READER_CONTENT_SEARCH,
        RouteIds.READER_CONTENT_REPLACEMENT,
        RouteIds.READER_FULL_DIRECTORY,
        RouteIds.READER_FULL_TTS,
        RouteIds.READER_FULL_APPEARANCE,
        RouteIds.READER_FULL_SETTINGS,
        RouteIds.READER_BOOK_CACHE,
        RouteIds.READER_DEBUG_INFO -> ReaderRoute.ReaderControl(routeId)
        RouteIds.READER_FULL_FONT -> ReaderRoute.ReaderFullFont
        RouteIds.READER_FULL_THEME -> ReaderRoute.ReaderFullTheme
        RouteIds.READER_FULL_THEME_EDIT -> ReaderRoute.ReaderFullThemeEdit
        RouteIds.READER_FULL_LAYOUT -> ReaderRoute.ReaderFullLayout
        RouteIds.READER_FULL_PAGE_TURN -> ReaderRoute.ReaderFullPageTurn
        RouteIds.READER_SETTINGS -> ReaderRoute.ReaderSettings
        RouteIds.SOURCE_SWITCH -> ReaderRoute.SourceSwitchFlow()
        RouteIds.BOOK_SEARCH -> ReaderRoute.Search
        RouteIds.SOURCE_IMPORT_PREVIEW -> ReaderRoute.ImportSource
        RouteIds.BOOK_BATCH_MANAGEMENT -> ReaderRoute.BookBatchManagement
        RouteIds.GROUP_MANAGEMENT -> ReaderRoute.GroupManagement
        RouteIds.LOCAL_IMPORT -> ReaderRoute.LocalImport
        RouteIds.BOOKSHELF_SEARCH_SETTINGS -> ReaderRoute.BookshelfSearchSettings
        RouteIds.SETTINGS_GENERAL -> ReaderRoute.SettingsGeneral
        RouteIds.ABOUT_FEEDBACK -> ReaderRoute.AboutFeedback
        RouteIds.SYNC_BACKUP -> ReaderRoute.SyncBackup
        RouteIds.WEBDAV_CONFIG -> ReaderRoute.WebDavConfig
        RouteIds.SOURCE_MANAGEMENT -> ReaderRoute.SourceManagement
        RouteIds.SOURCE_DETAIL -> ReaderRoute.SourceDetail
        RouteIds.SOURCE_EDIT -> ReaderRoute.SourceEdit
        RouteIds.SOURCE_SWITCH_RESULTS -> ReaderRoute.SourceSwitchFlow()
        RouteIds.RSS_SEARCH -> ReaderRoute.RssSearch
        RouteIds.RSS_ALL -> ReaderRoute.RssAll
        RouteIds.RSS_STARRED -> ReaderRoute.RssStarred
        RouteIds.RSS_REFRESHING -> ReaderRoute.RssRefreshing
        RouteIds.RSS_SUBSCRIPTION_MANAGEMENT -> ReaderRoute.RssSubscriptionManagement
        RouteIds.RSS_DETAIL -> ReaderRoute.RssDetail
        RouteIds.RSS_ORIGINAL -> ReaderRoute.RssOriginal
        RouteIds.RSS_ORIGINAL_BROWSER -> ReaderRoute.RssOriginalBrowser
        RouteIds.RSS_SOURCE_EDIT -> ReaderRoute.RssSourceEdit
        RouteIds.RSS_SOURCE_IMPORT -> ReaderRoute.RssSourceImport
        RouteIds.RSS_SOURCE_IMPORT_DETAIL -> ReaderRoute.RssSourceImportDetail
        RouteIds.RSS_SOURCE_IMPORT_RESULT -> ReaderRoute.RssSourceImportResult
        RouteIds.RSS_RULE_SUBSCRIPTION -> ReaderRoute.RssRuleSubscription
        RouteIds.RSS_RULE_SUBSCRIPTION_DETAIL -> ReaderRoute.RssRuleSubscriptionDetail
        RouteIds.RSS_RULE_SUBSCRIPTION_EDIT -> ReaderRoute.RssRuleSubscriptionEdit
        RouteIds.RSS_RULE_SUBSCRIPTION_TEST -> ReaderRoute.RssRuleSubscriptionTest
        RouteIds.RSS_RULE_SUBSCRIPTION_APPLY -> ReaderRoute.RssRuleSubscriptionApply
        RouteIds.RSS_SOURCE_GROUPS -> ReaderRoute.RssSourceGroups
        RouteIds.RSS_SOURCE_GROUP_EDIT -> ReaderRoute.RssSourceGroupEdit
        RouteIds.RSS_SOURCE_ACTIONS -> ReaderRoute.RssSourceActions
        RouteIds.RSS_SOURCE_BATCH -> ReaderRoute.RssSourceBatch
        RouteIds.RSS_SOURCE_EXPORT -> ReaderRoute.RssSourceExport
        RouteIds.RSS_SOURCE_EXPORT_DETAIL -> ReaderRoute.RssSourceExportDetail
        RouteIds.RSS_SOURCE_EXPORT_RESULT -> ReaderRoute.RssSourceExportResult
        RouteIds.RSS_SOURCE_BATCH_DISABLE -> ReaderRoute.RssSourceBatchDisable
        RouteIds.RSS_SOURCE_DEBUG -> ReaderRoute.RssSourceDebug
        RouteIds.RSS_SOURCE_VARS -> ReaderRoute.RssSourceVars
        RouteIds.RSS_SOURCE_LOGIN -> ReaderRoute.RssSourceLogin
        RouteIds.RSS_SOURCE_LOGIN_WEB -> ReaderRoute.RssSourceLoginWeb
        RouteIds.RSS_SOURCE_LOGIN_COOKIE -> ReaderRoute.RssSourceLoginCookie
        RouteIds.RSS_SOURCE_LOGIN_CLEAR -> ReaderRoute.RssSourceLoginClear
        RouteIds.RSS_SOURCE_PIN -> ReaderRoute.RssSourcePin
        RouteIds.RSS_SOURCE_DISABLE -> ReaderRoute.RssSourceDisable
        RouteIds.RSS_READ_RECORD -> ReaderRoute.RssReadRecord
        RouteIds.RSS_RECORD_CLEAR -> ReaderRoute.RssRecordClear
        in bookStateRouteIds -> ReaderRoute.BookState(routeId)
        in rssStateRouteIds -> ReaderRoute.RssState(routeId)
        in restoreStateRouteIds -> ReaderRoute.RestoreState(routeId)
        in discoverStateRouteIds -> ReaderRoute.DiscoverState(routeId)
        in sourceStateRouteIds -> ReaderRoute.SourceState(routeId)
        else -> ReaderRoute.Demo(routeId)
    }
}
