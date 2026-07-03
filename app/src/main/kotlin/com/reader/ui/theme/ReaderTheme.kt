package com.reader.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Theme tokens for Reader for Android, mapped 1:1 (px→dp at mdpi) from the canonical demo
 * at `Reader UI/frontend-demo/tokens.css` and `styles/01-shell-layout.css`.
 *
 * Only the token *values* are inherited from the UI contract — no CSS, DOM, or selectors.
 */

// ── Color tokens (light) ────────────────────────────────────────────────────────
// Source: tokens.css:4-18, 00-foundation.css:4-13
private val Paper = Color(0xFFF8F4EC)            // --fd-paper-solid (body bg)
private val PaperBright = Color(0xFFFFF8F1)       // --reader-ds-color-paper-bright
private val Surface = Color(0xFFFFFCF8)           // --fd-surface (rgba 255,252,248,0.9 on paper)
private val SurfaceSoft = Color(0xCCFFF8F0)       // --reader-ds-color-surface-soft
private val Ink = Color(0xFF1F1B17)               // --fd-ink
private val ControlInk = Color(0xFF41484C)        // --reader-ds-color-control-ink
private val Muted = Color(0xFF756F69)             // --fd-muted
private val Border = Color(0xFFC1C7CD)            // --fd-border
private val Primary = Color(0xFF366179)           // --fd-primary
private val PrimaryDark = Color(0xFF274F66)       // --fd-primary-dark (active fill)
private val Accent = Color(0xFFF48B13)            // --fd-accent
private val Danger = Color(0xFFD62222)            // --fd-danger
private val Forest = Color(0xFF367A4D)            // --fd-forest
private val MetaBg = Color(0xFFF5ECE6)            // --reader-ds-color-meta-bg
private val NavBg = Color(0xEBFFFCF8)             // rgba(255,252,248,0.92)
private val NavInactive = Color(0xFF6B625A)       // tab item inactive label
private val Hairline = Color(0x6BB4A697)          // rgba(180,166,151,0.42)
private val ReaderInk = Color(0xFF2B241D)         // --reader-ink default
private val InfoLayer = Color(0xFF766C61)         // reader info layer text

// ── Color tokens (dark — minimal adaptation, demo only specifies light) ─────────
private val PaperDark = Color(0xFF1A1714)
private val SurfaceDark = Color(0xFF2A2420)
private val InkDark = Color(0xFFF0E8DF)
private val MutedDark = Color(0xFFB5ABA0)
private val BorderDark = Color(0xFF4A4239)
private val PrimaryDarkDark = Color(0xFF4A7B92)
private val PrimaryDarkTextDark = Color(0xFFD4E4ED)

/**
 * Extra colors not covered by Material3 `ColorScheme` (nav background, hairline, reader ink,
 * info layer). Provided via [LocalReaderExtraColors] so components can read them without
 * extra parameters.
 */
data class ReaderExtraColors(
    val paper: Color,
    val surfaceSoft: Color,
    val controlInk: Color,
    val muted: Color,
    val hairline: Color,
    val navBackground: Color,
    val navInactive: Color,
    val primaryDark: Color,
    val accent: Color,
    val metaBackground: Color,
    val readerInk: Color,
    val infoLayer: Color,
    val danger: Color,
    val forest: Color
)

val LocalReaderExtraColors = staticCompositionLocalOf {
    ReaderExtraColors(
        paper = Paper,
        surfaceSoft = SurfaceSoft,
        controlInk = ControlInk,
        muted = Muted,
        hairline = Hairline,
        navBackground = NavBg,
        navInactive = NavInactive,
        primaryDark = PrimaryDark,
        accent = Accent,
        metaBackground = MetaBg,
        readerInk = ReaderInk,
        infoLayer = InfoLayer,
        danger = Danger,
        forest = Forest
    )
}

private val LightExtra = ReaderExtraColors(
    paper = Paper,
    surfaceSoft = SurfaceSoft,
    controlInk = ControlInk,
    muted = Muted,
    hairline = Hairline,
    navBackground = NavBg,
    navInactive = NavInactive,
    primaryDark = PrimaryDark,
    accent = Accent,
    metaBackground = MetaBg,
    readerInk = ReaderInk,
    infoLayer = InfoLayer,
    danger = Danger,
    forest = Forest
)

private val DarkExtra = ReaderExtraColors(
    paper = PaperDark,
    surfaceSoft = SurfaceDark,
    controlInk = InkDark,
    muted = MutedDark,
    hairline = BorderDark,
    navBackground = Color(0xE6332C26),
    navInactive = MutedDark,
    primaryDark = PrimaryDarkTextDark,
    accent = Accent,
    metaBackground = SurfaceDark,
    readerInk = InkDark,
    infoLayer = MutedDark,
    danger = Danger,
    forest = Forest
)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = MetaBg,
    onSurfaceVariant = Muted,
    outline = Border,
    outlineVariant = Hairline,
    error = Danger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDarkTextDark,
    onPrimary = Color(0xFF0E2A38),
    primaryContainer = PrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = MutedDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = Danger,
    onError = Color.White
)

// ── Typography (px→sp 1:1) ──────────────────────────────────────────────────────
// Source: 01-shell-layout.css (per-element font-size / line-height / weight)
private val Serif = FontFamily.Serif       // maps to device serif (Noto Serif on modern Android)
private val Sans = FontFamily.Default      // maps to device sans (Roboto / Noto Sans)

object ReaderTextStyles {
    // Top bar title — .fd-top-bar h1: 29px serif 700
    val appBarTitle = TextStyle(fontFamily = Serif, fontSize = 29.sp, fontWeight = FontWeight(700))

    // Back bar title — .fd-back-bar h1: 24px
    val backBarTitle = TextStyle(fontFamily = Serif, fontSize = 24.sp, fontWeight = FontWeight(700))

    // Book card title — .fd-book-card strong: 15px serif, line-height 1.22, 2 lines
    val bookTitle = TextStyle(fontFamily = Serif, fontSize = 15.sp, lineHeight = (15 * 1.22f).sp, fontWeight = FontWeight.Normal)

    // Book card author — .fd-book-card span: 12px, line-height 1.25, 1 line
    val bookAuthor = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = (12 * 1.25f).sp)

    // Tab label — .fd-main-nav-item: 11px weight 800, line-height 18px
    val tabLabel = TextStyle(fontFamily = Sans, fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight(800))

    // Continue-reading label — .fd-continue-card h2: 13px weight 900, color primary
    val continueLabel = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(900))

    // Continue-reading title — .fd-continue-card strong: 20px serif, 2 lines
    val continueTitle = TextStyle(fontFamily = Serif, fontSize = 20.sp, lineHeight = (20 * 1.2f).sp)

    // Continue-reading author — .fd-continue-author: 14px, 2 lines
    val continueAuthor = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = (14 * 1.2f).sp)

    // Continue-reading action button — 13px weight 800
    val continueAction = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(800))

    // Empty-state heading — .fd-bookshelf-empty-state h2: 19px weight 900
    val emptyHeading = TextStyle(fontFamily = Sans, fontSize = 19.sp, lineHeight = (19 * 1.25f).sp, fontWeight = FontWeight(900))

    // Empty-state body — .fd-bookshelf-empty-state p: 13px line-height 1.55
    val emptyBody = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = (13 * 1.55f).sp)

    // Immersive chapter title — .fd-ir-reading-layer h1: 23px serif, line-height 1.25, center, margin-bottom 24
    val readerChapterTitle = TextStyle(fontFamily = Serif, fontSize = 23.sp, lineHeight = (23 * 1.25f).sp, textAlign = TextAlign.Center)

    // Immersive body — .fd-ir-reading-layer p: 18px serif, line-height 1.96
    val readerBody = TextStyle(fontFamily = Serif, fontSize = 18.sp, lineHeight = (18 * 1.96f).sp)

    // Info layer — .fd-ir-info-layer: 12px
    val infoLayer = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = (12 * 1.2f).sp)

    // Section head — .fd-section-head h2: 15px
    val sectionTitle = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(900))
}

// ── Shapes ──────────────────────────────────────────────────────────────────────
// Source: tokens.css:41-49, 00-foundation.css:18-25
object ReaderShapes {
    val xs = RoundedCornerShape(4.dp)        // --fd-radius-xs
    val sm = RoundedCornerShape(6.dp)        // --fd-radius-sm (continue cover)
    val md = RoundedCornerShape(8.dp)        // --fd-radius-md (cover, card, sheet)
    val lg = RoundedCornerShape(12.dp)       // --fd-radius-lg
    val xl = RoundedCornerShape(24.dp)       // --fd-radius-xl (tab nav, reader top)
    val pill = CircleShape                     // --fd-radius-pill (999px)
}

private val MaterialShapes = Shapes(
    extraSmall = ReaderShapes.xs,
    small = ReaderShapes.sm,
    medium = ReaderShapes.md,
    large = ReaderShapes.lg,
    extraLarge = ReaderShapes.xl
)

@Composable
fun ReaderTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) DarkExtra else LightExtra
    CompositionLocalProvider(LocalReaderExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colors,
            shapes = MaterialShapes,
            typography = androidx.compose.material3.Typography(),
            content = content
        )
    }
}

/** Consume the extra color tokens in a composable. */
@Composable
fun readerExtraColors(): ReaderExtraColors = LocalReaderExtraColors.current
