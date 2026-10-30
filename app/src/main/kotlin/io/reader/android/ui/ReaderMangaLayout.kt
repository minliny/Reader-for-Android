package io.reader.android.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/** Coordinates are fractions of the actual full image rectangle, never viewport fractions. */
internal data class ReaderMangaAnchor(
    val pageId: String,
    val ordinal: Int,
    val resourceRef: String,
    val x: Double,
    val y: Double,
)

/** Epoch is issued by the reading owner; a Compose remount cannot restart it. */
internal data class ReaderMangaLayoutTicket(
    val window: ReaderMangaWindowTicket,
    val epoch: Long,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val zoom: Float,
)

/** Desired native scroll position; this is never itself a measured or saveable location. */
internal data class ReaderMangaRestoreRequest(val id: Long, val anchor: ReaderMangaAnchor)

/** Issued by the surface only after the current native layout has actually drawn. */
internal data class ReaderMangaMeasuredAnchor(
    val ticket: ReaderMangaLayoutTicket,
    val anchor: ReaderMangaAnchor,
    val restoredRequestId: Long? = null,
    val restoredTargetPageId: String? = null,
)

internal data class ReaderMangaImageRect(val left: Double, val top: Double, val width: Double, val height: Double) {
    val right get() = left + width
    val bottom get() = top + height
    val valid get() = listOf(left, top, width, height).all(Double::isFinite) && width > 0 && height > 0
    fun intersects(other: ReaderMangaImageRect) = valid && other.valid && right > other.left && left < other.right && bottom > other.top && top < other.bottom
}

internal data class ReaderMangaImageGeometry(
    val pageId: String,
    val ordinal: Int,
    val resourceRef: String,
    val transferId: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rect: ReaderMangaImageRect,
)

internal data class ReaderMangaNativeRestore(val itemIndex: Int, val scrollOffsetPx: Int, val horizontalOffsetPx: Int)

/** A draw or a layout-induced coordinate change is not evidence of a new user scroll. */
internal class ReaderMangaScrollAdmission {
    private var ticket: ReaderMangaLayoutTicket? = null
    private var restoring = true
    private var active = false
    private var idleMovement = false

    fun bind(value: ReaderMangaLayoutTicket?) {
        if (ticket == value) return
        ticket = value
        restoring = true
        active = false
        idleMovement = false
    }

    fun beginRestore(issued: ReaderMangaLayoutTicket) {
        if (ticket != issued) return
        restoring = true
        active = false
        idleMovement = false
    }

    fun confirmRestore(issued: ReaderMangaLayoutTicket) {
        if (ticket != issued) return
        restoring = false
        active = false
        idleMovement = false
    }

    /** Compose's native scroll states include the drag and its fling, not just pointer-up. */
    fun nativeMotion(issued: ReaderMangaLayoutTicket?, scrolling: Boolean, transforming: Boolean,
        restorationPending: Boolean = false) {
        if (ticket == null || ticket != issued) return
        // A queued native scrollToItem activity from the restore cannot arm a later ordinary observation.
        if (restorationPending) return
        if (restoring || transforming) {
            active = false
            idleMovement = false
        } else if (scrolling) {
            active = true
            idleMovement = false
        } else if (active) {
            active = false
            idleMovement = true
        }
    }

    fun canObserve(issued: ReaderMangaLayoutTicket) = ticket == issued && !restoring && !active && idleMovement

    /** Keep the permit through stale/missing draw callbacks; consume only the admitted actual location. */
    fun accepted(issued: ReaderMangaLayoutTicket) {
        if (canObserve(issued)) idleMovement = false
    }
}

/** Reader coordinate adaptation only. Compose owns gestures, scrolling, clamping and item layout. */
internal object ReaderMangaLayoutGeometry {
    fun accepts(ticket: ReaderMangaLayoutTicket, viewport: ReaderMangaImageRect, image: ReaderMangaImageGeometry): Boolean {
        if (!viewport.valid || !image.rect.valid || image.sourceWidth <= 0 || image.sourceHeight <= 0 ||
            ticket.viewportWidthPx <= 0 || ticket.viewportHeightPx <= 0 || !ticket.zoom.isFinite() || ticket.zoom < 1f) return false
        val expectedWidth = ticket.viewportWidthPx.toDouble() * ticket.zoom
        val expectedHeight = expectedWidth * image.sourceHeight / image.sourceWidth
        return abs(viewport.width - ticket.viewportWidthPx) <= 1.0 && abs(viewport.height - ticket.viewportHeightPx) <= 1.0 &&
            abs(image.rect.width - expectedWidth) <= 1.0 && abs(image.rect.height - expectedHeight) <= 1.0
    }

    /** A post-position callback can lag native scrollToItem, even with the correct window and image size. */
    fun matchesNativePlacement(viewport: ReaderMangaImageRect, image: ReaderMangaImageGeometry,
        nativeRelativeTopPx: Int, nativeHeightPx: Int, horizontalScrollPx: Int): Boolean =
        viewport.valid && image.rect.valid && nativeHeightPx > 0 && horizontalScrollPx >= 0 &&
            abs(image.rect.top - (viewport.top + nativeRelativeTopPx)) <= 1.0 &&
            abs(image.rect.height - nativeHeightPx) <= 1.0 &&
            abs(image.rect.left - (viewport.left - horizontalScrollPx)) <= 1.0

    fun visibleAnchor(ticket: ReaderMangaLayoutTicket, viewport: ReaderMangaImageRect, images: List<ReaderMangaImageGeometry>): ReaderMangaAnchor? {
        val image = images.filter { accepts(ticket, viewport, it) && it.rect.intersects(viewport) }
            .minWithOrNull(compareBy<ReaderMangaImageGeometry> { it.rect.top }.thenBy { it.ordinal }) ?: return null
        return ReaderMangaAnchor(image.pageId, image.ordinal, image.resourceRef,
            ((viewport.left - image.rect.left) / image.rect.width).coerceIn(0.0, 1.0),
            ((viewport.top - image.rect.top) / image.rect.height).coerceIn(0.0, 1.0))
    }

    fun restore(ticket: ReaderMangaLayoutTicket, target: ReaderMangaAnchor, pages: List<ReaderMangaImageGeometry>): ReaderMangaNativeRestore? {
        if (!target.x.isFinite() || !target.y.isFinite() || target.x !in 0.0..1.0 || target.y !in 0.0..1.0 ||
            ticket.viewportWidthPx <= 0 || !ticket.zoom.isFinite() || ticket.zoom < 1f) return null
        val index = pages.indexOfFirst { it.pageId == target.pageId && it.ordinal == target.ordinal && it.resourceRef == target.resourceRef }
        if (index < 0) return null
        val image = pages[index]
        if (image.sourceWidth <= 0 || image.sourceHeight <= 0) return null
        val width = ticket.viewportWidthPx.toDouble() * ticket.zoom
        val height = width * image.sourceHeight / image.sourceWidth
        if (!width.isFinite() || !height.isFinite() || width > Int.MAX_VALUE || height > Int.MAX_VALUE) return null
        // An end-of-image coordinate still addresses this image, so retain its final physical pixel.
        val vertical = (target.y * height).roundToInt().coerceIn(0, (height.roundToInt() - 1).coerceAtLeast(0))
        return ReaderMangaNativeRestore(index, vertical, (target.x * width).roundToInt())
    }
}
