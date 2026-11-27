package io.reader.rebuild.host.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import com.reader.core.protocol.M3MangaResourceInspectData
import com.reader.core.protocol.M3MangaResourceInspectDataAbsent
import com.reader.core.protocol.M3MangaResourceInspectDataPresent
import java.io.File
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class MangaPixelBudgetExceeded : IllegalStateException("Manga image exceeds decoded pixel budget")

internal class MangaTileImage(
    val encodedWidth: Int,
    val encodedHeight: Int,
    val sampleSize: Int,
    val orientation: Int,
    private val tiles: List<Tile>,
    private val pixels: MangaPixelBudget.Lease,
) : AutoCloseable {
    data class Tile(val bitmap: Bitmap, val start: Int, val end: Int)

    val tileCount get() = tiles.size
    val orientationSwapsAxes get() = orientation in 5..8
    val aspectRatio get() = if (orientationSwapsAxes) encodedHeight.toDouble() / encodedWidth
        else encodedWidth.toDouble() / encodedHeight
    private var closed = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun originalPoint(x: Double, y: Double): Pair<Double, Double> {
        require(x in 0.0..1.0 && y in 0.0..1.0)
        return when (orientation) {
            2 -> 1 - x to y
            3 -> 1 - x to 1 - y
            4 -> x to 1 - y
            5 -> y to x
            6 -> y to 1 - x
            7 -> 1 - y to 1 - x
            8 -> 1 - y to x
            else -> x to y
        }
    }

    /** Tile coordinates refer to encoded rows. Orientation is a single drawing transform. */
    @Synchronized fun draw(canvas: Canvas, destination: RectF) {
        check(!closed)
        val source = floatArrayOf(0f, 0f, encodedWidth.toFloat(), 0f, 0f, encodedHeight.toFloat())
        val left = destination.left
        val right = destination.right
        val top = destination.top
        val bottom = destination.bottom
        val target = when (orientation) {
            2 -> floatArrayOf(right, top, left, top, right, bottom)
            3 -> floatArrayOf(right, bottom, left, bottom, right, top)
            4 -> floatArrayOf(left, bottom, right, bottom, left, top)
            5 -> floatArrayOf(left, top, left, bottom, right, top)
            6 -> floatArrayOf(right, top, right, bottom, left, top)
            7 -> floatArrayOf(right, bottom, right, top, left, bottom)
            8 -> floatArrayOf(left, bottom, left, top, right, bottom)
            else -> floatArrayOf(left, top, right, top, left, bottom)
        }
        val transform = Matrix()
        check(transform.setPolyToPoly(source, 0, target, 0, 3))
        val saved = canvas.save()
        try {
            canvas.concat(transform)
            for (tile in tiles) canvas.drawBitmap(tile.bitmap, null,
                RectF(0f, tile.start.toFloat(), encodedWidth.toFloat(), tile.end.toFloat()), paint)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        tiles.forEach { it.bitmap.recycle() }
        pixels.close()
    }
}

internal object MangaImageDecoder {
    suspend fun decode(file: File, inspection: M3MangaResourceInspectData, preferredWidth: Int): MangaTileImage {
        require(preferredWidth in 1..4096)
        val (profile, orientation) = when (inspection) {
            is M3MangaResourceInspectDataAbsent -> inspection.decodeProfile to 1
            is M3MangaResourceInspectDataPresent -> inspection.decodeProfile to inspection.orientation.toInt()
        }
        check(orientation in 1..8)
        val (width, height) = dimensions(file)
        check(profile.encodedWidth == null || profile.encodedWidth == width.toLong())
        check(profile.encodedHeight == null || profile.encodedHeight == height.toLong())
        val sourcePixels = width.toLong() * height
        if (sourcePixels >= MangaPixelBudget.LIMIT) throw MangaPixelBudgetExceeded()
        val displayWidth = if (orientation in 5..8) height else width
        var sample = 1
        while (displayWidth / (sample * 2) >= preferredWidth) sample *= 2
        val tileStep = minOf(1024L * sample, height.toLong()).toInt()
        val reservation = ceilDiv(width, sample).toLong() * ceilDiv(height, sample)
        val scratch = MangaPixelBudget.reserve(sourcePixels)
        try {
            val owned = MangaPixelBudget.reserve(reservation)
            val tiles = mutableListOf<MangaTileImage.Tile>()
            try {
                val decoder = open(file)
                try {
                    check(decoder.width == width && decoder.height == height)
                    var actualPixels = 0L
                    for (start in 0 until height step tileStep) {
                        currentCoroutineContext().ensureActive()
                        val end = minOf(start + tileStep, height)
                        val bitmap = decoder.decodeRegion(Rect(0, start, width, end), BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }) ?: throw IOException("Manga tile could not be decoded")
                        tiles.add(MangaTileImage.Tile(bitmap, start, end))
                        actualPixels += bitmap.width.toLong() * bitmap.height
                        check(actualPixels <= reservation) { "Manga decoder exceeded its allocation" }
                    }
                } finally { decoder.recycle() }
                return MangaTileImage(width, height, sample, orientation, tiles, owned)
            } catch (error: Throwable) {
                tiles.forEach { it.bitmap.recycle() }
                owned.close()
                throw error
            }
        } finally { scratch.close() }
    }

    fun dimensions(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) throw IOException("Manga image geometry unavailable")
        return width to height
    }

    @Suppress("DEPRECATION")
    fun open(file: File): BitmapRegionDecoder =
        (if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(file.path)
        else BitmapRegionDecoder.newInstance(file.path, false)) ?: throw IOException("Manga region decoder unavailable")

    private fun ceilDiv(n: Int, d: Int): Int = (n - 1) / d + 1
}

/** Bounds every live decoded image and the decoder's worst-case source allocation. */
internal object MangaPixelBudget {
    const val LIMIT = 24_000_000L
    private var used = 0L

    @Synchronized fun reserve(count: Long): Lease {
        if (count !in 1..(LIMIT - used)) throw MangaPixelBudgetExceeded()
        used += count
        return Lease(count)
    }

    @Synchronized private fun release(count: Long) {
        used -= count
    }

    internal class Lease(private val count: Long) : AutoCloseable {
        private var closed = false

        @Synchronized override fun close() {
            if (!closed) release(count)
            closed = true
        }
    }
}
