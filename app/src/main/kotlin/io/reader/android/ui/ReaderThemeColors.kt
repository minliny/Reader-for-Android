package io.reader.android.ui

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Generated from Reader-UI@95366cd93775ea0ad3ecbc0f60e8882717f93169/theme/registry.json.
// Source SHA256 1a3ea68f162a2e440b60fa0dfe0001bc9c3051b6adba7700a60fdc73024a798f. Do not tune individual colors here.
internal val ReaderDayColors = lightColorScheme(
    primary = Color(0xFF2D4A3E), // TOK_GREEN
    onPrimary = Color(0xFFFFFAF4), // TOK_ON_PRIMARY
    secondary = Color(0xFF1F3528), // TOK_PRIMARY_DARK
    background = Color(0xFFF8F4EC), // TOK_PAPER
    onBackground = Color(0xFF1F1B17), // TOK_INK
    surface = Color(0xFFFFFCF8), // TOK_SURFACE_SOLID
    onSurface = Color(0xFF1F1B17), // TOK_INK
    onSurfaceVariant = Color(0xFF756F69), // TOK_MUTED
    surfaceVariant = Color(0xD1FFF8EF), // TOK_FIELD_SURFACE
    outline = Color(0xFFC1C7CD), // TOK_BORDER
    error = Color(0xFFD62222), // TOK_DANGER
)

internal val ReaderNightColors = darkColorScheme(
    primary = Color(0xFFD2BD96), // TOK_GREEN
    onPrimary = Color(0xFFFFFAF4), // TOK_ON_PRIMARY
    secondary = Color(0xFF7A684F), // TOK_PRIMARY_DARK
    background = Color(0xFF1C1E1A), // TOK_PAPER
    onBackground = Color(0xFFEAE7DE), // TOK_INK
    surface = Color(0xFF2A2622), // TOK_SURFACE_SOLID
    onSurface = Color(0xFFEAE7DE), // TOK_INK
    onSurfaceVariant = Color(0xFFABA99F), // TOK_MUTED
    surfaceVariant = Color(0xD12C2824), // TOK_FIELD_SURFACE
    outline = Color(0xFF41483B), // TOK_BORDER
    error = Color(0xFFD62222), // TOK_DANGER
)

fun ReaderPaper.background(): Color = when (this) {
    ReaderPaper.Paper -> Color(0xFFF7F3EA)
    ReaderPaper.Warm -> Color(0xFFF2E8D3)
    ReaderPaper.Night -> Color(0xFF2B2823)
}

fun ReaderPaper.ink(): Color = when (this) {
    ReaderPaper.Paper -> Color(0xFF2B241D)
    ReaderPaper.Warm -> Color(0xFF2C241D)
    ReaderPaper.Night -> Color(0xFFE9DECE)
}
