package io.reader.rebuild.features.reading

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import androidx.annotation.MainThread
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
    private var style = ReadingStyle()
    private var sideInset = 0

    override fun open(chapter: TextSurfaceChapter, anchor: Long, style: ReadingStyle) {
        document = chapter
        this.anchor = anchor.takeIf(chapter.coordinates::hasCharacter)
        this.style = style
        selected = null
        measureText()
    }

    override fun reflow(style: ReadingStyle, anchor: Long?) {
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

    fun scalarAt(x: Float, y: Float): Long? {
        val current = engine ?: return null
        val page = anchor?.let(current::pageFor) ?: return null
        return current.hit(page, x - paddingLeft - sideInset, y - paddingTop)
    }

    fun movePage(delta: Int): Boolean {
        val current = engine ?: return false
        val page = anchor?.let(current::pageFor) ?: return false
        val index = current.pages.binarySearchBy(page.start) { it.start }
        val next = current.pages.getOrNull(index + delta) ?: return false
        anchor = next.start
        invalidate()
        return true
    }

    override fun retire() {
        document = null
        anchor = null
        selected = null
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
            current.draw(canvas, page, selected, ReaderPaperThemes.all.first().selection.toInt())
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
        invalidate()
    }

    private fun densityPixels(dp: Int) = dp * resources.displayMetrics.density
}
