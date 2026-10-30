package io.reader.android.ui

import android.graphics.BitmapFactory
import android.view.ViewTreeObserver
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

@Composable
internal fun ReaderMangaReadingScreen(state: ReaderMangaReadingUiState, model: ReaderViewModel) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).testTag("reader_manga_reading")) {
        // The viewport owns the controls too, so zoom/exit cannot capture an in-flight native pan.
        when {
            state.restoreBlocked != null -> {
                MangaHeading(state, model, moving = false)
                ReaderPageState("阅读位置暂时无法恢复", state.restoreBlocked)
            }
            state.phase == ReaderMangaReadingPhase.READY -> MangaViewport(state, model, Modifier.weight(1f))
            else -> {
                MangaHeading(state, model, moving = false)
                when (state.phase) {
                    ReaderMangaReadingPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("reader_manga_loading"))
                    }
                    ReaderMangaReadingPhase.ERROR -> ReaderPageState("漫画暂时不可用", state.error ?: "资源读取失败。", "重试", model::retryMangaReading)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun MangaHeading(state: ReaderMangaReadingUiState, model: ReaderViewModel, moving: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ model.closeMangaReading() }, enabled = !state.exitPending && !moving,
                    modifier = Modifier.testTag("reader_manga_exit")) { Text("返回") }
                Text("漫画阅读", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.progressBusy) CircularProgressIndicator(Modifier.size(18.dp).testTag("reader_manga_progress_busy"))
            }
            if (state.phase == ReaderMangaReadingPhase.READY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${state.currentOrdinal + 1} 页 / ${state.window?.totalPages ?: state.pages.size}", modifier = Modifier.weight(1f))
                    val enabled = !state.exitPending && !moving && state.visibleAnchor != null && state.restoreRequest == null
                    TextButton({ model.setMangaZoom(state.zoom - .25f) }, enabled = enabled,
                        modifier = Modifier.testTag("reader_manga_zoom_out")) { Text("缩小") }
                    Text("${(state.zoom * 100).toInt()}%")
                    TextButton({ model.setMangaZoom(state.zoom + .25f) }, enabled = enabled,
                        modifier = Modifier.testTag("reader_manga_zoom_in")) { Text("放大") }
                }
            }
        }
    }
    val pending = state.progressPending.firstOrNull()
    val rejected = state.knownRejection
    if (rejected != null && pending == null) {
        ReaderRecoveryNotice("本次阅读位置未保存。", state.progressBusy || state.exitPending,
            { model.discardRejectedMangaProgress(rejected.operationId) },
            actionLabel = "不保存并返回", actionTag = "reader_manga_discard_rejected_progress")
    } else if (pending != null || state.progressError != null) {
        ReaderRecoveryNotice("阅读进度尚未确认。", state.progressBusy,
            pending?.let { item -> { model.reconcileMangaProgress(item.operationId) } },
            actionTag = "reader_manga_reconcile_progress")
    }
    if (state.windowError != null) {
        ReaderRecoveryNotice(state.windowError, state.pendingWindow != null,
            if (state.failedWindow != null) ({ model.retryMangaWindow() }) else null,
            actionLabel = "重试加载", actionTag = "reader_manga_retry_window")
    }
}

private data class MangaBitmapPage(val page: ReaderMangaReadingPage, val pageId: String, val bitmap: ImageBitmap)

@Composable
private fun MangaViewport(state: ReaderMangaReadingUiState, model: ReaderViewModel, modifier: Modifier) {
    val list = rememberLazyListState()
    val horizontal = rememberScrollState()
    val view = LocalView.current
    val current by rememberUpdatedState(state)
    val observation = remember(state.generation) { MangaDrawObservation() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val bitmapPages = remember(state.visibleWindow, state.pages) {
        state.pages.mapNotNull { page ->
            val identity = state.chapter?.manifest?.pages?.singleOrNull {
                it.ordinal == page.ordinal && it.resourceRef == page.resource.resourceRef
            } ?: return@mapNotNull null
            val bitmap = BitmapFactory.decodeByteArray(page.decoded.bytes, 0, page.decoded.bytes.size)?.asImageBitmap()
                ?: return@mapNotNull null
            MangaBitmapPage(page, identity.pageId, bitmap)
        }
    }
    val latestPages by rememberUpdatedState(bitmapPages)
    val transform = rememberTransformableState { change, _, _ ->
        val now = current
        if (!now.exitPending && !list.isScrollInProgress && !horizontal.isScrollInProgress && change.isFinite() && change > 0f)
            model.setMangaZoom(now.zoom * change)
    }
    val moving = list.isScrollInProgress || horizontal.isScrollInProgress || transform.isTransformInProgress
    val ticket = state.layoutTicket?.takeIf {
        it.window == state.visibleWindow && it.viewportWidthPx == viewportSize.width &&
            it.viewportHeightPx == viewportSize.height && it.zoom == state.zoom
    }
    SideEffect { observation.bind(ticket) }
    LaunchedEffect(state.visibleWindow, viewportSize, state.zoom) {
        val window = current.visibleWindow
        if (window != null && viewportSize.width > 0 && viewportSize.height > 0)
            model.beginMangaLayout(window, viewportSize.width, viewportSize.height)
    }
    DisposableEffect(view, observation) {
        val tree = view.viewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener {
            val stamp = observation.beforeDraw()
            view.post {
                if (view.isAttachedToWindow && view.isShown && !observation.afterDraw(stamp)) view.invalidate()
            }
        }
        tree.addOnDrawListener(listener)
        onDispose { if (tree.isAlive) tree.removeOnDrawListener(listener); observation.close() }
    }
    LaunchedEffect(observation, list, horizontal, transform) {
        snapshotFlow { MangaScrollActivity(current.layoutTicket, current.restoreRequest != null,
            list.isScrollInProgress || horizontal.isScrollInProgress, transform.isTransformInProgress) }
            .collect { activity ->
                observation.scrollAdmission.nativeMotion(activity.ticket, activity.scrolling,
                    activity.transforming, activity.restorationPending)
                if (!activity.scrolling && !activity.transforming) view.invalidate()
            }
    }
    LaunchedEffect(ticket, state.restoreRequest?.id) {
        val issued = ticket ?: return@LaunchedEffect
        val request = current.restoreRequest ?: return@LaunchedEffect
        observation.restoring(issued)
        // A previous window can have the same count and dimensions. Its layoutInfo alone is insufficient.
        observation.layoutDrawn.first { it == issued }
        // Now native has drawn an image measured against this exact window/width ticket.
        snapshotFlow { list.layoutInfo.viewportSize to list.layoutInfo.totalItemsCount }.first { (size, count) ->
            count == latestPages.size && size.height == issued.viewportHeightPx &&
                abs(size.width - issued.viewportWidthPx * issued.zoom) <= 1f
        }
        if (current.layoutTicket != issued || current.restoreRequest?.id != request.id) return@LaunchedEffect
        val pages = latestPages.map { value -> value.geometry(ReaderMangaImageRect(0.0, 0.0, 1.0, 1.0)) }
        val target = ReaderMangaLayoutGeometry.restore(issued, request.anchor, pages) ?: return@LaunchedEffect
        list.scrollToItem(target.itemIndex, target.scrollOffsetPx)
        horizontal.scrollTo(target.horizontalOffsetPx)
        if (current.layoutTicket == issued && current.restoreRequest?.id == request.id) {
            observation.restoreApplied(request.id)
            // Invalidation requests actual drawing; no timer/frame-count can acknowledge restoration.
            view.invalidate()
        }
    }
    LaunchedEffect(observation) {
        var lastAccepted: ReaderMangaAnchor? = null
        var lastWindowRequest: Pair<ReaderMangaWindowTicket, Int>? = null
        for (frame in observation.frames) {
            val now = current
            val issued = now.layoutTicket ?: continue
            if (!observation.isCurrent(frame) || frame.ticket != issued || issued.window != now.visibleWindow || now.exitPending ||
                list.isScrollInProgress || horizontal.isScrollInProgress || transform.isTransformInProgress) continue
            val nativeLayout = list.layoutInfo
            val nativeVisible = nativeLayout.visibleItemsInfo.filter {
                it.offset + it.size > nativeLayout.viewportStartOffset && it.offset < nativeLayout.viewportEndOffset
            }
            val nativeKeys = nativeVisible.mapTo(mutableSetOf()) { it.key }
            val visibleImages = frame.images.filter { image ->
                image.pageId in nativeKeys && nativeVisible.singleOrNull { it.key == image.pageId }?.let { item ->
                    ReaderMangaLayoutGeometry.matchesNativePlacement(frame.viewport, image,
                        item.offset - nativeLayout.viewportStartOffset, item.size, horizontal.value)
                } == true
            }
            val actual = ReaderMangaLayoutGeometry.visibleAnchor(issued, frame.viewport, visibleImages) ?: continue
            val firstNativeKey = nativeVisible.firstOrNull()?.key
            if (firstNativeKey != actual.pageId) continue // A later measured row cannot stand in for an unmeasured first row.
            val restore = now.restoreRequest
            val target = restore?.let { request ->
                if (frame.appliedRestoreId != request.id) return@let null
                visibleImages.singleOrNull { image -> image.pageId == request.anchor.pageId &&
                    image.ordinal == request.anchor.ordinal && image.resourceRef == request.anchor.resourceRef &&
                    image.rect.intersects(frame.viewport) && ReaderMangaLayoutGeometry.accepts(issued, frame.viewport, image) }
            }
            if (restore != null && target == null) continue
            if (restore == null && !observation.scrollAdmission.canObserve(issued)) continue
            val measured = ReaderMangaMeasuredAnchor(issued, actual, restore?.id, target?.pageId)
            if (!model.observeMangaVisible(measured)) continue
            if (restore != null) observation.restoreConfirmed(issued, restore.id)
            else observation.scrollAdmission.accepted(issued)
            val previous = lastAccepted
            lastAccepted = actual
            // A restore establishes a new geometry baseline. Paging requires later real vertical movement,
            // so an overlapping replacement cannot immediately request the preceding window again.
            if (restore != null || previous == null || now.pendingWindow != null || now.windowError != null) continue
            val direction = if (actual.ordinal != previous.ordinal) actual.ordinal.compareTo(previous.ordinal) else actual.y.compareTo(previous.y)
            val first = now.pages.firstOrNull()?.ordinal ?: continue
            val last = now.pages.lastOrNull()?.ordinal ?: continue
            val total = now.window?.totalPages ?: continue
            val start = when {
                direction > 0 && actual.ordinal >= last - 1 && last + 1 < total -> (actual.ordinal - 1).coerceAtLeast(0)
                direction < 0 && first > 0 && actual.ordinal <= first + 1 -> (actual.ordinal - 5).coerceAtLeast(0)
                else -> continue
            }
            if (start == issued.window.start || lastWindowRequest == (issued.window to start)) continue
            lastWindowRequest = issued.window to start
            model.loadMangaWindow(start)
        }
    }
    Column(modifier) {
        MangaHeading(state, model, moving)
        if (bitmapPages.size != state.pages.size || bitmapPages.isEmpty()) {
            ReaderPageState("漫画暂时无法显示", "图片读取未完成。", "重试", model::retryMangaReading)
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { coordinates ->
                viewportSize = coordinates.size
                val position = coordinates.positionInRoot()
                observation.viewport(ReaderMangaImageRect(position.x.toDouble(), position.y.toDouble(),
                    coordinates.size.width.toDouble(), coordinates.size.height.toDouble()))
            }.testTag("reader_manga_viewport")) {
                val contentWidth = maxWidth * state.zoom
                val viewportHeight = maxHeight
                Box(Modifier.fillMaxSize().horizontalScroll(horizontal, enabled = !state.exitPending && state.restoreRequest == null)
                    .transformable(transform, canPan = { false }, enabled = !state.exitPending)) {
                    LazyColumn(state = list,
                        userScrollEnabled = !state.exitPending && state.restoreRequest == null && !transform.isTransformInProgress,
                        modifier = Modifier.width(contentWidth).height(viewportHeight).testTag("reader_manga_pages")) {
                        items(bitmapPages, key = { it.pageId }) { value ->
                            key(ticket?.epoch) {
                            DisposableEffect(ticket, value.page.decoded.transferId) {
                                val issued = ticket
                                onDispose { if (issued != null) observation.remove(issued, value.page.decoded.transferId) }
                            }
                            Image(value.bitmap, "第 ${value.page.ordinal + 1} 页", contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth().aspectRatio(value.bitmap.width.toFloat() / value.bitmap.height)
                                    .onGloballyPositioned { coordinates ->
                                        val actualTicket = ticket ?: return@onGloballyPositioned
                                        val position = coordinates.positionInRoot()
                                        observation.image(actualTicket, value.geometry(ReaderMangaImageRect(position.x.toDouble(), position.y.toDouble(),
                                            coordinates.size.width.toDouble(), coordinates.size.height.toDouble())))
                                    }.drawWithContent {
                                        drawContent()
                                        ticket?.let { observation.drawn(it, value.page.decoded.transferId) }
                                    }.testTag("reader_manga_page_${value.page.ordinal}"))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MangaBitmapPage.geometry(rect: ReaderMangaImageRect) = ReaderMangaImageGeometry(
    pageId, page.ordinal, page.resource.resourceRef, page.decoded.transferId, bitmap.width, bitmap.height, rect,
)

private data class MangaDrawStamp(val ticket: ReaderMangaLayoutTicket, val revision: Long)
private data class MangaScrollActivity(val ticket: ReaderMangaLayoutTicket?, val restorationPending: Boolean,
    val scrolling: Boolean, val transforming: Boolean)
private data class MangaDrawFrame(val ticket: ReaderMangaLayoutTicket, val revision: Long, val viewport: ReaderMangaImageRect,
    val images: List<ReaderMangaImageGeometry>, val appliedRestoreId: Long?)

/** UI-thread observations only. No owner identity, scroll physics or durable progress is created here. */
private class MangaDrawObservation {
    val frames = Channel<MangaDrawFrame>(Channel.CONFLATED)
    val layoutDrawn = MutableStateFlow<ReaderMangaLayoutTicket?>(null)
    val scrollAdmission = ReaderMangaScrollAdmission()
    private var ticket: ReaderMangaLayoutTicket? = null
    private var viewport: ReaderMangaImageRect? = null
    private val images = mutableMapOf<String, ReaderMangaImageGeometry>()
    private val drawn = mutableSetOf<String>()
    private var revision = 0L
    private var appliedRestoreId: Long? = null
    fun bind(value: ReaderMangaLayoutTicket?) {
        if (ticket == value) return
        ticket = value; images.clear(); drawn.clear(); appliedRestoreId = null; layoutDrawn.value = null; revision++
        scrollAdmission.bind(value)
    }
    fun viewport(value: ReaderMangaImageRect) { if (viewport != value) { viewport = value; revision++ } }
    fun image(issued: ReaderMangaLayoutTicket, value: ReaderMangaImageGeometry) {
        if (ticket != issued || images[value.transferId] == value) return
        images[value.transferId] = value; revision++
    }
    fun drawn(issued: ReaderMangaLayoutTicket, transferId: String) { if (ticket == issued) drawn += transferId }
    fun remove(issued: ReaderMangaLayoutTicket, transferId: String) {
        if (ticket != issued) return
        if (images.remove(transferId) != null) revision++
        drawn.remove(transferId)
    }
    fun restoring(issued: ReaderMangaLayoutTicket) {
        if (ticket != issued) return
        appliedRestoreId = null; revision++
        scrollAdmission.beginRestore(issued)
    }
    fun restoreApplied(id: Long) { appliedRestoreId = id; revision++ }
    fun restoreConfirmed(issued: ReaderMangaLayoutTicket, id: Long) {
        if (ticket == issued && appliedRestoreId == id) scrollAdmission.confirmRestore(issued)
    }
    fun isCurrent(frame: MangaDrawFrame) = frame.ticket == ticket && frame.revision == revision
    fun beforeDraw(): MangaDrawStamp? = ticket?.let { MangaDrawStamp(it, revision) }
    /** A layout change during native draw requests another real draw, never a synthetic acknowledgment. */
    fun afterDraw(stamp: MangaDrawStamp?): Boolean {
        if (stamp == null || stamp.ticket != ticket) return true
        if (stamp.revision != revision) return false
        val area = viewport ?: return true
        val visible = images.values.filter { it.transferId in drawn }
        if (visible.any { it.rect.intersects(area) && ReaderMangaLayoutGeometry.accepts(stamp.ticket, area, it) })
            layoutDrawn.value = stamp.ticket
        frames.trySend(MangaDrawFrame(stamp.ticket, stamp.revision, area, visible, appliedRestoreId))
        return true
    }
    fun close() { frames.close() }
}
