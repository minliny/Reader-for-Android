package com.reader.android.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class ReaderSharedComponentsStructureTest {

    private val componentDir = Paths.get("src/main/kotlin/com/reader/android/ui/components")

    private fun componentSource(): String =
        Files.walk(componentDir).asSequence()
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".kt") }
            .joinToString(separator = "\n") { String(Files.readAllBytes(it)) }

    private fun commonSource(): String =
        String(Files.readAllBytes(componentDir.resolve("CommonComponents.kt")))

    private fun componentFileSource(fileName: String): String =
        String(Files.readAllBytes(componentDir.resolve(fileName)))

    @Test
    fun `slice 2 shared component package exposes required components`() {
        val source = componentSource()

        listOf(
            "ReaderAppTopBar",
            "ReaderMainTabShell",
            "ReaderMainTabBar",
            "ReaderSectionHeader",
            "ReaderCard",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton",
            "ReaderIconButton",
            "ReaderChip",
            "ReaderDivider",
            "ReaderListItem",
            "BookCard",
            "BookListItem",
            "BookCover",
            "BookProgressIndicator",
            "BookMetaText",
            "BookActionSheetItem",
            "ReaderSearchBox",
            "SearchResultItem",
            "SearchResultSourceChip",
            "ReaderSettingsRow",
            "ReaderSettingsSwitchRow",
            "ReaderSettingsDropdownRow",
            "ReaderSettingsGroup",
            "ReaderLoadingState",
            "ReaderEmptyState",
            "ReaderErrorState",
            "ReaderOfflineState",
            "ReaderPermissionRequiredState"
        ).forEach { componentName ->
            assertTrue("Missing shared component $componentName", "fun $componentName(" in source)
        }
    }

    @Test
    fun `slice 2 shared components use reader theme tokens`() {
        val source = componentSource()

        listOf(
            "ReaderTheme.colors",
            "ReaderTheme.spacing",
            "ReaderTheme.shapes",
            "ReaderTheme.elevation",
            "ReaderTheme.typography"
        ).forEach { token ->
            assertTrue("Shared components must use $token", token in source)
        }
    }

    @Test
    fun `slice 2 shared components expose base semantics`() {
        val source = componentSource()

        listOf(
            "contentDescription",
            "semantics",
            "heading()"
        ).forEach { semanticToken ->
            assertTrue("Shared components must expose $semanticToken", semanticToken in source)
        }
    }

    @Test
    fun `shared top bar uses icon token for back action`() {
        val source = commonSource()

        assertTrue("ReaderAppTopBar must use Back icon token", "ReaderIconToken.Back.asImageVector()" in source)
        assertTrue("ReaderAppTopBar must not hardcode ArrowBack", "Icons.AutoMirrored.Filled.ArrowBack" !in source)
    }

    @Test
    fun `shared icon tokens cover main navigation and common actions`() {
        val source = componentSource()

        listOf(
            "ReaderIconToken.Bookshelf",
            "ReaderIconToken.Discover",
            "ReaderIconToken.Rss",
            "ReaderIconToken.Settings",
            "ReaderIconToken.Search",
            "ReaderIconToken.More",
            "ReaderIconToken.Back",
            "ReaderIconToken.Chevron",
            "ReaderIconToken.ChevronDown",
            "ReaderIconToken.ViewList",
            "ReaderIconToken.Grid",
            "ReaderIconToken.Add",
            "ReaderIconToken.Delete",
            "ReaderIconToken.FileOpen",
            "ReaderIconToken.Folder",
            "ReaderIconToken.FolderOff",
            "ReaderIconToken.Badge",
            "ReaderIconToken.Warning",
            "ReaderIconToken.Offline",
            "ReaderIconToken.Permission",
            "ReaderIconToken.Directory",
            "ReaderIconToken.Refresh",
            "ReaderIconToken.SourceSwitch",
            "ReaderIconToken.AutoBrightness",
            "ReaderIconToken.ChevronLeft",
            "ReaderIconToken.AutoScroll",
            "ReaderIconToken.ContentReplace",
            "ReaderIconToken.NightMode",
            "ReaderIconToken.Tts",
            "ReaderIconToken.Appearance",
            "ReaderIconToken.ReadingSettings",
            "ReaderIconToken.Bookmark",
            "ReaderIconToken.CurrentLocation",
            "ReaderIconToken.Close",
            "ReaderIconToken.Sort",
            "ReaderIconToken.People",
            "ReaderIconToken.Clock",
            "ReaderIconToken.List",
            "ReaderIconToken.Trash",
            "ReaderIconToken.Bell",
            "ReaderIconToken.Battery",
            "ReaderIconToken.EyeOff",
            "ReaderIconToken.Info",
            "ReaderIconToken.Shield",
            "ReaderIconToken.Bug",
            "ReaderIconToken.Storage",
            "ReaderIconToken.Download",
            "ReaderIconToken.Image",
            "ReaderIconToken.Check",
            "ReaderIconToken.Link",
            "ReaderIconToken.Message",
            "ReaderIconToken.Code",
            "ReaderIconToken.Help"
        ).forEach { token ->
            assertTrue("Shared icon token mapping must include $token", token in source)
        }
    }

    @Test
    fun `shared component files use icon tokens instead of page local material icons`() {
        val files = mapOf(
            "CommonComponents.kt" to listOf("ReaderIconToken.Back.asImageVector()"),
            "SearchComponents.kt" to listOf("ReaderIconToken.Search.asImageVector()"),
            "SettingsComponents.kt" to listOf("ReaderIconToken.ChevronDown.asImageVector()"),
            "StateComponents.kt" to listOf(
                "ReaderIconToken.FolderOff.asImageVector()",
                "ReaderIconToken.Warning.asImageVector()",
                "ReaderIconToken.Offline.asImageVector()",
                "ReaderIconToken.Permission.asImageVector()"
            ),
            "ReaderNativeComponents.kt" to listOf("ReaderIconToken.Chevron.asImageVector()")
        )

        files.forEach { (fileName, requiredTokens) ->
            val source = componentFileSource(fileName)
            requiredTokens.forEach { token ->
                assertTrue("$fileName must use $token", token in source)
            }
            assertTrue("$fileName must not import Material Icons directly", "import androidx.compose.material.icons" !in source)
        }
    }

    @Test
    fun `slice 2 shared components do not reintroduce stitch or webview runtime`() {
        val source = componentSource()

        listOf(
            "bg-" + "surface-container",
            "bg-" + "surface-container-high",
            "bg-" + "surface-container-highest",
            "text-" + "on-surface",
            "text-" + "on-surface-variant",
            "shadow-" + "lg",
            "shadow-" + "md",
            "FDF6EC",
            "EAE1DA",
            "F5ECE6",
            "EFE7E0",
            "8B5000",
            "Web" + "View",
            "normalized-" + "html"
        ).forEach { forbidden ->
            assertTrue("Forbidden Slice 2 token must not appear: $forbidden", forbidden !in source)
        }
    }
}
