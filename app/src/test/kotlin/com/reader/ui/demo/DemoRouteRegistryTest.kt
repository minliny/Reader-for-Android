package com.reader.ui.demo

import com.reader.ui.shell.ReaderRoute
import io.reader.ui.contract.RouteId
import io.reader.ui.contract.RouteShell
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generated-contract verification for [DemoRouteRegistry].
 *
 * Verifies:
 * - Registry contains exactly the generated RouteId wire-name set
 * - All route ids are unique
 * - All route ids match contract RouteId enum count
 * - shellFor returns the correct shell for known route ids
 * - shellFor returns null for unknown route ids
 * - All pages have non-empty title and body
 * - Shell distribution covers all 5 shell types
 *
 * Contract source: generated/kotlin/Route.kt RouteId enum.
 */
class DemoRouteRegistryTest {

    @Test
    fun `registry contains exactly all 235 generated routes`() {
        assertEquals(235, RouteId.entries.size)
        assertEquals(RouteId.entries.size, DemoRouteRegistry.pages.size)
    }

    @Test
    fun `all route ids are unique`() {
        val ids = DemoRouteRegistry.pages.map { it.id }
        assertEquals("Duplicate route ids found", ids.size, ids.toSet().size)
    }

    @Test
    fun `registry ids exactly match generated serial names`() {
        assertEquals(generatedRouteIds(), DemoRouteRegistry.routeIds)
    }

    @Test
    fun `all 35 contract 2_5 routes have explicit native renderer classifications`() {
        val expected = setOf(
            "reader-font-import-confirm",
            "reader-font-delete-confirm",
            "reader-font-fallback",
            "reader-theme-new",
            "reader-theme-delete-confirm",
            "reader-typography-reset-confirm",
            "reader-replace-delete-confirm",
            "reader-replace-apply-result",
            "reader-replace-import-export",
            "reader-replace-page",
            "reader-replace-preview",
            "source-switch-empty",
            "source-switch-error",
            "source-switch-timeout",
            "source-switch-loading",
            "source-switch-rollback",
            "source-switch-preview",
            "reader-toc-loading",
            "reader-toc-offline",
            "reader-toc-error",
            "reader-content-loading",
            "reader-content-offline",
            "reader-content-error",
            "reader-page-boundary-first",
            "reader-page-boundary-last",
            "reader-progress-restore",
            "reader-background-restore",
            "import-permission-denied",
            "import-format-unsupported",
            "import-empty-file",
            "import-parsing",
            "import-duplicate",
            "import-conflict-resolve",
            "import-partial-success",
            "import-result-detail"
        )

        assertEquals(expected, DemoRouteRegistry.contract25RouteIds)
        assertTrue(expected.all { DemoRouteRegistry.rendererFor(it) != null })
    }

    @Test
    fun `contract 2_5 route actions only target generated routes`() {
        val generated = generatedRouteIds()
        val actionTargets = DemoRouteRegistry.contract25RouteIds
            .mapNotNull(DemoRouteRegistry::page)
            .flatMap { it.actions }
            .map { it.targetRoute }

        assertTrue("Unknown action targets: ${actionTargets.filterNot(generated::contains)}", actionTargets.all(generated::contains))
    }

    @Test
    fun `key new routes select their canonical renderers and remain navigable`() {
        assertEquals(
            DemoRouteRenderer.ReaderWorkspaceState,
            DemoRouteRegistry.rendererFor("reader-font-import-confirm")
        )
        assertEquals(
            DemoRouteRenderer.ReaderReplacementState,
            DemoRouteRegistry.rendererFor("reader-replace-page")
        )
        assertEquals(
            DemoRouteRenderer.SourceSwitchState,
            DemoRouteRegistry.rendererFor("source-switch-preview")
        )
        assertEquals(
            DemoRouteRenderer.ReaderContentState,
            DemoRouteRegistry.rendererFor("reader-content-error")
        )
        assertEquals(
            DemoRouteRenderer.LocalImportState,
            DemoRouteRegistry.rendererFor("import-conflict-resolve")
        )
        assertTrue(DemoRouteRegistry.routeFor("reader-replace-page") is ReaderRoute.Demo)
        assertTrue(DemoRouteRegistry.routeFor("source-switch-preview") is ReaderRoute.Demo)
        assertTrue(DemoRouteRegistry.routeFor("import-conflict-resolve") is ReaderRoute.Demo)
    }

    @Test
    fun `shellFor returns correct shell for known routeId`() {
        val shell = DemoRouteRegistry.shellFor("bookshelf")
        assertNotNull(shell)
        assertEquals(RouteShell.MainTabShell, shell)
    }

    @Test
    fun `shellFor returns null for unknown routeId`() {
        val shell = DemoRouteRegistry.shellFor("nonexistent-route")
        assertNull(shell)
    }

    @Test
    fun `all pages have non-empty title`() {
        DemoRouteRegistry.pages.forEach { page ->
            assertTrue("Route ${page.id} has empty title", page.title.isNotBlank())
        }
    }

    @Test
    fun `all pages have non-empty body`() {
        DemoRouteRegistry.pages.forEach { page ->
            assertTrue("Route ${page.id} has empty body", page.body.isNotEmpty())
        }
    }

    @Test
    fun `shell distribution covers all 5 shell types`() {
        val shells = DemoRouteRegistry.pages.map { it.shell }.toSet()
        assertTrue("Missing shells: ${RouteShell.entries - shells}", shells.size == 5)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun generatedRouteIds(): Set<String> {
        val descriptor = RouteId.serializer().descriptor
        return RouteId.entries.mapTo(linkedSetOf()) { descriptor.getElementName(it.ordinal) }
    }
}
