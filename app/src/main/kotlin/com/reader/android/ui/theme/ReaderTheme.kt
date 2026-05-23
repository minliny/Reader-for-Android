package com.reader.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

data class ReaderThemeTokens(
    val colors: ReaderColors = ReaderColors.Day,
    val typography: ReaderTypography = ReaderTypography.Default,
    val spacing: ReaderSpacing = ReaderSpacing.Default,
    val shapes: ReaderShapes = ReaderShapes.Default,
    val elevation: ReaderElevation = ReaderElevation.Default
)

val LocalReaderTheme = staticCompositionLocalOf { ReaderThemeTokens() }

object ReaderTheme {
    val colors: ReaderColors
        @Composable
        @ReadOnlyComposable
        get() = LocalReaderTheme.current.colors

    val typography: ReaderTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalReaderTheme.current.typography

    val spacing: ReaderSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalReaderTheme.current.spacing

    val shapes: ReaderShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalReaderTheme.current.shapes

    val elevation: ReaderElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalReaderTheme.current.elevation
}

@Composable
fun ReaderTheme(
    visualMode: ReaderVisualMode = ReaderVisualMode.Day,
    content: @Composable () -> Unit
) {
    val colors = when (visualMode) {
        ReaderVisualMode.Day -> ReaderColors.Day
        ReaderVisualMode.Night -> ReaderColors.Night
    }
    CompositionLocalProvider(
        LocalReaderTheme provides ReaderThemeTokens(colors = colors),
        content = content
    )
}
