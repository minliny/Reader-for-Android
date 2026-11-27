package io.reader.rebuild.features.downloads

import com.reader.core.protocol.M2CacheBookPrefetchData
import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheBookPrefetchParamsContentKind
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2CacheChapterMaterializationReportData
import com.reader.core.protocol.M2CacheChapterMaterializationReportParams
import com.reader.core.protocol.M2CacheChapterMaterializationReportParamsOutcome
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2CacheDownloadOperationQueryParams
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction.CANCEL
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction.PAUSE
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction.PREFETCH
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction.REPORT
import com.reader.core.protocol.M2CacheDownloadOperationQueryParamsAction.RESUME
import com.reader.core.protocol.M2CacheDownloadOperationSettleParams
import com.reader.core.protocol.M2CacheDownloadOperationSettleParamsAction
import com.reader.core.protocol.M2CacheDownloadTaskParams
import com.reader.core.protocol.M2CacheDownloadTaskResult
import com.reader.core.protocol.M2CacheStorageParams
import com.reader.core.protocol.M2ChapterContentParams
import com.reader.core.protocol.M2DownloadOperationExpected
import com.reader.core.protocol.M2DownloadOperationExpectedCancel
import com.reader.core.protocol.M2DownloadOperationExpectedCancelParams
import com.reader.core.protocol.M2DownloadOperationExpectedPause
import com.reader.core.protocol.M2DownloadOperationExpectedPauseParams
import com.reader.core.protocol.M2DownloadOperationExpectedPrefetch
import com.reader.core.protocol.M2DownloadOperationExpectedPrefetchParams
import com.reader.core.protocol.M2DownloadOperationExpectedPrefetchParamsContentKind
import com.reader.core.protocol.M2DownloadOperationExpectedReport
import com.reader.core.protocol.M2DownloadOperationExpectedReportParams
import com.reader.core.protocol.M2DownloadOperationExpectedReportParamsOutcome
import com.reader.core.protocol.M2DownloadOperationExpectedResume
import com.reader.core.protocol.M2DownloadOperationExpectedResumeParams
import com.reader.core.protocol.M2DownloadOperationReceipt
import com.reader.core.protocol.M2DownloadOperationReceiptPhase
import com.reader.core.protocol.M2ReadingDocumentBlockDataImage
import com.reader.core.protocol.ReaderM2
import io.reader.rebuild.core.RequestIdentity
import io.reader.rebuild.features.reading.OnlineBookKey
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import io.reader.rebuild.features.reading.requireOnlineText
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DownloadOperationUnresolved(val original: M2CacheDownloadOperationQueryParams) :
    IllegalStateException("Download operation requires its original receipt")

internal class DownloadOperation(private val terminal: CompletableDeferred<M2CacheBookPrefetchData>,
    private val id: CompletableDeferred<RequestIdentity>) {
    suspend fun identity(): RequestIdentity = id.await()
    suspend fun await(): M2CacheBookPrefetchData = terminal.await()
}

internal class DownloadCoordinator(private val reading: OnlineReadingCoordinator, private val journal: DownloadJournal) {
    private val core get() = reading.core
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monitor = Any()
    private val owners = mutableSetOf<OnlineBookKey>()
    private val controls = mutableSetOf<Pair<OnlineBookKey, Long>>()
    private val panel = AtomicLong()
    private var closed = false
    val panelGeneration: Long get() = panel.get()

    fun retirePanel(): Long = panel.incrementAndGet()
    fun isWorking(sourceId: String, bookId: String): Boolean = synchronized(monitor) {
        OnlineBookKey(sourceId, bookId) in owners
    }

    fun begin(draft: M2CacheBookPrefetchParams): DownloadOperation {
        val key = OnlineBookKey(draft.sourceId, draft.bookId)
        reserve(key)
        val terminal = CompletableDeferred<M2CacheBookPrefetchData>()
        val identity = CompletableDeferred<RequestIdentity>()
        val job = scope.launch {
            val final = runCatching {
                val params = bind(draft)
                val exact = exactPrefetch(params)
                journal.remember(params)
                val operation = core.submit(ReaderM2.cacheBookPrefetch, params)
                identity.complete(operation.identity)
                val result = operation.await()
                val receipt = confirm(exact)
                materialize(receipt, params)
                journal.forget(requireNotNull(params.operationId))
                result
            }
            synchronized(monitor) {
                owners.remove(key)
                final.exceptionOrNull()?.let(identity::completeExceptionally)
                final.fold(terminal::complete, terminal::completeExceptionally)
            }
        }
        job.invokeOnCompletion { failure ->
            if (failure != null) {
                synchronized(monitor) {
                    owners.remove(key)
                    identity.completeExceptionally(failure)
                    terminal.completeExceptionally(failure)
                }
            }
        }
        return DownloadOperation(terminal, identity)
    }

    suspend fun queryPrefetch(params: M2CacheBookPrefetchParams) = query(exactPrefetch(params))

    suspend fun recoverSavedPrefetches(): List<M2DownloadOperationReceipt> =
        withContext(Dispatchers.IO) { journal.entries().map { recoverPrefetch(it.prefetch) } }

    suspend fun recoverPrefetch(params: M2CacheBookPrefetchParams): M2DownloadOperationReceipt {
        val book = OnlineBookKey(params.sourceId, params.bookId)
        reserve(book)
        try {
            return withContext(NonCancellable + Dispatchers.IO) {
                val expected = exactPrefetch(params)
                val original = query(expected)
                val neverAdmitted = original.receipt == null && (original.head?.sequence ?: 0L) < requireNotNull(params.sequence)
                if (original.canResumeOriginal || neverAdmitted) core.call(ReaderM2.cacheBookPrefetch, params)
                val observed = confirm(expected)
                materialize(observed, params)
                journal.forget(requireNotNull(params.operationId))
                observed
            }
        } finally {
            synchronized(monitor) { owners.remove(book) }
        }
    }

    suspend fun query(params: M2CacheDownloadOperationQueryParams) = core.call(ReaderM2.cacheDownloadOperationQuery, params)
    suspend fun status(params: M2CacheBookStatusParams) = core.call(ReaderM2.cacheBookStatus, params)
    suspend fun summary(params: M2CacheStorageParams = M2CacheStorageParams()) = core.call(ReaderM2.cacheStorageSummary, params)
    suspend fun cleanup(params: M2CacheStorageParams) = core.call(ReaderM2.cacheStorageCleanup, params)

    suspend fun pause(params: M2CacheDownloadTaskParams) = control(PAUSE, params)
    suspend fun resume(params: M2CacheDownloadTaskParams) = control(RESUME, params)
    suspend fun cancel(params: M2CacheDownloadTaskParams) = control(CANCEL, params)

    fun close() {
        synchronized(monitor) { closed = true }
        scope.cancel()
    }

    private fun reserve(book: OnlineBookKey) = synchronized(monitor) {
        check(!closed && owners.add(book)) { "The previous download still owns the book" }
    }

    private suspend fun bind(draft: M2CacheBookPrefetchParams): M2CacheBookPrefetchParams {
        require(draft.contentKind != M2CacheBookPrefetchParamsContentKind.MANGA)
        require((draft.operationId == null) == (draft.sequence == null))
        if (draft.operationId != null) return draft
        val head = query(M2CacheDownloadOperationQueryParams(draft.sourceId, draft.bookId, action = PREFETCH)).head
        val sequence = (head?.sequence ?: 0L) + 1L
        return draft.copy(operationId = operationId(sequence), sequence = sequence)
    }

    private fun exactPrefetch(params: M2CacheBookPrefetchParams): M2CacheDownloadOperationQueryParams {
        require(params.contentKind != M2CacheBookPrefetchParamsContentKind.MANGA)
        val id = requireNotNull(params.operationId)
        val expected = M2DownloadOperationExpectedPrefetch(params = M2DownloadOperationExpectedPrefetchParams(
            preparationRevision = params.preparationRevision, sourceId = params.sourceId, bookId = params.bookId,
            chapterRange = params.chapterRange, priority = params.priority, requestedAt = params.requestedAt,
            contentKind = params.contentKind?.let { M2DownloadOperationExpectedPrefetchParamsContentKind.fromWire(it.wireValue) },
            operationId = id, sequence = requireNotNull(params.sequence)))
        return M2CacheDownloadOperationQueryParams(params.sourceId, params.bookId, id, expected, PREFETCH)
    }

    private suspend fun confirm(exact: M2CacheDownloadOperationQueryParams): M2DownloadOperationReceipt {
        val receipt = query(exact).receipt ?: throw DownloadOperationUnresolved(exact)
        if (receipt.phase == M2DownloadOperationReceiptPhase.PENDING) throw DownloadOperationUnresolved(exact)
        if (receipt.phase == M2DownloadOperationReceiptPhase.SETTLED) return receipt
        val binding = receipt.binding
        val action = requireNotNull(M2CacheDownloadOperationSettleParamsAction.fromWire(exact.action.wireValue))
        return core.call(ReaderM2.cacheDownloadOperationSettle, M2CacheDownloadOperationSettleParams(
            binding.operationId, binding.sequence, binding.payloadFingerprint, exact.sourceId, exact.bookId, action,
            exact.chapterIndex)).also { check(it.binding == binding && it.phase == M2DownloadOperationReceiptPhase.SETTLED) }
    }

    private suspend fun control(action: M2CacheDownloadOperationQueryParamsAction,
        draft: M2CacheDownloadTaskParams): M2CacheDownloadTaskResult {
        require((draft.operationId == null) == (draft.sequence == null))
        val key = OnlineBookKey(draft.sourceId, draft.bookId) to draft.chapterIndex
        synchronized(monitor) { check(controls.add(key)) { "An original chapter operation is still active" } }
        try {
            return withContext(NonCancellable) {
                val head = query(M2CacheDownloadOperationQueryParams(draft.sourceId, draft.bookId, action = action,
                    chapterIndex = draft.chapterIndex)).head
                val sequence = draft.sequence ?: (head?.sequence ?: 0L) + 1L
                val id = draft.operationId ?: operationId(sequence)
                val params = draft.copy(operationId = id, sequence = sequence)
                val expected = expectedControl(action, params, id, sequence)
                val exact = M2CacheDownloadOperationQueryParams(params.sourceId, params.bookId, id, expected, action,
                    params.chapterIndex)
                val rpc = when (action) {
                    PAUSE -> ReaderM2.cacheDownloadPause
                    RESUME -> ReaderM2.cacheDownloadResume
                    CANCEL -> ReaderM2.cacheDownloadCancel
                    else -> error("Invalid download task control")
                }
                core.call(rpc, params)
                val result = checkNotNull(confirm(exact).data as? M2CacheDownloadTaskResult)
                check(result.sourceId == params.sourceId && result.bookId == params.bookId &&
                    result.chapterIndex == params.chapterIndex)
                result
            }
        } finally {
            synchronized(monitor) { controls.remove(key) }
        }
    }

    private fun expectedControl(action: M2CacheDownloadOperationQueryParamsAction, p: M2CacheDownloadTaskParams,
        id: String, sequence: Long): M2DownloadOperationExpected = when (action) {
        PAUSE -> M2DownloadOperationExpectedPause(params = M2DownloadOperationExpectedPauseParams(
            p.sourceId, p.bookId, p.chapterIndex, p.now, id, sequence))
        RESUME -> M2DownloadOperationExpectedResume(params = M2DownloadOperationExpectedResumeParams(
            p.sourceId, p.bookId, p.chapterIndex, p.now, id, sequence))
        CANCEL -> M2DownloadOperationExpectedCancel(params = M2DownloadOperationExpectedCancelParams(
            p.sourceId, p.bookId, p.chapterIndex, p.now, id, sequence))
        else -> error("Invalid download task control")
    }

    private suspend fun materialize(receipt: M2DownloadOperationReceipt, original: M2CacheBookPrefetchParams) {
        val result = checkNotNull(receipt.data as? M2CacheBookPrefetchData)
        check(result.sourceId == original.sourceId && result.bookId == original.bookId && result.chapterRange == original.chapterRange)
        val batch = requireNotNull(original.operationId)
        for (lease in result.materializations) {
            check(lease.manga == null) { "M2 supports text operations only" }
            val key = OnlineBookKey(result.sourceId, result.bookId) to lease.chapterIndex
            synchronized(monitor) { check(controls.add(key)) { "A chapter control owns the lease" } }
            try {
                val existing = journal.entries().singleOrNull { it.prefetch.operationId == batch }
                    ?.reports?.singleOrNull { it.chapterIndex == lease.chapterIndex && it.token == lease.token }
                val report = existing ?: makeMaterializationReport(result, lease.chapterIndex, lease.token)
                    .also { journal.rememberReport(batch, it) }
                reportMaterialization(report)
            } finally {
                synchronized(monitor) { controls.remove(key) }
            }
        }
    }

    private suspend fun makeMaterializationReport(result: M2CacheBookPrefetchData, index: Long, token: String):
        M2CacheChapterMaterializationReportParams {
        val head = query(M2CacheDownloadOperationQueryParams(result.sourceId, result.bookId, action = REPORT,
            chapterIndex = index)).head
        val body = reading.chapter(M2ChapterContentParams(cacheOnly = true, sourceId = result.sourceId,
            bookId = result.bookId, chapterIndex = index))
        val error = if (runCatching { body.requireOnlineText() }.isFailure || body.bodyVersion == null ||
            body.processingVersion == null || body.contentRefreshRequired == true || body.sourceCorrectionRequired == true ||
            body.blocks.isNullOrEmpty()) "OFFLINE_BODY_PROJECTION_UNAVAILABLE"
        else if (body.blocks.filterIsInstance<M2ReadingDocumentBlockDataImage>().any { !hasOfflineBytes(it.source) }) {
            "OFFLINE_IMAGE_BYTES_UNAVAILABLE"
        } else null
        val outcome = if (error == null) M2CacheChapterMaterializationReportParamsOutcome.COMPLETED
            else M2CacheChapterMaterializationReportParamsOutcome.FAILED
        val sequence = (head?.sequence ?: 0L) + 1L
        return M2CacheChapterMaterializationReportParams(result.sourceId, result.bookId, index, token, outcome,
            errorCode = error, operationId = operationId(sequence), sequence = sequence)
    }

    private fun hasOfflineBytes(source: String): Boolean {
        val header = source.substringBefore(',')
        if (!header.startsWith("data:image/") || !header.endsWith(";base64") || source.length > 12 * 1024 * 1024) return false
        return runCatching { Base64.getDecoder().decode(source.substringAfter(',')).isNotEmpty() }.getOrDefault(false)
    }

    private suspend fun reportMaterialization(report: M2CacheChapterMaterializationReportParams) {
        val id = requireNotNull(report.operationId)
        val sequence = requireNotNull(report.sequence)
        val outcome = requireNotNull(M2DownloadOperationExpectedReportParamsOutcome.fromWire(report.outcome.wireValue))
        val expected = M2DownloadOperationExpectedReport(params = M2DownloadOperationExpectedReportParams(
            report.sourceId, report.bookId, report.chapterIndex, report.token, outcome, report.errorCode,
            report.reportedAt, report.manga, id, sequence))
        val exact = M2CacheDownloadOperationQueryParams(report.sourceId, report.bookId, id, expected, REPORT, report.chapterIndex)
        if (query(exact).receipt == null) core.call(ReaderM2.cacheChapterMaterializationReport, report)
        val result = checkNotNull(confirm(exact).data as? M2CacheChapterMaterializationReportData)
        check(result.sourceId == report.sourceId && result.bookId == report.bookId && result.chapterIndex == report.chapterIndex)
        check(result.state == M2CacheChapterState.PAUSED || result.state == M2CacheChapterState.CANCELLED ||
            result.retainedCachedBody && result.state.wireValue == report.outcome.wireValue)
    }

    private fun operationId(sequence: Long): String = "${UUID.randomUUID()}/$sequence"
}
