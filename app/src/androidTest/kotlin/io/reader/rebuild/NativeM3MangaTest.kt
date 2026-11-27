package io.reader.rebuild

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2SourceImportParams
import com.reader.core.protocol.M3MangaProgressGetParams
import com.reader.core.protocol.M3MangaProgressUpdateParams
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderHostCapability
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderM3
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.HostCapabilityRouter
import io.reader.rebuild.features.manga.MangaPageFrame
import io.reader.rebuild.features.manga.MangaReadingCoordinator
import io.reader.rebuild.host.media.MangaPixelBudgetExceeded
import io.reader.rebuild.host.network.NetworkAndWebHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeM3MangaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val port = requireNotNull(args.getString("readerM3MangaPort")).toInt().also { require(it in 1024..65535) }
    private val run = requireNotNull(args.getString("readerM3MangaRun")).also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }
    private val home = File(context.filesDir, "instrumentation-m3-manga/$run")
    private val source = "m3-manga-source"
    private val book = "m3-manga-book"
    private val base = "http://127.0.0.1:$port"
    private val x = 0.375
    private val y = 0.625

    @Test fun writeTilesRetryBudgetAndPosition() = runBlocking {
        check(home.mkdirs())
        val scratch = File(home, "host/manga-scratch")
        val stale = File(scratch, UUID.randomUUID().toString())
        check(stale.mkdirs())
        File(stale, "abandoned").writeText("copy")
        val sentinel = File(scratch, "unrelated")
        sentinel.writeText("keep")
        withLibrary(fresh = true) { runtime, reader ->
            val core = runtime.core
            val origin = "$base/m3/manga"
            check(core.call(ReaderM2.sourceImport, M2SourceImportParams(sourceId = source, name = source,
                baseUrl = origin, bookSource = buildJsonObject {
                    put("bookSourceUrl", origin)
                    put("bookSourceName", source)
                    put("bookSourceType", 2)
                    putJsonObject("ruleToc") {
                        put("chapterList", "ol li")
                        put("chapterName", "a@text")
                        put("chapterUrl", "a@href")
                    }
                    putJsonObject("ruleContent") { put("content", "tag.img@src") }
                },
            )).imported)
            check(core.call(ReaderM2.bookToc, M2BookTocParams(sourceId = source, bookId = book, tocUrl = "$origin/toc")).toc.size == 1)
            val current = reader.open(source, book)
            check(current.progress.location == null && current.chapter.totalPages == 3L)
            check(!stale.exists() && sentinel.readText() == "keep")
            val window = reader.readWindow(1, 1)
            val page = window.manifest.pages.single()
            check(window.pageStart == 1L && page.ordinal == 1L &&
                current.chapter.manifest.pages.single { it.ordinal == 1L }.pageId == page.pageId)
            try {
                fixture("/test/offline")
                reader.page(window, page, 48).use { frame ->
                    val failure = (frame.content as MangaPageFrame.Content.Unavailable).cause as CoreRejected
                    check(failure.detail.code == ReaderErrorCode.INTERNAL)
                    check(frame.page.pageId == page.pageId && frame.aspectRatio == 0.5)
                }
            } finally { fixture("/test/online") }
            reader.page(window, page, 48).use { frame ->
                check(frame.page.pageId == page.pageId)
                val image = frame.content as? MangaPageFrame.Content.Image
                    ?: run {
                        val cause = (frame.content as MangaPageFrame.Content.Unavailable).cause
                        val details = (cause as? CoreRejected)?.detail
                        println("ANDROID_M3_MANGA_DIAGNOSTIC onlineUnavailable=$cause error=${details?.code} reason=${details?.details?.reason} host=${details?.details?.host}")
                        error("online image decode unavailable: $cause")
                    }
                val tiles = image.tiles
                check(tiles.encodedWidth == 96 && tiles.encodedHeight == 22000 && tiles.sampleSize == 2)
                check(tiles.originalPoint(x, y) == (x to y))
                check(tiles.tileCount == 11 && image.chunks > 1)
                check(image.chunks.toLong() == (image.bytes + 1048575) / 1048576)
                check(image.sha256 == "2b6c236cd081f422640f22f6e91090138b49c737dbc70a19309d73512b9ac289")
                val raster = Bitmap.createBitmap(8, 64, Bitmap.Config.ARGB_8888)
                try {
                    tiles.draw(Canvas(raster), RectF(0f, 0f, 8f, 64f))
                    check(raster.getPixel(4, 8) == Color.rgb(32, 76, 158))
                    check(raster.getPixel(4, 55) == Color.rgb(176, 39, 82))
                } finally { raster.recycle() }
                println("M3_ANDROID_MANGA_TILE pageId=${page.pageId} original=96x22000 sample=${tiles.sampleSize} tiles=${tiles.tileCount} bytes=${image.bytes} chunks=${image.chunks} sha256=${image.sha256} retry=PASS draw=PASS")
            }
            val oversized = reader.readWindow(2, 1)
            reader.page(oversized, oversized.manifest.pages.single(), 48).use { frame ->
                check((frame.content as MangaPageFrame.Content.Unavailable).cause is MangaPixelBudgetExceeded)
                check(frame.page.ordinal == 2L && frame.aspectRatio == 0.5)
            }
            val saved = reader.save(window, page, x, y)
            check(saved.location?.pageId == page.pageId && saved.token.revision == 1L)
            Properties().apply {
                setProperty("pid", Process.myPid().toString())
                setProperty("epoch", saved.token.epoch.toString())
                setProperty("revision", saved.token.revision.toString())
                setProperty("manifest", window.manifest.manifestVersion)
                setProperty("page", page.pageId)
            }.let { values -> File(home, "checkpoint").outputStream().use { values.store(it, null) } }
            println("M3_ANDROID_MANGA_WRITE pid=${Process.myPid()} window=[1,2) pageId=${page.pageId} revision=1 x=$x y=$y sourcePixelBudget=PASS scratchReclaim=PASS")
        }
    }

    @Test fun readPositionInIndependentProcess() = runBlocking {
        val saved = Properties().apply { File(home, "checkpoint").inputStream().use { load(it) } }
        val pid = Process.myPid()
        check(pid.toString() != saved.getProperty("pid"))
        val before = fixture("/test/state")
        withLibrary(fresh = false) { runtime, reader ->
            val current = reader.open(source, book)
            val location = checkNotNull(current.progress.location)
            check(!current.recoveryRequired && current.targetOrdinal == 1L)
            check(location.x == x && location.y == y && location.pageId == saved.getProperty("page") &&
                location.manifestVersion == saved.getProperty("manifest"))
            val previousEpoch = saved.getProperty("epoch").toLong()
            val revision = saved.getProperty("revision").toLong()
            check(current.progress.token.epoch == previousEpoch + 1 && current.progress.token.revision == revision)
            val stale = runCatching {
                runtime.core.call(ReaderM3.readingProgressUpdateManga, M3MangaProgressUpdateParams(
                    location = location.copy(progressRevision = revision + 1), expectedEpoch = previousEpoch, expectedRevision = revision,
                ))
            }.exceptionOrNull() as CoreRejected
            check(stale.detail.code == ReaderErrorCode.INTERNAL)
            val readBack = runtime.core.call(ReaderM3.readingProgressGetManga,
                M3MangaProgressGetParams(sourceId = source, bookId = book))
            check(readBack == current.progress)
            check(fixture("/test/state") == before)
            println("M3_ANDROID_MANGA_COLD writer=${saved.getProperty("pid")} reader=$pid pageId=${location.pageId} revision=$revision x=$x y=$y epoch=$previousEpoch->${current.progress.token.epoch} staleWrite=REJECTED refetch=0")
        }
    }

    private suspend fun fixture(path: String): String = withContext(Dispatchers.IO) {
        val connection = URI.create(base + path).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
    }

    private suspend fun withLibrary(fresh: Boolean, action: suspend (LibraryRuntime, MangaReadingCoordinator) -> Unit) {
        check(Os.sysconf(OsConstants._SC_PAGESIZE) == 16384L)
        check(context.packageName == "io.reader.minliny.rebuild.android.dev")
        val errors = ConcurrentLinkedQueue<Throwable>()
        val network = NetworkAndWebHost.android(context, File(home, "host"))
        try {
            check(ReaderHostCapability.MANGA_RESOURCE_TRANSFER in network.available)
            val library = LibraryRuntime.open(File(home, "library"), HostCapabilityRouter(SystemSurfaceHost(), network), errors::add)
            try {
                check(library.observed.observation.freshStorage == fresh)
                println("M3_ANDROID_MANGA_TARGET pid=${Process.myPid()} api=${Build.VERSION.SDK_INT} pageSize=16384")
                val reader = MangaReadingCoordinator(library.core, network.mangaTransfers)
                try { action(library, reader) } finally { reader.retire() }
            } finally { library.close() }
            check(errors.isEmpty()) { errors.joinToString() }
        } finally { network.close() }
    }
}
