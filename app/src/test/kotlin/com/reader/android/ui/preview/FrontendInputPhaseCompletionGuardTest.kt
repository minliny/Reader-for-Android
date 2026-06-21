package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class FrontendInputPhaseCompletionGuardTest {

    private val branchContents: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/BRANCH_CONTENTS.md")
    }

    private val mappingGuide: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/FRONTEND_MAPPING_GUIDE.md")
    }

    private val manifestSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/manifest.json")
    }

    private val validationReportSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json")
    }

    @Test
    fun `phase completion summary stays backed by current manifest and validation report`() {
        val screenCount = formalScreenDirs().size
        val manifestTargets = manifestHtmlTargets()
        val reportTargets = validationReportTargets()

        assertEquals("formal UI design screen count", 30, screenCount)
        assertEquals("manifest target count", 64, manifestTargets.size)
        assertEquals("validation report target count", 64, reportTargets.size)
        assertEquals("validation report targets must match manifest", manifestTargets.sorted(), reportTargets.sorted())
        assertTrue("validation report must pass", """"passed": true""" in validationReportSource)
        assertTrue("validation report must not contain failing target", """"passed": false""" !in validationReportSource)

        mapOf(
            "targets: `64`" to manifestTargets.size,
            "preview targets: `30`" to manifestTargets.count { it.endsWith("/frontend-input/preview.html") && it.startsWith("docs/ui-design/0") },
            "state matrix targets: `30`" to manifestTargets.count { it.endsWith("/frontend-input/state-matrix.html") },
            "library/demo targets: `4`" to manifestTargets.count { it.startsWith("docs/ui-design/frontend-input/") },
            "component reference smoke targets: `30`" to componentReferenceSmokeCount()
        ).forEach { (docToken, count) ->
            assertTrue("branch contents must document $docToken", docToken in branchContents)
            assertEquals("branch contents documented count for $docToken must match current sources", docTokenValue(docToken), count)
        }

        listOf(
            "输入件闭环（Input Package Closure）",
            "框架闭环（Shell Closure）",
            "组件与素材闭环（Component and Asset Closure）",
            "Compose 输入闭环（Compose Input Closure）",
            "验证闭环（Validation Closure）",
            "留白明确（Explicit Remaining Work）"
        ).forEach { token ->
            assertTrue("branch contents must keep phase done criterion $token", token in branchContents)
        }
    }

    @Test
    fun `phase guard command lists every frontend input protection layer`() {
        listOf(
            "FrontendInputComposeCoverageTest",
            "FrontendInputHtmlInventoryTest",
            "FrontendInputAssetLibraryInventoryTest",
            "FrontendInputComponentLibraryInventoryTest",
            "FrontendInputFlowShellInventoryTest",
            "FrontendInputPhaseCompletionGuardTest",
            "ReaderSharedComponentsStructureTest",
            "ReaderIconTokenMappingTest",
            "ReaderIconImportBoundaryTest",
            "MainTabStateMatrixPreviewTest",
            "LibraryFlowStateMatrixPreviewTest",
            "ReaderControlStateMatrixPreviewTest",
            "ReaderShellStateMatrixPreviewTest",
            "SettingsSecondaryStateMatrixPreviewTest",
            "SourceSwitchFlowStructureTest"
        ).forEach { guardName ->
            assertTrue("branch validation command must include $guardName", "--tests com.reader.android" in branchContents && guardName in branchContents)
        }

        listOf(
            "HTML inventory",
            "asset library inventory",
            "component library inventory",
            "FlowShell inventory",
            "phase completion summary",
            "runtime shell anchors",
            "icon token/import boundary"
        ).forEach { scopeToken ->
            assertTrue("branch guard scope must mention $scopeToken", scopeToken in branchContents)
        }
    }

    @Test
    fun `mapping guide keeps completion boundary separate from next phase implementation`() {
        listOf(
            "设计输入闭合（Design Input Closure）",
            "框架输入闭合（Shell Input Closure）",
            "状态输入闭合（State Input Closure）",
            "事件契约闭合（Event Contract Closure）",
            "覆盖守卫闭合（Coverage Guard Closure）",
            "后续边界清楚（Remaining Boundary）"
        ).forEach { token ->
            assertTrue("mapping guide must document phase criterion $token", token in mappingGuide)
        }

        listOf(
            "下一阶段边界（Next Phase Boundary）",
            "真实业务数据接入（Real Data Wiring）",
            "完整事件链路接入（Full Event Wiring）",
            "动效细节实现（Motion Implementation）",
            "可交互 UI test（Interactive UI Tests）"
        ).forEach { token ->
            assertTrue("branch contents must keep next phase boundary $token", token in branchContents)
        }

        listOf("staged 内容", "未跟踪目录", "提交整理（Commit Planning）").forEach { staleToken ->
            assertTrue("branch contents must not keep stale temporary wording $staleToken", staleToken !in branchContents)
        }

        assertTrue(
            "mapping guide must keep real data outside current input completion",
            "真实业务数据、完整事件链路、动效细节和端到端 UI test 属于下一阶段" in mappingGuide
        )
        assertTrue(
            "branch notes must keep implementation source-of-truth boundary",
            "后续真实前端开发应以 shell kit、manifest、contracts 和页面 `COMPONENT_SPEC.md` 为准" in branchContents
        )
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun formalScreenDirs(): List<String> {
        val uiDesignRoot = workspacePath("docs/ui-design").toAbsolutePath().normalize()
        return Files.walk(uiDesignRoot).use { stream ->
            stream
                .filter { it.fileName.toString() == "UI设计图.png" }
                .map { uiDesignRoot.relativize(it.parent).toString().replace('\\', '/') }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    private fun manifestHtmlTargets(): List<String> =
        Regex(""""html"\s*:\s*"([^"]+)"""")
            .findAll(manifestSource)
            .map { it.groupValues[1] }
            .toList()

    private fun validationReportTargets(): List<String> {
        val resultsStart = validationReportSource.lastIndexOf("\n  \"results\": [")
        assertTrue("validation report must contain top-level results", resultsStart >= 0)
        return Regex(""""html"\s*:\s*"([^"]+)"""")
            .findAll(validationReportSource.substring(resultsStart))
            .map { it.groupValues[1].removePrefix("/Users/minliny/Documents/Reader for Android/") }
            .toList()
    }

    private fun componentReferenceSmokeCount(): Int =
        Regex("\"actualCount\"\\s*:\\s*(\\d+)")
            .find(validationReportSource)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Missing component reference smoke actualCount")

    private fun docTokenValue(token: String): Int =
        Regex("""`(\d+)`""")
            .find(token)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Missing numeric token in $token")
}
