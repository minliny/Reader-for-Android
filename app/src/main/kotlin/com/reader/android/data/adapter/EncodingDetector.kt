package com.reader.android.data.adapter

data class EncodingResult(
    val encoding: String,
    val confidence: Float,
    val hasBom: Boolean
)

object EncodingDetector {
    fun detect(bytes: ByteArray): EncodingResult {
        // BOM detection
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return EncodingResult("UTF-8", 1.0f, true)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return EncodingResult("UTF-16LE", 1.0f, true)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return EncodingResult("UTF-16BE", 1.0f, true)

        // UTF-8 validity check
        if (isValidUtf8(bytes)) return EncodingResult("UTF-8", 0.9f, false)

        // Heuristic: if high bytes appear frequently (0x80-0xFF), likely GBK/Big5
        val highByteRatio = bytes.count { it.toInt() and 0x80 != 0 }.toFloat() / bytes.size.coerceAtLeast(1)
        return if (highByteRatio > 0.3f)
            EncodingResult("GBK", 0.5f, false)
        else
            EncodingResult("UTF-8", 0.3f, false)
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC0..0xDF -> { if (i + 1 >= bytes.size) return false; i += 2 }
                b in 0xE0..0xEF -> { if (i + 2 >= bytes.size) return false; i += 3 }
                b in 0xF0..0xF7 -> { if (i + 3 >= bytes.size) return false; i += 4 }
                else -> return false
            }
        }
        return true
    }
}
