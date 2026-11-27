package io.reader.rebuild.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.reader.core.theme.ReaderIconMapping

@Composable
internal fun ReaderIcon(
    mapping: ReaderIconMapping,
    description: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color? = null,
) {
    val colors = LocalReaderColors.current
    val foreground = tint ?: mapping.tintRole?.let(colors::iconTint) ?: colors.accent
    val sized = modifier.size(size)
    val asset = mapping.assetName
    if (asset != null) {
        val assets = LocalContext.current.assets
        val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
        val bitmap = remember(assets, asset, pixels) {
            val svg = SVG.getFromAsset(assets, "reader_design/icons/$asset")
            val image = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            val edge = pixels.toFloat()
            svg.renderToCanvas(Canvas(image), RectF(0f, 0f, edge, edge))
            image.asImageBitmap()
        }
        Icon(bitmap, description, sized, foreground)
    } else {
        Icon(systemIcon(requireNotNull(mapping.systemName)), description, sized, foreground)
    }
}

private fun systemIcon(name: String): ImageVector = when (name) {
    "AutoMirrored.Outlined.ArrowBack" -> Icons.AutoMirrored.Outlined.ArrowBack
    "AutoMirrored.Outlined.KeyboardArrowLeft" -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    "AutoMirrored.Outlined.KeyboardArrowRight" -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
    "Filled.Bookmark" -> Icons.Filled.Bookmark
    "Filled.Pause" -> Icons.Filled.Pause
    "Filled.PlayArrow" -> Icons.Filled.PlayArrow
    "Filled.Stop" -> Icons.Filled.Stop
    "Outlined.Add" -> Icons.Outlined.Add
    "Outlined.ArrowDownward" -> Icons.Outlined.ArrowDownward
    "Outlined.ArrowUpward" -> Icons.Outlined.ArrowUpward
    "Outlined.Book" -> Icons.Outlined.Book
    "Outlined.BookmarkBorder" -> Icons.Outlined.BookmarkBorder
    "Outlined.Check" -> Icons.Outlined.Check
    "Outlined.CheckCircleOutline" -> Icons.Outlined.CheckCircleOutline
    "Outlined.Close" -> Icons.Outlined.Close
    "Outlined.ContentCopy" -> Icons.Outlined.ContentCopy
    "Outlined.ContentPaste" -> Icons.Outlined.ContentPaste
    "Outlined.DeleteOutline" -> Icons.Outlined.DeleteOutline
    "Outlined.Edit" -> Icons.Outlined.Edit
    "Outlined.FileDownload" -> Icons.Outlined.FileDownload
    "Outlined.FilterList" -> Icons.Outlined.FilterList
    "Outlined.Folder" -> Icons.Outlined.Folder
    "Outlined.History" -> Icons.Outlined.History
    "Outlined.Info" -> Icons.Outlined.Info
    "Outlined.InsertDriveFile" -> Icons.AutoMirrored.Outlined.InsertDriveFile
    "Outlined.KeyboardArrowDown" -> Icons.Outlined.KeyboardArrowDown
    "Outlined.KeyboardArrowUp" -> Icons.Outlined.KeyboardArrowUp
    "Outlined.Link" -> Icons.Outlined.Link
    "Outlined.List" -> Icons.AutoMirrored.Outlined.List
    "Outlined.MoreHoriz" -> Icons.Outlined.MoreHoriz
    "Outlined.Refresh" -> Icons.Outlined.Refresh
    "Outlined.Search" -> Icons.Outlined.Search
    "Outlined.Settings" -> Icons.Outlined.Settings
    "Outlined.Storage" -> Icons.Outlined.Storage
    "Outlined.TextFields" -> Icons.Outlined.TextFields
    "Outlined.WarningAmber" -> Icons.Outlined.WarningAmber
    else -> error("Unknown pinned Reader icon $name")
}
