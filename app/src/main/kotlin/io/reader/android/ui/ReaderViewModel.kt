package io.reader.android.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.reader.android.runtime.ChapterItem
import io.reader.android.runtime.LibraryBook
import io.reader.android.runtime.ReaderChapter
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.PendingImport
import io.reader.android.runtime.PendingPosition
import io.reader.android.runtime.PendingOperationException
import io.reader.android.runtime.coreProtocolPending
import io.reader.android.runtime.CoreProtocolReference
import io.reader.android.ReaderApplication
import io.reader.android.runtime.CoreCommandException
import io.reader.android.runtime.CoreReadTimeoutException
import io.reader.android.runtime.LibrarySort
import io.reader.android.runtime.LibrarySortDirection
import io.reader.android.runtime.ReaderBookmark
import io.reader.android.runtime.PendingBookmark
import io.reader.android.runtime.ReaderContentSearchSession
import io.reader.android.runtime.ReaderContentSearchHit
import io.reader.android.runtime.BookmarkTargetStaleException
import io.reader.android.runtime.ContentSearchPendingException
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ReaderRoute { Bookshelf, Settings, Directory, Reading, Management }
enum class ReaderAppTheme { System, Light, Dark }
enum class ReaderPaper { Paper, Warm, Night }
enum class ReaderPanel { Bookmarks, Search }

data class ReadingSession(
    val identity: Long,
    val book: LibraryBook,
    val chapter: ReaderChapter,
    val restoreScalarOffset: Int,
    val visibleScalarOffset: Int = restoreScalarOffset,
)

data class ReaderUiState(
    val route: ReaderRoute = ReaderRoute.Bookshelf,
    val books: List<LibraryBook> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val selectedBook: LibraryBook? = null,
    val chapters: List<ChapterItem> = emptyList(),
    val reading: ReadingSession? = null,
    val controlsVisible: Boolean = false,
    val fontSize: Int = 20,
    val theme: ReaderAppTheme = ReaderAppTheme.System,
    val paper: ReaderPaper = ReaderPaper.Paper,
    val keepScreenOn: Boolean = false,
    val pendingImports: List<PendingImport> = emptyList(),
    val pendingReady: Boolean = false,
    val importRevision: Long = 0,
    val lastImportedBookId: String? = null,
    val pendingPositions: List<PendingPosition> = emptyList(),
    val shelfQuery: String = "",
    val shelfSort: LibrarySort = LibrarySort.LAST_READ,
    val shelfDirection: LibrarySortDirection = LibrarySortDirection.DESCENDING,
    val readerPanel: ReaderPanel? = null,
    val bookmarks: List<ReaderBookmark> = emptyList(),
    val bookmarksReady: Boolean = false,
    val bookmarkError: String? = null,
    val pendingBookmarks: List<PendingBookmark> = emptyList(),
    val bookmarkDeletion: ReaderBookmark? = null,
    val bookmarkRevision: Long = 0,
    val lastCreatedBookmark: ReaderBookmark? = null,
    val searchQuery: String = "",
    val searchSession: ReaderContentSearchSession? = null,
    val searchResults: List<ReaderContentSearchHit> = emptyList(),
    val searchCursor: String? = null,
    val searchHasMore: Boolean = false,
    val searchBusy: Boolean = false,
    val searchReady: Boolean = false,
    val searchError: String? = null,
    val pendingSearchClose: ReaderContentSearchSession? = null,
    val protocolRecoveryReference: CoreProtocolReference? = null,
) {
    val pendingPosition: PendingPosition? get() = pendingPositions.firstOrNull { it.bookId == reading?.book?.id }
}

/** Platform display preferences only. Books, chapters and positions belong to Core. */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    // Does not access lazy Library/AppCoreOwner or send a Core request.
    private val protocolVmId = java.util.UUID.randomUUID().toString()
    private val protocolFeed = (application as ReaderApplication).coreProtocolRecovery
    internal val coreProtocolRecoveries = protocolFeed.view
    private val libraryDelegate = lazy { ReaderLibrary(application) }
    private val library by libraryDelegate
    private val preferences = application.getSharedPreferences("reader.display", 0)
    private val gate = Mutex()
    private var generation = 0L
    private var delayedSave: Job? = null
    private var shelfRead: Job? = null
    private var searchGeneration = 0L
    private var deletionOwner: Long? = null
    private var directoryReturn = ReaderRoute.Bookshelf
    private val mutableState = MutableStateFlow(
        ReaderUiState(
            fontSize = preferences.getInt("fontSize", 20).coerceIn(14, 32),
            theme = enumValue(preferences.getString("appTheme", null), ReaderAppTheme.System),
            paper = enumValue(preferences.getString("paper", null), ReaderPaper.Paper),
            keepScreenOn = preferences.getBoolean("keepScreenOn", false),
            shelfSort = enumValue(preferences.getString("shelfSort", null), LibrarySort.LAST_READ),
            shelfDirection = enumValue(preferences.getString("shelfDirection", null), LibrarySortDirection.DESCENDING),
        )
    )
    val state = mutableState.asStateFlow()

    private var managementBusyGeneration: Long? = null
    private val managementDelegate = lazy {
        ReaderShelfManagement(viewModelScope, { library }, { state.value.route == ReaderRoute.Management },
            onVmLane = { action -> gate.withLock {
                if (managementBusyGeneration != generation) return@withLock
                withContext(Dispatchers.IO) { library }; action()
            } },
            mainBusy = { value ->
                if (value) {
                    managementBusyGeneration = ++generation
                    shelfRead?.cancel(); delayedSave?.cancel()
                    mutableState.update { it.copy(busy = true) }
                } else {
                    val original = managementBusyGeneration
                    managementBusyGeneration = null
                    if (original == generation) mutableState.update { it.copy(busy = false) }
                }
            },
            retainProtocol = ::retainProtocolPending)
    }
    internal val shelfManagement get() = managementDelegate.value // Constructor/status observation never reads Library/Native.
    fun showShelfManagement() {
        if (state.value.busy || state.value.route != ReaderRoute.Bookshelf) return
        ++generation; shelfRead?.cancel(); delayedSave?.cancel()
        mutableState.update { it.copy(route = ReaderRoute.Management, error = null) }
        shelfManagement.open()
    }

    init {
        viewModelScope.launch {
            coreProtocolRecoveries.collect { view ->
                if (view.initialized && view.admissionOpen) mutableState.update { current ->
                    val original = current.protocolRecoveryReference
                    if (original != null && view.items.none { it.original == original })
                        current.copy(protocolRecoveryReference = null) else current
                }
            }
        }
        refreshBooks() // Existing startup action, not a status-query side effect.
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun Throwable.isRejectedStaleBookmarkCreate(): Boolean = this is CoreCommandException &&
        method == "bookmark.create" && error.optString("code") == "INVALID_PARAMS" &&
        error.optJSONObject("details")?.optString("reason") == "POSITION_CONTEXT_STALE" &&
        error.optJSONObject("details")?.optBoolean("originalPending") != true

    private fun Throwable.userMessage(): String {
        Log.w("ReaderUI", "Original operation failed", this)
        if (isRejectedStaleBookmarkCreate()) return "正文版本已变化，本次未添加书签。"
        return when (this) {
            is SecurityException -> "无法访问所选文件，请重新授权文件访问。"
            is FileNotFoundException -> "找不到所选文件，请确认原文件仍可读取。"
            is CoreReadTimeoutException -> "读取超时，请重试读取。"
            is BookmarkTargetStaleException -> "书签对应的正文版本已变化，请重新阅读并创建书签。"
            else -> "本次操作未能完成。"
        }
    }

    private fun perform(bookmarkOwner: Boolean = false, operation: suspend (Long) -> Unit) {
        val request = ++generation
        shelfRead?.cancel()
        delayedSave?.cancel()
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            gate.withLock {
                if (request != generation) return@withLock
                try {
                    withContext(Dispatchers.IO) { library }
                    operation(request)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (request == generation) {
                        val protocolPending = error.coreProtocolPending()
                        if (protocolPending != null) {
                            // Protocol facts never enter the import/bookmark business pending classifier.
                            mutableState.update { it.copy(protocolRecoveryReference = protocolPending.original, error = null) }
                            return@withLock // finally still retires this invocation's busy state.
                        }
                        if (error is PendingOperationException && error.stage == "reading.progress.update") {
                            retainPositionPending(error)
                        } else if (error is PendingOperationException && bookmarkOwner) {
                            val pending = try { library.pendingBookmarks() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) {
                                state.value.reading?.let { listOf(PendingBookmark(error.operationId, it.book.id, error.stage)) }
                                    ?: state.value.pendingBookmarks
                            }
                            mutableState.update { it.copy(pendingBookmarks = pending, bookmarkError = null) }
                        } else if (error is PendingOperationException) {
                            // Read the original journal; never redispatch import to discover its outcome.
                            try {
                                val pending = library.pendingImports()
                                mutableState.update { it.copy(pendingImports = pending, pendingReady = pending.isNotEmpty()) }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { mutableState.update { it.copy(pendingReady = false) } }
                        }
                        if (bookmarkOwner && error !is PendingOperationException) {
                            if (error.isRejectedStaleBookmarkCreate()) mutableState.update {
                                it.copy(bookmarkError = null, error = error.userMessage())
                            } else mutableState.update { it.copy(bookmarkError = error.userMessage()) }
                        } else if (!bookmarkOwner && (error !is PendingOperationException || error.stage != "reading.progress.update")) {
                            mutableState.update { it.copy(error = if (error is PendingOperationException)
                                "原导入尚未确认，请核对原操作。" else error.userMessage()) }
                        }
                    }
                } finally {
                    if (request == generation) mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun retainProtocolPending(error: Throwable): Boolean {
        val pending = error.coreProtocolPending() ?: return false
        mutableState.update { it.copy(protocolRecoveryReference = pending.original, error = null) }
        return true
    }

    private fun retainPositionPending(error: PendingOperationException) {
        val reading = state.value.reading ?: return
        val pending = PendingPosition(error.operationId, reading.book.id, error.stage)
        mutableState.update { it.copy(pendingPositions = it.pendingPositions.filterNot { old -> old.id == pending.id } + pending, error = null) }
        delayedSave?.cancel()
    }

    fun refreshBooks() = perform { request ->
        val pending = library.pendingImports()
        val positions = library.pendingPositions()
        val bookmarks = library.pendingBookmarks()
        val books = readShelf()
        if (request == generation) mutableState.update {
            it.copy(books = books, ready = true, pendingImports = pending, pendingReady = true,
                pendingPositions = positions, pendingBookmarks = bookmarks)
        }
    }

    private suspend fun readShelf(): List<LibraryBook> {
        val captured = state.value
        return library.books(captured.shelfQuery.ifBlank { null }, captured.shelfSort, captured.shelfDirection)
    }

    fun setShelfQuery(query: String) {
        if (state.value.busy || state.value.route != ReaderRoute.Bookshelf) return
        mutableState.update { it.copy(shelfQuery = query) }
        shelfRead?.cancel()
        shelfRead = viewModelScope.launch { delay(250); refreshBooks() }
    }

    fun setShelfOrder(sort: LibrarySort, direction: LibrarySortDirection = state.value.shelfDirection) {
        if (state.value.busy || state.value.route != ReaderRoute.Bookshelf) return
        shelfRead?.cancel()
        preferences.edit().putString("shelfSort", sort.name).putString("shelfDirection", direction.name).apply()
        mutableState.update { it.copy(shelfSort = sort, shelfDirection = direction) }
        refreshBooks()
    }

    fun importDocument(uri: Uri) {
        if (state.value.busy || !state.value.pendingReady || state.value.pendingImports.isNotEmpty()) return
        perform { request ->
            val imported = library.importDocument(uri)
            if (request == generation) mutableState.update {
                it.copy(importRevision = it.importRevision + 1, lastImportedBookId = imported.id)
            }
            // Import's successful receipt is independent of the following projection read.
            val books = readShelf()
            if (request == generation) mutableState.update {
                it.copy(books = books, ready = true, route = ReaderRoute.Bookshelf)
            }
        }
    }

    fun reconcilePendingImports() {
        if (state.value.busy) return
        perform { request ->
            library.reconcilePendingImports()
            val pending = library.pendingImports()
            val books = readShelf()
            if (request == generation) mutableState.update {
                it.copy(books = books, ready = true, pendingImports = pending, pendingReady = true)
            }
        }
    }

    fun reconcilePendingPosition(pending: PendingPosition) {
        if (state.value.busy || state.value.pendingPositions.none { it.id == pending.id && it.bookId == pending.bookId }) return
        perform { request ->
            var originalRejection: CoreCommandException? = null
            try { library.reconcilePendingPosition(pending.bookId, pending.id) }
            catch (rejected: CoreCommandException) { originalRejection = rejected }
            val positions = library.pendingPositions()
            val books = readShelf()
            if (request == generation) mutableState.update {
                it.copy(pendingPositions = positions, books = books, error = originalRejection?.userMessage())
            }
        }
    }

    fun showSettings() {
        if (!state.value.busy) {
            if (state.value.route == ReaderRoute.Management) shelfManagement.retirePresentation()
            mutableState.update { it.copy(route = ReaderRoute.Settings, error = null) }
        }
    }

    fun showBookshelf() {
        if (state.value.reading != null) { exitReading(); return }
        if (!state.value.busy) {
            val fromManagement = state.value.route == ReaderRoute.Management
            if (fromManagement) shelfManagement.retirePresentation()
            mutableState.update { it.copy(route = ReaderRoute.Bookshelf, error = null) }
            if (fromManagement) refreshBooks()
        }
    }

    fun showBookmarks() {
        if (state.value.busy || state.value.reading == null) return
        retireSearch()
        mutableState.update { it.copy(readerPanel = ReaderPanel.Bookmarks, controlsVisible = true, bookmarkError = null) }
        loadBookmarks()
    }

    fun loadBookmarks() {
        val reading = state.value.reading ?: return
        if (state.value.busy) return
        perform(bookmarkOwner = true) { request ->
            val bookmarks = library.bookmarks(reading.book.id)
            val pending = library.pendingBookmarks()
            if (request == generation && state.value.reading?.identity == reading.identity) mutableState.update {
                it.copy(bookmarks = bookmarks, bookmarksReady = true, bookmarkError = null, pendingBookmarks = pending)
            }
        }
    }

    fun createVisibleBookmark(owner: Long) {
        val reading = state.value.reading ?: return
        if (reading.identity != owner || state.value.readerPanel != ReaderPanel.Bookmarks || state.value.busy ||
            state.value.pendingBookmarks.isNotEmpty() || state.value.pendingPosition != null) return
        val offset = reading.visibleScalarOffset
        val start = io.reader.android.runtime.ReaderTextCoordinates.utf16Offset(reading.chapter.text, offset)
        val end = io.reader.android.runtime.ReaderTextCoordinates.utf16Offset(reading.chapter.text,
            (offset + 80).coerceAtMost(reading.chapter.scalarCount))
        perform(bookmarkOwner = true) { request ->
            val created = library.createBookmark(reading.book.id, reading.chapter.index, offset, reading.chapter.sessionToken,
                excerpt = reading.chapter.text.substring(start, end))
            if (request == generation && state.value.reading?.identity == reading.identity) mutableState.update {
                it.copy(bookmarks = listOf(created) + it.bookmarks.filterNot { old -> old.id == created.id },
                    bookmarkRevision = it.bookmarkRevision + 1, lastCreatedBookmark = created,
                    bookmarksReady = true, bookmarkError = null, error = "已添加书签")
            }
        }
    }

    fun requestDeleteBookmark(bookmark: ReaderBookmark, owner: Long) {
        val reading = state.value.reading ?: return
        if (reading.identity != owner || state.value.readerPanel != ReaderPanel.Bookmarks || state.value.busy ||
            bookmark.bookId != reading.book.id || state.value.pendingBookmarks.isNotEmpty()) return
        deletionOwner = reading.identity
        mutableState.update { it.copy(bookmarkDeletion = bookmark) }
    }

    fun dismissBookmarkDeletion(identity: String) {
        if (state.value.bookmarkDeletion?.id != identity) return
        deletionOwner = null
        mutableState.update { it.copy(bookmarkDeletion = null) }
    }

    fun confirmBookmarkDeletion(identity: String) {
        val bookmark = state.value.bookmarkDeletion ?: return
        val reading = state.value.reading ?: return
        if (bookmark.id != identity || deletionOwner != reading.identity || bookmark.bookId != reading.book.id ||
            state.value.busy || state.value.pendingBookmarks.isNotEmpty()) return
        dismissBookmarkDeletion(identity)
        perform(bookmarkOwner = true) { request ->
            val deletion = library.deleteBookmark(bookmark.bookId, bookmark.time, reading.chapter.sessionToken)
            if (request == generation && state.value.reading?.identity == reading.identity) mutableState.update {
                it.copy(bookmarks = if (deletion.deleted) it.bookmarks.filterNot { old -> old.time == deletion.time } else it.bookmarks,
                    bookmarkRevision = it.bookmarkRevision + 1, bookmarkError = null,
                    error = if (deletion.deleted) "已删除书签" else "书签未发生删除")
            }
            // A following projection failure does not erase the write's original receipt.
            val bookmarks = try { library.bookmarks(reading.book.id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == generation) mutableState.update { it.copy(bookmarkError = "删除已核对，书签列表暂时无法刷新。") }
                return@perform
            }
            if (request == generation && state.value.reading?.identity == reading.identity) mutableState.update {
                it.copy(bookmarks = bookmarks, bookmarksReady = true, bookmarkError = null)
            }
        }
    }

    fun reconcileBookmark(pending: PendingBookmark) {
        if (state.value.busy || state.value.pendingBookmarks.none { it.id == pending.id && it.bookId == pending.bookId }) return
        perform(bookmarkOwner = true) { request ->
            var rejected: CoreCommandException? = null
            val result = try { library.reconcilePendingBookmark(pending.bookId, pending.id) }
            catch (original: CoreCommandException) { rejected = original; null }
            val stillPending = library.pendingBookmarks()
            if (request == generation) mutableState.update {
                it.copy(pendingBookmarks = stillPending, bookmarkError = rejected?.userMessage(),
                    bookmarkRevision = if (result != null) it.bookmarkRevision + 1 else it.bookmarkRevision,
                    lastCreatedBookmark = result?.bookmark ?: it.lastCreatedBookmark)
            }
            if (state.value.reading?.book?.id == pending.bookId) {
                val bookmarks = try { library.bookmarks(pending.bookId) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (request == generation) mutableState.update { it.copy(bookmarkError = "原书签操作已核对，列表暂时无法刷新。") }
                    return@perform
                }
                if (request == generation) mutableState.update { it.copy(bookmarks = bookmarks, bookmarksReady = true) }
            }
        }
    }

    fun openBookmark(bookmark: ReaderBookmark, owner: Long) {
        val reading = state.value.reading ?: return
        if (reading.identity != owner || state.value.readerPanel != ReaderPanel.Bookmarks || state.value.busy ||
            state.value.pendingPosition != null || bookmark.bookId != reading.book.id || state.value.bookmarks.none { it == bookmark }) return
        perform(bookmarkOwner = true) { request ->
            persist(reading)
            val chapter = library.openBookmark(bookmark)
            if (request == generation && state.value.reading?.identity == reading.identity) mutableState.update {
                it.copy(reading = ReadingSession(request, reading.book, chapter, bookmark.scalarOffset),
                    readerPanel = null, controlsVisible = false, bookmarkError = null)
            }
        }
    }

    fun showContentSearch() {
        if (state.value.busy || state.value.reading == null) return
        mutableState.update { it.copy(readerPanel = ReaderPanel.Search, controlsVisible = true, bookmarkDeletion = null) }
    }

    fun setContentSearchQuery(query: String) {
        if (state.value.busy) return
        retireSearch()
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun searchContent() {
        val reading = state.value.reading ?: return
        val query = state.value.searchQuery.trim()
        if (query.isEmpty() || state.value.searchBusy || state.value.busy || state.value.pendingSearchClose != null) return
        retireSearch()
        val request = ++searchGeneration
        mutableState.update { it.copy(searchBusy = true, searchError = null) }
        viewModelScope.launch {
            try {
                val page = library.startContentSearch(reading.book.id, query, 20)
                if (request != searchGeneration || state.value.reading?.identity != reading.identity ||
                    state.value.readerPanel != ReaderPanel.Search) {
                    library.closeContentSearch(page.session)
                    return@launch
                }
                mutableState.update { it.copy(searchSession = page.session, searchResults = page.results,
                    searchCursor = page.nextCursor, searchHasMore = page.hasMore, searchReady = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (pending: ContentSearchPendingException) {
                retainProtocolPending(pending)
                mutableState.update { it.copy(pendingSearchClose = pending.session) }
            }
            catch (error: Exception) { if (request == searchGeneration && !retainProtocolPending(error)) mutableState.update {
                it.copy(searchError = error.userMessage(), searchHasMore = false, searchCursor = null, searchSession = null)
            } }
            finally { if (request == searchGeneration) mutableState.update { it.copy(searchBusy = false) } }
        }
    }

    fun nextContentSearch() {
        val captured = state.value
        val session = captured.searchSession ?: return
        val cursor = captured.searchCursor ?: return
        if (captured.searchBusy || !captured.searchHasMore) return
        val request = searchGeneration
        mutableState.update { it.copy(searchBusy = true, searchError = null) }
        viewModelScope.launch {
            try {
                val page = library.nextContentSearch(session, cursor)
                if (request == searchGeneration && state.value.searchSession?.id == session.id &&
                    state.value.readerPanel == ReaderPanel.Search) mutableState.update {
                    it.copy(searchResults = it.searchResults + page.results, searchCursor = page.nextCursor,
                        searchHasMore = page.hasMore, searchReady = true)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (pending: ContentSearchPendingException) {
                retainProtocolPending(pending)
                mutableState.update { it.copy(pendingSearchClose = pending.session) }
                if (request == searchGeneration) mutableState.update {
                    it.copy(searchSession = null, searchCursor = null, searchHasMore = false)
                }
            }
            catch (error: Exception) { if (request == searchGeneration && !retainProtocolPending(error)) mutableState.update {
                it.copy(searchError = error.userMessage(), searchHasMore = false, searchCursor = null, searchSession = null)
            } }
            finally { if (request == searchGeneration) mutableState.update { it.copy(searchBusy = false) } }
        }
    }

    fun openContentSearchHit(hit: ReaderContentSearchHit, owner: Long) {
        val captured = state.value
        val reading = captured.reading ?: return
        val session = captured.searchSession ?: return
        if (reading.identity != owner || captured.readerPanel != ReaderPanel.Search || captured.busy || captured.searchBusy || captured.pendingPosition != null ||
            hit.bookId != reading.book.id || captured.searchResults.none { it.id == hit.id }) return
        perform { request ->
            persist(reading)
            val chapter = library.openContentSearchHit(session, hit)
            if (request == generation && state.value.searchSession?.id == session.id) {
                retireSearch()
                mutableState.update { it.copy(reading = ReadingSession(request, reading.book, chapter, hit.chapterOffset),
                    readerPanel = null, controlsVisible = false) }
            }
        }
    }

    private fun retireSearch() {
        ++searchGeneration
        val session = state.value.searchSession
        mutableState.update { it.copy(searchSession = null, searchResults = emptyList(), searchCursor = null,
            searchHasMore = false, searchBusy = false, searchReady = false, searchError = null) }
        if (session != null) viewModelScope.launch {
            try {
                library.closeContentSearch(session)
                mutableState.update { if (it.pendingSearchClose?.id == session.id) it.copy(pendingSearchClose = null) else it }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (pending: ContentSearchPendingException) {
                retainProtocolPending(pending)
                mutableState.update { it.copy(pendingSearchClose = pending.session) }
            }
            catch (error: Exception) {
                retainProtocolPending(error)
                Log.w("ReaderUI", "Original search cursor close failed", error)
                mutableState.update { it.copy(pendingSearchClose = session) }
            }
        }
    }

    fun reconcileSearchClose() {
        val session = state.value.pendingSearchClose ?: return
        if (state.value.searchBusy) return
        mutableState.update { it.copy(searchBusy = true) }
        viewModelScope.launch {
            try {
                library.closeContentSearch(session)
                mutableState.update { if (it.pendingSearchClose?.id == session.id) it.copy(pendingSearchClose = null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (pending: ContentSearchPendingException) {
                retainProtocolPending(pending)
                mutableState.update { it.copy(pendingSearchClose = pending.session) }
            }
            catch (error: Exception) {
                retainProtocolPending(error)
                Log.w("ReaderUI", "Original search close remains unconfirmed", error)
            }
            finally { mutableState.update { it.copy(searchBusy = false) } }
        }
    }

    fun closeReaderPanel() {
        retireSearch()
        deletionOwner = null
        mutableState.update { it.copy(readerPanel = null, bookmarkDeletion = null, controlsVisible = true) }
    }

    fun openBook(book: LibraryBook) = openChapter(book, book.progressChapter, book.progressOffset)

    private fun openChapter(book: LibraryBook, index: Int, scalarOffset: Int) {
        if (state.value.busy || state.value.pendingPosition != null) return
        val previous = state.value.reading
        perform { request ->
            persist(previous)
            val chapter = library.chapter(book.id, index.coerceIn(0, (book.chapterCount - 1).coerceAtLeast(0)))
            retireSearch()
            if (request == generation) mutableState.update {
                it.copy(
                    route = ReaderRoute.Reading,
                    selectedBook = book,
                    reading = ReadingSession(request, book, chapter,
                        scalarOffset.coerceIn(0, chapter.text.codePointCount(0, chapter.text.length))),
                    controlsVisible = false,
                    readerPanel = null, bookmarks = emptyList(), bookmarksReady = false, bookmarkError = null,
                    bookmarkDeletion = null,
                )
            }
        }
    }

    fun showDirectory(book: LibraryBook? = state.value.selectedBook) {
        if (book == null) return
        if (state.value.busy || state.value.pendingPosition != null) return
        val previous = state.value.reading
        directoryReturn = if (previous != null) ReaderRoute.Reading else ReaderRoute.Bookshelf
        perform { request ->
            persist(previous)
            val chapters = library.toc(book.id)
            retireSearch()
            if (request == generation) mutableState.update {
                it.copy(route = ReaderRoute.Directory, selectedBook = book, chapters = chapters,
                    controlsVisible = false, readerPanel = null, bookmarkDeletion = null)
            }
        }
    }

    fun selectChapter(item: ChapterItem) {
        val book = state.value.selectedBook ?: return
        openChapter(book, item.index, 0)
    }

    fun changeChapter(delta: Int) {
        val reading = state.value.reading ?: return
        val target = reading.chapter.index + delta
        if (target in 0 until reading.book.chapterCount) openChapter(reading.book, target, 0)
    }

    fun visibleAnchor(session: Long, scalarOffset: Int) {
        val reading = state.value.reading ?: return
        if (state.value.route != ReaderRoute.Reading || state.value.busy ||
            state.value.pendingPosition != null || reading.identity != session) return
        val offset = scalarOffset.coerceIn(0, reading.chapter.text.codePointCount(0, reading.chapter.text.length))
        if (reading.visibleScalarOffset == offset) return
        mutableState.update { it.copy(reading = reading.copy(visibleScalarOffset = offset)) }
        delayedSave?.cancel()
        delayedSave = viewModelScope.launch {
            delay(450)
            gate.withLock {
                val current = state.value.reading
                if (current?.identity != session || state.value.busy || state.value.pendingPosition != null) return@withLock
                try { persist(current) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (state.value.reading?.identity == session) {
                        if (retainProtocolPending(error)) Unit
                        else if (error is PendingOperationException) retainPositionPending(error)
                        else mutableState.update { it.copy(error = "阅读位置尚未保存：${error.userMessage()}") }
                    }
                }
            }
        }
    }

    private suspend fun persist(reading: ReadingSession?) {
        if (reading != null) library.savePosition(reading.book.id, reading.chapter.index,
            reading.visibleScalarOffset, reading.chapter.sessionToken)
    }

    /** Back leaves only after the captured session's actual save receipt. */
    fun exitReading() {
        if (state.value.busy) return
        val reading = state.value.reading ?: return
        val pending = state.value.pendingPosition
        perform { request ->
            if (pending == null) persist(reading)
            val books = readShelf()
            retireSearch()
            if (request == generation) mutableState.update {
                it.copy(route = ReaderRoute.Bookshelf, books = books, reading = null,
                    selectedBook = null, controlsVisible = false, readerPanel = null, bookmarkDeletion = null)
            }
        }
    }

    fun back() {
        if (state.value.readerPanel != null) { closeReaderPanel(); return }
        if (state.value.busy) return
        when (state.value.route) {
            ReaderRoute.Reading -> if (state.value.controlsVisible) toggleControls() else exitReading()
            ReaderRoute.Directory -> mutableState.update { it.copy(route = directoryReturn, error = null) }
            ReaderRoute.Settings, ReaderRoute.Management -> showBookshelf()
            ReaderRoute.Bookshelf -> Unit
        }
    }

    fun onBackground() {
        if (state.value.pendingPosition != null) return
        val reading = state.value.reading ?: return
        delayedSave?.cancel()
        viewModelScope.launch {
            gate.withLock {
                if (state.value.reading?.identity != reading.identity || state.value.pendingPosition != null) return@withLock
                try { persist(reading) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (retainProtocolPending(error)) Unit
                    else if (error is PendingOperationException) retainPositionPending(error)
                    else mutableState.update { it.copy(error = "阅读位置尚未保存：${error.userMessage()}") }
                }
            }
        }
    }

    fun toggleControls() {
        if (state.value.readerPanel != null) closeReaderPanel()
        else mutableState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }
    fun recoverCoreProtocol(presentationId: String, expectedHost: String) {
        if (expectedHost != protocolHostIdentity()) return
        if (coreProtocolRecoveries.value.items.none { it.presentationId == presentationId }) return
        protocolFeed.requestAction(presentationId) // App-scope owner; no VM business/Core request.
    }
    private fun protocolHostIdentity(): String = "$protocolVmId:$generation:${state.value.route}"
    fun protocolRecoveryHost(): String = protocolHostIdentity()

    fun clearError() = mutableState.update { it.copy(error = null) }
    fun setFontSize(size: Int) {
        val value = size.coerceIn(14, 32)
        preferences.edit().putInt("fontSize", value).apply()
        mutableState.update { it.copy(fontSize = value) }
    }
    fun setTheme(theme: ReaderAppTheme) {
        preferences.edit().putString("appTheme", theme.name).apply()
        mutableState.update { it.copy(theme = theme) }
    }
    fun setPaper(paper: ReaderPaper) {
        preferences.edit().putString("paper", paper.name).apply()
        mutableState.update { it.copy(paper = paper) }
    }
    fun setKeepScreenOn(keep: Boolean) {
        preferences.edit().putBoolean("keepScreenOn", keep).apply()
        mutableState.update { it.copy(keepScreenOn = keep) }
    }

    override fun onCleared() {
        delayedSave?.cancel()
        shelfRead?.cancel()
        if (managementDelegate.isInitialized()) shelfManagement.close()
        if (libraryDelegate.isInitialized()) library.close()
        super.onCleared()
    }
}
