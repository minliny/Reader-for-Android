package io.reader.android.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixed-Core wire classification only; no parser, corpus, JNI or rendering claim. */
class ReaderAzwFamilyAdmissionTest {
    @Test fun azwOriginalUsesLibmobiPresentationWithoutRelabellingItsBinding() {
        val original = ReaderLocalBookFormat.fromCore("azw")
        assertEquals(ReaderLocalBookFormat.AZW, original)
        // The old sourceFormat == kind.format check rejects this genuine fixed-Core pair.
        assertTrue(original.acceptsEbookPresentation("ebook", "mobi", false))
        assertEquals("azw", original.wire)
        assertFalse(original.acceptsEbookPresentation("ebook", "epub", false))
        assertFalse(original.acceptsEbookPresentation("text", "mobi", false))
        assertFalse(original.acceptsEbookPresentation("ebook", "mobi", true))
        assertFalse(ReaderLocalBookFormat.EPUB.acceptsEbookPresentation("ebook", "mobi", false))
        // Fixed Core may summarize a .kf8 BOOKMOBI input as mobi; never synthesize a suffix wire label.
        assertEquals(ReaderLocalBookFormat.MOBI, ReaderLocalBookFormat.fromCore("mobi"))
        for (suffixOnly in listOf("azw3", "kf8")) {
            assertThrows(IllegalStateException::class.java) { ReaderLocalBookFormat.fromCore(suffixOnly) }
        }
    }
}
