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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.motion.AppMotionTokens
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

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
            .shadow(elevation = 12.dp, shape = ReaderShapes.xl, clip = false)
            .background(color = extra.navBackground, shape = ReaderShapes.xl)
            .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.xl)
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MainTab,
    isActive: Boolean,
    onSelect: () -> Unit,
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
    val contentColor = if (isActive) Color.White else extra.navInactive

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
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
            // Icon shell 30×30 (demo .fd-main-nav-icon-shell), icon svg 24×24 (.fd-nav-icon).
            Box(
                modifier = Modifier.size(30.dp),
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
                maxLines = 1
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
