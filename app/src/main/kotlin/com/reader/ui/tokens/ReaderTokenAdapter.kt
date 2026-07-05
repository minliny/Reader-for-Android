package com.reader.ui.tokens

import androidx.compose.ui.graphics.Color
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

    fun supports(token: Token): Boolean = when (token.category) {
        TokenCategory.Color,
        TokenCategory.Spacing,
        TokenCategory.Size,
        TokenCategory.Radius,
        TokenCategory.Type,
        TokenCategory.ZIndex,
        TokenCategory.MotionDuration,
        TokenCategory.MotionEasing -> TokenRegistry.token(token.name.toRegistryTokenName())?.category == token.category
        else -> false
    }
}

enum class ReaderTokenMode {
    LIGHT,
    DARK
}

enum class ReaderColorToken(
    val contractName: String,
    internal val light: Color,
    internal val dark: Color
) {
    PAPER("--reader-ds-color-paper", Color(0xFFFFF8F4), Color(0xFF24211E)),
    PAPER_BRIGHT("--reader-ds-color-paper-bright", Color(0xFFFFF8F1), Color(0xFF2C2824)),
    INK("--reader-ds-color-ink", Color(0xFF1F1B17), Color(0xFFEADFCE)),
    CONTROL_INK("--reader-ds-color-control-ink", Color(0xFF41484C), Color(0xFFEADFCE)),
    SURFACE("--reader-ds-color-surface", Color(0xFAFFFCF8), Color(0xF52A2622)),
    SURFACE_SOFT("--reader-ds-color-surface-soft", Color(0xB8FFFCF8), Color(0xB82A2622)),
    BORDER("--reader-ds-color-border", Color(0xFFC1C7CD), Color(0x33E2D1B9)),
    MUTED("--reader-ds-color-muted", Color(0xFF756F69), Color(0xFFBAAD9C)),
    PRIMARY("--reader-ds-color-primary", Color(0xFF366179), Color(0xFFD2BD96)),
    PRIMARY_DARK("--reader-ds-color-primary-dark", Color(0xFF274F66), Color(0xFF7A684F)),
    ACCENT("--reader-ds-color-accent", Color(0xFFF48B13), Color(0xFFD69B5F)),
    BOTTOM_BAR_BG("--reader-ds-color-bottom-bar-bg", Color(0xFFFBF2EB), Color(0xF52A2622)),
    FLOATING_CONTROL_BG("--reader-ds-color-floating-control-bg", Color(0xFFFBF2EB), Color(0xF52A2622)),
    FLOATING_CONTROL_BG_ALT("--reader-ds-color-floating-control-bg-alt", Color(0xFFEAE1DA), Color(0xF52A2622)),
    META_BG("--reader-ds-color-meta-bg", Color(0xFFEEE8DF), Color(0xF52A2622)),
    RSS_UNREAD("--reader-ds-color-rss-unread", Color(0xFFF48B13), Color(0xFFD69B5F))
}

enum class ReaderSpacingToken(val contractName: String, internal val value: Dp) {
    SCREEN_PADDING("--reader-ds-space-screen-padding", 16.dp),
    CARD_PADDING("--reader-ds-space-card-padding", 14.dp),
    SAFE_AREA_TOP("--reader-ds-space-safe-area-top", 24.dp),
    SAFE_AREA_BOTTOM("--reader-ds-space-safe-area-bottom", 14.dp),
    SAFE_AREA_HORIZONTAL("--reader-ds-space-safe-area-horizontal", 16.dp),
    KEYBOARD_GAP("--reader-ds-space-keyboard-gap", 12.dp),
    MD("--reader-ds-space-md", 16.dp)
}

enum class ReaderSizeToken(val contractName: String, internal val value: Dp) {
    BOTTOM_BAR_HEIGHT("--reader-ds-size-bottom-bar-height", 68.dp),
    MAIN_NAV_HEIGHT("--reader-ds-size-main-nav-height", 68.dp),
    READER_BOTTOM_SHEET_MIN_HEIGHT("--reader-ds-size-reader-bottom-sheet-min-height", 240.dp),
    READER_MODULE_NAV_HEIGHT("--reader-ds-size-reader-module-nav-height", 54.dp)
}

enum class ReaderRadiusToken(val contractName: String, internal val value: Dp) {
    CARD("--reader-ds-radius-card", 8.dp),
    CONTROL("--reader-ds-radius-control", 24.dp),
    BOTTOM_SHEET("--reader-ds-radius-bottom-sheet", 24.dp)
}

enum class ReaderTypeToken(val contractName: String, internal val value: TextUnit) {
    APP_TITLE("--reader-ds-type-app-title-size", 20.sp),
    PAGE_TITLE("--reader-ds-type-page-title-size", 20.sp),
    SECTION_TITLE("--reader-ds-type-section-title-size", 15.sp),
    BOOK_TITLE("--reader-ds-type-book-title-size", 14.sp),
    BOOK_META("--reader-ds-type-book-meta-size", 12.sp),
    READER_BODY("--reader-ds-type-reader-body-size", 18.sp),
    READER_CONTROL_LABEL("--reader-ds-type-reader-control-label-size", 12.sp)
}

enum class ReaderZIndexToken(val contractName: String, internal val value: Int) {
    MAIN_NAV("--reader-ds-z-main-nav", 20),
    OVERLAY("--reader-ds-z-overlay", 30),
    BOTTOM_SHEET("--reader-ds-z-bottom-sheet", 40),
    READER_MODULE_NAV("--reader-ds-z-reader-module-nav", 45),
    DIALOG("--reader-ds-z-dialog", 50)
}

enum class ReaderDurationToken(val contractName: String, internal val millis: Int) {
    FIRST_OPEN("--reader-ds-motion-duration-firstOpen", 280),
    TAB_PRESS("--reader-ds-motion-duration-tabPress", 80),
    TAB_SELECT("--reader-ds-motion-duration-tabSelect", 120),
    TAB_SWITCH("--reader-ds-motion-duration-tabSwitch", 160),
    BUTTON_PRESS("--reader-ds-motion-duration-buttonPress", 80),
    BUTTON_ACTIVATE("--reader-ds-motion-duration-buttonActivate", 80),
    TOGGLE_SWITCH("--reader-ds-motion-duration-toggleSwitch", 120),
    PANEL("--reader-ds-motion-duration-panel", 200),
    STATE_REPLACE("--reader-ds-motion-duration-stateReplace", 160),
    READER_ENTRY("--reader-ds-motion-duration-readerEntry", 240),
    PAGE_TURN("--reader-ds-motion-duration-pageTurn", 220),
    OVERLAY("--reader-ds-motion-duration-overlay", 240),
    INTERRUPT_SETTLE("--reader-ds-motion-duration-interruptSettle", 80),
    VIEWPORT_RESHAPE("--reader-ds-motion-duration-viewportReshape", 240),
    LOADING_SPIN("--reader-ds-motion-duration-loadingSpin", 800),
    FEEDBACK_TOAST("--reader-ds-motion-duration-feedbackToast", 180)
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
        startsWith("--reader-ds-") -> this
        startsWith("app.motion.duration.") -> "--reader-ds-motion-duration-${substringAfterLast('.')}"
        startsWith("reader.motion.duration.") -> "--reader-ds-motion-duration-${substringAfterLast('.')}"
        startsWith("app.motion.easing.") -> "--reader-ds-motion-easing-${substringAfterLast('.')}"
        alias != null -> alias
        else -> this
    }
}

private val String.readerEasingAlias: String?
    get() = if (startsWith("reader.motion.easing.")) {
        "--reader-ds-motion-easing-${substringAfterLast('.')}"
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
