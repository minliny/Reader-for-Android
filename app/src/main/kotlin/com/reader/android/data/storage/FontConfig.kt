package com.reader.android.data.storage

/**
 * Reader font family selector.
 *
 * Each enum value maps 1:1 to a contract `--fd-ds-font-*` token (see
 * `Reader UI/contracts/fixtures/token.fixtures.json`). The contract's
 * `platforms.kotlin` field provides the Compose [androidx.compose.ui.text.font.FontFamily]
 * reference used at render time via [com.reader.ui.tokens.ReaderTokenAdapter.font].
 *
 * Mapping:
 *  - SYSTEM     → --fd-ds-font-sans       (FontFamily.Default)
 *  - SERIF      → --fd-ds-font-serif      (FontFamily.Serif)
 *  - SANS_SERIF → --fd-ds-font-sans       (FontFamily.Default)
 *  - MONO       → --fd-ds-font-mono       (FontFamily.Monospace)
 *  - KAI        → --fd-ds-font-kai        (FontFamily.Serif — no native Compose equivalent)
 *  - FANGSONG   → --fd-ds-font-fangsong   (FontFamily.Serif — no native Compose equivalent)
 */
enum class FontFamily(val displayName: String) {
    SYSTEM("系统默认"),
    SERIF("宋体/衬线"),
    SANS_SERIF("黑体/无衬线"),
    MONO("等宽"),
    KAI("楷体"),
    FANGSONG("仿宋")
}

data class FontConfig(
    val family: FontFamily = FontFamily.SYSTEM,
    val customTypeface: String? = null
) {
    companion object {
        val DEFAULT = FontConfig()
        val SERIF = FontConfig(family = FontFamily.SERIF)
        val SANS = FontConfig(family = FontFamily.SANS_SERIF)
        val MONO = FontConfig(family = FontFamily.MONO)
        val KAI = FontConfig(family = FontFamily.KAI)
        val FANGSONG = FontConfig(family = FontFamily.FANGSONG)

        fun custom(name: String): FontConfig =
            FontConfig(family = FontFamily.SYSTEM, customTypeface = name)
    }
}
