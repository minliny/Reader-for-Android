package io.reader.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.ReaderOnlineContentKind
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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Actual MainActivity/Compose/Library journey over an owned loopback source.
 * The source is seeded through the real Library; SAF import, remote-source
 * compatibility, progress writes and cold recovery are outside this method.
 */
@RunWith(AndroidJUnit4::class)
class UiMangaComposeJourneyTest {
    private val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val phaseMarker = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            marker("rule-enter")
            try { base.evaluate() } finally { marker("rule-exit") }
        }
    } }
    @get:Rule val rules: TestRule = RuleChain.outerRule(
        Timeout.builder().withTimeout(180, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    ).around(phaseMarker).around(compose)

    @Test fun fixtureSourceOpensMangaScrollsZoomsAndExits() {
        marker("method-enter")
        val runId = UUID.randomUUID().toString()
        val sourceId = "android-manga-compose-$runId"
        val fixture = Fixture()
        val library = ReaderLibrary(ApplicationProvider.getApplicationContext())
        var scenario: ActivityScenario<MainActivity>? = null
        var phase = "source-seed"
        var failure: Throwable? = null
        var complete = false
        try {
            runBlocking {
                val imported = library.importSourceJson(sourceId, fixture.sourceJson())
                assertTrue(imported.durable)
                assertEquals(SourceImportConfirmation.APPLIED, imported.confirmation)
            }
            marker("owned-source-original-ACK", Bundle().apply { putString("sourceId", sourceId) })
            phase = "main-activity"
            lateinit var activity: MainActivity
            val launched = ActivityScenario.launch(MainActivity::class.java)
            scenario = launched
            launched.onActivity { activity = it }
            val model = activity.readerViewModel
            compose.waitUntil(20_000) {
                !model.state.value.busy && (model.state.value.ready || model.state.value.error != null ||
                    model.state.value.protocolRecoveryReference != null)
            }
            healthy(model)
            assertEquals(ReaderRoute.Bookshelf, model.state.value.route)
            val shelfBefore = model.state.value.books.toList()
            val remoteBefore = model.state.value.remoteBooks.toList()

            phase = "source-selection"
            compose.onNodeWithTag("reader_online_sources").assertIsEnabled().performClick()
            onlineIdle(model)
            val workspace = model.onlineSources
            val sourceIndex = workspace.state.value.sources.indexOfFirst { it.sourceId == sourceId }
            assertTrue("Owned source is absent from the actual source list", sourceIndex >= 0)
            clickWorkspace("reader_source_select_$sourceIndex")
            onlineIdle(model)
            assertEquals(sourceId, workspace.state.value.selection?.sourceId)

            phase = "search-and-detail"
            compose.onNodeWithTag("reader_online_query").assertIsEnabled().performTextInput("Comic")
            launched.onActivity { current ->
                current.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(current.window.decorView.windowToken, 0)
            }
            clickWorkspace("reader_online_search")
            onlineIdle(model)
            assertEquals(1, workspace.state.value.books.size)
            assertEquals(sourceId, workspace.state.value.books.single().key.sourceId)
            clickWorkspace("reader_online_detail_0")
            onlineIdle(model)
            assertEquals(ReaderOnlineContentKind.MANGA, workspace.state.value.detail?.contentKind)
            marker("manga-detail-visible")

            phase = "native-manga-entry"
            clickWorkspace("reader_online_manga_read")
            compose.waitUntil(45_000) {
                val manga = model.mangaReadingState.value
                manga.phase == ReaderMangaReadingPhase.READY || manga.phase == ReaderMangaReadingPhase.ERROR ||
                    (!model.state.value.busy && model.state.value.onlineError != null) ||
                    model.state.value.protocolRecoveryReference != null
            }
            healthy(model)
            assertEquals("Manga entry: ${model.state.value.onlineError}", ReaderRoute.MangaReading, model.state.value.route)
            val loaded = model.mangaReadingState.value
            assertEquals("Manga owner: ${loaded.error}", ReaderMangaReadingPhase.READY, loaded.phase)
            assertEquals(2, loaded.pages.size)
            assertEquals(sourceId, loaded.chapter?.manifest?.chapter?.sourceId)
            assertEquals(1f, loaded.zoom)
            compose.onNodeWithTag("reader_manga_reading").assertIsDisplayed()
            compose.onNodeWithTag("reader_manga_page_0").assertIsDisplayed()
            capturePage(runId, 0, red = true)
            marker("manga-first-page-visible")

            phase = "native-vertical-scroll"
            val initialScroll = scrollPosition()
            swipePageUp()
            compose.waitUntil(3_000) { scrollPosition() > initialScroll || model.mangaReadingState.value.phase == ReaderMangaReadingPhase.ERROR }
            assertTrue("At zoom 1, a vertical drag on the image must move the real LazyColumn", scrollPosition() > initialScroll)
            // Two measured upward drags at most, not a retry/reload or semantic scroll shortcut.
            if (model.mangaReadingState.value.currentOrdinal == 0) swipePageUp()
            compose.waitUntil(3_000) { model.mangaReadingState.value.currentOrdinal == 1 }
            assertEquals(1, model.mangaReadingState.value.currentOrdinal)
            compose.onNodeWithTag("reader_manga_page_1").assertIsDisplayed()
            capturePage(runId, 1, red = false)
            marker("manga-second-page-scrolled-visible", Bundle().apply { putFloat("scrollPosition", scrollPosition()) })

            phase = "native-zoom-controls"
            compose.onNodeWithTag("reader_manga_zoom_in").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(3_000) { model.mangaReadingState.value.zoom == 1.25f }
            compose.onNodeWithText("125%").assertIsDisplayed()
            compose.onNodeWithTag("reader_manga_zoom_out").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(3_000) { model.mangaReadingState.value.zoom == 1f }
            compose.onNodeWithText("100%").assertIsDisplayed()
            assertEquals(1, model.mangaReadingState.value.currentOrdinal)
            marker("manga-zoom-controls-visible")

            phase = "native-exit"
            compose.onNodeWithTag("reader_manga_exit").assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(5_000) { model.state.value.route == ReaderRoute.Bookshelf &&
                model.mangaReadingState.value.phase == ReaderMangaReadingPhase.IDLE }
            healthy(model)
            assertTrue(model.mangaReadingState.value.pages.isEmpty())
            compose.onNodeWithTag("reader_manga_reading").assertDoesNotExist()
            compose.onNodeWithTag("reader_online_sources").assertIsDisplayed()
            assertEquals(shelfBefore, model.state.value.books)
            assertEquals(remoteBefore, model.state.value.remoteBooks)
            marker("manga-exit-retired")
            complete = true
        } catch (error: Throwable) {
            failure = error
            marker("method-failure", Bundle().apply {
                putString("phase", phase); putString("failureClass", error.javaClass.name)
                putString("message", error.message?.take(512))
            })
            throw error
        } finally {
            fun cleanup(action: () -> Unit) = try { action() } catch (error: Throwable) {
                val original = failure
                if (original == null) { failure = error; throw error } else original.addSuppressed(error)
            }
            try {
                cleanup { scenario?.close() }
                // Failed runs retain their exact owned source for diagnosis; never replay a pending original.
                if (complete) cleanup {
                    // MainActivity activated a newer client. The seed client cannot
                    // observe or mutate again after that production ownership change.
                    ReaderLibrary(ApplicationProvider.getApplicationContext()).use { cleanupLibrary ->
                        runBlocking {
                            marker("owned-source-cleanup-enter")
                            val source = cleanupLibrary.sources(sourceId).single()
                            val deleted = cleanupLibrary.deleteSource(sourceId, source.sourceVersion)
                            assertTrue(deleted.durable)
                            assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
                            marker("owned-source-cleanup-ACK")
                        }
                    }
                }
            } finally {
                try { cleanup { library.close() } } finally { cleanup { fixture.close() } }
            }
        }
        marker("method-exit")
    }

    private fun healthy(model: ReaderViewModel) {
        val state = model.state.value
        assertTrue(state.ready && state.pendingReady)
        assertFalse(state.busy)
        assertNull(state.error); assertNull(state.protocolRecoveryReference)
        assertTrue(state.pendingImports.isEmpty() && state.pendingPositions.isEmpty() && state.pendingBookmarks.isEmpty())
        assertNull(state.pendingSearchClose)
    }

    private fun onlineIdle(model: ReaderViewModel) {
        compose.waitUntil(30_000) { !model.state.value.busy && !model.onlineSources.state.value.busy }
        healthy(model)
        assertNull(model.onlineSources.state.value.error)
        assertFalse(model.onlineSources.state.value.pending)
    }

    private fun clickWorkspace(tag: String) {
        compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("reader_online_workspace")))
            .performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().performClick()
    }

    private fun scrollPosition(): Float = compose.onNodeWithTag("reader_manga_pages")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun swipePageUp() {
        compose.onNodeWithTag("reader_manga_pages").performTouchInput {
            swipeUp(startY = height * 0.85f, endY = height * 0.2f, durationMillis = 400)
        }
    }

    private fun capturePage(runId: String, ordinal: Int, red: Boolean) {
        val image = compose.onNodeWithTag("reader_manga_page_$ordinal").captureToImage()
        assertTrue("Actual image must occupy a visible area", image.width > 64 && image.height > 64)
        val pixels = image.toPixelMap()
        var colored = 0
        var sampled = 0
        for (y in 0 until image.height step maxOf(1, image.height / 24)) {
            for (x in 0 until image.width step maxOf(1, image.width / 24)) {
                val color = pixels[x, y]
                if (if (red) color.red > 0.65f && color.red > color.green * 1.4f && color.red > color.blue * 1.4f
                    else color.blue > 0.65f && color.blue > color.green * 1.4f && color.blue > color.red * 1.4f) colored++
                sampled++
            }
        }
        assertTrue("Expected fixture colors must be drawn by Compose, not just present in Owner bytes", colored * 5 > sampled)
        val relative = "reader-manga-compose/$runId/page-$ordinal.png"
        val file = File(instrumentation.targetContext.cacheDir, relative)
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.outputStream().use { check(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
        marker("manga-rendered-page-captured", Bundle().apply {
            putInt("ordinal", ordinal); putInt("width", image.width); putInt("height", image.height)
            putString("relativeCachePath", relative)
            putString("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) })
        })
    }

    private fun marker(step: String, extras: Bundle = Bundle()) {
        extras.putString("readerStep", step)
        extras.putString("readerMethod", "fixtureSourceOpensMangaScrollsZoomsAndExits")
        extras.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        instrumentation.sendStatus(2, extras)
    }

    private class Fixture : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newFixedThreadPool(2)
        private val base = "http://127.0.0.1:${server.localPort}"
        private val images = listOf(page(Color.rgb(224, 35, 35)), page(Color.rgb(35, 55, 224)))
        private val acceptor = thread(start = true, name = "reader-manga-compose-fixture") {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    workers.submit { handle(socket) }
                }
            } catch (_: java.io.IOException) { }
        }

        fun sourceJson(): String {
            fun js(path: String) = "<js>eval(String(Reload('$base/$path.js')))</js>"
            return JSONObject().put("bookSourceUrl", base).put("bookSourceName", "Android manga Compose probe")
                .put("bookSourceType", 2).put("enabled", true).put("enabledCookieJar", false)
                .put("jsLib", JSONObject().put("fixture", "$base/library.js").toString())
                .put("searchUrl", js("search-url"))
                .put("ruleSearch", JSONObject().put("bookList", js("search-rule")).put("name", "<js>Label(result.name)</js>")
                    .put("author", "<js>Label(result.author)</js>").put("bookUrl", "url"))
                .put("ruleBookInfo", JSONObject().put("init", js("detail-rule")).put("name", "name")
                    .put("author", "author").put("intro", "intro").put("tocUrl", "url"))
                .put("ruleToc", JSONObject().put("chapterList", js("toc-rule")).put("chapterName", "name").put("chapterUrl", "url"))
                .put("ruleContent", JSONObject().put("content", js("content-rule"))).toString()
        }

        private fun page(color: Int): ByteArray {
            // Each page exceeds this phone's reading viewport, so the final page can
            // become the first visible item without adding unrelated filler content.
            val bitmap = Bitmap.createBitmap(256, 512, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(color)
                val paint = Paint().apply { this.color = Color.WHITE }
                canvas.drawRect(176f, 24f, 232f, 360f, paint)
                canvas.drawRect(24f, 96f, 152f, 116f, paint)
                canvas.drawRect(24f, 264f, 152f, 284f, paint)
                return ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }.toByteArray()
            } finally { bitmap.recycle() }
        }

        private fun handle(socket: java.net.Socket) {
            socket.use {
                it.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.ISO_8859_1))
                val first = reader.readLine() ?: return
                while (reader.readLine().orEmpty().isNotEmpty()) { }
                val path = runCatching { URI(first.split(' ')[1]).path }.getOrNull() ?: "/"
                val body = when (path) {
                    "/library.js" -> "function Reload(url){return String(java.importScript(url))}; function Label(value){return String(value)+' checked'}".toByteArray()
                    "/search-url.js" -> "'$base/search'".toByteArray()
                    "/search-rule.js" -> "[{name:'Comic',author:'Author',url:'$base/book'}]".toByteArray()
                    "/detail-rule.js" -> "({name:'Comic',author:'Author',intro:'Two colored pages',url:'$base/toc'})".toByteArray()
                    "/toc-rule.js" -> "[{name:'Chapter 1',url:'$base/chapter/1'}]".toByteArray()
                    "/content-rule.js" -> "'<img src=\"$base/images/1.jpg\"><img src=\"$base/images/2.jpg\">'".toByteArray()
                    "/images/1.jpg" -> images[0]
                    "/images/2.jpg" -> images[1]
                    "/search", "/book", "/toc", "/chapter/1" -> "fixture".toByteArray()
                    else -> null
                }
                val status = if (body == null) "404 Not Found" else "200 OK"
                val payload = body ?: "missing".toByteArray()
                val type = if (path.endsWith(".jpg")) "image/jpeg" else "application/javascript"
                val header = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n"
                it.getOutputStream().apply { write(header.toByteArray(StandardCharsets.ISO_8859_1)); write(payload); flush() }
            }
        }

        override fun close() {
            server.close()
            acceptor.join(2_000)
            workers.shutdownNow()
            check(workers.awaitTermination(12, TimeUnit.SECONDS)) { "MANGA_FIXTURE_WORKERS_DID_NOT_STOP" }
        }
    }
}
