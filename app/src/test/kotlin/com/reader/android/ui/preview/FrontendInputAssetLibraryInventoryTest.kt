package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class FrontendInputAssetLibraryInventoryTest {

    private val assetLibraryDir = workspacePath("docs/ui-design/frontend-input/asset-library").toAbsolutePath().normalize()

    private val fixtureJson: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/asset-library/fixture.json")
    }

    private val fixtureJs: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/asset-library/fixture.js")
    }

    private val iconsSource: String by lazy {
        workspaceSource("docs/ui-design/frontend-input/asset-library/icons.js")
    }

    @Test
    fun `asset library fixture tracks every UI design screen and cover asset`() {
        val screenPaths = assetScreenPaths()
        val coverPaths = assetBookCoverPaths()
        val expectedScreenPaths = formalScreenDirs().map { "../../$it/UI设计图.png" }.sorted()
        val expectedCoverPaths = bookCoverAssetPaths()

        assertEquals("fixture screenCount meta", 30, fixtureMetaCount("screenCount"))
        assertEquals("fixture bookCoverCount meta", 6, fixtureMetaCount("bookCoverCount"))
        assertEquals("fixture fixtureIconTokenCount meta", 55, fixtureMetaCount("fixtureIconTokenCount"))
        assertEquals("fixture validationScreenshotCount meta", 60, fixtureMetaCount("validationScreenshotCount"))
        assertEquals("asset fixture must list every UI design screen", expectedScreenPaths, screenPaths)
        assertEquals("asset fixture must list every book cover asset", expectedCoverPaths, coverPaths)
        assertEquals(
            "asset fixture screen shell counts",
            mapOf(
                "MainTabShell" to 4,
                "LibraryShell" to 8,
                "ReaderShell" to 10,
                "FlowShell" to 1,
                "SettingsShell" to 7
            ),
            assetScreenShells().groupingBy { it }.eachCount()
        )
        assertEquals("validation screenshot inventory", 60, validationScreenshotPaths().size)

        (screenPaths + coverPaths).forEach { relativePath ->
            assertTrue(
                "asset fixture path must exist: $relativePath",
                Files.exists(assetLibraryDir.resolve(relativePath).normalize())
            )
            assertTrue("fixture.js must mirror asset path $relativePath", relativePath in fixtureJs)
        }
    }

    @Test
    fun `asset library icon registry stays aligned with fixture groups and docs`() {
        val iconRegistryNames = assetIconNames()
        val iconGroupNames = fixtureIconGroupNames()
        val supplementedIcons = fixtureSupplementedIconNames()

        assertEquals("local asset icon token count", 90, iconRegistryNames.size)
        assertEquals("icon groups must list every local asset icon token once", iconRegistryNames, iconGroupNames)
        assertEquals("supplemented icon count", 35, supplementedIcons.size)
        assertTrue("supplemented icons must exist in the local icon registry", iconRegistryNames.containsAll(supplementedIcons))
        assertEquals(
            "fixture scanned icons plus supplemented icons must equal local icon inventory",
            90,
            fixtureMetaCount("fixtureIconTokenCount") + supplementedIcons.size
        )

        iconRegistryNames.forEach { iconName ->
            assertTrue("fixture.js must mirror icon token $iconName", """"$iconName"""" in fixtureJs || "$iconName:" in fixtureJs)
        }

        val assetLibraryMarkdown = workspaceSource("docs/ui-design/frontend-input/asset-library/ASSET_LIBRARY.md")
        val assetReadme = workspaceSource("docs/ui-design/frontend-input/asset-library/README.md")
        listOf(
            "UI 设计图（UI Design Screens） | 30",
            "书籍封面素材（Book Cover Assets） | 6",
            "fixture 图标 token（Fixture Icon Tokens） | 55",
            "补齐图标（Supplemented Icons） | 35",
            "本地总图标 token（Total Local Icon Tokens） | 90",
            "验证截图（Validation Screenshots） | 60"
        ).forEach { token ->
            assertTrue("ASSET_LIBRARY.md must document $token", token in assetLibraryMarkdown)
        }
        listOf("30 张 `UI设计图.png`", "6 张书架封面图片", "共 90 个本地 token", "60 张 `design-draft-*.png`").forEach { token ->
            assertTrue("asset-library README must document $token", token in assetReadme)
        }
    }

    @Test
    fun `asset library manifest target and validation report stay aligned`() {
        val manifestAssetTarget = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input/manifest.json"),
            startNeedle = """"name": "asset-library"""",
            endNeedle = """"name": "frontend-demo-draft""""
        )
        val validationAssetTarget = sectionAfter(
            source = workspaceSource("docs/ui-design/frontend-input-design-draft-validation.json"),
            startNeedle = """"name": "asset-library"""",
            endNeedle = """"name": "frontend-demo-draft""""
        )

        listOf(
            "\"html\": \"docs/ui-design/frontend-input/asset-library/preview.html\"",
            "\"screenshot\": \"docs/ui-design/frontend-input/asset-library/verify/asset-library-preview.png\"",
            "\"shellName\": \"AssetLibraryShell\"",
            "\"pageRole\": \"asset-library\"",
            "\"type\": \"asset-library\"",
            "\"90 个图标 token\"",
            "\"selector\": \".asset-icon-card\"",
            "\"min\": 90"
        ).forEach { token ->
            assertTrue("asset-library manifest target must contain $token", token in manifestAssetTarget)
        }

        listOf(
            "\"passed\": true",
            "\"shellName\": \"AssetLibraryShell\"",
            "\"pageRole\": \"asset-library\"",
            "\"type\": \"asset-library\"",
            "\"imageCount\": 36",
            "\"missingImages\": 0",
            "\"expectedDomCount\": 90"
        ).forEach { token ->
            assertTrue("asset-library validation report target must contain $token", token in validationAssetTarget)
        }
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun fixtureMetaCount(fieldName: String): Int =
        Regex("\"${Regex.escape(fieldName)}\"\\s*:\\s*(\\d+)")
            .find(fixtureJson)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Missing fixture meta field $fieldName")

    private fun assetScreenPaths(): List<String> =
        Regex("\"path\"\\s*:\\s*\"(\\.\\./\\.\\./0[2-5][^\"]*/UI设计图\\.png)\"")
            .findAll(fixtureJson)
            .map { it.groupValues[1] }
            .sorted()
            .toList()

    private fun assetScreenShells(): List<String> =
        Regex("\"shell\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(fixtureJson)
            .map { it.groupValues[1] }
            .toList()

    private fun assetBookCoverPaths(): List<String> =
        Regex("\"path\"\\s*:\\s*\"(\\.\\./\\.\\./02-主标签页/书架/bookshelf-cover-assets/[^\"]+\\.png)\"")
            .findAll(fixtureJson)
            .map { it.groupValues[1] }
            .sorted()
            .toList()

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

    private fun bookCoverAssetPaths(): List<String> {
        val coverRoot = workspacePath("docs/ui-design/02-主标签页/书架/bookshelf-cover-assets").toAbsolutePath().normalize()
        return Files.walk(coverRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".png") }
                .map { assetLibraryDir.relativize(it.toAbsolutePath().normalize()).toString().replace('\\', '/') }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    private fun validationScreenshotPaths(): List<String> {
        val uiDesignRoot = workspacePath("docs/ui-design").toAbsolutePath().normalize()
        return Files.walk(uiDesignRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { !it.toString().contains("frontend-input 2") }
                .filter { it.parent.fileName.toString() == "verify" }
                .filter { it.fileName.toString().startsWith("design-draft-") && it.fileName.toString().endsWith(".png") }
                .map { uiDesignRoot.relativize(it.toAbsolutePath().normalize()).toString().replace('\\', '/') }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    private fun assetIconNames(): List<String> =
        Regex("""^\s*(?:"([^"]+)"|([A-Za-z][A-Za-z0-9]*)):\s*'""", RegexOption.MULTILINE)
            .findAll(iconsSource)
            .map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            .sorted()
            .toList()

    private fun fixtureIconGroupNames(): List<String> {
        val iconGroupSource = sectionAfter(fixtureJson, """"iconGroups"""", """"supplementedIcons"""")
        return Regex("(?s)\"items\"\\s*:\\s*\\[(.*?)\\]")
            .findAll(iconGroupSource)
            .flatMap { match -> Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { it.groupValues[1] } }
            .sorted()
            .toList()
    }

    private fun fixtureSupplementedIconNames(): List<String> {
        val supplementedSource = Regex("(?s)\"supplementedIcons\"\\s*:\\s*\\[(.*?)\\]")
            .find(fixtureJson)
            ?.groupValues
            ?.get(1)
            ?: error("Missing supplementedIcons section")

        return Regex("\"([^\"]+)\"")
            .findAll(supplementedSource)
            .map { it.groupValues[1] }
            .sorted()
            .toList()
    }

    private fun sectionAfter(source: String, startNeedle: String, endNeedle: String): String {
        val start = source.indexOf(startNeedle)
        assertTrue("Missing section start $startNeedle", start >= 0)
        val end = source.indexOf(endNeedle, start + startNeedle.length)
        assertTrue("Missing section end $endNeedle", end > start)
        return source.substring(start, end)
    }
}
