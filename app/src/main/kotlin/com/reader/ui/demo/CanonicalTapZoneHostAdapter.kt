package com.reader.ui.demo

import com.reader.ui.reading.ReaderTapZoneContract
import com.reader.ui.reading.ReaderTapZoneHostState
import io.reader.ui.contract.ComponentType
import io.reader.ui.contract.UiEventType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

internal sealed interface CanonicalTapZoneHostAdapterResult {
    data class Ready(val plan: CanonicalTapZoneHostPlan) : CanonicalTapZoneHostAdapterResult
    data class Invalid(val reason: String) : CanonicalTapZoneHostAdapterResult
}

/** Strict canonical bindings plus live Host state. Fixture enabled props are not retained. */
internal data class CanonicalTapZoneHostPlan(
    val hostState: ReaderTapZoneHostState,
    val actionsByTarget: Map<String, CanonicalComponentAction>
) {
    val effectiveState: ReaderTapZoneHostState = ReaderTapZoneHostState(
        enabled = hostState.enabled && actionsByTarget.isNotEmpty(),
        previousEnabled = hostState.previousEnabled && "previous" in actionsByTarget,
        controlEnabled = hostState.controlEnabled && "control" in actionsByTarget,
        nextEnabled = hostState.nextEnabled && "next" in actionsByTarget
    )

    fun action(target: String): CanonicalComponentAction? = actionsByTarget[target]
}

/**
 * Five-instance ScreenGraph bridge. It validates generated geometry and exact
 * target/event/payload bindings, then intersects those bindings with live
 * Host state. Canonical `enabled` props are evidence only and never authority.
 */
internal object CanonicalTapZoneHostAdapter {
    private val exactPropKeys = setOf(
        "enabled",
        "mode",
        "previousRatio",
        "controlRatio",
        "nextRatio",
        "previousEnabled",
        "controlEnabled",
        "nextEnabled"
    )
    private val expectedEvents = mapOf(
        "previous" to UiEventType.ReaderPagePrev,
        "control" to UiEventType.ReaderControlToggle,
        "next" to UiEventType.ReaderPageNext
    )

    fun adapt(
        model: CanonicalComponentRenderModel,
        hostState: ReaderTapZoneHostState
    ): CanonicalTapZoneHostAdapterResult {
        fun invalid(reason: String) = CanonicalTapZoneHostAdapterResult.Invalid(reason)
        if (model.node.type != ComponentType.TapZones) return invalid("type must be TapZones")
        if (model.node.compositionMode != "host-composite") return invalid("compositionMode must be host-composite")
        if (model.node.stateAuthorities != listOf("reader-ui-runtime", "host-layout")) {
            return invalid("stateAuthorities must be reader-ui-runtime,host-layout")
        }
        if (model.node.children.isNotEmpty()) return invalid("TapZones host-composite must not have children")
        if (model.node.stateEventEvidence.isNotEmpty()) return invalid("TapZones must not promote state evidence")
        if (model.props.keys != exactPropKeys) return invalid("TapZones props must match the exact schema")
        if ((model.props["mode"] as? JsonPrimitive)?.content != "horizontal") {
            return invalid("mode must be horizontal")
        }
        val ratios = listOf("previousRatio", "controlRatio", "nextRatio").map { key ->
            (model.props[key] as? JsonPrimitive)?.doubleOrNull
                ?: return invalid("$key must be numeric")
        }
        if (
            abs(ratios[0] - ReaderTapZoneContract.PreviousRatio.toDouble()) > 1e-6 ||
            abs(ratios[1] - ReaderTapZoneContract.ControlRatio.toDouble()) > 1e-6 ||
            abs(ratios[2] - ReaderTapZoneContract.NextRatio.toDouble()) > 1e-6
        ) return invalid("TapZones ratios must be 0.26/0.48/0.26")

        val fixtureBooleans = listOf("enabled", "previousEnabled", "controlEnabled", "nextEnabled")
            .associateWith { key ->
                (model.props[key] as? JsonPrimitive)?.booleanOrNull
                    ?: return invalid("$key must be boolean")
            }
        val actions = model.actions()
        if (actions.map { it.target }.toSet().size != actions.size) {
            return invalid("TapZones targets must be unique")
        }
        val actionsByTarget = actions.associateBy(CanonicalComponentAction::target)
        if (actionsByTarget.keys.any { it !in expectedEvents }) return invalid("unknown TapZones target")
        actionsByTarget.forEach { (target, action) ->
            val binding = action.binding
            if (binding.event != expectedEvents.getValue(target)) return invalid("$target event mismatch")
            if (binding.trigger != "tap") return invalid("$target trigger must be tap")
            if (binding.evidenceProperty != "explicitBinding") {
                return invalid("$target evidenceProperty must be explicitBinding")
            }
            val expectedPayload = if (target == "control") {
                mapOf("overlay" to JsonPrimitive("reader-control"))
            } else {
                emptyMap()
            }
            if (binding.payload != expectedPayload) return invalid("$target payload mismatch")
        }

        // Schema consistency only. Rendering below still uses hostState, never these values.
        if (fixtureBooleans.getValue("enabled") != actionsByTarget.isNotEmpty()) {
            return invalid("fixture enabled/binding consistency drifted")
        }
        expectedEvents.keys.forEach { target ->
            if (fixtureBooleans.getValue("${target}Enabled") != (target in actionsByTarget)) {
                return invalid("fixture ${target}Enabled/binding consistency drifted")
            }
        }
        return CanonicalTapZoneHostAdapterResult.Ready(
            CanonicalTapZoneHostPlan(hostState = hostState, actionsByTarget = actionsByTarget)
        )
    }
}
