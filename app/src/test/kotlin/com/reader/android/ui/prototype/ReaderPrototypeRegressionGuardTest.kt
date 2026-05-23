package com.reader.android.ui.prototype

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderPrototypeRegressionGuardTest {

    private val prototypeSource: String by lazy {
        val dir = Paths.get("src/main/kotlin/com/reader/android/ui/prototype")
        Files.walk(dir)
            .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { String(Files.readAllBytes(it)) }
            .toList()
            .joinToString("\n")
    }

    @Test
    fun `prototype uses fixture state and does not touch runtime data layers`() {
        listOf(
            "ReaderPrototypeFixtures",
            "ReaderRuntimeFixture",
            "DiscoverRssWebDavMapper"
        ).forEach { token ->
            assertTrue("Prototype must use $token", token in prototypeSource)
        }

        listOf(
            "com.reader.android.data",
            "Repository",
            "Parser",
            "Bridge",
            "Http" + "Client",
            ".execute(",
            ".get(",
            "Room",
            "DataStore"
        ).forEach { forbidden ->
            assertTrue("Prototype must not contain $forbidden", forbidden !in prototypeSource)
        }
    }

    @Test
    fun `prototype does not introduce webview legacy tokens or real secrets`() {
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
            assertTrue("Prototype must not contain $forbidden", forbidden !in prototypeSource)
        }
    }

    @Test
    fun `reader prototype rules stay visible in source`() {
        listOf(
            "reader-night",
            "reader-replace",
            "reader-directory",
            "reader-tts",
            "reader-settings"
        ).forEach { id ->
            assertTrue("Prototype must keep $id", id in prototypeSource)
        }

        listOf("skip_" + "previous", "skip_" + "next", "WebDAV", "RSS", "书源").forEach { forbidden ->
            val settingsEntry = prototypeSource.substringAfter("\"reader-settings\"").substringBefore("\"source-list\"")
            assertTrue("Reader settings prototype must not contain $forbidden", forbidden !in settingsEntry)
        }
    }
}
