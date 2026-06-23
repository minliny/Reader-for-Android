package com.reader.android.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

class FrontendInputHtmlInventoryTest {

    @Test
    fun `local html inventory stays classified by frontend input role`() {
        val screenDirs = formalScreenDirs()
        val allHtml = htmlFilesUnder("docs/ui-design")
        val frontendInputHtml = allHtml.filter { "/frontend-input/" in it }
        val expectedPagePreviews = screenDirs.map { "docs/ui-design/$it/frontend-input/preview.html" }.sorted()
        val expectedStateMatrices = screenDirs.map { "docs/ui-design/$it/frontend-input/state-matrix.html" }.sorted()
        val expectedComponentReferences = screenDirs.map { "docs/ui-design/$it/frontend-input/components.html" }.sorted()
        val expectedSharedTargets = listOf(
            "docs/ui-design/frontend-input/asset-library/preview.html",
            "docs/ui-design/frontend-input/component-library/preview.html",
            "docs/ui-design/frontend-input/frontend-demo-draft/index.html",
            "docs/ui-design/frontend-input/shared-shell-kit/preview.html"
        ).sorted()
        val expectedLegacyPreviewCandidates = listOf(
            "docs/ui-design/02-主标签页/书架/frontend-input/preview 2.html",
            "docs/ui-design/04-阅读链路/阅读控制层/frontend-input/preview 2.html"
        ).sorted()
        val expectedReviewDemos = listOf(
            "docs/ui-design/frontend-input/bookshelf-demo/index.html"
        ).sorted()
        val expectedStandaloneReproductions = listOf(
            "docs/ui-design/02-主标签页/书架/bookshelf-cover-mode.html",
            "docs/ui-design/02-主标签页/书架/frontend-demo/index.html",
            "docs/ui-design/04-阅读链路/阅读控制层/reader-control-layer.html"
        ).sorted()

        assertEquals("formal UI design screen count", 30, screenDirs.size)
        assertEquals("all local HTML files under docs/ui-design", 100, allHtml.size)
        assertEquals("HTML files under any frontend-input directory", 97, frontendInputHtml.size)
        assertEquals("formal page preview count", 30, expectedPagePreviews.size)
        assertEquals("formal state matrix count", 30, expectedStateMatrices.size)
        assertEquals("component reference count", 30, expectedComponentReferences.size)

        assertEquals(
            "frontend-input HTML inventory must equal formal pages, legacy previews, and shared targets",
            (expectedPagePreviews + expectedStateMatrices + expectedComponentReferences +
                expectedLegacyPreviewCandidates + expectedSharedTargets + expectedReviewDemos).sorted(),
            frontendInputHtml
        )
        assertEquals(
            "local HTML inventory must equal frontend-input HTML plus standalone historical reproductions",
            (frontendInputHtml + expectedStandaloneReproductions).sorted(),
            allHtml
        )
        assertEquals(
            "legacy preview candidates must stay explicit and excluded from the manifest",
            expectedLegacyPreviewCandidates,
            allHtml.filter { it.endsWith("/preview 2.html") }
        )

        val manifestTargets = manifestHtmlTargets()
        assertEquals(
            "manifest must contain only preview, state matrix, and shared library/demo targets",
            (expectedPagePreviews + expectedStateMatrices + expectedSharedTargets).sorted(),
            manifestTargets.sorted()
        )
        assertTrue(
            "manifest must not include component reference pages",
            manifestTargets.none { it.endsWith("/components.html") }
        )
        assertTrue(
            "manifest must not include legacy preview candidates",
            expectedLegacyPreviewCandidates.none { it in manifestTargets }
        )
        assertTrue(
            "manifest must not include review demo pages",
            expectedReviewDemos.none { it in manifestTargets }
        )
        assertTrue(
            "manifest must not include standalone historical reproductions",
            expectedStandaloneReproductions.none { it in manifestTargets }
        )
    }

    private fun workspacePath(path: String): Path =
        Paths.get("..").resolve(path)

    private fun workspaceSource(path: String): String =
        String(Files.readAllBytes(workspacePath(path)))

    private fun htmlFilesUnder(path: String): List<String> {
        val root = workspacePath(path).toAbsolutePath().normalize()
        val workspaceRoot = workspacePath("").toAbsolutePath().normalize()
        return Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".html") }
                .map { workspaceRoot.relativize(it.toAbsolutePath().normalize()).toString().replace('\\', '/') }
                .filter { isTrackedHtmlInventoryPath(it) }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    private fun isTrackedHtmlInventoryPath(path: String): Boolean {
        if ("/frontend-input 2/" in path) return false
        if ("/asset-library 2/" in path) return false
        if ("/component-library 2/" in path) return false
        if ("/shared-shell-kit 2/" in path) return false
        if ("/frontend-demo-draft 2/" in path) return false
        if ("/frontend-demo 2/" in path) return false
        if ("/interactive-demo/" in path) return false
        if ("/91-历史归档/" in path) return false
        if (path.endsWith(" 2.html")) {
            return path.endsWith("02-主标签页/书架/frontend-input/preview 2.html") ||
                path.endsWith("04-阅读链路/阅读控制层/frontend-input/preview 2.html")
        }
        return true
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
            .findAll(workspaceSource("docs/ui-design/frontend-input/manifest.json"))
            .map { it.groupValues[1] }
            .toList()
    }
}
