package io.reader.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.NativeCoreRuntimePort
import io.reader.android.runtime.ReaderCoreEventActor
import io.reader.android.runtime.ReaderMangaChapterIdentity
import io.reader.android.runtime.ReaderMangaMaterializationCompleteResult
import io.reader.android.runtime.ReaderMangaMaterializationIntent
import io.reader.android.runtime.ReaderMangaMaterializationPrepareResult
import io.reader.android.runtime.ReaderMangaResourceStore
import io.reader.android.runtime.http.ReaderHttpHost
import io.reader.android.runtime.http.ReaderMangaResourceHost
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
import kotlin.concurrent.thread

/** Real JNI/Core manga resource path over a private loopback source fixture. */
@RunWith(AndroidJUnit4::class)
class UiMangaResourceJourneyTest {
    @Test(timeout = 120_000)
    fun realMangaResourcePrepareDecodesPixelsAndCompletesMaterialization(): Unit = runBlocking {
        marker("method-enter")
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("io.reader.minliny", context.packageName)
        assertEquals(context.applicationInfo.uid, Process.myUid())
        assertEquals(PIN, BuildConfig.CORE_COMMIT)

        val probe = File(context.cacheDir, "A6MangaResource/${UUID.randomUUID()}")
        assertTrue(probe.mkdirs())
        val data = File(probe, "data").also { assertTrue(it.mkdir()) }
        val cache = File(probe, "cache").also { assertTrue(it.mkdir()) }
        val image = fixtureJpeg()
        LoopbackFixture(image).use { server ->
            val store = ReaderMangaResourceStore(File(data, "Manga/Resources"))
            val materializations = File(data, "Manga/Materializations")
            val http = ReaderHttpHost()
            val mangaHost = ReaderMangaResourceHost(http, store)
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val actor = ReaderCoreEventActor(
                NativeCoreRuntimePort(data, cache), dispatcher,
                hostHandler = ReaderCoreEventActor.HostHandler { envelope ->
                    marker("host-request", Bundle().apply {
                        putString("capability", envelope.capability)
                        putString("parametersJson", envelope.parametersJson)
                    })
                    if (envelope.capability == ReaderMangaResourceHost.CAPABILITY) mangaHost.execute(envelope)
                    else http.execute(envelope)
                }, pollIntervalMs = 5,
            )
            try {
                withTimeout(10_000) { actor.awaitReady() }
                marker("real-actor-ready", Bundle().apply { putString("runtimeSession", actor.runtimeSession) })
                val sourceId = "android-manga-probe"
                val source = sourceJson(sourceId, server.baseUrl)
                val imported = request(actor, "source.import", JSONObject()
                    .put("sourceId", sourceId).put("name", "Android manga probe")
                    .put("baseUrl", server.baseUrl).put("rules", JSONObject())
                    .put("bookSource", source), ReaderCoreEventActor.Kind.DURABLE_WRITE)
                assertEquals(true, imported.getBoolean("imported"))
                marker("source-imported", Bundle().apply { putString("sourceId", sourceId) })

                val search = request(actor, "book.search", JSONObject()
                    .put("sourceId", sourceId).put("keyword", "Comic"), ReaderCoreEventActor.Kind.READ_ONLY)
                val books = search.getJSONArray("books")
                assertEquals(1, books.length())
                val book = books.getJSONObject(0)
                val bookId = book.getString("bookId")
                request(actor, "book.detail", JSONObject()
                    .put("sourceId", sourceId).put("book", book).put("bookUrl", server.baseUrl + "/book"), ReaderCoreEventActor.Kind.READ_ONLY)
                request(actor, "book.toc", JSONObject()
                    .put("sourceId", sourceId).put("bookId", bookId).put("tocUrl", server.baseUrl + "/toc"), ReaderCoreEventActor.Kind.READ_ONLY)
                val chapterResult = request(actor, "manga.chapter.get", JSONObject()
                    .put("sourceId", sourceId).put("bookId", bookId).put("chapterIndex", 0), ReaderCoreEventActor.Kind.READ_ONLY)
                val manifest = chapterResult.getJSONObject("manifest")
                val chapterObject = manifest.getJSONObject("chapter")
                val chapter = ReaderMangaChapterIdentity(chapterObject.getString("sourceId"), chapterObject.getString("bookId"), chapterObject.getString("chapterId"))
                val manifestVersion = manifest.getString("manifestVersion")
                val sourceRuleVersion = manifest.getString("sourceRuleVersion")
                val resourceRef = manifest.getJSONArray("pages").getJSONObject(0).getString("resourceRef")
                val transferId = "rmt1:${"a".repeat(64)}"
                marker("manifest-ready", Bundle().apply {
                    putString("sourceId", sourceId); putString("bookId", bookId)
                    putString("chapterId", chapter.chapterId); putString("manifestVersion", manifestVersion)
                    putString("resourceRef", resourceRef)
                })

                val prepared = request(actor, "manga.resource.prepare", JSONObject()
                    .put("chapter", JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId).put("chapterId", chapter.chapterId))
                    .put("manifestVersion", manifestVersion).put("sourceRuleVersion", sourceRuleVersion)
                    .put("resourceRef", resourceRef).put("transferId", transferId), ReaderCoreEventActor.Kind.DURABLE_WRITE)
                assertEquals(true, prepared.getBoolean("prepared"))
                assertEquals(resourceRef, prepared.getString("resourceRef"))
                assertEquals(transferId, prepared.getString("transferId"))
                val decoded = store.readDecodedByTransferId(transferId)
                val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                assertNotNull(bitmap)
                assertEquals(2, bitmap!!.width)
                assertEquals(2, bitmap.height)
                bitmap.recycle()
                marker("native-image-decoded", Bundle().apply {
                    putString("transferId", transferId); putInt("encodedBytes", image.size); putInt("decodedBytes", decoded.size)
                    putString("decodedSha256", sha(decoded)); putInt("decodedWidth", 2); putInt("decodedHeight", 2)
                })

                val inspected = request(actor, "manga.resource.inspect", JSONObject()
                    .put("transferId", transferId).put("sha256", sha(image)).put("bytes", image.size), ReaderCoreEventActor.Kind.DURABLE_WRITE)
                assertEquals("absent", inspected.getString("status"))
                assertTrue(inspected.isNull("orientation"))
                assertEquals("jpeg", inspected.getJSONObject("decodeProfile").getString("format"))
                assertEquals(2, inspected.getJSONObject("decodeProfile").getLong("encodedWidth"))
                assertEquals(2, inspected.getJSONObject("decodeProfile").getLong("encodedHeight"))
                marker("resource-inspected", Bundle().apply { putString("transferId", transferId); putString("status", inspected.getString("status")) })

                val materialization = request(actor, "manga.cache.materialization.prepare", JSONObject()
                    .put("chapter", JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId).put("chapterId", chapter.chapterId))
                    .put("manifestVersion", manifestVersion).put("intent", "cache"), ReaderCoreEventActor.Kind.DURABLE_WRITE)
                val token = materialization.getString("token")
                val materializationStore = io.reader.android.runtime.ReaderMangaMaterializationStore(store, materializations)
                materializationStore.prepare(ReaderMangaMaterializationPrepareResult(token, chapter, manifestVersion, ReaderMangaMaterializationIntent.CACHE))
                materializationStore.requireResources(token, chapter, manifestVersion, listOf(resourceRef))
                val completed = request(actor, "manga.cache.materialization.complete", JSONObject()
                    .put("chapter", JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId).put("chapterId", chapter.chapterId))
                    .put("manifestVersion", manifestVersion).put("token", token)
                    .put("resourceRefs", JSONArray().put(resourceRef)), ReaderCoreEventActor.Kind.DURABLE_WRITE)
                assertEquals(true, completed.getBoolean("completed"))
                materializationStore.complete(ReaderMangaMaterializationCompleteResult(true, manifestVersion), token)
                assertTrue(File(materializations, "materialized-$token.json").isFile)
                marker("materialization-complete", Bundle().apply { putString("token", token); putString("resourceRef", resourceRef) })
                marker("method-body-complete")
            } finally {
                withContext(NonCancellable) { withTimeout(10_000) { actor.close() } }
                dispatcher.close()
            }
        }
        marker("method-exit")
    }

    private suspend fun request(actor: ReaderCoreEventActor, method: String, params: JSONObject,
        kind: ReaderCoreEventActor.Kind): JSONObject {
        val plan = actor.prepare(method, params.toString(), kind)
        val outcome = actor.dispatch(plan) {}
        assertTrue("$method dispatch failed: $outcome", outcome is ReaderCoreEventActor.DispatchOutcome.Accepted)
        val observed = withTimeout(60_000) { actor.observe(plan, 60_000) }
        val terminal = (observed as? ReaderCoreEventActor.Observation.Terminal)?.delivery
            ?: throw AssertionError("$method did not reach a terminal: $observed")
        val wire = JSONObject(terminal.rawEvent)
        assertEquals(1L, wire.getLong("protocolVersion"))
        if (wire.getString("type") != "result") throw AssertionError("$method failed: ${wire.optJSONObject("error")}")
        val data = wire.getJSONObject("data")
        assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, actor.consume(terminal) {})
        return data
    }

    private fun sourceJson(sourceId: String, base: String): JSONObject {
        fun js(path: String) = "<js>eval(String(Reload('$base/$path.js')))</js>"
        return JSONObject().put("bookSourceUrl", base).put("bookSourceName", "Android manga probe")
            .put("bookSourceType", 2).put("enabled", true).put("enabledCookieJar", false)
            .put("jsLib", JSONObject().put("fixture", "$base/library.js").toString())
            .put("searchUrl", js("search-url"))
            .put("ruleSearch", JSONObject().put("bookList", js("search-rule")).put("name", "<js>Label(result.name)</js>")
                .put("author", "<js>Label(result.author)</js>").put("bookUrl", "url"))
            .put("ruleBookInfo", JSONObject().put("init", js("detail-rule")).put("name", "name").put("author", "author")
                .put("intro", "intro").put("tocUrl", "url"))
            .put("ruleToc", JSONObject().put("chapterList", js("toc-rule")).put("chapterName", "name").put("chapterUrl", "url"))
            .put("ruleContent", JSONObject().put("content", js("content-rule")))
    }

    private fun fixtureJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE), 2, 2, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)); bitmap.recycle() }.toByteArray()
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerMethod", METHOD); fields.putString("readerStep", step)
        fields.putInt("readerProcessPid", Process.myPid()); fields.putLong("readerElapsedMs", android.os.SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, fields)
    }

    private class LoopbackFixture(private val image: ByteArray) : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newCachedThreadPool()
        val baseUrl: String = "http://127.0.0.1:${server.localPort}"
        private val acceptor = thread(start = true, name = "reader-a6-loopback") {
            try { while (!server.isClosed) workers.submit { handle(server.accept()) } } catch (_: Exception) { }
        }
        private fun handle(socket: java.net.Socket) {
            socket.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.ISO_8859_1))
                val first = reader.readLine() ?: return
                while (true) { if (reader.readLine().orEmpty().isEmpty()) break }
                val path = runCatching { URI(first.split(' ')[1]).path }.getOrNull() ?: "/"
                val response = route(path)
                val output = it.getOutputStream()
                output.write("HTTP/1.1 ${response.first} ${if (response.first == 200) "OK" else "Not Found"}\r\nContent-Type: ${response.second}\r\nContent-Length: ${response.third.size}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                output.write(response.third); output.flush()
            }
        }
        private fun route(path: String): Triple<Int, String, ByteArray> {
            val text = when (path) {
                "/library.js" -> "function Reload(url){return String(java.importScript(url))}; function Label(value){return String(value)+' checked'}"
                "/search-url.js" -> "'$baseUrl/search'"
                "/search-rule.js" -> "[{name:'Comic',author:'Author',url:'$baseUrl/book'}]"
                "/detail-rule.js" -> "({name:'Comic',author:'Author',intro:'Synopsis',url:'$baseUrl/toc'})"
                "/toc-rule.js" -> "[{name:'Chapter 1',url:'$baseUrl/chapter/1'}]"
                "/content-rule.js" -> "'<img src=\"$baseUrl/images/1.jpg\">'"
                "/search", "/book", "/toc", "/chapter/1" -> "fixture"
                else -> null
            }
            if (path == "/images/1.jpg") return Triple(200, "image/jpeg", image)
            return if (text == null) Triple(404, "text/plain", "missing".toByteArray())
            else Triple(200, "application/javascript", text.toByteArray())
        }
        override fun close() { server.close(); acceptor.join(2_000); workers.shutdownNow() }
    }

    companion object {
        private const val PIN = "c3109d570dd62a636eff1ffb88f4e242ede0bf3a"
        private const val METHOD = "realMangaResourcePrepareDecodesPixelsAndCompletesMaterialization"
    }
}
