package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class MainTabStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/MainTabStateMatrixPreviews.kt")))
    }

    @Test
    fun `main tab compose previews expose default and state matrix entries`() {
        listOf(
            "@Preview",
            "BookshelfMainTabDefaultPreview",
            "BookshelfMainTabEmptyPreview",
            "RssMainTabDefaultPreview",
            "RssMainTabLoadingPreview",
            "RssMainTabEmptyPreview",
            "RssMainTabUnreadEmptyPreview",
            "SettingsMainTabDefaultPreview",
            "SettingsMainTabLoadingOverviewPreview",
            "SettingsMainTabNoBackupPreview",
            "SettingsMainTabPermissionNeededPreview"
        ).forEach { token ->
            assertTrue("Main tab preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `main tab compose previews use frontend input states`() {
        listOf(
            "BookshelfMapper.fakeFallback",
            "BookshelfMapper.empty",
            "DiscoverRssWebDavMapper.rssList",
            "DiscoverRssWebDavMapper.rssLoading",
            "DiscoverRssWebDavMapper.rssEmpty",
            "SettingsHomeDisplayState.LoadingOverview",
            "SettingsHomeDisplayState.NoBackup",
            "SettingsHomeDisplayState.PermissionNeeded"
        ).forEach { token ->
            assertTrue("Main tab preview source must use $token", token in previewSource)
        }
    }
}
