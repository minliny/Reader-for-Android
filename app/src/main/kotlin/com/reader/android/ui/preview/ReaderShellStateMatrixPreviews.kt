package com.reader.android.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reader.android.ui.reader.ImmersiveReadingMapper
import com.reader.android.ui.reader.ImmersiveReadingScreen
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
