package io.reader.android

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaLocation
import io.reader.android.runtime.ReaderMangaProgressPolicy
import io.reader.android.runtime.ReaderOnlineContentKind
import io.reader.android.runtime.SourceImportConfirmation
import io.reader.android.ui.ReaderMangaReadingPhase
import io.reader.android.ui.ReaderMangaReadingUiState
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

/** Three owned loopback chapters through the actual Library, Compose and original progress journal.
 * Normal settled-ACK navigation only; this does not claim external-source or failure/rollback coverage. */
@RunWith(AndroidJUnit4::class)
class UiMangaChapterJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val files get() = instrumentation.targetContext.filesDir
    private val phaseRule = TestRule { base, _ -> object : Statement() {
        override fun evaluate() { marker("rule-enter"); try { base.evaluate() } finally { marker("rule-exit") } }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(180, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseRule).around(compose)

    @Test fun realDirectoryAndAdjacentChaptersSaveOnlyDrawnAnchors() {
        marker("method-enter")
        val runId = UUID.randomUUID().toString()
        val sourceId = "android-manga-chapter-" + runId
        val retained = operationSnapshot()
        marker("chapter-preservation-before", Bundle().apply {
            putInt("retainedOperations", retained.size); putString("operationsProofSha256", snapshotSha(retained))
        })
        val fixture = UiMangaComposeJourneyTest.Fixture(pageCount = 2, pageHeight = 512, chapterCount = 3)
        val seed = ReaderLibrary(ApplicationProvider.getApplicationContext())
        var scenario: ActivityScenario<MainActivity>? = null
        var activity: MainActivity? = null
        var model: ReaderViewModel? = null
        var phase = "owned-source-import"
        var complete = false
        var failure: Throwable? = null
        var finalOriginals: List<Original> = emptyList()
        try {
            runBlocking {
                val imported = seed.importSourceJson(sourceId, fixture.sourceJson())
                assertTrue(imported.durable); assertEquals(SourceImportConfirmation.APPLIED, imported.confirmation)
                marker("chapter-owned-source-original-ACK", Bundle().apply {
                    putString("sourceId", sourceId); putString("operationId", imported.operationId)
                })
            }
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
            marker("chapter-physical-interaction-preflight", Bundle().apply {
                putBoolean("interactive", interactive); putBoolean("keyguardLocked", locked); putBoolean("windowFocused", focused)
            })
            val shelfBefore = vm.state.value.books.toList(); val remoteBefore = vm.state.value.remoteBooks.toList()
            compose.onNodeWithTag("reader_online_sources").performClick(); onlineIdle(vm)
            val sourceIndex = vm.onlineSources.state.value.sources.indexOfFirst { it.sourceId == sourceId }
            assertTrue(sourceIndex >= 0)
            clickWorkspace("reader_source_select_" + sourceIndex); onlineIdle(vm)
            assertEquals(sourceId, vm.onlineSources.state.value.selection?.sourceId)
            compose.onNodeWithTag("reader_online_query").performTextInput("Comic")
            launched.onActivity { it.getSystemService(InputMethodManager::class.java)
                .hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
            clickWorkspace("reader_online_search"); onlineIdle(vm)
            assertEquals(1, vm.onlineSources.state.value.books.size)
            clickWorkspace("reader_online_detail_0"); onlineIdle(vm)
            assertEquals(ReaderOnlineContentKind.MANGA, vm.onlineSources.state.value.detail?.contentKind)
            clickWorkspace("reader_online_manga_read")
            awaitDraw(vm, chapterIndex = 0, ordinal = 0)
            val initial = vm.mangaReadingState.value
            assertEquals(sourceId, initial.chapter?.manifest?.chapter?.sourceId)
            assertEquals(0L, initial.progressToken?.revision)
            assertFalse(initial.progressDirty || initial.progressBusy); assertNull(initial.lastConfirmedProgress)
            assertTrue(ownedOriginals(sourceId, retained.keys).isEmpty())
            capture(runId, "initial-red", ordinal = 0, hue = Hue.RED)
            marker("chapter-initial-drawn-without-write")

            phase = "one-native-scroll-and-original-ACK"
            val scrollBefore = scrollPosition()
            compose.onNodeWithTag("reader_manga_pages").performTouchInput {
                swipeUp(startY = height * .85f, endY = height * .2f, durationMillis = 400)
            }
            compose.waitUntil(5_000) { scrollPosition() > scrollBefore }
            assertTrue(scrollPosition() > scrollBefore)
            // One gesture, one idle anchor; do not manufacture a second movement just to choose a page number.
            val first = awaitOriginal(vm, sourceId, retained.keys, chapterIndex = 0, revision = 1)
            assertTrue(first.location.pageOrdinalFallback > 0 || first.location.y > 0.0)
            capture(runId, "scrolled-first", first.location.pageOrdinalFallback,
                if (first.location.pageOrdinalFallback == 0) Hue.RED else Hue.BLUE)
            originalMarker("chapter-scrolled-original-ACK", first, vm.mangaReadingState.value)

            phase = "next-chapter-FIRST"
            assertOriginalUnchanged(first)
            marker("chapter-next-origin-ACK-before-click", originalFields(first))
            compose.onNodeWithTag("reader_manga_next_chapter").assertIsEnabled().performClick()
            awaitDraw(vm, chapterIndex = 1, ordinal = 0)
            assertOriginalUnchanged(first)
            val secondDraw = vm.mangaReadingState.value
            assertEquals(0.0, checkNotNull(secondDraw.visibleAnchor).x, 0.0)
            assertEquals(0.0, checkNotNull(secondDraw.visibleAnchor).y, 0.0)
            capture(runId, "next-green", ordinal = 0, hue = Hue.GREEN)
            marker("chapter-next-target-actual-draw", Bundle().apply {
                putString("priorOperationId", first.record.getString("id")); putString("priorOriginalSha256", sha(first.raw))
                putInt("chapterIndex", 1); putInt("ordinal", 0)
            })
            val second = awaitOriginal(vm, sourceId, retained.keys, chapterIndex = 1, revision = 2)
            originalMarker("chapter-next-original-ACK", second, vm.mangaReadingState.value)

            phase = "previous-chapter-LAST-native-clamp"
            assertOriginalUnchanged(second)
            marker("chapter-previous-origin-ACK-before-click", originalFields(second))
            compose.onNodeWithTag("reader_manga_previous_chapter").assertIsEnabled().performClick()
            awaitDraw(vm, chapterIndex = 0, ordinal = 1)
            assertOriginalUnchanged(second)
            val previousDraw = vm.mangaReadingState.value
            val actualLast = checkNotNull(previousDraw.visibleAnchor)
            assertTrue("Native tail clamp, not the desired y=1, must be reported", actualLast.y >= 0.0 && actualLast.y < 1.0)
            capture(runId, "previous-blue-last", ordinal = 1, hue = Hue.BLUE)
            marker("chapter-previous-target-actual-draw", Bundle().apply {
                putString("priorOperationId", second.record.getString("id")); putString("priorOriginalSha256", sha(second.raw))
                putInt("chapterIndex", 0); putInt("ordinal", 1); putDouble("nativeClampedY", actualLast.y)
            })
            val third = awaitOriginal(vm, sourceId, retained.keys, chapterIndex = 0, revision = 3)
            assertEquals(actualLast.y, third.location.y, 0.0)
            originalMarker("chapter-previous-original-ACK", third, vm.mangaReadingState.value)

            phase = "directory-third-chapter-FIRST"
            assertOriginalUnchanged(third)
            val retainedWindow = vm.mangaReadingState.value.visibleWindow
            val retainedAnchor = vm.mangaReadingState.value.visibleAnchor
            val retainedTicket = vm.mangaReadingState.value.layoutTicket
            compose.onNodeWithTag("reader_manga_directory_open").assertIsEnabled().performClick()
            compose.waitUntil(3_000) { vm.mangaDirectoryState.value.visible && !vm.mangaDirectoryState.value.busy }
            assertNull(vm.mangaDirectoryState.value.error)
            val directory = vm.mangaDirectoryState.value
            val thirdIndex = directory.nodes.indexOfFirst { it.kind == "target" && it.chapterIndex == 2 }
            assertTrue("The actual issued directory must contain chapter 3", thirdIndex >= 0)
            assertEquals(retainedWindow, vm.mangaReadingState.value.visibleWindow)
            assertEquals(retainedAnchor, vm.mangaReadingState.value.visibleAnchor)
            assertEquals(retainedTicket, vm.mangaReadingState.value.layoutTicket)
            compose.onNodeWithTag("reader_manga_directory").assertIsDisplayed()
            captureDirectoryScreen(runId)
            assertOriginalUnchanged(third)
            marker("chapter-directory-origin-ACK-before-click", originalFields(third).apply {
                putString("issuedNodeId", directory.nodes[thirdIndex].nodeId); putInt("issuedChapterIndex", 2)
            })
            compose.onNodeWithTag("reader_manga_directory_" + thirdIndex).assertIsDisplayed().assertIsEnabled().performClick()
            awaitDraw(vm, chapterIndex = 2, ordinal = 0)
            assertOriginalUnchanged(third)
            assertFalse(vm.mangaDirectoryState.value.visible)
            capture(runId, "directory-purple", ordinal = 0, hue = Hue.PURPLE)
            marker("chapter-directory-target-actual-draw", Bundle().apply {
                putString("priorOperationId", third.record.getString("id")); putString("priorOriginalSha256", sha(third.raw))
                putInt("chapterIndex", 2); putInt("ordinal", 0)
            })
            val fourth = awaitOriginal(vm, sourceId, retained.keys, chapterIndex = 2, revision = 4)
            assertEquals(0.0, fourth.location.x, 0.0); assertEquals(0.0, fourth.location.y, 0.0)
            originalMarker("chapter-directory-original-ACK", fourth, vm.mangaReadingState.value)
            finalOriginals = ownedOriginals(sourceId, retained.keys)
            assertEquals(listOf(1L, 2L, 3L, 4L), finalOriginals.map { it.location.progressRevision })

            phase = "normal-no-change-exit"
            compose.onNodeWithTag("reader_manga_exit").assertIsEnabled().performClick()
            compose.waitUntil(5_000) { vm.state.value.route == ReaderRoute.Bookshelf &&
                vm.mangaReadingState.value.phase == ReaderMangaReadingPhase.IDLE }
            healthy(vm)
            assertEquals(shelfBefore, vm.state.value.books); assertEquals(remoteBefore, vm.state.value.remoteBooks)
            assertEquals(finalOriginals.map { sha(it.raw) }, ownedOriginals(sourceId, retained.keys).map { sha(it.raw) })
            marker("chapter-normal-exit-no-extra-write", Bundle().apply {
                putInt("originalCount", 4); putInt("preservedLocalShelfCount", shelfBefore.size)
                putInt("preservedRemoteShelfCount", remoteBefore.size)
            })
            complete = true
        } catch (error: Throwable) {
            failure = error
            marker("method-failure", Bundle().apply {
                putString("phase", phase); putString("failureClass", error.javaClass.name); putString("message", error.message?.take(512))
                model?.mangaReadingState?.value?.let {
                    putInt("chapterIndex", it.chapter?.chapterIndex ?: -1); putInt("ordinal", it.currentOrdinal)
                    putString("chapterError", it.chapterError); putString("windowError", it.windowError)
                    putString("progressError", it.progressError); putBoolean("dirty", it.progressDirty)
                    putString("transition", it.chapterTransition?.phase?.name)
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
                    ReaderLibrary(ApplicationProvider.getApplicationContext()).use { library -> runBlocking {
                        val source = library.sources(sourceId).single()
                        val deleted = library.deleteSource(sourceId, source.sourceVersion)
                        assertTrue(deleted.durable); assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
                        marker("chapter-owned-source-cleanup-ACK", Bundle().apply { putString("operationId", deleted.operationId) })
                    } }
                    assertEquals(finalOriginals.map { sha(it.raw) }, ownedOriginals(sourceId, retained.keys).map { sha(it.raw) })
                    val after = operationSnapshot()
                    retained.forEach { (name, identity) -> assertEquals("Existing original changed: " + name, identity, after[name]) }
                    marker("chapter-preservation-after", Bundle().apply {
                        putInt("retainedOperations", retained.size); putString("operationsProofSha256", snapshotSha(retained))
                        putInt("ownedProgressOriginals", finalOriginals.size)
                    })
                }
            } finally { try { cleanup { seed.close() } } finally { cleanup { fixture.close() } } }
        }
        marker("method-exit")
    }

    private fun awaitDraw(vm: ReaderViewModel, chapterIndex: Int, ordinal: Int) {
        compose.waitUntil(35_000) { vm.mangaReadingState.value.let {
            (it.phase == ReaderMangaReadingPhase.READY && it.chapter?.chapterIndex == chapterIndex &&
                it.visibleAnchor?.ordinal == ordinal && it.restoreRequest == null && it.chapterTransition == null) ||
                it.phase == ReaderMangaReadingPhase.ERROR || it.chapterError != null || it.progressError != null
        } }
        val state = vm.mangaReadingState.value
        assertEquals(ReaderMangaReadingPhase.READY, state.phase)
        assertNull(state.error); assertNull(state.chapterError); assertNull(state.windowError); assertNull(state.progressError)
        assertEquals(chapterIndex, state.chapter?.chapterIndex); assertEquals(ordinal, state.visibleAnchor?.ordinal)
        assertNull(state.restoreRequest); assertNull(state.chapterTransition)
        assertNotNull(state.layoutTicket)
        compose.onNodeWithTag("reader_manga_page_" + ordinal).assertIsDisplayed()
    }

    private fun awaitOriginal(vm: ReaderViewModel, sourceId: String, baselineNames: Set<String>,
        chapterIndex: Int, revision: Long): Original {
        compose.waitUntil(12_000) { vm.mangaReadingState.value.let {
            (!it.progressBusy && !it.progressDirty && it.lastConfirmedProgress?.progressRevision == revision) ||
                it.chapterError != null || it.progressError != null || it.windowError != null
        } }
        val state = vm.mangaReadingState.value
        assertEquals(chapterIndex, state.chapter?.chapterIndex)
        assertNull(state.chapterError); assertNull(state.progressError); assertNull(state.windowError)
        assertFalse(state.progressBusy || state.progressDirty); assertTrue(state.progressPending.isEmpty())
        val location = checkNotNull(state.lastConfirmedProgress)
        val anchor = checkNotNull(state.visibleAnchor)
        val chapter = checkNotNull(state.chapter)
        assertEquals(revision, location.progressRevision); assertEquals(revision, state.progressToken?.revision)
        assertEquals(chapter.manifest.chapter, location.chapter); assertEquals(chapter.manifest.manifestVersion, location.manifestVersion)
        assertEquals(anchor.pageId, location.pageId); assertEquals(anchor.ordinal, location.pageOrdinalFallback)
        assertEquals(anchor.x, location.x, 0.0); assertEquals(anchor.y, location.y, 0.0)
        val originals = ownedOriginals(sourceId, baselineNames)
        assertEquals(revision.toInt(), originals.size)
        assertEquals((1L..revision).toList(), originals.map { it.location.progressRevision })
        val original = originals.last()
        assertEquals(location, original.location)
        assertEquals(revision - 1, JSONObject(original.record.getString("parametersJson")).getLong("expectedRevision"))
        return original
    }

    private data class Original(val file: File, val raw: ByteArray, val record: JSONObject, val location: ReaderMangaLocation)
    private fun ownedOriginals(sourceId: String, baselineNames: Set<String>): List<Original> {
        val directory = File(files, "Reader/Operations/Completed")
        val candidates = directory.listFiles().orEmpty().filter {
            it.name.startsWith("android-manga-progress-") && it.name !in baselineNames
        }
        assertFalse("Never promote an uncommitted progress candidate", candidates.any { it.name.endsWith(".new") || it.name.endsWith(".bak") })
        return candidates.filter { it.extension == "json" }.map { file ->
            assertTrue(file.isFile && file.length() in 1..8L * 1024 * 1024)
            assertFalse(File(directory, file.name + ".new").exists()); assertFalse(File(directory, file.name + ".bak").exists())
            val raw = file.readBytes()
            val record = ReaderMangaProgressPolicy.decodeRecord(raw)
            assertEquals(file.name, record.getString("id") + ".json")
            assertEquals("No foreign progress may be attributed to this journey", sourceId, record.getString("sourceId"))
            assertTrue(record.getBoolean("completed") && record.getBoolean("transportConfirmed"))
            val ack = ReaderMangaProgressPolicy.acknowledgement(record, record.getJSONObject("wireTerminal"))
            Original(file, raw, record, checkNotNull(ack.location))
        }.sortedBy { it.location.progressRevision }
    }
    private fun assertOriginalUnchanged(original: Original) {
        assertFalse(File(original.file.parentFile, original.file.name + ".new").exists())
        assertFalse(File(original.file.parentFile, original.file.name + ".bak").exists())
        assertArrayEquals(original.raw, original.file.readBytes())
    }
    private fun originalFields(original: Original) = Bundle().apply {
        putString("sourceId", original.location.chapter.sourceId); putString("operationId", original.record.getString("id"))
        putString("originalRecordSha256", sha(original.raw)); putInt("originalRecordBytes", original.raw.size)
        putString("chapterId", original.location.chapter.chapterId); putString("pageId", original.location.pageId)
        putInt("ordinal", original.location.pageOrdinalFallback); putDouble("x", original.location.x); putDouble("y", original.location.y)
        putLong("revision", original.location.progressRevision)
        putString("locationJson", ReaderMangaProgressPolicy.locationJson(original.location).toString())
    }
    private fun originalMarker(step: String, original: Original, state: ReaderMangaReadingUiState) =
        marker(step, originalFields(original).apply {
            putInt("chapterIndex", checkNotNull(state.chapter).chapterIndex); putLong("layoutEpoch", checkNotNull(state.layoutTicket).epoch)
            putString("ackScope", "EXACT_COMPLETED_TRANSPORT_CONFIRMED_ORIGINAL_MATCHES_ACTUAL_DRAWN_ANCHOR")
        })

    private enum class Hue { RED, BLUE, GREEN, PURPLE }
    private fun capture(runId: String, label: String, ordinal: Int, hue: Hue) {
        val image = compose.onNodeWithTag("reader_manga_page_" + ordinal).captureToImage()
        assertTrue("Actual page pixels must be visible", image.width > 64 && image.height > 64)
        val pixels = image.toPixelMap(); var colored = 0; var sampled = 0
        for (y in 0 until image.height step maxOf(1, image.height / 24))
            for (x in 0 until image.width step maxOf(1, image.width / 24)) {
                val p = pixels[x,y]
                if (when (hue) {
                    Hue.RED -> p.red > .65f && p.red > p.green * 1.4f && p.red > p.blue * 1.4f
                    Hue.BLUE -> p.blue > .65f && p.blue > p.green * 1.4f && p.blue > p.red * 1.4f
                    Hue.GREEN -> p.green > .5f && p.green > p.red * 1.4f && p.green > p.blue * 1.4f
                    Hue.PURPLE -> p.red > .4f && p.blue > .6f && p.red > p.green * 1.6f && p.blue > p.green * 1.6f
                }) colored++
                sampled++
            }
        assertTrue("Expected chapter pixels, not just decoded owner bytes", colored * 5 > sampled)
        val relative = "reader-manga-chapter/" + runId + "/" + label + ".png"
        val file = File(instrumentation.targetContext.cacheDir, relative)
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
        marker("chapter-pixels-" + label, Bundle().apply {
            putString("imagePath", relative); putString("imageSha256", sha(file.readBytes()))
            putInt("ordinal", ordinal); putInt("width", image.width); putInt("height", image.height); putString("hue", hue.name)
        })
    }
    private fun captureDirectoryScreen(runId: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Actual display capture unavailable" }
        try {
            assertTrue(bitmap.width > 300 && bitmap.height > 600)
            val relative = "reader-manga-chapter/" + runId + "/directory-panel-screen.png"
            val file = File(instrumentation.targetContext.cacheDir, relative)
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            marker("chapter-pixels-directory-panel-screen", Bundle().apply {
                putString("imagePath", relative); putString("imageSha256", sha(file.readBytes()))
                putInt("width", bitmap.width); putInt("height", bitmap.height); putString("hue", "FULL_SCREEN")
            })
        } finally { bitmap.recycle() }
    }
    private fun operationSnapshot(): Map<String, Pair<Long, String>> {
        val root = File(files, "Reader/Operations")
        assertTrue("An active original remains", root.listFiles().orEmpty().none { it.isFile })
        val completed = File(root, "Completed").listFiles().orEmpty().filter { it.isFile }
        assertFalse("A committed operation is required", completed.any { it.name.endsWith(".new") || it.name.endsWith(".bak") })
        return completed.associate { file ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            file.name to (file.length() to digest.digest().joinToString("") { "%02x".format(it) })
        }
    }
    private fun snapshotSha(snapshot: Map<String, Pair<Long, String>>) = sha(snapshot.toSortedMap().entries.joinToString("\n") {
        it.key + ":" + it.value.first + ":" + it.value.second
    }.toByteArray())
    private fun healthy(vm: ReaderViewModel) {
        val state = vm.state.value
        assertTrue(state.ready && state.pendingReady); assertFalse(state.busy)
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
    }
    private fun onlineIdle(vm: ReaderViewModel) {
        compose.waitUntil(25_000) { !vm.state.value.busy && !vm.onlineSources.state.value.busy }
        healthy(vm); assertNull(vm.onlineSources.state.value.error); assertFalse(vm.onlineSources.state.value.pending)
    }
    private fun clickWorkspace(tag: String) {
        compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("reader_online_workspace"))).performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }
    private fun scrollPosition() = compose.onNodeWithTag("reader_manga_pages")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun finish(scenario: ActivityScenario<MainActivity>?, activity: MainActivity?) {
        val owned = scenario ?: return; val current = activity ?: return
        marker("chapter-activity-finish-enter")
        if (owned.state != Lifecycle.State.DESTROYED) {
            assertEquals(ReaderRoute.Bookshelf, current.readerViewModel.state.value.route)
            val destroyed = CountDownLatch(1)
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
            try {
                instrumentation.runOnMainSync { current.lifecycle.addObserver(observer); current.finish() }
                assertTrue(destroyed.await(5, TimeUnit.SECONDS))
                compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
            } finally { instrumentation.runOnMainSync { current.lifecycle.removeObserver(observer) } }
        }
        marker("chapter-activity-destroyed"); owned.close(); marker("chapter-scenario-closed")
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step); extras.putString("readerMethod", "realDirectoryAndAdjacentChaptersSaveOnlyDrawnAnchors")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, extras)
    }
}
