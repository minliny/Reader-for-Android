package io.reader.android.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.reader.android.runtime.ChapterItem
import io.reader.android.runtime.LibraryBook
import io.reader.android.runtime.ReaderRemoteShelfBook
import io.reader.android.runtime.ReaderChapter
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderLocalBookFormat
import io.reader.android.runtime.ReaderEbookViewLease
import io.reader.android.runtime.ReaderEbookViewPreparation
import io.reader.android.ui.ebookdraft.ReaderEbookReady
import io.reader.android.ui.ebookdraft.ReaderEbookVisibleAnchor
import io.reader.android.ui.ebookdraft.ReaderEbookRenderFailure
import io.reader.android.ui.ebookdraft.ReaderEbookBodyTap
import io.reader.android.ui.ebookdraft.ReaderEbookSelectionState
import io.reader.android.runtime.PendingImport
import io.reader.android.runtime.PendingPosition
import io.reader.android.runtime.ReaderNewPositionIntent
import io.reader.android.runtime.ReaderPositionSaveResult
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
import io.reader.android.runtime.ReaderContentSearchCloseObservation
import io.reader.android.runtime.ReaderSearchCloseScope
import io.reader.android.runtime.ReaderSearchCloseConfirmation
import io.reader.android.runtime.ReaderSearchClosePublicationPolicy
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

enum class ReaderRoute { Bookshelf, Settings, Directory, Reading, Management, Online }
enum class ReaderAppTheme { System, Light, Dark }
enum class ReaderPaper { Paper, Warm, Night }
enum class ReaderPanel { Bookmarks, Search }

data class ReadingSession(
    val identity: Long,
    val book: LibraryBook,
    val chapter: ReaderChapter,
    val restoreScalarOffset: Int,
    val format: ReaderLocalBookFormat,
    val visibleScalarOffset: Int = restoreScalarOffset,
    // This reading owner/chapter has actually passed the existing ebook Ready admission.
    // It survives reflow/view failure, and is never an original Core save receipt.
    val ebookPresented: Boolean = false,
    // Actual Library-issued object; UI cannot mint the backend capability.
    val newPositionIntent: ReaderNewPositionIntent? = null,
    // Mirror of a successful backend observation of this reading's actual Ready, not current layout readiness.
    val newPositionIntentPresented: Boolean = false,
) {
    val retainedPositionIds: List<String> get() {
        val intent = newPositionIntent ?: return emptyList()
        return if (newPositionIntentPresented && intent.sourceId == "local" && intent.bookId == book.id &&
            intent.chapterIndex == chapter.index && intent.sessionToken == chapter.sessionToken &&
            intent.bodyVersion == chapter.bodyVersion && intent.processingVersion == chapter.processingVersion)
            intent.retainedUnknownIds else emptyList()
    }
}

data class ReaderPositionSaveProjection(val readingOwner: Long, val sessionToken: String, val result: ReaderPositionSaveResult)

data class ReaderUiState(
    val route: ReaderRoute = ReaderRoute.Bookshelf,
    val books: List<LibraryBook> = emptyList(),
    val remoteBooks: List<ReaderRemoteShelfBook> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val selectedBook: LibraryBook? = null,
    val chapters: List<ChapterItem> = emptyList(),
    val reading: ReadingSession? = null,
    val controlsVisible: Boolean = false,
    val ebookFailureOwner: Long? = null,
    val fontSize: Int = 20,
    val theme: ReaderAppTheme = ReaderAppTheme.System,
    val paper: ReaderPaper = ReaderPaper.Paper,
    val keepScreenOn: Boolean = false,
    val pendingImports: List<PendingImport> = emptyList(),
    val pendingReady: Boolean = false,
    val importRevision: Long = 0,
    val lastImportedBookId: String? = null,
    val pendingPositions: List<PendingPosition> = emptyList(),
    val lastPositionSaveAttempt: ReaderPositionSaveProjection? = null,
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
    val searchCloseInFlight: ReaderSearchCloseScope? = null,
    val lastSearchCloseConfirmation: ReaderSearchCloseConfirmation? = null,
    val protocolRecoveryReference: CoreProtocolReference? = null,
) {
    // Original UNKNOWN facts stay visible/enumerable; this getter continues guarding unrelated navigation/tools.
    val pendingPosition: PendingPosition? get() = pendingPositions.firstOrNull { it.bookId == reading?.book?.id }
    val positionSaveBarrier: PendingPosition? get() = pendingPositions.firstOrNull {
        it.bookId == reading?.book?.id && it.id !in reading?.retainedPositionIds.orEmpty()
    }
    val historicalPosition: PendingPosition? get() = pendingPositions.firstOrNull {
        it.bookId == reading?.book?.id && it.id in reading?.retainedPositionIds.orEmpty()
    }
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
    private var ebookMount: ReaderEbookUiMount? = null
    private var shelfRead: Job? = null
    private var searchGeneration = 0L
    private var lastRetiredSearchScope: ReaderSearchCloseScope? = null
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

    private var onlineBusyGeneration: Long? = null
    private val onlineDelegate = lazy {
        ReaderOnlineWorkspace(viewModelScope, { library }, { state.value.route == ReaderRoute.Online },
            onVmLane = { action -> gate.withLock {
                if (onlineBusyGeneration != generation) return@withLock
                withContext(Dispatchers.IO) { library }; action()
            } },
            cleanupLane = { action -> gate.withLock { if (libraryDelegate.isInitialized()) action() } },
            mainBusy = { value ->
                if (value) {
                    onlineBusyGeneration = ++generation; shelfRead?.cancel(); delayedSave?.cancel()
                    mutableState.update { it.copy(busy = true) }
                } else {
                    val original = onlineBusyGeneration; onlineBusyGeneration = null
                    if (original == generation) mutableState.update { it.copy(busy = false) }
                }
            }, retainProtocol = ::retainProtocolPending, reloadShelf = {
                val captured = state.value
                val books = readShelf()
                val remote = library.remoteShelfMetadata(captured.shelfQuery.ifBlank { null }, captured.shelfSort, captured.shelfDirection)
                mutableState.update { it.copy(books = books, remoteBooks = remote, ready = true) }
            })
    }
    internal val onlineSources get() = onlineDelegate.value
    fun showOnlineSources() {
        if (state.value.busy || state.value.route != ReaderRoute.Bookshelf || !state.value.ready) return
        ++generation; shelfRead?.cancel(); delayedSave?.cancel()
        mutableState.update { it.copy(route = ReaderRoute.Online, error = null) }
        onlineSources.open()
    }

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

    private fun retainPositionPending(error: PendingOperationException, originalReading: ReadingSession? = state.value.reading) {
        val reading = originalReading ?: return
        val pending = PendingPosition(error.operationId, reading.book.id, error.stage)
        mutableState.update { current -> current.copy(
            pendingPositions = current.pendingPositions.filterNot { old -> old.id == pending.id } + pending,
            error = if (current.reading?.identity == reading.identity) null else current.error) }
        if (state.value.reading?.identity == reading.identity) delayedSave?.cancel()
    }

    fun refreshBooks() = perform { request ->
        val pending = library.pendingImports()
        val positions = library.pendingPositions()
        val bookmarks = library.pendingBookmarks()
        val books = readShelf()
        val captured = state.value
        val remote = library.remoteShelfMetadata(captured.shelfQuery.ifBlank { null }, captured.shelfSort, captured.shelfDirection)
        if (request == generation) mutableState.update {
            it.copy(books = books, remoteBooks = remote, ready = true, pendingImports = pending, pendingReady = true,
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

    fun reconcilePendingPosition(pending: PendingPosition, expectedRoute: ReaderRoute, expectedReadingOwner: Long?) {
        if (state.value.busy || state.value.route != expectedRoute || state.value.reading?.identity != expectedReadingOwner ||
            state.value.pendingPositions.none { it.id == pending.id && it.bookId == pending.bookId }) return
        perform { request ->
            if (state.value.route != expectedRoute || state.value.reading?.identity != expectedReadingOwner ||
                state.value.pendingPositions.none { it.id == pending.id && it.bookId == pending.bookId }) return@perform
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

    /** Explicit user intention; the owner, never this UI, qualifies the exact retained originals. */
    fun beginNewPositionIntent(owner: Long, chapterToken: String, retainedIds: List<String>) {
        val reading = state.value.reading ?: return
        if (state.value.busy || state.value.route != ReaderRoute.Reading || reading.identity != owner ||
            reading.chapter.sessionToken != chapterToken || !reading.ebookPresented ||
            reading.newPositionIntent != null || retainedIds.isEmpty() || retainedIds.distinct().size != retainedIds.size ||
            state.value.pendingPositions.filter { it.bookId == reading.book.id }.map { it.id }.toSet() != retainedIds.toSet()) return
        perform { request ->
            val current = state.value.reading
            if (current == null || !sameReadingScope(current, reading) || current.newPositionIntent != null ||
                state.value.route != ReaderRoute.Reading ||
                state.value.pendingPositions.filter { it.bookId == reading.book.id }.map { it.id }.toSet() != retainedIds.toSet()) return@perform
            val intent = library.beginNewPositionIntent(reading.book.id, reading.chapter.index, chapterToken, retainedIds.toList())
            check(intent.sourceId == "local" && intent.bookId == reading.book.id && intent.chapterIndex == reading.chapter.index &&
                intent.sessionToken == chapterToken && intent.bodyVersion == reading.chapter.bodyVersion &&
                intent.processingVersion == reading.chapter.processingVersion && intent.retainedUnknownIds.toSet() == retainedIds.toSet())
            if (request != generation) return@perform
            mutableState.update { state ->
                val active = state.reading
                if (active == null || state.route != ReaderRoute.Reading || !sameReadingScope(active, reading)) state
                else state.copy(reading = active.copy(newPositionIntent = intent, newPositionIntentPresented = false), lastPositionSaveAttempt = null)
            }
            // Existing actual Ready may be submitted once; never manufacture a JS/Native probe.
            val mount = ebookMount
            val ready = mount?.ready
            if (mount != null && ready != null && currentEbookMount(mount.view) === mount)
                observeNewPositionReady(mount, ready)
        }
    }

    private fun sameReadingScope(first: ReadingSession, second: ReadingSession): Boolean =
        first.identity == second.identity && first.book.id == second.book.id && first.format == second.format &&
            first.chapter.bookId == second.chapter.bookId && first.chapter.index == second.chapter.index &&
            first.chapter.sessionToken == second.chapter.sessionToken && first.chapter.bodyVersion == second.chapter.bodyVersion &&
            first.chapter.processingVersion == second.chapter.processingVersion

    fun showSettings() {
        if (!state.value.busy) {
            if (state.value.route == ReaderRoute.Management) shelfManagement.retirePresentation()
            if (state.value.route == ReaderRoute.Online) onlineSources.leave()
            mutableState.update { it.copy(route = ReaderRoute.Settings, error = null) }
        }
    }

    fun showBookshelf() {
        if (state.value.reading != null) { exitReading(); return }
        if (!state.value.busy) {
            val fromOnline = state.value.route == ReaderRoute.Online
            if (fromOnline) onlineSources.leave()
            val fromManagement = state.value.route == ReaderRoute.Management
            if (fromManagement) shelfManagement.retirePresentation()
            mutableState.update { it.copy(route = ReaderRoute.Bookshelf, error = null) }
            if (fromManagement || fromOnline) refreshBooks()
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
                it.copy(reading = ReadingSession(request, reading.book, chapter, bookmark.scalarOffset, reading.format),
                    readerPanel = null, controlsVisible = false, bookmarkError = null, lastPositionSaveAttempt = null)
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
                mutableState.update { it.copy(reading = ReadingSession(request, reading.book, chapter, hit.chapterOffset, reading.format),
                    readerPanel = null, controlsVisible = false, lastPositionSaveAttempt = null) }
            }
        }
    }

    private fun publishSearchClose(scope: ReaderSearchCloseScope?, observation: ReaderContentSearchCloseObservation?) {
        if (scope == null) return
        mutableState.update { current ->
            if (current.searchCloseInFlight != scope) current else {
                val confirmation = ReaderSearchClosePublicationPolicy.confirmed(scope, current.reading?.identity,
                    searchGeneration, current.searchCloseInFlight, observation)
                current.copy(searchCloseInFlight = null, lastSearchCloseConfirmation = confirmation)
            }
        }
    }

    private fun clearSearchCloseInFlight(scope: ReaderSearchCloseScope?) {
        if (scope != null) mutableState.update { if (it.searchCloseInFlight == scope) it.copy(searchCloseInFlight = null) else it }
    }

    private fun retireSearch() {
        val request = ++searchGeneration
        val captured = state.value
        val session = captured.searchSession
        val scope = session?.let { original -> captured.reading?.identity?.let { ReaderSearchCloseScope(it, request, original) } }
        if (session != null) lastRetiredSearchScope = scope
        mutableState.update { it.copy(searchSession = null, searchResults = emptyList(), searchCursor = null,
            searchHasMore = false, searchBusy = false, searchReady = false, searchError = null,
            searchCloseInFlight = scope, lastSearchCloseConfirmation = null) }
        if (session != null) viewModelScope.launch {
            try {
                val observation = library.closeContentSearch(session)
                publishSearchClose(scope, observation)
                mutableState.update { if (it.pendingSearchClose == session) it.copy(pendingSearchClose = null) else it }
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
            finally { clearSearchCloseInFlight(scope) }
        }
    }

    fun reconcileSearchClose(session: ReaderContentSearchSession, expectedRoute: ReaderRoute, expectedReadingOwner: Long?) {
        val current = state.value
        if (current.pendingSearchClose != session || current.route != expectedRoute ||
            current.reading?.identity != expectedReadingOwner || current.searchBusy) return
        val generation = searchGeneration
        val scope = lastRetiredSearchScope?.takeIf { it.session == session && it.searchGeneration == generation &&
            it.readingOwner == state.value.reading?.identity }
        mutableState.update { if (scope == null) it.copy(searchBusy = true) else
            it.copy(searchBusy = true, searchCloseInFlight = scope, lastSearchCloseConfirmation = null) }
        viewModelScope.launch {
            try {
                val observation = library.closeContentSearch(session)
                publishSearchClose(scope, observation)
                mutableState.update { if (it.pendingSearchClose == session) it.copy(pendingSearchClose = null) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (pending: ContentSearchPendingException) {
                retainProtocolPending(pending)
                mutableState.update { it.copy(pendingSearchClose = pending.session) }
            }
            catch (error: Exception) {
                retainProtocolPending(error)
                Log.w("ReaderUI", "Original search close remains unconfirmed", error)
            }
            finally {
                clearSearchCloseInFlight(scope)
                if (generation == searchGeneration) mutableState.update { it.copy(searchBusy = false) }
            }
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
            val format = library.localBookFormat(book.id) // Retained settled Core format; no extension/text fallback.
            val chapter = library.chapter(book.id, index.coerceIn(0, (book.chapterCount - 1).coerceAtLeast(0)))
            retireSearch()
            if (request == generation) mutableState.update {
                it.copy(
                    route = ReaderRoute.Reading,
                    selectedBook = book,
                    reading = ReadingSession(request, book, chapter,
                        scalarOffset.coerceIn(0, chapter.text.codePointCount(0, chapter.text.length)), format),
                    controlsVisible = false,
                    readerPanel = null, bookmarks = emptyList(), bookmarksReady = false, bookmarkError = null,
                    lastPositionSaveAttempt = null,
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

    private fun ebookReading(owner: Long, chapterToken: String): ReadingSession? = state.value.reading?.takeIf {
        state.value.route == ReaderRoute.Reading && it.identity == owner && it.chapter.sessionToken == chapterToken &&
            it.format in setOf(ReaderLocalBookFormat.EPUB, ReaderLocalBookFormat.MOBI, ReaderLocalBookFormat.AZW)
    }

    private fun currentEbookMount(view: ReaderEbookViewLease): ReaderEbookUiMount? {
        val mount = ebookMount?.takeIf { it.view === view } ?: return null
        val reading = ebookReading(mount.readingOwner, mount.chapter.sessionToken) ?: return null
        return mount.takeIf { reading.chapter.bookId == it.chapter.bookId && reading.chapter.index == it.chapter.index &&
            reading.chapter.bodyVersion == it.chapter.bodyVersion && reading.chapter.processingVersion == it.chapter.processingVersion }
    }

    internal fun attachEbookView(owner: Long, chapterToken: String): ReaderEbookViewLease {
        val reading = checkNotNull(ebookReading(owner, chapterToken)) { "RETIRED_EBOOK_READING_OWNER" }
        ebookMount?.view?.close()
        ebookMount = null
        mutableState.update { if (it.ebookFailureOwner == owner) it.copy(ebookFailureOwner = null) else it }
        return library.newEbookView().also { ebookMount = ReaderEbookUiMount(owner, reading.chapter, it) }
    }

    internal suspend fun prepareEbookView(owner: Long, chapterToken: String, view: ReaderEbookViewLease): ReaderEbookViewPreparation =
        gate.withLock {
            val mount = checkNotNull(currentEbookMount(view)) { "RETIRED_EBOOK_VIEW" }
            check(mount.readingOwner == owner && mount.chapter.sessionToken == chapterToken)
            val prepared = library.prepareEbookForView(view, mount.chapter.bookId, mount.chapter.index, chapterToken)
            try {
                prepared.requireCurrent()
                check(currentEbookMount(view) === mount && prepared.view === view &&
                    prepared.publication.lease.renderId == view.physicalViewId) { "RETIRED_EBOOK_PREPARATION" }
                mount.preparation = prepared
                prepared // Leave this gate before mounting, resource interception or any draw callback.
            } catch (error: Throwable) { prepared.close(); throw error }
        }

    internal fun releaseEbookView(view: ReaderEbookViewLease) {
        view.close()
        if (ebookMount?.view === view) ebookMount = null
    }

    private fun readyEbookMount(view: ReaderEbookViewLease, lease: io.reader.android.runtime.ebookdraft.EbookRenderLease,
        epoch: Long, token: String): ReaderEbookUiMount? {
        val mount = currentEbookMount(view) ?: return null
        val prepared = mount.preparation ?: return null
        if (prepared.publication.lease != lease || lease.renderId != view.physicalViewId) return null
        try { prepared.requireCurrent() } catch (_: IllegalStateException) { return null }
        return mount.takeIf { it.ready?.let { ready -> ready.layoutEpoch == epoch && ready.layoutToken == token } == true }
    }

    internal fun ebookReady(view: ReaderEbookViewLease, event: ReaderEbookReady) {
        val mount = currentEbookMount(view) ?: return
        val prepared = mount.preparation ?: return
        if (prepared.publication.lease != event.lease || event.lease.renderId != view.physicalViewId ||
            event.layoutEpoch <= 0 || event.layoutToken.isEmpty() || event.scalar != state.value.reading?.visibleScalarOffset) return
        try { prepared.requireCurrent() } catch (_: IllegalStateException) { return }
        val prior = mount.ready
        if (prior != null && (event.layoutEpoch < prior.layoutEpoch ||
            (event.layoutEpoch == prior.layoutEpoch && event.layoutToken != prior.layoutToken))) return
        mount.ready = event // Admission only; a restore/reflow never becomes a new business reading position.
        mutableState.update { current ->
            val reading = current.reading
            if (current.route != ReaderRoute.Reading || reading == null ||
                reading.identity != mount.readingOwner || reading.chapter.sessionToken != mount.chapter.sessionToken ||
                reading.book.id != event.lease.bookId || reading.chapter.bookId != event.lease.bookId ||
                reading.chapter.index != event.lease.chapterIndex || reading.chapter.bodyVersion != event.lease.bodyVersion ||
                reading.chapter.processingVersion != event.lease.processingVersion || reading.visibleScalarOffset != event.scalar) current
            else current.copy(reading = reading.copy(ebookPresented = true))
        }
        observeNewPositionReady(mount, event)
    }

    private fun observeNewPositionReady(mount: ReaderEbookUiMount, event: ReaderEbookReady) {
        val reading = state.value.reading ?: return
        val intent = reading.newPositionIntent ?: return
        if (currentEbookMount(mount.view) !== mount || mount.ready != event || !reading.ebookPresented ||
            reading.identity != mount.readingOwner || reading.chapter.sessionToken != intent.sessionToken) return
        if (!library.observeNewPositionReady(intent, mount.view, event.lease, event.layoutEpoch, event.layoutToken, event.scalar)) return
        mutableState.update { state ->
            val current = state.reading
            if (current == null || !sameReadingScope(current, reading) || current.newPositionIntent !== intent) state
            else state.copy(reading = current.copy(newPositionIntentPresented = true))
        }
    }

    /** True means this exact visible observation was admitted, never that Core saved it. */
    internal fun ebookVisibleAnchor(view: ReaderEbookViewLease, event: ReaderEbookVisibleAnchor): Boolean {
        val mount = readyEbookMount(view, event.lease, event.layoutEpoch, event.layoutToken) ?: return false
        if (event.scalar !in 0..mount.chapter.scalarCount) return false
        return acceptVisibleAnchor(mount.readingOwner, event.scalar) {
            val intent = state.value.reading?.newPositionIntent
            intent == null || library.observeNewPositionAnchor(intent, view, event.lease, event.layoutEpoch, event.layoutToken, event.scalar)
        }
    }

    internal fun ebookBodyTap(view: ReaderEbookViewLease, event: ReaderEbookBodyTap) {
        readyEbookMount(view, event.lease, event.layoutEpoch, event.layoutToken) ?: return
        if (!state.value.busy) toggleControls()
    }

    internal fun ebookSelectionChanged(view: ReaderEbookViewLease, event: ReaderEbookSelectionState) {
        val mount = readyEbookMount(view, event.lease, event.layoutEpoch, event.layoutToken) ?: return
        mount.selectionActive = event.active // Observation only; the trusted browser owner gates selection/tap/anchor.
    }

    internal fun ebookPreparationFailed(owner: Long, chapterToken: String, view: ReaderEbookViewLease?, error: Exception) {
        if (ebookReading(owner, chapterToken) == null || (view != null && currentEbookMount(view) == null)) return
        Log.w("ReaderEbook", "stage=prepare renderId=${view?.physicalViewId} epoch=0", error)
        // The existing top exit remains available above the failed PageState; this is not a retry or navigation action.
        mutableState.update { it.copy(ebookFailureOwner = owner) }
        view?.close()
        retainProtocolPending(error)
    }

    internal fun ebookRenderFailed(view: ReaderEbookViewLease, event: ReaderEbookRenderFailure) {
        val mount = currentEbookMount(view) ?: return
        if (mount.preparation?.publication?.lease != event.lease || event.lease.renderId != view.physicalViewId) return
        val ready = mount.ready
        if (ready != null && (event.layoutEpoch < ready.layoutEpoch ||
            (event.layoutEpoch == ready.layoutEpoch && event.layoutToken != ready.layoutToken))) return
        mount.ready = null
        mutableState.update { it.copy(ebookFailureOwner = mount.readingOwner) }
        view.close()
        retainProtocolPending(event.cause) // Do not misclassify a resource/protocol failure as an import pending operation.
    }

    fun visibleAnchor(session: Long, scalarOffset: Int) {
        if (state.value.reading?.format != ReaderLocalBookFormat.TXT) return
        acceptVisibleAnchor(session, scalarOffset) // Ebook positions use only their full view/lease/epoch path above.
    }

    private fun acceptVisibleAnchor(session: Long, scalarOffset: Int, admitBeforePublish: () -> Boolean = { true }): Boolean {
        val reading = state.value.reading ?: return false
        if (state.value.route != ReaderRoute.Reading || state.value.busy ||
            state.value.positionSaveBarrier != null || reading.identity != session) return false
        val offset = scalarOffset.coerceIn(0, reading.chapter.text.codePointCount(0, reading.chapter.text.length))
        if (reading.visibleScalarOffset == offset) return true
        if (!admitBeforePublish()) return false
        mutableState.update { it.copy(reading = reading.copy(visibleScalarOffset = offset)) }
        delayedSave?.cancel()
        delayedSave = viewModelScope.launch {
            delay(450)
            gate.withLock {
                val current = state.value.reading
                if (current?.identity != session || state.value.busy || state.value.positionSaveBarrier != null) return@withLock
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
        return true
    }

    private suspend fun persist(reading: ReadingSession?): ReaderPositionSaveResult? {
        if (reading == null) return null
        if (reading.format != ReaderLocalBookFormat.TXT) {
            val current = state.value.reading ?: return null
            if (!current.ebookPresented || !sameReadingScope(current, reading)) return null
        }
        val result = library.savePosition(reading.book.id, reading.chapter.index,
            reading.visibleScalarOffset, reading.chapter.sessionToken)
        when (result) {
            is ReaderPositionSaveResult.SkippedUnchanged -> check(result.bookId == reading.book.id &&
                result.chapterIndex == reading.chapter.index && result.scalarOffset == reading.visibleScalarOffset &&
                reading.newPositionIntent != null && result.scalarOffset == reading.newPositionIntent.baselineScalarOffset &&
                result.retainedUnknownIds.toSet() == reading.newPositionIntent.retainedUnknownIds.toSet())
            is ReaderPositionSaveResult.ConfirmedNewOriginal -> check(result.bookId == reading.book.id &&
                result.chapterIndex == reading.chapter.index && result.scalarOffset == reading.visibleScalarOffset &&
                result.bodyVersion == reading.chapter.bodyVersion && result.processingVersion == reading.chapter.processingVersion &&
                result.originalId !in reading.newPositionIntent?.retainedUnknownIds.orEmpty() && result.requestId > 0 &&
                result.progressRevision.isNotBlank() &&
                (reading.newPositionIntent == null || result.runtimeSession == reading.newPositionIntent.runtimeSession))
        }
        mutableState.update { state ->
            val current = state.reading
            if (current == null || !sameReadingScope(current, reading) || current.visibleScalarOffset != reading.visibleScalarOffset) state
            else state.copy(lastPositionSaveAttempt = ReaderPositionSaveProjection(reading.identity, reading.chapter.sessionToken, result))
        }
        // Skipped is only a no-dispatch observation. No Saved message, ACK counter or pending removal follows it.
        return result
    }

    /** New-save confirmation remains typed; unchanged/blocked exit never fabricates a receipt. */
    fun exitReading() {
        if (state.value.busy) return
        val reading = state.value.reading ?: return
        val pending = state.value.positionSaveBarrier
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
            ReaderRoute.Online -> if (!onlineSources.back()) showBookshelf()
            ReaderRoute.Settings, ReaderRoute.Management -> showBookshelf()
            ReaderRoute.Bookshelf -> Unit
        }
    }

    fun onBackground() {
        if (state.value.positionSaveBarrier != null) return
        val reading = state.value.reading ?: return
        delayedSave?.cancel()
        viewModelScope.launch {
            gate.withLock {
                if (state.value.reading?.identity != reading.identity || state.value.positionSaveBarrier != null) return@withLock
                try { persist(reading) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    if (retainProtocolPending(error)) Unit
                    else if (error is PendingOperationException) retainPositionPending(error, reading)
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
        ebookMount?.view?.close(); ebookMount = null
        delayedSave?.cancel()
        shelfRead?.cancel()
        if (onlineDelegate.isInitialized()) onlineSources.close()
        if (managementDelegate.isInitialized()) shelfManagement.close()
        if (libraryDelegate.isInitialized()) library.close()
        super.onCleared()
    }
}
