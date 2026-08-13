package com.reader.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class ReaderSharedComponentsStructureTest {

    private val componentDir = Paths.get("src/main/kotlin/com/reader/android/ui/components")

    private data class ComponentMapping(
        val componentName: String,
        val sourcePaths: List<String>,
        val requiredTokens: List<String>
    )

    private data class ShellComposeAnchor(
        val shellName: String,
        val sourcePaths: List<String>,
        val requiredTokens: List<String>
    )

    private fun componentSource(): String =
        Files.walk(componentDir).asSequence()
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".kt") }
            .joinToString(separator = "\n") { String(Files.readAllBytes(it)) }

    private fun projectSource(path: String): String =
        String(Files.readAllBytes(Paths.get(path)))

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(Paths.get("..").resolve(path)))

    private fun projectSource(paths: List<String>): String =
        paths.joinToString(separator = "\n") { projectSource(it) }

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
            "ReaderIconToken.Help",
            "ReaderIconToken.Upload",
            "ReaderIconToken.Edit"
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
    fun `component library core semantics have compose implementation anchors`() {
        val componentLibrary = workspaceSource("docs/ui-design/frontend-input/component-library/COMPONENT_LIBRARY.md")
        val mappings = listOf(
            ComponentMapping(
                componentName = "MainNav",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt"),
                requiredTokens = listOf("fun ReaderMainTabShell(", "fun ReaderMainTabBar(")
            ),
            ComponentMapping(
                componentName = "IconButton",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt",
                    "src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"
                ),
                requiredTokens = listOf("fun ReaderIconButton(")
            ),
            ComponentMapping(
                componentName = "SearchBar",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/SearchComponents.kt"),
                requiredTokens = listOf("fun ReaderSearchBox(")
            ),
            ComponentMapping(
                componentName = "Chip",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt",
                    "src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"
                ),
                requiredTokens = listOf("fun ReaderChip(")
            ),
            ComponentMapping(
                componentName = "Switch",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"),
                requiredTokens = listOf("fun ReaderSwitch(")
            ),
            ComponentMapping(
                componentName = "PrimaryActionButton",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt"),
                requiredTokens = listOf("fun ReaderPrimaryButton(")
            ),
            ComponentMapping(
                componentName = "SecondaryActionButton",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt"),
                requiredTokens = listOf("fun ReaderSecondaryButton(")
            ),
            ComponentMapping(
                componentName = "ProgressSlider",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"),
                requiredTokens = listOf("fun ReaderSlider(", "fun ReaderProgressRail(")
            ),
            ComponentMapping(
                componentName = "BookCover",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/BookComponents.kt"),
                requiredTokens = listOf("fun BookCover(")
            ),
            ComponentMapping(
                componentName = "BookCard",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/BookComponents.kt"),
                requiredTokens = listOf("fun BookCard(")
            ),
            ComponentMapping(
                componentName = "SearchResultItem",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/SearchComponents.kt"),
                requiredTokens = listOf("fun SearchResultItem(")
            ),
            ComponentMapping(
                componentName = "SettingRow",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/components/SettingsComponents.kt",
                    "src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"
                ),
                requiredTokens = listOf("fun ReaderSettingsRow(", "fun ReaderSettingRow(")
            ),
            ComponentMapping(
                componentName = "SettingGroupCard",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/SettingsComponents.kt"),
                requiredTokens = listOf("fun ReaderSettingsGroup(")
            ),
            ComponentMapping(
                componentName = "LoadingState",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/StateComponents.kt"),
                requiredTokens = listOf("fun ReaderLoadingState(")
            ),
            ComponentMapping(
                componentName = "EmptyState",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/StateComponents.kt"),
                requiredTokens = listOf("fun ReaderEmptyState(")
            ),
            ComponentMapping(
                componentName = "ErrorState",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/StateComponents.kt"),
                requiredTokens = listOf("fun ReaderErrorState(")
            ),
            ComponentMapping(
                componentName = "PermissionState",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/StateComponents.kt"),
                requiredTokens = listOf("fun ReaderPermissionRequiredState(")
            ),
            ComponentMapping(
                componentName = "BrightnessSlider",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"),
                requiredTokens = listOf("fun ReaderSlider(", "fun ReaderProgressRail(")
            ),
            ComponentMapping(
                componentName = "QuickAction",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/components/ReaderNativeComponents.kt"),
                requiredTokens = listOf("fun ReaderQuickCircle(")
            ),
            ComponentMapping(
                componentName = "SourceCandidateRow",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                requiredTokens = listOf("fun SourceSwitchFlowScreen(", "fun SourceCandidateRow(")
            ),
            ComponentMapping(
                componentName = "CurrentSourceBadge",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                requiredTokens = listOf("fun CurrentSourceBadge(")
            ),
            ComponentMapping(
                componentName = "DetectStatusBadge",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                requiredTokens = listOf("fun DetectStatusBadge(")
            ),
            ComponentMapping(
                componentName = "SwitchSourceButton",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                requiredTokens = listOf("fun SwitchSourceButton(")
            ),
            ComponentMapping(
                componentName = "CacheSizeCard",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignScreen.kt"),
                requiredTokens = listOf("fun CacheSizeCard(")
            ),
            ComponentMapping(
                componentName = "BackupActionRow",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignScreen.kt"),
                requiredTokens = listOf("fun BackupActionRow(")
            ),
            ComponentMapping(
                componentName = "SourceRow",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/SourceManagementDesignScreen.kt"),
                requiredTokens = listOf("fun SourceRow(")
            )
        )

        mappings.forEach { mapping ->
            assertTrue(
                "Component library must declare ${mapping.componentName}",
                "`" + mapping.componentName + "`" in componentLibrary
            )
            val source = projectSource(mapping.sourcePaths)
            mapping.requiredTokens.forEach { token ->
                assertTrue("${mapping.componentName} must have Compose anchor $token", token in source)
            }
        }
    }

    @Test
    fun `manifest runtime shell taxonomy has compose implementation anchors`() {
        assertEquals(
            "manifest shell counts must stay aligned with the frontend input shell taxonomy",
            mapOf(
                "ComponentLibraryShell" to 3,
                "AssetLibraryShell" to 1,
                "MainTabShell" to 8,
                "LibraryShell" to 16,
                "ReaderShell" to 20,
                "FlowShell" to 2,
                "SettingsShell" to 14
            ),
            manifestShellCounts()
        )

        val anchors = listOf(
            ShellComposeAnchor(
                shellName = "MainTabShell",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/components/CommonComponents.kt",
                    "src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt",
                    "src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt"
                ),
                requiredTokens = listOf(
                    "fun ReaderMainTabShell(",
                    "fun ReaderMainTabBar(",
                    "showMainNav = showMainBottomBar",
                    "BookshelfMainTabDefaultPreview",
                    "RssMainTabDefaultPreview",
                    "SettingsMainTabDefaultPreview"
                )
            ),
            ShellComposeAnchor(
                shellName = "LibraryShell",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/preview/LibraryFlowStateMatrixPreviews.kt",
                    "src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfEmptyDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/search/SearchScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/detail/BookDetailScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/toc/TOCScreen.kt"
                ),
                requiredTokens = listOf(
                    "LibraryShell，书架空状态",
                    "ReaderAppTopBar",
                    "ReaderMainTabBar",
                    "ReaderPermissionRequiredState",
                    "LibraryBookshelfEmptyDefaultPreview",
                    "LibrarySearchHomePreview",
                    "LibraryBookDetailDefaultPreview",
                    "LibraryBookDirectoryDefaultPreview",
                    "LibrarySortFilterDefaultPreview",
                    "LibraryBookActionSheetDefaultPreview",
                    "LibraryGroupManagementDefaultPreview",
                    "LibraryLocalImportDefaultPreview"
                )
            ),
            ShellComposeAnchor(
                shellName = "ReaderShell",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ReaderShellDesignScreens.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ReadingAppearanceDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ReadingAloudDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ReadingSettingsDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/AutoPageDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ContentSearchDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/ContentReplacementDesignScreen.kt"
                ),
                requiredTokens = listOf(
                    "ReadingEntryDefaultPreview",
                    "ImmersiveReadingDefaultPreview",
                    "ReadingTocBookmarkDefaultPreview",
                    "ReadingAppearanceDefaultPreview",
                    "ReadingAloudDefaultPreview",
                    "ReadingSettingsDefaultPreview",
                    "AutoPageDefaultPreview",
                    "ContentSearchDefaultPreview",
                    "ContentReplacementDefaultPreview",
                    "contentDescription = \"readingSurface\"",
                    "contentDescription = \"readerOverlayHost\"",
                    "contentDescription = \"bottomSheetHost\"",
                    "contentDescription = \"readerModuleNav\"",
                    "contentDescription = \"readerStateHost\""
                )
            ),
            ShellComposeAnchor(
                shellName = "FlowShell",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                requiredTokens = listOf(
                    "fun SourceSwitchFlowScreen(",
                    "FlowShell flowFrame",
                    "slotName = \"stepRegion\"",
                    "slotName = \"comparisonRegion\"",
                    "slotName = \"resultRegion\"",
                    "slotName = \"stateHost\"",
                    "SourceSwitchFlowDefaultPreview",
                    "SourceSwitchFlowPermissionPreview"
                )
            ),
            ShellComposeAnchor(
                shellName = "SettingsShell",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/BookshelfSearchSettingsDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/PrivacyPermissionsDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/AboutFeedbackDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/settings/SourceManagementDesignScreen.kt"
                ),
                requiredTokens = listOf(
                    "SettingsGeneralDefaultPreview",
                    "SettingsBookshelfSearchDefaultPreview",
                    "SettingsPrivacyPermissionsDefaultPreview",
                    "SettingsCacheManagementDefaultPreview",
                    "SettingsAboutFeedbackDefaultPreview",
                    "SettingsSyncBackupDefaultPreview",
                    "SettingsSourceManagementDefaultPreview",
                    "ReaderAppTopBar",
                    "contentDescription = \"settingsContent\"",
                    "contentDescription = \"sheetHost\"",
                    "contentDescription = \"dialogHost\"",
                    "contentDescription = \"settingsStateHost\""
                )
            )
        )

        assertEquals(
            "runtime shell anchors must cover every runtime shell exactly",
            setOf("MainTabShell", "LibraryShell", "ReaderShell", "FlowShell", "SettingsShell"),
            anchors.map { it.shellName }.toSet()
        )

        anchors.forEach { anchor ->
            val source = projectSource(anchor.sourcePaths)
            anchor.requiredTokens.forEach { token ->
                assertTrue("${anchor.shellName} must have Compose anchor $token", token in source)
            }
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

    private fun manifestShellCounts(): Map<String, Int> =
        Regex(""""shellName"\s*:\s*"([^"]+)"""")
            .findAll(workspaceSource("docs/ui-design/frontend-input/manifest.json"))
            .map { it.groupValues[1] }
            .groupingBy { it }
            .eachCount()
}
