package com.reader.ui.motion

import com.reader.ui.shell.AppShellViewModel
import com.reader.ui.shell.InterruptKind
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
    // 47 Motion ID 契约查询
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
     * 47 个 Motion ID 的契约表（motion-controller.js 292-622）。
     * P0 子集完整定义；其余同样给出 from/to/interrupt/finalState/reducedMotion。
     */
    private val MOTION_CONTRACTS: Map<String, MotionContract> = buildMap {
        // ---- P0：App launch / route ----
        put(
            "app.firstOpen.enter",
            MotionContract(
                motionId = "app.firstOpen.enter",
                from = listOf("coldStart", "deepLinkStart"),
                to = listOf("shellVisible", "entryRouteReady"),
                interrupt = listOf("deepLinkRedirect", "resumeInsteadOfColdStart", "reducedMotion"),
                finalState = "entryRouteVisibleOnce",
                reducedMotion = "Render shell and entry route immediately; do not replay on route, tab, or back actions.",
                defaultDurationMs = 280L
            )
        )
        put(
            "app.route.push.forward",
            MotionContract(
                motionId = "app.route.push.forward",
                from = listOf("route.current"),
                to = listOf("route.targetOnStack"),
                interrupt = listOf("backBeforeSettle", "replaceBeforeSettle", "newPush"),
                finalState = "targetRouteVisibleAndStackUpdated",
                reducedMotion = "Update stack and content immediately without forward slide.",
                defaultDurationMs = 160L
            )
        )
        put(
            "app.route.pop.backward",
            MotionContract(
                motionId = "app.route.pop.backward",
                from = listOf("route.current"),
                to = listOf("route.previousOnStack"),
                interrupt = listOf("newPushBeforeSettle", "replaceBeforeSettle", "emptyBackStack"),
                finalState = "previousRouteVisibleAndStackPopped",
                reducedMotion = "Pop stack and render previous route immediately without backward slide.",
                defaultDurationMs = 160L
            )
        )
        put(
            "app.route.replace",
            MotionContract(
                motionId = "app.route.replace",
                from = listOf("route.current"),
                to = listOf("route.replacedTarget"),
                interrupt = listOf("newReplace", "backBeforeCommit", "sessionStartRedirect"),
                finalState = "targetRouteVisibleWithoutNewBackEntry",
                reducedMotion = "Replace route state in place with no push/pop movement.",
                defaultDurationMs = 160L
            )
        )

        // ---- Tab / segment ----
        put(
            "tab.item.press",
            MotionContract(
                motionId = "tab.item.press",
                from = listOf("idle"),
                to = listOf("pressed"),
                interrupt = listOf("pointerCancel", "pointerLeave", "routeChange"),
                finalState = "pressedReleased",
                reducedMotion = "Keep pressed feedback instant and do not move tab layout.",
                defaultDurationMs = 80L
            )
        )
        put(
            "tab.item.select",
            MotionContract(
                motionId = "tab.item.select",
                from = listOf("inactive"),
                to = listOf("active"),
                interrupt = listOf("switchTarget", "routeChange"),
                finalState = "selectedTabActive",
                reducedMotion = "Commit selected color/icon/text state without background travel.",
                defaultDurationMs = 120L
            )
        )
        put(
            "tab.item.switch",
            MotionContract(
                motionId = "tab.item.switch",
                from = listOf("activeTab.previous"),
                to = listOf("activeTab.next"),
                interrupt = listOf("switchTargetAgain", "routeChange", "pointerCancel"),
                finalState = "oneActiveTabAndStableBarSize",
                reducedMotion = "Switch active state instantly and keep indicator static.",
                defaultDurationMs = 160L
            )
        )
        put(
            "segment.item.switch",
            MotionContract(
                motionId = "segment.item.switch",
                from = listOf("segment.previous"),
                to = listOf("segment.next"),
                interrupt = listOf("switchTargetAgain", "routeChange", "pointerCancel", "stateReset"),
                finalState = "oneActiveSegmentAndStableGroupSize",
                reducedMotion = "Commit selected segment state immediately without indicator travel or layout movement.",
                defaultDurationMs = 120L
            )
        )

        // ---- Dropdown ----
        put(
            "dropdown.trigger.press",
            MotionContract(
                motionId = "dropdown.trigger.press",
                from = listOf("closed", "open"),
                to = listOf("triggerPressed"),
                interrupt = listOf("pointerCancel", "openAnotherDropdown", "routeChange"),
                finalState = "triggerReleased",
                reducedMotion = "Apply trigger pressed state instantly without chevron travel.",
                defaultDurationMs = 80L
            )
        )
        put(
            "dropdown.menu.expand",
            MotionContract(
                motionId = "dropdown.menu.expand",
                from = listOf("closed", "anchorMeasured"),
                to = listOf("open"),
                interrupt = listOf("openAnotherDropdown", "back", "routeChange", "viewportChanged"),
                finalState = "openAtLegalAnchor",
                reducedMotion = "Measure anchor, then show menu immediately without fade or y-offset.",
                defaultDurationMs = 160L
            )
        )
        put(
            "dropdown.menu.expand/collapse",
            MotionContract(
                motionId = "dropdown.menu.expand/collapse",
                from = listOf("closed", "open"),
                to = listOf("open", "closed"),
                interrupt = listOf("openAnotherDropdown", "back", "routeChange", "viewportChanged"),
                finalState = "closedOrOpenAtLegalAnchor",
                reducedMotion = "Commit final open/closed state immediately after anchor measurement.",
                defaultDurationMs = 160L
            )
        )
        put(
            "dropdown.menu.collapse",
            MotionContract(
                motionId = "dropdown.menu.collapse",
                from = listOf("open"),
                to = listOf("closed"),
                interrupt = listOf("routeChange", "openAnotherDropdown", "destroy"),
                finalState = "closedAndFocusReturnedToTrigger",
                reducedMotion = "Hide menu and release focus/click target immediately.",
                defaultDurationMs = 160L
            )
        )
        put(
            "dropdown.menu.reposition",
            MotionContract(
                motionId = "dropdown.menu.reposition",
                from = listOf("openAtPreviousAnchor"),
                to = listOf("openAtLegalAnchor"),
                interrupt = listOf("collapse", "routeChange", "newViewportMetrics"),
                finalState = "openWithinViewportOrSheetFallback",
                reducedMotion = "Recompute placement and snap to legal bounds without animated travel.",
                defaultDurationMs = 120L
            )
        )
        put(
            "dropdown.option.press",
            MotionContract(
                motionId = "dropdown.option.press",
                from = listOf("optionIdle"),
                to = listOf("optionPressed"),
                interrupt = listOf("pointerCancel", "collapse", "routeChange"),
                finalState = "optionReleased",
                reducedMotion = "Apply option pressed state instantly without moving menu container.",
                defaultDurationMs = 80L
            )
        )
        put(
            "dropdown.option.select",
            MotionContract(
                motionId = "dropdown.option.select",
                from = listOf("open", "optionPressed"),
                to = listOf("valueCommitted", "closedOrOpen"),
                interrupt = listOf("routeChange", "newSelection", "collapse"),
                finalState = "valueAndSemanticsCommitted",
                reducedMotion = "Update value, check/icon, and close single-select menus immediately.",
                defaultDurationMs = 120L
            )
        )

        // ---- Button / toggle ----
        put(
            "button.activate",
            MotionContract(
                motionId = "button.activate",
                from = listOf("pressed", "enabled"),
                to = listOf("commandCommitted", "loading", "idle"),
                interrupt = listOf("disabledBeforeRelease", "routeChange", "submitCancelled"),
                finalState = "commandStateResolved",
                reducedMotion = "Commit button command state without scale or label crossfade.",
                defaultDurationMs = 80L
            )
        )
        put(
            "toggle.switch",
            MotionContract(
                motionId = "toggle.switch",
                from = listOf("checked.previous"),
                to = listOf("checked.next"),
                interrupt = listOf("revert", "routeChange", "pointerCancel"),
                finalState = "checkedSemanticsCommitted",
                reducedMotion = "Update check/thumb/background and semantics instantly.",
                defaultDurationMs = 120L
            )
        )

        // ---- P0：Reader entry / control ----
        put(
            "reader.entry.coverToImmersive",
            MotionContract(
                motionId = "reader.entry.coverToImmersive",
                from = listOf("sourceRoute", "coverPressed", "coverSnapshotMeasured"),
                to = listOf("immersiveReading"),
                interrupt = listOf("snapshotUnavailable", "backBeforeCommit", "routeChange"),
                finalState = "immersiveReadingNoControlLayerAndSourceBackStackKept",
                reducedMotion = "Use cover press and reader surface reveal; skip shared-element movement.",
                defaultDurationMs = 240L
            )
        )
        put(
            "reader.entry.actionToImmersive",
            MotionContract(
                motionId = "reader.entry.actionToImmersive",
                from = listOf("sourceRoute", "actionPressed"),
                to = listOf("immersiveReading"),
                interrupt = listOf("backBeforeCommit", "routeChange"),
                finalState = "immersiveReadingNoControlLayerAndSourceBackStackKept",
                reducedMotion = "Use action press plus immediate reader surface reveal.",
                defaultDurationMs = 240L
            )
        )
        put(
            "reader.control.hide",
            MotionContract(
                motionId = "reader.control.hide",
                from = listOf("controlLayerVisible"),
                to = listOf("immersiveReading"),
                interrupt = listOf("showAgain", "routeChange", "orientationPrepare"),
                finalState = "immersiveReadingHotZonesRestored",
                reducedMotion = "Hide control layer immediately and restore immersive hit regions.",
                defaultDurationMs = 160L
            )
        )

        // ---- P0：Reader control handle ----
        put(
            "reader.control.handle.press",
            MotionContract(
                motionId = "reader.control.handle.press",
                from = listOf("handleIdle", "controlLayerVisible"),
                to = listOf("handlePressed"),
                interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare"),
                finalState = "handlePressedFeedbackVisible",
                reducedMotion = "Commit pressed state without scale or pull preview.",
                defaultDurationMs = 80L
            )
        )
        put(
            "reader.control.handle.drag",
            MotionContract(
                motionId = "reader.control.handle.drag",
                from = listOf("handlePressed"),
                to = listOf("handleDragging", "dragOffsetPreview"),
                interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare"),
                finalState = "dragOffsetPreviewOnly",
                reducedMotion = "Track drag semantics without panel translation.",
                defaultDurationMs = 0L
            )
        )
        put(
            "reader.control.handle.release",
            MotionContract(
                motionId = "reader.control.handle.release",
                from = listOf("handleDragging", "handlePressed"),
                to = listOf("snapBack", "expandCommitted", "collapseCommitted"),
                interrupt = listOf("routeChange", "orientationPrepare"),
                finalState = "controlLayerResolvedToSingleRouteState",
                reducedMotion = "Resolve expand, collapse, or snap-back immediately without panel travel.",
                defaultDurationMs = 160L
            )
        )

        // ---- Reader control dock ----
        put(
            "reader.control.dock.longPress",
            MotionContract(
                motionId = "reader.control.dock.longPress",
                from = listOf("fixedWidthDock", "handlePressed"),
                to = listOf("dockDragArmed"),
                interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare", "viewportClassChange"),
                finalState = "dockDragReadyWithinBounds",
                reducedMotion = "Arm dock movement without scale or halo animation.",
                defaultDurationMs = 80L
            )
        )
        put(
            "reader.control.dock.drag",
            MotionContract(
                motionId = "reader.control.dock.drag",
                from = listOf("dockDragArmed", "dockOffset.previous"),
                to = listOf("dockOffset.previewClamped"),
                interrupt = listOf("pointerCancel", "routeChange", "orientationPrepare", "viewportClassChange"),
                finalState = "dockPreviewOffsetWithinMovableSpace",
                reducedMotion = "Update clamped dock offset directly while keeping dock dimensions fixed.",
                defaultDurationMs = 0L
            )
        )
        put(
            "reader.control.dock.release",
            MotionContract(
                motionId = "reader.control.dock.release",
                from = listOf("dockDragging", "dockOffset.previewClamped"),
                to = listOf("dockOffset.committed"),
                interrupt = listOf("routeChange", "orientationPrepare", "viewportClassChange"),
                finalState = "dockOffsetSavedForViewportClass",
                reducedMotion = "Commit the legal dock offset immediately without snap movement.",
                defaultDurationMs = 160L
            )
        )
        put(
            "reader.control.dock.rebound",
            MotionContract(
                motionId = "reader.control.dock.rebound",
                from = listOf("dockOffset.saved", "bounds.changed"),
                to = listOf("dockOffset.clamped"),
                interrupt = listOf("routeChange", "orientationPrepare"),
                finalState = "dockOffsetLegalInCurrentBounds",
                reducedMotion = "Clamp dock offset to the current movable space immediately.",
                defaultDurationMs = 120L
            )
        )

        // ---- Reader session ----
        put(
            "reader.session.autoPage.start",
            MotionContract(
                motionId = "reader.session.autoPage.start",
                from = listOf("controlLayerVisible", "session.inactiveOrTts"),
                to = listOf("immersiveReading", "session.autoPage", "capsuleVisible"),
                interrupt = listOf("ttsStart", "stop", "exitReader", "routeChange"),
                finalState = "autoPageOwnsSessionAndCapsule",
                reducedMotion = "Set autoPage session, replace route, and show capsule immediately.",
                defaultDurationMs = 200L
            )
        )
        put(
            "reader.session.tts.start",
            MotionContract(
                motionId = "reader.session.tts.start",
                from = listOf("controlLayerVisible", "ttsPageVisible", "session.inactiveOrAutoPage"),
                to = listOf("immersiveReading", "session.tts", "capsuleVisible"),
                interrupt = listOf("autoPageStart", "stop", "exitReader", "routeChange"),
                finalState = "ttsOwnsSessionAndCapsule",
                reducedMotion = "Set TTS session, replace route, and show capsule immediately.",
                defaultDurationMs = 200L
            )
        )

        // ---- P0：Reader session capsule ----
        put(
            "reader.session.capsule.enter",
            MotionContract(
                motionId = "reader.session.capsule.enter",
                from = listOf("sessionActive", "capsuleHidden"),
                to = listOf("capsuleVisible"),
                interrupt = listOf("sessionSwitch", "stop", "controlLayerOpen", "exitReader"),
                finalState = "capsuleVisibleAtReaderStatusAnchor",
                reducedMotion = "Show capsule at anchor immediately without container scale or y-offset.",
                defaultDurationMs = 200L
            )
        )
        put(
            "reader.session.capsule.update",
            MotionContract(
                motionId = "reader.session.capsule.update",
                from = listOf("capsuleVisible", "session.previousState"),
                to = listOf("capsuleVisible", "session.nextState"),
                interrupt = listOf("sessionSwitch", "stop", "controlLayerOpen", "exitReader"),
                finalState = "capsuleInternalStateUpdated",
                reducedMotion = "Update internal icon, text, and count without replaying capsule enter.",
                defaultDurationMs = 120L
            )
        )
        put(
            "reader.session.capsule.control.press/toggle",
            MotionContract(
                motionId = "reader.session.capsule.control.press/toggle",
                from = listOf("capsuleVisible", "playing.previous"),
                to = listOf("capsuleVisible", "playing.next"),
                interrupt = listOf("pointerCancel", "sessionStop", "controlLayerOpen", "exitReader"),
                finalState = "playingStateCommittedInsideCapsule",
                reducedMotion = "Commit play/pause icon and state instantly; do not open control layer.",
                defaultDurationMs = 80L
            )
        )
        put(
            "reader.session.capsule.countdownTick",
            MotionContract(
                motionId = "reader.session.capsule.countdownTick",
                from = listOf("countdown.previous"),
                to = listOf("countdown.next"),
                interrupt = listOf("pause", "sessionSwitch", "pageTurn", "stop"),
                finalState = "latestCountdownVisibleInFixedWidthSlot",
                reducedMotion = "Replace number immediately in the fixed-width slot.",
                defaultDurationMs = 80L
            )
        )
        put(
            "reader.session.capsule.voiceIcon.active",
            MotionContract(
                motionId = "reader.session.capsule.voiceIcon.active",
                from = listOf("ttsPlaying"),
                to = listOf("ttsPlayingVisualActive"),
                interrupt = listOf("pause", "reducedMotion", "sessionSwitch", "stop"),
                finalState = "voiceIconActiveOnlyWhilePlaying",
                reducedMotion = "Keep voice icon static while preserving playing semantics.",
                defaultDurationMs = 80L
            )
        )
        put(
            "reader.session.capsule.switch",
            MotionContract(
                motionId = "reader.session.capsule.switch",
                from = listOf("capsuleVisible", "session.previousType"),
                to = listOf("capsuleVisible", "session.nextType"),
                interrupt = listOf("stop", "controlLayerOpen", "exitReader"),
                finalState = "singleCapsuleWithNextSessionType",
                reducedMotion = "Swap capsule internal content immediately at the same anchor.",
                defaultDurationMs = 160L
            )
        )
        put(
            "reader.session.capsule.exit",
            MotionContract(
                motionId = "reader.session.capsule.exit",
                from = listOf("capsuleVisible"),
                to = listOf("capsuleHidden"),
                interrupt = listOf("sessionRestart", "routeChange", "destroy"),
                finalState = "capsuleHiddenAndHitTargetReleased",
                reducedMotion = "Hide capsule and release hit target immediately.",
                defaultDurationMs = 160L
            )
        )

        // ---- P0：Reader session controlSpace ----
        put(
            "reader.session.controlSpace.enter",
            MotionContract(
                motionId = "reader.session.controlSpace.enter",
                from = listOf("capsuleVisible", "controlLayerOpening"),
                to = listOf("controlSpaceVisible"),
                interrupt = listOf("controlLayerClose", "sessionStop", "orientationPrepare"),
                finalState = "singleRunningControlOwnerInControlLayer",
                reducedMotion = "Hide capsule and show running control space without morph.",
                defaultDurationMs = 200L
            )
        )
        put(
            "reader.session.controlSpace.update",
            MotionContract(
                motionId = "reader.session.controlSpace.update",
                from = listOf("controlSpaceVisible", "session.previousState"),
                to = listOf("controlSpaceVisible", "session.nextState"),
                interrupt = listOf("sessionStop", "controlLayerClose", "orientationPrepare"),
                finalState = "controlSpaceInternalStateUpdated",
                reducedMotion = "Update internal running state instantly.",
                defaultDurationMs = 120L
            )
        )
        put(
            "reader.session.controlSpace.exit",
            MotionContract(
                motionId = "reader.session.controlSpace.exit",
                from = listOf("controlSpaceVisible", "controlLayerClosing"),
                to = listOf("capsuleVisible", "immersiveReading"),
                interrupt = listOf("sessionStop", "exitReader", "orientationPrepare"),
                finalState = "singleCapsuleOwnerInImmersiveReading",
                reducedMotion = "Hide running control space and show capsule without morph.",
                defaultDurationMs = 200L
            )
        )

        // ---- Reader module / page ----
        put(
            "reader.module.switch",
            MotionContract(
                motionId = "reader.module.switch",
                from = listOf("readerModule.previous", "controlLayerVisible"),
                to = listOf("readerModule.next", "controlLayerVisible"),
                interrupt = listOf("routeChange", "switchTargetAgain", "hideControlLayer"),
                finalState = "oneActiveReaderModuleAndStableModuleBar",
                reducedMotion = "Commit active module and panel content immediately; keep module nav dimensions stable.",
                defaultDurationMs = 160L
            )
        )
        put(
            "reader.page.turn.next/prev",
            MotionContract(
                motionId = "reader.page.turn.next/prev",
                from = listOf("page.current"),
                to = listOf("page.nextOrPrevious"),
                interrupt = listOf("oppositeTurn", "chapterJump", "routeChange", "sessionTick"),
                finalState = "pageIndexCommittedAndPageInfoAnchored",
                reducedMotion = "Commit page index and footer/page info immediately without slide.",
                defaultDurationMs = 160L
            )
        )

        // ---- P0：Motion interrupt 三态 ----
        put(
            "motion.interrupt.cancel",
            MotionContract(
                motionId = "motion.interrupt.cancel",
                from = listOf("motionRunning", "pressed", "dragging", "entering"),
                to = listOf("latestCommittedState"),
                interrupt = listOf("newInterrupt", "destroy"),
                finalState = "transientMotionCleared",
                reducedMotion = "Clear transient motion flags immediately.",
                defaultDurationMs = ReaderMotionTokens.DurationInterruptSettle.inWholeMilliseconds
            )
        )
        put(
            "motion.interrupt.redirect",
            MotionContract(
                motionId = "motion.interrupt.redirect",
                from = listOf("motionRunningTowardOldTarget"),
                to = listOf("motionRunningTowardNewTarget"),
                interrupt = listOf("newTarget", "routeChange", "destroy"),
                finalState = "newTargetOwnsMotion",
                reducedMotion = "Cancel old target and commit new target without interpolation.",
                defaultDurationMs = ReaderMotionTokens.DurationInterruptSettle.inWholeMilliseconds
            )
        )
        put(
            "motion.interrupt.completeThenReplace",
            MotionContract(
                motionId = "motion.interrupt.completeThenReplace",
                from = listOf("requiredStateMotion", "loadingMinimumVisible"),
                to = listOf("replacementState"),
                interrupt = listOf("userBack", "routeChange", "newerAsyncResult"),
                finalState = "replacementVisibleOnlyIfStillCurrent",
                reducedMotion = "Replace with the latest valid state immediately.",
                defaultDurationMs = ReaderMotionTokens.DurationInterruptSettle.inWholeMilliseconds
            )
        )

        // ---- P0：Viewport orientation ----
        put(
            "viewport.orientation.prepare",
            MotionContract(
                motionId = "viewport.orientation.prepare",
                from = listOf("viewportStable"),
                to = listOf("viewportFrozen"),
                interrupt = listOf("routeChange", "newMetricsBeforeFreeze"),
                finalState = "routeReaderSessionOverlayFocusFrozen",
                reducedMotion = "Freeze motion state immediately.",
                defaultDurationMs = ReaderMotionTokens.DurationOrientationFreeze.inWholeMilliseconds
            )
        )
        put(
            "viewport.orientation.reshape",
            MotionContract(
                motionId = "viewport.orientation.reshape",
                from = listOf("viewportFrozen", "viewportStable"),
                to = listOf("viewportReshaped"),
                interrupt = listOf("newMetrics", "foldChange", "routeChange"),
                finalState = "readerOverlayCapsuleDockReanchored",
                reducedMotion = "Recompute layout, pagination anchor, overlay, capsule, and dock bounds without interpolation.",
                defaultDurationMs = ReaderMotionTokens.DurationViewportReshape.inWholeMilliseconds
            )
        )
        put(
            "viewport.orientation.settle",
            MotionContract(
                motionId = "viewport.orientation.settle",
                from = listOf("viewportReshaped"),
                to = listOf("viewportStable"),
                interrupt = listOf("newMetrics", "routeChange"),
                finalState = "focusPointerSessionMicroMotionRestored",
                reducedMotion = "Restore focus, pointer, and session semantics without settle animation.",
                defaultDurationMs = ReaderMotionTokens.DurationOrientationSettle.inWholeMilliseconds
            )
        )
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
 * 47 个 Motion ID 常量（motion-controller.js 292-622）。
 * 与 [MotionIds] 现有 17 个常量互补：[MotionIds] 是早期 P0 子集别名，
 * 这里给出完整 47 项 authoritative 列表。
 */
object MotionIdConstants {
    // App launch / route
    const val APP_FIRST_OPEN_ENTER = "app.firstOpen.enter"
    const val APP_ROUTE_PUSH_FORWARD = "app.route.push.forward"
    const val APP_ROUTE_POP_BACKWARD = "app.route.pop.backward"
    const val APP_ROUTE_REPLACE = "app.route.replace"

    // Tab / segment
    const val TAB_ITEM_PRESS = "tab.item.press"
    const val TAB_ITEM_SELECT = "tab.item.select"
    const val TAB_ITEM_SWITCH = "tab.item.switch"
    const val SEGMENT_ITEM_SWITCH = "segment.item.switch"

    // Dropdown
    const val DROPDOWN_TRIGGER_PRESS = "dropdown.trigger.press"
    const val DROPDOWN_MENU_EXPAND = "dropdown.menu.expand"
    const val DROPDOWN_MENU_EXPAND_COLLAPSE = "dropdown.menu.expand/collapse"
    const val DROPDOWN_MENU_COLLAPSE = "dropdown.menu.collapse"
    const val DROPDOWN_MENU_REPOSITION = "dropdown.menu.reposition"
    const val DROPDOWN_OPTION_PRESS = "dropdown.option.press"
    const val DROPDOWN_OPTION_SELECT = "dropdown.option.select"

    // Button / toggle
    const val BUTTON_ACTIVATE = "button.activate"
    const val TOGGLE_SWITCH = "toggle.switch"

    // Reader entry / control
    const val READER_ENTRY_COVER_TO_IMMERSIVE = "reader.entry.coverToImmersive"
    const val READER_ENTRY_ACTION_TO_IMMERSIVE = "reader.entry.actionToImmersive"
    const val READER_CONTROL_HIDE = "reader.control.hide"
    const val READER_CONTROL_HANDLE_PRESS = "reader.control.handle.press"
    const val READER_CONTROL_HANDLE_DRAG = "reader.control.handle.drag"
    const val READER_CONTROL_HANDLE_RELEASE = "reader.control.handle.release"
    const val READER_CONTROL_DOCK_LONG_PRESS = "reader.control.dock.longPress"
    const val READER_CONTROL_DOCK_DRAG = "reader.control.dock.drag"
    const val READER_CONTROL_DOCK_RELEASE = "reader.control.dock.release"
    const val READER_CONTROL_DOCK_REBOUND = "reader.control.dock.rebound"

    // Reader session
    const val READER_SESSION_AUTO_PAGE_START = "reader.session.autoPage.start"
    const val READER_SESSION_TTS_START = "reader.session.tts.start"
    const val READER_SESSION_CAPSULE_ENTER = "reader.session.capsule.enter"
    const val READER_SESSION_CAPSULE_UPDATE = "reader.session.capsule.update"
    const val READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE = "reader.session.capsule.control.press/toggle"
    const val READER_SESSION_CAPSULE_COUNTDOWN_TICK = "reader.session.capsule.countdownTick"
    const val READER_SESSION_CAPSULE_VOICE_ICON_ACTIVE = "reader.session.capsule.voiceIcon.active"
    const val READER_SESSION_CAPSULE_SWITCH = "reader.session.capsule.switch"
    const val READER_SESSION_CAPSULE_EXIT = "reader.session.capsule.exit"
    const val READER_SESSION_CONTROL_SPACE_ENTER = "reader.session.controlSpace.enter"
    const val READER_SESSION_CONTROL_SPACE_UPDATE = "reader.session.controlSpace.update"
    const val READER_SESSION_CONTROL_SPACE_EXIT = "reader.session.controlSpace.exit"

    // Reader module / page
    const val READER_MODULE_SWITCH = "reader.module.switch"
    const val READER_PAGE_TURN_NEXT_PREV = "reader.page.turn.next/prev"

    // Motion interrupt 三态
    const val MOTION_INTERRUPT_CANCEL = "motion.interrupt.cancel"
    const val MOTION_INTERRUPT_REDIRECT = "motion.interrupt.redirect"
    const val MOTION_INTERRUPT_COMPLETE_THEN_REPLACE = "motion.interrupt.completeThenReplace"

    // Viewport orientation
    const val VIEWPORT_ORIENTATION_PREPARE = "viewport.orientation.prepare"
    const val VIEWPORT_ORIENTATION_RESHAPE = "viewport.orientation.reshape"
    const val VIEWPORT_ORIENTATION_SETTLE = "viewport.orientation.settle"
}
