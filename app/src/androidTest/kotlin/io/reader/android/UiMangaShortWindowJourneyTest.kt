package io.reader.android

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaProgressPolicy
import io.reader.android.runtime.SourceImportConfirmation
import io.reader.android.ui.ReaderMangaReadingPhase
import io.reader.android.ui.ReaderRoute
import io.reader.android.ui.ReaderViewModel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real short-page window demand and explicit continuation; no simulated scroll or fake anchor. */
@RunWith(AndroidJUnit4::class)
class UiMangaShortWindowJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phaseRule = TestRule { base, _ -> object : Statement() {
        override fun evaluate() { marker("rule-enter"); try { base.evaluate() } finally { marker("rule-exit") } }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(180, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseRule).around(compose)

    @Test fun tinyPagesFillBoundedWindowAndExplicitContinuationSavesOnlyDrawnTarget() {
        marker("method-enter")
        val runId = UUID.randomUUID().toString()
        val sourceId = "android-manga-short-$runId"
        val fixture = UiMangaComposeJourneyTest.Fixture(pageCount = 40, pageHeight = 8)
        val seed = ReaderLibrary(ApplicationProvider.getApplicationContext())
        var scenario: ActivityScenario<MainActivity>? = null
        var activity: MainActivity? = null
        var model: ReaderViewModel? = null
        var phase = "source-seed"
        var complete = false
        var failure: Throwable? = null
        try {
            runBlocking {
                val imported = seed.importSourceJson(sourceId, fixture.sourceJson())
                assertTrue(imported.durable); assertEquals(SourceImportConfirmation.APPLIED, imported.confirmation)
            }
            marker("owned-short-source-ACK", Bundle().apply { putString("sourceId", sourceId) })
            phase = "source-search-detail"
            val launched = ActivityScenario.launch(MainActivity::class.java); scenario = launched
            lateinit var current: MainActivity
            launched.onActivity { current = it }; activity = current
            val vm = current.readerViewModel; model = vm
            compose.waitUntil(20_000) { vm.state.value.ready && !vm.state.value.busy }
            healthy(vm)
            val power = checkNotNull(current.getSystemService(PowerManager::class.java))
            val keyguard = checkNotNull(current.getSystemService(KeyguardManager::class.java))
            compose.waitUntil(5_000) { power.isInteractive && !keyguard.isKeyguardLocked &&
                compose.runOnUiThread { current.window.decorView.hasWindowFocus() } }
            val interactive = power.isInteractive; val locked = keyguard.isKeyguardLocked
            val focused = compose.runOnUiThread { current.window.decorView.hasWindowFocus() }
            assertTrue(interactive); assertFalse(locked); assertTrue(focused)
            marker("short-physical-interaction-preflight", Bundle().apply {
                putBoolean("interactive", interactive); putBoolean("keyguardLocked", locked); putBoolean("windowFocused", focused)
            })
            val shelfBefore = vm.state.value.books.toList(); val remoteBefore = vm.state.value.remoteBooks.toList()
            compose.onNodeWithTag("reader_online_sources").performClick(); onlineIdle(vm)
            val sourceIndex = vm.onlineSources.state.value.sources.indexOfFirst { it.sourceId == sourceId }
            assertTrue(sourceIndex >= 0)
            clickWorkspace("reader_source_select_$sourceIndex"); onlineIdle(vm)
            compose.onNodeWithTag("reader_online_query").performTextInput("Comic")
            launched.onActivity { it.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
            clickWorkspace("reader_online_search"); onlineIdle(vm)
            clickWorkspace("reader_online_detail_0"); onlineIdle(vm)
            phase = "automatic-short-window"
            clickWorkspace("reader_online_manga_read")
            // No scroll gesture or anchor injection. The actual viewport has to demand its visible tail.
            compose.waitUntil(60_000) { vm.mangaReadingState.value.let {
                (it.phase == ReaderMangaReadingPhase.READY && it.pages.size == 32 && it.pendingWindow == null && it.restoreRequest == null) ||
                    it.phase == ReaderMangaReadingPhase.ERROR || it.windowError != null || it.progressError != null
            } }
            val filled = vm.mangaReadingState.value
            assertEquals("Initial window: ${filled.error}/${filled.windowError}", ReaderMangaReadingPhase.READY, filled.phase)
            assertEquals(40, filled.window?.totalPages)
            assertEquals((0..31).toList(), filled.pages.map { it.ordinal })
            assertEquals(0, filled.visibleAnchor?.ordinal)
            assertFalse(filled.progressDirty || filled.progressBusy)
            assertEquals(0L, filled.progressToken?.revision)
            assertNull(filled.lastConfirmedProgress)
            assertTrue(progressRecords(sourceId).isEmpty())
            compose.onNodeWithTag("reader_manga_page_31").assertIsDisplayed()
            compose.onNodeWithTag("reader_manga_continue").assertIsDisplayed().assertIsEnabled()
            val beforeImage = capture(runId, "bounded-window")
            marker("short-window-filled-without-scroll-or-write", Bundle().apply {
                putInt("firstOrdinal", filled.pages.first().ordinal); putInt("lastOrdinal", filled.pages.last().ordinal)
                putInt("loadedPages", filled.pages.size); putLong("revision", checkNotNull(filled.progressToken).revision)
                putString("imagePath", beforeImage.first); putString("imageSha256", beforeImage.second)
            })

            phase = "explicit-next-segment"
            compose.onNodeWithTag("reader_manga_continue").performClick()
            compose.waitUntil(45_000) { vm.mangaReadingState.value.let {
                (it.visibleAnchor?.ordinal == 32 && it.pages.lastOrNull()?.ordinal == 39 && it.pendingWindow == null &&
                    it.restoreRequest == null && !it.progressBusy && !it.progressDirty && it.lastConfirmedProgress?.pageOrdinalFallback == 32) ||
                    it.phase == ReaderMangaReadingPhase.ERROR || it.windowError != null || it.progressError != null
            } }
            val advanced = vm.mangaReadingState.value
            assertNull(advanced.error); assertNull(advanced.windowError); assertNull(advanced.progressError)
            assertEquals((32..39).toList(), advanced.pages.map { it.ordinal })
            assertEquals(32, advanced.visibleAnchor?.ordinal)
            compose.onNodeWithTag("reader_manga_page_32").assertIsDisplayed()
            compose.onNodeWithTag("reader_manga_page_39").assertIsDisplayed()
            val confirmed = checkNotNull(advanced.lastConfirmedProgress)
            assertEquals(32, confirmed.pageOrdinalFallback)
            assertEquals(checkNotNull(advanced.visibleAnchor).pageId, confirmed.pageId)
            assertEquals(checkNotNull(advanced.visibleAnchor).x, confirmed.x, 0.0)
            assertEquals(checkNotNull(advanced.visibleAnchor).y, confirmed.y, 0.0)
            assertEquals(1L, confirmed.progressRevision)
            val records = progressRecords(sourceId)
            assertEquals("The explicit navigation creates exactly one actual progress original", 1, records.size)
            val original = records.single()
            assertTrue(original.getBoolean("completed")); assertTrue(original.getBoolean("transportConfirmed"))
            assertEquals(confirmed, ReaderMangaProgressPolicy.acknowledgement(original, original.getJSONObject("wireTerminal")).location)
            val file = File(instrumentation.targetContext.filesDir, "Reader/Operations/Completed/${original.getString("id")}.json")
            val afterImage = capture(runId, "continued-window")
            marker("short-explicit-target-drawn-original-ACK", Bundle().apply {
                putString("sourceId", sourceId); putString("operationId", original.getString("id"))
                putString("originalRecordSha256", sha(file.readBytes())); putLong("originalRecordBytes", file.length())
                putString("pageId", confirmed.pageId); putInt("ordinal", confirmed.pageOrdinalFallback)
                putDouble("x", confirmed.x); putDouble("y", confirmed.y); putLong("revision", confirmed.progressRevision)
                putString("imagePath", afterImage.first); putString("imageSha256", afterImage.second)
            })

            phase = "settled-exit"
            compose.onNodeWithTag("reader_manga_exit").assertIsEnabled().performClick()
            compose.waitUntil(5_000) { vm.state.value.route == ReaderRoute.Bookshelf && vm.mangaReadingState.value.phase == ReaderMangaReadingPhase.IDLE }
            healthy(vm)
            assertEquals(shelfBefore, vm.state.value.books); assertEquals(remoteBefore, vm.state.value.remoteBooks)
            assertEquals(records.map { it.toString() }, progressRecords(sourceId).map { it.toString() })
            marker("short-settled-exit-no-extra-write")
            complete = true
        } catch (error: Throwable) {
            failure = error
            marker("method-failure", Bundle().apply {
                putString("phase", phase); putString("failureClass", error.javaClass.name); putString("message", error.message?.take(512))
                model?.mangaReadingState?.value?.let {
                    putInt("pages", it.pages.size); putInt("ordinal", it.currentOrdinal); putString("windowError", it.windowError)
                    putString("progressError", it.progressError); putBoolean("dirty", it.progressDirty)
                }
            })
            throw error
        } finally {
            fun cleanup(block: () -> Unit) { try { block() } catch (error: Throwable) {
                val previous = failure; if (previous == null) { failure = error; throw error } else previous.addSuppressed(error)
            } }
            try {
                if (model?.state?.value?.route == ReaderRoute.Bookshelf) cleanup { finish(scenario, activity) }
                if (complete) cleanup {
                    marker("short-cleanup-client-enter")
                    ReaderLibrary(ApplicationProvider.getApplicationContext()).use { library -> runBlocking {
                        val source = library.sources(sourceId).single()
                        val deleted = library.deleteSource(sourceId, source.sourceVersion)
                        assertTrue(deleted.durable); assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
                    } }
                    marker("short-owned-source-cleanup-ACK")
                }
            } finally { try { cleanup { seed.close() } } finally { cleanup { fixture.close() } } }
        }
        marker("method-exit")
    }

    private fun healthy(model: ReaderViewModel) {
        val state = model.state.value
        assertTrue(state.ready && state.pendingReady); assertFalse(state.busy)
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
    }
    private fun onlineIdle(model: ReaderViewModel) {
        compose.waitUntil(30_000) { !model.state.value.busy && !model.onlineSources.state.value.busy }
        healthy(model); assertNull(model.onlineSources.state.value.error); assertFalse(model.onlineSources.state.value.pending)
    }
    private fun clickWorkspace(tag: String) {
        compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("reader_online_workspace"))).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun progressRecords(sourceId: String): List<JSONObject> {
        val directory = File(instrumentation.targetContext.filesDir, "Reader/Operations")
        assertTrue("Active original is not completion", directory.listFiles().orEmpty().none { it.isFile })
        val completed = File(directory, "Completed").listFiles().orEmpty().filter { it.name.startsWith("android-manga-progress-") }
        assertFalse(completed.any { it.name.endsWith(".new") || it.name.endsWith(".bak") })
        return completed.filter { it.extension == "json" }.map { JSONObject(it.readText()) }
            .filter { it.getString("sourceId") == sourceId }.sortedBy { it.getString("id") }
    }
    private fun capture(runId: String, label: String): Pair<String, String> {
        val image = compose.onNodeWithTag("reader_manga_viewport").captureToImage()
        assertTrue(image.width > 300 && image.height > 300)
        val pixels = image.toPixelMap(); var colored = 0
        for (y in 0 until image.height step 10) for (x in 0 until image.width step 20) {
            val pixel = pixels[x,y]
            if (pixel.red > 0.6f && pixel.red > pixel.green * 1.4f || pixel.blue > 0.6f && pixel.blue > pixel.green * 1.4f) colored++
        }
        assertTrue("Actual short-page colored strips must be drawn", colored > 300)
        val relative = "reader-manga-short/$runId/$label.png"
        val file = File(instrumentation.targetContext.cacheDir, relative)
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)) }
        return relative to sha(file.readBytes())
    }
    private fun finish(scenario: ActivityScenario<MainActivity>?, activity: MainActivity?) {
        val owned = scenario ?: return; val current = activity ?: return
        marker("short-activity-finish-enter")
        if (owned.state != Lifecycle.State.DESTROYED) {
            assertEquals(ReaderRoute.Bookshelf, current.readerViewModel.state.value.route)
            val destroyed = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(destroyed.await(5,TimeUnit.SECONDS))
                compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } finally { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
        }
        marker("short-activity-destroyed")
        owned.close(); marker("short-scenario-closed")
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep",step)
        extras.putString("readerMethod","tinyPagesFillBoundedWindowAndExplicitContinuationSavesOnlyDrawnTarget")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2,extras)
    }
}
