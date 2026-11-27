package io.reader.rebuild.features.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2CacheBookStatusData
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2CacheChapterStatusData
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import io.reader.rebuild.design.BookCover
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.ReaderTaskProgress
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.library.BookReference

internal data class DownloadRow(
    val book: BookReference,
    val status: M2CacheBookStatusData? = null,
    val working: Boolean = false,
    val detail: String? = null,
    val recovering: Boolean = false,
) {
    val key get() = "${book.sourceId}\u0000${book.bookId}"
    val startRange: List<Long>?
        get() {
            if (working || recovering) return null
            val chapters = status?.chapters.orEmpty().filter {
                it.navigable != false && it.state in startable
            }.sortedBy(M2CacheChapterStatusData::chapterIndex)
            val first = chapters.firstOrNull()?.chapterIndex ?: return null
            var limit = first + 1
            for (chapter in chapters.drop(1)) {
                if (chapter.chapterIndex != limit) break
                limit++
            }
            return listOf(first, limit)
        }

    companion object {
        private val startable = setOf(M2CacheChapterState.MISSING, M2CacheChapterState.CACHED,
            M2CacheChapterState.FAILED, M2CacheChapterState.CANCELLED)
    }
}

internal enum class DownloadAction(val title: String) { PAUSE("暂停"), RESUME("继续"), CANCEL("取消") }

@Composable
internal fun DownloadContent(
    books: List<DownloadRow>,
    summary: String?,
    state: PageState = PageState.Empty("暂没有在线书籍需要下载"),
    expandedByDefault: Boolean = true,
    canClean: Boolean = true,
    onStart: (DownloadRow) -> Unit = {},
    onControl: (DownloadRow, M2CacheChapterStatusData, DownloadAction) -> Unit = { _, _, _ -> },
    onPrepare: (DownloadRow) -> Unit = {},
    onClean: () -> Unit = {},
    onRetry: (Long) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val colors = LocalReaderColors.current
    var toggled by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Metrics.screenInset.dp).testTag("download.page")) {
        item("summary") {
            Surface(Modifier.padding(bottom = Metrics.spaceSmall.dp),
                color = colors.elevated, shape = RoundedCornerShape(Metrics.cardRadius.dp)) {
                Row(Modifier.fillMaxWidth().padding(Metrics.cardPadding.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("正文缓存", color = colors.primaryText, style = readerTextStyle(Metrics.downloadSectionLabel))
                        summary?.let { Text(it, color = colors.secondaryText, style = readerTextStyle(Metrics.downloadMetadata)) }
                    }
                    ReaderAction(Icons.refresh, "刷新下载状态", onRefresh)
                    TextButton(onClick = onClean, enabled = summary != null && canClean) {
                        Text("清理", style = readerTextStyle(Metrics.downloadControl))
                    }
                }
            }
        }
        if (books.isEmpty()) item("empty") { ReaderPageState(state, onRetry = onRetry) }
        for (book in books) {
            val expanded = expandedByDefault != (book.key in toggled)
            item(key = "book/${book.key}") {
                DownloadBookCard(book, expanded, {
                    toggled = ArrayList(if (book.key in toggled) toggled - book.key else toggled + book.key)
                }, onStart, onPrepare)
            }
            if (expanded) items(book.status?.chapters.orEmpty(), key = { "chapter/${book.key}/${it.chapterIndex}" }) { chapter ->
                Surface(color = colors.surface) {
                    Column(Modifier.padding(horizontal = Metrics.cardPadding.dp)) {
                        DownloadChapterRow(chapter) { onControl(book, chapter, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadBookCard(
    row: DownloadRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onStart: (DownloadRow) -> Unit,
    onPrepare: (DownloadRow) -> Unit,
) {
    val colors = LocalReaderColors.current
    Surface(color = colors.surface, shape = RoundedCornerShape(Metrics.cardRadius.dp)) {
        Column(Modifier.fillMaxWidth().padding(Metrics.cardPadding.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
                BookCover(row.book.coverUrl, Modifier.size(Metrics.downloadCoverWidth.dp, Metrics.downloadCoverHeight.dp),
                    Metrics.downloadCoverRadius)
                Column(Modifier.weight(1f)) {
                    Text(row.book.title, color = colors.primaryText, style = readerTextStyle(Metrics.downloadSheetTitle),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(row.book.displaySource, color = colors.secondaryText, style = readerTextStyle(Metrics.downloadMetadata),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val state = when {
                        row.recovering -> "原任务待核对"
                        row.working -> "下载进行中"
                        row.status != null -> "${row.status.chapterCount} 章"
                        else -> "等待目录"
                    }
                    Text(state, color = if (row.recovering) colors.warning else colors.accent,
                        style = readerTextStyle(Metrics.downloadSectionLabel))
                }
                ReaderAction(if (expanded) Icons.collapse else Icons.expand,
                    if (expanded) "收起章节任务" else "展开章节任务", onToggle)
            }
            row.detail?.let { Text(it, color = colors.secondaryText, style = readerTextStyle(Metrics.downloadMetadata)) }
            row.status?.takeIf { it.chapterCount > 0 }?.let { status ->
                ReaderTaskProgress(ReaderTaskProgressState("章节下载进度", status.completedCount.asCount(),
                    status.failedCount.asCount(), status.inProgressCount.asCount(),
                    (status.missingCount + status.queuedCount).asCount()))
            }
            if (row.startRange != null) {
                Button({ onStart(row) }, modifier = Modifier.testTag("download.start")) {
                    Text("开始下载", style = readerTextStyle(Metrics.downloadControl))
                }
            } else if (row.status?.tocAvailable != true && !row.recovering) {
                TextButton(onClick = { onPrepare(row) }) {
                    Text("读取目录", style = readerTextStyle(Metrics.downloadControl))
                }
            }
            if (expanded && row.status?.chapters.isNullOrEmpty()) {
                Text("暂无可下载的目录", color = colors.secondaryText, style = readerTextStyle(Metrics.downloadMetadata))
            }
        }
    }
}

@Composable
private fun DownloadChapterRow(chapter: M2CacheChapterStatusData, action: (DownloadAction) -> Unit) {
    val colors = LocalReaderColors.current
    HorizontalDivider(color = colors.divider)
    Row(Modifier.fillMaxWidth().heightIn(min = Metrics.downloadChapterRowHeight.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val title = chapter.displayTitle ?: chapter.title
        Column(Modifier.weight(1f)) {
            Text(title, style = readerTextStyle(Metrics.downloadChapterTitle), color = colors.primaryText,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(chapter.state.displayName(), style = readerTextStyle(Metrics.downloadMetadata),
                color = if (chapter.state == M2CacheChapterState.FAILED) colors.danger else colors.secondaryText)
        }
        for (control in chapter.state.actions()) {
            val icon = when (control) {
                DownloadAction.PAUSE -> Icons.pause
                DownloadAction.RESUME -> Icons.resume
                DownloadAction.CANCEL -> Icons.stop
            }
            ReaderAction(icon, "${control.title}《$title》", { action(control) })
        }
    }
}

private fun Long.asCount() = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

private fun M2CacheChapterState.actions() = when (this) {
    M2CacheChapterState.QUEUED, M2CacheChapterState.IN_PROGRESS -> listOf(DownloadAction.PAUSE, DownloadAction.CANCEL)
    M2CacheChapterState.PAUSED -> listOf(DownloadAction.RESUME, DownloadAction.CANCEL)
    M2CacheChapterState.FAILED, M2CacheChapterState.CANCELLED -> listOf(DownloadAction.RESUME)
    else -> emptyList()
}

private fun M2CacheChapterState.displayName() = when (this) {
    M2CacheChapterState.MISSING -> "未执行"
    M2CacheChapterState.QUEUED -> "排队中"
    M2CacheChapterState.IN_PROGRESS -> "下载中"
    M2CacheChapterState.CACHED -> "已有正文缓存"
    M2CacheChapterState.FAILED -> "失败"
    M2CacheChapterState.CANCELLED -> "已取消"
    M2CacheChapterState.PAUSED -> "已暂停"
    M2CacheChapterState.COMPLETED -> "已完成"
}
