package com.reader.android.data.storage

data class AlphaConfig(val value: Float) {
    init {
        require(value in 0.0f..1.0f) { "Alpha must be in [0.0, 1.0], got $value" }
    }

    val isOpaque: Boolean get() = value >= 1.0f
    val isTransparent: Boolean get() = value <= 0.0f
    val percentage: Int get() = (value * 100).toInt()

    companion object {
        val OPAQUE = AlphaConfig(1.0f)
        val SEMI_TRANSPARENT = AlphaConfig(0.7f)
        val HALF = AlphaConfig(0.5f)
        val LIGHT = AlphaConfig(0.3f)
        val TRANSPARENT = AlphaConfig(0.0f)

        fun safe(value: Float): AlphaConfig =
            AlphaConfig(value.coerceIn(0.0f, 1.0f))
    }
}
