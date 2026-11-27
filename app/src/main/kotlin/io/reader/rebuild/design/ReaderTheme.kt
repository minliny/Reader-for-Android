package io.reader.rebuild.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
    val press = color(ReaderAppRole.TOK_INK).copy(alpha =
        (if (night) ReaderDesignMetrics.pressNightOpacity else ReaderDesignMetrics.pressDayOpacity).toFloat())

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
                onSurfaceVariant = secondaryText, outline = outline, outlineVariant = divider,
                error = danger, errorContainer = dangerTint, onErrorContainer = danger,
                scrim = scrim, surfaceTint = Color.Transparent,
            )
        }
    }
    CompositionLocalProvider(LocalReaderColors provides colors) {
        MaterialTheme(colorScheme = materialColors, typography = readerMaterialTypography(), content = content)
    }
}
