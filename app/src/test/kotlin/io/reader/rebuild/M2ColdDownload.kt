package io.reader.rebuild

import com.reader.core.protocol.M2CacheBookPrefetchData
import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2DownloadOperationReceiptPhase
import com.reader.core.protocol.M2ReadingOperationBinding
import com.reader.core.protocol.ReaderProtocolJson
import io.reader.rebuild.features.downloads.DownloadCoordinator
import io.reader.rebuild.features.downloads.DownloadJournal
import io.reader.rebuild.features.reading.OnlineBookKey
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

@Serializable
private data class DownloadProof(val writer: Long, val input: M2CacheBookPrefetchParams, val binding: M2ReadingOperationBinding)

internal object M2ColdDownload {
    @JvmStatic fun main(arguments: Array<String>) = runBlocking {
        val (phase, directory, origin, edition) = arguments
        val root = File(directory)
        val source = SourceFixture(origin, edition)
        val (library, network) = openM2(root)
        val reader = OnlineReadingCoordinator(library.core)
        val journal = DownloadJournal(File(root, "downloads.json"))
        val downloads = DownloadCoordinator(reader, journal)
        val checkpoint = File(root, "cold-checkpoint.json")
        try {
            val pid = processId()
            if (phase == "write") {
                val request = source.download(3, 4, 2)
                downloads.begin(request).identity()
                withTimeout(12_000) { while (!File(root, "host-arrived").isFile) delay(20) }
                val exact = downloads.queryPrefetch(request)
                val active = checkNotNull(exact.receipt)
                check(active.phase == M2DownloadOperationReceiptPhase.PENDING && !exact.canResumeOriginal)
                checkpoint.writeText(ReaderProtocolJson.encodeToString(DownloadProof(pid, request, active.binding)))
                println("ANDROID_M2_DOWNLOAD_WRITE pid=$pid id=${active.binding.operationId} payload=${active.binding.payloadFingerprint}")
                System.out.flush()
                Runtime.getRuntime().halt(0)
            } else {
                check(phase == "read")
                val original = ReaderProtocolJson.decodeFromString<DownloadProof>(checkpoint.readText())
                check(pid != original.writer)
                val pending = downloads.queryPrefetch(original.input)
                check(pending.canResumeOriginal && pending.receipt?.binding == original.binding &&
                    pending.receipt?.phase == M2DownloadOperationReceiptPhase.PENDING)
                val operation = downloads.recoverSavedPrefetches().single()
                check(operation.phase == M2DownloadOperationReceiptPhase.SETTLED && operation.binding == original.binding)
                check((operation.data as M2CacheBookPrefetchData).materializations.single().chapterIndex == 3L)
                val queue = downloads.status(M2CacheBookStatusParams(source.id, source.bookId, includeChapterStates = true))
                check(queue.chapters.first { it.chapterIndex == 3L }.state == M2CacheChapterState.COMPLETED)
                val directory = requireNotNull(reader.cachedDirectory(OnlineBookKey(source.id, source.bookId)))
                check(reader.open(directory, 1, cacheOnly = true).scalar == 11L)
                check(downloads.recoverSavedPrefetches().isEmpty())
                println("ANDROID_M2_DOWNLOAD_COLD writer=${original.writer} reader=$pid payload=${operation.binding.payloadFingerprint} queue=completed restoredScalar=11")
            }
        } finally { downloads.close(); closeM2(library, network) }
    }
}
