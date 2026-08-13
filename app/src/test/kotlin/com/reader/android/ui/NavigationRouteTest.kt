package com.reader.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder

class NavigationRouteTest {

    // ── Route constants ──

    @Test
    fun `all tab routes are unique`() {
        val routes = appScreens.map { it.route }.toSet()
        assertEquals(4, routes.size)
        assertEquals(setOf("bookshelf", "discover", "rss", "settings"), routes)
    }

    @Test
    fun `Routes object defines SEARCH constant`() {
        assertEquals("search", Routes.SEARCH)
    }

    @Test
    fun `Routes DETAIL pattern includes detailUrl argument`() {
        assertTrue(Routes.DETAIL.contains("{detailUrl}"))
    }

    @Test
    fun `Routes TOC pattern includes tocUrl argument`() {
        assertTrue(Routes.TOC.contains("{tocUrl}"))
    }

    @Test
    fun `Routes READER_CONTENT pattern includes contentUrl and chapterTitle arguments`() {
        assertTrue(Routes.READER_CONTENT.contains("{contentUrl}"))
        assertTrue(Routes.READER_CONTENT.contains("{chapterTitle}"))
    }

    // ── URL encode/decode round-trip ──

    @Test
    fun `detail URL round-trip preserves original value`() {
        val original = "https://example.com/book/123?ref=search"
        val encoded = URLEncoder.encode(original, "UTF-8")
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals(original, decoded)
    }

    @Test
    fun `toc URL with Chinese chars round-trip`() {
        val original = "https://example.com/toc/一剑独尊"
        val encoded = URLEncoder.encode(original, "UTF-8")
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals(original, decoded)
    }

    @Test
    fun `chapter title with spaces round-trip`() {
        val original = "第一章 下山"
        val encoded = URLEncoder.encode(original, "UTF-8")
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals(original, decoded)
    }

    @Test
    fun `route builder functions produce non-empty paths`() {
        val detailRoute = Routes.detail("http://example.com/book/1")
        assertTrue(detailRoute.startsWith("detail/"))
        assertTrue(detailRoute.length > "detail/".length)

        val tocRoute = Routes.toc("http://example.com/toc")
        assertTrue(tocRoute.startsWith("toc/"))

        val readerRoute = Routes.readerContent("http://example.com/ch1", "第一章")
        assertTrue(readerRoute.startsWith("reader_content/"))
        // Chinese chars in URL-encoded form
        assertTrue(readerRoute.length > "reader_content/".length)
    }

    // ── Route uniqueness ──

    @Test
    fun `flow route patterns do not collide with tab routes`() {
        val tabRoutes = appScreens.map { it.route }.toSet()
        val flowRoutePrefixes = listOf(
            Routes.SEARCH,
            Routes.DETAIL.split("/").first(),
            Routes.TOC.split("/").first(),
            Routes.READER_CONTENT.split("/").first()
        )
        flowRoutePrefixes.forEach { prefix ->
            assertTrue("Route $prefix should not collide with tab routes", prefix !in tabRoutes)
        }
    }

    @Test
    fun `argument names are consistent`() {
        // Verify argument naming convention: camelCase
        val detailArg = Routes.DETAIL.substringAfter("{").substringBefore("}")
        val tocArg = Routes.TOC.substringAfter("{").substringBefore("}")
        val readerArgs = Routes.READER_CONTENT
            .split("/")
            .filter { it.startsWith("{") }
            .map { it.substringAfter("{").substringBefore("}") }

        assertEquals("detailUrl", detailArg)
        assertEquals("tocUrl", tocArg)
        assertEquals(listOf("contentUrl", "chapterTitle"), readerArgs)
    }
}
