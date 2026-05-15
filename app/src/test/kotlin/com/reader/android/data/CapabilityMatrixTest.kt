package com.reader.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityMatrixTest {

    @Test
    fun `register and retrieve capability`() {
        val matrix = CapabilityMatrix()
        matrix.register(CapabilityEntry("A-01", "Gradle project", CapabilityOwner.APP, false, CapabilityStatus.DONE))
        assertEquals(CapabilityStatus.DONE, matrix.get("A-01")?.status)
    }

    @Test
    fun `nonUiDone counts only non-UI DONE entries`() {
        val matrix = CapabilityMatrix()
        matrix.register(CapabilityEntry("a", "", CapabilityOwner.APP, false, CapabilityStatus.DONE))
        matrix.register(CapabilityEntry("b", "", CapabilityOwner.PARSER, false, CapabilityStatus.DONE))
        matrix.register(CapabilityEntry("c", "", CapabilityOwner.APP, true, CapabilityStatus.DONE))
        matrix.register(CapabilityEntry("d", "", CapabilityOwner.STORAGE, false, CapabilityStatus.TODO))
        assertEquals(2, matrix.nonUiDone()) // a and b only
        assertEquals(3, matrix.nonUiTotal()) // a, b, d
    }

    @Test
    fun `uiOnlyGaps returns UI-required non-DONE capabilities`() {
        val matrix = CapabilityMatrix()
        matrix.register(CapabilityEntry("ui1", "Bookshelf grid", CapabilityOwner.APP, true, CapabilityStatus.TODO))
        matrix.register(CapabilityEntry("ui2", "Settings page", CapabilityOwner.APP, true, CapabilityStatus.DONE))
        val gaps = matrix.uiOnlyGaps()
        assertEquals(1, gaps.size)
        assertEquals("ui1", gaps[0].id)
    }
}
