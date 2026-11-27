package io.reader.rebuild.features.bookshelf

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun BookshelfContent(
    books: List<BookshelfEntryData>,
    onBook: (BookshelfEntryData) -> Unit = {},
    onRemove: (BookshelfEntryData) -> Unit = {},
    side: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val wide = availableWidth >= Metrics.wideBreakpoint.dp
        Row(Modifier.fillMaxSize()) {
            val list = if (wide) Modifier.width((availableWidth * .46f).coerceIn(292.dp, 420.dp)) else Modifier.fillMaxWidth()
            Box(list.fillMaxHeight()) {
                if (books.isEmpty()) ReaderPageState(PageState.Empty("书架为空。导入本地图书，或搜索在线书籍。"))
                else LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Metrics.screenInset.dp),
                    verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
                ) {
                    items(books, key = { listOf(it.sourceId, it.bookId).joinToString("\u0000") }) { book ->
                        ShelfBookRow(book, availableWidth < Metrics.narrowBreakpoint.dp, { onBook(book) }, { onRemove(book) })
                    }
                }
            }
            if (wide) Box(Modifier.weight(1f).fillMaxHeight()) {
                if (side != null) side()
                else ReaderPageState(PageState.Empty("选择一本书查看详情"))
            }
        }
    }
}

@Composable
private fun ShelfBookRow(book: BookshelfEntryData, narrow: Boolean, open: () -> Unit, remove: () -> Unit) {
    val colors = LocalReaderColors.current
    val scale = LocalDensity.current.fontScale
    val height = ((Metrics.shelfCoverHeight + 8) * scale.coerceAtLeast(1f)).dp
    val progress = (book.readProgress ?: 0L).coerceIn(0L, 100L)
    var menu by remember { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = open),
        shape = RoundedCornerShape(Metrics.cardRadius.dp), color = colors.surface,
        contentColor = colors.primaryText, border = BorderStroke(1.dp, colors.outline),
    ) {
        Row(
            Modifier.padding(Metrics.shelfCardPadding.dp).height(height),
            horizontalArrangement = Arrangement.spacedBy(Metrics.shelfListColumnSpacing.dp),
        ) {
            BookCover(book.coverUrl, Modifier.width(Metrics.shelfCoverWidth.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(book.title, Modifier.padding(end = 38.dp), style = readerTextStyle(Metrics.shelfTitle),
                        maxLines = if (narrow || scale >= 1.3f) Metrics.shelfTitleMaxLines else 1, overflow = TextOverflow.Ellipsis)
                    Text(book.author.ifBlank { "作者未知" }, Modifier.padding(end = 34.dp),
                        color = colors.secondaryText, style = readerTextStyle(Metrics.shelfAuthor), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(book.currentChapterTitle?.let { "读到 · $it" } ?: "尚未阅读",
                        color = colors.secondaryText, style = readerTextStyle(Metrics.shelfChapter), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (book.sourceId != "local" && !book.lastChapter.isNullOrBlank()) {
                        Text("最新 · ${book.lastChapter}", color = colors.secondaryText,
                            style = readerTextStyle(Metrics.shelfChapter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${book.sourceName ?: if (book.sourceId == "local") "本地" else "在线"} · 已读 $progress%",
                        color = colors.secondaryText, style = readerTextStyle(Metrics.shelfProgress), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(Metrics.shelfProgressStroke.dp),
                        color = colors.accent, trackColor = colors.progressTrack, drawStopIndicator = {})
                }
                Box(Modifier.align(Alignment.TopEnd).size(Metrics.minimumActionTarget.dp)) {
                    ReaderAction(ReaderIconRegistry.more, "书籍更多操作", { menu = true })
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("查看详情") }, onClick = { menu = false; open() })
                        DropdownMenuItem(text = { Text("移出书架") }, onClick = { menu = false; remove() })
                    }
                }
            }
        }
    }
}
