package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.booksource.BookSourceScreen
import com.reader.android.ui.booksource.SourceDetailScreen
import com.reader.android.ui.booksource.SourceEditScreen
import com.reader.android.ui.booksource.SourceImportScreen
import com.reader.android.ui.booksource.SourceImportStatus
import com.reader.android.ui.booksource.SourceImportUiState
import com.reader.android.ui.booksource.SourceManagementMapper
import com.reader.android.ui.booksource.SourceManagementUiState

private const val SourcePreviewWidth = 390
private const val SourcePreviewHeight = 844

@Preview(name = "Source Management / List / Default", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementListDefaultPreview() {
    BookSourceScreen(sourceStateOverride = SourceManagementMapper.fakeFallback())
}

@Preview(name = "Source Management / List / Loading", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementListLoadingPreview() {
    BookSourceScreen(sourceStateOverride = SourceManagementUiState(sources = emptyList(), isLoading = true))
}

@Preview(name = "Source Management / List / Empty", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementListEmptyPreview() {
    BookSourceScreen(sourceStateOverride = SourceManagementUiState(sources = emptyList()))
}

@Preview(name = "Source Management / List / Error", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementListErrorPreview() {
    BookSourceScreen(
        sourceStateOverride = SourceManagementUiState(
            sources = emptyList(),
            errorMessage = "书源列表解析失败，请检查导入内容"
        )
    )
}

@Preview(name = "Source Management / Detail / Enabled", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementDetailEnabledPreview() {
    val source = SourceManagementMapper.fakeFallback().sources.first()
    SourceDetailScreen(source = SourceManagementMapper.detailDataOf(source))
}

@Preview(name = "Source Management / Detail / Disabled", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementDetailDisabledPreview() {
    val source = SourceManagementMapper.fakeFallback().sources.last()
    SourceDetailScreen(source = SourceManagementMapper.detailDataOf(source))
}

@Preview(name = "Source Management / Edit / Existing", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementEditExistingPreview() {
    SourceEditScreen(
        initialName = "Fixture 书源 A",
        initialUrl = "https://fixture.local/source-a"
    )
}

@Preview(name = "Source Management / Edit / Blank", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementEditBlankPreview() {
    SourceEditScreen()
}

@Preview(name = "Source Management / Import / Idle", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementImportIdlePreview() {
    SourceImportScreen()
}

@Preview(name = "Source Management / Import / Blank Json", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementImportBlankJsonPreview() {
    SourceImportScreen(importState = SourceManagementMapper.importPreview("   "))
}

@Preview(name = "Source Management / Import / Ready Json", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementImportReadyJsonPreview() {
    SourceImportScreen(importState = SourceManagementMapper.importPreview("""{"sourceName":"Fixture","sourceUrl":"https://fixture.local"}"""))
}

@Preview(name = "Source Management / Import / Imported", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementImportImportedPreview() {
    SourceImportScreen(importState = SourceManagementMapper.importSuccess(3))
}

@Preview(name = "Source Management / Import / Error", widthDp = SourcePreviewWidth, heightDp = SourcePreviewHeight, showBackground = true)
@Composable
fun SourceManagementImportErrorPreview() {
    SourceImportScreen(
        importState = SourceImportUiState(
            status = SourceImportStatus.Error,
            message = "导入内容不是 JSON",
            input = "fixture.local/source.json"
        )
    )
}
