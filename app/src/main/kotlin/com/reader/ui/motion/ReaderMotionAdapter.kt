package com.reader.ui.motion

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import com.reader.ui.tokens.ReaderTokenAdapter
import io.reader.ui.contract.Motion
import io.reader.ui.contract.MotionEasing
import io.reader.ui.contract.MotionId
import io.reader.ui.contract.MotionSpecRegistry
import io.reader.ui.contract.MotionVisualPattern
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Returns the contract wire string for this [MotionId] — the `@SerialName` value from the
 * generated contract enum. This is the authoritative accessor for motion ID strings;
 * local code should prefer this over hand-maintained string literals.
 *
 * The Reader UI contract module owns the serialization plugin, so the generated serializer
 * descriptor is also the authoritative wire-name table. Reading it here removes the second,
 * hand-maintained Android map that used to fail during static initialization whenever the
 * generated enum gained a member.
 */
@OptIn(ExperimentalSerializationApi::class)
val MotionId.serialName: String
    get() = MotionId.serializer().descriptor.getElementName(ordinal)

/**
 * Maps Reader UI generated MotionSpecRegistry entries into Compose animation specs.
 *
 * The generated registry owns duration/easing/token metadata. Android keeps only platform
 * primitive decisions here: Compose easing conversion and movement allowance. String callers
 * are resolved against the exact generated serializer names.
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
            allowsMovement = !reducedMotion && generatedSpec.allowsMovement(),
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
    motionIdBySerialName[value]

/**
 * Movement policy comes from the generated visual pattern instead of an Android enum allowlist.
 * This means new generated motions automatically inherit the correct platform policy while
 * reduced motion still collapses every movement at the adapter boundary.
 */
private fun Motion.allowsMovement(): Boolean = when (visualPattern) {
    MotionVisualPattern.NativeStackForward,
    MotionVisualPattern.NativeStackBackward,
    MotionVisualPattern.SlideSheetUp,
    MotionVisualPattern.ScaleDialog,
    MotionVisualPattern.MatchedCoverToReader,
    MotionVisualPattern.PageTurn,
    MotionVisualPattern.DirectDrag,
    MotionVisualPattern.CapsuleAnchorMove -> true
    MotionVisualPattern.FadeReplace,
    MotionVisualPattern.NoMotion,
    null -> false
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

private val motionIdBySerialName: Map<String, MotionId> by lazy {
    MotionId.entries.associateBy { it.serialName }
}

private fun MotionEasing.toComposeEasing(): Easing = when (this) {
    MotionEasing.Linear,
    MotionEasing.None -> LinearEasing
    MotionEasing.Ease,
    MotionEasing.EaseInOut -> FastOutSlowInEasing
    MotionEasing.EaseIn,
    MotionEasing.EaseOut -> LinearOutSlowInEasing
}
