package io.reader.rebuild

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.ReaderM1
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EbookWebViewTest {
    private val runner = InstrumentationRegistry.getInstrumentation()
    private val context = runner.targetContext
    private val events = Channel<SurfaceEvent>(Channel.UNLIMITED)
    private val runRoot: File
        get() {
            check(context.packageName == "io.reader.minliny.rebuild.android.dev")
            val run = requireNotNull(InstrumentationRegistry.getArguments().getString("readerM1Run"))
            require(run.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")))
            return File(context.filesDir, "instrumentation-m1/$run")
        }

    @Test fun writeChaptersSelectionReflowAndRetiredSave() = runBlocking {
        val dir = runRoot
        check(dir.mkdirs()) { "Use a new readerM1Run for the write phase" }
        val errors = ConcurrentLinkedQueue<Throwable>()
        val files = FileAndAssetHost(dir)
        val host = NativeM1CoreProof.HostProof()
        val activity = launch()
        val surface = surface(activity)
        var opened: LibraryRuntime? = null
        try {
            val runtime = LibraryRuntime.open(File(dir, "library"), host, errors::add)
            opened = runtime
            val layout = NativeM1CoreProof.storage(runtime, fresh = true)
            host.verify(runtime)
            val original = AuthorizedDocument("m1-window.epub") { runner.context.assets.open("fixtures/m1-window.epub") }
            val imports = LocalImportCoordinator(runtime.core, files)
            val shelf = imports.import(original)
            check(shelf.created && shelf.sourceId == "local" && shelf.bookId == "local:${NativeM1CoreProof.EPUB_SHA}")
            check(!imports.import(original).created && files.journals.listFiles().orEmpty().isEmpty())
            verifyOriginal(runtime, files, shelf.bookId)
            val coordinator = ReadingSessionCoordinator(runtime.core, files)
            val chapter1 = coordinator.ebook(shelf.bookId, 0)
            val chapter2 = coordinator.ebook(shelf.bookId, 1)
            check(chapter1.descriptor.entryPath == chapter2.descriptor.entryPath)
            check(chapter2.descriptor.chapterStartScalar == 65L && chapter2.descriptor.chapterEndScalar == 962L)
            ui { surface.adapter.open(chapter1, 0, ReadingStyle(fontSize = 18.0)) }
            val first = verifyReady(0)
            ui { surface.adapter.open(chapter2, 27, ReadingStyle(fontSize = 18.0)) }
            val second = verifyReady(27, wantImage = true)
            check(first.documentId != second.documentId && first.session == second.session)
            check(awaitValue<String> { callback ->
                surface.view.evaluateJavascript("document.querySelector('iframe').contentDocument.images[0].naturalWidth", callback)
            } == "96")
            checkFontSize(surface, "18px")
            checkPosition(surface)
            check(awaitValue<ReadingSelection> { surface.adapter.select(27, 34, it) } == ReadingSelection(27, 34))
            check(awaitValue<ReadingSelection> { surface.adapter.currentSelection(it) } == ReadingSelection(27, 34))
            val text = awaitValue<String> { callback ->
                surface.view.evaluateJavascript("document.querySelector('iframe').contentWindow.getSelection().toString()", callback)
            }
            check(ReaderProtocolJson.decodeFromString<String>(requireNotNull(text)) == "𠀀与Café")
            ui { surface.adapter.reflow(ReadingStyle(fontSize = 23.0), null) }
            val reflow = verifyReady(27, wantImage = true)
            check(reflow.documentId == second.documentId && reflow.layoutGeneration == 1L)
            checkFontSize(surface, "23px")
            nativeTap(surface, reflow)
            rejectRetiredMessages(surface, listOf(first, second), reflow)
            val measured = checkPosition(surface)
            println("M1_WEBVIEW_WRITE pid=${Process.myPid()} api=${Build.VERSION.SDK_INT} pagesize=${Os.sysconf(OsConstants._SC_PAGESIZE)}" +
                " provider=${WebView.getCurrentWebViewPackage()?.packageName}/${WebView.getCurrentWebViewPackage()?.versionName}" +
                " fixture=${NativeM1CoreProof.EPUB_SHA} xhtml=${chapter2.descriptor.entryPath} window=[65,962)" +
                " imageWidth=96 images=${readyPosition(reflow).loadedImageCount} selection=[27,34) text=𠀀与Café" +
                " reflow=18->23 scalar=${measured.scalar} tap=trusted staleDocument=rejected staleGeneration=rejected")
            val terminal = NativeM1CoreProof.saveAfterRetire(runtime, coordinator, chapter2.text, measured.scalar) {
                ui { surface.adapter.retire() }
            }
            NativeM1CoreProof.positionGuards(runtime, chapter2.text, terminal)
            check(errors.isEmpty()) { errors.toString() }
            val proof = ColdProof(Process.myPid(), shelf.bookId, requireNotNull(layout.databaseIdentity),
                chapter2.descriptor.chapterIndex, measured.scalar, measured.text)
            runtime.close()
            opened = null
            File(dir, "cold-proof.json").writeText(ReaderProtocolJson.encodeToString(proof))
            println("M1_WRITE_COMPLETE writer=${proof.writerPid} route=${dir.name} book=${proof.bookId} scalar=${proof.scalar}")
        } finally {
            ui { surface.adapter.destroy(); activity.finish() }
            opened?.close()
        }
    }

    @Test fun independentlyColdReopenAndRejectMismatchedWindow() = runBlocking {
        val dir = runRoot
        val proof = ReaderProtocolJson.decodeFromString<ColdProof>(File(dir, "cold-proof.json").readText())
        check(Process.myPid() != proof.writerPid) { "Run this phase in an independent instrumentation invocation" }
        val errors = ConcurrentLinkedQueue<Throwable>()
        val files = FileAndAssetHost(dir)
        val activity = launch()
        val surface = surface(activity)
        var opened: LibraryRuntime? = null
        try {
            val runtime = LibraryRuntime.open(File(dir, "library"), SystemSurfaceHost(), errors::add)
            opened = runtime
            val layout = NativeM1CoreProof.storage(runtime, fresh = false)
            check(layout.databaseIdentity == proof.identity)
            check(proof.chapter == 1L && proof.scalar == 27L && proof.visible == "𠀀")
            verifyOriginal(runtime, files, proof.bookId)
            NativeM1CoreProof.checkPositions(runtime, proof.bookId, proof.chapter, proof.scalar)
            val chapter = ReadingSessionCoordinator(runtime.core, files).ebook(proof.bookId, proof.chapter)
            check(chapter.text.savedScalar == 27L)
            ui { surface.adapter.open(chapter, chapter.text.savedScalar, ReadingStyle(fontSize = 23.0)) }
            verifyReady(27, wantImage = true)
            val visible = checkPosition(surface)
            val mismatch = chapter.copy(descriptor = chapter.descriptor.copy(
                chapterStartScalar = chapter.descriptor.chapterStartScalar + 7,
            ))
            ui { surface.adapter.open(mismatch, 27) }
            val failure = awaitEvent(setOf("failure", "ready"))
            val failureCode = failure.value.jsonObject.getValue("code").jsonPrimitive.content
            check(failure.kind == "failure" && failureCode == "CORE_PROCESSED_MAPPING_UNPROVEN") { failure.toString() }
            check(awaitValue<ReadingPosition> { surface.adapter.currentPosition(it) } == null)
            NativeM1CoreProof.checkPositions(runtime, proof.bookId, proof.chapter, proof.scalar)
            check(errors.isEmpty()) { errors.toString() }
            println("M1_COLD writer=${proof.writerPid} reader=${Process.myPid()} identity=${proof.identity}" +
                " chapter=${proof.chapter} core=${chapter.text.savedScalar} visible=${visible.scalar}" +
                " text=${visible.text} mismatch=$failureCode persistedAnchorUnchanged=true")
        } finally {
            ui { surface.adapter.destroy(); activity.finish() }
            opened?.close()
        }
    }

    private suspend fun verifyOriginal(runtime: LibraryRuntime, files: FileAndAssetHost, bookId: String) {
        val shelf = runtime.core.call(ReaderM1.bookshelfGet, BookshelfGetParams("local", bookId))
        check(shelf.book?.bookId == bookId && shelf.original?.sha256 == NativeM1CoreProof.EPUB_SHA)
        val original = files.authorize(NativeM1CoreProof.EPUB_SHA).file
        check(original.name == "${NativeM1CoreProof.EPUB_SHA}.source" &&
            FileAndAssetHost.sha256(original) == NativeM1CoreProof.EPUB_SHA)
    }

    private suspend fun nativeTap(surface: NativeSurface, current: SurfaceEvent) {
        val tap = awaitValue<String> { callback ->
            val find = """
                (() => {
                    const f = document.querySelector('iframe');
                    const d = f.contentDocument;
                    const r = f.getBoundingClientRect();
                    for (const [xf, yf] of [[0.6,0.6],[0.4,0.7],[0.7,0.5]]) {
                        const x = xf * r.width, y = yf * r.height;
                        const hit = d.elementFromPoint(x, y);
                        if (hit && !hit.closest('[data-reader-href]')) {
                            return {x:(r.left+x)/innerWidth, y:(r.top+y)/innerHeight};
                        }
                    }
                    return null;
                })()
            """.trimIndent()
            surface.view.evaluateJavascript(find, callback)
        }
        val point = ReaderProtocolJson.decodeFromString<TapPoint>(requireNotNull(tap))
        check(point.x in 0.0..1.0 && point.y in 0.0..1.0)
        runner.waitForIdleSync()
        val (x, y) = ui {
            val origin = IntArray(2).also(surface.view::getLocationOnScreen)
            (origin[0] + surface.view.width * point.x).toFloat() to
                (origin[1] + surface.view.height * point.y).toFloat()
        }
        val started = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                check(runner.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        val received = awaitEvent(setOf("tap", "link", "failure"))
        check(received.kind == "tap" && received.value == JsonNull &&
            received.session == current.session && received.documentId == current.documentId &&
            received.layoutGeneration == current.layoutGeneration) { received.toString() }
    }

    private suspend fun rejectRetiredMessages(surface: NativeSurface, old: List<SurfaceEvent>, current: SurfaceEvent) {
        for (event in old) {
            val payload = ReaderProtocolJson.encodeToString(ReaderProtocolJson.encodeToString(event))
            awaitValue<String> { surface.view.evaluateJavascript("window.ReaderNativePort.postMessage($payload); 'sent'", it) }
        }
        check(awaitValue<ReadingPosition> { surface.adapter.restore(27, it) }?.scalar == 27L)
        val position = awaitEvent(setOf("position", "ready"))
        check(position.kind == "position" && position.documentId == current.documentId &&
            position.layoutGeneration == current.layoutGeneration) { position.toString() }
    }

    private suspend fun checkFontSize(surface: NativeSurface, expected: String) {
        val read = awaitValue<String> { callback ->
            surface.view.evaluateJavascript(
                "document.querySelector('iframe').contentWindow.getComputedStyle(" +
                    "document.querySelector('iframe').contentDocument.body).fontSize", callback,
            )
        }
        check(ReaderProtocolJson.decodeFromString<String>(requireNotNull(read)) == expected) { read }
    }

    private suspend fun checkPosition(surface: NativeSurface): ReadingPosition {
        val position = requireNotNull(awaitValue<ReadingPosition> { surface.adapter.currentPosition(it) })
        check(position.scalar == 27L && position.text == "𠀀") { position.toString() }
        return position
    }

    private suspend fun verifyReady(scalar: Long, wantImage: Boolean = false): SurfaceEvent {
        val ready = awaitEvent(setOf("ready", "failure"))
        check(ready.kind == "ready") { ready.value.toString() }
        val body = readyPosition(ready)
        check(body.scalar == scalar && (!wantImage || (body.loadedImageCount ?: 0) >= 1)) { body.toString() }
        return ready
    }

    private fun readyPosition(event: SurfaceEvent) =
        ReaderProtocolJson.decodeFromJsonElement(ReadingPosition.serializer(), event.value)

    private suspend fun awaitEvent(kinds: Set<String>): SurfaceEvent = withTimeout(15_000) {
        var event = events.receive()
        while (event.kind !in kinds) event = events.receive()
        event
    }

    private fun launch(): Activity = runner.startActivitySync(
        Intent(context, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

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

    @Serializable
    private data class TapPoint(val x: Double, val y: Double)

    @Serializable
    private data class ColdProof(
        val writerPid: Int, val bookId: String, val identity: String, val chapter: Long, val scalar: Long, val visible: String,
    )

    private data class NativeSurface(val view: WebView, val adapter: EbookWebViewAdapter)
}
