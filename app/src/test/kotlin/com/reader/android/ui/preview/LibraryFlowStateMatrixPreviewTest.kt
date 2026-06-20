package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class LibraryFlowStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/LibraryFlowStateMatrixPreviews.kt")))
    }

    private val searchScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/search/SearchScreen.kt")))
    }

    private val detailScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/detail/BookDetailScreen.kt")))
    }

    private val tocScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/toc/TOCScreen.kt")))
    }

    private val sortFilterScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfSortFilterScreen.kt")))
    }

    @Test
    fun `library flow compose previews expose search detail and directory state matrices`() {
        listOf(
            "LibrarySearchHomePreview",
            "LibrarySearchResultsPreview",
            "LibrarySearchLoadingPreview",
            "LibrarySearchEmptyPreview",
            "LibrarySearchErrorPreview",
            "LibrarySearchOfflinePreview",
            "LibrarySearchPermissionPreview",
            "LibraryBookDetailDefaultPreview",
            "LibraryBookDetailLoadingPreview",
            "LibraryBookDetailEmptyPreview",
            "LibraryBookDetailErrorPreview",
            "LibraryBookDetailOfflinePreview",
            "LibraryBookDetailPermissionPreview",
            "LibraryBookDirectoryDefaultPreview",
            "LibraryBookDirectoryLoadingPreview",
            "LibraryBookDirectoryEmptyPreview",
            "LibraryBookDirectoryErrorPreview",
            "LibrarySortFilterDefaultPreview",
            "LibrarySortFilterSelectedPreview",
            "LibrarySortFilterEmptyPreview",
            "LibrarySortFilterErrorPreview"
        ).forEach { token ->
            assertTrue("Library flow preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `library flow previews use adapter shell fixture states`() {
        listOf(
            "SearchAdapterShell.searchHome",
            "SearchAdapterShell.searchResults",
            "SearchAdapterShell.searchLoading",
            "SearchAdapterShell.searchEmpty",
            "SearchAdapterShell.searchError",
            "BookDetailAdapterShell.detailReady",
            "BookDetailAdapterShell.detailLoading",
            "BookDetailAdapterShell.detailEmpty",
            "BookDetailAdapterShell.detailError",
            "BookDirectoryUiStateMapper.fromFixture",
            "BookDirectoryUiStateMapper.loading",
            "BookDirectoryUiStateMapper.empty",
            "BookDirectoryUiStateMapper.error",
            "BookshelfSortFilterMapper.fromFixture",
            "BookshelfSortFilterMapper.selected",
            "BookshelfSortFilterMapper.empty",
            "BookshelfSortFilterMapper.error",
            "ReaderUiState.Offline",
            "ReaderUiState.PermissionRequired"
        ).forEach { token ->
            assertTrue("Library flow preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `search and detail screens accept frontend input state injection`() {
        listOf(
            "searchState: SearchUiState? = null",
            "SearchStateContent",
            "state.results",
            "state.errorMessage",
            "ReaderPermissionRequiredState"
        ).forEach { token ->
            assertTrue("SearchScreen must expose frontend input state token $token", token in searchScreenSource)
        }

        listOf(
            "detailState: BookDetailUiState? = null",
            "BookDetailStateContent",
            "detailState == null && uiState == null",
            "state.detail",
            "state.errorMessage",
            "ReaderPermissionRequiredState"
        ).forEach { token ->
            assertTrue("BookDetailScreen must expose frontend input state token $token", token in detailScreenSource)
        }

        listOf(
            "directoryState: BookDirectoryUiState? = null",
            "BookDirectoryStateContent",
            "BookDirectorySummaryBar",
            "BookDirectoryCurrentChapterRow",
            "BookDirectoryChapterRow",
            "BookDirectoryDisplayState.Loading",
            "BookDirectoryDisplayState.Empty",
            "BookDirectoryDisplayState.Error"
        ).forEach { token ->
            assertTrue("TOCScreen must expose frontend input state token $token", token in tocScreenSource)
        }

        listOf(
            "BookshelfSortFilterUiState",
            "SortFilterBackdrop",
            "SortFilterBottomSheet",
            "SortFilterSections",
            "SortFilterFeedbackBlock",
            "BookshelfSortFilterDisplayState.Error",
            "ReaderChip",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton"
        ).forEach { token ->
            assertTrue("BookshelfSortFilterScreen must expose frontend input state token $token", token in sortFilterScreenSource)
        }
    }
}
