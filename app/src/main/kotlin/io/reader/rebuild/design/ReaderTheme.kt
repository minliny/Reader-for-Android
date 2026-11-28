package io.reader.rebuild.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.RippleDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.reader.core.theme.ReaderAppRole
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderThemeTokens

internal class ReaderColors(val night: Boolean) {
    val background = color(ReaderAppRole.APP_WINDOW_BACKGROUND)
    val surface = color(ReaderAppRole.TOK_CARD_BG)
    val elevated = color(ReaderAppRole.TOK_SURFACE_ELEVATED)
    val field = color(ReaderAppRole.TOK_SURFACE_FIELD)
    val panel = color(ReaderAppRole.TOK_SURFACE_PANEL)
    val primaryText = color(ReaderAppRole.APP_WINDOW_FOREGROUND)
    val secondaryText = color(ReaderAppRole.TOK_MUTED)
    val mutedText = color(ReaderAppRole.TOK_MESSAGE_MUTED)
    val accent = color(ReaderAppRole.TOK_PRIMARY_TEXT)
    val accentFill = color(ReaderAppRole.TOK_PRIMARY_DARK)
    val onAccent = color(ReaderAppRole.TOK_ON_PRIMARY)
    val accentTint = color(ReaderAppRole.TOK_PRIMARY_SOFT)
    val divider = color(ReaderAppRole.TOK_ROW_DIVIDER)
    val outline = color(ReaderAppRole.TOK_BORDER)
    val progressTrack = color(ReaderAppRole.TOK_LINE)
    val mutedFill = color(ReaderAppRole.TOK_META_BG)
    val danger = color(ReaderAppRole.TOK_DANGER)
    val dangerTint = color(ReaderAppRole.APP_ACTION_DESTRUCTIVE_SOFT)
    val success = color(ReaderAppRole.TOK_GREEN)
    val warning = color(ReaderAppRole.TOK_ACCENT)
    val shadow = color(ReaderAppRole.APP_SHADOW_DIALOG)
    val scrim = color(ReaderAppRole.TOK_SS_DRAWER_SCRIM)
    val coverPlaceholder = color(ReaderAppRole.TOK_PRIMARY_BG)
    val messageSurface = color(ReaderAppRole.TOK_MESSAGE_SURFACE)
    val messageText = color(ReaderAppRole.TOK_MESSAGE_INK)
    val messageBorder = color(ReaderAppRole.TOK_MESSAGE_BORDER)
    val noticeSurface = color(ReaderAppRole.TOK_MESSAGE_NOTICE)
    val noticeText = color(ReaderAppRole.TOK_MESSAGE_NOTICE_INK)
    val messageSoftSurface = color(ReaderAppRole.TOK_MESSAGE_SOFT)
    val messageSoftText = color(ReaderAppRole.TOK_MESSAGE_SOFT_INK)
    val press = color(ReaderAppRole.TOK_INK).copy(alpha =
        (if (night) ReaderDesignMetrics.pressNightOpacity else ReaderDesignMetrics.pressDayOpacity).toFloat())

    // Recessed segmented rail tokens aligned with Reader-UI/theme/registry.json (app.segment.*).
    val segmentTrack = Color(if (night) 0xFF0B0D0B.toInt() else 0xFFE2D9C9.toInt())
    val segmentTrackTopShade = Color(if (night) 0xB8000000.toInt() else 0x2E3E3223.toInt())
    val segmentTrackBorder = Color(if (night) 0x73000000.toInt() else 0x245C4F3F.toInt())
    val segmentTrackBevel = Color(if (night) 0x17ECE8E0.toInt() else 0xE6FFFDF9.toInt())
    val segmentThumb = Color(if (night) 0xFF282E29.toInt() else 0xFFFFFDF9.toInt())
    val segmentThumbBorder = Color(if (night) 0x21ECE8E0.toInt() else 0xF2FFFFFF.toInt())
    val segmentThumbShadow = Color(if (night) 0x85000000.toInt() else 0x292D241A.toInt())

    internal fun iconTint(wireRole: String): Color =
        color(ReaderAppRole.entries.first { it.wireRole == wireRole })

    private fun color(role: ReaderAppRole): Color = Color(ReaderThemeTokens.argb(role, night).toInt())
}

internal object ReaderReadingColors {
    val paper = css(ReaderAppRole.TOK_PAPER)
    val ink = css(ReaderAppRole.TOK_INK)

    private fun css(role: ReaderAppRole): String =
        "#" + (ReaderThemeTokens.argb(role, night = false) and 0xffffff).toString(16).padStart(6, '0')
}

internal val LocalReaderColors = staticCompositionLocalOf { ReaderColors(night = false) }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReaderTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = remember(night) { ReaderColors(night) }
    val materialColors = remember(colors) {
        with(colors) {
            val base = if (night) darkColorScheme() else lightColorScheme()
            base.copy(
                primary = accentFill, onPrimary = onAccent,
                primaryContainer = accentTint, onPrimaryContainer = accent,
                secondary = accent, onSecondary = onAccent,
                secondaryContainer = mutedFill, onSecondaryContainer = primaryText,
                background = background, onBackground = primaryText,
                surface = surface, onSurface = primaryText, surfaceVariant = panel,
                surfaceBright = surface, surfaceDim = background,
                surfaceContainerLowest = background, surfaceContainerLow = surface,
                surfaceContainer = elevated, surfaceContainerHigh = panel, surfaceContainerHighest = field,
                onSurfaceVariant = secondaryText, outline = outline, outlineVariant = divider,
                error = danger, errorContainer = dangerTint, onErrorContainer = danger,
                scrim = scrim, surfaceTint = Color.Transparent,
            )
        }
    }
    val neutralRipple = remember(colors) {
        val normal = RippleDefaults.RippleAlpha
        RippleConfiguration(color = colors.press.copy(alpha = 1f), rippleAlpha = RippleAlpha(
            draggedAlpha = normal.draggedAlpha, focusedAlpha = normal.focusedAlpha, hoveredAlpha = normal.hoveredAlpha,
            pressedAlpha = colors.press.alpha,
        ))
    }
    CompositionLocalProvider(LocalReaderColors provides colors) {
        MaterialTheme(colorScheme = materialColors, typography = readerMaterialTypography()) {
            CompositionLocalProvider(LocalRippleConfiguration provides neutralRipple, content = content)
        }
    }
}
