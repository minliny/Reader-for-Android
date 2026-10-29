package io.reader.android.ui.ebookdraft

import kotlin.math.abs

/** Already typed/exact-scalar measurement; owner/lease/epoch/token gates remain at the WebView owner. */
internal data class ReaderEbookMeasurement(
    val left: Double, val right: Double, val top: Double, val bottom: Double,
    val width: Double, val height: Double, val viewportWidth: Double, val viewportHeight: Double,
    val expectedWidth: Double, val expectedHeight: Double, val topInset: Double, val bottomInset: Double)

/** Reader observation admission only: no coordinate clamp, restore, synthetic anchor or Core write. */
internal object ReaderEbookMeasurementAdmission {
    /** Ready still authenticates an actually visible anchor after the native visual/draw fence. */
    fun requireReady(value: ReaderEbookMeasurement) {
        check(validAndIntersects(value)) { "ANCHOR_NOT_IN_REAL_VIEWPORT" }
    }

    /** A missed transient sample supplies no new display position and cannot retire the render. */
    fun observePosition(value: ReaderEbookMeasurement, onVisible: () -> Unit) {
        if (!validAndIntersects(value)) return
        onVisible()
    }

    private fun validAndIntersects(v: ReaderEbookMeasurement): Boolean {
        check(v.left.isFinite() && v.right.isFinite() && v.top.isFinite() && v.bottom.isFinite() &&
            v.width.isFinite() && v.height.isFinite() && v.viewportWidth.isFinite() && v.viewportHeight.isFinite())
        check(v.right > v.left && v.bottom > v.top && v.width > 0 && v.height > 0)
        check(abs(v.viewportWidth - v.expectedWidth) <= 1.1 && abs(v.viewportHeight - v.expectedHeight) <= 1.1)
        return v.bottom > v.topInset && v.top < v.viewportHeight - v.bottomInset &&
            v.right > 0 && v.left < v.viewportWidth
    }
}
