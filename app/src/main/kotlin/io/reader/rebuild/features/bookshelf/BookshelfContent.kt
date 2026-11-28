package io.reader.rebuild.features.bookshelf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconMapping
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun BookshelfContent(
    books: List<BookshelfEntryData>,
    onBook: (BookshelfEntryData) -> Unit = {},
    onRemove: (BookshelfEntryData) -> Unit = {},
    side: (@Composable () -> Unit)? = null,
    onImport: () -> Unit = {},
    onSearch: () -> Unit = {},
    initialCover: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= Metrics.wideBreakpoint.dp
        val contentWidth = if (wide) (maxWidth * .62f).coerceIn(420.dp, 760.dp) else maxWidth
        val narrow = contentWidth < Metrics.narrowBreakpoint.dp
        var coverMode by remember { mutableStateOf(initialCover) }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(contentWidth).fillMaxHeight()) {
                ShelfSection(books, coverMode, { coverMode = it }, narrow, onImport, onSearch, onBook)
                BookshelfBody(books, wide, coverMode, onBook, onRemove)
            }
            if (wide) Box(Modifier.weight(1f).fillMaxHeight()) {
                side?.invoke() ?: ReaderPageState(PageState.Empty("选择一本书查看详情"))
            }
        }
    }
}

@Composable
private fun ShelfSection(books: List<BookshelfEntryData>, cover: Boolean, onCover: (Boolean) -> Unit,
    narrow: Boolean, onImport: () -> Unit, onSearch: () -> Unit, onBook: (BookshelfEntryData) -> Unit) {
    val colors = LocalReaderColors.current
    val compactSwitch = narrow && LocalDensity.current.fontScale >= 1.3f
    Column(Modifier.fillMaxWidth().padding(horizontal = Metrics.screenInset.dp)) {
        books.firstOrNull { it.readingPosition != null }?.let { ContinueReadingCard(it, { onBook(it) }) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("我的书架", style = readerTextStyle(Metrics.sectionHeading), color = colors.primaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" · ${books.size} 本", style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            ViewModeSwitch(cover, { onCover(false) }, { onCover(true) }, books.isNotEmpty(), showLabels = !compactSwitch)
            IconButton(onClick = onSearch, enabled = books.isNotEmpty()) { ReaderIcon(ReaderIconRegistry.filter, "筛选") }
        }
        if (books.isEmpty()) EmptyShelfCard(onImport, onSearch)
    }
}

@Composable
private fun ViewModeSwitch(cover: Boolean, onList: () -> Unit, onCover: () -> Unit, enabled: Boolean, showLabels: Boolean) {
    val colors = LocalReaderColors.current
    val pillShape = RoundedCornerShape(Metrics.pillRadius.dp)
    val laneWidth = if (showLabels) 62.dp else 30.dp
    val laneProgress by animateFloatAsState(
        targetValue = if (cover) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
        label = "shelfViewModeThumb",
    )
    Box(Modifier.width(laneWidth * 2 + 6.dp).height(34.dp).alpha(if (enabled) 1f else .42f)) {
        // Bottom bevel catch beneath the recessed groove.
        Box(Modifier.matchParentSize().offset(y = 0.75.dp).background(colors.segmentTrackBevel, pillShape))
        // Recessed groove track (darker than page canvas with subtle top inner shade).
        Box(
            Modifier.matchParentSize()
                .clip(pillShape)
                .background(colors.segmentTrack)
                .background(Brush.verticalGradient(0f to colors.segmentTrackTopShade, 0.38f to Color.Transparent))
                .border(0.75.dp, colors.segmentTrackBorder, pillShape),
        )
        // Animated elevated sliding thumb.
        Box(Modifier.fillMaxSize().padding(3.dp)) {
            Box(
                Modifier.width(laneWidth)
                    .height(28.dp)
                    .offset { IntOffset((laneProgress * laneWidth.toPx()).roundToInt(), 0) }
                    .shadow(
                        elevation = 2.dp,
                        shape = pillShape,
                        ambientColor = colors.segmentThumbShadow,
                        spotColor = colors.segmentThumbShadow,
                    )
                    .background(colors.segmentThumb, pillShape)
                    .border(0.5.dp, colors.segmentThumbBorder, pillShape),
            )
        }
        // Interactive segment slots.
        Row(Modifier.fillMaxSize().padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            ViewModeItem("列表", ReaderIconRegistry.directory, !cover, enabled, showLabels, laneWidth, pillShape, onList)
            ViewModeItem("封面", ReaderIconRegistry.shelf, cover, enabled, showLabels, laneWidth, pillShape, onCover)
        }
    }
}

@Composable
private fun ViewModeItem(
    label: String,
    icon: ReaderIconMapping,
    active: Boolean,
    enabled: Boolean,
    showLabels: Boolean,
    laneWidth: androidx.compose.ui.unit.Dp,
    pillShape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val colors = LocalReaderColors.current
    Row(
        Modifier.width(laneWidth).height(28.dp).clip(pillShape).clickable(enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderIcon(icon, label, size = if (showLabels) 16.dp else 15.dp, tint = if (active) colors.accent else colors.secondaryText)
        if (showLabels) {
            Text(label, Modifier.padding(start = 3.dp), color = if (active) colors.accent else colors.secondaryText,
                style = readerTextStyle(Metrics.shelfProgress))
        }
    }
}

@Composable
private fun EmptyShelfCard(onImport: () -> Unit, onSearch: () -> Unit) {
    val colors = LocalReaderColors.current
    Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(Metrics.cardRadius.dp),
        color = colors.surface, border = BorderStroke(1.dp, colors.outline)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderIcon(ReaderIconRegistry.shelf, "空书架", size = 42.dp, tint = colors.accent)
            Text("书架还是空的", style = readerTextStyle(Metrics.pageTitle), textAlign = TextAlign.Center)
            Text("导入本地书籍或通过搜索加入书架。", color = colors.secondaryText,
                style = readerTextStyle(Metrics.rowDetail), textAlign = TextAlign.Center)
            Button(onClick = onImport, Modifier.fillMaxWidth()) { Text("导入本地书籍") }
            OutlinedButton(onClick = onSearch, Modifier.fillMaxWidth()) { Text("搜索书籍") }
        }
    }
}

@Composable
private fun ContinueReadingCard(book: BookshelfEntryData, onClick: () -> Unit) {
    val colors = LocalReaderColors.current
    val progress = (book.readProgress ?: 0L).coerceIn(0L, 100L)
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp).clickable(onClick = onClick), shape = RoundedCornerShape(Metrics.cardRadius.dp),
        color = colors.surface, border = BorderStroke(1.dp, colors.outline)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookCover(book.coverUrl, Modifier.size(56.dp, 78.dp), Metrics.cardRadius)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, style = readerTextStyle(Metrics.shelfTitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author.ifBlank { "作者未知" }, color = colors.secondaryText, style = readerTextStyle(Metrics.shelfAuthor))
                Text(book.currentChapterTitle ?: "尚未阅读", color = colors.secondaryText, style = readerTextStyle(Metrics.shelfChapter),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$progress%", color = colors.accent, style = readerTextStyle(Metrics.shelfProgress))
            }
            Button(onClick = onClick, shape = RoundedCornerShape(Metrics.pillRadius.dp)) { Text("继续") }
        }
    }
}

@Composable
private fun BookshelfBody(books: List<BookshelfEntryData>, wide: Boolean, coverMode: Boolean,
    onBook: (BookshelfEntryData) -> Unit, onRemove: (BookshelfEntryData) -> Unit) {
    if (books.isEmpty()) return
    // The default remains list mode. The section switcher and grid share the same production data projection.
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Metrics.screenInset.dp)) {
        if (coverMode) {
            LazyVerticalGrid(GridCells.Fixed(if (wide) 4 else 3), Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(books, key = { "cover-${it.sourceId}-${it.bookId}" }) { book -> CoverBookTile(book, { onBook(book) }) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(books, key = { "list-${it.sourceId}-${it.bookId}" }) { book ->
                    ShelfBookRow(book, maxWidth < Metrics.narrowBreakpoint.dp, { onBook(book) }, { onRemove(book) })
                }
            }
        }
    }
}

@Composable
private fun CoverBookTile(book: BookshelfEntryData, onClick: () -> Unit) {
    val colors = LocalReaderColors.current
    Column(Modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BookCover(book.coverUrl, Modifier.fillMaxWidth().aspectRatio(3f / 4.1f), Metrics.cardRadius)
        Text(book.title, style = readerTextStyle(Metrics.shelfTitle), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author.ifBlank { "作者未知" }, color = colors.secondaryText, style = readerTextStyle(Metrics.shelfAuthor), maxLines = 1)
        Text("${(book.readProgress ?: 0L).coerceIn(0L, 100L)}%", color = colors.accent, style = readerTextStyle(Metrics.shelfProgress))
    }
}

@Composable
private fun ShelfBookRow(book: BookshelfEntryData, narrow: Boolean, open: () -> Unit, remove: () -> Unit) {
    val colors = LocalReaderColors.current
    val scale = LocalDensity.current.fontScale
    val height = ((Metrics.shelfCoverHeight + 8) * scale.coerceAtLeast(1f)).dp
    val progress = (book.readProgress ?: 0L).coerceIn(0L, 100L)
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().clickable(onClick = open), shape = RoundedCornerShape(Metrics.cardRadius.dp),
        color = colors.surface, contentColor = colors.primaryText, border = BorderStroke(1.dp, colors.outline)) {
        Row(Modifier.padding(Metrics.shelfCardPadding.dp).height(height), horizontalArrangement = Arrangement.spacedBy(Metrics.shelfListColumnSpacing.dp)) {
            BookCover(book.coverUrl, Modifier.width(Metrics.shelfCoverWidth.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(book.title, Modifier.padding(end = 38.dp), style = readerTextStyle(Metrics.shelfTitle),
                        maxLines = if (narrow || scale >= 1.3f) Metrics.shelfTitleMaxLines else 1, overflow = TextOverflow.Ellipsis)
                    Text(book.author.ifBlank { "作者未知" }, Modifier.padding(end = 34.dp), color = colors.secondaryText,
                        style = readerTextStyle(Metrics.shelfAuthor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(book.currentChapterTitle?.let { "读到 · $it" } ?: "尚未阅读", color = colors.secondaryText,
                        style = readerTextStyle(Metrics.shelfChapter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (book.sourceId != "local" && !book.lastChapter.isNullOrBlank()) Text("最新 · ${book.lastChapter}",
                        color = colors.secondaryText, style = readerTextStyle(Metrics.shelfChapter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${book.sourceName ?: if (book.sourceId == "local") "本地书籍" else book.sourceId} · ${if (progress == 0L) "未读" else "$progress%"}",
                        color = colors.secondaryText, style = readerTextStyle(Metrics.shelfProgress), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().height(Metrics.shelfProgressStroke.dp),
                        color = colors.accent, trackColor = colors.progressTrack, drawStopIndicator = {})
                }
                Box(Modifier.align(Alignment.TopEnd).size(Metrics.minimumActionTarget.dp)) {
                    ReaderAction(ReaderIconRegistry.more, "书籍更多操作", { menu = !menu })
                    if (menu) Surface(Modifier.align(Alignment.TopEnd).padding(top = 40.dp), shadowElevation = 6.dp,
                        shape = RoundedCornerShape(Metrics.inputRadius.dp), color = colors.surface) {
                        Column(Modifier.padding(4.dp)) {
                            Text("查看详情", Modifier.clickable { menu = false; open() }.padding(12.dp), style = readerTextStyle(Metrics.rowLabel))
                            Text("移出书架", Modifier.clickable { menu = false; remove() }.padding(12.dp), style = readerTextStyle(Metrics.rowLabel))
                        }
                    }
                }
            }
        }
    }
}
