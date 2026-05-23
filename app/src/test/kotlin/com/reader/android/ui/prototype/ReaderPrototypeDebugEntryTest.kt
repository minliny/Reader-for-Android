package com.reader.android.ui.prototype

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderPrototypeDebugEntryTest {

    private val accessSource: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt",
            "src/main/kotlin/com/reader/android/ui/settings/SettingsScreen.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeGallery.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeFixtures.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeCatalog.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    private val prototypeRuntimeSource: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeGallery.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeFixtures.kt",
            "src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeCatalog.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    @Test
    fun `prototype access is guarded by debug build config`() {
        val routeBlock = accessSource.substringAfter("if (BuildConfig.DEBUG)").substringBefore("ReaderRoutes.STATE_ERROR")

        assertTrue("Prototype route must be inside debug guard", "ReaderRoutes.PROTOTYPE_GALLERY" in routeBlock)
        assertTrue("Prototype gallery must be inside debug guard", "ReaderPrototypeGallery()" in routeBlock)
        assertTrue("Settings entry must use debug guard", "BuildConfig.DEBUG && onPrototypeGalleryClick != null" in accessSource)
    }

    @Test
    fun `prototype access does not touch runtime data layers`() {
        listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "Http" + "Client",
            ".execute(",
            "Room",
            "DataStore"
        ).forEach { forbidden ->
            assertTrue("Prototype route must not contain $forbidden", forbidden !in prototypeRuntimeSource)
        }
    }

    @Test
    fun `prototype access does not expose webview legacy tokens or secrets`() {
        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec",
            "#" + "eae1da",
            "#" + "f5ece6",
            "#" + "efe7e0",
            "#" + "8b5000",
            "Web" + "View",
            "normalized-" + "html",
            "Authorization",
            "Bearer ",
            "token=",
            "secret="
        ).forEach { forbidden ->
            assertTrue("Prototype access must not contain $forbidden", forbidden !in prototypeRuntimeSource)
        }
    }
}
