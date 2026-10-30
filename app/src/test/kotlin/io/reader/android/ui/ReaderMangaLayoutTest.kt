package io.reader.android.ui

import io.reader.android.runtime.ReaderMangaChapterIdentity
import org.junit.Assert.*
import org.junit.Test

class ReaderMangaLayoutTest {
    private val window = ReaderMangaWindowTicket(7, 11, ReaderMangaChapterIdentity("source", "book", "chapter"), "manifest", "rules", "decode", 0, 8)
    private val ticket = ReaderMangaLayoutTicket(window, 13, 400, 600, 2f)
    private val viewport = ReaderMangaImageRect(20.0, 80.0, 400.0, 600.0)
    private fun image(id: String = "page-0", ordinal: Int = 0, left: Double = -180.0, top: Double = -320.0,
        width: Double = 800.0, height: Double = 1600.0, sourceWidth: Int = 500, sourceHeight: Int = 1000) =
        ReaderMangaImageGeometry(id, ordinal, "resource-$id", "transfer-$id", sourceWidth, sourceHeight,
            ReaderMangaImageRect(left, top, width, height))

    @Test fun actualImageRectUsesImageFractionsAndRestoresThroughNativeOffsets() {
        val page = image()
        val anchor = ReaderMangaLayoutGeometry.visibleAnchor(ticket, viewport, listOf(page))!!
        assertEquals(.25, anchor.x, 0.0); assertEquals(.25, anchor.y, 0.0)
        val target = ReaderMangaLayoutGeometry.restore(ticket, anchor, listOf(page))!!
        assertEquals(0, target.itemIndex); assertEquals(400, target.scrollOffsetPx); assertEquals(200, target.horizontalOffsetPx)
    }

    @Test fun staleZoomOrViewportRectCannotInventAZeroPosition() {
        assertNull(ReaderMangaLayoutGeometry.visibleAnchor(ticket.copy(zoom = 1f), viewport, listOf(image())))
        assertNull(ReaderMangaLayoutGeometry.visibleAnchor(ticket, viewport.copy(width = 399.0 - .01), listOf(image())))
        assertNull(ReaderMangaLayoutGeometry.visibleAnchor(ticket, viewport, listOf(image(height = 1500.0))))
        assertNull(ReaderMangaLayoutGeometry.visibleAnchor(ticket, viewport, emptyList()))
    }

    @Test fun nativeTailClampKeepsActualFirstVisibleInsteadOfClaimingTheTarget() {
        val first = image("page-6", 6, top = -1020.0)
        val last = image("page-7", 7, top = 580.0, height = 100.0, sourceHeight = 125, sourceWidth = 1000)
        assertTrue(last.rect.intersects(viewport))
        val actual = ReaderMangaLayoutGeometry.visibleAnchor(ticket, viewport, listOf(last, first))!!
        assertEquals("page-6", actual.pageId); assertEquals(1100.0 / 1600, actual.y, 0.0)
        val measured = ReaderMangaMeasuredAnchor(ticket, actual, restoredRequestId = 3, restoredTargetPageId = "page-7")
        assertEquals("page-7", measured.restoredTargetPageId)
        assertNotEquals(measured.anchor.pageId, measured.restoredTargetPageId)
    }

    @Test fun replacedWindowUsesPageIdentityAndItsNewIndex() {
        val pages = listOf(image("page-5", 5), image("page-6", 6))
        val anchor = ReaderMangaAnchor("page-6", 6, "resource-page-6", .5, .3)
        assertEquals(1, ReaderMangaLayoutGeometry.restore(ticket.copy(window = window.copy(revision = 12, start = 5)), anchor, pages)!!.itemIndex)
        assertNull(ReaderMangaLayoutGeometry.restore(ticket, anchor.copy(resourceRef = "foreign"), pages))
        assertNull(ReaderMangaLayoutGeometry.restore(ticket, anchor.copy(pageId = "page-7"), pages))
        assertNull(ReaderMangaLayoutGeometry.restore(ticket, anchor.copy(y = Double.NaN), pages))
    }

    @Test fun resizedImagePreservesNormalizedAnchorWithoutUsingOldPixels() {
        val target = ReaderMangaAnchor("page-0", 0, "resource-page-0", .25, .25)
        val newTicket = ticket.copy(epoch = 14, viewportWidthPx = 600, zoom = 1f)
        val planned = ReaderMangaLayoutGeometry.restore(newTicket, target, listOf(image()))!!
        assertEquals(300, planned.scrollOffsetPx); assertEquals(150, planned.horizontalOffsetPx)
        val newViewport = viewport.copy(width = 600.0)
        val actuallyClamped = image(left = 20.0, top = -220.0, width = 600.0, height = 1200.0)
        val actual = ReaderMangaLayoutGeometry.visibleAnchor(newTicket, newViewport, listOf(actuallyClamped))!!
        assertEquals(0.0, actual.x, 0.0); assertEquals(.25, actual.y, 0.0)
    }

    @Test fun zoomRestoreRoundingCannotBecomeAnOrdinaryProgressObservation() {
        val saved = 261.0 / 2160
        val restored = 326.0 / 2700
        assertNotEquals(saved, restored, 0.0) // Exact physical rounding from the failed device original, not a tolerance.
        val admission = ReaderMangaScrollAdmission()
        admission.bind(ticket)
        admission.beginRestore(ticket)
        admission.nativeMotion(ticket, scrolling = true, transforming = false) // scrollToItem is native motion too.
        admission.nativeMotion(ticket, scrolling = false, transforming = false)
        admission.confirmRestore(ticket)
        assertFalse(admission.canObserve(ticket))
        // Late notifications retain their restore provenance even if the owner already cleared its request.
        admission.nativeMotion(ticket, scrolling = true, transforming = false, restorationPending = true)
        admission.nativeMotion(ticket, scrolling = false, transforming = false, restorationPending = true)
        assertFalse(admission.canObserve(ticket))
        admission.nativeMotion(ticket, scrolling = false, transforming = false) // More idle draws after restore.
        assertFalse(admission.canObserve(ticket))

        val zoomOut = ticket.copy(epoch = ticket.epoch + 1, zoom = 1f)
        admission.bind(zoomOut)
        admission.beginRestore(zoomOut)
        admission.nativeMotion(zoomOut, scrolling = true, transforming = false)
        admission.nativeMotion(zoomOut, scrolling = false, transforming = false)
        admission.confirmRestore(zoomOut)
        assertFalse(admission.canObserve(zoomOut))
    }

    @Test fun genuineScrollPermitWaitsForTheCurrentDrawAndIsConsumedOnce() {
        val admission = ReaderMangaScrollAdmission()
        admission.bind(ticket); admission.confirmRestore(ticket)
        admission.nativeMotion(ticket, scrolling = true, transforming = false)
        assertFalse(admission.canObserve(ticket))
        admission.nativeMotion(ticket, scrolling = false, transforming = false)
        assertTrue(admission.canObserve(ticket))
        // A rejected old/missing frame must not consume the new real scroll's pending observation.
        admission.accepted(ticket.copy(epoch = ticket.epoch - 1))
        assertTrue(admission.canObserve(ticket))
        assertTrue(admission.canObserve(ticket))
        admission.accepted(ticket)
        assertFalse(admission.canObserve(ticket))
        admission.nativeMotion(ticket, scrolling = true, transforming = false)
        admission.nativeMotion(ticket, scrolling = false, transforming = false)
        assertTrue(admission.canObserve(ticket)) // A later genuine scroll is not suppressed.
    }

    @Test fun restoringTransformingOrRetiredMotionCannotAuthorizeANewWindow() {
        val admission = ReaderMangaScrollAdmission()
        admission.bind(ticket); admission.confirmRestore(ticket)
        admission.nativeMotion(ticket, scrolling = true, transforming = false)
        admission.nativeMotion(ticket, scrolling = false, transforming = true)
        assertFalse(admission.canObserve(ticket))
        val next = ticket.copy(epoch = ticket.epoch + 1)
        admission.bind(next); admission.confirmRestore(next)
        admission.nativeMotion(ticket, scrolling = true, transforming = false)
        admission.nativeMotion(ticket, scrolling = false, transforming = false)
        assertFalse(admission.canObserve(next))
        admission.nativeMotion(next, scrolling = true, transforming = false)
        admission.nativeMotion(next, scrolling = false, transforming = false)
        assertTrue(admission.canObserve(next))
        admission.beginRestore(next)
        assertFalse(admission.canObserve(next))
    }

    @Test fun currentTicketAndSizeCannotAdmitThePreRestoreNativeOffset() {
        val area = ReaderMangaImageRect(0.0, 100.0, 1080.0, 1899.0)
        val oldRect = image(left = 0.0, top = 100.0 - 261, width = 1350.0, height = 2700.0)
        assertTrue(ReaderMangaLayoutGeometry.accepts(ticket.copy(viewportWidthPx = 1080,
            viewportHeightPx = 1899, zoom = 1.25f), area, oldRect))
        assertFalse(ReaderMangaLayoutGeometry.matchesNativePlacement(area, oldRect,
            nativeRelativeTopPx = -326, nativeHeightPx = 2700, horizontalScrollPx = 0))
        val actual = oldRect.copy(rect = oldRect.rect.copy(top = 100.0 - 326))
        assertTrue(ReaderMangaLayoutGeometry.matchesNativePlacement(area, actual,
            nativeRelativeTopPx = -326, nativeHeightPx = 2700, horizontalScrollPx = 0))
        assertFalse(ReaderMangaLayoutGeometry.matchesNativePlacement(area, actual,
            nativeRelativeTopPx = -326, nativeHeightPx = 2700, horizontalScrollPx = 50))
    }
}
