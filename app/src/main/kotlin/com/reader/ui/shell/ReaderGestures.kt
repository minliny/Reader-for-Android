package com.reader.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.reader.ui.motion.MotionController
import com.reader.ui.motion.MotionIdConstants
import com.reader.ui.motion.ReaderMotionTokens
import kotlinx.coroutines.launch

/**
 * 手势工具集
 *
 * 契约来源：MOTION_CONTRACT.md 行 322 + 审计报告 N2（手势绑定几乎全无）
 */

// ========== 1. 翻页 swipe ==========
/**
 * 水平滑动手势，超过阈值触发翻页
 * 契约：reader.page.turn.next/prev 220ms 横向 16px
 */
fun Modifier.readerPageSwipe(
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    threshold: Float = 80f  // 80px 阈值
): Modifier = composed {
    pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = { /* 在 onHorizontalDrag 内累计判断 */ }
        ) { change, dragAmount ->
            if (dragAmount < -threshold) {
                MotionController.start(
                    motionId = MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
                    from = "page.current",
                    to = "page.next",
                    durationMs = MotionController.contractFor(MotionIdConstants.READER_PAGE_TURN_NEXT_PREV)?.defaultDurationMs
                        ?: 220L,
                    reducedMotion = MotionController.reducedFrom(null)
                )
                onNextPage()
            } else if (dragAmount > threshold) {
                MotionController.start(
                    motionId = MotionIdConstants.READER_PAGE_TURN_NEXT_PREV,
                    from = "page.current",
                    to = "page.previous",
                    durationMs = MotionController.contractFor(MotionIdConstants.READER_PAGE_TURN_NEXT_PREV)?.defaultDurationMs
                        ?: 220L,
                    reducedMotion = MotionController.reducedFrom(null)
                )
                onPrevPage()
            }
        }
    }
}

// ========== 2. 字号 pinch ==========
/**
 * 双指缩放调整字号
 * 契约：reader 字号 pinch 手势
 *
 * 注：契约中无 reader.font.size.pinch motionId，pinch 是连续手势不产生离散 motion transaction。
 * 字号变化通过 UpdateReaderTypography intent 的 reducer 路径处理，不需要 motion 触发。
 */
fun Modifier.readerFontSizePinch(
    onFontSizeChange: (Float) -> Unit
): Modifier = composed {
    pointerInput(Unit) {
        detectTransformGestures { _, _, zoom, _ ->
            if (zoom != 1f) {
                onFontSizeChange(zoom)
            }
        }
    }
}

// ========== 3. 控制层小横条 press/drag/release 三态 ==========
/**
 * 控制层小横条手势
 * 契约：reader.control.handle.press(80ms) / drag(无 easing) / release(snap 120ms)
 *
 * @param onDrag 跟手更新（无 easing）
 * @param onRelease 释放后判断：超过阈值 onExpand，未超过 onSnapBack
 */
fun Modifier.readerControlHandle(
    onExpand: () -> Unit,
    onSnapBack: () -> Unit,
    threshold: Float = 80f
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }

    pointerInput(Unit) {
        detectTapGestures(
            onPress = {
                // press 80ms
                MotionController.start(
                    motionId = MotionIdConstants.READER_CONTROL_HANDLE_PRESS,
                    from = "handleIdle",
                    to = "handlePressed",
                    durationMs = ReaderMotionTokens.DurationMicro.inWholeMilliseconds,
                    reducedMotion = MotionController.reducedFrom(null)
                )
                tryAwaitRelease()
            }
        )
    }.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                MotionController.start(
                    motionId = MotionIdConstants.READER_CONTROL_HANDLE_DRAG,
                    from = "handlePressed",
                    to = "handleDragging",
                    durationMs = 0,  // drag 无 easing
                    reducedMotion = MotionController.reducedFrom(null)
                )
            },
            onDragEnd = {
                // release snap 120ms
                scope.launch {
                    if (dragOffset.value > threshold) {
                        onExpand()
                    } else {
                        onSnapBack()
                        dragOffset.animateTo(0f, tween<Float>(ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()))
                    }
                }
                MotionController.start(
                    motionId = MotionIdConstants.READER_CONTROL_HANDLE_RELEASE,
                    from = "handleDragging",
                    to = "controlLayerResolvedToSingleRouteState",
                    durationMs = ReaderMotionTokens.DurationFast.inWholeMilliseconds,
                    reducedMotion = MotionController.reducedFrom(null)
                )
            },
            onDragCancel = {
                scope.launch {
                    dragOffset.animateTo(0f, tween<Float>(ReaderMotionTokens.DurationFast.inWholeMilliseconds.toInt()))
                }
            }
        ) { change, dragAmount ->
            // drag 跟手无 easing
            scope.launch {
                dragOffset.snapTo(dragOffset.value + dragAmount.y)
            }
        }
    }
}

// ========== 4. 宽屏 dock longPress + drag ==========
/**
 * 宽屏控制层 dock 长按进入拖动模式
 * 契约：reader.control.dock.longPress(320ms) / drag / release
 *
 * @param onDockOffsetChange dock offset 跟手更新
 * @param onDockOffsetCommit 释放后提交（按 viewport class 保存）
 */
fun Modifier.readerDockDrag(
    onDockOffsetChange: (Float) -> Unit,
    onDockOffsetCommit: (Float) -> Unit,
    longPressDurationMs: Long = 320L
): Modifier = composed {
    pointerInput(Unit) {
        detectTapGestures(
            onLongPress = {
                MotionController.start(
                    motionId = MotionIdConstants.READER_CONTROL_DOCK_LONG_PRESS,
                    from = "fixedWidthDock, handlePressed",
                    to = "dockDragArmed",
                    durationMs = longPressDurationMs,
                    reducedMotion = MotionController.reducedFrom(null)
                )
            }
        )
    }.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                MotionController.start(
                    motionId = MotionIdConstants.READER_CONTROL_DOCK_RELEASE,
                    from = "dockDragging, dockOffset.previewClamped",
                    to = "dockOffset.committed",
                    durationMs = 0,  // 立即提交
                    reducedMotion = MotionController.reducedFrom(null)
                )
            }
        ) { change, dragAmount ->
            // drag 跟手
            onDockOffsetChange(dragAmount.x)
        }
    }
}

// ========== 5. 文本选择 handle drag ==========
/**
 * 文本选择手柄拖动
 * 契约：selection.range.drag 跟手更新，不使用 easing
 */
fun Modifier.readerSelectionHandleDrag(
    onSelectionRangeUpdate: (Int) -> Unit
): Modifier = composed {
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                MotionController.start(
                    motionId = MotionIdConstants.SELECTION_RANGE_SHOW,
                    from = "selection.idle",
                    to = "selection.active",
                    durationMs = MotionController.contractFor(MotionIdConstants.SELECTION_RANGE_SHOW)?.defaultDurationMs
                        ?: 160L,
                    reducedMotion = MotionController.reducedFrom(null)
                )
            }
        ) { change, dragAmount ->
            onSelectionRangeUpdate(dragAmount.x.toInt())
        }
    }
}

// ========== 6. slider 跟手 + snap ==========
/**
 * 通用 slider/progress 拖动
 * 契约：slider.drag.start 取消装饰动画 / update 跟手无 easing / release snap 120ms
 *
 * @param onValueChange 跟手更新（无 easing）
 * @param onValueChangeFinished 释放后 snap 120ms
 */
fun Modifier.readerSliderDrag(
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
): Modifier = composed {
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                MotionController.start(
                    motionId = MotionIdConstants.SLIDER_DRAG_START,
                    from = "slider.idle",
                    to = "slider.dragging",
                    durationMs = 0,
                    reducedMotion = MotionController.reducedFrom(null)
                )
            },
            onDragEnd = {
                MotionController.start(
                    motionId = MotionIdConstants.SLIDER_DRAG_RELEASE,
                    from = "slider.dragging",
                    to = "slider.released",
                    durationMs = ReaderMotionTokens.DurationFast.inWholeMilliseconds,  // 120ms snap
                    reducedMotion = MotionController.reducedFrom(null)
                )
                onValueChangeFinished()
            }
        ) { change, dragAmount ->
            onValueChange(dragAmount.x)
        }
    }
}
