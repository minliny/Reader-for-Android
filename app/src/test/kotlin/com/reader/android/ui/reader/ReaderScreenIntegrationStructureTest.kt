package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderScreenIntegrationStructureTest {

    private val screenSource: String by lazy {
        String(
            Files.readAllBytes(
                Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReaderScreen.kt")
            )
        )
    }

    @Test
    fun `reader screen integrates reader night state not dialog`() {
        assertTrue("Must use ReaderNightState", "ReaderNightState(" in screenSource)
        assertTrue("Must have isNight state", "var isNight" in screenSource)
        assertTrue("Must toggle isNight", "isNight = !isNight" in screenSource)
        listOf("Dialog(", "AlertDialog(", "NightModeDialog").forEach { forbidden ->
            assertTrue("Must not use $forbidden", forbidden !in screenSource)
        }
    }

    @Test
    fun `reader screen integrates reader control base`() {
        listOf(
            "ReaderControlBase(",
            "BrightnessDock",
            "onBackClick",
            "onNightModeClick",
            "bookTitle",
            "chapterTitle",
            "sourceName",
            "chapterProgress"
        ).forEach { token ->
            assertTrue("Must integrate $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen preserves viewmodel fake real boundary`() {
        listOf(
            "ReaderViewModel",
            "useRealHttp",
            "FakeCoreBridge",
            "ContentParser",
            "HttpClient"
        ).forEach { token ->
            assertTrue("Must preserve $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen uses reader theme tokens`() {
        listOf("ReaderTheme.colors", "ReaderTheme.typography").forEach { token ->
            assertTrue("Must use $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen handles three ui states`() {
        listOf("isLoading", "content != null", "contentUrl == null").forEach { token ->
            assertTrue("Must handle state: $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen uses reader loading state`() {
        assertTrue("Must use ReaderLoadingState", "ReaderLoadingState(" in screenSource)
    }

    @Test
    fun `reader screen preserves callback interface`() {
        listOf("onBack", "onNextChapter", "contentUrl", "chapterTitle").forEach { token ->
            assertTrue("Must expose $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen body text uses reader typography`() {
        listOf("readerBody", "bodyText").forEach { token ->
            assertTrue("Must use $token", token in screenSource)
        }
    }

    @Test
    fun `reader screen does not reintroduce stitch old patterns`() {
        listOf(
            "MaterialTheme",
            "Scaffold(",
            "TopAppBar(",
            "BottomAppBar",
            "CircularProgressIndicator",
            "ArrowForward",
            "ExperimentalMaterial3Api",
            "bg-" + "surface-container",
            "shadow-" + "lg",
            "shadow-" + "md",
            "#" + "fdf6ec", "#" + "eae1da", "#" + "f5ece6", "#" + "efe7e0", "#" + "8b5000",
            "Web" + "View",
            "normalized-" + "html",
            "skip_" + "previous",
            "skip_" + "next",
            "上一章",
            "下一章"
        ).forEach { forbidden ->
            assertTrue(
                "Must not reintroduce $forbidden",
                forbidden !in screenSource
            )
        }
    }

    @Test
    fun `reader screen content area uses correct padding for control layer clearance`() {
        listOf("128.dp").forEach { token ->
            assertTrue("Content must clear control layers: $token", token in screenSource)
        }
    }
}
