package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FrontendInputFlowShellInventoryTest {

    private val packageDir = "docs/ui-design/04-阅读链路/换源/frontend-input"

    private val fixtureJson: String by lazy {
        workspaceSource("$packageDir/fixture.json")
    }

    private val renderSource: String by lazy {
        workspaceSource("$packageDir/render.js")
    }

    @Test
    fun `source switch package keeps the FlowShell frontend input file set`() {
        listOf(
            "fixture.json",
            "fixture.js",
            "render.js",
            "preview.html",
            "state-matrix.html",
            "components.html",
            "README.md",
            "COMPONENT_SPEC.md",
            "components.css",
            "verify/design-draft-preview.png",
            "verify/design-draft-state-matrix.png"
        ).forEach { fileName ->
            assertTrue("source switch frontend input package must contain $fileName", Files.exists(workspacePath("$packageDir/$fileName")))
        }

        listOf(
            "shared-shell-kit/kit.js",
            "asset-library/icons.js",
            "SourceSwitchInput.renderSourceSwitch",
            "SourceSwitchInput.renderSourceSwitchStateMatrix"
        ).forEach { token ->
            assertTrue("source switch HTML entry points must preserve $token", token in sourceSwitchHtmlEntries())
        }
    }

    @Test
    fun `source switch renderer uses centralized FlowShell and non empty state host`() {
        assertEquals("source switch renderer must call renderFlowShell for preview and state matrix", 2, renderFlowShellCallCount())
        assertTrue("source switch renderer must require ReaderShellKit", "ReaderShellKit is required before 换源/frontend-input/render.js" in renderSource)
        assertTrue("source switch renderer must not define a private FlowShell root", "data-shell=\"FlowShell\"" !in renderSource)

        listOf(
            "frameClass: \"sw-flow-frame\"",
            "stepClass: \"sw-flow-step-region\"",
            "comparisonClass: \"sw-flow-comparison-region\"",
            "resultClass: \"sw-flow-result-region\"",
            "stateHostClass: \"sw-flow-state-host\"",
            "stateHostHtml: flowStateHost(data, \"flow\")",
            "stateHostHtml: flowStateHost(data, state.key)",
            "sw-flow-status-strip",
            "FlowShell StateHost",
            "阅读位置已保留"
        ).forEach { token ->
            assertTrue("source switch renderer must preserve $token", token in renderSource)
        }
    }

    @Test
    fun `source switch fixture keeps the landscape flow and state matrix data`() {
        assertEquals("source switch candidate count", 5, sectionItemCount("sources"))
        assertEquals("source switch filter count", 4, stringArrayCount("filters"))
        assertEquals("source switch checking step count", 3, sectionItemCount("steps"))

        listOf(
            "\"currentSource\": \"起点中文网\"",
            "\"switchedSource\": \"书香阁\"",
            "\"status\": \"可用\"",
            "\"status\": \"失效\"",
            "\"title\": \"正在加载\"",
            "\"title\": \"暂无可用来源\"",
            "\"title\": \"加载失败，请重试\"",
            "\"title\": \"网络不可用，请稍后重试\"",
            "\"title\": \"需要网络权限\""
        ).forEach { token ->
            assertTrue("source switch fixture must preserve $token", token in fixtureJson)
        }
    }

    @Test
    fun `source switch manifest validation report docs and compose anchors stay aligned`() {
        val manifestPreview = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input/manifest.json"),
            startNeedle = """"name": "source-switch-preview"""",
            endNeedle = """"name": "source-switch-state-matrix""""
        )
        val manifestStateMatrix = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input/manifest.json"),
            startNeedle = """"name": "source-switch-state-matrix"""",
            endNeedle = """"name": "general-settings-preview""""
        )
        val validationPreview = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json"),
            startNeedle = """"name": "source-switch-preview"""",
            endNeedle = """"name": "source-switch-state-matrix""""
        )
        val validationStateMatrix = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json"),
            startNeedle = """"name": "source-switch-state-matrix"""",
            endNeedle = """"name": "general-settings-preview""""
        )

        listOf(
            "\"shellName\": \"FlowShell\"",
            "\"pageRole\": \"landscape-flow\"",
            "\"flowFrame\"",
            "\"stepRegion\"",
            "\"comparisonRegion\"",
            "\"resultRegion\"",
            "\"stateHost\""
        ).forEach { token ->
            assertTrue("source-switch-preview manifest must contain $token", token in manifestPreview)
            assertTrue("source-switch-state-matrix manifest must contain $token", token in manifestStateMatrix)
        }
        assertTrue("source-switch-preview manifest must require the visible StateHost label", "\"FlowShell StateHost\"" in manifestPreview)

        listOf(
            "\"passed\": true",
            "\"shellName\": \"FlowShell\"",
            "\"pageRole\": \"landscape-flow\"",
            "\"usesKit\": true",
            "\"flowFrame\": 1",
            "\"stateHost\": 1",
            "\"expectedDomCount\": 10"
        ).forEach { token ->
            assertTrue("source-switch preview validation report must contain $token", token in validationPreview)
        }

        listOf(
            "\"passed\": true",
            "\"stateCardCount\": 6",
            "\"flowFrame\": 6",
            "\"stateHost\": 6"
        ).forEach { token ->
            assertTrue("source-switch state matrix validation report must contain $token", token in validationStateMatrix)
        }

        listOf(
            "FlowShell StateHost",
            "FrontendInputFlowShellInventoryTest",
            "SourceSwitchFlowDefaultPreview",
            "SourceSwitchFlowPermissionPreview"
        ).forEach { token ->
            assertTrue("source switch docs and Compose anchors must contain $token", token in sourceSwitchDocsAndCompose())
        }
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun sourceSwitchHtmlEntries(): String =
        listOf(
            workspaceSource("$packageDir/preview.html"),
            workspaceSource("$packageDir/state-matrix.html"),
            workspaceSource("$packageDir/components.html")
        ).joinToString("\n")

    private fun renderFlowShellCallCount(): Int =
        Regex("""renderFlowShell\(\{""").findAll(renderSource).count()

    private fun sectionItemCount(sectionName: String): Int {
        val section = Regex("(?s)\"${Regex.escape(sectionName)}\"\\s*:\\s*\\[(.*?)]")
            .find(fixtureJson)
            ?.groupValues
            ?.get(1)
            ?: error("Missing source switch fixture section $sectionName")
        return Regex("\\{").findAll(section).count()
    }

    private fun stringArrayCount(sectionName: String): Int {
        val section = Regex("(?s)\"${Regex.escape(sectionName)}\"\\s*:\\s*\\[(.*?)]")
            .find(fixtureJson)
            ?.groupValues
            ?.get(1)
            ?: error("Missing source switch fixture string array $sectionName")
        return Regex("\"[^\"]+\"").findAll(section).count()
    }

    private fun sourceSwitchDocsAndCompose(): String =
        listOf(
            workspaceSource("$packageDir/README.md"),
            workspaceSource("$packageDir/COMPONENT_SPEC.md"),
            workspaceSource("docs/ui-design/frontend-input/BRANCH_CONTENTS.md"),
            workspaceSource("docs/ui-design/frontend-input/FRAMEWORK_COMPONENT_ROADMAP.md"),
            workspaceSource("app/src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt")
        ).joinToString("\n")

    private fun sectionAfter(source: String, startNeedle: String, endNeedle: String): String {
        val start = source.indexOf(startNeedle)
        assertTrue("Missing section start $startNeedle", start >= 0)
        val end = source.indexOf(endNeedle, start + startNeedle.length)
        assertTrue("Missing section end $endNeedle", end > start)
        return source.substring(start, end)
    }
}
