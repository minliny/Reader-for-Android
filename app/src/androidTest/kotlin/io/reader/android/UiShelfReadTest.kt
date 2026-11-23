package io.reader.android

import android.os.Bundle
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.LibraryBook
import io.reader.android.runtime.LibrarySort
import io.reader.android.runtime.LibrarySortDirection
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Source-only Runner candidate. Reads the existing shelf; never opens or writes a book. */
@RunWith(AndroidJUnit4::class)
class UiShelfReadTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val model get() = compose.activity.readerViewModel
    private var deadline = 0L
    @get:Rule val rules: TestRule = RuleChain.outerRule(Timeout.builder().withTimeout(60, TimeUnit.SECONDS).build()).around(compose)

    @Test fun existingThreeRealBooksSearchAndTitleSortPreservesData() {
        deadline = SystemClock.elapsedRealtime() + 50_000
        marker("method-enter")
        var original: ReaderUiState? = null
        var primary: Throwable? = null
        var phase = "precondition"
        try {
            val args = InstrumentationRegistry.getArguments()
            val ids = listOf("protectedBookSHA", "firstOwnedBookSHA", "secondOwnedBookSHA").map { key ->
                val sha = args.getString(key) ?: throw AssertionError("$key required from stopped proof")
                assertTrue(sha.matches(Regex("[a-f0-9]{64}")))
                "local:$sha"
            }
            assertEquals(3, ids.toSet().size)
            val initial = ready { it.books.size == 3 && it.books.map(LibraryBook::id).toSet() == ids.toSet() }
            original = initial
            assertTrue(initial.books.all { it.sourceId == "local" })
            assertEquals(3, initial.books.map(LibraryBook::title).toSet().size)
            val byId = initial.books.associateBy(LibraryBook::id)
            val query = initial.books.map { it.title.trim() }.firstOrNull { candidate ->
                candidate.length in 2..64 && candidate.all { it in '\u4E00'..'\u9FFF' } &&
                    initial.books.count { matchesKnownChineseKeyword(it, candidate) } == 1
            } ?: throw AssertionError("Actual titles lack one unambiguous Chinese query; no invented fallback")
            val matching = initial.books.single { matchesKnownChineseKeyword(it, query) }
            val absent = (2..8).map { query.repeat(it) }.firstOrNull { candidate ->
                initial.books.none { matchesKnownChineseKeyword(it, candidate) }
            } ?: throw AssertionError("Cannot derive a no-match keyword from the real title")
            marker("actual-three-books-ready", Bundle().apply {
                putStringArrayList("bookIds", ArrayList(initial.books.map(LibraryBook::id)))
                putString("originalQuery", initial.shelfQuery)
                putString("originalSort", initial.shelfSort.name)
                putString("originalDirection", initial.shelfDirection.name)
            })

            phase = "unique-title-search"
            setQuery(query)
            val found = ready { it.shelfQuery == query && it.books.map(LibraryBook::id) == listOf(matching.id) }
            assertEquals(matching, found.books.single())
            compose.onNodeWithTag("reader_book_${matching.id}").assertIsDisplayed()
            marker("unique-real-title-visible", Bundle().apply { putString("bookId", matching.id); putString("query", query) })

            phase = "empty-search"
            setQuery(absent)
            ready { it.shelfQuery == absent && it.books.isEmpty() }
            compose.onNodeWithText("没有符合条件的书籍").assertIsDisplayed()
            ids.forEach { compose.onNodeWithTag("reader_book_$it").assertDoesNotExist() }
            marker("real-title-derived-empty-state")

            phase = "clear-search"
            compose.onNodeWithText("清空搜索").performClick()
            val cleared = ready { it.shelfQuery.isEmpty() && it.books.map(LibraryBook::id).toSet() == ids.toSet() }
            assertEquals(byId, cleared.books.associateBy(LibraryBook::id))
            marker("clear-restores-three-books")

            phase = "title-ascending"
            setOrder(LibrarySort.TITLE, LibrarySortDirection.ASCENDING)
            val ascending = ready { it.shelfSort == LibrarySort.TITLE && it.shelfDirection == LibrarySortDirection.ASCENDING }
            assertEquals(byId, ascending.books.associateBy(LibraryBook::id))
            assertVisibleOrder(ascending.books)
            val ascIds = ascending.books.map(LibraryBook::id)
            marker("core-title-ascending-visible", Bundle().apply { putStringArrayList("bookIds", ArrayList(ascIds)) })

            phase = "title-descending"
            setOrder(LibrarySort.TITLE, LibrarySortDirection.DESCENDING)
            val descending = ready { it.shelfSort == LibrarySort.TITLE && it.shelfDirection == LibrarySortDirection.DESCENDING }
            assertEquals(ascIds.reversed(), descending.books.map(LibraryBook::id))
            assertEquals(byId, descending.books.associateBy(LibraryBook::id))
            assertVisibleOrder(descending.books)
            marker("core-title-descending-visible", Bundle().apply {
                putStringArrayList("bookIds", ArrayList(descending.books.map(LibraryBook::id)))
            })
        } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("phase", phase); putString("message", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { if (failure !== markerFailure) failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            original?.let { captured ->
                try {
                    setOrder(captured.shelfSort, captured.shelfDirection)
                    setQuery(captured.shelfQuery)
                    val restored = ready {
                        it.shelfQuery == captured.shelfQuery && it.shelfSort == captured.shelfSort &&
                            it.shelfDirection == captured.shelfDirection && it.books == captured.books
                    }
                    assertEquals(captured.books, restored.books)
                    marker("original-shelf-values-restored")
                } catch (cleanup: Throwable) {
                    val failure = primary
                    if (failure == null) throw cleanup
                    if (failure !== cleanup) failure.addSuppressed(cleanup)
                }
            }
        }
        marker("method-exit")
    }

    // Fixed Core uses instr(lower(title/author/book_id), normalized keyword).
    // The selected input is only Han characters, so no platform case/collation model is needed.
    private fun matchesKnownChineseKeyword(book: LibraryBook, query: String) =
        book.title.contains(query) || book.author.contains(query) || book.id.contains(query)

    private fun ready(predicate: (ReaderUiState) -> Boolean): ReaderUiState {
        val remaining = deadline - SystemClock.elapsedRealtime()
        assertTrue("One method's bounded observation budget expired", remaining > 0)
        compose.waitUntil(minOf(8_000L, remaining)) {
            val state = model.state.value
            state.error != null || state.protocolRecoveryReference != null ||
                (!state.busy && state.ready && state.pendingReady && predicate(state))
        }
        val state = model.state.value
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(!state.busy && state.ready && state.pendingReady && predicate(state))
        assertEquals(ReaderRoute.Bookshelf, state.route)
        assertNull(state.reading); assertNull(state.pendingSearchClose)
        assertTrue(state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
        assertEquals(0L, state.importRevision)
        return state
    }

    private fun setQuery(value: String) {
        if (model.state.value.shelfQuery == value) return
        compose.onNodeWithTag("reader_shelf_query").performTextReplacement(value)
        compose.runOnUiThread {
            val activity = compose.activity
            activity.currentFocus?.let { focused ->
                activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(focused.windowToken, 0)
            }
        }
    }

    private fun setOrder(sort: LibrarySort, direction: LibrarySortDirection) {
        if (model.state.value.shelfSort != sort) {
            compose.onNodeWithTag("reader_shelf_sort").performClick()
            compose.onNodeWithTag("reader_shelf_sort_${sort.name}").performClick()
            ready { it.shelfSort == sort }
        }
        if (model.state.value.shelfDirection != direction) {
            compose.onNodeWithTag("reader_shelf_sort").performClick()
            compose.onNodeWithTag("reader_shelf_sort_direction").performClick()
            ready { it.shelfSort == sort && it.shelfDirection == direction }
        }
    }

    private fun assertVisibleOrder(books: List<LibraryBook>) {
        val list = compose.onNodeWithTag("reader_bookshelf")
        list.performScrollToNode(hasTestTag("reader_book_${books.first().id}"))
        val viewport = list.fetchSemanticsNode().boundsInRoot
        val visible = books.mapNotNull { book ->
            val nodes = compose.onAllNodesWithTag("reader_book_${book.id}").fetchSemanticsNodes()
            assertTrue("One real shelf row per book identity", nodes.size <= 1)
            val bounds = nodes.singleOrNull()?.boundsInRoot ?: return@mapNotNull null
            if (bounds.width > 0 && bounds.height > 0 && bounds.bottom > viewport.top && bounds.top < viewport.bottom)
                book.id to bounds.top else null
        }.sortedBy { it.second }.map { it.first }
        assertTrue("At least two actual visible rows required to observe order", visible.size >= 2)
        assertEquals(books.map(LibraryBook::id).filter { it in visible }, visible)
        visible.forEach { id ->
            compose.onNodeWithTag("reader_book_$id").assertIsDisplayed()
                .assert(hasText(books.single { it.id == id }.title))
        }
    }

    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step)
        extras.putString("readerMethod", "existingThreeRealBooksSearchAndTitleSortPreservesData")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, extras)
    }
}
