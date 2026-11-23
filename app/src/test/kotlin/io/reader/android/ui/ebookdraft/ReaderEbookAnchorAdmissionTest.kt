package io.reader.android.ui.ebookdraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderEbookAnchorAdmissionTest {
    @Test fun rejectedObservationKeepsReflowBasisAndExplicitHostAnchorReplacesIt() {
        val anchors = ReaderEbookAnchorAdmission(210)
        var pending = true
        var observations = 0
        val admitDisplay = { observations++; !pending }

        // The actual browser can observe a new coordinate before the blocked UI policy binds.
        anchors.observe(322, admitDisplay)
        assertNull(anchors.lastVisible)
        anchors.restore(210)
        assertEquals(210, anchors.desiredAnchor)
        assertEquals(1, observations) // Reflow did not call the display consumer, let alone save again.

        // Only a new observation after original pending resolution can advance the UI reflow basis.
        pending = false
        anchors.observe(322, admitDisplay)
        assertEquals(322, anchors.lastVisible ?: -1)
        anchors.restore(210)
        assertEquals(322, anchors.desiredAnchor)
        assertEquals(2, observations)
        anchors.observe(322, admitDisplay)
        assertEquals(2, observations)

        // Explicit Host navigation replaces an earlier accepted observation before later font reflow.
        anchors.restore(640, external = true)
        anchors.restore(640)
        assertEquals(640, anchors.lastVisible ?: -1)
        assertEquals(640, anchors.desiredAnchor)
        assertEquals(2, observations) // Neither restoration nor explicit Host input called the consumer.
    }
}
