package com.reader.android.data.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTextFeederTest {

    @Test
    fun `first segment has CHAPTER_START boundary`() {
        val feeder = ChapterTextFeeder("段落一\n\n段落二", "第一章")
        val segment = feeder.next()
        assertNotNull(segment)
        assertEquals(BoundaryEvent.CHAPTER_START, segment!!.boundary)
        assertTrue(segment.text.contains("段落一"))
    }

    @Test
    fun `subsequent segments have PARAGRAPH_BOUNDARY`() {
        val feeder = ChapterTextFeeder("p1\n\np2\n\np3", "Ch1")
        feeder.next() // CHAPTER_START
        val seg2 = feeder.next()!!
        assertEquals(BoundaryEvent.PARAGRAPH_BOUNDARY, seg2.boundary)
        assertEquals("p2", seg2.text)
    }

    @Test
    fun `next returns null when exhausted`() {
        val feeder = ChapterTextFeeder("single paragraph", "Ch1")
        feeder.next()
        assertNull(feeder.next())
    }

    @Test
    fun `nextChapterSignal returns CHAPTER_END`() {
        val feeder = ChapterTextFeeder("text", "Ch1")
        val signal = feeder.nextChapterSignal()
        assertEquals(BoundaryEvent.CHAPTER_END, signal.boundary)
    }

    @Test
    fun `remaining counts unread paragraphs`() {
        val feeder = ChapterTextFeeder("a\n\nb\n\nc", "Ch1")
        assertEquals(3, feeder.remaining())
        feeder.next()
        assertEquals(2, feeder.remaining())
    }

    @Test
    fun `reset returns to start`() {
        val feeder = ChapterTextFeeder("a\n\nb", "Ch1")
        feeder.next()
        feeder.reset()
        assertEquals(2, feeder.remaining())
        assertEquals(BoundaryEvent.CHAPTER_START, feeder.next()!!.boundary)
    }
}
