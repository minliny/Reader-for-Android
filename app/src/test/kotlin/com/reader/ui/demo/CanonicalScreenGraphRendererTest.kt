package com.reader.ui.demo

import io.reader.ui.contract.ComponentType
import io.reader.ui.contract.PageState
import io.reader.ui.contract.RouteId
import io.reader.ui.contract.ScreenGraphCanonicalAsset
import io.reader.ui.contract.ScreenGraphComponentCatalogStatus
import io.reader.ui.contract.ScreenGraphComponentNode
import io.reader.ui.contract.ScreenGraphRegistry
import io.reader.ui.contract.ScreenGraphRouteStatus
import io.reader.ui.contract.UiEventType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalScreenGraphRendererTest {
    private val graph by lazy(ScreenGraphRegistry::loadCanonical)
    private val planner by lazy(CanonicalScreenGraphPlanner::loadCanonical)
    private val adapters by lazy(AndroidCanonicalComponentAdapterRegistry::loadCanonical)

    @Test
    fun `generated canonical identity and aggregate counts are consumed directly`() {
        assertEquals("1.1.0", graph.document.schemaVersion)
        assertEquals("6a9c9623d5cbef3a4a1fb1625b9503b3b20bbe01b74a4e16c6df34a0cd8f8f02", ScreenGraphCanonicalAsset.sha256)
        assertEquals(235, graph.document.routes.size)
        assertEquals(165, graph.document.routes.sumOf { it.variants.size })
        assertEquals(519, graph.document.routes.sumOf { route -> route.variants.sumOf { countNodes(it.components) } })
        assertEquals(36, graph.document.routes.sumOf { route -> route.variants.sumOf { countBindings(it.components) } })
        assertEquals(
            19,
            graph.document.routes.sumOf { route ->
                route.variants.sumOf { countStateEventEvidence(it.components) }
            }
        )
        assertEquals(6, graph.document.routes.sumOf { route -> route.variants.sumOf { it.actionGaps.size } })
    }

    @Test
    fun `adapter registry exact-set equals 129 referenced and excludes 45 explicit gaps`() {
        val canonicalReferenced = graph.document.componentCatalog
            .filter { it.status == ScreenGraphComponentCatalogStatus.Referenced }
            .mapTo(linkedSetOf()) { it.type }
        val canonicalGaps = graph.document.componentCatalog
            .filter { it.status == ScreenGraphComponentCatalogStatus.ExplicitGap }
            .mapTo(linkedSetOf()) { it.type }

        assertEquals(129, canonicalReferenced.size)
        assertEquals(45, canonicalGaps.size)
        assertEquals(canonicalReferenced, adapters.entries.keys)
        assertEquals(canonicalReferenced, adapters.coverage.referencedTypes)
        assertEquals(canonicalGaps, adapters.coverage.explicitGapTypes)
        assertTrue((adapters.entries.keys intersect canonicalGaps).isEmpty())
        assertEquals(129, adapters.coverage.supportedTypes.size + adapters.coverage.visibleFailureTypes.size)
        assertEquals(39, adapters.coverage.supportedTypes.size)
        assertEquals(18, adapters.coverage.faithfulTypes.size)
        assertEquals(21, adapters.coverage.genericUsableTypes.size)
        assertEquals(6, adapters.coverage.partialTypes.size)
        assertEquals(84, adapters.coverage.insufficientTypes.size)
        assertEquals(90, adapters.coverage.visibleFailureTypes.size)
        assertEquals(90, adapters.referencedVsAdapterGaps.size)
        assertEquals(45, adapters.canonicalExplicitGaps.size)
        assertFalse(adapters.coverage.isAdapterRegistryClosed)
        assertFalse("R16 must not claim a full 129-primitive renderer", adapters.coverage.isFullRenderer)
    }

    @Test
    fun `all 235 generated RouteIds query to a visible plan without Android route table copy`() {
        val plans = RouteId.entries.map { routeId ->
            planner.plan(CanonicalScreenGraphQuery(routeId.contractRouteId()))
        }

        assertEquals(235, plans.size)
        assertTrue(plans.all { it is CanonicalRouteRenderPlan.Ready })
        val ready = plans.filterIsInstance<CanonicalRouteRenderPlan.Ready>()
        assertEquals(76, ready.count { it.requestedRoute.status == ScreenGraphRouteStatus.Alias })
        assertEquals(159, ready.count { it.requestedRoute.status == ScreenGraphRouteStatus.Direct })
        assertTrue(ready.all { it.aliasPath.last().status == ScreenGraphRouteStatus.Direct })
    }

    @Test
    fun `alias resolves to direct route and reuses its selected variant`() {
        val alias = planner.plan(CanonicalScreenGraphQuery("discover-refreshing"))
            as CanonicalRouteRenderPlan.Ready
        val direct = planner.plan(CanonicalScreenGraphQuery("discover"))
            as CanonicalRouteRenderPlan.Ready

        assertEquals("discover-refreshing", alias.requestedRoute.routeId.contractRouteId())
        assertEquals("discover", alias.resolvedRoute.routeId.contractRouteId())
        assertEquals(listOf("discover-refreshing", "discover"), alias.aliasPath.map { it.routeId.contractRouteId() })
        assertEquals(direct.variant, alias.variant)
    }

    @Test
    fun `variant selection honors explicit id page state and typed context`() {
        val loading = planner.plan(
            CanonicalScreenGraphQuery(routeId = "book-detail", pageState = PageState.Loading)
        ) as CanonicalRouteRenderPlan.Ready
        val explicit = planner.plan(
            CanonicalScreenGraphQuery(routeId = "reader", variantId = "offline")
        ) as CanonicalRouteRenderPlan.Ready
        val context = planner.plan(
            CanonicalScreenGraphQuery(
                routeId = "source-management",
                context = mapOf("sourceId" to JsonPrimitive("source-disabled"))
            )
        ) as CanonicalRouteRenderPlan.Ready

        assertEquals("loading", loading.variant.variantId)
        assertEquals(PageState.Offline, explicit.variant.pageState)
        assertEquals("source-unavailable", context.variant.variantId)
        assertTrue(
            planner.plan(CanonicalScreenGraphQuery("reader", variantId = "missing"))
                is CanonicalRouteRenderPlan.VisibleFailure
        )
    }

    @Test
    fun `recursive children are observed in canonical preorder with adapter decisions`() {
        val observation = CanonicalScreenGraphShadowObserver.observe(
            CanonicalScreenGraphQuery("bookshelf")
        )
        val signature = observation.components.map { item ->
            "${item.depth}:${item.node.type.name}#${item.node.id}"
        }

        assertEquals(
            listOf(
                "0:AppTopBar#bookshelf-topbar",
                "0:ContinueReadingCard#continue-reading",
                "0:BookshelfShelfSection#bookshelf-shelf-section",
                "1:ShelfSectionHeader#shelf-header",
                "1:BookGrid#book-grid",
                "2:BookCard#book-1",
                "2:BookCard#book-2",
                "2:BookCard#book-3",
                "2:BookCard#book-4",
                "2:BookCard#book-5",
                "2:BookCard#book-6",
                "0:BottomNav#bottom-nav"
            ),
            signature
        )
        assertTrue(ComponentType.ContinueReadingCard in observation.genericUsableComponentTypes)
        assertTrue(ComponentType.BookCard in observation.faithfulComponentTypes)
        assertTrue(observation.unsupportedComponentTypes.isEmpty())
    }

    @Test
    fun `typed props and nested action payload pass through without scalar projection`() {
        val plan = planner.plan(CanonicalScreenGraphQuery("source-switch-preview"))
            as CanonicalRouteRenderPlan.Ready
        val confirm = flatten(plan.variant.components)
            .single { it.id == "source_switch_preview-action" }
        val model = CanonicalComponentRenderModel(
            routeId = "source-switch-preview",
            variantId = plan.variant.variantId,
            node = confirm
        )
        val action = model.actions().single()

        assertEquals(UiEventType.SourceSwitchConfirm, action.binding.event)
        assertTrue(action.binding.payload["from"] is JsonObject)
        assertTrue(action.binding.payload["target"] is JsonObject)
        assertTrue(action.binding.payload["newToc"] is JsonArray)
        val chapterIndex = action.binding.payload["currentChapterIndex"] as JsonPrimitive
        assertFalse(chapterIndex.isString)
        assertEquals(0, chapterIndex.content.toInt())
        assertEquals(JsonNull, (action.binding.payload["target"] as JsonObject)["coverUrl"])
        assertEquals("tap", action.binding.trigger)
        assertEquals(confirm.props, model.props)
        assertEquals(confirm.bindings, model.bindings)
        assertEquals("确认切换", model.accessibility().contentDescription)
        assertEquals(1, model.accessibility().bindingCount)
        assertEquals(0, model.accessibility().stateEventEvidenceCount)
        assertEquals(1, model.accessibility().actionCount)
    }

    @Test
    fun `every referenced type is explicitly faithful generic usable or visible gap`() {
        val supported = adapters.entries.mapNotNull { (type, entry) ->
            (entry as? AndroidCanonicalComponentAdapterEntry.Supported)?.let { type to it }
        }.toMap()
        val visibleGaps = adapters.entries.mapNotNull { (type, entry) ->
            (entry as? AndroidCanonicalComponentAdapterEntry.VisibleFailure)?.let { type to it }
        }.toMap()

        assertEquals(39, supported.size)
        assertEquals(90, visibleGaps.size)
        assertEquals(adapters.coverage.referencedTypes, supported.keys + visibleGaps.keys)
        assertTrue((supported.keys intersect visibleGaps.keys).isEmpty())
        assertTrue(supported.values.all { it.provenance.isNotBlank() })
        assertEquals(
            adapters.coverage.faithfulTypes,
            supported.filterValues {
                it.fidelity == AndroidCanonicalComponentAdapterFidelity.Faithful
            }.keys
        )
        assertEquals(
            adapters.coverage.genericUsableTypes,
            supported.filterValues {
                it.fidelity == AndroidCanonicalComponentAdapterFidelity.GenericUsable
            }.keys
        )
        val aboutVersion = supported.getValue(ComponentType.AboutVersionPage)
        assertEquals(AndroidCanonicalComponentAdapterKind.GenericText, aboutVersion.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.GenericUsable, aboutVersion.fidelity)
        assertEquals(
            "ANDROID_COMPONENT_PARTIAL_CANONICAL_DATA",
            visibleGaps.getValue(ComponentType.ReaderBase).code
        )
        assertEquals(
            "ANDROID_COMPONENT_CONTRACT_DATA_INSUFFICIENT",
            visibleGaps.getValue(ComponentType.RssAllPage).code
        )
    }

    @Test
    fun `canonical props derive stable accessibility semantics without invented scalar data`() {
        val plan = planner.plan(CanonicalScreenGraphQuery("discover-no-results"))
            as CanonicalRouteRenderPlan.Ready
        val node = flatten(plan.variant.components).single { it.id == "discover-no-results" }
        val model = CanonicalComponentRenderModel(
            routeId = "discover-no-results",
            variantId = plan.variant.variantId,
            node = node,
            routeTitle = plan.requestedRoute.title
        )
        val semantics = model.accessibility()

        assertEquals("当前条件没有发现结果", semantics.contentDescription)
        assertEquals("no-results", semantics.stateDescription)
        assertEquals(1, semantics.actionCount)
        assertEquals(null, semantics.enabled)
        assertEquals(null, semantics.progress)
        assertEquals(1, semantics.bindingCount)
        assertEquals(0, semantics.stateEventEvidenceCount)
        assertEquals(node.bindings.single(), model.actions().single().binding)
    }

    @Test
    fun `generic usable descriptor renders route data props children and typed actions not type labels`() {
        val discoverPlan = planner.plan(CanonicalScreenGraphQuery("discover-no-results"))
            as CanonicalRouteRenderPlan.Ready
        val discoverNode = flatten(discoverPlan.variant.components)
            .single { it.id == "discover-no-results" }
        val discover = CanonicalComponentRenderModel(
            routeId = "discover-no-results",
            variantId = discoverPlan.variant.variantId,
            node = discoverNode,
            routeTitle = discoverPlan.requestedRoute.title
        ).genericDescriptor()

        assertEquals("当前条件没有发现结果", discover.title)
        assertEquals("可以重置筛选、切换入口，或刷新当前书源。", discover.message)
        assertEquals(discoverNode.props, discover.props)
        assertEquals(discoverNode.bindings.single(), discover.actions.single().binding)
        assertFalse(discover.title.contains(ComponentType.DiscoverStatePage.name))

        val aboutPlan = planner.plan(CanonicalScreenGraphQuery("about-version"))
            as CanonicalRouteRenderPlan.Ready
        val aboutNode = flatten(aboutPlan.variant.components)
            .single { it.type == ComponentType.AboutVersionPage }
        val about = CanonicalComponentRenderModel(
            routeId = "about-version",
            variantId = aboutPlan.variant.variantId,
            node = aboutNode,
            routeTitle = aboutPlan.requestedRoute.title
        ).genericDescriptor()
        assertEquals("Reader for Android", about.title)
        assertEquals("default · about-version-page", about.identity)
        assertEquals("1.0.0", (about.props["version"] as JsonPrimitive).content)
        assertTrue(about.actions.isEmpty())
        assertTrue(about.stateEventObservations.isEmpty())
    }

    @Test
    fun `state event evidence is audited but never exposed as an executable callback`() {
        val plan = planner.plan(CanonicalScreenGraphQuery("reader-background-restore"))
            as CanonicalRouteRenderPlan.Ready
        val node = flatten(plan.variant.components)
            .single { it.id == "reader-background-restore-toast" }
        val model = CanonicalComponentRenderModel(
            routeId = "reader-background-restore",
            variantId = plan.variant.variantId,
            node = node,
            routeTitle = plan.requestedRoute.title
        )

        assertTrue(node.bindings.isEmpty())
        assertEquals(1, node.stateEventEvidence.size)
        assertTrue(model.actions().isEmpty())
        assertTrue(model.triggerableActions().isEmpty())
        assertEquals(node.stateEventEvidence.single(), model.stateEventObservations().single().evidence)
        assertEquals(0, model.accessibility().bindingCount)
        assertEquals(1, model.accessibility().stateEventEvidenceCount)
        assertEquals(0, model.accessibility().actionCount)
    }

    @Test
    fun `unknown route and unregistered primitive produce visible failures`() {
        val unknownRoute = planner.plan(CanonicalScreenGraphQuery("android-private-route"))
        assertTrue(unknownRoute is CanonicalRouteRenderPlan.VisibleFailure)
        assertEquals(
            "CANONICAL_ROUTE_UNKNOWN",
            (unknownRoute as CanonicalRouteRenderPlan.VisibleFailure).code
        )

        val missingAdapter = AndroidCanonicalComponentAdapterRegistry.lookupEntry(
            ComponentType.AppTopBar,
            emptyMap()
        )
        assertTrue(missingAdapter is AndroidCanonicalComponentAdapterEntry.VisibleFailure)
        assertEquals(
            "ANDROID_COMPONENT_ADAPTER_UNREGISTERED",
            (missingAdapter as AndroidCanonicalComponentAdapterEntry.VisibleFailure).code
        )
    }

    @Test
    fun `45 canonical explicit gaps remain visible and never appear as rendered nodes`() {
        val explicitGapEntry = adapters.entry(ComponentType.SearchEntry)
        assertTrue(explicitGapEntry is AndroidCanonicalComponentAdapterEntry.VisibleFailure)
        assertEquals(
            "CANONICAL_EXPLICIT_COMPONENT_GAP",
            (explicitGapEntry as AndroidCanonicalComponentAdapterEntry.VisibleFailure).code
        )

        val renderedTypes = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant -> flatten(variant.components).map { it.type } }
        }.toSet()
        assertTrue((renderedTypes intersect adapters.coverage.explicitGapTypes).isEmpty())
        assertEquals(adapters.coverage.referencedTypes, renderedTypes)
    }

    @Test
    fun `representative direct state snapshot stays in parity with canonical graph`() {
        val plan = planner.plan(CanonicalScreenGraphQuery("discover-no-results"))
            as CanonicalRouteRenderPlan.Ready
        val nodes = flatten(plan.variant.components)
        val state = nodes.single { it.id == "discover-no-results" }

        assertEquals(PageState.Empty, plan.variant.pageState)
        assertEquals(ComponentType.DiscoverStatePage, state.type)
        assertEquals("当前条件没有发现结果", (state.props["title"] as JsonPrimitive).content)
        assertEquals(UiEventType.DiscoverFilterReset, state.bindings.single().event)
        assertTrue(state.bindings.single().payload.isEmpty())
        assertNotNull(nodes.singleOrNull { it.type == ComponentType.AppTopBar })
        assertNotNull(nodes.singleOrNull { it.type == ComponentType.BottomNav })
    }

    private fun countNodes(nodes: List<ScreenGraphComponentNode>): Int =
        nodes.sumOf { 1 + countNodes(it.children) }

    private fun countBindings(nodes: List<ScreenGraphComponentNode>): Int =
        nodes.sumOf { it.bindings.size + countBindings(it.children) }

    private fun countStateEventEvidence(nodes: List<ScreenGraphComponentNode>): Int =
        nodes.sumOf { it.stateEventEvidence.size + countStateEventEvidence(it.children) }

    private fun flatten(nodes: List<ScreenGraphComponentNode>): List<ScreenGraphComponentNode> =
        buildList {
            fun walk(items: List<ScreenGraphComponentNode>) {
                items.forEach { item ->
                    add(item)
                    walk(item.children)
                }
            }
            walk(nodes)
        }
}
