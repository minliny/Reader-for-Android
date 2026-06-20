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
