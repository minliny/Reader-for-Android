package io.reader.rebuild.features.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.reader.core.protocol.M2BookSearchDataContentKind
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.core.readerResult
import kotlinx.coroutines.launch

@Composable
internal fun SearchScreen(coordinator: SearchCoordinator, sources: suspend () -> Map<String, String>, onBook: (BookReference) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var hasQuery by rememberSaveable { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(coordinator.inFlight) }
    var status by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf(emptyList<String>()) }
    var sourceNames by remember { mutableStateOf(emptyMap<String, String>()) }
    val results by coordinator.results.collectAsState()
    val running by coordinator.running.collectAsState()
    val scope = rememberCoroutineScope()
    DisposableEffect(coordinator) { onDispose { coordinator.stopDisplaying() } }
    LaunchedEffect(coordinator, running) {
        if (!running) readerResult { coordinator.history(12) }.onSuccess { history = it.keywords }
    }
    LaunchedEffect(coordinator) { readerResult { sources() }.onSuccess { sourceNames = it } }

    fun dispatch(term: String?) {
        if (coordinator.inFlight) return
        status = null
        stopped = false
        if (term != null) query = term
        val operation = readerResult { if (term == null) coordinator.retry() else coordinator.start(term) }
        operation.onFailure { status = it.localizedMessage ?: "无法搜索" }.onSuccess { search ->
            hasQuery = true
            scope.launch {
                readerResult { search.await() }.onSuccess { terminal ->
                    if (!stopped) status = when {
                        terminal.isEmpty() -> "没有已启用的书源"
                        terminal.all { it.result.isFailure } -> "书源检索失败，可以重试"
                        terminal.none { entry -> entry.result.getOrNull()?.books?.isNotEmpty() == true } -> "未找到图书"
                        else -> null
                    }
                }.onFailure { status = it.localizedMessage ?: "搜索失败" }
            }
        }
    }

    val books = results.flatMap { settled ->
        val data = settled.result.getOrNull()
        if (data == null || data.contentKind == M2BookSearchDataContentKind.MANGA ||
            data.contentKind == M2BookSearchDataContentKind.AUDIO) emptyList()
        else data.books.map { BookReference(data.sourceId, it, sourceNames[data.sourceId]) }
    }.distinctBy { it.key }
    SearchContent(query, history, books, running, stopped, { query = it }, ::dispatch, {
        coordinator.stopDisplaying()
        stopped = true
    }, onRetry = if (hasQuery) ({ dispatch(null) }) else null, onBook = onBook, status = status)
}
