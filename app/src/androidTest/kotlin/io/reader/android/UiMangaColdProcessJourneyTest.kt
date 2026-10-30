package io.reader.android

import android.app.KeyguardManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.PowerManager
import android.os.Process as AndroidProcess
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
import io.reader.android.runtime.AppCoreOwner
import io.reader.android.runtime.ReaderCoreEventActor
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderMangaLocation
import io.reader.android.runtime.ReaderMangaProgressPolicy
import io.reader.android.runtime.ReaderMangaProgressState
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
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Two separately selected methods. The host-owned HTTP fixture outlives the first App process.
 * A real source import/scroll/settled original precedes cold reopening; no zoom or other-format replay. */
@RunWith(AndroidJUnit4::class)
class UiMangaColdProcessJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private var method = "UNSELECTED"
    private var phase = "input"
    private val files get() = instrumentation.targetContext.filesDir
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(120, TimeUnit.SECONDS).withLookingForStuckThread(true).build()).around(compose)

    @Test fun seedOwnedMangaScrollsAndExitsWithAcknowledgedProgress() {
        method = SEED_METHOD
        journey {
            val input = input()
            val retained = retainedFiles()
            assertNoPendingOperations()
            phase = "one-owned-source-seed"
            marker("manga-cold-seed-client-enter")
            ReaderLibrary(ApplicationProvider.getApplicationContext()).use { library ->
                assertTrue(runBlocking { library.sources(input.sourceId) }.isEmpty())
                val result = runBlocking { library.importSourceJson(input.sourceId, input.sourceJson) }
                assertTrue(result.durable && !result.candidateRetained)
                assertEquals(SourceImportConfirmation.APPLIED, result.confirmation)
                marker("manga-cold-owned-source-original-ACK", Bundle().apply {
                    input.fields(this); putString("sourceOperationId", result.operationId)
                    putString("sourceCoreOperationId", result.coreOperationId); putLong("sourceSequence", result.sequence)
                })
            }
            marker("manga-cold-seed-client-retired")
            val model = launchShelf()
            val books = model.state.value.books.toList(); val remote = model.state.value.remoteBooks.toList()
            openOwnedManga(model, input)
            val initial = model.mangaReadingState.value
            assertEquals(2, initial.pages.size); assertEquals(1f, initial.zoom)
            assertFalse(initial.progressDirty || initial.progressBusy)
            assertNull(initial.lastConfirmedProgress)
            compose.onNodeWithTag("reader_manga_page_0").assertIsDisplayed()
            phase = "one-real-scroll-at-default-zoom"
            val before = scrollPosition()
            swipeUp()
            compose.waitUntil(3_000) { scrollPosition() > before || model.mangaReadingState.value.phase == ReaderMangaReadingPhase.ERROR }
            assertTrue(scrollPosition() > before)
            if (model.mangaReadingState.value.currentOrdinal == 0) swipeUp()
            compose.waitUntil(5_000) { model.mangaReadingState.value.let {
                (it.currentOrdinal == 1 && it.visibleAnchor != null && it.restoreRequest == null) || it.phase == ReaderMangaReadingPhase.ERROR
            } }
            val visible = checkNotNull(model.mangaReadingState.value.visibleAnchor)
            assertEquals(1, visible.ordinal)
            compose.onNodeWithTag("reader_manga_page_1").assertIsDisplayed()
            marker("manga-cold-real-scroll-observed", Bundle().apply {
                putString("pageId", visible.pageId); putDouble("x", visible.x); putDouble("y", visible.y)
                putFloat("nativeScrollPosition", scrollPosition())
            })
            // Exit freezes this actual measurement. An already admitted autosave may supply the final ACK.
            val identity = currentNativeIdentity(model)
            val chapter = checkNotNull(model.mangaReadingState.value.chapter)
            phase = "one-normal-exit-with-settled-original"
            exitManga(model)
            val originals = progressRecords(input.sourceId)
            assertTrue(originals.size in 1..16)
            val final = originals.maxBy { it.ack.token.revision }
            val location = checkNotNull(final.ack.location)
            assertEquals(visible.pageId, location.pageId); assertEquals(visible.ordinal, location.pageOrdinalFallback)
            assertEquals(visible.x, location.x, 0.000001); assertEquals(visible.y, location.y, 0.000001)
            assertEquals(chapter.manifest.chapter, location.chapter); assertEquals(chapter.manifest.manifestVersion, location.manifestVersion)
            assertEquals(identity.runtime, final.record.getString("runtimeSession"))
            assertEquals(identity.client, final.record.getString("clientIdentity"))
            val source = model.onlineSources.state.value.sources.single { it.sourceId == input.sourceId }
            marker("manga-cold-seed-normal-exit-settled-original-ACK", Bundle().apply {
                input.fields(this); identity.fields(this)
                putString("sourceVersion", source.sourceVersion); putString("bookId", location.chapter.bookId)
                putString("chapterId", location.chapter.chapterId); putInt("chapterIndex", chapter.chapterIndex)
                putString("manifestVersion", chapter.manifest.manifestVersion)
                putString("sourceRuleVersion", chapter.manifest.sourceRuleVersion); putString("decodeRevision", chapter.manifest.decodeRevision)
                putString("originalId", final.record.getString("id")); putString("originalRecordSha256", sha(final.raw))
                putLong("originalRecordBytes", final.raw.size.toLong())
                putString("locationJson", ReaderMangaProgressPolicy.locationJson(location).toString())
                putString("tokenJson", JSONObject().put("epoch", final.ack.token.epoch).put("revision", final.ack.token.revision).toString())
                putString("ownedProgressFilesJson", progressFiles(originals).toString())
                putString("ackScope", "ACTUAL_FINAL_ORIGINAL_ACK_AND_NORMAL_EXIT_SETTLED_AUTOSAVE_OR_EXIT_FLUSH_NO_GET_AS_ACK")
            })
            assertEquals(books, model.state.value.books); assertEquals(remote, model.state.value.remoteBooks)
            finishBookshelf(model)
            assertNoPendingOperations(); assertRetainedFiles(retained)
            marker("manga-cold-seed-ready-for-controller-cold")
        }
    }

    @Test fun coldRestoresOwnedMangaOriginalAnchorWithoutWrite() {
        method = RESTORE_METHOD
        journey {
            val input = input()
            val previousPid = required("previousProcessPid").toInt().also { assertTrue(it > 0) }
            val previousStart = required("previousProcessStartElapsedMs").toLong().also { assertTrue(it > 0) }
            val previousRuntime = required("previousRuntimeSession")
            val previousClient = required("previousClientIdentity")
            assertTrue(AndroidProcess.getStartElapsedRealtime() > previousStart)
            assertFalse(AndroidProcess.myPid() == previousPid && AndroidProcess.getStartElapsedRealtime() == previousStart)
            val originalId = required("originalId").also { assertTrue(ReaderMangaProgressPolicy.id.matches(it)) }
            val original = safeReadCompleted(File(files, "Reader/Operations/Completed/$originalId.json"))
            assertEquals(required("originalSha256"), sha(original.raw)); assertEquals(required("originalBytes").toLong(), original.raw.size.toLong())
            assertEquals(previousRuntime, original.record.getString("runtimeSession")); assertEquals(previousClient, original.record.getString("clientIdentity"))
            val expected = checkNotNull(original.ack.location)
            assertEquals(input.sourceId, expected.chapter.sourceId)
            assertEquals(expected, ReaderMangaProgressPolicy.location(JSONObject(required("expectedLocationJson"))))
            val expectedToken = JSONObject(required("expectedTokenJson"))
            assertEquals(expectedToken.getLong("epoch"), original.ack.token.epoch); assertEquals(expectedToken.getLong("revision"), original.ack.token.revision)
            val expectedFiles = JSONObject(required("expectedOwnedProgressFilesJson"))
            assertProgressFiles(expectedFiles, progressRecords(input.sourceId))
            val retained = retainedFiles()
            marker("manga-cold-process-and-original-precondition-bound", Bundle().apply {
                input.fields(this); putInt("previousProcessPid", previousPid); putLong("previousProcessStartElapsedMs", previousStart)
                putString("originalId", originalId); putString("originalRecordSha256", sha(original.raw))
                putString("baselineProofSha256", required("baselineProofSha256"))
                putString("previousRuntimeSession", previousRuntime); putString("previousClientIdentity", previousClient)
            })
            val model = launchShelf()
            val books = model.state.value.books.toList(); val remote = model.state.value.remoteBooks.toList()
            openOwnedManga(model, input)
            val state = model.mangaReadingState.value
            val identity = currentNativeIdentity(model)
            assertNotEquals(previousRuntime, identity.runtime); assertNotEquals(previousClient, identity.client)
            val source = model.onlineSources.state.value.sources.single { it.sourceId == input.sourceId }
            assertEquals(required("expectedSourceVersion"), source.sourceVersion)
            val chapter = checkNotNull(state.chapter)
            assertEquals(expected.chapter, chapter.manifest.chapter); assertEquals(required("expectedChapterIndex").toInt(), chapter.chapterIndex)
            assertEquals(expected.manifestVersion, chapter.manifest.manifestVersion)
            assertEquals(required("expectedSourceRuleVersion"), chapter.manifest.sourceRuleVersion)
            assertEquals(required("expectedDecodeRevision"), chapter.manifest.decodeRevision)
            val anchor = checkNotNull(state.visibleAnchor)
            assertEquals(expected.pageId, anchor.pageId); assertEquals(expected.pageOrdinalFallback, anchor.ordinal)
            assertEquals(expected.x, anchor.x, 0.002); assertEquals(expected.y, anchor.y, 0.002)
            // The immutable original authenticates its old ACK token. SQLite reopening advances
            // the runtime CAS epoch while the stored location/revision stays unchanged.
            val currentToken = checkNotNull(state.progressToken)
            assertTrue("Current SQLite CAS epoch must advance after backend reopen", currentToken.epoch > original.ack.token.epoch)
            assertEquals("Stored progress revision must survive backend reopen", original.ack.token.revision, currentToken.revision)
            assertNull(state.lastConfirmedProgress) // Opening observation never becomes a save acknowledgement.
            assertFalse(state.progressDirty || state.progressBusy); assertNull(state.progressError)
            assertTrue(state.progressPending.isEmpty()); assertNull(state.restoreRequest)
            compose.onNodeWithTag("reader_manga_page_${expected.pageOrdinalFallback}").assertIsDisplayed()
            captureBluePage(input.runId, expected.pageOrdinalFallback)
            assertProgressFiles(expectedFiles, progressRecords(input.sourceId))
            marker("manga-cold-native-draw-restored-without-new-progress", Bundle().apply {
                input.fields(this); identity.fields(this)
                putString("sourceVersion", source.sourceVersion); putString("originalId", originalId)
                putString("originalRecordSha256", sha(original.raw))
                putString("chapterId", chapter.manifest.chapter.chapterId); putString("bookId", chapter.manifest.chapter.bookId)
                putInt("chapterIndex", chapter.chapterIndex); putString("manifestVersion", chapter.manifest.manifestVersion)
                putString("sourceRuleVersion", chapter.manifest.sourceRuleVersion); putString("decodeRevision", chapter.manifest.decodeRevision)
                putString("pageId", anchor.pageId); putInt("pageOrdinal", anchor.ordinal); putDouble("x", anchor.x); putDouble("y", anchor.y)
                putLong("tokenEpoch", currentToken.epoch); putLong("tokenRevision", currentToken.revision)
                putLong("originalAckEpoch", original.ack.token.epoch); putLong("originalAckRevision", original.ack.token.revision)
                putLong("layoutEpoch", checkNotNull(state.layoutTicket).epoch)
                putBoolean("lastConfirmedProgressAbsent", true); putBoolean("noNewProgressOriginal", true)
                putString("restoreClaim", "PRODUCTION_DRAW_AND_IMAGE_MEASUREMENT_PLUS_CAPTURE_NOT_INDEPENDENT_COORDINATE_EXPORT")
            })
            phase = "one-no-change-exit"
            exitManga(model)
            assertProgressFiles(expectedFiles, progressRecords(input.sourceId))
            assertEquals(books, model.state.value.books); assertEquals(remote, model.state.value.remoteBooks)
            marker("manga-cold-normal-exit-no-new-progress")
            finishBookshelf(model)
            phase = "owned-source-only-cleanup"
            marker("manga-cold-cleanup-client-enter")
            ReaderLibrary(ApplicationProvider.getApplicationContext()).use { library ->
                marker("manga-cold-cleanup-client-created")
                val observed = runBlocking { library.sources(input.sourceId) }.single()
                assertEquals(required("expectedSourceVersion"), observed.sourceVersion)
                val deleted = runBlocking { library.deleteSource(input.sourceId, observed.sourceVersion) }
                assertTrue(deleted.durable && !deleted.candidateRetained)
                assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
                marker("manga-cold-owned-source-cleanup-ACK", Bundle().apply { putString("sourceId", input.sourceId); putString("operationId", deleted.operationId) })
            }
            marker("manga-cold-cleanup-client-retired")
            assertProgressFiles(expectedFiles, progressRecords(input.sourceId)); assertRetainedFiles(retained)
        }
    }

    private data class Input(val runId: String, val sourceId: String, val base: String, val sourceJson: String,
        val sourceSha: String, val fixtureSha: String) {
        fun fields(bundle: Bundle) {
            bundle.putString("runId", runId); bundle.putString("sourceId", sourceId); bundle.putString("fixtureBase", base)
            bundle.putString("fixtureSourceSha256", sourceSha); bundle.putString("fixtureManifestSha256", fixtureSha)
        }
    }
    private fun required(name: String) = InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }
        ?: throw AssertionError("Required actual controller input missing: $name")
    private fun input(): Input {
        val runId = required("runId").also { assertEquals(it, UUID.fromString(it).toString()) }
        val sourceId = required("sourceId"); assertEquals("android-manga-cold-$runId", sourceId)
        val base = required("fixtureBase"); val uri = URI(base)
        assertEquals("http", uri.scheme); assertEquals("127.0.0.1", uri.host); assertTrue(uri.port in 1024..65535)
        assertEquals("/run/$runId", uri.path); assertNull(uri.query); assertNull(uri.fragment); assertNull(uri.userInfo)
        val sourceJson = required("fixtureSourceJson"); val sourceSha = required("fixtureSourceSha256")
        assertTrue(sourceJson.toByteArray().size in 1..65536); assertEquals(sourceSha, sha(sourceJson.toByteArray(Charsets.UTF_8)))
        assertEquals(base, JSONObject(sourceJson).getString("bookSourceUrl"))
        val fixtureSha = required("fixtureManifestSha256"); assertTrue(fixtureSha.matches(Regex("[a-f0-9]{64}")))
        return Input(runId, sourceId, base, sourceJson, sourceSha, fixtureSha)
    }
    private fun journey(body: () -> Unit) {
        marker("method-enter")
        try { body(); marker("method-exit") }
        catch (failure: Throwable) {
            marker("method-failure", Bundle().apply { putString("phase", phase); putString("failureClass", failure.javaClass.name); putString("message", failure.message?.take(512)) })
            marker("activity-retained-for-controller") // No extra business cleanup or source deletion on failure.
            throw failure
        }
    }
    private fun launchShelf(): ReaderViewModel {
        phase = "main-activity"
        scenario = ActivityScenario.launch(MainActivity::class.java); scenario!!.onActivity { activity = it }
        val model = activity.readerViewModel
        compose.waitUntil(20_000) { !model.state.value.busy && (model.state.value.ready || model.state.value.error != null || model.state.value.protocolRecoveryReference != null) }
        healthy(model); assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
        assertNoPendingOperations()
        val power = checkNotNull(activity.getSystemService(PowerManager::class.java))
        val keyguard = checkNotNull(activity.getSystemService(KeyguardManager::class.java))
        compose.waitUntil(5_000) {
            power.isInteractive && !keyguard.isKeyguardLocked &&
                compose.runOnUiThread { activity.window.decorView.hasWindowFocus() }
        }
        val physical = compose.runOnUiThread {
            Triple(power.isInteractive, keyguard.isKeyguardLocked, activity.window.decorView.hasWindowFocus())
        }
        assertTrue("Physical screen must already be interactive", physical.first)
        assertFalse("Physical screen must already be unlocked", physical.second)
        assertTrue("Current Activity window must already have focus", physical.third)
        marker("manga-cold-physical-interaction-preflight", Bundle().apply {
            putBoolean("screenInteractive", physical.first); putBoolean("keyguardLocked", physical.second)
            putBoolean("windowFocused", physical.third)
        })
        return model
    }
    private fun openOwnedManga(model: ReaderViewModel, input: Input) {
        phase = "owned-source-search-detail"
        compose.onNodeWithTag("reader_online_sources").assertIsEnabled().performClick(); onlineIdle(model)
        val source = model.onlineSources.state.value.sources.single { it.sourceId == input.sourceId }
        assertEquals(input.base, source.baseUrl)
        val index = model.onlineSources.state.value.sources.indexOf(source)
        clickWorkspace("reader_source_select_$index"); onlineIdle(model)
        compose.onNodeWithTag("reader_online_query").performTextInput("Comic")
        scenario!!.onActivity { it.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.window.decorView.windowToken, 0) }
        clickWorkspace("reader_online_search"); onlineIdle(model)
        assertEquals(1, model.onlineSources.state.value.books.size)
        assertEquals(input.sourceId, model.onlineSources.state.value.books.single().key.sourceId)
        clickWorkspace("reader_online_detail_0"); onlineIdle(model)
        assertEquals(ReaderOnlineContentKind.MANGA, model.onlineSources.state.value.detail?.contentKind)
        phase = "actual-manga-native-draw"
        clickWorkspace("reader_online_manga_read")
        compose.waitUntil(45_000) { model.mangaReadingState.value.let {
            (it.phase == ReaderMangaReadingPhase.READY && it.restoreRequest == null && it.visibleAnchor != null) ||
                it.phase == ReaderMangaReadingPhase.ERROR || model.state.value.protocolRecoveryReference != null || model.state.value.onlineError != null
        } }
        healthy(model)
        val state = model.mangaReadingState.value
        assertEquals("Manga entry: ${model.state.value.onlineError}", ReaderRoute.MangaReading, model.state.value.route)
        assertEquals("Manga owner: ${state.error}", ReaderMangaReadingPhase.READY, state.phase)
        assertEquals(input.sourceId, state.chapter?.manifest?.chapter?.sourceId)
        assertNotNull(state.layoutTicket); assertNull(state.restoreBlocked); assertNull(state.progressError)
        compose.onNodeWithTag("reader_manga_reading").assertIsDisplayed()
    }
    private fun exitManga(model: ReaderViewModel) {
        compose.onNodeWithTag("reader_manga_exit").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model.state.value.route == ReaderRoute.Bookshelf && model.mangaReadingState.value.phase == ReaderMangaReadingPhase.IDLE }
        healthy(model); assertTrue(model.mangaReadingState.value.pages.isEmpty()); assertNoPendingOperations()
    }
    private data class Identity(val runtime: String, val client: String) {
        fun fields(bundle: Bundle) { bundle.putString("runtimeSession", runtime); bundle.putString("clientIdentity", client) }
    }
    /** Read the existing issued owner/client only; never create a Library, Native runtime or capability. */
    private fun currentNativeIdentity(model: ReaderViewModel): Identity {
        val libraryLazy = field(model, "libraryDelegate") as Lazy<*>
        assertTrue(libraryLazy.isInitialized())
        val client = field(checkNotNull(libraryLazy.value), "client") as AppCoreOwner.Client
        val actorLazy = field(client.owner, "actorDelegate") as Lazy<*>
        assertTrue(actorLazy.isInitialized())
        val actor = actorLazy.value as ReaderCoreEventActor
        return Identity(actor.runtimeSession, client.lease.toString())
    }
    private data class Original(val file: File, val record: JSONObject, val raw: ByteArray, val ack: ReaderMangaProgressState)
    private data class Completed(val file: File, val record: JSONObject, val raw: ByteArray)
    private fun safeReadCompletedRaw(file: File): Completed {
        assertTrue(file.isFile && file.length() in 1..8L * 1024 * 1024)
        assertFalse(File(file.parentFile, file.name + ".new").exists()); assertFalse(File(file.parentFile, file.name + ".bak").exists())
        val raw = file.readBytes(); val record = JSONObject(raw.toString(Charsets.UTF_8))
        ReaderMangaProgressPolicy.record(record)
        assertEquals(record.getString("id") + ".json", file.name)
        return Completed(file, record, raw)
    }
    private fun successfulOriginal(completed: Completed): Original {
        val (file, record, raw) = completed
        assertTrue(record.getBoolean("completed") && record.getBoolean("transportConfirmed"))
        return Original(file, record, raw, ReaderMangaProgressPolicy.acknowledgement(record, record.getJSONObject("wireTerminal")))
    }
    private fun safeReadCompleted(file: File) = successfulOriginal(safeReadCompletedRaw(file))
    private fun progressRecords(sourceId: String): List<Original> {
        assertNoPendingOperations()
        return File(files, "Reader/Operations/Completed").listFiles().orEmpty()
            .filter { it.isFile && ReaderMangaProgressPolicy.id.matches(it.name.removeSuffix(".json")) && it.extension == "json" }
            .map(::safeReadCompletedRaw).filter { it.record.getString("sourceId") == sourceId }.map(::successfulOriginal)
    }
    private fun progressFiles(records: List<Original>) = JSONObject().apply {
        records.sortedBy { it.file.name }.forEach { put(it.file.name, JSONObject().put("bytes", it.raw.size).put("sha256", sha(it.raw))) }
    }
    private fun assertProgressFiles(expected: JSONObject, actual: List<Original>) {
        assertEquals(expected.keys().asSequence().toSet(), actual.map { it.file.name }.toSet())
        actual.forEach { record ->
            val identity = expected.getJSONObject(record.file.name)
            assertEquals(identity.getLong("bytes"), record.raw.size.toLong()); assertEquals(identity.getString("sha256"), sha(record.raw))
        }
    }
    private fun assertNoPendingOperations() {
        val root = File(files, "Reader/Operations")
        assertTrue("An actual active original remains", root.listFiles().orEmpty().none { it.isFile })
        assertTrue("Never promote a Completed candidate", File(root, "Completed").listFiles().orEmpty().none { it.name.endsWith(".new") || it.name.endsWith(".bak") })
    }
    private fun retainedFiles(): Map<String, Pair<Long, String>> {
        val reader = File(files, "Reader")
        return listOf("Operations", "Originals").flatMap { name ->
            File(reader, name).walkTopDown().filter { it.isFile }.map { file -> file.relativeTo(reader).invariantSeparatorsPath to (file.length() to shaFile(file)) }.toList()
        }.toMap()
    }
    private fun assertRetainedFiles(expected: Map<String, Pair<Long, String>>) {
        expected.forEach { (path, identity) ->
            val file = File(files, "Reader/$path"); assertTrue("Retained file absent: $path", file.isFile)
            assertEquals("Retained file size changed: $path", identity.first, file.length()); assertEquals("Retained file bytes changed: $path", identity.second, shaFile(file))
        }
    }
    private fun finishBookshelf(model: ReaderViewModel) {
        phase = "bounded-bookshelf-finish"
        assertEquals(ReaderRoute.Bookshelf, model.state.value.route); assertNull(model.state.value.reading)
        assertEquals(ReaderMangaReadingPhase.IDLE, model.mangaReadingState.value.phase); assertNoPendingOperations()
        val owned = checkNotNull(scenario); val destroyed = CountDownLatch(1)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) destroyed.countDown() }
        marker("manga-cold-manual-finish-enter")
        try {
            instrumentation.runOnMainSync { activity.lifecycle.addObserver(observer); activity.finish() }
            assertTrue("MainActivity destroy did not complete", destroyed.await(5, TimeUnit.SECONDS))
            compose.waitUntil(5_000) { owned.state == Lifecycle.State.DESTROYED }
        } finally { instrumentation.runOnMainSync { activity.lifecycle.removeObserver(observer) } }
        marker("manga-cold-activity-destroyed")
        assertEquals(Lifecycle.State.DESTROYED, owned.state)
        marker("manga-cold-scenario-close-enter"); owned.close(); marker("manga-cold-scenario-close-returned")
    }
    private fun healthy(model: ReaderViewModel) {
        val state = model.state.value
        assertTrue(state.ready && state.pendingReady); assertFalse(state.busy)
        assertNull(state.error); assertNull(state.protocolRecoveryReference); assertNull(state.pendingSearchClose)
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
    private fun scrollPosition() = compose.onNodeWithTag("reader_manga_pages").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun swipeUp() = compose.onNodeWithTag("reader_manga_pages").performTouchInput {
        swipeUp(startY = height * 0.85f, endY = height * 0.2f, durationMillis = 400)
    }
    private fun captureBluePage(runId: String, ordinal: Int) {
        val image = compose.onNodeWithTag("reader_manga_page_$ordinal").captureToImage()
        assertTrue(image.width > 64 && image.height > 64)
        val pixels = image.toPixelMap(); var blue = 0; var sampled = 0
        for (y in 0 until image.height step maxOf(1, image.height / 24)) for (x in 0 until image.width step maxOf(1, image.width / 24)) {
            val color = pixels[x, y]; if (color.blue > 0.65f && color.blue > color.green * 1.4f && color.blue > color.red * 1.4f) blue++; sampled++
        }
        assertTrue("Actual Compose image colors were not drawn", blue * 5 > sampled)
        val relative = "reader-manga-cold/$runId/restored-page-$ordinal.png"; val file = File(instrumentation.targetContext.cacheDir, relative)
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
        marker("manga-cold-restored-page-captured", Bundle().apply {
            putString("relativeCachePath", relative); putString("sha256", shaFile(file)); putLong("bytes", file.length())
            putInt("width", image.width); putInt("height", image.height); putInt("ordinal", ordinal)
        })
    }
    private fun field(owner: Any, name: String): Any = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun shaFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun marker(step: String, details: Bundle = Bundle()) {
        details.putString("readerStep", step); details.putString("readerMethod", method)
        details.putInt("readerProcessPid", AndroidProcess.myPid()); details.putLong("readerProcessStartElapsedMs", AndroidProcess.getStartElapsedRealtime())
        details.putLong("readerElapsedMs", SystemClock.elapsedRealtime()); instrumentation.sendStatus(2, details)
    }
    companion object {
        private const val SEED_METHOD = "seedOwnedMangaScrollsAndExitsWithAcknowledgedProgress"
        private const val RESTORE_METHOD = "coldRestoresOwnedMangaOriginalAnchorWithoutWrite"
    }
}
