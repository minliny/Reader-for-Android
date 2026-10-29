package io.reader.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.reader.android.runtime.*

/** A UI projection of a real Library entry; no local format, Original or local ReadingSession. */
data class ReaderOnlineReadingUi(
    val identity: String, val nativeOwner: Long, val book: ReaderRemoteShelfBook, val opened: ReaderOnlineChapter,
    val visibleScalar: Int = opened.restoreScalarOffset, val controls: Boolean = false,
)
data class ReaderOnlineDirectoryUi(val identity: String, val book: ReaderRemoteShelfBook,
    val page: ReaderOnlineDirectoryPage, val nodes: List<ReaderOnlineDirectoryNode> = page.nodes)
data class ReaderOnlineSaveProjection(val readingIdentity: String, val sessionToken: String,
    val result: ReaderPositionSaveResult.ConfirmedNewOriginal)

@Composable
internal fun ReaderOnlineDirectoryScreen(state: ReaderUiState, model: ReaderViewModel) {
    val directory = state.onlineDirectory
    if (directory == null) {
        Column(Modifier.fillMaxSize()) {
            state.onlineFeedback?.let { message -> key(state.onlineFeedbackId) { ReaderTransientFeedback(message) { model.retireOnlineFeedback(state.onlineFeedbackId) } } }
            Text(state.onlineSelectedBook?.title.orEmpty(), modifier = Modifier.padding(19.dp))
            ReaderOnlineOriginalNotice(state, model)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else ReaderPageState("目录暂时不可用", state.onlineError.orEmpty())
        }
        return
    }
    val blocked = state.busy || state.onlineOriginal != null || state.pendingPositions.any { it.key == directory.book.key }
    Column(Modifier.fillMaxSize().testTag("reader_online_directory")) {
        state.onlineFeedback?.let { message -> key(state.onlineFeedbackId) { ReaderTransientFeedback(message) { model.retireOnlineFeedback(state.onlineFeedbackId) } } }
        Text(directory.book.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(19.dp))
        ReaderOnlineOriginalNotice(state, model)
        state.onlineError?.let { ReaderPageState("正文暂时不可用", it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(19.dp)) {
            item { TextButton({ model.continueOnlineReading(directory.identity) }, enabled = !blocked,
                modifier = Modifier.testTag("reader_online_resume")) { Text("继续阅读") } }
            itemsIndexed(directory.nodes) { index, node ->
                TextButton({ model.openOnlineDirectoryNode(directory.identity, node) }, enabled = !blocked && node.kind == "target",
                    modifier = Modifier.fillMaxWidth().testTag("reader_online_directory_$index")) { Text(node.title) }
            }
            if (directory.page.nextOffset != null) item {
                TextButton({ model.nextOnlineDirectory(directory.identity) }, enabled = !blocked) { Text("更多目录") }
            }
        }
    }
}

@Composable
internal fun ReaderOnlineOriginalNotice(state: ReaderUiState, model: ReaderViewModel) {
    val reading = state.onlineReading
    val key = reading?.book?.key ?: state.onlineDirectory?.book?.key ?: state.onlineSelectedBook?.key
    val directoryId = state.onlineDirectoryIdentity
    state.pendingPositions.filter { it.key == key }.forEach { pending ->
        ReaderRecoveryNotice("原阅读位置尚未确认，已暂停后续保存。", state.busy,
            { model.reconcileOnlinePosition(pending, reading?.identity, directoryId) }, "核对原保存", "reader_online_reconcile_position")
    }
    state.onlineOriginal?.let { original ->
        ReaderRecoveryNotice("原在线请求尚未确认，原请求会继续保留。", state.busy,
            if (state.route in setOf(ReaderRoute.OnlineReading, ReaderRoute.OnlineDirectory))
                ({ model.observeOnlineReadingOriginal(original.id, reading?.identity, directoryId) }) else null,
            "读取原状态", "reader_online_observe_original")
    }
}

/** Only the neutral native text surface and implemented controls. No local-only tool entry. */
@Composable
internal fun ReaderOnlineReadingScreen(state: ReaderUiState, model: ReaderViewModel) {
    val reading = state.onlineReading ?: return
    val blocked = state.busy || state.onlineOriginal != null || state.pendingPositions.any { it.key == reading.book.key }
    Column(Modifier.fillMaxSize().background(state.paper.background()).windowInsetsPadding(WindowInsets.safeDrawing)
        .testTag("reader_online_reading")) {
        ReaderProtocolRecoveryHost(model)
        state.onlineFeedback?.let { message -> key(state.onlineFeedbackId) { ReaderTransientFeedback(message) { model.retireOnlineFeedback(state.onlineFeedbackId) } } }
        ReaderOnlineOriginalNotice(state, model)
        state.onlineError?.let { ReaderPageState("本次操作未完成", it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (reading.controls) Surface(color = state.paper.background()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 8.dp)) {
                Text(reading.opened.chapter.title, color = state.paper.ink())
                Row {
                    TextButton({ model.exitOnlineReading() }, enabled = !state.busy && !blocked,
                        modifier = Modifier.testTag("reader_online_exit")) { Text("返回书架") }
                    TextButton({ model.showOnlineDirectory() }, enabled = !blocked,
                        modifier = Modifier.testTag("reader_online_directory_open")) { Text("目录") }
                    TextButton({ model.toggleOnlineControls(reading.identity) }, enabled = !state.busy) { Text("收起") }
                }
                Row {
                    TextButton({ model.setFontSize(state.fontSize - 1) }, enabled = !state.busy && state.fontSize > 14) { Text("字号－") }
                    TextButton({ model.setFontSize(state.fontSize + 1) }, enabled = !state.busy && state.fontSize < 32) { Text("字号＋") }
                }
                Row {
                    ReaderPaper.entries.forEach { paper -> TextButton({ model.setPaper(paper) }, enabled = !state.busy) { Text(when (paper) {
                        ReaderPaper.Paper -> "纸白"; ReaderPaper.Warm -> "暖纸"; ReaderPaper.Night -> "夜读"
                    }) } }
                }
            }
        }
        ReaderOnlineNativeChapterBody(reading.nativeOwner, reading.opened, reading.visibleScalar, state.fontSize, state.paper,
            !reading.controls && !blocked, { model.visibleOnlineAnchor(reading.identity, reading.opened, it) },
            { model.toggleOnlineControls(reading.identity) }, Modifier.weight(1f).fillMaxWidth())
    }
}
