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
private val PaperStart = Color(0xFFFBF4E9)        // V7 paper gradient start (LightExtra paper #fbf4e9)
private val PaperEnd = Color(0xFFFBF4E9)          // V7 paper gradient end (LightExtra paper #fbf4e9)
private val ReaderPaper = Color(0xFFFFF8F4)       // --fd-paper (reader paper, tokens.css:4)
private val Surface = Color(0xFAFFFCF8)           // alpha 0.98 (day control.surface, render-runtime.js:2799)
private val SurfaceSoft = Color(0xB8FFFCF8)       // --reader-ds-color-surface-soft rgba(255,252,248,0.72)
private val Ink = Color(0xFF1F1B17)               // --fd-ink
private val ControlInk = Color(0xFF41484C)        // --reader-ds-color-control-ink
private val Muted = Color(0xFF756F69)             // --fd-muted
private val Border = Color(0xFFC1C7CD)            // --fd-border
private val Primary = Color(0xFF366179)           // --fd-primary
private val PrimaryDark = Color(0xFF274F66)       // --fd-primary-dark (active fill)
private val OnPrimary = Color(0xFFFFFAF4)         // --fd-on-primary (warm white)
private val Accent = Color(0xFFF48B13)            // --fd-accent
private val Danger = Color(0xFFD62222)            // --fd-danger
private val Forest = Color(0xFF367A4D)            // --fd-forest
private val MetaBg = Color(0xFFEEE8DF)            // --reader-ds-color-meta-bg (rgba(238,232,223))
private val BottomBarBg = Color(0xFFFBF2EB)       // --reader-ds-color-bottom-bar-bg
private val FloatingControlBg = Color(0xFFFBF2EB) // --reader-ds-color-floating-control-bg
private val FloatingControlBgAlt = Color(0xFFEAE1DA) // --reader-ds-color-floating-control-bg-alt
private val NavBg = Color(0xE6FFFCF8)             // --fd-surface alpha 0.9 (01-shell-layout.css:112)
private val NavInactive = Color(0xFF756F69)       // .fd-main-nav-item color = var(--fd-muted)
private val Hairline = Color(0x6BB4A697)          // rgba(180,166,151,0.42)
private val ReaderInk = Color(0xFF2B241D)         // --reader-ink default
private val InfoLayer = Color(0xFF6F655D)         // reader info layer text (#6f655d)

// ── Reader control / selection tokens (day) ────────────────────────────────────
// Source: render-runtime.js:2799-2830 (controlSurface / controlLine / selectionToolbar etc.)
private val ControlSurface = Color(0xFAFFFAF4)        // rgba(255,250,244,0.98)
private val ControlSurfaceSolid = Color(0xFAFFFCF8)   // rgba(255,252,248,0.98)
private val ControlPanel = Color(0x9EFFFCF8)          // rgba(255,252,248,0.62)
private val ControlPanelSoft = Color(0xA3EEE6DB)      // rgba(238,230,219,0.64)
private val ControlElevated = Color(0xBDFFFCF8)       // rgba(255,252,248,0.74)
private val ControlField = Color(0xC7FFF8EF)          // rgba(255,248,239,0.78)
private val ControlLine = Color(0x2E9B8466)           // rgba(155,132,102,0.18)
private val ControlLineStrong = Color(0x57B4A697)     // rgba(180,166,151,0.34)
private val ControlInkToken = Color(0xFF332C25)       // #332c25
private val ControlMuted = Color(0xFF5B5046)          // #5b5046
private val ControlIcon = Color(0xFF4D463F)           // #4d463f
private val ControlPrimary = Color(0xFF2F6373)        // #2f6373
private val ControlPrimaryText = Color(0xFFFFFAF4)    // #fffaf4
private val ControlAction = Color(0xFF2F6373)         // #2f6373
private val ControlActiveBg = Color(0x1A2F6373)       // rgba(47,99,115,0.1)
private val ControlActiveStrong = Color(0x292F6373)   // rgba(47,99,115,0.16)
private val ControlActiveSoft = Color(0x142F6373)     // rgba(47,99,115,0.08)
private val ControlDisabledBg = Color(0x8FEEE6DB)     // rgba(238,230,219,0.56)
private val ControlHandle = Color(0xFFB9AD9F)         // #b9ad9f
private val SelectionToolbar = Color(0xF2302A23)      // rgba(48,42,35,0.95)
private val SelectionToolbarLine = Color(0x3D4B3F32)  // rgba(75,63,50,0.24)
private val SelectionToolbarText = Color(0xFFFFFAF4)  // #fffaf4
private val SelectionFill = Color(0x1F393128)         // rgba(57,49,40,0.12)
private val SelectionLine = Color(0x42393128)          // rgba(57,49,40,0.26)
private val SelectionHandle = Color(0xFF4A4036)       // #4a4036 (demo CSS fallback)
private val SelectionHandleBorder = Color(0xFFFFFAF4) // #fffaf4

// ── Color tokens (dark — demo night theme, 00-foundation.css:101-116) ───────────
private val PaperDark = Color(0xFF1C1A18)         // --fd-paper-solid night
private val PaperBrightDark = Color(0xFF2C2824)   // --fd-paper-bright night
private val PaperStartDark = Color(0xFF302B26)    // V7 paper-night gradient start #302b26
private val PaperEndDark = Color(0xFF302B26)      // V7 paper-night gradient end #302b26
private val ReaderPaperDark = Color(0xFF24211E)   // --fd-paper night
private val SurfaceDark = Color(0xF52A2622)       // alpha 0.96 (night control.surface, render-runtime.js:2766)
private val SurfaceSoftDark = Color(0xB82A2622)   // surface-soft night (0.72 alpha of surface)
private val InkDark = Color(0xFFEADFCE)           // --fd-ink night
private val MutedDark = Color(0xFFBAAD9C)         // --fd-muted night
private val BorderDark = Color(0x33E2D1B9)        // --fd-border rgba(226,209,185,0.2) night
private val PrimaryDarkNight = Color(0xFFD2BD96)  // --fd-primary night (gold-brown)
private val PrimaryDarkDarkNight = Color(0xFF7A684F) // --fd-primary-dark night
private val OnPrimaryDark = Color(0xFFFFFAF4)     // --fd-on-primary night (same warm white)
private val AccentDark = Color(0xFFD69B5F)        // --fd-accent night
private val NavBgDark = Color(0xE62A2622)         // surface alpha 0.9 night
private val HairlineDark = Color(0x33E2D1B9)      // border rgba(226,209,185,0.2) night

// ── Reader control / selection tokens (night) ───────────────────────────────────
// Source: render-runtime.js:2766-2797
private val ControlSurfaceDark = Color(0xF526231F)        // rgba(38,35,31,0.96)
private val ControlSurfaceSolidDark = Color(0xFA221F1C)   // rgba(34,31,28,0.98)
private val ControlPanelDark = Color(0xD12E2A25)          // rgba(46,42,37,0.82)
private val ControlPanelSoftDark = Color(0xA8423B33)      // rgba(66,59,51,0.66)
private val ControlElevatedDark = Color(0xEB342F2A)       // rgba(52,47,42,0.92)
private val ControlFieldDark = Color(0xC73A342E)          // rgba(58,52,46,0.78)
private val ControlLineDark = Color(0x29E2D1B9)           // rgba(226,209,185,0.16)
private val ControlLineStrongDark = Color(0x47E2D1B9)     // rgba(226,209,185,0.28)
private val ControlInkTokenDark = Color(0xFFEADFCE)       // #eadfce
private val ControlMutedDark = Color(0xFFBAAD9C)          // #baad9c
private val ControlIconDark = Color(0xFFD4C5B2)           // #d4c5b2
private val ControlPrimaryDark = Color(0xFF7A684F)        // #7a684f
private val ControlPrimaryTextDark = Color(0xFFFFFAF4)    // #fffaf4
private val ControlActionDark = Color(0xFFD2BD96)         // #d2bd96
private val ControlActiveBgDark = Color(0x2ED2BD96)       // rgba(210,189,150,0.18)
private val ControlActiveStrongDark = Color(0x47D2BD96)   // rgba(210,189,150,0.28)
private val ControlActiveSoftDark = Color(0x1FD2BD96)     // rgba(210,189,150,0.12)
private val ControlDisabledBgDark = Color(0x1FE2D1B9)     // rgba(226,209,185,0.12)
private val ControlHandleDark = Color(0x6BD7CBBC)         // rgba(215,203,188,0.42)
private val SelectionToolbarDark = Color(0xF41C1916)      // rgba(28,25,22,0.96)
private val SelectionToolbarLineDark = Color(0x29EBDECC)  // rgba(235,222,204,0.16)
private val SelectionToolbarTextDark = Color(0xFFFFF7EC)  // #fff7ec
private val SelectionFillDark = Color(0x24EBDECC)         // rgba(235,222,204,0.14)
private val SelectionLineDark = Color(0x61EBDECC)         // rgba(235,222,204,0.38)
private val SelectionHandleDark = Color(0xFFD7C7B2)       // #d7c7b2
private val SelectionHandleBorderDark = Color(0xEB1C1916) // rgba(28,25,22,0.92)

/**
 * Extra colors not covered by Material3 `ColorScheme` (nav background, hairline, reader ink,
 * info layer). Provided via [LocalReaderExtraColors] so components can read them without
 * extra parameters.
 */
data class ReaderExtraColors(
    val paper: Color,
    val readerPaper: Color,
    val paperBright: Color,
    val surfaceSoft: Color,
    val controlInk: Color,
    val muted: Color,
    val hairline: Color,
    val border: Color,
    val navBackground: Color,
    val navInactive: Color,
    val primaryDark: Color,
    val accent: Color,
    val metaBackground: Color,
    val readerInk: Color,
    val infoLayer: Color,
    val danger: Color,
    val forest: Color,
    val bottomBarBackground: Color,
    val floatingControlBackground: Color,
    val floatingControlBackgroundAlt: Color,
    val onPrimary: Color,
    // ── reader-control / selection token族 (render-runtime.js:2766-2830) ──
    val controlSurface: Color,
    val controlSurfaceSolid: Color,
    val controlPanel: Color,
    val controlPanelSoft: Color,
    val controlElevated: Color,
    val controlField: Color,
    val controlLine: Color,
    val controlLineStrong: Color,
    val controlMuted: Color,
    val controlIcon: Color,
    val controlPrimary: Color,
    val controlPrimaryText: Color,
    val controlAction: Color,
    val controlActiveBg: Color,
    val controlActiveStrong: Color,
    val controlActiveSoft: Color,
    val controlDisabledBg: Color,
    val controlHandle: Color,
    val selectionToolbar: Color,
    val selectionToolbarLine: Color,
    val selectionToolbarText: Color,
    val selectionFill: Color,
    val selectionLine: Color,
    val selectionHandle: Color,
    val selectionHandleBorder: Color,
    // ── V7 demo --reader-ds-color-* semantic aliases ──
    val primary: Color,         // alias of primaryDark (demo --fd-primary; note demo primary is #366179)
    val mutedText: Color,       // alias of muted
    val divider: Color,         // alias of hairline
    val paperStart: Color,      // reader paper gradient start (injected from ReaderThemeRepository)
    val paperEnd: Color         // reader paper gradient end (injected from ReaderThemeRepository)
)

val LocalReaderExtraColors = staticCompositionLocalOf {
    ReaderExtraColors(
        paper = Paper,
        readerPaper = ReaderPaper,
        paperBright = PaperBright,
        surfaceSoft = SurfaceSoft,
        controlInk = ControlInk,
        muted = Muted,
        hairline = Hairline,
        border = Border,
        navBackground = NavBg,
        navInactive = NavInactive,
        primaryDark = PrimaryDark,
        accent = Accent,
        metaBackground = MetaBg,
        readerInk = ReaderInk,
        infoLayer = InfoLayer,
        danger = Danger,
        forest = Forest,
        bottomBarBackground = BottomBarBg,
        floatingControlBackground = FloatingControlBg,
        floatingControlBackgroundAlt = FloatingControlBgAlt,
        onPrimary = OnPrimary,
        controlSurface = ControlSurface,
        controlSurfaceSolid = ControlSurfaceSolid,
        controlPanel = ControlPanel,
        controlPanelSoft = ControlPanelSoft,
        controlElevated = ControlElevated,
        controlField = ControlField,
        controlLine = ControlLine,
        controlLineStrong = ControlLineStrong,
        controlMuted = ControlMuted,
        controlIcon = ControlIcon,
        controlPrimary = ControlPrimary,
        controlPrimaryText = ControlPrimaryText,
        controlAction = ControlAction,
        controlActiveBg = ControlActiveBg,
        controlActiveStrong = ControlActiveStrong,
        controlActiveSoft = ControlActiveSoft,
        controlDisabledBg = ControlDisabledBg,
        controlHandle = ControlHandle,
        selectionToolbar = SelectionToolbar,
        selectionToolbarLine = SelectionToolbarLine,
        selectionToolbarText = SelectionToolbarText,
        selectionFill = SelectionFill,
        selectionLine = SelectionLine,
        selectionHandle = SelectionHandle,
        selectionHandleBorder = SelectionHandleBorder,
        primary = PrimaryDark,
        mutedText = Muted,
        divider = Hairline,
        paperStart = PaperStart,
        paperEnd = PaperEnd
    )
}

private val LightExtra = ReaderExtraColors(
    paper = Paper,
    readerPaper = ReaderPaper,
    paperBright = PaperBright,
    surfaceSoft = SurfaceSoft,
    controlInk = ControlInk,
    muted = Muted,
    hairline = Hairline,
    border = Border,
    navBackground = NavBg,
    navInactive = NavInactive,
    primaryDark = PrimaryDark,
    accent = Accent,
    metaBackground = MetaBg,
    readerInk = ReaderInk,
    infoLayer = InfoLayer,
    danger = Danger,
    forest = Forest,
    bottomBarBackground = BottomBarBg,
    floatingControlBackground = FloatingControlBg,
    floatingControlBackgroundAlt = FloatingControlBgAlt,
    onPrimary = OnPrimary,
    controlSurface = ControlSurface,
    controlSurfaceSolid = ControlSurfaceSolid,
    controlPanel = ControlPanel,
    controlPanelSoft = ControlPanelSoft,
    controlElevated = ControlElevated,
    controlField = ControlField,
    controlLine = ControlLine,
    controlLineStrong = ControlLineStrong,
    controlMuted = ControlMuted,
    controlIcon = ControlIcon,
    controlPrimary = ControlPrimary,
    controlPrimaryText = ControlPrimaryText,
    controlAction = ControlAction,
    controlActiveBg = ControlActiveBg,
    controlActiveStrong = ControlActiveStrong,
    controlActiveSoft = ControlActiveSoft,
    controlDisabledBg = ControlDisabledBg,
    controlHandle = ControlHandle,
    selectionToolbar = SelectionToolbar,
    selectionToolbarLine = SelectionToolbarLine,
    selectionToolbarText = SelectionToolbarText,
    selectionFill = SelectionFill,
    selectionLine = SelectionLine,
    selectionHandle = SelectionHandle,
    selectionHandleBorder = SelectionHandleBorder,
    primary = PrimaryDark,
    mutedText = Muted,
    divider = Hairline,
    paperStart = PaperStart,
    paperEnd = PaperEnd
)

private val DarkExtra = ReaderExtraColors(
    paper = PaperDark,
    readerPaper = ReaderPaperDark,
    paperBright = PaperBrightDark,
    surfaceSoft = SurfaceSoftDark,
    controlInk = InkDark,
    muted = MutedDark,
    hairline = HairlineDark,
    border = BorderDark,
    navBackground = NavBgDark,
    navInactive = MutedDark,
    primaryDark = PrimaryDarkDarkNight,
    accent = AccentDark,
    metaBackground = SurfaceDark,
    readerInk = InkDark,
    infoLayer = MutedDark,
    danger = Danger,
    forest = Forest,
    bottomBarBackground = SurfaceDark,
    floatingControlBackground = SurfaceDark,
    floatingControlBackgroundAlt = SurfaceDark,
    onPrimary = OnPrimaryDark,
    controlSurface = ControlSurfaceDark,
    controlSurfaceSolid = ControlSurfaceSolidDark,
    controlPanel = ControlPanelDark,
    controlPanelSoft = ControlPanelSoftDark,
    controlElevated = ControlElevatedDark,
    controlField = ControlFieldDark,
    controlLine = ControlLineDark,
    controlLineStrong = ControlLineStrongDark,
    controlMuted = ControlMutedDark,
    controlIcon = ControlIconDark,
    controlPrimary = ControlPrimaryDark,
    controlPrimaryText = ControlPrimaryTextDark,
    controlAction = ControlActionDark,
    controlActiveBg = ControlActiveBgDark,
    controlActiveStrong = ControlActiveStrongDark,
    controlActiveSoft = ControlActiveSoftDark,
    controlDisabledBg = ControlDisabledBgDark,
    controlHandle = ControlHandleDark,
    selectionToolbar = SelectionToolbarDark,
    selectionToolbarLine = SelectionToolbarLineDark,
    selectionToolbarText = SelectionToolbarTextDark,
    selectionFill = SelectionFillDark,
    selectionLine = SelectionLineDark,
    selectionHandle = SelectionHandleDark,
    selectionHandleBorder = SelectionHandleBorderDark,
    primary = PrimaryDarkDarkNight,
    mutedText = MutedDark,
    divider = HairlineDark,
    paperStart = PaperStartDark,
    paperEnd = PaperEndDark
)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = OnPrimary,
    secondary = Accent,
    onSecondary = OnPrimary,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = MetaBg,
    onSurfaceVariant = Muted,
    outline = Border,
    outlineVariant = Hairline,
    error = Danger,
    onError = OnPrimary
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDarkNight,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryDarkDarkNight,
    onPrimaryContainer = OnPrimaryDark,
    secondary = AccentDark,
    onSecondary = OnPrimaryDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = MutedDark,
    outline = BorderDark,
    outlineVariant = HairlineDark,
    error = Danger,
    onError = OnPrimaryDark
)

// ── Typography tokens (px→sp 1:1) ──────────────────────────────────────────────
// Source: tokens.css:50-56 (font-size), 04-settings-source.css (font-weight),
// 01-shell-layout.css (per-element font-size / line-height / weight).

/**
 * Typography design tokens — font sizes, weights, line heights, and font families.
 *
 * Font family fallback: `res/font/` is not bundled with the app. [FontFamily.Default] and
 * [FontFamily.Serif] resolve through Android's system font resolver, which automatically falls
 * back to Roboto + Noto Sans CJK (sans) and Noto Serif CJK (serif) on modern Android (API 21+).
 * To bundle explicit fonts later, add `roboto.ttf` / `noto_sans_sc.ttf` / `noto_serif_sc.ttf`
 * under `app/src/main/res/font/` and replace the values below with
 * `FontFamily(Font(R.font.roboto), Font(R.font.noto_sans_sc))` etc.
 */
object ReaderTypography {
    // ── Font families ──
    val Sans = FontFamily.Default       // demo --reader-ds-font-sans (Roboto + Noto Sans CJK fallback)
    val Serif = FontFamily.Serif        // demo --reader-ds-font-serif (Noto Serif CJK fallback)

    // ── Font size tokens (tokens.css:50-56) ──
    val appTitleSize = 20.sp            // --reader-ds-type-app-title-size
    val pageTitleSize = 20.sp           // --reader-ds-type-page-title-size
    val sectionTitleSize = 15.sp        // --reader-ds-type-section-title-size
    val bookTitleSize = 14.sp           // --reader-ds-type-book-title-size
    val bookMetaSize = 12.sp            // --reader-ds-type-book-meta-size
    val readerBodySize = 18.sp          // --reader-ds-type-reader-body-size
    val readerControlLabelSize = 12.sp  // --reader-ds-type-reader-control-label-size

    // ── Font weight tokens (04-settings-source.css) ──
    val weightMedium = FontWeight(500)     // --reader-ds-weight-medium
    val weightBold = FontWeight(700)       // --reader-ds-weight-bold
    val weightExtraBold = FontWeight(800)  // --reader-ds-weight-extra-bold
    val weightBlack = FontWeight(900)      // --reader-ds-weight-black

    // ── Line height tokens ──
    val lineHeightTight = 1.2f        // --reader-ds-line-height-tight
    val lineHeightNormal = 1.25f      // --reader-ds-line-height-normal
    val lineHeightRelaxed = 1.55f     // --reader-ds-line-height-relaxed
    val lineHeightReader = 1.96f      // --reader-ds-line-height-reader
}

private val Sans = ReaderTypography.Sans
private val Serif = ReaderTypography.Serif

object ReaderTextStyles {
    // Top bar title — .fd-top-bar h1: 29px serif 700
    val appBarTitle = TextStyle(fontFamily = Serif, fontSize = 29.sp, fontWeight = FontWeight(700))

    // Back bar title — .fd-back-bar h1: 29px serif 700 (same as .fd-top-bar h1)
    val backBarTitle = TextStyle(fontFamily = Serif, fontSize = 29.sp, fontWeight = FontWeight(700))

    // Book card title — .fd-book-card strong: 15px serif weight 700, line-height 1.22, 2 lines
    val bookTitle = TextStyle(fontFamily = Serif, fontSize = 15.sp, lineHeight = (15 * 1.22f).sp, fontWeight = FontWeight(700))

    // Book card author — .fd-book-card span: 12px, line-height 1.25, 1 line
    val bookAuthor = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = (12 * 1.25f).sp)

    // Tab label — .fd-main-nav-item: 11px weight 800, line-height 18px
    val tabLabel = TextStyle(fontFamily = Sans, fontSize = 11.sp, lineHeight = 18.sp, fontWeight = FontWeight(800))

    // Continue-reading label — .fd-continue-card h2: 13px weight 900, color primary
    val continueLabel = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight(900))

    // Continue-reading title — .fd-continue-card strong: 20px serif weight 700, 2 lines
    val continueTitle = TextStyle(fontFamily = Serif, fontSize = 20.sp, lineHeight = (20 * 1.2f).sp, fontWeight = FontWeight(700))

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

    // Section head — .fd-section-head h2: 15px weight 700 (h2 default)
    val sectionTitle = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight(700))

    // Reader top title — .fd-reader-top strong: 16px
    val readerTopTitle = TextStyle(fontFamily = Sans, fontSize = 16.sp, fontWeight = FontWeight.Normal)
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
    val circle = CircleShape                   // --fd-radius-circle (50%)
    val device = RoundedCornerShape(34.dp)     // --fd-radius-device (34px)
}

/**
 * Shadow elevation tokens — demo `--reader-control-shadow` / `--fd-soft-shadow` / `--fd-shadow`
 * resolve to `0 8px 26px rgba(89,70,50,0.1)` (soft) and `0 14px 30px rgba(82,66,48,0.16)` (elevated).
 * Compose `Modifier.shadow(elevation)` cannot replicate arbitrary offsets/blur, so we map to Dp
 * elevations that approximate the demo's visual weight:
 *  - controlShadow ≈ y-offset 8px → 8.dp (reader sheet, top overlay, module nav)
 *  - softShadow ≈ y-offset 8px → 8.dp (tab nav, main nav)
 *  - cardShadow ≈ y-offset 14px → 14.dp (filter popover)
 *  - selectionToolbarShadow ≈ y-offset 10px → 10.dp (selection toolbar)
 *  - sessionCapsuleShadow ≈ y-offset 4px → 4.dp (session capsule)
 *  - handleShadow ≈ y-offset 3px → 3.dp (selection handle)
 */
object ReaderElevations {
    val controlShadow = 8.dp
    val softShadow = 8.dp
    val cardShadow = 14.dp
    val selectionToolbarShadow = 10.dp
    val sessionCapsuleShadow = 4.dp
    val handleShadow = 3.dp
    val bookCoverShadow = 0.dp     // .fd-book-cover-frame has no shadow
}

// ── Spacing tokens ─────────────────────────────────────────────────────────────
// Source: tokens.css:19-28, 40
object ReaderSpacing {
    val xs = 8.dp                   // --reader-ds-space-xs
    val sm = 12.dp                  // --reader-ds-space-sm
    val md = 16.dp                  // --reader-ds-space-md
    val lg = 24.dp                  // --reader-ds-space-lg
    val xl = 48.dp                  // --reader-ds-space-xl
    val screenPadding = 16.dp       // --reader-ds-space-screen-padding
    val cardPadding = 14.dp         // --reader-ds-space-card-padding
    val keyboardGap = 12.dp         // --reader-ds-space-keyboard-gap
    val safeAreaTop = 24.dp         // --reader-ds-safe-area-top
    val safeAreaBottom = 14.dp      // --reader-ds-safe-area-bottom
    val safeAreaHorizontal = 16.dp  // --reader-ds-safe-area-horizontal
}

// ── Sizing tokens ──────────────────────────────────────────────────────────────
// Source: tokens.css:29-49
object ReaderSizes {
    // Phone frame
    val phoneWidth = 390.dp         // --reader-ds-size-phone-width
    val phoneHeight = 844.dp        // --reader-ds-size-phone-height

    // Bar heights
    val topBarHeight = 58.dp        // --reader-ds-size-top-bar-height
    val bottomBarHeight = 68.dp     // --reader-ds-size-bottom-bar-height
    val mainNavHeight = 68.dp       // --reader-ds-size-main-nav-height

    // Reader
    val readerBottomSheetMinHeight = 284.dp  // --reader-ds-size-reader-bottom-sheet-min-height
    val readerModuleNavHeight = 82.dp        // --reader-ds-size-reader-module-nav-height

    // Keyboard
    val keyboardHeight = 320.dp     // --reader-ds-size-keyboard-height

    // Corner radii
    val radiusSmall = 4.dp          // --reader-ds-radius-small
    val radiusMedium = 6.dp         // --reader-ds-radius-medium
    val radiusLarge = 8.dp          // --reader-ds-radius-large
    val radiusCard = 4.dp           // --reader-ds-radius-card
    val radiusChip = 2.dp           // --reader-ds-radius-chip
    val radiusBottomSheet = 8.dp    // --reader-ds-radius-bottom-sheet
    val radiusControl = 999.dp      // --reader-ds-radius-control (pill)
    val radiusPanel = 8.dp          // --reader-ds-radius-panel

    // z-index (demo z values → Int)
    val zContent = 0                // --reader-ds-z-content
    val zOverlay = 10               // --reader-ds-z-overlay
    val zMainNav = 20               // --reader-ds-z-main-nav
    val zBottomSheet = 30           // --reader-ds-z-bottom-sheet
    val zReaderModuleNav = 40       // --reader-ds-z-reader-module-nav
    val zDialog = 60                // --reader-ds-z-dialog
    val zKeyboard = 70              // --reader-ds-z-keyboard
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
