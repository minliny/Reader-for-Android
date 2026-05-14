package com.reader.android.data.storage

enum class FontFamily(val displayName: String) {
    SYSTEM("系统默认"),
    SERIF("宋体/衬线"),
    SANS_SERIF("黑体/无衬线"),
    MONO("等宽")
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

        fun custom(name: String): FontConfig =
            FontConfig(family = FontFamily.SYSTEM, customTypeface = name)
    }
}
