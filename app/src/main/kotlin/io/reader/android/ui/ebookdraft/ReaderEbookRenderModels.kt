package io.reader.android.ui.ebookdraft

import io.reader.android.runtime.ReaderChapter
import io.reader.android.runtime.ReaderEbookPreparedPublication
import io.reader.android.runtime.ebookdraft.EbookRenderLease
import io.reader.android.ui.ReaderPaper

/** Actual reading-container padding; WebView size is measured by the owner, never inferred from a phone model. */
internal data class ReaderEbookGeometry(val leftPx: Int, val topPx: Int, val rightPx: Int, val bottomPx: Int,
    val density: Float, val fontScale: Float) {
    init { require(listOf(leftPx, topPx, rightPx, bottomPx).all { it in 0..100_000 })
        require(density.isFinite() && density > 0 && fontScale.isFinite() && fontScale > 0) }
}
internal data class ReaderEbookRenderInput(val prepared: ReaderEbookPreparedPublication, val chapter: ReaderChapter,
    val currentAnchor: Int, val paper: ReaderPaper, val fontSizeSp: Int, val geometry: ReaderEbookGeometry, val userScrollEnabled: Boolean = true) {
    init { require(currentAnchor in 0..chapter.scalarCount && fontSizeSp in 14..32)
        require(chapter.bookId == prepared.lease.bookId && chapter.index == prepared.lease.chapterIndex &&
            chapter.bodyVersion == prepared.lease.bodyVersion && chapter.processingVersion == prepared.lease.processingVersion) }
}
internal data class ReaderEbookReady(val lease: EbookRenderLease, val layoutEpoch: Long, val layoutToken: String,
    val scalar: Int, val widthPx: Int, val heightPx: Int)
internal data class ReaderEbookVisibleAnchor(val lease: EbookRenderLease, val layoutEpoch: Long, val layoutToken: String, val scalar: Int)
internal data class ReaderEbookRenderFailure(val lease: EbookRenderLease, val layoutEpoch: Long, val layoutToken: String?,
    val stage: String, val cause: Exception)
internal enum class ReaderEbookPhase { Restoring, Ready, Failed }

internal data class ReaderEbookBodyTap(val lease: EbookRenderLease, val layoutEpoch: Long, val layoutToken: String) {
    val physicalViewId get() = lease.renderId
}
internal data class ReaderEbookSelectionState(val lease: EbookRenderLease, val layoutEpoch: Long, val layoutToken: String, val active: Boolean) {
    val physicalViewId get() = lease.renderId
}
