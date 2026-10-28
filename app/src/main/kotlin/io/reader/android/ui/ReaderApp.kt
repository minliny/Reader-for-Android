package io.reader.android.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.testTag
import io.reader.android.R
import io.reader.android.runtime.LibraryBook
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReaderApp(model: ReaderViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val view = LocalView.current
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.onBackground() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, state.keepScreenOn, state.route) {
        val old = view.keepScreenOn
        view.keepScreenOn = state.keepScreenOn && state.route == ReaderRoute.Reading
        onDispose { view.keepScreenOn = old }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importDocument(uri)
    }
    val importAllowed = !state.busy && state.pendingReady && state.pendingImports.isEmpty()
    val requestImport = { if (importAllowed) picker.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) }
    BackHandler(enabled = state.route != ReaderRoute.Bookshelf) { model.back() }
    ReaderTheme(state.theme) {
        if (state.route == ReaderRoute.Reading && state.reading != null) {
            ReaderReadingScreen(state, model)
        } else {
            Scaffold(
                modifier = Modifier.semantics { testTagsAsResourceId = true },
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.route == ReaderRoute.Directory) {
                                IconButton(onClick = model::back, enabled = !state.busy,
                                    modifier = Modifier.testTag("reader_back")) {
                                    Icon(painterResource(R.drawable.reader_back), "返回")
                                }
                            }
                            Text(when (state.route) {
                                ReaderRoute.Bookshelf -> "书架"
                                ReaderRoute.Settings -> "设置"
                                ReaderRoute.Directory -> "目录"
                                else -> "阅读"
                            }, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                            if (state.route == ReaderRoute.Bookshelf) {
                                IconButton(onClick = requestImport, enabled = importAllowed,
                                    modifier = Modifier.testTag("reader_import")) {
                                    Icon(painterResource(R.drawable.reader_import), "导入本地书")
                                }
                            }
                        }
                        if (state.busy) LoadingLine()
                    }
                },
                bottomBar = {
                    if (state.route != ReaderRoute.Directory) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center) {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp),
                                tonalElevation = 1.dp, shadowElevation = 3.dp) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    TabAction("书架", R.drawable.reader_library, state.route == ReaderRoute.Bookshelf,
                                        !state.busy, "reader_tab_library", model::showBookshelf)
                                    TabAction("设置", R.drawable.reader_settings, state.route == ReaderRoute.Settings,
                                        !state.busy, "reader_tab_settings", model::showSettings)
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (state.pendingImports.isNotEmpty() || (!state.pendingReady && state.ready)) {
                        ReaderRecoveryNotice("有一次导入尚未确认。核对原操作后再导入新的书籍。", state.busy,
                            model::reconcilePendingImports)
                    } else state.error?.let { ReaderTransientFeedback(it, model::clearError) }
                    state.pendingPositions.firstOrNull()?.let { pending ->
                        ReaderRecoveryNotice("原进度保存尚未确认，原记录会继续保留。", state.busy,
                            { model.reconcilePendingPosition(pending) }, "核对原保存", "reader_reconcile_position")
                    }
                    when (state.route) {
                        ReaderRoute.Bookshelf -> BookshelfScreen(state, model, requestImport)
                        ReaderRoute.Settings -> SettingsScreen(state, model)
                        ReaderRoute.Directory -> DirectoryScreen(state, model)
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TabAction(label: String, icon: Int, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.widthIn(min = 104.dp).clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 6.dp).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(23.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BookshelfScreen(state: ReaderUiState, model: ReaderViewModel, onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            !state.ready && state.busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
            !state.ready -> ReaderPageState("书架暂时不可用", "请重试读取已有书籍。", "重试", model::refreshBooks)
            state.books.isEmpty() -> Column(Modifier.widthIn(max = 480.dp).padding(24.dp).testTag("reader_empty_shelf")) {
                Text("我的书架", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(18.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(painterResource(R.drawable.reader_library), null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("让阅读从这里开始", style = MaterialTheme.typography.titleLarge)
                        Text("导入本地 TXT，将喜欢的书放进书架。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onImport, enabled = !state.busy && state.pendingReady && state.pendingImports.isEmpty(),
                            modifier = Modifier.testTag("reader_empty_import")) {
                            Text("导入本地书")
                        }
                    }
                }
            }
            else -> LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("reader_bookshelf"),
                contentPadding = PaddingValues(horizontal = 19.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("我的书架 · ${state.books.size}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.books, key = { it.id }) { book -> BookRow(book, state.busy, model) }
            }
        }
    }
}

@Composable
private fun BookRow(book: LibraryBook, busy: Boolean, model: ReaderViewModel) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { model.openBook(book) }
            .padding(15.dp).testTag("reader_book_${book.id}"), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), shape = RoundedCornerShape(5.dp)) {
                Box(Modifier.width(54.dp).height(76.dp).padding(7.dp), contentAlignment = Alignment.Center) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("本地 TXT · ${book.chapterCount} 章", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("继续阅读 · 第 ${book.progressChapter + 1} 章", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { model.showDirectory(book) }, enabled = !busy,
                modifier = Modifier.testTag("reader_book_directory_${book.id}")) {
                Icon(painterResource(R.drawable.reader_directory), "${book.title}的目录")
            }
        }
    }
}

@Composable
private fun DirectoryScreen(state: ReaderUiState, model: ReaderViewModel) {
    if (state.chapters.isEmpty() && !state.busy) {
        ReaderPageState("暂无目录", "目录读取完成后可选择章节。", "重试", { model.showDirectory() })
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("reader_directory"), contentPadding = PaddingValues(horizontal = 19.dp)) {
        item { Text(state.selectedBook?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp)) }
        items(state.chapters, key = { it.index }) { chapter ->
            val current = chapter.index == (state.reading?.chapter?.index ?: state.selectedBook?.progressChapter)
            Text(chapter.title.ifBlank { "第 ${chapter.index + 1} 章" },
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { model.selectChapter(chapter) }
                    .padding(vertical = 16.dp).testTag("reader_chapter_${chapter.index}"))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
        }
    }
}

@Composable
private fun SettingsScreen(state: ReaderUiState, model: ReaderViewModel) {
    LazyColumn(Modifier.fillMaxSize().testTag("reader_settings"), contentPadding = PaddingValues(19.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SettingsGroup("外观") {
            Text("应用主题", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ReaderAppTheme.System to "跟随", ReaderAppTheme.Light to "浅色", ReaderAppTheme.Dark to "深色").forEach { (value, text) ->
                    FilterChip(selected = state.theme == value, onClick = { model.setTheme(value) }, label = { Text(text) },
                        modifier = Modifier.testTag("reader_theme_${value.name}"))
                }
            }
        } }
        item { SettingsGroup("阅读") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("阅读时保持亮屏", style = MaterialTheme.typography.titleMedium)
                    Text("仅在正文阅读期间生效", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.keepScreenOn, onCheckedChange = model::setKeepScreenOn,
                    modifier = Modifier.testTag("reader_keep_screen_on"))
            }
        } }
        item { SettingsGroup("关于 Reader") {
            Text("书籍保存在本机", style = MaterialTheme.typography.titleMedium)
            Text("书架和阅读位置会自动保留。应用外观与阅读纸色可分别设置。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
internal fun LoadingLine() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("正在处理", style = MaterialTheme.typography.bodySmall)
    }
}
