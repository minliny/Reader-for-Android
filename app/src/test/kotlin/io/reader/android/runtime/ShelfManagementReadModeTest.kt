package io.reader.android.runtime

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Exact new pure-method admission only. It neither dispatches Native nor creates an App/DB. */
class ShelfManagementReadModeTest {
    @Test fun onlyGroupListIsAdmittedAsManagementObserverRead(): Unit = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            withTimeout(3_000) {
                val owner = ReaderOperationOwner.create(File(System.getProperty("java.io.tmpdir"), "group-read-${UUID.randomUUID()}"), dispatcher) {}
                val lease = owner.activateClient(UUID.randomUUID(), 0)
                val observers = ReaderReadObservers()
                val caller = SupervisorJob()
                observers.activate(lease)
                val observer = observers.open(lease, caller)
                try {
                    assertEquals(ReaderCoreEventActor.Kind.READ_ONLY, actorKindForObserver(observer, "book-group.list", false))
                    for (method in listOf("book-group.create", "book-group.update", "book-group.delete", "bookshelf.group.assign",
                        "bookshelf.removeBatch", "bookshelf.management.mutation.query", "bookshelf.management.mutation.apply")) {
                        assertThrows(IllegalStateException::class.java) { actorKindForObserver(observer, method, false) }
                    }
                    assertThrows(IllegalStateException::class.java) { actorKindForObserver(observer, "book-group.list", true) }
                    assertEquals(ReaderCoreEventActor.Kind.DURABLE_WRITE, actorKindForObserver(null, "book-group.list", false))
                } finally {
                    observer.closeObservation(); caller.cancel(); owner.close().await()
                }
            }
        }
    }
}
