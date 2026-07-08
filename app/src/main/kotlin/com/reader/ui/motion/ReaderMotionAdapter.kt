package com.reader.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import com.reader.ui.tokens.ReaderTokenAdapter
import io.reader.ui.contract.MotionEasing
import io.reader.ui.contract.MotionId
import io.reader.ui.contract.MotionSpecRegistry

/**
 * Returns the contract wire string for this [MotionId] — the `@SerialName` value from the
 * generated contract enum. This is the authoritative accessor for motion ID strings;
 * local code should prefer this over hand-maintained string literals.
 *
 * Backed by [motionIdSerialNames] until the kotlinx.serialization compiler plugin is wired
 * (the runtime dependency alone does not generate `MotionId.serializer()`); the map mirrors
 * the `@SerialName` annotations in the generated `MotionId` enum 1:1.
 */
val MotionId.serialName: String
    get() = motionIdSerialNames.getValue(this)

/**
 * Maps Reader UI generated MotionSpecRegistry entries into Compose animation specs.
 *
 * The generated registry owns duration/easing/token metadata. Android keeps only platform
 * primitive decisions here: Compose easing conversion, movement allowance, and a temporary
 * string alias bridge for pre-generated local callers.
 */
object ReaderMotionAdapter {
    fun specFor(motionId: MotionId, reducedMotion: Boolean = false): ReaderComposeMotionSpec =
        specFor(
            resolved = motionId.resolve(),
            reducedMotion = reducedMotion
        )

    fun specFor(motionId: String, reducedMotion: Boolean = false): ReaderComposeMotionSpec {
        val resolved = motionIdFromContractName(motionId)
            ?.resolve(outputId = motionId)
            ?: throw IllegalArgumentException("Unsupported Reader motion id: $motionId")
        return specFor(resolved, reducedMotion)
    }

    fun hasMotion(motionId: MotionId): Boolean = MotionSpecRegistry.spec(motionId) != null

    fun hasMotion(motionId: String): Boolean = motionIdFromContractName(motionId)
        ?.let { MotionSpecRegistry.spec(it) != null }
        ?: false

    private fun specFor(
        resolved: ResolvedMotionId,
        reducedMotion: Boolean
    ): ReaderComposeMotionSpec {
        val generatedSpec = MotionSpecRegistry.spec(resolved.generatedId)
            ?: throw IllegalArgumentException("Unsupported Reader motion id: ${resolved.outputId}")
        val contract = MotionController.contractFor(resolved.localContractId)
        return ReaderComposeMotionSpec(
            motionId = resolved.outputId,
            durationMillis = ReaderTokenAdapter.durationMillis(generatedSpec, reducedMotion),
            easing = if (reducedMotion) LinearEasing else generatedSpec.easing.toComposeEasing(),
            allowsMovement = !reducedMotion && resolved.generatedId.allowsMovement(),
            finalState = contract?.finalState ?: resolved.generatedId.finalStateFallback(),
            reducedMotion = reducedMotion
        )
    }
}

data class ReaderComposeMotionSpec(
    val motionId: String,
    val durationMillis: Int,
    val easing: Easing,
    val allowsMovement: Boolean,
    val finalState: String,
    val reducedMotion: Boolean
) {
    fun floatTween(): TweenSpec<Float> = tween(
        durationMillis = durationMillis,
        easing = easing
    )
}

private data class ResolvedMotionId(
    val generatedId: MotionId,
    val contractName: String,
    val localContractId: String = contractName,
    val outputId: String = contractName
)

private fun MotionId.resolve(outputId: String = contractName()): ResolvedMotionId =
    ResolvedMotionId(
        generatedId = this,
        contractName = contractName(),
        localContractId = localContractId(),
        outputId = outputId
    )

private fun MotionId.localContractId(): String = when (this) {
    MotionId.TabSwitch -> MotionIdConstants.TAB_ITEM_SWITCH
    MotionId.ReaderPageTurnNextPrev -> MotionIdConstants.READER_PAGE_TURN_NEXT_PREV
    MotionId.ReaderSessionCapsuleControlPressToggle -> MotionIdConstants.READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE
    else -> contractName()
}

private fun MotionId.contractName(): String = serialName

private fun motionIdFromContractName(value: String): MotionId? =
    motionIdBySerialName[value] ?: legacyMotionIdAliases[value]

private fun MotionId.allowsMovement(): Boolean = when (this) {
    MotionId.AppRoutePushForward,
    MotionId.AppRoutePopBackward,
    MotionId.ReaderEntryCoverToImmersive,
    MotionId.ReaderPageTurnNextPrev,
    MotionId.ReaderControlHide,
    MotionId.ViewportOrientationReshape -> true
    else -> false
}

private fun MotionId.finalStateFallback(): String = when (this) {
    MotionId.AppFirstOpenEnter -> "firstScreenSettled"
    MotionId.AppRoutePushForward -> "routeTargetSettled"
    MotionId.AppRoutePopBackward -> "previousRouteSettled"
    MotionId.AppRouteReplace -> "replacementRouteSettled"
    MotionId.TabItemSelect -> "tabSelected"
    MotionId.TabSwitch -> "targetTabSelected"
    MotionId.ReaderEntryCoverToImmersive,
    MotionId.ReaderEntryActionToImmersive -> "immersiveReading"
    MotionId.ReaderPageTurnNextPrev -> "pageIndexCommitted"
    MotionId.ReaderControlHide -> "immersiveReadingHotZonesRestored"
    MotionId.ReaderModuleSwitch -> "readerModuleSelected"
    MotionId.ReaderSessionTtsStart -> "ttsOwnsSessionAndCapsule"
    MotionId.ReaderSessionAutoPageStart -> "autoPageOwnsSessionAndCapsule"
    MotionId.MotionInterruptCancel -> "transientMotionCleared"
    MotionId.MotionInterruptRedirect -> "newTargetOwnsMotion"
    MotionId.MotionInterruptCompleteThenReplace -> "replacementVisibleOnlyIfStillCurrent"
    MotionId.ViewportOrientationPrepare -> "viewportFrozen"
    MotionId.ViewportOrientationReshape -> "viewportReshaped"
    MotionId.ViewportOrientationSettle -> "viewportStable"
    else -> "motionSettled"
}

private val legacyMotionIdAliases: Map<String, MotionId> = mapOf(
    MotionIdConstants.TAB_ITEM_SWITCH to MotionId.TabSwitch,
    MotionIdConstants.READER_PAGE_TURN_NEXT_PREV to MotionId.ReaderPageTurnNextPrev,
    MotionIdConstants.READER_SESSION_CAPSULE_CONTROL_PRESS_TOGGLE to MotionId.ReaderSessionCapsuleControlPressToggle
)

private val motionIdSerialNames: Map<MotionId, String> = mapOf(
    MotionId.AppFirstOpenEnter to "app.firstOpen.enter",
    MotionId.AppRoutePushForward to "app.route.push.forward",
    MotionId.AppRoutePopBackward to "app.route.pop.backward",
    MotionId.AppRouteReplace to "app.route.replace",
    MotionId.BookshelfViewSwitch to "bookshelf.view.switch",
    MotionId.ButtonActivate to "button.activate",
    MotionId.CardPress to "card.press",
    MotionId.CardSelect to "card.select",
    MotionId.CardRoute to "card.route",
    MotionId.ChipItemSelect to "chip.item.select",
    MotionId.DestructiveConfirmCommit to "destructive.confirm.commit",
    MotionId.DropdownMenuCollapse to "dropdown.menu.collapse",
    MotionId.DropdownMenuExpand to "dropdown.menu.expand",
    MotionId.DropdownOptionSelect to "dropdown.option.select",
    MotionId.DropdownTriggerPress to "dropdown.trigger.press",
    MotionId.FeedbackToastEnter to "feedback.toast.enter",
    MotionId.FeedbackToastUpdate to "feedback.toast.update",
    MotionId.FeedbackToastExit to "feedback.toast.exit",
    MotionId.FilterApplyCommit to "filter.apply.commit",
    MotionId.FilterItemToggle to "filter.item.toggle",
    MotionId.InputBlur to "input.blur",
    MotionId.InputClear to "input.clear",
    MotionId.InputFocus to "input.focus",
    MotionId.InputFocusBlur to "input.focus-blur",
    MotionId.InputSubmit to "input.submit",
    MotionId.ListRowSelect to "listRow.select",
    MotionId.MotionInterruptCancel to "motion.interrupt.cancel",
    MotionId.MotionInterruptCompleteThenReplace to "motion.interrupt.completeThenReplace",
    MotionId.MotionInterruptRedirect to "motion.interrupt.redirect",
    MotionId.OverlayDialogEnter to "overlay.dialog.enter",
    MotionId.OverlayDialogEnterExit to "overlay.dialog.enter-exit",
    MotionId.OverlayDialogExit to "overlay.dialog.exit",
    MotionId.OverlayKeyboardEnterExit to "overlay.keyboard.enter-exit",
    MotionId.OverlaySheetEnter to "overlay.sheet.enter",
    MotionId.OverlaySheetEnterExit to "overlay.sheet.enter-exit",
    MotionId.OverlaySheetExit to "overlay.sheet.exit",
    MotionId.ReaderChapterJump to "reader.chapter.jump",
    MotionId.ReaderControlDockDrag to "reader.control.dock.drag",
    MotionId.ReaderControlDockLongPress to "reader.control.dock.longPress",
    MotionId.ReaderControlDockRebound to "reader.control.dock.rebound",
    MotionId.ReaderControlDockRelease to "reader.control.dock.release",
    MotionId.ReaderControlHandlePress to "reader.control.handle.press",
    MotionId.ReaderControlHandleRelease to "reader.control.handle.release",
    MotionId.ReaderControlHide to "reader.control.hide",
    MotionId.ReaderEntryActionToImmersive to "reader.entry.actionToImmersive",
    MotionId.ReaderEntryCoverToImmersive to "reader.entry.coverToImmersive",
    MotionId.ReaderModuleSwitch to "reader.module.switch",
    MotionId.ReaderPageTurnNextPrev to "reader.page.turn.next-prev",
    MotionId.ReaderQuickPromote to "reader.quick.promote",
    MotionId.ReaderSessionAutoPageStart to "reader.session.autoPage.start",
    MotionId.ReaderSessionCapsuleEnter to "reader.session.capsule.enter",
    MotionId.ReaderSessionCapsuleUpdate to "reader.session.capsule.update",
    MotionId.ReaderSessionCapsuleExit to "reader.session.capsule.exit",
    MotionId.ReaderSessionCapsuleSwitch to "reader.session.capsule.switch",
    MotionId.ReaderSessionCapsuleControlPressToggle to "reader.session.capsule.control.press-toggle",
    MotionId.ReaderSessionCapsuleCountdownTick to "reader.session.capsule.countdownTick",
    MotionId.ReaderSessionCapsuleVoiceIconActive to "reader.session.capsule.voiceIcon.active",
    MotionId.ReaderSessionControlSpaceEnter to "reader.session.controlSpace.enter",
    MotionId.ReaderSessionControlSpaceUpdate to "reader.session.controlSpace.update",
    MotionId.ReaderSessionControlSpaceExit to "reader.session.controlSpace.exit",
    MotionId.ReaderSessionTtsStart to "reader.session.tts.start",
    MotionId.ReaderSourceSwitchOpenClose to "reader.sourceSwitch.open-close",
    MotionId.SearchStateReplace to "search.state.replace",
    MotionId.SegmentItemSwitch to "segment.item.switch",
    MotionId.SelectionGroupToggle to "selection.group.toggle",
    MotionId.SelectionItemToggle to "selection.item.toggle",
    MotionId.SelectionOptionToggle to "selection.option.toggle",
    MotionId.SelectionRangeShow to "selection.range.show",
    MotionId.SelectionToolbarAction to "selection.toolbar.action",
    MotionId.SelectionToolbarExit to "selection.toolbar.exit",
    MotionId.SliderDragStart to "slider.drag.start",
    MotionId.SliderDragUpdate to "slider.drag.update",
    MotionId.SliderDragRelease to "slider.drag.release",
    MotionId.StateContentReplace to "state.content.replace",
    MotionId.StateLoadingInline to "state.loading.inline",
    MotionId.StepperPress to "stepper.press",
    MotionId.StepperValueChange to "stepper.value.change",
    MotionId.TabItemSelect to "tab.item.select",
    MotionId.TabSwitch to "tab.switch",
    MotionId.ToggleSwitch to "toggle.switch",
    MotionId.ToolingModeSwitch to "tooling.mode.switch",
    MotionId.ViewportOrientationPrepare to "viewport.orientation.prepare",
    MotionId.ViewportOrientationReshape to "viewport.orientation.reshape",
    MotionId.ViewportOrientationSettle to "viewport.orientation.settle"
)

private val motionIdBySerialName: Map<String, MotionId> by lazy {
    motionIdSerialNames.entries.associate { (id, name) -> name to id }
}

private fun MotionEasing.toComposeEasing(): Easing = when (this) {
    MotionEasing.Linear,
    MotionEasing.None -> LinearEasing
    MotionEasing.Ease,
    MotionEasing.EaseInOut -> FastOutSlowInEasing
    MotionEasing.EaseIn,
    MotionEasing.EaseOut -> LinearOutSlowInEasing
}
