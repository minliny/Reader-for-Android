package io.reader.rebuild.features.manga

import com.reader.core.protocol.M3MangaManifestPage
import com.reader.core.protocol.M3MangaResourceInspectData
import com.reader.core.protocol.M3MangaResourceInspectDataAbsent
import com.reader.core.protocol.M3MangaResourceInspectDataPresent
import com.reader.core.protocol.M3MangaResourceInspectParams
import com.reader.core.protocol.M3MangaResourcePrepareParams
import com.reader.core.protocol.M3MangaWindowManifest
import com.reader.core.protocol.ReaderM3
import com.reader.core.protocol.ReaderRPC
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.host.media.MangaImageDecoder
import io.reader.rebuild.host.media.MangaPixelBudgetExceeded
import io.reader.rebuild.host.media.MangaTileImage
import io.reader.rebuild.host.network.MangaResourceFile
import io.reader.rebuild.host.network.MangaTransferStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Each successful frame has a bounded tile lease that its viewer closes when no longer visible. */
internal class MangaPageFrame(
    val page: M3MangaManifestPage,
    val aspectRatio: Double,
    val content: Content,
) : AutoCloseable {
    sealed interface Content {
        data class Image(val tiles: MangaTileImage, val bytes: Long, val sha256: String, val chunks: Int) : Content
        data class Unavailable(val cause: Exception) : Content
    }

    override fun close() { (content as? Content.Image)?.tiles?.close() }
}

internal class MangaPageAssets(private val core: CoreRuntimeActor, private val store: MangaTransferStore) {
    private data class Key(val manifest: String, val resource: String)
    private data class Entry(val transfer: String, val file: MangaResourceFile, val inspected: M3MangaResourceInspectData)

    private val lock = Mutex()
    private var generation = 0L
    private val encoded = linkedMapOf<Key, Entry>()
    private val ratios = mutableMapOf<String, Double>()

    suspend fun decode(owner: Long, manifest: M3MangaWindowManifest, page: M3MangaManifestPage, width: Int):
        MangaPageFrame = lock.withLock {
        if (generation != owner) throw CancellationException("Manga page owner retired")
        val key = Key(manifest.manifestVersion, page.resourceRef)
        var image: MangaTileImage? = null
        try {
            val file = encoded[key] ?: fetch(key, manifest, page)
            withContext(Dispatchers.IO) { image = MangaImageDecoder.decode(file.file.file, file.inspected, width) }
            val tiles = checkNotNull(image)
            ratios[page.pageId] = tiles.aspectRatio
            MangaPageFrame(page, tiles.aspectRatio,
                MangaPageFrame.Content.Image(tiles, file.file.bytes, file.file.sha256, file.file.chunks))
        } catch (error: Exception) {
            image?.close()
            if (error !is MangaPixelBudgetExceeded) encoded.remove(key)?.let { store.discard(it.transfer) }
            if (error is CancellationException) throw error
            MangaPageFrame(page, ratios[page.pageId] ?: 0.5, MangaPageFrame.Content.Unavailable(error))
        }
    }

    suspend fun reset(owner: Long) = lock.withLock {
        generation = owner
        encoded.values.forEach { store.discard(it.transfer) }
        encoded.clear()
        ratios.clear()
    }

    private suspend fun fetch(key: Key, manifest: M3MangaWindowManifest, page: M3MangaManifestPage): Entry {
        if (encoded.size >= 2) encoded.remove(encoded.keys.first())?.let { store.discard(it.transfer) }
        val transfer = UUID.randomUUID().toString()
        val inspect = UUID.randomUUID().toString()
        store.register(transfer)
        try {
            val prepared = cancellable(ReaderM3.mangaResourcePrepare, M3MangaResourcePrepareParams(
                manifest.chapter, manifest.manifestVersion, page.resourceRef, manifest.sourceRuleVersion, transfer,
            ))
            val file = checkNotNull(store.result(transfer))
            check(prepared.prepared && prepared.transferId == transfer && prepared.resourceRef == page.resourceRef &&
                prepared.bytes == file.bytes)
            store.registerInspection(inspect, transfer)
            val inspected = cancellable(ReaderM3.mangaResourceInspect,
                M3MangaResourceInspectParams(inspect, file.sha256, file.bytes))
            check(when (inspected) {
                is M3MangaResourceInspectDataPresent ->
                    inspected.transferId == inspect && inspected.sha256 == file.sha256 && inspected.bytes == file.bytes
                is M3MangaResourceInspectDataAbsent ->
                    inspected.transferId == inspect && inspected.sha256 == file.sha256 && inspected.bytes == file.bytes
            })
            return Entry(transfer, file, inspected).also { encoded[key] = it }
        } catch (error: Throwable) {
            store.discard(transfer)
            throw error
        } finally { store.finishInspection(inspect) }
    }

    private suspend fun <P, R> cancellable(rpc: ReaderRPC<P, R>, params: P): R {
        val request = core.submit(rpc, params)
        return try { request.await() } catch (failure: CancellationException) {
            runCatching { core.cancelRead(request.identity) }
            throw failure
        }
    }
}
