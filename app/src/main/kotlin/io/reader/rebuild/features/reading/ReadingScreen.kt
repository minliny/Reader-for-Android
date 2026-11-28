@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.reader.rebuild.features.reading

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reader.core.protocol.BookmarkData
import com.reader.core.protocol.M2ChangeBookSourceCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.RecoveryAction
import io.reader.rebuild.design.RecoveryNoticeState
import io.reader.rebuild.design.TransientToastState
import io.reader.rebuild.design.readerFeedbackEnabled
import io.reader.rebuild.features.library.BookReference

private enum class ReaderSheet { DIRECTORY, BOOKMARKS, SOURCES, FAILED_CHAPTER }

@Composable
internal fun ReadingScreen(
    book: BookReference,
    startWithDirectory: Boolean,
    startWithSwitch: Boolean,
    journey: ReadingJourney,
    ownerScope: CoroutineScope,
    appearance: ReadingAppearance,
    onAppearance: (ReadingAppearance) -> Unit,
    onBack: () -> Unit,
    keepAwake: Boolean = false,
    onBookChanged: (BookReference) -> Unit = {},
    speech: SpeechSessionCoordinator? = null,
) {
    val screenScope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var document by remember(journey) { mutableStateOf<ReaderChapter?>(null) }
    var surface by remember(journey) { mutableStateOf<ReadingSurface<*>?>(null) }
    var scalar by remember(journey) { mutableLongStateOf(0L) }
    var generation by remember(journey) { mutableLongStateOf(0L) }
    var ready by remember(journey) { mutableStateOf(false) }
    var busy by remember(journey) { mutableStateOf(true) }
    var fallback by remember(journey) { mutableStateOf(false) }
    var requestedIndex by remember(journey) { mutableStateOf<Long?>(null) }
    var loadFailure by remember(journey) { mutableStateOf<String?>(null) }
    var controls by remember(journey) { mutableStateOf(false) }
    var expanded by remember(journey) { mutableStateOf(false) }
    var speechPanel by remember(journey) { mutableStateOf(false) }
    var sheet by remember(journey) { mutableStateOf<ReaderSheet?>(null) }
    var directory by remember(journey) { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
    var unsettled by remember(journey) { mutableStateOf<ReadingSwitchRecovery?>(null) }
    var leaving by remember(journey) { mutableStateOf(false) }
    val toast = remember(journey) { TransientToastState() }
    val foreground = readerFeedbackEnabled(ownerForeground = true)
    val latestForeground by rememberUpdatedState(foreground)

    fun message(key: String, text: String) { toast.present(key, text, latestForeground) }

    fun accept(chapter: ReaderChapter) {
        surface = null
        ready = false
        generation++
        scalar = chapter.initialScalar
        directory = chapter.entries
        document = chapter
        fallback = chapter.ebookFallback
        loadFailure = null
        if (fallback) message("fallback", "已保留位置并切换为可核对的正文")
    }

    fun capture(action: (Long) -> Unit) {
        val expected = generation
        val shown = surface
        if (shown == null) action(scalar)
        else shown.currentPosition { position ->
            if (generation == expected) action(position?.scalar ?: scalar)
        }
    }

    fun saveOnExit() {
        val chapter = document ?: return
        if (!ready || unsettled != null) return
        val fallbackScalar = scalar
        capture { captured ->
            ownerScope.launch { readerResult { journey.save(chapter, captured) }.onFailure {
                if (!leaving) message("progress", "未能保存当前位置，已保留上次确认的位置")
            } }
        }
        // When the view is removed, its WebView callback can be invalidated. The
        // most recent published position remains a valid save candidate.
        if (leaving && surface is EbookWebViewAdapter) {
            ownerScope.launch { readerResult { journey.save(chapter, fallbackScalar) } }
        }
    }

    val saveLatest by rememberUpdatedState(::saveOnExit)
    DisposableEffect(lifecycle, journey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) saveLatest()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun exit() {
        if (leaving) return
        leaving = true
        val pending = surface
        val chapter = document
        if (pending is EbookWebViewAdapter && chapter != null && ready && unsettled == null) {
            val backup = scalar
            var completed = false
            fun finish(position: Long) {
                if (completed) return
                completed = true
                ownerScope.launch { readerResult { journey.save(chapter, position) } }
                onBack()
            }
            capture(::finish)
            screenScope.launch { kotlinx.coroutines.delay(250); finish(backup) }
        } else {
            saveOnExit()
            onBack()
        }
    }

    PredictiveBackHandler {
        it.collect()
        when {
            sheet != null -> sheet = null
            controls -> controls = false
            else -> exit()
        }
    }

    fun open(index: Long?, bookmark: BookmarkData? = null) {
        if (busy || unsettled != null) return
        requestedIndex = index
        busy = true
        capture { position ->
            screenScope.launch {
                val target = document?.book ?: book
                readerResult { journey.open(target, index, position.takeIf { ready }, bookmark) }
                    .onSuccess { if (!leaving) { accept(it); sheet = null } }
                    .onFailure {
                        loadFailure = it.localizedMessage ?: "无法打开章节"
                        if (document != null && !leaving) sheet = ReaderSheet.FAILED_CHAPTER
                    }
                busy = false
            }
        }
    }

    fun adjacent(delta: Int) {
        val chapter = document ?: return
        val offset = directory.indexOfFirst { it.index == chapter.index }
        directory.getOrNull(offset + delta)?.let { open(it.index) }
    }

    fun refresh() {
        if (busy || unsettled != null) return
        busy = true
        capture { position ->
            screenScope.launch {
                readerResult { journey.refreshContent(position) }.onSuccess { if (!leaving) accept(it) }
                    .onFailure { message("refresh", "正文更新失败，已保留当前正文和位置") }
                busy = false
            }
        }
    }

    fun switch(candidate: M2ChangeBookSourceCandidate) {
        if (busy || unsettled != null) return
        busy = true
        capture { position ->
            screenScope.launch {
                readerResult { journey.switch(candidate, position) }
                    .onSuccess { if (!leaving) { accept(it); onBookChanged(it.book); sheet = null; message("switch", "已切换书源") } }
                    .onFailure {
                        if (it is SwitchUnresolved) {
                            unsettled = ReadingSwitchRecovery.fromFailure(it)
                            sheet = null
                            controls = false
                        } else message("switch", it.localizedMessage ?: "换源失败，已保留原位置")
                    }
                busy = false
            }
        }
    }

    LaunchedEffect(journey) {
        unsettled = readerResult { journey.pendingSwitch(book) }.getOrNull()
        readerResult { journey.open(book, null, null) }.onSuccess {
            accept(it)
            sheet = when {
                unsettled == null && startWithSwitch && it.online != null -> ReaderSheet.SOURCES
                startWithDirectory -> ReaderSheet.DIRECTORY
                else -> null
            }
        }.onFailure { loadFailure = it.localizedMessage ?: "无法打开正文" }
        busy = false
    }

    PaperStatusBar(appearance, keepAwake)
    val paperInsets = WindowInsets.statusBars.getTop(LocalDensity.current)
    val statusHeight = with(LocalDensity.current) { paperInsets.toDp() }
    val readingChapter = document
    val pending = unsettled
    val notice = pending?.let {
        RecoveryNoticeState(it.query.operationId, "换源仍待核对", "核对原事务，或撤回尚未结案的切换。",
            generation, RecoveryAction("核对原操作", generation))
    }.takeIf { !controls && sheet == null }

    ReaderMessageOverlay(true, toast, notice, onVerify = { operation, expected, _ ->
        if (pending?.query?.operationId == operation && generation == expected && !busy) {
            busy = true
            screenScope.launch {
                readerResult { journey.recover(pending, rollback = readingChapter == null) }
                    .onSuccess { recovered ->
                        if (recovered != null) { accept(recovered); onBookChanged(recovered.book); unsettled = null }
                        else sheet = ReaderSheet.SOURCES
                    }.onFailure { message("recover", "暂时无法核对原换源") }
                busy = false
            }
        }
    }, Modifier.fillMaxSize()) {
        ReaderPaperFrame(appearance, statusInset = statusHeight) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(
                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
                if (readingChapter == null) ReaderPageState(
                    loadFailure?.let { PageState.Failure(it, generation) } ?: PageState.Loading,
                    Modifier.align(Alignment.Center), onRetry = { if (it == generation) open(requestedIndex) },
                ) else key(generation) {
                    val expected = generation
                    ReadingSurfaceContent(readingChapter.copy(initialScalar = scalar), appearance, fallback,
                        onPosition = { if (generation == expected && it in 0 until readingChapter.scalarCount) scalar = it },
                        onReady = { mounted ->
                            if (generation == expected) { surface = mounted; ready = mounted != null }
                        },
                        onTap = { controls = !controls; if (!controls) expanded = false },
                        onBoundary = ::adjacent,
                        onFallback = { if (generation == expected) {
                            fallback = true
                            message("fallback", "电子书版式无法显示，已保留位置并切换正文")
                        } },
                    )
                }
                if (readingChapter != null && controls) Box(Modifier.align(Alignment.BottomCenter)) {
                    val chapterPos = directory.indexOfFirst { it.index == readingChapter.index }
                    val chapterNum = if (chapterPos >= 0) chapterPos + 1 else 0
                    ReadingControlsContent(readingChapter.book.title, readingChapter.title, appearance, expanded,
                        onExpand = { expanded = !expanded }, onAppearance = onAppearance,
                        onDirectory = { sheet = ReaderSheet.DIRECTORY }, onBookmarks = { sheet = ReaderSheet.BOOKMARKS },
                        onPrevious = { adjacent(-1) }, onNext = { adjacent(1) }, onBack = ::exit,
                        onRefresh = if (readingChapter.online == null) null else ::refresh,
                        onSwitch = if (readingChapter.online == null) null else ({ sheet = ReaderSheet.SOURCES }),
                        onSpeech = speech?.let { { speechPanel = true } },
                        chapterNumber = chapterNum, chapterCount = directory.size,
                    )
                }
                if (speechPanel && readingChapter != null && speech != null) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        SpeechReadingControls(readingChapter, speech)
                    }
                }
            }
        }
    }

    val layer = sheet
    if (layer != null && readingChapter != null) ModalBottomSheet(onDismissRequest = { sheet = null }) {
        when (layer) {
            ReaderSheet.DIRECTORY -> ReadingDirectorySheet(directory, readingChapter.index,
                onSelect = { open(it) },
                onRefresh = if (readingChapter.online == null) null else suspend {
                    directory = journey.refreshDirectory(readingChapter.book)
                })
            ReaderSheet.BOOKMARKS -> ReadingBookmarkSheet(readingChapter, journey, onAdd = {
                val at = scalar
                journey.bookmark(readingChapter, at)
            }, onJump = { open(it.chapterIndex, it) })
            ReaderSheet.SOURCES -> if (pending == null) ReadingSourceSheet(readingChapter.book, journey, busy, ::switch)
            else PendingSourceSwitchSheet(busy) {
                busy = true
                screenScope.launch {
                    readerResult { journey.recover(pending, rollback = true) }.onSuccess {
                        if (it != null) {
                            accept(it)
                            onBookChanged(it.book)
                            unsettled = null
                            sheet = null
                        } else message("recover", "暂未收到换源结果，请稍后核对")
                    }.onFailure { message("recover", "无法撤回换源，已保留上次记录") }
                    busy = false
                }
            }
            ReaderSheet.FAILED_CHAPTER -> ReaderPageState(PageState.Failure(
                loadFailure ?: "无法加载章节", generation), onRetry = { if (it == generation) open(requestedIndex) })
        }
    }
}

@Composable
private fun PaperStatusBar(appearance: ReadingAppearance, keepAwake: Boolean) {
    val window = (LocalContext.current as? Activity)?.window ?: return
    val defaults = LocalReaderColors.current.night
    DisposableEffect(window, keepAwake) {
        if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (keepAwake) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(window, appearance.paper.id, defaults) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = Color(appearance.paper.statusForeground.toInt()).luminance() < .45f
        onDispose { insetsController.isAppearanceLightStatusBars = !defaults }
    }
}
