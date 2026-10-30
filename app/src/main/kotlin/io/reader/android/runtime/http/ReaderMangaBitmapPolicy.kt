package io.reader.android.runtime.http

/** Preflight for known simultaneous Host surfaces. It is not an aggregate Bitmap/GPU/process-memory guarantee.
 * The 32 MiB local envelope follows the fixed Harmony graphics Host; Android's actual stages differ.
 */
internal object ReaderMangaBitmapPolicy {
    const val MAX_ENCODED_BYTES = 16L * 1024 * 1024
    const val MAX_WORKING_BYTES = 32L * 1024 * 1024
    const val BUDGET_ERROR = "MANGA_IMAGE_WORKING_BUDGET"
    // inPreferredConfig is a preference: alpha or wide color may require ARGB_8888 or RGBA_F16.
    private const val MAX_SOFTWARE_DECODE_BYTES_PER_PIXEL = 8

    fun ordinary(width: Int, height: Int, encodedBytes: Long) {
        ordinaryAllocation(width, height, encodedBytes, raster(width, height, MAX_SOFTWARE_DECODE_BYTES_PER_PIXEL))
    }

    internal fun ordinaryAllocation(width: Int, height: Int, encodedBytes: Long, allocationBytes: Long) {
        val reserve = encodedReserve(encodedBytes)
        checkBudget(allocationBytes > 0)
        checkBudget(sum(reserve, codec(width, height), allocationBytes) <= MAX_WORKING_BYTES)
    }

    /** Core c310 graphics envelope, validated before any dimension-sized storage is allocated. */
    fun graphicsDimensions(width: Int, height: Int) {
        checkBudget(width in 1..4096 && height in 1..65536 && product(width.toLong(), height.toLong()) <= 16L * 1024 * 1024)
    }

    data class GraphicsReservation(val targetBytes: Long, val stripBytes: Long, val maximumOutputBytes: Int)

    fun graphics(width: Int, height: Int, encodedBytes: Long, maximumStripHeight: Int): GraphicsReservation {
        graphicsDimensions(width, height)
        checkBudget(maximumStripHeight in 1..height)
        val target = raster(width, height, 2)
        val strip = raster(width, maximumStripHeight, MAX_SOFTWARE_DECODE_BYTES_PER_PIXEL)
        val reserve = encodedReserve(encodedBytes)
        checkBudget(sum(reserve, codec(width, height), target, strip) <= MAX_WORKING_BYTES)
        // ByteArrayOutputStream may hold up to twice the accepted output, then toByteArray copies it.
        // Bound writes before growth; reserve those three output copies independently from the input.
        val available = MAX_WORKING_BYTES - sum(reserve, target)
        val output = minOf(MAX_ENCODED_BYTES, available / 3)
        checkBudget(output >= 32)
        return GraphicsReservation(target, strip, output.toInt())
    }

    internal fun requireAllocation(actualBytes: Long, reservedBytes: Long) {
        checkBudget(actualBytes > 0 && actualBytes <= reservedBytes)
    }

    internal fun requireOutputSize(currentBytes: Int, additionalBytes: Int, maximumBytes: Int) {
        checkBudget(currentBytes >= 0 && additionalBytes >= 0 && maximumBytes > 0 &&
            currentBytes.toLong() + additionalBytes.toLong() <= maximumBytes.toLong())
    }

    private fun encodedReserve(bytes: Long): Long {
        checkBudget(bytes in 1..MAX_ENCODED_BYTES)
        return product(bytes, 3)
    }

    private fun raster(width: Int, height: Int, bytesPerPixel: Int): Long {
        checkBudget(width > 0 && height > 0)
        // Reserve row padding as well as the logical pixels; actual returned allocation is checked again.
        return product(padded(width), height.toLong(), bytesPerPixel.toLong())
    }

    private fun codec(width: Int, height: Int): Long {
        checkBudget(width > 0 && height > 0)
        return product(padded(width), padded(height), 8)
    }

    private fun padded(value: Int): Long = ((value.toLong() + 31) / 32) * 32
    private fun product(vararg values: Long): Long = checked { values.fold(1L) { a, b -> Math.multiplyExact(a, b) } }
    private fun sum(vararg values: Long): Long = checked { values.fold(0L) { a, b -> Math.addExact(a, b) } }
    private inline fun checked(block: () -> Long): Long = try { block() }
        catch (_: ArithmeticException) { throw IllegalArgumentException(BUDGET_ERROR) }
    private fun checkBudget(condition: Boolean) { require(condition) { BUDGET_ERROR } }
}
