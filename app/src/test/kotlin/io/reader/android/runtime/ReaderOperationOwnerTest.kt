package io.reader.android.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

class ReaderOperationOwnerTest {
    @Test fun closeWaitsForAllStagesAndOriginalFinally(): Unit = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val events = CopyOnWriteArrayList<String>()
                val entered = CompletableDeferred<Unit>()
                val finishStages = CompletableDeferred<Unit>()
                val enteredFinally = CompletableDeferred<Unit>()
                val finishFinally = CompletableDeferred<Unit>()
                val closeEntered = CompletableDeferred<Unit>()
                val directory = directory()
                val owner = ReaderOperationOwner.create(directory, dispatcher) { events += "close"; closeEntered.complete(Unit) }
                val lease = owner.activateClient(UUID.randomUUID(), 1)
                val caller = async {
                    owner.withTurn(lease) {
                        try {
                            events += "prepare"; entered.complete(Unit)
                            finishStages.await()
                            events += "persist"; events += "settle"
                        } finally {
                            events += "finally-enter"; enteredFinally.complete(Unit)
                            finishFinally.await(); events += "finally-exit"
                        }
                    }
                }
                entered.await()
                val closed = owner.close()
                assertSame(closed, owner.close())
                closed.cancel() // An observer cannot cancel the App-owned close action.
                assertFalse(closed.isCancelled)
                assertFalse(closeEntered.isCompleted)
                rejected { owner.withTurn(lease) { error("must not enter") } }
                finishStages.complete(Unit); enteredFinally.await()
                assertFalse(closeEntered.isCompleted)
                finishFinally.complete(Unit); caller.await()
                val result = closed.await()
                assertEquals(listOf("prepare", "persist", "settle", "finally-enter", "finally-exit", "close"), events)
                assertEquals(ReaderOperationOwner.CloseStatus.HostDrainedNativeQuiescenceUnconfirmed, result.status)
                assertNull(result.closeFailure)
                assertThrows(IllegalStateException::class.java) { ReaderOperationOwner.create(directory, dispatcher) {} }
            }
        }
    }

    @Test fun callerCancellationAndRetirementDoNotCancelAdmittedWrite(): Unit = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val events = CopyOnWriteArrayList<String>()
                val admitted = CompletableDeferred<Unit>()
                val continueWrite = CompletableDeferred<Unit>()
                val originalFinally = CompletableDeferred<Unit>()
                val owner = owner(dispatcher) { events += "close" }
                val lease = owner.activateClient(UUID.randomUUID(), 7)
                val caller = launch {
                    owner.withTurn(lease) {
                        try {
                            events += "write-1"; admitted.complete(Unit)
                            continueWrite.await(); events += "write-2"
                        } finally { events += "original-finally"; originalFinally.complete(Unit) }
                    }
                }
                admitted.await()
                assertTrue(owner.retireClient(lease))
                caller.cancel(); caller.join()
                assertFalse(originalFinally.isCompleted)
                rejected { owner.withTurn(lease) { error("retired client entered") } }
                continueWrite.complete(Unit); originalFinally.await(); owner.close().await()
                assertEquals(listOf("write-1", "write-2", "original-finally", "close"), events)
            }
        }
    }

    @Test fun equalLocalGenerationsCannotAdmitOrClearAnotherClientCleanup(): Unit = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            withTimeout(5_000) {
                val cleanupEvents = CopyOnWriteArrayList<String>()
                val unresolved = IllegalStateException("original cleanup outcome is unconfirmed")
                val owner = owner(dispatcher) {}
                val first = owner.activateClient(UUID(0, 1), 7)
                fun assertOriginalCleanup(pending: ReaderOperationOwner.PendingCleanup) {
                    assertEquals(first, pending.lease)
                    assertEquals("cursor", pending.id)
                    val failure = checkNotNull(pending.failure)
                    assertEquals(unresolved.javaClass, failure.javaClass)
                    assertEquals(unresolved.message, failure.message)
                    // withTimeout may recover a stack trace in a copy whose cause is the original exception.
                    assertTrue("The exact original cleanup failure must remain in the cause chain",
                        generateSequence(failure) { it.cause }.take(16).any { it === unresolved })
                }
                lateinit var oldTurn: ReaderOperationOwner.Turn
                owner.withTurn(first) {
                    oldTurn = this
                    registerRetiringCleanup("cursor") { cleanupEvents += "old"; throw unresolved }
                }
                val second = owner.activateClient(UUID(0, 2), 7)
                assertEquals(first.localGeneration, second.localGeneration)
                assertFalse(first.clientId == second.clientId)
                assertFalse(first.ownerGeneration == second.ownerGeneration)
                owner.withTurn(second) { registerRetiringCleanup("cursor") { cleanupEvents += "new" } }
                rejected { owner.withTurn(first) { error("old lease entered") } }
                assertFalse(owner.retireClient(first))
                assertThrows(IllegalStateException::class.java) { oldTurn.cleanupCompleted("cursor") }
                owner.withTurn(second) { } // The current client remains admissible.
                assertEquals(listOf("old"), cleanupEvents)
                val pending = owner.pendingCleanups().single()
                assertOriginalCleanup(pending)
                rejected { owner.retryCleanup(first, pending) }
                assertOriginalCleanup(checkNotNull(owner.retryCleanup(second, pending)))
                assertEquals(listOf("old", "old"), cleanupEvents) // One explicit retry, no automatic replay.
                val closed = owner.close().await()
                assertEquals(listOf("old", "old", "new"), cleanupEvents)
                assertOriginalCleanup(closed.unresolvedCleanups.single())
            }
        }
    }

    private fun directory() = File(System.getProperty("java.io.tmpdir"), "reader-owner-test-${UUID.randomUUID()}")
    private fun owner(dispatcher: CoroutineDispatcher, close: suspend () -> Unit) =
        ReaderOperationOwner.create(directory(), dispatcher, closeAction = close)

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); throw AssertionError("Expected admission rejection") }
        catch (_: ReaderOperationOwner.AdmissionRejected) { }
    }
}
