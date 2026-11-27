package io.reader.rebuild.host.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.reader.core.protocol.M3MangaGraphicsPlan
import com.reader.core.protocol.M3MangaGraphicsPlanStripsItem
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Core assigns the source/destination strips. Android only decodes and encodes the assigned pixels. */
internal suspend fun applyMangaGraphicsPlan(file: File, plan: M3MangaGraphicsPlan, byteLimit: Long) {
    require(plan.readerMangaGraphicsPlan && plan.width in 1..4096 && plan.height in 1..65_535 &&
        plan.width * plan.height <= 8_000_000 && plan.strips.size in 1..64)
    fun covers(order: (M3MangaGraphicsPlanStripsItem) -> Long): Boolean {
        var end = 0L
        for (strip in plan.strips.sortedBy(order)) {
            if (order(strip) != end || strip.height !in 1..(plan.height - end)) return false
            end += strip.height
        }
        return end == plan.height
    }
    require(covers { it.sourceY } && covers { it.targetY })
    val width = plan.width.toInt()
    val height = plan.height.toInt()
    MangaPixelBudget.reserve(width.toLong() * height * 3).use {
        val decoder = MangaImageDecoder.open(file)
        try {
            check(decoder.width == width && decoder.height == height)
            val matrix = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            try {
                val canvas = Canvas(matrix)
                for (strip in plan.strips) {
                    currentCoroutineContext().ensureActive()
                    val pixels = decoder.decodeRegion(
                        Rect(0, strip.sourceY.toInt(), width, (strip.sourceY + strip.height).toInt()),
                        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
                    ) ?: throw IOException("Manga strip could not be decoded")
                    try { canvas.drawBitmap(pixels, 0f, strip.targetY.toFloat(), null) } finally { pixels.recycle() }
                }
                val output = File.createTempFile("transformed-", ".image", file.parentFile)
                try {
                    output.outputStream().use { stream ->
                        check(matrix.compress(Bitmap.CompressFormat.JPEG, plan.quality.wireValue.toInt(),
                            BoundedImageOutput(stream, byteLimit)))
                    }
                    currentCoroutineContext().ensureActive()
                    Files.move(output.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally { output.delete() }
            } finally { matrix.recycle() }
        } finally { decoder.recycle() }
    }
}

private class BoundedImageOutput(stream: OutputStream, private var remaining: Long) : FilterOutputStream(stream) {
    override fun write(value: Int) {
        charge(1)
        out.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        charge(length)
        out.write(bytes, offset, length)
    }

    private fun charge(size: Int) {
        if (size > remaining) throw IOException("Transformed manga image exceeds byte budget")
        remaining -= size
    }
}
