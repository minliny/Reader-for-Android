package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsQueueTest {

    @Test
    fun `enqueue adds items sorted by priority desc`() {
        val queue = TtsQueue()
        queue.enqueue(TtsUtterance("a", "1"), priority = 0)
        queue.enqueue(TtsUtterance("b", "2"), priority = 10)
        assertEquals("b", queue.dequeue()?.utterance?.text)
    }

    @Test
    fun `dequeue returns null when empty`() {
        assertTrue(TtsQueue().dequeue() == null)
    }

    @Test
    fun `markCompleted removes item from queue`() {
        val queue = TtsQueue()
        queue.enqueue(TtsUtterance("text", "id1"))
        queue.markCompleted("id1")
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `clear empties queue`() {
        val queue = TtsQueue()
        queue.enqueue(TtsUtterance("a", "1"))
        queue.enqueue(TtsUtterance("b", "2"))
        queue.clear()
        assertEquals(0, queue.size())
    }

    @Test
    fun `dequeue marks item as STARTED`() {
        val queue = TtsQueue()
        queue.enqueue(TtsUtterance("text", "id"))
        val item = queue.dequeue()
        assertEquals(QueueEvent.STARTED, item?.state)
    }
}
