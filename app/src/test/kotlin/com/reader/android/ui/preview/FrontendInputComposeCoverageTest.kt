package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class FrontendInputComposeCoverageTest {

    private data class CoverageEntry(
        val name: String,
        val specPath: String,
        val sourcePaths: List<String>,
        val previewPath: String,
        val sourceTokens: List<String>,
        val previewTokens: List<String>
    )

    private data class ContractEntry(
        val specPath: String,
        val typeName: String
    )

    private data class ManifestTarget(
        val html: String,
        val shellName: String,
        val pageRole: String,
        val slots: List<String>
    )

    private data class ShellContract(
        val pageRole: String,
        val slots: List<String>
    )

    private val manifestSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/manifest.json")
    }

    private val contractsSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/contracts.d.ts")
    }

    private val validationReportSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json")
    }

    @Test
    fun `formal frontend input pages have compose state mapping coverage`() {
        coverageEntries.forEach { entry ->
            val spec = workspaceSource(entry.specPath)
            assertTrue("${entry.name} spec must declare states", "## States" in spec || "## 状态" in spec || "状态矩阵" in spec)
            assertTrue("${entry.name} spec must declare event contract entries", eventContractLines(spec).isNotEmpty())

            val basePath = entry.specPath.removeSuffix("COMPONENT_SPEC.md")
            assertTrue("${entry.name} preview target must be in manifest", "${basePath}preview.html" in manifestSource)
            assertTrue("${entry.name} state matrix target must be in manifest", "${basePath}state-matrix.html" in manifestSource)

            val composeSource = entry.sourcePaths.joinToString(separator = "\n") { appSource(it) }
            entry.sourceTokens.forEach { token ->
                assertTrue("${entry.name} compose source must contain $token", token in composeSource)
            }

            val previewSource = appSource(entry.previewPath)
            entry.previewTokens.forEach { token ->
                assertTrue("${entry.name} preview source must contain $token", token in previewSource)
            }
        }
    }

    @Test
    fun `formal frontend input pages have global data state event contracts`() {
        val coverageSpecPaths = coverageEntries.map { it.specPath }.sorted()
        val contractSpecPaths = contractEntries.map { it.specPath }.sorted()
        assertEquals("contract entries must match coverage entries", coverageSpecPaths, contractSpecPaths)

        contractEntries.forEach { entry ->
            val spec = workspaceSource(entry.specPath)
            val specStates = stateContractLines(spec)
                .map { contractName(it) }
                .sorted()
            val contractStates = contractStateNames(entry.typeName).sorted()
            val manifestStateCardCounts = manifestStateCardCounts(entry.specPath)
            val specEvents = eventContractLines(spec)
                .map { contractName(it) }
                .sorted()
            val contractEvents = contractEventNames(entry.typeName).sorted()

            assertTrue(
                "${entry.specPath} must declare ${entry.typeName}Fixture",
                "interface ${entry.typeName}Fixture" in contractsSource
            )
            assertTrue(
                "${entry.specPath} must declare ${entry.typeName}State",
                "type ${entry.typeName}State" in contractsSource
            )
            assertTrue(
                "${entry.specPath} must declare ${entry.typeName}Event",
                "type ${entry.typeName}Event" in contractsSource
            )
            assertEquals(
                "${entry.specPath} states must match ${entry.typeName}State",
                specStates,
                contractStates
            )
            assertEquals(
                "${entry.specPath} manifest state matrix card counts must match ${entry.typeName}State",
                listOf(contractStates.size, contractStates.size),
                manifestStateCardCounts
            )
            assertEquals(
                "${entry.specPath} events must match ${entry.typeName}Event",
                specEvents,
                contractEvents
            )
        }
    }

    @Test
    fun `ui design screens have formal frontend input packages`() {
        val screenDirs = formalScreenDirs()
        val coveredScreenDirs = coverageEntries
            .map { it.specPath.removePrefix("docs/ui-design/").removeSuffix("/frontend-input/COMPONENT_SPEC.md") }
            .sorted()

        assertEquals("formal UI design screen count", 30, screenDirs.size)
        assertEquals("coverage entries must match UI design screens", screenDirs, coveredScreenDirs)

        screenDirs.forEach { dir ->
            requiredInputFiles.forEach { fileName ->
                assertTrue(
                    "$dir frontend input package must contain $fileName",
                    Files.exists(workspacePath("docs/ui-design/$dir/frontend-input/$fileName"))
                )
            }
            assertTrue("$dir preview target must be in manifest", "docs/ui-design/$dir/frontend-input/preview.html" in manifestSource)
            assertTrue("$dir state matrix target must be in manifest", "docs/ui-design/$dir/frontend-input/state-matrix.html" in manifestSource)
        }
    }

    @Test
    fun `manifest target set only contains formal frontend input pages and library previews`() {
        val screenDirs = formalScreenDirs()
        val manifestTargets = manifestHtmlTargets()
        val expectedPreviewTargets = screenDirs
            .map { "docs/ui-design/$it/frontend-input/preview.html" }
            .sorted()
        val expectedStateMatrixTargets = screenDirs
            .map { "docs/ui-design/$it/frontend-input/state-matrix.html" }
            .sorted()
        val expectedLibraryTargets = listOf(
            "docs/ui-design/frontend-input/component-library/preview.html",
            "docs/ui-design/frontend-input/shared-shell-kit/preview.html",
            "docs/ui-design/frontend-input/asset-library/preview.html",
            "docs/ui-design/frontend-input/frontend-demo-draft/index.html"
        ).sorted()

        assertEquals("manifest target count", 64, manifestTargets.size)
        assertEquals("manifest html targets must be unique", manifestTargets.size, manifestTargets.distinct().size)
        assertEquals(
            "manifest page preview targets must match UI design screens",
            expectedPreviewTargets,
            manifestTargets
                .filter { it.startsWith("docs/ui-design/0") && it.endsWith("/frontend-input/preview.html") }
                .sorted()
        )
        assertEquals(
            "manifest state matrix targets must match UI design screens",
            expectedStateMatrixTargets,
            manifestTargets
                .filter { it.startsWith("docs/ui-design/0") && it.endsWith("/frontend-input/state-matrix.html") }
                .sorted()
        )
        assertEquals(
            "manifest non-page targets must stay limited to shared libraries and demo draft",
            expectedLibraryTargets,
            manifestTargets
                .filter { it.startsWith("docs/ui-design/frontend-input/") }
                .sorted()
        )
        assertTrue(
            "manifest must not include legacy temporary preview pages",
            manifestTargets.none { it.contains("preview 2.html") }
        )
        assertTrue(
            "manifest must not include component reference pages",
            manifestTargets.none { it.endsWith("/frontend-input/components.html") }
        )
    }

    @Test
    fun `manifest shell metadata matches formal shell taxonomy`() {
        val targets = manifestTargets()
        assertEquals("manifest metadata target count", 64, targets.size)

        targets.forEach { target ->
            assertTrue(
                "${target.html} shellName must be a formal shell",
                target.shellName in shellContracts
            )
            val contract = shellContracts.getValue(target.shellName)
            assertEquals("${target.html} pageRole must match ${target.shellName}", contract.pageRole, target.pageRole)
            assertEquals("${target.html} slots must match ${target.shellName}", contract.slots, target.slots)
        }

        assertEquals(
            "manifest shell target counts",
            mapOf(
                "ComponentLibraryShell" to 3,
                "AssetLibraryShell" to 1,
                "MainTabShell" to 8,
                "LibraryShell" to 16,
                "ReaderShell" to 20,
                "FlowShell" to 2,
                "SettingsShell" to 14
            ),
            targets.groupingBy { it.shellName }.eachCount()
        )
    }

    @Test
    fun `frontend input validation report matches manifest and component references`() {
        val screenDirs = formalScreenDirs()
        val manifestTargets = manifestHtmlTargets().sorted()
        val reportHtmlTargets = validationReportHtmlTargets()
        val reportManifestTargets = reportHtmlTargets
            .filterNot { it.endsWith("/frontend-input/components.html") }
            .sorted()
        val reportComponentTargets = reportHtmlTargets
            .filter { it.endsWith("/frontend-input/components.html") }
            .sorted()
        val expectedComponentTargets = screenDirs
            .map { "docs/ui-design/$it/frontend-input/components.html" }
            .sorted()

        assertTrue(
            "validation report must point at frontend input manifest",
            """"manifest": "docs/ui-design/frontend-input/manifest.json"""" in validationReportSource
        )
        assertTrue(
            "validation report must pass",
            Regex(""""passed"\s*:\s*true""").containsMatchIn(validationReportSource)
        )
        assertTrue(
            "validation report must not contain failed results",
            !Regex(""""passed"\s*:\s*false""").containsMatchIn(validationReportSource)
        )
        listOf("failures", "failedRequests", "consoleErrors", "pageErrors").forEach { field ->
            assertTrue(
                "validation report must not contain $field entries",
                !Regex("(?s)\"$field\"\\s*:\\s*\\[(?!\\s*\\])")
                    .containsMatchIn(validationReportSource)
            )
        }
        assertTrue(
            "component reference smoke must expect 30 pages",
            """"expectedCount": 30""" in validationReportSource
        )
        assertTrue(
            "component reference smoke must find 30 pages",
            """"actualCount": 30""" in validationReportSource
        )
        assertEquals("validation report target count", 64, reportManifestTargets.size)
        assertEquals("validation report targets must match manifest targets", manifestTargets, reportManifestTargets)
        assertEquals(
            "validation report component reference smoke targets must match UI design screens",
            expectedComponentTargets,
            reportComponentTargets
        )
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun appSource(path: String): String =
        String(Files.readAllBytes(Paths.get(path)))

    private fun eventContractLines(spec: String): List<String> {
        return contractLines(spec, "Events")
    }

    private fun stateContractLines(spec: String): List<String> {
        return contractLines(spec, "States") + contractLines(spec, "状态")
    }

    private fun contractLines(spec: String, heading: String): List<String> {
        val content = Regex("""(?ms)^## ${Regex.escape(heading)}\s*\n(.*?)(?=^## |\z)""")
            .find(spec)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        return content.lines()
            .map { it.trim() }
            .filter { it.startsWith("- `") }
    }

    private fun contractName(line: String): String {
        val raw = Regex("""`([^`]+)`""")
            .find(line)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        return raw.substringBefore("(")
    }

    private fun contractStateNames(typeName: String): List<String> {
        val section = Regex("""(?ms)^export type ${Regex.escape(typeName)}State\s*=\s*(.*?);""")
            .find(contractsSource)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        return Regex(""""([^"]*)"""")
            .findAll(section)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun manifestStateCardCounts(specPath: String): List<Int> {
        val stateMatrixPath = specPath.removeSuffix("COMPONENT_SPEC.md") + "state-matrix.html"
        val htmlNeedle = "\"$stateMatrixPath\""
        val htmlIndex = manifestSource.indexOf(htmlNeedle)
        assertTrue("$stateMatrixPath must be in manifest", htmlIndex >= 0)

        val nextTargetIndex = manifestSource.indexOf("\n    {\n      \"name\"", startIndex = htmlIndex + htmlNeedle.length)
        val targetSource = if (nextTargetIndex >= 0) {
            manifestSource.substring(htmlIndex, nextTargetIndex)
        } else {
            manifestSource.substring(htmlIndex)
        }

        return Regex(""""expectedStateCards"\s*:\s*(\d+)""")
            .findAll(targetSource)
            .map { it.groupValues[1].toInt() }
            .toList()
    }

    private fun contractEventNames(typeName: String): List<String> {
        val section = Regex("""(?ms)^export type ${Regex.escape(typeName)}Event\s*=\s*(.*?)(?=^export |\z)""")
            .find(contractsSource)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        return Regex("""type:\s*"([^"]+)"""")
            .findAll(section)
            .map { it.groupValues[1] }
            .toList()
    }

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

    private fun manifestHtmlTargets(): List<String> {
        return Regex(""""html"\s*:\s*"([^"]+)"""")
            .findAll(manifestSource)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun validationReportHtmlTargets(): List<String> {
        return Regex(""""html"\s*:\s*"([^"]+)"""")
            .findAll(validationReportSource)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun manifestTargets(): List<ManifestTarget> {
        return manifestTargetSections().map { section ->
            ManifestTarget(
                html = manifestField(section, "html"),
                shellName = manifestField(section, "shellName"),
                pageRole = manifestField(section, "pageRole"),
                slots = manifestSlots(section)
            )
        }
    }

    private fun manifestTargetSections(): List<String> {
        val targetsStart = manifestSource.indexOf(""""targets"""")
        assertTrue("manifest must contain targets", targetsStart >= 0)

        return Regex("""(?ms)\n    \{\n      "name":.*?(?=\n    \{\n      "name":|\n  \]\n\})""")
            .findAll(manifestSource.substring(targetsStart))
            .map { it.value }
            .toList()
    }

    private fun manifestField(section: String, fieldName: String): String {
        return Regex(""""${Regex.escape(fieldName)}"\s*:\s*"([^"]+)"""")
            .find(section)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    }

    private fun manifestSlots(section: String): List<String> {
        val slotsSource = Regex("""(?ms)"slots"\s*:\s*\[(.*?)\]""")
            .find(section)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        return Regex(""""([^"]+)"""")
            .findAll(slotsSource)
            .map { it.groupValues[1] }
            .toList()
    }

    private companion object {
        private const val MainTabPreview = "src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt"
        private const val LibraryPreview = "src/main/kotlin/com/reader/android/ui/preview/LibraryFlowStateMatrixPreviews.kt"
        private const val ReaderPreview = "src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt"
        private const val ReaderControlPreview = "src/main/kotlin/com/reader/android/ui/preview/ReaderControlStateMatrixPreviews.kt"
        private const val SettingsPreview = "src/main/kotlin/com/reader/android/ui/preview/SettingsSecondaryStateMatrixPreviews.kt"

        private val requiredInputFiles = listOf(
            "fixture.json",
            "fixture.js",
            "render.js",
            "preview.html",
            "state-matrix.html",
            "components.html",
            "README.md",
            "COMPONENT_SPEC.md"
        )

        private val shellContracts = mapOf(
            "ComponentLibraryShell" to ShellContract(
                pageRole = "component-library",
                slots = listOf("foundations", "appShell", "basicControls", "cardsRows", "sheetsPanels", "states")
            ),
            "AssetLibraryShell" to ShellContract(
                pageRole = "asset-library",
                slots = listOf("foundations", "screenAssets", "iconAssets", "bookCoverAssets", "missingSupplements", "usageRules")
            ),
            "MainTabShell" to ShellContract(
                pageRole = "main-tab-root",
                slots = listOf("appFrame", "statusBar", "appTopBar", "contentRegion", "mainNav", "stateHost")
            ),
            "LibraryShell" to ShellContract(
                pageRole = "library-stack",
                slots = listOf("stackFrame", "backTopBar", "contentRegion", "bottomActionHost", "sheetHost", "dialogHost", "stateHost")
            ),
            "ReaderShell" to ShellContract(
                pageRole = "reader-flow",
                slots = listOf("readerFrame", "readingSurface", "readerOverlayHost", "readerModuleNav", "bottomSheetHost", "readerStateHost")
            ),
            "FlowShell" to ShellContract(
                pageRole = "landscape-flow",
                slots = listOf("flowFrame", "stepRegion", "comparisonRegion", "resultRegion", "stateHost")
            ),
            "SettingsShell" to ShellContract(
                pageRole = "settings-stack",
                slots = listOf("settingsFrame", "backTopBar", "settingsContent", "settingSection", "toastHost", "dialogHost", "settingsStateHost")
            )
        )

        private val contractEntries = listOf(
            ContractEntry(
                specPath = "docs/ui-design/02-主标签页/书架/frontend-input/COMPONENT_SPEC.md",
                typeName = "Bookshelf"
            ),
            ContractEntry(
                specPath = "docs/ui-design/02-主标签页/发现/frontend-input/COMPONENT_SPEC.md",
                typeName = "DiscoveryHome"
            ),
            ContractEntry(
                specPath = "docs/ui-design/02-主标签页/RSS/frontend-input/COMPONENT_SPEC.md",
                typeName = "RssHome"
            ),
            ContractEntry(
                specPath = "docs/ui-design/02-主标签页/设置/frontend-input/COMPONENT_SPEC.md",
                typeName = "SettingsHome"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/书架空状态/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookshelfEmpty"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/书籍搜索/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookSearch"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/书籍详情/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookDetail"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/书籍目录/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookDirectory"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/排序与筛选/frontend-input/COMPONENT_SPEC.md",
                typeName = "SortFilter"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/书籍操作底表/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookActionSheet"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/分组管理/frontend-input/COMPONENT_SPEC.md",
                typeName = "GroupManagement"
            ),
            ContractEntry(
                specPath = "docs/ui-design/03-书架链路/本地书导入/frontend-input/COMPONENT_SPEC.md",
                typeName = "LocalImport"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/阅读控制层/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReaderControl"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/阅读入口/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReadingEntry"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/沉浸阅读/frontend-input/COMPONENT_SPEC.md",
                typeName = "ImmersiveReading"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/目录与书签/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReadingTocBookmark"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/阅读外观/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReadingAppearance"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/朗读/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReadingAloud"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/阅读设置/frontend-input/COMPONENT_SPEC.md",
                typeName = "ReadingSettings"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/自动翻页/frontend-input/COMPONENT_SPEC.md",
                typeName = "AutoPage"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/内容搜索/frontend-input/COMPONENT_SPEC.md",
                typeName = "ContentSearch"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/内容替换/frontend-input/COMPONENT_SPEC.md",
                typeName = "ContentReplacement"
            ),
            ContractEntry(
                specPath = "docs/ui-design/04-阅读链路/换源/frontend-input/COMPONENT_SPEC.md",
                typeName = "SourceSwitch"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/App通用设置/frontend-input/COMPONENT_SPEC.md",
                typeName = "GeneralSettings"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/书架与搜索设置/frontend-input/COMPONENT_SPEC.md",
                typeName = "BookshelfSearchSettings"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/隐私与权限/frontend-input/COMPONENT_SPEC.md",
                typeName = "PrivacyPermissions"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/缓存管理/frontend-input/COMPONENT_SPEC.md",
                typeName = "CacheManagement"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/关于与反馈/frontend-input/COMPONENT_SPEC.md",
                typeName = "AboutFeedback"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/同步与备份/frontend-input/COMPONENT_SPEC.md",
                typeName = "SyncBackup"
            ),
            ContractEntry(
                specPath = "docs/ui-design/05-设置链路/书源管理/frontend-input/COMPONENT_SPEC.md",
                typeName = "SourceManagement"
            )
        )

        private val coverageEntries = listOf(
            CoverageEntry(
                name = "书架主标签页",
                specPath = "docs/ui-design/02-主标签页/书架/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("BookshelfHomeMapper", "fun filtering()", "fun loading()", "fun empty()"),
                previewTokens = listOf("BookshelfMainTabDefaultPreview", "BookshelfMainTabFilteringPreview", "BookshelfMainTabLoadingPreview", "BookshelfMainTabEmptyPreview")
            ),
            CoverageEntry(
                name = "发现主标签页",
                specPath = "docs/ui-design/02-主标签页/发现/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/discover/DiscoveryHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("DiscoveryHomeMapper", "fun subscription()", "fun loading()", "fun empty()", "fun error()", "fun offline()"),
                previewTokens = listOf("DiscoverMainTabDefaultPreview", "DiscoverMainTabSubscriptionPreview", "DiscoverMainTabLoadingPreview", "DiscoverMainTabEmptyPreview", "DiscoverMainTabErrorPreview", "DiscoverMainTabOfflinePreview")
            ),
            CoverageEntry(
                name = "RSS 主标签页",
                specPath = "docs/ui-design/02-主标签页/RSS/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/discover/RssHomeDesignUiState.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("RssHomeDesignMapper", "fun loading()", "fun empty()", "fun unreadEmpty()", "fun error()"),
                previewTokens = listOf("RssMainTabDefaultPreview", "RssMainTabLoadingPreview", "RssMainTabEmptyPreview", "RssMainTabUnreadEmptyPreview", "RssMainTabErrorPreview")
            ),
            CoverageEntry(
                name = "设置主标签页",
                specPath = "docs/ui-design/02-主标签页/设置/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/MineScreen.kt"),
                previewPath = MainTabPreview,
                sourceTokens = listOf("SettingsHomeMapper", "fun loadingOverview()", "fun noBackup()", "fun permissionNeeded()"),
                previewTokens = listOf("SettingsMainTabDefaultPreview", "SettingsMainTabLoadingOverviewPreview", "SettingsMainTabNoBackupPreview", "SettingsMainTabPermissionNeededPreview")
            ),
            CoverageEntry(
                name = "书架空状态",
                specPath = "docs/ui-design/03-书架链路/书架空状态/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfEmptyDesignUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfEmptyDesignMapper", "fun allEmpty()", "fun loading()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("LibraryBookshelfEmptyDefaultPreview", "LibraryBookshelfEmptyAllEmptyPreview", "LibraryBookshelfEmptyLoadingPreview", "LibraryBookshelfEmptyErrorPreview", "LibraryBookshelfEmptyOfflinePreview", "LibraryBookshelfEmptyPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍搜索",
                specPath = "docs/ui-design/03-书架链路/书籍搜索/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("SearchAdapterShell", "searchHome", "searchResults", "searchLoading", "searchEmpty", "searchError"),
                previewTokens = listOf("LibrarySearchHomePreview", "LibrarySearchResultsPreview", "LibrarySearchLoadingPreview", "LibrarySearchEmptyPreview", "LibrarySearchErrorPreview", "LibrarySearchOfflinePreview", "LibrarySearchPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍详情",
                specPath = "docs/ui-design/03-书架链路/书籍详情/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookDetailAdapterShell", "detailReady", "detailLoading", "detailEmpty", "detailError"),
                previewTokens = listOf("LibraryBookDetailDefaultPreview", "LibraryBookDetailLoadingPreview", "LibraryBookDetailEmptyPreview", "LibraryBookDetailErrorPreview", "LibraryBookDetailOfflinePreview", "LibraryBookDetailPermissionPreview")
            ),
            CoverageEntry(
                name = "书籍目录",
                specPath = "docs/ui-design/03-书架链路/书籍目录/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/toc/BookDirectoryUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookDirectoryUiStateMapper", "fromFixture", "loading", "empty", "error"),
                previewTokens = listOf("LibraryBookDirectoryDefaultPreview", "LibraryBookDirectoryLoadingPreview", "LibraryBookDirectoryEmptyPreview", "LibraryBookDirectoryErrorPreview")
            ),
            CoverageEntry(
                name = "排序与筛选",
                specPath = "docs/ui-design/03-书架链路/排序与筛选/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfSortFilterUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfSortFilterMapper", "fun selected()", "fun empty()", "fun error("),
                previewTokens = listOf("LibrarySortFilterDefaultPreview", "LibrarySortFilterSelectedPreview", "LibrarySortFilterEmptyPreview", "LibrarySortFilterErrorPreview")
            ),
            CoverageEntry(
                name = "书籍操作底表",
                specPath = "docs/ui-design/03-书架链路/书籍操作底表/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfActionSheetDesignUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfActionSheetMapper", "fun danger()", "fun loading()", "fun error()"),
                previewTokens = listOf("LibraryBookActionSheetDefaultPreview", "LibraryBookActionSheetDangerPreview", "LibraryBookActionSheetLoadingPreview", "LibraryBookActionSheetErrorPreview")
            ),
            CoverageEntry(
                name = "分组管理",
                specPath = "docs/ui-design/03-书架链路/分组管理/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfGroupManagementUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfGroupManagementMapper", "fun newGroup()", "fun rename()", "fun delete()", "fun empty()", "fun loading()", "fun error("),
                previewTokens = listOf("LibraryGroupManagementDefaultPreview", "LibraryGroupManagementNewPreview", "LibraryGroupManagementRenamePreview", "LibraryGroupManagementDeletePreview", "LibraryGroupManagementEmptyPreview", "LibraryGroupManagementLoadingPreview", "LibraryGroupManagementErrorPreview")
            ),
            CoverageEntry(
                name = "本地书导入",
                specPath = "docs/ui-design/03-书架链路/本地书导入/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfLocalImportUiState.kt"),
                previewPath = LibraryPreview,
                sourceTokens = listOf("BookshelfLocalImportMapper", "fun importing()", "fun success()", "fun partialFailed()", "fun failed()", "fun pickerCancelled()"),
                previewTokens = listOf("LibraryLocalImportDefaultPreview", "LibraryLocalImportImportingPreview", "LibraryLocalImportSuccessPreview", "LibraryLocalImportPartialFailedPreview", "LibraryLocalImportFailedPreview", "LibraryLocalImportPickerCancelledPreview")
            ),
            CoverageEntry(
                name = "阅读控制层",
                specPath = "docs/ui-design/04-阅读链路/阅读控制层/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderBottomFunctionOverlay.kt",
                    "src/main/kotlin/com/reader/android/ui/reader/components/ReaderQuickActionOverlay.kt"
                ),
                previewPath = ReaderControlPreview,
                sourceTokens = listOf("ReaderControlBase", "ReaderBottomPanel", "ReaderQuickActionPanel"),
                previewTokens = listOf("ReaderControlDefaultPreview", "ReaderControlDirectoryModulePreview", "ReaderControlTtsModulePreview", "ReaderControlAppearanceModulePreview", "ReaderControlSettingsModulePreview")
            ),
            CoverageEntry(
                name = "阅读入口",
                specPath = "docs/ui-design/04-阅读链路/阅读入口/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReaderShellDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingEntryMapper", "fun loading()", "fun error()", "fun offline()"),
                previewTokens = listOf("ReadingEntryDefaultPreview", "ReadingEntryLoadingPreview", "ReadingEntryErrorPreview", "ReadingEntryOfflinePreview")
            ),
            CoverageEntry(
                name = "沉浸阅读",
                specPath = "docs/ui-design/04-阅读链路/沉浸阅读/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReaderShellDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ImmersiveReadingMapper", "fun loading()", "fun error()", "fun offline()"),
                previewTokens = listOf("ImmersiveReadingDefaultPreview", "ImmersiveReadingLoadingPreview", "ImmersiveReadingErrorPreview", "ImmersiveReadingOfflinePreview")
            ),
            CoverageEntry(
                name = "目录与书签",
                specPath = "docs/ui-design/04-阅读链路/目录与书签/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingTocBookmarkMapper", "fun bookmark()", "fun search()", "fun empty()", "fun loading()", "fun error()", "fun moreMenu()"),
                previewTokens = listOf("ReadingTocBookmarkDefaultPreview", "ReadingTocBookmarkBookmarkPreview", "ReadingTocBookmarkSearchPreview", "ReadingTocBookmarkEmptyPreview", "ReadingTocBookmarkLoadingPreview", "ReadingTocBookmarkErrorPreview", "ReadingTocBookmarkMoreMenuPreview")
            ),
            CoverageEntry(
                name = "阅读外观",
                specPath = "docs/ui-design/04-阅读链路/阅读外观/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingAppearanceDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingAppearanceMapper", "fun font()", "fun theme()", "fun edit()", "fun loading()", "fun error()"),
                previewTokens = listOf("ReadingAppearanceDefaultPreview", "ReadingAppearanceFontPreview", "ReadingAppearanceThemePreview", "ReadingAppearanceEditPreview", "ReadingAppearanceLoadingPreview", "ReadingAppearanceErrorPreview")
            ),
            CoverageEntry(
                name = "朗读",
                specPath = "docs/ui-design/04-阅读链路/朗读/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingAloudDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingAloudMapper", "fun running()", "fun paused()", "fun error()"),
                previewTokens = listOf("ReadingAloudDefaultPreview", "ReadingAloudRunningPreview", "ReadingAloudPausedPreview", "ReadingAloudErrorPreview")
            ),
            CoverageEntry(
                name = "阅读设置",
                specPath = "docs/ui-design/04-阅读链路/阅读设置/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ReadingSettingsDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ReadingSettingsMapper", "fun subpage()", "fun loading()", "fun error()"),
                previewTokens = listOf("ReadingSettingsDefaultPreview", "ReadingSettingsSubpagePreview", "ReadingSettingsLoadingPreview", "ReadingSettingsErrorPreview")
            ),
            CoverageEntry(
                name = "自动翻页",
                specPath = "docs/ui-design/04-阅读链路/自动翻页/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/AutoPageDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("AutoPageMapper", "fun running()", "fun paused()", "fun error()"),
                previewTokens = listOf("AutoPageDefaultPreview", "AutoPageRunningPreview", "AutoPagePausedPreview", "AutoPageErrorPreview")
            ),
            CoverageEntry(
                name = "内容搜索",
                specPath = "docs/ui-design/04-阅读链路/内容搜索/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ContentSearchDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ContentSearchMapper", "fun loading()", "fun empty()", "fun error()", "fun offline()"),
                previewTokens = listOf("ContentSearchDefaultPreview", "ContentSearchLoadingPreview", "ContentSearchEmptyPreview", "ContentSearchErrorPreview", "ContentSearchOfflinePreview")
            ),
            CoverageEntry(
                name = "内容替换",
                specPath = "docs/ui-design/04-阅读链路/内容替换/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/ContentReplacementDesignUiState.kt"),
                previewPath = ReaderPreview,
                sourceTokens = listOf("ContentReplacementMapper", "fun edit()", "fun empty()", "fun loading()", "fun error()"),
                previewTokens = listOf("ContentReplacementDefaultPreview", "ContentReplacementEditPreview", "ContentReplacementEmptyPreview", "ContentReplacementLoadingPreview", "ContentReplacementErrorPreview")
            ),
            CoverageEntry(
                name = "换源",
                specPath = "docs/ui-design/04-阅读链路/换源/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt"),
                previewPath = "src/main/kotlin/com/reader/android/ui/reader/source/SourceSwitchFlowScreen.kt",
                sourceTokens = listOf("SourceSwitchFlowMapper", "fun loading()", "fun empty()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("SourceSwitchFlowDefaultPreview", "SourceSwitchFlowLoadingPreview", "SourceSwitchFlowEmptyPreview", "SourceSwitchFlowErrorPreview", "SourceSwitchFlowOfflinePreview", "SourceSwitchFlowPermissionPreview")
            ),
            CoverageEntry(
                name = "App 通用设置",
                specPath = "docs/ui-design/05-设置链路/App通用设置/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/GeneralSettingsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("GeneralSettingsMapper", "fun fromFixture()", "fun optionSheet()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsGeneralDefaultPreview", "SettingsGeneralOptionSheetPreview", "SettingsGeneralLoadingPreview", "SettingsGeneralErrorPreview", "SettingsGeneralPermissionPreview")
            ),
            CoverageEntry(
                name = "书架与搜索设置",
                specPath = "docs/ui-design/05-设置链路/书架与搜索设置/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/BookshelfSearchSettingsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("BookshelfSearchSettingsMapper", "fun fromFixture()", "fun optionSheet()", "fun confirm()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsBookshelfSearchDefaultPreview", "SettingsBookshelfSearchOptionSheetPreview", "SettingsBookshelfSearchConfirmPreview", "SettingsBookshelfSearchLoadingPreview", "SettingsBookshelfSearchErrorPreview", "SettingsBookshelfSearchPermissionPreview")
            ),
            CoverageEntry(
                name = "隐私与权限",
                specPath = "docs/ui-design/05-设置链路/隐私与权限/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/PrivacyPermissionsDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("PrivacyPermissionsMapper", "fun fromFixture()", "fun confirm()", "fun loading()", "fun error()", "fun permission()"),
                previewTokens = listOf("SettingsPrivacyPermissionsDefaultPreview", "SettingsPrivacyPermissionsConfirmPreview", "SettingsPrivacyPermissionsLoadingPreview", "SettingsPrivacyPermissionsErrorPreview", "SettingsPrivacyPermissionsPermissionPreview")
            ),
            CoverageEntry(
                name = "缓存管理",
                specPath = "docs/ui-design/05-设置链路/缓存管理/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/CacheManagementDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("CacheManagementMapper", "fun fromFixture()", "fun loading()", "fun empty()", "fun confirm()", "fun error()"),
                previewTokens = listOf("SettingsCacheManagementDefaultPreview", "SettingsCacheManagementLoadingPreview", "SettingsCacheManagementEmptyPreview", "SettingsCacheManagementConfirmPreview", "SettingsCacheManagementErrorPreview")
            ),
            CoverageEntry(
                name = "关于与反馈",
                specPath = "docs/ui-design/05-设置链路/关于与反馈/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/AboutFeedbackDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("AboutFeedbackMapper", "fun fromFixture()", "fun loading()", "fun error()", "fun confirm()", "fun offline()"),
                previewTokens = listOf("SettingsAboutFeedbackDefaultPreview", "SettingsAboutFeedbackLoadingPreview", "SettingsAboutFeedbackErrorPreview", "SettingsAboutFeedbackConfirmPreview", "SettingsAboutFeedbackOfflinePreview")
            ),
            CoverageEntry(
                name = "同步与备份",
                specPath = "docs/ui-design/05-设置链路/同步与备份/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf("src/main/kotlin/com/reader/android/ui/settings/SyncBackupDesignUiState.kt"),
                previewPath = SettingsPreview,
                sourceTokens = listOf("SyncBackupMapper", "fun fromFixture()", "fun confirm()", "fun loading()", "fun empty()", "fun error()", "fun offline()", "fun permission()"),
                previewTokens = listOf("SettingsSyncBackupDefaultPreview", "SettingsSyncBackupConfirmPreview", "SettingsSyncBackupLoadingPreview", "SettingsSyncBackupEmptyPreview", "SettingsSyncBackupErrorPreview", "SettingsSyncBackupOfflinePreview", "SettingsSyncBackupPermissionPreview")
            ),
            CoverageEntry(
                name = "书源管理",
                specPath = "docs/ui-design/05-设置链路/书源管理/frontend-input/COMPONENT_SPEC.md",
                sourcePaths = listOf(
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceManagementUiState.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/BookSourceScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceDetailScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceEditScreen.kt",
                    "src/main/kotlin/com/reader/android/ui/booksource/SourceImportScreen.kt"
                ),
                previewPath = "src/main/kotlin/com/reader/android/ui/preview/SourceManagementStateMatrixPreviews.kt",
                sourceTokens = listOf("SourceManagementMapper", "fun fakeFallback()", "fun importPreview", "fun importSuccess", "fun BookSourceScreen", "fun SourceDetailScreen", "fun SourceEditScreen", "fun SourceImportScreen"),
                previewTokens = listOf("SourceManagementListDefaultPreview", "SourceManagementListLoadingPreview", "SourceManagementListEmptyPreview", "SourceManagementListErrorPreview", "SourceManagementDetailEnabledPreview", "SourceManagementDetailDisabledPreview", "SourceManagementEditExistingPreview", "SourceManagementEditBlankPreview", "SourceManagementImportIdlePreview", "SourceManagementImportReadyJsonPreview", "SourceManagementImportImportedPreview", "SourceManagementImportErrorPreview")
            )
        )
    }
}
