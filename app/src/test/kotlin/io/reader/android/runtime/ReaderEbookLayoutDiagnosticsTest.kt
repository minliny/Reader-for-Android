package io.reader.android.runtime

import io.reader.android.ui.ebookdraft.ReaderEbookLayoutDiagnostics
import org.junit.Assert.*
import org.junit.Test

class ReaderEbookLayoutDiagnosticsTest {
    @Test fun malformedAndRetiredDiagnosticsCannotEnterBusinessFailurePath() {
        val trace = ReaderEbookLayoutDiagnostics("render") { 10L }
        trace.beginLayout(1, "one")
        trace.terminal(ReaderEbookLayoutDiagnostics.Outcome.NATIVE_READY)
        val malformed = listOf("{", "{}",
            """{"phase":"DOM_ADMIT_COMPLETE","layoutEpoch":1.5,"layoutToken":"one","sinceLayoutStartMs":1}""",
            """{"phase":"unknown","layoutEpoch":1,"layoutToken":"one","sinceLayoutStartMs":1}""",
            """{"phase":"DOM_ADMIT_COMPLETE","layoutEpoch":1,"layoutToken":"old","sinceLayoutStartMs":1}""")
        for (value in malformed) {
            assertTrue(trace.consume("phase", value, 1, "one"))
            assertEquals("NATIVE_READY", trace.snapshot().getString("outcome"))
            assertEquals(0, trace.snapshot().getJSONObject("jsPhases").length())
        }
        trace.beginLayout(2, "two")
        trace.terminal(ReaderEbookLayoutDiagnostics.Outcome.NATIVE_READY)
        assertTrue(trace.consume("phase", """{"phase":"DOM_ADMIT_COMPLETE","layoutEpoch":1,"layoutToken":"one","sinceLayoutStartMs":1}""", 2, "two"))
        assertEquals(0, trace.snapshot().getJSONObject("jsPhases").length())
        assertEquals("NATIVE_READY", trace.snapshot().getString("outcome"))
        assertFalse(trace.consume("ready", "{", 2, "two")) // Existing business decoder still owns malformed ready.
        assertTrue(trace.consume("phase", """{"phase":"CONFIG_FETCH_COMPLETE","layoutEpoch":2,"layoutToken":"two","sinceLayoutStartMs":1}""", 2, "two"))
        assertTrue(trace.snapshot().getJSONObject("jsPhases").has("CONFIG_FETCH_COMPLETE"))
        val unavailable = ReaderEbookLayoutDiagnostics("clock-failure") { throw IllegalStateException("clock") }
        assertTrue(unavailable.consume("phase", "{", 1, "one"))
    }
}
