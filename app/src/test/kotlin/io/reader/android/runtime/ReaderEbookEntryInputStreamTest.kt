package io.reader.android.runtime

import io.reader.android.ui.ebookdraft.ReaderEbookEntryInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ReaderEbookEntryInputStreamTest {
    @Test fun memoryObservationPreservesStockReadSkipMarkResetCloseAndClockFailure() {
        val data = "abcdef".toByteArray()
        fun readOutcome(stream: InputStream, offset: Int, length: Int): Pair<String, Int?> = try {
            "returned" to stream.read(ByteArray(2), offset, length)
        } catch (failure: Exception) { failure.javaClass.name to null }
        val boundaryStock = ByteArrayInputStream(data)
        val boundaryObserved = ReaderEbookEntryInputStream(ByteArrayInputStream(data), data.size.toLong()) { 5L }
        assertEquals(readOutcome(boundaryStock, -1, 1), readOutcome(boundaryObserved, -1, 1))
        assertEquals(readOutcome(boundaryStock, 0, -1), readOutcome(boundaryObserved, 0, -1))
        assertEquals(readOutcome(boundaryStock, 2, 1), readOutcome(boundaryObserved, 2, 1))
        assertEquals(readOutcome(boundaryStock, 0, 0), readOutcome(boundaryObserved, 0, 0))
        assertFalse(boundaryObserved.snapshot().getBoolean("fullyReadWithoutResetOrSkipObserved"))
        assertEquals(boundaryStock.skip(data.size.toLong()), boundaryObserved.skip(data.size.toLong()))
        assertEquals(readOutcome(boundaryStock, 0, 0), readOutcome(boundaryObserved, 0, 0)) // Stock EOF/zero-length ordering.
        assertEquals(boundaryStock.read(), boundaryObserved.read())
        assertFalse(boundaryObserved.snapshot().getBoolean("fullyReadWithoutResetOrSkipObserved")) // Skip is never delivery.
        fun sequence(stream: InputStream): List<Int> {
            val result = mutableListOf(stream.read())
            stream.mark(100)
            result += stream.skip(2).toInt()
            val buffer = ByteArray(2)
            result += stream.read(buffer, 0, 2)
            result += buffer.map { it.toInt() }
            stream.reset()
            result += stream.read(buffer)
            result += buffer.map { it.toInt() }
            stream.close()
            result += stream.read() // Stock memory streams remain readable after close.
            result += stream.available()
            return result
        }
        val observed = ReaderEbookEntryInputStream(ByteArrayInputStream(data), data.size.toLong()) { 10L }
        assertEquals(sequence(ByteArrayInputStream(data)), sequence(observed))
        assertTrue(observed.snapshot().getBoolean("closeObserved"))
        assertEquals(1L, observed.snapshot().getLong("resetCalls"))
        val exact = ReaderEbookEntryInputStream(ByteArrayInputStream(data), data.size.toLong()) { 20L }
        val copied = ByteArray(data.size)
        assertEquals(data.size, exact.read(copied, 0, copied.size)); assertArrayEquals(data, copied)
        assertEquals(-1, exact.read()); exact.close()
        val snapshot = exact.snapshot()
        assertEquals(data.size.toLong(), snapshot.getLong("readBytesCumulative"))
        assertTrue(snapshot.getBoolean("fullyReadWithoutResetOrSkipObserved")); assertTrue(snapshot.getBoolean("eofObserved"))
        val badClock = ReaderEbookEntryInputStream(ByteArrayInputStream(data), data.size.toLong()) { throw IllegalStateException("observer clock") }
        val failedObserverBytes = ByteArray(data.size)
        assertEquals(data.size, badClock.read(failedObserverBytes)); assertArrayEquals(data, failedObserverBytes)
        badClock.close(); assertEquals(-1, badClock.read())
    }
}
