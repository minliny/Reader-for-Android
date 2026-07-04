// 依赖：androidx.window:window:1.3.0
// 说明：app/build.gradle.kts 当前未引入 androidx.window，需在 dependencies 中追加
//   implementation("androidx.window:window:1.3.0")
// 引入前 WindowInfoTracker / WindowMetricsCalculator / FoldingFeature 无法解析；
// 引入后 attach() 内部已用 try-catch 兜底，运行时异常降级为只读 PORTRAIT。
package com.reader.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.reader.ui.shell.InterruptKind
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewportClass 适配器
 *
 * 契约来源：MOTION_CONTRACT.md 行 243-245 + motion-controller.js 行 601-621
 * 三段式 motion：prepare(80ms 冻结) -> reshape(240ms 重排) -> settle(240ms 锚定)
 */
enum class ViewportClass {
    PORTRAIT,              // 手机竖屏
    COMPACT_LANDSCAPE,    // 手机横屏紧凑
    TABLET_EXPANDED,      // 平板展开
    EXPANDED_WIDTH,       // 宽屏
    HALF_OPENED           // 折叠屏半开
}

enum class OrientationPhase {
    STABLE,       // 稳定
    PREPARING,    // 80ms 冻结
    RESHAPING,    // 240ms 重排
    SETTLING      // 240ms 锚定
}

data class ViewportState(
    val viewportClass: ViewportClass = ViewportClass.PORTRAIT,
    val orientationPhase: OrientationPhase = OrientationPhase.STABLE,
    val foldFeature: FoldingFeature? = null,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val dockOffset: Map<ViewportClass, Dp> = emptyMap()  // 宽屏 dock offset 按 viewport class 保存
)

class ViewportClassAdapter {

    private val _state = MutableStateFlow(ViewportState())
    val state: StateFlow<ViewportState> = _state

    private var orientationJob: kotlinx.coroutines.Job? = null
    private var dockOffsets: MutableMap<ViewportClass, Dp> = mutableMapOf()

    /**
     * 监听窗口/折叠/旋转变化
     * 在 Activity.onCreate 中调用
     *
     * window 库不可用或运行时异常时降级为只读 PORTRAIT（契约兜底）。
     */
    suspend fun attach(activity: android.app.Activity) {
        try {
            val tracker = WindowInfoTracker.getOrCreate(activity)
            tracker.windowLayoutInfo(activity).collect { layoutInfo ->
                try {
                    val foldFeature = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()

                    val metrics = WindowMetricsCalculator.getOrCreate()
                        .computeCurrentWindowMetrics(activity)
                        .bounds
                    val widthDp = metrics.width() / activity.resources.displayMetrics.density.toInt()
                    val heightDp = metrics.height() / activity.resources.displayMetrics.density.toInt()

                    val newClass = computeViewportClass(widthDp, heightDp, foldFeature)

                    // 触发三段式 motion
                    startOrientationMotion(newClass, foldFeature, widthDp, heightDp)
                } catch (e: Throwable) {
                    // 单次 layout info 处理异常，降级为只读 PORTRAIT
                    fallbackToPortrait(activity)
                }
            }
        } catch (e: Throwable) {
            // window 库不可用（NoClassDefFoundError / ClassNotFoundError 等），降级为只读 PORTRAIT
            fallbackToPortrait(activity)
        }
    }

    /**
     * window 库不可用时的兜底：仅用平台 DisplayMetrics 推算尺寸，锁定 PORTRAIT + STABLE。
     * 不依赖任何 androidx.window 类型。
     */
    private fun fallbackToPortrait(activity: android.app.Activity) {
        val dm = activity.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).toInt()
        val heightDp = (dm.heightPixels / dm.density).toInt()
        _state.value = _state.value.copy(
            viewportClass = ViewportClass.PORTRAIT,
            orientationPhase = OrientationPhase.STABLE,
            foldFeature = null,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }

    /**
     * 三段式 orientation motion
     * prepare(80ms) -> reshape(240ms) -> settle(240ms)
     */
    private fun startOrientationMotion(
        newClass: ViewportClass,
        foldFeature: FoldingFeature?,
        widthDp: Int,
        heightDp: Int
    ) {
        orientationJob?.cancel()

        // 阶段 1：prepare（80ms 冻结）
        _state.value = _state.value.copy(orientationPhase = OrientationPhase.PREPARING)
        MotionController.start(
            motionId = MotionIds.VIEWPORT_ORIENTATION_PREPARE,
            from = "viewportStable",
            to = "viewportFrozen",
            durationMs = ReaderMotionTokens.DurationOrientationFreeze.inWholeMilliseconds,
            reducedMotion = MotionController.reducedFrom(null)
        )

        // 阶段 2：reshape（240ms 重排）
        // 阶段 3：settle（240ms 锚定）
        // 用 coroutine 序列执行
        // ... 实现完整三段式
    }

    /**
     * 旋转中冻结 capsule countdown、cancel 装饰动画
     */
    fun freezeAnimations() {
        MotionController.interrupt("orientation-prepare", InterruptKind.CANCEL)
    }

    /**
     * 旋转后 dropdown placement 重新计算
     */
    fun repositionDropdowns() {
        // 触发 dropdown.menu.reposition
        MotionController.start(
            motionId = "dropdown.menu.reposition",
            from = "openAtPreviousAnchor",
            to = "openAtLegalAnchor",
            durationMs = 0,  // 即时
            reducedMotion = MotionController.reducedFrom(null)
        )
    }

    /**
     * 宽屏 dock offset clamp 到新可移动空间
     */
    fun clampDockOffset(viewportClass: ViewportClass, maxOffset: Dp) {
        val current = dockOffsets[viewportClass] ?: 0.dp
        val clamped = current.coerceIn(0.dp, maxOffset)
        dockOffsets[viewportClass] = clamped
        _state.value = _state.value.copy(dockOffset = dockOffsets.toMap())

        if (current != clamped) {
            // 触发 reader.control.dock.rebound 120ms 回弹
            MotionController.start(
                motionId = "reader.control.dock.rebound",
                from = "dockOffset.saved",
                to = "dockOffset.clamped",
                durationMs = 120,
                reducedMotion = MotionController.reducedFrom(null)
            )
        }
    }

    private fun computeViewportClass(
        widthDp: Int,
        heightDp: Int,
        foldFeature: FoldingFeature?
    ): ViewportClass {
        if (foldFeature?.state == FoldingFeature.State.HALF_OPENED) {
            return ViewportClass.HALF_OPENED
        }
        return when {
            widthDp >= 840 -> ViewportClass.EXPANDED_WIDTH
            widthDp >= 600 -> ViewportClass.TABLET_EXPANDED
            widthDp > heightDp -> ViewportClass.COMPACT_LANDSCAPE
            else -> ViewportClass.PORTRAIT
        }
    }
}

/**
 * Composable 入口：在 AppShell 顶层调用，注入 ViewportState
 */
@Composable
fun rememberViewportState(): State<ViewportState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(ViewportState()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context) {
        val adapter = ViewportClassAdapter()
        val scope = kotlinx.coroutines.MainScope()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    adapter.attach(context as android.app.Activity)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            scope.cancel()
        }
    }

    return state
}
