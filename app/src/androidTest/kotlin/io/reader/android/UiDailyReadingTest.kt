package io.reader.android

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.graphics.Point
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.runtime.ReaderBookmark
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderScalarOffsetKey
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
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Exact existing-book A2 selectors. No import, fixtures, data reset or business retries. */
@RunWith(AndroidJUnit4::class)
class UiDailyReadingTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var method = "unassigned"
    private var observedNativeOwner: Long? = null
    private var observedNativeScroll: ScrollView? = null
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
        controls(model)
        val beforeFont = model.state.value.reading!!
        val oldFont = model.state.value.fontSize
        compose.onNodeWithTag(if (oldFont < 32) "reader_font_larger" else "reader_font_smaller").performClick()
        nativeRestored()
        assertEquals("Reflow alone cannot rewrite Core scalar", beforeFont.visibleScalarOffset,
            model.state.value.reading!!.visibleScalarOffset)
        compose.onNodeWithTag("reader_close_controls").performClick()
        compose.onNodeWithTag("reader_body").performTouchInput { swipeUp(durationMillis = 500) }
        compose.waitForIdle()
        compose.waitUntil(10_000) { model.state.value.reading!!.visibleScalarOffset != beforeFont.visibleScalarOffset }
        controls(model)
        val captured = model.state.value.reading!!
        marker("post-font-user-anchor", Bundle().apply { putInt("scalarOffset", captured.visibleScalarOffset) })
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

    /** Exit-only continuation after an independently retained current-position receipt. */
    @Test fun existingRealTxtCurrentPositionControlsAndExit() = cleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val expectedChapter = args.getString("expectedChapter")?.toIntOrNull()
            ?: throw AssertionError("expectedChapter must come from the retained current-position receipt")
        val expectedOffset = args.getString("expectedOffset")?.toIntOrNull()
            ?: throw AssertionError("expectedOffset must come from the retained current-position receipt")
        assertTrue(expectedChapter >= 0 && expectedOffset >= 0)
        val model = openExistingOriginal()
        val reading = model.state.value.reading!!
        assertEquals(expectedChapter, reading.chapter.index)
        assertEquals(expectedOffset, reading.restoreScalarOffset)
        assertEquals(expectedOffset, reading.visibleScalarOffset)
        assertNativeAnchorVisible(expectedOffset)
        marker("current-position-admitted", Bundle().apply {
            putInt("chapter", expectedChapter); putInt("scalarOffset", expectedOffset)
        })
        exitAndAssertReceipt(model)
        marker("method-exit")
    }

    private fun existingBookmarkJumpDeleteAndSearch(model: ReaderViewModel, created: ReaderBookmark,
        retainedIds: Set<String>, retiredBookmarkOwner: Long, query: String) {
        marker("bookmark-row-scroll-request")
        scrollToolRow(R.id.reader_native_bookmark_list, model.state.value.bookmarks.indexOfFirst { it.id == created.id },
            "reader_bookmark_${created.id}", "reader_bookmark_${created.id}")
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
        captureNativeEvidence("bookmark-jump")
        controls(model)
        compose.onNodeWithTag("reader_open_bookmarks").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy }
        val deleteRevision = model.state.value.bookmarkRevision
        scrollToolRow(R.id.reader_native_bookmark_list, model.state.value.bookmarks.indexOfFirst { it.id == created.id },
            "reader_bookmark_${created.id}", "reader_delete_bookmark_${created.id}")
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
        val firstSession = model.state.value.searchSession ?: throw AssertionError("Original search session missing")
        val firstCursor = model.state.value.searchCursor ?: throw AssertionError("Original next cursor missing")
        scrollToolRow(R.id.reader_native_search_list, firstPage.size,
            "reader_content_search_next:${firstSession.id}:$firstCursor", "reader_content_search_next")
        marker("search-next-page-request")
        compose.onNodeWithTag("reader_content_search_next").performClick()
        searchTerminal(model)
        assertTrue(model.state.value.searchResults.size > firstPage.size)
        assertEquals(firstPage, model.state.value.searchResults.take(firstPage.size).map { it.id })
        assertEquals(model.state.value.searchResults.size, model.state.value.searchResults.map { it.id }.distinct().size)
        val hit = model.state.value.searchResults[firstPage.size]
        val searchOwner = model.state.value.reading!!.identity
        marker("search-cursor-page-confirmed")
        scrollToolRow(R.id.reader_native_search_list, firstPage.size,
            "reader_content_hit_${hit.id}", "reader_content_hit_${hit.id}")
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
        captureNativeEvidence("search-jump")
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
        var selectionDevice: UiDevice? = null
        compose.runOnUiThread {
            clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            originalClip = clipboard.primaryClip // Memory only; never log or persist clipboard content.
        }
        try {
            val model = openExistingOriginal()
            val reading = model.state.value.reading!!
            val text = reading.chapter.text
            val beforeCopyScrollY = compose.runOnUiThread {
                compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll).scrollY
            }
            assertTrue("The original chapter must contain real line breaks", text.contains('\n'))
            marker("native-copy-source-shape", Bundle().apply {
                putBoolean("hasLineBreak", text.contains('\n'))
                putBoolean("hasBlankLine", text.contains("\n\n") || text.contains("\r\n\r\n"))
            })
            val (selectionView, point) = visibleNativeCharacter()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            selectionDevice = device
            // UiAutomator 2.3's drag holds DOWN for the platform long-press
            // duration in real time. Compose's event clock cannot drive the
            // native TextView Handler's delayed long-press callback.
            marker("native-long-press-request")
            assertTrue("Native long press was not dispatched", device.drag(point.x, point.y, point.x, point.y, 1))
            fun ownsSelection(): Boolean = compose.runOnUiThread {
                model.state.value.reading?.identity == reading.identity && selectionView.isAttachedToWindow &&
                    compose.activity.findViewById<TextView>(R.id.reader_native_text) === selectionView &&
                    selectionView.hasSelection() && text.contentEquals(selectionView.text)
            }
            compose.waitUntil(5_000) { ownsSelection() }
            marker("native-selection-owner-ready")
            val selectAll = device.findObject(UiSelector().textMatches("全选|Select all|SELECT ALL").enabled(true))
            if (!selectAll.waitForExists(5_000)) {
                val overflowId = "android:id/floating_toolbar_overflow_button"
                assertTrue("Native Select all is absent and no unique observed toolbar overflow is available",
                    device.findObjects(By.res(overflowId).enabled(true)).size == 1)
                assertTrue("The observed overflow must belong to the original native selection", ownsSelection())
                marker("native-selection-overflow-observed")
                assertTrue("Observed native toolbar overflow click failed",
                    device.findObject(UiSelector().resourceId(overflowId).enabled(true)).click())
                assertTrue("Opening overflow must retain the original native selection", ownsSelection())
            }
            assertTrue("Native Select all action did not appear", selectAll.waitForExists(5_000))
            assertTrue("Native Select all action failed", selectAll.click())
            compose.waitUntil(5_000) {
                ownsSelection() && compose.runOnUiThread {
                    minOf(selectionView.selectionStart, selectionView.selectionEnd) == 0 &&
                        maxOf(selectionView.selectionStart, selectionView.selectionEnd) == text.length
                }
            }
            marker("native-selection-whole-chapter-ready")
            val copy = device.findObject(
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
            assertEquals("Native whole-chapter copy must retain original UTF-16, line breaks and blank lines without the title", text, selected)
            marker("native-copy-original-whole-chapter", Bundle().apply { putInt("utf16Length", selected.length) })
            assertEquals("Native selection/copy must not rebase the logical reading scalar", reading.visibleScalarOffset,
                model.state.value.reading!!.visibleScalarOffset)
            val afterCopyScrollY = compose.runOnUiThread {
                compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll).scrollY
            }
            marker("native-copy-logical-anchor-preserved", Bundle().apply {
                putInt("scalarOffset", reading.visibleScalarOffset)
                putBoolean("nativeViewportMoved", afterCopyScrollY != beforeCopyScrollY)
            })
            exitAndAssertReceipt(model)
            marker("method-exit")
        } catch (failure: Throwable) {
            primaryFailure = failure
            try { selectionDevice?.let(::captureSelectionFailure) }
            catch (evidenceFailure: Throwable) { failure.addSuppressed(evidenceFailure) }
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

    /** Coordinates come from the attached platform Layout and its real clipped viewport. */
    private fun visibleNativeCharacter(): Pair<TextView, Point> = compose.runOnUiThread {
        val body = compose.activity.findViewById<TextView>(R.id.reader_native_text)
        val scroll = compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
        val layout = body.layout ?: throw AssertionError("Native Layout is absent")
        val viewport = Rect()
        assertTrue(scroll.getGlobalVisibleRect(viewport))
        val bodyLocation = IntArray(2); body.getLocationOnScreen(bodyLocation)
        val scrollLocation = IntArray(2); scroll.getLocationOnScreen(scrollLocation)
        assertTrue("Native selection must use the content clip without ScrollView padding", viewport.intersect(
            scrollLocation[0] + scroll.paddingLeft, scrollLocation[1] + scroll.paddingTop,
            scrollLocation[0] + scroll.width - scroll.paddingRight, scrollLocation[1] + scroll.height - scroll.paddingBottom))
        val origin = bodyLocation[1] + body.totalPaddingTop
        val first = layout.getLineForVertical((viewport.top - origin).coerceAtLeast(0))
        val last = layout.getLineForVertical((viewport.bottom - origin - 1).coerceAtLeast(0))
        val raw = body.text.toString()
        val line = (first..last).firstOrNull { index ->
            origin + layout.getLineTop(index) >= viewport.top && origin + layout.getLineBottom(index) <= viewport.bottom &&
                (layout.getLineStart(index) until layout.getLineEnd(index)).any { !raw[it].isWhitespace() }
        } ?: throw AssertionError("No original character is visible for native selection")
        val offset = (layout.getLineStart(line) until layout.getLineEnd(line)).first { !raw[it].isWhitespace() }
        val next = offset + Character.charCount(raw.codePointAt(offset))
        val x = bodyLocation[0] + body.totalPaddingLeft + (layout.getPrimaryHorizontal(offset) + layout.getPrimaryHorizontal(next)) / 2f
        val y = origin + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val screenPoint = Point(x.roundToInt(), y.roundToInt())
        assertTrue("The actual glyph point must remain inside the native content clip", viewport.contains(screenPoint.x, screenPoint.y))
        body to screenPoint
    }

    /** Same UiDevice/UiAutomation, once on failure; diagnostics never replace the primary result. */
    private fun captureSelectionFailure(device: UiDevice) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = "reader-evidence-$method-selection-failure"
        val details = Bundle().apply { putString("fileBase", base) }
        try {
            device.dumpWindowHierarchy(File(context.cacheDir, "$base.xml"))
            details.putBoolean("hierarchySaved", true)
        } catch (failure: Exception) { details.putString("hierarchyFailureClass", failure.javaClass.name) }
        try { details.putBoolean("screenshotSaved", device.takeScreenshot(File(context.cacheDir, "$base.png"))) }
        catch (failure: Exception) { details.putString("screenshotFailureClass", failure.javaClass.name) }
        marker("native-selection-failure-evidence", details)
    }

    private fun captureNativeEvidence(phase: String) {
        val name = "reader-evidence-$method-$phase.png"
        val details = Bundle().apply { putString("fileName", name) }
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.cacheDir, name))
            details.putBoolean("saved", saved)
        } catch (failure: Exception) {
            details.putBoolean("saved", false)
            details.putString("failureClass", failure.javaClass.name)
        }
        try { marker("native-screenshot-$phase", details) } catch (_: Exception) { /* Independent evidence only. */ }
    }

    private fun controls(model: ReaderViewModel) {
        val owner = model.state.value.reading!!.identity
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        // Native GestureDetector uses the real main Handler; a model flag alone
        // does not prove that StateFlow collection has committed the controls.
        compose.waitUntil(5_000) {
            model.state.value.reading?.identity == owner && model.state.value.controlsVisible && enabledExitPresent()
        }
        marker("controls-exit-enabled")
        awaitAttachedDraw(compose.activity.window.decorView)
        assertEquals(owner, model.state.value.reading!!.identity)
        assertTrue("Actual enabled exit must survive the attached draw", enabledExitPresent())
        marker("controls-draw-ACK")
    }

    private fun enabledExitPresent(): Boolean {
        val nodes = compose.onAllNodes(hasTestTag("reader_exit")).fetchSemanticsNodes()
        return nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config &&
            nodes.single().boundsInRoot.width > 0f && nodes.single().boundsInRoot.height > 0f
    }

    /** One standard native scroll, then bounded observation of the exact bound row and real action. */
    private fun scrollToolRow(resourceId: Int, position: Int, expectedKey: String, actionTag: String) {
        assertTrue("The original target must exist in the admitted projection", position >= 0)
        compose.waitUntil(5_000) {
            compose.runOnUiThread {
                val list = compose.activity.findViewById<RecyclerView>(resourceId)
                list != null && list.isAttachedToWindow && position < (list.adapter?.itemCount ?: 0)
            }
        }
        compose.runOnUiThread {
            val list = compose.activity.findViewById<RecyclerView>(resourceId)
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
        }
        compose.waitUntil(5_000) {
            compose.runOnUiThread {
                val list = compose.activity.findViewById<RecyclerView>(resourceId)
                val row = list?.findViewHolderForAdapterPosition(position)?.itemView
                row != null && row.isAttachedToWindow && row.height > 0 && row.tag == expectedKey
            }
        }
        compose.waitUntil(5_000) {
            val nodes = compose.onAllNodes(hasTestTag(actionTag)).fetchSemanticsNodes()
            nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config
        }
    }

    private fun searchTerminal(model: ReaderViewModel) {
        // The service has a 30s page request plus bounded original cleanup.
        compose.waitUntil(35_000) { !model.state.value.searchBusy }
        assertEquals("Original search may not remain unknown", null, model.state.value.pendingSearchClose)
        assertEquals(null, model.state.value.searchError)
        assertTrue(model.state.value.searchReady && model.state.value.searchResults.isNotEmpty())
    }

    private fun nativeRestored() {
        val model = compose.activity.readerViewModel
        val expected = model.state.value
        val reading = expected.reading ?: throw AssertionError("No current reading session to restore")
        val previousOwner = observedNativeOwner
        val previousView = observedNativeScroll
        var actualView: ScrollView? = null
        compose.waitUntil(10_000) {
            val roots = compose.onAllNodes(hasTestTag("reader_reading")).fetchSemanticsNodes()
            val semanticsMatch = roots.size == 1 && ReaderChapterIndexKey in roots.single().config &&
                ReaderScalarOffsetKey in roots.single().config &&
                roots.single().config[ReaderChapterIndexKey] == reading.chapter.index &&
                roots.single().config[ReaderScalarOffsetKey] == reading.visibleScalarOffset
            val controlsMatch = compose.onAllNodes(hasTestTag("reader_controls")).fetchSemanticsNodes().isNotEmpty() == expected.controlsVisible
            val panelGone = expected.readerPanel != null ||
                compose.onAllNodes(hasTestTag("reader_tools_panel")).fetchSemanticsNodes().isEmpty()
            val loadingGone = compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty()
            val nativeMatch = compose.runOnUiThread {
                val activity = compose.activity
                val scroll = activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                val body = activity.findViewById<TextView>(R.id.reader_native_text)
                val layout = body?.layout
                val replaced = previousOwner == null || previousOwner == reading.identity ||
                    (scroll !== previousView && previousView?.isAttachedToWindow != true)
                val panelsRetired = expected.readerPanel != null ||
                    listOf(R.id.reader_native_bookmark_list, R.id.reader_native_search_list).all { id ->
                        activity.findViewById<RecyclerView>(id)?.isAttachedToWindow != true
                    }
                val ready = scroll != null && body != null && scroll.isAttachedToWindow && body.isAttachedToWindow &&
                    scroll.isShown && body.isShown && scroll.isLaidOut && body.isLaidOut &&
                    !scroll.isLayoutRequested && !body.isLayoutRequested && layout != null &&
                    layout.width == body.width - body.totalPaddingLeft - body.totalPaddingRight &&
                    reading.chapter.text.contentEquals(body.text) && abs(body.textSize - TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, expected.fontSize.toFloat(), body.resources.displayMetrics)) < .5f &&
                    replaced && panelsRetired
                if (ready) actualView = scroll
                ready
            }
            model.state.value.reading?.identity == reading.identity && semanticsMatch && controlsMatch && panelGone && loadingGone && nativeMatch
        }
        val native = actualView ?: throw AssertionError("Current native viewport did not become ready")
        awaitAttachedDraw(native)
        assertEquals("The native draw must still belong to the admitted reading session", reading.identity, model.state.value.reading!!.identity)
        observedNativeOwner = reading.identity
        observedNativeScroll = native
        marker("native-session-draw-ACK", Bundle().apply {
            putLong("readingOwner", reading.identity); putInt("chapter", reading.chapter.index)
            putInt("scalarOffset", reading.visibleScalarOffset)
        })
    }

    /** A real attached tree draw acknowledgement, without sleeps or business input. */
    private fun awaitAttachedDraw(view: View) {
        val drawn = AtomicBoolean(false)
        lateinit var observer: ViewTreeObserver
        val listener = ViewTreeObserver.OnDrawListener {
            if (view.isAttachedToWindow && view.isShown && view.width > 0 && view.height > 0) drawn.set(true)
        }
        compose.runOnUiThread {
            assertTrue("The draw target must be attached", view.isAttachedToWindow && view.isShown)
            observer = view.viewTreeObserver
            observer.addOnDrawListener(listener)
            view.invalidate()
        }
        try {
            compose.waitUntil(5_000) { drawn.get() }
            // This main-queue observation runs after the OnDraw dispatch returns.
            compose.runOnUiThread { assertTrue("The acknowledged view must remain attached", view.isAttachedToWindow && view.isShown) }
        } finally {
            compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) }
        }
    }

    private fun assertNativeAnchorVisible(scalar: Int) {
        val reading = compose.activity.readerViewModel.state.value.reading
            ?: throw AssertionError("No reader owns the native anchor")
        val utf16 = ReaderTextCoordinates.utf16Offset(reading.chapter.text, scalar)
        compose.runOnUiThread {
            val body = compose.activity.findViewById<TextView>(R.id.reader_native_text)
                ?: throw AssertionError("Actual native body is absent")
            val scroll = compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                ?: throw AssertionError("Actual native viewport is absent")
            assertTrue(body.isAttachedToWindow && scroll.isAttachedToWindow)
            assertEquals("Native text must retain the complete original UTF-16 body", reading.chapter.text, body.text.toString())
            val layout = body.layout ?: throw AssertionError("Actual native Layout is absent")
            val line = layout.getLineForOffset(utf16)
            val viewport = Rect()
            assertTrue(scroll.getGlobalVisibleRect(viewport))
            val scrollLocation = IntArray(2)
            scroll.getLocationOnScreen(scrollLocation)
            assertTrue("Native content clip must exclude ScrollView padding", viewport.intersect(
                scrollLocation[0] + scroll.paddingLeft, scrollLocation[1] + scroll.paddingTop,
                scrollLocation[0] + scroll.width - scroll.paddingRight, scrollLocation[1] + scroll.height - scroll.paddingBottom))
            val location = IntArray(2)
            body.getLocationOnScreen(location)
            val top = location[1] + body.totalPaddingTop + layout.getLineTop(line)
            val bottom = location[1] + body.totalPaddingTop + layout.getLineBottom(line)
            assertTrue("Original scalar's actual native line must intersect the viewport", top < viewport.bottom && bottom > viewport.top)
        }
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
