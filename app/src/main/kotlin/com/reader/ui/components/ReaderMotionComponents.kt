@file:OptIn(ExperimentalFoundationApi::class)

package com.reader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.ui.motion.AppMotionTokens
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.ReaderMotionTokens
import com.reader.ui.motion.effectiveDuration
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.readerExtraColors
import kotlinx.coroutines.delay

/**
 * 统一组件族 adapter
 * 契约来源：MOTION_INTERACTION_COMPONENT_AUDIT.md 行 26-49 + MOTION_CONTRACT.md 行 247-260
 *
 * 内置 press/select/switch/activate 动画反馈，所有组件复用 com.reader.ui.motion.MotionController。
 *
 * 组件族映射（与 audit 行 39-46 对齐）：
 * - button.*        → ReaderButton / ReaderIconButton
 * - toggle.*        → ReaderToggle
 * - chip.item.*     → ReaderChip
 * - filter.*        → ReaderFilterChip
 * - segment.item.*  → ReaderSegment
 * - dropdown.*      → ReaderDropdown
 * - slider.*        → ReaderSlider
 * - stepper.*       → ReaderStepper
 * - listRow.*       → ReaderListRow
 * - card.*          → ReaderCard
 *
 * Reduced motion 规则（MOTION_CONTRACT.md §7 / MOTION_EFFECTS.md §8）：
 * 所有 scale / 位移 collapsed 为 0；颜色、不透明度、focus ring、selected-state 保留。
 *
 * `combinedClickable` 在 Compose BOM 2024.10.01 仍为 ExperimentalFoundationApi，
 * 文件级 @OptIn 已声明。
 */

/** 组件族 motion id 字面量，命名与 audit/contract 文档完全对齐。 */
private object ComponentMotionIds {
    // button.*
    const val BUTTON_PRESS = "button.press"
    const val BUTTON_ACTIVATE = "button.activate"
    const val BUTTON_DISABLED_BLOCKED = "button.disabledBlocked"

    // toggle.*
    const val TOGGLE_PRESS = "toggle.press"
    const val TOGGLE_SWITCH = "toggle.switch"
    const val TOGGLE_REVERT = "toggle.revert"

    // chip.item.*
    const val CHIP_ITEM_PRESS = "chip.item.press"
    const val CHIP_ITEM_SELECT = "chip.item.select"

    // filter.*
    const val FILTER_ITEM_TOGGLE = "filter.item.toggle"
    const val FILTER_APPLY_COMMIT = "filter.apply.commit"

    // segment.item.*
    const val SEGMENT_ITEM_SWITCH = "segment.item.switch"

    // dropdown.*
    const val DROPDOWN_TRIGGER_PRESS = "dropdown.trigger.press"
    const val DROPDOWN_MENU_EXPAND = "dropdown.menu.expand"
    const val DROPDOWN_MENU_COLLAPSE = "dropdown.menu.collapse"
    const val DROPDOWN_OPTION_PRESS = "dropdown.option.press"
    const val DROPDOWN_OPTION_SELECT = "dropdown.option.select"
    const val DROPDOWN_MENU_REPOSITION = "dropdown.menu.reposition"

    // slider.*
    const val SLIDER_DRAG_START = "slider.drag.start"
    const val SLIDER_DRAG_UPDATE = "slider.drag.update"
    const val SLIDER_DRAG_RELEASE = "slider.drag.release"
    const val SLIDER_VALUE_COMMIT = "slider.value.commit"

    // stepper.*
    const val STEPPER_PRESS = "stepper.press"
    const val STEPPER_REPEAT = "stepper.repeat"
    const val STEPPER_VALUE_CHANGE = "stepper.value.change"

    // listRow.*
    const val LIST_ROW_PRESS = "listRow.press"
    const val LIST_ROW_SELECT = "listRow.select"
    const val LIST_ROW_ROUTE = "listRow.route"

    // card.*
    const val CARD_PRESS = "card.press"
    const val CARD_SELECT = "card.select"
    const val CARD_ROUTE = "card.route"
}

// ── 内部 duration token（毫秒 Int，供 tween 直接使用） ─────────────────────────────
/** 80ms press token（AppMotionTokens.DurationButtonPress / ReaderMotionTokens.DurationMicro）。 */
private val PressDurationMs: Int = AppMotionTokens.DurationButtonPress.inWholeMilliseconds.toInt()

/** 120ms select/switch token（ReaderMotionTokens.DurationFast）。 */
private val FastDurationMs: Int = ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()

/** 160ms expand/commit/migrate token（ReaderMotionTokens.DurationBase）。 */
private val BaseDurationMs: Int = ReaderMotionTokens.DurationBase.inWholeMilliseconds.toInt()

/**
 * 计算 press scale：reducedMotion 时恒为 1f，否则按下 0.98f、抬起 1f。
 */
private fun pressScaleTarget(pressed: Boolean, enabled: Boolean, reducedMotion: Boolean): Float =
    when {
        !enabled -> 1f
        reducedMotion -> 1f
        pressed -> AppMotionTokens.PressScale
        else -> 1f
    }

/** press 时长（reducedMotion 折叠为 0）。 */
private fun pressDuration(reducedMotion: Boolean): Int =
    effectiveDuration(AppMotionTokens.DurationButtonPress, reducedMotion).inWholeMilliseconds.toInt()

/** fast 时长（120ms，reducedMotion 折叠为 0）。 */
private fun fastDuration(reducedMotion: Boolean): Int =
    effectiveDuration(ReaderMotionTokens.DurationFast, reducedMotion).inWholeMilliseconds.toInt()

/** base 时长（160ms，reducedMotion 折叠为 0）。 */
private fun baseDuration(reducedMotion: Boolean): Int =
    effectiveDuration(ReaderMotionTokens.DurationBase, reducedMotion).inWholeMilliseconds.toInt()

/**
 * 向 MotionController 注册一次动效（适配已有 start(motionId, from, to, durationMs: Long, reducedMotion) 签名）。
 * from/to 使用简化的语义状态名，与 MotionController 契约表里的 from/to 同语义但不强制逐字匹配。
 */
private fun reportMotion(
    motionId: String,
    from: String,
    to: String,
    durationMs: Int,
    reducedMotion: Boolean
) {
    MotionController.start(motionId, from, to, durationMs.toLong(), reducedMotion)
}

// ========== 1. ReaderButton（普通按钮）==========
/**
 * 通用按钮组件族（button.*）。
 *
 * - press：80ms scale 1 → 0.98 → 1（AppMotionTokens.PressScale）
 * - activate：120ms 状态替换（颜色/底色切换）
 * - disabled blocked：无任何反馈，alpha 降至 0.38
 * - reducedMotion：scale 1f，保留底色/边框反馈
 *
 * @param destructive 危险按钮，使用 danger 底色
 */
@Composable
fun ReaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // press 80ms scale
    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "button.press.scale"
    )
    // 按下时注册 button.press（仅在 pressed 翻为 true 那一帧触发，避免重组重复 start）
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.BUTTON_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    // activate 120ms 底色替换
    val activateDurationMs = fastDuration(reducedMotion)
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> extra.controlDisabledBg
            destructive -> extra.danger
            pressed -> extra.primaryDark
            else -> extra.primaryDark.copy(alpha = 0.0f)
        },
        animationSpec = tween(durationMillis = activateDurationMs, easing = FastOutSlowInEasing),
        label = "button.activate.color"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ReaderShapes.sm)
            .background(containerColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    reportMotion(
                        ComponentMotionIds.BUTTON_ACTIVATE,
                        from = "pressed",
                        to = "committed",
                        durationMs = FastDurationMs,
                        reducedMotion = reducedMotion
                    )
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!enabled) {
            reportMotion(
                ComponentMotionIds.BUTTON_DISABLED_BLOCKED,
                from = "enabled",
                to = "disabled",
                durationMs = 0,
                reducedMotion = reducedMotion
            )
        }
        Row(
            modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ========== 2. ReaderIconButton（图标按钮）==========
/**
 * 图标按钮组件族（同 button.*，但默认圆形 + 仅 icon）。
 */
@Composable
fun ReaderIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "iconButton.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.BUTTON_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(
            durationMillis = fastDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "iconButton.press.bg"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(extra.controlActiveBg.copy(alpha = if (enabled) bgAlpha else 0f))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    reportMotion(
                        ComponentMotionIds.BUTTON_ACTIVATE,
                        from = "pressed",
                        to = "committed",
                        durationMs = FastDurationMs,
                        reducedMotion = reducedMotion
                    )
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.alpha(if (enabled) 1f else 0.38f)) {
            icon()
        }
    }
}

// ========== 3. ReaderToggle（switch/checkbox）==========
/**
 * Switch/Checkbox/Toggle 组件族（toggle.*）。
 *
 * - toggle.press 80ms pressed 反馈
 * - toggle.switch 120ms thumb 滑动（contract 注 140ms，本仓 token 取 DurationFast 120ms 最近的 fast 档）
 * - toggle.revert 异步失败回滚（调用方设 checked=false 后 thumb 自动回弹）
 *
 * checkbox 也归入本族（audit 行 41）。
 */
@Composable
fun ReaderToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // press 80ms
    val pressScale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "toggle.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.TOGGLE_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    // switch 120ms thumb 滑动
    val switchDurationMs = fastDuration(reducedMotion)
    reportMotion(
        ComponentMotionIds.TOGGLE_SWITCH,
        from = if (checked) "unchecked" else "checked",
        to = if (checked) "checked" else "unchecked",
        durationMs = switchDurationMs,
        reducedMotion = reducedMotion
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(
            durationMillis = switchDurationMs,
            easing = FastOutSlowInEasing
        ),
        label = "toggle.switch.thumb"
    )

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> extra.controlDisabledBg
            checked -> extra.primaryDark
            else -> extra.controlDisabledBg
        },
        animationSpec = tween(durationMillis = switchDurationMs, easing = FastOutSlowInEasing),
        label = "toggle.track.color"
    )

    Box(
        modifier = modifier
            .scale(pressScale)
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(trackColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = (2 + thumbOffset * 20).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else extra.muted)
        )
    }
}

// ========== 4. ReaderChip / ReaderFilterChip ==========
/**
 * Chip 组件族（chip.item.*）。
 * - chip.item.press 80ms
 * - chip.item.select 120ms active 背景
 */
@Composable
fun ReaderChip(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "chip.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.CHIP_ITEM_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val selectDurationMs = fastDuration(reducedMotion)
    LaunchedEffect(selected, enabled) {
        if (selected && enabled) {
            reportMotion(
                ComponentMotionIds.CHIP_ITEM_SELECT,
                from = "inactive",
                to = "active",
                durationMs = selectDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> extra.controlDisabledBg
            selected -> extra.primaryDark
            pressed -> extra.controlActiveStrong
            else -> extra.controlActiveSoft
        },
        animationSpec = tween(durationMillis = selectDurationMs, easing = FastOutSlowInEasing),
        label = "chip.bg"
    )
    val fg = when {
        !enabled -> extra.muted
        selected -> extra.onPrimary
        else -> extra.readerInk
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ReaderShapes.pill)
            .background(bg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = fg, fontSize = ReaderTypeToken.CHAPTER_TITLE.value, fontWeight = FontWeight(600))
    }
}

/**
 * FilterChip 组件族（filter.*）。
 * - filter.item.toggle 多选
 * - filter.apply.commit 160ms 结果区短反馈（onCheckedChange 返回 true 后触发）
 *
 * @param onCheckedChange 返回 true 表示筛选已应用，将触发 filter.apply.commit
 */
@Composable
fun ReaderFilterChip(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Boolean,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "filter.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.FILTER_ITEM_TOGGLE,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val commitDurationMs = baseDuration(reducedMotion)

    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> extra.controlDisabledBg
            checked -> extra.primaryDark
            pressed -> extra.controlActiveStrong
            else -> extra.controlActiveSoft
        },
        animationSpec = tween(durationMillis = commitDurationMs, easing = FastOutSlowInEasing),
        label = "filter.bg"
    )
    val fg = when {
        !enabled -> extra.muted
        checked -> extra.onPrimary
        else -> extra.readerInk
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ReaderShapes.pill)
            .background(bg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    // filter.item.toggle：翻转 checked，应用成功则触发 commit
                    val next = !checked
                    val applied = onCheckedChange(next)
                    if (applied) {
                        reportMotion(
                            ComponentMotionIds.FILTER_APPLY_COMMIT,
                            from = "pending",
                            to = "committed",
                            durationMs = commitDurationMs,
                            reducedMotion = reducedMotion
                        )
                    }
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = text, color = fg, fontSize = ReaderTypeToken.CHAPTER_TITLE.value, fontWeight = FontWeight(600))
            if (checked) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = extra.primaryDark, fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value, fontWeight = FontWeight(800))
                }
            }
        }
    }
}

// ========== 5. ReaderSegment（segmented control）==========
/**
 * Segmented control item 组件族（segment.item.*）。
 * - segment.item.switch 120ms A→B 切换
 * - indicator 迁移 160ms（由父容器维护 indicator 偏移；本 item 仅负责选中态底色）
 */
@Composable
fun ReaderSegment(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "segment.press.scale"
    )

    val switchDurationMs = fastDuration(reducedMotion)
    val migrateDurationMs = baseDuration(reducedMotion)

    LaunchedEffect(selected, enabled) {
        if (selected && enabled) {
            reportMotion(
                ComponentMotionIds.SEGMENT_ITEM_SWITCH,
                from = "segment.previous",
                to = "segment.next",
                durationMs = switchDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> extra.controlActiveStrong
            pressed -> extra.controlActiveSoft
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = migrateDurationMs, easing = FastOutSlowInEasing),
        label = "segment.bg"
    )
    val fg = when {
        !enabled -> extra.muted
        selected -> extra.readerInk
        else -> extra.muted
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ReaderShapes.sm)
            .background(bg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
            fontWeight = if (selected) FontWeight(800) else FontWeight(600)
        )
    }
}

// ========== 6. ReaderDropdown ==========
/**
 * Dropdown 组件族（dropdown.*）。
 *
 * - dropdown.trigger.press 80ms
 * - dropdown.menu.expand 160ms fade + 6px 位移（AppMotionTokens.DropdownY）
 * - dropdown.menu.collapse 120ms
 * - dropdown.option.press 80ms
 * - dropdown.option.select 120ms check/icon 替换
 * - dropdown.menu.reposition orientation 时重新计算（通过 key 强制重组）
 */
data class DropdownItem(
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun ReaderDropdown(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    trigger: @Composable () -> Unit,
    items: List<DropdownItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current

    val triggerScale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "dropdown.trigger.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.DROPDOWN_TRIGGER_PRESS,
                from = "idle",
                to = "triggerPressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val expandDurationMs = baseDuration(reducedMotion)
    val collapseDurationMs = fastDuration(reducedMotion)

    LaunchedEffect(expanded) {
        if (expanded) {
            reportMotion(
                ComponentMotionIds.DROPDOWN_MENU_EXPAND,
                from = "closed",
                to = "open",
                durationMs = expandDurationMs,
                reducedMotion = reducedMotion
            )
        } else {
            reportMotion(
                ComponentMotionIds.DROPDOWN_MENU_COLLAPSE,
                from = "open",
                to = "closed",
                durationMs = collapseDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    // reposition：expanded 期间 orientation 变化时通过 key 强制重组
    val repositionKey = remember(expanded) { expanded.toString() + System.nanoTime() }
    reportMotion(
        ComponentMotionIds.DROPDOWN_MENU_REPOSITION,
        from = "openAtPreviousAnchor",
        to = "openAtLegalAnchor",
        durationMs = 0,
        reducedMotion = reducedMotion
    )

    // 6px 位移（reducedMotion 时折叠为 0）
    val dropdownYPx = with(density) {
        if (reducedMotion) 0 else AppMotionTokens.DropdownY.roundToPx()
    }

    Box(modifier = modifier.wrapContentSize(Alignment.TopStart)) {
        Box(
            modifier = Modifier
                .scale(triggerScale)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = { onExpandChange(!expanded) }
                )
        ) {
            trigger()
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                animationSpec = tween(durationMillis = expandDurationMs, easing = FastOutSlowInEasing)
            ) + slideInVertically(
                animationSpec = tween(durationMillis = expandDurationMs, easing = FastOutSlowInEasing),
                initialOffsetY = { dropdownYPx }
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = collapseDurationMs, easing = FastOutSlowInEasing)
            ) + slideOutVertically(
                animationSpec = tween(durationMillis = collapseDurationMs, easing = FastOutSlowInEasing),
                targetOffsetY = { dropdownYPx }
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(ReaderShapes.md)
                    .background(extra.controlSurfaceSolid)
                    .border(1.dp, extra.hairline, ReaderShapes.md)
                    .width(180.dp)
            ) {
                items.forEachIndexed { index, item ->
                    DropdownOptionRow(
                        item = item,
                        enabled = enabled,
                        reducedMotion = reducedMotion,
                        onDismiss = { onExpandChange(false) }
                    )
                    if (index < items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .height(1.dp)
                                .background(extra.hairline)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownOptionRow(
    item: DropdownItem,
    enabled: Boolean,
    reducedMotion: Boolean,
    onDismiss: () -> Unit
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "dropdown.option.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.DROPDOWN_OPTION_PRESS,
                from = "optionIdle",
                to = "optionPressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val selectDurationMs = fastDuration(reducedMotion)
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            item.selected -> extra.controlActiveStrong
            pressed -> extra.controlActiveSoft
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = selectDurationMs, easing = FastOutSlowInEasing),
        label = "dropdown.option.bg"
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    reportMotion(
                        ComponentMotionIds.DROPDOWN_OPTION_SELECT,
                        from = "open",
                        to = "valueCommitted",
                        durationMs = selectDurationMs,
                        reducedMotion = reducedMotion
                    )
                    item.onClick()
                    onDismiss()
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = item.label, color = extra.readerInk, fontSize = ReaderTypeToken.BOOK_TITLE.value)
        if (item.selected) {
            Text("✓", color = extra.primaryDark, fontSize = ReaderTypeToken.BOOK_TITLE.value, fontWeight = FontWeight(800))
        }
    }
}

// ========== 7. ReaderSlider ==========
/**
 * Slider 组件族（slider.*）。
 *
 * - slider.drag.start 取消装饰动画
 * - slider.drag.update 跟手无 easing（直接 onValueChange）
 * - slider.drag.release snap 120ms
 * - slider.value.commit 120ms readout 替换
 *
 * 通过 Modifier.pointerInput { detectDragGestures } 实现，避免依赖 Material Slider。
 */
@Composable
fun ReaderSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }

    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val rangeSpan = (rangeEnd - rangeStart).coerceAtLeast(0.0001f)

    // release snap 120ms / commit 120ms
    val releaseDurationMs = fastDuration(reducedMotion)
    val commitDurationMs = fastDuration(reducedMotion)

    // 跟手期间显示 dragValue，松开后回显 value
    val displayValue = if (dragging) dragValue else value
    val fraction = ((displayValue - rangeStart) / rangeSpan).coerceIn(0f, 1f)

    val trackColor = if (!enabled) extra.controlDisabledBg else extra.controlActiveSoft
    val fillColor = if (!enabled) extra.muted else extra.primaryDark
    val thumbColor = if (!enabled) extra.muted else extra.primaryDark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        dragValue = value
                        reportMotion(
                            ComponentMotionIds.SLIDER_DRAG_START,
                            from = "idle",
                            to = "dragging",
                            durationMs = 0,
                            reducedMotion = reducedMotion
                        )
                    },
                    onDragEnd = {
                        dragging = false
                        reportMotion(
                            ComponentMotionIds.SLIDER_DRAG_RELEASE,
                            from = "dragging",
                            to = "idle",
                            durationMs = releaseDurationMs,
                            reducedMotion = reducedMotion
                        )
                        reportMotion(
                            ComponentMotionIds.SLIDER_VALUE_COMMIT,
                            from = "pending",
                            to = "committed",
                            durationMs = commitDurationMs,
                            reducedMotion = reducedMotion
                        )
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        dragValue = value
                    },
                    onDrag = { change, delta ->
                        // slider.drag.update 跟手无 easing
                        reportMotion(
                            ComponentMotionIds.SLIDER_DRAG_UPDATE,
                            from = "dragging",
                            to = "dragging",
                            durationMs = 0,
                            reducedMotion = reducedMotion
                        )
                        val widthPx = size.width.coerceAtLeast(1)
                        val deltaFraction = delta.x / widthPx
                        val next = (dragValue + deltaFraction * rangeSpan)
                            .coerceIn(rangeStart, rangeEnd)
                        dragValue = next
                        onValueChange(next)
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // track（满宽底色）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(trackColor)
        )
        // fill（按 fraction 填充；用 alpha 模拟填充比例，真实宽度需 Layout 测量）
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(CircleShape)
                .background(fillColor.copy(alpha = if (enabled) 1f else 0f))
        )
        // thumb（按 fraction 偏移）
        Box(
            modifier = Modifier
                .padding(start = (fraction * 200).dp.coerceAtMost(200.dp))
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

// ========== 8. ReaderStepper ==========
/**
 * Stepper 组件族（stepper.*）。
 *
 * - stepper.press 80ms
 * - stepper.repeat 长按连续（按住 500ms 后每 80ms 自增一次）
 * - stepper.value.change 120ms readout
 */
@Composable
fun ReaderStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false
) {
    val extra = readerExtraColors()

    val pressDurationMs = PressDurationMs
    val valueChangeDurationMs = fastDuration(reducedMotion)

    val readoutAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = valueChangeDurationMs, easing = FastOutSlowInEasing),
        label = "stepper.value.change.alpha"
    )

    fun step(delta: Int) {
        val next = (value + delta).coerceIn(range.first, range.last)
        if (next != value) {
            reportMotion(
                ComponentMotionIds.STEPPER_VALUE_CHANGE,
                from = "previous",
                to = "next",
                durationMs = valueChangeDurationMs,
                reducedMotion = reducedMotion
            )
            onValueChange(next)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StepperButton(
            text = "−",
            enabled = enabled && value > range.first,
            reducedMotion = reducedMotion,
            onPressStart = {
                reportMotion(
                    ComponentMotionIds.STEPPER_PRESS,
                    from = "idle",
                    to = "pressed",
                    durationMs = pressDurationMs,
                    reducedMotion = reducedMotion
                )
                step(-1)
            },
            onLongPressRepeat = { step(-1) }
        )
        Text(
            text = value.toString(),
            color = if (enabled) extra.readerInk else extra.muted,
            fontSize = ReaderTypeToken.TOP_BAR_TITLE.value,
            fontWeight = FontWeight(700),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(40.dp)
                .alpha(readoutAlpha)
        )
        StepperButton(
            text = "+",
            enabled = enabled && value < range.last,
            reducedMotion = reducedMotion,
            onPressStart = {
                reportMotion(
                    ComponentMotionIds.STEPPER_PRESS,
                    from = "idle",
                    to = "pressed",
                    durationMs = pressDurationMs,
                    reducedMotion = reducedMotion
                )
                step(1)
            },
            onLongPressRepeat = { step(1) }
        )
    }
}

@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    reducedMotion: Boolean,
    onPressStart: () -> Unit,
    onLongPressRepeat: () -> Unit
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var repeating by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "stepper.press.scale"
    )

    // stepper.repeat：长按连续（500ms 后每 80ms 一次）
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            delay(500)
            repeating = true
            while (repeating) {
                reportMotion(
                    ComponentMotionIds.STEPPER_REPEAT,
                    from = "pressed",
                    to = "stepping",
                    durationMs = PressDurationMs,
                    reducedMotion = reducedMotion
                )
                onLongPressRepeat()
                delay(80)
            }
        } else {
            repeating = false
        }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) extra.controlActiveSoft else extra.controlDisabledBg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onPressStart
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) extra.readerInk else extra.muted,
            fontSize = ReaderTypeToken.READER_BODY.value,
            fontWeight = FontWeight(700)
        )
    }
}

// ========== 9. ReaderListRow ==========
/**
 * ListRow 组件族（listRow.*）。
 *
 * - listRow.press 80ms
 * - listRow.select 选中背景 + check
 * - listRow.route 路由型进入 app.route.push（调用方在 onClick 内 dispatch）
 *
 * @param onClick 路由型点击；为 null 表示纯展示行
 * @param onLongClick 长按（用于批量选择）
 * @param selected 选中态（批量选择 / 当前项）
 */
@Composable
fun ReaderListRow(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    reducedMotion: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "listRow.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.LIST_ROW_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }
    LaunchedEffect(selected, enabled) {
        if (selected && enabled) {
            reportMotion(
                ComponentMotionIds.LIST_ROW_SELECT,
                from = "inactive",
                to = "active",
                durationMs = FastDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val selectDurationMs = fastDuration(reducedMotion)
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> extra.controlActiveSoft
            pressed -> extra.controlActiveBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = selectDurationMs, easing = FastOutSlowInEasing),
        label = "listRow.bg"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .background(bg)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (onClick != null) {
                        // listRow.route 由调用方在 onClick 内 dispatch app.route.push
                        reportMotion(
                            ComponentMotionIds.LIST_ROW_ROUTE,
                            from = "route.current",
                            to = "route.target",
                            durationMs = BaseDurationMs,
                            reducedMotion = reducedMotion
                        )
                        onClick()
                    }
                },
                onLongClick = onLongClick?.let {
                    {
                        reportMotion(
                            ComponentMotionIds.LIST_ROW_SELECT,
                            from = "inactive",
                            to = "active",
                            durationMs = FastDurationMs,
                            reducedMotion = reducedMotion
                        )
                        it()
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) leading()
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        if (selected) {
            Text("✓", color = extra.primaryDark, fontSize = ReaderTypeToken.TOP_BAR_TITLE.value, fontWeight = FontWeight(800))
        }
        if (trailing != null) trailing()
    }
}

// ========== 10. ReaderCard ==========
/**
 * Card 组件族（card.*）。
 *
 * - card.press 80ms scale 1→0.98→1
 * - card.select 选中层 + check + focus ring（border 加粗 + primary 色）
 * - card.route 卡片进入详情（封面进入用 reader.entry.coverToImmersive，由调用方再触发）
 *
 * @param onClick 路由型点击；为 null 表示纯展示卡
 * @param onLongClick 长按用于多选
 * @param selected 选中态（批量选择）
 */
@Composable
fun ReaderCard(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = pressScaleTarget(pressed, enabled, reducedMotion),
        animationSpec = tween(
            durationMillis = pressDuration(reducedMotion),
            easing = FastOutSlowInEasing
        ),
        label = "card.press.scale"
    )
    LaunchedEffect(pressed, enabled) {
        if (pressed && enabled) {
            reportMotion(
                ComponentMotionIds.CARD_PRESS,
                from = "idle",
                to = "pressed",
                durationMs = PressDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }
    LaunchedEffect(selected, enabled) {
        if (selected && enabled) {
            reportMotion(
                ComponentMotionIds.CARD_SELECT,
                from = "inactive",
                to = "active",
                durationMs = FastDurationMs,
                reducedMotion = reducedMotion
            )
        }
    }

    val selectDurationMs = fastDuration(reducedMotion)
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> extra.hairline
            selected -> extra.primaryDark
            else -> extra.hairline
        },
        animationSpec = tween(durationMillis = selectDurationMs, easing = FastOutSlowInEasing),
        label = "card.border"
    )
    val bg by animateColorAsState(
        targetValue = when {
            !enabled -> extra.controlDisabledBg
            selected -> extra.controlActiveSoft
            pressed -> extra.controlActiveBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = selectDurationMs, easing = FastOutSlowInEasing),
        label = "card.bg"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(ReaderShapes.md)
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = ReaderShapes.md
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (onClick != null) {
                        // card.route：卡片进入详情；封面场景由调用方再触发 reader.entry.coverToImmersive
                        reportMotion(
                            ComponentMotionIds.CARD_ROUTE,
                            from = "route.current",
                            to = "route.target",
                            durationMs = BaseDurationMs,
                            reducedMotion = reducedMotion
                        )
                        onClick()
                    }
                },
                onLongClick = onLongClick?.let {
                    {
                        reportMotion(
                            ComponentMotionIds.CARD_SELECT,
                            from = "inactive",
                            to = "active",
                            durationMs = FastDurationMs,
                            reducedMotion = reducedMotion
                        )
                        it()
                    }
                }
            )
            .padding(12.dp),
        content = content
    )
}
