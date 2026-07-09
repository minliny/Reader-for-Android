package com.reader.ui.demo

import io.reader.ui.contract.RouteId
import io.reader.ui.contract.RouteShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 verification for [DemoRouteRegistry] — the 200-route demo catalog.
 *
 * Verifies:
 * - Registry contains exactly 200 routes (matches contract RouteId enum count)
 * - All route ids are unique
 * - All route ids match contract RouteId enum count
 * - shellFor returns the correct shell for known route ids
 * - shellFor returns null for unknown route ids
 * - All pages have non-empty title and body
 * - Shell distribution covers all 5 shell types
 *
 * Contract source: generated/kotlin/Route.kt RouteId enum (200 entries).
 */
class DemoRouteRegistryTest {

    @Test
    fun `registry contains exactly 200 routes`() {
        assertEquals(200, DemoRouteRegistry.pages.size)
    }

    @Test
    fun `all route ids are unique`() {
        val ids = DemoRouteRegistry.pages.map { it.id }
        assertEquals("Duplicate route ids found", ids.size, ids.toSet().size)
    }

    @Test
    fun `all route ids match contract RouteId enum count`() {
        // RouteId enum 的 @SerialName 值是 routeId 字符串。
        // 简化方案：验证 demoIds 数量 = RouteId.entries 数量 = 200。
        val contractCount = RouteId.entries.size
        val demoCount = DemoRouteRegistry.pages.size
        assertEquals(
            "Demo route count ($demoCount) must match contract RouteId count ($contractCount)",
            contractCount,
            demoCount
        )
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
}
