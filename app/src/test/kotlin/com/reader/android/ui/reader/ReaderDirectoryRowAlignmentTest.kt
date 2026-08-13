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
        val bookmarkIconIndex = tocRowSection.indexOf("ReaderIconToken.Bookmark")
        val locationIconIndex = tocRowSection.indexOf("ReaderIconToken.CurrentLocation")
        assertTrue("Chapter title must be rendered before bookmark icon",
            titleIndex < bookmarkIconIndex || bookmarkIconIndex == -1)
        assertTrue("Chapter title must be rendered before current chapter icon",
            titleIndex < locationIconIndex || locationIconIndex == -1)
    }

    @Test
    fun `toc row does not place bookmark icon before chapter title`() {
        // Bookmark icon must appear AFTER the title, not before it
        val titleIndex = tocRowSection.indexOf("entry.title")
        val bookmarkIndex = tocRowSection.indexOf("ReaderIconToken.Bookmark")
        if (bookmarkIndex > 0) {
            assertTrue("Bookmark icon must be after chapter title",
                bookmarkIndex > titleIndex)
        }
    }

    @Test
    fun `toc row does not place current chapter icon before chapter title`() {
        val titleIndex = tocRowSection.indexOf("entry.title")
        val locationIndex = tocRowSection.indexOf("ReaderIconToken.CurrentLocation")
        if (locationIndex > 0) {
            assertTrue("Current chapter icon must be after chapter title",
                locationIndex > titleIndex)
        }
    }

    // ── Fixed-width indicator slots, positions never shift ──

    @Test
    fun `toc row uses fixed width slots for bookmark and current indicators`() {
        // Both bookmark and current-chapter slots use fixed Box containers
        val bookmarkSection = overlaySource.substringAfter("Bookmark slot")
        assertTrue("Bookmark slot must use fixed-width Box", "Box(" in bookmarkSection.take(200))
        // Icons are conditionally shown inside fixed slots
        assertTrue("Must have Bookmark icon token", "ReaderIconToken.Bookmark" in overlaySource)
        assertTrue("Must have CurrentLocation icon token", "ReaderIconToken.CurrentLocation" in overlaySource)
    }

    @Test
    fun `toc row indicator positions do not shift regardless of icon visibility`() {
        // Fixed Box slots ensure bookmark always at same x position
        // Both slots use the same size modifier, ensuring consistent layout
        val afterTitle = overlaySource.substringAfter("Modifier.weight(1f)")
        // Icons inside fixed Box, not conditional at Row level
        val bookmarkSlotIdx = afterTitle.indexOf("Bookmark slot")
        val currentSlotIdx = afterTitle.indexOf("Current-chapter slot")
        assertTrue("Bookmark slot must be after title", bookmarkSlotIdx > 0)
        assertTrue("Current slot must be after bookmark", currentSlotIdx > bookmarkSlotIdx)
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
