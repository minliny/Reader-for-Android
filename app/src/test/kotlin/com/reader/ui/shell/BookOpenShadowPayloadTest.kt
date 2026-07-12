package com.reader.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W2 bridge guard: sourceKind is derived from the Core/native identity, never
 * supplied by a display label or a test-only constant before shadow dispatch.
 */
class BookOpenShadowPayloadTest {

    @Test
    fun `source kind classifies Core local and remote identities`() {
        assertEquals("local", canonicalBookOpenSourceKind("local", "local://book/1"))
        assertEquals("local", canonicalBookOpenSourceKind("source-a", "local://book/1"))
        assertEquals("local", canonicalBookOpenSourceKind("fixture://discover", "fixture://book/1"))
        assertEquals("local", canonicalBookOpenSourceKind("source-a", "content://provider/books/1"))
        assertEquals("remote", canonicalBookOpenSourceKind("source-a", "https://example.test/book/1"))
    }

    @Test
    fun `book open shadow effects carry the actual local source kind`() {
        val viewModel = AppShellViewModel(
            readerUiRuntimeCoordinator = ReaderUiRuntimeCoordinator(bookOpenPilotEnabled = false)
        )

        viewModel.dispatch(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "local",
                bookUrl = "local://book/1",
                bookName = "本地书",
                requestId = "local-book-open"
            )
        )

        val observation = requireNotNull(viewModel.readerUiRuntimeShadowObservation)
        assertEquals("book.open", observation.event)
        assertEquals(ReaderUiRuntimeDispatchMode.SHADOW, observation.mode)
        assertEquals("local", observation.runtimeEffects.first().payload["sourceKind"])
        assertEquals("local", observation.runtimeEffects.first().payload["sourceId"])
        assertEquals("local://book/1", observation.runtimeEffects.first().payload["bookId"])
    }
}
