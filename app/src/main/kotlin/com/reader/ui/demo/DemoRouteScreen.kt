package com.reader.ui.demo

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.android.BuildConfig
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.ReaderThemeResolver
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.motion.MotionController
import com.reader.ui.tokens.ReaderColorToken
import com.reader.ui.tokens.ReaderTokenAdapter
import io.reader.ui.contract.RouteShell
import com.reader.ui.shell.DemoFlowShell
import com.reader.ui.shell.DemoLibraryShell
import com.reader.ui.shell.DemoMainTabShell
import com.reader.ui.shell.DemoReaderShell
import com.reader.ui.shell.DemoSettingsShell
import com.reader.ui.bookshelf.W1ImportRouteScreen
import com.reader.ui.reading.ReaderContentStateScreen
import com.reader.ui.reading.SourceSwitchStateScreen
import com.reader.ui.shell.OverlayState
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.shell.readerFontSizePinch
import com.reader.ui.shell.readerPageSwipe

@Composable
fun DemoRouteScreen(
    routeId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onDispatch: (ReaderUiIntent) -> Unit = {},
    overlayState: OverlayState = OverlayState.None,
    enableCanonicalGraphDiagnostics: Boolean = BuildConfig.DEBUG
) {
    // R16 Shadow only: consume generated ScreenGraph for the live route and
    // record alias/variant/component coverage without changing renderer authority.
    remember(routeId) {
        CanonicalScreenGraphShadowObserver.observe(CanonicalScreenGraphQuery(routeId))
    }
    val page = remember(routeId) {
        requireNotNull(DemoRouteRegistry.page(routeId)) {
            "Route $routeId is not present in the generated 3.0 DemoRouteRegistry"
        }
    }
    val contractRenderer = remember(routeId) { DemoRouteRegistry.rendererFor(routeId) }
    var graphPreviewOpen by remember(routeId) { mutableStateOf(false) }
    var graphDiagnostic by remember(routeId) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        when {
            contractRenderer != null -> ExplicitContractRouteScreen(
                renderer = contractRenderer,
                page = page,
                onBack = onBack,
                onNavigate = onNavigate
            )
            routeId == "source-switch" -> ReaderDemoScreen(page, onBack, onNavigate, onDispatch)
            routeId.startsWith("discover-") -> DiscoverDemoScreen(page, onBack, onNavigate)
            routeId.startsWith("rss-") -> RssDemoScreen(page, onBack, onNavigate)
            routeId in bookDemoRoutes -> BookDemoScreen(page, onBack, onNavigate)
            routeId in readerDemoRoutes -> ReaderDemoScreen(page, onBack, onNavigate, onDispatch)
            routeId.startsWith("source-") -> SourceDemoScreen(page, onBack, onNavigate)
            routeId.startsWith("restore-") -> RestoreDemoScreen(page, onBack, onNavigate)
            // 通用分发：按 Shell 类型
            else -> renderGenericRoute(page, onBack, onNavigate, overlayState)
        }

        if (enableCanonicalGraphDiagnostics) {
            if (graphPreviewOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .zIndex(10_000f)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ScreenGraph · $routeId",
                            style = ReaderTextStyles.sectionTitle,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        DemoButton(label = "关闭", compact = true) { graphPreviewOpen = false }
                    }
                    graphDiagnostic?.let { diagnostic ->
                        Text(
                            text = diagnostic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                        CanonicalScreenGraphRouteRenderer(
                            query = CanonicalScreenGraphQuery(routeId),
                            onBack = { graphPreviewOpen = false },
                            onNavigate = { target -> graphDiagnostic = "navigate:$target" },
                            onAction = { action ->
                                graphDiagnostic =
                                    "${action.target}:${action.binding.event.name}:${action.binding.payload}"
                            }
                        )
                    }
                }
            } else {
                DemoButton(
                    label = "SG",
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(8.dp)
                        .zIndex(9_999f)
                ) { graphPreviewOpen = true }
            }
        }
    }
}

/**
 * Explicit render dispatch for every post-2.4 route addition. There is intentionally no fallback
 * branch: registry initialization proves the 2.5 and 3.0 classifications are exhaustive.
 */
@Composable
private fun ExplicitContractRouteScreen(
    renderer: DemoRouteRenderer,
    page: DemoRoutePage,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when (renderer) {
        DemoRouteRenderer.ReaderWorkspaceState -> ReaderContractStateScreen(
            page = page,
            iconRes = R.drawable.reader_ic_appearance,
            onNavigate = onNavigate
        )
        DemoRouteRenderer.ReaderReplacementState -> ReaderReplaceStateScreen(
            routeId = page.id,
            onBack = onBack,
            onNavigate = onNavigate
        )
        DemoRouteRenderer.ReaderContentState -> ReaderContentStateScreen(
            routeId = page.id,
            onBack = onBack,
            onNavigate = onNavigate
        )
        DemoRouteRenderer.SourceSwitchState -> SourceSwitchStateScreen(
            routeId = page.id,
            onBack = onBack,
            onNavigate = onNavigate
        )
        DemoRouteRenderer.LocalImportState -> W1ImportRouteScreen(
            page = page,
            onBack = onBack,
            onNavigate = onNavigate
        )
        DemoRouteRenderer.CapabilityClosureStructure -> CapabilityClosureRouteScreen(
            page = page,
            onBack = onBack
        )
    }
}

/**
 * Native shell coverage for Reader-UI 3.0 capability routes. The registry has no actions for
 * these pages and every shell receives a no-op navigation callback, so generated planned
 * bindings remain visible in ScreenGraph diagnostics but cannot execute as Android behavior.
 */
@Composable
private fun CapabilityClosureRouteScreen(
    page: DemoRoutePage,
    onBack: () -> Unit
) {
    val readOnlyPage = page.copy(actions = emptyList())
    val failClosedNavigate: (String) -> Unit = {}
    when (readOnlyPage.shell) {
        RouteShell.MainTabShell -> DemoMainTabShell(
            title = readOnlyPage.cleanTitle(),
            activeRoute = "bookshelf",
            onNavigate = failClosedNavigate
        ) {
            capabilityClosureLazyContent(readOnlyPage)
        }
        RouteShell.LibraryShell -> DemoLibraryShell(
            title = readOnlyPage.cleanTitle(),
            onBack = onBack,
            bottomActions = emptyList(),
            onNavigate = failClosedNavigate
        ) {
            capabilityClosureLazyContent(readOnlyPage)
        }
        RouteShell.ReaderShell -> DemoReaderShell(
            readingContent = {
                CapabilityClosureNativeContent(
                    page = readOnlyPage,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        )
        RouteShell.SettingsShell -> DemoSettingsShell(
            title = readOnlyPage.cleanTitle(),
            onBack = onBack,
            bottomActions = emptyList(),
            onNavigate = failClosedNavigate
        ) {
            capabilityClosureLazyContent(readOnlyPage)
        }
        RouteShell.FlowShell -> DemoFlowShell(
            title = readOnlyPage.cleanTitle(),
            onBack = onBack,
            stepContent = {
                CapabilityClosureNativeContent(
                    page = readOnlyPage,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        )
    }
}

private fun LazyListScope.capabilityClosureLazyContent(page: DemoRoutePage) {
    item { CapabilityClosureNativeContent(page = page, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun CapabilityClosureNativeContent(
    page: DemoRoutePage,
    modifier: Modifier = Modifier
) {
    val componentTypes = requireNotNull(DemoRouteRegistry.capabilityClosureStructures[page.id]) {
        "Missing Reader-UI 3.0 native structure for ${page.id}"
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DemoStateCard(
            iconRes = R.drawable.reader_ic_info,
            title = page.cleanTitle(),
            body = page.body.first()
        )
        DemoSectionCard(title = "Reader-UI 3.0 原生结构") {
            componentTypes.forEachIndexed { index, type ->
                DemoListItem(
                    title = type.name,
                    subtitle = "canonical top-level ${index + 1} / ${componentTypes.size}",
                    trailingText = "只读"
                )
            }
        }
        DemoSectionCard(title = "交互约束") {
            Text(
                text = page.body.last(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ReaderContractStateScreen(
    page: DemoRoutePage,
    @DrawableRes iconRes: Int,
    onNavigate: (String) -> Unit
) {
    DemoReaderShell(
        readingContent = { genericReaderContent(page.copy(actions = emptyList())) },
        bottomSheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                DemoStateCard(
                    iconRes = iconRes,
                    title = page.cleanTitle(),
                    body = page.body.joinToString("\n"),
                    actions = page.actions,
                    onNavigate = onNavigate
                )
            }
        }
    )
}

private val bookDemoRoutes = setOf("book-detail", "book-directory", "bookshelf-empty", "sort-filter")
private val readerDemoRoutes = setOf(
    "reader",
    "toc-bookmarks",
    "tts",
    "auto-page",
    "content-search",
    "content-replacement",
    "reader-appearance",
    "reader-settings",
    "reader-full-directory",
    "reader-full-tts",
    "reader-full-appearance",
    "reader-full-settings",
    "reader-book-cache",
    "reader-debug-info"
)

private fun DemoRoutePage.cleanTitle(): String = title.substringBefore("（").trim().ifEmpty { title }

@Composable
private fun renderGenericRoute(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit, overlayState: OverlayState = OverlayState.None) {
    when (page.shell) {
        RouteShell.MainTabShell -> DemoMainTabShell(
            title = page.cleanTitle(),
            activeRoute = "bookshelf",
            onNavigate = onNavigate
        ) {
            genericMainTabContent(page, onNavigate)
        }
        RouteShell.LibraryShell -> DemoLibraryShell(
            title = page.cleanTitle(),
            onBack = onBack,
            bottomActions = page.actions,
            onNavigate = onNavigate,
            overlayState = overlayState
        ) {
            genericLibraryContent(page, onNavigate)
        }
        RouteShell.ReaderShell -> DemoReaderShell(
            readingContent = { genericReaderContent(page) }
        )
        RouteShell.SettingsShell -> DemoSettingsShell(
            title = page.cleanTitle(),
            onBack = onBack,
            bottomActions = page.actions,
            onNavigate = onNavigate,
            overlayState = overlayState
        ) {
            genericSettingsContent(page, onNavigate)
        }
        RouteShell.FlowShell -> DemoFlowShell(
            title = page.cleanTitle(),
            onBack = onBack,
            stepContent = { genericFlowContent(page) }
        )
    }
}

private fun LazyListScope.genericMainTabContent(page: DemoRoutePage, onNavigate: (String) -> Unit) {
    item { DemoStateCard(iconRes = R.drawable.reader_ic_info, title = page.cleanTitle(), body = page.body.firstOrNull() ?: page.id) }
    if (page.body.size > 1) {
        item {
            DemoSectionCard(title = "详情") {
                Column {
                    page.body.drop(1).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
    if (page.actions.isNotEmpty()) {
        item {
            DemoSectionCard(title = "操作") {
                Column {
                    page.actions.forEach { action ->
                        DemoListItem(title = action.label, trailingText = "›", onClick = { onNavigate(action.targetRoute) })
                    }
                }
            }
        }
    }
}

private fun LazyListScope.genericLibraryContent(page: DemoRoutePage, onNavigate: (String) -> Unit) {
    item { DemoStateCard(iconRes = R.drawable.reader_ic_info, title = page.cleanTitle(), body = page.body.firstOrNull() ?: page.id) }
    if (page.body.size > 1) {
        item {
            DemoSectionCard(title = "详情") {
                Column {
                    page.body.drop(1).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
    if (page.actions.isNotEmpty()) {
        item {
            DemoSectionCard(title = "操作") {
                Column {
                    page.actions.forEach { action ->
                        DemoListItem(title = action.label, trailingText = "›", onClick = { onNavigate(action.targetRoute) })
                    }
                }
            }
        }
    }
}

@Composable
private fun genericReaderContent(page: DemoRoutePage) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        page.body.forEach { line ->
            Text(line, style = ReaderTextStyles.readerBody, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

private fun LazyListScope.genericSettingsContent(page: DemoRoutePage, onNavigate: (String) -> Unit) {
    item {
        DemoSectionCard(title = page.cleanTitle()) {
            Column {
                page.body.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
    if (page.actions.isNotEmpty()) {
        item {
            DemoSectionCard(title = "操作") {
                Column {
                    page.actions.forEach { action ->
                        DemoSettingsRow(title = action.label, onClick = { onNavigate(action.targetRoute) })
                    }
                }
            }
        }
    }
}

@Composable
private fun genericFlowContent(page: DemoRoutePage) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(page.cleanTitle(), style = ReaderTextStyles.appBarTitle, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        page.body.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

// Discover --------------------------------------------------------------------------------------

@Composable
private fun DiscoverDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    when (page.id) {
        "discover-source-login" -> DiscoverSourceLoginScreen(onBack, onNavigate)
        "discover-rule-test" -> DiscoverRuleTestScreen(onBack, onNavigate)
        "discover-source-bulk" -> DiscoverSourceBulkScreen(onBack, onNavigate)
        else -> Box(Modifier.fillMaxSize()) {
            DemoMainTabShell(title = "发现", activeRoute = "discover", onNavigate = onNavigate) {
                discoverMainContent(page, onNavigate)
            }
            if (page.id == "discover-cache-confirm") {
                DiscoverCacheDialog(onNavigate = onNavigate, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun LazyListScope.discoverMainContent(page: DemoRoutePage, onNavigate: (String) -> Unit) {
    val state = discoverState(page.id)
    if (page.id == "discover-empty") {
        item {
            DemoStateCard(
                iconRes = R.drawable.reader_ic_source_stack,
                title = "当前没有启用发现的书源",
                body = "启用发现后，可以在这里浏览书源提供的排行榜、分类和书单。",
                actions = listOf(DemoRouteAction("去书源管理", "source-management"), DemoRouteAction("导入书源", "source-import-options")),
                onNavigate = onNavigate
            )
        }
        return
    }
    if (page.id == "discover-error") {
        item { DiscoverSourceBar("优书网", "排行榜 · 解析失败", expanded = false, onNavigate = onNavigate) }
        item {
            DemoStateCard(
                iconRes = R.drawable.reader_ic_warning,
                title = "发现入口解析失败",
                body = "当前入口返回异常，已保留上一批缓存结果。你可以重试、刷新入口、编辑源或切换书源。",
                actions = listOf(
                    DemoRouteAction("重试", "discover-refreshing"),
                    DemoRouteAction("切换书源", "discover-control"),
                    DemoRouteAction("编辑源", "discover-rule-test")
                ),
                onNavigate = onNavigate
            )
        }
        item { DiscoverBookList(discoverBooks(), muted = true, onNavigate = onNavigate) }
        return
    }
    if (page.id == "discover-cache-toast") {
        item { DemoInlineNotice("已清除优书网发现缓存", R.drawable.reader_ic_check) }
    }
    item { DiscoverSourceBar(state.sourceName, state.sourceMeta, state.expanded, onNavigate) }
    if (state.expanded) {
        item { DiscoverControlPanel(state, onNavigate) }
    } else {
        item { DiscoverEntryChips(active = state.entry, entries = state.entries, onNavigate = onNavigate) }
        item { DiscoverFilterBar(filter = state.filter, sort = state.sort, sortOpen = page.id == "discover-sort", onNavigate = onNavigate) }
    }
    if (page.id == "discover-refreshing" || page.id == "discover-login-return") {
        item {
            DemoInlineNotice(
                if (page.id == "discover-login-return") "登录成功，正在刷新当前发现入口" else "正在刷新当前列表",
                R.drawable.reader_ic_refresh
            )
        }
    }
    if (page.id == "discover-no-results") {
        item {
            DemoStateCard(
                iconRes = R.drawable.reader_ic_search,
                title = "当前条件没有发现结果",
                body = "可以重置筛选、切换入口，或刷新当前书源。",
                actions = listOf(
                    DemoRouteAction("重置筛选", "discover"),
                    DemoRouteAction("切换入口", "discover-control"),
                    DemoRouteAction("刷新", "discover-refreshing")
                ),
                onNavigate = onNavigate
            )
        }
    } else {
        item { DiscoverListHeader(state.entry, state.total) }
        if (page.id == "discover-loading") {
            items(listOf("s1", "s2", "s3", "s4")) { DemoSkeletonRow() }
        } else {
            item { DiscoverBookList(if (state.switched) switchedDiscoverBooks() else discoverBooks(page.id), muted = state.muted, onNavigate = onNavigate) }
        }
        if (page.id == "discover-infinite-loading") item { DemoInlineNotice("继续加载", R.drawable.reader_ic_more) }
        if (page.id == "discover-page-two") item { DemoButton("回到顶部", iconRes = R.drawable.reader_ic_top) { onNavigate("discover") } }
    }
}

private data class DiscoverState(
    val sourceName: String,
    val sourceMeta: String,
    val entry: String,
    val filter: String,
    val sort: String,
    val total: Int,
    val entries: List<String>,
    val expanded: Boolean,
    val muted: Boolean,
    val switched: Boolean
)

private fun discoverState(routeId: String): DiscoverState {
    val switched = routeId == "discover-switched-source"
    val entry = when (routeId) {
        "discover-entry-bestseller" -> "畅销"
        "discover-entry-category" -> "分类"
        "discover-entry-finished" -> "完本"
        "discover-entry-latest" -> "最新"
        "discover-entry-new" -> "新书"
        "discover-entry-booklist" -> "书单"
        else -> if (switched) "畅销" else "排行榜"
    }
    val filter = when (routeId) {
        "discover-filter-keyword" -> "关键词"
        "discover-filter-female" -> "女频"
        else -> "男频"
    }
    val sort = when (routeId) {
        "discover-sort-update" -> "更新"
        "discover-sort-collection" -> "收藏"
        "discover-sort-finished" -> "完本"
        "discover-sort-words" -> "字数"
        else -> if (switched) "更新" else "人气"
    }
    return DiscoverState(
        sourceName = if (switched) "起点导入" else "优书网",
        sourceMeta = if (switched) "正版 · 已启用发现 · 180ms" else "默认分组 · 已启用发现 · 120ms",
        entry = entry,
        filter = filter,
        sort = sort,
        total = when (routeId) {
            "discover-page-two" -> 38
            "discover-entry-category" -> 32
            "discover-entry-latest" -> 27
            "discover-entry-booklist" -> 14
            else -> 18
        },
        entries = if (switched) listOf("畅销", "分类", "新书", "完本") else listOf("排行榜", "分类", "完本", "最新", "书单"),
        expanded = routeId in setOf("discover-control", "discover-cache-confirm", "discover-switching-source", "discover-entry-error"),
        muted = routeId in setOf("discover-switching-source", "discover-entry-error"),
        switched = switched
    )
}

@Composable
private fun DiscoverSourceBar(source: String, meta: String, expanded: Boolean, onNavigate: (String) -> Unit) {
    DemoCard(onClick = { onNavigate(if (expanded) "discover" else "discover-control") }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoIconBox(R.drawable.reader_ic_source_stack)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(source, style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
                Text(meta, style = denseMetaStyle(), color = readerExtraColors().muted)
            }
            DemoIcon(R.drawable.reader_ic_chevron, size = 18.dp, tint = readerExtraColors().muted)
        }
    }
}

@Composable
private fun DiscoverEntryChips(active: String, entries: List<String>, onNavigate: (String) -> Unit) {
    DemoChipRow(entries.map { it to discoverEntryRoute(it) }, active, onNavigate)
}

@Composable
private fun DiscoverFilterBar(filter: String, sort: String, sortOpen: Boolean, onNavigate: (String) -> Unit) {
    DemoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoChip("筛选", active = false) { onNavigate("discover-sort") }
            DemoChip("$filter · $sort", active = true) { onNavigate("discover-sort") }
            Spacer(Modifier.weight(1f))
            DemoButton("应用", compact = true, primary = true) { onNavigate("discover-refreshing") }
        }
        if (sortOpen) {
            Spacer(Modifier.height(10.dp))
            DemoSectionLabel("范围")
            DemoChipRow(listOf("关键词" to "discover-filter-keyword", "男频" to "discover-filter-male", "女频" to "discover-filter-female"), filter, onNavigate)
            Spacer(Modifier.height(8.dp))
            DemoSectionLabel("排序")
            DemoChipRow(listOf("人气" to "discover-sort-popularity", "更新" to "discover-sort-update", "收藏" to "discover-sort-collection", "完本" to "discover-sort-finished", "字数" to "discover-sort-words"), sort, onNavigate)
        }
    }
}

@Composable
private fun DiscoverControlPanel(state: DiscoverState, onNavigate: (String) -> Unit) {
    DemoCard {
        DemoSectionLabel("书源")
        listOf(
            Triple("优书网", "默认 · 120ms", "discover-control"),
            Triple("起点导入", "正版 · 180ms", "discover-switching-source"),
            Triple("轻小说文库", "需登录", "discover-control"),
            Triple("本地聚合源", "维护中", "discover-control")
        ).forEach { (name, meta, route) ->
            DemoRow(
                iconRes = R.drawable.reader_ic_source_stack,
                title = name,
                meta = meta,
                trailing = if (name == state.sourceName) "当前" else null,
                onClick = { onNavigate(route) }
            )
        }
        if (state.muted) {
            DemoInlineNotice("入口解析失败", R.drawable.reader_ic_warning)
        }
        Spacer(Modifier.height(8.dp))
        DemoSectionLabel("入口")
        DemoChipRow(state.entries.map { it to discoverEntryRoute(it) }, state.entry, onNavigate)
        Spacer(Modifier.height(8.dp))
        DemoSectionLabel("筛选")
        DemoChipRow(listOf("男频" to "discover-filter-male", "女频" to "discover-filter-female", "排序：${state.sort}" to "discover-sort"), state.filter, onNavigate)
        Spacer(Modifier.height(10.dp))
        DemoActionGrid(
            actions = listOf(
                DemoRouteAction("刷新入口", "discover-switching-source"),
                DemoRouteAction("清缓存", "discover-cache-confirm"),
                DemoRouteAction("登录", "discover-source-login"),
                DemoRouteAction("编辑源", "discover-rule-test"),
                DemoRouteAction("管理发现源", "discover-source-bulk")
            ),
            onNavigate = onNavigate
        )
    }
}

@Composable
private fun DiscoverListHeader(entry: String, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(entry, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
        Text("$total 本", style = denseMetaStyle(), color = readerExtraColors().muted)
    }
}

@Composable
private fun DiscoverBookList(books: List<DemoBook>, muted: Boolean, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        books.forEach { book ->
            DemoCard(alpha = if (muted) 0.46f else 0.78f, onClick = { onNavigate("book-detail") }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DemoCover(title = book.title, width = 42.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(book.title, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground)
                        Text(book.meta, style = denseMetaStyle(), color = readerExtraColors().muted)
                        Text(book.latest, style = tinyStrongStyle(), color = MaterialTheme.colorScheme.primary)
                        Text(book.desc, style = denseMetaStyle(), color = readerExtraColors().controlInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (book.inShelf) Box(Modifier.size(7.dp).background(readerExtraColors().forest, ReaderShapes.pill))
                }
            }
        }
    }
}

@Composable
private fun DiscoverCacheDialog(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(28.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, ReaderShapes.lg)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.lg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DemoIconBox(R.drawable.reader_ic_trash, size = 42.dp)
        Text("清除发现缓存？", style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground)
        Text("将清除优书网的发现入口缓存，不影响书架和阅读进度。", style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoButton("取消", modifier = Modifier.weight(1f)) { onNavigate("discover-control") }
            DemoButton("确认清除", primary = true, modifier = Modifier.weight(1f)) { onNavigate("discover-cache-toast") }
        }
    }
}

@Composable
private fun DiscoverSourceLoginScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "书源登录", onBack = onBack, bottomActions = listOf(DemoRouteAction("返回控制层", "discover-control"), DemoRouteAction("完成刷新", "discover-login-return")), onNavigate = onNavigate) {
        item { DemoHeroCard(R.drawable.reader_ic_shield, "优书网", "默认分组 · 发现入口需要登录") }
        item {
            DemoCard {
                DemoRow(R.drawable.reader_ic_warning, "登录状态", "未登录 · 最近检测 10:32", "需登录")
                DemoDivider()
                DemoRow(R.drawable.reader_ic_source_stack, "适用范围", "发现入口、详情页、目录页", "当前源")
                DemoDivider()
                DemoRow(R.drawable.reader_ic_shield, "Cookie 保存", "仅保存在本机书源配置中", "开启")
            }
        }
        item {
            DemoActionGrid(
                listOf(
                    DemoRouteAction("打开网页登录", "discover-login-return"),
                    DemoRouteAction("保存登录信息", "discover-login-return"),
                    DemoRouteAction("重新检测", "discover-control")
                ),
                onNavigate
            )
        }
        item { Text("返回发现页后，当前书源和当前入口保持不变，只刷新内容列表。", style = denseMetaStyle(), color = readerExtraColors().muted) }
    }
}

@Composable
private fun DiscoverRuleTestScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    SourceRuleLikeScreen(
        title = "发现规则测试",
        onBack = onBack,
        onNavigate = onNavigate,
        heroTitle = "优书网",
        heroMeta = "发现入口 · 已启用发现 · 120ms",
        fields = listOf(
            Triple("入口规则", "排行榜", "ruleExploreUrl"),
            Triple("HTML 片段", "<li class=\"book\">长夜余火</li>", "ruleExploreList"),
            Triple("解析结果", "解析到 18 本书", "首条：长夜余火 · 爱潜水的乌贼")
        ),
        actions = listOf(DemoRouteAction("重新调测", "discover-rule-test"), DemoRouteAction("保存", "discover-control"))
    )
}

@Composable
private fun DiscoverSourceBulkScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "发现源管理", onBack = onBack, bottomActions = listOf(DemoRouteAction("禁用", "discover-source-bulk"), DemoRouteAction("刷新", "discover-control")), onNavigate = onNavigate) {
        item { DemoSearchBar("搜索书源名称或分组") }
        item { DemoChipRow(listOf("全部" to "discover-source-bulk", "已启用发现" to "discover-source-bulk", "需处理" to "discover-source-bulk"), "全部", onNavigate) }
        items(
            listOf(
                Triple("优书网", "默认分组 · 已启用发现 · 120ms", "已选"),
                Triple("起点导入", "正版 · 已启用发现 · 180ms", "已选"),
                Triple("轻小说文库", "测试书源 · 需登录", "需处理"),
                Triple("本地聚合源", "自定义 · 维护中", "暂停")
            )
        ) { (name, meta, status) ->
            DemoRowCard(R.drawable.reader_ic_source_stack, name, meta, status)
        }
    }
}

private fun discoverEntryRoute(label: String): String = when (label) {
    "排行榜" -> "discover-entry-ranking"
    "畅销" -> "discover-entry-bestseller"
    "分类" -> "discover-entry-category"
    "完本" -> "discover-entry-finished"
    "最新" -> "discover-entry-latest"
    "新书" -> "discover-entry-new"
    "书单" -> "discover-entry-booklist"
    else -> "discover"
}

private data class DemoBook(val title: String, val meta: String, val latest: String, val desc: String, val inShelf: Boolean = false)

private fun discoverBooks(routeId: String = "discover"): List<DemoBook> = listOf(
    DemoBook("长夜余火", "爱潜水的乌贼 · 科幻 · 连载", "最新：第 32 章 雨夜", "雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", true),
    DemoBook("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。"),
    DemoBook("三体", "刘慈欣 · 科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。"),
    DemoBook("明朝那些事儿", "当年明月 · 历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。"),
    DemoBook("纸上城市", "默认分组 · 都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。")
) + if (routeId in setOf("discover-page-two", "discover-infinite-loading")) {
    listOf(DemoBook("旧日回响", "离线书库 · 奇幻 · 连载", "最新：第 18 章", "旧日钟声从废墟里传回，缓存章节仍可打开。"))
} else {
    emptyList()
}

private fun switchedDiscoverBooks(): List<DemoBook> = listOf(
    DemoBook("诡秘之主", "爱潜水的乌贼 · 奇幻 · 完本", "最新：番外已整理", "克莱恩在迷雾中醒来，新的线索沿着塔罗会延伸。"),
    DemoBook("纸上城市", "默认分组 · 都市 · 连载", "最新：第 18 章", "城市被写在纸页上，所有路口都藏着旧书源的暗号。"),
    DemoBook("灯塔与雾", "书源同步 · 悬疑 · 连载", "最新：第 51 章", "雾气吞没海岸线，灯塔的记录仍在夜里闪烁。"),
    DemoBook("群星之间", "本地导入 · 科幻 · 连载", "最新：第 12 章", "星舰穿过静默航道，旧文明的坐标重新亮起。")
)

// RSS -------------------------------------------------------------------------------------------

@Composable
private fun RssDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    when (page.id) {
        "rss-empty" -> DemoLibraryShell(title = "RSS 空状态", onBack = onBack) {
            item {
                DemoStateCard(
                    iconRes = R.drawable.reader_ic_rss,
                    title = "暂无未读订阅",
                    body = "当前订阅源没有新的未读条目。你可以查看全部、管理订阅源或手动刷新。日常空状态仍保留 RSS 主导航上下文。",
                    actions = listOf(DemoRouteAction("查看全部", "rss-all"), DemoRouteAction("订阅管理", "rss-subscription-management")),
                    onNavigate = onNavigate
                )
            }
        }
        "rss-error" -> DemoLibraryShell(title = "RSS 错误", onBack = onBack) {
            item {
                DemoStateCard(
                    iconRes = R.drawable.reader_ic_warning,
                    title = "订阅刷新失败",
                    body = "2 个订阅源刷新失败，已保留最近缓存条目。可以稍后重试、查看错误源，或进入订阅源管理修复登录态和规则。",
                    actions = listOf(DemoRouteAction("重试刷新", "rss-refreshing"), DemoRouteAction("订阅管理", "rss-subscription-management")),
                    onNavigate = onNavigate
                )
            }
            item { DemoRowCard(R.drawable.reader_ic_warning, "书源维护公告", "登录态失效 · 需要重新登录", "异常") }
            item { DemoRowCard(R.drawable.reader_ic_offline, "本地系统通知", "源已暂停 · 不参与自动刷新", "暂停") }
        }
        "rss-favorite-groups" -> RssFavoriteGroupsScreen(onBack, onNavigate)
        "rss-favorite-group-edit" -> RssFavoriteEditScreen(onBack, onNavigate)
        "rss-favorite-clear" -> DemoConfirmScreen("清空收藏分组", "清空当前收藏分组？", "只会移出当前分组，不会删除文章、订阅源或阅读记录。", onBack, onNavigate, "rss-starred")
        else -> RssSourceFeedScreen(page, onBack, onNavigate)
    }
}

@Composable
private fun RssSourceFeedScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val category = when (page.id) {
        "rss-source-category-releases" -> Triple("Releases", "版本发布 · 8 条", "Releases")
        "rss-source-category-issues" -> Triple("Issues", "问题讨论 · 6 条", "Issues")
        "rss-source-category-discussions" -> Triple("Discussions", "社区讨论 · 4 条", "Discussions")
        else -> Triple("全部", "默认 RSS 解析 · 18 条", "GitHub Releases")
    }
    DemoLibraryShell(title = category.third, onBack = onBack) {
        item {
            DemoHeroCard(
                iconRes = R.drawable.reader_ic_rss,
                title = "GitHub Releases",
                meta = "开源项目 · ${category.second} · 默认 RSS 解析 · 10:18 更新",
                badge = "正常"
            )
        }
        item {
            DemoActionGrid(
                actions = listOf(
                    DemoRouteAction("刷新", "rss-refreshing"),
                    DemoRouteAction("编辑源", "rss-source-edit"),
                    DemoRouteAction("记录", "rss-read-record"),
                    DemoRouteAction("调试", "rss-source-debug")
                ),
                onNavigate = onNavigate
            )
        }
        item {
            DemoCard {
                DemoSectionLabel("分类")
                DemoChipRow(
                    items = listOf(
                        "全部" to "rss-source-feed",
                        "Releases" to "rss-source-category-releases",
                        "Issues" to "rss-source-category-issues",
                        "Discussions" to "rss-source-category-discussions"
                    ),
                    active = category.first,
                    onNavigate = onNavigate
                )
            }
        }
        item { RssArticleSection(category.third, rssArticles().filter { it.source == "GitHub Releases" }, "源操作", "rss-source-actions", onNavigate) }
        item { DemoInlineNotice("继续下滑加载下一页", R.drawable.reader_ic_more) }
    }
}

@Composable
private fun RssArticleSection(title: String, articles: List<RssArticle>, actionLabel: String, actionRoute: String, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
            DemoTextButton(actionLabel) { onNavigate(actionRoute) }
        }
        articles.forEach { article ->
            DemoCard(onClick = { onNavigate("rss-detail") }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (article.unread) MaterialTheme.colorScheme.primary else readerExtraColors().hairline, ReaderShapes.pill))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(article.title, style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${article.source} · ${article.time} · ${article.group}", style = denseMetaStyle(), color = readerExtraColors().muted)
                        Text(article.desc, style = denseMetaStyle(), color = readerExtraColors().controlInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DemoIcon(if (article.starred) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_chevron, tint = readerExtraColors().muted)
                }
            }
        }
    }
}

@Composable
private fun RssFavoriteGroupsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "收藏分组", onBack = onBack, bottomActions = listOf(DemoRouteAction("取消", "rss-starred"), DemoRouteAction("保存", "rss-starred")), onNavigate = onNavigate) {
        items(
            listOf(
                Triple("默认分组", "2 条收藏 · 首页显示", "显示"),
                Triple("开源项目", "1 条收藏 · 自动归类", "显示"),
                Triple("社区", "1 条收藏 · 手动归类", "隐藏")
            )
        ) { (name, meta, status) -> DemoRowCard(R.drawable.reader_ic_bookmark, name, meta, status) }
        item { DemoActionGrid(listOf(DemoRouteAction("新增分组", "rss-favorite-group-edit"), DemoRouteAction("排序", "rss-favorite-group-edit")), onNavigate) }
    }
}

@Composable
private fun RssFavoriteEditScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "编辑收藏分组", onBack = onBack, bottomActions = listOf(DemoRouteAction("取消", "rss-favorite-groups"), DemoRouteAction("保存", "rss-favorite-groups")), onNavigate = onNavigate) {
        items(
            listOf(
                "分组名称" to "默认分组",
                "首页显示" to "开启",
                "排序方式" to "最近收藏优先",
                "包含条目" to "Reader UI 前端输入件更新说明、阅读器路线图讨论摘要"
            )
        ) { (label, value) ->
            DemoFieldCard("收藏分组", label, value)
        }
    }
}

private data class RssArticle(val title: String, val source: String, val time: String, val group: String, val desc: String, val unread: Boolean, val starred: Boolean)

private fun rssArticles(): List<RssArticle> = listOf(
    RssArticle("Reader UI 前端输入件更新说明", "GitHub Releases", "10:18", "开源项目", "新增发现页状态路由、阅读控制层响应式约束，并补充 RSS 页面结构规划。", true, true),
    RssArticle("订阅源规则解析失败排查", "书源维护公告", "09:52", "维护", "部分订阅源返回 HTML 而不是 XML，已建议检查 Cookie、登录态和正文提取规则。", true, false),
    RssArticle("Legado 订阅源配置经验整理", "阅读器版本讨论", "昨天", "社区", "社区整理了单 URL 源、分类入口、文章样式和 WebView 正文处理的常见配置方式。", true, false),
    RssArticle("本地导入完成解析", "本地系统通知", "周二", "系统", "本地 OPML 导入完成，4 个订阅源已启用，1 个订阅源需要补全图标。", false, false),
    RssArticle("阅读器路线图讨论摘要", "阅读器版本讨论", "周一", "社区", "围绕 RSS 收藏、源分组、正文阅读和同步备份的交互关系做了讨论。", false, true)
)

// Book / Bookshelf ------------------------------------------------------------------------------

@Composable
private fun BookDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    when (page.id) {
        "bookshelf-empty" -> DemoMainTabShell(title = "书架", activeRoute = "bookshelf", onNavigate = onNavigate) {
            item { BookshelfEmptyHighFidelity(onNavigate) }
        }
        "sort-filter" -> DemoMainTabShell(title = "书架", activeRoute = "bookshelf", onNavigate = onNavigate) {
            item { ContinueReadingCard(onNavigate) }
            item { BookshelfFilterPopover(onNavigate) }
            item { BookFocusMenu(onNavigate) }
        }
        "book-directory" -> DemoLibraryShell(title = "书籍目录", onBack = onBack) {
            item { DemoHeroCard(R.drawable.reader_ic_book_open, "长夜余火", "爱潜水的乌贼 · 共 10 章") }
            item { DemoChipRow(listOf("目录" to "book-directory", "书签" to "book-directory"), "目录", onNavigate) }
            items(chapterRows()) { chapter -> ChapterRow(chapter, onNavigate) }
        }
        else -> DemoLibraryShell(
            title = "书籍详情",
            onBack = onBack,
            bottomActions = listOf(DemoRouteAction("继续阅读", "immersive-reading"), DemoRouteAction("移除书架", "book-detail")),
            onNavigate = onNavigate
        ) {
            item { BookDetailHero(onNavigate) }
            item { DemoTextBlock("简介", "灾变后的世界里，旧文明的回声仍在荒野中游荡。长夜之后，余火尚存，新的旅程从雨夜开始。") }
            item {
                DemoCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        DemoSectionLabel("章节信息")
                        DemoTextButton("完整目录") { onNavigate("book-directory") }
                    }
                    chapterRows().take(7).forEach { ChapterRowInline(it, onNavigate) }
                }
            }
        }
    }
}

@Composable
private fun BookshelfEmptyHighFidelity(onNavigate: (String) -> Unit) {
    DemoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("我的书架", style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoIcon(R.drawable.reader_ic_grid)
                DemoIcon(R.drawable.reader_ic_filter)
                DemoIcon(R.drawable.reader_ic_gear)
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DemoIconBox(R.drawable.reader_ic_bookshelf, size = 54.dp)
            Text("书架还是空的", style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground)
            Text("添加网络书籍或导入本地文件后，会在这里显示继续阅读和书架内容。", style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(14.dp))
        DemoActionGrid(
            listOf(
                DemoRouteAction("搜索书籍", "book-search"),
                DemoRouteAction("导入本地书", "local-import"),
                DemoRouteAction("去发现", "discover"),
                DemoRouteAction("书架设置", "bookshelf-search-settings")
            ),
            onNavigate
        )
    }
}

@Composable
private fun ContinueReadingCard(onNavigate: (String) -> Unit) {
    DemoCard(onClick = { onNavigate("immersive-reading") }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoCover("长夜余火", width = 70.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("继续阅读", style = ReaderTextStyles.continueLabel, color = MaterialTheme.colorScheme.primary)
                Text("长夜余火", style = ReaderTextStyles.continueTitle, color = MaterialTheme.colorScheme.onBackground)
                Text("爱潜水的乌贼", style = ReaderTextStyles.continueAuthor, color = readerExtraColors().muted)
            }
            DemoButton("阅读", compact = true, primary = true) { onNavigate("immersive-reading") }
        }
    }
}

@Composable
private fun BookshelfFilterPopover(onNavigate: (String) -> Unit) {
    DemoCard {
        DemoSectionLabel("分组")
        DemoChipRow(listOf("全部" to "sort-filter", "默认" to "sort-filter", "本地书" to "sort-filter", "追更" to "sort-filter"), "全部", onNavigate)
        Spacer(Modifier.height(8.dp))
        DemoSectionLabel("排序")
        DemoChipRow(listOf("最近更新" to "sort-filter", "阅读进度" to "sort-filter", "书名" to "sort-filter", "作者" to "sort-filter"), "最近更新", onNavigate)
        Spacer(Modifier.height(8.dp))
        DemoSectionLabel("筛选")
        DemoChipRow(listOf("全部" to "sort-filter", "未读" to "sort-filter", "已完结" to "sort-filter", "更新失败" to "sort-filter"), "全部", onNavigate)
    }
}

@Composable
private fun BookFocusMenu(onNavigate: (String) -> Unit) {
    DemoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoCover("长夜余火", width = 42.dp)
            Column(Modifier.weight(1f)) {
                Text("长夜余火", style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
                Text("爱潜水的乌贼 · 第 32 章 雨夜", style = denseMetaStyle(), color = readerExtraColors().muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        DemoActionGrid(
            listOf(
                DemoRouteAction("多选", "book-batch-management"),
                DemoRouteAction("分支", "group-management"),
                DemoRouteAction("书籍详情", "book-detail"),
                DemoRouteAction("删除", "sort-filter")
            ),
            onNavigate
        )
    }
}

@Composable
private fun BookDetailHero(onNavigate: (String) -> Unit) {
    DemoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DemoCover("长夜余火", width = 92.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("长夜余火", style = ReaderTextStyles.continueTitle, color = MaterialTheme.colorScheme.onBackground)
                Text("爱潜水的乌贼", style = denseMetaStyle(), color = readerExtraColors().muted)
                DemoFact("最新", "第 32 章 雨夜")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("书源：优书网", style = denseMetaStyle(), color = readerExtraColors().controlInk)
                    DemoTextButton("更换书源") { onNavigate("source-switch") }
                }
            }
        }
    }
}

private data class DemoChapter(val title: String, val marker: String = "", val current: Boolean = false)

private fun chapterRows(): List<DemoChapter> = listOf(
    DemoChapter("第 30 章 灰土", "已缓存"),
    DemoChapter("第 31 章 归途", "已缓存"),
    DemoChapter("第 32 章 雨夜", "书签", current = true),
    DemoChapter("第 33 章 灯塔"),
    DemoChapter("第 34 章 旧地图", "已缓存"),
    DemoChapter("第 35 章 夜行"),
    DemoChapter("第 36 章 灯塔之后", "书签")
)

@Composable
private fun ChapterRow(chapter: DemoChapter, onNavigate: (String) -> Unit) {
    DemoRowCard(
        iconRes = if (chapter.current) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_nav_list,
        title = chapter.title,
        meta = if (chapter.current) "当前章节" else chapter.marker.ifEmpty { "未读" },
        trailing = chapter.marker.ifEmpty { null },
        onClick = { onNavigate("immersive-reading") }
    )
}

@Composable
private fun ChapterRowInline(chapter: DemoChapter, onNavigate: (String) -> Unit) {
    DemoRow(
        iconRes = if (chapter.current) R.drawable.reader_ic_bookmark else R.drawable.reader_ic_nav_list,
        title = chapter.title,
        meta = if (chapter.current) "当前章节" else chapter.marker,
        trailing = chapter.marker.ifEmpty { null },
        onClick = { onNavigate("immersive-reading") }
    )
}

// Reader ----------------------------------------------------------------------------------------

@Composable
private fun ReaderDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit, onDispatch: (ReaderUiIntent) -> Unit = {}) {
    val paragraphs = page.body.drop(1).filter { it.length > 18 }.take(8).ifEmpty {
        listOf("雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerExtraColors().paper)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .readerPageSwipe(
                    onNextPage = { onDispatch(ReaderUiIntent.TurnPageNext()) },
                    onPrevPage = { onDispatch(ReaderUiIntent.TurnPagePrev()) }
                )
                .readerFontSizePinch { zoom -> /* 字号 pinch 暂不触发 intent，留空 */ },
            contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 78.dp, bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "雨夜",
                    style = ReaderTextStyles.readerChapterTitle,
                    color = readerExtraColors().readerInk,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(paragraphs) { paragraph ->
                Text(text = paragraph, style = ReaderTextStyles.readerBody, color = readerExtraColors().readerInk)
            }
        }
        ReaderInfoLayer(modifier = Modifier.align(Alignment.TopCenter))
        ReaderBottomPanel(page = page, onBack = onBack, onNavigate = onNavigate, onDispatch = onDispatch, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReaderInfoLayer(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("长夜余火 · 第 32 章 雨夜", style = ReaderTextStyles.infoLayer, color = readerExtraColors().infoLayer)
        Text("10:18", style = ReaderTextStyles.infoLayer, color = readerExtraColors().infoLayer)
    }
}

@Composable
private fun ReaderBottomPanel(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit, onDispatch: (ReaderUiIntent) -> Unit = {}, modifier: Modifier = Modifier) {
    val full = page.id.startsWith("reader-full")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), ReaderShapes.xl)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            DemoTextButton("返回") { onBack() }
            Text(readerPanelTitle(page.id), style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
            DemoTextButton(if (full) "收起" else "全屏") { onNavigate(readerFullTarget(page.id)) }
        }
        ReaderProgress(page.id, onDispatch)
        when (readerPanelKind(page.id)) {
            "directory" -> ReaderDirectoryPanel(full, onNavigate)
            "tts" -> ReaderTtsPanel(full, onNavigate, onDispatch)
            "appearance" -> ReaderAppearancePanel(full, onNavigate, onDispatch)
            "settings" -> ReaderSettingsPanel(full, onNavigate)
            "search" -> ReaderSearchPanel(onNavigate)
            "auto-page" -> ReaderAutoPagePanel(onNavigate)
            "replace" -> ReaderReplacePanel(onNavigate)
            "cache" -> ReaderCachePanel(onNavigate)
            "debug" -> ReaderDebugPanel()
            "source" -> SourceSwitchPanel(onNavigate)
            else -> ReaderModuleDock(onNavigate)
        }
    }
}

@Composable
private fun ReaderProgress(routeId: String, onDispatch: (ReaderUiIntent) -> Unit = {}) {
    DemoCard(alpha = 0.42f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoTextButton("上一章") { onDispatch(ReaderUiIntent.TurnPagePrev()) }
            Box(Modifier.weight(1f).height(5.dp).background(readerExtraColors().hairline, ReaderShapes.pill)) {
                Box(Modifier.fillMaxWidth(0.38f).height(5.dp).background(MaterialTheme.colorScheme.primary, ReaderShapes.pill))
            }
            DemoTextButton("下一章") { onDispatch(ReaderUiIntent.TurnPageNext()) }
        }
        Text(if (routeId == "auto-page") "自动翻页 · 8 秒" else "38% · 第 1 / 3 页", style = denseMetaStyle(), color = readerExtraColors().muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReaderModuleDock(onNavigate: (String) -> Unit) {
    DemoActionGrid(
        listOf(
            DemoRouteAction("目录", "toc-bookmarks"),
            DemoRouteAction("朗读", "tts"),
            DemoRouteAction("界面", "reader-appearance"),
            DemoRouteAction("设置", "reader-settings"),
            DemoRouteAction("自动翻页", "auto-page"),
            DemoRouteAction("替换", "content-replacement")
        ),
        onNavigate
    )
}

@Composable
private fun ReaderDirectoryPanel(full: Boolean, onNavigate: (String) -> Unit) {
    DemoChipRow(listOf("目录" to "toc-bookmarks", "书签" to "toc-bookmarks"), "目录", onNavigate)
    chapterRows().take(if (full) 7 else 3).forEach { ChapterRowInline(it, onNavigate) }
}

@Composable
private fun ReaderTtsPanel(full: Boolean, onNavigate: (String) -> Unit, onDispatch: (ReaderUiIntent) -> Unit = {}) {
    DemoRow(R.drawable.reader_ic_book_open, "声音", "系统女声 · 1.0x", "朗读")
    DemoRow(R.drawable.reader_ic_clock, "当前句", "雨声在窗外连成一片", if (full) "第 1 句" else null)
    DemoActionGrid(listOf(
        DemoRouteAction("开始", "tts", onStart = { onDispatch(ReaderUiIntent.StartTtsSession(text = "雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。")) }),
        DemoRouteAction("暂停", "tts", onStart = { onDispatch(ReaderUiIntent.ToggleSessionPlaying) }),
        DemoRouteAction("停止", "reader", onStart = { onDispatch(ReaderUiIntent.StopSession) })
    ), onNavigate)
}

@Composable
private fun ReaderAppearancePanel(full: Boolean, onNavigate: (String) -> Unit, onDispatch: (ReaderUiIntent) -> Unit = {}) {
    DemoSectionLabel("主题")
    // 读者主题色板：使用 ReaderThemeResolver.DAY_SWATCHES 的真实日间 swatch 颜色
    // （paper=#F5EAD8 / warm=#FBF0DF / green=#E7F0E2 / blue=#E9F1F4）。
    val extra = readerExtraColors()
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ReaderThemeResolver.DAY_SWATCHES.forEach { swatch ->
            Box(
                Modifier
                    .size(if (full) 34.dp else 24.dp)
                    .background(swatch.color, ReaderShapes.sm)
                    .border(1.dp, extra.hairline, ReaderShapes.sm)
                    .clickable {
                        onDispatch(ReaderUiIntent.UpdateReaderTheme(themeId = swatch.id))
                        // 绑定 segment.item.switch 动效。
                        MotionController.start(
                            motionId = "segment.item.switch",
                            from = "segment.previous",
                            to = "segment.next",
                            durationMs = 120L,
                            reducedMotion = MotionController.reducedFrom(null)
                        )
                    }
            )
        }
    }
    DemoRow(R.drawable.reader_ic_settings, "字号", "18", "+")
    DemoRow(R.drawable.reader_ic_columns, "行距", "1.96", "+")
    DemoChipRow(listOf("宋体" to "reader-appearance", "黑体" to "reader-appearance", "系统" to "reader-appearance"), "宋体", onNavigate)
}

@Composable
private fun ReaderSettingsPanel(full: Boolean, onNavigate: (String) -> Unit) {
    DemoRow(R.drawable.reader_ic_refresh, "自动翻页", "关闭", "开启")
    DemoRow(R.drawable.reader_ic_replace, "内容替换", "4 条规则", "管理")
    DemoRow(R.drawable.reader_ic_download, "本书缓存", "31/36 章", "缓存")
    if (full) DemoRow(R.drawable.reader_ic_bug, "调试信息", "章节、分页与排版状态", "查看")
    DemoActionGrid(listOf(DemoRouteAction("本书缓存", "reader-book-cache"), DemoRouteAction("调试信息", "reader-debug-info")), onNavigate)
}

@Composable
private fun ReaderSearchPanel(onNavigate: (String) -> Unit) {
    DemoSearchBar("搜索当前章节内容")
    DemoRow(R.drawable.reader_ic_search, "雨夜", "第 32 章 · 4 处结果", "定位")
    DemoButton("返回阅读", primary = true) { onNavigate("reader") }
}

@Composable
private fun ReaderAutoPagePanel(onNavigate: (String) -> Unit) {
    DemoRow(R.drawable.reader_ic_refresh, "自动翻页", "8 秒 · 运行中", "暂停")
    DemoRow(R.drawable.reader_ic_clock, "倒计时", "8", "调整")
    DemoActionGrid(listOf(DemoRouteAction("暂停", "auto-page"), DemoRouteAction("停止", "reader")), onNavigate)
}

@Composable
private fun ReaderReplacePanel(onNavigate: (String) -> Unit) {
    listOf("雨容称呼", "旧称统一", "标点清理", "广告过滤").forEachIndexed { index, label ->
        DemoRow(R.drawable.reader_ic_replace, label, if (index == 2) "未启用" else "已启用", if (index == 2) "关闭" else "开启")
    }
    DemoButton("返回阅读", primary = true) { onNavigate("reader") }
}

@Composable
private fun ReaderCachePanel(onNavigate: (String) -> Unit) {
    DemoRow(R.drawable.reader_ic_download, "已缓存", "31 章", "查看")
    DemoRow(R.drawable.reader_ic_refresh, "正在缓存", "第 34 章 旧地图", "暂停")
    DemoRow(R.drawable.reader_ic_warning, "未缓存", "5 章", "下载")
    DemoButton("返回设置", primary = true) { onNavigate("reader-full-settings") }
}

@Composable
private fun ReaderDebugPanel() {
    DemoRow(R.drawable.reader_ic_bug, "分页模式", "fallback · 第 1 / 3 页", null)
    DemoRow(R.drawable.reader_ic_code, "章节索引", "32 / 36 · progress 38%", null)
    DemoRow(R.drawable.reader_ic_info, "控制层", "ReaderShell · module=settings", null)
}

@Composable
private fun SourceSwitchPanel(onNavigate: (String) -> Unit) {
    DemoSectionLabel("换源")
    listOf("优书网", "书仓搜索", "本地缓存").forEachIndexed { index, name ->
        DemoRow(R.drawable.reader_ic_source_stack, name, if (index == 0) "当前源 · 120ms" else "可切换", if (index == 0) "当前" else "选择")
    }
    DemoButton("确认换源", primary = true) { onNavigate("reader") }
}

private fun readerPanelKind(routeId: String): String = when (routeId) {
    "toc-bookmarks", "reader-full-directory" -> "directory"
    "tts", "reader-full-tts" -> "tts"
    "reader-appearance", "reader-full-appearance" -> "appearance"
    "reader-settings", "reader-full-settings" -> "settings"
    "content-search" -> "search"
    "auto-page" -> "auto-page"
    "content-replacement" -> "replace"
    "reader-book-cache" -> "cache"
    "reader-debug-info" -> "debug"
    "source-switch" -> "source"
    else -> "dock"
}

private fun readerPanelTitle(routeId: String): String = when (readerPanelKind(routeId)) {
    "directory" -> "目录"
    "tts" -> "朗读"
    "appearance" -> "界面"
    "settings" -> "设置"
    "search" -> "内容搜索"
    "auto-page" -> "自动翻页"
    "replace" -> "替换"
    "cache" -> "本书缓存"
    "debug" -> "调试信息"
    "source" -> "换源"
    else -> "阅读控制"
}

private fun readerFullTarget(routeId: String): String = when (readerPanelKind(routeId)) {
    "directory" -> "reader-full-directory"
    "tts" -> "reader-full-tts"
    "appearance" -> "reader-full-appearance"
    "settings" -> "reader-full-settings"
    else -> routeId
}

// Source / Restore ------------------------------------------------------------------------------

@Composable
private fun SourceDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    when (page.id) {
        "source-groups" -> SourceGroupsScreen(onBack, onNavigate)
        "source-detail" -> SourceDetailScreen(onBack, onNavigate)
        "source-detect", "source-debug", "source-debug-search-result", "source-debug-detail-result", "source-debug-catalog-result", "source-debug-content-log", "source-code-view" ->
            SourceDebugScreen(page, onBack, onNavigate)
        "source-rule-edit", "source-edit-debug" -> SourceRuleLikeScreen(
            title = "规则编辑",
            onBack = onBack,
            onNavigate = onNavigate,
            heroTitle = "笔趣阁",
            heroMeta = "biquge.example · 玄幻书源",
            fields = listOf(
                Triple("基础", "源名称", "笔趣阁"),
                Triple("基础", "源地址", "https://biquge.example"),
                Triple("请求", "请求方式", "GET"),
                Triple("正文", "正文内容规则", "#content@text")
            ),
            actions = listOf(DemoRouteAction("保存规则", "source-management"), DemoRouteAction("调测当前模块", "source-debug"))
        )
        "source-logs" -> SourceLogsScreen(onBack, onNavigate)
        "source-delete-confirm" -> DemoConfirmScreen("删除书源", "删除已选书源？", "将从书源管理中删除已选 3 个书源，书架和阅读记录不会被静默删除。", onBack, onNavigate, "source-management")
        else -> SourceListLikeScreen(page, onBack, onNavigate)
    }
}

@Composable
private fun SourceListLikeScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val batch = page.id == "source-batch"
    DemoLibraryShell(
        title = if (batch) "已选 3 个" else "添加书源",
        onBack = onBack,
        bottomActions = if (batch) listOf(DemoRouteAction("分组", "source-groups"), DemoRouteAction("删除", "source-delete-confirm")) else emptyList(),
        onNavigate = onNavigate
    ) {
        item { DemoSearchBar("搜索书源名称或域名") }
        item { DemoInlineNotice("12 个书源 · 8 个启用 · 4 个异常 · 10:30 检测", R.drawable.reader_ic_source_stack) }
        item { DemoChipRow(listOf("全部" to "source-import-options", "全部分组" to "source-import-options", "异常" to "source-logs"), "全部", onNavigate) }
        items(sourceRows()) { source ->
            DemoRowCard(
                iconRes = if (batch && source.selected) R.drawable.reader_ic_check else R.drawable.reader_ic_source_stack,
                title = source.name,
                meta = source.meta,
                trailing = source.status,
                onClick = { onNavigate("source-detail") }
            )
        }
        if (!batch) {
            item {
                DemoActionGrid(
                    listOf(
                        DemoRouteAction("网络导入", "source-import-preview"),
                        DemoRouteAction("本地导入", "source-import-preview"),
                        DemoRouteAction("剪贴板导入", "source-import-preview"),
                        DemoRouteAction("手动新建", "source-rule-edit")
                    ),
                    onNavigate
                )
            }
        }
    }
}

@Composable
private fun SourceGroupsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "分组管理", onBack = onBack) {
        item { DemoInlineNotice("分组用于筛选和批量整理书源，删除分组不会删除书源。", R.drawable.reader_ic_folder) }
        items(listOf("全部分组" to "12 个书源", "玄幻书源" to "4 个书源 · 当前筛选", "起点导入" to "3 个书源", "测试书源" to "2 个书源", "自定义" to "2 个书源", "未分组" to "1 个书源")) { (name, meta) ->
            DemoRowCard(R.drawable.reader_ic_folder, name, meta, if (name == "玄幻书源") "当前筛选" else null)
        }
        item { DemoActionGrid(listOf(DemoRouteAction("批量移动", "source-batch"), DemoRouteAction("新增分组", "source-groups")), onNavigate) }
    }
}

@Composable
private fun SourceDetailScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "书源详情", onBack = onBack) {
        item { DemoHeroCard(R.drawable.reader_ic_source_stack, "笔趣阁", "biquge.example · 玄幻书源 · 异常 · 最近检测 10:30 · 规则版本 3", "异常") }
        item { DemoChipRow(listOf("站点" to "source-detail", "详情" to "source-detail", "目录" to "source-detail", "正文" to "source-detail", "登录" to "source-detail"), "正文", onNavigate) }
        item { DemoStateCard(R.drawable.reader_ic_warning, "最近检测结果", "搜索、详情、目录均可解析；正文模块失败。失败规则：正文内容规则“#content@text”返回空内容。建议进入规则编辑后调测正文模块。") }
        item {
            DemoActionGrid(
                listOf(
                    DemoRouteAction("复制书源", "source-detail"),
                    DemoRouteAction("导出书源", "source-detail"),
                    DemoRouteAction("检测此源", "source-detect"),
                    DemoRouteAction("编辑规则", "source-rule-edit"),
                    DemoRouteAction("删除", "source-delete-confirm")
                ),
                onNavigate
            )
        }
    }
}

@Composable
private fun SourceDebugScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = page.cleanTitle(), onBack = onBack, bottomActions = listOf(DemoRouteAction("重新调测", page.id), DemoRouteAction("回到编辑", "source-rule-edit")), onNavigate = onNavigate) {
        item { DemoHeroCard(R.drawable.reader_ic_bug, "笔趣阁", "正文模块 · 当前请求返回 · /book/123/128.html") }
        item { DemoChipRow(listOf("搜索" to "source-debug-search-result", "详情" to "source-debug-detail-result", "目录" to "source-debug-catalog-result", "正文" to "source-debug", "源码" to "source-code-view", "日志" to "source-debug-content-log"), if (page.id == "source-code-view") "源码" else if (page.id == "source-debug-content-log") "日志" else "正文", onNavigate) }
        item {
            DemoCard {
                DemoRow(R.drawable.reader_ic_check, "搜索", "关键词 -> 结果列表", "通过")
                DemoDivider()
                DemoRow(R.drawable.reader_ic_check, "详情", "详情 URL -> 书籍字段", "通过")
                DemoDivider()
                DemoRow(R.drawable.reader_ic_check, "目录", "目录 URL -> 章节列表", "通过")
                DemoDivider()
                DemoRow(R.drawable.reader_ic_warning, "正文", "章节 URL -> 正文文本", "失败")
            }
        }
        item { DemoTextBlock("解析结果", "可尝试将正文内容规则改为“.chapter-content@text”后重新调测。") }
    }
}

@Composable
private fun SourceRuleLikeScreen(
    title: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    heroTitle: String,
    heroMeta: String,
    fields: List<Triple<String, String, String>>,
    actions: List<DemoRouteAction>
) {
    DemoLibraryShell(title = title, onBack = onBack, bottomActions = actions, onNavigate = onNavigate) {
        item { DemoHeroCard(R.drawable.reader_ic_edit, heroTitle, heroMeta, "已启用") }
        item { DemoChipRow(listOf("基础" to "source-rule-edit", "搜索" to "source-debug-search-result", "详情" to "source-debug-detail-result", "目录" to "source-debug-catalog-result", "正文" to "source-debug"), "基础", onNavigate) }
        items(fields) { (group, label, value) -> DemoFieldCard(group, label, value) }
        item { DemoInlineNotice("规则修改后先调测当前模块，确认解析结果正常后再保存。", R.drawable.reader_ic_info) }
    }
}

@Composable
private fun SourceLogsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(title = "错误日志", onBack = onBack) {
        item { DemoSearchBar("搜索书源或错误内容") }
        item { DemoChipRow(listOf("全部" to "source-logs", "异常" to "source-logs", "警告" to "source-logs", "今日" to "source-logs"), "全部", onNavigate) }
        items(listOf("笔趣阁 · ERROR" to "10:30 · 正文 · 正文规则返回空内容", "旧规则源 · ERROR" to "10:22 · 搜索 · HTTP 403", "本地导入源 · WARN" to "09:50 · 目录 · 尚未检测", "失效示例源 · ERROR" to "昨天 · 详情 · 详情页 URL 为空")) { (title, meta) ->
            DemoRowCard(R.drawable.reader_ic_warning, title, meta, null)
        }
        item { DemoActionGrid(listOf(DemoRouteAction("复制全部", "source-logs"), DemoRouteAction("重新检测异常", "source-detect"), DemoRouteAction("清空", "source-delete-confirm")), onNavigate) }
    }
}

private data class SourceRow(val name: String, val meta: String, val status: String, val selected: Boolean)

private fun sourceRows(): List<SourceRow> = listOf(
    SourceRow("起点中文网", "qidian.com · 起点导入", "正常", true),
    SourceRow("笔趣阁", "biquge.example · 玄幻书源", "异常", true),
    SourceRow("本地导入源", "本地文件导入 · 自定义", "待检测", true),
    SourceRow("测试书源", "test.example · 测试书源", "正常", false),
    SourceRow("轻小说文库", "lightnovel.example · 测试书源", "需登录", false),
    SourceRow("旧规则源", "old.example · 自定义", "异常", false),
    SourceRow("飞卢小说网", "faloo.com · 玄幻书源", "正常", false),
    SourceRow("晋江文学城", "jjwx.example · 起点导入", "正常", false),
    SourceRow("纵横中文网", "zongheng.com · 玄幻书源", "正常", false),
    SourceRow("豆瓣阅读", "read.douban.com · 自定义", "正常", false),
    SourceRow("失效示例源", "dead.example · 测试书源", "异常", false)
)

@Composable
private fun RestoreDemoScreen(page: DemoRoutePage, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    DemoLibraryShell(
        title = page.cleanTitle(),
        onBack = onBack,
        bottomActions = when (page.id) {
            "restore-confirm" -> listOf(DemoRouteAction("开始恢复", "restore-progress"))
            "restore-progress" -> listOf(DemoRouteAction("处理冲突", "restore-conflict"), DemoRouteAction("查看结果", "restore-result"))
            "restore-conflict" -> listOf(DemoRouteAction("进度", "restore-progress"), DemoRouteAction("应用选择", "restore-result"))
            else -> listOf(DemoRouteAction("同步页", "sync-backup"))
        },
        onNavigate = onNavigate
    ) {
        item { DemoHeroCard(R.drawable.reader_ic_sync, page.cleanTitle(), "WebDAV · 2026-06-23 08:00 · 完整备份") }
        when (page.id) {
            "restore-confirm" -> {
                item { DemoTextBlock("确认恢复数据", "将使用选中的备份覆盖本机同类数据。恢复前会创建本地快照，取消不会改变当前数据。") }
                items(listOf("书架与分组" to "恢复书架书籍、分组和排序", "阅读进度" to "恢复章节位置和阅读进度", "阅读与 App 设置" to "恢复主题、排版和通用设置", "书源配置" to "恢复书源、分组和启用状态")) { (title, meta) -> DemoRowCard(R.drawable.reader_ic_check, title, meta, "已选") }
                item { DemoInlineNotice("冲突项会在恢复过程中单独确认，不会静默覆盖。", R.drawable.reader_ic_warning) }
            }
            "restore-progress" -> items(listOf("下载备份" to "12.8 MB · WebDAV", "校验文件" to "manifest、hash、版本兼容", "合并数据" to "书架 128 本 · 进度 96 条", "写入设置" to "等待合并完成")) { (title, meta) -> DemoRowCard(R.drawable.reader_ic_refresh, title, meta, if (title == "合并数据") "进行中" else "完成") }
            "restore-conflict" -> items(listOf("分组：玄幻连载" to "本地 42 本 · 远程 46 本", "阅读进度：长夜余火" to "本地第 32 章 · 远程第 35 章", "阅读设置：浅色主题" to "本地字号 18 · 远程字号 17")) { (title, meta) -> DemoRowCard(R.drawable.reader_ic_warning, title, meta, "选择") }
            else -> {
                item { DemoStateCard(R.drawable.reader_ic_check, "恢复完成", "书架、分组和阅读进度已恢复。1 条书源配置因版本不兼容被跳过，可在日志中查看详情。") }
                items(listOf("128 本" to "书架与分组", "12 个" to "分组", "96 条" to "阅读进度", "1 条" to "书源配置跳过")) { (title, meta) -> DemoRowCard(R.drawable.reader_ic_check, title, meta, null) }
            }
        }
    }
}

@Composable
private fun DemoConfirmScreen(title: String, heading: String, body: String, onBack: () -> Unit, onNavigate: (String) -> Unit, confirmRoute: String) {
    DemoLibraryShell(title = title, onBack = onBack, bottomActions = listOf(DemoRouteAction("取消", confirmRoute), DemoRouteAction("确认", confirmRoute)), onNavigate = onNavigate) {
        item {
            DemoStateCard(
                iconRes = R.drawable.reader_ic_warning,
                title = heading,
                body = body,
                actions = listOf(DemoRouteAction("取消", confirmRoute), DemoRouteAction("确认", confirmRoute)),
                onNavigate = onNavigate
            )
        }
    }
}

// Common components -----------------------------------------------------------------------------

@Composable
private fun DemoCard(
    modifier: Modifier = Modifier,
    alpha: Float = 0.78f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha), ReaderShapes.lg)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.lg)
            .then(clickable)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun DemoHeroCard(@DrawableRes iconRes: Int, title: String, meta: String, badge: String? = null) {
    DemoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoIconBox(iconRes, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = denseTitleStyle().copy(fontSize = 17.sp), color = MaterialTheme.colorScheme.onBackground)
                Text(meta, style = denseMetaStyle(), color = readerExtraColors().muted)
            }
            if (badge != null) DemoBadge(badge)
        }
    }
}

@Composable
private fun DemoStateCard(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    actions: List<DemoRouteAction> = emptyList(),
    onNavigate: (String) -> Unit = {}
) {
    DemoCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DemoIconBox(iconRes, size = 50.dp)
            Text(title, style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text(body, style = ReaderTextStyles.emptyBody, color = readerExtraColors().muted, textAlign = TextAlign.Center)
        }
        if (actions.isNotEmpty()) {
            DemoActionGrid(actions, onNavigate)
        }
    }
}

@Composable
private fun DemoTextBlock(title: String, body: String) {
    DemoCard {
        DemoSectionLabel(title)
        Text(body, style = denseBodyStyle(), color = readerExtraColors().controlInk)
    }
}

@Composable
private fun DemoFieldCard(group: String, label: String, value: String) {
    DemoCard {
        Text(group, style = tinyStrongStyle(), color = MaterialTheme.colorScheme.primary)
        Text(label, style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
        Text(value, style = denseBodyStyle(), color = readerExtraColors().controlInk)
    }
}

@Composable
private fun DemoRowCard(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    trailing: String?,
    onClick: (() -> Unit)? = null
) {
    DemoCard(onClick = onClick) {
        DemoRow(iconRes, title, meta, trailing)
    }
}

@Composable
private fun DemoRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .then(clickable),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DemoIcon(iconRes, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, style = denseMetaStyle(), color = readerExtraColors().muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) DemoBadge(trailing)
    }
}

@Composable
private fun DemoActionGrid(actions: List<DemoRouteAction>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    DemoButton(action.label, modifier = Modifier.weight(1f)) {
                        if (action.onStart != null) action.onStart!!()
                        else onNavigate(action.targetRoute)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DemoButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    compact: Boolean = false,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) 34.dp else 44.dp)
            .background(if (primary) readerExtraColors().primaryDark else colors.surface.copy(alpha = 0.8f), ReaderShapes.pill)
            .border(1.dp, if (primary) Color.Transparent else readerExtraColors().hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            DemoIcon(iconRes, size = 16.dp, tint = if (primary) colors.onPrimary else colors.primary)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = if (compact) tinyStrongStyle() else denseButtonStyle(),
            color = if (primary) colors.onPrimary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DemoTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = tinyStrongStyle(),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    )
}

@Composable
private fun DemoChipRow(items: List<Pair<String, String>>, active: String, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items.forEach { (label, route) ->
            DemoChip(label, active = label == active || active.startsWith(label)) { onNavigate(route) }
        }
    }
}

@Composable
private fun DemoChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = label,
        style = tinyStrongStyle(),
        color = if (active) colors.onPrimary else readerExtraColors().controlInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(if (active) readerExtraColors().primaryDark else colors.surface.copy(alpha = 0.74f), ReaderShapes.pill)
            .border(1.dp, if (active) Color.Transparent else readerExtraColors().hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    )
}

@Composable
private fun DemoSearchBar(placeholder: String) {
    DemoCard(alpha = 0.62f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoIcon(R.drawable.reader_ic_search, tint = readerExtraColors().muted)
            Text(placeholder, style = denseMetaStyle(), color = readerExtraColors().muted)
        }
    }
}

@Composable
private fun DemoInlineNotice(text: String, @DrawableRes iconRes: Int) {
    DemoCard(alpha = 0.56f) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoIcon(iconRes, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
            Text(text, style = denseMetaStyle().copy(fontWeight = FontWeight(750)), color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
private fun DemoSectionLabel(label: String) {
    Text(label, style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun DemoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline)
    )
}

@Composable
private fun DemoBadge(label: String) {
    Text(
        text = label,
        style = tinyStrongStyle(),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun DemoIcon(@DrawableRes iconRes: Int, size: Dp = 20.dp, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Icon(painter = painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun DemoIconBox(@DrawableRes iconRes: Int, size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.md),
        contentAlignment = Alignment.Center
    ) {
        DemoIcon(iconRes, size = size * 0.52f, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DemoCover(title: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(4),
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = ReaderTypeToken.SECTION_TITLE.value, lineHeight = 18.sp, fontWeight = FontWeight(700)),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DemoFact(label: String, value: String) {
    Column(
        modifier = Modifier
            .background(readerExtraColors().metaBackground, ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = tinyStrongStyle(), color = readerExtraColors().muted)
        Text(value, style = denseTitleStyle(), color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun DemoSkeletonRow() {
    DemoCard(alpha = 0.42f) {
        Box(Modifier.fillMaxWidth(0.72f).height(14.dp).background(readerExtraColors().hairline, ReaderShapes.pill))
        Box(Modifier.fillMaxWidth(0.50f).height(11.dp).background(readerExtraColors().hairline, ReaderShapes.pill))
        Box(Modifier.fillMaxWidth(0.88f).height(11.dp).background(readerExtraColors().hairline, ReaderShapes.pill))
    }
}

private fun denseTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.SECTION_TITLE.value,
    lineHeight = 19.sp,
    fontWeight = FontWeight(800)
)

private fun denseButtonStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 17.sp,
    fontWeight = FontWeight(800)
)

private fun denseMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.BOOK_META.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(550)
)

private fun denseBodyStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 20.sp,
    fontWeight = FontWeight(500)
)

private fun tinyStrongStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(800)
)
