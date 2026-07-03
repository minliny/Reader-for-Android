package com.reader.ui.discover

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.android.R
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun DiscoverScreen(
    onOpenBook: (sourceId: String, bookUrl: String, bookName: String) -> Unit,
    onOpenDiscoverControl: () -> Unit = {}
) {
    var activeEntry by remember { mutableStateOf("排行榜") }
    var activeFilter by remember { mutableStateOf("男频") }
    var activeSort by remember { mutableStateOf("人气") }
    var controlExpanded by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    val books = remember(activeEntry, activeFilter, activeSort) { discoverDemoBooks(activeEntry) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        DiscoverTopBar()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 118.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DiscoverSourceBar(
                    expanded = controlExpanded,
                    onClick = {
                        controlExpanded = !controlExpanded
                        onOpenDiscoverControl()
                    }
                )
            }
            item {
                if (controlExpanded) {
                    DiscoverControlPanel(
                        entries = discoverEntries,
                        activeEntry = activeEntry,
                        activeFilter = activeFilter,
                        activeSort = activeSort,
                        onEntry = { activeEntry = it },
                        onFilter = { activeFilter = it },
                        onSort = { activeSort = it },
                        onReset = {
                            activeEntry = "排行榜"
                            activeFilter = "男频"
                            activeSort = "人气"
                        }
                    )
                } else {
                    DiscoverEntryChips(
                        entries = discoverEntries,
                        activeEntry = activeEntry,
                        onEntry = { activeEntry = it }
                    )
                }
            }
            item {
                if (!controlExpanded) {
                    DiscoverFilterControl(
                        activeFilter = activeFilter,
                        activeSort = activeSort,
                        open = filterOpen,
                        onToggle = { filterOpen = !filterOpen },
                        onFilter = { activeFilter = it },
                        onSort = { activeSort = it },
                        onApply = { filterOpen = false }
                    )
                }
            }
            item {
                DiscoverListHeader(activeEntry)
            }
            item {
                DiscoverBookList(
                    books = books,
                    onOpenBook = { book ->
                        onOpenBook("fixture://discover", book.bookUrl, book.title)
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscoverTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "发现",
            style = ReaderTextStyles.appBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverSourceBar(expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(colors.surface.copy(alpha = if (expanded) 1f else 0.92f), ReaderShapes.md)
            .border(1.dp, if (expanded) colors.primary.copy(alpha = 0.30f) else extra.hairline, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DiscoverIconBox(R.drawable.reader_ic_source_stack, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "优书网",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight(800)
                ),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "默认分组 · 已启用发现 · 120ms",
                style = discoverMetaStyle().copy(fontWeight = FontWeight(650)),
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
private fun DiscoverEntryChips(entries: List<String>, activeEntry: String, onEntry: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        entries.forEach { entry ->
            DiscoverChip(
                text = entry,
                active = activeEntry == entry,
                onClick = { onEntry(entry) }
            )
        }
    }
}

@Composable
private fun DiscoverFilterControl(
    activeFilter: String,
    activeSort: String,
    open: Boolean,
    onToggle: () -> Unit,
    onFilter: (String) -> Unit,
    onSort: (String) -> Unit,
    onApply: () -> Unit
) {
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
                .clickable(onClick = onToggle),
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
                style = discoverTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$activeFilter · $activeSort",
                style = discoverMetaStyle().copy(fontWeight = FontWeight(800)),
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
        if (open) {
            DiscoverFilterMenuGroup(
                title = "范围",
                options = listOf("关键词", "男频", "女频"),
                active = activeFilter,
                onSelect = onFilter
            )
            DiscoverFilterMenuGroup(
                title = "排序",
                options = listOf("人气", "更新", "收藏", "完本", "字数"),
                active = activeSort,
                onSelect = onSort
            )
            DiscoverPrimaryAction(text = "应用", onClick = onApply)
        }
    }
}

@Composable
private fun DiscoverListHeader(title: String) {
    Text(
        text = title,
        style = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight(900)
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
private fun DiscoverFilterMenuGroup(
    title: String,
    options: List<String>,
    active: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = discoverMetaStyle().copy(fontWeight = FontWeight(900)),
            color = readerExtraColors().muted
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            options.forEach { option ->
                DiscoverFilterButton(
                    text = option,
                    active = active == option,
                    iconRes = if (option == "关键词") R.drawable.reader_ic_search else null,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverControlPanel(
    entries: List<String>,
    activeEntry: String,
    activeFilter: String,
    activeSort: String,
    onEntry: (String) -> Unit,
    onFilter: (String) -> Unit,
    onSort: (String) -> Unit,
    onReset: () -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DiscoverControlSection(title = "当前书源") {
            val sources = listOf(
                Triple("优书网", "默认 · 120ms", true),
                Triple("起点导入", "正版 · 180ms", false),
                Triple("轻小说文库", "需登录", false),
                Triple("本地聚合源", "维护中", false)
            )
            sources.forEach { (name, meta, active) ->
                DiscoverSourceOption(name = name, meta = meta, active = active)
            }
        }
        DiscoverControlSection(title = "发现入口") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                entries.forEach { entry ->
                    DiscoverChip(
                        text = entry,
                        active = activeEntry == entry,
                        onClick = { onEntry(entry) }
                    )
                }
            }
        }
        DiscoverControlSection(title = "筛选与排序") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverFilterButton(
                    text = "关键词",
                    active = activeFilter == "关键词",
                    iconRes = R.drawable.reader_ic_search,
                    onClick = { onFilter("关键词") },
                    modifier = Modifier.weight(1f)
                )
                DiscoverFilterButton(
                    text = "男频",
                    active = activeFilter == "男频",
                    onClick = { onFilter("男频") },
                    modifier = Modifier.weight(1f)
                )
                DiscoverFilterButton(
                    text = "女频",
                    active = activeFilter == "女频",
                    onClick = { onFilter("女频") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverFilterButton(
                    text = "排序：$activeSort",
                    active = false,
                    onClick = {
                        val sorts = listOf("人气", "更新", "收藏", "完本", "字数")
                        onSort(sorts[(sorts.indexOf(activeSort).coerceAtLeast(0) + 1) % sorts.size])
                    },
                    modifier = Modifier.weight(1.3f)
                )
                DiscoverFilterButton(
                    text = "重置",
                    active = false,
                    onClick = onReset,
                    modifier = Modifier.weight(0.75f)
                )
                DiscoverPrimaryAction(
                    text = "应用",
                    onClick = {},
                    modifier = Modifier.weight(0.82f)
                )
            }
        }
        DiscoverControlSection(title = "源操作") {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverActionButton("刷新入口", R.drawable.reader_ic_refresh, Modifier.weight(1f))
                DiscoverActionButton("清缓存", R.drawable.reader_ic_trash, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                DiscoverActionButton("登录", R.drawable.reader_ic_shield, Modifier.weight(1f))
                DiscoverActionButton("编辑源", R.drawable.reader_ic_edit, Modifier.weight(1f))
            }
            DiscoverActionButton("管理发现源", R.drawable.reader_ic_source_stack, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DiscoverControlSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = discoverTitleStyle().copy(fontWeight = FontWeight(900)),
            color = MaterialTheme.colorScheme.onBackground
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun DiscoverSourceOption(name: String, meta: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(if (active) colors.primary.copy(alpha = 0.10f) else extra.metaBackground, ReaderShapes.md)
            .border(1.dp, if (active) colors.primary.copy(alpha = 0.30f) else extra.hairline, ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = name,
            style = discoverTitleStyle(),
            color = if (active) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = meta,
            style = discoverMetaStyle(),
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(MaterialTheme.colorScheme.primary, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = discoverMetaStyle().copy(fontWeight = FontWeight(900)),
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverActionButton(
    text: String,
    @androidx.annotation.DrawableRes iconRes: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(extra.metaBackground, ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = discoverMetaStyle().copy(fontWeight = FontWeight(850)),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverBookList(books: List<DiscoverBook>, onOpenBook: (DiscoverBook) -> Unit) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f), ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(horizontal = 12.dp)
    ) {
        books.forEachIndexed { index, book ->
            DiscoverBookRow(book = book, onClick = { onOpenBook(book) })
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
private fun DiscoverBookRow(book: DiscoverBook, onClick: () -> Unit) {
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
            DiscoverCoverTile(book.title)
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
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight(850)
                ),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.author} · ${book.kind}",
                style = discoverMetaStyle().copy(fontWeight = FontWeight(700)),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.latest,
                style = discoverMetaStyle().copy(fontWeight = FontWeight(800)),
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
private fun DiscoverCoverTile(title: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .background(extra.metaBackground, ReaderShapes.xs)
            .border(1.dp, extra.hairline, ReaderShapes.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.firstOrNull()?.toString() ?: "书",
            style = ReaderTextStyles.bookTitle.copy(fontSize = 18.sp, lineHeight = 22.sp),
            color = colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun DiscoverChip(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .background(if (active) colors.primary else extra.metaBackground, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(800)
            ),
            color = if (active) colors.onPrimary else colors.onBackground,
            maxLines = 1
        )
    }
}

@Composable
private fun DiscoverFilterButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes iconRes: Int? = null,
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
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (active) colors.primary else extra.controlInk,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight(850)
            ),
            color = if (active) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverIconBox(@androidx.annotation.DrawableRes iconRes: Int, size: androidx.compose.ui.unit.Dp) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

private data class DiscoverBook(
    val title: String,
    val author: String,
    val kind: String,
    val latest: String,
    val intro: String,
    val inShelf: Boolean,
    val bookUrl: String
)

private val discoverEntries = listOf("排行榜", "畅销", "分类", "完本", "最新", "新书", "书单")

private fun discoverDemoBooks(entry: String): List<DiscoverBook> {
    val base = listOf(
        DiscoverBook("长夜余火", "爱潜水的乌贼", "科幻 · 连载", "最新：第 32 章 雨夜", "雨声在窗外连成一片，旧世界的线索在夜里慢慢浮出。", true, "fixture://discover/long-night"),
        DiscoverBook("诡秘之主", "爱潜水的乌贼", "奇幻 · 完本", "最新：番外已整理", "蒸汽、塔罗与旧日秘密交织，适合继续追读。", true, "fixture://discover/mystery-lord"),
        DiscoverBook("三体", "刘慈欣", "科幻 · 完本", "最新：三部曲合集", "文明在宇宙暗处相互凝视，微小选择带来巨大回声。", false, "fixture://discover/three-body"),
        DiscoverBook("明朝那些事儿", "当年明月", "历史 · 完本", "最新：全集校对", "用更轻松的方式重新翻开明朝人物与权力线索。", false, "fixture://discover/ming"),
        DiscoverBook("纸上城市", "默认分组", "都市 · 连载", "最新：第 12 章", "纸页边缘折起，城市的名字开始变化。", false, "fixture://discover/paper-city")
    )
    return if (entry == "最新") base.reversed() else base
}

private fun discoverTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight(800)
)

private fun discoverMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)
