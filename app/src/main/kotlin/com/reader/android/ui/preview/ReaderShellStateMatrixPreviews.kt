package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.reader.ImmersiveReadingMapper
import com.reader.android.ui.reader.ImmersiveReadingScreen
import com.reader.android.ui.reader.ReadingAppearanceMapper
import com.reader.android.ui.reader.ReadingAppearanceScreen
import com.reader.android.ui.reader.ReadingAloudMapper
import com.reader.android.ui.reader.ReadingAloudScreen
import com.reader.android.ui.reader.ReadingSettingsMapper
import com.reader.android.ui.reader.ReadingSettingsScreen
import com.reader.android.ui.reader.ReadingTocBookmarkMapper
import com.reader.android.ui.reader.ReadingTocBookmarkScreen
import com.reader.android.ui.reader.ReadingEntryMapper
import com.reader.android.ui.reader.ReadingEntryScreen

private const val ReaderShellPreviewWidth = 390
private const val ReaderShellPreviewHeight = 844

@Preview(name = "Reader Shell / Reading Entry / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingEntryDefaultPreview() {
    ReadingEntryScreen(state = ReadingEntryMapper.fromFixture())
}

@Preview(name = "Reader Shell / Reading Entry / Loading", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingEntryLoadingPreview() {
    ReadingEntryScreen(state = ReadingEntryMapper.loading())
}

@Preview(name = "Reader Shell / Reading Entry / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingEntryErrorPreview() {
    ReadingEntryScreen(state = ReadingEntryMapper.error())
}

@Preview(name = "Reader Shell / Reading Entry / Offline", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingEntryOfflinePreview() {
    ReadingEntryScreen(state = ReadingEntryMapper.offline())
}

@Preview(name = "Reader Shell / Immersive / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ImmersiveReadingDefaultPreview() {
    ImmersiveReadingScreen(state = ImmersiveReadingMapper.fromFixture())
}

@Preview(name = "Reader Shell / Immersive / Loading", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ImmersiveReadingLoadingPreview() {
    ImmersiveReadingScreen(state = ImmersiveReadingMapper.loading())
}

@Preview(name = "Reader Shell / Immersive / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ImmersiveReadingErrorPreview() {
    ImmersiveReadingScreen(state = ImmersiveReadingMapper.error())
}

@Preview(name = "Reader Shell / Immersive / Offline", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ImmersiveReadingOfflinePreview() {
    ImmersiveReadingScreen(state = ImmersiveReadingMapper.offline())
}

@Preview(name = "Reader Shell / TOC Bookmark / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkDefaultPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.fromFixture())
}

@Preview(name = "Reader Shell / TOC Bookmark / Bookmark", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkBookmarkPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.bookmark())
}

@Preview(name = "Reader Shell / TOC Bookmark / Search", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkSearchPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.search())
}

@Preview(name = "Reader Shell / TOC Bookmark / Empty", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkEmptyPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.empty())
}

@Preview(name = "Reader Shell / TOC Bookmark / Loading", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkLoadingPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.loading())
}

@Preview(name = "Reader Shell / TOC Bookmark / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkErrorPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.error())
}

@Preview(name = "Reader Shell / TOC Bookmark / More Menu", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingTocBookmarkMoreMenuPreview() {
    ReadingTocBookmarkScreen(state = ReadingTocBookmarkMapper.moreMenu())
}

@Preview(name = "Reader Shell / Appearance / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceDefaultPreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.fromFixture())
}

@Preview(name = "Reader Shell / Appearance / Font", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceFontPreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.font())
}

@Preview(name = "Reader Shell / Appearance / Theme", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceThemePreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.theme())
}

@Preview(name = "Reader Shell / Appearance / Edit", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceEditPreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.edit())
}

@Preview(name = "Reader Shell / Appearance / Loading", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceLoadingPreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.loading())
}

@Preview(name = "Reader Shell / Appearance / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAppearanceErrorPreview() {
    ReadingAppearanceScreen(state = ReadingAppearanceMapper.error())
}

@Preview(name = "Reader Shell / Aloud / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAloudDefaultPreview() {
    ReadingAloudScreen(state = ReadingAloudMapper.fromFixture())
}

@Preview(name = "Reader Shell / Aloud / Running", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAloudRunningPreview() {
    ReadingAloudScreen(state = ReadingAloudMapper.running())
}

@Preview(name = "Reader Shell / Aloud / Paused", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAloudPausedPreview() {
    ReadingAloudScreen(state = ReadingAloudMapper.paused())
}

@Preview(name = "Reader Shell / Aloud / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingAloudErrorPreview() {
    ReadingAloudScreen(state = ReadingAloudMapper.error())
}

@Preview(name = "Reader Shell / Settings / Default", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingSettingsDefaultPreview() {
    ReadingSettingsScreen(state = ReadingSettingsMapper.fromFixture())
}

@Preview(name = "Reader Shell / Settings / Subpage", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingSettingsSubpagePreview() {
    ReadingSettingsScreen(state = ReadingSettingsMapper.subpage())
}

@Preview(name = "Reader Shell / Settings / Loading", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingSettingsLoadingPreview() {
    ReadingSettingsScreen(state = ReadingSettingsMapper.loading())
}

@Preview(name = "Reader Shell / Settings / Error", widthDp = ReaderShellPreviewWidth, heightDp = ReaderShellPreviewHeight, showBackground = true)
@Composable
fun ReadingSettingsErrorPreview() {
    ReadingSettingsScreen(state = ReadingSettingsMapper.error())
}
