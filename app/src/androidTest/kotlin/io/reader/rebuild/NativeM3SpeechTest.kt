package io.reader.rebuild

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.view.View
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpResponse
import com.reader.core.protocol.M3HttpTtsPutParams
import com.reader.core.protocol.M3TtsChapterRef
import com.reader.core.protocol.M3TtsQueueState
import com.reader.core.protocol.ReaderM3
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.features.reading.ReaderSpeechEngine
import io.reader.rebuild.features.reading.ReadingStyle
import io.reader.rebuild.features.reading.SpeechChapter
import io.reader.rebuild.features.reading.SpeechSessionCoordinator
import io.reader.rebuild.features.reading.StaticLayoutReadingSurface
import io.reader.rebuild.features.reading.TextScalarMap
import io.reader.rebuild.features.reading.TextSurfaceChapter
import io.reader.rebuild.features.shell.ReaderActivity
import io.reader.rebuild.host.media.ReaderSpeechSessionService
import io.reader.rebuild.host.media.SpeechAndMediaHost
import io.reader.rebuild.host.network.HTTP_BYTES
import io.reader.rebuild.host.network.readBounded
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeM3SpeechTest {
    private val runner = InstrumentationRegistry.getInstrumentation()
    private val app = runner.targetContext
    private val args = InstrumentationRegistry.getArguments()

    @Test fun systemRangesDriveNativeHighlightAndMedia3Commands() = runBlocking {
        hosted({ error("The system driver does not send HTTP") }) { _, coordinator ->
            val words = "with several spoken words ".repeat(12).trimEnd()
            val content = "Hello 𠀀 and Café $words. Next sentence $words."
            val chapter = chapter("wave3-system", content)
            coordinator.start(chapter, "Native system speech", 0, ReaderSpeechEngine.System(Locale.US))
            val highlight = awaitValue { coordinator.currentHighlight(chapter.identity, chapter.bodyVersion, chapter.processingVersion) }
            val spoken = checkNotNull(TextScalarMap(content).substring(highlight.start, highlight.endExclusive))
            check(spoken.isNotBlank() && highlight.start < highlight.endExclusive)
            check(coordinator.currentHighlight(chapter.identity.copy(bookId = "another"), chapter.bodyVersion, chapter.processingVersion) == null)
            check(coordinator.currentHighlight(chapter.identity, "stale", chapter.processingVersion) == null)
            check(coordinator.currentHighlight(chapter.identity, chapter.bodyVersion, "other-processing") == null)
            val surface = StaticLayoutReadingSurface(app)
            surface.open(TextSurfaceChapter(content), highlight.start, ReadingStyle(fontSize = 18.0))
            verifyHighlightedPixels(surface, 520, 850, highlight)
            surface.reflow(ReadingStyle(fontSize = 23.0), highlight.start)
            verifyHighlightedPixels(surface, 660, 850, highlight)
            check(coordinator.currentHighlight(chapter.identity, chapter.bodyVersion, chapter.processingVersion) == highlight)
            surface.retire()
            connect().use { observer ->
                awaitValue { true.takeIf { observer.mediaMetadata.title == "Native system speech" && observer.playWhenReady } }
            }
            delay(150)
            check(coordinator.snapshot.value?.state == M3TtsQueueState.PLAYING)
            connect().use { controller ->
                awaitValue { true.takeIf { controller.mediaMetadata.title == "Native system speech" && controller.playWhenReady } }
                controller.pause()
                awaitValue { true.takeIf { coordinator.snapshot.value?.state == M3TtsQueueState.PAUSED && !controller.playWhenReady } }
                controller.play()
                awaitValue { true.takeIf { coordinator.snapshot.value?.state == M3TtsQueueState.PLAYING && controller.playWhenReady } }
                controller.seekToNext()
                awaitValue { true.takeIf { coordinator.snapshot.value?.currentSliceIndex == 1L && controller.canSeekPrevious } }
                controller.seekToPrevious()
                awaitValue { true.takeIf { coordinator.snapshot.value?.currentSliceIndex == 0L } }
                val replacement = chapter("wave3-system-replacement", content)
                coordinator.start(replacement, "Replacement speech owner", 0, ReaderSpeechEngine.System(Locale.US))
                awaitValue { coordinator.currentHighlight(replacement.identity, replacement.bodyVersion, replacement.processingVersion) }
                check(coordinator.currentHighlight(chapter.identity, chapter.bodyVersion, chapter.processingVersion) == null)
                awaitValue { true.takeIf { controller.mediaMetadata.title == "Replacement speech owner" && controller.playWhenReady } }
                coordinator.retire()
                awaitValue { true.takeIf { controller.currentMediaItem == null } }
            }
            check(coordinator.highlight.value == null && coordinator.snapshot.value == null)
            println("ANDROID_M3_SYSTEM_TTS pid=${Process.myPid()} trueNativeRange=${highlight.start}..<${highlight.endExclusive} staticLayout18to23=true projectionRetiredPlayback=true platformMediaController=true pauseResume=true nextPrevious=true switchedOwner=true retired=true")
        }
    }

    @Test fun coreHTTPSRequestPlaysThroughExoPlayerAndSettles() = runBlocking {
        val port = requireNotNull(args.getString("readerSpeechTlsPort")).toInt().also { require(it in 1024..65535) }
        val origin = "https://127.0.0.1:$port"
        val received = mutableListOf<String>()
        val client = testLocalTLS(requireNotNull(args.getString("readerSpeechCA")))
        hosted({ request ->
            check(request.url.startsWith("$origin/") && request.method == "GET" && request.responseAsset == true)
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(request.url).build()).execute().use { response ->
                    check(response.isSuccessful)
                    received.add(response.request.url.queryParameter("rate").orEmpty())
                    M2HostHttpResponse(body = "", status = response.code.toLong(), finalUrl = response.request.url.toString(),
                        bodyBase64 = Base64.getEncoder().encodeToString(response.body.readBounded(HTTP_BYTES)))
                }
            }
        }) { runtime, coordinator ->
            runtime.call(ReaderM3.httpTtsPut, M3HttpTtsPutParams(3, "Wave 3 WAV",
                "$origin/wave3.wav?q={{text}}&rate={{rate}}", readerProfile = """{"version":1,"format":"wav"}"""))
            val chapter = chapter("wave3-http", "Audio fixture.")
            coordinator.start(chapter, "Native HTTPS speech", 0, ReaderSpeechEngine.Http(3), ratePercent = 125)
            val highlight = awaitValue { coordinator.highlight.value }
            check(highlight.start == 0L && highlight.endExclusive == 14L)
            check(received.toList() == listOf("1.25"))
            connect().use { controller ->
                awaitValue { true.takeIf { controller.mediaMetadata.title == "Native HTTPS speech" && controller.playWhenReady } }
                awaitValue { true.takeIf { coordinator.snapshot.value?.state == M3TtsQueueState.COMPLETED } }
                awaitValue { true.takeIf { controller.currentMediaItem == null } }
            }
            check(coordinator.failure.value == null)
            println("ANDROID_M3_HTTP_TTS pid=${Process.myPid()} tls=true wireRate=1.25 nativeExoPlayer=true scalar=0..<14 coreQueueCompleted=true sessionItemRetired=true")
        }
    }

    private fun verifyHighlightedPixels(surface: StaticLayoutReadingSurface, width: Int, height: Int,
                                        highlight: io.reader.rebuild.features.reading.SpeechHighlight) {
        surface.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, width, height)
        val plain = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val colored = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            surface.projectSpeech(null)
            surface.draw(Canvas(plain))
            surface.projectSpeech(highlight)
            surface.draw(Canvas(colored))
            check(!plain.sameAs(colored)) { "Native line highlight did not change pixels" }
        } finally {
            plain.recycle()
            colored.recycle()
        }
    }

    private suspend fun connect(): ConnectedController {
        val token = SessionToken(app, ComponentName(app, ReaderSpeechSessionService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        return withTimeout(6_000) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { future.cancel(true) }
                future.addListener({
                    try { continuation.resume(ConnectedController(future.get())) } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                    }
                }, Executor { it.run() })
            }
        }
    }

    private class ConnectedController(private val raw: MediaController) : AutoCloseable {
        val mediaMetadata get() = raw.mediaMetadata
        val playWhenReady get() = raw.playWhenReady
        val currentMediaItem get() = raw.currentMediaItem
        val canSeekPrevious get() = raw.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)
        fun pause() = raw.pause()
        fun play() = raw.play()
        fun seekToNext() = raw.seekToNext()
        fun seekToPrevious() = raw.seekToPrevious()
        override fun close() = raw.release()
    }

    private fun chapter(bookId: String, content: String) =
        SpeechChapter(M3TtsChapterRef("local", bookId, 0, "Synthesized fixture chapter"), content, "body-1", "processing-1")

    private suspend fun <T : Any> awaitValue(value: () -> T?): T = withTimeout(22_000) {
        var current = value()
        while (current == null) {
            delay(25)
            current = value()
        }
        checkNotNull(current)
    }

    private suspend fun hosted(fetch: suspend (M2HostHttpRequest) -> M2HostHttpResponse,
                               test: suspend (CoreRuntimeActor, SpeechSessionCoordinator) -> Unit) {
        check(app.packageName == "io.reader.minliny.rebuild.android.dev")
        val root = File(app.cacheDir, "m3-speech-test").apply { mkdirs() }
        val directory = java.nio.file.Files.createTempDirectory(root.toPath(), "run-").toFile()
        val activity: Activity = runner.startActivitySync(Intent(app, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val library = LibraryRuntime.open(File(directory, "library"), SystemSurfaceHost())
            try {
                withContext(Dispatchers.Main.immediate) {
                    val speech = SpeechSessionCoordinator(library.core, SpeechAndMediaHost(app, File(directory, "audio"), fetch))
                    try { test(library.core, speech) } finally { speech.close() }
                }
            } finally {
                library.close()
            }
        } finally {
            runner.runOnMainSync { activity.finish() }
            directory.deleteRecursively()
        }
    }

    private fun testLocalTLS(derBase64: String): OkHttpClient {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(Base64.getDecoder().decode(derBase64).inputStream())
        val keys = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture", certificate) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        return OkHttpClient.Builder().sslSocketFactory(context.socketFactory, trust).followRedirects(false).build()
    }
}
