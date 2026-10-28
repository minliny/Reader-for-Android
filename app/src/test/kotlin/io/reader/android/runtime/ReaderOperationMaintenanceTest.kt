package io.reader.android.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * UNAPPLIED pure JVM test draft, not AppCoreOwner or Native lifecycle evidence.
 * Requires ReaderOperationOwner-required.patch
 * SHA256 6292709c12b634eb467e3799bb63bb51cf6a165e057dfa5e2603056a8bb54a8e.
 * Reuses the existing Owner3's App-owned turn + CompletableDeferred barriers.
 * No real/fictional book, cursor wire result, database, network or Native port.
 */
class ReaderOperationMaintenanceTest {
    @Test fun retiredOriginalMaintenanceWaitsForWholeTurnAndFinallyAndStillExcludesNewBusiness(): Unit = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val events = CopyOnWriteArrayList<String>()
                val businessEntered = CompletableDeferred<Unit>()
                val finishBusiness = CompletableDeferred<Unit>()
                val finallyEntered = CompletableDeferred<Unit>()
                val finishFinally = CompletableDeferred<Unit>()
                val maintenanceEntered = CompletableDeferred<Unit>()
                val finishMaintenance = CompletableDeferred<Unit>()
                val maintenanceFinally = CompletableDeferred<Unit>()
                val newBusinessEntered = CompletableDeferred<Unit>()
                val owner = ReaderOperationOwner.create(directory(), dispatcher) { events += "close" }
                val old = owner.activateClient(UUID(0, 1), 7)
                lateinit var oldTurn: ReaderOperationOwner.Turn
                lateinit var originalLane: ReaderOperationOwner.OriginalMaintenance
                try {
                    val business = async(start = CoroutineStart.UNDISPATCHED) {
                        owner.withTurn(old) {
                            oldTurn = this
                            assertActive()
                            try {
                                events += "business"
                                businessEntered.complete(Unit)
                                finishBusiness.await()
                            } finally {
                                events += "business-finally-enter"
                                finallyEntered.complete(Unit)
                                finishFinally.await()
                                events += "business-finally-exit"
                            }
                        }
                    }
                    businessEntered.await()
                    val current = owner.activateClient(UUID(0, 2), 7)
                    val maintenanceObserver = launch(start = CoroutineStart.UNDISPATCHED) {
                        owner.withOriginalMaintenance(old, "original-receipt") {
                            originalLane = this
                            assertEquals(old, lease)
                            assertEquals("original-receipt", originalId)
                            assertActive()
                            try {
                                events += "maintenance-enter"
                                maintenanceEntered.complete(Unit)
                                finishMaintenance.await()
                                assertActive()
                                events += "maintenance-body-finished"
                            } finally {
                                events += "maintenance-finally"
                                maintenanceFinally.complete(Unit)
                            }
                        }
                    }
                    schedulerFence(executor)
                    assertFalse(maintenanceEntered.isCompleted)
                    finishBusiness.complete(Unit)
                    finallyEntered.await()
                    schedulerFence(executor)
                    assertFalse(maintenanceEntered.isCompleted)
                    finishFinally.complete(Unit)
                    business.await()
                    maintenanceEntered.await()
                    assertThrows(IllegalStateException::class.java) { oldTurn.assertActive() }
                    rejected { owner.withTurn(old) { throw AssertionError("Retired lease entered business") } }
                    val newBusiness = async(start = CoroutineStart.UNDISPATCHED) {
                        owner.withTurn(current) {
                            assertActive()
                            events += "new-business"
                            newBusinessEntered.complete(Unit)
                        }
                    }
                    schedulerFence(executor)
                    assertFalse(newBusinessEntered.isCompleted)
                    // Cancellation ends this observer, not the admitted original maintenance.
                    maintenanceObserver.cancelAndJoin()
                    assertFalse(maintenanceFinally.isCompleted)
                    finishMaintenance.complete(Unit)
                    maintenanceFinally.await()
                    newBusiness.await()
                    assertThrows(IllegalStateException::class.java) { originalLane.assertActive() }
                    owner.close().await()
                    assertEquals(listOf("business", "business-finally-enter", "business-finally-exit",
                        "maintenance-enter", "maintenance-body-finished", "maintenance-finally", "new-business", "close"), events)
                } finally {
                    finishBusiness.complete(Unit); finishFinally.complete(Unit); finishMaintenance.complete(Unit)
                    withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } }
                }
            }
        }
    }

    @Test fun foreignOwnerAndClosingRejectOriginalMaintenanceWithoutRunningItsBlock(): Unit = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val entered = CompletableDeferred<Unit>()
                val finishTurn = CompletableDeferred<Unit>()
                val maintenanceCalls = AtomicInteger()
                val closeCalls = AtomicInteger()
                val owner = ReaderOperationOwner.create(directory(), dispatcher) { closeCalls.incrementAndGet() }
                val foreignOwner = ReaderOperationOwner.create(directory(), dispatcher) {}
                val local = owner.activateClient(UUID(0, 3), 9)
                val foreign = foreignOwner.activateClient(local.clientId, local.localGeneration)
                try {
                    assertEquals(local.clientId, foreign.clientId)
                    assertEquals(local.ownerGeneration, foreign.ownerGeneration)
                    assertEquals(local.localGeneration, foreign.localGeneration)
                    assertFalse(local.ownerId == foreign.ownerId)
                    val wrongOwner = fails<IllegalStateException> {
                        owner.withOriginalMaintenance(foreign, "foreign-original") { maintenanceCalls.incrementAndGet() }
                    }
                    assertEquals("FOREIGN_ORIGINAL_MAINTENANCE", wrongOwner.message)
                    val business = async(start = CoroutineStart.UNDISPATCHED) {
                        owner.withTurn(local) { entered.complete(Unit); finishTurn.await() }
                    }
                    entered.await()
                    val queuedBeforeClose = async(start = CoroutineStart.UNDISPATCHED) {
                        rejected { owner.withOriginalMaintenance(local, "queued-original") { maintenanceCalls.incrementAndGet() } }
                    }
                    schedulerFence(executor)
                    val closed = owner.close() // closes admission synchronously, while the original turn still holds the mutex.
                    val requestedAfterClose = async(start = CoroutineStart.UNDISPATCHED) {
                        rejected { owner.withOriginalMaintenance(local, "after-close") { maintenanceCalls.incrementAndGet() } }
                    }
                    schedulerFence(executor)
                    assertFalse(closed.isCompleted)
                    assertEquals(0, maintenanceCalls.get())
                    finishTurn.complete(Unit)
                    business.await(); queuedBeforeClose.await(); requestedAfterClose.await()
                    val report = closed.await()
                    assertEquals(1, closeCalls.get())
                    assertEquals(0, maintenanceCalls.get())
                    assertEquals(ReaderOperationOwner.CloseStatus.HostDrainedNativeQuiescenceUnconfirmed, report.status)
                    rejected { owner.withOriginalMaintenance(local, "already-closed") { maintenanceCalls.incrementAndGet() } }
                    assertEquals(0, maintenanceCalls.get())
                } finally {
                    finishTurn.complete(Unit)
                    withContext(NonCancellable) {
                        withTimeout(2_000) { owner.close().await(); foreignOwner.close().await() }
                    }
                }
            }
        }
    }

    @Test fun cleanupRequiresExactLeaseAndIdRetiresAfterFailureAndOnlyExplicitRetryRunsAgain(): Unit = runBlocking {
        val executor = Executors.newSingleThreadExecutor()
        executor.asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val turnRegistered = CompletableDeferred<Unit>()
                val finishTurn = CompletableDeferred<Unit>()
                val cleanupEntered = CompletableDeferred<Unit>()
                val failOriginal = CompletableDeferred<Unit>()
                val oldAttempts = AtomicInteger()
                val actionFinallys = AtomicInteger()
                val newCleanupCalls = AtomicInteger()
                val originalFailure = IOException("original cleanup unconfirmed")
                val owner = ReaderOperationOwner.create(directory(), dispatcher, cleanupTimeoutMs = 2_000) {}
                val old = owner.activateClient(UUID(0, 4), 12)
                lateinit var current: ReaderOperationOwner.ClientLease
                val id = "same-resource-id"
                try {
                    val original = async(start = CoroutineStart.UNDISPATCHED) {
                        owner.withTurn(old) {
                            registerRetiringCleanup(id) {
                                val attempt = oldAttempts.incrementAndGet()
                                try {
                                    owner.assertCleanupActive(old, id)
                                    for (wrong in listOf(current, old.copy(ownerId = UUID(8, 1)), old.copy(clientId = UUID(8, 2)),
                                        old.copy(ownerGeneration = old.ownerGeneration + 1), old.copy(localGeneration = old.localGeneration + 1))) {
                                        assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(wrong, id) }
                                    }
                                    assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(old, "other-id") }
                                    if (attempt == 1) {
                                        cleanupEntered.complete(Unit)
                                        failOriginal.await()
                                        throw originalFailure
                                    }
                                } finally { actionFinallys.incrementAndGet() }
                            }
                            turnRegistered.complete(Unit)
                            finishTurn.await()
                        }
                    }
                    turnRegistered.await()
                    current = owner.activateClient(UUID(0, 5), old.localGeneration)
                    finishTurn.complete(Unit)
                    cleanupEntered.await()
                    failOriginal.complete(Unit)
                    original.await()
                    // Acquiring the same gate proves attemptCleanup's catch/finally have completed.
                    owner.withTurn(current) {
                        assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(old, id) }
                        registerRetiringCleanup(id) {
                            owner.assertCleanupActive(current, id)
                            assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(old, id) }
                            newCleanupCalls.incrementAndGet()
                        }
                    }
                    val pending = owner.pendingCleanups().single()
                    assertEquals(old, pending.lease)
                    assertEquals(id, pending.id)
                    val stored = checkNotNull(pending.failure)
                    assertEquals(originalFailure.javaClass, stored.javaClass)
                    assertEquals(originalFailure.message, stored.message)
                    assertTrue(generateSequence(stored) { it.cause }.take(16).any { it === originalFailure })
                    assertEquals(1, oldAttempts.get())
                    assertEquals(1, actionFinallys.get())
                    owner.withOriginalMaintenance(old, "late-original-terminal") { assertActive() }
                    owner.withTurn(current) {}
                    assertEquals(1, oldAttempts.get()) // No maintenance/finally/background replay of a failed cleanup.
                    assertEquals(0, newCleanupCalls.get())
                    rejected { owner.retryCleanup(old, pending) }
                    assertNull(owner.retryCleanup(current, pending.copy(id = "other-id")))
                    assertEquals(pending.lease, owner.pendingCleanups().single().lease)
                    assertEquals(pending.id, owner.pendingCleanups().single().id)
                    assertEquals(1, oldAttempts.get())
                    assertNull(owner.retryCleanup(current, pending))
                    assertEquals(2, oldAttempts.get())
                    assertEquals(2, actionFinallys.get())
                    assertTrue(owner.pendingCleanups().isEmpty())
                    assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(old, id) }
                    assertEquals(0, newCleanupCalls.get())
                    owner.close().await()
                    assertEquals(1, newCleanupCalls.get())
                    assertEquals(2, oldAttempts.get())
                    assertThrows(IllegalStateException::class.java) { owner.assertCleanupActive(current, id) }
                } finally {
                    finishTurn.complete(Unit); failOriginal.complete(Unit)
                    withContext(NonCancellable) { withTimeout(2_000) { owner.close().await() } }
                }
            }
        }
    }

    /** A FIFO executor barrier lets every already queued owner coroutine reach its mutex suspension. */
    private suspend fun schedulerFence(executor: ExecutorService) {
        val reached = CompletableDeferred<Unit>()
        executor.execute { reached.complete(Unit) }
        reached.await()
    }

    private fun directory() = File(System.getProperty("java.io.tmpdir"), "reader-maintenance-test-${UUID.randomUUID()}")

    private suspend inline fun <reified T : Throwable> fails(noinline block: suspend () -> Unit): T {
        try { block() } catch (error: Throwable) { if (error is T) return error; throw error }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private suspend fun rejected(block: suspend () -> Unit) {
        fails<ReaderOperationOwner.AdmissionRejected>(block)
    }
}
