package io.reader.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.reader.android.R
import io.reader.android.runtime.ReaderLocalBookFormat
import io.reader.android.runtime.ReaderTextPresentationKind
import io.reader.android.runtime.ReaderTtsPlaybackStatus

val ReaderScalarOffsetKey = SemanticsPropertyKey<Int>("ReaderScalarOffset")
var SemanticsPropertyReceiver.readerScalarOffset by ReaderScalarOffsetKey
val ReaderChapterIndexKey = SemanticsPropertyKey<Int>("ReaderChapterIndex")
var SemanticsPropertyReceiver.readerChapterIndex by ReaderChapterIndexKey

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ReaderReadingScreen(
    state: ReaderUiState,
    model: ReaderViewModel,
    manga: ReaderMangaReadingUiState,
) {
    if (state.route == ReaderRoute.MangaReading) { ReaderMangaReadingScreen(manga, model); return }
    if (state.route == ReaderRoute.OnlineReading) { ReaderOnlineReadingScreen(state, model); return }
    val reading = state.reading ?: return
    Box(Modifier.fillMaxSize().background(state.paper.background()).windowInsetsPadding(WindowInsets.safeDrawing)
        .testTag("reader_reading").semantics {
            testTagsAsResourceId = true
            readerScalarOffset = reading.visibleScalarOffset
            readerChapterIndex = reading.chapter.index
        }) {
        key(reading.identity) {
            val bodyModifier = Modifier.align(Alignment.Center).widthIn(max = 760.dp).fillMaxSize()
            when (reading.format) {
                ReaderLocalBookFormat.TXT -> ReaderNativeChapterBody(reading, state.fontSize, state.paper, !state.controlsVisible && !state.busy,
                    onVisibleAnchor = { model.visibleAnchor(reading.identity, it) }, onTap = model::toggleControls, modifier = bodyModifier,
                    speechHighlight = state.ttsPlayback.highlight)
                ReaderLocalBookFormat.EPUB, ReaderLocalBookFormat.MOBI, ReaderLocalBookFormat.AZW -> {
                    var footerHeightPx by remember(reading.identity, reading.chapter.sessionToken) { mutableIntStateOf(0) }
                    // The footer owns real viewport space even while controls cover it. Its measured
                    // footprint is also removed from the existing end inset by the ebook body.
                    Column(bodyModifier) {
                        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("reader_ebook_viewport")) {
                            ReaderEbookReadingBody(state, model, Modifier.fillMaxSize(), reservedFooterHeightPx = footerHeightPx)
                        }
                        Text("${reading.chapter.index + 1} / ${reading.book.chapterCount}", color = state.paper.ink().copy(alpha = .6f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.End).alpha(if (state.controlsVisible) 0f else 1f)
                                .testTag("reader_ebook_footer").onSizeChanged { footerHeightPx = it.height }
                                .then(if (state.controlsVisible) Modifier.clearAndSetSemantics { } else Modifier)
                                .padding(horizontal = 22.dp, vertical = 8.dp))
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            if (state.controlsVisible || state.error != null || state.busy || state.positionSaveBarrier != null || state.ebookFailureOwner == reading.identity) {
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
            ReaderProtocolRecoveryHost(model)
            if (state.error != null) ReaderTransientFeedback(state.error, model::clearError)
            state.positionSaveBarrier?.let { pending ->
                val retainedIds = state.pendingPositions.filter { it.sourceId == "local" && it.bookId == reading.book.id }.map { it.id }
                val canRequestNewIntent = reading.newPositionIntent == null && reading.format != ReaderLocalBookFormat.TXT
                ReaderRecoveryNotice("原进度保存尚未确认，已暂停后续保存。当前正文仍可阅读。", state.busy,
                    { model.reconcilePendingPosition(pending, ReaderRoute.Reading, reading.identity) },
                    "核对原保存", "reader_reconcile_position",
                    secondaryActionLabel = if (canRequestNewIntent) "保留原记录，继续新阅读" else null,
                    onSecondaryAction = if (canRequestNewIntent) ({ model.beginNewPositionIntent(
                        reading.identity, reading.chapter.sessionToken, retainedIds) }) else null,
                    secondaryActionTag = "reader_begin_new_position_intent", secondaryActionEnabled = reading.ebookPresented)
            }
            // Historical UNKNOWN stays in state and on the shelf; hiding this host never retires its record.
            if (state.positionSaveBarrier == null && state.controlsVisible) state.historicalPosition?.let {
                ReaderRecoveryNotice("原进度保存尚未确认，原记录会继续保留。", state.busy, null)
            }
            state.pendingBookmarks.firstOrNull { it.bookId == reading.book.id }?.let { pending ->
                ReaderRecoveryNotice("原书签操作尚未确认，请先核对原操作。", state.busy,
                    { model.reconcileBookmark(pending) }, "核对原书签操作", "reader_reconcile_bookmark")
            }
            state.pendingSearchClose?.let { original ->
                val hostRoute = state.route
                val hostReadingOwner = state.reading?.identity
                ReaderRecoveryNotice("原搜索尚未结束，请核对并关闭原搜索。", state.searchBusy,
                    { model.reconcileSearchClose(original, hostRoute, hostReadingOwner) },
                    "核对原搜索", "reader_reconcile_search")
            }
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
                        if (reading.format == ReaderLocalBookFormat.TXT &&
                            reading.chapter.presentationKind == ReaderTextPresentationKind.LOCAL_DOCUMENT) {
                            val ttsStatus = state.ttsPlayback.status
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("朗读：${when (ttsStatus) {
                                    ReaderTtsPlaybackStatus.PLAYING -> "播放中"
                                    ReaderTtsPlaybackStatus.PREPARING -> "准备中"
                                    ReaderTtsPlaybackStatus.PAUSED -> "已暂停"
                                    ReaderTtsPlaybackStatus.COMPLETED -> "已完成"
                                    ReaderTtsPlaybackStatus.FAILED -> "不可用"
                                    ReaderTtsPlaybackStatus.STOPPED -> "已停止"
                                    ReaderTtsPlaybackStatus.IDLE -> "未开始"
                                }}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                                    .testTag("reader_tts_status"))
                                TextButton(onClick = model::toggleTts, enabled = !state.busy && state.pendingPosition == null,
                                    modifier = Modifier.testTag("reader_tts_toggle")) {
                                    Text(when (ttsStatus) {
                                        ReaderTtsPlaybackStatus.PLAYING, ReaderTtsPlaybackStatus.PREPARING -> "暂停"
                                        ReaderTtsPlaybackStatus.PAUSED -> "继续"
                                        else -> "朗读"
                                    })
                                }
                                if (ttsStatus in setOf(ReaderTtsPlaybackStatus.PLAYING, ReaderTtsPlaybackStatus.PREPARING,
                                        ReaderTtsPlaybackStatus.PAUSED)) {
                                    TextButton(onClick = model::stopTts, enabled = !state.busy,
                                        modifier = Modifier.testTag("reader_tts_stop")) { Text("停止") }
                                }
                            }
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
        } else if (reading.format == ReaderLocalBookFormat.TXT) {
            Text("${reading.chapter.index + 1} / ${reading.book.chapterCount}", color = state.paper.ink().copy(alpha = .6f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 22.dp, vertical = 8.dp))
        }
        ReaderNativeConfirmation(state.bookmarkDeletion?.let {
            ReaderConfirmationPresentation(it.id, "删除书签", it.chapterTitle, "删除")
        }, model::confirmBookmarkDeletion, model::dismissBookmarkDeletion)
    }
}
