package io.reader.android.ui.ebookdraft

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.FilterInputStream

/** Existing standard memory response; observation owns no URL/text, extra data reads or Core call. */
internal class ReaderEbookEntryInputStream(private val memory: ByteArrayInputStream,
    declaredBytes: Long, clock: () -> Long) : FilterInputStream(memory) {
    // Collector retains only these finite primitives, never the response/13MiB byte array.
    class Observation internal constructor(private val declaredBytes: Long, private val clock: () -> Long) {
        private val lock = Any()
        private var readBytes = 0L
        private var skippedBytes = 0L
        private var remainingBytes = declaredBytes
        private var reads = 0L
        private var resets = 0L
        private var closes = 0L
        private var firstReadAt = 0L
        private var fullyReadAt = 0L
        private var eofAt = 0L
        private var closeAt = 0L
        private var firstRead = false
        private var fullyRead = false
        private var eof = false
        private var closeObserved = false
        init { require(declaredBytes >= 0) }
        private fun time(): Long = try { clock().coerceAtLeast(0) } catch (_: Exception) { 0 }
        // Callers own the original memory monitor. Snapshot owns only this local lock.
        // No collector/stream/clock/JSON operation occurs while this observation lock is held.
        internal fun readReturned(value: Int, remaining: Int) {
            val needsTime = synchronized(lock) {
                !firstRead || (value < 0 && !eof) || (!fullyRead && resets == 0L && skippedBytes == 0L &&
                    readBytes + value.coerceAtLeast(0) == declaredBytes && remaining == 0)
            }
            val at = if (needsTime) time() else 0L
            synchronized(lock) {
                reads++; remainingBytes = remaining.toLong()
                if (!firstRead) { firstReadAt = at; firstRead = true }
                if (value > 0) readBytes += value
                if (value < 0 && !eof) { eofAt = at; eof = true }
                if (!fullyRead && resets == 0L && skippedBytes == 0L && readBytes == declaredBytes && remaining == 0) {
                    fullyReadAt = at; fullyRead = true
                }
            }
        }
        internal fun skipped(value: Long, remaining: Int) = synchronized(lock) {
            skippedBytes += value; remainingBytes = remaining.toLong()
        }
        internal fun reset(remaining: Int) = synchronized(lock) {
            resets++; remainingBytes = remaining.toLong()
        }
        internal fun closed() {
            val needsTime = synchronized(lock) { !closeObserved }
            val at = if (needsTime) time() else 0L
            synchronized(lock) { closes++; if (!closeObserved) { closeAt = at; closeObserved = true } }
        }
        fun snapshot(): JSONObject {
            val copied = synchronized(lock) { listOf(declaredBytes, readBytes, skippedBytes, remainingBytes,
                reads, resets, closes, firstReadAt, fullyReadAt, eofAt, closeAt, firstRead, fullyRead, eof, closeObserved) }
            return JSONObject().put("scope", "ONE_ENTRY_MEMORY_RESPONSE_READ_RETURNS_NOT_RENDERER_JOIN")
                .put("declaredBytes", copied[0]).put("readBytesCumulative", copied[1]).put("skippedBytesCumulative", copied[2]).put("remainingBytes", copied[3])
                .put("readCalls", copied[4]).put("resetCalls", copied[5]).put("closeCalls", copied[6])
                .put("firstReadElapsedMs", copied[7]).put("fullyReadElapsedMs", copied[8]).put("eofElapsedMs", copied[9]).put("closeElapsedMs", copied[10])
                .put("firstReadObserved", copied[11]).put("fullyReadWithoutResetOrSkipObserved", copied[12]).put("eofObserved", copied[13]).put("closeObserved", copied[14])
        }
    }
    val observation = Observation(declaredBytes, clock)
    private inline fun observed(block: () -> Unit) { try { block() } catch (_: Exception) {} }
    // Preserve stock read results/exceptions and linear order via its original standard monitor.
    // Only finite primitive bookkeeping occurs per call; no per-byte loop or collector lock.
    override fun read(): Int = synchronized(memory) {
        val value = memory.read()
        observed { observation.readReturned(if (value < 0) -1 else 1, memory.available()) }; value
    }
    override fun read(buffer: ByteArray): Int = read(buffer, 0, buffer.size)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = synchronized(memory) {
        val value = memory.read(buffer, offset, length)
        observed { observation.readReturned(value, memory.available()) }; value
    }
    override fun skip(count: Long): Long = synchronized(memory) {
        val value = memory.skip(count)
        observed { observation.skipped(value, memory.available()) }; value
    }
    override fun available(): Int = memory.available()
    override fun markSupported(): Boolean = memory.markSupported()
    override fun mark(readlimit: Int) = synchronized(memory) { memory.mark(readlimit) }
    override fun reset() = synchronized(memory) {
        memory.reset()
        observed { observation.reset(memory.available()) }
    }
    override fun close() {
        try { memory.close() }
        finally {
            // ByteArrayInputStream.close is a no-op: do not add a gate against later reads.
            try { synchronized(memory) { observed { observation.closed() } } } catch (_: Exception) {}
        }
    }
    fun snapshot(): JSONObject = observation.snapshot()
}
