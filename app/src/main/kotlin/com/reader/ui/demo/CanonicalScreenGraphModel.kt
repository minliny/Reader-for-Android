package com.reader.ui.demo

import io.reader.ui.contract.ComponentType
import io.reader.ui.contract.PageState
import io.reader.ui.contract.RouteId
import io.reader.ui.contract.ScreenGraphActionBinding
import io.reader.ui.contract.ScreenGraphCanonicalAsset
import io.reader.ui.contract.ScreenGraphComponentCatalogStatus
import io.reader.ui.contract.ScreenGraphComponentNode
import io.reader.ui.contract.ScreenGraphRegistry
import io.reader.ui.contract.ScreenGraphRegistryException
import io.reader.ui.contract.ScreenGraphRouteNode
import io.reader.ui.contract.ScreenGraphStateEventEvidence
import io.reader.ui.contract.ScreenGraphVariant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Query accepted by the generated-contract renderer without copying a route table. */
internal data class CanonicalScreenGraphQuery(
    val routeId: String,
    val variantId: String? = null,
    val pageState: PageState? = null,
    val context: Map<String, JsonElement> = emptyMap(),
    val facets: Map<String, JsonElement> = emptyMap()
)

internal sealed interface CanonicalRouteRenderPlan {
    data class Ready(
        val query: CanonicalScreenGraphQuery,
        val requestedRoute: ScreenGraphRouteNode,
        val resolvedRoute: ScreenGraphRouteNode,
        val aliasPath: List<ScreenGraphRouteNode>,
        val variant: ScreenGraphVariant
    ) : CanonicalRouteRenderPlan

    data class VisibleFailure(
        val routeId: String,
        val code: String,
        val message: String
    ) : CanonicalRouteRenderPlan
}

/** Honest Android adapter state for every canonical referenced ComponentType. */
internal sealed interface AndroidCanonicalComponentAdapterEntry {
    data class Supported(
        val kind: AndroidCanonicalComponentAdapterKind,
        val fidelity: AndroidCanonicalComponentAdapterFidelity,
        val provenance: String
    ) :
        AndroidCanonicalComponentAdapterEntry

    data class VisibleFailure(val code: String, val reason: String) :
        AndroidCanonicalComponentAdapterEntry
}

internal enum class AndroidCanonicalComponentAdapterFidelity {
    Faithful,
    GenericUsable
}

/** Existing Compose primitives that can currently consume the node without inventing data. */
internal enum class AndroidCanonicalComponentAdapterKind {
    AppTopBar,
    BackTopBar,
    BottomNav,
    BookCard,
    BookshelfSection,
    ShelfSectionHeader,
    Loading,
    Empty,
    Error,
    Offline,
    Permission,
    Toast,
    List,
    ListRow,
    Button,
    FormSection,
    Content,
    GenericStructure,
    GenericText,
    GenericContinueCard,
    GenericGrid,
    GenericState,
    GenericProgress,
    GenericControl,
    GenericBackground,
    GenericDialog
}

internal data class AndroidCanonicalComponentCoverage(
    val canonicalComponentCount: Int,
    val referencedTypes: Set<ComponentType>,
    val explicitGapTypes: Set<ComponentType>,
    val supportedTypes: Set<ComponentType>,
    val faithfulTypes: Set<ComponentType>,
    val genericUsableTypes: Set<ComponentType>,
    val partialTypes: Set<ComponentType>,
    val insufficientTypes: Set<ComponentType>,
    val visibleFailureTypes: Set<ComponentType>
) {
    val isFullRenderer: Boolean
        get() = visibleFailureTypes.isEmpty() && genericUsableTypes.isEmpty()

    val isAdapterRegistryClosed: Boolean
        get() = visibleFailureTypes.isEmpty()
}

/**
 * Machine-executable referenced-vs-adapter registry. Keys are derived from
 * generated ScreenGraph catalog data, not from an Android copy of 129 names.
 */
internal class AndroidCanonicalComponentAdapterRegistry private constructor(
    val entries: Map<ComponentType, AndroidCanonicalComponentAdapterEntry>,
    val coverage: AndroidCanonicalComponentCoverage
) {
    val referencedVsAdapterGaps: Map<ComponentType, AndroidCanonicalComponentAdapterEntry.VisibleFailure> =
        entries.mapNotNull { (type, entry) ->
            (entry as? AndroidCanonicalComponentAdapterEntry.VisibleFailure)?.let { type to it }
        }.toMap(linkedMapOf())

    val canonicalExplicitGaps: Set<ComponentType>
        get() = coverage.explicitGapTypes

    fun entry(type: ComponentType): AndroidCanonicalComponentAdapterEntry = when {
        type in coverage.explicitGapTypes -> AndroidCanonicalComponentAdapterEntry.VisibleFailure(
            code = "CANONICAL_EXPLICIT_COMPONENT_GAP",
            reason = "$type has no canonical ViewState instance and must not be claimed as rendered"
        )
        else -> lookupEntry(type, entries)
    }

    companion object {
        private val faithfulKinds: Map<ComponentType, AndroidCanonicalComponentAdapterKind> = mapOf(
            ComponentType.AppTopBar to AndroidCanonicalComponentAdapterKind.AppTopBar,
            ComponentType.BackTopBar to AndroidCanonicalComponentAdapterKind.BackTopBar,
            ComponentType.BottomNav to AndroidCanonicalComponentAdapterKind.BottomNav,
            ComponentType.BookCard to AndroidCanonicalComponentAdapterKind.BookCard,
            ComponentType.BookshelfShelfSection to AndroidCanonicalComponentAdapterKind.BookshelfSection,
            ComponentType.ShelfSectionHeader to AndroidCanonicalComponentAdapterKind.ShelfSectionHeader,
            ComponentType.Loading to AndroidCanonicalComponentAdapterKind.Loading,
            ComponentType.Empty to AndroidCanonicalComponentAdapterKind.Empty,
            ComponentType.Error to AndroidCanonicalComponentAdapterKind.Error,
            ComponentType.ErrorState to AndroidCanonicalComponentAdapterKind.Error,
            ComponentType.Offline to AndroidCanonicalComponentAdapterKind.Offline,
            ComponentType.Permission to AndroidCanonicalComponentAdapterKind.Permission,
            ComponentType.Toast to AndroidCanonicalComponentAdapterKind.Toast,
            ComponentType.List to AndroidCanonicalComponentAdapterKind.List,
            ComponentType.ListRow to AndroidCanonicalComponentAdapterKind.ListRow,
            ComponentType.Button to AndroidCanonicalComponentAdapterKind.Button,
            ComponentType.FormSection to AndroidCanonicalComponentAdapterKind.FormSection,
            ComponentType.Content to AndroidCanonicalComponentAdapterKind.Content
        )

        private val genericKinds: Map<ComponentType, AndroidCanonicalComponentAdapterKind> = mapOf(
            ComponentType.AboutVersionPage to AndroidCanonicalComponentAdapterKind.GenericText,
            ComponentType.AppShellStructure to AndroidCanonicalComponentAdapterKind.GenericStructure,
            ComponentType.MainTabsStructure to AndroidCanonicalComponentAdapterKind.GenericStructure,
            ComponentType.BookGrid to AndroidCanonicalComponentAdapterKind.GenericGrid,
            ComponentType.ContinueReadingCard to AndroidCanonicalComponentAdapterKind.GenericContinueCard,
            ComponentType.Dialog to AndroidCanonicalComponentAdapterKind.GenericDialog,
            ComponentType.BookshelfEmptyPage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.DiscoverStatePage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.GlobalStatePage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.OfflineStatePage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.PermissionRequiredPage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.RssEmptyState to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.RssErrorState to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.SearchStatePage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.SourceDisabledState to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.SourceSwitchResultsPanel to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.SyncErrorPage to AndroidCanonicalComponentAdapterKind.GenericState,
            ComponentType.SyncProgressPage to AndroidCanonicalComponentAdapterKind.GenericProgress,
            ComponentType.ReadingInfoLayer to AndroidCanonicalComponentAdapterKind.GenericProgress,
            ComponentType.FloatingPageControl to AndroidCanonicalComponentAdapterKind.GenericControl,
            ComponentType.ReadingBackgroundLayer to AndroidCanonicalComponentAdapterKind.GenericBackground
        )

        private val partialReasons: Map<ComponentType, String> = mapOf(
            ComponentType.ReaderAppearancePanel to "1 of 7 canonical instances has no children",
            ComponentType.ReaderBase to "26 of 47 canonical instances have no children",
            ComponentType.ReaderDirectoryPanel to "2 of 5 canonical instances have no children",
            ComponentType.ReaderReplacePanel to "1 of 6 canonical instances has no children",
            ComponentType.ReadingTextFlow to "5 of 8 instances have no text children or text payload",
            ComponentType.SourceSwitchFlowPage to "2 of 8 canonical instances have no children"
        )

        fun loadCanonical(): AndroidCanonicalComponentAdapterRegistry {
            val graph = ScreenGraphRegistry.loadCanonical()
            val referenced = graph.document.componentCatalog
                .filter { it.status == ScreenGraphComponentCatalogStatus.Referenced }
                .mapTo(linkedSetOf()) { it.type }
            val explicitGaps = graph.document.componentCatalog
                .filter { it.status == ScreenGraphComponentCatalogStatus.ExplicitGap }
                .mapTo(linkedSetOf()) { it.type }
            val insufficient = referenced - faithfulKinds.keys - genericKinds.keys - partialReasons.keys
            check(faithfulKinds.size == 18 && genericKinds.size == 21) {
                "Android adapter classification drifted: faithful=${faithfulKinds.size} generic=${genericKinds.size}"
            }
            check(partialReasons.size == 6 && insufficient.size == 84) {
                "Android visible-gap classification drifted: partial=${partialReasons.size} insufficient=${insufficient.size}"
            }
            check(
                faithfulKinds.keys + genericKinds.keys + partialReasons.keys + insufficient == referenced
            ) {
                "Android component classification must exactly partition all referenced types"
            }
            val entries: Map<ComponentType, AndroidCanonicalComponentAdapterEntry> = referenced.associateWith { type ->
                when {
                    type in faithfulKinds -> AndroidCanonicalComponentAdapterEntry.Supported(
                        kind = faithfulKinds.getValue(type),
                        fidelity = AndroidCanonicalComponentAdapterFidelity.Faithful,
                        provenance = faithfulProvenance(type)
                    )
                    type in genericKinds -> AndroidCanonicalComponentAdapterEntry.Supported(
                        kind = genericKinds.getValue(type),
                        fidelity = AndroidCanonicalComponentAdapterFidelity.GenericUsable,
                        provenance = genericProvenance(type, genericKinds.getValue(type))
                    )
                    type in partialReasons -> AndroidCanonicalComponentAdapterEntry.VisibleFailure(
                        code = "ANDROID_COMPONENT_PARTIAL_CANONICAL_DATA",
                        reason = "${partialReasons.getValue(type)}; type remains visible-gap until every instance is renderable"
                    )
                    else -> AndroidCanonicalComponentAdapterEntry.VisibleFailure(
                        code = "ANDROID_COMPONENT_CONTRACT_DATA_INSUFFICIENT",
                        reason = "$type lacks the typed props, children, or trigger semantics required by its existing Compose implementation"
                    )
                }
            }
            check(entries.keys == referenced) {
                "Android canonical adapter registry must exactly cover referenced types"
            }
            check((entries.keys intersect explicitGaps).isEmpty()) {
                "Canonical explicit-gap types must not be registered as rendered adapters"
            }
            check(referenced.size == 129 && explicitGaps.size == 45) {
                "ScreenGraph component coverage drifted: referenced=${referenced.size} explicitGaps=${explicitGaps.size}"
            }
            val supported = entries.filterValues { it is AndroidCanonicalComponentAdapterEntry.Supported }.keys
            val faithful = entries.filterValues {
                (it as? AndroidCanonicalComponentAdapterEntry.Supported)?.fidelity ==
                    AndroidCanonicalComponentAdapterFidelity.Faithful
            }.keys
            val generic = entries.filterValues {
                (it as? AndroidCanonicalComponentAdapterEntry.Supported)?.fidelity ==
                    AndroidCanonicalComponentAdapterFidelity.GenericUsable
            }.keys
            val unsupported = entries.filterValues { it is AndroidCanonicalComponentAdapterEntry.VisibleFailure }.keys
            return AndroidCanonicalComponentAdapterRegistry(
                entries = entries,
                coverage = AndroidCanonicalComponentCoverage(
                    canonicalComponentCount = graph.document.componentCatalog.size,
                    referencedTypes = referenced,
                    explicitGapTypes = explicitGaps,
                    supportedTypes = supported,
                    faithfulTypes = faithful,
                    genericUsableTypes = generic,
                    partialTypes = partialReasons.keys,
                    insufficientTypes = insufficient,
                    visibleFailureTypes = unsupported
                )
            )
        }

        private fun faithfulProvenance(type: ComponentType): String = when (type) {
            ComponentType.AppTopBar -> "DemoTopBar"
            ComponentType.BackTopBar -> "DemoBackBar"
            ComponentType.BottomNav -> "DemoBottomNav"
            ComponentType.BookCard -> "DemoBookCover + ReaderTextStyles"
            ComponentType.BookshelfShelfSection,
            ComponentType.FormSection,
            ComponentType.Content -> "DemoSectionCard"
            ComponentType.ShelfSectionHeader -> "ReaderTextStyles.sectionTitle"
            ComponentType.Loading -> "DemoLoadingSkeleton"
            ComponentType.Empty,
            ComponentType.Error,
            ComponentType.ErrorState,
            ComponentType.Offline,
            ComponentType.Permission -> "DemoStateBlock"
            ComponentType.Toast -> "DemoToast"
            ComponentType.List -> "Compose Column recursive children"
            ComponentType.ListRow -> "DemoListItem"
            ComponentType.Button -> "DemoButton + typed ScreenGraphActionBinding"
            else -> error("No faithful provenance registered for $type")
        }

        private fun genericProvenance(
            type: ComponentType,
            kind: AndroidCanonicalComponentAdapterKind
        ): String = when (kind) {
            AndroidCanonicalComponentAdapterKind.GenericGrid -> "child-aware canonical grid + BookCard adapters"
            AndroidCanonicalComponentAdapterKind.GenericContinueCard -> "DemoBookCover + canonical title/author/card data"
            AndroidCanonicalComponentAdapterKind.GenericState -> "DemoStateBlock + canonical title/message/action evidence"
            AndroidCanonicalComponentAdapterKind.GenericProgress -> "DemoReaderProgressBar + canonical progress/chapter props"
            AndroidCanonicalComponentAdapterKind.GenericBackground -> "strict paper theme background surface"
            AndroidCanonicalComponentAdapterKind.GenericDialog -> "overlay card + recursive canonical Button children"
            AndroidCanonicalComponentAdapterKind.GenericControl -> "static canonical control; binding retained as evidence only"
            AndroidCanonicalComponentAdapterKind.GenericText -> "Reader text styles + canonical text props"
            AndroidCanonicalComponentAdapterKind.GenericStructure -> "canonical title/message structure surface"
            else -> error("Unexpected generic family for $type: $kind")
        }

        internal fun lookupEntry(
            type: ComponentType,
            entries: Map<ComponentType, AndroidCanonicalComponentAdapterEntry>
        ): AndroidCanonicalComponentAdapterEntry = entries[type]
            ?: AndroidCanonicalComponentAdapterEntry.VisibleFailure(
                code = "ANDROID_COMPONENT_ADAPTER_UNREGISTERED",
                reason = "$type reached renderer without a referenced-set registry entry"
            )
    }
}

/** Alias-aware, variant-aware planner backed only by generated ScreenGraph. */
internal class CanonicalScreenGraphPlanner private constructor(
    private val registry: ScreenGraphRegistry,
    private val routeIdsBySerialName: Map<String, RouteId>
) {
    fun plan(query: CanonicalScreenGraphQuery): CanonicalRouteRenderPlan {
        val routeId = routeIdsBySerialName[query.routeId]
            ?: return CanonicalRouteRenderPlan.VisibleFailure(
                routeId = query.routeId,
                code = "CANONICAL_ROUTE_UNKNOWN",
                message = "Route is absent from generated ScreenGraph"
            )
        return try {
            val aliasPath = aliasPath(routeId)
            val requested = aliasPath.first()
            val resolved = aliasPath.last()
            val variant = selectVariant(query, resolved.variants)
                ?: return CanonicalRouteRenderPlan.VisibleFailure(
                    routeId = query.routeId,
                    code = "CANONICAL_VARIANT_UNRESOLVED",
                    message = variantFailureMessage(query, resolved.variants)
                )
            CanonicalRouteRenderPlan.Ready(
                query = query,
                requestedRoute = requested,
                resolvedRoute = resolved,
                aliasPath = aliasPath,
                variant = variant
            )
        } catch (error: ScreenGraphRegistryException) {
            CanonicalRouteRenderPlan.VisibleFailure(
                routeId = query.routeId,
                code = "CANONICAL_GRAPH_INVALID",
                message = error.message ?: "Generated ScreenGraph resolution failed"
            )
        }
    }

    private fun aliasPath(routeId: RouteId): List<ScreenGraphRouteNode> {
        val result = mutableListOf<ScreenGraphRouteNode>()
        val visited = mutableSetOf<RouteId>()
        var cursor = routeId
        while (true) {
            if (!visited.add(cursor)) throw ScreenGraphRegistryException("alias cycle")
            val node = registry.route(cursor)
                ?: throw ScreenGraphRegistryException("route missing from generated index")
            result += node
            cursor = node.aliasFor ?: return result
        }
    }

    private fun selectVariant(
        query: CanonicalScreenGraphQuery,
        variants: List<ScreenGraphVariant>
    ): ScreenGraphVariant? {
        query.variantId?.let { requested ->
            return variants.singleOrNull { it.variantId == requested }
        }
        val constrained = variants.filter { variant ->
            (query.pageState == null || variant.pageState == query.pageState) &&
                query.context.all { (key, value) -> variant.context[key] == value } &&
                query.facets.all { (key, value) -> variant.facets[key] == value }
        }
        if (constrained.size == 1) return constrained.single()
        constrained.singleOrNull { it.variantId == "default" }?.let { return it }
        constrained.singleOrNull { it.pageState == PageState.Default }?.let { return it }
        return null
    }

    private fun variantFailureMessage(
        query: CanonicalScreenGraphQuery,
        variants: List<ScreenGraphVariant>
    ): String = buildString {
        append("No unique canonical variant for route=")
        append(query.routeId)
        append(" requestedVariant=")
        append(query.variantId)
        append(" pageState=")
        append(query.pageState)
        append(" available=")
        append(variants.map { it.variantId })
    }

    companion object {
        fun loadCanonical(): CanonicalScreenGraphPlanner {
            val registry = ScreenGraphRegistry.loadCanonical()
            check(registry.document.routes.size == ScreenGraphCanonicalAsset.routeCount)
            val routeIds = RouteId.entries.associateBy { it.contractRouteId() }
            check(routeIds.size == ScreenGraphCanonicalAsset.routeCount)
            return CanonicalScreenGraphPlanner(registry, routeIds)
        }
    }
}

internal data class CanonicalComponentObservation(
    val depth: Int,
    val node: ScreenGraphComponentNode,
    val adapter: AndroidCanonicalComponentAdapterEntry
)

internal data class CanonicalScreenGraphShadowObservation(
    val query: CanonicalScreenGraphQuery,
    val plan: CanonicalRouteRenderPlan,
    val components: List<CanonicalComponentObservation>,
    val faithfulComponentTypes: Set<ComponentType>,
    val genericUsableComponentTypes: Set<ComponentType>,
    val unsupportedComponentTypes: Set<ComponentType>,
    val actionGapCount: Int
)

/** Production-safe observer: consumes the graph but never replaces the current renderer. */
internal object CanonicalScreenGraphShadowObserver {
    private val planner by lazy(CanonicalScreenGraphPlanner::loadCanonical)
    private val adapters by lazy(AndroidCanonicalComponentAdapterRegistry::loadCanonical)

    @Volatile
    var lastObservation: CanonicalScreenGraphShadowObservation? = null
        private set

    @Synchronized
    fun observe(query: CanonicalScreenGraphQuery): CanonicalScreenGraphShadowObservation {
        val plan = planner.plan(query)
        val components = buildList {
            if (plan is CanonicalRouteRenderPlan.Ready) {
                fun walk(nodes: List<ScreenGraphComponentNode>, depth: Int) {
                    nodes.forEach { node ->
                        add(CanonicalComponentObservation(depth, node, adapters.entry(node.type)))
                        walk(node.children, depth + 1)
                    }
                }
                walk(plan.variant.components, 0)
            }
        }
        return CanonicalScreenGraphShadowObservation(
            query = query,
            plan = plan,
            components = components,
            faithfulComponentTypes = components.mapNotNullTo(linkedSetOf()) { item ->
                (item.adapter as? AndroidCanonicalComponentAdapterEntry.Supported)
                    ?.takeIf { it.fidelity == AndroidCanonicalComponentAdapterFidelity.Faithful }
                    ?.let { item.node.type }
            },
            genericUsableComponentTypes = components.mapNotNullTo(linkedSetOf()) { item ->
                (item.adapter as? AndroidCanonicalComponentAdapterEntry.Supported)
                    ?.takeIf { it.fidelity == AndroidCanonicalComponentAdapterFidelity.GenericUsable }
                    ?.let { item.node.type }
            },
            unsupportedComponentTypes = components
                .filter { it.adapter is AndroidCanonicalComponentAdapterEntry.VisibleFailure }
                .mapTo(linkedSetOf()) { it.node.type },
            actionGapCount = (plan as? CanonicalRouteRenderPlan.Ready)?.variant?.actionGaps?.size ?: 0
        ).also { lastObservation = it }
    }
}

internal data class CanonicalComponentAction(
    val componentId: String,
    val binding: ScreenGraphActionBinding
)

internal data class CanonicalStateEventObservation(
    val componentId: String,
    val evidence: ScreenGraphStateEventEvidence
)

internal data class CanonicalComponentRenderModel(
    val routeId: String,
    val variantId: String,
    val node: ScreenGraphComponentNode,
    val routeTitle: String = routeId
) {
    val props: Map<String, JsonElement> get() = node.props
    val bindings: List<ScreenGraphActionBinding> get() = node.bindings
    val stateEventEvidence: List<ScreenGraphStateEventEvidence> get() = node.stateEventEvidence

    /** Generated bindings are executable only when the canonical trigger is explicit. */
    fun bindingObservations(): List<CanonicalComponentAction> =
        bindings.filter { it.trigger.isNotBlank() }.map { CanonicalComponentAction(node.id, it) }

    /** State events are proof/evidence only and are never promoted to callbacks. */
    fun stateEventObservations(): List<CanonicalStateEventObservation> =
        stateEventEvidence.map { CanonicalStateEventObservation(node.id, it) }

    fun triggerableActions(): List<CanonicalComponentAction> = bindingObservations()

    /** Retained source-compatible name; only generated trigger bindings are actions. */
    fun actions(): List<CanonicalComponentAction> = triggerableActions()

    fun accessibility(): CanonicalComponentAccessibility {
        fun string(key: String): String? =
            (props[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val label = listOf("label", "title", "message", "author")
            .firstNotNullOfOrNull(::string)
            ?.takeIf { it.isNotBlank() }
            ?: "$routeTitle · ${node.id}"
        val state = listOf("state", "phase", "variant", "mode", "selected")
            .firstNotNullOfOrNull(::string)
        return CanonicalComponentAccessibility(
            contentDescription = label,
            stateDescription = state,
            enabled = (props["enabled"] as? JsonPrimitive)?.booleanOrNull,
            selected = (props["selected"] as? JsonPrimitive)?.booleanOrNull,
            progress = (props["progress"] as? JsonPrimitive)?.doubleOrNull,
            bindingCount = bindings.size,
            stateEventEvidenceCount = stateEventEvidence.size,
            actionCount = triggerableActions().size
        )
    }

    fun genericDescriptor(): CanonicalGenericRenderDescriptor {
        fun string(key: String): String? =
            (props[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        return CanonicalGenericRenderDescriptor(
            title = listOf("title", "label", "message")
                .firstNotNullOfOrNull(::string)
                ?.takeIf { it.isNotBlank() }
                ?: routeTitle,
            message = string("message"),
            identity = "$variantId · ${node.id}",
            props = props,
            bindingObservations = bindingObservations(),
            stateEventObservations = stateEventObservations(),
            actions = triggerableActions(),
            childIds = node.children.map { it.id },
            accessibility = accessibility()
        )
    }
}

internal data class CanonicalComponentAccessibility(
    val contentDescription: String,
    val stateDescription: String?,
    val enabled: Boolean?,
    val selected: Boolean?,
    val progress: Double?,
    val bindingCount: Int,
    val stateEventEvidenceCount: Int,
    val actionCount: Int
)

internal data class CanonicalGenericRenderDescriptor(
    val title: String,
    val message: String?,
    val identity: String,
    val props: Map<String, JsonElement>,
    val bindingObservations: List<CanonicalComponentAction>,
    val stateEventObservations: List<CanonicalStateEventObservation>,
    val actions: List<CanonicalComponentAction>,
    val childIds: List<String>,
    val accessibility: CanonicalComponentAccessibility
)

@OptIn(ExperimentalSerializationApi::class)
internal fun RouteId.contractRouteId(): String =
    RouteId.serializer().descriptor.getElementName(ordinal)
