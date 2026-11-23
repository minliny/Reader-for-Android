package io.reader.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReaderTextCoordinatesTest {
    @Test fun supplementaryScalarDoesNotBecomeTwoCorePositions() {
        val text = "甲\uD83D\uDE00乙"
        assertEquals(2, ReaderTextCoordinates.scalarOffset(text, 3))
        assertEquals(3, ReaderTextCoordinates.utf16Offset(text, 2))
        assertEquals(4, ReaderTextCoordinates.utf16Offset(text, 3))
    }

    @Test fun splitSurrogateAndOutOfRangePositionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReaderTextCoordinates.scalarOffset("\uD83D\uDE00", 1) }
        assertThrows(IllegalArgumentException::class.java) { ReaderTextCoordinates.utf16Offset("正文", 3) }
    }
}
