package io.reader.android.runtime

import android.content.Context
import io.reader.android.ReaderApplication
import kotlinx.coroutines.flow.StateFlow
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class LibraryBook(
    val id: String,
    val title: String,
    val author: String,
    val chapterCount: Int,
    val progressChapter: Int = 0,
    val progressOffset: Int = 0,
    val sourceId: String = "local",
)

data class ChapterItem(val index: Int, val title: String)

data class ReaderChapter(
    val bookId: String,
    val index: Int,
    val title: String,
    val text: String,
    val bodyVersion: String,
    val processingVersion: String,
    val sessionToken: String = UUID.randomUUID().toString(),
) {
    val scalarCount: Int get() = text.codePointCount(0, text.length)
}

data class PendingImport(val id: String, val fileName: String, val stage: String)
data class PendingPosition(val id: String, val bookId: String, val stage: String)

/**
 * A1's real local TXT product adapter. Core f322 owns parsing, shelf, TOC,
 * documents, progress and transactions. Kotlin owns SAF, source bytes and the
 * original-operation journal, following the existing iOS local-import v2 lane.
 */
class ReaderLibrary(context: Context) : AutoCloseable {
    private val application = context.applicationContext
    private val client = (application as? ReaderApplication)?.coreOwner?.newClient()
        ?: error("READER_APPLICATION_OWNER_REQUIRED")
    private val root get() = client.root
    private val originals get() = client.originals
    private val sourcePublisher = OriginalSourcePublisher(originals)
    private val staging get() = client.staging
    private val journal get() = client.journal
    private val transport: CoreTransport = client.transport
    private val bookmarkService get() = client.bookmarks
    private val managementService get() = client.management
    private val searchServiceDelegate = lazy { ReaderContentSearch(transport) }
    private val searchService by searchServiceDelegate
    private val registeredSearchCleanups = mutableSetOf<String>()
    private val closed = AtomicBoolean(false)
    private data class Session(val chapter: ReaderChapter, var revision: String)
    private val readingSessions = mutableMapOf<String, Session>()

    suspend fun books(
        keyword: String? = null,
        sort: LibrarySort = LibrarySort.LAST_READ,
        direction: LibrarySortDirection = LibrarySortDirection.DESCENDING,
    ): List<LibraryBook> = onIO(WholeTurnMode.OBSERVER_READ) {
        val result = mutableListOf<LibraryBook>()
        var offset = 0
        repeat(10_000) {
            val parameters = JSONObject()
                .put("sourceId", "local").put("contentKind", "text")
                .put("sortBy", sort.wire).put("sortDirection", direction.wire)
                .put("offset", offset).put("limit", 128)
            keyword?.takeIf { it.isNotBlank() }?.let { parameters.put("keyword", it) }
            val page = transport.request("bookshelf.list", parameters)
            val entries = page.getJSONArray("books")
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                check(entry.getString("sourceId") == "local") { "UNEXPECTED_SOURCE_IN_LOCAL_LIBRARY" }
                val id = entry.getString("bookId")
                val progress = readProgress(id).optJSONObject("progress")
                result += decodeBook(entry, progress, readLocalToc(id).size)
            }
            offset += entries.length()
            if (entries.length() == 0 || offset >= page.getInt("total")) return@onIO result
        }
        error("BOOKSHELF_PAGE_LIMIT")
    }

    suspend fun toc(bookId: String): List<ChapterItem> = onIO(WholeTurnMode.OBSERVER_READ) { readLocalToc(bookId) }

    /** Pure materialized Core TOC read; callers already own the library mutex. */
    private fun readLocalToc(bookId: String): List<ChapterItem> {
        localIdentity(bookId)
        val data = transport.request("local_book.toc", JSONObject().put("bookId", bookId))
        check(data.getString("sourceId") == "local" && data.getString("bookId") == bookId) { "LOCAL_TOC_IDENTITY_CHANGED" }
        val entries = data.getJSONArray("toc")
        return (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            val chapterIndex = item.getLong("index")
            check(chapterIndex in 0..Int.MAX_VALUE.toLong()) { "LOCAL_TOC_INDEX_OUT_OF_RANGE" }
            ChapterItem(chapterIndex.toInt(), item.getString("title"))
        }
    }

    suspend fun chapter(bookId: String, index: Int): ReaderChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) { readChapter(bookId, index) }

    /** Validate a jump before publishing its new session; stale targets leave the old session usable. */
    private fun readChapter(bookId: String, index: Int, validate: (ReaderChapter) -> Unit = {}): ReaderChapter {
        localIdentity(bookId)
        require(index >= 0)
        val data = transport.request("local_book.chapter.content", JSONObject()
            .put("bookId", bookId).put("chapterIndex", index))
        check(data.getString("sourceId") == "local" && data.getString("bookId") == bookId &&
            data.getInt("chapterIndex") == index) { "CONTENT_IDENTITY_CHANGED" }
        val content = ReaderChapter(bookId, index, data.getString("chapterTitle"), data.getString("content"),
            data.getString("bodyVersion"), data.getString("processingVersion"))
        check(content.bodyVersion.isNotEmpty() && content.processingVersion.isNotEmpty()) { "CONTENT_VERSION_MISSING" }
        validate(content)
        val revision = readProgress(bookId).getString("progressRevision")
        check(revision.isNotEmpty()) { "PROGRESS_OBSERVATION_MISSING" }
        readingSessions[bookId] = Session(content, revision)
        return content
    }

    suspend fun bookmarks(bookId: String, query: String? = null): List<ReaderBookmark> = onIO(WholeTurnMode.OBSERVER_READ) {
        val book = readShelfEntry(bookId)
        bookmarkService.list(bookId, book.getString("title"), book.getString("author"), query)
    }

    suspend fun createBookmark(
        bookId: String,
        index: Int,
        scalarOffset: Int,
        sessionToken: String,
        excerpt: String = "",
        note: String = "",
    ): ReaderBookmark = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val chapter = activeChapter(bookId, sessionToken, index)
        bookmarkService.requireWritable()
        val book = readShelfEntry(bookId)
        bookmarkService.create(chapter, book.getString("title"), book.getString("author"), scalarOffset, excerpt, note)
    }

    suspend fun deleteBookmark(bookId: String, time: Long, sessionToken: String): BookmarkDeleteResult = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val chapter = activeChapter(bookId, sessionToken)
        bookmarkService.requireWritable()
        val book = readShelfEntry(bookId)
        val target = bookmarkService.list(bookId, book.getString("title"), book.getString("author"))
            .firstOrNull { it.time == time } ?: error("BOOKMARK_TARGET_NOT_FOUND")
        bookmarkService.delete(chapter, target)
    }

    suspend fun pendingBookmarks(): List<PendingBookmark> = onIO(WholeTurnMode.OBSERVER_READ) { bookmarkService.pending() }

    suspend fun reconcilePendingBookmark(bookId: String, operationId: String): BookmarkOperationResult = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        localIdentity(bookId)
        bookmarkService.reconcile(bookId, operationId)
    }

    suspend fun openBookmark(bookmark: ReaderBookmark): ReaderChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        check(bookmark.sourceId == "local") { "LOCAL_BOOKMARK_REQUIRED" }
        readChapter(bookmark.bookId, bookmark.chapterIndex) { chapter ->
            if (chapter.bodyVersion != bookmark.bodyVersion || chapter.processingVersion != bookmark.processingVersion ||
                bookmark.scalarOffset !in 0..chapter.scalarCount) throw BookmarkTargetStaleException(bookmark.bookId, bookmark.time)
        }
    }

    suspend fun startContentSearch(bookId: String, keyword: String, limit: Int = 50): ReaderContentSearchPage = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        localIdentity(bookId)
        val originalService = searchService
        try {
            originalService.start(bookId, keyword, limit).also { registerSearchCleanup(it.session, originalService) }
        } catch (pending: ContentSearchPendingException) {
            registerSearchCleanup(pending.session, originalService)
            throw pending
        }
    }

    private fun registerSearchCleanup(session: ReaderContentSearchSession, originalService: ReaderContentSearch) {
        if (!registeredSearchCleanups.add(session.id)) return
        client.registerSearchCleanup(client.currentTurn(), session.id) {
            originalService.close(session) // Exact old service + UUID; never close a mutable "current cursor".
        }
    }

    suspend fun nextContentSearch(session: ReaderContentSearchSession, cursor: String): ReaderContentSearchPage = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        searchService.next(session, cursor)
    }

    suspend fun closeContentSearch(session: ReaderContentSearchSession): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        searchService.close(session) // Returns only after the exact original cursor close is confirmed.
        client.currentTurn().cleanupCompleted("search:${session.id}")
        registeredSearchCleanups.remove(session.id)
    }

    suspend fun openContentSearchHit(session: ReaderContentSearchSession, hit: ReaderContentSearchHit): ReaderChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        readChapter(hit.bookId, hit.chapterIndex) { chapter -> searchService.validateJump(session, hit, chapter) }
    }

    private fun activeChapter(bookId: String, sessionToken: String, index: Int? = null): ReaderChapter {
        localIdentity(bookId)
        val chapter = readingSessions[bookId]?.chapter ?: error("READING_SESSION_MISSING")
        check(chapter.sessionToken == sessionToken && (index == null || chapter.index == index)) { "RETIRED_READING_SESSION_TOKEN" }
        return chapter
    }

    private fun readShelfEntry(bookId: String): JSONObject {
        localIdentity(bookId)
        val entry = transport.request("bookshelf.get", JSONObject().put("sourceId", "local").put("bookId", bookId))
            .getJSONObject("book")
        check(entry.getString("sourceId") == "local" && entry.getString("bookId") == bookId) { "LOCAL_SHELF_IDENTITY_CHANGED" }
        return entry
    }

    suspend fun savePosition(bookId: String, index: Int, scalarOffset: Int, sessionToken: String? = null): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        localIdentity(bookId)
        val session = readingSessions[bookId] ?: error("READING_SESSION_MISSING")
        check(session.chapter.index == index) { "RETIRED_READING_SESSION" }
        check(sessionToken != null && sessionToken == session.chapter.sessionToken) { "RETIRED_READING_SESSION_TOKEN" }
        require(scalarOffset in 0..session.chapter.scalarCount)
        val pending = journal.readAll().firstOrNull {
            it.optString("kind") == "progress" && it.optString("bookId") == bookId && !it.optBoolean("completed")
        }
        if (pending != null) {
            val terminal = pending.optJSONObject("wireTerminal")
                ?: throw PendingOperationException(pending.getString("id"), "reading.progress.update")
            consumeProgressTerminal(pending, terminal)
        }
        val parameters = JSONObject().put("sourceId", "local").put("bookId", bookId)
            .put("chapterIndex", index).put("chapterOffset", scalarOffset)
            .put("chapterProgress", if (session.chapter.scalarCount == 0) 0.0 else scalarOffset.toDouble() / session.chapter.scalarCount)
            .put("expectedBodyVersion", session.chapter.bodyVersion)
            .put("expectedProcessingVersion", session.chapter.processingVersion)
            .put("expectedProgressRevision", session.revision)
        val record = JSONObject().put("journalSchema", 1).put("kind", "progress")
            .put("id", "android-progress-${UUID.randomUUID()}").put("bookId", bookId)
            .put("parameters", parameters).put("completed", false)
        try {
            transport.request("reading.progress.update", parameters,
                beforeSend = { id ->
                    record.put("requestId", id).put("runtimeSession", transport.session)
                    journal.write(record)
                }, terminal = { event ->
                    record.put("wireTerminal", event)
                    journal.terminal(record.getString("id"), record.getLong("requestId"), transport.session, event)
                })
        } finally {
            record.optJSONObject("wireTerminal")?.let { consumeProgressTerminal(record, it) }
        }
        Unit
    }

    private fun consumeProgressTerminal(record: JSONObject, event: JSONObject) {
        check(event.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_PROGRESS_ACK_IDENTITY" }
        if (event.getString("type") == "error") {
            // A normal Core terminal rejection confirms this original request,
            // except an explicit originalPending/unknown outcome.
            val details = event.getJSONObject("error").optJSONObject("details")
            val code = event.getJSONObject("error").getString("code")
            if (details?.optBoolean("originalPending") == true || code !in DEFINITE_REJECTIONS) {
                throw PendingOperationException(record.getString("id"), "reading.progress.update")
            }
            record.put("completed", true)
            journal.write(record)
            CoreTerminal.result("reading.progress.update", event)
        }
        val parameters = record.getJSONObject("parameters")
        val revision = ProgressReceiptPolicy.revision(record, event)
        record.put("completed", true)
        journal.write(record)
        readingSessions[record.getString("bookId")]?.let { session ->
            if (session.revision == parameters.getString("expectedProgressRevision")) session.revision = revision
        }
    }

    suspend fun pendingPositions(): List<PendingPosition> = onIO(WholeTurnMode.OBSERVER_READ) {
        journal.readAll().filter { it.getString("kind") == "progress" && !it.optBoolean("completed") }
            .map { PendingPosition(it.getString("id"), it.getString("bookId"), "reading.progress.update") }
    }

    /** Explicitly inspect one original ACK. No get, new save, cancellation or replay. */
    suspend fun reconcilePendingPosition(bookId: String, operationId: String): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        localIdentity(bookId)
        var record = journal.exact(operationId, includeCompleted = true)
            ?: throw PendingOperationException(operationId, "reading.progress.update")
        check(record.getString("kind") == "progress" && record.getString("bookId") == bookId) {
            "ORIGINAL_PROGRESS_OWNER_MISMATCH"
        }
        if (record.optJSONObject("wireTerminal") == null &&
            record.getString("runtimeSession") == transport.session && !record.optBoolean("completed")) {
            transport.drainOriginalTerminal(record.getLong("requestId"))
            record = journal.exact(operationId, includeCompleted = true)
                ?: throw PendingOperationException(operationId, "reading.progress.update")
        }
        val terminal = record.optJSONObject("wireTerminal")
            ?: throw PendingOperationException(operationId, "reading.progress.update")
        check(terminal.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_PROGRESS_ACK_IDENTITY" }
        // Archived originals can be read again by their exact owner, but their
        // ACK must still validate; absence is never converted into success.
        if (record.optBoolean("completed")) {
            if (terminal.getString("type") == "error") CoreTerminal.result("reading.progress.update", terminal)
            ProgressReceiptPolicy.revision(record, terminal)
        } else consumeProgressTerminal(record, terminal)
        Unit
    }

    suspend fun pendingImports(): List<PendingImport> = onIO(WholeTurnMode.OBSERVER_READ) {
        pendingImportRecords().map { PendingImport(it.getString("id"), it.getString("fileName"),
            it.optString("inFlightStage", it.getString("nextStage"))) }
    }

    /** Explicit recovery queries and, only when Core admits it, replays the same binding. */
    suspend fun reconcilePendingImports(): List<LibraryBook> = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        pendingImportRecords().map { continueImport(it, explicitRecovery = true) }
    }

    suspend fun importDocument(uri: Uri): LibraryBook = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val pending = pendingImportRecords().firstOrNull()
        if (pending != null) throw PendingOperationException(pending.getString("id"), pending.getString("nextStage"))
        val filename = displayName(uri)
        require(filename.substringAfterLast('.', "").lowercase() == "txt") { "A1_SUPPORTS_LOCAL_TXT_ONLY" }
        check(staging.mkdirs() || staging.isDirectory)
        check(originals.mkdirs() || originals.isDirectory)
        val id = "android-import-${UUID.randomUUID()}"
        val staged = File(staging, "$id.source")
        var journalMayOwnSource = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val input = application.contentResolver.openInputStream(uri) ?: error("SOURCE_UNAVAILABLE")
            input.use { source ->
                FileOutputStream(staged).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        size += count
                        require(size <= MAX_SOURCE_BYTES) { "SOURCE_BYTE_LIMIT" }
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            syncDirectory(staging)
            require(size > 0) { "EMPTY_SOURCE" }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            val bookId = "local:$hash"
            val existing = transport.request("bookshelf.get", JSONObject().put("sourceId", "local").put("bookId", bookId))
            if (!existing.isNull("book")) {
                commitOriginal(staged, hash, size)
                val entry = existing.getJSONObject("book")
                return@onIO decodeBook(entry, readProgress(bookId).optJSONObject("progress"), readLocalToc(bookId).size)
            }
            val capabilities = transport.request("core.info").getJSONArray("capabilities")
            check((0 until capabilities.length()).any { capabilities.getString(it) == "import.local.receipts.v2" }) {
                "IMPORT_ORIGINAL_RECEIPT_CAPABILITY_REQUIRED"
            }
            val parsed = transport.request("import.parse", JSONObject().put("kind", "localBook").put("input", JSONObject()
                .put("bookId", bookId).put("fileName", filename).put("filePath", staged.absolutePath)
                .put("title", filename.substringBeforeLast('.'))), timeoutMs = 120_000)
            val preview = parsed.getJSONObject("preview")
            val summary = preview.getJSONObject("summary")
            val integrity = summary.getJSONObject("integrity")
            check(integrity.getInt("schemaVersion") == 1 &&
                integrity.getString("readability") in setOf("complete", "recoverable") &&
                summary.getString("format") == "txt" && summary.getLong("byteLen") == size) { "UNREADABLE_LOCAL_SOURCE" }
            currentCoroutineContext().ensureActive()
            val authority = transport.request("import.local.authority.v2")
            ImportReceiptPolicy.authority(authority)
            check(authority.getInt("activeCount") < authority.getInt("activeLimit")) { "IMPORT_RECEIPT_CAPACITY" }
            val sequence = authority.getLong("headSequence")
            check(sequence in 0 until Long.MAX_VALUE) { "IMPORT_SEQUENCE_EXHAUSTED" }
            val binding = JSONObject().put("authorityEpoch", authority.getString("authorityEpoch"))
                .put("sequence", sequence + 1).put("transactionId", id).put("sourceId", "local")
                .put("bookId", bookId).put("sourceSha256", hash).put("sourceBytes", size).put("format", "txt")
            val record = JSONObject().put("journalSchema", 1).put("kind", "import").put("id", id)
                .put("fileName", filename).put("stagedName", staged.name).put("hash", hash).put("sourceBytes", size)
                .put("binding", binding).put("preview", preview).put("receipts", JSONObject())
                .put("nextStage", "persist").put("completed", false)
            // Even a failed directory fsync may follow a successful atomic journal
            // write. Conservatively retain the staged original from this point.
            journalMayOwnSource = true
            journal.write(record)
            continueImport(record)
        } finally {
            if (!journalMayOwnSource && staged.exists()) {
                check(staged.delete()) { "STAGING_CLEANUP_FAILED" }
                syncDirectory(staging)
            }
        }
    }

    private fun continueImport(original: JSONObject, explicitRecovery: Boolean = false): LibraryBook {
        var record = original
        val dispatched = mutableSetOf<String>()
        repeat(8) {
            val pendingStage = record.optString("inFlightStage")
            if (pendingStage.isNotEmpty()) {
                val terminal = record.optJSONObject("wireTerminal")
                if (terminal != null) {
                    check(terminal.getLong("requestId") == record.getLong("requestId")) { "ORIGINAL_IMPORT_ACK_IDENTITY" }
                    val reply = try { importTerminal(record, pendingStage, terminal) }
                        catch (pending: PendingOperationException) { if (explicitRecovery) null else throw pending }
                    if (reply != null) {
                        if (pendingStage == "settle") {
                            completeSettlement(record, reply)
                            return importResult(record)
                        }
                        record = ImportReceiptPolicy.accept(record, reply, pendingStage)
                        journal.write(record)
                        return@repeat
                    }
                }
                if (pendingStage == "settle") {
                    record = ImportReceiptPolicy.settlementReplay(record, explicitRecovery)
                    journal.write(record)
                    return@repeat
                }
                val query = JSONObject().put("binding", record.getJSONObject("binding")).put("stage", pendingStage)
                val originalReceipt = record.getJSONObject("receipts").optJSONObject(pendingStage)
                if (originalReceipt != null) query.put("fingerprint", originalReceipt.getString("semanticFingerprint"))
                else if (pendingStage == "persist") query.put("parsed", record.getJSONObject("preview"))
                val response = transport.request("import.local.query.v2", query)
                record = ImportReceiptPolicy.recover(record, response, pendingStage, explicitRecovery)
                journal.write(record)
                return@repeat
            }
            val stage = record.getString("nextStage")
            if (stage == "publish") {
                try { commitOriginal(stagedSource(record), record.getString("hash"), record.getLong("sourceBytes")) }
                catch (failure: Exception) {
                    val publication = failure as? OriginalSourcePublisher.Failure
                    val rootClass = publication?.rootClass ?: failure.javaClass.simpleName
                    val detail = JSONObject().put("operation", publication?.operation ?: "original.publish")
                        .put("rootClass", rootClass).put("errno", publication?.errno ?: JSONObject.NULL)
                    record.put("nextStage", "rollback").put("resourceFailure", rootClass)
                        .put("resourceFailureDetail", detail)
                    record.remove("replayAuthorised")
                    journal.write(record)
                    return@repeat
                }
            }
            val params = JSONObject().put("binding", record.getJSONObject("binding"))
            if (stage == "persist") {
                val source = stagedSource(record)
                verifyOriginal(source, record.getString("hash"), record.getLong("sourceBytes"))
                params.put("parsed", record.getJSONObject("preview")).put("sourcePath", source.absolutePath)
            }
            if (stage == "settle") {
                val terminalStage = record.getString("terminalStage")
                params.put("terminalStage", terminalStage).put("terminalReceiptFingerprint",
                    record.getJSONObject("receipts").getJSONObject(terminalStage).getString("ackFingerprint"))
            }
            // One user recovery action dispatches each stage at most once. An
            // uncertain response returns to the owner; there is no retry loop.
            if (!dispatched.add(stage)) throw PendingOperationException(record.getString("id"), stage)
            val replay = record.optString("replayAuthorised")
            check(replay.isEmpty() || (explicitRecovery && replay == stage)) { "IMPORT_REPLAY_NOT_AUTHORISED" }
            val owned = record
            val response = try { transport.request("import.local.$stage.v2", params,
                timeoutMs = if (stage == "persist") 120_000 else 30_000,
                beforeSend = { requestId ->
                    owned.put("inFlightStage", stage).put("requestId", requestId).put("runtimeSession", transport.session)
                        .put("sentParameters", params).put("lastDispatchWasReplay", replay == stage)
                    owned.remove("replayAuthorised")
                    journal.write(owned)
                }, terminal = { event ->
                    // A callback retains its original record instance after timeout.
                    journal.terminal(owned.getString("id"), event.getLong("requestId"), transport.session, event)
                }) } catch (failure: CoreCommandException) {
                val latest = journal.readAll().first { it.getString("id") == record.getString("id") }
                latest.optJSONObject("wireTerminal")?.let { importTerminal(latest, stage, it) }
                throw PendingOperationException(record.getString("id"), stage)
            }
            if (stage == "settle") {
                completeSettlement(record, response)
                return importResult(record)
            }
            record = ImportReceiptPolicy.accept(record, response, stage)
            journal.write(record)
        }
        throw PendingOperationException(record.getString("id"), record.getString("nextStage"))
    }

    private fun importTerminal(record: JSONObject, stage: String, event: JSONObject): JSONObject {
        if (event.getString("type") == "error") {
            val error = event.getJSONObject("error")
            val details = error.optJSONObject("details")
            if (stage == "persist" && !record.optBoolean("persistReplayAttempted") &&
                details?.optString("localImportDisposition") == "nonAdmitted" &&
                record.getJSONObject("receipts").length() == 0) {
                // This is the original Core proof that the very first persist
                // was refused before admission, rather than a guessed rollback.
                record.put("nonAdmission", event).put("completed", true)
                journal.write(record)
                val source = File(staging, record.getString("stagedName"))
                if (source.exists()) {
                    verifyOriginal(source, record.getString("hash"), record.getLong("sourceBytes"))
                    check(source.delete())
                    syncDirectory(staging)
                }
                throw CoreCommandException("import.local.persist.v2", error)
            }
            throw PendingOperationException(record.getString("id"), stage)
        }
        return CoreTerminal.result("import.local.$stage.v2", event)
    }

    private fun completeSettlement(record: JSONObject, response: JSONObject) {
        check(!record.isNull("terminalStage")) { "DURABLE_TERMINAL_REQUIRED" }
        ImportReceiptPolicy.authority(response.getJSONObject("authority"), record.getJSONObject("binding"))
        check(response.getString("state") in setOf("settled", "retired")) { "IMPORT_SETTLEMENT_UNCONFIRMED" }
        record.put("settlement", response).put("completed", true)
        record.remove("inFlightStage")
        journal.write(record)
        // Stable .source bytes are retained even after rollback: no old receipt
        // may remove a source now owned by a later import of the same content.
        val staged = File(staging, record.getString("stagedName"))
        if (staged.exists()) {
            verifyOriginal(staged, record.getString("hash"), record.getLong("sourceBytes"))
            check(staged.delete()) { "STAGING_CLEANUP_FAILED" }
            syncDirectory(staging)
        }
    }

    private fun importResult(record: JSONObject): LibraryBook {
        if (record.getString("terminalStage") != "finalize") {
            val receipts = record.getJSONObject("receipts")
            val rejection = receipts.optJSONObject("publish")?.optJSONObject("error")
                ?: receipts.optJSONObject("persist")?.optJSONObject("error")
                ?: JSONObject().put("code", "IMPORT_ROLLED_BACK").put("message", "Original source publication was rolled back")
            throw CoreCommandException("import.local.v2", rejection)
        }
        val receipts = record.getJSONObject("receipts")
        check(receipts.getJSONObject("publish").getString("outcome") == "applied")
        val persisted = receipts.getJSONObject("persist").getJSONObject("data")
        check(persisted.getString("kind") == "localBook")
        val summary = persisted.getJSONObject("data")
        val metadata = summary.getJSONObject("book")
        return LibraryBook(record.getJSONObject("binding").getString("bookId"), metadata.optString("title"),
            metadata.optString("author"), summary.getInt("chapterCount"))
    }

    private fun pendingImportRecords() = journal.readAll().filter {
        it.getString("kind") == "import" && !it.optBoolean("completed")
    }

    private fun stagedSource(record: JSONObject): File {
        val name = record.getString("stagedName")
        check(name == File(name).name && name.matches(Regex("android-import-[a-fA-F0-9-]+\\.source")))
        val staged = File(staging, name)
        return if (staged.isFile) staged else File(originals, record.getString("hash") + ".source")
    }

    private fun commitOriginal(staged: File, hash: String, size: Long) {
        sourcePublisher.publish(staged, hash, size)
    }

    private fun verifyOriginal(file: File, hash: String, size: Long) {
        OriginalSourcePublisher.verify(file, hash, size)
    }

    private fun displayName(uri: Uri): String {
        val resolver = application.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: error("SOURCE_NAME_MISSING")
        require(name.isNotBlank() && name.length <= 1024 && !name.contains('\u0000')) { "SOURCE_NAME_INVALID" }
        return name.substringAfterLast('/')
    }

    private fun readProgress(bookId: String) = transport.request("reading.progress.get", JSONObject()
        .put("sourceId", "local").put("bookId", bookId))

    private fun decodeBook(entry: JSONObject, progress: JSONObject?, chapterCount: Int): LibraryBook = LibraryBook(
        entry.getString("bookId"), entry.optString("title"), entry.optString("author"),
        chapterCount, progress?.optInt("chapterIndex") ?: 0,
        (progress?.optLong("chapterOffset") ?: 0).also { check(it in 0..Int.MAX_VALUE.toLong()) }.toInt(),
    )

    private fun localIdentity(bookId: String) {
        require(bookId.matches(Regex("local:[a-f0-9]{64}"))) { "LOCAL_BOOK_ID_REQUIRED" }
    }

    private suspend fun <T> onIO(mode: WholeTurnMode, block: suspend () -> T): T {
        check(!closed.get()) { "LIBRARY_CLIENT_RETIRED" }
        return client.wholeTurn(mode, block = { block() }, protectedFinally = {
            // Even a pure-read body can reveal a retired search's original cursor.
            // This exact existing service cleanup never inherits the read observer Job.
            if (searchServiceDelegate.isInitialized()) searchService.finishOwnerTurn()
        })
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) client.retire()
        // Existing ViewModel.onCleared() still calls this, but no native destroy,
        // detached close thread, current-cursor close, or old-session map clearing.
    }

    companion object {
        /** These read the cheap Application feed and never instantiate Library/AppCoreOwner/Native. */
        internal fun observeCoreProtocol(context: Context): StateFlow<CoreProtocolRecoveryView> =
            (context.applicationContext as ReaderApplication).coreProtocolRecovery.view
        internal fun requestCoreProtocolRecovery(context: Context, presentationId: String): Boolean =
            (context.applicationContext as ReaderApplication).coreProtocolRecovery.requestAction(presentationId)
        private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
        private val DEFINITE_REJECTIONS = setOf("INVALID_PARAMS", "INVALID_MESSAGE", "UNKNOWN_METHOD",
            "INVALID_PROTOCOL_VERSION", "BUSY", "RESOURCE_EXHAUSTED", "BOOK_ALREADY_ON_SHELF")
    }
}
