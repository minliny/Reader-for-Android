package com.reader.ui.bookshelf

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reader.android.R
import com.reader.api.Book
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * Bookshelf screen — `bookshelf` main tab.
 *
 * Structural source: `frontend-demo/render-runtime.js` `mainTabBookshelf` /
 * `bookshelfEmptyScreen`, with dimensions from `styles/00-foundation.css` and
 * `styles/01-shell-layout.css`. This file mirrors the demo's component hierarchy rather than
 * copying DOM/CSS:
 *
 * MainTabShell -> `.fd-top-bar` -> `.fd-phone-content` -> `.fd-continue-card` ->
 * `.fd-bookshelf-shelf-section` -> `.fd-section-head` + `.fd-bookshelf-filter-popover` +
 * `.fd-book-grid` / `.fd-bookshelf-empty-state`; `.fd-bookshelf-more-layer` is a state-host
 * overlay.
 */
@Composable
fun BookshelfScreen(
    onSearch: () -> Unit,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onBookBatchManagement: () -> Unit,
    onGroupManagement: () -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onDiscover: () -> Unit,
    vm: BookshelfViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val continueReading by vm.continueReading.collectAsStateWithLifecycle()
    val chrome by vm.chromeState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BookshelfTopBar(
                onSearch = onSearch,
                onMore = { vm.setMoreMenuOpen(true) }
            )

            when (val s = state) {
                is UiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UiState.Error -> Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }

                is UiState.Empty -> BookshelfContent(
                    continueReading = null,
                    books = emptyList(),
                    isEmpty = true,
                    chrome = chrome,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction,
                    onCoverView = { vm.setViewMode(BookshelfViewMode.COVER) },
                    onListView = { vm.setViewMode(BookshelfViewMode.LIST) },
                    onToggleFilter = { vm.toggleFilter() },
                    onFilterGroup = { vm.setFilterGroup(it) },
                    onFilterSort = { vm.setFilterSort(it) },
                    onFilterValue = { vm.setFilterValue(it) },
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onBookshelfSettings = onBookshelfSettings,
                    onDiscover = onDiscover
                )

                is UiState.Success -> BookshelfContent(
                    continueReading = continueReading,
                    books = s.books,
                    isEmpty = false,
                    chrome = chrome,
                    onOpenBookFromCover = onOpenBookFromCover,
                    onOpenBookFromAction = onOpenBookFromAction,
                    onCoverView = { vm.setViewMode(BookshelfViewMode.COVER) },
                    onListView = { vm.setViewMode(BookshelfViewMode.LIST) },
                    onToggleFilter = { vm.toggleFilter() },
                    onFilterGroup = { vm.setFilterGroup(it) },
                    onFilterSort = { vm.setFilterSort(it) },
                    onFilterValue = { vm.setFilterValue(it) },
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onBookshelfSettings = onBookshelfSettings,
                    onDiscover = onDiscover
                )
            }
        }

        if (chrome.isMoreMenuOpen) {
            BookshelfMoreLayer(
                onDismiss = { vm.setMoreMenuOpen(false) },
                onBookBatchManagement = onBookBatchManagement,
                onGroupManagement = onGroupManagement,
                onLocalImport = onLocalImport
            )
        }
    }
}

@Composable
private fun BookshelfTopBar(onSearch: () -> Unit, onMore: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "书架",
            style = ReaderTextStyles.appBarTitle,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TopIconButton(
                iconRes = R.drawable.reader_ic_search,
                contentDescription = "搜索",
                onClick = onSearch
            )
            TopIconButton(
                iconRes = R.drawable.reader_ic_more,
                contentDescription = "更多",
                onClick = onMore
            )
        }
    }
}

@Composable
private fun TopIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BookshelfContent(
    continueReading: Book?,
    books: List<Book>,
    isEmpty: Boolean,
    chrome: BookshelfChromeState,
    onOpenBookFromCover: (Book) -> Unit,
    onOpenBookFromAction: (Book) -> Unit,
    onCoverView: () -> Unit,
    onListView: () -> Unit,
    onToggleFilter: () -> Unit,
    onFilterGroup: (String) -> Unit,
    onFilterSort: (String) -> Unit,
    onFilterValue: (String) -> Unit,
    onSearch: () -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onDiscover: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 19.dp,
            top = 8.dp,
            end = 19.dp,
            bottom = 116.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isEmpty && continueReading != null) {
            item {
                ContinueReadingCard(
                    book = continueReading,
                    onCoverClick = { onOpenBookFromCover(continueReading) },
                    onContinue = { onOpenBookFromAction(continueReading) }
                )
            }
        }
        item {
            BookshelfShelfSection(
                books = books,
                isEmpty = isEmpty,
                chrome = chrome,
                onOpenBookFromCover = onOpenBookFromCover,
                onCoverView = onCoverView,
                onListView = onListView,
                onToggleFilter = onToggleFilter,
                onFilterGroup = onFilterGroup,
                onFilterSort = onFilterSort,
                onFilterValue = onFilterValue,
                onSearch = onSearch,
                onLocalImport = onLocalImport,
                onBookshelfSettings = onBookshelfSettings,
                onDiscover = onDiscover
            )
        }
    }
}

@Composable
private fun BookshelfShelfSection(
    books: List<Book>,
    isEmpty: Boolean,
    chrome: BookshelfChromeState,
    onOpenBookFromCover: (Book) -> Unit,
    onCoverView: () -> Unit,
    onListView: () -> Unit,
    onToggleFilter: () -> Unit,
    onFilterGroup: (String) -> Unit,
    onFilterSort: (String) -> Unit,
    onFilterValue: (String) -> Unit,
    onSearch: () -> Unit,
    onLocalImport: () -> Unit,
    onBookshelfSettings: () -> Unit,
    onDiscover: () -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isEmpty) 9.dp else 10.dp)
        ) {
            BookshelfSectionHeader(
                viewMode = chrome.viewMode,
                actionsEnabled = !isEmpty,
                filterActive = chrome.filter.isActive,
                onCoverView = onCoverView,
                onListView = onListView,
                onToggleFilter = onToggleFilter,
                onDisplaySettings = onBookshelfSettings
            )
            if (isEmpty) {
                EmptyState(
                    onSearch = onSearch,
                    onLocalImport = onLocalImport,
                    onDiscover = onDiscover,
                    onBookshelfSettings = onBookshelfSettings
                )
            } else {
                BookGrid(
                    books = books,
                    viewMode = chrome.viewMode,
                    onOpenBookFromCover = onOpenBookFromCover
                )
            }
        }

        if (!isEmpty && chrome.filter.isOpen) {
            BookshelfFilterPopover(
                filter = chrome.filter,
                onGroup = onFilterGroup,
                onSort = onFilterSort,
                onFilter = onFilterValue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp)
            )
        }
    }
}

@Composable
private fun BookshelfSectionHeader(
    viewMode: BookshelfViewMode,
    actionsEnabled: Boolean,
    filterActive: Boolean,
    onCoverView: () -> Unit,
    onListView: () -> Unit,
    onToggleFilter: () -> Unit,
    onDisplaySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
            SectionIconButton(
                iconRes = R.drawable.reader_ic_grid,
                contentDescription = "封面视图",
                selected = viewMode == BookshelfViewMode.COVER,
                enabled = actionsEnabled,
                onClick = onCoverView
            )
            SectionIconButton(
                iconRes = R.drawable.reader_ic_list,
                contentDescription = "列表视图",
                selected = viewMode == BookshelfViewMode.LIST,
                enabled = actionsEnabled,
                onClick = onListView
            )
            SectionIconButton(
                iconRes = R.drawable.reader_ic_filter,
                contentDescription = "书架筛选",
                selected = filterActive,
                enabled = actionsEnabled,
                onClick = onToggleFilter
            )
            SectionIconButton(
                iconRes = R.drawable.reader_ic_gear,
                contentDescription = "书架显示设置",
                onClick = onDisplaySettings
            )
        }
    }
}

@Composable
private fun SectionIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    val extra = readerExtraColors()
    val color = when {
        !enabled -> extra.muted.copy(alpha = 0.42f)
        selected -> MaterialTheme.colorScheme.primary
        else -> extra.navInactive
    }
    val clickModifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier

    Box(
        modifier = Modifier
            .size(34.dp)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BookGrid(
    books: List<Book>,
    viewMode: BookshelfViewMode,
    onOpenBookFromCover: (Book) -> Unit
) {
    when (viewMode) {
        BookshelfViewMode.COVER -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            books.chunked(3).forEach { rowBooks ->
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    rowBooks.forEach { book ->
                        BookCard(
                            book = book,
                            viewMode = viewMode,
                            onClick = { onOpenBookFromCover(book) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowBooks.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        BookshelfViewMode.LIST -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            books.forEach { book ->
                BookCard(
                    book = book,
                    viewMode = viewMode,
                    onClick = { onOpenBookFromCover(book) }
                )
            }
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    viewMode: BookshelfViewMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (viewMode) {
        BookshelfViewMode.COVER -> CoverBookCard(book = book, onClick = onClick, modifier = modifier)
        BookshelfViewMode.LIST -> ListBookCard(book = book, onClick = onClick, modifier = modifier)
    }
}

@Composable
private fun CoverBookCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .shadow(elevation = 4.dp, shape = ReaderShapes.md, clip = false)
                .background(color = extra.surfaceSoft, shape = ReaderShapes.md)
                .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.name.take(1),
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
            color = extra.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ListBookCard(book: Book, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 66.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 60.dp)
                .shadow(elevation = 3.dp, shape = ReaderShapes.sm, clip = false)
                .background(color = extra.surfaceSoft, shape = ReaderShapes.sm)
                .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = book.name.take(1),
                style = ReaderTextStyles.continueLabel,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
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
}

@Composable
private fun ContinueReadingCard(
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
        border = BorderStroke(1.dp, extra.hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 78.dp)
                    .background(color = extra.surfaceSoft, shape = ReaderShapes.sm)
                    .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.sm)
                    .clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = book.name.take(1),
                    style = ReaderTextStyles.continueTitle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "继续阅读",
                    style = ReaderTextStyles.continueLabel,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.name,
                    style = ReaderTextStyles.continueTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = ReaderTextStyles.continueAuthor,
                    color = extra.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 74.dp, minHeight = 40.dp)
                    .background(color = MaterialTheme.colorScheme.primary, shape = ReaderShapes.pill)
                    .clickable(onClick = onContinue)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "阅读",
                    style = ReaderTextStyles.continueAction,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun BookshelfFilterPopover(
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
            .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f), shape = ReaderShapes.lg)
            .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.lg)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterOptionGroup(
            title = "分组",
            options = listOf("全部", "默认", "本地书", "追更"),
            selected = filter.group,
            onSelect = onGroup
        )
        FilterOptionGroup(
            title = "排序",
            options = listOf("最近更新", "阅读进度", "书名", "作者"),
            selected = filter.sort,
            onSelect = onSort
        )
        FilterOptionGroup(
            title = "筛选",
            options = listOf("全部", "未读", "已完结", "更新失败"),
            selected = filter.filter,
            onSelect = onFilter
        )
    }
}

@Composable
private fun FilterOptionGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = ReaderTextStyles.tabLabel,
            color = extra.muted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    text = option,
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val extra = readerExtraColors()
    val background = if (selected) MaterialTheme.colorScheme.primary else extra.metaBackground.copy(alpha = 0.72f)
    val border = if (selected) MaterialTheme.colorScheme.primary else extra.hairline
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 30.dp)
            .background(color = background, shape = ReaderShapes.pill)
            .border(width = 1.dp, color = border, shape = ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = ReaderTextStyles.tabLabel, color = content, maxLines = 1)
    }
}

@Composable
private fun EmptyState(
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
            .background(color = MaterialTheme.colorScheme.surface, shape = ReaderShapes.md)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = ReaderShapes.md)
            .padding(horizontal = 18.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EmptyVisual()
            Text(
                text = "书架还是空的",
                style = ReaderTextStyles.emptyHeading,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "添加网络书籍或导入本地文件后，会在这里显示继续阅读和书架内容。",
                style = ReaderTextStyles.emptyBody,
                color = extra.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                EmptyActionButton(
                    iconRes = R.drawable.reader_ic_search,
                    title = "搜索书籍",
                    meta = "按书名、作者或关键词查找",
                    primary = true,
                    onClick = onSearch
                )
                EmptyActionButton(
                    iconRes = R.drawable.reader_ic_folder,
                    title = "导入本地书",
                    meta = "添加本机文件到书架",
                    onClick = onLocalImport
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmptyHintButton(
                    iconRes = R.drawable.reader_ic_sparkle,
                    text = "去发现",
                    onClick = onDiscover
                )
                EmptyHintButton(
                    iconRes = R.drawable.reader_ic_gear,
                    text = "书架设置",
                    onClick = onBookshelfSettings
                )
            }
        }
    }
}

@Composable
private fun EmptyVisual() {
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
                .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape = ReaderShapes.sm)
                .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.sm)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 8.dp)
                .size(width = 72.dp, height = 52.dp)
                .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape = ReaderShapes.sm)
                .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.sm)
        )
        Box(
            modifier = Modifier
                .size(62.dp)
                .shadow(elevation = 6.dp, shape = ReaderShapes.lg, clip = false)
                .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = ReaderShapes.lg)
                .border(width = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), shape = ReaderShapes.lg),
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
private fun EmptyActionButton(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    primary: Boolean = false,
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
            .background(color = background, shape = ReaderShapes.md)
            .border(width = 1.dp, color = border, shape = ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color = Color.White.copy(alpha = 0.38f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ReaderTextStyles.continueAction,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = ReaderTextStyles.tabLabel,
                color = content.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHintButton(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(color = extra.metaBackground.copy(alpha = 0.9f), shape = ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(14.dp)
        )
        Text(text = text, style = ReaderTextStyles.tabLabel, color = extra.controlInk, maxLines = 1)
    }
}

@Composable
private fun BookshelfMoreLayer(
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
                .background(Color(0x2E1F1B17))
                .clickable(onClick = onDismiss)
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 66.dp, end = 18.dp)
                .width(238.dp)
                .shadow(elevation = 18.dp, shape = ReaderShapes.lg, clip = false)
                .background(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), shape = ReaderShapes.lg)
                .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.lg)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "书架更多操作",
                style = ReaderTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            MoreMenuButton(
                iconRes = R.drawable.reader_ic_check,
                title = "批量管理",
                meta = "选择多本书后移动或删除",
                onClick = {
                    onDismiss()
                    onBookBatchManagement()
                }
            )
            MoreMenuButton(
                iconRes = R.drawable.reader_ic_people,
                title = "分组管理",
                meta = "编辑书架分组与归属",
                onClick = {
                    onDismiss()
                    onGroupManagement()
                }
            )
            MoreMenuButton(
                iconRes = R.drawable.reader_ic_book_open,
                title = "本地书导入",
                meta = "导入本地文件到书架",
                onClick = {
                    onDismiss()
                    onLocalImport()
                }
            )
        }
    }
}

@Composable
private fun MoreMenuButton(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ReaderTextStyles.continueAction,
                color = extra.controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = ReaderTextStyles.tabLabel,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
