package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class SourceManagementStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/SourceManagementStateMatrixPreviews.kt")))
    }

    private val bookSourceScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/booksource/BookSourceScreen.kt")))
    }

    private val sourceImportScreenSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/booksource/SourceImportScreen.kt")))
    }

    @Test
    fun `source management compose previews expose list detail edit and import states`() {
        listOf(
            "SourceManagementListDefaultPreview",
            "SourceManagementListLoadingPreview",
            "SourceManagementListEmptyPreview",
            "SourceManagementListErrorPreview",
            "SourceManagementDetailEnabledPreview",
            "SourceManagementDetailDisabledPreview",
            "SourceManagementEditExistingPreview",
            "SourceManagementEditBlankPreview",
            "SourceManagementImportIdlePreview",
            "SourceManagementImportBlankJsonPreview",
            "SourceManagementImportReadyJsonPreview",
            "SourceManagementImportImportedPreview",
            "SourceManagementImportErrorPreview"
        ).forEach { token ->
            assertTrue("Source management preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `source management previews use real source state mappers`() {
        listOf(
            "SourceManagementMapper.fakeFallback",
            "SourceManagementUiState(sources = emptyList(), isLoading = true)",
            "SourceManagementUiState(sources = emptyList())",
            "SourceManagementMapper.detailDataOf",
            "SourceManagementMapper.importPreview",
            "SourceManagementMapper.importSuccess",
            "SourceImportStatus.Error"
        ).forEach { token ->
            assertTrue("Source management preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `source management screens accept preview injected state`() {
        assertTrue(
            "BookSourceScreen must expose sourceStateOverride for state matrix previews",
            "sourceStateOverride: SourceManagementUiState? = null" in bookSourceScreenSource
        )
        assertTrue(
            "SourceImportScreen must expose importState for state matrix previews",
            "importState: SourceImportUiState = SourceImportUiState()" in sourceImportScreenSource
        )
        assertTrue("BookSourceScreen must render loading from source state", "sourceState.isLoading" in bookSourceScreenSource)
        assertTrue("BookSourceScreen must render error from source state", "sourceState.errorMessage" in bookSourceScreenSource)
        assertTrue("SourceImportScreen must render import message", "importState.message" in sourceImportScreenSource)
        assertTrue("SourceImportScreen must render imported count", "importState.importedCount" in sourceImportScreenSource)
    }
}
