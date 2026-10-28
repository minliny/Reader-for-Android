package io.reader.android.runtime

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

/** The caller must select a whole business contract, not infer it from an individual RPC callback. */
internal enum class WholeTurnMode { OBSERVER_READ, RETAIN_ORIGINAL }

/** Optional bridge policy. Absence conservatively retains the entire request, including reads within writes. */
internal fun interface CoreReadRequestLane {
    fun observer(lease: ReaderOperationOwner.ClientLease): ReaderReadObservers.ReadObserver?
    companion object { val RetainAll = CoreReadRequestLane { null } }
}

/** Job identities only; no Native handle, native gate, event channel or second poller. */
internal class ReaderReadObservers {
    private val lock = Any()
    private var ownerId: UUID? = null
    private var highestGeneration = -1L
    private var activeLease: ReaderOperationOwner.ClientLease? = null
    private val reads = linkedMapOf<UUID, ReadObserver>()

    fun activate(lease: ReaderOperationOwner.ClientLease) {
        val old = synchronized(lock) {
            check(ownerId == null || ownerId == lease.ownerId) { "FOREIGN_READ_OBSERVER_OWNER" }
            ownerId = lease.ownerId
            // activateClient and this method may finish on different caller threads.
            // A delayed old activate must neither retire a new read nor revive a retired lease.
            if (lease.ownerGeneration <= highestGeneration) return
            highestGeneration = lease.ownerGeneration
            activeLease = lease
            reads.values.filter { it.lease != lease }.toList()
        }
        old.forEach { it.job.cancel() } // Never queue cancellation behind the read's held operation mutex.
    }
    fun retire(lease: ReaderOperationOwner.ClientLease) {
        val old = synchronized(lock) {
            check(ownerId == null || ownerId == lease.ownerId) { "FOREIGN_READ_OBSERVER_OWNER" }
            if (activeLease == lease) activeLease = null
            reads.values.filter { it.lease == lease }.toList()
        }
        old.forEach { it.job.cancel() }
    }
    fun open(lease: ReaderOperationOwner.ClientLease, callerJob: Job): ReadObserver = synchronized(lock) {
        check(activeLease == lease) { "READ_OBSERVER_CLIENT_RETIRED" }
        callerJob.ensureActive()
        val id = UUID.randomUUID()
        ReadObserver(id, lease, SupervisorJob(callerJob)) { exact ->
            synchronized(lock) { if (reads[id] === exact) reads.remove(id) }
        }.also { reads[id] = it }
    }
    class ReadObserver internal constructor(val id: UUID, val lease: ReaderOperationOwner.ClientLease,
        internal val job: CompletableJob, private val remove: (ReadObserver) -> Unit) {
        fun ensureActive() = job.ensureActive()
        fun closeObservation() { remove(this); job.complete() }
    }
}

/** Capture body cancellation as a result so the App-owned Turn/finally never inherits its Job cancellation. */
internal suspend fun <T> ReaderOperationOwner.withObservedRead(
    lease: ReaderOperationOwner.ClientLease,
    observer: ReaderReadObservers.ReadObserver,
    body: suspend ReaderOperationOwner.Turn.() -> T,
    protectedFinally: suspend ReaderOperationOwner.Turn.() -> Unit,
): T {
    val outcome: Result<T> = withTurn(lease) {
        val turn = this
        check(observer.lease == lease)
        var bodyOutcome: Result<T> = try {
            Result.success(withContext(observer.job) { observer.ensureActive(); body(turn).also { observer.ensureActive() } })
        } catch (failure: Throwable) { Result.failure(failure) }
        try { withContext(NonCancellable) { protectedFinally(turn) } }
        catch (cleanup: Throwable) {
            val primary = bodyOutcome.exceptionOrNull()
            if (primary != null) primary.addSuppressed(cleanup) else bodyOutcome = Result.failure(cleanup)
        }
        bodyOutcome // OperationOwner releases the gate / original retired cleanup before cancellation reaches the caller.
    }
    return outcome.getOrThrow()
}

internal fun <T> blockingObserverRequest(observer: ReaderReadObservers.ReadObserver?,
    action: suspend CoroutineScope.() -> T): T {
    observer?.ensureActive()
    return if (observer == null) runBlocking(block = action) else runBlocking(observer.job, action)
}

internal fun actorKindForObserver(observer: ReaderReadObservers.ReadObserver?, method: String, hasOriginalSink: Boolean): ReaderCoreEventActor.Kind {
    if (observer == null) return ReaderCoreEventActor.Kind.DURABLE_WRITE // Entire retained business turn, including read prelude.
    observer.ensureActive()
    check(!hasOriginalSink && method in setOf("bookshelf.list", "bookshelf.get", "reading.progress.get", "local_book.toc", "bookmark.list")) {
        "NON_PURE_REQUEST_IN_OBSERVER_READ_TURN"
    }
    return ReaderCoreEventActor.Kind.READ_ONLY
}
