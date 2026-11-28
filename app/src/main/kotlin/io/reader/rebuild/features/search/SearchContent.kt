package io.reader.rebuild.features.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.library.BookReference

@Composable
internal fun SearchContent(
    query: String,
    history: List<String>,
    books: List<BookReference>,
    running: Boolean,
    stopped: Boolean = false,
    onQuery: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    onStop: () -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onBook: (BookReference) -> Unit = {},
    status: String? = null,
) {
    val colors = LocalReaderColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = Metrics.screenInset.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, onQuery, Modifier.weight(1f), singleLine = true,
                label = { Text("书名或作者") }, leadingIcon = { ReaderIcon(ReaderIconRegistry.search, null, size = 20.dp) },
                textStyle = readerTextStyle(Metrics.searchFieldText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (!running && query.isNotBlank()) onSearch(query) }))
            Button({ onSearch(query) }, enabled = !running && query.isNotBlank()) { Text("搜索") }
        }
        if (history.isNotEmpty() && books.isEmpty() && !running) {
            Text("搜索历史", Modifier.padding(top = Metrics.spaceMedium.dp), style = readerTextStyle(Metrics.sectionHeading))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                history.take(10).forEach { term ->
                    AssistChip({ onSearch(term) }, label = { Text(term, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        } else if (history.isEmpty() && books.isEmpty() && !running && onRetry == null && status == null) {
            ReaderPageState(
                PageState.Empty("输入书名或作者，搜索已启用书源"),
                Modifier.padding(top = Metrics.spaceLarge.dp),
            )
        }
        if (running || onRetry != null || status != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status ?: when {
                    running && stopped -> "已停止展示 · 等待原搜索结束"
                    running -> "正在搜索书源"
                    else -> "搜索已结束"
                }, Modifier.weight(1f).padding(vertical = Metrics.spaceSmall.dp), color = colors.secondaryText,
                    style = readerTextStyle(Metrics.rowDetail))
                if (running && !stopped) TextButton(onStop) { Text("停止展示") }
                if (!running && onRetry != null) TextButton(onRetry) { Text("重试上次搜索") }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
            items(books, key = { "${it.sourceId}\u0000${it.bookId}" }) { book ->
                Surface(Modifier.fillMaxWidth().clickable { onBook(book) },
                    shape = RoundedCornerShape(Metrics.cardRadius.dp), color = colors.surface,
                    contentColor = colors.primaryText, border = BorderStroke(1.dp, colors.outline)) {
                    Row(Modifier.padding(Metrics.cardPadding.dp), horizontalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
                        BookCover(book.coverUrl, Modifier.size(Metrics.searchResultCoverCompactMaxWidth.dp,
                            (Metrics.searchResultCoverCompactMaxWidth * Metrics.searchResultCoverAspect).dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp)) {
                            Text(book.title, style = readerTextStyle(Metrics.shelfTitle), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(book.author?.takeIf(String::isNotBlank), book.displaySource).joinToString(" · "),
                                style = readerTextStyle(Metrics.searchResultAuthor), color = colors.secondaryText,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            book.lastChapter?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = readerTextStyle(Metrics.searchResultChapter), color = colors.accent) }
                            book.intro?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = readerTextStyle(Metrics.searchResultIntro), color = colors.secondaryText) }
                        }
                    }
                }
            }
        }
    }
}
