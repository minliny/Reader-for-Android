package io.reader.rebuild

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.ReaderProtocolJson
import io.reader.rebuild.features.bookshelf.LocalImportCoordinator
import io.reader.rebuild.features.reading.EbookWebViewAdapter
import io.reader.rebuild.features.reading.ReadingPosition
import io.reader.rebuild.features.reading.ReadingSelection
import io.reader.rebuild.features.reading.ReadingSessionCoordinator
import io.reader.rebuild.features.reading.ReadingStyle
import io.reader.rebuild.features.reading.SurfaceEvent
import io.reader.rebuild.features.shell.ReaderActivity
import io.reader.rebuild.host.storage.AuthorizedDocument
import io.reader.rebuild.host.storage.FileAndAssetHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EbookWebViewTest {
    private val runner = InstrumentationRegistry.getInstrumentation()
    private val context = runner.targetContext
    private val events = Channel<SurfaceEvent>(Channel.UNLIMITED)

    @Test fun twoChaptersSelectionReflowAndReopen() = runBlocking {
        val activity = runner.startActivitySync(Intent(context, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val dir = File(context.cacheDir, "native-ebook-${UUID.randomUUID()}")
        val db = File(dir, "library")
        val fixture = "fixtures/m1-window.epub"
        val original = AuthorizedDocument("m1-window.epub") { runner.context.assets.open(fixture) }
        val files = FileAndAssetHost(dir)
        var runtime = LibraryRuntime.open(db, SystemSurfaceHost())
        var surface = surface(activity)
        try {
            val shelf = LocalImportCoordinator(runtime.core, files).import(original)
            check(shelf.bookId == "local:35efc3246026578c8f56ea227964bb05a5d7cd8d49ee0f7794221f9ab3a4481e")
            var coordinator = ReadingSessionCoordinator(runtime.core, files)
            val chapter1 = coordinator.ebook(shelf.bookId, 0)
            val chapter2 = coordinator.ebook(shelf.bookId, 1)
            check(chapter1.descriptor.entryPath == chapter2.descriptor.entryPath)
            check(chapter2.descriptor.chapterStartScalar == 65L && chapter2.descriptor.chapterEndScalar == 962L)
            ui { surface.adapter.open(chapter1, 0) }
            val first = verifyReady(0)
            ui { surface.adapter.open(chapter2, 27) }
            val second = verifyReady(27, wantImage = true)
            check(first.documentId != second.documentId && first.session == second.session)
            val imageWidth = awaitValue<String> {
                surface.view.evaluateJavascript("document.querySelector('iframe').contentDocument.images[0].naturalWidth", it)
            }
            check(imageWidth == "96")
            val position = awaitValue<ReadingPosition> { surface.adapter.currentPosition(it) }
            check(position?.scalar == 27L && position.text.startsWith("𠀀"))
            check(awaitValue<ReadingSelection> { surface.adapter.select(27, 34, it) } == ReadingSelection(27, 34))
            check(awaitValue<ReadingSelection> { surface.adapter.currentSelection(it) } == ReadingSelection(27, 34))
            val text = awaitValue<String> { callback ->
                surface.view.evaluateJavascript("document.querySelector('iframe').contentWindow.getSelection().toString()", callback)
            }
            check(ReaderProtocolJson.decodeFromString<String>(requireNotNull(text)) == "𠀀与Café")
            ui { surface.adapter.reflow(ReadingStyle(fontSize = 23.0), null) }
            check(verifyReady(27, wantImage = true).layoutGeneration == 1L)
            rejectRetiredMessages(surface, listOf(first, second))
            val reflowed = requireNotNull(awaitValue<ReadingPosition> { surface.adapter.currentPosition(it) })
            check(reflowed.scalar == 27L && reflowed.text.startsWith("𠀀"))
            val save = coordinator.save(chapter2.text, reflowed.scalar)
            ui { surface.adapter.retire() }
            check(save.await().chapterOffset == 27L)
            ui { surface.adapter.destroy() }
            runtime.close()
            runtime = LibraryRuntime.open(db, SystemSurfaceHost())
            coordinator = ReadingSessionCoordinator(runtime.core, files)
            val restored = coordinator.ebook(shelf.bookId, 1)
            check(restored.text.savedScalar == 27L)
            surface = surface(activity)
            ui { surface.adapter.open(restored, restored.text.savedScalar, ReadingStyle(fontSize = 23.0)) }
            verifyReady(27, wantImage = true)
            val reopened = awaitValue<ReadingPosition> { surface.adapter.currentPosition(it) }
            check(reopened?.scalar == 27L && reopened.text.startsWith("𠀀"))
            val mismatch = restored.copy(descriptor = restored.descriptor.copy(chapterStartScalar = 0L))
            ui { surface.adapter.open(mismatch, 27) }
            val failure = awaitEvent(setOf("failure", "ready"))
            check(failure.kind == "failure" && failure.value.jsonObject.getValue("code").jsonPrimitive.content == "CORE_WINDOW_MISMATCH")
            println("PASS Android WebView ${WebView.getCurrentWebViewPackage()?.versionName}: chapter [65,962), image, selection [27,34), reflow and reopened scalar 27, stale doc and mismatch rejected")
        } finally {
            ui { surface.adapter.destroy(); activity.finish() }
            runtime.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun rejectRetiredMessages(surface: NativeSurface, old: List<SurfaceEvent>) {
        for (event in old) {
            val payload = ReaderProtocolJson.encodeToString(ReaderProtocolJson.encodeToString(event))
            awaitValue<String> { surface.view.evaluateJavascript("window.ReaderNativePort.postMessage($payload); 'sent'", it) }
        }
        check(awaitValue<ReadingPosition> { surface.adapter.restore(27, it) }?.scalar == 27L)
        val position = awaitEvent(setOf("position", "ready"))
        check(position.kind == "position" && position.documentId == old.last().documentId && position.layoutGeneration == 1L)
    }

    private suspend fun verifyReady(scalar: Long, wantImage: Boolean = false): SurfaceEvent {
        val ready = awaitEvent(setOf("ready", "failure"))
        check(ready.kind == "ready") { ready.value.toString() }
        val body = ReaderProtocolJson.decodeFromJsonElement(ReadingPosition.serializer(), ready.value)
        check(body.scalar == scalar && (!wantImage || (body.loadedImageCount ?: 0) >= 1)) { body.toString() }
        return ready
    }

    private suspend fun awaitEvent(kinds: Set<String>): SurfaceEvent = withTimeout(15_000) {
        var event = events.receive()
        while (event.kind !in kinds) event = events.receive()
        event
    }

    private fun surface(activity: Activity): NativeSurface = ui {
        val view = WebView(activity)
        activity.setContentView(view)
        NativeSurface(view, EbookWebViewAdapter(view) { check(events.trySend(it).isSuccess) })
    }

    private suspend fun <T> awaitValue(call: ((T?) -> Unit) -> Unit): T? = withTimeout(10_000) {
        suspendCancellableCoroutine { continuation ->
            runner.runOnMainSync { call { if (continuation.isActive) continuation.resume(it) } }
        }
    }

    private fun <T> ui(action: () -> T): T {
        var result: Result<T>? = null
        runner.runOnMainSync { result = runCatching(action) }
        return requireNotNull(result).getOrThrow()
    }

    private data class NativeSurface(val view: WebView, val adapter: EbookWebViewAdapter)
}
