package com.reader.ui.tokens

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.reader.ui.contract.Motion
import io.reader.ui.contract.Token
import io.reader.ui.contract.TokenCategory
import io.reader.ui.contract.TokenPlatforms
import io.reader.ui.contract.TokenRegistry

/**
 * Android TokenAdapter boundary for the Reader UI contract.
 *
 * This is the platform-owned mapping layer for semantic Reader UI tokens into Compose
 * primitives. Raw values are allowed here because this file is the adapter; contract-owned
 * components should depend on these semantic accessors instead of declaring ad hoc colors,
 * spacing, radii, sizes, or durations in screen code.
 *
 * Contract token names use the `--fd-ds-*` prefix (matching `tokens.css` and the generated
 * `TokenRegistry`). Dark-mode Color values for the 18 base color tokens are sourced from
 * `00-foundation.css` night overrides (which differ from the contract's 5 `*-night` tokens).
 */
object ReaderTokenAdapter {
    fun color(token: ReaderColorToken, mode: ReaderTokenMode = ReaderTokenMode.LIGHT): Color =
        when (mode) {
            ReaderTokenMode.LIGHT -> token.light
            ReaderTokenMode.DARK -> token.dark
        }

    fun spacing(token: ReaderSpacingToken): Dp = token.value

    fun size(token: ReaderSizeToken): Dp = token.value

    fun radius(token: ReaderRadiusToken): Dp = token.value

    fun type(token: ReaderTypeToken): TextUnit = token.value

    fun zIndex(token: ReaderZIndexToken): Float = token.value.toFloat()

    fun easing(token: ReaderEasingToken): Easing = token.easing

    fun durationMillis(token: ReaderDurationToken, reducedMotion: Boolean = false): Int =
        durationMillis(token.toGeneratedToken(), reducedMotion)

    fun durationMillis(motion: Motion, reducedMotion: Boolean = false): Int {
        val tokenName = motion.tokens?.durationToken
        val generatedToken = tokenName
            ?.let {
                val registryName = it.toRegistryTokenName()
                TokenRegistry.token(registryName) ?: Token(
                    name = registryName,
                    category = TokenCategory.MotionDuration,
                    value = "${motion.durationMs}ms"
                )
            }
            ?: Token(
                name = "${motion.id.name}.duration",
                category = TokenCategory.MotionDuration,
                value = "${motion.durationMs}ms"
            )
        return durationMillis(generatedToken, reducedMotion)
    }

    fun durationMillis(token: Token, reducedMotion: Boolean = false): Int {
        require(token.category == TokenCategory.MotionDuration) {
            "Expected motion-duration token, got ${token.category}: ${token.name}"
        }
        if (reducedMotion) return 0
        val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: token
        return registryToken.value.parseDurationMillis()
    }

    /**
     * Resolve a contract [TokenCategory.Font] token to a Compose [FontFamily].
     *
     * Mapping follows `platforms.kotlin` in the contract:
     *  - `--fd-ds-font-sans` / `--fd-ds-font-serif` / `--fd-ds-font-mono` → FontFamily.Default/Serif/Monospace
     *  - `--fd-ds-font-kai` / `--fd-ds-font-fangsong` → FontFamily.Serif (no native Compose equivalent,
     *    Android system serif fallback covers CJK glyphs).
     */
    fun font(token: Token): FontFamily? = when (token.category) {
        TokenCategory.Font -> {
            val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: return null
            when (registryToken.name) {
                "--fd-ds-font-sans" -> FontFamily.Default
                "--fd-ds-font-serif" -> FontFamily.Serif
                "--fd-ds-font-kai" -> FontFamily.Serif
                "--fd-ds-font-fangsong" -> FontFamily.Serif
                "--fd-ds-font-mono" -> FontFamily.Monospace
                else -> null
            }
        }
        else -> null
    }

    /**
     * Resolve a contract [TokenCategory.Shadow] token to its raw CSS box-shadow expression.
     * Returns the contract value verbatim (e.g. `"0 8px 26px rgba(89,70,50,0.1)"`); callers
     * that need a Compose [androidx.compose.ui.graphics.Shadow] should parse it themselves.
     */
    fun shadow(token: Token): String? = when (token.category) {
        TokenCategory.Shadow -> {
            val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: return null
            registryToken.value
        }
        else -> null
    }

    /**
     * Resolve a contract [TokenCategory.Elevation] token (e.g. `--fd-ds-elevation-card` = `2px`)
     * to a Compose [Dp] elevation. Mirrors the [size] parsing logic.
     */
    fun elevation(token: Token): Dp? = when (token.category) {
        TokenCategory.Elevation -> {
            val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: return null
            registryToken.value.parseDp()
        }
        else -> null
    }

    /**
     * Resolve a contract [TokenCategory.TextConstraint] token to its integer constraint.
     * Supports line-count tokens (`"1"`, `"2"`) and the reader line-length token (`"31ch"`).
     */
    fun textConstraint(token: Token): Int? = when (token.category) {
        TokenCategory.TextConstraint -> {
            val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: return null
            registryToken.value.parseIntOrNull()
        }
        else -> null
    }

    /**
     * Resolve a contract [TokenCategory.Icon] token to an Android drawable resource id.
     *
     * Uses [Context.getResources].getIdentifier to look up `R.drawable.reader_ic_<name>`
     * dynamically — the 94 contract icons are too many to enumerate by hand and the contract's
     * `platforms.kotlin` field already encodes the resource-name convention.
     */
    fun icon(token: Token, context: Context): Int? = when (token.category) {
        TokenCategory.Icon -> {
            val registryToken = TokenRegistry.token(token.name.toRegistryTokenName()) ?: return null
            // P3.10: handle both kebab-case (reader-module-settings) and camelCase
            // (eyeOff) token values so they map to the existing snake_case
            // drawable resources (reader_ic_eye_off.xml etc.).
            val rawName = registryToken.value
            val resourceName = "reader_ic_" + rawName
                .replace("-", "_")
                .replace(Regex("(?<=[a-z])[A-Z]"), "_$0")
                .lowercase()
            val id = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            if (id != 0) id else null
        }
        else -> null
    }

    fun supports(token: Token): Boolean = when (token.category) {
        TokenCategory.Color,
        TokenCategory.Font,
        TokenCategory.Spacing,
        TokenCategory.Size,
        TokenCategory.Radius,
        TokenCategory.Type,
        TokenCategory.Shadow,
        TokenCategory.Elevation,
        TokenCategory.ZIndex,
        TokenCategory.TextConstraint,
        TokenCategory.MotionDuration,
        TokenCategory.MotionEasing,
        TokenCategory.Icon -> TokenRegistry.token(token.name.toRegistryTokenName())?.category == token.category
        else -> false
    }
}

enum class ReaderTokenMode {
    LIGHT,
    DARK
}

// ── Color tokens (23: 18 base + 5 night) ──────────────────────────────────────
// Source: Token.kt --fd-ds-color-* (contract) + 00-foundation.css night overrides
// Dark values for base tokens come from 00-foundation.css [data-app-theme-scheme="night"]
// Night-specific tokens (paper-night etc.) use contract values for both modes.
enum class ReaderColorToken(
    val contractName: String,
    internal val light: Color,
    internal val dark: Color
) {
    PAPER("--fd-ds-color-paper", Color(0xFFFFF8F4), Color(0xFF24211E)),
    PAPER_BRIGHT("--fd-ds-color-paper-bright", Color(0xFFFFF8F1), Color(0xFF2C2824)),
    SURFACE("--fd-ds-color-surface", Color(0xE0FFFFFF), Color(0xE62A2622)),
    SURFACE_SOFT("--fd-ds-color-surface-soft", Color(0xB8FFFCF8), Color(0xB82A2622)),
    INK("--fd-ds-color-ink", Color(0xFF1F1B17), Color(0xFFEADFCE)),
    CONTROL_INK("--fd-ds-color-control-ink", Color(0xFF41484C), Color(0xFFEADFCE)),
    MUTED("--fd-ds-color-muted", Color(0xFF756F69), Color(0xFFBAAD9C)),
    BORDER("--fd-ds-color-border", Color(0xFFC1C7CD), Color(0x33E2D1B9)),
    PRIMARY("--fd-ds-color-primary", Color(0xFF2D4A3E), Color(0xFFD2BD96)),
    PRIMARY_DARK("--fd-ds-color-primary-dark", Color(0xFF1F3528), Color(0xFF7A684F)),
    ACCENT("--fd-ds-color-accent", Color(0xFFF48B13), Color(0xFFD69B5F)),
    BOTTOM_BAR_BG("--fd-ds-color-bottom-bar-bg", Color(0xFFFBF2EB), Color(0xF52A2622)),
    FLOATING_CONTROL_BG("--fd-ds-color-floating-control-bg", Color(0xFFFBF2EB), Color(0xF52A2622)),
    FLOATING_CONTROL_BG_ALT("--fd-ds-color-floating-control-bg-alt", Color(0xFFEAE1DA), Color(0xF52A2622)),
    META_BG("--fd-ds-color-meta-bg", Color(0xFFF5ECE6), Color(0xF52A2622)),
    RSS_UNREAD("--fd-ds-color-rss-unread", Color(0xFF2F6F93), Color(0xFF8FB6CA)),
    STATUS_GOOD("--fd-ds-color-status-good", Color(0xFF338144), Color(0xFF5BAE6E)),
    STATUS_WARN("--fd-ds-color-status-warn", Color(0xFFD7473E), Color(0xFFE56B62)),
    // Night-specific tokens (contract values, same for both modes)
    PAPER_NIGHT("--fd-ds-color-paper-night", Color(0xFF181F22), Color(0xFF181F22)),
    INK_NIGHT("--fd-ds-color-ink-night", Color(0xFFD8CCC4), Color(0xFFD8CCC4)),
    CONTROL_INK_NIGHT("--fd-ds-color-control-ink-night", Color(0xFFD7E1E5), Color(0xFFD7E1E5)),
    PRIMARY_NIGHT("--fd-ds-color-primary-night", Color(0xFF8FB6CA), Color(0xFF8FB6CA)),
    FLOATING_CONTROL_BG_ALT_NIGHT("--fd-ds-color-floating-control-bg-alt-night", Color(0xFF2B3B43), Color(0xFF2B3B43))
}

// ── Spacing tokens (11) ───────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-space-* + --fd-ds-safe-area-*
enum class ReaderSpacingToken(val contractName: String, internal val value: Dp) {
    XS("--fd-ds-space-xs", 8.dp),
    SM("--fd-ds-space-sm", 12.dp),
    MD("--fd-ds-space-md", 16.dp),
    LG("--fd-ds-space-lg", 24.dp),
    XL("--fd-ds-space-xl", 48.dp),
    SCREEN_PADDING("--fd-ds-space-screen-padding", 16.dp),
    CARD_PADDING("--fd-ds-space-card-padding", 14.dp),
    SAFE_AREA_TOP("--fd-ds-safe-area-top", 24.dp),
    SAFE_AREA_BOTTOM("--fd-ds-safe-area-bottom", 14.dp),
    SAFE_AREA_HORIZONTAL("--fd-ds-safe-area-horizontal", 16.dp),
    KEYBOARD_GAP("--fd-ds-space-keyboard-gap", 12.dp)
}

// ── Size tokens (11) ──────────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-size-*
enum class ReaderSizeToken(val contractName: String, internal val value: Dp) {
    PHONE_WIDTH("--fd-ds-size-phone-width", 390.dp),
    PHONE_HEIGHT("--fd-ds-size-phone-height", 844.dp),
    STACK_PHONE_HEIGHT("--fd-ds-size-stack-phone-height", 878.dp),
    FLOW_WIDTH("--fd-ds-size-flow-width", 1284.dp),
    FLOW_MIN_HEIGHT("--fd-ds-size-flow-min-height", 520.dp),
    TOP_BAR_HEIGHT("--fd-ds-size-top-bar-height", 58.dp),
    BOTTOM_BAR_HEIGHT("--fd-ds-size-bottom-bar-height", 68.dp),
    MAIN_NAV_HEIGHT("--fd-ds-size-main-nav-height", 68.dp),
    READER_BOTTOM_SHEET_MIN_HEIGHT("--fd-ds-size-reader-bottom-sheet-min-height", 284.dp),
    READER_MODULE_NAV_HEIGHT("--fd-ds-size-reader-module-nav-height", 82.dp),
    KEYBOARD_HEIGHT("--fd-ds-size-keyboard-height", 320.dp)
}

// ── Radius tokens (7) ─────────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-radius-*
enum class ReaderRadiusToken(val contractName: String, internal val value: Dp) {
    SMALL("--fd-ds-radius-small", 4.dp),
    MEDIUM("--fd-ds-radius-medium", 6.dp),
    LARGE("--fd-ds-radius-large", 8.dp),
    CARD("--fd-ds-radius-card", 4.dp),
    CHIP("--fd-ds-radius-chip", 2.dp),
    BOTTOM_SHEET("--fd-ds-radius-bottom-sheet", 8.dp),
    CONTROL("--fd-ds-radius-control", 999.dp)
}

// ── Type tokens (16) ──────────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-type-*-size
enum class ReaderTypeToken(val contractName: String, internal val value: TextUnit) {
    APP_TITLE("--fd-ds-type-app-title-size", 20.sp),
    PAGE_TITLE("--fd-ds-type-page-title-size", 20.sp),
    SECTION_TITLE("--fd-ds-type-section-title-size", 15.sp),
    BOOK_TITLE("--fd-ds-type-book-title-size", 14.sp),
    BOOK_META("--fd-ds-type-book-meta-size", 12.sp),
    READER_BODY("--fd-ds-type-reader-body-size", 18.sp),
    READER_CONTROL_LABEL("--fd-ds-type-reader-control-label-size", 12.sp),
    TOP_BAR_TITLE("--fd-ds-type-top-bar-title-size", 16.sp),
    TOP_BAR_SUBTITLE("--fd-ds-type-top-bar-subtitle-size", 10.sp),
    ACTION_LABEL("--fd-ds-type-action-label-size", 11.sp),
    CHAPTER_TITLE("--fd-ds-type-chapter-title-size", 13.sp),
    READER_TITLE("--fd-ds-type-reader-title-size", 28.sp),
    APP_BAR_TITLE("--fd-ds-type-app-bar-title-size", 29.sp),
    BACK_BAR_TITLE("--fd-ds-type-back-bar-title-size", 29.sp),
    READER_CHAPTER_TITLE("--fd-ds-type-reader-chapter-title-size", 23.sp),
    EMPTY_HEADING("--fd-ds-type-empty-heading-size", 19.sp)
}

// ── z-index tokens (12) ───────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-z-* (values match tokens.css exactly)
// z-index唯一来源：通过 ReaderTokenAdapter.zIndex() 消费，不在 ReaderSizes 中重复定义。
enum class ReaderZIndexToken(val contractName: String, internal val value: Int) {
    CONTENT("--fd-ds-z-content", 0),
    OVERLAY("--fd-ds-z-overlay", 10),
    MAIN_NAV("--fd-ds-z-main-nav", 20),
    BOTTOM_SHEET("--fd-ds-z-bottom-sheet", 30),
    FLOW_WINDOW("--fd-ds-z-flow-window", 36),
    READER_MODULE_NAV("--fd-ds-z-reader-module-nav", 40),
    SETTINGS_DROPDOWN("--fd-ds-z-settings-dropdown", 42),
    DIALOG("--fd-ds-z-dialog", 60),
    KEYBOARD("--fd-ds-z-keyboard", 70),
    DEV_OVERLAY("--fd-ds-z-dev-overlay", 95),
    DEV_REGION("--fd-ds-z-dev-region", 96),
    DEMO_SWITCH("--fd-ds-z-demo-switch", 100)
}

// ── Easing tokens (4) ─────────────────────────────────────────────────────────
// Source: Token.kt --fd-ds-motion-easing-*
enum class ReaderEasingToken(val contractName: String, internal val easing: Easing) {
    STANDARD("--fd-ds-motion-easing-standard", Easing { t -> t }),                    // ease
    ENTER("--fd-ds-motion-easing-enter", CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f)), // ease-out
    EXIT("--fd-ds-motion-easing-exit", CubicBezierEasing(0.42f, 0.0f, 1.0f, 1.0f)),   // ease-in
    RESHAPE("--fd-ds-motion-easing-reshape", CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)) // ease-in-out
}

// ── Motion duration tokens (40) ──────────────────────────────────────────────
// Source: Token.kt --fd-ds-motion-duration-*
enum class ReaderDurationToken(val contractName: String, internal val millis: Int) {
    FIRST_OPEN("--fd-ds-motion-duration-firstOpen", 280),
    TAB_PRESS("--fd-ds-motion-duration-tabPress", 80),
    TAB_SELECT("--fd-ds-motion-duration-tabSelect", 120),
    TAB_SWITCH("--fd-ds-motion-duration-tabSwitch", 160),
    BUTTON_PRESS("--fd-ds-motion-duration-buttonPress", 80),
    BUTTON_ACTIVATE("--fd-ds-motion-duration-buttonActivate", 120),
    TOGGLE_SWITCH("--fd-ds-motion-duration-toggleSwitch", 140),
    CHIP_SELECT("--fd-ds-motion-duration-chipSelect", 120),
    FILTER_COMMIT("--fd-ds-motion-duration-filterCommit", 160),
    NUMERIC_COMMIT("--fd-ds-motion-duration-numericCommit", 120),
    INPUT_FOCUS("--fd-ds-motion-duration-inputFocus", 120),
    SEARCH_STATE("--fd-ds-motion-duration-searchState", 160),
    FEEDBACK_TOAST("--fd-ds-motion-duration-feedbackToast", 180),
    STATE_REPLACE("--fd-ds-motion-duration-stateReplace", 160),
    SELECTION_TOOLBAR("--fd-ds-motion-duration-selectionToolbar", 160),
    DROPDOWN_PRESS("--fd-ds-motion-duration-dropdownPress", 80),
    DROPDOWN_EXPAND("--fd-ds-motion-duration-dropdownExpand", 160),
    DROPDOWN_COLLAPSE("--fd-ds-motion-duration-dropdownCollapse", 120),
    DROPDOWN_SELECT("--fd-ds-motion-duration-dropdownSelect", 120),
    READER_INSTANT("--fd-ds-motion-duration-readerInstant", 0),
    READER_MICRO("--fd-ds-motion-duration-readerMicro", 80),
    READER_FAST("--fd-ds-motion-duration-readerFast", 120),
    READER_BASE("--fd-ds-motion-duration-readerBase", 160),
    HANDLE_LONG_PRESS("--fd-ds-motion-duration-handleLongPress", 320),
    HANDLE_SNAP("--fd-ds-motion-duration-handleSnap", 120),
    PANEL("--fd-ds-motion-duration-panel", 200),
    PAGE_TURN("--fd-ds-motion-duration-pageTurn", 220),
    READER_ENTRY("--fd-ds-motion-duration-readerEntry", 240),
    SESSION_RETURN("--fd-ds-motion-duration-sessionReturn", 200),
    RUNNING_SPACE("--fd-ds-motion-duration-runningSpace", 180),
    CAPSULE_ENTER("--fd-ds-motion-duration-capsuleEnter", 160),
    CAPSULE_CONTROL("--fd-ds-motion-duration-capsuleControl", 120),
    CAPSULE_TICK("--fd-ds-motion-duration-capsuleTick", 120),
    VOICE_PULSE("--fd-ds-motion-duration-voicePulse", 960),
    OVERLAY("--fd-ds-motion-duration-overlay", 240),
    LOADING_SPIN("--fd-ds-motion-duration-loadingSpin", 800),
    INTERRUPT_SETTLE("--fd-ds-motion-duration-interruptSettle", 80),
    VIEWPORT_RESHAPE("--fd-ds-motion-duration-viewportReshape", 240),
    ORIENTATION_FREEZE("--fd-ds-motion-duration-orientationFreeze", 80),
    ORIENTATION_SETTLE("--fd-ds-motion-duration-orientationSettle", 240)
}

internal fun ReaderDurationToken.toGeneratedToken(): Token =
    TokenRegistry.token(contractName) ?: Token(
        name = contractName,
        category = TokenCategory.MotionDuration,
        value = "${millis}ms",
        platforms = TokenPlatforms(kotlin = name)
    )

private fun String.toRegistryTokenName(): String {
    val alias = readerEasingAlias
    return when {
        startsWith("--fd-ds-") -> this
        startsWith("--reader-ds-") -> "--fd-ds-${substringAfter("--reader-ds-")}"
        startsWith("app.motion.duration.") -> "--fd-ds-motion-duration-${substringAfterLast('.')}"
        startsWith("reader.motion.duration.") -> "--fd-ds-motion-duration-${substringAfterLast('.')}"
        startsWith("app.motion.easing.") -> "--fd-ds-motion-easing-${substringAfterLast('.')}"
        alias != null -> alias
        else -> this
    }
}

private val String.readerEasingAlias: String?
    get() = if (startsWith("reader.motion.easing.")) {
        "--fd-ds-motion-easing-${substringAfterLast('.')}"
    } else {
        null
    }

private fun String.parseDurationMillis(): Int {
    val trimmed = trim()
    return when {
        trimmed.endsWith("ms") -> trimmed.removeSuffix("ms").trim().toInt()
        trimmed.endsWith("s") -> (trimmed.removeSuffix("s").trim().toDouble() * 1000).toInt()
        else -> trimmed.toInt()
    }
}

private fun String.parseDp(): Dp? {
    val trimmed = trim()
    val numeric = when {
        trimmed.endsWith("px") -> trimmed.removeSuffix("px").trim()
        trimmed.endsWith("dp") -> trimmed.removeSuffix("dp").trim()
        else -> trimmed
    }
    return numeric.toFloatOrNull()?.dp
}

private fun String.parseIntOrNull(): Int? {
    val trimmed = trim()
    // Strip unit suffixes like "ch", "px", "lines" to extract the leading integer.
    val digits = trimmed.takeWhile { it.isDigit() || it == '-' }
    return digits.toIntOrNull()
}
