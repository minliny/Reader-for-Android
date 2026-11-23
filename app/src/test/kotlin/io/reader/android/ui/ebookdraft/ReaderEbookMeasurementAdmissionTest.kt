package io.reader.android.ui.ebookdraft

import org.junit.Assert.*
import org.junit.Test

class ReaderEbookMeasurementAdmissionTest {
    @Test
    fun transientMissRetainsLastAnchorButNextVisibleSampleAdmitsAndReadyStaysStrict() {
        // Synthetic finite rectangles, not a reconstruction of the unrecorded737b payload.
        val outside = ReaderEbookMeasurement(left = 20.0, right = 30.0, top = 0.0, bottom = 20.0,
            width = 10.0, height = 20.0, viewportWidth = 360.0, viewportHeight = 640.0,
            expectedWidth = 360.0, expectedHeight = 640.0, topInset = 32.0, bottomInset = 52.0)
        val anchors = ReaderEbookAnchorAdmission(7)
        anchors.observe(11) { true }
        var callbacks = 0
        ReaderEbookMeasurementAdmission.observePosition(outside) {
            callbacks++; anchors.observe(23) { true }
        }
        assertEquals(0, callbacks); assertEquals(11, anchors.lastVisible ?: -1); assertEquals(11, anchors.desiredAnchor)

        val visible = outside.copy(top = 64.0, bottom = 84.0)
        ReaderEbookMeasurementAdmission.requireReady(visible)
        ReaderEbookMeasurementAdmission.observePosition(visible) {
            callbacks++; anchors.observe(23) { true }
        }
        assertEquals(1, callbacks); assertEquals(23, anchors.lastVisible ?: -1); assertEquals(23, anchors.desiredAnchor)

        try {
            ReaderEbookMeasurementAdmission.requireReady(outside)
            fail("Out-of-viewport Ready must remain fatal")
        } catch (expected: IllegalStateException) { assertEquals("ANCHOR_NOT_IN_REAL_VIEWPORT", expected.message) }
        for (invalid in listOf(visible.copy(left = Double.NaN), visible.copy(width = 0.0),
            visible.copy(viewportWidth = 400.0))) {
            for (ready in listOf(false, true)) {
                try {
                    if (ready) ReaderEbookMeasurementAdmission.requireReady(invalid)
                    else ReaderEbookMeasurementAdmission.observePosition(invalid) { fail("Malformed sample cannot enter") }
                    fail("Malformed geometry must not become an ordinary missed sample")
                } catch (_: IllegalStateException) { /* Preserve the owner-fatal validation boundary. */ }
            }
        }
        assertEquals(1, callbacks); assertEquals(23, anchors.lastVisible ?: -1)
    }
}
