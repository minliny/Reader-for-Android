package com.reader.ui.motion

import com.reader.ui.shell.AppShellViewModel
import com.reader.ui.shell.InterruptKind
import io.reader.ui.contract.Motion
import io.reader.ui.contract.MotionId
import io.reader.ui.contract.MotionSpecRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

/**
 * 动效运行时单例，等价于 frontend-demo `motion-controller.js` 的 `create()` 工厂产物。
 *
 * 关键契约来源：
 * - motion-controller.js 1164-1334（create / start / update / interrupt / settle / destroy）
 * - motion-controller.js 292-622（47 个 Motion ID 状态机表）
 * - MOTION_CONTRACT.md 237-245（interrupt 三态 + async guard）
 * - ReaderUiState.kt 69-85（InterruptKind / MotionInterrupt）
 *
 * 单例化理由：Web demo 中 `create()` 在 AppShell 启动时只构造一次并挂到 root；
 * Android 侧同等语义是进程级单例，由 AppShellViewModel 在 dispatch intent 时调用。
 */
object MotionController {

    // ------------------------------------------------------------------
    // 状态字段
    // ------------------------------------------------------------------

    /** 当前 active transaction；null 表示空闲。等价 motion-controller.js 的 `active`。 */
    @Volatile
    private var active: MotionTransaction? = null

    /** 递增序号；每次 start() 自增。等价 motion-controller.js 的 `sequence`。 */
    private val sequenceCounter = AtomicLong(0L)

    /** 事件日志，上限 120 条，超过则 shift 旧的。等价 motion-controller.js 的 `events`。 */
    private val events = mutableListOf<MotionEvent>()

    /** 监听器集合，用于外部订阅 motion 事件。 */
    private val listeners = mutableSetOf<MotionEventListener>()

    /** reduced-motion 覆盖值；null 表示走系统解析器。等价 `reducedOverride`。 */
    @Volatile
    private var reducedOverride: Boolean? = null

    /** 系统级 reduced-motion 解析器；attachToViewModel 时由外部注入。 */
    @Volatile
    private var reducedMotionResolver: ReducedMotionResolver? = null

    /** 延迟 settle 的协程句柄；interrupt/settle/destroy 时取消。等价 `transaction.timer`。 */
    private var settleJob: Job? = null

    /** 控制器协程作用域；destroy() 时取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 已 attach 的 ViewModel 弱引用，供 attachToViewModel 集成入口使用。 */
    private var attachedViewModel: WeakReference<AppShellViewModel>? = null

    // ------------------------------------------------------------------
    // 临时状态（transient state）—— interrupt 时清理
    // ------------------------------------------------------------------

    @Volatile private var pressed: Boolean = false
    @Volatile private var dragging: Boolean = false
    @Volatile private var dropdownPressed: Boolean = false
    @Volatile private var handleDragging: Boolean = false
    @Volatile private var dockDragging: Boolean = false

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 启动一次 motion transaction。
     *
     * 流程（motion-controller.js 1256-1297）：
     * 1. 若 active 存在，先 interrupt("superseded")
     * 2. 计算 reducedMotion
     * 3. 构造 transaction（phase=RUNNING, sequence=++counter）
     * 4. active = transaction
     * 5. dispatch START 事件
     * 6. durationMs == 0 → 立即 settle(transaction, "reduced-motion")
     *    否则 → 协程 delay(durationMs) 后 settle(transaction, "complete")
     */
    fun start(
        motionId: String,
        from: String,
        to: String,
        durationMs: Long,
        reducedMotion: Boolean
    ): MotionTransaction {
        // 步骤 1：若已有 active，先打断
        if (active != null) {
            interrupt("superseded")
        }

        // 步骤 2：reducedMotion 由调用方传入（已通过 reducedFrom 解析）。
        // P6 修复：duration 必须在 runtime 层应用 durationFor() 折叠到 0，避免依赖每个
        // 调用方都记得先调 durationFor()。契约 MOTION_EFFECTS.md §8：reduced → duration=0。
        val reduced = reducedMotion
        val effectiveDuration = durationFor(durationMs, reduced)

        // 步骤 3：构造 transaction（durationMs 记录 effective 值，便于审计/事件）
        val transaction = MotionTransaction(
            id = motionId,
            sequence = sequenceCounter.incrementAndGet(),
            phase = MotionPhase.RUNNING,
            from = from,
            to = to,
            durationMs = effectiveDuration,
            reducedMotion = reduced,
            interruptReason = null,
            startedAt = System.currentTimeMillis()
        )

        // 步骤 4：设为 active
        active = transaction

        // 步骤 5：dispatch START
        dispatch(
            MotionEventType.START,
            transaction,
            extra = buildMap {
                val contract = contractFor(motionId)
                put("finalState", contract?.finalState ?: "")
                put("unresolvedContract", contract == null)
            }
        )

        // 步骤 6：effectiveDuration == 0 立即 settle（reduced-motion 或 contract 0ms）；
        // 否则延迟 settle。
        if (effectiveDuration == 0L) {
            settle(transaction, "reduced-motion")
        } else {
            settleJob?.cancel()
            settleJob = scope.launch {
                delay(effectiveDuration)
                settle(transaction, "complete")
            }
        }

        return transaction
    }

    /**
     * 更新 active transaction 的字段（motion-controller.js 1299-1305）。
     * 当前实现：仅刷新 from/to/interruptReason 等，并 dispatch UPDATE 事件。
     * 不允许通过 update 改 sequence / phase。
     */
    fun update(patch: Map<String, Any?>) {
        val current = active ?: return
        val patched = current.copy(
            from = (patch["from"] as? String) ?: current.from,
            to = (patch["to"] as? String) ?: current.to,
            interruptReason = (patch["interruptReason"] as? String) ?: current.interruptReason
        )
        active = patched
        dispatch(MotionEventType.UPDATE, patched)
    }

    /**
     * 打断当前 active transaction（motion-controller.js 1240-1254 + MOTION_CONTRACT.md 237-239）。
     *
     * 三态语义：
     * - [InterruptKind.CANCEL]：取消正在播放的非必要动画，状态切到最新目标
     * - [InterruptKind.REDIRECT]：不倒放回起点，直接从当前视觉位置接管到新目标
     * - [InterruptKind.COMPLETE_THEN_REPLACE]：当前必要状态动画收尾后立即替换内容
     *
     * 实现步骤：
     * 1. 清 active transaction timer（取消 settleJob）
     * 2. 设 phase = INTERRUPTED
     * 3. 清临时状态（pressed/dragging/dropdown pressed/handle dragging/dock dragging）
     * 4. 触发 80ms 收尾动画（DurationInterruptSettle）——此处仅记录，实际视觉收尾由消费方执行
     * 5. dispatch INTERRUPT 事件
     * 6. active = null
     */
    fun interrupt(reason: String, kind: InterruptKind = InterruptKind.CANCEL): MotionTransaction? {
        val target = active ?: return null

        // 步骤 1：清 timer
        settleJob?.cancel()
        settleJob = null

        // 步骤 2：标记 INTERRUPTED
        val interrupted = target.copy(
            phase = MotionPhase.INTERRUPTED,
            interruptReason = reason
        )
        active = interrupted

        // 步骤 3：清临时状态
        clearTransientState()

        // 步骤 4：80ms 收尾（DurationInterruptSettle = 80ms）
        // 视觉消费方应在收到 INTERRUPT 事件后用 80ms 收尾动画；此处仅发出事件
        dispatch(
            MotionEventType.INTERRUPT,
            interrupted,
            extra = mapOf(
                "reason" to reason,
                "kind" to kind.name,
                "kindMotionId" to kind.motionId,
                "settleDurationMs" to ReaderMotionTokens.DurationInterruptSettle.inWholeMilliseconds
            )
        )

        // 步骤 6：active = null
        active = null
        return interrupted
    }

    /**
     * 收尾 transaction（motion-controller.js 1215-1238）。
     *
     * 1. 已 SETTLED 跳过
     * 2. 设 phase = SETTLED
     * 3. dispatch SETTLE 事件
     * 4. 若 active.sequence == transaction.sequence：active = null
     */
    fun settle(transaction: MotionTransaction, reason: String) {
        val target = transaction
        if (target.phase == MotionPhase.SETTLED) return

        settleJob?.cancel()
        settleJob = null

        val settled = target.copy(phase = MotionPhase.SETTLED)
        dispatch(
            MotionEventType.SETTLE,
            settled,
            extra = mapOf("reason" to reason)
        )

        if (active?.sequence == target.sequence) {
            active = null
        }
    }

    /**
     * 销毁控制器（motion-controller.js 1321-1332）。
     * 先 interrupt("destroy")，再清协程作用域内挂起的 settle 任务。
     *
     * P6: 同时清空事件日志，使单例在测试间能完全 reset 到初始状态。
     * 生产环境不依赖 destroy 后的事件历史，因此清理是安全的。
     */
    fun destroy() {
        interrupt("destroy")
        settleJob?.cancel()
        settleJob = null
        listeners.clear()
        synchronized(events) { events.clear() }
    }

    // ------------------------------------------------------------------
    // active 跟踪 / 快照
    // ------------------------------------------------------------------

    /** 返回当前 active transaction（只读视图）。 */
    fun activeTransaction(): MotionTransaction? = active

    /** 返回控制器快照（active + events 副本），等价 `getSnapshot()`。 */
    fun snapshot(): MotionSnapshot = MotionSnapshot(
        active = active,
        events = events.toList()
    )

    // ------------------------------------------------------------------
    // 事件分发
    // ------------------------------------------------------------------

    fun addListener(listener: MotionEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: MotionEventListener) {
        listeners.remove(listener)
    }

    /**
     * 派发事件（motion-controller.js 1176-1196）：
     * - 入 events 列表，超过 120 条 shift 旧的
     * - 通知所有 listener
     */
    private fun dispatch(
        type: MotionEventType,
        transaction: MotionTransaction,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val event = MotionEvent(
            type = type,
            transactionId = transaction.id,
            motionId = transaction.id,
            phase = transaction.phase,
            from = transaction.from,
            to = transaction.to,
            timestamp = System.currentTimeMillis(),
            extra = extra + mapOf(
                "sequence" to transaction.sequence,
                "reducedMotion" to transaction.reducedMotion
            )
        )
        synchronized(events) {
            events.add(event)
            while (events.size > MAX_EVENTS) {
                events.removeAt(0)
            }
        }
        listeners.forEach { runCatching { it.onMotionEvent(event) } }
    }

    // ------------------------------------------------------------------
    // 临时状态清理
    // ------------------------------------------------------------------

    /**
     * 清理临时状态并返回清理报告。interrupt() 内部调用；也可单独调用以强制清空。
     */
    fun clearTransientState(): TransientStateCleanup {
        val clearedPressed = pressed
        val clearedDragging = dragging
        val clearedDropdownPressed = dropdownPressed
        val clearedHandleDragging = handleDragging
        val clearedDockDragging = dockDragging
        pressed = false
        dragging = false
        dropdownPressed = false
        handleDragging = false
        dockDragging = false
        return TransientStateCleanup(
            clearedPressed = clearedPressed,
            clearedDragging = clearedDragging,
            clearedDropdownPressed = clearedDropdownPressed,
            clearedHandleDragging = clearedHandleDragging,
            clearedDockDragging = clearedDockDragging
        )
    }

    // ------------------------------------------------------------------
    // reducedMotion 处理（motion-controller.js 1142-1162）
    // ------------------------------------------------------------------

    /**
     * 解析当前生效的 reduced-motion（motion-controller.js 1142-1150 `reducedFrom`）：
     * - override != null → 用 override
     * - 否则查 [ReducedMotionResolver.isReducedMotion]
     * - 都没有 → false
     */
    fun reducedFrom(override: Boolean?): Boolean {
        if (override != null) return override
        return reducedMotionResolver?.isReducedMotion() ?: false
    }

    /**
     * 计算 duration（motion-controller.js 1152-1162 `durationFor`）：
     * - reduced → 0
     * - 否则用传入的 durationMs
     */
    fun durationFor(durationMs: Long, reduced: Boolean): Long {
        if (reduced) return 0L
        return durationMs.coerceAtLeast(0L)
    }

    /** 设置 reduced-motion 覆盖值（等价 `setReducedMotion`）。null 表示走系统解析器。 */
    fun setReducedMotion(value: Boolean?) {
        reducedOverride = value
    }

    // ------------------------------------------------------------------
    // Generated Motion ID contract query
    // ------------------------------------------------------------------

    /**
     * 返回指定 Motion ID 的契约（from/to/interrupt/finalState/reducedMotion + 默认 duration）。
     * 未登记的 ID 返回 null（消费方应按 fallback 处理）。
     */
    fun contractFor(motionId: String): MotionContract? = MOTION_CONTRACTS[motionId]

    // ------------------------------------------------------------------
    // 集成入口：attachToViewModel
    // ------------------------------------------------------------------

    /**
     * 将控制器 attach 到 [AppShellViewModel]，使 ViewModel 在 dispatch intent 时可调用
     * [MotionController.start]。
     *
     * 调用方式（在 AppShellViewModel.init 或 AppShell 组装处）：
     * ```
     * MotionController.attachToViewModel(this)
     * MotionController.setReducedMotionResolver(reducedMotionResolver)
     * ```
     * 随后在 dispatch 中：
     * ```
     * fun dispatch(intent: ReaderUiIntent) {
     *     MotionController.startForIntent(intent)   // 可选：映射 intent → motion
     *     _state.update { ReaderUiReducer.reduce(it, intent) }
     * }
     * ```
     */
    fun attachToViewModel(viewModel: AppShellViewModel) {
        attachedViewModel = WeakReference(viewModel)
    }

    /** 注入系统级 reduced-motion 解析器（通常在 attachToViewModel 时同步注入）。 */
    fun setReducedMotionResolver(resolver: ReducedMotionResolver?) {
        reducedMotionResolver = resolver
    }

    /** detached；用于测试 teardown。 */
    fun detachFromViewModel() {
        attachedViewModel = null
    }

    // ------------------------------------------------------------------
    // 内部常量
    // ------------------------------------------------------------------

    /** 事件日志上限（motion-controller.js 1189）。 */
    private const val MAX_EVENTS = 120

/**
 * motion-controller.js 语义契约（from/to/interrupt/finalState）。
 * 从 frontend-demo-optimized/motion-controller.js 行 300-649 提取，供 [MotionController.MOTION_CONTRACTS] 合并使用。
 */
private data class MotionControllerJsSpec(
    val from: List<String>,
    val to: List<String>,
    val interrupt: List<String>,
    val finalState: String
)

private val MOTION_CONTROLLER_JS_SPECS: Map<String, MotionControllerJsSpec> = mapOf(
        "app.launch" to MotionControllerJsSpec(
            from = listOf("coldStart", "deepLinkStart"),
            to = listOf("shellVisible", "entryRouteReady"),
            interrupt = listOf("deepLinkRedirect", "reducedMotion", "appBackgrounded"),
            finalState = "shellVisible"
        ),
        "app.route" to MotionControllerJsSpec(
            from = listOf("route.current"),
            to = listOf("route.target"),
            interrupt = listOf("newRoute", "back", "replace", "destroy"),
            finalState = "route.targetVisible"
        ),
        "button.destructive" to MotionControllerJsSpec(
            from = listOf("armed", "pressed", "confirming"),
            to = listOf("confirmed", "cancelled"),
            interrupt = listOf("cancel", "overlayDismiss", "routeChange"),
            finalState = "confirmationResolved"
        ),
        "reader.entry" to MotionControllerJsSpec(
            from = listOf("sourceRoute", "coverPressed"),
            to = listOf("immersiveReading"),
            interrupt = listOf("back", "routeChange", "snapshotUnavailable"),
            finalState = "immersiveReadingWithoutControlLayer"
        ),
        "reader.control" to MotionControllerJsSpec(
            from = listOf("controlHidden", "controlVisible", "dragging", "docked"),
            to = listOf("controlHidden", "controlVisible", "dockOffsetCommitted"),
            interrupt = listOf("back", "routeChange", "orientationPrepare", "pointerCancel"),
            finalState = "controlLayerLegalPosition"
        ),
        "reader.module" to MotionControllerJsSpec(
            from = listOf("module.active"),
            to = listOf("module.targetActive"),
            interrupt = listOf("routeChange", "switchTarget"),
            finalState = "oneActiveReaderModule"
        ),
        "reader.quick" to MotionControllerJsSpec(
            from = listOf("quickIdle", "quickPressed"),
            to = listOf("targetPanel", "loading", "committed"),
            interrupt = listOf("routeChange", "panelDismiss", "newQuickAction"),
            finalState = "quickActionResolved"
        ),
        "reader.session" to MotionControllerJsSpec(
            from = listOf("inactive", "autoPage", "tts", "capsuleVisible", "controlSpaceVisible"),
            to = listOf("autoPage", "tts", "capsuleVisible", "controlSpaceVisible", "inactive"),
            interrupt = listOf("mutualSessionSwitch", "stop", "exitReader", "orientationPrepare", "routeChange"),
            finalState = "singleSessionOwner"
        ),
        "reader.page" to MotionControllerJsSpec(
            from = listOf("page.current"),
            to = listOf("page.next", "page.previous"),
            interrupt = listOf("chapterJump", "autoPageTick", "manualTurn", "routeChange"),
            finalState = "pageIndexCommitted"
        ),
        "reader.chapter" to MotionControllerJsSpec(
            from = listOf("chapter.current"),
            to = listOf("chapter.target"),
            interrupt = listOf("newJump", "routeChange", "sessionTick"),
            finalState = "chapterAnchorCommitted"
        ),
        "reader.sourceSwitch" to MotionControllerJsSpec(
            from = listOf("readerVisible", "sourceOverlayOpen"),
            to = listOf("sourceOverlayOpen", "sourceCommitted", "readerVisible"),
            interrupt = listOf("dismiss", "routeChange", "newSource"),
            finalState = "readerSourceResolved"
        ),
        "motion.interrupt" to MotionControllerJsSpec(
            from = listOf("motionRunning", "pressed", "dragging", "loading", "overlayEntering"),
            to = listOf("latestTarget", "cancelled", "redirected", "replaced"),
            interrupt = listOf("newInterrupt", "destroy", "routeChange"),
            finalState = "latestStateOwnsSurface"
        ),
        "app.firstOpen.enter" to MotionControllerJsSpec(
            from = listOf("coldStart", "deepLinkStart"),
            to = listOf("shellVisible", "entryRouteReady"),
            interrupt = listOf("deepLinkRedirect", "resumeInsteadOfColdStart", "reducedMotion"),
            finalState = "entryRouteVisibleOnce"
        ),
        "app.route.push.forward" to MotionControllerJsSpec(
            from = listOf("route.current"),
            to = listOf("route.targetOnStack"),
            interrupt = listOf("backBeforeSettle", "replaceBeforeSettle", "newPush"),
            finalState = "targetRouteVisibleAndStackUpdated"
        ),
        "app.route.pop.backward" to MotionControllerJsSpec(
            from = listOf("route.current"),
            to = listOf("route.previousOnStack"),
            interrupt = listOf("newPushBeforeSettle", "replaceBeforeSettle", "emptyBackStack"),
            finalState = "previousRouteVisibleAndStackPopped"
        ),
        "app.route.replace" to MotionControllerJsSpec(
            from = listOf("route.current"),
            to = listOf("route.replacedTarget"),
            interrupt = listOf("newReplace", "backBeforeCommit", "sessionStartRedirect"),
            finalState = "targetRouteVisibleWithoutNewBackEntry"
        ),
        "tab.item.press" to MotionControllerJsSpec(
            from = listOf("idle"),
            to = listOf("pressed"),
            interrupt = listOf("pointerCancel", "pointerLeave", "routeChange"),
            finalState = "pressedReleased"
        ),
        "tab.item.select" to MotionControllerJsSpec(
            from = listOf("inactive"),
            to = listOf("active"),
            interrupt = listOf("switchTarget", "routeChange"),
            finalState = "selectedTabActive"
        ),
        "tab.item.switch" to MotionControllerJsSpec(
            from = listOf("activeTab.previous"),
            to = listOf("activeTab.next"),
            interrupt = listOf("switchTargetAgain", "routeChange", "pointerCancel"),
            finalState = "oneActiveTabAndStableBarSize"
        ),
        "tab.switch" to MotionControllerJsSpec(
            from = listOf("activeTab.previous"),
            to = listOf("activeTab.next"),
            interrupt = listOf("switchTargetAgain", "routeChange", "pointerCancel"),
            finalState = "oneActiveTabAndStableBarSize"
        ),
        "segment.item.switch" to MotionControllerJsSpec(
            from = listOf("segment.previous"),
            to = listOf("segment.next"),
            interrupt = listOf("switchTargetAgain", "routeChange", "pointerCancel", "stateReset"),
            finalState = "oneActiveSegmentAndStableGroupSize"
        ),
        "dropdown.trigger.press" to MotionControllerJsSpec(
            from = listOf("closed", "open"),
            to = listOf("triggerPressed"),
            interrupt = listOf("pointerCancel", "openAnotherDropdown", "routeChange"),
            finalState = "triggerReleased"
        ),
        "dropdown.menu.expand" to MotionControllerJsSpec(
            from = listOf("closed", "anchorMeasured"),
            to = listOf("open"),
            interrupt = listOf("openAnotherDropdown", "back", "routeChange", "viewportChanged"),
            finalState = "openAtLegalAnchor"
        ),
        "dropdown.menu.expand/collapse" to MotionControllerJsSpec(
            from = listOf("closed", "open"),
            to = listOf("open", "closed"),
            interrupt = listOf("openAnotherDropdown", "back", "routeChange", "viewportChanged"),
            finalState = "closedOrOpenAtLegalAnchor"
        ),
        "dropdown.menu.collapse" to MotionControllerJsSpec(
            from = listOf("open"),
            to = listOf("closed"),
            interrupt = listOf("routeChange", "openAnotherDropdown", "destroy"),
            finalState = "closedAndFocusReturnedToTrigger"
        ),
        "dropdown.menu.reposition" to MotionControllerJsSpec(
            from = listOf("openAtPreviousAnchor"),
            to = listOf("openAtLegalAnchor"),
            interrupt = listOf("collapse", "routeChange", "newViewportMetrics"),
            finalState = "openWithinViewportOrSheetFallback"
        ),
        "dropdown.option.press" to MotionControllerJsSpec(
            from = listOf("optionIdle"),
            to = listOf("optionPressed"),
            interrupt = listOf("pointerCancel", "collapse", "routeChange"),
            finalState = "optionReleased"
        ),
        "dropdown.option.select" to MotionControllerJsSpec(
            from = listOf("open", "optionPressed"),
            to = listOf("valueCommitted", "closedOrOpen"),
            interrupt = listOf("routeChange", "newSelection", "collapse"),
            finalState = "valueAndSemanticsCommitted"
        ),
        "button.activate" to MotionControllerJsSpec(
            from = listOf("pressed", "enabled"),
            to = listOf("commandCommitted", "loading", "idle"),
            interrupt = listOf("disabledBeforeRelease", "routeChange", "submitCancelled"),
            finalState = "commandStateResolved"
        ),
        "toggle.switch" to MotionControllerJsSpec(
            from = listOf("checked.previous"),
            to = listOf("checked.next"),
            interrupt = listOf("revert", "routeChange", "pointerCancel"),
            finalState = "checkedSemanticsCommitted"
        ),
        "reader.entry.coverToImmersive" to MotionControllerJsSpec(
            from = listOf("sourceRoute", "coverPressed", "coverSnapshotMeasured"),
            to = listOf("immersiveReading"),
            interrupt = listOf("snapshotUnavailable", "backBeforeCommit", "routeChange"),
            finalState = "immersiveReadingNoControlLayerAndSourceBackStackKept"
        ),
        "reader.entry.actionToImmersive" to MotionControllerJsSpec(
            from = listOf("sourceRoute", "actionPressed"),
            to = listOf("immersiveReading"),
            interrupt = listOf("backBeforeCommit", "routeChange"),
            finalState = "immersiveReadingNoControlLayerAndSourceBackStackKept"
        ),
        "reader.control.hide" to MotionControllerJsSpec(
            from = listOf("controlLayerVisible"),
            to = listOf("immersiveReading"),
            interrupt = listOf("showAgain", "routeChange", "orientationPrepare"),
            finalState = "immersiveReadingHotZonesRestored"
        ),
        "reader.control.handle.press" to MotionControllerJsSpec(
            from = listOf("handleIdle", "controlLayerVisible"),
            to = listOf("handlePressed"),
            interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare"),
            finalState = "handlePressedFeedbackVisible"
        ),
        "reader.control.handle.drag" to MotionControllerJsSpec(
            from = listOf("handlePressed"),
            to = listOf("handleDragging", "dragOffsetPreview"),
            interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare"),
            finalState = "dragOffsetPreviewOnly"
        ),
        "reader.control.handle.release" to MotionControllerJsSpec(
            from = listOf("handleDragging", "handlePressed"),
            to = listOf("snapBack", "expandCommitted", "collapseCommitted"),
            interrupt = listOf("routeChange", "orientationPrepare"),
            finalState = "controlLayerResolvedToSingleRouteState"
        ),
        "reader.control.dock.longPress" to MotionControllerJsSpec(
            from = listOf("fixedWidthDock", "handlePressed"),
            to = listOf("dockDragArmed"),
            interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare", "viewportClassChange"),
            finalState = "dockDragReadyWithinBounds"
        ),
        "reader.control.dock.drag" to MotionControllerJsSpec(
            from = listOf("dockDragArmed", "dockOffset.previous"),
            to = listOf("dockOffset.previewClamped"),
            interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare", "viewportClassChange"),
            finalState = "dockPreviewOffsetWithinMovableSpace"
        ),
        "reader.control.dock.release" to MotionControllerJsSpec(
            from = listOf("dockDragging", "dockOffset.previewClamped"),
            to = listOf("dockOffset.committed"),
            interrupt = listOf("routeChange", "orientationPrepare", "viewportClassChange"),
            finalState = "dockOffsetSavedForViewportClass"
        ),
        "reader.control.dock.rebound" to MotionControllerJsSpec(
            from = listOf("dockOffset.saved", "bounds.changed"),
            to = listOf("dockOffset.clamped"),
            interrupt = listOf("routeChange", "orientationPrepare"),
            finalState = "dockOffsetLegalInCurrentBounds"
        ),
        "reader.session.autoPage.start" to MotionControllerJsSpec(
            from = listOf("controlLayerVisible", "session.inactiveOrTts"),
            to = listOf("immersiveReading", "session.autoPage", "capsuleVisible"),
            interrupt = listOf("ttsStart", "stop", "exitReader", "routeChange"),
            finalState = "autoPageOwnsSessionAndCapsule"
        ),
        "reader.session.tts.start" to MotionControllerJsSpec(
            from = listOf("controlLayerVisible", "ttsPageVisible", "session.inactiveOrAutoPage"),
            to = listOf("immersiveReading", "session.tts", "capsuleVisible"),
            interrupt = listOf("autoPageStart", "stop", "exitReader", "routeChange"),
            finalState = "ttsOwnsSessionAndCapsule"
        ),
        "reader.session.capsule.enter" to MotionControllerJsSpec(
            from = listOf("sessionActive", "capsuleHidden"),
            to = listOf("capsuleVisible"),
            interrupt = listOf("sessionSwitch", "stop", "controlLayerOpen", "exitReader"),
            finalState = "capsuleVisibleAtReaderStatusAnchor"
        ),
        "reader.session.capsule.update" to MotionControllerJsSpec(
            from = listOf("capsuleVisible", "session.previousState"),
            to = listOf("capsuleVisible", "session.nextState"),
            interrupt = listOf("sessionSwitch", "stop", "controlLayerOpen", "exitReader"),
            finalState = "capsuleInternalStateUpdated"
        ),
        "reader.session.capsule.control.press/toggle" to MotionControllerJsSpec(
            from = listOf("capsuleVisible", "playing.previous"),
            to = listOf("capsuleVisible", "playing.next"),
            interrupt = listOf("pointerCancel", "sessionStop", "controlLayerOpen", "exitReader"),
            finalState = "playingStateCommittedInsideCapsule"
        ),
        "reader.session.capsule.control.press-toggle" to MotionControllerJsSpec(
            from = listOf("capsuleVisible", "playing.previous"),
            to = listOf("capsuleVisible", "playing.next"),
            interrupt = listOf("pointerCancel", "sessionStop", "controlLayerOpen", "exitReader"),
            finalState = "playingStateCommittedInsideCapsule"
        ),
        "reader.session.capsule.countdownTick" to MotionControllerJsSpec(
            from = listOf("countdown.previous"),
            to = listOf("countdown.next"),
            interrupt = listOf("pause", "sessionSwitch", "pageTurn", "stop"),
            finalState = "latestCountdownVisibleInFixedWidthSlot"
        ),
        "reader.session.capsule.voiceIcon.active" to MotionControllerJsSpec(
            from = listOf("ttsPlaying"),
            to = listOf("ttsPlayingVisualActive"),
            interrupt = listOf("pause", "reducedMotion", "sessionSwitch", "stop"),
            finalState = "voiceIconActiveOnlyWhilePlaying"
        ),
        "reader.session.capsule.switch" to MotionControllerJsSpec(
            from = listOf("capsuleVisible", "session.previousType"),
            to = listOf("capsuleVisible", "session.nextType"),
            interrupt = listOf("stop", "controlLayerOpen", "exitReader"),
            finalState = "singleCapsuleWithNextSessionType"
        ),
        "reader.session.capsule.exit" to MotionControllerJsSpec(
            from = listOf("capsuleVisible"),
            to = listOf("capsuleHidden"),
            interrupt = listOf("sessionRestart", "routeChange", "destroy"),
            finalState = "capsuleHiddenAndHitTargetReleased"
        ),
        "reader.session.controlSpace.enter" to MotionControllerJsSpec(
            from = listOf("capsuleVisible", "controlLayerOpening"),
            to = listOf("controlSpaceVisible"),
            interrupt = listOf("controlLayerClose", "sessionStop", "orientationPrepare"),
            finalState = "singleRunningControlOwnerInControlLayer"
        ),
        "reader.session.controlSpace.update" to MotionControllerJsSpec(
            from = listOf("controlSpaceVisible", "session.previousState"),
            to = listOf("controlSpaceVisible", "session.nextState"),
            interrupt = listOf("sessionStop", "controlLayerClose", "orientationPrepare"),
            finalState = "controlSpaceInternalStateUpdated"
        ),
        "reader.session.controlSpace.exit" to MotionControllerJsSpec(
            from = listOf("controlSpaceVisible", "controlLayerClosing"),
            to = listOf("capsuleVisible", "immersiveReading"),
            interrupt = listOf("sessionStop", "exitReader", "orientationPrepare"),
            finalState = "singleCapsuleOwnerInImmersiveReading"
        ),
        "reader.module.switch" to MotionControllerJsSpec(
            from = listOf("readerModule.previous", "controlLayerVisible"),
            to = listOf("readerModule.next", "controlLayerVisible"),
            interrupt = listOf("routeChange", "switchTargetAgain", "hideControlLayer"),
            finalState = "oneActiveReaderModuleAndStableModuleBar"
        ),
        "reader.page.turn.next/prev" to MotionControllerJsSpec(
            from = listOf("page.current"),
            to = listOf("page.nextOrPrevious"),
            interrupt = listOf("oppositeTurn", "chapterJump", "routeChange", "sessionTick"),
            finalState = "pageIndexCommittedAndPageInfoAnchored"
        ),
        "reader.page.turn.next-prev" to MotionControllerJsSpec(
            from = listOf("page.current"),
            to = listOf("page.nextOrPrevious"),
            interrupt = listOf("oppositeTurn", "chapterJump", "routeChange", "sessionTick"),
            finalState = "pageIndexCommittedAndPageInfoAnchored"
        ),
        "motion.interrupt.cancel" to MotionControllerJsSpec(
            from = listOf("motionRunning", "pressed", "dragging", "entering"),
            to = listOf("latestCommittedState"),
            interrupt = listOf("newInterrupt", "destroy"),
            finalState = "transientMotionCleared"
        ),
        "motion.interrupt.redirect" to MotionControllerJsSpec(
            from = listOf("motionRunningTowardOldTarget"),
            to = listOf("motionRunningTowardNewTarget"),
            interrupt = listOf("newTarget", "routeChange", "destroy"),
            finalState = "newTargetOwnsMotion"
        ),
        "motion.interrupt.completeThenReplace" to MotionControllerJsSpec(
            from = listOf("requiredStateMotion", "loadingMinimumVisible"),
            to = listOf("replacementState"),
            interrupt = listOf("userBack", "routeChange", "newerAsyncResult"),
            finalState = "replacementVisibleOnlyIfStillCurrent"
        ),
        "viewport.orientation.prepare" to MotionControllerJsSpec(
            from = listOf("viewportStable"),
            to = listOf("viewportFrozen"),
            interrupt = listOf("routeChange", "newMetricsBeforeFreeze"),
            finalState = "routeReaderSessionOverlayFocusFrozen"
        ),
        "viewport.orientation.reshape" to MotionControllerJsSpec(
            from = listOf("viewportFrozen", "viewportStable"),
            to = listOf("viewportReshaped"),
            interrupt = listOf("newMetrics", "foldChange", "routeChange"),
            finalState = "readerOverlayCapsuleDockReanchored"
        ),
        "viewport.orientation.settle" to MotionControllerJsSpec(
            from = listOf("viewportReshaped"),
            to = listOf("viewportStable"),
            interrupt = listOf("newMetrics", "routeChange"),
            finalState = "focusPointerSessionMicroMotionRestored"
        )
)

    /**
     * Motion ID 契约表以生成的 [MotionSpecRegistry.all] 为唯一 canonical 数据源，
     * 并仅在生成项尚未声明精确状态时回退到本地 JS 兼容语义表。
     *
     * 仅存在于旧 JS 语义表、但不属于生成枚举的高层别名继续补入查询表；所有
     * [MotionIdConstants] 则直接委托给生成枚举的 serializer wire name。
     *
     * Contract source: generated/kotlin/Motion.kt MotionSpecRegistry.all +
     *                  motion-controller.js 语义表与本地遗留别名。
     */
    private val MOTION_CONTRACTS: Map<String, MotionContract> = buildMap {
        MotionSpecRegistry.all.forEach { spec ->
            val serialName = spec.id.serialName
            val jsSpec = MOTION_CONTROLLER_JS_SPECS[serialName]
            put(
                serialName,
                MotionContract(
                    motionId = serialName,
                    from = spec.from ?: jsSpec?.from ?: buildList {
                        spec.operation?.let { add(it.name) }
                        spec.containerRole?.let { add(it.name) }
                        if (isEmpty()) add("idle")
                    },
                    to = spec.to ?: jsSpec?.to ?: buildList {
                        spec.visualPattern?.let { add(it.name) }
                        add(spec.implementationKind.name)
                    },
                    interrupt = spec.interrupt ?: jsSpec?.interrupt ?: listOf(spec.interruptPolicy.name),
                    finalState = spec.finalState ?: jsSpec?.finalState ?: spec.id.name,
                    reducedMotion = spec.reducedMotionPolicy.name,
                    defaultDurationMs = spec.durationMs.toLong()
                )
            )
        }
        // 旧 JS 语义表中的高层别名不属于 MotionId enum，仍保留查询兼容性。
        MOTION_CONTROLLER_JS_SPECS.forEach { (serialName, jsSpec) ->
            if (!containsKey(serialName)) {
                put(
                    serialName,
                    MotionContract(
                        motionId = serialName,
                        from = jsSpec.from,
                        to = jsSpec.to,
                        interrupt = jsSpec.interrupt,
                        finalState = jsSpec.finalState,
                        reducedMotion = "reducedMotion",
                        defaultDurationMs = 0L
                    )
                )
            }
        }
        // Generated keeps both tab.item.switch and tab.switch. Preserve the established Android
        // semantic contract for tab.item.switch (including durationMs=160).
        this["tab.switch"]?.let { tabSwitchContract ->
            put("tab.item.switch", tabSwitchContract.copy(motionId = "tab.item.switch"))
        }
    }
}

// ----------------------------------------------------------------------
// 数据类 / 枚举 / 接口
// ----------------------------------------------------------------------

/** Motion transaction 阶段。 */
enum class MotionPhase { RUNNING, INTERRUPTED, SETTLED }

/** Motion 事件类型。 */
enum class MotionEventType { START, UPDATE, INTERRUPT, SETTLE }

/**
 * 单次 motion transaction 记录（motion-controller.js transaction object 的 Kotlin 等价物）。
 */
data class MotionTransaction(
    val id: String,                 // Motion ID（如 "tab.item.switch"）
    val sequence: Long,             // 递增序号
    val phase: MotionPhase,         // RUNNING / INTERRUPTED / SETTLED
    val from: String,               // from state
    val to: String,                 // to state
    val durationMs: Long,           // duration
    val reducedMotion: Boolean,
    val interruptReason: String? = null,
    val startedAt: Long = System.currentTimeMillis()
)

/**
 * Motion 事件（motion-controller.js 1176-1196 dispatch 产物）。
 */
data class MotionEvent(
    val type: MotionEventType,      // START / UPDATE / INTERRUPT / SETTLE
    val transactionId: String,
    val motionId: String,
    val phase: MotionPhase,
    val from: String,
    val to: String,
    val timestamp: Long,
    val extra: Map<String, Any?> = emptyMap()
)

/** 事件监听器接口。 */
fun interface MotionEventListener {
    fun onMotionEvent(event: MotionEvent)
}

/**
 * Motion 契约（motion-controller.js 292-622 状态机表的 Kotlin 等价物）。
 */
data class MotionContract(
    val motionId: String,
    val from: List<String>,
    val to: List<String>,
    val interrupt: List<String>,
    val finalState: String,
    val reducedMotion: String,
    val defaultDurationMs: Long
)

/** 控制器快照（等价 `getSnapshot()`）。 */
data class MotionSnapshot(
    val active: MotionTransaction?,
    val events: List<MotionEvent>
)

/**
 * 临时状态清理报告。interrupt() 调用 [MotionController.clearTransientState] 后返回。
 */
data class TransientStateCleanup(
    val clearedPressed: Boolean,
    val clearedDragging: Boolean,
    val clearedDropdownPressed: Boolean,
    val clearedHandleDragging: Boolean,
    val clearedDockDragging: Boolean
)

/**
 * Local constant-name compatibility layer. Every wire value delegates to generated [MotionId]
 * serializer metadata; Android no longer keeps a second literal string table.
 */
object MotionIdConstants {
    // App launch / route
    val APP_FIRST_OPEN_ENTER: String get() = MotionId.AppFirstOpenEnter.serialName
    val APP_ROUTE_PUSH_FORWARD: String get() = MotionId.AppRoutePushForward.serialName
    val APP_ROUTE_POP_BACKWARD: String get() = MotionId.AppRoutePopBackward.serialName
    val APP_ROUTE_REPLACE: String get() = MotionId.AppRouteReplace.serialName

    // Tab / segment
    val TAB_ITEM_PRESS: String get() = MotionId.TabItemPress.serialName
    val TAB_ITEM_SELECT: String get() = MotionId.TabItemSelect.serialName
    val TAB_ITEM_SWITCH: String get() = MotionId.TabItemSwitch.serialName
    val SEGMENT_ITEM_SWITCH: String get() = MotionId.SegmentItemSwitch.serialName

    // Dropdown
    val DROPDOWN_TRIGGER_PRESS: String get() = MotionId.DropdownTriggerPress.serialName
    val DROPDOWN_MENU_EXPAND: String get() = MotionId.DropdownMenuExpand.serialName
    val DROPDOWN_MENU_COLLAPSE: String get() = MotionId.DropdownMenuCollapse.serialName
    val DROPDOWN_MENU_REPOSITION: String get() = MotionId.DropdownMenuReposition.serialName
    val DROPDOWN_OPTION_PRESS: String get() = MotionId.DropdownOptionPress.serialName
    val DROPDOWN_OPTION_SELECT: String get() = MotionId.DropdownOptionSelect.serialName

    // Overlay (keyboard / sheet / dialog)
    val OVERLAY_KEYBOARD_ENTER_EXIT: String get() = MotionId.OverlayKeyboardEnterExit.serialName
    val OVERLAY_SHEET_ENTER: String get() = MotionId.OverlaySheetEnter.serialName
    val OVERLAY_SHEET_EXIT: String get() = MotionId.OverlaySheetExit.serialName
    val OVERLAY_DIALOG_ENTER: String get() = MotionId.OverlayDialogEnter.serialName
    val OVERLAY_DIALOG_EXIT: String get() = MotionId.OverlayDialogExit.serialName

    // Button / toggle
    val BUTTON_ACTIVATE: String get() = MotionId.ButtonActivate.serialName
    val TOGGLE_SWITCH: String get() = MotionId.ToggleSwitch.serialName

    // Reader entry / control
    val READER_ENTRY_COVER_TO_IMMERSIVE: String get() = MotionId.ReaderEntryCoverToImmersive.serialName
    val READER_ENTRY_ACTION_TO_IMMERSIVE: String get() = MotionId.ReaderEntryActionToImmersive.serialName
    val READER_CONTROL_SHOW: String get() = MotionId.ReaderControlShow.serialName
    val READER_CONTROL_HIDE: String get() = MotionId.ReaderControlHide.serialName
    val READER_CONTROL_HANDLE_PRESS: String get() = MotionId.ReaderControlHandlePress.serialName
    val READER_CONTROL_HANDLE_DRAG: String get() = MotionId.ReaderControlHandleDrag.serialName
    val READER_CONTROL_HANDLE_RELEASE: String get() = MotionId.ReaderControlHandleRelease.serialName
    val READER_CONTROL_DOCK_LONG_PRESS: String get() = MotionId.ReaderControlDockLongPress.serialName
    val READER_CONTROL_DOCK_DRAG: String get() = MotionId.ReaderControlDockDrag.serialName
    val READER_CONTROL_DOCK_RELEASE: String get() = MotionId.ReaderControlDockRelease.serialName
    val READER_CONTROL_DOCK_REBOUND: String get() = MotionId.ReaderControlDockRebound.serialName

    // Reader session
    val READER_SESSION_AUTO_PAGE_START: String get() = MotionId.ReaderSessionAutoPageStart.serialName
    val READER_SESSION_TTS_START: String get() = MotionId.ReaderSessionTtsStart.serialName
    val READER_SESSION_CAPSULE_ENTER: String get() = MotionId.ReaderSessionCapsuleEnter.serialName
    val READER_SESSION_CAPSULE_UPDATE: String get() = MotionId.ReaderSessionCapsuleUpdate.serialName
    val READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE: String get() = MotionId.ReaderSessionCapsuleControlPressToggle.serialName
    val READER_SESSION_CAPSULE_COUNTDOWN_TICK: String get() = MotionId.ReaderSessionCapsuleCountdownTick.serialName
    val READER_SESSION_CAPSULE_VOICE_ICON_ACTIVE: String get() = MotionId.ReaderSessionCapsuleVoiceIconActive.serialName
    val READER_SESSION_CAPSULE_SWITCH: String get() = MotionId.ReaderSessionCapsuleSwitch.serialName
    val READER_SESSION_CAPSULE_EXIT: String get() = MotionId.ReaderSessionCapsuleExit.serialName
    val READER_SESSION_CONTROL_SPACE_ENTER: String get() = MotionId.ReaderSessionControlSpaceEnter.serialName
    val READER_SESSION_CONTROL_SPACE_UPDATE: String get() = MotionId.ReaderSessionControlSpaceUpdate.serialName
    val READER_SESSION_CONTROL_SPACE_EXIT: String get() = MotionId.ReaderSessionControlSpaceExit.serialName

    // Reader module / page
    val READER_MODULE_SWITCH: String get() = MotionId.ReaderModuleSwitch.serialName
    val READER_PAGE_TURN_NEXT_PREV: String get() = MotionId.ReaderPageTurnNextPrev.serialName

    // Motion interrupt 三态
    val MOTION_INTERRUPT_CANCEL: String get() = MotionId.MotionInterruptCancel.serialName
    val MOTION_INTERRUPT_REDIRECT: String get() = MotionId.MotionInterruptRedirect.serialName
    val MOTION_INTERRUPT_COMPLETE_THEN_REPLACE: String get() = MotionId.MotionInterruptCompleteThenReplace.serialName

    // Viewport orientation
    val VIEWPORT_ORIENTATION_PREPARE: String get() = MotionId.ViewportOrientationPrepare.serialName
    val VIEWPORT_ORIENTATION_RESHAPE: String get() = MotionId.ViewportOrientationReshape.serialName
    val VIEWPORT_ORIENTATION_SETTLE: String get() = MotionId.ViewportOrientationSettle.serialName

    // Selection / slider
    val SELECTION_RANGE_SHOW: String get() = MotionId.SelectionRangeShow.serialName
    val SLIDER_DRAG_START: String get() = MotionId.SliderDragStart.serialName
    val SLIDER_DRAG_RELEASE: String get() = MotionId.SliderDragRelease.serialName
}
