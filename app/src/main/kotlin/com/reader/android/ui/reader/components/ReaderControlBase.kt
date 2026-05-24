package com.reader.android.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.ui.reader.ReaderControlLayerState
import com.reader.android.ui.theme.ReaderTheme

enum class BrightnessDock { Left, Right }

// Zone metrics — single source of truth for control layer vertical layout
private val topBarHeight = 56.dp
private val metaRowHeight = 36.dp
private val topZoneHeight = topBarHeight + metaRowHeight // 92.dp
private val floatingPageControlHeight = 52.dp
private val bottomBarHeight = 68.dp
private val bottomSafeGap = 8.dp
private val quickCircleSize = 48.dp

@Composable
fun ReaderControlBase(
    chapterTitle: String,
    bookTitle: String,
    sourceName: String,
    chapterProgress: Float,
    brightnessDock: BrightnessDock,
    modifier: Modifier = Modifier,
    overlayState: ReaderControlLayerState? = null,
    brightnessValue: Float = 0.5f,
    onBackClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    onSourceChangeClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAutoScrollClick: () -> Unit = {},
    onReplaceClick: () -> Unit = {},
    onNightModeClick: () -> Unit = {},
    onPrevPageClick: () -> Unit = {},
    onNextPageClick: () -> Unit = {},
    onDirectoryClick: () -> Unit = {},
    onTtsClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    overlayContent: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    // ── Visibility rules ──
    val isQuickOverlay = overlayState is ReaderControlLayerState.QuickActionOverlay
    val isBottomOverlay = overlayState is ReaderControlLayerState.BottomFunctionOverlay

    val showBrightness = !isQuickOverlay && !isBottomOverlay
    val showQuickActions = !isBottomOverlay
    val showPageControl = !isBottomOverlay

    // Compute quick-action and page-control bottom insets so overlays don't cover them
    val quickActionsBottomInset = bottomBarHeight + bottomSafeGap + floatingPageControlHeight +
        ReaderTheme.spacing.controlGap + quickCircleSize + ReaderTheme.spacing.controlGap
    val pageControlBottomInset = bottomBarHeight + bottomSafeGap
    val bottomOverlayBottomInset = bottomBarHeight + bottomSafeGap

    Box(modifier = modifier.fillMaxSize()) {
        // Reading content behind all controls
        content()

        // Top area — always visible
        ReaderTopArea(
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            sourceName = sourceName,
            onBackClick = onBackClick,
            onRefreshClick = onRefreshClick,
            onSourceChangeClick = onSourceChangeClick,
            onMoreClick = onMoreClick,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Brightness dock — hidden during any overlay
        if (showBrightness) {
            ReaderFloatingBrightness(
                dock = brightnessDock,
                brightnessValue = brightnessValue,
                onBrightnessChange = onBrightnessChange,
                modifier = Modifier.align(
                    if (brightnessDock == BrightnessDock.Left) Alignment.CenterStart
                    else Alignment.CenterEnd
                )
            )
        }

        // Quick actions — hidden during bottom overlays
        if (showQuickActions) {
            ReaderFloatingQuickActions(
                onSearchClick = onSearchClick,
                onAutoScrollClick = onAutoScrollClick,
                onReplaceClick = onReplaceClick,
                onNightModeClick = onNightModeClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pageControlBottomInset + floatingPageControlHeight + ReaderTheme.spacing.controlGap)
            )
        }

        // Page control — hidden during bottom overlays
        if (showPageControl) {
            ReaderFloatingPageControl(
                progress = chapterProgress,
                onPrevPageClick = onPrevPageClick,
                onNextPageClick = onNextPageClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = pageControlBottomInset)
            )
        }

        // Bottom bar — always visible
        ReaderControlBottomBar(
            onDirectoryClick = onDirectoryClick,
            onTtsClick = onTtsClick,
            onAppearanceClick = onAppearanceClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── Zone-based overlay panels ──
        if (isQuickOverlay) {
            // Covers zone from below meta-row down to above quick actions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topZoneHeight,
                        bottom = quickActionsBottomInset
                    )
                    .padding(horizontal = 18.dp)
            ) {
                overlayContent()
            }
        }

        if (isBottomOverlay) {
            // Covers zone from below meta-row down to above bottom bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = topZoneHeight,
                        bottom = bottomOverlayBottomInset
                    )
                    .padding(horizontal = 18.dp)
            ) {
                overlayContent()
            }
        }
    }
}

@Composable
private fun ReaderTopArea(
    bookTitle: String,
    chapterTitle: String,
    sourceName: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSourceChangeClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Top bar row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarHeight)
                .background(ReaderTheme.colors.softTopBg)
                .padding(horizontal = ReaderTheme.spacing.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = ReaderTheme.colors.controlInk
                )
            }
            Text(
                text = bookTitle,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.pageTitle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)) {
                ReaderTopIcon(Icons.Filled.Refresh, "刷新当前章节", onRefreshClick)
                ReaderTopIcon(Icons.Filled.SwapHoriz, "换源", onSourceChangeClick)
                ReaderTopIcon(Icons.Filled.MoreVert, "更多操作", onMoreClick)
            }
        }
        // Meta row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metaRowHeight)
                .background(ReaderTheme.colors.metaBg)
                .padding(horizontal = ReaderTheme.spacing.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = chapterTitle,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.readerControlLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(ReaderTheme.spacing.sm))
            Text(
                text = sourceName,
                modifier = Modifier
                    .clip(ReaderTheme.shapes.chip)
                    .background(ReaderTheme.colors.floatingControlBg)
                    .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.chip)
                    .padding(horizontal = ReaderTheme.spacing.sm, vertical = 2.dp),
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.readerControlLabel,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ReaderTopIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = ReaderTheme.colors.controlInk)
    }
}

@Composable
private fun ReaderFloatingBrightness(
    dock: BrightnessDock,
    brightnessValue: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(40.dp)
            .height(256.dp)
            .background(ReaderTheme.colors.floatingControlBg, CircleShape)
            .border(1.dp, ReaderTheme.colors.controlBorder, CircleShape)
            .semantics { contentDescription = "亮度控制" }
            .padding(vertical = ReaderTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
    ) {
        Icon(
            Icons.Filled.BrightnessAuto,
            contentDescription = "自动亮度",
            tint = ReaderTheme.colors.controlInk,
            modifier = Modifier.size(20.dp)
        )
        // Vertical brightness track — proportional fill conveys slider state
        Box(
            modifier = Modifier
                .weight(1f)
                .width(4.dp)
                .clip(CircleShape)
                .background(ReaderTheme.colors.mutedTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(brightnessValue.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(ReaderTheme.colors.primary)
            )
        }
        Icon(
            if (dock == BrightnessDock.Left) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = "移动亮度条到${if (dock == BrightnessDock.Left) "右" else "左"}侧",
            tint = ReaderTheme.colors.controlInk,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ReaderFloatingQuickActions(
    onSearchClick: () -> Unit,
    onAutoScrollClick: () -> Unit,
    onReplaceClick: () -> Unit,
    onNightModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { contentDescription = "快捷功能" },
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.quickCircleGap)
    ) {
        ReaderQuickCircle(Icons.Filled.Search, "搜索本章", onSearchClick)
        ReaderQuickCircle(Icons.Filled.AutoMode, "自动翻页", onAutoScrollClick)
        ReaderQuickCircle(Icons.Filled.SwapHoriz, "内容替换", onReplaceClick)
        ReaderQuickCircle(Icons.Filled.DarkMode, "切换夜间模式", onNightModeClick)
    }
}

@Composable
private fun ReaderQuickCircle(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(quickCircleSize)
            .clip(CircleShape)
            .background(ReaderTheme.colors.quickButtonBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = ReaderTheme.colors.controlInk)
    }
}

@Composable
private fun ReaderFloatingPageControl(
    progress: Float,
    onPrevPageClick: () -> Unit,
    onNextPageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(floatingPageControlHeight)
            .clip(ReaderTheme.shapes.floatingControl)
            .background(ReaderTheme.colors.floatingControlBg)
            .border(1.dp, ReaderTheme.colors.controlBorder, ReaderTheme.shapes.floatingControl)
            .semantics { contentDescription = "本章内翻页控制" }
            .padding(horizontal = ReaderTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.md)
    ) {
        IconButton(
            onClick = onPrevPageClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "本章内上一页",
                tint = ReaderTheme.colors.primary
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "本章阅读进度，${(progress * 100).toInt()}%" },
            color = ReaderTheme.colors.primary,
            trackColor = ReaderTheme.colors.mutedTrack,
            drawStopIndicator = {}
        )
        IconButton(
            onClick = onNextPageClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "本章内下一页",
                tint = ReaderTheme.colors.primary
            )
        }
    }
}

@Composable
private fun ReaderControlBottomBar(
    onDirectoryClick: () -> Unit,
    onTtsClick: () -> Unit,
    onAppearanceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(bottomBarHeight)
            .background(ReaderTheme.colors.bottomBarBg)
            .border(1.dp, ReaderTheme.colors.controlBorder)
            .semantics { contentDescription = "阅读控制底栏" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReaderBottomItem(Icons.Filled.MenuBook, "目录", onDirectoryClick)
        ReaderBottomItem(Icons.Filled.RecordVoiceOver, "朗读", onTtsClick)
        ReaderBottomItem(Icons.Filled.Tune, "界面设置", onAppearanceClick)
        ReaderBottomItem(Icons.Filled.Settings, "阅读行为设置", onSettingsClick)
    }
}

@Composable
private fun ReaderBottomItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = ReaderTheme.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = ReaderTheme.colors.controlInk)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = ReaderTheme.colors.controlInk,
            style = ReaderTheme.typography.readerControlLabel
        )
    }
}
