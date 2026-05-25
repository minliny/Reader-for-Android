package com.reader.android.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class ReaderThemeTokenTest {

    @Test
    fun `reader day colors match stitch design system tokens`() {
        val colors = ReaderColors.Day

        assertEquals(0xFFF8F9FF.toInt(), colors.paperBg.toArgb())
        assertEquals(0xFF181C22.toInt(), colors.bodyText.toArgb())
        assertEquals(0xFF404752.toInt(), colors.controlInk.toArgb())
        assertEquals(0xFF0061A4.toInt(), colors.primary.toArgb())
        assertEquals(0xFFF1F3FC.toInt(), colors.bottomBarBg.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), colors.floatingControlBg.toArgb())
        assertEquals(0xFFEBEEF6.toInt(), colors.floatingControlBgAlt.toArgb())
        assertEquals(0xFFF1F3FC.toInt(), colors.quickButtonBg.toArgb())
        assertEquals(0xFFBFC7D4.toInt(), colors.controlBorder.toArgb())
        assertEquals(0x18707883, colors.handoffBoundary.toArgb())
    }

    @Test
    fun `reader control layer spacing matches normalized layout tokens`() {
        val spacing = ReaderSpacing.Default

        assertEquals(68.dp, spacing.bottomBarHeight)
        assertEquals(8.dp, spacing.bottomSafeGap)
        assertEquals(52.dp, spacing.floatingPageControlHeight)
        assertEquals(48.dp, spacing.quickCircleSize)
        assertEquals(20.dp, spacing.quickCircleGap)
    }

    @Test
    fun `reader night state is separate from day reader tokens`() {
        val day = ReaderColors.Day
        val night = ReaderColors.Night

        assertNotEquals(day.paperBg, night.paperBg)
        assertNotEquals(day.bodyText, night.bodyText)
        assertNotEquals(day.bottomBarBg, night.bottomBarBg)
        assertEquals(ReaderVisualMode.Night, ReaderVisualMode.valueOf("Night"))
    }

    @Test
    fun `reader theme token holder defaults to day colors without replacing material theme`() {
        val tokens = ReaderThemeTokens()

        assertEquals(ReaderColors.Day, tokens.colors)
        assertEquals(ReaderTypography.Default, tokens.typography)
        assertEquals(ReaderSpacing.Default, tokens.spacing)
        assertEquals(ReaderShapes.Default, tokens.shapes)
        assertEquals(ReaderElevation.Default, tokens.elevation)
    }

    @Test
    fun `reader main token files do not reintroduce old stitch colors`() {
        val themeDir = Paths.get("src/main/kotlin/com/reader/android/ui/theme")
        val content = Files.walk(themeDir).asSequence()
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".kt") }
            .joinToString(separator = "\n") { String(Files.readAllBytes(it)) }

        listOf(
            "FDF6EC",
            "EAE1DA",
            "F5ECE6",
            "EFE7E0",
            "8B5000"
        ).forEach { oldColor ->
            assertTrue("Old Stitch color $oldColor must not be a Reader token", oldColor !in content)
        }
    }
}
