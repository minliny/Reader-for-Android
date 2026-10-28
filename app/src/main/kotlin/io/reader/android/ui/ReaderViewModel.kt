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
import io.reader.android.runtime.CoreCommandException
import io.reader.android.runtime.CoreReadTimeoutException
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ReaderRoute { Bookshelf, Settings, Directory, Reading }
enum class ReaderAppTheme { System, Light, Dark }
enum class ReaderPaper { Paper, Warm, Night }

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
) {
    val pendingPosition: PendingPosition? get() = pendingPositions.firstOrNull { it.bookId == reading?.book?.id }
}

/** Platform display preferences only. Books, chapters and positions belong to Core. */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryDelegate = lazy { ReaderLibrary(application) }
    private val library by libraryDelegate
    private val preferences = application.getSharedPreferences("reader.display", 0)
    private val gate = Mutex()
    private var generation = 0L
    private var delayedSave: Job? = null
    private var directoryReturn = ReaderRoute.Bookshelf
    private val mutableState = MutableStateFlow(
        ReaderUiState(
            fontSize = preferences.getInt("fontSize", 20).coerceIn(14, 32),
            theme = enumValue(preferences.getString("appTheme", null), ReaderAppTheme.System),
            paper = enumValue(preferences.getString("paper", null), ReaderPaper.Paper),
            keepScreenOn = preferences.getBoolean("keepScreenOn", false),
        )
    )
    val state = mutableState.asStateFlow()

    init { refreshBooks() }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun Throwable.userMessage(): String {
        Log.w("ReaderUI", "Original operation failed", this)
        return when (this) {
            is SecurityException -> "无法访问所选文件，请重新授权文件访问。"
            is FileNotFoundException -> "找不到所选文件，请确认原文件仍可读取。"
            is CoreReadTimeoutException -> "读取超时，请重试读取。"
            else -> "本次操作未能完成。"
        }
    }

    private fun perform(operation: suspend (Long) -> Unit) {
        val request = ++generation
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
                        if (error is PendingOperationException && error.stage == "reading.progress.update") {
                            retainPositionPending(error)
                        } else if (error is PendingOperationException) {
                            // Read the original journal; never redispatch import to discover its outcome.
                            try {
                                val pending = library.pendingImports()
                                mutableState.update { it.copy(pendingImports = pending, pendingReady = pending.isNotEmpty()) }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { mutableState.update { it.copy(pendingReady = false) } }
                        }
                        if (error !is PendingOperationException || error.stage != "reading.progress.update") {
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

    private fun retainPositionPending(error: PendingOperationException) {
        val reading = state.value.reading ?: return
        val pending = PendingPosition(error.operationId, reading.book.id, error.stage)
        mutableState.update { it.copy(pendingPositions = it.pendingPositions.filterNot { old -> old.id == pending.id } + pending, error = null) }
        delayedSave?.cancel()
    }

    fun refreshBooks() = perform { request ->
        val pending = library.pendingImports()
        val positions = library.pendingPositions()
        val books = library.books()
        if (request == generation) mutableState.update {
            it.copy(books = books, ready = true, pendingImports = pending, pendingReady = true, pendingPositions = positions)
        }
    }

    fun importDocument(uri: Uri) {
        if (state.value.busy || !state.value.pendingReady || state.value.pendingImports.isNotEmpty()) return
        perform { request ->
            val imported = library.importDocument(uri)
            if (request == generation) mutableState.update {
                it.copy(importRevision = it.importRevision + 1, lastImportedBookId = imported.id)
            }
            // Import's successful receipt is independent of the following projection read.
            val books = library.books()
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
            val books = library.books()
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
            val books = library.books()
            if (request == generation) mutableState.update {
                it.copy(pendingPositions = positions, books = books, error = originalRejection?.userMessage())
            }
        }
    }

    fun showSettings() {
        if (!state.value.busy) mutableState.update { it.copy(route = ReaderRoute.Settings, error = null) }
    }

    fun showBookshelf() {
        if (state.value.reading != null) { exitReading(); return }
        if (!state.value.busy) mutableState.update { it.copy(route = ReaderRoute.Bookshelf, error = null) }
    }

    fun openBook(book: LibraryBook) = openChapter(book, book.progressChapter, book.progressOffset)

    private fun openChapter(book: LibraryBook, index: Int, scalarOffset: Int) {
        if (state.value.busy || state.value.pendingPosition != null) return
        val previous = state.value.reading
        perform { request ->
            persist(previous)
            val chapter = library.chapter(book.id, index.coerceIn(0, (book.chapterCount - 1).coerceAtLeast(0)))
            if (request == generation) mutableState.update {
                it.copy(
                    route = ReaderRoute.Reading,
                    selectedBook = book,
                    reading = ReadingSession(request, book, chapter,
                        scalarOffset.coerceIn(0, chapter.text.codePointCount(0, chapter.text.length))),
                    controlsVisible = false,
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
            if (request == generation) mutableState.update {
                it.copy(route = ReaderRoute.Directory, selectedBook = book, chapters = chapters, controlsVisible = false)
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
        if (state.value.route != ReaderRoute.Reading || state.value.busy || state.value.controlsVisible ||
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
                        if (error is PendingOperationException) retainPositionPending(error)
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
            val books = library.books()
            if (request == generation) mutableState.update {
                it.copy(route = ReaderRoute.Bookshelf, books = books, reading = null,
                    selectedBook = null, controlsVisible = false)
            }
        }
    }

    fun back() {
        if (state.value.busy) return
        when (state.value.route) {
            ReaderRoute.Reading -> if (state.value.controlsVisible) toggleControls() else exitReading()
            ReaderRoute.Directory -> mutableState.update { it.copy(route = directoryReturn, error = null) }
            ReaderRoute.Settings -> showBookshelf()
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
                    if (error is PendingOperationException) retainPositionPending(error)
                    else mutableState.update { it.copy(error = "阅读位置尚未保存：${error.userMessage()}") }
                }
            }
        }
    }

    fun toggleControls() = mutableState.update { it.copy(controlsVisible = !it.controlsVisible) }
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
        if (libraryDelegate.isInitialized()) library.close()
        super.onCleared()
    }
}
