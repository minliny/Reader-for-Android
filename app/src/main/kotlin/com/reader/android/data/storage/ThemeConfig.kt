package com.reader.android.data.storage

data class ThemeConfig(
    val backgroundColor: Long = 0xFFFFFFFF,
    val textColor: Long = 0xFF000000,
    val accentColor: Long = 0xFF6200EE,
    val backgroundAlpha: Float = 1.0f
)

data class ThemePair(
    val dayTheme: ThemeConfig = ThemeConfig(),
    val nightTheme: ThemeConfig = ThemeConfig(
        backgroundColor = 0xFF1E1E1E,
        textColor = 0xFFE0E0E0,
        accentColor = 0xFFBB86FC,
        backgroundAlpha = 1.0f
    )
)
