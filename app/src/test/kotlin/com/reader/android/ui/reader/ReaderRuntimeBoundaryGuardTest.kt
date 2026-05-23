package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderRuntimeBoundaryGuardTest {

    private val readerSource: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui/reader")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    @Test
    fun `reader runtime state does not import stitch old classes`() {
        listOf(
            "bg-surface-container",
            "bg-surface-container-high",
            "bg-surface-container-highest",
            "text-on-surface",
            "text-on-surface-variant",
            "shadow-lg",
            "shadow-md"
        ).forEach { forbidden ->
            assertTrue("Must not contain $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `reader runtime state does not import stitch old colors`() {
        listOf("#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000").forEach { forbidden ->
            assertTrue("Must not contain $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `reader runtime does not use webview`() {
        listOf("WebView", "normalized-html").forEach { forbidden ->
            assertTrue("Must not contain $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `reader runtime does not modify reader core bridge`() {
        listOf("FakeCoreBridge", "ContentParser", "TOCParser", "BookSourceRepository").forEach { core ->
            // These should only appear in ReaderViewModel (existing, allowed)
            // New runtime state code should not import them
            val stateFile = String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReaderRuntimeState.kt")))
            assertTrue("ReaderRuntimeState must not import $core", core !in stateFile)

            val fixtureFile = String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReaderRuntimeFixture.kt")))
            assertTrue("ReaderRuntimeFixture must not import $core", core !in fixtureFile)

            val mapperFile = String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReaderRuntimeMapper.kt")))
            assertTrue("ReaderRuntimeMapper must not import $core", core !in mapperFile)
        }
    }

    @Test
    fun `reader control state layer has correct overlay types`() {
        assertTrue("Must have SEARCH type", "SEARCH" in readerSource)
        assertTrue("Must have DIRECTORY type", "DIRECTORY" in readerSource)
        assertTrue("Must have TTS type", "TTS" in readerSource)
        assertTrue("Must have NONE type", "NONE" in readerSource)
    }

    @Test
    fun `page control never uses chapter skip semantics`() {
        listOf("skip_previous", "skip_next", "上一章", "下一章").forEach { forbidden ->
            assertTrue("Must not use $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `night mode is not dialog`() {
        listOf("Dialog(", "AlertDialog(").forEach { forbidden ->
            assertTrue("Night mode must not use $forbidden", forbidden !in readerSource)
        }
    }

    @Test
    fun `content replace shows current book rules only`() {
        val fixtureFile = String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReaderRuntimeFixture.kt")))
        assertTrue("Fixture must include current book replace rules", "sampleReplaceRules" in fixtureFile)
    }
}
