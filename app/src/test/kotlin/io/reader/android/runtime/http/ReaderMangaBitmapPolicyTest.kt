package io.reader.android.runtime.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMangaBitmapPolicyTest {
    @Test fun ordinaryKeepsOriginalGeometryWithinTheLocalWorkingEnvelope() {
        ReaderMangaBitmapPolicy.ordinary(1024, 1024, 256 * 1024)
        // Full geometry is retained; the extra RGBA_F16 fallback reservation narrows the safe full-frame height.
        ReaderMangaBitmapPolicy.ordinary(32, 32768, 1024)
        rejected { ReaderMangaBitmapPolicy.ordinary(4096, 32768, 1024) }
    }

    @Test fun ordinaryRejectsOverflowAndInvalidDimensionsOrEncodedBudgetBeforeAllocation() {
        listOf(0 to 1, -1 to 1, Int.MAX_VALUE to Int.MAX_VALUE, Int.MAX_VALUE to 1).forEach { (width, height) ->
            rejected { ReaderMangaBitmapPolicy.ordinary(width, height, 1) }
        }
        listOf(0L, -1L, ReaderMangaBitmapPolicy.MAX_ENCODED_BYTES, Long.MAX_VALUE).forEach {
            rejected { ReaderMangaBitmapPolicy.ordinary(1, 1, it) }
        }
    }

    @Test fun ordinaryBoundaryAccountsForEncodedCopiesAndPaddedCodecSurface() {
        val surface = 32L * 32 * (8 + 8)
        val encoded = (ReaderMangaBitmapPolicy.MAX_WORKING_BYTES - surface) / 3
        ReaderMangaBitmapPolicy.ordinary(32, 32, encoded)
        rejected { ReaderMangaBitmapPolicy.ordinary(32, 32, encoded + 1) }
        rejected { ReaderMangaBitmapPolicy.ordinaryAllocation(32, 32, encoded, 32L * 32 * 8 + 3) }
    }

    @Test fun coreGraphicsDimensionsDoNotGrantAnUnboundedNativeAllocation() {
        ReaderMangaBitmapPolicy.graphicsDimensions(4096, 4096)
        ReaderMangaBitmapPolicy.graphicsDimensions(1, 65536)
        rejected { ReaderMangaBitmapPolicy.graphicsDimensions(4097, 1) }
        rejected { ReaderMangaBitmapPolicy.graphicsDimensions(1, 65537) }
        rejected { ReaderMangaBitmapPolicy.graphicsDimensions(4096, 65536) }
        rejected { ReaderMangaBitmapPolicy.graphics(4096, 4096, 1, 1) }
    }

    @Test fun graphicsReservesTargetMaximumStripAndBoundedCompressionCopies() {
        val reserved = ReaderMangaBitmapPolicy.graphics(64, 128, 1024, 64)
        assertEquals(64L * 128 * 2, reserved.targetBytes)
        assertEquals(64L * 64 * 8, reserved.stripBytes)
        assertTrue(3 * 1024L + reserved.targetBytes + 3L * reserved.maximumOutputBytes <= ReaderMangaBitmapPolicy.MAX_WORKING_BYTES)
        assertTrue(reserved.maximumOutputBytes <= ReaderMangaBitmapPolicy.MAX_ENCODED_BYTES)
        ReaderMangaBitmapPolicy.requireAllocation(reserved.targetBytes, reserved.targetBytes)
        rejected { ReaderMangaBitmapPolicy.requireAllocation(reserved.targetBytes + 1, reserved.targetBytes) }
        rejected { ReaderMangaBitmapPolicy.graphics(64, 128, 1024, 129) }
    }

    @Test fun compressedWriteAdmissionRejectsOverflowBeforeStreamGrowth() {
        ReaderMangaBitmapPolicy.requireOutputSize(8, 8, 16)
        rejected { ReaderMangaBitmapPolicy.requireOutputSize(8, 9, 16) }
        rejected { ReaderMangaBitmapPolicy.requireOutputSize(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE) }
        rejected { ReaderMangaBitmapPolicy.requireOutputSize(0, -1, 16) }
    }

    @Test fun alphaAndWideColorFallbackAllocationsFitOnlyWithinPreflightReservation() {
        val pixels = 512L * 512
        val reservation = ReaderMangaBitmapPolicy.graphics(512, 512, 1024, 512)
        assertEquals(pixels * 2, reservation.targetBytes) // Explicit createBitmap RGB565 remains a known target.
        assertEquals(pixels * 8, reservation.stripBytes)
        ReaderMangaBitmapPolicy.requireAllocation(pixels * 4, reservation.stripBytes)
        ReaderMangaBitmapPolicy.requireAllocation(pixels * 8, reservation.stripBytes)
        ReaderMangaBitmapPolicy.ordinary(512, 512, 1024)
        ReaderMangaBitmapPolicy.ordinaryAllocation(512, 512, 1024, pixels * 4)
        ReaderMangaBitmapPolicy.ordinaryAllocation(512, 512, 1024, pixels * 8)
        // These fit the old preferred-config estimates but not an allowed higher-byte fallback.
        rejected { ReaderMangaBitmapPolicy.graphics(1600, 1728, 1024, 1728) }
        rejected { ReaderMangaBitmapPolicy.ordinary(1600, 1728, 1024) }
    }

    private fun rejected(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(ReaderMangaBitmapPolicy.BUDGET_ERROR, failure?.message)
    }
}
