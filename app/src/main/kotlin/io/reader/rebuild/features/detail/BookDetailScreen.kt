package io.reader.rebuild.features.detail

import io.reader.rebuild.features.library.BookReference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.reader.core.protocol.M2BookDetailData
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.features.bookshelf.ShelfLibrary
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import io.reader.rebuild.core.readerResult
import kotlinx.coroutines.launch

@Composable
internal fun BookDetailScreen(
    input: BookReference,
    shelf: ShelfLibrary,
    online: OnlineReadingCoordinator,
    onRead: (BookReference, Boolean, Boolean) -> Unit,
    onDownload: (BookReference) -> Unit,
    onShelfChanged: () -> Unit,
    feedback: (String, String) -> Unit,
) {
    var book by remember(input.key) { mutableStateOf(input) }
    var detail by remember(input.key) { mutableStateOf<M2BookDetailData?>(null) }
    var inShelf by remember(input.key) { mutableStateOf<Boolean?>(null) }
    var state by remember(input.key) { mutableStateOf<PageState>(PageState.Loading) }
    var confirmation by remember(input.key) { mutableStateOf<ReaderConfirmationState?>(null) }
    var generation by remember(input.key) { mutableLongStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(input.key, generation) {
        readerResult {
            val existing = shelf.get(input.key)
            if (input.local) {
                book = BookReference(checkNotNull(existing) { "书籍已移出书架" })
            } else {
                val cached = online.cachedDirectory(io.reader.rebuild.features.reading.OnlineBookKey(input.sourceId, input.bookId))
                readerResult { online.detail(input.detailParams) }.onSuccess {
                    detail = it
                    book = input.fromDetail(it)
                }.onFailure { failure -> if (cached == null) throw failure else feedback("detail-cache", "在线详情更新失败，仍可打开缓存章节") }
            }
            inShelf = existing != null
        }.onFailure {
            state = PageState.Failure(it.localizedMessage ?: "无法加载详情", generation)
            inShelf = null
        }
    }
    val membership = inShelf
    if (membership == null) ReaderPageState(state, onRetry = { if (it == generation) generation++ })
    else BookDetailContent(book, membership,
        onRead = { onRead(book, false, false) }, onDirectory = { onRead(book, true, false) }, onShelf = {
            if (membership) confirmation = ReaderConfirmationState("移出书架", "将《${book.title}》移出书架？", "移出", generation)
            else if (detail != null) scope.launch {
                readerResult { online.addToShelf(checkNotNull(detail)) }.onSuccess {
                    inShelf = true
                    onShelfChanged()
                    feedback("shelf-add", "已加入书架")
                }.onFailure { feedback("shelf-add", it.localizedMessage ?: "加入书架失败") }
            } else feedback("shelf-add", "请在线更新详情后重试")
        }, onDownload = if (book.local) null else ({ onDownload(book) }),
        onSwitch = if (book.local) null else ({ onRead(book, false, true) }))
    ReaderConfirmation(confirmation, ownerForeground = true, onDismiss = { confirmation = null }) {
        confirmation = null
        scope.launch {
            readerResult { shelf.remove(book.key) }.onSuccess {
                inShelf = false
                onShelfChanged()
                feedback("shelf-remove", "已移出书架")
            }.onFailure { feedback("shelf-remove", it.localizedMessage ?: "移出书架失败") }
        }
    }
}
