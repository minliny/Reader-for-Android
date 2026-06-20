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

    private val sortFilterStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfSortFilterUiState.kt")))
    }

    private val groupManagementScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfGroupManagementScreen.kt")))
    }

    private val groupManagementStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfGroupManagementUiState.kt")))
    }

    private val localImportScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfLocalImportScreen.kt")))
    }

    private val localImportStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfLocalImportUiState.kt")))
    }

    @Test
    fun `library flow compose previews expose search detail directory and sort filter state matrices`() {
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
            "LibrarySortFilterErrorPreview",
            "LibraryGroupManagementDefaultPreview",
            "LibraryGroupManagementNewPreview",
            "LibraryGroupManagementRenamePreview",
            "LibraryGroupManagementDeletePreview",
            "LibraryGroupManagementEmptyPreview",
            "LibraryGroupManagementLoadingPreview",
            "LibraryGroupManagementErrorPreview",
            "LibraryLocalImportDefaultPreview",
            "LibraryLocalImportImportingPreview",
            "LibraryLocalImportSuccessPreview",
            "LibraryLocalImportPartialFailedPreview",
            "LibraryLocalImportFailedPreview",
            "LibraryLocalImportPickerCancelledPreview"
        ).forEach { token ->
            assertTrue("Library flow preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `library flow previews use adapter shell and mapper fixture states`() {
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
            "BookshelfGroupManagementMapper.fromFixture",
            "BookshelfGroupManagementMapper.newGroup",
            "BookshelfGroupManagementMapper.rename",
            "BookshelfGroupManagementMapper.delete",
            "BookshelfGroupManagementMapper.empty",
            "BookshelfGroupManagementMapper.loading",
            "BookshelfGroupManagementMapper.error",
            "BookshelfLocalImportMapper.fromFixture",
            "BookshelfLocalImportMapper.importing",
            "BookshelfLocalImportMapper.success",
            "BookshelfLocalImportMapper.partialFailed",
            "BookshelfLocalImportMapper.failed",
            "BookshelfLocalImportMapper.pickerCancelled",
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

        listOf(
            "BookshelfSortFilterFeedbackUiModel",
            "val message: String",
            "BookshelfSortFilterDisplayState.Selected",
            "BookshelfSortFilterDisplayState.Empty",
            "BookshelfSortFilterMapper",
            "fun selected()",
            "fun empty()",
            "fun error(",
            "fun fromBookshelfState",
            "BookshelfGroupFilterSort.apply"
        ).forEach { token ->
            assertTrue("BookshelfSortFilterUiState must expose frontend input state token $token", token in sortFilterStateSource)
        }

        listOf(
            "BookshelfGroupManagementUiState",
            "GroupManagementContent",
            "GroupRow",
            "GroupEmptyBlock",
            "GroupNameDialog",
            "GroupDeleteConfirmDialog",
            "BookshelfGroupManagementDisplayState.Delete",
            "ReaderIconToken.Delete",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton"
        ).forEach { token ->
            assertTrue("BookshelfGroupManagementScreen must expose frontend input state token $token", token in groupManagementScreenSource)
        }

        listOf(
            "BookshelfGroupRowUiModel",
            "BookshelfGroupDialogUiModel",
            "BookshelfGroupDeleteConfirmUiModel",
            "BookshelfGroupManagementMapper",
            "fun newGroup()",
            "fun rename()",
            "fun delete()",
            "fun empty()",
            "fun loading()",
            "fun error(",
            "不会删除书籍",
            "canDelete = false"
        ).forEach { token ->
            assertTrue("BookshelfGroupManagementUiState must expose frontend input state token $token", token in groupManagementStateSource)
        }

        listOf(
            "BookshelfLocalImportUiState",
            "ImportIntroCard",
            "ImportPermissionCard",
            "SupportedFormatsCard",
            "ImportFlowCard",
            "ImportingCard",
            "ImportSummaryCard",
            "ImportResultRow",
            "PickerCancelledCard",
            "ImportBottomActions",
            "BookshelfLocalImportDisplayState.Failed",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton"
        ).forEach { token ->
            assertTrue("BookshelfLocalImportScreen must expose frontend input state token $token", token in localImportScreenSource)
        }

        listOf(
            "BookshelfLocalImportIntroUiModel",
            "BookshelfLocalImportPermissionUiModel",
            "BookshelfLocalImportProgressUiModel",
            "BookshelfLocalImportResultRowUiModel",
            "BookshelfLocalImportMapper",
            "fun importing()",
            "fun success()",
            "fun partialFailed()",
            "fun failed()",
            "fun pickerCancelled()",
            "系统文件选择器",
            "不会扫描全盘",
            "不需要“管理所有文件”权限",
            "不展示错误页"
        ).forEach { token ->
            assertTrue("BookshelfLocalImportUiState must expose frontend input state token $token", token in localImportStateSource)
        }
    }
}
