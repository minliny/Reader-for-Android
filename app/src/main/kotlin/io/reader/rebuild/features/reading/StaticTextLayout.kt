package io.reader.rebuild.features.reading

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LineHeightSpan
import kotlin.math.max
import kotlin.math.min

internal data class TextSurfaceChapter(val text: String, val chapterStartScalar: Long = 0L) {
    val coordinates = TextScalarMap(text, chapterStartScalar)
}

internal data class TextPage(val start: Long, val end: Long, val top: Int, val bottom: Int)

/** Owns only measured geometry for one Core text projection and one viewport. */
internal class StaticTextLayout private constructor(
    val coordinates: TextScalarMap,
    private val layout: StaticLayout,
    val pages: List<TextPage>,
) {
    fun pageFor(scalar: Long): TextPage? {
        if (!coordinates.hasCharacter(scalar)) return null
        val index = pages.binarySearchBy(scalar) { it.start }
        return pages.getOrNull(if (index >= 0) index else -index - 2)
    }

    fun hit(page: TextPage, x: Float, y: Float): Long? {
        if (!x.isFinite() || !y.isFinite() || x < 0f || x >= layout.width ||
            y < 0f || y >= page.bottom - page.top
        ) return null
        val line = layout.getLineForVertical(page.top + y.toInt())
        val offset = layout.getOffsetForHorizontal(line, x)
        return coordinates.scalarAtOrBefore(offset)?.coerceIn(page.start, page.end - 1)
    }

    fun lineTop(scalar: Long): Int {
        val offset = checkNotNull(coordinates.utf16(scalar))
        return layout.getLineTop(layout.getLineForOffset(offset))
    }

    fun offsetAt(pixel: Int): Long {
        val line = layout.getLineForVertical(pixel.coerceIn(0, (layout.height - 1).coerceAtLeast(0)))
        return checkNotNull(coordinates.scalar(layout.getLineStart(line))).coerceAtMost(coordinates.chapterEnd - 1)
    }

    fun draw(canvas: Canvas, page: TextPage, selected: ReadingSelection?, selectionColor: Int) {
        val saved = canvas.save()
        try {
            canvas.clipRect(0, 0, layout.width, page.bottom - page.top)
            canvas.translate(0f, -page.top.toFloat())
            selected?.let {
                val start = max(it.startScalar, page.start)
                val end = min(it.endScalar, page.end)
                if (start < end) {
                    val path = Path()
                    layout.getSelectionPath(checkNotNull(coordinates.utf16(start)), checkNotNull(coordinates.utf16(end)), path)
                    canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = selectionColor })
                }
            }
            layout.draw(canvas)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    companion object {
        fun create(chapter: TextSurfaceChapter, width: Int, pageHeight: Int, paint: TextPaint,
                   lineHeightPx: Float, paragraphGapPx: Int): StaticTextLayout {
            require(width > 0 && pageHeight > 0 && lineHeightPx.isFinite() && lineHeightPx > 0 && paragraphGapPx >= 0)
            val text = addParagraphSpacing(chapter.text, paragraphGapPx)
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing((lineHeightPx - paint.fontSpacing).coerceAtLeast(0f), 1f)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUseLineSpacingFromFallbacks(true)
            val layout = builder.build()
            val pages = paginate(layout, chapter.coordinates, text.length, pageHeight)
            return StaticTextLayout(chapter.coordinates, layout, pages)
        }

        private fun paginate(layout: StaticLayout, coordinates: TextScalarMap,
                             textLength: Int, pageHeight: Int): List<TextPage> = buildList {
            var line = 0
            while (line < layout.lineCount && layout.getLineStart(line) < textLength) {
                val first = line
                val top = layout.getLineTop(line)
                do { line++ } while (line < layout.lineCount && layout.getLineStart(line) < textLength &&
                    layout.getLineBottom(line) - top <= pageHeight)
                val start = checkNotNull(coordinates.scalar(layout.getLineStart(first)))
                val end = checkNotNull(coordinates.scalar(layout.getLineEnd(line - 1)))
                if (end > start) add(TextPage(start, end, top, layout.getLineBottom(line - 1)))
            }
        }

        private fun addParagraphSpacing(text: String, gap: Int): CharSequence {
            if (gap == 0 || '\n' !in text) return text
            return SpannableString(text).also { spannable ->
                text.forEachIndexed { index, character ->
                    if (character == '\n' && index + 1 < text.length) {
                        spannable.setSpan(ParagraphGap(index + 1, gap), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }
}

private class ParagraphGap(private val paragraphEnd: Int, private val gap: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int,
                              metrics: Paint.FontMetricsInt) {
        if (end == paragraphEnd) {
            metrics.descent += gap
            metrics.bottom += gap
        }
    }
}
