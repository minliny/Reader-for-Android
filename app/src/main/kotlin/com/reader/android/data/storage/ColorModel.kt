package com.reader.android.data.storage

data class ColorModel(val argb: Long) {
    val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    val blue: Int get() = (argb and 0xFF).toInt()

    fun toHexString(): String = "#%08X".format(argb)

    val isDark: Boolean get() {
        val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
        return luminance < 128
    }

    companion object {
        fun fromHex(hex: String): ColorModel? {
            val cleaned = hex.trimStart('#')
            val argb = when (cleaned.length) {
                6 -> {
                    val rgb = cleaned.toLongOrNull(16) ?: return null
                    0xFF000000 or rgb
                }
                8 -> cleaned.toLongOrNull(16) ?: return null
                else -> return null
            }
            return ColorModel(argb)
        }

        // Preset colors
        val WHITE = ColorModel(0xFFFFFFFF)
        val BLACK = ColorModel(0xFF000000)
        val DARK_BG = ColorModel(0xFF1E1E1E)
        val LIGHT_TEXT = ColorModel(0xFFE0E0E0)
        val DEFAULT_ACCENT = ColorModel(0xFF6200EE)
        val NIGHT_ACCENT = ColorModel(0xFFBB86FC)
    }
}
