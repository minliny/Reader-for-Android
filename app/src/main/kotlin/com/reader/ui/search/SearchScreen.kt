package com.reader.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reader.android.R
import com.reader.api.SearchBook
import com.reader.ui.bookshelf.BookshelfViewModel
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onBookClick: (SearchBook) -> Unit,
    vm: SearchViewModel = viewModel(),
    // P0-2: share the Activity-scoped BookshelfViewModel so search results can query
    // real shelf membership (replacing the hardcoded `title == "三体"` stub) and fire
    // `bookshelf.book.add` writes through the same Core-backed VM the bookshelf tab uses.
    bookshelfVm: BookshelfViewModel = viewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val shelfBookUrls by bookshelfVm.shelfBookUrls.collectAsStateWithLifecycle()

    val isAfter = state is SearchUiState.Success
    LibraryShellFrame(
        backTopBar = {
            SearchTopBar(
                title = "书籍搜索",
                onBack = onBack
            )
        },
        contentRegion = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                SearchInputBox(
                    query = query,
                    onQueryChange = vm::updateQuery,
                    onSearch = vm::search
                )
                if (isAfter) {
                    Spacer(Modifier.height(10.dp))
                    SearchScopeRow()
                }
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    when (val screenState = state) {
                        is SearchUiState.Success -> SearchResultsList(
                            results = screenState.results,
                            onBookClick = onBookClick,
                            shelfBookUrls = shelfBookUrls,
                            onAddToBookshelf = { book -> bookshelfVm.addToBookshelf(book) },
                            modifier = Modifier.fillMaxSize()
                        )

                        else -> SearchHomeState(
                            history = history,
                            onHistoryClick = { keyword ->
                                vm.updateQuery(keyword)
                            },
                            onClearHistory = vm::clearHistory
                        )
                    }
                }
            }
        },
        bottomActionHost = {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SearchBottomActions(
                    after = isAfter,
                    onPrimary = {
                        if (isAfter) {
                            vm.reset()
                        } else {
                            vm.search()
                        }
                    },
                    onSecondary = {
                        if (!isAfter) {
                            vm.updateQuery("")
                        }
                    }
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    )
}

@Composable
private fun SearchScopeRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("全部", "书名", "作者", "书源").forEachIndexed { index, label ->
            SearchSuggestionChip(
                text = label,
                modifier = Modifier.weight(1f),
                active = index == 0,
                onClick = {}
            )
        }
    }
}

@Composable
private fun SearchBottomActions(after: Boolean, onPrimary: () -> Unit, onSecondary: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SearchBottomButton(
            text = if (after) "重新搜索" else "开始搜索",
            primary = true,
            onClick = onPrimary,
            modifier = Modifier.weight(1f)
        )
        SearchBottomButton(
            text = if (after) "查看详情" else "清除历史",
            primary = false,
            onClick = onSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchTopBar(title: String, onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                contentDescription = "返回",
                tint = ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun SearchInputBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(colors.surface.copy(alpha = 0.58f), ReaderShapes.pill)
            .border(1.dp, extra.hairline, ReaderShapes.pill)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_search),
            contentDescription = null,
            tint = extra.controlInk,
            modifier = Modifier.size(24.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = colors.onBackground,
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.BOOK_TITLE.value,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    if (query.isBlank()) {
                        Text(
                            text = "搜索书名、作者、关键词",
                            style = TextStyle(
                                color = extra.muted,
                                fontFamily = FontFamily.Default,
                                fontSize = ReaderTypeToken.BOOK_TITLE.value,
                                lineHeight = 18.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SearchHomeState(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchHistoryHeader(onClear = onClearHistory)
        if (history.isEmpty()) {
            // P2: empty-history state — no rows to render. The demo contract renders
            // history rows from real user searches; with no history, the section is empty.
            Spacer(Modifier.height(0.dp))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.forEach { keyword ->
                    SearchHistoryRow(
                        title = keyword,
                        meta = "书名 · 全部",
                        onClick = { onHistoryClick(keyword) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        style = ReaderTextStyles.sectionTitle.copy(fontSize = ReaderTypeToken.TOP_BAR_TITLE.value),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SearchHistoryHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "搜索历史",
            style = ReaderTextStyles.sectionTitle.copy(fontSize = ReaderTypeToken.TOP_BAR_TITLE.value),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "清空",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.BOOK_META.value,
                lineHeight = 16.sp,
                fontWeight = FontWeight(800)
            ),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.clickable(onClick = onClear)
        )
    }
}

@Composable
private fun SearchHistoryRow(title: String, meta: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_search),
            contentDescription = null,
            tint = extra.muted,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight(700)
                ),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    lineHeight = 15.sp
                ),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "填入",
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.BOOK_META.value,
                lineHeight = 16.sp,
                fontWeight = FontWeight(800)
            ),
            color = colors.primary
        )
    }
}

@Composable
private fun SearchSuggestionChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .background(if (active) colors.primary else extra.metaBackground, ReaderShapes.pill)
            .border(1.dp, if (active) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable { onClick(text) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                lineHeight = 17.sp,
                fontWeight = FontWeight(800),
                textAlign = TextAlign.Center
            ),
            color = if (active) colors.onPrimary else colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchBottomButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .background(if (primary) colors.primary else colors.surface.copy(alpha = 0.86f), ReaderShapes.pill)
            .border(1.dp, if (primary) colors.primary else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                lineHeight = 16.sp,
                fontWeight = FontWeight(900)
            ),
            color = if (primary) colors.onPrimary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
    shelfBookUrls: Set<String>,
    onAddToBookshelf: (SearchBook) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchSectionTitle("搜索结果")
                Text(
                    text = "找到 ${results.size} 个结果 · 已标注书架状态",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = ReaderTypeToken.BOOK_META.value,
                        lineHeight = 16.sp
                    ),
                    color = readerExtraColors().muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        items(results, key = { "${it.origin}:${it.bookUrl}:${it.name}" }) { book ->
            SearchResultRow(
                book = book,
                inShelf = shelfBookUrls.contains(book.bookUrl),
                onClick = { onBookClick(book) },
                onAddToBookshelf = { onAddToBookshelf(book) }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    book: SearchBook,
    inShelf: Boolean,
    onClick: () -> Unit,
    onAddToBookshelf: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val title = book.name.ifBlank { "未命名书籍" }
    val author = book.author.ifBlank { "未知作者" }
    val origin = book.origin.ifBlank { "未知书源" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 86.dp)
            .background(colors.surface.copy(alpha = 0.78f), ReaderShapes.lg)
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchCoverTile(title)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.BOOK_TITLE.value,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight(700)
                ),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$author · $origin",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = ReaderTypeToken.ACTION_LABEL.value,
                    lineHeight = 15.sp
                ),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (book.lastChapter.isNotBlank()) {
                Text(
                    text = book.lastChapter,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = ReaderTypeToken.ACTION_LABEL.value,
                        lineHeight = 15.sp
                    ),
                    color = extra.infoLayer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        SearchStatusPill(if (inShelf) "已在书架" else "未加入")
        // P0-2: when not on shelf, the primary action adds the book via Core
        // `bookshelf.book.add`; when already on shelf, it opens the reader.
        SearchPrimaryAction(
            text = if (inShelf) "阅读" else "加入书架",
            onClick = if (inShelf) onClick else onAddToBookshelf
        )
    }
}

@Composable
private fun SearchCoverTile(title: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 64.dp)
            .background(extra.metaBackground, ReaderShapes.xs)
            .border(1.dp, extra.hairline, ReaderShapes.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = firstGlyph(title),
            style = ReaderTextStyles.bookTitle.copy(
                fontSize = ReaderTypeToken.READER_BODY.value,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            ),
            color = colors.primary,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchStatusPill(text: String) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .width(58.dp)
            .defaultMinSize(minHeight = 28.dp)
            .background(extra.metaBackground, ReaderShapes.pill)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
                lineHeight = 12.sp,
                fontWeight = FontWeight(800),
                textAlign = TextAlign.Center
            ),
            color = extra.forest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchInlineAction(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .defaultMinSize(minHeight = 32.dp)
            .background(colors.primary, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.BOOK_META.value,
                lineHeight = 14.sp,
                fontWeight = FontWeight(800)
            ),
            color = colors.onPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchPrimaryAction(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .width(64.dp)
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.ACTION_LABEL.value,
                lineHeight = 13.sp,
                fontWeight = FontWeight(900)
            ),
            color = colors.onPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchStatePage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val muted = readerExtraColors().muted
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (showProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = title,
            style = ReaderTextStyles.emptyHeading,
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = ReaderTextStyles.emptyBody,
            color = muted,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            SearchInlineAction(text = actionLabel, onClick = onAction)
        }
    }
}

private fun firstGlyph(text: String): String {
    val trimmed = text.trim()
    return trimmed.firstOrNull()?.toString() ?: "书"
}
