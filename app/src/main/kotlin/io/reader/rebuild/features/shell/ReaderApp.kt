package io.reader.rebuild.features.shell

import android.app.Activity
import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.design.LocalCoverBytes
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.ReaderTaskProgress
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.ReaderTheme
import io.reader.rebuild.design.ReaderTopBar
import io.reader.rebuild.design.RecoveryAction
import io.reader.rebuild.design.RecoveryNoticeState
import io.reader.rebuild.design.TransientToastState
import io.reader.rebuild.design.readerFeedbackEnabled
import io.reader.rebuild.features.bookshelf.BookshelfContent
import io.reader.rebuild.features.detail.BookDetailScreen
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.downloads.DownloadScreen
import io.reader.rebuild.features.manga.MangaReadingScreen
import io.reader.rebuild.features.reading.ReadingJourney
import io.reader.rebuild.features.reading.ReadingScreen
import io.reader.rebuild.features.search.SearchScreen
import io.reader.rebuild.features.settings.AppAppearance
import io.reader.rebuild.features.settings.SettingsScreen
import io.reader.rebuild.features.settings.ShelfSort
import io.reader.rebuild.features.source.SourceManagementScreen
import io.reader.rebuild.host.storage.AuthorizedDocument
import io.reader.rebuild.host.storage.SystemDocumentPicker
import kotlinx.coroutines.launch

internal sealed interface Destination {
    data object Search : Destination
    data object Sources : Destination
    data object Settings : Destination
    data class Detail(val book: BookReference) : Destination
    data class Read(val book: BookReference, val directory: Boolean, val sources: Boolean) : Destination
    data class Downloads(val book: BookReference? = null) : Destination
}

internal data class Route(val id: Long, val page: Destination)

@Composable
internal fun ReaderApp(owner: ReaderActivityOwner) {
    val library by owner.library.collectAsStateWithLifecycle()
    var theme by remember(owner) { mutableStateOf(owner.preferences.theme()) }
    var reading by remember(owner) { mutableStateOf(owner.preferences.reading()) }
    var shelfSort by remember(owner) { mutableStateOf(owner.preferences.shelfSort()) }
    var keepAwake by remember(owner) { mutableStateOf(owner.preferences.keepAwake()) }
    val night = theme == AppAppearance.NIGHT || theme == AppAppearance.SYSTEM && isSystemInDarkTheme()
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window, night) {
        if (window != null) WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !night
        onDispose {}
    }
    ReaderTheme(night) {
        val active = library?.getOrNull()
        if (active == null) {
            Box(Modifier.fillMaxSize().background(LocalReaderColors.current.background)
                .windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
                ReaderPageState(library?.exceptionOrNull()?.let {
                    PageState.Failure("无法打开阅读库：${it.localizedMessage}", 0)
                } ?: PageState.Loading, onRetry = { owner.open() })
            }
        } else ReaderProduct(active, owner.navigation, theme, reading, shelfSort, keepAwake, onTheme = {
            theme = it
            owner.preferences.setTheme(it)
        }, onAppearance = {
            reading = it
            owner.preferences.setReading(it)
        }, onSort = {
            shelfSort = it
            owner.preferences.setShelfSort(it)
        }, onKeepAwake = {
            keepAwake = it
            owner.preferences.setKeepAwake(it)
        })
    }
}

@Composable
private fun ReaderProduct(
    session: ReaderSession,
    navigation: ReaderNavigation,
    theme: AppAppearance,
    appearance: io.reader.rebuild.features.reading.ReadingAppearance,
    shelfSort: ShelfSort,
    keepAwake: Boolean,
    onTheme: (AppAppearance) -> Unit,
    onAppearance: (io.reader.rebuild.features.reading.ReadingAppearance) -> Unit,
    onSort: (ShelfSort) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
) {
    val saved = rememberSaveableStateHolder()
    val route = navigation.current
    val scope = rememberCoroutineScope()
    val toast = remember(route?.id) { TransientToastState() }
    val foreground = readerFeedbackEnabled(ownerForeground = true)
    val currentRoute by rememberUpdatedState(route?.id)
    val isForeground by rememberUpdatedState(foreground)
    var books by remember { mutableStateOf<List<BookshelfEntryData>?>(null) }
    var shelfGeneration by remember { mutableLongStateOf(0L) }
    var shelfFailure by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<BookReference?>(null) }
    var confirm by remember { mutableStateOf<BookReference?>(null) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var importRunning by remember { mutableStateOf(false) }
    val colors = LocalReaderColors.current
    val resolver = LocalContext.current.contentResolver
    val capturedId = route?.id

    fun feedback(key: String, text: String) {
        toast.present(key, text, isForeground && currentRoute == capturedId)
    }
    fun navigate(destination: Destination) {
        toast.hide()
        navigation.open(destination)
    }
    fun back() {
        val removed = navigation.back() ?: return
        saved.removeState(removed.id)
        toast.hide()
        if (navigation.current == null) shelfGeneration++
    }

    LaunchedEffect(session, shelfGeneration, shelfSort) {
        val loaded = readerResult { session.shelf.list(shelfSort.sort, shelfSort.direction) }
        loaded.onSuccess {
            books = it
            shelfFailure = null
            if (selected != null && it.none { entry -> BookReference(entry).key == selected?.key }) selected = null
        }.onFailure { shelfFailure = it.localizedMessage ?: "无法读取书架" }
        pendingImport = readerResult { session.importer.pending() }.getOrNull()?.firstOrNull()
    }

    var documentAction by remember { mutableStateOf<((AuthorizedDocument) -> Unit)?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        val consume = documentAction
        documentAction = null
        val uri = response.data?.data
        if (response.resultCode == Activity.RESULT_OK && uri != null && consume != null) {
            readerResult { SystemDocumentPicker.granted(resolver, uri) }
                .onSuccess(consume).onFailure { feedback("saf", "无法读取所选文件") }
        }
    }
    fun pick(consume: (AuthorizedDocument) -> Unit) {
        documentAction = consume
        picker.launch(SystemDocumentPicker.request())
    }
    fun importBook() {
        if (importRunning) return
        pick { document ->
            if (document.name.substringAfterLast('.', "").lowercase() !in setOf("txt", "epub", "mobi")) {
                feedback("saf-format", "请选择 TXT、EPUB 或 MOBI 文件")
            } else {
                importRunning = true
                session.actions.launch {
                    readerResult { session.importer.import(document) }.onSuccess { feedback("import", "已加入书架") }
                        .onFailure {
                            Log.w("Reader", "Original book import failed", it)
                            feedback("import", it.localizedMessage ?: "导入失败")
                        }
                    importRunning = false
                    shelfGeneration++
                }
            }
        }
    }

    val remove = confirm
    ReaderConfirmation(remove?.let {
        ReaderConfirmationState("移出书架", "将《${it.title}》移出书架？", "移出", shelfGeneration)
    }, ownerForeground = route == null, onDismiss = { confirm = null }) {
        confirm = null
        if (remove != null) session.actions.launch {
            readerResult { session.shelf.remove(remove.key) }
                .onSuccess { shelfGeneration++; feedback("remove", "已移出书架") }
                .onFailure { feedback("remove", it.localizedMessage ?: "移出书架失败") }
        }
    }

    val pending = pendingImport
    val notice = when {
        route != null || remove != null -> null
        pending != null -> RecoveryNoticeState(pending, "本地导入仍待核对", "保留原文件和导入身份，可核对原操作。",
            shelfGeneration, RecoveryAction("核对导入", shelfGeneration))
        shelfFailure != null && books != null -> RecoveryNoticeState("shelf", "书架更新失败", checkNotNull(shelfFailure),
            shelfGeneration, RecoveryAction("重新读取", shelfGeneration))
        else -> null
    }
    PredictiveBackHandler(enabled = route != null && route.page !is Destination.Read && route.page != Destination.Sources) {
        it.collect {}
        back()
    }

    val coverBytes: suspend (String) -> ByteArray? = remember(session) { { url -> session.cover(url) } }
    CompositionLocalProvider(LocalCoverBytes provides coverBytes) {
        ReaderMessageOverlay(true, toast, notice, onVerify = { operation, expected, _ ->
            if (expected == shelfGeneration) {
                if (operation == "shelf") shelfGeneration++
                else if (operation == pendingImport) scope.launch {
                    val finalized = readerResult { session.importer.verify(operation) }.getOrNull() == true
                    if (!finalized) feedback("import-verify", "原导入仍未结案，已保留原文件")
                    shelfGeneration++
                }
            }
        }, Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val wideShell = maxWidth >= ReaderDesignMetrics.wideBreakpoint.dp
            val mainDestination = if (route?.page == Destination.Settings) MainDestination.Settings else MainDestination.Shelf
            val showMainNavigation = route == null || route?.page == Destination.Settings
            ReaderRouteMotion(route?.id, navigation.popping) {
                val destination = route?.page
                if (destination is Destination.Read) key(route.id) {
                    val journey = remember(route.id) { ReadingJourney(session.core, session.local, session.online, session.switching) }
                    if (destination.book.isManga()) {
                        MangaReadingScreen(destination.book, session.mangaReader(), ::back, ::feedback)
                    } else ReadingScreen(destination.book, destination.directory, destination.sources, journey, session.actions,
                        appearance, onAppearance, ::back, keepAwake = keepAwake, onBookChanged = {
                            navigation.updateReadingBook(it)
                            selected = it
                            shelfGeneration++
                        }, speech = session.speech)
                } else Column(
                    Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(start = if (showMainNavigation && wideShell) 94.dp else 0.dp,
                            bottom = if (showMainNavigation && !wideShell) 94.dp else 0.dp),
                ) {
                    if (destination == null) {
                        ReaderTopBar("书架") {
                            ReaderAction(ReaderIconRegistry.search, "全局搜索", { navigate(Destination.Search) })
                            ReaderHomeMenu(
                                onSources = { navigate(Destination.Sources) },
                                onDownloads = { navigate(Destination.Downloads()) },
                                onSettings = { navigate(Destination.Settings) },
                                onImport = ::importBook,
                                onCheckUpdates = { feedback("updates", "当前仅支持已准入书源更新") },
                                onShelfData = { feedback("shelf-data", "书架数据管理将在本地备份恢复准入后开放") },
                            )
                        }
                        if (importRunning) ReaderTaskProgress(ReaderTaskProgressState("正在导入所选文件", 0, 0, 1, 0))
                        val rows = books
                        if (rows == null) ReaderPageState(shelfFailure?.let { PageState.Failure(it, shelfGeneration) } ?: PageState.Loading,
                            onRetry = { if (it == shelfGeneration) shelfGeneration++ })
                        else BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wide = maxWidth >= ReaderDesignMetrics.wideBreakpoint.dp
                            BookshelfContent(rows, onBook = {
                                val book = BookReference(it)
                                if (wide) selected = book else navigate(Destination.Detail(book))
                            }, onRemove = { confirm = BookReference(it) }, side = selected?.let { book ->
                                { BookDetailScreen(book, session.shelf, session.online,
                                    { value, directory, sources -> navigate(Destination.Read(value, directory, sources)) },
                                    { navigate(Destination.Downloads(it)) }, { shelfGeneration++ }, ::feedback) }
                            }, onImport = ::importBook, onSearch = { navigate(Destination.Search) })
                        }
                    } else saved.SaveableStateProvider(checkNotNull(route).id) {
                        if (destination != Destination.Sources) ReaderTopBar(when (destination) {
                            Destination.Search -> "全局搜索"
                            Destination.Settings -> "设置"
                            is Destination.Detail -> "书籍详情"
                            is Destination.Downloads -> "下载管理"
                            else -> ""
                        }, if (destination == Destination.Settings) null else ::back,
                            actions = if (destination is Destination.Detail) {
                                { ReaderAction(ReaderIconRegistry.more, "书籍操作", { feedback("detail-menu", "书籍操作") }) }
                            } else {{}})
                        when (destination) {
                            Destination.Search -> SearchScreen(session.search, sources = {
                                session.sources.list(M2SourceListParams()).sources.associate { it.sourceId to it.name }
                            }) { navigate(Destination.Detail(it)) }
                            Destination.Sources -> SourceManagementScreen(session.sources, ::back, ::pick)
                            Destination.Settings -> SettingsScreen(theme, appearance, shelfSort, keepAwake, onTheme, onAppearance, onSort, onKeepAwake)
                            is Destination.Detail -> BookDetailScreen(destination.book, session.shelf, session.online,
                                { value, directory, sources -> navigate(Destination.Read(value, directory, sources)) },
                                { navigate(Destination.Downloads(it)) }, { shelfGeneration++ }, ::feedback)
                            is Destination.Downloads -> DownloadScreen(session.shelf, session.online, session.downloads,
                                destination.book, ::feedback)
                            else -> Unit
                        }
                    }
                }
            }
            if (showMainNavigation) {
                Box(Modifier.fillMaxSize(), contentAlignment = if (wideShell) Alignment.CenterStart else Alignment.BottomCenter) {
                    ReaderBottomNavigation(mainDestination, wideShell,
                        onSelect = { destination ->
                            if (destination == MainDestination.Settings && route == null) navigate(Destination.Settings)
                            else if (destination == MainDestination.Shelf && route?.page == Destination.Settings) back()
                        },
                        onSelectedTap = { destination ->
                            if (destination == MainDestination.Shelf && route == null) shelfGeneration++
                            if (destination == MainDestination.Settings && route?.page == Destination.Settings) { /* keep settings at top */ }
                        })
                }
            }
            }
        }
    }
}

private fun BookReference.isManga(): Boolean = kind?.lowercase() in setOf("manga", "comic", "漫画")
