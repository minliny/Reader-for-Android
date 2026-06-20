package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderShellStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt")))
    }

    private val tocBookmarkScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignScreen.kt")))
    }

    private val tocBookmarkStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/ReadingTocBookmarkDesignUiState.kt")))
    }

    @Test
    fun `reader shell compose previews expose reading entry and immersive state matrices`() {
        listOf(
            "ReadingEntryDefaultPreview",
            "ReadingEntryLoadingPreview",
            "ReadingEntryErrorPreview",
            "ReadingEntryOfflinePreview",
            "ImmersiveReadingDefaultPreview",
            "ImmersiveReadingLoadingPreview",
            "ImmersiveReadingErrorPreview",
            "ImmersiveReadingOfflinePreview",
            "ReadingTocBookmarkDefaultPreview",
            "ReadingTocBookmarkBookmarkPreview",
            "ReadingTocBookmarkSearchPreview",
            "ReadingTocBookmarkEmptyPreview",
            "ReadingTocBookmarkLoadingPreview",
            "ReadingTocBookmarkErrorPreview",
            "ReadingTocBookmarkMoreMenuPreview"
        ).forEach { token ->
            assertTrue("Reader shell preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `reader shell previews use design state mappers`() {
        listOf(
            "ReadingEntryMapper.fromFixture",
            "ReadingEntryMapper.loading",
            "ReadingEntryMapper.error",
            "ReadingEntryMapper.offline",
            "ImmersiveReadingMapper.fromFixture",
            "ImmersiveReadingMapper.loading",
            "ImmersiveReadingMapper.error",
            "ImmersiveReadingMapper.offline",
            "ReadingTocBookmarkMapper.fromFixture",
            "ReadingTocBookmarkMapper.bookmark",
            "ReadingTocBookmarkMapper.search",
            "ReadingTocBookmarkMapper.empty",
            "ReadingTocBookmarkMapper.loading",
            "ReadingTocBookmarkMapper.error",
            "ReadingTocBookmarkMapper.moreMenu"
        ).forEach { token ->
            assertTrue("Reader shell preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `toc bookmark screen keeps readershell slots separate`() {
        listOf(
            "ReadingTocBookmarkScreen",
            "TocBookmarkReadingSurface",
            "TocBookmarkOverlay",
            "TocBookmarkBottomSheet",
            "TocBookmarkModuleNav",
            "TocBookmarkBrightnessPanel",
            "contentDescription = \"readingSurface\"",
            "contentDescription = \"readerOverlayHost\"",
            "contentDescription = \"bottomSheetHost\"",
            "contentDescription = \"readerModuleNav\"",
            "contentDescription = \"readerStateHost\""
        ).forEach { token ->
            assertTrue("Toc bookmark screen source must include $token", token in tocBookmarkScreenSource)
        }
    }

    @Test
    fun `toc bookmark state model keeps frontend input contract text`() {
        listOf(
            "ReadingTocBookmarkDisplayState",
            "ReadingTocBookmarkMapper",
            "fun bookmark()",
            "fun search()",
            "fun empty()",
            "fun loading()",
            "fun error()",
            "fun moreMenu()",
            "当前 · 38%",
            "暂无书签",
            "加载失败，请重试",
            "已保留当前阅读位置",
            "缓存当前卷",
            "只看未读"
        ).forEach { token ->
            assertTrue("Toc bookmark state source must include $token", token in tocBookmarkStateSource)
        }
    }
}
