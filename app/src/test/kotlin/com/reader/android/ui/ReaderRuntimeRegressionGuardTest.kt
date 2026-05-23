package com.reader.android.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderRuntimeRegressionGuardTest {

    private val runtimeSource: String by lazy {
        listOf(
            "src/main/kotlin/com/reader/android/MainActivity.kt",
            "src/main/kotlin/com/reader/android/ui/ReaderAndroidApp.kt",
            "src/main/kotlin/com/reader/android/ui/AppNavigation.kt",
            "src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt"
        ).joinToString("\n") { path ->
            String(Files.readAllBytes(Paths.get(path)))
        }
    }

    @Test
    fun `runtime entry does not reference old webview normalized html path`() {
        val forbidden = listOf("Web" + "View", "docs/ui-handoff/normalized-" + "html")
        forbidden.forEach {
            assertTrue("Runtime entry must not contain $it", it !in runtimeSource)
        }
    }

    @Test
    fun `runtime entry does not use stitch class or old color literals`() {
        val forbidden = listOf(
            "bg-" + "surface-container",
            "text-" + "on-surface",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec",
            "#" + "eae1da",
            "#" + "f5ece6",
            "#" + "efe7e0",
            "#" + "8b5000"
        )
        forbidden.forEach {
            assertTrue("Runtime entry must not contain $it", it !in runtimeSource)
        }
    }
}
