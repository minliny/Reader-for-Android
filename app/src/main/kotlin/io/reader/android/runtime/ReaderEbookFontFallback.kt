package io.reader.android.runtime

import com.reader.core.ReaderCoreAssetException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Per-publication observations only. The caller must first admit the canonical URI and full render guard. */
internal class ReaderEbookFontFallback {
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun recordMissingFont(url: String, canonicalEntry: String, error: Exception): Boolean {
        if (error !is ReaderCoreAssetException || error.assetStatus() != 6 ||
            canonicalEntry.substringAfterLast('.', "").lowercase(Locale.ROOT) !in FONT_EXTENSIONS) return false
        missing.add(url)
        return true
    }

    fun contains(url: String): Boolean = missing.contains(url)

    fun acceptsHttpFailure(url: String, status: Int): Boolean = status == 404 && contains(url)

    private companion object {
        val FONT_EXTENSIONS = setOf("ttf", "otf", "woff", "woff2")
    }
}
