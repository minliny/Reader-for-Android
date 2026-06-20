package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.detail.BookDetailAdapterShell
import com.reader.android.ui.detail.BookDetailScreen
import com.reader.android.ui.search.SearchAdapterShell
import com.reader.android.ui.search.SearchScreen
import com.reader.android.ui.state.ReaderUiState

private const val LibraryPreviewWidth = 390
private const val LibraryPreviewHeight = 844

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
