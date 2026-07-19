package com.reader.ui.demo

import com.reader.ui.reading.ReaderTapZoneHostState
import com.reader.ui.theme.ReaderThemeHostState
import com.reader.ui.theme.ReaderThemeResolver
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
        assertEquals("1.2.0", graph.document.schemaVersion)
        assertEquals("bf17ea66c21c869b2f8207d252df58d5a727164a1f767ac1edd6eb18b55d851d", ScreenGraphCanonicalAsset.sha256)
        assertEquals(260, graph.document.routes.size)
        assertEquals(190, graph.document.routes.sumOf { it.variants.size })
        assertEquals(615, graph.document.routes.sumOf { route -> route.variants.sumOf { countNodes(it.components) } })
        assertEquals(97, graph.document.routes.sumOf { route -> route.variants.sumOf { countBindings(it.components) } })
        assertEquals(
            19,
            graph.document.routes.sumOf { route ->
                route.variants.sumOf { countStateEventEvidence(it.components) }
            }
        )
        assertEquals(6, graph.document.routes.sumOf { route -> route.variants.sumOf { it.actionGaps.size } })
    }

    @Test
    fun `host composites retain authorities while only TapZones uses a non recursive Host adapter`() {
        val hostCatalog = graph.document.componentCatalog.filter { it.compositionMode == "host-composite" }
        assertEquals(
            setOf(ComponentType.ReaderBase, ComponentType.ReaderTopArea, ComponentType.ReaderBottomBar, ComponentType.TapZones),
            hostCatalog.mapTo(linkedSetOf()) { it.type }
        )
        assertEquals(115, hostCatalog.sumOf { it.instanceCount })

        val readerPlan = planner.plan(CanonicalScreenGraphQuery("reader-background-restore"))
            as CanonicalRouteRenderPlan.Ready
        val readerBase = CanonicalComponentRenderModel(
            routeId = "reader-background-restore",
            variantId = readerPlan.variant.variantId,
            node = readerPlan.variant.components.single { it.type == ComponentType.ReaderBase }
        )
        assertEquals(listOf("core", "reader-ui-runtime", "host-store", "host-layout"), readerBase.node.stateAuthorities)
        assertTrue(readerBase.node.children.isNotEmpty())
        assertTrue(readerBase.requiresHostCompositeAdapter)
        assertFalse(readerBase.allowsContractTreeRecursion)
        val readerBaseGap = adapters.entries.getValue(ComponentType.ReaderBase)
            as AndroidCanonicalComponentAdapterEntry.VisibleFailure
        val tapZonesAdapter = adapters.entries.getValue(ComponentType.TapZones)
            as AndroidCanonicalComponentAdapterEntry.Supported
        assertEquals("ANDROID_COMPONENT_PARTIAL_CANONICAL_DATA", readerBaseGap.code)
        assertEquals(AndroidCanonicalComponentAdapterKind.TapZones, tapZonesAdapter.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.Integrated, tapZonesAdapter.fidelity)
        assertTrue(tapZonesAdapter.provenance.contains("ReaderTapZoneHostAdapter"))
    }

    @Test
    fun `tap zones retain canonical ratios enabled states and region targets`() {
        val zones = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type == ComponentType.TapZones }
                    .map { route.routeId.contractRouteId() to CanonicalComponentRenderModel(
                        routeId = route.routeId.contractRouteId(),
                        variantId = variant.variantId,
                        node = it,
                        routeTitle = route.title
                    ) }
            }
        }.toMap()

        assertEquals(7, zones.size)
        val plannedFormatZones = zones.filterKeys { it in setOf("pdf-reader", "manga-reader") }
        val strictReaderZones = zones - plannedFormatZones.keys
        assertEquals(5, strictReaderZones.size)
        strictReaderZones.values.forEach { model ->
            assertEquals("host-composite", model.node.compositionMode)
            assertEquals(listOf("reader-ui-runtime", "host-layout"), model.node.stateAuthorities)
            assertEquals("horizontal", (model.props.getValue("mode") as JsonPrimitive).content)
            assertEquals(0.26, (model.props.getValue("previousRatio") as JsonPrimitive).content.toDouble(), 0.0)
            assertEquals(0.48, (model.props.getValue("controlRatio") as JsonPrimitive).content.toDouble(), 0.0)
            assertEquals(0.26, (model.props.getValue("nextRatio") as JsonPrimitive).content.toDouble(), 0.0)
            assertFalse(model.allowsContractTreeRecursion)
            assertTrue(model.actions().all { it.target == it.binding.target })
            assertTrue(
                CanonicalTapZoneHostAdapter.adapt(
                    model,
                    ReaderTapZoneHostState(
                        enabled = true,
                        previousEnabled = true,
                        controlEnabled = true,
                        nextEnabled = true
                    )
                ) is CanonicalTapZoneHostAdapterResult.Ready
            )
        }
        assertEquals(setOf("pdf-reader", "manga-reader"), plannedFormatZones.keys)
        plannedFormatZones.forEach { (routeId, model) ->
            assertEquals(setOf("enabled", "mode"), model.props.keys)
            assertEquals(listOf("previous", "control", "next"), model.actions().map { it.target })
            assertTrue(
                CanonicalTapZoneHostAdapter.adapt(model, ReaderTapZoneHostState.Disabled)
                    is CanonicalTapZoneHostAdapterResult.Invalid
            )
            assertTrue(requireNotNull(DemoRouteRegistry.page(routeId)).actions.isEmpty())
        }
        assertTrue(zones.getValue("reader-content-loading").actions().isEmpty())
        assertEquals(
            listOf("previous", "control", "next"),
            zones.getValue("reader-content-offline").actions().map { it.target }
        )
        assertEquals(
            listOf("control", "next"),
            zones.getValue("reader-page-boundary-first").actions().map { it.target }
        )
        assertEquals(
            listOf("previous", "control"),
            zones.getValue("reader-page-boundary-last").actions().map { it.target }
        )

        val fixtureEnabledButHostLoading = CanonicalTapZoneHostAdapter.adapt(
            zones.getValue("reader-content-offline"),
            ReaderTapZoneHostState.Disabled
        ) as CanonicalTapZoneHostAdapterResult.Ready
        assertEquals(
            "fixture enabled=true must not override live loading state",
            ReaderTapZoneHostState.Disabled,
            fixtureEnabledButHostLoading.plan.effectiveState
        )
        val firstPage = CanonicalTapZoneHostAdapter.adapt(
            zones.getValue("reader-page-boundary-first"),
            ReaderTapZoneHostState(true, true, true, true)
        ) as CanonicalTapZoneHostAdapterResult.Ready
        assertFalse("missing previous binding remains fail-closed", firstPage.plan.effectiveState.previousEnabled)
        assertTrue(firstPage.plan.effectiveState.controlEnabled)
        assertTrue(firstPage.plan.effectiveState.nextEnabled)

        val offline = zones.getValue("reader-content-offline")
        val ratioDrift = offline.copy(
            node = offline.node.copy(
                props = offline.props + ("controlRatio" to JsonPrimitive(0.5))
            )
        )
        assertTrue(
            CanonicalTapZoneHostAdapter.adapt(ratioDrift, ReaderTapZoneHostState.Disabled)
                is CanonicalTapZoneHostAdapterResult.Invalid
        )
        val controlBinding = offline.bindings.single { it.target == "control" }
        val payloadDrift = offline.copy(
            node = offline.node.copy(
                bindings = offline.bindings.map { binding ->
                    if (binding.target == "control") binding.copy(payload = emptyMap()) else binding
                }
            )
        )
        assertTrue(controlBinding.payload.isNotEmpty())
        assertTrue(
            CanonicalTapZoneHostAdapter.adapt(payloadDrift, ReaderTapZoneHostState.Disabled)
                is CanonicalTapZoneHostAdapterResult.Invalid
        )
    }

    @Test
    fun `all 21 reading backgrounds use strict schema and live Host paper authority`() {
        val hostTheme = ReaderThemeHostState(
            themeId = "green",
            effectiveThemeId = "green-night",
            isNight = true,
            paper = ReaderThemeResolver.palette("green", true).paper
        )
        val backgrounds = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type == ComponentType.ReaderBase }
                    .flatMap { readerBase ->
                        readerBase.children
                            .filter { it.type == ComponentType.ReadingBackgroundLayer }
                            .map { child ->
                                CanonicalComponentRenderModel(
                                    routeId = route.routeId.contractRouteId(),
                                    variantId = variant.variantId,
                                    node = child,
                                    routeTitle = route.title
                                )
                            }
                    }
            }
        }

        assertEquals(21, backgrounds.size)
        backgrounds.forEach { model ->
            assertEquals("reader-background", model.node.id)
            assertEquals("contract-tree", model.node.compositionMode)
            assertEquals(listOf("host-store"), model.node.stateAuthorities)
            assertEquals(setOf("theme"), model.props.keys)
            assertEquals("paper", (model.props.getValue("theme") as JsonPrimitive).content)
            assertTrue(model.node.children.isEmpty())
            assertTrue(model.bindings.isEmpty())
            assertTrue(model.node.stateEventEvidence.isEmpty())
            assertTrue(model.allowsContractTreeRecursion)

            val adapted = CanonicalReadingBackgroundAdapter.adapt(model, hostTheme)
                as CanonicalReadingBackgroundAdapterResult.Ready
            assertEquals("paper", adapted.plan.fixtureTheme)
            assertEquals(
                CanonicalReadingBackgroundLayoutBoundary.HostProvided,
                adapted.plan.layoutBoundary
            )
            assertEquals(
                "fixture paper must not replace the live Host theme",
                "green-night",
                adapted.plan.hostTheme.effectiveThemeId
            )
            assertEquals(hostTheme.paper, adapted.plan.hostTheme.paper)
        }

        val canonical = backgrounds.first()
        val extraProp = canonical.copy(
            node = canonical.node.copy(props = canonical.props + ("color" to JsonPrimitive("#ffffff")))
        )
        val knownButNonCanonicalFixture = canonical.copy(
            node = canonical.node.copy(props = canonical.props + ("theme" to JsonPrimitive("warm")))
        )
        val unknownFixture = canonical.copy(
            node = canonical.node.copy(props = canonical.props + ("theme" to JsonPrimitive("unknown")))
        )
        val wrongAuthority = canonical.copy(
            node = canonical.node.copy(stateAuthorities = listOf("contract"))
        )
        val unknownHost = hostTheme.copy(
            themeId = "unknown",
            effectiveThemeId = "unknown-night"
        )

        assertTrue(
            CanonicalReadingBackgroundAdapter.adapt(extraProp, hostTheme)
                is CanonicalReadingBackgroundAdapterResult.Invalid
        )
        assertTrue(
            CanonicalReadingBackgroundAdapter.adapt(knownButNonCanonicalFixture, hostTheme)
                is CanonicalReadingBackgroundAdapterResult.Invalid
        )
        assertTrue(
            CanonicalReadingBackgroundAdapter.adapt(unknownFixture, hostTheme)
                is CanonicalReadingBackgroundAdapterResult.Invalid
        )
        assertTrue(
            CanonicalReadingBackgroundAdapter.adapt(wrongAuthority, hostTheme)
                is CanonicalReadingBackgroundAdapterResult.Invalid
        )
        assertTrue(
            CanonicalReadingBackgroundAdapter.adapt(canonical, unknownHost)
                is CanonicalReadingBackgroundAdapterResult.Invalid
        )
    }

    @Test
    fun `adapter registry exact-set equals 138 referenced and excludes 36 explicit gaps`() {
        val canonicalReferenced = graph.document.componentCatalog
            .filter { it.status == ScreenGraphComponentCatalogStatus.Referenced }
            .mapTo(linkedSetOf()) { it.type }
        val canonicalGaps = graph.document.componentCatalog
            .filter { it.status == ScreenGraphComponentCatalogStatus.ExplicitGap }
            .mapTo(linkedSetOf()) { it.type }

        assertEquals(138, canonicalReferenced.size)
        assertEquals(36, canonicalGaps.size)
        assertEquals(canonicalReferenced, adapters.entries.keys)
        assertEquals(canonicalReferenced, adapters.coverage.referencedTypes)
        assertEquals(canonicalGaps, adapters.coverage.explicitGapTypes)
        assertTrue((adapters.entries.keys intersect canonicalGaps).isEmpty())
        assertEquals(138, adapters.coverage.supportedTypes.size + adapters.coverage.visibleFailureTypes.size)
        assertEquals(67, adapters.coverage.supportedTypes.size)
        assertEquals(19, adapters.coverage.faithfulTypes.size)
        assertEquals(46, adapters.coverage.genericUsableTypes.size)
        assertEquals(
            setOf(ComponentType.TapZones, ComponentType.ReadingBackgroundLayer),
            adapters.coverage.integratedTypes
        )
        assertEquals(6, adapters.coverage.partialTypes.size)
        assertEquals(65, adapters.coverage.insufficientTypes.size)
        assertEquals(71, adapters.coverage.visibleFailureTypes.size)
        assertEquals(71, adapters.referencedVsAdapterGaps.size)
        assertEquals(36, adapters.canonicalExplicitGaps.size)
        assertEquals(615, adapters.coverage.canonicalInstanceCount)
        assertEquals(262, adapters.coverage.faithfulInstanceCount)
        assertEquals(104, adapters.coverage.genericUsableInstanceCount)
        assertEquals(28, adapters.coverage.integratedInstanceCount)
        assertEquals(85, adapters.coverage.partialInstanceCount)
        assertEquals(136, adapters.coverage.insufficientInstanceCount)
        assertFalse(adapters.coverage.isAdapterRegistryClosed)
        assertFalse("R16 must not claim a full 138-primitive renderer", adapters.coverage.isFullRenderer)
    }

    @Test
    fun `all 260 generated RouteIds query to a visible plan without Android route table copy`() {
        val plans = RouteId.entries.map { routeId ->
            planner.plan(CanonicalScreenGraphQuery(routeId.contractRouteId()))
        }

        assertEquals(260, plans.size)
        assertTrue(plans.all { it is CanonicalRouteRenderPlan.Ready })
        val ready = plans.filterIsInstance<CanonicalRouteRenderPlan.Ready>()
        assertEquals(76, ready.count { it.requestedRoute.status == ScreenGraphRouteStatus.Alias })
        assertEquals(184, ready.count { it.requestedRoute.status == ScreenGraphRouteStatus.Direct })
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
                "1:BookGrid#book-collection",
                "2:BookCard#book-bk-001",
                "2:BookCard#book-bk-002",
                "2:BookCard#book-bk-003",
                "2:BookCard#book-bk-004",
                "2:BookCard#book-bk-005",
                "2:BookCard#book-bk-006",
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
        assertEquals("self", action.target)
        assertEquals(confirm.props, model.props)
        assertEquals(confirm.bindings, model.bindings)
        assertEquals("确认切换", model.accessibility().contentDescription)
        assertEquals(1, model.accessibility().bindingCount)
        assertEquals(0, model.accessibility().stateEventEvidenceCount)
        assertEquals(1, model.accessibility().actionCount)
    }

    @Test
    fun `every referenced type is faithful generic integrated or a visible gap`() {
        val supported = adapters.entries.mapNotNull { (type, entry) ->
            (entry as? AndroidCanonicalComponentAdapterEntry.Supported)?.let { type to it }
        }.toMap()
        val visibleGaps = adapters.entries.mapNotNull { (type, entry) ->
            (entry as? AndroidCanonicalComponentAdapterEntry.VisibleFailure)?.let { type to it }
        }.toMap()

        assertEquals(67, supported.size)
        assertEquals(71, visibleGaps.size)
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
        assertEquals(
            adapters.coverage.integratedTypes,
            supported.filterValues {
                it.fidelity == AndroidCanonicalComponentAdapterFidelity.Integrated
            }.keys
        )
        val tapZones = supported.getValue(ComponentType.TapZones)
        assertEquals(AndroidCanonicalComponentAdapterKind.TapZones, tapZones.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.Integrated, tapZones.fidelity)
        val readingBackground = supported.getValue(ComponentType.ReadingBackgroundLayer)
        assertEquals(AndroidCanonicalComponentAdapterKind.ReadingBackground, readingBackground.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.Integrated, readingBackground.fidelity)
        assertTrue(readingBackground.provenance.contains("full-surface bounds remain Host-owned"))
        val aboutVersion = supported.getValue(ComponentType.AboutVersionPage)
        assertEquals(AndroidCanonicalComponentAdapterKind.GenericText, aboutVersion.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.GenericUsable, aboutVersion.fidelity)
        val sourceDebugResult = supported.getValue(ComponentType.SourceDebugResultPage)
        assertEquals(AndroidCanonicalComponentAdapterKind.SourceDebugResultPage, sourceDebugResult.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.GenericUsable, sourceDebugResult.fidelity)
        val sourceRuleEdit = supported.getValue(ComponentType.SourceRuleEditPage)
        assertEquals(AndroidCanonicalComponentAdapterKind.SourceRuleEditPage, sourceRuleEdit.kind)
        assertEquals(AndroidCanonicalComponentAdapterFidelity.GenericUsable, sourceRuleEdit.fidelity)
        val sourceContentTypes = setOf(
            ComponentType.SourceDetailPage,
            ComponentType.SourceBatchPage,
            ComponentType.SourceGroupsPage,
            ComponentType.SourceLogsPage,
            ComponentType.SourceDetectPage,
            ComponentType.SourceDebugPage,
            ComponentType.SourceCodeViewPage,
            ComponentType.SourceDebugContentLogPage
        )
        assertTrue(sourceContentTypes.all {
            supported.getValue(it).kind == AndroidCanonicalComponentAdapterKind.SourceDemoContentPage
        })
        val restoreTypes = setOf(
            ComponentType.RestoreConfirmPage,
            ComponentType.RestoreProgressPage,
            ComponentType.RestoreResultPage
        )
        assertTrue(restoreTypes.all {
            supported.getValue(it).kind == AndroidCanonicalComponentAdapterKind.RestoreReadOnlyPage
        })
        val settingsTypes = setOf(
            ComponentType.SettingsGeneralPage,
            ComponentType.AboutFeedbackPage,
            ComponentType.BookshelfSearchSettingsPage
        )
        assertTrue(settingsTypes.all {
            supported.getValue(it).kind == AndroidCanonicalComponentAdapterKind.SettingsReadOnlyPage
        })
        val contract30ReadOnlyTypes = setOf(
            ComponentType.ProgressBar,
            ComponentType.SettingsListItem,
            ComponentType.BookCover,
            ComponentType.ListRow,
            ComponentType.Toggle,
            ComponentType.Slider,
            ComponentType.Dropdown,
            ComponentType.Input,
            ComponentType.WebView,
            ComponentType.SourceFormPage
        )
        assertTrue(contract30ReadOnlyTypes.all {
            supported.getValue(it).kind == AndroidCanonicalComponentAdapterKind.GenericStructure &&
                supported.getValue(it).fidelity == AndroidCanonicalComponentAdapterFidelity.GenericUsable
        })
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
    fun `sync backup and rss candidates remain precise gaps until canonical data and actions exist`() {
        val candidateCounts = mapOf(
            ComponentType.SyncBackupPage to 3,
            ComponentType.RssSearchEntry to 2,
            ComponentType.RssArticleSection to 2,
            ComponentType.RssSourceEditPage to 2
        )
        val expectedReasons = mapOf(
            ComponentType.SyncBackupPage to
                "3 canonical instances expose only route/loading variant; server, account, directory, connection status, backup rows, and executable sync/network bindings are absent",
            ComponentType.RssSearchEntry to
                "2 canonical instances expose no props, children, or bindings; native search entry requires an explicit search-navigation callback",
            ComponentType.RssArticleSection to
                "2 canonical instances expose no article props/children or bindings; native article sections require article data and an open-article callback",
            ComponentType.RssSourceEditPage to
                "2 canonical instances expose add/edit mode only; field values and debug/save/cancel bindings required by the native editor are absent"
        )
        val models = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type in candidateCounts }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(9, models.size)
        assertEquals(candidateCounts, models.groupingBy { it.node.type }.eachCount())
        assertTrue(models.all {
            it.node.children.isEmpty() && it.bindings.isEmpty() && it.stateEventEvidence.isEmpty()
        })
        candidateCounts.keys.forEach { type ->
            assertTrue(type in adapters.coverage.insufficientTypes)
            assertFalse(type in adapters.coverage.supportedTypes)
            val entry = adapters.entries.getValue(type) as AndroidCanonicalComponentAdapterEntry.VisibleFailure
            assertEquals("ANDROID_COMPONENT_CONTRACT_DATA_INSUFFICIENT", entry.code)
            assertEquals(expectedReasons.getValue(type), entry.reason)
        }

        fun propsAt(routeId: String, variantId: String, type: ComponentType) =
            models.single {
                it.routeId == routeId && it.variantId == variantId && it.node.type == type
            }.props

        assertTrue(propsAt("sync-backup", "default", ComponentType.SyncBackupPage).isEmpty())
        assertEquals(
            mapOf("variant" to JsonPrimitive("loading")),
            propsAt("sync-backup", "loading", ComponentType.SyncBackupPage)
        )
        assertTrue(propsAt("webdav-config", "default", ComponentType.SyncBackupPage).isEmpty())
        assertTrue(propsAt("rss", "default", ComponentType.RssSearchEntry).isEmpty())
        assertTrue(propsAt("rss-search", "default", ComponentType.RssSearchEntry).isEmpty())
        assertTrue(propsAt("rss", "default", ComponentType.RssArticleSection).isEmpty())
        assertTrue(propsAt("rss-starred", "default", ComponentType.RssArticleSection).isEmpty())
        assertEquals(
            mapOf("mode" to JsonPrimitive("add")),
            propsAt("rss-source-add", "default", ComponentType.RssSourceEditPage)
        )
        assertEquals(
            mapOf("mode" to JsonPrimitive("edit")),
            propsAt("rss-source-edit", "default", ComponentType.RssSourceEditPage)
        )
    }

    @Test
    fun `all canonical source debug result instances bridge their typed variant to Android content state`() {
        val resultModels = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type == ComponentType.SourceDebugResultPage }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(4, resultModels.size)
        assertEquals(
            setOf("source-debug-result", "source-debug-search-result", "source-debug-detail-result", "source-debug-catalog-result"),
            resultModels.mapTo(linkedSetOf()) { it.routeId }
        )
        assertTrue(resultModels.all { it.node.children.isEmpty() && it.bindings.isEmpty() })
        assertTrue(resultModels.all { it.sourceDebugResultAdapterState() != null })
        assertEquals(
            listOf("search", "search", "detail", "catalog"),
            resultModels.map { it.sourceDebugResultAdapterState()!!.activeModuleKey }
        )
    }

    @Test
    fun `all canonical source rule edit instances strictly bridge route props without host actions`() {
        val ruleEditModels = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type == ComponentType.SourceRuleEditPage }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(3, ruleEditModels.size)
        assertEquals(
            setOf("source-edit", "source-rule-edit", "source-edit-debug"),
            ruleEditModels.mapTo(linkedSetOf()) { it.routeId }
        )
        assertTrue(ruleEditModels.all { it.node.children.isEmpty() && it.bindings.isEmpty() })
        assertTrue(ruleEditModels.all { it.sourceRuleEditAdapterState() != null })
        assertTrue(ruleEditModels.all { it.sourceRuleEditAdapterState()!!.actions.isNotEmpty() })
        assertTrue(ruleEditModels.all { it.actions().isEmpty() })
    }

    @Test
    fun `source content family strictly bridges eight native pages without canonical host actions`() {
        val sourceContentTypes = setOf(
            ComponentType.SourceDetailPage,
            ComponentType.SourceBatchPage,
            ComponentType.SourceGroupsPage,
            ComponentType.SourceLogsPage,
            ComponentType.SourceDetectPage,
            ComponentType.SourceDebugPage,
            ComponentType.SourceCodeViewPage,
            ComponentType.SourceDebugContentLogPage
        )
        val models = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type in sourceContentTypes }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(8, models.size)
        assertEquals(sourceContentTypes, models.mapTo(linkedSetOf()) { it.node.type })
        assertTrue(models.all {
            it.node.children.isEmpty() && it.bindings.isEmpty() && it.stateEventEvidence.isEmpty()
        })
        assertTrue(models.all { it.sourceDemoContentAdapterState() != null })
        assertTrue(models.all { it.actions().isEmpty() })

        val detail = models.single { it.node.type == ComponentType.SourceDetailPage }
        val unexpectedProp = detail.copy(
            node = detail.node.copy(props = detail.props + ("unexpected" to JsonPrimitive(true)))
        )
        assertEquals(null, unexpectedProp.sourceDemoContentAdapterState())
    }

    @Test
    fun `restore family strictly bridges six read only instances without invented actions`() {
        val restoreTypes = setOf(
            ComponentType.RestoreConfirmPage,
            ComponentType.RestoreProgressPage,
            ComponentType.RestoreResultPage
        )
        val models = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type in restoreTypes }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(6, models.size)
        assertEquals(restoreTypes, models.mapTo(linkedSetOf()) { it.node.type })
        assertTrue(models.all {
            it.node.children.isEmpty() && it.bindings.isEmpty() && it.stateEventEvidence.isEmpty()
        })
        assertTrue(models.all { it.restoreReadOnlyAdapterPage() != null })
        assertTrue(models.all { it.actions().isEmpty() })
        assertEquals(
            mapOf("restore-confirm" to 3, "restore-progress" to 2, "restore-result" to 1),
            models.groupingBy { it.restoreReadOnlyAdapterPage()!!.routeId }.eachCount()
        )

        val progress = models.single { it.routeId == "restore-progress" }
        val unexpectedBinding = progress.copy(
            node = progress.node.copy(bindings = listOf(graph.document.routes
                .asSequence()
                .flatMap { it.variants.asSequence() }
                .flatMap { flatten(it.components).asSequence() }
                .first { it.bindings.isNotEmpty() }
                .bindings.first()))
        )
        assertEquals(null, unexpectedBinding.restoreReadOnlyAdapterPage())
    }

    @Test
    fun `settings family keeps five strict pages and four capability pages fail closed`() {
        val settingsTypes = setOf(
            ComponentType.SettingsGeneralPage,
            ComponentType.AboutFeedbackPage,
            ComponentType.BookshelfSearchSettingsPage
        )
        val models = graph.document.routes.flatMap { route ->
            route.variants.flatMap { variant ->
                flatten(variant.components)
                    .filter { it.type in settingsTypes }
                    .map { node ->
                        CanonicalComponentRenderModel(
                            routeId = route.routeId.contractRouteId(),
                            variantId = variant.variantId,
                            node = node,
                            routeTitle = route.title
                        )
                    }
            }
        }

        assertEquals(9, models.size)
        assertEquals(settingsTypes, models.mapTo(linkedSetOf()) { it.node.type })
        val strictModels = models.filter { it.settingsReadOnlyAdapterPage() != null }
        assertEquals(5, strictModels.size)
        assertTrue(strictModels.all {
            it.node.children.isEmpty() && it.bindings.isEmpty() && it.stateEventEvidence.isEmpty()
        })
        assertTrue(strictModels.all { it.actions().isEmpty() })
        assertEquals(
            mapOf(
                CanonicalSettingsReadOnlyPage.General to 1,
                CanonicalSettingsReadOnlyPage.DeveloperMotion to 1,
                CanonicalSettingsReadOnlyPage.AboutFeedback to 2,
                CanonicalSettingsReadOnlyPage.BookshelfSearch to 1
            ),
            strictModels.groupingBy { it.settingsReadOnlyAdapterPage()!! }.eachCount()
        )

        val capabilityModels = models - strictModels.toSet()
        assertEquals(
            setOf("http-tts-management", "settings-tts", "settings-storage", "settings-accessibility"),
            capabilityModels.mapTo(linkedSetOf()) { it.routeId }
        )
        assertTrue(capabilityModels.all { it.node.children.isNotEmpty() && it.bindings.isNotEmpty() })
        assertTrue(capabilityModels.all { it.settingsReadOnlyAdapterPage() == null })
        assertTrue(capabilityModels.all {
            requireNotNull(DemoRouteRegistry.page(it.routeId)).actions.isEmpty()
        })

        val general = models.single { it.routeId == "settings-general" }
        val unexpectedChild = general.copy(node = general.node.copy(children = listOf(
            graph.document.routes.asSequence()
                .flatMap { it.variants.asSequence() }
                .flatMap { flatten(it.components).asSequence() }
                .first()
        )))
        assertEquals(null, unexpectedChild.settingsReadOnlyAdapterPage())
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
    fun `36 canonical explicit gaps remain visible and never appear as rendered nodes`() {
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
