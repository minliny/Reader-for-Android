package io.reader.rebuild.features.bookshelf

import com.reader.core.protocol.BookshelfAddData
import com.reader.core.protocol.BookshelfAddParams
import com.reader.core.protocol.ImportLocalBookFinalizeParams
import com.reader.core.protocol.ImportLocalBookParseParams
import com.reader.core.protocol.ImportLocalBookPersistParams
import com.reader.core.protocol.ImportLocalBookRollbackParams
import com.reader.core.protocol.ImportLocalBookRollbackToken
import com.reader.core.protocol.LocalBookParseParams
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderProtocolJson
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.host.storage.AuthorizedDocument
import io.reader.rebuild.host.storage.FileAndAssetHost
import io.reader.rebuild.host.storage.StagedDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private enum class Phase { STAGED, PERSISTING, RETAINING, SHELVING, FINALIZING, ROLLING_BACK, CLEANING }

@Serializable
private data class Journal(
    val transactionId: String, val bookId: String, val sourceSha256: String, val stagedPath: String,
    val phase: Phase, val rollbackToken: ImportLocalBookRollbackToken? = null,
)

internal class LocalImportCoordinator(private val core: CoreRuntimeActor, private val files: FileAndAssetHost) {
    private val imports = Mutex()

    suspend fun import(document: AuthorizedDocument): BookshelfAddData = imports.withLock {
        // Once admitted, this operation settles under its journal independently of its observing UI.
        withContext(NonCancellable + Dispatchers.IO) { perform(files.stage(document.name, document.open)) }
    }

    suspend fun pending(): List<String> = imports.withLock {
        withContext(Dispatchers.IO) {
            files.journals.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
                val id = runCatching { UUID.fromString(file.nameWithoutExtension) }.getOrNull()
                id?.toString()
            }.sorted()
        }
    }

    suspend fun verify(id: String): Boolean = imports.withLock {
        withContext(NonCancellable + Dispatchers.IO) {
            val identity = UUID.fromString(id)
            val file = File(files.journals, "$id.json")
            if (!file.exists()) return@withContext true
            check(file.length() <= 2_097_152)
            val journal = ReaderProtocolJson.decodeFromString<Journal>(file.readText())
            check(journal.transactionId == id)
            val stage = StagedDocument(identity, File(journal.stagedPath), journal.sourceSha256)
            check(stage.bookId == journal.bookId)
            if (journal.phase == Phase.SHELVING || journal.phase == Phase.FINALIZING) {
                val token = requireNotNull(journal.rollbackToken)
                check(token.token.transactionId == id)
                val membership = core.call(ReaderM1.bookshelfGet,
                    com.reader.core.protocol.BookshelfGetParams("local", journal.bookId))
                if (membership.book?.bookId != journal.bookId || membership.original?.sha256 != journal.sourceSha256)
                    return@withContext false
                files.authorize(journal.sourceSha256)
                val result = core.call(ReaderM1.importFinalize, ImportLocalBookFinalizeParams(token))
                check(result.data.finalized && result.data.bookId == journal.bookId)
            } else if (journal.phase != Phase.CLEANING && journal.phase != Phase.STAGED) {
                return@withContext false
            }
            files.cleanStage(stage)
            files.removeJournal(identity)
            true
        }
    }

    private suspend fun perform(staged: StagedDocument): BookshelfAddData {
        var token: ImportLocalBookRollbackToken? = null
        fun journal(phase: Phase) = files.writeJournal(staged.transactionId, ReaderProtocolJson.encodeToString(Journal(
            staged.transactionId.toString(), staged.bookId, staged.sha256, staged.file.absolutePath, phase, token,
        )))
        fun clean() {
            journal(Phase.CLEANING)
            files.cleanStage(staged)
            files.removeJournal(staged.transactionId)
        }
        journal(Phase.STAGED)
        val parsed = try {
            core.call(ReaderM1.importParse, ImportLocalBookParseParams(input = LocalBookParseParams(
                bookId = staged.bookId, fileName = staged.file.name, filePath = staged.file.absolutePath,
            ))).also {
                check(it.preview.summary.book.variables?.get("localBookSourceSha256") == staged.sha256) { "Core original SHA-256 differs from staged bytes" }
                check(it.preview.summary.integrity?.readability?.wireValue == "complete")
            }
        } catch (failure: Exception) {
            clean()
            throw failure
        }
        journal(Phase.PERSISTING)
        val persisted = core.call(ReaderM1.importPersist, ImportLocalBookPersistParams(staged.transactionId.toString(), parsed))
        token = persisted.rollbackToken
        check(token.token.transactionId == staged.transactionId.toString())
        suspend fun rollback(failure: Exception): Nothing {
            journal(Phase.ROLLING_BACK)
            val receipt = core.call(ReaderM1.importRollback, ImportLocalBookRollbackParams(requireNotNull(token)))
            check(receipt.data.restored && receipt.data.bookId == staged.bookId)
            clean()
            throw failure
        }
        val book = persisted.persisted.data.book
        journal(Phase.RETAINING)
        try {
            check(book.bookId == staged.bookId)
            files.retain(staged)
        } catch (failure: Exception) { rollback(failure) }
        journal(Phase.SHELVING)
        val shelf = try {
            core.call(ReaderM1.bookshelfAdd, BookshelfAddParams(
                sourceId = "local", bookId = book.bookId, title = book.title, author = book.author,
                kind = book.kind, requireReadable = true,
            ))
        } catch (failure: CoreRejected) {
            // Reconcile a terminal rejection against the authoritative shelf before compensating.
            val current = core.call(ReaderM1.bookshelfGet, com.reader.core.protocol.BookshelfGetParams("local", book.bookId))
            if (current.book == null) rollback(failure)
            throw failure
        }
        check(shelf.bookId == book.bookId && shelf.sourceId == "local")
        journal(Phase.FINALIZING)
        val final = core.call(ReaderM1.importFinalize, ImportLocalBookFinalizeParams(requireNotNull(token)))
        check(final.data.finalized && final.data.bookId == book.bookId)
        clean()
        return shelf
    }
}
