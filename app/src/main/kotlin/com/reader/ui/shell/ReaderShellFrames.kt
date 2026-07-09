package com.reader.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.readerExtraColors

/**
 * Public shell frames aligned with `frontend-demo/shared-shell-kit/kit.js`.
 *
 * Each frame mirrors the demo's fixed slot layout and exposes named slot lambdas so that
 * individual route screens can populate `backTopBar` / `contentRegion` / `bottomActionHost`
 * while the Frame guarantees the structural parity required by the demo contract.
 *
 * Slots default to empty placeholders (zero-size Box) so that screens which don't
 * need a particular slot still preserve its addressability per demo's `display:contents` rule.
 */

// ============================================================
// LibraryShell — 7 fixed slots (kit.js renderLibraryShell)
// ============================================================
// statusBar + backTopBar + contentRegion + bottomActionHost + sheetHost + dialogHost + stateHost

@Composable
fun LibraryShellFrame(
    overlayState: OverlayState = OverlayState.None,
    statusBar: @Composable () -> Unit = { LibraryShellStatusBarSlot() },
    backTopBar: @Composable () -> Unit,
    contentRegion: @Composable () -> Unit,
    bottomActionHost: @Composable () -> Unit = { LibraryBottomActionHostSlot() },
    sheetHost: @Composable () -> Unit = { LibrarySheetHostSlot(overlayState) },
    dialogHost: @Composable () -> Unit = { LibraryDialogHostSlot(overlayState) },
    stateHost: @Composable () -> Unit = { LibraryStateHostSlot() }
) {
    val paper = readerExtraColors().paper
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            statusBar()
            backTopBar()
            Box(modifier = Modifier.weight(1f)) {
                contentRegion()
            }
        }
        // bottomActionHost: demo .fd-bottom-action-host — position:absolute overlay at bottom.
        // left/right = safe-area + 3px, bottom = safe-area-bottom, padding-top 10px,
        // gradient bg (transparent → paper 0.96 → paper solid).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            paper.copy(alpha = 0f),
                            paper.copy(alpha = 0.96f),
                            paper
                        )
                    )
                )
                .padding(top = 10.dp)
        ) {
            bottomActionHost()
        }
        // Overlay-positioned slots (demo CSS: display:contents; rendered as overlay when populated)
        sheetHost()
        dialogHost()
        stateHost()
    }
}

// ============================================================
// SettingsShell — 8 fixed slots (kit.js renderSettingsShell)
// ============================================================
// statusBar + backTopBar + settingsContent + bottomActionHost + sheetHost + toastHost + dialogHost + settingsStateHost

@Composable
fun SettingsShellFrame(
    overlayState: OverlayState = OverlayState.None,
    statusBar: @Composable () -> Unit = { SettingsShellStatusBarSlot() },
    backTopBar: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    bottomActionHost: @Composable () -> Unit = { SettingsBottomActionHostSlot() },
    sheetHost: @Composable () -> Unit = { SettingsSheetHostSlot(overlayState) },
    toastHost: @Composable () -> Unit = { SettingsToastHostSlot() },
    dialogHost: @Composable () -> Unit = { SettingsDialogHostSlot(overlayState) },
    settingsStateHost: @Composable () -> Unit = { SettingsStateHostSlot() }
) {
    val paper = readerExtraColors().paper
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            statusBar()
            backTopBar()
            Box(modifier = Modifier.weight(1f)) {
                settingsContent()
            }
        }
        // bottomActionHost: demo .fd-bottom-action-host — position:absolute overlay at bottom.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 3.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            paper.copy(alpha = 0f),
                            paper.copy(alpha = 0.96f),
                            paper
                        )
                    )
                )
                .padding(top = 10.dp)
        ) {
            bottomActionHost()
        }
        // Overlay-positioned slots (demo CSS: display:contents; rendered as overlay when populated)
        sheetHost()
        toastHost()
        dialogHost()
        settingsStateHost()
    }
}

// ============================================================
// Default slot placeholders — zero-size / no-op for empty slots
// ============================================================
// Per demo CSS: .fd-state-host, .fd-sheet-host, .fd-dialog-host, .fd-toast-host,
// .fd-bottom-action-host:empty { display: none } — empty slots vanish from layout.

@Composable
fun LibraryShellStatusBarSlot() {
    // statusBar slot: native Android uses system status bar inset (windowInsetsPadding on frame);
    // demo renders time + signal/wifi/battery icons. Zero-size Box preserves addressability.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun LibraryBottomActionHostSlot() {
    // bottomActionHost slot: .fd-bottom-action-host:empty { display: none }
    // Preserved as zero-size Box for slot addressability per demo contract.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun LibrarySheetHostSlot(overlayState: OverlayState = OverlayState.None) {
    // sheetHost slot: display:contents; renders Sheet overlay when overlayState is Sheet.
    when (overlayState) {
        is OverlayState.Sheet -> {
            when (overlayState.content) {
                is SheetContent.ReaderSetting -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, ReaderShapes.xl)
                            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
                            .padding(16.dp)
                    ) {
                        Text(
                            "阅读设置 · ${overlayState.content.module}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is SheetContent.BookshelfFilter -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, ReaderShapes.xl)
                            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
                            .padding(16.dp)
                    ) {
                        Text(
                            "书架筛选 · ${overlayState.content.filterId}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        else -> {}  // None/Keyboard/Dialog 不在此槽位渲染
    }
}

@Composable
fun LibraryDialogHostSlot(overlayState: OverlayState = OverlayState.None) {
    // dialogHost slot: display:contents; renders Dialog overlay when overlayState is Dialog.
    when (overlayState) {
        is OverlayState.Dialog -> {
            when (overlayState.content) {
                is DialogContent.Confirm -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {
                            TextButton(onClick = overlayState.content.onConfirm) {
                                Text("确认")
                            }
                        },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text(overlayState.content.title) },
                        text = { Text(overlayState.content.message) }
                    )
                }
                is DialogContent.SourceSwitch -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("切换") } },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text("换源确认") },
                        text = { Text("书源 ID：${overlayState.content.sourceId}") }
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
fun LibraryStateHostSlot() {
    // stateHost slot: display:contents; preserved as zero-size Box for addressability.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun SettingsShellStatusBarSlot() {
    // statusBar slot: native Android uses system status bar inset (windowInsetsPadding on frame);
    // demo renders time + signal/wifi/battery icons. Zero-size Box preserves addressability.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun SettingsBottomActionHostSlot() {
    // bottomActionHost slot: .fd-bottom-action-host:empty { display: none }
    // Preserved as zero-size Box for slot addressability per demo contract.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun SettingsSheetHostSlot(overlayState: OverlayState = OverlayState.None) {
    // sheetHost slot: display:contents; renders Sheet overlay when overlayState is Sheet.
    when (overlayState) {
        is OverlayState.Sheet -> {
            when (overlayState.content) {
                is SheetContent.ReaderSetting -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, ReaderShapes.xl)
                            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
                            .padding(16.dp)
                    ) {
                        Text(
                            "阅读设置 · ${overlayState.content.module}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is SheetContent.BookshelfFilter -> {
                    Box(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, ReaderShapes.xl)
                            .border(1.dp, readerExtraColors().hairline, ReaderShapes.xl)
                            .padding(16.dp)
                    ) {
                        Text(
                            "书架筛选 · ${overlayState.content.filterId}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        else -> {}  // None/Keyboard/Dialog 不在此槽位渲染
    }
}

@Composable
fun SettingsToastHostSlot() {
    // toastHost slot: display:contents; preserved as zero-size Box for addressability (SettingsShell-only).
    Box(modifier = Modifier.size(0.dp))
}

@Composable
fun SettingsDialogHostSlot(overlayState: OverlayState = OverlayState.None) {
    // dialogHost slot: display:contents; renders Dialog overlay when overlayState is Dialog.
    when (overlayState) {
        is OverlayState.Dialog -> {
            when (overlayState.content) {
                is DialogContent.Confirm -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {
                            TextButton(onClick = overlayState.content.onConfirm) {
                                Text("确认")
                            }
                        },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text(overlayState.content.title) },
                        text = { Text(overlayState.content.message) }
                    )
                }
                is DialogContent.SourceSwitch -> {
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = { TextButton(onClick = {}) { Text("切换") } },
                        dismissButton = { TextButton(onClick = {}) { Text("取消") } },
                        title = { Text("换源确认") },
                        text = { Text("书源 ID：${overlayState.content.sourceId}") }
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
fun SettingsStateHostSlot() {
    // settingsStateHost slot: display:contents; preserved as zero-size Box for addressability (SettingsShell-only).
    Box(modifier = Modifier.size(0.dp))
}
