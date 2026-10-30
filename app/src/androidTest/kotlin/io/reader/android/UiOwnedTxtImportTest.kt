package io.reader.android

import android.os.Bundle
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.TextView
import android.os.Process as AndroidProcess
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiWatcher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.reader.android.runtime.ReaderTextCoordinates
import io.reader.android.runtime.LibraryBook
import io.reader.android.ui.ReaderViewModel
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderChapterIndexKey
import io.reader.android.ui.ReaderScalarOffsetKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import kotlin.math.abs

/** SOURCE-ONLY one-owned-TXT SAF import. Picker/native/teardown helpers reused unchanged. */
@RunWith(AndroidJUnit4::class)
class UiOwnedTxtImportTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var currentMethod = "unassigned"
    private var observedNativeOwner: Long? = null
    private var observedNativeScroll: ScrollView? = null
    private val phaseMarker = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                currentMethod = description.methodName
                marker("rule-enter")
                try { base.evaluate() }
                finally { marker("rule-exit") }
            }
        }
    }
    // Unlike @Test(timeout), this deadline also surrounds Compose/Activity
    // setup and teardown. The external controller retains its own client bound.
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(240, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseMarker).around(compose)

    @Test
    fun oneRealOwnedTxtImportsWithOriginalReceipt() = withMethodCleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("displayName") ?: throw AssertionError("displayName required")
        val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required")
        val bytes = args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required")
        val protectedHash = args.getString("protectedBookSHA") ?: throw AssertionError("protectedBookSHA required")
        assertTrue(name.endsWith(".txt") && '/' !in name && '\\' !in name)
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")) && protectedHash.matches(Regex("[a-f0-9]{64}")) && hash != protectedHash && bytes > 0)
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        val before = model.state.value
        assertEquals(null, before.error)
        assertEquals(null, before.protocolRecoveryReference)
        assertTrue(before.pendingReady && before.pendingImports.isEmpty() && before.pendingPositions.isEmpty() && before.pendingBookmarks.isEmpty())
        assertEquals(null, before.pendingSearchClose)
        assertEquals(0L, before.importRevision)
        val protected = before.books.single { it.id == "local:$protectedHash" }
        assertTrue(before.books.none { it.id == "local:$hash" })
        marker("owned-input-admitted", Bundle().apply {
            putString("displayName", name); putString("expectedSHA", hash); putLong("expectedBytes", bytes)
            putString("protectedBookSHA", protectedHash)
        })
        compose.onNodeWithTag("reader_import").performClick()
        selectRealDocument(name)
        compose.waitUntil(120_000) {
            val value = model.state.value
            !value.busy && (value.importRevision > before.importRevision || value.error != null ||
                value.protocolRecoveryReference != null || value.pendingImports.isNotEmpty())
        }
        val imported = model.state.value
        assertEquals(null, imported.error)
        assertEquals(null, imported.protocolRecoveryReference)
        assertTrue(imported.pendingImports.isEmpty() && imported.pendingPositions.isEmpty() && imported.pendingBookmarks.isEmpty())
        assertEquals(1L, imported.importRevision)
        assertEquals("local:$hash", imported.lastImportedBookId)
        val book = imported.books.single { it.id == "local:$hash" }
        assertTrue(book.chapterCount > 0)
        assertEquals(protected, imported.books.single { it.id == protected.id })
        marker("owned-import-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapterCount", book.chapterCount); putLong("importRevision", imported.importRevision)
        })
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Reading }
        waitForNativeRestore()
        showControlsAndAwaitDraw(model, "reader_open_directory")
        compose.onNodeWithTag("reader_open_directory").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Directory }
        val toc = model.state.value.chapters
        assertEquals(book.chapterCount, toc.size)
        val selected = toc.firstOrNull { it.title == "Chapter 1" }
            ?: throw AssertionError("Real Core TOC lacks the first nonempty Alice chapter")
        assertEquals("Chapter 1", selected.title)
        marker("owned-core-directory-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
        })
        compose.onNodeWithTag("reader_chapter_${selected.index}").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Reading &&
            model.state.value.reading?.chapter?.index == selected.index }
        waitForNativeRestore()
        val reading = model.state.value.reading ?: throw AssertionError("Actual reading owner absent")
        assertTrue(reading.chapter.text.any { !it.isWhitespace() })
        assertEquals(book.id, reading.chapter.bookId)
        assertNativeAnchorVisible(reading.visibleScalarOffset)
        marker("owned-nonempty-native-anchor-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapter", reading.chapter.index); putInt("scalarOffset", reading.visibleScalarOffset)
            putInt("scalarCount", reading.chapter.scalarCount); putInt("utf16Count", reading.chapter.text.length)
            putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
        })
        exitToBookshelfAndAwaitSave(model, book.id, reading.chapter.index, reading.visibleScalarOffset)
        val saved = model.state.value.books.single { it.id == book.id }
        marker("owned-final-saved-projection", Bundle().apply {
            putString("bookId", saved.id); putInt("chapter", saved.progressChapter); putInt("scalarOffset", saved.progressOffset)
        })
        assertEquals(protected, model.state.value.books.single { it.id == protected.id })
        assertEquals(1L, model.state.value.importRevision)
        marker("method-exit")
    }

    /** SOURCE-ONLY one-owned-TXT SAF import from an explicitly empty App state. */
    @Test
    fun oneFreshOwnedTxtImportsFromEmptyState() = withMethodCleanup {
        marker("method-enter")
        val args = InstrumentationRegistry.getArguments()
        val name = args.getString("displayName") ?: throw AssertionError("displayName required")
        val hash = args.getString("expectedSHA") ?: throw AssertionError("expectedSHA required")
        val bytes = args.getString("expectedBytes")?.toLongOrNull() ?: throw AssertionError("expectedBytes required")
        assertTrue(name.endsWith(".txt") && '/' !in name && '\\' !in name)
        assertTrue(hash.matches(Regex("[a-f0-9]{64}")) && bytes > 0)
        val model = compose.activity.readerViewModel
        compose.waitUntil(20_000) { model.state.value.ready && !model.state.value.busy }
        val before = model.state.value
        assertEquals(null, before.error)
        assertEquals(null, before.protocolRecoveryReference)
        assertTrue(before.pendingReady && before.pendingImports.isEmpty() && before.pendingPositions.isEmpty() && before.pendingBookmarks.isEmpty())
        assertEquals(null, before.pendingSearchClose)
        assertEquals(0L, before.importRevision)
        assertTrue("Fresh App state must have no books", before.books.isEmpty())
        marker("owned-input-admitted", Bundle().apply {
            putString("displayName", name); putString("expectedSHA", hash); putLong("expectedBytes", bytes)
            putBoolean("freshState", true)
        })
        compose.onNodeWithTag("reader_import").performClick()
        selectFreshDocument(name)
        compose.waitUntil(120_000) {
            val value = model.state.value
            !value.busy && (value.importRevision > before.importRevision || value.error != null ||
                value.protocolRecoveryReference != null || value.pendingImports.isNotEmpty())
        }
        val imported = model.state.value
        assertEquals(null, imported.error)
        assertEquals(null, imported.protocolRecoveryReference)
        assertTrue(imported.pendingImports.isEmpty() && imported.pendingPositions.isEmpty() && imported.pendingBookmarks.isEmpty())
        assertEquals(1L, imported.importRevision)
        assertEquals("local:$hash", imported.lastImportedBookId)
        val book = imported.books.single { it.id == "local:$hash" }
        assertTrue(book.chapterCount > 0)
        marker("owned-import-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapterCount", book.chapterCount); putLong("importRevision", imported.importRevision)
        })
        compose.onNodeWithTag("reader_book_${book.id}").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Reading }
        waitForNativeRestore()
        showControlsAndAwaitDraw(model, "reader_open_directory")
        compose.onNodeWithTag("reader_open_directory").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Directory }
        val toc = model.state.value.chapters
        assertEquals(book.chapterCount, toc.size)
        val selected = toc.firstOrNull { it.index > 0 } ?: toc.firstOrNull() ?: throw AssertionError("Real Core TOC is empty")
        marker("owned-core-directory-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapterCount", toc.size); putInt("selectedChapter", selected.index)
        })
        compose.onNodeWithTag("reader_chapter_${selected.index}").performClick()
        compose.waitUntil(20_000) { !model.state.value.busy && model.state.value.route == ReaderRoute.Reading &&
            model.state.value.reading?.chapter?.index == selected.index }
        waitForNativeRestore()
        val reading = model.state.value.reading ?: throw AssertionError("Actual reading owner absent")
        assertTrue(reading.chapter.text.any { !it.isWhitespace() })
        assertEquals(book.id, reading.chapter.bookId)
        assertNativeAnchorVisible(reading.visibleScalarOffset)
        marker("owned-nonempty-native-anchor-ACK", Bundle().apply {
            putString("bookId", book.id); putInt("chapter", reading.chapter.index); putInt("scalarOffset", reading.visibleScalarOffset)
            putInt("scalarCount", reading.chapter.scalarCount); putInt("utf16Count", reading.chapter.text.length)
            putString("bodyVersion", reading.chapter.bodyVersion); putString("processingVersion", reading.chapter.processingVersion)
        })
        exitToBookshelfAndAwaitSave(model, book.id, reading.chapter.index, reading.visibleScalarOffset)
        val saved = model.state.value.books.single { it.id == book.id }
        marker("owned-final-saved-projection", Bundle().apply {
            putString("bookId", saved.id); putInt("chapter", saved.progressChapter); putInt("scalarOffset", saved.progressOffset)
        })
        assertEquals(1L, model.state.value.importRevision)
        marker("method-exit")
    }

    private fun withMethodCleanup(body: () -> Unit) {
        var primaryFailure: Throwable? = null
        try {
            body()
        } catch (failure: Throwable) {
            primaryFailure = failure
            try {
                marker("method-failure", Bundle().apply {
                    putString("failureClass", failure.javaClass.name)
                    putString("failureMessage", failure.message?.take(256))
                })
            } catch (markerFailure: Throwable) { failure.addSuppressed(markerFailure) }
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            try {
                marker("teardown-enter")
                finishOwnedActivity()
            } catch (failure: Throwable) { cleanupFailure = failure }
            try {
                marker("teardown-exit", Bundle().apply { putBoolean("destroyed", cleanupFailure == null) })
            } catch (failure: Throwable) {
                val earlierCleanupFailure = cleanupFailure
                if (earlierCleanupFailure == null) cleanupFailure = failure else earlierCleanupFailure.addSuppressed(failure)
            }
            cleanupFailure?.let { failure ->
                val originalFailure = primaryFailure
                if (originalFailure != null) originalFailure.addSuppressed(failure)
                else throw AssertionError("Business method completed but Activity teardown failed").apply { addSuppressed(failure) }
            }
        }
    }

    private fun finishOwnedActivity() {
        val scenario = compose.activityRule.scenario
        if (scenario.state == Lifecycle.State.DESTROYED) {
            marker("activity-destroyed")
            return
        }
        val destroyed = CountDownLatch(1)
        var owner: MainActivity? = null
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    source.lifecycle.removeObserver(this)
                    destroyed.countDown()
                }
            }
        }
        try {
            // The scenario supplies its actual current instance, including after
            // recreate. Standard finish avoids launching a test-APK bootstrap.
            scenario.onActivity { activity ->
                owner = activity
                activity.lifecycle.addObserver(observer)
                activity.finish()
            }
            assertTrue("Owned MainActivity did not reach ON_DESTROY within 10 seconds",
                destroyed.await(10, TimeUnit.SECONDS))
            // Drain the completed destruction callback before inspecting the
            // scenario's steady state; no Compose idle or second UI owner.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue("The exact finished MainActivity is not destroyed", owner?.isDestroyed == true)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            marker("activity-destroyed")
        } finally {
            owner?.let { activity -> activity.runOnUiThread { activity.lifecycle.removeObserver(observer) } }
        }
    }

    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step)
        details.putString("readerMethod", currentMethod)
        details.putInt("readerProcessPid", AndroidProcess.myPid())
        details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, details)
    }

    private fun exitToBookshelfAndAwaitSave(model: ReaderViewModel, bookId: String, chapter: Int, scalar: Int) {
        showControlsAndAwaitDraw(model, "reader_exit")
        marker("final-exit-request")
        compose.onNodeWithTag("reader_exit").performClick()
        compose.waitUntil(20_000) { model.state.value.route == ReaderRoute.Bookshelf && !model.state.value.busy }
        assertEquals("Final exit must retain no failure", null, model.state.value.error)
        assertTrue("Final save cannot remain unknown", model.state.value.pendingPositions.none { it.bookId == bookId })
        val saved = model.state.value.books.single { it.id == bookId }
        assertEquals(chapter, saved.progressChapter)
        assertEquals(scalar, saved.progressOffset)
        marker("final-exit-save-ACK", Bundle().apply { putInt("chapter", chapter); putInt("scalarOffset", scalar) })
    }

    /** Confirm the expected content's actual native laid-out line intersects the visible body. */
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
            val top = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineTop(line)
            val bottom = location[1] + body.totalPaddingTop - body.scrollY + layout.getLineBottom(line)
            assertTrue("Original scalar's actual native line must intersect the viewport: scalar=$scalar utf16=$utf16 " +
                "line=$line top=$top bottom=$bottom viewport=$viewport scrollY=${scroll.scrollY} " +
                "bodyScrollY=${body.scrollY} selection=${body.selectionStart}/${body.selectionEnd}",
                top < viewport.bottom && bottom > viewport.top)
        }
    }

    private fun waitForNativeRestore(replacedView: ScrollView? = null) {
        val model = compose.activity.readerViewModel
        val expected = model.state.value
        val reading = expected.reading ?: throw AssertionError("No current reading session to restore")
        val previousView = replacedView ?: observedNativeScroll
        val mustReplace = replacedView != null || (observedNativeOwner != null && observedNativeOwner != reading.identity)
        var actualView: ScrollView? = null
        compose.waitUntil(10_000) {
            val roots = compose.onAllNodes(hasTestTag("reader_reading")).fetchSemanticsNodes()
            val semanticsMatch = roots.size == 1 && ReaderChapterIndexKey in roots.single().config &&
                ReaderScalarOffsetKey in roots.single().config &&
                roots.single().config[ReaderChapterIndexKey] == reading.chapter.index &&
                roots.single().config[ReaderScalarOffsetKey] == reading.visibleScalarOffset
            val controlsMatch = compose.onAllNodes(hasTestTag("reader_controls")).fetchSemanticsNodes().isNotEmpty() == expected.controlsVisible
            val loadingGone = compose.onAllNodes(hasTestTag("reader_restore_loading")).fetchSemanticsNodes().isEmpty()
            val nativeMatch = compose.runOnUiThread {
                val scroll = compose.activity.findViewById<ScrollView>(R.id.reader_native_scroll)
                val body = compose.activity.findViewById<TextView>(R.id.reader_native_text)
                val layout = body?.layout
                val replaced = !mustReplace || (scroll !== previousView && previousView?.isAttachedToWindow != true)
                val ready = scroll != null && body != null && scroll.isAttachedToWindow && body.isAttachedToWindow &&
                    scroll.isShown && body.isShown && scroll.isLaidOut && body.isLaidOut &&
                    !scroll.isLayoutRequested && !body.isLayoutRequested && layout != null &&
                    layout.width == body.width - body.totalPaddingLeft - body.totalPaddingRight &&
                    reading.chapter.text.contentEquals(body.text) && abs(body.textSize - TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, expected.fontSize.toFloat(), body.resources.displayMetrics)) < .5f && replaced
                if (ready) actualView = scroll
                ready
            }
            model.state.value.reading?.identity == reading.identity && semanticsMatch && controlsMatch && loadingGone && nativeMatch
        }
        val native = actualView ?: throw AssertionError("Current native viewport did not become ready")
        awaitAttachedDraw(native)
        assertEquals("The drawn native view must retain the admitted reader", reading.identity, model.state.value.reading!!.identity)
        observedNativeOwner = reading.identity
        observedNativeScroll = native
        marker("native-session-draw-ACK", Bundle().apply {
            putLong("readingOwner", reading.identity); putInt("chapter", reading.chapter.index)
            putInt("scalarOffset", reading.visibleScalarOffset); putBoolean("replacedView", mustReplace)
        })
    }

    private fun showControlsAndAwaitDraw(model: ReaderViewModel, actionTag: String) {
        val owner = model.state.value.reading!!.identity
        if (!model.state.value.controlsVisible) compose.onNodeWithTag("reader_body").performTouchInput { click(center) }
        compose.waitUntil(5_000) {
            model.state.value.reading?.identity == owner && model.state.value.controlsVisible && enabledControlPresent(actionTag)
        }
        awaitAttachedDraw(compose.activity.window.decorView)
        assertEquals(owner, model.state.value.reading!!.identity)
        assertTrue("The actual enabled control must survive the attached draw", enabledControlPresent(actionTag))
        marker("native-control-draw-ACK", Bundle().apply { putString("actionTag", actionTag) })
    }

    private fun enabledControlPresent(tag: String): Boolean {
        val nodes = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes()
        return nodes.size == 1 && SemanticsProperties.Disabled !in nodes.single().config &&
            nodes.single().boundsInRoot.width > 0f && nodes.single().boundsInRoot.height > 0f
    }

    /** Observe the real attached tree's next draw without sleeping or issuing business input. */
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
            compose.runOnUiThread { assertTrue("The acknowledged view must remain attached", view.isAttachedToWindow && view.isShown) }
        } finally {
            compose.runOnUiThread { if (observer.isAlive) observer.removeOnDrawListener(listener) }
        }
    }

    private fun selectRealDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-original-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) {
                false
            } else {
                val titleSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/alertTitle").text(consentTitle)
                val checkboxSelector = UiSelector().packageName(consentPackage)
                    .resourceId("$consentPackage:id/coui_security_alert_dialog_checkbox").text("始终允许打开")
                val openSelector = UiSelector().packageName(consentPackage)
                    .resourceId("android:id/button1").text("打开").enabled(true)
                if (!device.findObject(titleSelector).exists() || device.findObject(checkboxSelector).isChecked ||
                    !device.findObject(openSelector).isEnabled) false else {
                    consentClickAttempted = true // At most one dispatch, including a failed click.
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle); putBoolean("checkboxChecked", false) })
                    val clicked = device.findObject(openSelector).click()
                    val gone = device.findObject(titleSelector).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android\\.documentsui"
            val providerSelector = UiSelector().packageNameMatches(provider)
            assertTrue("System document picker must actually open",
                device.findObject(providerSelector).waitForExists(10_000))
            marker("picker-visible")
            val file = providerSelector.text(name)
            val folder = providerSelector.text("Reader")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().textMatches("Downloads|下载|下载内容"))
            if (!device.findObject(file).exists()) {
                // The previous picker can retain its drawer. Do not toggle an already
                // open drawer, and never carry UiObject2 across its animation.
                if (device.findObject(drawer).exists()) {
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                } else if (!device.findObject(folder).exists()) {
                    val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                    clickPickerOnce(device, roots, "picker-roots", drawer)
                    marker("picker-drawer-ready")
                    clickPickerOnce(device, downloads, "picker-downloads", folder, gone = drawer)
                }
                if (!device.findObject(file).exists()) clickPickerOnce(device, folder, "picker-folder", file)
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            // UiObject stores a selector and resolves its live node during this one click.
            assertTrue("Original document click was not dispatched", device.findObject(file).click())
            assertTrue("System picker did not return to Reader", device.findObject(providerSelector).waitUntilGone(10_000))
            assertTrue("Original picker return did not expose Reader's actual window",
                device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000))
            marker("picker-returned")
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    /** Fresh-shelf picker variant: retain click booleans as observations and
     * require the next DocumentsUI state as the actual navigation evidence. */
    private fun selectFreshDocument(name: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val watcherName = "reader-fresh-picker-open-consent"
        val consentPackage = "com.oplus.securitypermission"
        val consentTitle = "“Reader”想要打开“io.reader.minliny.test”"
        var consentClickAttempted = false
        device.registerWatcher(watcherName, UiWatcher {
            val title = By.pkg(consentPackage).res("$consentPackage:id/alertTitle").text(consentTitle)
            val checkbox = By.pkg(consentPackage).res("$consentPackage:id/coui_security_alert_dialog_checkbox")
                .text("始终允许打开").checkable(true).checked(false).enabled(true)
            val open = By.pkg(consentPackage).res("android:id/button1").text("打开").enabled(true)
            if (consentClickAttempted || device.findObjects(title).size != 1 ||
                device.findObjects(checkbox).size != 1 || device.findObjects(open).size != 1) false
            else {
                val selector = UiSelector().packageName(consentPackage).resourceId("android:id/button1").text("打开").enabled(true)
                if (!device.findObject(selector).exists()) false else {
                    consentClickAttempted = true
                    marker("oem-open-observed", Bundle().apply { putString("dialogTitle", consentTitle) })
                    val clicked = device.findObject(selector).click()
                    val goneSelector = UiSelector().packageName(consentPackage)
                        .resourceId("$consentPackage:id/alertTitle").text(consentTitle)
                    val gone = device.findObject(goneSelector).waitUntilGone(2_000)
                    marker("oem-open-ACK", Bundle().apply { putBoolean("clickDispatched", clicked); putBoolean("dialogGone", gone) })
                    clicked
                }
            }
        })
        try {
            val provider = "com\\.(google\\.)?android.documentsui"
            val providerSelector = UiSelector().packageNameMatches(provider)
            assertTrue("System document picker must actually open",
                device.findObject(providerSelector).waitForExists(10_000))
            marker("picker-visible")
            val file = providerSelector.text(name)
            val folder = providerSelector.text("Reader")
            val readerBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").text("Reader")
            val downloadsBreadcrumb = providerSelector.resourceIdMatches(".*:id/breadcrumb_text").textMatches("Downloads|下载|下载内容")
            val drawer = providerSelector.resourceIdMatches(".*:id/roots_list")
            val downloads = drawer.childSelector(UiSelector().textMatches("Downloads|下载|下载内容"))
            if (!device.findObject(file).waitForExists(2_000)) {
                val alreadyInReader = device.findObject(readerBreadcrumb).waitForExists(2_000) &&
                    device.findObject(downloadsBreadcrumb).waitForExists(2_000)
                if (!alreadyInReader) {
                    if (device.findObject(drawer).exists()) {
                        marker("picker-drawer-ready")
                        clickPickerOnceObserved(device, downloads, "picker-downloads", folder, gone = drawer)
                    } else if (!device.findObject(folder).exists()) {
                        val roots = providerSelector.descriptionMatches("Show roots|显示根目录|显示位置|导航抽屉")
                        clickPickerOnceObserved(device, roots, "picker-roots", drawer)
                        marker("picker-drawer-ready")
                        clickPickerOnceObserved(device, downloads, "picker-downloads", folder, gone = drawer)
                    }
                    if (!device.findObject(readerBreadcrumb).exists()) {
                        clickPickerOnceObserved(device, folder, "picker-folder", readerBreadcrumb)
                    }
                    assertTrue("Reader breadcrumb absent", device.findObject(readerBreadcrumb).waitForExists(5_000))
                    assertTrue("Downloads ancestor breadcrumb absent", device.findObject(downloadsBreadcrumb).waitForExists(5_000))
                }
                if (!device.findObject(file).waitForExists(2_000)) {
                    exposeFreshDocumentTitle(device, provider, file)
                }
            }
            assertTrue("Original input missing: $name", device.findObject(file).waitForExists(5_000))
            marker("picker-file-click")
            val clickReturned = device.findObject(file).click()
            marker("picker-file-click-observed", Bundle().apply { putBoolean("clickReturned", clickReturned) })
            val pickerGone = device.findObject(providerSelector).waitUntilGone(10_000)
            assertTrue("System picker disappearance was not observed within 10 seconds", pickerGone)
            val readerWindowObserved = device.findObject(UiSelector().packageName("io.reader.minliny")).waitForExists(10_000)
            assertTrue("Reader actual window was not observed after file selection", readerWindowObserved)
            marker("picker-returned", Bundle().apply {
                putBoolean("clickReturned", clickReturned); putBoolean("pickerGone", pickerGone)
                putBoolean("readerWindowObserved", readerWindowObserved)
            })
        } finally {
            device.removeWatcher(watcherName)
        }
    }

    private fun exposeFreshDocumentTitle(device: UiDevice, provider: String, file: UiSelector) {
        val container = UiSelector().packageNameMatches(provider).className("android.widget.ScrollView").scrollable(true)
        for (attempt in 1..2) {
            val beforeVisible = device.findObject(file).waitForExists(750)
            if (beforeVisible) return
            assertTrue("Unique DocumentsUI scroll container absent", device.findObject(container.instance(0)).exists())
            assertFalse("DocumentsUI scroll container ambiguous", device.findObject(container.instance(1)).exists())
            val returned = UiScrollable(container).scrollForward(35)
            val afterVisible = device.findObject(file).waitForExists(1_000)
            marker("picker-document-scroll-$attempt", Bundle().apply {
                putInt("attempt", attempt); putInt("maxScrolls", 2); putInt("scrollSteps", 35)
                putBoolean("beforeVisible", beforeVisible); putBoolean("scrollReturned", returned); putBoolean("afterVisible", afterVisible)
            })
            if (afterVisible) return
        }
        throw AssertionError("Exact fresh TXT document-row title absent after two bounded DocumentsUI scrolls")
    }

    private fun clickPickerOnceObserved(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        val returned = device.findObject(selector).click()
        marker("$stage-click-observed", Bundle().apply { putBoolean("clickReturned", returned) })
        try {
            if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
            assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        } catch (failure: Throwable) {
            try {
                val hierarchy = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                    "reader-fresh-picker-${stage}-${System.currentTimeMillis()}.xml")
                device.dumpWindowHierarchy(hierarchy)
                marker("fresh-picker-hierarchy", Bundle().apply {
                    putString("stage", stage); putString("path", hierarchy.absolutePath); putLong("bytes", hierarchy.length())
                })
            } catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
            throw failure
        }
        marker("$stage-ready")
    }

    private fun clickPickerOnce(device: UiDevice, selector: UiSelector, stage: String, next: UiSelector, gone: UiSelector? = null) {
        assertTrue("$stage target absent", device.findObject(selector).waitForExists(5_000))
        marker("$stage-click")
        // Reacquire from the exact selector immediately before the only click.
        assertTrue("$stage click was not dispatched", device.findObject(selector).click())
        if (gone != null) assertTrue("$stage did not retire its drawer", device.findObject(gone).waitUntilGone(5_000))
        assertTrue("$stage did not expose the next state", device.findObject(next).waitForExists(5_000))
        marker("$stage-ready")
    }
}
