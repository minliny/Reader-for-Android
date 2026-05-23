package com.reader.android.ui.prototype

import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class PrototypeMainTabsDesignFixTest {

    private val prototypeSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/prototype/ReaderPrototypeGallery.kt")))
    }

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    @Test
    fun `prototype app shell uses the same four primary tabs as the app shell`() {
        assertEquals(listOf("书架", "发现", "书源", "我的"), appScreens.map { it.label })
        assertTrue("Prototype App Shell must render appScreens", "appScreens.map" in prototypeSource)
    }

    @Test
    fun `prototype app shell does not show old search or settings primary tabs`() {
        listOf(
            "Icons.Filled.Search to \"搜索\"",
            "Icons.Filled.Settings to \"设置\""
        ).forEach { forbidden ->
            assertFalse("Prototype App Shell must not keep old main tab token $forbidden", forbidden in prototypeSource)
        }
    }

    @Test
    fun `prototype gallery remains debug only`() {
        assertTrue("Prototype route must stay debug guarded", "if (BuildConfig.DEBUG)" in routeHostSource)
        assertTrue("Prototype route must stay registered by explicit debug route", "PROTOTYPE_GALLERY" in routeHostSource)
    }
}
