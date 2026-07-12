package com.reader.ui.bookshelf

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reader.android.R
import com.reader.api.Book
import com.reader.ui.demo.demoCoverDrawableRes
import com.reader.ui.demo.demoCoverUrl
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * Host-ready native screens for the remaining bookshelf demo routes:
 * `bookshelf-empty` and `sort-filter`.
 *
 * Canonical source: `frontend-demo/render-runtime.js` `bookshelfEmptyScreen` and
 * `sortFilterScreen` / `mainTabBookshelf`.
 */
@Composable
fun BookshelfEmptyRouteScreen(
    onSearch: () -> Unit,
    onLocalImport: () -> Unit,
    onDiscover: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    state: BookshelfDemoRouteState = demoBookshelfEmptyRouteState()
) {
    var moreOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BookshelfRouteScaffold(
            title = "书架",
            onSearch = onSearch,
            onMore = { moreOpen = true }
        ) {
            item {
                BookshelfRouteShelfSection(
                    state = state,
                    actionsEnabled = false,
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = onDiscover,
                    onBookshelfSettings = onBookshelfSettings,
                    onOpenBookFromCover = {},
                    onOpenBookFromAction = {}
                )
            }
        }
        if (moreOpen) {
            BookshelfRouteMoreLayer(
                onDismiss = { moreOpen = false },
                onBookBatchManagement = {
                    moreOpen = false
                    onBookBatchManagement()
                },
                onGroupManagement = {
                    moreOpen = false
                    onGroupManagement()
                },
                onLocalImport = {
                    moreOpen = false
                    onLocalImport()
                }
            )
        }
    }
}

@Composable
fun BookshelfSortFilterRouteScreen(
    onSearch: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    state: BookshelfDemoRouteState = demoBookshelfSortFilterRouteState(),
    onFilterChange: (BookshelfFilterState) -> Unit = {}
) {
    var routeState by remember(state) { mutableStateOf(state) }
    var moreOpen by remember { mutableStateOf(false) }

    fun updateFilter(filter: BookshelfFilterState) {
        routeState = routeState.copy(chrome = routeState.chrome.copy(filter = filter))
        onFilterChange(filter)
    }

    Box(Modifier.fillMaxSize()) {
        BookshelfRouteScaffold(
            title = "书架",
            onSearch = onSearch,
            onMore = { moreOpen = true }
        ) {
            routeState.continueReading?.let { book ->
                item {
                    BookshelfRouteContinueCard(
                        book = book,
                        onCoverClick = { onOpenBookFromCover(book) },
                        onContinue = { onOpenBookFromAction(book) }
                    )
                }
            }
            item {
                BookshelfRouteShelfSection(
                    state = routeState,
                    actionsEnabled = true,
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = {},
                    onBookshelfSettings = onBookshelfSettings,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction,
                    onFilterChange = ::updateFilter
                )
            }
        }
        if (moreOpen) {
            BookshelfRouteMoreLayer(
                onDismiss = { moreOpen = false },
                onBookBatchManagement = {
                    moreOpen = false
                    onBookBatchManagement()
                },
                onGroupManagement = {
                    moreOpen = false
                    onGroupManagement()
                },
                onLocalImport = {
                    moreOpen = false
                    onLocalImport()
                }
            )
        }
    }
}

data class BookshelfDemoRouteState(
    val books: List<Book>,
    val continueReading: Book? = books.firstOrNull(),
    val chrome: BookshelfChromeState = BookshelfChromeState()
)

fun demoBookshelfEmptyRouteState(): BookshelfDemoRouteState =
    BookshelfDemoRouteState(
        books = emptyList(),
        continueReading = null,
        chrome = BookshelfChromeState(viewMode = BookshelfViewMode.COVER)
    )

fun demoBookshelfSortFilterRouteState(): BookshelfDemoRouteState {
    val books = demoBookshelfRouteBooks()
    return BookshelfDemoRouteState(
        books = books,
        continueReading = books.first(),
        chrome = BookshelfChromeState(
            viewMode = BookshelfViewMode.COVER,
            filter = BookshelfFilterState(isOpen = true)
        )
    )
}

fun demoBookshelfRouteBooks(): List<Book> = listOf(
    demoBookshelfBook("fixture://book/long-night", "长夜余火", "爱潜水的乌贼", "第 32 章 雨夜", "38%", "long-night"),
    demoBookshelfBook("fixture://book/mystery-lord", "诡秘之主", "爱潜水的乌贼", "第 1426 章", "58%", "mystery-lord"),
    demoBookshelfBook("fixture://book/bright-moon", "明朝那些事儿", "当年明月", "第 218 章", "58%", "bright-moon"),
    demoBookshelfBook("fixture://book/three-body", "三体", "刘慈欣", "65%", "65%", "three-body"),
    demoBookshelfBook("fixture://book/renjian", "人间词话", "王国维", "卷上 · 境界", "24%", "renjian-cihua"),
    demoBookshelfBook("fixture://book/android-notes", "Android 开发笔记", "本地文档", "Compose Shell 结构", "12%", "android-notes")
)

fun demoBookshelfBook(
    bookUrl: String = "fixture://book/long-night",
    name: String = "长夜余火",
    author: String = "爱潜水的乌贼",
    latestChapterTitle: String = "第 32 章 雨夜",
    wordCount: String = "38%",
    coverKey: String = "long-night"
): Book =
    Book(
        bookUrl = bookUrl,
        name = name,
        author = author,
        coverUrl = demoCoverUrl(coverKey),
        intro = "用于匹配 canonical frontend-demo 书架链路的本地展示数据。",
        wordCount = wordCount,
        latestChapterTitle = latestChapterTitle,
        origin = "fixture://bookshelf"
    )

@Composable
private fun BookshelfRouteScaffold(
    title: String,
    onSearch: () -> Unit,
    onMore: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        BookshelfRouteTopBar(title = title, onSearch = onSearch, onMore = onMore)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 19.dp, top = 8.dp, end = 19.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun BookshelfRouteTopBar(
    title: String,
    onSearch: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.appBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BookshelfRouteIconButton(R.drawable.reader_ic_search, "搜索", onSearch)
            BookshelfRouteIconButton(R.drawable.reader_ic_more, "更多", onMore)
        }
    }
}

@Composable
private fun BookshelfRouteShelfSection(
    state: BookshelfDemoRouteState,
    actionsEnabled: Boolean,
    onSearch: () -> Unit,
    onLocalImport: () -> Unit,
    onDiscover: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onFilterChange: (BookshelfFilterState) -> Unit = {},
    onCoverView: () -> Unit = {},
    onListView: () -> Unit = {}
) {
    val isEmpty = state.books.isEmpty()
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isEmpty) 9.dp else 10.dp)
        ) {
            BookshelfRouteSectionHeader(
                viewMode = state.chrome.viewMode,
                actionsEnabled = actionsEnabled,
                filterActive = state.chrome.filter.isActive,
                onCoverView = onCoverView,
                onListView = onListView,
                onToggleFilter = {
                    onFilterChange(state.chrome.filter.copy(isOpen = !state.chrome.filter.isOpen))
                },
                onDisplaySettings = onBookshelfSettings
            )
            if (isEmpty) {
                BookshelfRouteEmptyState(
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = onDiscover,
                    onBookshelfSettings = onBookshelfSettings
                )
            } else {
                BookshelfRouteBookGrid(
                    books = state.books,
                    viewMode = state.chrome.viewMode,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction
                )
            }
        }
        if (!isEmpty && state.chrome.filter.isOpen) {
            BookshelfRouteFilterPopover(
                filter = state.chrome.filter,
                onGroup = { onFilterChange(state.chrome.filter.copy(group = it)) },
                onSort = { onFilterChange(state.chrome.filter.copy(sort = it)) },
                onFilter = { onFilterChange(state.chrome.filter.copy(filter = it)) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp)
            )
        }
    }
}

@Composable
private fun BookshelfRouteSectionHeader(
    viewMode: BookshelfViewMode,
    actionsEnabled: Boolean,
    filterActive: Boolean,
    onCoverView: () -> Unit,
    onListView: () -> Unit,
    onToggleFilter: () -> Unit,
    onDisplaySettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "我的书架",
            style = ReaderTextStyles.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookshelfRouteSectionIcon(R.drawable.reader_ic_grid, "封面视图", viewMode == BookshelfViewMode.COVER, actionsEnabled, onCoverView)
            BookshelfRouteSectionIcon(R.drawable.reader_ic_list, "列表视图", viewMode == BookshelfViewMode.LIST, actionsEnabled, onListView)
            BookshelfRouteSectionIcon(R.drawable.reader_ic_filter, "书架筛选", filterActive, actionsEnabled, onToggleFilter)
            BookshelfRouteSectionIcon(R.drawable.reader_ic_gear, "书架显示设置", false, true, onDisplaySettings)
        }
    }
}

@Composable
private fun BookshelfRouteBookGrid(
    books: List<Book>,
    viewMode: BookshelfViewMode,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit
) {
    when (viewMode) {
        BookshelfViewMode.COVER -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            books.chunked(3).forEach { rowBooks ->
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    rowBooks.forEach { book ->
                        BookshelfRouteBookCard(
                            book = book,
                            onClick = { onOpenBookFromCover(book) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowBooks.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        BookshelfViewMode.LIST -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            books.forEach { book ->
                BookshelfRouteListBookCard(
                    book = book,
                    onClick = { onOpenBookFromAction(book) }
                )
            }
        }
    }
}

@Composable
private fun BookshelfRouteBookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BookshelfRouteCoverFrame(
            book = book,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(elevation = 4.dp, shape = ReaderShapes.md, clip = false)
        )
        Text(
            text = book.name,
            style = ReaderTextStyles.bookTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.author,
            style = ReaderTextStyles.bookAuthor,
            color = readerExtraColors().muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookshelfRouteListBookCard(book: Book, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 66.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookshelfRouteCoverFrame(
            book = book,
            shape = ReaderShapes.sm,
            modifier = Modifier
                .size(width = 48.dp, height = 72.dp)
                .shadow(elevation = 3.dp, shape = ReaderShapes.sm, clip = false)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.name, style = ReaderTextStyles.bookTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = ReaderTextStyles.bookAuthor, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookshelfRouteContinueCard(
    book: Book,
    onCoverClick: () -> Unit,
    onContinue: () -> Unit
) {
    val extra = readerExtraColors()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 100.dp)
            .shadow(elevation = 2.dp, shape = ReaderShapes.md, clip = false),
        shape = ReaderShapes.md,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, extra.hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookshelfRouteCoverFrame(
                book = book,
                shape = ReaderShapes.sm,
                modifier = Modifier
                    .width(62.dp)
                    .aspectRatio(2f / 3f)
                    .clickable(onClick = onCoverClick)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("继续阅读", style = ReaderTextStyles.continueLabel, color = MaterialTheme.colorScheme.primary)
                Text(book.name, style = ReaderTextStyles.continueTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = ReaderTextStyles.continueAuthor, color = extra.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 74.dp, minHeight = 40.dp)
                    .background(MaterialTheme.colorScheme.primary, ReaderShapes.pill)
                    .clickable(onClick = onContinue)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("阅读", style = ReaderTextStyles.continueAction, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun BookshelfRouteFilterPopover(
    filter: BookshelfFilterState,
    onGroup: (String) -> Unit,
    onSort: (String) -> Unit,
    onFilter: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .widthIn(max = 286.dp)
            .fillMaxWidth()
            .shadow(elevation = 14.dp, shape = ReaderShapes.lg, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookshelfRouteFilterOptionGroup("分组", listOf("全部", "默认", "本地书", "追更"), filter.group, onGroup)
        BookshelfRouteFilterOptionGroup("排序", listOf("最近更新", "阅读进度", "书名", "作者"), filter.sort, onSort)
        BookshelfRouteFilterOptionGroup("筛选", listOf("全部", "未读", "已完结", "更新失败"), filter.filter, onFilter)
    }
}

@Composable
private fun BookshelfRouteFilterOptionGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = ReaderTextStyles.tabLabel, color = readerExtraColors().muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                BookshelfRouteFilterChip(option, option == selected) { onSelect(option) }
            }
        }
    }
}

@Composable
private fun BookshelfRouteFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val extra = readerExtraColors()
    val background = if (selected) MaterialTheme.colorScheme.primary else extra.metaBackground.copy(alpha = 0.72f)
    val border = if (selected) MaterialTheme.colorScheme.primary else extra.hairline
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 30.dp)
            .background(background, ReaderShapes.pill)
            .border(1.dp, border, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = ReaderTextStyles.tabLabel, color = content, maxLines = 1)
    }
}

@Composable
private fun BookshelfRouteEmptyState(
    onSearch: () -> Unit,
    onLocalImport: () -> Unit,
    onDiscover: () -> Unit,
    onBookshelfSettings: () -> Unit
) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 350.dp)
            .shadow(elevation = 2.dp, shape = ReaderShapes.md, clip = false)
            .background(MaterialTheme.colorScheme.surface, ReaderShapes.md)
            .border(1.dp, MaterialTheme.colorScheme.outline, ReaderShapes.md)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BookshelfRouteEmptyVisual()
            Text("书架还是空的", style = ReaderTextStyles.emptyHeading, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = "添加网络书籍或导入本地文件后，会在这里显示继续阅读和书架内容。",
                style = ReaderTextStyles.emptyBody,
                color = extra.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
            Column(Modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                BookshelfRouteEmptyAction(R.drawable.reader_ic_search, "搜索书籍", "按书名、作者或关键词查找", true, onSearch)
                BookshelfRouteEmptyAction(R.drawable.reader_ic_folder, "导入本地书", "添加本机文件到书架", false, onLocalImport)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BookshelfRouteHintAction(R.drawable.reader_ic_sparkle, "去发现", onDiscover)
                BookshelfRouteHintAction(R.drawable.reader_ic_gear, "书架设置", onBookshelfSettings)
            }
        }
    }
}

@Composable
private fun BookshelfRouteEmptyVisual() {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier.size(width = 112.dp, height = 92.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 8.dp)
                .size(width = 72.dp, height = 52.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), ReaderShapes.sm)
                .border(1.dp, extra.hairline, ReaderShapes.sm)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 8.dp)
                .size(width = 72.dp, height = 52.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), ReaderShapes.sm)
                .border(1.dp, extra.hairline, ReaderShapes.sm)
        )
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(elevation = 6.dp, shape = ReaderShapes.lg, clip = false)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), ReaderShapes.lg)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), ReaderShapes.lg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_bookshelf),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BookshelfRouteEmptyAction(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    val background = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val border = if (primary) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else extra.primaryDark
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(background, ReaderShapes.md)
            .border(1.dp, border, ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), null, tint = content, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = ReaderTextStyles.continueAction, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = ReaderTextStyles.tabLabel, color = content.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookshelfRouteHintAction(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(extra.metaBackground.copy(alpha = 0.9f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(painterResource(iconRes), null, tint = extra.controlInk, modifier = Modifier.size(14.dp))
        Text(text, style = ReaderTextStyles.tabLabel, color = extra.controlInk, maxLines = 1)
    }
}

@Composable
private fun BookshelfRouteMoreLayer(
    onDismiss: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    onLocalImport: () -> Unit
) {
    val extra = readerExtraColors()
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 模态遮罩：映射到 ink 语义 token（onBackground）的半透明 scrim（0x2E1F1B17 → onBackground@0.18）
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 66.dp, end = 18.dp)
                .width(238.dp)
                .shadow(elevation = 18.dp, shape = ReaderShapes.lg, clip = false)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), ReaderShapes.lg)
                .border(1.dp, extra.hairline, ReaderShapes.lg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("书架更多操作", style = ReaderTextStyles.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
            BookshelfRouteMoreButton(R.drawable.reader_ic_check, "批量管理", "选择多本书后移动或删除", onBookBatchManagement)
            BookshelfRouteMoreButton(R.drawable.reader_ic_people, "分组管理", "编辑书架分组与归属", onGroupManagement)
            BookshelfRouteMoreButton(R.drawable.reader_ic_book_open, "本地书导入", "导入本地文件到书架", onLocalImport)
        }
    }
}

@Composable
private fun BookshelfRouteMoreButton(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(iconRes), null, tint = readerExtraColors().controlInk, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ReaderTextStyles.continueAction, color = readerExtraColors().controlInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = ReaderTextStyles.tabLabel, color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BookshelfRouteSectionIcon(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    val color = when {
        !enabled -> extra.muted.copy(alpha = 0.42f)
        selected -> MaterialTheme.colorScheme.primary
        else -> extra.navInactive
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(iconRes), contentDescription, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun BookshelfRouteIconButton(
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
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BookshelfRouteCoverFrame(
    book: Book,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ReaderShapes.md
) {
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .clip(shape)
            .background(extra.surfaceSoft, shape)
            .border(1.dp, extra.hairline, shape),
        contentAlignment = Alignment.Center
    ) {
        val coverRes = demoCoverDrawableRes(book.coverUrl)
        if (coverRes != null) {
            Image(
                painter = painterResource(id = coverRes),
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (book.coverUrl.isNotBlank()) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.reader_cover_long_night),
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// H1-Android 批次 1：3 个书架状态变体路由
//  - bookshelf-cover-mode      书架封面模式 + 反馈条
//  - bookshelf-list-mode       书架列表模式 + 反馈条
//  - bookshelf-book-more-menu  书架网格 + 底部书籍操作菜单浮层（8 个操作按钮）
//
// 参考 `bookshelf-empty` / `sort-filter` 的实现模式：
//  - 复用 BookshelfRouteScaffold / BookshelfRouteShelfSection / BookshelfRouteContinueCard
//  - 读取真实状态（BookshelfDemoRouteState），不使用固定文本冒充
// ------------------------------------------------------------------------------------------------

fun demoBookshelfCoverModeRouteState(): BookshelfDemoRouteState {
    val books = demoBookshelfRouteBooks()
    return BookshelfDemoRouteState(
        books = books,
        continueReading = books.first(),
        chrome = BookshelfChromeState(viewMode = BookshelfViewMode.COVER)
    )
}

fun demoBookshelfListModeRouteState(): BookshelfDemoRouteState {
    val books = demoBookshelfRouteBooks()
    return BookshelfDemoRouteState(
        books = books,
        continueReading = books.first(),
        chrome = BookshelfChromeState(viewMode = BookshelfViewMode.LIST)
    )
}

fun demoBookshelfBookMoreMenuRouteState(): BookshelfDemoRouteState {
    val books = demoBookshelfRouteBooks()
    return BookshelfDemoRouteState(
        books = books,
        continueReading = books.first(),
        chrome = BookshelfChromeState(
            viewMode = BookshelfViewMode.COVER,
            focusedBook = books.first()
        )
    )
}

/**
 * 书架封面模式：封面网格视图 + “已切换到封面视图”反馈条。
 */
@Composable
fun BookshelfCoverModeRouteScreen(
    onSearch: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    onSwitchToList: () -> Unit,
    state: BookshelfDemoRouteState = demoBookshelfCoverModeRouteState()
) {
    var moreOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BookshelfRouteScaffold(
            title = "书架",
            onSearch = onSearch,
            onMore = { moreOpen = true }
        ) {
            state.continueReading?.let { book ->
                item {
                    BookshelfRouteContinueCard(
                        book = book,
                        onCoverClick = { onOpenBookFromCover(book) },
                        onContinue = { onOpenBookFromAction(book) }
                    )
                }
            }
            item {
                BookshelfRouteShelfSection(
                    state = state,
                    actionsEnabled = true,
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = {},
                    onBookshelfSettings = onBookshelfSettings,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction,
                    onCoverView = {},
                    onListView = onSwitchToList
                )
            }
        }
        BookshelfRouteViewSwitchToast(
            text = "已切换到封面视图",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        if (moreOpen) {
            BookshelfRouteMoreLayer(
                onDismiss = { moreOpen = false },
                onBookBatchManagement = {
                    moreOpen = false
                    onBookBatchManagement()
                },
                onGroupManagement = {
                    moreOpen = false
                    onGroupManagement()
                },
                onLocalImport = {
                    moreOpen = false
                    onLocalImport()
                }
            )
        }
    }
}

/**
 * 书架列表模式：列表视图 + “已切换到列表视图”反馈条。
 */
@Composable
fun BookshelfListModeRouteScreen(
    onSearch: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    onSwitchToCover: () -> Unit,
    state: BookshelfDemoRouteState = demoBookshelfListModeRouteState()
) {
    var moreOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BookshelfRouteScaffold(
            title = "书架",
            onSearch = onSearch,
            onMore = { moreOpen = true }
        ) {
            state.continueReading?.let { book ->
                item {
                    BookshelfRouteContinueCard(
                        book = book,
                        onCoverClick = { onOpenBookFromCover(book) },
                        onContinue = { onOpenBookFromAction(book) }
                    )
                }
            }
            item {
                BookshelfRouteShelfSection(
                    state = state,
                    actionsEnabled = true,
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = {},
                    onBookshelfSettings = onBookshelfSettings,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction,
                    onCoverView = onSwitchToCover,
                    onListView = {}
                )
            }
        }
        BookshelfRouteViewSwitchToast(
            text = "已切换到列表视图",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        if (moreOpen) {
            BookshelfRouteMoreLayer(
                onDismiss = { moreOpen = false },
                onBookBatchManagement = {
                    moreOpen = false
                    onBookBatchManagement()
                },
                onGroupManagement = {
                    moreOpen = false
                    onGroupManagement()
                },
                onLocalImport = {
                    moreOpen = false
                    onLocalImport()
                }
            )
        }
    }
}

/**
 * 书架网格 + 底部书籍操作菜单浮层（详情、换源、缓存、加入分组、编辑、替换规则、删除、取消）。
 */
@Composable
fun BookshelfBookMoreMenuRouteScreen(
    onSearch: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    onBookDetail: () -> Unit,
    onSourceSwitch: () -> Unit,
    onCacheBook: () -> Unit,
    onAddToGroup: () -> Unit,
    onEditBook: () -> Unit,
    onReplaceRule: () -> Unit,
    onDeleteBook: () -> Unit,
    onDismiss: () -> Unit = {},
    state: BookshelfDemoRouteState = demoBookshelfBookMoreMenuRouteState()
) {
    val focusedBook = state.chrome.focusedBook ?: state.books.firstOrNull()
    var moreOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        BookshelfRouteScaffold(
            title = "书架",
            onSearch = onSearch,
            onMore = { moreOpen = true }
        ) {
            state.continueReading?.let { book ->
                item {
                    BookshelfRouteContinueCard(
                        book = book,
                        onCoverClick = { onOpenBookFromCover(book) },
                        onContinue = { onOpenBookFromAction(book) }
                    )
                }
            }
            item {
                BookshelfRouteShelfSection(
                    state = state,
                    actionsEnabled = true,
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = {},
                    onBookshelfSettings = onBookshelfSettings,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction
                )
            }
        }
        if (focusedBook != null) {
            BookshelfRouteBookMoreMenuLayer(
                book = focusedBook,
                onDismiss = onDismiss,
                onBookDetail = onBookDetail,
                onSourceSwitch = onSourceSwitch,
                onCacheBook = onCacheBook,
                onAddToGroup = onAddToGroup,
                onEditBook = onEditBook,
                onReplaceRule = onReplaceRule,
                onDeleteBook = onDeleteBook
            )
        }
        if (moreOpen) {
            BookshelfRouteMoreLayer(
                onDismiss = { moreOpen = false },
                onBookBatchManagement = {
                    moreOpen = false
                    onBookBatchManagement()
                },
                onGroupManagement = {
                    moreOpen = false
                    onGroupManagement()
                },
                onLocalImport = {
                    moreOpen = false
                    onLocalImport()
                }
            )
        }
    }
}

@Composable
private fun BookshelfRouteViewSwitchToast(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .shadow(elevation = 6.dp, shape = ReaderShapes.pill, clip = false)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), ReaderShapes.pill)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), ReaderShapes.pill)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(text, style = ReaderTextStyles.tabLabel, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun BookshelfRouteBookMoreMenuLayer(
    book: Book,
    onDismiss: () -> Unit,
    onBookDetail: () -> Unit,
    onSourceSwitch: () -> Unit,
    onCacheBook: () -> Unit,
    onAddToGroup: () -> Unit,
    onEditBook: () -> Unit,
    onReplaceRule: () -> Unit,
    onDeleteBook: () -> Unit
) {
    val extra = readerExtraColors()
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.36f))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .shadow(elevation = 18.dp, shape = ReaderShapes.xl, clip = false)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), ReaderShapes.xl)
                .border(1.dp, extra.hairline, ReaderShapes.xl)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BookshelfRouteCoverFrame(
                    book = book,
                    shape = ReaderShapes.sm,
                    modifier = Modifier.size(width = 38.dp, height = 56.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        style = ReaderTextStyles.bookTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.author,
                        style = ReaderTextStyles.bookAuthor,
                        color = extra.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extra.hairline)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_book_open, "详情", Modifier.weight(1f), onBookDetail)
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_source_switch, "换源", Modifier.weight(1f), onSourceSwitch)
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_download, "缓存", Modifier.weight(1f), onCacheBook)
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_folder, "加入分组", Modifier.weight(1f), onAddToGroup)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_edit, "编辑", Modifier.weight(1f), onEditBook)
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_replace, "替换规则", Modifier.weight(1f), onReplaceRule)
                BookshelfRouteMoreMenuAction(R.drawable.reader_ic_trash, "删除", Modifier.weight(1f), onDeleteBook)
                Spacer(Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .background(extra.metaBackground.copy(alpha = 0.72f), ReaderShapes.pill)
                    .border(1.dp, extra.hairline, ReaderShapes.pill)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", style = ReaderTextStyles.continueAction, color = extra.controlInk)
            }
        }
    }
}

@Composable
private fun BookshelfRouteMoreMenuAction(
    @DrawableRes iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 64.dp)
            .background(extra.metaBackground.copy(alpha = 0.6f), ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(22.dp)
        )
        Text(label, style = ReaderTextStyles.tabLabel, color = extra.controlInk, maxLines = 1)
    }
}
