package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.reader.ui.demo.DemoRouteAction
import com.reader.ui.motion.ViewportClass
import com.reader.ui.tokens.ReaderSpacingToken
import com.reader.ui.tokens.ReaderTokenAdapter
import com.reader.ui.tokens.ReaderZIndexToken

/**
 * SettingsShell — mirrors `renderSettingsShell` in `shared-shell-kit/kit.js`.
 *
 * Fixed slot order: statusBar + backTopBar + settingsContent + bottomActionHost +
 * sheetHost + toastHost + dialogHost + settingsStateHost.
 *
 * Structurally a sibling of [DemoLibraryShell] (backTopBar + scrollable content +
 * optional bottom actions) with an additional [toastContent] slot that floats above
 * the content region. Top-bar height consumes [ReaderSizeToken.TOP_BAR_HEIGHT] (via
 * [DemoBackBar]); horizontal content padding consumes [ReaderSpacingToken.SCREEN_PADDING].
 *
 * 自适应：TABLET_EXPANDED / EXPANDED_WIDTH 时内容区 maxWidth 约束为 720.dp（居中）。
 *
 * Slice D: [overlayState] drives the sheetHost / dialogHost overlay slots — when non-None
 * the corresponding Sheet/Dialog content renders above the content region.
 */
@Composable
fun DemoSettingsShell(
    title: String,
    onBack: () -> Unit,
    bottomActions: List<DemoRouteAction> = emptyList(),
    onNavigate: (String) -> Unit = {},
    toastContent: (@Composable () -> Unit)? = null,
    viewportClass: ViewportClass = ViewportClass.PORTRAIT,
    overlayState: OverlayState = OverlayState.None,
    content: LazyListScope.() -> Unit
) {
    val screenPadding = ReaderTokenAdapter.spacing(ReaderSpacingToken.SCREEN_PADDING)
    val overlayZ = ReaderTokenAdapter.zIndex(ReaderZIndexToken.OVERLAY)
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
        // toastHost — transient notification slot, layered above content but beneath dialogs.
        if (toastContent != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = screenPadding,
                        end = screenPadding,
                        bottom = if (bottomActions.isNotEmpty()) 96.dp else 28.dp
                    )
                    .zIndex(overlayZ)
            ) {
                toastContent()
            }
        }
        // Overlay host slots (sheetHost + dialogHost) — display:contents; rendered as overlay when populated
        SettingsSheetHostSlot(overlayState)
        SettingsDialogHostSlot(overlayState)
    }
}
