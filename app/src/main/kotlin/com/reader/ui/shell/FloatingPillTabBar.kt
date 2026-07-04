package com.reader.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.motion.AppMotionTokens
import com.reader.ui.theme.ReaderElevations
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

enum class MainNavLayout { BottomPill, LeftRail }

/**
 * Floating rounded-pill bottom navigation — the App Shell main tab bar.
 *
 * Dimensions mirror `frontend-demo/styles/01-shell-layout.css` `.fd-main-nav` /
 * `.fd-main-nav-item` verbatim (px → dp at mdpi):
 * - Floats 14dp above the bottom safe area with 14dp side margins (NOT full-width).
 * - min-height 68dp, inner padding 7dp × 8dp, corner radius 24dp.
 * - Background rgba(255,252,248,0.92), 1dp hairline border, soft shadow.
 * - 4 equal-weight items; active item = full-item filled pill (primary-dark #274f66) with
 *   white icon/label — no separate indicator (geometry stays stable across switches).
 * - Per item: 30dp icon shell + 3dp gap + 18dp label row; icon svg 24dp; label 11sp/800.
 *
 * Contract alignment (FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 1, MOTION_EFFECTS.md §4):
 * button count, size, and hit area are identical before and after a switch; only the active
 * fill migrates. `tab.item.press` (80ms pressed feedback) is wired via the interaction
 * source; under reduced motion only color/state changes are emitted.
 */
@Composable
fun FloatingPillTabBar(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    layout: MainNavLayout = MainNavLayout.BottomPill,
    modifier: Modifier = Modifier
) {
    when (layout) {
        MainNavLayout.BottomPill -> BottomPillTabBar(
            activeTab = activeTab,
            onSelect = onSelect,
            modifier = modifier
        )
        MainNavLayout.LeftRail -> LeftRailTabBar(
            activeTab = activeTab,
            onSelect = onSelect,
            modifier = modifier
        )
    }
}

@Composable
private fun BottomPillTabBar(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            // Float 14dp above the system navigation bar inset (gesture bar / 3-button nav).
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .shadow(elevation = ReaderElevations.softShadow, shape = ReaderShapes.xl, clip = false)
            .background(color = extra.navBackground, shape = ReaderShapes.xl)
            .border(width = 1.dp, color = extra.border, shape = ReaderShapes.xl)
            .padding(vertical = 7.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            MainTab.ORDER.forEach { tab ->
                TabItem(
                    tab = tab,
                    isActive = tab == activeTab,
                    onSelect = { onSelect(tab) },
                    layout = MainNavLayout.BottomPill,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LeftRailTabBar(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // Demo tablet-expanded: left:16, top:50%, translateY(-50%), width:82, max-height:calc(100%-96).
    // Parent (MainTabShellFrame) supplies fillMaxHeight; we center within it via wrapContentHeight.
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 16.dp)
            .width(82.dp)
            .heightIn(max = 696.dp), // 100% - 96px approximation on phone-height viewport
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .shadow(elevation = ReaderElevations.softShadow, shape = ReaderShapes.xl, clip = false)
                .background(color = extra.navBackground, shape = ReaderShapes.xl)
                .border(width = 1.dp, color = extra.border, shape = ReaderShapes.xl)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.width(66.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MainTab.ORDER.forEach { tab ->
                    TabItem(
                        tab = tab,
                        isActive = tab == activeTab,
                        onSelect = { onSelect(tab) },
                        layout = MainNavLayout.LeftRail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    layout: MainNavLayout,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor = when {
        isActive -> extra.primaryDark
        isPressed -> extra.hairline.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val contentColor = if (isActive) extra.onPrimary else extra.navInactive
    // Demo .fd-main-nav-item: grid-template-rows 30px 18px (portrait), 32px 16px (tablet-expanded).
    val iconShellSize = if (layout == MainNavLayout.LeftRail) 32.dp else 30.dp
    val labelLineHeight = if (layout == MainNavLayout.LeftRail) 16.dp else 18.dp

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = if (layout == MainNavLayout.LeftRail) 58.dp else 54.dp)
            .background(color = backgroundColor, shape = ReaderShapes.xl)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon shell (demo .fd-main-nav-icon-shell): 30×30 portrait / 32×32 tablet, circle clip.
            Box(
                modifier = Modifier
                    .size(iconShellSize)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = tabIconRes(tab)),
                    contentDescription = tab.label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = tab.label,
                style = ReaderTextStyles.tabLabel.copy(color = contentColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@DrawableRes
private fun tabIconRes(tab: MainTab): Int = when (tab) {
    MainTab.BOOKSHELF -> R.drawable.reader_ic_bookshelf
    MainTab.DISCOVER -> R.drawable.reader_ic_discover
    MainTab.RSS -> R.drawable.reader_ic_rss
    MainTab.SETTINGS -> R.drawable.reader_ic_settings
}
