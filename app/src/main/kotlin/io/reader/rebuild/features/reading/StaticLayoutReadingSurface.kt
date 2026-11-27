package io.reader.rebuild.features.reading

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.MainThread
import kotlin.math.abs
import com.reader.core.theme.ReaderPaperThemes
import io.reader.rebuild.design.ReaderFonts
import kotlin.math.roundToInt

/** Android text surface. The caller owns chapter identity, saved revisions and gesture arbitration. */
@MainThread
internal class StaticLayoutReadingSurface(context: Context) : View(context), ReadingSurface<TextSurfaceChapter> {
    private val face by lazy { Typeface.createFromAsset(context.assets, ReaderFonts.serifPath) }
    private var document: TextSurfaceChapter? = null
    private var engine: StaticTextLayout? = null
    private var anchor: Long? = null
    private var selected: ReadingSelection? = null
    private var spoken: ReadingSelection? = null
    private var style = ReadingStyle()
    private var sideInset = 0
    private var scrollOffset = 0
    private var selectionAnchor: Long? = null
    var onAnchor: (Long) -> Unit = {}
    var onSurfaceTap: () -> Unit = {}
    var onPageBoundary: (Int) -> Unit = {}
    var scrolling = false
        set(value) {
            if (field == value) return
            field = value
            syncScrollOffset()
            invalidate()
        }

    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            performClick()
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            val point = scalarAt(event.x, event.y) ?: return
            selectionAnchor = point
            select(point, point + 1) {}
        }

        override fun onScroll(first: MotionEvent?, last: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scrolling || anchor == null || abs(dx) > abs(dy)) return false
            val layout = engine ?: return false
            scrollOffset = (scrollOffset + dy.roundToInt()).coerceIn(0, scrollLimit(layout))
            anchor = layout.offsetAt(scrollOffset)
            anchor?.let(onAnchor)
            invalidate()
            return true
        }

        override fun onFling(first: MotionEvent?, last: MotionEvent, vx: Float, vy: Float): Boolean {
            if (first == null || scrolling || abs(vx) < abs(vy) ||
                abs(first.x - last.x) < densityPixels(36)) return false
            val delta = if (last.x < first.x) 1 else -1
            if (!movePage(delta)) onPageBoundary(delta)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // The system owns edge-origin drags so predictive back can commit or cancel them.
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            (event.x < densityPixels(24) || event.x > width - densityPixels(24))) return false
        val selecting = selectionAnchor
        if (selecting != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> scalarAt(event.x, event.y)?.let {
                    select(minOf(selecting, it), maxOf(selecting, it) + 1) {}
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> selectionAnchor = null
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE) return true
        }
        return gesture.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onSurfaceTap()
        return true
    }

    override fun open(chapter: TextSurfaceChapter, anchor: Long, style: ReadingStyle) {
        document = chapter
        this.anchor = anchor.takeIf(chapter.coordinates::hasCharacter)
        this.style = style
        selected = null
        spoken = null
        measureText()
    }

    override fun reflow(style: ReadingStyle, anchor: Long?) {
        if (this.style == style && anchor == null) return
        this.style = style
        if (anchor != null && document?.coordinates?.hasCharacter(anchor) == true) this.anchor = anchor
        measureText()
    }

    override fun restore(scalar: Long, completion: (ReadingPosition?) -> Unit) {
        if (document?.coordinates?.hasCharacter(scalar) != true) {
            completion(null)
            return
        }
        anchor = scalar
        syncScrollOffset()
        onAnchor(scalar)
        invalidate()
        currentPosition(completion)
    }

    override fun currentPosition(completion: (ReadingPosition?) -> Unit) {
        val value = anchor
        val current = engine
        if (value == null || current?.pageFor(value) == null) completion(null)
        else completion(ReadingPosition(value, checkNotNull(current.coordinates.substring(value, value + 1))))
    }

    override fun select(start: Long, end: Long, completion: (ReadingSelection?) -> Unit) {
        val map = document?.coordinates
        val range = if (map != null && start < end && map.utf16(start) != null && map.utf16(end) != null)
            ReadingSelection(start, end) else null
        if (range != null) {
            selected = range
            invalidate()
        }
        completion(range)
    }

    override fun currentSelection(completion: (ReadingSelection?) -> Unit) = completion(selected)

    fun projectSpeech(highlight: SpeechHighlight?) {
        val coordinates = document?.coordinates
        spoken = highlight?.takeIf { coordinates?.utf16(it.start) != null && coordinates.utf16(it.endExclusive) != null }
            ?.let { ReadingSelection(it.start, it.endExclusive) }
        invalidate()
    }

    fun scalarAt(x: Float, y: Float): Long? {
        val current = engine ?: return null
        val absoluteY = y - paddingTop + if (scrolling) scrollOffset else 0
        val page = if (scrolling) current.pages.firstOrNull { absoluteY >= it.top && absoluteY < it.bottom }
            else anchor?.let(current::pageFor)
        return current.hit(page ?: return null, x - paddingLeft - sideInset,
            if (scrolling) absoluteY - page.top else absoluteY)
    }

    fun movePage(delta: Int): Boolean {
        val current = engine ?: return false
        val page = anchor?.let(current::pageFor) ?: return false
        val index = current.pages.binarySearchBy(page.start) { it.start }
        val next = current.pages.getOrNull(index + delta) ?: return false
        anchor = next.start
        syncScrollOffset()
        onAnchor(next.start)
        invalidate()
        return true
    }

    override fun retire() {
        document = null
        anchor = null
        selected = null
        spoken = null
        selectionAnchor = null
        engine = null
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        measureText()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor(style.paper))
        val current = engine ?: return
        val page = anchor?.let(current::pageFor) ?: return
        val saved = canvas.save()
        try {
            canvas.translate((paddingLeft + sideInset).toFloat(), paddingTop.toFloat())
            canvas.clipRect(0, 0, width - paddingLeft - paddingRight - sideInset * 2, height - paddingTop - paddingBottom)
            val selection = ReaderPaperThemes.all.firstOrNull {
                Color.parseColor(style.paper) == it.paperStart.toInt()
            }?.selection ?: ReaderPaperThemes.all.first().selection
            if (scrolling) {
                val visibleHeight = height - paddingTop - paddingBottom
                current.pages.asSequence().dropWhile { it.bottom <= scrollOffset }
                    .takeWhile { it.top < scrollOffset + visibleHeight }.forEach { tile ->
                        val tileSave = canvas.save()
                        canvas.translate(0f, (tile.top - scrollOffset).toFloat())
                        current.draw(canvas, tile, selected ?: spoken, selection.toInt())
                        canvas.restoreToCount(tileSave)
                    }
            } else current.draw(canvas, page, selected ?: spoken, selection.toInt())
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun measureText() {
        sideInset = densityPixels(style.horizontalInset).roundToInt()
        val contentWidth = width - paddingLeft - paddingRight - sideInset * 2
        val contentHeight = height - paddingTop - paddingBottom
        engine = document?.takeIf { contentWidth > 0 && contentHeight > 0 }?.let { chapter ->
            val metrics = resources.displayMetrics
            val appearance = style
            val font = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, appearance.fontSize.toFloat(), metrics)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = font
                typeface = face
                color = Color.parseColor(appearance.ink)
                letterSpacing = (appearance.letterSpacing / appearance.fontSize).toFloat()
            }
            StaticTextLayout.create(chapter, contentWidth, contentHeight, paint,
                font * (appearance.lineHeight / appearance.fontSize).toFloat(),
                densityPixels(appearance.paragraphSpacing).roundToInt())
        }
        syncScrollOffset()
        invalidate()
    }

    private fun syncScrollOffset() {
        val layout = engine ?: return
        val value = anchor ?: return
        scrollOffset = layout.lineTop(value).coerceAtMost(scrollLimit(layout))
    }

    private fun scrollLimit(layout: StaticTextLayout) =
        layout.lineTop(layout.coordinates.chapterEnd - 1).coerceAtLeast(0)

    private fun densityPixels(dp: Int) = dp * resources.displayMetrics.density
}
