package com.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.api.Book
import com.reader.api.ReaderCoreClient
import com.reader.api.SearchBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Bookshelf data source.
 *
 * P2 rollback: Now backed by Core bridge `bookshelf.list` (DomainState owned by
 * Reader-Core-Native per CONTRACT_FIRST_NATIVE_UI_PLAN §2). The previous
 * `BookshelfRepository`/`FakeBookshelfRepository` self-made layer was removed —
 * bookshelf data is DomainState, must come from Core, not from a platform-side
 * repository.
 *
 * Cover/list press/focus state lives in [chromeState] (`viewMode`, `focusedBook`) and is
 * observable via [chromeState] StateFlow for reducer/test verification. These are
 * EphemeralState (PLAN §2, Native UI owned).
 */
class BookshelfViewModel : ViewModel() {
    private val core = ReaderCoreClient.get()

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _continueReading = MutableStateFlow<Book?>(null)
    val continueReading: StateFlow<Book?> = _continueReading.asStateFlow()

    // P0-2: real inShelf query source. Derived from the bookshelf.list response so
    // search/discover/detail screens can check membership without a platform-side table
    // (bookshelf is Core-owned DomainState per CORE_HOST_BOUNDARY.md §1.1).
    private val _shelfBookUrls = MutableStateFlow<Set<String>>(emptySet())
    val shelfBookUrls: StateFlow<Set<String>> = _shelfBookUrls.asStateFlow()

    private val _chromeState = MutableStateFlow(BookshelfChromeState())
    val chromeState: StateFlow<BookshelfChromeState> = _chromeState.asStateFlow()

    init { loadBooks() }

    fun setViewMode(mode: BookshelfViewMode) {
        _chromeState.update { it.copy(viewMode = mode) }
    }

    fun setFocusedBook(book: Book?) {
        _chromeState.update { it.copy(focusedBook = book) }
    }

    fun toggleFilter() {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(isOpen = !state.filter.isOpen))
        }
    }

    fun setFilterGroup(group: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(group = group))
        }
    }

    fun setFilterSort(sort: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(sort = sort))
        }
    }

    fun setFilterValue(filter: String) {
        _chromeState.update { state ->
            state.copy(filter = state.filter.copy(filter = filter))
        }
    }

    /**
     * Load bookshelf from Core `bookshelf.list`. Core owns DomainState; Android only
     * renders what Core returns. Empty result renders `UiState.Empty`.
     */
    fun loadBooks() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val result = core.sendAndAwait("bookshelf.list", JSONObject())
                val books = parseBookshelfList(result)
                _uiState.value = if (books.isEmpty()) UiState.Empty else UiState.Success(books)
                // Continue reading = first book on the shelf (Core owns ordering).
                _continueReading.value = books.firstOrNull()
                _shelfBookUrls.value = books.map { it.bookUrl }.toSet()
            } catch (e: Exception) {
                // Core not ready or command failed — show empty, not a fake fixture.
                // P2 acceptance: bookshelf data comes from Core; no platform-side fallback.
                _uiState.value = UiState.Empty
                _continueReading.value = null
                _shelfBookUrls.value = emptySet()
            }
        }
    }

    /**
     * P0-2: Add a book to the shelf via Core `bookshelf.add` CoreCommand.
     * Core owns the bookshelf DomainState; Android only fires the command and refreshes.
     * The book object uses the same field names as `bookshelf.list` response (`bookId`,
     * `title`, `author`, `coverUrl`, `intro`, `origin`).
     */
    fun addToBookshelf(book: SearchBook) {
        viewModelScope.launch {
            try {
                core.sendAndAwait("bookshelf.add", BookshelfWriteParams.buildAddParams(book))
                loadBooks()
            } catch (e: Exception) {
                // Core not ready or command failed — shelf stays as-is; no fake fallback.
            }
        }
    }

    /**
     * P0-2: Remove a book from the shelf via Core `bookshelf.remove` CoreCommand.
     */
    fun removeFromBookshelf(bookUrl: String) {
        viewModelScope.launch {
            try {
                core.sendAndAwait("bookshelf.remove", BookshelfWriteParams.buildRemoveParams(bookUrl))
                loadBooks()
            } catch (e: Exception) {
                // Core not ready or command failed — shelf stays as-is.
            }
        }
    }

    private fun parseBookshelfList(data: JSONObject): List<Book> {
        val arr = data.optJSONArray("books") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            Book(
                bookUrl = b.optString("bookId"),
                name = b.optString("title"),
                author = b.optString("author"),
                coverUrl = b.optString("coverUrl"),
                intro = b.optString("intro"),
                origin = b.optString("origin")
            )
        }
    }
}

sealed class UiState {
    object Loading : UiState()
    object Empty : UiState()
    data class Success(val books: List<Book>) : UiState()
    data class Error(val message: String) : UiState()
}

enum class BookshelfViewMode { COVER, LIST }

data class BookshelfChromeState(
    val viewMode: BookshelfViewMode = BookshelfViewMode.COVER,
    val focusedBook: Book? = null,
    val filter: BookshelfFilterState = BookshelfFilterState()
)

data class BookshelfFilterState(
    val group: String = "全部",
    val sort: String = "最近更新",
    val filter: String = "全部",
    val isOpen: Boolean = false
) {
    val isActive: Boolean
        get() = isOpen || group != "全部" || sort != "最近更新" || filter != "全部"
}

/**
 * P0-2: Pure JSON params builders for `bookshelf.add` / `bookshelf.remove`
 * CoreCommands. Extracted as internal object so JVM tests can verify the Core command
 * contract (field names + structure) without a running Core runtime.
 *
 * Field names mirror the `bookshelf.list` response (`bookId`, `title`, `author`,
 * `coverUrl`, `intro`, `origin`) — Core owns the bookshelf DomainState schema.
 */
internal object BookshelfWriteParams {
    fun buildAddParams(book: SearchBook): JSONObject = JSONObject().apply {
        put("book", JSONObject().apply {
            put("bookId", book.bookUrl)
            put("title", book.name)
            put("author", book.author)
            put("coverUrl", book.coverUrl)
            put("intro", book.intro)
            put("origin", book.origin)
        })
    }

    fun buildRemoveParams(bookUrl: String): JSONObject = JSONObject().apply {
        put("bookId", bookUrl)
    }
}
