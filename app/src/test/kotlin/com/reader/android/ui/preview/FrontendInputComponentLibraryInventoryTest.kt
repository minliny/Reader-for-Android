package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FrontendInputComponentLibraryInventoryTest {

    private val fixtureJson: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/component-library/fixture.json")
    }

    private val fixtureJs: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/component-library/fixture.js")
    }

    private val renderSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/component-library/render.js")
    }

    private val componentLibraryMarkdown: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/component-library/COMPONENT_LIBRARY.md")
    }

    @Test
    fun `component library renderer inventory stays aligned with shared input contract`() {
        val componentCards = renderComponentCards()

        assertEquals("component library rendered card count", 49, componentCards.size)
        assertEquals("component library rendered cards must be unique", componentCards.size, componentCards.distinct().size)

        listOf(
            "Color Tokens",
            "AppFrame / StatusBar",
            "IconButton",
            "MainNav",
            "SearchBar",
            "BookCard",
            "SearchResultItem",
            "SourceStatusBar / RankingRow",
            "ReaderModuleNav",
            "BrightnessSlider",
            "SourceCandidateRow / CurrentSourceBadge / DetectStatusBadge / SwitchSourceButton",
            "SettingGroupCard / PresetRow",
            "DangerActionRow / PermissionRow / CacheSizeCard / BackupRecordRow / SourceRow / LogPanel",
            "Loading / Empty / Error",
            "Conversion Guardrail"
        ).forEach { componentName ->
            assertTrue("component library renderer must include $componentName", componentName in componentCards)
        }

        listOf(
            "Foundations",
            "App Shell",
            "Basic Controls",
            "Cards & Rows",
            "Sheets & Panels",
            "States"
        ).forEach { sectionTitle ->
            assertTrue("component library renderer must preserve section $sectionTitle", sectionTitle in renderSource)
            assertTrue("component library manifest must preserve slot text $sectionTitle", sectionTitle in componentLibraryTarget())
        }
    }

    @Test
    fun `component library fixture icons come from the shared asset registry`() {
        val fixtureIcons = fixtureIconTokens()
        val assetIcons = assetIconNames()

        assertEquals("component library fixture icon token count", 17, fixtureIcons.size)
        assertTrue("component library fixture icons must exist in the asset registry", assetIcons.containsAll(fixtureIcons))
        fixtureIcons.forEach { iconName ->
            assertTrue("fixture.js must mirror component icon token $iconName", "icon: \"$iconName\"" in fixtureJs)
        }

        listOf(
            "\"bottomNav\"",
            "\"quickActions\"",
            "\"moduleNav\"",
            "\"sourceSwitch\"",
            "\"readingSettingGroups\"",
            "\"settingsManagement\"",
            "\"states\""
        ).forEach { fixtureKey ->
            assertTrue("component fixture must preserve $fixtureKey", fixtureKey in fixtureJson)
        }
    }

    @Test
    fun `component library docs manifest and validation report stay aligned`() {
        val manifestTarget = componentLibraryTarget()
        val validationTarget = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json"),
            startNeedle = """"name": "component-library"""",
            endNeedle = """"name": "shared-shell-kit""""
        )

        listOf(
            "\"html\": \"docs/ui-design/frontend-input/component-library/preview.html\"",
            "\"screenshot\": \"docs/ui-design/frontend-input/component-library/verify/component-library-preview.png\"",
            "\"selector\": \".rl-library\"",
            "\"rendererGlobal\": \"ReaderComponentLibrary\"",
            "\"shellName\": \"ComponentLibraryShell\"",
            "\"pageRole\": \"component-library\"",
            "\"type\": \"component-library\"",
            "\"selector\": \".rl-component-card\"",
            "\"min\": 49"
        ).forEach { token ->
            assertTrue("component-library manifest target must contain $token", token in manifestTarget)
        }

        listOf(
            "\"passed\": true",
            "\"title\": \"Reader 公共组件库\"",
            "\"rendererLoaded\": true",
            "\"shellName\": \"ComponentLibraryShell\"",
            "\"pageRole\": \"component-library\"",
            "\"type\": \"component-library\"",
            "\"imageCount\": 10",
            "\"missingImages\": 0",
            "\"expectedDomCount\": 49"
        ).forEach { token ->
            assertTrue("component-library validation report target must contain $token", token in validationTarget)
        }

        listOf(
            "公共组件库",
            "49 个组件卡",
            "FrontendInputComponentLibraryInventoryTest",
            "新增页面优先复用已有组件"
        ).forEach { token ->
            assertTrue("component-library docs must document $token", token in componentLibraryDocs())
        }
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun renderComponentCards(): List<String> =
        Regex("""componentCard\("([^"]+)"""")
            .findAll(renderSource)
            .map { it.groupValues[1] }
            .toList()

    private fun fixtureIconTokens(): List<String> =
        Regex(""""icon"\s*:\s*"([^"]+)"""")
            .findAll(fixtureJson)
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()

    private fun assetIconNames(): List<String> =
        Regex("""^\s*(?:"([^"]+)"|([A-Za-z][A-Za-z0-9]*)):\s*'""", RegexOption.MULTILINE)
            .findAll(workspaceSource("docs/ui-design/frontend-input/asset-library/icons.js"))
            .map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            .sorted()
            .toList()

    private fun componentLibraryTarget(): String =
        sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input/manifest.json"),
            startNeedle = """"name": "component-library"""",
            endNeedle = """"name": "shared-shell-kit""""
        )

    private fun componentLibraryDocs(): String =
        listOf(
            componentLibraryMarkdown,
            workspaceSource("docs/ui-design/frontend-input/component-library/README.md"),
            workspaceSource("docs/ui-design/frontend-input/BRANCH_CONTENTS.md"),
            workspaceSource("docs/ui-design/frontend-input/FRAMEWORK_COMPONENT_ROADMAP.md"),
            workspaceSource("docs/ui-design/frontend-input/FRAMEWORK_COMPONENT_CATALOG.md")
        ).joinToString("\n")

    private fun sectionAfter(source: String, startNeedle: String, endNeedle: String): String {
        val start = source.indexOf(startNeedle)
        assertTrue("Missing section start $startNeedle", start >= 0)
        val end = source.indexOf(endNeedle, start + startNeedle.length)
        assertTrue("Missing section end $endNeedle", end > start)
        return source.substring(start, end)
    }
}
