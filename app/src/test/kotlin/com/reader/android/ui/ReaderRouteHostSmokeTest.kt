package com.reader.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderRouteHostSmokeTest {

    private val routeHostSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt")))
    }

    private val appNavigationSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/AppNavigation.kt")))
    }

    @Test
    fun `app navigation delegates to reader route host`() {
        assertTrue("AppNavigation must call ReaderRouteHost", "ReaderRouteHost()" in appNavigationSource)
    }

    @Test
    fun `route host has nav host and scaffold bottom bar`() {
        listOf("NavHost(", "Scaffold(", "NavigationBar", "NavigationBarItem").forEach { token ->
            assertTrue("ReaderRouteHost must contain $token", token in routeHostSource)
        }
    }

    @Test
    fun `route host registers all declared routes`() {
        val declaredRoutes = routeHostSource.lines().count { it.trimStart().startsWith("const val ") }
        val registeredRoutes = routeHostSource.lines().count { it.trimStart().startsWith("composable(") }

        assertEquals("ReaderRoutes must declare 24 production routes plus 1 debug prototype route", 25, declaredRoutes)
        assertEquals("ReaderRouteHost must register 24 production destinations plus 1 debug prototype destination", 25, registeredRoutes)
    }
}
