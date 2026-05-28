package com.reader.android.ui.state

import com.reader.android.ui.AppScreen
import com.reader.android.ui.appScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReaderStateRegressionGuardTest {

    @Test
    fun `state mapper stays in ui layer without core parser repository calls`() {
        val source = read("src/main/kotlin/com/reader/android/ui/state/ReaderStateIntegration.kt")

        listOf(
            "com.reader.android.data",
            "Repository",
            "ReaderCore",
            "Parser",
            "WebView",
            "HttpURLConnection",
            "OkHttp"
        ).forEach { forbidden ->
            assertFalse("State mapper must not depend on $forbidden", forbidden in source)
        }
    }

    @Test
    fun `app main bottom nav remains four redesigned modules`() {
        assertEquals(
            listOf("书架", "发现", "书源", "我的"),
            appScreens.map { it.label }
        )
        assertEquals(
            listOf(
                AppScreen.Bookshelf.route,
                AppScreen.Discover.route,
                AppScreen.Sources.route,
                AppScreen.Mine.route
            ),
            appScreens.map { it.route }
        )
        assertFalse(appScreens.any { it.label in setOf("搜索", "阅读", "设置") })
    }

    @Test
    fun `reader bottom bar remains reader control only`() {
        val readerSources = readAllUnder(Paths.get("src/main/kotlin/com/reader/android/ui/reader"))
        listOf("目录", "朗读", "界面", "设置").forEach { label ->
            assertTrue("Reader control bottom bar must keep $label", label in readerSources)
        }
        listOf("WebDAV", "书源", "RSS", "关于", "全局设置").forEach { label ->
            assertFalse("Reader settings must not include $label", label in readerSources)
        }
    }

    @Test
    fun `ui state hardening does not reintroduce stitch tokens or normalized webview`() {
        val uiSources = readAllUnder(Paths.get("src/main/kotlin/com/reader/android/ui"))
        listOf(
            "bg-surface-container",
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md",
            "#fdf6ec",
            // EAE1DA/F5ECE6/EFE7E0 are intentional Stitch warm palette in ReaderColors.kt
            "#8b5000",
            "docs/ui-handoff/normalized-html"
        ).forEach { forbidden ->
            assertFalse("Forbidden runtime token found: $forbidden", forbidden in uiSources)
        }
    }

    private fun read(path: String): String =
        String(Files.readAllBytes(Paths.get(path)))

    private fun readAllUnder(root: Path): String =
        Files.walk(root)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
}
