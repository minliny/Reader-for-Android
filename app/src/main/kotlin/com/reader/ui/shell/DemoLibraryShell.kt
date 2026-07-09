package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.demo.DemoButton
import com.reader.ui.demo.DemoIcon
import com.reader.ui.demo.DemoRouteAction
import com.reader.ui.motion.ViewportClass
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.tokens.ReaderSizeToken
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter

/**
 * LibraryShell — mirrors `renderLibraryShell` in `shared-shell-kit/kit.js`.
 *
 * Fixed slot order: statusBar + backTopBar + contentRegion + bottomActionHost +
 * sheetHost + dialogHost + stateHost.
 *
 * The status bar is honored through `WindowInsets.statusBars`; the optional bottom
 * action host floats above content. Structural heights consume
 * [ReaderSizeToken.TOP_BAR_HEIGHT] and horizontal content padding consumes
 * [ReaderSpacingToken.SCREEN_PADDING].
 *
 * 自适应：TABLET_EXPANDED / EXPANDED_WIDTH 时内容区 maxWidth 约束为 720.dp（居中）。
 *
 * Slice D: [overlayState] drives the sheetHost / dialogHost overlay slots — when non-None
 * the corresponding Sheet/Dialog content renders above the content region.
 */
@Composable
fun DemoLibraryShell(
    title: String,
    onBack: () -> Unit,
    bottomActions: List<DemoRouteAction> = emptyList(),
    onNavigate: (String) -> Unit = {},
    viewportClass: ViewportClass = ViewportClass.PORTRAIT,
    overlayState: OverlayState = OverlayState.None,
    content: LazyListScope.() -> Unit
) {
    val screenPadding = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING)
    val isWideScreen = viewportClass == ViewportClass.TABLET_EXPANDED ||
        viewportClass == ViewportClass.EXPANDED_WIDTH
    val contentMaxWidth = if (isWideScreen) 720.dp else Dp.Unspecified
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .widthIn(max = contentMaxWidth)
                .fillMaxSize()
        ) {
            DemoBackBar(title = title, onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = screenPadding,
                    end = screenPadding,
                    top = ReaderTokenAdapter.spacing(ReaderSpacingToken.XS),
                    bottom = if (bottomActions.isEmpty()) 28.dp else 92.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
        if (bottomActions.isNotEmpty()) {
            DemoBottomActions(
                actions = bottomActions,
                onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        // Overlay host slots (sheetHost + dialogHost) — display:contents; rendered as overlay when populated
        LibrarySheetHostSlot(overlayState)
        LibraryDialogHostSlot(overlayState)
    }
}

/**
 * App top bar (no back affordance) — mirrors kit.js `appTopBar` helper.
 *
 * Min height consumes [ReaderSizeToken.TOP_BAR_HEIGHT]. Used by MainTabShell.
 */
@Composable
fun DemoTopBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ReaderTokenAdapter.size(ReaderSizeToken.TOP_BAR_HEIGHT))
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.appBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Back top bar — mirrors kit.js `backTopBar` helper.
 *
 * Grid mirrors the demo `fd-back-bar`: 44px | 1fr | 44px (back icon / title / trailing
 * placeholder). Padding 6px 20px 0 per `00-foundation.css` `.fd-back-bar`. Min height
 * consumes [ReaderSizeToken.TOP_BAR_HEIGHT].
 */
@Composable
fun DemoBackBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ReaderTokenAdapter.size(ReaderSizeToken.TOP_BAR_HEIGHT))
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            DemoIcon(R.drawable.reader_ic_chevron_left, size = 24.dp, tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Trailing slot placeholder — demo `backTopBar` outputs `<span></span>` when
        // trailingIcon is null (kit.js line 64), preserving the 44px 1fr 44px grid.
        Spacer(Modifier.size(44.dp))
    }
}

/**
 * Bottom action host — mirrors kit.js `bottomActionHost` slot.
 *
 * Renders up to two primary/secondary actions. Extracted from `DemoRouteScreen` and
 * made public so that LibraryShell and SettingsShell can share the same chrome.
 */
@Composable
fun DemoBottomActions(
    actions: List<DemoRouteAction>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        actions.take(2).forEachIndexed { index, action ->
            DemoButton(
                label = action.label,
                primary = index == actions.lastIndex.coerceAtMost(1),
                modifier = Modifier.weight(1f)
            ) { onNavigate(action.targetRoute) }
        }
    }
}
