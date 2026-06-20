package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderShellStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/ReaderShellStateMatrixPreviews.kt")))
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
            "ImmersiveReadingOfflinePreview"
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
            "ImmersiveReadingMapper.offline"
        ).forEach { token ->
            assertTrue("Reader shell preview source must use $token", token in previewSource)
        }
    }
}
