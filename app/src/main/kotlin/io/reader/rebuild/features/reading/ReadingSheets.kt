package io.reader.rebuild.features.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.BookmarkData
import com.reader.core.protocol.M2ChangeBookSourceCandidate
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.library.BookReference
import kotlinx.coroutines.launch

@Composable
internal fun ReadingDirectorySheet(entries: List<DirectoryEntry>, index: Long,
    onSelect: (Long) -> Unit, onRefresh: (suspend () -> Unit)? = null) {
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = Metrics.screenInset.dp).heightIn(max = 520.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("目录", Modifier.weight(1f), style = readerTextStyle(Metrics.pageTitle))
            if (onRefresh != null) ReaderAction(ReaderIconRegistry.refresh, "刷新目录", {
                refreshing = true
                scope.launch {
                    error = readerResult { onRefresh() }.exceptionOrNull()?.let { "目录刷新失败，已保留原目录" }
                    refreshing = false
                }
            }, enabled = !refreshing)
        }
        error?.let { Text(it, style = readerTextStyle(Metrics.rowDetail)) }
        DirectoryContent(entries, index, onSelect)
    }
}

@Composable
internal fun ReadingBookmarkSheet(chapter: ReaderChapter, journey: ReadingJourney,
    onAdd: suspend () -> Unit, onJump: (BookmarkData) -> Unit) {
    var bookmarks by remember { mutableStateOf<List<BookmarkData>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            readerResult { action(); journey.bookmarks(chapter.book) }
                .onSuccess { bookmarks = it; error = null }
                .onFailure { error = it.localizedMessage ?: "书签操作失败" }
            busy = false
        }
    }
    LaunchedEffect(chapter.book.key) { load {} }
    Column(Modifier.padding(horizontal = Metrics.screenInset.dp).heightIn(max = 520.dp)) {
        Row {
            Text("书签", Modifier.weight(1f), style = readerTextStyle(Metrics.pageTitle))
            TextButton({ load(onAdd) }, enabled = !busy) { Text("添加当前位置") }
        }
        error?.let { Text(it, style = readerTextStyle(Metrics.rowDetail)) }
        val items = bookmarks
        when {
            items == null && busy -> ReaderPageState(PageState.Loading)
            items.orEmpty().isEmpty() && error == null -> ReaderPageState(PageState.Empty("还没有书签"))
            else -> LazyColumn {
                items(items.orEmpty(), key = { it.time }) { bookmark ->
                    Row(Modifier.fillMaxWidth()) {
                        TextButton({ onJump(bookmark) }, Modifier.weight(1f)) {
                            Column {
                                Text(bookmark.chapterName, style = readerTextStyle(Metrics.rowLabel))
                                Text(bookmark.bookText, style = readerTextStyle(Metrics.rowDetail), maxLines = 2)
                            }
                        }
                        ReaderAction(ReaderIconRegistry.delete, "删除书签", { load { journey.deleteBookmark(bookmark) } }, !busy)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadingSourceSheet(book: BookReference, journey: ReadingJourney, busy: Boolean,
    onSwitch: (M2ChangeBookSourceCandidate) -> Unit) {
    var query by remember { mutableStateOf(book.title) }
    var result by remember { mutableStateOf<Result<List<M2ChangeBookSourceCandidate>>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun search() {
        if (searching || busy) return
        searching = true
        val term = query
        scope.launch {
            result = readerResult { journey.candidates(book, term) }
            searching = false
        }
    }
    LaunchedEffect(book.key) { search() }
    ReadingSourceSheetContent(query, !searching && !busy, { query = it }, ::search) {
        val current = result
        when {
            busy || searching || current == null -> ReaderPageState(PageState.Loading)
            current.isFailure -> ReaderPageState(PageState.Failure("无法查找其他来源", 0), onRetry = { search() })
            else -> SourceSwitchContent(current.getOrThrow(), onSwitch)
        }
    }
}

@Composable
internal fun ReadingSourceSheetContent(
    query: String,
    canSearch: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = Metrics.screenInset.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("切换书源", style = readerTextStyle(Metrics.pageTitle))
        Row {
            OutlinedTextField(query, onQuery, Modifier.weight(1f), label = { Text("候选书源搜索") }, singleLine = true)
            TextButton(onSearch, enabled = canSearch) { Text("搜索") }
        }
        content()
    }
}

@Composable
internal fun PendingSourceSwitchSheet(busy: Boolean, onRollback: () -> Unit) {
    Column(Modifier.padding(Metrics.screenInset.dp)) {
        Text("原换源事务尚未结案", style = readerTextStyle(Metrics.pageTitle))
        Text("仍可核对原操作或撤回到原书源。", style = readerTextStyle(Metrics.rowDetail))
        Button(onRollback, enabled = !busy) { Text("撤回未结案的换源") }
    }
}
