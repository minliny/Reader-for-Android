package io.reader.rebuild.features.downloads

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheBookPrefetchParamsContentKind
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2CacheDownloadTaskParams
import com.reader.core.protocol.M2CacheStorageData
import com.reader.core.protocol.M2CacheStorageParams
import com.reader.core.protocol.M2CacheStorageParamsContentScope
import com.reader.core.protocol.M2CacheStorageParamsReason
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.RecoveryAction
import io.reader.rebuild.design.RecoveryNoticeState
import io.reader.rebuild.design.TransientToastState
import io.reader.rebuild.features.bookshelf.ShelfLibrary
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val textScope = M2CacheStorageParamsContentScope.TEXT

@Composable
internal fun DownloadScreen(
    shelf: ShelfLibrary,
    online: OnlineReadingCoordinator,
    coordinator: DownloadCoordinator,
    scoped: BookReference?,
    onFeedback: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast = remember { TransientToastState() }
    val owner = remember(coordinator) { coordinator.panelGeneration }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var visibleRows by remember(owner, scoped) { mutableStateOf(emptyList<DownloadRow>()) }
    var storage by remember(owner, scoped) { mutableStateOf<M2CacheStorageData?>(null) }
    var pending by remember(owner, scoped) { mutableStateOf<List<M2CacheBookPrefetchParams>>(emptyList()) }
    var page by remember(owner, scoped) { mutableStateOf<PageState>(PageState.Loading) }
    var confirmation by remember(owner) { mutableStateOf<ReaderConfirmationState?>(null) }
    var confirmGeneration by remember(owner) { mutableLongStateOf(0L) }
    var revision by remember(owner) { mutableIntStateOf(0) }
    var verifying by remember(owner) { mutableStateOf(false) }

    DisposableEffect(owner) { onDispose { coordinator.retirePanel() } }

    suspend fun prepare(book: BookReference) {
        val detail = online.detail(book.detailParams)
        online.refreshDirectory(M2BookTocParams(sourceId = book.sourceId, bookId = book.bookId,
            tocUrl = detail.tocUrl ?: book.tocUrl, variables = detail.variables ?: book.variables))
    }

    fun action(key: String, message: String, body: suspend () -> Unit) {
        scope.launch {
            try {
                body()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (coordinator.panelGeneration == owner) onFeedback(key, message)
            } finally {
                if (coordinator.panelGeneration == owner) revision++
            }
        }
    }

    fun launchPrefetch(book: BookReference, range: List<Long>) {
        action("download.start", "下载未完成，请核对原任务") {
            val operation = coordinator.begin(M2CacheBookPrefetchParams(sourceId = book.sourceId, bookId = book.bookId,
                chapterRange = range, contentKind = M2CacheBookPrefetchParamsContentKind.TEXT))
            revision++
            operation.await()
        }
    }

    LaunchedEffect(owner, revision, scoped, lifecycle) {
        if (!lifecycle.isAtLeast(Lifecycle.State.STARTED)) return@LaunchedEffect
        if (scoped != null && visibleRows.isEmpty()) {
            try {
                val read = coordinator.status(scoped.statusParams())
                if (!read.tocAvailable) prepare(scoped)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                // The status projection below preserves cached chapters and allows explicit preparation.
            }
        }
        var shelfBooks: List<BookReference>? = null
        var refreshAll = true
        while (isActive && coordinator.panelGeneration == owner) {
            val prior = visibleRows.associateBy(DownloadRow::key)
            try {
                val original = coordinator.pendingPrefetches()
                val refs = if (scoped != null) listOf(scoped) else {
                    val listed = shelfBooks ?: shelf.list().filterNot { it.sourceId == "local" }.map(::BookReference)
                        .also { shelfBooks = it }
                    listed + original.filter { item -> listed.none { it.sourceId == item.sourceId && it.bookId == item.bookId } }
                        .map { BookReference(it.sourceId, it.bookId, it.bookId.substringAfterLast('/').ifBlank { "在线书籍" }) }
                }
                val unresolved = original.filter { item ->
                    !coordinator.isWorking(item.sourceId, item.bookId) &&
                        refs.any { it.sourceId == item.sourceId && it.bookId == item.bookId }
                }
                val rows = refs.distinctBy { it.sourceId to it.bookId }.map { book ->
                    val working = coordinator.isWorking(book.sourceId, book.bookId)
                    val waiting = unresolved.any { it.sourceId == book.sourceId && it.bookId == book.bookId }
                    val key = DownloadRow(book).key
                    val previous = prior[key]
                    if (!refreshAll && !working && previous?.working == false && previous.recovering == waiting) {
                        return@map previous
                    }
                    try {
                        val status = coordinator.status(book.statusParams())
                        check(status.sourceId == book.sourceId && status.bookId == book.bookId)
                        DownloadRow(book, status, working, recovering = waiting)
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        DownloadRow(book, previous?.status, working, "状态读取失败，可刷新重试", waiting)
                    }
                }
                val summary = try {
                    if (refreshAll || rows.any(DownloadRow::working) || prior.values.any(DownloadRow::working)) {
                        coordinator.summary(M2CacheStorageParams(contentScope = textScope))
                    } else null
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    null
                }
                if (!isActive || coordinator.panelGeneration != owner) return@LaunchedEffect
                visibleRows = rows
                if (summary != null) storage = summary
                pending = unresolved
                page = PageState.Empty("暂没有在线书籍需要下载")
                refreshAll = false
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (visibleRows.isEmpty()) page = PageState.Failure("暂时无法读取下载状态", revision.toLong())
            }
            delay(if (visibleRows.any { it.working }) 500 else 2_000)
        }
    }

    val original = pending.firstOrNull()
    val recovery = if (original == null || verifying) null else RecoveryNoticeState(
        requireNotNull(original.operationId), "下载任务待核对",
        "按原操作核对下载结果。", owner, RecoveryAction("核对原任务", owner),
    )
    val cached = storage
    ReaderMessageOverlay(confirmation == null, toast, recovery, { id, generation, actionGeneration ->
        val matching = pending.firstOrNull { it.operationId == id }
        if (matching != null && generation == owner && actionGeneration == owner &&
            coordinator.panelGeneration == owner && !verifying) {
            verifying = true
            action("download.verify", "原任务仍待核对") {
                try { coordinator.recoverPrefetch(matching) } finally { verifying = false }
            }
        }
    }, Modifier.fillMaxSize()) {
        DownloadContent(visibleRows, cached?.let { "共 ${bytes(it.totalBytes)} · 可清理 ${bytes(it.cleanableBytes)}" },
            state = page, expandedByDefault = scoped != null, canClean = (cached?.cleanableBytes ?: 0) > 0,
            onStart = { row -> row.startRange?.let { launchPrefetch(row.book, it) } },
            onControl = { row, chapter, control ->
                action("download.control", "暂时无法确认${control.title}结果") {
                    val params = M2CacheDownloadTaskParams(row.book.sourceId, row.book.bookId, chapter.chapterIndex)
                    when (control) {
                        DownloadAction.PAUSE -> coordinator.pause(params)
                        DownloadAction.CANCEL -> coordinator.cancel(params)
                        DownloadAction.RESUME -> {
                            coordinator.resume(params)
                            if (!coordinator.isWorking(row.book.sourceId, row.book.bookId)) {
                                val exact = coordinator.pendingPrefetches().firstOrNull {
                                    it.sourceId == row.book.sourceId && it.bookId == row.book.bookId
                                }
                                if (exact != null) coordinator.recoverPrefetch(exact)
                                else launchPrefetch(row.book, listOf(chapter.chapterIndex, chapter.chapterIndex + 1))
                            }
                        }
                    }
                }
            },
            onPrepare = { row -> action("download.catalog", "目录暂不可读") { prepare(row.book) } },
            onClean = {
                confirmation = ReaderConfirmationState("清理正文缓存？",
                    "可清理的在线正文将移除。书架与阅读进度会保留。", "清理", ++confirmGeneration)
            },
            onRetry = { if (it == revision.toLong()) revision++ }, onRefresh = { revision++ })
    }
    ReaderConfirmation(confirmation, coordinator.panelGeneration == owner, { expected ->
        if (confirmation?.generation == expected) confirmation = null
    }, { expected ->
        if (confirmation?.generation == expected && coordinator.panelGeneration == owner) {
            confirmation = null
            action("cache.clear", "暂时无法清理正文缓存") {
                val result = coordinator.cleanup(M2CacheStorageParams(reason = M2CacheStorageParamsReason.CLEAR,
                    contentScope = textScope))
                if (coordinator.panelGeneration == owner) {
                    if (result.cleanupError == null && !result.hasMore) onFeedback("cache.clear", "缓存清理完成")
                    else onFeedback("cache.clear.partial", "部分缓存尚未清理，可重新核对")
                }
            }
        }
    })
}

private fun BookReference.statusParams() =
    M2CacheBookStatusParams(sourceId, bookId, includeGlobalStats = false, includeChapterStates = true)

private fun bytes(total: Long): String = when {
    total >= 1_048_576 -> "${total / 1_048_576} MiB"
    total >= 1_024 -> "${total / 1_024} KiB"
    else -> "$total B"
}
