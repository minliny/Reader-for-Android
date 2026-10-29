package io.reader.android.runtime

import com.reader.core.ReaderCoreAssetException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classification only; no archive read, browser fallback, Native result or rendering is claimed. */
class ReaderEbookFontFallbackTest {
    @Test fun missingEmbeddedFontAllowsOnlyItsActualUrlAndTypedNotFound() {
        val fallback = ReaderEbookFontFallback()
        val origin = "https://reader-ebook.invalid/publication/5741208c-1f7b-413f-b388-cebd2fd59d49/"
        val paths = listOf("OEBPS/Fonts/YaSong.ttf", "OEBPS/Fonts/SimHei.ttf")
        val missing = ReaderCoreAssetException(6, 2, "EPUB entry not found".toByteArray(Charsets.UTF_8))
        for (path in paths) {
            val url = origin + path
            assertFalse(fallback.contains(url))
            assertTrue(fallback.recordMissingFont(url, path, missing))
            assertTrue(fallback.recordMissingFont(url, path, missing)) // Same original observation is idempotent.
            assertTrue(fallback.contains(url))
            assertTrue(fallback.acceptsHttpFailure(url, 404))
            assertFalse(fallback.acceptsHttpFailure(url, 403))
            assertFalse(fallback.acceptsHttpFailure(url, 500))
        }
        val foreign = "https://reader-ebook.invalid/publication/00000000-0000-4000-8000-000000000001/" + paths.first()
        assertFalse(fallback.contains(foreign))
        assertFalse(fallback.acceptsHttpFailure(foreign, 404))
        assertFalse(fallback.acceptsHttpFailure(origin + paths.first() + "?other", 404))
        assertFalse(fallback.acceptsHttpFailure(origin + "OEBPS/Fonts/yasong.ttf", 404))

        for (path in listOf("OEBPS/Styles/Style0001.css", "OEBPS/Text/toc.xhtml", "OEBPS/Images/cover.jpg")) {
            assertFalse(fallback.recordMissingFont(origin + path, path, missing))
            assertFalse(fallback.acceptsHttpFailure(origin + path, 404))
        }
        val otherFont = "OEBPS/Fonts/Other.woff2"
        for (error in listOf(
            ReaderCoreAssetException(5, 2, "EPUB entry not found".toByteArray(Charsets.UTF_8)),
            ReaderCoreAssetException(7, 2, "EPUB entry too large".toByteArray(Charsets.UTF_8)),
            ReaderCoreAssetException(8, 2, "EPUB read failed".toByteArray(Charsets.UTF_8)),
            IllegalStateException("RESOURCE_SESSION_BYTE_LIMIT"),
            IllegalStateException("EPUB entry not found", missing),
        )) {
            assertFalse(fallback.recordMissingFont(origin + otherFont, otherFont, error))
            assertFalse(fallback.acceptsHttpFailure(origin + otherFont, 404))
        }
    }
}
