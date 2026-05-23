package com.reader.android.ui.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderPrototypeEntryRouteTest {

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    private val settingsSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/settings/SettingsScreen.kt")))
    }

    @Test
    fun `prototype gallery debug route is registered`() {
        listOf(
            "PROTOTYPE_GALLERY",
            "prototype_gallery",
            "ReaderPrototypeGallery",
            "BuildConfig.DEBUG",
            "startDestination"
        ).forEach { token ->
            assertTrue("Route host must contain $token", token in routeHostSource)
        }
    }

    @Test
    fun `debug app starts directly at prototype gallery`() {
        assertTrue("Debug start destination must use prototype gallery", "ReaderRoutes.PROTOTYPE_GALLERY" in routeHostSource.substringAfter("val startDestination = if (BuildConfig.DEBUG)"))
        assertTrue("Release fallback must stay bookshelf", "ReaderRoutes.BOOKSHELF" in routeHostSource.substringAfter("} else {").substringBefore("Scaffold("))
    }

    @Test
    fun `settings exposes debug only prototype entry`() {
        listOf(
            "onPrototypeGalleryClick",
            "BuildConfig.DEBUG",
            "UI 原型预览",
            "ReaderSettingsRow"
        ).forEach { token ->
            assertTrue("SettingsScreen must contain $token", token in settingsSource)
        }
    }

    @Test
    fun `prototype catalog remains large enough for human review`() {
        assertTrue(ReaderPrototypeCatalog.entries.size >= 38)
        assertEquals(9, ReaderPrototypeGroup.entries.size)
    }
}
