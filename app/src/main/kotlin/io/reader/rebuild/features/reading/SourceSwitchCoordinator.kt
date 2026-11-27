package io.reader.rebuild.features.reading

import com.reader.core.protocol.M2ChangeBookSourceParams
import com.reader.core.protocol.M2EmptyParams
import com.reader.core.protocol.M2SourceSwitchCommitParams
import com.reader.core.protocol.M2SourceSwitchTransactionParams
import com.reader.core.protocol.M2SourceSwitchTransactionPhase
import com.reader.core.protocol.M2SourceSwitchTransactionQueryParams
import com.reader.core.protocol.M2TextReadingProgressUpdateParams
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SwitchUnresolved(val query: M2SourceSwitchTransactionQueryParams, cause: Throwable) :
    IllegalStateException("The original source switch requires verification", cause)

internal class SourceSwitchCoordinator(private val reader: OnlineReadingCoordinator) {
    private val core get() = reader.core
    private val transitions = Mutex()

    suspend fun candidates(params: M2ChangeBookSourceParams) = core.call(ReaderM2.changeBookSource, params)
    suspend fun query(params: M2SourceSwitchTransactionQueryParams) = core.call(ReaderM2.sourceSwitchTransactionQuery, params)
    suspend fun pending() = core.call(ReaderM2.sourceSwitchPendingList, M2EmptyParams())
    suspend fun recover() = core.call(ReaderM2.sourceSwitchRecover, M2EmptyParams())

    suspend fun switch(current: OnlineChapter, draft: M2SourceSwitchCommitParams, scalar: Long = current.scalar):
        OnlineChapter = transitions.withLock {
        check(current.catalog.book == OnlineBookKey(draft.from.sourceId, draft.from.bookId) &&
            current.entry.index == draft.currentChapterIndex)
        require(scalar in 0 until current.scalarCount)
        withContext(NonCancellable) {
            val sourceWrite = reader.save(current, scalar).await()
            check(sourceWrite.stored && sourceWrite.chapterOffset == scalar && sourceWrite.chapterIndex == current.entry.index)
            val original = bind(draft)
            val id = requireNotNull(original.operationId)
            val evidence = M2SourceSwitchTransactionQueryParams(id, expected = original)
            val commit = try {
                core.call(ReaderM2.sourceSwitchCommit, original)
            } catch (failure: Exception) {
                runCatching { query(evidence).transaction?.commit }.getOrNull() ?: throw SwitchUnresolved(evidence, failure)
            }
            check(commit.phase == M2SourceSwitchTransactionPhase.PENDING && commit.transactionId == id &&
                commit.book.sourceId == draft.target.sourceId && commit.book.bookId == draft.target.bookId)
            val target = try {
                val catalog = checkNotNull(reader.cachedDirectory(OnlineBookKey(commit.book.sourceId, commit.book.bookId)))
                val open = reader.open(catalog, commit.matchedChapter.order)
                check(open.entry.url == commit.matchedChapter.chapterUrl)
                val sameText = open.text == current.text && open.entry.index == current.entry.index
                open.copy(scalar = if (sameText) scalar else 0L)
            } catch (failure: Exception) {
                try {
                    val rollback = core.call(ReaderM2.sourceSwitchRollback, M2SourceSwitchTransactionParams(id))
                    check(rollback.transactionId == id)
                } catch (rollback: Exception) {
                    failure.addSuppressed(rollback)
                    throw SwitchUnresolved(evidence, failure)
                }
                throw failure
            }
            val write = reader.progressUpdate(target, target.scalar, id, original.updatedAt)
            val exact = evidence.copy(expectedCanonical = M2TextReadingProgressUpdateParams(
                expectedProgressRevision = write.expectedProgressRevision, bookId = write.bookId, sourceId = write.sourceId,
                updatedAt = write.updatedAt, chapterIndex = write.chapterIndex, chapterOffset = write.chapterOffset,
                chapterProgress = write.chapterProgress, chapterTitle = write.chapterTitle,
                transactionId = write.transactionId, expectedBodyVersion = write.expectedBodyVersion,
                expectedProcessingVersion = write.expectedProcessingVersion))
            val revision = try {
                val receipt = core.call(ReaderM1.readingProgressUpdate, write)
                check(receipt.stored && receipt.transactionId == id && receipt.sourceSwitchFinalized == true &&
                    receipt.chapterIndex == target.entry.index && receipt.chapterOffset == target.scalar)
                requireNotNull(receipt.progressRevision)
            } catch (failure: Exception) {
                val receipt = runCatching { query(exact).transaction?.canonicalProgress }.getOrNull()
                    ?.takeIf { it.stored && it.transactionId == id && it.sourceSwitchFinalized == true &&
                        it.chapterIndex == target.entry.index && it.chapterOffset == target.scalar }
                receipt?.progressRevision ?: throw SwitchUnresolved(exact, failure)
            }
            target.copy(progressRevision = revision)
        }
    }

    private suspend fun bind(draft: M2SourceSwitchCommitParams): M2SourceSwitchCommitParams {
        if (draft.operationId != null && draft.sequence != null) return draft
        require(draft.operationId == null && draft.sequence == null)
        val prior = query(M2SourceSwitchTransactionQueryParams("${UUID.randomUUID()}/1")).head
        val sequence = (prior?.sequence ?: 0) + 1
        return draft.copy(operationId = "${UUID.randomUUID()}/$sequence", sequence = sequence)
    }
}
