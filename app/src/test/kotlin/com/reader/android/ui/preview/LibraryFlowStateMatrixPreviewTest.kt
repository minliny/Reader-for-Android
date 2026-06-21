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

    private val bookshelfEmptyScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfEmptyDesignScreen.kt")))
    }

    private val bookshelfEmptyStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfEmptyDesignUiState.kt")))
    }

    private val bookActionSheetScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfActionSheetDesignScreen.kt")))
    }

    private val bookActionSheetStateSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/bookshelf/BookshelfActionSheetDesignUiState.kt")))
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
            "LibraryBookshelfEmptyDefaultPreview",
            "LibraryBookshelfEmptyAllEmptyPreview",
            "LibraryBookshelfEmptyLoadingPreview",
            "LibraryBookshelfEmptyErrorPreview",
            "LibraryBookshelfEmptyOfflinePreview",
            "LibraryBookshelfEmptyPermissionPreview",
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
            "LibraryBookActionSheetDefaultPreview",
            "LibraryBookActionSheetDangerPreview",
            "LibraryBookActionSheetLoadingPreview",
            "LibraryBookActionSheetErrorPreview",
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
            "BookshelfEmptyDesignMapper.currentGroupEmpty",
            "BookshelfEmptyDesignMapper.allEmpty",
            "BookshelfEmptyDesignMapper.loading",
            "BookshelfEmptyDesignMapper.error",
            "BookshelfEmptyDesignMapper.offline",
            "BookshelfEmptyDesignMapper.permission",
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
            "BookshelfActionSheetMapper.fromFixture",
            "BookshelfActionSheetMapper.danger",
            "BookshelfActionSheetMapper.loading",
            "BookshelfActionSheetMapper.error",
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
            "BookshelfEmptyDesignUiState",
            "BookshelfEmptyGroupRow",
            "BookshelfEmptyStateHost",
            "BookshelfEmptyStateCard",
            "ReaderMainTabBar",
            "ReaderIconToken.Bookshelf",
            "ReaderIconToken.Discover",
            "ReaderIconToken.Rss",
            "ReaderIconToken.Settings",
            "ReaderPrimaryButton",
            "ReaderSecondaryButton",
            "LibraryShell，书架空状态"
        ).forEach { token ->
            assertTrue("BookshelfEmptyDesignScreen must expose frontend input state token $token", token in bookshelfEmptyScreenSource)
        }

        listOf(
            "BookshelfEmptyDisplayState.Default",
            "BookshelfEmptyDisplayState.AllEmpty",
            "BookshelfEmptyDisplayState.Loading",
            "BookshelfEmptyDisplayState.Error",
            "BookshelfEmptyDisplayState.Offline",
            "BookshelfEmptyDisplayState.Permission",
            "BookshelfEmptyDesignMapper",
            "fun allEmpty()",
            "fun loading()",
            "fun error()",
            "fun offline()",
            "fun permission()",
            "当前分组没有书籍",
            "本地导入不是 P0 强制流程",
            "不提前请求全盘权限"
        ).forEach { token ->
            assertTrue("BookshelfEmptyDesignUiState must expose frontend input state token $token", token in bookshelfEmptyStateSource)
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
            "BookshelfActionSheetDesignUiState",
            "BookshelfActionBackdrop",
            "BookshelfActionBottomSheet",
            "BookshelfActionConfirmDialog",
            "BookActionSummary",
            "BookActionSheetItem",
            "DialogHost，删除书架记录确认",
            "BottomSheet，书籍操作底表",
            "ReaderIconToken.Edit",
            "ReaderIconToken.Delete"
        ).forEach { token ->
            assertTrue("BookshelfActionSheetDesignScreen must expose frontend input state token $token", token in bookActionSheetScreenSource)
        }

        listOf(
            "BookshelfActionSheetDisplayState.Default",
            "BookshelfActionSheetDisplayState.Danger",
            "BookshelfActionSheetDisplayState.Loading",
            "BookshelfActionSheetDisplayState.Error",
            "BookshelfActionSheetMapper",
            "fun danger()",
            "fun loading()",
            "fun error()",
            "确认移除",
            "不会删除本地文件或网络来源",
            "请勿重复点击确认移除",
            "保留当前书架"
        ).forEach { token ->
            assertTrue("BookshelfActionSheetDesignUiState must expose frontend input state token $token", token in bookActionSheetStateSource)
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
