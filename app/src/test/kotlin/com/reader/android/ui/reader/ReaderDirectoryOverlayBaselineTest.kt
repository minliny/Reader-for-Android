package com.reader.android.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderDirectoryOverlayBaselineTest {

    private val overlaySource: String by lazy {
        String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderBottomFunctionOverlay.kt"
        )))
    }

    private val modelSource: String by lazy {
        String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/ReaderRuntimeState.kt"
        )))
    }

    // ── TOC structure ──

    @Test
    fun `directory overlay has toc and bookmark tabs`() {
        listOf("目录", "书签").forEach { label ->
            assertTrue("Directory must have $label tab", label in overlaySource)
        }
    }

    @Test
    fun `directory overlay has volume info section`() {
        assertTrue("Directory must show volume info", "volumeInfo" in overlaySource)
    }

    @Test
    fun `directory overlay bottom shows current reading chapter`() {
        assertTrue("Directory must show current chapter at bottom",
            "当前阅读章节" in overlaySource)
        assertTrue("Directory must have currentChapter param", "currentChapter" in overlaySource)
    }

    // ── TOC entry fields ──

    @Test
    fun `toc entry has level support for chapter indentation`() {
        // level field exists and is used for indentation
        assertTrue("TocEntry must have level field", "level: Int" in overlaySource)
        assertTrue("Level must drive indentation", "entry.level" in overlaySource)
    }

    @Test
    fun `toc entry has right side progress bar`() {
        // progress field in TocEntry + rendered LinearProgressIndicator
        assertTrue("TocEntry must have progress field", "progress: Float?" in overlaySource)
        assertTrue("Progress must be rendered", "entry.progress" in overlaySource)
        assertTrue("Progress must use LinearProgressIndicator",
            "LinearProgressIndicator" in overlaySource)
    }

    @Test
    fun `toc entry uses level for indentation`() {
        // Level drives indentation: (entry.level - 1) * 10
        assertTrue("TOC must use level for indent", "entry.level" in overlaySource)
        assertTrue("TOC must compute indent from level", "(entry.level - 1)" in overlaySource)
    }

    @Test
    fun `toc entry has bookmark indicator`() {
        assertTrue("TocEntry must have hasBookmark field",
            "hasBookmark" in overlaySource)
        assertTrue("Bookmark must be rendered with icon",
            "Icons.Filled.ChevronRight" in overlaySource && "书签" in overlaySource)
    }

    @Test
    fun `toc entry has current reading indicator`() {
        assertTrue("TocEntry must have isCurrent field",
            "isCurrent" in overlaySource)
        assertTrue("Current must be indicated with MyLocation",
            "Icons.Filled.MyLocation" in overlaySource)
    }

    // ── TOC model ──

    @Test
    fun `reader toc entry model has progress field`() {
        assertTrue("ReaderTocEntryUiModel must have progress",
            "progress: Float?" in modelSource)
    }

    @Test
    fun `reader toc entry model has level field`() {
        assertTrue("ReaderTocEntryUiModel must have level", "level:" in modelSource)
    }

    @Test
    fun `reader toc entry model has isCurrent field`() {
        assertTrue("ReaderTocEntryUiModel must have isCurrent", "isCurrent:" in modelSource)
    }

    @Test
    fun `reader toc entry model has hasBookmark field`() {
        assertTrue("ReaderTocEntryUiModel must have hasBookmark", "hasBookmark:" in modelSource)
    }
}
