package io.reader.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.reader.android.runtime.ReaderLibrary
import io.reader.android.runtime.CoreCommandException
import io.reader.android.runtime.ReaderMangaChapterRequest
import io.reader.android.runtime.ReaderMangaPageWindowRequest
import io.reader.android.runtime.ReaderMangaResourcePrepareRequest
import io.reader.android.runtime.SourceImportConfirmation
import io.reader.android.ui.ReaderMangaLibrary
import io.reader.android.ui.ReaderMangaLibraryAdapter
import io.reader.android.ui.ReaderMangaReadingOwner
import io.reader.android.ui.ReaderMangaReadingPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Real AppCoreOwner/ReaderLibrary/Host manga owner journey over an in-process HTTP source. */
@RunWith(AndroidJUnit4::class)
class UiMangaReadingOwnerJourneyTest {
    @Test(timeout = 120_000)
    fun realLibraryMangaOwnerLoadsDecodedPageAndReleasesOnClose(): Unit = runBlocking {
        val sourceId = "android-manga-ui-${UUID.randomUUID()}"
        val fixture = Fixture()
        val library = ReaderLibrary(ApplicationProvider.getApplicationContext())
        val adapter = ReaderMangaLibraryAdapter(library)
        val diagnosed = object : ReaderMangaLibrary by adapter {
            override suspend fun chapter(request: ReaderMangaChapterRequest) = stage("chapter") { adapter.chapter(request) }
            override suspend fun pagesWindow(request: ReaderMangaPageWindowRequest) = stage("window") { adapter.pagesWindow(request) }
            override suspend fun decodedResource(request: ReaderMangaResourcePrepareRequest) = stage("resource") { adapter.decodedResource(request) }
        }
        val ownerJob = SupervisorJob()
        val owner = ReaderMangaReadingOwner(CoroutineScope(coroutineContext + ownerJob)) { diagnosed }
        try {
            val imported = library.importSourceJson(sourceId, sourceJson(fixture.baseUrl))
            assertTrue(imported.durable)
            assertEquals(SourceImportConfirmation.APPLIED, imported.confirmation)
            val selection = library.selectOnlineSource(sourceId)
            val search = library.beginOnlineSearch(selection, "Comic")
            val searchPage = library.onlineSearchPage(search, 1)
            val book = searchPage.books.single()
            val detail = library.previewOnlineBook(search, book)
            val request = library.prepareMangaChapter(detail)
            library.closeOnlineSearch(search)
            owner.open(request)
            val ready = withTimeout(60_000) { owner.state.first { it.phase == ReaderMangaReadingPhase.READY || it.phase == ReaderMangaReadingPhase.ERROR } }
            assertEquals("owner error=${ready.error}", ReaderMangaReadingPhase.READY, ready.phase)
            assertEquals(1, ready.pages.size)
            assertTrue(ready.pages.single().decoded.bytes.isNotEmpty())
            assertEquals(ready.chapter!!.manifest.pages.single().resourceRef, ready.pages.single().resource.resourceRef)
            val bytes = ready.pages.single().decoded.bytes
            val bitmap = checkNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                assertEquals(1, bitmap.width)
                assertEquals(1, bitmap.height)
                assertTrue(android.graphics.Color.red(bitmap.getPixel(0, 0)) > 200)
            } finally { bitmap.recycle() }
            assertEquals(0, ready.currentOrdinal)
            owner.setZoom(3f)
            assertEquals(3f, owner.state.value.zoom)
            owner.close()
            assertEquals(ReaderMangaReadingPhase.CLOSED, owner.state.value.phase)
            assertTrue(owner.state.value.pages.isEmpty())
            val source = library.sources(sourceId).single()
            val deleted = library.deleteSource(sourceId, source.sourceVersion)
            assertTrue(deleted.durable)
            assertEquals(SourceImportConfirmation.APPLIED, deleted.confirmation)
        } finally {
            owner.close()
            ownerJob.cancel()
            library.close()
            fixture.close()
        }
    }

    private suspend fun <T> stage(name: String, action: suspend () -> T): T = try {
        action()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw IllegalStateException("$name: ${error.message}; core=${(error as? CoreCommandException)?.error}", error)
    }

    private fun sourceJson(base: String): String {
        fun js(path: String) = "<js>eval(String(Reload('$base/$path.js')))</js>"
        return JSONObject().put("bookSourceUrl", base).put("bookSourceName", "Android manga UI probe")
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

    private class Fixture : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newFixedThreadPool(2)
        val baseUrl = "http://127.0.0.1:${server.localPort}"
        private val image = ByteArrayOutputStream().also { out ->
            val bitmap = android.graphics.Bitmap.createBitmap(intArrayOf(android.graphics.Color.RED), 1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)); bitmap.recycle()
        }.toByteArray()
        private val acceptor = thread(start = true, name = "reader-manga-ui-fixture") {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    workers.submit { handle(socket) }
                }
            } catch (_: Exception) { }
        }
        private fun handle(socket: java.net.Socket) {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.ISO_8859_1))
                val first = reader.readLine() ?: return
                while (reader.readLine().orEmpty().isNotEmpty()) { }
                val path = runCatching { URI(first.split(' ')[1]).path }.getOrNull() ?: "/"
                val body = when (path) {
                    "/library.js" -> "function Reload(url){return String(java.importScript(url))}; function Label(value){return String(value)+' checked'}".toByteArray()
                    "/search-url.js" -> "'$baseUrl/search'".toByteArray()
                    "/search-rule.js" -> "[{name:'Comic',author:'Author',url:'$baseUrl/book'}]".toByteArray()
                    "/detail-rule.js" -> "({name:'Comic',author:'Author',intro:'Synopsis',url:'$baseUrl/toc'})".toByteArray()
                    "/toc-rule.js" -> "[{name:'Chapter 1',url:'$baseUrl/chapter/1'}]".toByteArray()
                    "/content-rule.js" -> "'<img src=\"$baseUrl/images/1.jpg\">'".toByteArray()
                    "/images/1.jpg" -> image
                    "/search", "/book", "/toc", "/chapter/1" -> "fixture".toByteArray()
                    else -> "missing".toByteArray()
                }
                val type = if (path == "/images/1.jpg") "image/jpeg" else "application/javascript"
                val status = if (path == "/images/1.jpg" || path.endsWith(".js") || path in setOf("/search", "/book", "/toc", "/chapter/1")) 200 else 404
                val label = if (status == 200) "OK" else "Not Found"
                val header = "HTTP/1.1 $status $label\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                it.getOutputStream().apply { write(header.toByteArray(StandardCharsets.ISO_8859_1)); write(body); flush() }
            }
        }
        override fun close() { server.close(); acceptor.join(2_000); workers.shutdownNow() }
    }
}
