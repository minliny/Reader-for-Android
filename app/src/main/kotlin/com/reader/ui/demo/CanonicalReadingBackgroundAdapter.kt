package com.reader.ui.demo

import com.reader.ui.theme.ReaderThemeHostState
import com.reader.ui.theme.ReaderThemeResolver
import io.reader.ui.contract.ComponentType
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface CanonicalReadingBackgroundAdapterResult {
    data class Ready(val plan: CanonicalReadingBackgroundPlan) :
        CanonicalReadingBackgroundAdapterResult

    data class Invalid(val reason: String) : CanonicalReadingBackgroundAdapterResult
}

/** The canonical prop is schema evidence; the live Host theme remains render authority. */
internal data class CanonicalReadingBackgroundPlan(
    val fixtureTheme: String,
    val hostTheme: ReaderThemeHostState,
    val layoutBoundary: CanonicalReadingBackgroundLayoutBoundary =
        CanonicalReadingBackgroundLayoutBoundary.HostProvided
)

/** Color integration is exact; full-surface bounds remain owned by the enclosing Host. */
internal enum class CanonicalReadingBackgroundLayoutBoundary {
    HostProvided
}

/** Strict bridge for the 21 direct ReadingBackgroundLayer children of ReaderBase. */
internal object CanonicalReadingBackgroundAdapter {
    private val knownThemeIds: Set<String>
        get() = ReaderThemeResolver.THEMES.mapTo(linkedSetOf()) { it.id }

    fun adapt(
        model: CanonicalComponentRenderModel,
        hostTheme: ReaderThemeHostState
    ): CanonicalReadingBackgroundAdapterResult {
        fun invalid(reason: String) = CanonicalReadingBackgroundAdapterResult.Invalid(reason)

        if (model.node.type != ComponentType.ReadingBackgroundLayer) {
            return invalid("type must be ReadingBackgroundLayer")
        }
        if (model.node.id != "reader-background") return invalid("id must be reader-background")
        if (model.node.compositionMode != "contract-tree") {
            return invalid("compositionMode must be contract-tree")
        }
        if (model.node.stateAuthorities != listOf("host-store")) {
            return invalid("stateAuthorities must be host-store")
        }
        if (model.props.keys != setOf("theme")) return invalid("props must contain only theme")
        val fixtureTheme = (model.props["theme"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?: return invalid("theme must be a string")
        if (fixtureTheme !in knownThemeIds) return invalid("unknown fixture theme: $fixtureTheme")
        if (fixtureTheme != "paper") return invalid("fixture theme must be canonical paper evidence")
        if (model.node.children.isNotEmpty()) return invalid("children must be empty")
        if (model.bindings.isNotEmpty()) return invalid("bindings must be empty")
        if (model.node.stateEventEvidence.isNotEmpty()) return invalid("stateEventEvidence must be empty")

        if (hostTheme.themeId !in knownThemeIds) {
            return invalid("unknown Host theme: ${hostTheme.themeId}")
        }
        if (hostTheme.effectiveThemeId !in knownThemeIds) {
            return invalid("unknown effective Host theme: ${hostTheme.effectiveThemeId}")
        }
        val expectedEffectiveId = if (hostTheme.isNight && !hostTheme.themeId.endsWith("-night")) {
            "${hostTheme.themeId}-night"
        } else {
            hostTheme.themeId
        }
        if (hostTheme.effectiveThemeId != expectedEffectiveId) {
            return invalid("Host theme identity is inconsistent")
        }
        val expectedPaper = ReaderThemeResolver.palette(hostTheme.themeId, hostTheme.isNight).paper
        if (hostTheme.paper != expectedPaper) return invalid("Host paper color is inconsistent")

        return CanonicalReadingBackgroundAdapterResult.Ready(
            CanonicalReadingBackgroundPlan(
                fixtureTheme = fixtureTheme,
                hostTheme = hostTheme
            )
        )
    }
}
