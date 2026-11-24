package io.reader.android.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalForInheritanceCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/** Application-owned turn gate. It is not bound to NativeRuntime or a UI lifetime yet. */
internal class ReaderOperationOwner private constructor(
    val dataDirectory: String,
    dispatcher: CoroutineDispatcher,
    private val cleanupTimeoutMs: Long,
    private val closeAction: suspend () -> Unit,
) {
    data class ClientLease internal constructor(
        val ownerId: UUID,
        val clientId: UUID,
        val ownerGeneration: Long,
        val localGeneration: Long,
    )

    data class PendingCleanup(val lease: ClientLease, val id: String, val failure: Throwable?)
    enum class CloseStatus { HostDrainedNativeQuiescenceUnconfirmed }
    data class CloseState internal constructor(
        val status: CloseStatus,
        val unresolvedCleanups: List<PendingCleanup>,
        val closeFailure: Throwable?,
    )

    class AdmissionRejected(message: String) : IllegalStateException(message)

    private enum class Phase { OPEN, CLOSING, HOST_DRAINED }
    private data class CleanupKey(val lease: ClientLease, val id: String)
    private class Cleanup(val action: suspend () -> Unit) {
        var attempted = false
        var failure: Throwable? = null
    }

    private val ownerId = UUID.randomUUID()
    private val stateLock = Any()
    private val operationMutex = Mutex()
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val cleanups = linkedMapOf<CleanupKey, Cleanup>()
    private var phase = Phase.OPEN
    private var generation = 0L
    private var client: ClientLease? = null
    private var activeTurn: Turn? = null
    private var activeMaintenance: OriginalMaintenance? = null
    private var activeCleanup: CleanupKey? = null
    private var closeResult: Deferred<CloseState>? = null

    /** A new owner generation retires previous admission, even if two VMs use the same local number. */
    fun activateClient(clientId: UUID, localGeneration: Long): ClientLease {
        val lease = synchronized(stateLock) {
            requireOpen()
            check(generation < Long.MAX_VALUE) { "READER_OWNER_GENERATION_EXHAUSTED" }
            ClientLease(ownerId, clientId, ++generation, localGeneration).also { client = it }
        }
        scheduleRetiredCleanup()
        return lease
    }

    /** Point-in-time admission observation only. Never takes operationMutex or runs a cleanup/native action. */
    fun assertCurrentAdmission(lease: ClientLease) = synchronized(stateLock) { requireAdmission(lease) }

    /** Retiring an old lease cannot retire a newly activated client. */
    fun retireClient(lease: ClientLease): Boolean {
        val retired = synchronized(stateLock) {
            if (phase == Phase.OPEN && client == lease) {
                client = null
                true
            } else false
        }
        if (retired) scheduleRetiredCleanup()
        return retired
    }

    suspend fun <T> withTurn(lease: ClientLease, block: suspend Turn.() -> T): T {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        synchronized(stateLock) { requireAdmission(lease) }
        // This Deferred belongs to the App scope. Cancelling await never cancels an admitted turn.
        val result = appScope.async {
            operationMutex.withLock {
                val turn = synchronized(stateLock) {
                    caller.ensureActive()
                    requireAdmission(lease)
                    Turn(lease).also { activeTurn = it }
                }
                try {
                    block(turn)
                } finally {
                    // The entire block, including its original journal finally, has finished first.
                    synchronized(stateLock) { activeTurn = null }
                    runRetiredCleanup()
                }
            }
        }
        return result.await()
    }

    /** AppCoreOwner alone calls this after exact actor-route lookup; never a native poller. */
    suspend fun <T> withOriginalMaintenance(
        lease: ClientLease, originalId: String, block: suspend OriginalMaintenance.() -> T,
    ): T {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        require(originalId.isNotBlank() && originalId.length <= 200)
        val result = appScope.async {
            operationMutex.withLock {
                val maintenance = synchronized(stateLock) {
                    caller.ensureActive()
                    requireOpen()
                    check(lease.ownerId == ownerId) { "FOREIGN_ORIGINAL_MAINTENANCE" }
                    // Retired client is intentional; it must never become current again.
                    OriginalMaintenance(lease, originalId).also { activeMaintenance = it }
                }
                try { block(maintenance) }
                finally {
                    synchronized(stateLock) { activeMaintenance = null }
                    runRetiredCleanup()
                }
            }
        }
        return result.await() // Cancellation stops observation, not an admitted original turn.
    }

    inner class OriginalMaintenance internal constructor(val lease: ClientLease, val originalId: String) {
        fun assertActive() = synchronized(stateLock) {
            check(activeMaintenance === this) { "ORIGINAL_MAINTENANCE_LANE_RETIRED" }
        }
    }

    fun assertCleanupActive(lease: ClientLease, id: String) = synchronized(stateLock) {
        check(activeCleanup == CleanupKey(lease, id)) { "FOREIGN_OR_RETIRED_CLEANUP_LANE" }
    }

    /** Read-only observation; failed cleanup stays here and is never silently replayed. */
    fun pendingCleanups(): List<PendingCleanup> = synchronized(stateLock) { unresolvedCleanups() }

    /** A current client may explicitly retry only the exact retained original cleanup. */
    suspend fun retryCleanup(lease: ClientLease, original: PendingCleanup): PendingCleanup? = withTurn(lease) {
        val key = CleanupKey(original.lease, original.id)
        val cleanup = synchronized(stateLock) {
            check(original.lease.ownerId == ownerId && isRetired(original.lease)) { "READER_CLEANUP_OWNER_MISMATCH" }
            cleanups[key]
        }
        if (cleanup != null) attemptCleanup(key, cleanup)
        synchronized(stateLock) { cleanups[key]?.let { PendingCleanup(key.lease, key.id, it.failure) } }
    }

    /** Stop admission immediately, then drain the existing whole turn before invoking close. */
    fun close(): Deferred<CloseState> {
        val result = synchronized(stateLock) {
            closeResult ?: run {
                phase = Phase.CLOSING
                client = null
                val worker = appScope.async(start = CoroutineStart.LAZY) {
                    operationMutex.withLock {
                        runRetiredCleanup()
                        var failure: Throwable? = null
                        try { closeAction() } catch (error: Throwable) { failure = error }
                        synchronized(stateLock) {
                            phase = Phase.HOST_DRAINED
                            CloseState(CloseStatus.HostDrainedNativeQuiescenceUnconfirmed,
                                unresolvedCleanups(), failure)
                        }
                    }
                }
                AwaitOnlyDeferred(worker).also { closeResult = it }
            }
        }
        result.start()
        return result
    }

    inner class Turn internal constructor(val lease: ClientLease) {
        fun assertActive() = synchronized(stateLock) {
            check(activeTurn === this) { "BUSINESS_TURN_RETIRED" }
        }
        /** Register the exact original resource cleanup; never a generic "close current cursor" callback. */
        fun registerRetiringCleanup(id: String, action: suspend () -> Unit) {
            require(id.isNotEmpty() && id.length <= 128)
            synchronized(stateLock) {
                check(activeTurn === this) { "RETIRED_READER_TURN" }
                val key = CleanupKey(lease, id)
                check(!cleanups.containsKey(key)) { "READER_CLEANUP_ALREADY_REGISTERED" }
                cleanups[key] = Cleanup(action)
            }
        }

        /** Call only after the original resource's explicit close was confirmed in this turn. */
        fun cleanupCompleted(id: String) {
            synchronized(stateLock) {
                check(activeTurn === this) { "RETIRED_READER_TURN" }
                cleanups.remove(CleanupKey(lease, id))
            }
        }
    }

    private fun scheduleRetiredCleanup() {
        val worker = synchronized(stateLock) {
            if (phase != Phase.OPEN || cleanups.none { isRetired(it.key.lease) && !it.value.attempted }) return
            appScope.launch(start = CoroutineStart.LAZY) {
                operationMutex.withLock {
                    if (synchronized(stateLock) { phase == Phase.OPEN }) runRetiredCleanup()
                }
            }
        }
        worker.start()
    }

    /** One finite attempt per registered retired cleanup, always under operationMutex. */
    private suspend fun runRetiredCleanup() {
        val eligible = synchronized(stateLock) {
            cleanups.filter { isRetired(it.key.lease) && !it.value.attempted }.toList()
        }
        for ((key, cleanup) in eligible) {
            attemptCleanup(key, cleanup)
        }
    }

    private suspend fun attemptCleanup(key: CleanupKey, cleanup: Cleanup) {
        synchronized(stateLock) { cleanup.attempted = true; activeCleanup = key }
        try {
            withTimeout(cleanupTimeoutMs) { cleanup.action() }
            synchronized(stateLock) { if (cleanups[key] === cleanup) cleanups.remove(key) }
        } catch (error: Throwable) {
            synchronized(stateLock) { cleanup.failure = error }
        } finally {
            synchronized(stateLock) { if (activeCleanup == key) activeCleanup = null }
        }
    }

    private fun unresolvedCleanups(): List<PendingCleanup> = cleanups.filter { isRetired(it.key.lease) }
        .map { (key, cleanup) -> PendingCleanup(key.lease, key.id, cleanup.failure) }

    private fun isRetired(lease: ClientLease): Boolean = phase != Phase.OPEN || client != lease
    private fun requireOpen() {
        if (phase != Phase.OPEN) throw AdmissionRejected("READER_OWNER_NOT_OPEN")
    }
    private fun requireAdmission(lease: ClientLease) {
        requireOpen()
        if (lease.ownerId != ownerId || lease != client) throw AdmissionRejected("READER_CLIENT_RETIRED")
    }

    /** Observation cancellation must not cancel shutdown or destroy its eventual result. */
    @OptIn(InternalForInheritanceCoroutinesApi::class)
    private class AwaitOnlyDeferred<T>(private val result: Deferred<T>) : Deferred<T> by result {
        override fun cancel(cause: CancellationException?) = Unit
    }

    companion object {
        // Keep the canonical claim for the process lifetime even after Host close.
        // The marker does not need to hold the closed runtime or its callbacks.
        private val claimedDirectories = mutableSetOf<String>()

        fun create(
            dataDirectory: File,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            cleanupTimeoutMs: Long = 1_000,
            closeAction: suspend () -> Unit,
        ): ReaderOperationOwner {
            require(cleanupTimeoutMs in 1..30_000)
            val directory = dataDirectory.canonicalPath
            return synchronized(claimedDirectories) {
                check(directory !in claimedDirectories) { "READER_DIRECTORY_OWNER_ALREADY_CLAIMED" }
                ReaderOperationOwner(directory, dispatcher, cleanupTimeoutMs, closeAction)
                    .also { claimedDirectories.add(directory) }
            }
        }
    }
}
