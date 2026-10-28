package io.reader.android

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.runtime.ReaderBookmark
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exact existing-book A2 selectors. No import, fixtures, data reset or business retries. */
@RunWith(AndroidJUnit4::class)
class UiDailyReadingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var method = "unassigned"
    private val phases = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            method = description.methodName
            marker("rule-enter")
            try { base.evaluate() } finally { marker("rule-exit") }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phases).around(compose)

    /** contentQuery must be a real original keyword known to have more than one 20-hit page. */
    @Test fun existingRealTxtBookmarkAndSearchPagination() = cleanup {
        marker("method-enter")
        val model = openExistingOriginal()
        val query = InstrumentationRegistry.getArguments().getString("contentQuery")
            ?.takeIf { it.isNotBlank() } ?: throw AssertionError("contentQuery must bind an actual abundant original keyword")
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        val beforeFont = model.state.value.reading!!
        controls(model)
        val oldFont = model.state.value.fontSize
        compose.onNodeWithTag(if (oldFont < 32) "reader_font_larger" else "reader_font_smaller").performClick()
        nativeRestored()
        assertEquals("Reflow alone cannot rewrite Core scalar", beforeFont.visibleScalarOffset,
            model.state.value.reading!!.visibleScalarOffset)
        compose.onNodeWithTag("reader_close_controls").performClick()
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        compose.waitUntil(10_000) { model.state.value.reading!!.visibleScalarOffset != beforeFont.visibleScalarOffset }
        val captured = model.state.value.reading!!
        marker("post-font-user-anchor", Bundle().apply { putInt("scalarOffset", captured.visibleScalarOffset) })
        controls(model)
        compose.onNodeWithTag("reader_open_bookmarks").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy }
        assertEquals(null, model.state.value.bookmarkError)
        val originalIds = model.state.value.bookmarks.map { it.id }.toSet()
        val createRevision = model.state.value.bookmarkRevision
        compose.onNodeWithTag("reader_add_bookmark").performClick()
        compose.waitUntil(20_000) {
            val state = model.state.value
            !state.busy && (state.bookmarkRevision > createRevision || state.bookmarkError != null ||
                state.pendingBookmarks.isNotEmpty() || state.error != null)
        }
        if (model.state.value.bookmarkRevision == createRevision && model.state.value.error != null) {
            marker("bookmark-create-terminal-failure", Bundle().apply { putString("reportedError", model.state.value.error) })
            throw AssertionError(model.state.value.error)
        }
        assertEquals(null, model.state.value.bookmarkError)
        assertTrue(model.state.value.pendingBookmarks.isEmpty())
        val created = model.state.value.lastCreatedBookmark ?: throw AssertionError("Original create receipt missing")
        assertTrue(created.id !in originalIds)
        assertEquals(captured.book.id, created.bookId)
        assertEquals(captured.chapter.index, created.chapterIndex)
        assertEquals(captured.visibleScalarOffset, created.scalarOffset)
        assertEquals(captured.chapter.bodyVersion, created.bodyVersion)
        assertEquals(captured.chapter.processingVersion, created.processingVersion)
        marker("bookmark-create-ACK", Bundle().apply { putString("bookmarkId", created.id) })
        compose.onNodeWithTag("reader_close_panel").performClick()
        // A real native scroll moves away from the captured bookmark before jumping back.
        compose.onNodeWithTag("reader_close_controls").performClick()
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        controls(model)
        compose.onNodeWithTag("reader_open_bookmarks").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.bookmarksReady }
        existingBookmarkJumpDeleteAndSearch(model, created, originalIds, captured.identity, query)
        marker("method-exit")
    }

    /** Consumes exactly the original retained create receipt; never creates another bookmark. */
    @Test fun existingRealTxtExistingBookmarkAndSearchPagination() = cleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val expectedId = args.getString("expectedBookmarkId")
            ?: throw AssertionError("expectedBookmarkId must bind the retained original create receipt")
        val expectedChapter = args.getString("expectedChapter")?.toIntOrNull()
            ?: throw AssertionError("expectedChapter must come from the retained bookmark scope")
        val expectedOffset = args.getString("expectedOffset")?.toIntOrNull()
            ?: throw AssertionError("expectedOffset must come from the retained bookmark scope")
        val bodyVersion = args.getString("expectedBodyVersion")?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("expectedBodyVersion required")
        val processingVersion = args.getString("expectedProcessingVersion")?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("expectedProcessingVersion required")
        val query = args.getString("contentQuery")?.takeIf { it.isNotBlank() }
            ?: throw AssertionError("contentQuery must bind an actual abundant original keyword")
        assertTrue(expectedId.toLongOrNull()?.let { it > 0 } == true && expectedChapter >= 0 && expectedOffset >= 0)
        val model = openExistingOriginal()
        val originalReaderOwner = model.state.value.reading!!.identity
        marker("bookmark-continuation-scroll-away-request")
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        compose.waitUntil(10_000) { model.state.value.reading!!.visibleScalarOffset != expectedOffset }
        marker("bookmark-continuation-scrolled-away")
        controls(model)
        compose.onNodeWithTag("reader_open_bookmarks").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy }
        assertTrue(model.state.value.bookmarksReady && model.state.value.pendingBookmarks.isEmpty())
        assertEquals(null, model.state.value.bookmarkError)
        val bookmark = model.state.value.bookmarks.singleOrNull { it.id == expectedId }
            ?: throw AssertionError("The exact retained bookmark is absent; do not create a replacement")
        assertEquals(model.state.value.reading!!.book.id, bookmark.bookId)
        assertEquals(expectedChapter, bookmark.chapterIndex)
        assertEquals(expectedOffset, bookmark.scalarOffset)
        assertEquals(bodyVersion, bookmark.bodyVersion)
        assertEquals(processingVersion, bookmark.processingVersion)
        val retainedIds = model.state.value.bookmarks.map { it.id }.toSet() - expectedId
        marker("existing-bookmark-admitted", Bundle().apply {
            putString("bookmarkId", bookmark.id); putInt("chapter", bookmark.chapterIndex); putInt("scalarOffset", bookmark.scalarOffset)
        })
        existingBookmarkJumpDeleteAndSearch(model, bookmark, retainedIds, originalReaderOwner, query)
        marker("method-exit")
    }

    private fun existingBookmarkJumpDeleteAndSearch(model: ReaderViewModel, created: ReaderBookmark,
        retainedIds: Set<String>, retiredBookmarkOwner: Long, query: String) {
        marker("bookmark-row-scroll-request")
        compose.onNodeWithTag("reader_bookmark_list").performScrollToNode(hasTestTag("reader_bookmark_${created.id}"))
        marker("bookmark-jump-request")
        compose.onNodeWithTag("reader_bookmark_${created.id}").performClick()
        marker("bookmark-jump-click-returned")
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.readerPanel == null }
        marker("bookmark-core-session-ready")
        nativeRestored()
        marker("bookmark-native-restored-before-anchor")
        val jumped = model.state.value.reading!!
        assertEquals(created.chapterIndex, jumped.chapter.index)
        assertEquals(created.scalarOffset, jumped.restoreScalarOffset)
        assertNativeAnchorVisible(created.scalarOffset)
        marker("bookmark-native-jump")
        controls(model)
        compose.onNodeWithTag("reader_open_bookmarks").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy }
        val deleteRevision = model.state.value.bookmarkRevision
        compose.onNodeWithTag("reader_bookmark_list").performScrollToNode(hasTestTag("reader_delete_bookmark_${created.id}"))
        compose.onNodeWithTag("reader_delete_bookmark_${created.id}").performClick()
        compose.onNodeWithTag("reader_confirmation_confirm").performClick()
        compose.waitUntil(20_000) {
            val state = model.state.value
            !state.busy && (state.bookmarkRevision > deleteRevision || state.bookmarkError != null || state.pendingBookmarks.isNotEmpty())
        }
        assertEquals(null, model.state.value.bookmarkError)
        assertTrue(model.state.value.pendingBookmarks.isEmpty())
        assertEquals("Only the admitted bookmark may be removed", retainedIds, model.state.value.bookmarks.map { it.id }.toSet())
        marker("bookmark-delete-ACK")
        compose.onNodeWithTag("reader_close_panel").performClick()
        compose.onNodeWithTag("reader_open_content_search").performClick()
        compose.onNodeWithTag("reader_content_query").performTextReplacement(query)
        marker("search-first-page-request")
        compose.onNodeWithTag("reader_content_search_submit").performClick()
        searchTerminal(model)
        val firstPage = model.state.value.searchResults.map { it.id }
        marker("search-first-page-confirmed", Bundle().apply { putInt("count", firstPage.size) })
        assertTrue("The supplied real query must expose an actual next cursor", model.state.value.searchHasMore)
        compose.onNodeWithTag("reader_content_results").performScrollToNode(hasTestTag("reader_content_search_next"))
        marker("search-next-page-request")
        compose.onNodeWithTag("reader_content_search_next").performClick()
        searchTerminal(model)
        assertTrue(model.state.value.searchResults.size > firstPage.size)
        assertEquals(firstPage, model.state.value.searchResults.take(firstPage.size).map { it.id })
        assertEquals(model.state.value.searchResults.size, model.state.value.searchResults.map { it.id }.distinct().size)
        val hit = model.state.value.searchResults[firstPage.size]
        val searchOwner = model.state.value.reading!!.identity
        marker("search-cursor-page-confirmed")
        compose.onNodeWithTag("reader_content_results").performScrollToNode(hasTestTag("reader_content_hit_${hit.id}"))
        marker("search-jump-request")
        compose.onNodeWithTag("reader_content_hit_${hit.id}").performClick()
        marker("search-jump-click-returned")
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.readerPanel == null }
        nativeRestored()
        val searched = model.state.value.reading!!
        assertEquals(hit.chapterIndex, searched.chapter.index)
        assertEquals(hit.chapterOffset, searched.restoreScalarOffset)
        assertEquals(hit.positionScope.bodyVersion, searched.chapter.bodyVersion)
        assertEquals(hit.positionScope.processingVersion, searched.chapter.processingVersion)
        assertNativeAnchorVisible(hit.chapterOffset)
        compose.runOnUiThread { model.openContentSearchHit(hit, searchOwner); model.openBookmark(created, retiredBookmarkOwner) }
        assertEquals("Old query/bookmark callbacks cannot move the new session", searched.identity, model.state.value.reading!!.identity)
        marker("search-native-jump-and-retired-callbacks")
        exitAndAssertReceipt(model)
    }

    @Test fun existingRealTxtNativeSelectionCopiesOriginal() = cleanup {
        marker("method-enter")
        lateinit var clipboard: ClipboardManager
        var originalClip: ClipData? = null
        var ownedText: String? = null
        var ownedTimestamp: Long? = null
        var primaryFailure: Throwable? = null
        compose.runOnUiThread {
            clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            originalClip = clipboard.primaryClip // Memory only; never log or persist clipboard content.
        }
        try {
            val model = openExistingOriginal()
            val reading = model.state.value.reading!!
            val text = reading.chapter.text
            val utf16 = ReaderTextCoordinates.utf16Offset(text, reading.visibleScalarOffset)
            val start = if (utf16 == 0) 0 else text.lastIndexOf('\n', utf16 - 1) + 1
            val paragraph = compose.onNodeWithTag("reader_paragraph_$start")
            val bounds = paragraph.fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("reader_body").fetchSemanticsNode().boundsInRoot
            val visibleTop = maxOf(bounds.top, viewport.top)
            val visibleBottom = minOf(bounds.bottom, viewport.bottom)
            assertTrue("Actual original paragraph must be visible", visibleBottom > visibleTop)
            paragraph.performTouchInput {
                longClick(Offset((bounds.width * .35f).coerceAtLeast(1f), (visibleTop + visibleBottom) / 2f - bounds.top))
            }
            val copy = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).findObject(
                UiSelector().textMatches("复制|Copy|COPY").enabled(true))
            assertTrue("Native selection Copy action did not appear", copy.waitForExists(5_000))
            assertTrue("Native Copy action failed", copy.click())
            var copied: String? = null
            var copiedTimestamp: Long? = null
            compose.runOnUiThread {
                val clip = clipboard.primaryClip
                copied = clip?.getItemAt(0)?.text?.toString()
                copiedTimestamp = if (Build.VERSION.SDK_INT >= 26) clip?.description?.timestamp else null
            }
            val selected = copied?.takeIf { it.isNotEmpty() } ?: throw AssertionError("Native selection produced no clipboard text")
            ownedText = selected
            ownedTimestamp = copiedTimestamp
            assertTrue("Clipboard must be an exact substring of original Core body; no title or display placeholder", text.contains(selected))
            marker("native-copy-original-substring", Bundle().apply { putInt("utf16Length", selected.length) })
            exitAndAssertReceipt(model)
            marker("method-exit")
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                if (ownedText != null) compose.runOnUiThread {
                    val current = clipboard.primaryClip
                    val timestamp = if (Build.VERSION.SDK_INT >= 26) current?.description?.timestamp else null
                    if (current?.itemCount == 1 && current.getItemAt(0).text?.toString() == ownedText &&
                        timestamp == ownedTimestamp) {
                        val original = originalClip
                        if (original != null) clipboard.setPrimaryClip(original)
                        else {
                            check(Build.VERSION.SDK_INT >= 28) { "An empty clipboard requires the platform clear API" }
                            clipboard.clearPrimaryClip()
                        }
                    }
                }
            } catch (restoreFailure: Throwable) {
                val primary = primaryFailure
                if (primary != null) primary.addSuppressed(restoreFailure) else throw restoreFailure
            }
        }
    }

    private fun openExistingOriginal(): ReaderViewModel {
        val args = InstrumentationRegistry.getArguments()
        val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required")
        val count = args.getString("expectedChapterCount")?.toIntOrNull() ?: throw AssertionError("expectedChapterCount required")
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")) && count > 1)
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        assertEquals(0L, model.state.value.importRevision)
        assertTrue(model.state.value.pendingImports.isEmpty() && model.state.value.pendingPositions.isEmpty() && model.state.value.pendingBookmarks.isEmpty())
        val book = model.state.value.books.single { it.id == "local:$hash" }
        assertEquals(count, book.chapterCount)
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Reading && !model.state.value.busy }
        nativeRestored()
        marker("existing-original-native-ready")
        return model
    }

    private fun controls(model: ReaderViewModel) {
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.waitUntil(5_000) { model.state.value.controlsVisible }
    }

    private fun searchTerminal(model: ReaderViewModel) {
        // The service has a 30s page request plus bounded original cleanup.
        compose.waitUntil(35_000) { !model.state.value.searchBusy }
        assertEquals("Original search may not remain unknown", null, model.state.value.pendingSearchClose)
        assertEquals(null, model.state.value.searchError)
        assertTrue(model.state.value.searchReady && model.state.value.searchResults.isNotEmpty())
    }

    private fun nativeRestored() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty() }
    }

    private fun assertNativeAnchorVisible(scalar: Int) {
        val reading = compose.activity.readerViewModel.state.value.reading!!
        val utf16 = ReaderTextCoordinates.utf16Offset(reading.chapter.text, scalar)
        val start = if (utf16 == 0) 0 else reading.chapter.text.lastIndexOf('\n', utf16 - 1) + 1
        val node = compose.onNodeWithTag("reader_paragraph_$start").fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = node.config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(compose.runOnUiThread { action?.invoke(layouts) == true })
        val result = layouts.single()
        val line = result.getLineForOffset((utf16 - start).coerceAtMost(result.layoutInput.text.length))
        val viewport = compose.onNodeWithTag("reader_body").fetchSemanticsNode().boundsInRoot
        val top = node.positionInRoot.y + result.getLineTop(line)
        val bottom = node.positionInRoot.y + result.getLineBottom(line)
        assertTrue("Native target line must intersect real viewport", top < viewport.bottom && bottom > viewport.top)
    }

    private fun exitAndAssertReceipt(model: ReaderViewModel) {
        controls(model)
        val captured = model.state.value.reading!!
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        assertTrue(model.state.value.pendingPositions.isEmpty() && model.state.value.pendingBookmarks.isEmpty())
        assertEquals(null, model.state.value.pendingSearchClose)
        val saved = model.state.value.books.single { it.id == captured.book.id }
        assertEquals(captured.chapter.index, saved.progressChapter)
        assertEquals(captured.visibleScalarOffset, saved.progressOffset)
        assertEquals(0L, model.state.value.importRevision)
        marker("exit-save-ACK")
    }

    private fun cleanup(body: () -> Unit) {
        var primary: Throwable? = null
        try { body() } catch (failure: Throwable) {
            primary = failure
            try { marker("method-failure", Bundle().apply { putString("failureMessage", failure.message?.take(256)) }) }
            catch (markerFailure: Throwable) { failure.addSuppressed(markerFailure) }
            throw failure
        }
        finally {
            try {
                marker("teardown-enter")
                val scenario = compose.activityRule.scenario
                if (scenario.state != Lifecycle.State.DESTROYED) {
                    val destroyed = CountDownLatch(1)
                    scenario.onActivity { activity ->
                        activity.lifecycle.addObserver(LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() })
                        activity.finish()
                    }
                    assertTrue(destroyed.await(10, TimeUnit.SECONDS))
                    InstrumentationRegistry.getInstrumentation().runOnMainSync { }
                    assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                }
                marker("activity-destroyed")
                marker("teardown-exit")
            } catch (failure: Throwable) { if (primary != null) primary.addSuppressed(failure) else throw failure }
        }
    }

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerMethod", method)
        details.putString("readerStep", step)
        details.putInt("readerProcessPid", android.os.Process.myPid())
        details.putLong("readerElapsedMs", android.os.SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, details)
    }
}
