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
import java.util.concurrent.atomic.AtomicReference
import io.reader.android.runtime.ebookdraft.EbookLeaseGuard
import io.reader.android.runtime.ebookdraft.EbookRenderLease

data class LibraryBook(
    val id: String,
    val title: String,
    val author: String,
    val chapterCount: Int,
    val progressChapter: Int = 0,
    val progressOffset: Int = 0,
    val sourceId: String = "local",
    val group: String? = null,
) { val key get() = ShelfBookKey(sourceId, id) }

data class ChapterItem(val index: Int, val title: String)

enum class ReaderTextPresentationKind { LOCAL_DOCUMENT, ONLINE_TEXT }

data class ReaderChapter(
    val bookId: String,
    val index: Int,
    val title: String,
    val text: String,
    val bodyVersion: String,
    val processingVersion: String,
    val sessionToken: String = UUID.randomUUID().toString(),
    val sourceId: String = "local",
    val presentationKind: ReaderTextPresentationKind = ReaderTextPresentationKind.LOCAL_DOCUMENT,
) {
    val key get() = ShelfBookKey(sourceId, bookId)
    val scalarCount: Int get() = text.codePointCount(0, text.length)
}

data class PendingImport(val id: String, val fileName: String, val stage: String)
data class PendingPosition(val id: String, val bookId: String, val stage: String, val sourceId: String = "local") {
    val key get() = ShelfBookKey(sourceId, bookId)
}

/**
 * Local TXT/EPUB/MOBI/AZW product adapter draft. Fixed Core 1fe owns parsing, shelf, TOC,
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
    private val sourceRulesService get() = client.sourceRules
    private val onlineCatalog get() = client.onlineCatalog
    private val onlineAdds get() = client.onlineAdds
    private val onlineReading get() = client.onlineReading
    private val tts get() = client.tts
    /** Uses the App owner’s single store instance so Host leases and materialization protection agree. */
    private val mangaService by lazy { ReaderMangaService(transport, client.mangaMaterializationStore) }
    private val searchServiceDelegate = lazy { ReaderContentSearch(transport) }
    private val searchService by searchServiceDelegate
    private val registeredSearchCleanups = mutableSetOf<String>()
    private val closed = AtomicBoolean(false)
    private data class Session(val chapter: ReaderChapter, var revision: String)
    private val readingSessions = mutableMapOf<ShelfBookKey, Session>()
    private var openedOnlineChapter: ReaderOnlineChapter? = null
    private val progressDispatchSessions = mutableMapOf<String, Session>()
    private val positionIntent = AtomicReference<ReaderPositionIntentState?>()
    private val originalAuthorities = ReaderLocalOriginalAuthorityReader(File(root, "Operations/Completed"))
    private val ebookViewIdentity = Any()
    private data class EbookRenderState(val lease: EbookRenderLease, val chapterToken: String,
        val view: ReaderEbookViewLease, val publication: ReaderEbookPreparedPublication? = null)
    private val ebookRender = AtomicReference<EbookRenderState?>()
    private val ebookGuard = EbookLeaseGuard { lease ->
        client.requireEbookCurrent(lease)
        val captured = ebookRender.get() ?: error("RETIRED_EBOOK_RENDER_LEASE")
        check(!closed.get() && captured.lease == lease) { "RETIRED_EBOOK_RENDER_LEASE" }
        captured.view.requireCurrent(ebookViewIdentity)
        client.requireEbookCurrent(lease)
        check(ebookRender.get() === captured) { "EBOOK_RENDER_CHANGED_DURING_OBSERVATION" }
    }

    /** Exact original import fact for renderer selection; no book-extension or genre inference. */
    suspend fun localBookFormat(bookId: String): ReaderLocalBookFormat = onIO(WholeTurnMode.OBSERVER_READ) {
        localIdentity(bookId)
        originalAuthorities.forBook(bookId).format
    }

    /** Mint before launching preparation; close may win before, during or after its whole turn. */
    internal fun newEbookView(): ReaderEbookViewLease {
        check(!closed.get()) { "LIBRARY_CLOSED" }; client.assertCurrentAdmission()
        return ReaderEbookViewLease(ebookViewIdentity, client.lease, ::retireEbookView)
    }

    /** One token, one actual view. Caller must leave the turn before awaiting WebView readiness. */
    internal suspend fun prepareEbookForView(view: ReaderEbookViewLease, bookId: String, index: Int,
        sessionToken: String): ReaderEbookViewPreparation {
        view.claim(ebookViewIdentity)
        try {
            val publication = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
                view.requireCurrent(ebookViewIdentity); check(view.ownerLease == client.lease)
                val chapter = activeChapter(bookId, sessionToken, index)
                val turn = client.currentTurn()
                check(turn.lease == client.lease) { "FOREIGN_EBOOK_WHOLE_TURN" }
                val original = originalAuthorities.forBook(bookId)
                check(original.format in setOf(ReaderLocalBookFormat.EPUB, ReaderLocalBookFormat.MOBI, ReaderLocalBookFormat.AZW)) { "EBOOK_FORMAT_REQUIRED" }
                val lease = EbookRenderLease(client.lease.ownerId, client.lease.clientId, client.lease.ownerGeneration,
                    transport.session, view.physicalViewId, chapter.bookId, chapter.index, chapter.bodyVersion, chapter.processingVersion)
                retirePreparedEbook()
                val admitted = EbookRenderState(lease, chapter.sessionToken, view)
                ebookRender.set(admitted)
                var prepared: ReaderEbookPreparedPublication? = null
                try {
                    ebookGuard.requireCurrent(lease)
                    val value = ReaderEbookOwnerPreparation(client, ebookGuard).prepare(lease, chapter, original.sourceBytes, original.format.wire)
                    prepared = value
                    turn.assertActive(); activeChapter(bookId, sessionToken, index); view.requireCurrent(ebookViewIdentity)
                    ebookGuard.requireCurrent(lease)
                    if (!ebookRender.compareAndSet(admitted, admitted.copy(publication = value))) error("RETIRED_EBOOK_PREPARATION")
                    value
                } catch (error: Throwable) {
                    prepared?.retire(); ebookRender.compareAndSet(admitted, null); throw error
                }
            }
            val capturedGuard = EbookLeaseGuard { lease ->
                view.requireCurrent(ebookViewIdentity)
                val current = ebookRender.get() ?: error("RETIRED_EBOOK_VIEW")
                check(current.view === view && current.publication === publication && publication.lease == lease) { "FOREIGN_PHYSICAL_EBOOK_VIEW" }
                ebookGuard.requireCurrent(lease)
                check(ebookRender.get() === current) { "RETIRED_EBOOK_VIEW_OBSERVATION" }
            }
            capturedGuard.requireCurrent(publication.lease)
            return ReaderEbookViewPreparation(view, publication, capturedGuard)
        } catch (error: Throwable) {
            try { view.close() } catch (cleanup: Throwable) { if (cleanup !== error) error.addSuppressed(cleanup) }
            throw error
        }
    }

    private fun retireEbookView(view: ReaderEbookViewLease) {
        while (true) {
            val captured = ebookRender.get() ?: return
            if (captured.view !== view) return
            if (ebookRender.compareAndSet(captured, null)) { captured.publication?.retire(); return }
        }
    }

    /** A late unmount can retire only its captured render, never the new one. No business gate is entered. */
    internal fun retireEbook(renderId: UUID) {
        while (true) {
            val captured = ebookRender.get() ?: return
            if (captured.lease.renderId != renderId) return
            if (ebookRender.compareAndSet(captured, null)) { captured.publication?.retire(); return }
        }
    }

    private fun retirePreparedEbook() { ebookRender.getAndSet(null)?.publication?.retire() }

    /** Preview a complete original document. No mutation, persistent URI permission, temporary file or local-book authority. */
    internal suspend fun previewSourceDocument(uri: Uri): ReaderSourceDocumentPreview {
        val preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ReaderSourceJsonDocument.read(application.contentResolver, uri) }
        return onIO(WholeTurnMode.OBSERVER_READ) { preview } // Recheck this actual client after file IO; no Core RPC.
    }
    /** One explicit selected definition; full object fields, one existing durable sourceRules transaction. */
    internal suspend fun importSourceDocumentSelection(document: ReaderSourceDocumentPreview, index: Int): SourceImportResult {
        val input = document.select(index)
        return importSourceJson(input.sourceId, input.selectedDefinitionJson)
    }

    /** Remote metadata only. No local format/TOC/Original/read-session fallback. */
    suspend fun remoteShelfMetadata(keyword: String?, sort: LibrarySort, direction: LibrarySortDirection): List<ReaderRemoteShelfBook> = onIO(WholeTurnMode.OBSERVER_READ) {
        val output = mutableListOf<ReaderRemoteShelfBook>(); val seen = mutableSetOf<ShelfBookKey>(); var offset = 0
        repeat(10_000) {
            val parameters = JSONObject().put("contentKind", "text").put("sourceKind", "online").put("sortBy", sort.wire).put("sortDirection", direction.wire)
                .put("offset", offset).put("limit", 128)
            keyword?.takeIf { it.isNotBlank() }?.let { parameters.put("keyword", it) }
            val page = transport.request("bookshelf.list", parameters); val rows = page.getJSONArray("books")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index); val key = ShelfBookKey(row.getString("sourceId"), row.getString("bookId"))
                check(key.sourceId != "local" && key.sourceId.isNotBlank() && key.bookId.isNotBlank() && seen.add(key)) { "REMOTE_SHELF_IDENTITY" }
                output.add(ReaderRemoteShelfBook(key, row.getString("title"), row.getString("author"),
                    if (row.has("sourceName") && !row.isNull("sourceName")) row.getString("sourceName") else null))
            }
            offset += rows.length()
            if (rows.length() == 0 || offset >= page.getInt("total")) return@onIO output
        }
        error("REMOTE_SHELF_PAGE_LIMIT")
    }

    suspend fun sources(sourceId: String? = null, enabledOnly: Boolean? = null): List<ReaderSourceEntry> =
        onIO(WholeTurnMode.OBSERVER_READ) { sourceRulesService.list(sourceId, enabledOnly) }
    suspend fun pendingSourceImports(): List<PendingSourceImport> =
        onIO(WholeTurnMode.OBSERVER_READ) { sourceRulesService.pending() }
    suspend fun importSourceJson(sourceId: String, originalBookSourceJson: String): SourceImportResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { sourceRulesService.importJson(sourceId, originalBookSourceJson) }
    suspend fun setSourceEnabled(sourceId: String, observedSourceVersion: String, enabled: Boolean): SourceImportResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { sourceRulesService.setEnabled(sourceId, observedSourceVersion, enabled) }
    suspend fun deleteSource(sourceId: String, observedSourceVersion: String): SourceImportResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { sourceRulesService.deleteSource(sourceId, observedSourceVersion) }
    suspend fun reconcileSourceImport(operationId: String): SourceImportResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { sourceRulesService.reconcile(operationId) }

    // Metadata acquisition never creates ReadingSession, local format/Original authority, shelf or progress writes.
    suspend fun selectOnlineSource(sourceId: String): ReaderOnlineSourceSelection =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineCatalog.select(client.lease, sourceId) }
    suspend fun beginOnlineSearch(source: ReaderOnlineSourceSelection, keyword: String): ReaderOnlineSearchSession =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) {
            val session = onlineCatalog.start(client.lease, source, keyword)
            client.registerSearchCleanup(client.currentTurn(), "online:" + session.id) { onlineCatalog.close(session) }
            session
        }
    suspend fun onlineSearchPage(session: ReaderOnlineSearchSession, page: Int): ReaderOnlineSearchPage =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineCatalog.page(client.lease, session, page) }
    suspend fun previewOnlineBook(session: ReaderOnlineSearchSession, selected: ReaderOnlineBook): ReaderOnlineDetailPreview =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineCatalog.preview(client.lease, session, selected) }
    suspend fun closeOnlineSearch(session: ReaderOnlineSearchSession): Unit =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) {
            onlineCatalog.close(session)
            client.currentTurn().cleanupCompleted("search:online:" + session.id)
        }
    suspend fun pendingOnlineAcquisitions(): List<ReaderOnlineOriginalObservation> =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineCatalog.pending() }
    suspend fun observeOnlineOriginal(id: String): ReaderOnlineOriginalObservation =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineCatalog.observeOriginal(id) }

    suspend fun addOnlinePreview(preview: ReaderOnlineDetailPreview): ReaderOnlineAddResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineAdds.begin(client.lease, preview) }
    suspend fun reconcileOnlineAdd(id: String): ReaderOnlineAddResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineAdds.reconcile(id) }
    suspend fun pendingOnlineAdds(): List<ReaderOnlineAddPending> =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineAdds.pending() }
    suspend fun observeOnlineAddState(id: String): ReaderOnlineAddObservation =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineAdds.observe(id) }

    /** Core TTS plan only; Android speech output is intentionally host-owned and not started here. */
    suspend fun ttsSlice(chapter: ReaderTtsChapterRef, content: String,
        strategy: ReaderTtsSlicingStrategy = ReaderTtsSlicingStrategy.PARAGRAPH): ReaderTtsSlicePlan =
        onIO(WholeTurnMode.OBSERVER_READ) { tts.slice(chapter, content, strategy) }

    suspend fun ttsQueueStatus(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.OBSERVER_READ) { tts.queueStatus(chapter) }

    suspend fun ttsChapterPlan(chapter: ReaderTtsChapterRef, next: ReaderTtsChapterRef? = null,
        drainBehavior: ReaderTtsDrainBehavior = ReaderTtsDrainBehavior.STOP_ON_BOUNDARY): ReaderTtsChapterTransition =
        onIO(WholeTurnMode.OBSERVER_READ) { tts.chapterPlan(chapter, next, drainBehavior) }

    /** Core queue controls are serialized as one retained host turn; no Android speech is started here. */
    suspend fun ttsQueuePlay(plan: ReaderTtsSlicePlan, startSliceIndex: Int = 0): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queuePlay(plan, startSliceIndex) }
    suspend fun ttsQueuePause(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queuePause(chapter) }
    suspend fun ttsQueueResume(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueResume(chapter) }
    suspend fun ttsQueueStop(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueStop(chapter) }
    suspend fun ttsQueueNext(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueNext(chapter) }
    suspend fun ttsQueuePrev(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queuePrev(chapter) }
    suspend fun ttsQueueSeek(chapter: ReaderTtsChapterRef, sliceIndex: Int): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueSeek(chapter, sliceIndex) }
    suspend fun ttsQueueSkip(chapter: ReaderTtsChapterRef): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueSkip(chapter) }
    suspend fun ttsQueueSetRate(chapter: ReaderTtsChapterRef, rate: Int): ReaderTtsQueueSetRateResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueSetRate(chapter, rate) }
    suspend fun ttsQueueReportStatus(chapter: ReaderTtsChapterRef, sliceIndex: Int,
        status: ReaderTtsSliceStatus): ReaderTtsQueueSnapshot =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { tts.queueReportStatus(chapter, sliceIndex, status) }
    suspend fun ttsQueueReportCallback(chapter: ReaderTtsChapterRef, sliceIndex: Int,
        status: ReaderTtsSliceStatus, callbackId: String,
        failurePolicy: ReaderTtsFailurePolicy = ReaderTtsFailurePolicy.STOP,
        failureLimit: Int = 3): ReaderTtsQueueCallbackResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) {
            tts.queueReportCallback(chapter, sliceIndex, status, callbackId, failurePolicy, failureLimit)
        }

    /** Core extracts ordered image descriptors; Android does not download or decode them here. */
    suspend fun mangaPagesExtract(request: ReaderMangaExtractRequest): ReaderMangaPagesExtract =
        onIO(WholeTurnMode.OBSERVER_READ) { mangaService.pagesExtract(request) }

    suspend fun mangaChapter(request: ReaderMangaChapterRequest): ReaderMangaChapterData =
        onIO(WholeTurnMode.OBSERVER_READ) { mangaService.chapter(request) }

    suspend fun mangaPagesWindow(request: ReaderMangaPageWindowRequest): ReaderMangaPageWindow =
        onIO(WholeTurnMode.OBSERVER_READ) { mangaService.pagesWindow(request) }

    /** Resource preparation/inspection retain the original because Core waits on Host continuations. */
    suspend fun mangaResourcePrepare(request: ReaderMangaResourcePrepareRequest): ReaderMangaResourcePrepareResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { mangaService.resourcePrepare(request) }

    suspend fun mangaResourceInspect(request: ReaderMangaResourceInspectRequest): ReaderMangaResourceInspectResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { mangaService.resourceInspect(request) }

    /** Materialization changes durable cache state and therefore owns a retained original turn. */
    suspend fun mangaMaterializationPrepare(request: ReaderMangaMaterializationPrepareRequest): ReaderMangaMaterializationPrepareResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { mangaService.materializationPrepare(request) }

    suspend fun mangaMaterializationComplete(request: ReaderMangaMaterializationCompleteRequest): ReaderMangaMaterializationCompleteResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { mangaService.materializationComplete(request) }

    suspend fun mangaMaterializationAbort(token: String): ReaderMangaMaterializationAbortResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { mangaService.materializationAbort(token) }

    suspend fun shelfGroups(): List<ShelfGroup> = onIO(WholeTurnMode.OBSERVER_READ) { managementService.listGroups() }
    suspend fun pendingShelfManagement(): List<PendingManagement> = onIO(WholeTurnMode.OBSERVER_READ) { managementService.pending() }
    suspend fun reconcileShelfManagement(id: String, continueOriginal: Boolean = false): ManagementResult =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { managementService.reconcile(id, continueOriginal) }
    suspend fun createShelfGroup(name: String): ShelfGroup = onIO(WholeTurnMode.RETAIN_ORIGINAL) { managementService.createGroup(name) }
    suspend fun renameShelfGroup(id: Long, name: String): ShelfGroup = onIO(WholeTurnMode.RETAIN_ORIGINAL) { managementService.updateGroup(id, groupName = name) }
    suspend fun deleteShelfGroup(id: Long): ShelfGroupDeletion = onIO(WholeTurnMode.RETAIN_ORIGINAL) { managementService.deleteGroup(id) }
    suspend fun assignShelfGroup(target: ShelfBookKey, group: String): ShelfGroupAssignment = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        requireLocalShelfKey(target); managementService.assignGroup(target.sourceId, target.bookId, group)
    }
    suspend fun removeShelfBooks(targets: List<ShelfBookKey>): ManagementResult = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        targets.forEach(::requireLocalShelfKey)
        managementService.removeBooks(targets.map { it.copy() }) // One literal Core batch; preserve composite identity and order.
    }
    private fun requireLocalShelfKey(target: ShelfBookKey) {
        require(target.sourceId == "local") { "LOCAL_MANAGEMENT_SCOPE_REQUIRED" }; localIdentity(target.bookId)
    }

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
        retirePreparedEbook() // Only after the new chapter/jump scope passed all validation.
        positionIntent.set(null) // A fresh chapter/session never inherits another reading's new-intent capability.
        readingSessions[ShelfBookKey("local", bookId)] = Session(content, revision)
        return content
    }

    /** Actual stored-source entry. This does not use localBookFormat, Original or local_book commands. */
    suspend fun onlineDirectory(key: ShelfBookKey): ReaderOnlineDirectoryPage = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val page = onlineReading.directory(client.lease, key)
        client.registerSearchCleanup(client.currentTurn(), "online-dir:" + page.directory.id) { onlineReading.close(page.directory) }
        page
    }
    suspend fun nextOnlineDirectory(page: ReaderOnlineDirectory, offset: Int): ReaderOnlineDirectoryPage =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineReading.next(client.lease, page, offset) }
    suspend fun resolveOnlineDirectory(node: ReaderOnlineDirectoryNode): ReaderOnlineDirectoryTarget =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { onlineReading.resolve(client.lease, node) }
    suspend fun closeOnlineDirectory(directory: ReaderOnlineDirectory): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        onlineReading.close(directory); client.currentTurn().cleanupCompleted("search:online-dir:" + directory.id)
    }
    suspend fun openOnlineChapter(key: ShelfBookKey, target: ReaderOnlineDirectoryTarget? = null): ReaderOnlineChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val opened = onlineReading.open(client.lease, key, target)
        check(opened.chapter.key == key)
        publishOnlineChapter(opened)
    }
    private fun publishOnlineChapter(opened: ReaderOnlineChapter): ReaderOnlineChapter {
        check(opened.chapter.sourceId != "local" && opened.chapter.presentationKind == ReaderTextPresentationKind.ONLINE_TEXT)
        openedOnlineChapter?.let { old ->
            onlineReading.retire(client.lease, old)
            client.currentTurn().cleanupCompleted("search:online-entry:" + old.chapter.sessionToken)
            if (readingSessions[old.chapter.key]?.chapter === old.chapter) readingSessions.remove(old.chapter.key)
        }
        // New chapter/session never inherits a local Original or a different position capability.
        retirePreparedEbook(); positionIntent.set(null)
        readingSessions[opened.chapter.key] = Session(opened.chapter, opened.progressRevision)
        openedOnlineChapter = opened
        val exactLease = client.lease
        client.registerSearchCleanup(client.currentTurn(), "online-entry:" + opened.chapter.sessionToken) { onlineReading.retire(exactLease, opened) }
        return opened
    }
    private fun requireOnlineChapter(opened: ReaderOnlineChapter) {
        check(openedOnlineChapter === opened && readingSessions[opened.chapter.key]?.chapter === opened.chapter &&
            opened.chapter.presentationKind == ReaderTextPresentationKind.ONLINE_TEXT) { "ONLINE_CHAPTER_SESSION_RETIRED" }
    }
    /** UI Ready is a display fact; all actual window authority remains inside the same held App turn. */
    suspend fun onlineReadingWindow(opened: ReaderOnlineChapter, schedule: ReaderOnlineWindowSchedule): ReaderOnlineReadingWindowSnapshot = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        requireOnlineChapter(opened); onlineReading.window(client.lease, opened, schedule)
    }
    suspend fun prepareOnlineNeighbor(window: ReaderOnlineReadingWindow, neighbor: ReaderOnlineNeighbor): ReaderOnlineNeighborState =
        onIO(WholeTurnMode.RETAIN_ORIGINAL) { requireOnlineChapter(window.opened); onlineReading.warm(client.lease, window, neighbor) }
    /** Only the exact newly archived current-runtime ACK can authorize leaving this chapter. */
    suspend fun openOnlineNeighbor(window: ReaderOnlineReadingWindow, neighbor: ReaderOnlineNeighbor,
        confirmed: ReaderPositionSaveResult.ConfirmedNewOriginal): ReaderOnlineChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        requireOnlineChapter(window.opened)
        val record = checkNotNull(journal.exact(confirmed.originalId, includeCompleted = true)) { "ONLINE_MOVE_ORIGINAL_MISSING" }
        check(record.getBoolean("completed")) { "ONLINE_MOVE_ORIGINAL_NOT_COMPLETED" }
        val actual = ReaderProgressNewIntentPolicy.confirmed(record)
        check(actual.sourceId == window.key.sourceId && actual.bookId == window.key.bookId && actual.chapterIndex == window.chapterIndex &&
            actual.runtimeSession == transport.session && actual.bodyVersion == window.bodyVersion && actual.processingVersion == window.processingVersion &&
            actual.progressRevision == readingSessions[window.key]?.revision && actual.originalId == confirmed.originalId && actual.requestId == confirmed.requestId &&
            actual.scalarOffset == confirmed.scalarOffset && actual.progressRevision == confirmed.progressRevision &&
            ProgressJournalCandidatePolicy.sameObject(JSONObject(actual.originalParametersJson), JSONObject(confirmed.originalParametersJson)) &&
            ProgressJournalCandidatePolicy.sameObject(JSONObject(actual.parsedTerminalJson), JSONObject(confirmed.parsedTerminalJson))) { "ONLINE_MOVE_ORIGINAL_SCOPE" }
        publishOnlineChapter(onlineReading.move(client.lease, window, neighbor))
    }
    suspend fun closeOnlineChapter(opened: ReaderOnlineChapter): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        onlineReading.retire(client.lease, opened)
        client.currentTurn().cleanupCompleted("search:online-entry:" + opened.chapter.sessionToken)
        if (readingSessions[opened.chapter.key]?.chapter === opened.chapter) readingSessions.remove(opened.chapter.key)
        if (openedOnlineChapter === opened) openedOnlineChapter = null
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

    suspend fun closeContentSearch(session: ReaderContentSearchSession): ReaderContentSearchCloseObservation? = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        val observation = searchService.close(session) // No fabricated observation for already-closed/no-dispatch.
        client.currentTurn().cleanupCompleted("search:${session.id}")
        registeredSearchCleanups.remove(session.id)
        observation
    }

    suspend fun openContentSearchHit(session: ReaderContentSearchSession, hit: ReaderContentSearchHit): ReaderChapter = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        readChapter(hit.bookId, hit.chapterIndex) { chapter -> searchService.validateJump(session, hit, chapter) }
    }

    private fun activeChapter(bookId: String, sessionToken: String, index: Int? = null): ReaderChapter {
        localIdentity(bookId)
        val chapter = readingSessions[ShelfBookKey("local", bookId)]?.chapter ?: error("READING_SESSION_MISSING")
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

    /** Explicit new user intent only. This observes current Core state; it never reconciles an old write. */
    suspend fun beginNewPositionIntent(bookId: String, index: Int, sessionToken: String,
        retainedUnknownIds: List<String>): ReaderNewPositionIntent {
        val expectedIds = retainedUnknownIds.toList()
        require(expectedIds.isNotEmpty() && expectedIds.toSet().size == expectedIds.size) { "NEW_POSITION_INTENT_EXPECTED_ORIGINALS" }
        return onIO(WholeTurnMode.RETAIN_ORIGINAL) {
            val chapter = activeChapter(bookId, sessionToken, index)
            check(originalAuthorities.forBook(bookId).format != ReaderLocalBookFormat.TXT) { "NEW_POSITION_INTENT_EBOOK_REQUIRED" }
            positionIntent.get()?.let { existing ->
                check(existing.intent.bookId == bookId && existing.intent.sessionToken == sessionToken &&
                    existing.intent.retainedUnknownIds.toSet() == expectedIds.toSet()) { "NEW_POSITION_INTENT_READING_CHANGED" }
                client.requireNewPositionAuthority(existing.authority, chapter)
                return@onIO existing.intent // No new baseline/permission from a duplicate button callback.
            }
            val authority = client.newPositionAuthority(chapter, expectedIds)
            val observed = readProgress(bookId)
            check(observed.get("found") == true) { "NEW_POSITION_INTENT_CURRENT_POSITION_MISSING" }
            val progress = observed.getJSONObject("progress")
            check(progress.getString("sourceId") == "local" && progress.getString("bookId") == bookId &&
                progress.getInt("chapterIndex") == index && progress.getString("bodyVersion") == chapter.bodyVersion &&
                progress.getString("processingVersion") == chapter.processingVersion) { "NEW_POSITION_INTENT_CURRENT_SCOPE_CHANGED" }
            val offset = progress.getLong("chapterOffset")
            check(offset in 0..chapter.scalarCount.toLong()) { "NEW_POSITION_INTENT_CURRENT_SCALAR" }
            val revision = observed.getString("progressRevision")
            check(revision.matches(Regex("reader-progress-v1:[a-f0-9]{64}"))) { "NEW_POSITION_INTENT_OBSERVATION_REQUIRED" }
            check(revision == readingSessions.getValue(ShelfBookKey("local", bookId)).revision) { "NEW_POSITION_INTENT_REOPEN_CURRENT_POSITION" }
            client.requireNewPositionAuthority(authority, chapter) // Revalidate after the read, still in the same whole turn.
            val intent = ReaderNewPositionIntent(UUID.randomUUID().toString(), "local", bookId, index, sessionToken,
                chapter.bodyVersion, chapter.processingVersion, authority.runtimeSession, offset.toInt(), revision,
                authority.originals.map { it.identity() })
            readingSessions.getValue(ShelfBookKey("local", bookId)).revision = revision // Baseline for a different future CAS; never an old ACK.
            positionIntent.set(ReaderPositionIntentState(intent, authority, chapter.scalarCount, offset.toInt()))
            intent
        }
    }

    internal fun observeNewPositionReady(intent: ReaderNewPositionIntent, view: ReaderEbookViewLease,
        lease: EbookRenderLease, layoutEpoch: Long, layoutToken: String, scalarOffset: Int): Boolean =
        observeNewPosition(intent, view, lease, layoutEpoch, layoutToken, scalarOffset, anchor = false)

    internal fun observeNewPositionAnchor(intent: ReaderNewPositionIntent, view: ReaderEbookViewLease,
        lease: EbookRenderLease, layoutEpoch: Long, layoutToken: String, scalarOffset: Int): Boolean =
        observeNewPosition(intent, view, lease, layoutEpoch, layoutToken, scalarOffset, anchor = true)

    /** Called only after VM's existing real Ready/anchor gates; adds no Core/IO/business-lane wait. */
    private fun observeNewPosition(intent: ReaderNewPositionIntent, view: ReaderEbookViewLease,
        lease: EbookRenderLease, epoch: Long, token: String, scalar: Int, anchor: Boolean): Boolean {
        return try {
            val before = positionIntent.get() ?: return false
            if (before.intent !== intent || epoch !in 1..9_007_199_254_740_991L || !ReaderProgressNewIntentPolicy.uuid(token) ||
                scalar !in 0..before.scalarCount || lease.bookId != intent.bookId || lease.chapterIndex != intent.chapterIndex ||
                lease.bodyVersion != intent.bodyVersion || lease.processingVersion != intent.processingVersion ||
                lease.runtimeSession != intent.runtimeSession) return false
            val render = ebookRender.get() ?: return false
            if (render.view !== view || render.lease != lease || render.chapterToken != intent.sessionToken || render.publication == null) return false
            ebookGuard.requireCurrent(lease)
            val fact = ReaderPositionViewFact(lease, epoch, token)
            val after = if (anchor) {
                if (!before.everPresented || before.currentReady != fact) return false
                before.copy(lastVisible = scalar)
            } else {
                if (scalar != before.lastVisible) return false // Restore/reflow is never a new business position.
                val previous = before.currentReady
                if (previous?.lease == lease && (epoch < previous.epoch || (epoch == previous.epoch && token != previous.token))) return false
                before.copy(everPresented = true, currentReady = fact)
            }
            if (!positionIntent.compareAndSet(before, after)) return false
            val published = try {
                ebookGuard.requireCurrent(lease)
                ebookRender.get() === render && !closed.get()
            } catch (_: IllegalStateException) { false }
            // A failed observation was never published to the VM. Roll back only our exact CAS;
            // a later real observation or session retirement must never be overwritten.
            if (!published) positionIntent.compareAndSet(after, before)
            published
        } catch (_: IllegalStateException) { false }
    }

    suspend fun savePosition(bookId: String, index: Int, scalarOffset: Int, sessionToken: String? = null): ReaderPositionSaveResult {
        localIdentity(bookId)
        return savePosition(ShelfBookKey("local", bookId), index, scalarOffset, checkNotNull(sessionToken) { "RETIRED_READING_SESSION_TOKEN" })
    }

    suspend fun savePosition(key: ShelfBookKey, index: Int, scalarOffset: Int, sessionToken: String): ReaderPositionSaveResult = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        if (key.sourceId == "local") localIdentity(key.bookId) else onlineReading.requireNoUnknown(key)
        val bookId = key.bookId
        val session = readingSessions[key] ?: error("READING_SESSION_MISSING")
        check((key.sourceId == "local" || session.chapter.presentationKind == ReaderTextPresentationKind.ONLINE_TEXT) &&
            session.chapter.key == key && session.chapter.index == index) { "RETIRED_READING_SESSION" }
        check(sessionToken == session.chapter.sessionToken) { "RETIRED_READING_SESSION_TOKEN" }
        require(scalarOffset in 0..session.chapter.scalarCount)
        val continued = if (key.sourceId == "local") positionIntent.get() else null
        if (continued != null) {
            check(continued.intent.bookId == bookId && continued.intent.chapterIndex == index && continued.intent.sessionToken == sessionToken) { "NEW_POSITION_INTENT_READING_CHANGED" }
            client.requireNewPositionAuthority(continued.authority, session.chapter)
            check(continued.everPresented && scalarOffset == continued.lastVisible) { "NEW_POSITION_INTENT_VISIBLE_POSITION_REQUIRED" }
            if (!continued.hasConfirmedNewOriginal && scalarOffset == continued.intent.baselineScalarOffset) {
                return@onIO ReaderPositionSaveResult.SkippedUnchanged(continued.intent.retainedUnknownIds, bookId, index, scalarOffset)
            }
        } else {
            val pending = journal.readAll().firstOrNull {
                it.optString("kind") == "progress" && ProgressReceiptPolicy.key(it) == key && !it.optBoolean("completed")
            }
            if (pending != null) {
                val terminal = pending.optJSONObject("wireTerminal")
                    ?: throw PendingOperationException(pending.getString("id"), "reading.progress.update")
                consumeProgressTerminal(pending, terminal)
                check(key.sourceId == "local" || session.revision == ProgressReceiptPolicy.revision(pending, terminal)) {
                    "ONLINE_REOPEN_AFTER_ORIGINAL_CONFIRMATION"
                }
            }
        }
        val parameters = JSONObject().put("sourceId", key.sourceId).put("bookId", bookId)
            .put("chapterIndex", index).put("chapterOffset", scalarOffset)
            .put("chapterProgress", if (session.chapter.scalarCount == 0) 0.0 else scalarOffset.toDouble() / session.chapter.scalarCount)
            .put("expectedBodyVersion", session.chapter.bodyVersion)
            .put("expectedProcessingVersion", session.chapter.processingVersion)
            .put("expectedProgressRevision", session.revision)
        val record = JSONObject().put("journalSchema", 1).put("kind", "progress")
            .put("id", "android-progress-${UUID.randomUUID()}").put("bookId", bookId)
            .put("parameters", parameters).put("completed", false)
        withProgressTerminalCompletion(request = {
            transport.request("reading.progress.update", parameters,
                beforeSend = { id ->
                    record.put("requestId", id).put("runtimeSession", transport.session)
                    progressDispatchSessions[record.getString("id")] = session
                    journal.write(record)
                }, terminal = { event ->
                    record.put("wireTerminal", event)
                    journal.terminal(record.getString("id"), record.getLong("requestId"), transport.session, event)
                })
        }, complete = {
            record.optJSONObject("wireTerminal")?.let { consumeProgressTerminal(record, it) }
        })
        val confirmed = ReaderProgressNewIntentPolicy.confirmed(record)
        if (continued != null) positionIntent.updateAndGet { current ->
            if (current != null && current.intent === continued.intent) current.copy(hasConfirmedNewOriginal = true) else current
        }
        confirmed
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
            progressDispatchSessions.remove(record.getString("id"))
            CoreTerminal.result("reading.progress.update", event)
        }
        val parameters = record.getJSONObject("parameters")
        val revision = ProgressReceiptPolicy.revision(record, event)
        record.put("completed", true)
        journal.write(record)
        val owner = ProgressReceiptPolicy.key(record)
        val dispatched = progressDispatchSessions.remove(record.getString("id"))
        readingSessions[owner]?.let { session ->
            val sameScope = session.chapter.key == owner && session.chapter.index == parameters.getInt("chapterIndex") &&
                session.chapter.bodyVersion == parameters.getString("expectedBodyVersion") && session.chapter.processingVersion == parameters.getString("expectedProcessingVersion")
            // A remote old terminal never rewrites a later session's CAS. Cold reconcile requires a new entry observation.
            if (session.revision == parameters.getString("expectedProgressRevision") &&
                (owner.sourceId == "local" || (session === dispatched && sameScope))) session.revision = revision
        }
    }

    suspend fun pendingPositions(): List<PendingPosition> = onIO(WholeTurnMode.OBSERVER_READ) {
        journal.readAll().filter { it.getString("kind") == "progress" && !it.optBoolean("completed") }
            .map { PendingPosition(it.getString("id"), it.getString("bookId"), "reading.progress.update", ProgressReceiptPolicy.key(it).sourceId) }
    }

    /** Explicitly inspect one original ACK. No get, new save, cancellation or replay. */
    suspend fun reconcilePendingPosition(bookId: String, operationId: String) {
        localIdentity(bookId)
        reconcilePendingPosition(ShelfBookKey("local", bookId), operationId)
    }
    suspend fun reconcilePendingPosition(key: ShelfBookKey, operationId: String): Unit = onIO(WholeTurnMode.RETAIN_ORIGINAL) {
        if (key.sourceId == "local") localIdentity(key.bookId) else ReaderOnlineReadingPolicy.remote(key)
        var record = journal.exact(operationId, includeCompleted = true)
            ?: throw PendingOperationException(operationId, "reading.progress.update")
        check(record.getString("kind") == "progress" && ProgressReceiptPolicy.key(record) == key) {
            "ORIGINAL_PROGRESS_OWNER_MISMATCH"
        }
        if (!record.optBoolean("completed") && record.optJSONObject("wireTerminal") == null) {
            record = journal.progressCandidateForReconcile(operationId, key) ?: record
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
        require(filename.substringAfterLast('.', "").lowercase() in setOf("txt", "epub", "mobi", "azw", "azw3", "kf8")) { "LOCAL_EXTENSION_NOT_ADMITTED" }
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
            val format = ReaderLocalBookFormat.fromCore(summary.getString("format"))
            val integrity = summary.getJSONObject("integrity")
            check(integrity.getInt("schemaVersion") == 1 &&
                integrity.getString("readability") in setOf("complete", "recoverable") &&
                summary.getLong("byteLen") == size) { "UNREADABLE_LOCAL_SOURCE" }
            currentCoroutineContext().ensureActive()
            val authority = transport.request("import.local.authority.v2")
            ImportReceiptPolicy.authority(authority)
            check(authority.getInt("activeCount") < authority.getInt("activeLimit")) { "IMPORT_RECEIPT_CAPACITY" }
            val sequence = authority.getLong("headSequence")
            check(sequence in 0 until Long.MAX_VALUE) { "IMPORT_SEQUENCE_EXHAUSTED" }
            val binding = JSONObject().put("authorityEpoch", authority.getString("authorityEpoch"))
                .put("sequence", sequence + 1).put("transactionId", id).put("sourceId", "local")
                .put("bookId", bookId).put("sourceSha256", hash).put("sourceBytes", size).put("format", format.wire)
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
        sourceId = entry.getString("sourceId"),
        group = if (entry.isNull("group")) null else entry.getString("group"),
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
        if (closed.compareAndSet(false, true)) { positionIntent.set(null); retirePreparedEbook(); client.retire() }
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
