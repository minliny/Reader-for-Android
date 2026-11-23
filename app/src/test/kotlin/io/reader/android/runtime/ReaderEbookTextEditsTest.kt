package io.reader.android.runtime

import io.reader.android.runtime.ebookdraft.EbookProcessedTextEdit
import io.reader.android.runtime.ebookdraft.ReaderEbookTextEdits
import org.junit.Assert.*
import org.junit.Test

/** Actual AndroidX DiffUtil, not a substitute diff fixture or a new parser. */
class ReaderEbookTextEditsTest {
    @Test fun unicodeCoordinatesUseScalarsRatherThanUtf16() {
        val edits = ReaderEbookTextEdits.changes("A\uD83D\uDE00甲\nZ", "A\uD83D\uDE00乙\uD842\uDFB7\nZ")
        assertEquals(listOf(EbookProcessedTextEdit(2, 3, "乙\uD842\uDFB7")), edits)
        assertEquals("A\uD83D\uDE00乙\uD842\uDFB7\nZ", replay("A\uD83D\uDE00甲\nZ", edits))
    }

    @Test fun whitespaceAndConfirmedReplacementPreserveExistingImage() {
        val whitespace = ReaderEbookTextEdits.changes("A \n B", "A\nB")
        assertEquals(listOf(EbookProcessedTextEdit(1, 2, ""), EbookProcessedTextEdit(3, 4, "")), whitespace)
        assertEquals("A\nB", replay("A \n B", whitespace))
        val original = "旧词\n\n甲\uFFFC乙&<尾>"
        val processed = "替换词\n\n甲\uFFFC乙&<末>"
        val edits = ReaderEbookTextEdits.changes(original, processed)
        assertTrue(edits.isNotEmpty())
        assertFalse(edits.any { '\uFFFC' in it.text })
        assertEquals(processed, replay(original, edits))
    }

    @Test fun limitsInsertedImageAndInvalidUnicodeAreRejected() {
        val unchanged = "x".repeat(32_769)
        assertTrue(ReaderEbookTextEdits.changes(unchanged, unchanged).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { ReaderEbookTextEdits.changes(unchanged, unchanged + "y") }
        assertThrows(IllegalArgumentException::class.java) { ReaderEbookTextEdits.changes("甲乙", "甲\uFFFC乙") }
        assertThrows(IllegalArgumentException::class.java) { ReaderEbookTextEdits.changes("\uD800", "x") }
        assertThrows(IllegalStateException::class.java) { ReaderEbookTextEdits.changes("a".repeat(16_384), "b".repeat(16_384)) }
    }

    /** Apply the emitted public coordinate tuple to independently check the observable output. */
    private fun replay(original: String, edits: List<EbookProcessedTextEdit>): String {
        var scalar = 0
        return buildString {
            for (edit in edits) {
                append(original.substring(original.offsetByCodePoints(0, scalar), original.offsetByCodePoints(0, edit.startScalar)))
                append(edit.text)
                scalar = edit.endScalar
            }
            append(original.substring(original.offsetByCodePoints(0, scalar)))
        }
    }
}
