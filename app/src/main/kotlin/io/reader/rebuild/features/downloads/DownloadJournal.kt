package io.reader.rebuild.features.downloads

import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheChapterMaterializationReportParams
import com.reader.core.protocol.ReaderProtocolJson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable

/** Only immutable, admitted inputs. Task state and terminal receipts remain in Core. */
internal class DownloadJournal(private val file: File) {
    @Serializable
    internal data class Entry(val prefetch: M2CacheBookPrefetchParams,
        val reports: List<M2CacheChapterMaterializationReportParams> = emptyList())

    @Synchronized fun entries(): List<Entry> = if (file.exists()) {
        check(file.length() <= 1_048_576) { "Download input journal exceeds its size budget" }
        ReaderProtocolJson.decodeFromString(file.readText())
    } else emptyList()

    @Synchronized fun remember(params: M2CacheBookPrefetchParams) {
        val existing = entries()
        check(existing.none { it.prefetch.sourceId == params.sourceId && it.prefetch.bookId == params.bookId }) {
            "Verify the previous download operation first"
        }
        write(existing + Entry(params))
    }

    @Synchronized fun rememberReport(id: String, report: M2CacheChapterMaterializationReportParams) {
        val entries = entries()
        check(entries.any { it.prefetch.operationId == id })
        write(entries.map { entry ->
            if (entry.prefetch.operationId == id) entry.copy(reports = entry.reports + report) else entry
        })
    }

    @Synchronized fun forget(id: String) { write(entries().filterNot { it.prefetch.operationId == id }) }

    private fun write(entries: List<Entry>) {
        if (entries.isEmpty()) {
            Files.deleteIfExists(file.toPath())
            return
        }
        val parent = checkNotNull(file.absoluteFile.parentFile).also { it.mkdirs() }
        val staged = File.createTempFile(".download-", ".partial", parent)
        try {
            val bytes = ReaderProtocolJson.encodeToString(entries).toByteArray()
            check(bytes.size <= 1_048_576)
            FileOutputStream(staged).use { stream ->
                stream.write(bytes)
                stream.fd.sync()
            }
            try {
                Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            staged.delete()
        }
    }
}
