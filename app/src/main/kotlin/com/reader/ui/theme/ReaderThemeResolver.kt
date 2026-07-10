package com.reader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 阅读器主题解析器 —— 对齐 HarmonyOS `ReaderThemeResolver.ets`。
 *
 * 提供 8 个主题（paper/warm/green/blue × day/night），每个主题来自 demo fixture 的真实
 * swatch / ink / paper 颜色。`palette(themeId, isNight)` 返回上下文化颜色，作为
 * [ReaderExtraColors] 的来源，取代静态的 [LightExtra] / [DarkExtra] 切换。
 *
 * 调用方（[ReaderTheme]）根据 `appThemeMode`（system/light/dark）解析 `isNight`，
 * 再用 `(themeId, isNight)` 调用 [palette] 得到当前主题的 [ReaderExtraColors]。
 */
object ReaderThemeResolver {

    /** 单个主题的 swatch 描述。 */
    data class ThemeSwatch(
        val id: String,
        val label: String,
        val isNight: Boolean,
        val color: Color
    )

    /**
     * 8 个主题色板（4 日间 + 4 夜间）。色值来自 demo fixture：
     * 日间 paper=#F5EAD8 / warm=#FBF0DF / green=#E7F0E2 / blue=#E9F1F4，
     * 夜间 #2D2924 / #27231F / #202B26 / #232934。
     */
    val THEMES: List<ThemeSwatch> = listOf(
        ThemeSwatch("paper", "纸张", false, Color(0xFFF5EAD8)),
        ThemeSwatch("warm", "暖色", false, Color(0xFFFBF0DF)),
        ThemeSwatch("green", "护眼", false, Color(0xFFE7F0E2)),
        ThemeSwatch("blue", "蓝色", false, Color(0xFFE9F1F4)),
        ThemeSwatch("paper-night", "纸张夜间", true, Color(0xFF2D2924)),
        ThemeSwatch("warm-night", "暖色夜间", true, Color(0xFF27231F)),
        ThemeSwatch("green-night", "护眼夜间", true, Color(0xFF202B26)),
        ThemeSwatch("blue-night", "蓝色夜间", true, Color(0xFF232934))
    )

    /** 日间 4 swatch（快速面板用）。 */
    val DAY_SWATCHES: List<ThemeSwatch> = THEMES.filterNot { it.isNight }

    /** 取主题 swatch 颜色，未知 id 回退到 paper。 */
    fun swatchColor(themeId: String): Color =
        THEMES.firstOrNull { it.id == themeId }?.color ?: Color(0xFFF5EAD8)

    /** themeId 是否为夜间主题（以 `-night` 结尾）。 */
    fun isNightTheme(themeId: String): Boolean = themeId.endsWith("-night")

    /**
     * 主题专属调色（paper / ink / infoLayer 等），从 8 主题 fixture 取值。
     */
    private data class ThemePalette(
        val paper: Color,
        val readerPaper: Color,
        val paperBright: Color,
        val readerInk: Color,
        val infoLayer: Color,
        val paperStart: Color,
        val paperEnd: Color,
        val surfaceSoft: Color
    )

    // 日间 4 主题：swatch 驱动 paper，ink/infoLayer 用与 swatch 协调的深色。
    private val paperDay = ThemePalette(
        paper = Color(0xFFF5EAD8), readerPaper = Color(0xFFFFF8F4), paperBright = Color(0xFFFBF4E9),
        readerInk = Color(0xFF2B241D), infoLayer = Color(0xFF6F655D),
        paperStart = Color(0xFFFBF4E9), paperEnd = Color(0xFFFBF4E9), surfaceSoft = Color(0xB8FFFCF8)
    )
    private val warmDay = ThemePalette(
        paper = Color(0xFFFBF0DF), readerPaper = Color(0xFFFFFBF2), paperBright = Color(0xFFFFF5E6),
        readerInk = Color(0xFF3A2E22), infoLayer = Color(0xFF7A6A58),
        paperStart = Color(0xFFFFF5E6), paperEnd = Color(0xFFFFF5E6), surfaceSoft = Color(0xB8FFF8EE)
    )
    private val greenDay = ThemePalette(
        paper = Color(0xFFE7F0E2), readerPaper = Color(0xFFF2F8EE), paperBright = Color(0xFFEAF3E5),
        readerInk = Color(0xFF243024), infoLayer = Color(0xFF5E6B5A),
        paperStart = Color(0xFFEAF3E5), paperEnd = Color(0xFFEAF3E5), surfaceSoft = Color(0xB8F0F5EA)
    )
    private val blueDay = ThemePalette(
        paper = Color(0xFFE9F1F4), readerPaper = Color(0xFFF0F6F9), paperBright = Color(0xFFEBF3F6),
        readerInk = Color(0xFF1F2A30), infoLayer = Color(0xFF5C6B72),
        paperStart = Color(0xFFEBF3F6), paperEnd = Color(0xFFEBF3F6), surfaceSoft = Color(0xB8EBF3F6)
    )

    // 夜间 4 主题：swatch 驱动 paper，ink/infoLayer 用夜间的浅色。
    private val paperNight = ThemePalette(
        paper = Color(0xFF2D2924), readerPaper = Color(0xFF24211E), paperBright = Color(0xFF2C2824),
        readerInk = Color(0xFFEADFCE), infoLayer = Color(0xFFBAAD9C),
        paperStart = Color(0xFF302B26), paperEnd = Color(0xFF302B26), surfaceSoft = Color(0xB82A2622)
    )
    private val warmNight = ThemePalette(
        paper = Color(0xFF27231F), readerPaper = Color(0xFF24211E), paperBright = Color(0xFF2C2824),
        readerInk = Color(0xFFEADFCE), infoLayer = Color(0xFFBAAD9C),
        paperStart = Color(0xFF302B26), paperEnd = Color(0xFF302B26), surfaceSoft = Color(0xB82A2622)
    )
    private val greenNight = ThemePalette(
        paper = Color(0xFF202B26), readerPaper = Color(0xFF24211E), paperBright = Color(0xFF2C2824),
        readerInk = Color(0xFFE0DCC8), infoLayer = Color(0xFFAAB09E),
        paperStart = Color(0xFF302B26), paperEnd = Color(0xFF302B26), surfaceSoft = Color(0xB82A2622)
    )
    private val blueNight = ThemePalette(
        paper = Color(0xFF232934), readerPaper = Color(0xFF24211E), paperBright = Color(0xFF2C2824),
        readerInk = Color(0xFFD8DCE4), infoLayer = Color(0xFFA2AAB4),
        paperStart = Color(0xFF302B26), paperEnd = Color(0xFF302B26), surfaceSoft = Color(0xB82A2622)
    )

    private fun paletteFor(themeId: String): ThemePalette = when (themeId) {
        "paper" -> paperDay
        "warm" -> warmDay
        "green" -> greenDay
        "blue" -> blueDay
        "paper-night" -> paperNight
        "warm-night" -> warmNight
        "green-night" -> greenNight
        "blue-night" -> blueNight
        else -> paperDay
    }

    /**
     * 返回上下文化 [ReaderExtraColors]：以 `isNight` 选定的 [LightExtra]/[DarkExtra] 为结构基色
     * （controlSurface / selection / nav 等），用 `themeId` 对应的 [ThemePalette] 覆盖
     * paper / readerPaper / paperBright / readerInk / infoLayer / paperStart / paperEnd / surfaceSoft
     * 等主题相关字段，使颜色随选中主题上下文化。
     */
    fun palette(themeId: String, isNight: Boolean): ReaderExtraColors {
        val base = if (isNight) DarkExtra else LightExtra
        // 夜间模式自动取 "-night" 变体；日间取基础 id，保证 4-swatch 选择器在
        // 深色模式下渲染对应夜间色板。
        val effectiveId = if (isNight && !themeId.endsWith("-night")) "${themeId}-night" else themeId
        val tp = paletteFor(effectiveId)
        return base.copy(
            paper = tp.paper,
            readerPaper = tp.readerPaper,
            paperBright = tp.paperBright,
            readerInk = tp.readerInk,
            infoLayer = tp.infoLayer,
            paperStart = tp.paperStart,
            paperEnd = tp.paperEnd,
            surfaceSoft = tp.surfaceSoft
        )
    }
}
