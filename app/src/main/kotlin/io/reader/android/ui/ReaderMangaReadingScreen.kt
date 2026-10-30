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
                ReaderPageState("阅读位置暂时无法恢复", if (state.restoreBlocked == "MANGA_SAVED_POSITION_REQUIRES_EXACT_CHAPTER_MANIFEST")
                    "目录或图片版本已变化，原阅读位置仍然保留。" else "暂时无法恢复原阅读位置，请稍后重试。")
            }
            state.phase == ReaderMangaReadingPhase.READY -> MangaViewport(state, model, Modifier.weight(1f))
            else -> {
                MangaHeading(state, model, moving = false)
                when (state.phase) {
                    ReaderMangaReadingPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("reader_manga_loading"))
                    }
                    ReaderMangaReadingPhase.ERROR -> if (state.pages.isEmpty() && state.failedWindow != null &&
                        state.windowError == "MANGA_UI_ENCODED_WINDOW_BUDGET") {
                        ReaderPageState("图片暂时无法全部加载", "可先读取原阅读位置的一张图片。", "单页读取", model::retryMangaSinglePage)
                    } else ReaderPageState("漫画暂时不可用", if (state.error == "MANGA_UI_EMPTY_MANIFEST")
                        "本章暂时没有可阅读的图片。" else "本章图片暂时无法打开，请稍后重试。", "重试", model::retryMangaReading)
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
    if (state.windowError != null && state.pages.isNotEmpty()) {
        val budgetLimited = state.windowError == "MANGA_UI_ENCODED_WINDOW_BUDGET"
        ReaderRecoveryNotice(if (budgetLimited) "本段图片仍可阅读，到末尾可继续下一段。" else "后续图片暂时不可用，已打开的图片仍可阅读。", state.pendingWindow != null,
            if (!budgetLimited && state.failedWindow != null) ({ model.retryMangaWindow() }) else null,
            actionLabel = "重试加载", actionTag = "reader_manga_retry_window")
    }
}

private data class MangaImagePage(val page: ReaderMangaReadingPage, val pageId: String, val width: Int, val height: Int)
private data class MangaWindowNavigation(val frame: MangaDrawFrame, val anchor: ReaderMangaAnchor, val plan: ReaderMangaWindowPlan)

@Composable
private fun MangaViewport(state: ReaderMangaReadingUiState, model: ReaderViewModel, modifier: Modifier) {
    val list = rememberLazyListState()
    val horizontal = rememberScrollState()
    val view = LocalView.current
    val current by rememberUpdatedState(state)
    val observation = remember(state.generation) { MangaDrawObservation() }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var navigation by remember(state.generation) { mutableStateOf<MangaWindowNavigation?>(null) }
    val imagePages = remember(state.pages) {
        state.pages.mapNotNull { page ->
            val identity = state.chapter?.manifest?.pages?.singleOrNull {
                it.ordinal == page.ordinal && it.resourceRef == page.resource.resourceRef
            } ?: return@mapNotNull null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(page.decoded.bytes, 0, page.decoded.bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@mapNotNull null
            MangaImagePage(page, identity.pageId, bounds.outWidth, bounds.outHeight)
        }
    }
    val latestPages by rememberUpdatedState(imagePages)
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
        var lastWindowRequest: Pair<ReaderMangaWindowTicket, ReaderMangaWindowRequest>? = null
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
            var direction = 0
            val mayObserve = restore != null || observation.scrollAdmission.canObserve(issued)
            if (mayObserve) {
                val measured = ReaderMangaMeasuredAnchor(issued, actual, restore?.id, target?.pageId)
                if (!model.observeMangaVisible(measured)) continue
                if (restore != null) observation.restoreConfirmed(issued, restore.id)
                else observation.scrollAdmission.accepted(issued)
                val previous = lastAccepted
                lastAccepted = actual
                if (restore == null && previous != null)
                    direction = if (actual.ordinal != previous.ordinal) actual.ordinal.compareTo(previous.ordinal) else actual.y.compareTo(previous.y)
            } else if (now.visibleAnchor != actual) continue
            // A verified idle viewport can need more images without authorizing a progress write.
            // Require the native tail item to have an actual drawn rectangle too.
            val lastVisible = nativeVisible.lastOrNull()?.key
            val tail = visibleImages.singleOrNull { it.pageId == lastVisible } ?: continue
            val first = now.pages.firstOrNull()?.ordinal ?: continue
            val total = now.window?.totalPages ?: continue
            val fromBackwardRequest = lastWindowRequest?.let { (previousWindow, request) ->
                request.start < previousWindow.start && issued.window.start == request.start && issued.window.limit == request.limit
            } == true
            val plan = ReaderMangaWindowPaging.plan(first, now.pages.size, total, actual.ordinal, tail.ordinal,
                list.canScrollBackward, list.canScrollForward, direction,
                automaticBlocked = now.windowError != null, allowPassiveForward = !fromBackwardRequest)
            navigation = MangaWindowNavigation(frame, actual, plan)
            if (now.pendingWindow != null) continue
            val request = plan.automatic ?: continue
            if (!ReaderMangaWindowPaging.shouldDispatch(lastWindowRequest, issued.window, request)) continue
            lastWindowRequest = issued.window to request
            model.loadMangaWindow(request.start, request.limit, request.lastVisibleOrdinal)
        }
    }
    Column(modifier) {
        MangaHeading(state, model, moving)
        if (imagePages.size != state.pages.size || imagePages.isEmpty()) {
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
                        items(imagePages, key = { it.pageId }) { value ->
                            // Stable handle identity survives an overlapping window; zoom changes only layout nodes.
                            val bitmap = remember(value.page.decoded) {
                                BitmapFactory.decodeByteArray(value.page.decoded.bytes, 0, value.page.decoded.bytes.size)?.asImageBitmap()
                            }
                            key(ticket?.epoch) {
                                DisposableEffect(ticket, value.page.decoded.transferId) {
                                    val issued = ticket
                                    onDispose { if (issued != null) observation.remove(issued, value.page.decoded.transferId) }
                                }
                                if (bitmap == null || bitmap.width != value.width || bitmap.height != value.height) {
                                    ReaderPageState("这张图片暂时无法显示", "请稍后重试。")
                                } else Image(bitmap, "第 ${value.page.ordinal + 1} 页", contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(value.width.toFloat() / value.height)
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
        // A fixed host avoids a show/hide feedback loop that changes the viewport used by the window plan.
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val displayed = navigation
            val ready = displayed != null && displayed.frame.ticket == ticket && displayed.anchor == state.visibleAnchor &&
                state.restoreRequest == null && state.pendingWindow == null && !state.exitPending && !moving &&
                observation.isCurrent(displayed.frame) && !observation.scrollAdmission.hasUnobservedMovement(displayed.frame.ticket)
            fun navigate(forward: Boolean) {
                val captured = navigation ?: return
                val now = current
                if (now.layoutTicket != captured.frame.ticket || now.visibleAnchor != captured.anchor ||
                    now.restoreRequest != null || now.pendingWindow != null || now.exitPending ||
                    list.isScrollInProgress || horizontal.isScrollInProgress || transform.isTransformInProgress ||
                    !observation.isCurrent(captured.frame) || observation.scrollAdmission.hasUnobservedMovement(captured.frame.ticket)) return
                if (captured.plan.automatic != null || (if (forward) list.canScrollForward else list.canScrollBackward)) return
                val ordinal = (if (forward) captured.plan.continueOrdinal else captured.plan.previousOrdinal) ?: return
                model.jumpToMangaPage(ordinal)
            }
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ navigate(false) }, enabled = ready && displayed?.plan?.automatic == null && displayed?.plan?.previousOrdinal != null,
                    modifier = Modifier.testTag("reader_manga_previous_segment")) { Text("前一段") }
                TextButton({ navigate(true) }, enabled = ready && displayed?.plan?.automatic == null && displayed?.plan?.continueOrdinal != null,
                    modifier = Modifier.testTag("reader_manga_continue")) { Text("继续阅读") }
            }
        }
    }
}

private fun MangaImagePage.geometry(rect: ReaderMangaImageRect) = ReaderMangaImageGeometry(
    pageId, page.ordinal, page.resource.resourceRef, page.decoded.transferId, width, height, rect,
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
