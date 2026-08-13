package com.reader.android.ui.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ReaderControlStateMatrixPreviewTest {

    private val previewSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/preview/ReaderControlStateMatrixPreviews.kt")))
    }

    private val controlBaseSource: String by lazy {
        String(Files.readAllBytes(Paths.get("src/main/kotlin/com/reader/android/ui/reader/components/ReaderControlBase.kt")))
    }

    @Test
    fun `reader control compose previews expose base quick module and night states`() {
        listOf(
            "ReaderControlDefaultPreview",
            "ReaderControlSearchQuickActionPreview",
            "ReaderControlAutoScrollQuickActionPreview",
            "ReaderControlReplaceQuickActionPreview",
            "ReaderControlDirectoryModulePreview",
            "ReaderControlTtsModulePreview",
            "ReaderControlAppearanceModulePreview",
            "ReaderControlSettingsModulePreview",
            "ReaderControlNightPreview",
            "ReaderControlBrightnessRightDockPreview"
        ).forEach { token ->
            assertTrue("Reader control preview source must contain $token", token in previewSource)
        }
    }

    @Test
    fun `reader control previews use runtime fixtures as compose input states`() {
        listOf(
            "ReaderScreen(runtimeState = ReaderRuntimeFixture.createBaseControlVisible())",
            "ReaderRuntimeFixture.createSearchOverlay",
            "ReaderRuntimeFixture.createAutoScrollOverlay",
            "ReaderRuntimeFixture.createReplaceOverlay",
            "ReaderRuntimeFixture.createDirectoryOverlay",
            "ReaderRuntimeFixture.createTtsOverlay",
            "ReaderRuntimeFixture.createAppearanceOverlay",
            "ReaderRuntimeFixture.createSettingsOverlay",
            "ReaderRuntimeFixture.createNightState",
            "ReaderRuntimeFixture.createBrightnessRightDock"
        ).forEach { token ->
            assertTrue("Reader control preview source must use $token", token in previewSource)
        }
    }

    @Test
    fun `reader control bottom module active state keeps fixed layout and changes colors only`() {
        listOf(
            "activeBottomModule",
            "activeModule = activeBottomModule",
            "activeModule == ReaderOverlayType.DIRECTORY",
            "activeModule == ReaderOverlayType.TTS",
            "activeModule == ReaderOverlayType.APPEARANCE",
            "activeModule == ReaderOverlayType.SETTINGS",
            "active: Boolean",
            ".width(72.dp)",
            ".height(56.dp)",
            "ReaderTheme.colors.primary.copy(alpha = 0.18f)",
            "iconTint = if (active) ReaderTheme.colors.paperBg else ReaderTheme.colors.controlInk",
            "labelColor = if (active) ReaderTheme.colors.primary else ReaderTheme.colors.controlInk"
        ).forEach { token ->
            assertTrue("Reader control bottom module active rule must include $token", token in controlBaseSource)
        }
    }
}
