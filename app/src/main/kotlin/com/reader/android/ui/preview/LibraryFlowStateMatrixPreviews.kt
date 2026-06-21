package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.bookshelf.BookshelfActionSheetDesignScreen
import com.reader.android.ui.bookshelf.BookshelfActionSheetMapper
import com.reader.android.ui.bookshelf.BookshelfEmptyDesignMapper
import com.reader.android.ui.bookshelf.BookshelfEmptyDesignScreen
import com.reader.android.ui.bookshelf.BookshelfGroupManagementMapper
import com.reader.android.ui.bookshelf.BookshelfGroupManagementScreen
import com.reader.android.ui.bookshelf.BookshelfLocalImportMapper
import com.reader.android.ui.bookshelf.BookshelfLocalImportScreen
import com.reader.android.ui.bookshelf.BookshelfSortFilterMapper
import com.reader.android.ui.bookshelf.BookshelfSortFilterScreen
import com.reader.android.ui.detail.BookDetailAdapterShell
import com.reader.android.ui.detail.BookDetailScreen
import com.reader.android.ui.search.SearchAdapterShell
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.toc.BookDirectoryUiStateMapper
import com.reader.android.ui.toc.TOCScreen

private const val LibraryPreviewWidth = 390
private const val LibraryPreviewHeight = 844

@Preview(name = "Library Flow / Bookshelf Empty / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyDefaultPreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.currentGroupEmpty())
}

@Preview(name = "Library Flow / Bookshelf Empty / All Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyAllEmptyPreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.allEmpty())
}

@Preview(name = "Library Flow / Bookshelf Empty / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyLoadingPreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.loading())
}

@Preview(name = "Library Flow / Bookshelf Empty / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyErrorPreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.error())
}

@Preview(name = "Library Flow / Bookshelf Empty / Offline", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyOfflinePreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.offline())
}

@Preview(name = "Library Flow / Bookshelf Empty / Permission", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookshelfEmptyPermissionPreview() {
    BookshelfEmptyDesignScreen(state = BookshelfEmptyDesignMapper.permission())
}

@Preview(name = "Library Flow / Search / Home", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchHomePreview() {
    SearchScreen(searchState = SearchAdapterShell.searchHome())
}

@Preview(name = "Library Flow / Search / Results", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchResultsPreview() {
    SearchScreen(searchState = SearchAdapterShell.searchResults("群山"))
}

@Preview(name = "Library Flow / Search / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchLoadingPreview() {
    SearchScreen(searchState = SearchAdapterShell.searchLoading("群山"))
}

@Preview(name = "Library Flow / Search / Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchEmptyPreview() {
    SearchScreen(searchState = SearchAdapterShell.searchEmpty("未知书名"))
}

@Preview(name = "Library Flow / Search / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchErrorPreview() {
    SearchScreen(searchState = SearchAdapterShell.searchError("群山", "搜索失败，请重试"))
}

@Preview(name = "Library Flow / Search / Offline", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchOfflinePreview() {
    SearchScreen(uiState = ReaderUiState.Offline)
}

@Preview(name = "Library Flow / Search / Permission", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySearchPermissionPreview() {
    SearchScreen(uiState = ReaderUiState.PermissionRequired("网络"))
}

@Preview(name = "Library Flow / Detail / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailDefaultPreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        detailState = BookDetailAdapterShell.detailReady()
    )
}

@Preview(name = "Library Flow / Detail / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailLoadingPreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        detailState = BookDetailAdapterShell.detailLoading()
    )
}

@Preview(name = "Library Flow / Detail / Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailEmptyPreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        detailState = BookDetailAdapterShell.detailEmpty()
    )
}

@Preview(name = "Library Flow / Detail / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailErrorPreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        detailState = BookDetailAdapterShell.detailError("书籍详情加载失败，请重试")
    )
}

@Preview(name = "Library Flow / Detail / Offline", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailOfflinePreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        uiState = ReaderUiState.Offline
    )
}

@Preview(name = "Library Flow / Detail / Permission", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDetailPermissionPreview() {
    BookDetailScreen(
        detailUrl = "fixture-detail-paper-mountain",
        onBack = {},
        onTOC = {},
        uiState = ReaderUiState.PermissionRequired("网络")
    )
}

@Preview(name = "Library Flow / Directory / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDirectoryDefaultPreview() {
    TOCScreen(
        tocUrl = "fixture-toc-paper-mountain",
        onBack = {},
        onChapterClick = { _, _ -> },
        directoryState = BookDirectoryUiStateMapper.fromFixture()
    )
}

@Preview(name = "Library Flow / Directory / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDirectoryLoadingPreview() {
    TOCScreen(
        tocUrl = "fixture-toc-paper-mountain",
        onBack = {},
        onChapterClick = { _, _ -> },
        directoryState = BookDirectoryUiStateMapper.loading()
    )
}

@Preview(name = "Library Flow / Directory / Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDirectoryEmptyPreview() {
    TOCScreen(
        tocUrl = "fixture-toc-paper-mountain",
        onBack = {},
        onChapterClick = { _, _ -> },
        directoryState = BookDirectoryUiStateMapper.empty()
    )
}

@Preview(name = "Library Flow / Directory / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookDirectoryErrorPreview() {
    TOCScreen(
        tocUrl = "fixture-toc-paper-mountain",
        onBack = {},
        onChapterClick = { _, _ -> },
        directoryState = BookDirectoryUiStateMapper.error()
    )
}

@Preview(name = "Library Flow / Sort Filter / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySortFilterDefaultPreview() {
    BookshelfSortFilterScreen(state = BookshelfSortFilterMapper.fromFixture())
}

@Preview(name = "Library Flow / Sort Filter / Selected", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySortFilterSelectedPreview() {
    BookshelfSortFilterScreen(state = BookshelfSortFilterMapper.selected())
}

@Preview(name = "Library Flow / Sort Filter / Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySortFilterEmptyPreview() {
    BookshelfSortFilterScreen(state = BookshelfSortFilterMapper.empty())
}

@Preview(name = "Library Flow / Sort Filter / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibrarySortFilterErrorPreview() {
    BookshelfSortFilterScreen(state = BookshelfSortFilterMapper.error())
}

@Preview(name = "Library Flow / Book Action Sheet / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookActionSheetDefaultPreview() {
    BookshelfActionSheetDesignScreen(state = BookshelfActionSheetMapper.fromFixture())
}

@Preview(name = "Library Flow / Book Action Sheet / Danger", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookActionSheetDangerPreview() {
    BookshelfActionSheetDesignScreen(state = BookshelfActionSheetMapper.danger())
}

@Preview(name = "Library Flow / Book Action Sheet / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookActionSheetLoadingPreview() {
    BookshelfActionSheetDesignScreen(state = BookshelfActionSheetMapper.loading())
}

@Preview(name = "Library Flow / Book Action Sheet / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryBookActionSheetErrorPreview() {
    BookshelfActionSheetDesignScreen(state = BookshelfActionSheetMapper.error())
}

@Preview(name = "Library Flow / Group Management / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementDefaultPreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.fromFixture())
}

@Preview(name = "Library Flow / Group Management / New", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementNewPreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.newGroup())
}

@Preview(name = "Library Flow / Group Management / Rename", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementRenamePreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.rename())
}

@Preview(name = "Library Flow / Group Management / Delete", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementDeletePreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.delete())
}

@Preview(name = "Library Flow / Group Management / Empty", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementEmptyPreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.empty())
}

@Preview(name = "Library Flow / Group Management / Loading", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementLoadingPreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.loading())
}

@Preview(name = "Library Flow / Group Management / Error", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryGroupManagementErrorPreview() {
    BookshelfGroupManagementScreen(state = BookshelfGroupManagementMapper.error())
}

@Preview(name = "Library Flow / Local Import / Default", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportDefaultPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.fromFixture())
}

@Preview(name = "Library Flow / Local Import / Importing", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportImportingPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.importing())
}

@Preview(name = "Library Flow / Local Import / Success", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportSuccessPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.success())
}

@Preview(name = "Library Flow / Local Import / Partial Failed", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportPartialFailedPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.partialFailed())
}

@Preview(name = "Library Flow / Local Import / Failed", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportFailedPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.failed())
}

@Preview(name = "Library Flow / Local Import / Picker Cancelled", widthDp = LibraryPreviewWidth, heightDp = LibraryPreviewHeight, showBackground = true)
@Composable
fun LibraryLocalImportPickerCancelledPreview() {
    BookshelfLocalImportScreen(state = BookshelfLocalImportMapper.pickerCancelled())
}
