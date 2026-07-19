package com.reader.ui.reading

import com.reader.api.Book
import com.reader.api.Chapter
import com.reader.ui.shell.ReaderBookOpenDomainState
import com.reader.ui.shell.ReaderPlaybackDomainState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTapZoneContractTest {
    @Test
    fun `production reader tap zones match canonical geometry and stable identities`() {
        assertEquals(1f, ReaderTapZoneContract.PreviousRatio + ReaderTapZoneContract.ControlRatio + ReaderTapZoneContract.NextRatio, 0f)
        assertEquals(0.26f, ReaderTapZoneContract.PreviousRatio, 0f)
        assertEquals(0.48f, ReaderTapZoneContract.ControlRatio, 0f)
        assertEquals(0.26f, ReaderTapZoneContract.NextRatio, 0f)
        assertEquals("reader-tap-zone-previous", ReaderTapZoneContract.PreviousTag)
        assertEquals("reader-tap-zone-control", ReaderTapZoneContract.ControlTag)
        assertEquals("reader-tap-zone-next", ReaderTapZoneContract.NextTag)
    }

    @Test
    fun `loading disables all regions even when measured boundaries are available`() {
        val state = ReaderTapZoneHostAdapter.fromSnapshot(
            loading = true,
            contentReady = true,
            layoutBoundary = ReaderTapZoneLayoutBoundary(true, true)
        )

        assertEquals(ReaderTapZoneHostState.Disabled, state)
    }

    @Test
    fun `first middle and last page states come from measured page edges`() {
        val first = ReaderTapZoneHostAdapter.fromSnapshot(
            loading = false,
            contentReady = true,
            layoutBoundary = ReaderTapZoneLayoutBoundary(false, true)
        )
        val middle = ReaderTapZoneHostAdapter.fromSnapshot(
            loading = false,
            contentReady = true,
            layoutBoundary = ReaderTapZoneLayoutBoundary(true, true)
        )
        val last = ReaderTapZoneHostAdapter.fromSnapshot(
            loading = false,
            contentReady = true,
            layoutBoundary = ReaderTapZoneLayoutBoundary(true, false)
        )

        assertFalse(first.previousEnabled)
        assertTrue(first.controlEnabled)
        assertTrue(first.nextEnabled)
        assertTrue(middle.previousEnabled)
        assertTrue(middle.controlEnabled)
        assertTrue(middle.nextEnabled)
        assertTrue(last.previousEnabled)
        assertTrue(last.controlEnabled)
        assertFalse(last.nextEnabled)
    }

    @Test
    fun `single chapter single page enables only the control target`() {
        val state = ReaderTapZoneHostAdapter.fromSnapshot(
            loading = false,
            contentReady = true,
            layoutBoundary = ReaderTapZoneLayoutBoundary(false, false)
        )

        assertTrue(state.enabled)
        assertFalse(state.previousEnabled)
        assertTrue(state.controlEnabled)
        assertFalse(state.nextEnabled)
    }

    @Test
    fun `page boundary uses measured text height rather than fixture enabled props`() {
        assertEquals(
            ReaderTapZoneLayoutBoundary(hasPreviousPage = false, hasNextPage = true),
            readerTapZoneLayoutBoundary(
                currentLineTopPx = 0f,
                textHeightPx = 2400,
                viewportHeightPx = 800
            )
        )
        assertEquals(
            ReaderTapZoneLayoutBoundary(hasPreviousPage = true, hasNextPage = false),
            readerTapZoneLayoutBoundary(
                currentLineTopPx = 1600f,
                textHeightPx = 2400,
                viewportHeightPx = 800
            )
        )
        assertNull(readerTapZoneLayoutBoundary(Float.NaN, 2400, 800))
        assertNull(readerTapZoneLayoutBoundary(0f, 0, 800))
    }

    @Test
    fun `Pilot adapter consumes book open readiness and measured page boundary`() {
        val chapters = listOf(
            Chapter("One", "chapter-1", 1),
            Chapter("Two", "chapter-2", 2),
            Chapter("Three", "chapter-3", 3)
        )
        val loading = ReaderTapZoneHostAdapter.fromPilot(
            bookOpen = ReaderBookOpenDomainState(
                chapters = chapters,
                selectedChapterPosition = 0,
                contentLoaded = true,
                locationResolved = true,
                loading = true
            ),
            playback = ReaderPlaybackDomainState(),
            layoutBoundary = ReaderTapZoneLayoutBoundary(false, true)
        )
        val readyLast = ReaderTapZoneHostAdapter.fromPilot(
            bookOpen = ReaderBookOpenDomainState(
                chapters = chapters,
                selectedChapterPosition = 2,
                contentLoaded = true,
                locationResolved = true,
                loading = false
            ),
            playback = ReaderPlaybackDomainState(),
            layoutBoundary = ReaderTapZoneLayoutBoundary(true, false)
        )

        assertEquals(ReaderTapZoneHostState.Disabled, loading)
        assertTrue(readyLast.previousEnabled)
        assertTrue(readyLast.controlEnabled)
        assertFalse(readyLast.nextEnabled)
    }

    @Test
    fun `legacy adapter fails closed until real reading state is ready`() {
        val loading = ReaderTapZoneHostAdapter.fromLegacy(
            ReadingUiState.Loading,
            ReaderTapZoneLayoutBoundary(true, true)
        )
        val ready = ReaderTapZoneHostAdapter.fromLegacy(
            ReadingUiState.Ready(
                Book(bookUrl = "book-1", name = "Book"),
                listOf(Chapter("One", "chapter-1", 1))
            ),
            ReaderTapZoneLayoutBoundary(false, false)
        )

        assertEquals(ReaderTapZoneHostState.Disabled, loading)
        assertTrue(ready.controlEnabled)
        assertFalse(ready.previousEnabled)
        assertFalse(ready.nextEnabled)
    }

    @Test
    fun `missing page turn consumers fail closed while control remains available`() {
        val effective = ReaderTapZoneHostState(
            enabled = true,
            previousEnabled = true,
            controlEnabled = true,
            nextEnabled = true
        ).withAvailableCallbacks(
            ReaderTapZoneCallbacks(onControl = {})
        )

        assertTrue(effective.enabled)
        assertFalse(effective.previousEnabled)
        assertTrue(effective.controlEnabled)
        assertFalse(effective.nextEnabled)
    }
}
