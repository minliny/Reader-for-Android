package io.reader.android.runtime

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** TTS plan/status reads use the observer lane; queue controls remain retained writes. */
class TtsReadModeTest {
    @Test fun ttsPlanAndStatusAreObserverReads(): Unit = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            withTimeout(3_000) {
                val owner = ReaderOperationOwner.create(File(System.getProperty("java.io.tmpdir"), "tts-read-${UUID.randomUUID()}"), dispatcher) {}
                val lease = owner.activateClient(UUID.randomUUID(), 0)
                val observers = ReaderReadObservers()
                val caller = SupervisorJob()
                observers.activate(lease)
                val observer = observers.open(lease, caller)
                try {
                    assertEquals(ReaderCoreEventActor.Kind.READ_ONLY, actorKindForObserver(observer, "tts.slice", false))
                    assertEquals(ReaderCoreEventActor.Kind.READ_ONLY, actorKindForObserver(observer, "tts.queue.status", false))
                    for (method in listOf("tts.queue.play", "tts.queue.pause", "tts.queue.resume", "tts.queue.stop", "tts.queue.report-callback")) {
                        assertThrows(IllegalStateException::class.java) { actorKindForObserver(observer, method, false) }
                    }
                    assertThrows(IllegalStateException::class.java) { actorKindForObserver(observer, "tts.slice", true) }
                } finally {
                    observer.closeObservation(); caller.cancel(); owner.close().await()
                }
            }
        }
    }
}
