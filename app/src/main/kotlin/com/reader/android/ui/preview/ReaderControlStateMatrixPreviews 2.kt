package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.reader.ReaderRuntimeFixture
import com.reader.android.ui.reader.ReaderScreen

private const val ReaderControlPreviewWidth = 390
private const val ReaderControlPreviewHeight = 844

@Preview(name = "Reader Control / Default", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlDefaultPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createBaseControlVisible())
}

@Preview(name = "Reader Control / Quick / Search", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlSearchQuickActionPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createSearchOverlay())
}

@Preview(name = "Reader Control / Quick / Auto Scroll", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlAutoScrollQuickActionPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createAutoScrollOverlay())
}

@Preview(name = "Reader Control / Quick / Replace", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlReplaceQuickActionPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createReplaceOverlay())
}

@Preview(name = "Reader Control / Module / Directory", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlDirectoryModulePreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createDirectoryOverlay())
}

@Preview(name = "Reader Control / Module / TTS", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlTtsModulePreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createTtsOverlay())
}

@Preview(name = "Reader Control / Module / Appearance", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlAppearanceModulePreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createAppearanceOverlay())
}

@Preview(name = "Reader Control / Module / Settings", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlSettingsModulePreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createSettingsOverlay())
}

@Preview(name = "Reader Control / Night", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlNightPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createNightState())
}

@Preview(name = "Reader Control / Brightness Right Dock", widthDp = ReaderControlPreviewWidth, heightDp = ReaderControlPreviewHeight, showBackground = true)
@Composable
fun ReaderControlBrightnessRightDockPreview() {
    ReaderScreen(runtimeState = ReaderRuntimeFixture.createBrightnessRightDock())
}
