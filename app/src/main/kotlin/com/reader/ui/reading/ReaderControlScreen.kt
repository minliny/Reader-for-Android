package com.reader.ui.reading

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reader.android.R
import com.reader.ui.shell.AsyncResultStateValue
import com.reader.ui.shell.ReaderContext
import com.reader.ui.shell.ReaderRoute
import com.reader.ui.shell.RouteIds
import com.reader.ui.theme.ReaderElevations
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * Reader shell — self-rendering frame aligned with `frontend-demo/shared-shell-kit/kit.js`
 * `renderReaderShell`. Renders 5 fixed slots as internal regions (route-driven branching
 * makes slot-lambda injection impractical — unlike MainTab/Library/Settings frames which
 * serve multiple routes, this shell has a single call site with state-coupled layout):
 *
 * 1. `readingSurface` — [ReaderControlReadingSurface] (always rendered)
 * 2. `readerOverlayHost` — branch wrapper containing top overlay / tap zones / panels
 * 3. `bottomSheetHost` — [ReaderBottomSheetHostSlot] (zero-size Box placeholder when full/utility
 *    panel takes over, per demo `fd-reader-sheet-empty { display: none }`)
 * 4. `readerModuleNav` — [ReaderModuleNavSlot] (zero-size Box placeholder when full/utility panel
 *    takes over, per demo `fd-reader-module-nav-empty { display: none }`)
 * 5. `readerStateHost` — [ReaderGlobalBrightnessDim] (always rendered, non-interactive overlay)
 *
 * `readerTextSelectionLayer` is an overlay sub-slot of `readerOverlayHost`; it preserves a
 * zero-size Box placeholder when selection is closed (per demo fixed-slot contract).
 */
@Composable
fun ReaderShellScreen(
    route: ReaderRoute,
    fallbackContext: ReaderContext?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    activeSession: com.reader.ui.shell.ActiveSession? = null,
    onSessionToggle: () -> Unit = {},
    onSessionStop: () -> Unit = {},
    onAsyncStateChange: ((requestId: String, state: AsyncResultStateValue, value: Any?) -> Unit)? = null
) {
    val context = when (route) {
        is ReaderRoute.ImmersiveReading -> route.context
        is ReaderRoute.ReaderControl -> route.context ?: fallbackContext
        is ReaderRoute.SourceSwitchFlow -> route.context ?: fallbackContext
        else -> fallbackContext
    }
    val routeId = when (route) {
        is ReaderRoute.ImmersiveReading -> RouteIds.IMMERSIVE_READING
        is ReaderRoute.ReaderControl -> route.id
        is ReaderRoute.SourceSwitchFlow -> RouteIds.SOURCE_SWITCH
        else -> RouteIds.READER_CONTROL
    }
    val title = context?.bookName?.takeIf { it.isNotBlank() } ?: "长夜余火"
    val isImmersive = routeId == RouteIds.IMMERSIVE_READING
    val readerRouteId = if (routeId == RouteIds.SOURCE_SWITCH) RouteIds.READER_CONTROL else routeId
    val fullPanelKind = readerFullPanelKind(readerRouteId)
    val utilityPanelKind = readerUtilityPanelKind(readerRouteId)
    // bottomSheetHost and readerModuleNav are fixed slots per demo contract; they render
    // placeholder Boxes when full/utility panels take over, preserving slot addressability.
    val renderBottomSheet = !isImmersive && fullPanelKind == null && utilityPanelKind == null
    val renderModuleNav = !isImmersive && fullPanelKind == null && utilityPanelKind == null
    var readerTextSelectionOpen by remember { mutableStateOf(false) }
    // --reader-brightness-dim: adjustable 0..0.32 (demo contract: capped at 0.32)
    var brightnessDim by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerExtraColors().paper)
    ) {
        // readingSurface slot
        ReaderControlReadingSurface(
            context = context,
            fallbackTitle = title,
            onAsyncStateChange = onAsyncStateChange
        )
        // readerOverlayHost slot (named via branch wrapper)
        if (isImmersive) {
            ReaderShellImmersiveInfoLayer(
                title = title,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            ReaderOpenControlTapZone(
                onOpenControls = { onNavigate(RouteIds.READER_CONTROL) },
                onLongPress = { readerTextSelectionOpen = true },
                modifier = Modifier.fillMaxSize()
            )
            // readerTextSelectionLayer: text selection toolbar + range (immersive overlay slot)
            if (readerTextSelectionOpen) {
                ReaderTextSelectionLayer(
                    onClose = { readerTextSelectionOpen = false },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // readerTextSelectionLayer slot placeholder — preserved as zero-size Box for
                // slot addressability when selection is closed (demo contract: fixed slot).
                Box(modifier = Modifier.size(0.dp))
            }
            // bottomSheetHost + readerModuleNav fixed slots: empty placeholders when immersive
            ReaderBottomSheetHostSlot(modifier = Modifier.fillMaxSize())
            ReaderModuleNavSlot(modifier = Modifier.fillMaxSize())
        } else {
            // Demo .fd-reader-dismiss-zone: left:24, right:24, top:92, bottom:360, transparent.
            ReaderControlDismissTapZone(
                onDismiss = { onNavigate(RouteIds.IMMERSIVE_READING) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, top = 92.dp, bottom = 360.dp)
            )
            ReaderControlTopOverlay(
                title = title,
                sourceLine = "优书网 · 第 32 章 雨夜",
                onBack = onBack,
                onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            ReaderBrightnessRail(
                brightness = brightnessDim,
                onBrightnessChange = { brightnessDim = it },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 28.dp, bottom = 110.dp)
            )
            when {
                fullPanelKind != null -> ReaderFullPagePanel(
                    kind = fullPanelKind,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 34.dp)
                )
                utilityPanelKind != null -> ReaderUtilityPanel(
                    kind = utilityPanelKind,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 34.dp)
                )
                else -> ReaderControlBottomSheet(
                    routeId = readerRouteId,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
                )
            }
            // fd-reader-control-session-host: fixed slot positioned 8px above bottom sheet
            // (sheet: bottom 18dp + height 330dp + 8dp gap = 356dp from bottom).
            // Demo: position absolute, right: 24px, bottom: calc(18px+330px+8px), height: 26px,
            // width: 110px, pointer-events: none (host), inner capsule pointer-events: auto.
            // Only rendered when bottom sheet is visible (no full/utility panel takeover).
            if (renderBottomSheet) {
                ReaderControlSessionHost(
                    activeSession = activeSession,
                    onToggle = onSessionToggle,
                    onStop = onSessionStop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 356.dp)
                )
            }
            // bottomSheetHost fixed slot: when renderBottomSheet is true, the actual sheet
            // content was already rendered above via ReaderControlBottomSheet in the `when` else
            // branch (the sheet IS the slot content). When full/utility panel takes over
            // (renderBottomSheet false), render zero-size Box placeholder to preserve slot
            // addressability per demo fixed-slot contract (visibility:hidden equivalent).
            if (!renderBottomSheet) {
                ReaderBottomSheetHostSlot(modifier = Modifier.fillMaxSize())
            }
            // readerModuleNav fixed slot: real content when no full/utility panel, else placeholder
            if (renderModuleNav) {
                ReaderModuleNav(
                    routeId = readerRouteId,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                )
            } else {
                ReaderModuleNavSlot(modifier = Modifier.fillMaxSize())
            }
        }
        // readerStateHost: global brightness dim (fixed slot, non-interactive overlay)
        ReaderGlobalBrightnessDim(dimAlpha = brightnessDim, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ReaderBottomSheetHostSlot(modifier: Modifier = Modifier) {
    // bottomSheetHost slot placeholder — preserved as zero-size Box for slot addressability
    // when full/utility panels replace the bottom sheet (demo contract: fixed slot, visibility:hidden).
    Box(modifier = modifier.size(0.dp))
}

/**
 * Session-host capsule — fixed slot above bottom sheet for active session indicator (TTS/auto-page).
 * Demo contract: `.fd-reader-control-session-host` (right: 24px, bottom: 18+330+8, height: 26px,
 * width: 110px, pointer-events: none on host) + `.fd-reader-control-session-capsule`
 * (bg = --reader-control-surface-solid, box-shadow: 0 4px 12px rgba(0,0,0,0.12)).
 * Host is non-interactive; inner capsule is interactive.
 *
 * P5 wiring: capsule reads [activeSession] from the single ReaderUiState and dispatches
 * [onToggle] (play/pause) on click. When no session is active, the capsule is hidden
 * (demo: capsule only visible while session owns the reader — `reader.session.capsule.enter`).
 */
@Composable
private fun ReaderControlSessionHost(
    modifier: Modifier = Modifier,
    activeSession: com.reader.ui.shell.ActiveSession? = null,
    onToggle: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val extra = readerExtraColors()
    // No active session → capsule hidden (demo: capsule only visible while session owns reader).
    if (activeSession == null) {
        Box(modifier = modifier.size(0.dp))
        return
    }
    val isTts = activeSession.type == com.reader.ui.shell.SessionType.TTS
    val label = when (activeSession.type) {
        com.reader.ui.shell.SessionType.TTS -> if (activeSession.playing) "朗读中" else "已暂停"
        com.reader.ui.shell.SessionType.AUTO_PAGE -> if (activeSession.playing) "自动翻页" else "已暂停"
        com.reader.ui.shell.SessionType.NONE -> return
    }
    val statusColor = if (activeSession.playing) extra.controlPrimary else extra.controlMuted
    // Demo .fd-reader-control-session-host: host pointer-events:none, capsule pointer-events:auto.
    // Demo .fd-reader-control-session-capsule: bg controlSurfaceSolid, box-shadow 0 4px 12px rgba(0,0,0,0.12), no border.
    Box(
        modifier = modifier
            .width(110.dp)
            .height(26.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Inner capsule — interactive (clickable), surface-solid bg + shadow, no border per demo.
        Row(
            modifier = Modifier
                .height(26.dp)
                .shadow(elevation = ReaderElevations.sessionCapsuleShadow, shape = ReaderShapes.pill, clip = false)
                .background(color = extra.controlSurfaceSolid, shape = ReaderShapes.pill)
                .clip(ReaderShapes.pill)
                .clickable { onToggle() }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status dot — primary color while playing, muted while paused (pulse-ready indicator)
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = statusColor, shape = ReaderShapes.circle)
            )
            // Voice icon for TTS, auto-page icon for AUTO_PAGE (demo capsule voice-icon.active contract)
            Icon(
                painter = painterResource(
                    if (isTts) R.drawable.reader_ic_tts
                    else R.drawable.reader_ic_reader_auto_page
                ),
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                style = TextStyle(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight(700)
                ),
                color = extra.controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderModuleNavSlot(modifier: Modifier = Modifier) {
    // readerModuleNav slot placeholder — preserved as zero-size Box for slot addressability
    // when full/utility panels replace the module nav (demo contract: fixed slot, visibility:hidden).
    Box(modifier = modifier.size(0.dp))
}

@Composable
fun ReaderControlScreen(
    routeId: String,
    context: ReaderContext?,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    ReaderShellScreen(
        route = if (routeId == RouteIds.SOURCE_SWITCH) {
            ReaderRoute.SourceSwitchFlow(context)
        } else {
            ReaderRoute.ReaderControl(routeId, context)
        },
        fallbackContext = context,
        onBack = onBack,
        onNavigate = onNavigate
    )
}

/**
 * Flow shell — self-rendering frame aligned with `frontend-demo/shared-shell-kit/kit.js`
 * `renderFlowShell`. Renders 4 fixed slots as internal regions (source-switch flow has a
 * single call site with route-coupled layout; slot-lambda injection would add complexity
 * without benefit, unlike multi-route frames like LibraryShell/SettingsShell):
 *
 * 1. `stepRegion` — [FlowShellStepRegion] (reader control continuation, background layer)
 * 2. `comparisonRegion` — [FlowShellComparisonRegion] (source switch window, positioned overlay)
 * 3. `resultRegion` — [FlowShellResultRegionPlaceholder] (zero-size Box; phone portrait hides
 *    per demo `fd-source-result-slot { display: none }`, but slot preserved for breakpoints)
 * 4. `stateHost` — [FlowShellStateHostPlaceholder] (zero-size Box; empty per demo
 *    `fd-source-unused-slot { display: none }`, but slot preserved for future state overlays)
 *
 * All 4 slots preserve zero-size Box placeholders when empty, satisfying the demo fixed-slot
 * addressability contract.
 */
@Composable
fun FlowShellScreen(
    route: ReaderRoute.SourceSwitchFlow,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = route.context
    val title = context?.bookName?.takeIf { it.isNotBlank() } ?: "长夜余火"
    // FlowShell skeleton: 4 fixed slots — stepRegion / comparisonRegion / resultRegion / stateHost
    Box(modifier = Modifier.fillMaxSize().background(readerExtraColors().paper)) {
        // stepRegion: reader control continuation (background layer)
        FlowShellStepRegion(
            context = context,
            title = title,
            onBack = onBack,
            onNavigate = onNavigate
        )
        // comparisonRegion: source switch window (positioned overlay, drawn above step).
        // Demo .fd-source-window-slot: position absolute, left:12, right:12, top:92, bottom:360.
        FlowShellComparisonRegion(
            onClose = { onNavigate(RouteIds.READER_CONTROL) },
            onSelect = { onNavigate(RouteIds.READER_CONTROL) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 92.dp, bottom = 360.dp)
        )
        // resultRegion: fixed slot; hidden on phone portrait per demo CSS
        // (.fd-source-reader-continuation .fd-source-result-slot { display: none }).
        // Render placeholder Box so slot remains addressable for responsive breakpoints.
        FlowShellResultRegionPlaceholder(
            modifier = Modifier.fillMaxSize()
        )
        // stateHost: fixed slot; empty for source-switch per demo CSS
        // (.fd-source-unused-slot { display: none }). Render placeholder Box for addressability.
        FlowShellStateHostPlaceholder(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FlowShellResultRegionPlaceholder(modifier: Modifier = Modifier) {
    // resultRegion slot placeholder — not rendered on phone portrait (demo: display: none).
    // Kept as zero-size Box to preserve slot addressability across breakpoints.
    Box(modifier = modifier.size(0.dp))
}

@Composable
private fun FlowShellStateHostPlaceholder(modifier: Modifier = Modifier) {
    // stateHost slot placeholder — empty for source-switch (demo: display: none).
    // Kept as zero-size Box to preserve slot addressability for future state overlays.
    Box(modifier = modifier.size(0.dp))
}

@Composable
private fun FlowShellStepRegion(
    context: ReaderContext?,
    title: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // FlowShell is the source-switch continuation, not a fresh reader entry — the
        // async-result guard (M5) is not engaged here; onAsyncStateChange stays null.
        ReaderControlReadingSurface(
            context = context,
            fallbackTitle = title,
            onAsyncStateChange = null
        )
        ReaderControlTopOverlay(
            title = title,
            sourceLine = "优书网 · 第 32 章 雨夜",
            onBack = onBack,
            onNavigate = onNavigate,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        ReaderControlBottomSheet(
            routeId = RouteIds.READER_CONTROL,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
        )
        ReaderModuleNav(
            routeId = RouteIds.READER_CONTROL,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        )
    }
}

@Composable
private fun FlowShellComparisonRegion(
    onClose: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    ReaderSourceSwitchWindow(
        onClose = onClose,
        onSelect = onSelect,
        modifier = modifier
    )
}

@Composable
private fun ReaderControlReadingSurface(
    context: ReaderContext?,
    fallbackTitle: String,
    onAsyncStateChange: ((requestId: String, state: AsyncResultStateValue, value: Any?) -> Unit)? = null
) {
    if (context != null) {
        val vm: ImmersiveReadingViewModel = viewModel(
            key = "immersive-${context.bookUrl}",
            factory = ImmersiveReadingViewModelFactory(context, onAsyncStateChange)
        )
        val readingState by vm.uiState.collectAsStateWithLifecycle()
        val content by vm.content.collectAsStateWithLifecycle()
        val title = when (val state = readingState) {
            is ReadingUiState.Ready -> state.book.name.ifEmpty { context.bookName.ifEmpty { fallbackTitle } }
            else -> context.bookName.ifEmpty { fallbackTitle }
        }
        ReaderReadingSurface(
            title = title,
            content = content.ifBlank { readerPreviewText(fallbackTitle) }
        )
    } else {
        ReaderReadingSurface(
            title = "雨夜",
            content = readerPreviewText(fallbackTitle)
        )
    }
}

@Composable
private fun ReaderShellImmersiveInfoLayer(title: String, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${title.ifEmpty { "长夜余火" }} · 第 32 章 雨夜",
                style = ReaderTextStyles.infoLayer,
                color = extra.infoLayer,
                maxLines = 1
            )
            Text("10:18", style = ReaderTextStyles.infoLayer, color = extra.infoLayer)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("38%", style = ReaderTextStyles.infoLayer, color = extra.infoLayer)
            Text("第 1 / 3 页", style = ReaderTextStyles.infoLayer, color = extra.infoLayer)
        }
    }
}

@Composable
private fun ReaderTextSelectionLayer(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Box(modifier = modifier) {
        // backdrop: transparent per demo .fd-reader-selection-backdrop (background: transparent)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(onClick = onClose)
        )
        // selection range: 84dp-tall region with 2 selection lines + 2 handles
        // per demo .fd-reader-selection-range (height: 84px, pointer-events: none)
        ReaderSelectionRange(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 258.dp)
                .fillMaxWidth()
                .padding(start = 74.dp, end = 48.dp)
        )
        // toolbar: dark bg + 4-column grid + text-only buttons + triangle arrow
        // per demo .fd-reader-selection-toolbar
        ReaderSelectionToolbar(
            actions = listOf("复制", "划线", "笔记", "搜索"),
            onAction = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 78.dp, top = 208.dp)
        )
    }
}

/**
 * Selection toolbar — 4-column grid, text-only buttons, triangle arrow.
 * Demo contract: `display: grid; grid-template-columns: repeat(4, minmax(0,1fr))`;
 * `gap: 2px; min-width: 224px; height: 34px; padding: 3px`;
 * `border: 1px solid --reader-selection-toolbar-line; border-radius: --fd-radius-lg`;
 * `background: --reader-selection-toolbar; box-shadow: 0 10px 24px rgba(31,27,23,0.22)`.
 * Triangle `::after`: 10x10 rotate(45deg), bottom-right borders, bg = toolbar bg.
 * Button: `padding: 0 8px; color: --reader-selection-toolbar-text; font: 11px/700`.
 */
@Composable
private fun ReaderSelectionToolbar(
    actions: List<String>,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Box(modifier = modifier) {
        // Toolbar body: 4-column grid via Row with weighted children (Compose equivalent).
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 224.dp)
                .height(34.dp)
                .shadow(elevation = ReaderElevations.selectionToolbarShadow, shape = ReaderShapes.lg, clip = false)
                .background(color = extra.selectionToolbar, shape = ReaderShapes.lg)
                .border(width = 1.dp, color = extra.selectionToolbarLine, shape = ReaderShapes.lg)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { label ->
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight(700)
                    ),
                    color = extra.selectionToolbarText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp)
                )
            }
        }
        // Triangle arrow ::after — 10x10 Box rotated 45deg.
        // Demo: only border-right + border-bottom visible on the rotated square.
        // Compose cannot border individual sides of a Box easily, so we draw two thin lines
        // (right + bottom) using nested Boxes overlaid on the rotated square.
        Box(
            modifier = Modifier
                .offset(x = 72.dp, y = 29.dp)
                .size(10.dp)
                .rotate(45f)
        ) {
            // Background fill (covers toolbar bg color, no full border).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = extra.selectionToolbar)
            )
            // Right edge line (border-right equivalent).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(color = extra.selectionToolbarLine)
            )
            // Bottom edge line (border-bottom equivalent).
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(color = extra.selectionToolbarLine)
            )
        }
    }
}

/**
 * Selection range — 84dp-tall region with 2 selection lines and 2 handles.
 * Demo contract: `.fd-reader-selection-range { height: 84px; pointer-events: none }`.
 * Line 1 (is-first): left: 28px, right: 16px, top: 4px, height: 24px, sm radius,
 *   bg = --reader-selection-fill, inset 1px line + inset -1px line.
 * Line 2 (is-second): left: 0, right: 76px, top: 42px, height: 24px.
 * Handle (14x14, 2px border, circle, bg = --reader-selection-handle,
 *   border = --reader-selection-handle-border, ::after 2x18px line).
 *   Start handle: left: 20px, top: -5px. End handle: right: 68px, top: 60px.
 */
@Composable
private fun ReaderSelectionRange(
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // 84dp region; pointer-events: none in demo — we keep it non-interactive.
    // We render lines as width-constrained Boxes (not padding) so background clips correctly.
    Box(modifier = modifier.height(84.dp)) {
        // Line 1 (is-first): top 4dp, start 28dp, end 16dp from parent right, 24dp tall, sm radius.
        // Parent fillMaxWidth; we constrain this Box to weight=1f and add a 16dp spacer to the right.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 28.dp, y = 4.dp)
                .fillMaxWidth()
                .padding(end = 16.dp)
                .height(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = extra.selectionFill, shape = ReaderShapes.sm)
                    .border(width = 1.dp, color = extra.selectionLine, shape = ReaderShapes.sm)
            )
        }
        // Line 2 (is-second): top 42dp, start 0, end 76px, 24dp tall, sm radius.
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 0.dp, y = 42.dp)
                .fillMaxWidth()
                .padding(end = 76.dp)
                .height(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = extra.selectionFill, shape = ReaderShapes.sm)
                    .border(width = 1.dp, color = extra.selectionLine, shape = ReaderShapes.sm)
            )
        }
        // Start handle (is-start): left 20dp, top -5dp (relative to range), 14x14 circle,
        // 2px border, bg = --reader-selection-handle, ::after 2x18px line below.
        ReaderSelectionHandle(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = (-5).dp)
        )
        // End handle (is-end): right 68dp, top 60dp (relative to range)
        ReaderSelectionHandle(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-68).dp, y = 60.dp)
        )
    }
}

@Composable
private fun ReaderSelectionHandle(
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Box(modifier = modifier) {
        // 14x14 circle, 2px border, bg = --reader-selection-handle, soft shadow.
        Box(
            modifier = Modifier
                .size(14.dp)
                .shadow(elevation = ReaderElevations.handleShadow, shape = ReaderShapes.circle, clip = false)
                .background(color = extra.selectionHandle, shape = ReaderShapes.circle)
                .border(
                    width = 2.dp,
                    color = extra.selectionHandleBorder,
                    shape = ReaderShapes.circle
                )
        )
        // ::after — 2x18px line below handle.
        // Demo: left:50%, translateX(-50%) → centered on 14dp handle = x=7dp (14/2 - 2/2 = 6, but
        // demo uses left:50% on a 2px element = centered at 7dp). We use x=7dp to align center.
        Box(
            modifier = Modifier
                .offset(x = 6.dp, y = 11.dp)
                .size(width = 2.dp, height = 18.dp)
                .background(color = extra.selectionHandle, shape = ReaderShapes.pill)
        )
    }
}

@Composable
private fun ReaderGlobalBrightnessDim(
    dimAlpha: Float = 0f,
    modifier: Modifier = Modifier
) {
    // readerStateHost slot: global brightness dim overlay (non-interactive)
    // dim alpha mirrors demo --reader-brightness-dim (0 by default; capped at 0.32)
    val capped = dimAlpha.coerceIn(0f, 0.32f)
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = capped))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderOpenControlTapZone(
    onOpenControls: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight()
        )
        Box(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = onOpenControls,
                    onLongClick = onLongPress
                )
        )
        Spacer(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ReaderControlDismissTapZone(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // fd-reader-dismiss-zone: single transparent rectangle covering the dismiss area
    Box(
        modifier = modifier
            .clickable(onClick = onDismiss)
    )
}

@Composable
private fun ReaderControlTopOverlay(
    title: String,
    sourceLine: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 18.dp, start = 14.dp, end = 14.dp)
            .defaultMinSize(minHeight = 54.dp)
            .shadow(elevation = 10.dp, shape = ReaderShapes.xl, clip = false)
            .background(color = extra.controlSurface, shape = ReaderShapes.xl)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.xl)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReaderControlIconButton(
            iconRes = R.drawable.reader_ic_chevron_left,
            contentDescription = "返回",
            onClick = onBack
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = ReaderTextStyles.readerTopTitle,
                color = readerExtraColors().controlInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sourceLine,
                style = ReaderTextStyles.infoLayer,
                color = extra.controlMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        ReaderControlTextButton(
            iconRes = R.drawable.reader_ic_source_stack,
            label = "换源",
            onClick = { onNavigate(RouteIds.SOURCE_SWITCH) }
        )
        // Demo .fd-reader-top grid col 4 = 34px; button min-height 42px, 22px icon.
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 42.dp)
                .clickable { onNavigate(RouteIds.READER_SETTINGS) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_more),
                contentDescription = "更多",
                tint = extra.controlIcon,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ReaderControlBottomSheet(
    routeId: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // Demo .fd-reader-sheet: height 330, padding 0, border 1px controlLineStrong,
    // radius xl, bg controlSurface, shadow --reader-control-shadow, overflow hidden.
    // Grabber is absolutely positioned (top:9, centered horizontally), so the Column uses
    // padding 0 and relies on children to provide their own insets.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp)
            .shadow(elevation = ReaderElevations.controlShadow, shape = ReaderShapes.xl, clip = false)
            .background(color = extra.controlSurface, shape = ReaderShapes.xl)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.xl)
            .clip(ReaderShapes.xl)
    ) {
        // Grabber: demo .fd-reader-grabber — absolute top:9, centered, 42x4, pill, controlHandle.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 9.dp)
                .width(42.dp)
                .height(4.dp)
                .background(color = extra.controlHandle, shape = ReaderShapes.pill)
                .clickable { onNavigate(readerExpandedRoute(routeId)) }
        )
        // Control main / panel content sits below grabber with horizontal insets.
        // Demo .fd-reader-control-main: left:12, right:calc(12+38+14)=64, top:28, bottom:110.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, start = 12.dp, end = 64.dp, bottom = 110.dp)
        ) {
            when (readerPanelKind(routeId)) {
                "directory" -> ReaderDirectoryPanel(onNavigate)
                "tts" -> ReaderTtsPanel(onNavigate)
                "appearance" -> ReaderAppearancePanel(onNavigate)
                "settings" -> ReaderSettingsPanel(onNavigate)
                "search" -> ReaderSearchPanel(onNavigate)
                "auto-page" -> ReaderAutoPagePanel(onNavigate)
                "replace" -> ReaderReplacePanel(onNavigate)
                else -> ReaderControlMain(onNavigate, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ReaderFullPagePanel(
    kind: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val module = readerModules.firstOrNull { it.kind == kind } ?: readerModules.last()
    val quickRoute = readerQuickRoute(kind)
    ReaderLargePanelFrame(
        iconRes = module.iconRes,
        title = module.label,
        closeLabel = "收起",
        onClose = { onNavigate(quickRoute) },
        modifier = modifier.heightIn(min = 430.dp, max = 642.dp)
    ) {
        when (kind) {
            "directory" -> ReaderFullDirectoryContent(onNavigate)
            "tts" -> ReaderFullTtsContent(onNavigate)
            "appearance" -> ReaderFullAppearanceContent(onNavigate)
            else -> ReaderFullSettingsContent(onNavigate)
        }
    }
}

@Composable
private fun ReaderUtilityPanel(
    kind: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCache = kind == "cache"
    ReaderLargePanelFrame(
        iconRes = if (isCache) R.drawable.reader_ic_storage else R.drawable.reader_ic_bug,
        title = if (isCache) "书籍缓存" else "调试信息",
        closeLabel = "完成",
        onClose = { onNavigate(RouteIds.READER_CONTROL) },
        modifier = modifier.heightIn(min = 360.dp, max = 610.dp)
    ) {
        if (isCache) {
            ReaderBookCacheContent(onNavigate)
        } else {
            ReaderDebugInfoContent(onNavigate)
        }
    }
}

@Composable
private fun ReaderLargePanelFrame(
    @DrawableRes iconRes: Int,
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 20.dp, shape = ReaderShapes.lg, clip = false)
            .background(color = extra.controlSurfaceSolid, shape = ReaderShapes.lg)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.lg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(54.dp)
                .height(5.dp)
                .background(color = extra.controlHandle, shape = ReaderShapes.pill)
                .clickable(onClick = onClose)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = extra.controlIcon,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = ReaderTextStyles.sectionTitle,
                color = readerExtraColors().controlInk,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ReaderPanelTextAction(closeLabel, onClose)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ReaderFullDirectoryContent(onNavigate: (String) -> Unit) {
    ReaderChoiceGrid(
        options = listOf("目录", "书签"),
        selected = "目录",
        onSelected = {}
    )
    listOf(
        "第 28 章 城市边界" to "已缓存",
        "第 29 章 来信" to "书签",
        "第 30 章 旧车票" to "已缓存",
        "第 31 章 雨声" to "已缓存",
        "第 32 章 雨夜" to "当前",
        "第 33 章 白光" to "未读",
        "第 34 章 归途" to "未读"
    ).forEach { (title, marker) ->
        ReaderFullChapterRow(title = title, marker = marker) {
            onNavigate(RouteIds.IMMERSIVE_READING)
        }
    }
}

@Composable
private fun ReaderFullTtsContent(onNavigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ReaderIconOnlyAction(R.drawable.reader_ic_chevron_left, "上一句", {}, Modifier.weight(1f))
        ReaderIconOnlyAction(R.drawable.reader_ic_play, "开始朗读", {}, Modifier.weight(1f))
        ReaderIconOnlyAction(R.drawable.reader_ic_chevron, "下一句", {}, Modifier.weight(1f))
    }
    ReaderFullSettingBlock(title = "语速", meta = "1.0x") {
        ReaderChoiceGrid(listOf("0.8x", "1.0x", "1.2x", "1.5x"), "1.0x") {}
    }
    ReaderFullSettingBlock(title = "音色", meta = "系统女声") {
        ReaderChoiceGrid(listOf("系统女声", "系统男声", "本地引擎"), "系统女声") {}
    }
    ReaderFullSettingBlock(title = "朗读范围", meta = "当前章节") {
        ReaderChoiceGrid(listOf("当前句", "当前章节", "直到停止", "本书剩余"), "当前章节") {}
    }
    ReaderFullSettingBlock(title = "定时关闭", meta = "关闭") {
        ReaderChoiceGrid(listOf("关闭", "15 分钟", "30 分钟", "本章结束"), "关闭") {}
    }
    ReaderPanelRow(R.drawable.reader_ic_tts, "进入朗读详情", "保留当前 ReaderContext", "打开") {
        onNavigate(RouteIds.READER_TTS)
    }
}

@Composable
private fun ReaderFullAppearanceContent(onNavigate: (String) -> Unit) {
    ReaderFullSettingBlock(title = "阅读主题") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(Color(0xFFFFF7EC), Color(0xFFF2E7D5), Color(0xFF1F1B17), Color(0xFFEAF0E2)).forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(color = color, shape = ReaderShapes.md)
                        .border(width = 1.dp, color = readerExtraColors().controlLineStrong, shape = ReaderShapes.md)
                        .clickable { onNavigate(RouteIds.READER_APPEARANCE) }
                )
            }
        }
    }
    ReaderFullSettingBlock(title = "文字排版") {
        ReaderPanelRow(R.drawable.reader_ic_appearance, "字号", "18", "+") {}
        ReaderPanelRow(R.drawable.reader_ic_text, "行距", "1.96", "+") {}
        ReaderFontChoiceRow(onNavigate = onNavigate)
    }
    ReaderFullSettingBlock(title = "页面空间", meta = "边距 / 缩进") {
        ReaderPanelRow(R.drawable.reader_ic_text, "页边距", "默认", "调整") {}
        ReaderPanelRow(R.drawable.reader_ic_text, "段落缩进", "2 字符", "调整") {}
        ReaderPanelRow(R.drawable.reader_ic_text, "自定义字体", "2 个已导入", "管理") {
            onNavigate(RouteIds.READER_APPEARANCE)
        }
        ReaderPanelRow(R.drawable.reader_ic_palette, "自定义背景", "纯色 / 本地图片", "管理") {
            onNavigate(RouteIds.READER_APPEARANCE)
        }
    }
}

@Composable
private fun ReaderFullSettingsContent(onNavigate: (String) -> Unit) {
    ReaderFullSettingBlock(title = "点击翻页方式", meta = "左右区域") {
        ReaderChoiceGrid(listOf("左右区域", "上下区域", "全屏滚动", "禁用点击"), "左右区域") {}
    }
    ReaderFullSettingBlock(title = "翻页动画", meta = "覆盖") {
        ReaderChoiceGrid(listOf("覆盖", "仿真", "滑动", "无动画"), "覆盖") {}
    }
    ReaderFullSettingBlock(title = "阅读行为", meta = "开关项") {
        listOf(
            Triple(R.drawable.reader_ic_auto_page, "自动翻页", false),
            Triple(R.drawable.reader_ic_volume, "音量键翻页", true),
            Triple(R.drawable.reader_ic_phone, "横屏锁定", false),
            Triple(R.drawable.reader_ic_sun, "屏幕常亮", true),
            Triple(R.drawable.reader_ic_progress, "页脚进度信息", true),
            Triple(R.drawable.reader_ic_gesture, "触摸反馈", true),
            Triple(R.drawable.reader_ic_download, "自动缓存后续章节", false)
        ).forEach { (icon, label, enabled) ->
            ReaderFullToggleRow(iconRes = icon, title = label, enabled = enabled)
        }
    }
    ReaderPanelRow(R.drawable.reader_ic_settings, "更多阅读设置", "仍在 ReaderShell 内", "打开") {
        onNavigate(RouteIds.READER_SETTINGS)
    }
}

@Composable
private fun ReaderBookCacheContent(onNavigate: (String) -> Unit) {
    // summary: 3 articles (cached count, cache size, auto-cache status)
    ReaderUtilitySummary(
        items = listOf(
            "31/36" to "已缓存章节",
            "128 MB" to "当前书籍缓存",
            "未开启" to "自动缓存后续章节"
        )
    )
    // cache actions block: 4 action buttons
    ReaderFullSettingBlock(title = "缓存动作", meta = "只作用于当前书籍") {
        ReaderUtilityActionGrid(
            actions = listOf(
                ReaderUtilityAction(R.drawable.reader_ic_download, "缓存当前章节", "第 32 章 雨夜"),
                ReaderUtilityAction(R.drawable.reader_ic_refresh, "缓存后续章节", "从当前章节继续 20 章"),
                ReaderUtilityAction(R.drawable.reader_ic_directory, "更新缓存目录", "刷新章节列表和缓存标记"),
                ReaderUtilityAction(R.drawable.reader_ic_trash, "清理本书缓存", "保留阅读进度和书签", danger = true)
            )
        )
    }
    // chapter cache list: per-row cache/remove buttons
    ReaderFullSettingBlock(title = "章节缓存", meta = "右侧固定显示缓存状态和操作") {
        listOf(
            Triple("第 30 章 旧车票", true, false),
            Triple("第 31 章 雨声", true, false),
            Triple("第 32 章 雨夜", true, true),
            Triple("第 33 章 白光", false, false),
            Triple("第 34 章 归途", false, false)
        ).forEach { (title, cached, isCurrent) ->
            ReaderCacheListRow(
                title = title,
                cached = cached,
                isCurrent = isCurrent,
                onClick = { onNavigate(RouteIds.IMMERSIVE_READING) }
            )
        }
    }
}

@Composable
private fun ReaderDebugInfoContent(onNavigate: (String) -> Unit) {
    // summary: 3 articles (current page, current chapter, current errors)
    ReaderUtilitySummary(
        items = listOf(
            "1/3" to "当前页",
            "32/36" to "当前章节",
            "0" to "当前错误"
        )
    )
    // render state grid: 6 rows
    ReaderFullSettingBlock(title = "渲染状态", meta = "用于核对页面结构和正文渲染链路") {
        ReaderDebugStateGrid(
            rows = listOf(
                "当前路由" to "reader / immersive-reading",
                "当前章节" to "32/36 · 第 32 章 雨夜",
                "分页状态" to "1/3 · 流式测量分页",
                "正文排版" to "字号 18px · 行距 1.6 · 段距 14px",
                "阅读主题" to "纸质浅色 · default",
                "书源" to "优书网 · 128ms"
            )
        )
    }
    // debug logs: 4 entries
    ReaderFullSettingBlock(title = "调试日志", meta = "展示当前阅读链路关键节点") {
        ReaderDebugLogRow("正文渲染", "page-measure", "完成", "按容器高度切分分页，无重复章节名")
        ReaderDebugLogRow("章节缓存", "cache-status", "可用", "当前章节未缓存，前序章节已缓存")
        ReaderDebugLogRow("换源窗口", "source-switch", "完成", "候选书源按延迟升序排列")
        ReaderDebugLogRow("控制层", "overlay-state", "完成", "顶栏、快捷窗、亮度条保持同层结构")
    }
    // debug actions: 4 action buttons
    ReaderFullSettingBlock(title = "调试动作", meta = "不改变正文，只导出或刷新诊断信息") {
        ReaderUtilityActionGrid(
            actions = listOf(
                ReaderUtilityAction(R.drawable.reader_ic_copy, "复制调试信息", "复制当前路由、章节、分页和排版参数"),
                ReaderUtilityAction(R.drawable.reader_ic_log, "导出阅读日志", "生成当前书籍的调试记录"),
                ReaderUtilityAction(R.drawable.reader_ic_refresh, "重新测量分页", "刷新正文容器和分页结果"),
                ReaderUtilityAction(R.drawable.reader_ic_source_switch, "检查书源状态", "查看当前书源请求与解析结果")
            )
        )
    }
}

@Composable
private fun ReaderUtilitySummary(items: List<Pair<String, String>>) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (value, label) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = value,
                    style = ReaderTextStyles.sectionTitle,
                    color = readerExtraColors().controlInk
                )
                Text(
                    text = label,
                    style = ReaderTextStyles.infoLayer,
                    color = extra.controlMuted
                )
            }
        }
    }
}

private data class ReaderUtilityAction(
    @DrawableRes val iconRes: Int,
    val title: String,
    val meta: String,
    val danger: Boolean = false
)

@Composable
private fun ReaderUtilityActionGrid(actions: List<ReaderUtilityAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { action ->
                    ReaderUtilityActionButton(action, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReaderUtilityActionButton(action: ReaderUtilityAction, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .background(
                color = if (action.danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else readerExtraColors().controlPanel,
                shape = ReaderShapes.md
            )
            .border(
                width = 1.dp,
                color = if (action.danger) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else extra.controlLineStrong,
                shape = ReaderShapes.md
            )
            .clickable {}
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = action.iconRes),
            contentDescription = null,
            tint = if (action.danger) MaterialTheme.colorScheme.error else extra.controlInk,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = action.title,
                style = ReaderTextStyles.continueAction,
                color = if (action.danger) MaterialTheme.colorScheme.error else readerExtraColors().controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = action.meta,
                style = ReaderTextStyles.infoLayer,
                color = extra.controlMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReaderCacheListRow(
    title: String,
    cached: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .background(
                color = if (isCurrent) readerExtraColors().controlActiveBg else readerExtraColors().controlPanel,
                shape = ReaderShapes.md
            )
            .border(
                width = 1.dp,
                color = if (isCurrent) readerExtraColors().controlActiveStrong else extra.controlLineStrong,
                shape = ReaderShapes.md
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = ReaderTextStyles.continueAction,
                color = readerExtraColors().controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    isCurrent -> "当前章节"
                    cached -> "已下载到本地"
                    else -> "尚未缓存"
                },
                style = ReaderTextStyles.infoLayer,
                color = extra.controlMuted,
                maxLines = 1
            )
        }
        Icon(
            painter = painterResource(id = if (cached) R.drawable.reader_ic_check else R.drawable.reader_ic_download),
            contentDescription = null,
            tint = if (cached) readerExtraColors().controlPrimary else extra.controlMuted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = if (cached) "移除" else "缓存",
            style = ReaderTextStyles.tabLabel,
            color = readerExtraColors().controlPrimary,
            maxLines = 1,
            modifier = Modifier.clickable {}
        )
    }
}

@Composable
private fun ReaderDebugStateGrid(rows: List<Pair<String, String>>) {
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        rows.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    ReaderDebugStateItem(label, value, extra, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReaderDebugStateItem(
    label: String,
    value: String,
    extra: com.reader.ui.theme.ReaderExtraColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(color = readerExtraColors().controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = ReaderTextStyles.infoLayer,
            color = extra.controlMuted
        )
        Text(
            text = value,
            style = ReaderTextStyles.continueAction,
            color = readerExtraColors().controlInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderDebugLogRow(scope: String, code: String, state: String, message: String) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .background(color = readerExtraColors().controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = scope,
                style = ReaderTextStyles.continueAction,
                color = readerExtraColors().controlInk,
                maxLines = 1
            )
            Text(
                text = "$code · $message",
                style = ReaderTextStyles.infoLayer,
                color = extra.controlMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = state,
            style = ReaderTextStyles.tabLabel,
            color = readerExtraColors().controlPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun ReaderFullSettingBlock(
    title: String,
    meta: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = readerExtraColors().controlPanelSoft, shape = ReaderShapes.md)
            .border(width = 1.dp, color = readerExtraColors().controlLineStrong, shape = ReaderShapes.md)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = ReaderTextStyles.continueAction,
                color = readerExtraColors().controlInk,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = ReaderTextStyles.infoLayer,
                    color = readerExtraColors().controlMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        content()
    }
}

@Composable
private fun ReaderChoiceGrid(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val active = option == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 38.dp)
                            .background(
                                color = if (active) readerExtraColors().controlActiveBg else readerExtraColors().controlPanel,
                                shape = ReaderShapes.md
                            )
                            .border(
                                width = 1.dp,
                                color = if (active) readerExtraColors().controlActiveStrong else readerExtraColors().controlLineStrong,
                                shape = ReaderShapes.md
                            )
                            .clickable { onSelected(option) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            style = ReaderTextStyles.tabLabel,
                            color = if (active) readerExtraColors().controlPrimary else readerExtraColors().controlInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReaderFullChapterRow(title: String, marker: String, onClick: () -> Unit) {
    val active = marker == "当前"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .background(
                color = if (active) readerExtraColors().controlActiveBg else readerExtraColors().controlPanel,
                shape = ReaderShapes.md
            )
            .border(
                width = 1.dp,
                color = if (active) readerExtraColors().controlActiveStrong else readerExtraColors().controlLineStrong,
                shape = ReaderShapes.md
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = readerExtraColors().controlInk,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = marker,
            style = ReaderTextStyles.tabLabel,
            color = if (active) readerExtraColors().controlPrimary else readerExtraColors().controlMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun ReaderFullToggleRow(
    @DrawableRes iconRes: Int,
    title: String,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().controlIcon,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = readerExtraColors().controlInk,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ReaderSwitchPill(enabled = enabled)
    }
}

@Composable
private fun ReaderSwitchPill(enabled: Boolean) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(24.dp)
            .background(
                color = if (enabled) extra.controlPrimary else extra.controlDisabledBg,
                shape = ReaderShapes.pill
            )
            .padding(3.dp),
        contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(extra.controlSurfaceSolid, ReaderShapes.pill)
        )
    }
}

@Composable
private fun ReaderPanelTextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = ReaderTextStyles.tabLabel,
        color = readerExtraColors().controlPrimary,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun ReaderControlMain(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    // fd-reader-control-main: grid with 2 rows (0.82fr / 1.18fr), gap 6px
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: fd-reader-actions — container with bg + border + padding(9px 8px), 3-col grid, gap 8px
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.82f)
                .defaultMinSize(minHeight = 70.dp)
                .background(color = extra.controlPanel, shape = ReaderShapes.md)
                .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
                .padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderQuickAction(
                iconRes = R.drawable.reader_ic_search,
                label = "搜索",
                onClick = { onNavigate(RouteIds.READER_CONTENT_SEARCH) },
                modifier = Modifier.weight(1f)
            )
            ReaderQuickAction(
                iconRes = R.drawable.reader_ic_auto_page,
                label = "自动翻页",
                onClick = { onNavigate(RouteIds.READER_AUTO_PAGE) },
                modifier = Modifier.weight(1f)
            )
            ReaderQuickAction(
                iconRes = R.drawable.reader_ic_replace,
                label = "替换",
                onClick = { onNavigate(RouteIds.READER_CONTENT_REPLACEMENT) },
                modifier = Modifier.weight(1f)
            )
        }
        // Row 2: fd-reader-chapter-panel — weight 1.18fr, min 96px
        ReaderChapterPanel(modifier = Modifier.weight(1.18f).defaultMinSize(minHeight = 96.dp))
    }
}

@Composable
private fun ReaderChapterPanel(modifier: Modifier = Modifier) {
    val extra = readerExtraColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(color = extra.controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReaderRoundStep(iconRes = R.drawable.reader_ic_chevron_left, contentDescription = "上一章")
            Text(
                text = "第 32 章 雨夜",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight(750)
                ),
                color = readerExtraColors().controlInk,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            ReaderRoundStep(iconRes = R.drawable.reader_ic_chevron, contentDescription = "下一章")
        }
        // fd-reader-progress: 30dp tall container + 12x12 drag handle + 9sp labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "38%",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.controlMuted
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .background(color = Color.Transparent, shape = ReaderShapes.pill)
            ) {
                // track: 5dp tall, centered vertically
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .align(Alignment.Center)
                        .background(color = extra.controlLine, shape = ReaderShapes.pill)
                )
                // active fill (38%): 5dp tall, centered vertically
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(5.dp)
                        .align(Alignment.CenterStart)
                        .background(color = extra.controlPrimary, shape = ReaderShapes.pill)
                )
                // 12x12 drag handle centered on the 38% position
                // Wrapper at 38% width places its CenterEnd (offset +6dp) on the 38% line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .align(Alignment.CenterStart),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp)
                            .size(12.dp)
                            .background(color = extra.controlPrimary, shape = ReaderShapes.circle)
                            .border(1.dp, extra.controlPrimaryText, ReaderShapes.circle)
                    )
                }
            }
            Text(
                "共 36 章",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight(500)
                ),
                color = extra.controlMuted
            )
        }
    }
}

@Composable
private fun ReaderModuleNav(
    routeId: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 78.dp)
            .background(color = extra.controlSurfaceSolid, shape = ReaderShapes.lg)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.lg)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        readerModules.forEach { item ->
            ReaderModuleButton(
                item = item,
                active = readerPanelKind(routeId) == item.kind,
                onNavigate = onNavigate,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ReaderModuleButton(
    item: ReaderModuleItem,
    active: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // fd-reader-module: grid 42px/16px rows, gap 4px; active only colors the span (circle)
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 62.dp)
            .clickable { onNavigate(if (active) RouteIds.READER_CONTROL else item.routeId) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // fd-reader-module span: 42x42 circular icon container
        // inactive: bg=controlActiveSoft, icon=controlAction
        // active: bg=primaryDark, icon=controlPrimaryText
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (active) extra.primaryDark else extra.controlActiveSoft,
                    shape = ReaderShapes.circle
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.label,
                tint = if (active) extra.controlPrimaryText else extra.controlAction,
                modifier = Modifier.size(24.dp)
            )
        }
        // fd-reader-module small: 12sp/800 label
        Text(
            text = item.label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight(800)
            ),
            color = if (active) extra.controlAction else extra.controlIcon,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderBrightnessRail(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // Demo .fd-brightness-rail: width 38, pill radius, bg controlPanel, padding 12px 0, gap 10px.
    // Bar (i): width 8px, height 92px. Bar fill (b): background --fd-primary (#366179).
    // Auto-toggle: 24x20 box at bottom.
    // brightness 0..0.32 maps to fill 0..1 (top = brightest, bottom = dimmest)
    val fillFraction = (brightness / 0.32f).coerceIn(0f, 1f)
    val primaryColor = MaterialTheme.colorScheme.primary // --fd-primary #366179
    Column(
        modifier = modifier
            .width(38.dp)
            .height(178.dp)
            .background(color = extra.controlPanel, shape = ReaderShapes.pill)
            .padding(vertical = 12.dp, horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bar track: 8x92, controlLine bg, with drag gesture.
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(92.dp)
                .background(color = extra.controlLine, shape = ReaderShapes.pill)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val railHeightPx = size.height.toFloat().coerceAtLeast(1f)
                        // dragging up increases brightness (fill grows from bottom)
                        val delta = -dragAmount / railHeightPx
                        val newFill = (fillFraction + delta).coerceIn(0f, 1f)
                        onBrightnessChange(newFill * 0.32f)
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Bar fill: 8dp wide, height = fraction of 92dp, --fd-primary bg.
            Box(
                modifier = Modifier
                    .fillMaxHeight(fillFraction)
                    .width(8.dp)
                    .background(color = primaryColor, shape = ReaderShapes.pill)
            )
        }
        // Auto-toggle: demo 24x20, controlActiveBg, "A" label.
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 20.dp)
                .background(color = extra.controlActiveBg, shape = ReaderShapes.pill),
            contentAlignment = Alignment.Center
        ) {
            Text("A", style = ReaderTextStyles.tabLabel, color = primaryColor)
        }
    }
}

@Composable
private fun ReaderDirectoryPanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("目录")
    listOf("第 30 章 旧车票", "第 31 章 雨声", "第 32 章 雨夜").forEach { title ->
        ReaderChapterNameRow(title) {
            onNavigate(RouteIds.IMMERSIVE_READING)
        }
    }
}

@Composable
private fun ReaderTtsPanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("朗读")
    ReaderPanelRow(R.drawable.reader_ic_tts, "系统女声", "1.0x · 当前句", null) {}
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        ReaderIconOnlyAction(R.drawable.reader_ic_pause, "暂停朗读", { onNavigate(RouteIds.READER_TTS) }, Modifier.weight(1f))
        ReaderIconOnlyAction(R.drawable.reader_ic_play, "播放朗读", { onNavigate(RouteIds.READER_TTS) }, Modifier.weight(1f))
        ReaderIconOnlyAction(R.drawable.reader_ic_more, "停止朗读", { onNavigate(RouteIds.READER_CONTROL) }, Modifier.weight(1f))
    }
}

@Composable
private fun ReaderAppearancePanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("界面")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(Color(0xFFFFF7EC), Color(0xFFF2E7D5), Color(0xFF1F1B17), Color(0xFFEAF0E2)).forEach { color ->
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(21.dp)
                    .background(color = color, shape = ReaderShapes.sm)
                    .border(width = 1.dp, color = readerExtraColors().controlLineStrong, shape = ReaderShapes.sm)
                    .clickable { onNavigate(RouteIds.READER_APPEARANCE) }
            )
        }
    }
    ReaderPanelRow(R.drawable.reader_ic_appearance, "字号", "18", "+") {}
    ReaderPanelRow(R.drawable.reader_ic_text, "行距", "1.96", "+") {}
    ReaderFontChoiceRow(onNavigate = onNavigate)
    ReaderPanelRow(R.drawable.reader_ic_text, "自定义字体", "2 个已导入", "管理") {
        onNavigate(RouteIds.READER_APPEARANCE)
    }
    ReaderPanelRow(R.drawable.reader_ic_palette, "自定义背景", "纯色 / 本地图片", "管理") {
        onNavigate(RouteIds.READER_APPEARANCE)
    }
}

@Composable
private fun ReaderSettingsPanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("设置")
    ReaderPanelRow(R.drawable.reader_ic_auto_page, "自动翻页", "关闭", "开启") {
        onNavigate(RouteIds.READER_AUTO_PAGE)
    }
    ReaderPanelRow(R.drawable.reader_ic_replace, "内容替换", "4 条规则", "管理") {
        onNavigate(RouteIds.READER_CONTENT_REPLACEMENT)
    }
    ReaderPanelRow(R.drawable.reader_ic_download, "本书缓存", "31/36 章", "缓存") {
        onNavigate(RouteIds.READER_BOOK_CACHE)
    }
}

@Composable
private fun ReaderSearchPanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("内容搜索")
    ReaderPanelRow(R.drawable.reader_ic_search, "雨夜", "第 32 章 · 4 处结果", "定位") {
        onNavigate(RouteIds.IMMERSIVE_READING)
    }
}

@Composable
private fun ReaderAutoPagePanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("自动翻页")
    ReaderPanelRow(R.drawable.reader_ic_auto_page, "自动翻页", "8 秒 · 运行中", "暂停") {
        onNavigate(RouteIds.READER_AUTO_PAGE)
    }
    ReaderPanelRow(R.drawable.reader_ic_clock, "倒计时", "8", "调整") {}
}

@Composable
private fun ReaderReplacePanel(onNavigate: (String) -> Unit) {
    ReaderPanelTitle("替换")
    listOf("雨容称呼", "旧称统一", "标点清理").forEach { label ->
        ReaderPanelRow(R.drawable.reader_ic_replace, label, "已启用", "开启") {
            onNavigate(RouteIds.READER_CONTENT_REPLACEMENT)
        }
    }
}

@Composable
private fun ReaderPanelTitle(text: String) {
    Text(
        text = text,
        style = ReaderTextStyles.sectionTitle,
        color = readerExtraColors().controlInk,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReaderChapterNameRow(title: String, onClick: () -> Unit) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .background(color = readerExtraColors().controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = readerExtraColors().controlInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ReaderFontChoiceRow(onNavigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("系统", "宋体", "黑体").forEachIndexed { index, label ->
            val active = index == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 38.dp)
                    .background(
                        color = if (active) readerExtraColors().controlActiveBg else readerExtraColors().controlPanel,
                        shape = ReaderShapes.md
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) readerExtraColors().controlActiveStrong else readerExtraColors().controlLineStrong,
                        shape = ReaderShapes.md
                    )
                    .clickable { onNavigate(RouteIds.READER_APPEARANCE) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = ReaderTextStyles.continueAction,
                    color = if (active) readerExtraColors().controlPrimary else readerExtraColors().controlInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReaderPanelRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    trailing: String?,
    onClick: () -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .background(color = readerExtraColors().controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = extra.controlIcon,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = ReaderTextStyles.continueAction,
                color = readerExtraColors().controlInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = ReaderTextStyles.infoLayer,
                color = extra.controlMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Text(trailing, style = ReaderTextStyles.tabLabel, color = readerExtraColors().controlPrimary)
        }
    }
}

@Composable
private fun ReaderIconOnlyAction(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .background(color = readerExtraColors().controlPanel, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = extra.controlIcon,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ReaderSourceSwitchWindow(
    onClose: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Demo .fd-source-phone-flow .fd-source-switch-window: padding 7 9, border 1px
    // rgba(155,132,102,0.24), radius xl, bg #fffcf8, no shadow, overflow auto, gap 0, height 100%.
    val windowBg = Color(0xFFFFFCF8)
    val windowBorder = Color(0xFF9B8466).copy(alpha = 0.24f)
    val headerBorder = Color(0xFF9B8466).copy(alpha = 0.18f)
    val headerColor = Color(0xFF332C25)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = windowBg, shape = ReaderShapes.xl)
            .border(width = 1.dp, color = windowBorder, shape = ReaderShapes.xl)
            .clip(ReaderShapes.xl)
            .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // .fd-source-window-info: grid 20/1fr/auto/24, gap 8, padding 0 6 0 9,
        // border-bottom rgba(155,132,102,0.18), min-height 32, color #332c25.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp)
                .drawBehind {
                    drawLine(
                        color = headerBorder,
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(start = 9.dp, end = 6.dp, top = 0.dp, bottom = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // col 1: icon 20x20
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_source_switch),
                contentDescription = null,
                tint = headerColor,
                modifier = Modifier.size(20.dp)
            )
            // col 2: title "换源" (weight 1f)
            Text(
                text = "换源",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight(700)
                ),
                color = headerColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // col 3: "按延迟排序" (auto)
            Text(
                text = "按延迟排序",
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight(400)
                ),
                color = headerColor
            )
            // col 4: close button 24x24
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(ReaderShapes.md)
                    .background(Color(0xFFF0E9E2))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_close),
                    contentDescription = "关闭换源",
                    tint = headerColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        // .fd-source-candidate-list: grid, gap 0.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            sourceSwitchCandidates.forEachIndexed { index, candidate ->
                val isCurrent = candidate.state == "当前"
                val isSwitchable = !isCurrent && candidate.state != "落后" && candidate.state != "失效"
                SourceSwitchCandidateRow(
                    candidate = candidate,
                    isCurrent = isCurrent,
                    isSwitchable = isSwitchable,
                    showTopDivider = index > 0,
                    onClick = onSelect
                )
            }
        }
    }
}

private data class SourceSwitchCandidate(
    val source: String,
    val state: String,
    val speed: String,
    val latestChapter: String
)

// Demo fixture (fixture.js:349-470) — 11 candidates, state vocabulary: 当前 / 可切换 / 落后
private val sourceSwitchCandidates = listOf(
    SourceSwitchCandidate("优书网", "当前", "120 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("笔趣阁镜像", "可切换", "180 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("轻小说书站", "可切换", "210 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("云端书库", "可切换", "260 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("聚合书源一", "可切换", "320 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("聚合书源二", "可切换", "390 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("备用线路 A", "可切换", "510 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("备用线路 B", "可切换", "680 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("章节同步源", "可切换", "760 ms", "第 32 章 雨夜"),
    SourceSwitchCandidate("本地缓存", "可切换", "离线", "第 32 章 雨夜"),
    SourceSwitchCandidate("旧源备份", "落后", "超时", "第 31 章 归途")
)

/**
 * Source-switch candidate row per demo `sourceCandidateRow` (render-runtime.js:7608-7621).
 * Structure: <article><span class="fd-source-row-main"><b>source</b><em>speed</em><strong>latest</strong></span></article>
 * State expressed via class (is-current / is-selected / is-switchable / is-muted), no trailing text.
 * Phone-flow CSS (06-responsive.css:268-290): single column, min-height 32, no background,
 * border-top only between rows.
 * .fd-source-row-main (06-responsive.css:301-347): grid minmax(82,1fr)/45/minmax(86,1.08fr),
 * gap 7. b=10sp #2b241d w550, em=8sp #8d8378 w450 right, strong=9sp #332c25 w500 right.
 */
@Composable
private fun SourceSwitchCandidateRow(
    candidate: SourceSwitchCandidate,
    isCurrent: Boolean,
    isSwitchable: Boolean,
    showTopDivider: Boolean,
    onClick: () -> Unit
) {
    val rowBorderColor = Color(0xFF9B8466).copy(alpha = 0.18f)
    val selectedBg = Color(0xFF2F6373).copy(alpha = 0.08f)
    val bColor = Color(0xFF2B241D)
    val emColor = Color(0xFF8D8378)
    val strongColor = Color(0xFF332C25)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .then(
                if (showTopDivider) Modifier.drawBehind {
                    drawLine(
                        color = rowBorderColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                } else Modifier
            )
            .background(color = if (isCurrent) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // col 1 (min 82): <b>source</b> — 10sp #2b241d w550
        Text(
            text = candidate.source,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight(550)
            ),
            color = bColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // col 2 (45): <em>speed</em> — 8sp #8d8378 w450 right
        Text(
            text = candidate.speed,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight(450)
            ),
            color = emColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.width(45.dp)
        )
        // col 3 (min 86): <strong>latestChapter</strong> — 9sp #332c25 w500 right
        Text(
            text = candidate.latestChapter,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight(500)
            ),
            color = strongColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.08f)
        )
    }
}

@Composable
private fun ReaderQuickAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extra = readerExtraColors()
    // fd-reader-actions button: bg=controlElevated, no border, 24px icon, 11sp/800 label, gap 6px
    Column(
        modifier = modifier
            .background(color = extra.controlElevated, shape = ReaderShapes.md)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = extra.controlInk,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(800)
            ),
            color = extra.controlInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReaderControlIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = readerExtraColors().controlIcon,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ReaderControlTextButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .defaultMinSize(minWidth = 62.dp, minHeight = 42.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().controlIcon,
            modifier = Modifier.size(18.dp)
        )
        Text(label, style = ReaderTextStyles.tabLabel, color = readerExtraColors().controlInk, maxLines = 1)
    }
}

@Composable
private fun ReaderRoundStep(@DrawableRes iconRes: Int, contentDescription: String) {
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(color = extra.controlElevated, shape = ReaderShapes.pill)
            .border(width = 1.dp, color = extra.controlLineStrong, shape = ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = extra.controlIcon,
            modifier = Modifier.size(18.dp)
        )
    }
}

private data class ReaderModuleItem(
    val kind: String,
    val routeId: String,
    val label: String,
    @DrawableRes val iconRes: Int
)

private val readerModules = listOf(
    ReaderModuleItem("directory", RouteIds.READER_TOC_BOOKMARKS, "目录", R.drawable.reader_ic_nav_list),
    ReaderModuleItem("tts", RouteIds.READER_TTS, "朗读", R.drawable.reader_ic_tts),
    ReaderModuleItem("appearance", RouteIds.READER_APPEARANCE, "界面", R.drawable.reader_ic_appearance),
    ReaderModuleItem("settings", RouteIds.READER_SETTINGS, "设置", R.drawable.reader_ic_settings)
)

private fun readerPanelKind(routeId: String): String = when (routeId) {
    RouteIds.READER_TOC_BOOKMARKS, RouteIds.READER_FULL_DIRECTORY -> "directory"
    RouteIds.READER_TTS, RouteIds.READER_FULL_TTS -> "tts"
    RouteIds.READER_APPEARANCE, RouteIds.READER_FULL_APPEARANCE -> "appearance"
    RouteIds.READER_SETTINGS, RouteIds.READER_FULL_SETTINGS -> "settings"
    RouteIds.READER_CONTENT_SEARCH -> "search"
    RouteIds.READER_AUTO_PAGE -> "auto-page"
    RouteIds.READER_CONTENT_REPLACEMENT -> "replace"
    else -> "dock"
}

private fun readerFullPanelKind(routeId: String): String? = when (routeId) {
    RouteIds.READER_FULL_DIRECTORY -> "directory"
    RouteIds.READER_FULL_TTS -> "tts"
    RouteIds.READER_FULL_APPEARANCE -> "appearance"
    RouteIds.READER_FULL_SETTINGS -> "settings"
    else -> null
}

private fun readerUtilityPanelKind(routeId: String): String? = when (routeId) {
    RouteIds.READER_BOOK_CACHE -> "cache"
    RouteIds.READER_DEBUG_INFO -> "debug"
    else -> null
}

private fun readerQuickRoute(kind: String): String = when (kind) {
    "directory" -> RouteIds.READER_TOC_BOOKMARKS
    "tts" -> RouteIds.READER_TTS
    "appearance" -> RouteIds.READER_APPEARANCE
    "settings" -> RouteIds.READER_SETTINGS
    else -> RouteIds.READER_CONTROL
}

private fun readerExpandedRoute(routeId: String): String = when (readerPanelKind(routeId)) {
    "directory" -> RouteIds.READER_FULL_DIRECTORY
    "tts" -> RouteIds.READER_FULL_TTS
    "appearance" -> RouteIds.READER_FULL_APPEARANCE
    "settings" -> RouteIds.READER_FULL_SETTINGS
    else -> routeId
}

private fun readerPreviewParagraphs(title: String): List<String> = listOf(
    "《$title》的雨声在窗外连成一片，像无数细小的针，密密地刺在玻璃上，汇成一层朦胧的水幕。",
    "他站在窗前，手里握着那封被雨水润湿的信。纸页边角微微卷起，字迹却依旧清晰。",
    "这座城市在夜里显得格外安静，街道尽头偶尔有车灯掠过，又很快被雨幕吞没。",
    "他曾经以为自己已经习惯等待，习惯在没有回音的日子里把所有疑问折起来。"
)

private fun readerPreviewText(title: String): String = readerPreviewParagraphs(title).joinToString(separator = "\n\n")
