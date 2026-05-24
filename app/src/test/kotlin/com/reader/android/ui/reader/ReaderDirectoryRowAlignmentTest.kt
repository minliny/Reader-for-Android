package com.reader.android.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderDirectoryRowAlignmentTest {

    private val overlaySource: String by lazy {
        String(Files.readAllBytes(Paths.get(
            "src/main/kotlin/com/reader/android/ui/reader/components/ReaderBottomFunctionOverlay.kt"
        )))
    }

    // Extract the TOC entry row body — the content inside itemsIndexed { ... }
    private val tocRowSection: String by lazy {
        overlaySource.substringAfter("itemsIndexed(tocEntries)").take(1800)
    }

    // ── Left alignment: chapter title is the first visible element after indent ──

    @Test
    fun `toc row places chapter title in left main area`() {
        // The title must use weight(1f) to occupy the primary left area
        assertTrue("Chapter title must use weight(1f)", "Modifier.weight(1f)" in tocRowSection)
        // Title must appear before any icon in the composable tree
        val titleIndex = tocRowSection.indexOf("entry.title")
        val bookmarkIconIndex = tocRowSection.indexOf("Icons.Filled.Bookmark")
        val locationIconIndex = tocRowSection.indexOf("Icons.Filled.MyLocation")
        assertTrue("Chapter title must be rendered before bookmark icon",
            titleIndex < bookmarkIconIndex || bookmarkIconIndex == -1)
        assertTrue("Chapter title must be rendered before current chapter icon",
            titleIndex < locationIconIndex || locationIconIndex == -1)
    }

    @Test
    fun `toc row does not place bookmark icon before chapter title`() {
        // Bookmark icon must appear AFTER the title, not before it
        val titleIndex = tocRowSection.indexOf("entry.title")
        val bookmarkIndex = tocRowSection.indexOf("Icons.Filled.Bookmark")
        if (bookmarkIndex > 0) {
            assertTrue("Bookmark icon must be after chapter title",
                bookmarkIndex > titleIndex)
        }
    }

    @Test
    fun `toc row does not place current chapter icon before chapter title`() {
        val titleIndex = tocRowSection.indexOf("entry.title")
        val locationIndex = tocRowSection.indexOf("Icons.Filled.MyLocation")
        if (locationIndex > 0) {
            assertTrue("Current chapter icon must be after chapter title",
                locationIndex > titleIndex)
        }
    }

    // ── No spacer placeholders for missing icons ──

    @Test
    fun `toc row does not use spacer placeholders for missing bookmark icon`() {
        // Must NOT have else { Spacer(...) } pattern after hasBookmark check
        val bookmarkSection = tocRowSection.substringBefore("Icons.Filled.MyLocation")
            .take(400)
        // Must not have unconditional spacer that creates blank space
        val hasSpacerElse = bookmarkSection.contains("} else {") &&
            bookmarkSection.substringAfter("} else {").take(50).contains("Spacer")
        assertFalse("TOC row must not use spacer placeholder when hasBookmark is false",
            hasSpacerElse)
    }

    @Test
    fun `toc row does not use spacer placeholders for missing current icon`() {
        val locationSection = tocRowSection.substringAfter("Icons.Filled.MyLocation").take(300)
        val hasSpacerElse = locationSection.contains("} else {") &&
            locationSection.substringAfter("} else {").take(50).contains("Spacer")
        assertFalse("TOC row must not use spacer placeholder when isCurrent is false",
            hasSpacerElse)
    }

    // ── Indent is lightweight ──

    @Test
    fun `toc row uses lightweight level based indent`() {
        assertTrue("Must use entry.level for indent", "entry.level" in tocRowSection)
        assertTrue("Indent must be (entry.level - 1) * 10", "(entry.level - 1)" in tocRowSection)
    }

    // ── Right-side progress bar still present ──

    @Test
    fun `toc row retains right side progress bar`() {
        assertTrue("Must have progress bar", "LinearProgressIndicator" in overlaySource)
        assertTrue("Must use entry.progress", "entry.progress" in overlaySource)
    }

    // ── TOC structure elements ──

    @Test
    fun `directory overlay retains toc and bookmark tabs`() {
        assertTrue("Must have 目录 tab", "目录" in overlaySource)
        assertTrue("Must have 书签 tab", "书签" in overlaySource)
    }

    @Test
    fun `directory overlay retains volume breadcrumb text`() {
        assertTrue("Must show volumeInfo", "volumeInfo" in overlaySource)
    }

    @Test
    fun `directory overlay retains bottom current chapter footer`() {
        assertTrue("Must show 当前阅读章节",
            "当前阅读章节" in overlaySource)
    }

    // ── No full-screen overlay ──

    @Test
    fun `directory overlay is not full screen`() {
        // The overlay uses ReaderBottomPanel which constrains to zone
        assertTrue("Must use ReaderBottomPanel",
            "ReaderBottomPanel" in overlaySource)
    }

    // ── Bookmark view: chapter name and snippet left aligned ──

    @Test
    fun `toc entry data class retains all fields`() {
        assertTrue("TocEntry must have title", "title" in overlaySource)
        assertTrue("TocEntry must have level", "level: Int" in overlaySource)
        assertTrue("TocEntry must have isCurrent", "isCurrent" in overlaySource)
        assertTrue("TocEntry must have hasBookmark", "hasBookmark" in overlaySource)
        assertTrue("TocEntry must have progress", "progress" in overlaySource)
    }
}
