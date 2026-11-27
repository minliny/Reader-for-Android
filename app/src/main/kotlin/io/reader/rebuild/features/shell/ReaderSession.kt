package io.reader.rebuild.features.shell

import android.app.Application
import android.util.Base64
import android.util.Log
import com.reader.core.protocol.M2HostHttpRequest
import io.reader.rebuild.core.readerResult
import io.reader.rebuild.core.HostCapabilityRouter
import io.reader.rebuild.features.bookshelf.LocalImportCoordinator
import io.reader.rebuild.features.bookshelf.ShelfLibrary
import io.reader.rebuild.features.downloads.DownloadCoordinator
import io.reader.rebuild.features.downloads.DownloadJournal
import io.reader.rebuild.features.manga.MangaReadingCoordinator
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import io.reader.rebuild.features.reading.ReadingSessionCoordinator
import io.reader.rebuild.features.reading.SourceSwitchCoordinator
import io.reader.rebuild.features.reading.SpeechSessionCoordinator
import io.reader.rebuild.features.search.SearchCoordinator
import io.reader.rebuild.features.source.SourceManagementCoordinator
import io.reader.rebuild.host.media.SpeechAndMediaHost
import io.reader.rebuild.host.network.NetworkAndWebHost
import io.reader.rebuild.host.storage.FileAndAssetHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class ReaderSession private constructor(
    private val runtime: LibraryRuntime,
    private val network: NetworkAndWebHost,
    app: Application,
) {
    private val filesRoot = app.filesDir
    val core get() = runtime.core
    val actions = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val files = FileAndAssetHost(File(filesRoot, "Documents"))
    val shelf = ShelfLibrary(core)
    val importer = LocalImportCoordinator(core, files)
    val local = ReadingSessionCoordinator(core, files)
    val online = OnlineReadingCoordinator(core)
    val switching = SourceSwitchCoordinator(online)
    val search = SearchCoordinator(core)
    val sources = SourceManagementCoordinator(core, network)
    val downloads = DownloadCoordinator(online, DownloadJournal(File(filesRoot, "download-operations.json")))
    private val speechOwner = lazy {
        SpeechSessionCoordinator(core, SpeechAndMediaHost(app, File(app.cacheDir, "SpeechAudio"), network::fetch))
    }
    val speech get() = speechOwner.value
    private var mangaOwner: MangaReadingCoordinator? = null

    fun mangaReader(): MangaReadingCoordinator = mangaOwner
        ?: MangaReadingCoordinator(core, network.mangaTransfers).also { mangaOwner = it }

    suspend fun cover(url: String): ByteArray? {
        val response = network.fetch(M2HostHttpRequest(url = url, timeoutMs = 5_000, responseAsset = true))
        return response.bodyBase64?.let { Base64.decode(it, Base64.DEFAULT) }
    }

    suspend fun close() {
        actions.cancel()
        search.close()
        downloads.close()
        sources.close()
        try {
            try { if (speechOwner.isInitialized()) speech.close() } finally { mangaOwner?.retire() }
        } finally {
            try { runtime.close() } finally { network.close() }
        }
    }

    fun recoverDownloads() {
        actions.launch {
            readerResult { downloads.recoverSavedPrefetches() }.onFailure {
                Log.w("Reader", "Download verification is still pending", it)
            }
        }
    }

    companion object {
        suspend fun open(app: Application): ReaderSession {
            val files = app.filesDir
            val network = NetworkAndWebHost.android(app, File(files, "NetworkHost"))
            try {
                val host = HostCapabilityRouter(SystemSurfaceHost(), network)
                val runtime = LibraryRuntime.open(File(files, "Library"), host) { Log.e("Reader", "Core event failed", it) }
                return ReaderSession(runtime, network, app)
            } catch (failure: Throwable) {
                network.close()
                throw failure
            }
        }
    }
}
