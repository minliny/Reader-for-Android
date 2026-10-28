package io.reader.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.reader.android.R
import io.reader.android.runtime.ReaderTextCoordinates
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

val ReaderScalarOffsetKey = SemanticsPropertyKey<Int>("ReaderScalarOffset")
var SemanticsPropertyReceiver.readerScalarOffset by ReaderScalarOffsetKey
val ReaderChapterIndexKey = SemanticsPropertyKey<Int>("ReaderChapterIndex")
var SemanticsPropertyReceiver.readerChapterIndex by ReaderChapterIndexKey

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ReaderReadingScreen(state: ReaderUiState, model: ReaderViewModel) {
    val reading = state.reading ?: return
    Box(Modifier.fillMaxSize().background(state.paper.background()).windowInsetsPadding(WindowInsets.safeDrawing)
        .testTag("reader_reading").semantics {
            testTagsAsResourceId = true
            readerScalarOffset = reading.visibleScalarOffset
            readerChapterIndex = reading.chapter.index
        }) {
        key(reading.identity) {
            ReaderChapterBody(reading, state.fontSize, state.paper, !state.controlsVisible && !state.busy,
                onVisibleAnchor = { model.visibleAnchor(reading.identity, it) },
                onTap = model::toggleControls,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 760.dp).fillMaxSize())
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            if (state.controlsVisible || state.error != null || state.busy || state.pendingPosition != null) {
                Surface(color = state.paper.background()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = model::exitReading, enabled = !state.busy,
                            modifier = Modifier.testTag("reader_exit")) {
                            Icon(painterResource(R.drawable.reader_back), "返回书架", tint = state.paper.ink())
                        }
                        Text(reading.book.title, color = state.paper.ink(), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { model.showDirectory() }, enabled = !state.busy && state.pendingPosition == null,
                            modifier = Modifier.testTag("reader_open_directory")) {
                            Icon(painterResource(R.drawable.reader_directory), "目录", tint = state.paper.ink())
                        }
                    }
                }
            }
            if (state.error != null) ReaderTransientFeedback(state.error, model::clearError)
            state.pendingPosition?.let { pending ->
                ReaderRecoveryNotice("原进度保存尚未确认，已暂停后续保存。当前正文仍可阅读。", state.busy,
                    { model.reconcilePendingPosition(pending) }, "核对原保存", "reader_reconcile_position")
            }
            state.pendingBookmarks.firstOrNull { it.bookId == reading.book.id }?.let { pending ->
                ReaderRecoveryNotice("原书签操作尚未确认，请先核对原操作。", state.busy,
                    { model.reconcileBookmark(pending) }, "核对原书签操作", "reader_reconcile_bookmark")
            }
            if (state.pendingSearchClose != null) ReaderRecoveryNotice("原搜索尚未结束，请核对并关闭原搜索。",
                state.searchBusy, model::reconcileSearchClose, "核对原搜索", "reader_reconcile_search")
            if (state.busy) LoadingLine()
        }
        if (state.controlsVisible) {
            Surface(shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 760.dp).fillMaxWidth().testTag("reader_controls")) {
                if (state.readerPanel != null) ReaderToolsPanel(state, model)
                else {
                    Column(Modifier.padding(horizontal = 19.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = model::showBookmarks, enabled = !state.busy,
                                modifier = Modifier.testTag("reader_open_bookmarks")) { Text("书签") }
                            TextButton(onClick = model::showContentSearch, enabled = !state.busy,
                                modifier = Modifier.testTag("reader_open_content_search")) { Text("正文搜索") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { model.changeChapter(-1) }, enabled = !state.busy && state.pendingPosition == null && reading.chapter.index > 0,
                                modifier = Modifier.testTag("reader_previous_chapter")) { Text("上一章") }
                            Text("${reading.chapter.index + 1} / ${reading.book.chapterCount}", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            TextButton(onClick = { model.changeChapter(1) },
                                enabled = !state.busy && state.pendingPosition == null && reading.chapter.index + 1 < reading.book.chapterCount,
                                modifier = Modifier.testTag("reader_next_chapter")) { Text("下一章") }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("字号", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { model.setFontSize(state.fontSize - 1) }, enabled = state.fontSize > 14,
                                modifier = Modifier.testTag("reader_font_smaller")) { Text("A−") }
                            Text(state.fontSize.toString(), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { model.setFontSize(state.fontSize + 1) }, enabled = state.fontSize < 32,
                                modifier = Modifier.testTag("reader_font_larger")) { Text("A+") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ReaderPaper.Paper to "素纸", ReaderPaper.Warm to "暖纸", ReaderPaper.Night to "夜读").forEach { (paper, text) ->
                                FilterChip(selected = state.paper == paper, onClick = { model.setPaper(paper) }, label = { Text(text) },
                                    modifier = Modifier.testTag("reader_paper_${paper.name}"))
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = model::toggleControls, modifier = Modifier.testTag("reader_close_controls")) { Text("收起") }
                        }
                    }
                }
            }
        } else {
            Text("${reading.chapter.index + 1} / ${reading.book.chapterCount}", color = state.paper.ink().copy(alpha = .6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 22.dp, vertical = 8.dp))
        }
        ReaderNativeConfirmation(state.bookmarkDeletion?.let {
            ReaderConfirmationPresentation(it.id, "删除书签", it.chapterTitle, "删除")
        }, model::confirmBookmarkDeletion, model::dismissBookmarkDeletion)
    }
}

private data class Paragraph(val index: Int, val startUtf16: Int, val text: String)
private data class NativeParagraphLayout(val fontSize: Int, val result: TextLayoutResult)

/** Paragraph splitting is a view projection: raw UTF-16 starts retain every original newline. */
private fun paragraphs(text: String): List<Paragraph> {
    val result = ArrayList<Paragraph>()
    var start = 0
    text.forEachIndexed { index, char ->
        if (char == '\n') {
            result += Paragraph(result.size, start, text.substring(start, index))
            start = index + 1
        }
    }
    result += Paragraph(result.size, start, text.substring(start))
    return result
}

@Composable
private fun ReaderChapterBody(
    reading: ReadingSession,
    fontSize: Int,
    paper: ReaderPaper,
    userScrollEnabled: Boolean,
    onVisibleAnchor: (Int) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(reading.chapter.text) { paragraphs(reading.chapter.text) }
    val byStart = remember(blocks) { blocks.associateBy { it.startUtf16 } }
    val layouts = remember { mutableStateMapOf<Int, NativeParagraphLayout>() }
    val list = rememberLazyListState()
    var widthPx by remember { mutableIntStateOf(0) }
    var restoring by remember { mutableStateOf(true) }
    var restoreFailed by remember { mutableStateOf(false) }
    var restoreAttempt by remember { mutableIntStateOf(0) }
    val currentAnchor by rememberUpdatedState(onVisibleAnchor)
    val horizontalPx = with(LocalDensity.current) { 22.dp.roundToPx() }

    // Restore the original scalar anchor to the actual native line. Reflow changes
    // layout only, never the saved Core position or a percentage-derived guess.
    LaunchedEffect(reading.identity, fontSize, widthPx, restoreAttempt) {
        if (widthPx <= horizontalPx * 2) return@LaunchedEffect
        restoring = true
        restoreFailed = false
        val utf16 = ReaderTextCoordinates.utf16Offset(reading.chapter.text, reading.visibleScalarOffset)
        val block = blocks.lastOrNull { it.startUtf16 <= utf16 } ?: blocks.first()
        val restored = withTimeoutOrNull(5_000) {
            if (utf16 == 0) {
                list.scrollToItem(0)
                return@withTimeoutOrNull true
            }
            list.scrollToItem(block.index + 1)
            val native = snapshotFlow { layouts[block.startUtf16] }
                .filterNotNull().first { it.fontSize == fontSize && it.result.size.width == widthPx - horizontalPx * 2 }
            val local = (utf16 - block.startUtf16).coerceIn(0, block.text.length)
            val line = native.result.getLineForOffset(local)
            list.scrollToItem(block.index + 1, native.result.getLineTop(line).roundToInt().coerceAtLeast(0))
            true
        }
        restoreFailed = restored != true
        restoring = restored != true
    }

    LaunchedEffect(list, reading.identity, fontSize) {
        var userScrollObserved = false
        snapshotFlow {
            val scalar = if (restoring) null else {
                val visible = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key is Int }
                val block = (visible?.key as? Int)?.let(byStart::get)
                val native = block?.let { layouts[it.startUtf16] }
                if (visible == null || block == null || native == null || native.fontSize != fontSize ||
                    native.result.size.width != widthPx - horizontalPx * 2) null
                else {
                    val y = (list.layoutInfo.viewportStartOffset - visible.offset).coerceAtLeast(0).toFloat()
                    val line = native.result.getLineForVerticalPosition(y)
                    val utf16 = block.startUtf16 + native.result.getLineStart(line).coerceAtMost(block.text.length)
                    ReaderTextCoordinates.scalarOffset(reading.chapter.text, utf16)
                }
            }
            Triple(restoring, list.isScrollInProgress, scalar)
        }.distinctUntilChanged().collect { (isRestoring, scrolling, scalar) ->
            if (isRestoring) {
                userScrollObserved = false
            } else if (scrolling) {
                userScrollObserved = true
                if (scalar != null) currentAnchor(scalar)
            } else if (userScrollObserved) {
                // Native fling's final layout can arrive in the same snapshot
                // that retires scrolling. Capture that last user-driven anchor.
                userScrollObserved = false
                if (scalar != null) currentAnchor(scalar)
            }
        }
    }

    Box(modifier) {
        SelectionContainer {
            LazyColumn(
                state = list,
                userScrollEnabled = userScrollEnabled,
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 52.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }
                    .pointerInput(reading.identity) { detectTapGestures(onTap = { onTap() }) }
                    .testTag("reader_body"),
            ) {
                item(key = "chapter-title") {
                    DisableSelection {
                        Text(reading.chapter.title, fontFamily = ReaderFonts.Serif, fontSize = 23.sp,
                            color = paper.ink(), modifier = Modifier.padding(top = 32.dp, bottom = 12.dp).testTag("reader_chapter_title"))
                    }
                }
                items(blocks, key = { it.startUtf16 }) { block ->
                    // Native selection copies the original paragraph, including empty
                    // paragraphs. No display-only placeholder enters the clipboard.
                    Text(block.text,
                        style = TextStyle(fontFamily = ReaderFonts.Serif, fontSize = fontSize.sp, lineHeight = (fontSize * 1.8f).sp),
                        color = paper.ink(), modifier = Modifier.fillMaxWidth().testTag("reader_paragraph_${block.startUtf16}"),
                        onTextLayout = { result ->
                            val previous = layouts[block.startUtf16]
                            if (previous == null || previous.fontSize != fontSize || previous.result.size != result.size ||
                                previous.result.lineCount != result.lineCount) layouts[block.startUtf16] = NativeParagraphLayout(fontSize, result)
                        })
                }
            }
        }
        if (restoring) {
            Surface(color = paper.background(), modifier = Modifier.fillMaxSize().testTag("reader_restore_loading")) {
                Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    if (restoreFailed) {
                        Text("暂时无法恢复阅读位置", color = paper.ink(), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { restoreAttempt++ }, modifier = Modifier.testTag("reader_restore_retry")) { Text("重试排版") }
                    } else CircularProgressIndicator(Modifier.size(26.dp))
                }
            }
        }
    }
}
