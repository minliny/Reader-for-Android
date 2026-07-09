package com.reader.ui.discover

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.reader.android.R
import com.reader.ui.demo.demoCoverDrawableRes
import com.reader.ui.shell.LibraryDialogHostSlot
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.shell.LibraryShellStatusBarSlot
import com.reader.ui.shell.LibrarySheetHostSlot
import com.reader.ui.shell.LibraryStateHostSlot
import com.reader.ui.shell.OverlayState
import com.reader.ui.shell.SettingsDialogHostSlot
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.shell.SettingsShellStatusBarSlot
import com.reader.ui.shell.SettingsSheetHostSlot
import com.reader.ui.shell.SettingsStateHostSlot
import com.reader.ui.shell.SettingsToastHostSlot
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken
import coil.compose.AsyncImage

enum class DiscoverDemoRouteShell {
    Auto,
    Library,
    Settings
}

@Composable
fun DiscoverDemoRouteScreen(
    routeId: String,
    shell: DiscoverDemoRouteShell = DiscoverDemoRouteShell.Auto,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenBook: (sourceId: String, bookUrl: String, bookName: String) -> Unit,
    overlayState: OverlayState = OverlayState.None
) {
    val state = remember(routeId) {
        discoverDemoRouteState(routeId) ?: discoverDemoRouteState(DiscoverDemoRouteIds.CONTROL)!!
    }
    when (state.page) {
        DiscoverDemoPage.Main -> DiscoverDemoMainRouteScreen(
            state = state,
            onNavigate = onNavigate,
            onOpenBook = onOpenBook
        )
        DiscoverDemoPage.SourceLogin -> DiscoverSourceLoginRouteScreen(
            state = state,
            shell = shell,
            onBack = onBack,
            onNavigate = onNavigate,
            overlayState = overlayState
        )
        DiscoverDemoPage.RuleTest -> DiscoverRuleTestRouteScreen(
            state = state,
            shell = shell,
            onBack = onBack,
            onNavigate = onNavigate,
            overlayState = overlayState
        )
        DiscoverDemoPage.SourceBulk -> DiscoverSourceBulkRouteScreen(
            state = state,
            shell = shell,
            onBack = onBack,
            onNavigate = onNavigate,
            overlayState = overlayState
        )
    }
}

@Composable
private fun DiscoverDemoMainRouteScreen(
    state: DiscoverDemoRouteState,
    onNavigate: (String) -> Unit,
    onOpenBook: (sourceId: String, bookUrl: String, bookName: String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            DiscoverRouteTopBar(
                title = state.title,
                onRefresh = { onNavigate(DiscoverDemoRouteIds.REFRESHING) }
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 118.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.toast != null) {
                    item { DiscoverToast(text = state.toast) }
                }
                when (state.mainMode) {
                    DiscoverDemoMainMode.Empty -> item {
                        DiscoverRouteStateCard(
                            iconRes = R.drawable.reader_ic_source_stack,
                            message = state.message!!,
                            onNavigate = onNavigate,
                            minHeight = 360
                        )
                    }
                    DiscoverDemoMainMode.Error -> {
                        item { DiscoverRouteSourceBar(state = state, onNavigate = onNavigate) }
                        item {
                            DiscoverRouteStateCard(
                                iconRes = R.drawable.reader_ic_warning,
                                message = state.message!!,
                                onNavigate = onNavigate
                            )
                        }
                        item {
                            DiscoverRouteBookList(
                                books = state.books,
                                muted = true,
                                onOpenBook = onOpenBook
                            )
                        }
                    }
                    DiscoverDemoMainMode.NoResults,
                    DiscoverDemoMainMode.Content -> {
                        item { DiscoverRouteSourceBar(state = state, onNavigate = onNavigate) }
                        if (state.controlExpanded) {
                            item { DiscoverRouteControlPanel(state = state, onNavigate = onNavigate) }
                        } else {
                            item { DiscoverRouteEntryChips(state = state, onNavigate = onNavigate) }
                            item { DiscoverRouteFilterControl(state = state, onNavigate = onNavigate) }
                        }
                        if (state.refreshingText != null) {
                            item { DiscoverRefreshLine(text = state.refreshingText) }
                        }
                        if (state.mainMode == DiscoverDemoMainMode.NoResults) {
                            item {
                                DiscoverRouteStateCard(
                                    iconRes = R.drawable.reader_ic_search,
                                    message = state.message!!,
                                    onNavigate = onNavigate
                                )
                            }
                        } else {
                            item { DiscoverResultHeader(state = state) }
                            if (state.loading) {
                                item { DiscoverSkeletonList() }
                            } else {
                                item {
                                    DiscoverRouteBookList(
                                        books = state.books,
                                        muted = state.mutedResults,
                                        onOpenBook = onOpenBook
                                    )
                                }
                            }
                            if (state.infiniteLoading) {
                                item { DiscoverRefreshLine(text = "正在加载下一页") }
                            }
                            if (state.pageTwo) {
                                item {
                                    DiscoverBackTopButton(onClick = { onNavigate("discover") })
                                }
                            }
                        }
                    }
                }
            }
        }
        state.dialog?.let { dialog ->
            DiscoverConfirmDialog(
                dialog = dialog,
                onNavigate = onNavigate,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY))
            )
        }
    }
}

@Composable
private fun DiscoverRouteTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailingLabel: String? = null,
    onTrailing: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null
) {
    // Mirrors demo `fd-back-bar` grid (44px 1fr 44px) with start=20dp, gap=10dp, backBarTitle.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            DiscoverIconButton(
                iconRes = R.drawable.reader_ic_chevron_left,
                contentDescription = "返回",
                onClick = onBack
            )
        } else {
            Spacer(Modifier.size(44.dp))
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingLabel != null && onTrailing != null) {
            DiscoverRoutePillButton(text = trailingLabel, onClick = onTrailing, primary = false)
        }
        if (onRefresh != null) {
            DiscoverIconButton(
                iconRes = R.drawable.reader_ic_refresh,
                contentDescription = "刷新",
                onClick = onRefresh
            )
        }
        if (trailingLabel == null && onRefresh == null) {
            Spacer(Modifier.size(44.dp))
        }
    }
}

@Composable
private fun DiscoverRouteSourceBar(state: DiscoverDemoRouteState, onNavigate: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val target = if (state.controlExpanded) "discover" else DiscoverDemoRouteIds.CONTROL
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = if (state.controlExpanded) 1f else 0.92f), ReaderShapes.md)
            .border(1.dp, if (state.controlExpanded) colors.primary.copy(alpha = 0.30f) else extra.hairline, ReaderShapes.md)
            .clickable { onNavigate(target) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DiscoverRouteIconBox(iconRes = R.drawable.reader_ic_source_stack, size = 34)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = state.source.name,
                style = discoverRouteTitleStyle().copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight(850)),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.source.meta,
                style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(650)),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DiscoverRouteEntryChips(state: DiscoverDemoRouteState, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        state.entries.forEach { entry ->
            DiscoverRouteChip(
                text = entry,
                active = entry == state.activeEntry,
                onClick = { onNavigate(discoverEntryRouteForLabel(entry)) }
            )
        }
    }
}

@Composable
private fun DiscoverRouteFilterControl(state: DiscoverDemoRouteState, onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 36.dp)
                .clickable { onNavigate(DiscoverDemoRouteIds.SORT) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_filter),
                contentDescription = null,
                tint = readerExtraColors().controlInk,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "筛选",
                style = discoverRouteTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${state.activeFilter} · ${state.activeSort}",
                style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(800)),
                color = readerExtraColors().muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron),
                contentDescription = null,
                tint = readerExtraColors().muted,
                modifier = Modifier.size(18.dp)
            )
        }
        if (state.sortOpen) {
            DiscoverFilterGroup(
                title = "范围",
                options = listOf("关键词", "男频", "女频"),
                active = state.activeFilter,
                routeFor = ::discoverFilterRouteForLabel,
                onNavigate = onNavigate
            )
            DiscoverFilterGroup(
                title = "排序",
                options = listOf("人气", "更新", "收藏", "完本", "字数"),
                active = state.activeSort,
                routeFor = ::discoverSortRouteForLabel,
                onNavigate = onNavigate
            )
            DiscoverRoutePillButton(
                text = "应用",
                iconRes = R.drawable.reader_ic_check,
                primary = true,
                onClick = { onNavigate(DiscoverDemoRouteIds.REFRESHING) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiscoverFilterGroup(
    title: String,
    options: List<String>,
    active: String,
    routeFor: (String) -> String,
    onNavigate: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(900)),
            color = readerExtraColors().muted
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            options.forEach { option ->
                DiscoverRouteFilterButton(
                    text = option,
                    active = option == active,
                    iconRes = if (option == "关键词") R.drawable.reader_ic_search else null,
                    onClick = { onNavigate(routeFor(option)) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverRouteControlPanel(state: DiscoverDemoRouteState, onNavigate: (String) -> Unit) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DiscoverControlSection(title = "当前书源") {
            listOf(
                DiscoverDemoSourceRowState(
                    "优书网",
                    "默认 · 120ms",
                    DiscoverDemoTone.Good,
                    state.source.name == "优书网"
                ),
                DiscoverDemoSourceRowState(
                    "起点导入",
                    if (state.controlMode == DiscoverDemoControlMode.Switching) "正在解析入口" else "正版 · 180ms",
                    if (state.controlMode == DiscoverDemoControlMode.Switching) DiscoverDemoTone.Loading else DiscoverDemoTone.Good,
                    state.source.name == "起点导入"
                ),
                DiscoverDemoSourceRowState("轻小说文库", "需登录", DiscoverDemoTone.Warn, false),
                DiscoverDemoSourceRowState("本地聚合源", "维护中", DiscoverDemoTone.Muted, false)
            ).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { source ->
                        DiscoverSourceOption(
                            source = source,
                            onClick = {
                                onNavigate(
                                    if (source.name == "起点导入") {
                                        DiscoverDemoRouteIds.SWITCHING_SOURCE
                                    } else {
                                        DiscoverDemoRouteIds.CONTROL
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        DiscoverControlSection(title = "发现入口") {
            if (state.controlMode == DiscoverDemoControlMode.EntryError) {
                DiscoverInlineError(onNavigate = onNavigate)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    state.entries.forEach { entry ->
                        DiscoverRouteChip(
                            text = entry,
                            active = entry == state.activeEntry,
                            onClick = { onNavigate(discoverEntryRouteForLabel(entry)) }
                        )
                    }
                }
            }
        }
        DiscoverControlSection(title = "筛选与排序") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverRouteFilterButton(
                    text = "关键词",
                    active = state.activeFilter == "关键词",
                    iconRes = R.drawable.reader_ic_search,
                    onClick = { onNavigate(DiscoverDemoRouteIds.FILTER_KEYWORD) },
                    modifier = Modifier.weight(1f)
                )
                DiscoverRouteFilterButton(
                    text = "男频",
                    active = state.activeFilter == "男频",
                    onClick = { onNavigate(DiscoverDemoRouteIds.FILTER_MALE) },
                    modifier = Modifier.weight(1f)
                )
                DiscoverRouteFilterButton(
                    text = "女频",
                    active = state.activeFilter == "女频",
                    onClick = { onNavigate(DiscoverDemoRouteIds.FILTER_FEMALE) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverRouteFilterButton(
                    text = "排序：${state.activeSort}",
                    active = state.sortOpen,
                    onClick = { onNavigate(DiscoverDemoRouteIds.SORT) },
                    modifier = Modifier.weight(1.3f)
                )
                DiscoverRouteFilterButton(
                    text = "重置",
                    active = false,
                    onClick = { onNavigate("discover") },
                    modifier = Modifier.weight(0.75f)
                )
                DiscoverRoutePillButton(
                    text = "应用",
                    iconRes = R.drawable.reader_ic_check,
                    primary = true,
                    onClick = { onNavigate("discover") },
                    modifier = Modifier.weight(0.82f)
                )
            }
        }
        DiscoverControlSection(title = "源操作") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverRouteActionTile("刷新入口", R.drawable.reader_ic_refresh, { onNavigate(DiscoverDemoRouteIds.SWITCHING_SOURCE) }, Modifier.weight(1f))
                DiscoverRouteActionTile("清缓存", R.drawable.reader_ic_trash, { onNavigate(DiscoverDemoRouteIds.CACHE_CONFIRM) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverRouteActionTile("登录", R.drawable.reader_ic_shield, { onNavigate(DiscoverDemoRouteIds.SOURCE_LOGIN) }, Modifier.weight(1f))
                DiscoverRouteActionTile("编辑源", R.drawable.reader_ic_edit, { onNavigate(DiscoverDemoRouteIds.RULE_TEST) }, Modifier.weight(1f))
            }
            DiscoverRouteActionTile(
                text = "管理发现源",
                iconRes = R.drawable.reader_ic_source,
                onClick = { onNavigate(DiscoverDemoRouteIds.SOURCE_BULK) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiscoverControlSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = discoverRouteTitleStyle().copy(fontWeight = FontWeight(900)),
            color = MaterialTheme.colorScheme.onBackground
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            content()
        }
    }
}

@Composable
private fun DiscoverSourceOption(
    source: DiscoverDemoSourceRowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .background(if (source.selected) colors.primary.copy(alpha = 0.10f) else extra.metaBackground, ReaderShapes.md)
            .border(1.dp, if (source.selected) colors.primary.copy(alpha = 0.30f) else extra.hairline, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = source.name,
            style = discoverRouteTitleStyle(),
            color = if (source.selected) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = source.meta,
            style = discoverRouteMetaStyle(),
            color = discoverToneColor(source.tone),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverInlineError(onNavigate: (String) -> Unit) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(extra.danger.copy(alpha = 0.08f), ReaderShapes.md)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_warning),
            contentDescription = null,
            tint = extra.danger,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "入口解析失败",
                style = discoverRouteTitleStyle(),
                color = extra.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "当前书源的 exploreUrl 返回异常。",
                style = discoverRouteMetaStyle(),
                color = extra.danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DiscoverRoutePillButton("重试", onClick = { onNavigate(DiscoverDemoRouteIds.CONTROL) })
        DiscoverRoutePillButton("编辑源", onClick = { onNavigate(DiscoverDemoRouteIds.RULE_TEST) })
    }
}

@Composable
private fun DiscoverResultHeader(state: DiscoverDemoRouteState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = state.activeEntry,
            style = discoverRouteTitleStyle().copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight(900)),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${state.total} 本",
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(800)),
            color = readerExtraColors().muted
        )
    }
}

@Composable
private fun DiscoverRouteBookList(
    books: List<DiscoverDemoBookState>,
    muted: Boolean,
    onOpenBook: (sourceId: String, bookUrl: String, bookName: String) -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (muted) 0.56f else 1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(horizontal = 12.dp)
    ) {
        books.forEachIndexed { index, book ->
            DiscoverBookRow(
                book = book,
                onClick = { onOpenBook("fixture://discover", book.bookUrl, book.title) }
            )
            if (index != books.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline.copy(alpha = 0.58f))
                )
            }
        }
    }
}

@Composable
private fun DiscoverBookRow(book: DiscoverDemoBookState, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 108.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            DiscoverRouteCoverTile(book)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .background(if (book.inShelf) colors.primary else extra.muted, ReaderShapes.pill)
                    .border(2.dp, colors.surface, ReaderShapes.pill)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = book.title,
                style = discoverRouteTitleStyle().copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight(850)),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.author} · ${book.kind}",
                style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(700)),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.latest,
                style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(800)),
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.intro,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.infoLayer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoverSkeletonList() {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(horizontal = 12.dp)
    ) {
        repeat(4) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 96.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 74.dp)
                        .background(extra.hairline.copy(alpha = 0.55f), ReaderShapes.xs)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiscoverSkeletonLine(widthFraction = 0.46f)
                    DiscoverSkeletonLine(widthFraction = 0.62f)
                    DiscoverSkeletonLine(widthFraction = 1f)
                    DiscoverSkeletonLine(widthFraction = 1f)
                }
            }
            if (index != 3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun DiscoverSkeletonLine(widthFraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.58f), ReaderShapes.xs)
    )
}

@Composable
private fun DiscoverRouteStateCard(
    @DrawableRes iconRes: Int,
    message: DiscoverDemoMessageState,
    onNavigate: (String) -> Unit,
    minHeight: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .padding(horizontal = 18.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DiscoverRouteIconBox(iconRes = iconRes, size = 42)
        Text(
            text = message.title,
            style = discoverRouteTitleStyle().copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight(900)),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = message.body,
            style = discoverRouteMetaStyle().copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = readerExtraColors().muted,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            message.actions.forEach { action ->
                DiscoverRoutePillButton(
                    text = action.label,
                    primary = action.primary,
                    onClick = { onNavigate(action.target) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverRefreshLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), ReaderShapes.pill)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(800)),
            color = readerExtraColors().muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverBackTopButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        DiscoverRoutePillButton(
            text = "回到顶部",
            iconRes = R.drawable.reader_ic_top,
            primary = true,
            onClick = onClick
        )
    }
}

@Composable
private fun DiscoverToast(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(850)),
            color = Color.White,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f), ReaderShapes.pill)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverConfirmDialog(
    dialog: DiscoverDemoDialogState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.26f))
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), ReaderShapes.lg)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = dialog.title,
                style = discoverRouteTitleStyle().copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight(900)),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = dialog.body,
                style = discoverRouteMetaStyle().copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = readerExtraColors().muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dialog.actions.forEach { action ->
                    DiscoverRoutePillButton(
                        text = action.label,
                        primary = action.primary,
                        onClick = { onNavigate(action.target) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSourceLoginRouteScreen(
    state: DiscoverDemoRouteState,
    shell: DiscoverDemoRouteShell,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None
) {
    DiscoverSubpageScaffold(
        shell = shell.takeIf { it != DiscoverDemoRouteShell.Auto } ?: DiscoverDemoRouteShell.Library,
        title = state.title,
        onBack = onBack,
        trailingLabel = "完成",
        onTrailing = { onNavigate(DiscoverDemoRouteIds.CONTROL) },
        bottomActions = state.bottomActions,
        onNavigate = onNavigate,
        overlayState = overlayState
    ) {
        item {
            DiscoverSubpageHeader(
                iconRes = R.drawable.reader_ic_shield,
                title = state.source.name,
                body = "该书源的发现入口需要登录态，登录后返回当前入口并刷新列表。"
            )
        }
        item {
            DiscoverInfoCard {
                DiscoverInfoRow("登录状态", "未登录 · 最近检测 10:32", badge = "需登录", tone = DiscoverDemoTone.Warn)
                DiscoverInfoDivider()
                DiscoverInfoRow("适用范围", "发现入口、详情页、目录页", badge = "当前源", tone = DiscoverDemoTone.Good)
                DiscoverInfoDivider()
                DiscoverInfoRow("Cookie 保存", "仅保存在本机书源配置中", switchOn = true)
            }
        }
        item { DiscoverSubpageActionColumn(actions = state.actions, onNavigate = onNavigate) }
        item {
            Text(
                text = "返回发现页后，当前书源和当前入口保持不变，只刷新内容列表。",
                style = discoverRouteMetaStyle().copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = readerExtraColors().muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun DiscoverRuleTestRouteScreen(
    state: DiscoverDemoRouteState,
    shell: DiscoverDemoRouteShell,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None
) {
    DiscoverSubpageScaffold(
        shell = shell.takeIf { it != DiscoverDemoRouteShell.Auto } ?: DiscoverDemoRouteShell.Settings,
        title = state.title,
        onBack = onBack,
        trailingLabel = "完成",
        onTrailing = { onNavigate(DiscoverDemoRouteIds.CONTROL) },
        bottomActions = state.bottomActions,
        onNavigate = onNavigate,
        overlayState = overlayState
    ) {
        item {
            DiscoverSubpageHeader(
                iconRes = R.drawable.reader_ic_code,
                title = "优书网",
                body = "正在编辑：发现规则",
                badge = "已启用发现"
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("基本", "搜索", "详情", "目录", "正文", "发现", "高级").forEach { tab ->
                    DiscoverRouteChip(
                        text = tab,
                        active = tab == "发现",
                        onClick = {}
                    )
                }
            }
        }
        item {
            DiscoverInfoCard {
                state.fields.forEachIndexed { index, field ->
                    DiscoverFieldRow(field)
                    if (index != state.fields.lastIndex) DiscoverInfoDivider()
                }
            }
        }
        item {
            DiscoverRuleBox(title = "测试输入") {
                DiscoverRuleLine(label = "入口 URL", value = "https://example.com/rank/allvisit_1.html")
                DiscoverRuleLine(label = "HTML 片段", value = "<li class=\"book\">长夜余火</li>")
                DiscoverRoutePillButton(
                    text = "测试入口",
                    iconRes = R.drawable.reader_ic_play,
                    onClick = { onNavigate("run-discover-rule-test") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            DiscoverRuleBox(title = "测试结果") {
                DiscoverRuleResult("生成 5 个入口", "排行榜、分类、完本、最新、书单")
                DiscoverRuleResult("解析到 18 本书", "首条：长夜余火 · 爱潜水的乌贼")
            }
        }
    }
}

@Composable
private fun DiscoverSourceBulkRouteScreen(
    state: DiscoverDemoRouteState,
    shell: DiscoverDemoRouteShell,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None
) {
    DiscoverSubpageScaffold(
        shell = shell.takeIf { it != DiscoverDemoRouteShell.Auto } ?: DiscoverDemoRouteShell.Settings,
        title = state.title,
        onBack = onBack,
        trailingLabel = "完成",
        onTrailing = { onNavigate(DiscoverDemoRouteIds.CONTROL) },
        bottomActions = state.bottomActions,
        onNavigate = onNavigate,
        overlayState = overlayState
    ) {
        item {
            DiscoverSubpageHeader(
                iconRes = R.drawable.reader_ic_source_stack,
                title = "发现源管理",
                body = "选择启用发现的书源，批量启用、禁用或刷新入口。"
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiscoverRoutePillButton("取消", onClick = { onNavigate(DiscoverDemoRouteIds.CONTROL) })
                Text(
                    text = "已选 3 个",
                    style = discoverRouteTitleStyle().copy(fontSize = 13.sp, fontWeight = FontWeight(900)),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                DiscoverRoutePillButton("全选", onClick = { onNavigate("select-all-discover-sources") })
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 34.dp)
                    .background(readerExtraColors().metaBackground, ReaderShapes.md)
                    .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_search),
                    contentDescription = null,
                    tint = readerExtraColors().muted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "搜索书源名称或分组",
                    style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(750)),
                    color = readerExtraColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("已启用发现", "有发现未启用", "需登录", "异常").forEachIndexed { index, filter ->
                    DiscoverRouteChip(filter, active = index == 0, onClick = {})
                }
            }
        }
        item {
            DiscoverInfoCard {
                state.bulkSources.forEachIndexed { index, source ->
                    DiscoverBulkSourceRow(source = source)
                    if (index != state.bulkSources.lastIndex) DiscoverInfoDivider()
                }
            }
        }
    }
}

@Composable
private fun DiscoverSubpageScaffold(
    shell: DiscoverDemoRouteShell,
    title: String,
    onBack: () -> Unit,
    trailingLabel: String,
    onTrailing: () -> Unit,
    bottomActions: List<DiscoverDemoAction>,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    when (shell) {
        DiscoverDemoRouteShell.Library -> DiscoverLibraryShell(
            title = title,
            onBack = onBack,
            trailingLabel = trailingLabel,
            onTrailing = onTrailing,
            bottomActions = bottomActions,
            onNavigate = onNavigate,
            overlayState = overlayState,
            content = content
        )
        DiscoverDemoRouteShell.Settings -> DiscoverSettingsShell(
            title = title,
            onBack = onBack,
            trailingLabel = trailingLabel,
            onTrailing = onTrailing,
            bottomActions = bottomActions,
            onNavigate = onNavigate,
            overlayState = overlayState,
            content = content
        )
        DiscoverDemoRouteShell.Auto -> DiscoverLibraryShell(
            title = title,
            onBack = onBack,
            trailingLabel = trailingLabel,
            onTrailing = onTrailing,
            bottomActions = bottomActions,
            onNavigate = onNavigate,
            overlayState = overlayState,
            content = content
        )
    }
}

@Composable
private fun DiscoverLibraryShell(
    title: String,
    onBack: () -> Unit,
    trailingLabel: String,
    onTrailing: () -> Unit,
    bottomActions: List<DiscoverDemoAction>,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    // LibraryShell skeleton (per demo shared-shell-kit/kit.js renderLibraryShell):
    // statusBar + backTopBar + contentRegion + bottomActionHost + sheetHost + dialogHost + stateHost
    LibraryShellFrame(
        statusBar = { LibraryShellStatusBarSlot() },
        backTopBar = {
            DiscoverRouteTopBar(
                title = title,
                onBack = onBack,
                trailingLabel = trailingLabel,
                onTrailing = onTrailing
            )
        },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        bottomActionHost = {
            LibraryBottomActionHostSlot(actions = bottomActions, onNavigate = onNavigate)
        },
        sheetHost = { LibrarySheetHostSlot(overlayState) },
        dialogHost = { LibraryDialogHostSlot(overlayState) },
        stateHost = { LibraryStateHostSlot() }
    )
}

@Composable
private fun DiscoverSettingsShell(
    title: String,
    onBack: () -> Unit,
    trailingLabel: String,
    onTrailing: () -> Unit,
    bottomActions: List<DiscoverDemoAction>,
    onNavigate: (String) -> Unit,
    overlayState: OverlayState = OverlayState.None,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    // SettingsShell skeleton (per demo shared-shell-kit/kit.js renderSettingsShell):
    // statusBar + backTopBar + settingsContent + bottomActionHost + sheetHost + toastHost + dialogHost + settingsStateHost
    SettingsShellFrame(
        statusBar = { SettingsShellStatusBarSlot() },
        backTopBar = {
            DiscoverRouteTopBar(
                title = title,
                onBack = onBack,
                trailingLabel = trailingLabel,
                onTrailing = onTrailing
            )
        },
        settingsContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        bottomActionHost = {
            SettingsBottomActionHostSlot(actions = bottomActions, onNavigate = onNavigate)
        },
        sheetHost = { SettingsSheetHostSlot(overlayState) },
        toastHost = { SettingsToastHostSlot() },
        dialogHost = { SettingsDialogHostSlot(overlayState) },
        settingsStateHost = { SettingsStateHostSlot() }
    )
}

@Composable
private fun LibraryBottomActionHostSlot(
    actions: List<DiscoverDemoAction>,
    onNavigate: (String) -> Unit
) {
    // bottomActionHost slot: .fd-bottom-action-host:empty { display: none }
    if (actions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        actions.forEach { action ->
            DiscoverRoutePillButton(
                text = action.label,
                primary = action.primary,
                onClick = { onNavigate(action.target) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsBottomActionHostSlot(
    actions: List<DiscoverDemoAction>,
    onNavigate: (String) -> Unit
) {
    // bottomActionHost slot: .fd-bottom-action-host:empty { display: none }
    if (actions.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        actions.forEach { action ->
            DiscoverRoutePillButton(
                text = action.label,
                primary = action.primary,
                onClick = { onNavigate(action.target) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DiscoverSubpageHeader(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiscoverRouteIconBox(iconRes = iconRes, size = 34)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = discoverRouteTitleStyle().copy(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight(900)),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = discoverRouteMetaStyle().copy(lineHeight = 15.sp),
                color = readerExtraColors().muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badge != null) {
            DiscoverToneBadge(text = badge, tone = DiscoverDemoTone.Good)
        }
    }
}

@Composable
private fun DiscoverInfoCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
    ) {
        content()
    }
}

@Composable
private fun DiscoverInfoRow(
    title: String,
    meta: String,
    badge: String? = null,
    tone: DiscoverDemoTone = DiscoverDemoTone.Muted,
    switchOn: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = discoverRouteTitleStyle().copy(fontSize = 13.sp, fontWeight = FontWeight(900)),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = discoverRouteMetaStyle().copy(fontSize = 10.sp, fontWeight = FontWeight(700)),
                color = readerExtraColors().muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            badge != null -> DiscoverToneBadge(text = badge, tone = tone)
            switchOn -> DiscoverSwitchPill(on = true)
        }
    }
}

@Composable
private fun DiscoverFieldRow(field: DiscoverDemoFieldState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = field.label,
            style = discoverRouteMetaStyle().copy(fontSize = 10.sp, fontWeight = FontWeight(700)),
            color = readerExtraColors().muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = field.value,
            style = discoverRouteTitleStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(820)),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DiscoverRuleBox(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(900)),
            color = readerExtraColors().muted
        )
        content()
    }
}

@Composable
private fun DiscoverRuleLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.54f), ReaderShapes.sm)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = discoverRouteMetaStyle().copy(fontSize = 10.sp, fontWeight = FontWeight(700)),
            color = readerExtraColors().muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = discoverRouteTitleStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(850)),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverRuleResult(title: String, meta: String) {
    DiscoverRuleLine(label = meta, value = title)
}

@Composable
private fun DiscoverSubpageActionColumn(actions: List<DiscoverDemoAction>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.forEach { action ->
            DiscoverRoutePillButton(
                text = action.label,
                iconRes = when (action.target) {
                    DiscoverDemoRouteIds.LOGIN_RETURN -> if (action.primary) R.drawable.reader_ic_globe else R.drawable.reader_ic_check
                    DiscoverDemoRouteIds.CONTROL -> R.drawable.reader_ic_refresh
                    else -> null
                },
                primary = action.primary,
                onClick = { onNavigate(action.target) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiscoverBulkSourceRow(source: DiscoverDemoSourceRowState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(if (source.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (source.selected) MaterialTheme.colorScheme.primary else readerExtraColors().metaBackground, ReaderShapes.pill)
                .border(1.dp, readerExtraColors().hairline, ReaderShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            if (source.selected) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = source.name,
                style = discoverRouteTitleStyle().copy(fontSize = 13.sp, fontWeight = FontWeight(900)),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = source.meta,
                style = discoverRouteMetaStyle().copy(fontSize = 10.sp, fontWeight = FontWeight(700)),
                color = readerExtraColors().muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DiscoverToneBadge(
            text = when (source.tone) {
                DiscoverDemoTone.Good -> "可用"
                DiscoverDemoTone.Warn -> "需处理"
                DiscoverDemoTone.Muted -> "暂停"
                DiscoverDemoTone.Loading -> "处理中"
            },
            tone = source.tone
        )
    }
}

@Composable
private fun DiscoverInfoDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.60f))
    )
}

@Composable
private fun DiscoverToneBadge(text: String, tone: DiscoverDemoTone) {
    Text(
        text = text,
        style = discoverRouteMetaStyle().copy(fontSize = 10.sp, fontWeight = FontWeight(900)),
        color = discoverToneColor(tone),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(discoverToneColor(tone).copy(alpha = 0.12f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun DiscoverSwitchPill(on: Boolean) {
    val color = if (on) MaterialTheme.colorScheme.primary else readerExtraColors().hairline
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 26.dp)
            .background(color.copy(alpha = if (on) 0.95f else 0.48f), ReaderShapes.pill)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color.White, ReaderShapes.pill)
        )
    }
}

@Composable
private fun DiscoverRouteActionTile(
    text: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(readerExtraColors().metaBackground, ReaderShapes.md)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().controlInk,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(850)),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverRoutePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    primary: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(if (primary) colors.primary else readerExtraColors().metaBackground, ReaderShapes.pill)
            .border(1.dp, if (primary) Color.Transparent else readerExtraColors().hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (primary) colors.onPrimary else readerExtraColors().controlInk,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(900)),
            color = if (primary) colors.onPrimary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverRouteFilterButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    onClick: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(if (active) colors.primary.copy(alpha = 0.12f) else colors.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, if (active) colors.primary.copy(alpha = 0.30f) else extra.hairline, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (active) colors.primary else extra.controlInk,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontWeight = FontWeight(850)),
            color = if (active) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverRouteChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(if (active) MaterialTheme.colorScheme.primary else readerExtraColors().metaBackground, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = discoverRouteMetaStyle().copy(fontSize = 12.sp, fontWeight = FontWeight(850)),
            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverRouteCoverTile(book: DiscoverDemoBookState) {
    val coverRes = demoCoverDrawableRes(book.coverUrl)
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .clip(ReaderShapes.xs)
            .background(readerExtraColors().metaBackground, ReaderShapes.xs)
            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xs),
        contentAlignment = Alignment.Center
    ) {
        when {
            coverRes != null -> Image(
                painter = painterResource(id = coverRes),
                contentDescription = "${book.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            book.coverUrl.isNotBlank() -> AsyncImage(
                model = book.coverUrl,
                contentDescription = "${book.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            else -> Image(
                painter = painterResource(id = R.drawable.reader_cover_long_night),
                contentDescription = "${book.title}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun DiscoverRouteIconBox(@DrawableRes iconRes: Int, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size((size * 0.52f).dp)
        )
    }
}

@Composable
private fun DiscoverIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun discoverToneColor(tone: DiscoverDemoTone): Color = when (tone) {
    DiscoverDemoTone.Good -> MaterialTheme.colorScheme.primary
    DiscoverDemoTone.Warn -> readerExtraColors().accent
    DiscoverDemoTone.Muted -> readerExtraColors().muted
    DiscoverDemoTone.Loading -> MaterialTheme.colorScheme.tertiary
}

private fun discoverRouteTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun discoverRouteMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)
