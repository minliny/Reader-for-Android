package com.reader.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Intent
import android.net.Uri
import com.reader.android.R
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.WebDavCredential
import com.reader.host.HostReply
import com.reader.host.HostRequest
import com.reader.ui.shell.WebDavSaveStatus
import com.reader.ui.shell.WebDavTestStatus
import org.json.JSONObject
import com.reader.api.Book
import com.reader.api.ReaderCoreClient
import com.reader.ui.book.BookDetailScreen
import com.reader.ui.book.BookDirectoryScreen
import com.reader.ui.book.demoBookDetailRouteState
import com.reader.ui.book.demoBookDirectoryRouteState
import com.reader.ui.book.realBookDetailRouteState
import com.reader.ui.book.realBookDirectoryRouteState
import com.reader.ui.bookshelf.BookBatchManagementScreen
import com.reader.ui.bookshelf.BookshelfScreen
import com.reader.ui.bookshelf.BookshelfTabStateHost
import com.reader.ui.bookshelf.BookshelfTabTopBar
import com.reader.ui.bookshelf.BookshelfViewModel
import com.reader.ui.bookshelf.BookshelfEmptyRouteScreen
import com.reader.ui.bookshelf.BookshelfSortFilterRouteScreen
import com.reader.ui.bookshelf.BookshelfCoverModeRouteScreen
import com.reader.ui.bookshelf.BookshelfListModeRouteScreen
import com.reader.ui.bookshelf.BookshelfBookMoreMenuRouteScreen
import com.reader.ui.bookshelf.BookshelfSearchSettingsScreen
import com.reader.ui.bookshelf.GroupManagementScreen
import com.reader.ui.bookshelf.LocalImportScreen
import com.reader.ui.demo.DemoRouteRegistry
import com.reader.ui.demo.DemoRouteScreen
import com.reader.ui.discover.DiscoverDemoPage
import com.reader.ui.shell.OverlayState
import com.reader.ui.discover.DiscoverDemoRouteIds
import com.reader.ui.discover.DiscoverDemoRouteScreen
import com.reader.ui.discover.DiscoverDemoRouteShell
import com.reader.ui.discover.DiscoverScreen
import com.reader.ui.discover.DiscoverTabState
import com.reader.ui.discover.DiscoverTabTopBar
import com.reader.ui.discover.discoverDemoRouteState
import com.reader.ui.motion.AppMotionTokens
import com.reader.ui.motion.ReducedMotionResolver
import com.reader.ui.motion.ViewportClass
import com.reader.ui.motion.effectiveDuration
import com.reader.ui.motion.rememberViewportClass
import com.reader.ui.reading.FlowShellScreen
import com.reader.ui.reading.ReaderFontSettingsScreen
import com.reader.ui.reading.ReaderThemeSettingsScreen
import com.reader.ui.reading.ReaderThemeEditScreen
import com.reader.ui.reading.ReaderLayoutSettingsScreen
import com.reader.ui.reading.ReaderPageTurnSettingsScreen
import com.reader.ui.reading.ReaderReplaceRuleScreen
import com.reader.ui.source.SourceDetailScreen
import com.reader.ui.source.SourceEditScreen
import com.reader.ui.reading.ReaderSettingsScreen
import com.reader.ui.reading.ReaderShellScreen
import com.reader.ui.rss.RssReadRecordScreen
import com.reader.ui.rss.RssArticleHubScreen
import com.reader.ui.rss.RssDetailScreen
import com.reader.ui.rss.RssRemainingDemoRouteScreen
import com.reader.ui.rss.RssOriginalScreen
import com.reader.ui.rss.RssRefreshingScreen
import com.reader.ui.rss.RssRuleSubscriptionApplyScreen
import com.reader.ui.rss.RssRuleSubscriptionDetailScreen
import com.reader.ui.rss.RssRuleSubscriptionEditScreen
import com.reader.ui.rss.RssRuleSubscriptionScreen
import com.reader.ui.rss.RssRuleSubscriptionTestScreen
import com.reader.ui.rss.RssScreen
import com.reader.ui.rss.RssTabState
import com.reader.ui.rss.RssTabTopBar
import com.reader.ui.rss.RssSearchScreen
import com.reader.ui.rss.RssSourceActionsScreen
import com.reader.ui.rss.RssSourceBatchScreen
import com.reader.ui.rss.RssSourceConfirmScreen
import com.reader.ui.rss.RssSourceDebugScreen
import com.reader.ui.rss.RssSourceEditScreen
import com.reader.ui.rss.RssSourceExportDetailScreen
import com.reader.ui.rss.RssSourceExportScreen
import com.reader.ui.rss.RssSourceImportDetailScreen
import com.reader.ui.rss.RssSourceImportResultScreen
import com.reader.ui.rss.RssSourceImportScreen
import com.reader.ui.rss.RssSourceGroupEditScreen
import com.reader.ui.rss.RssSourceGroupsScreen
import com.reader.ui.rss.RssSourceLoginCookieScreen
import com.reader.ui.rss.RssSourceLoginScreen
import com.reader.ui.rss.RssSourceLoginWebScreen
import com.reader.ui.rss.RssSourceVarsScreen
import com.reader.ui.rss.RssSubscriptionManagementScreen
import com.reader.ui.restore.RestoreRouteIds
import com.reader.ui.restore.RestoreScreen
import com.reader.ui.restore.RestoreUiState
import com.reader.ui.source.ImportBookSourceScreen
import com.reader.ui.source.SourceDemoRouteScreen
import com.reader.ui.search.SearchScreen
import com.reader.ui.settings.AboutFeedbackScreen
import com.reader.ui.settings.SettingsScreen
import com.reader.ui.settings.SettingsTabTopBar
import com.reader.ui.settings.SettingsGeneralScreen
import com.reader.ui.settings.SourceManagementScreen
import com.reader.ui.settings.SyncBackupScreen
import com.reader.ui.settings.WebDavConfigScreen
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Native Compose App Shell. Hosts the four main tabs (`书架 / 发现 / RSS / 设置`) above a
 * floating rounded-pill [FloatingPillTabBar] and renders the current route via
 * [AnimatedContent] driven by the single [AppShellViewModel] state.
 *
 * Contract alignment (FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 1):
 * - Main tabs are exactly [MainTab.ORDER]; search / reader / source-management are NOT tabs.
 * - Tab switch mutates [ReaderUiState.activeTab] only — never a route push.
 * - Bottom bar geometry (count / size / hit area) is stable across switches.
 * - Content area fades 80–120ms (`app.tab.switch`); no horizontal slide, no overshoot.
 * - Reduced motion collapses all transitions to instant (MOTION_EFFECTS.md §8).
 *
 * Layout (mirrors `frontend-demo/styles/01-shell-layout.css`):
 * - TabShell: full-screen content with the floating pill bar bottom-aligned; the bar floats
 *   14dp above the system nav inset with 14dp side margins (handled inside [FloatingPillTabBar]).
 * - ImmersiveReading: full-screen text surface; center tap pushes the `reader` control layer.
 */
@Composable
fun AppShell(
    reducedMotionResolver: ReducedMotionResolver? = null,
    vm: AppShellViewModel = viewModel(
        factory = appShellViewModelFactory(
            reducedMotionResolver,
            // P1-4: bridge TtsSessionController.progressFlow → UpdateTtsProgress
            // so the reducer's activeSession reflects real playback position.
            if (
                com.reader.android.AppProvider.isInitialized &&
                !com.reader.android.BuildConfig.READER_UI_PLAYBACK_PILOT_ENABLED
            ) {
                com.reader.android.AppProvider.ttsSessionController.progressFlow
            } else null
        )
    )
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val bookOpenDomainState by vm.readerBookOpenDomainState.collectAsStateWithLifecycle()
    val playbackDomainState by vm.readerPlaybackDomainState.collectAsStateWithLifecycle()
    val reducedMotion = state.reducedMotion
    var restoreUiState by remember { mutableStateOf(RestoreUiState()) }

    // The auto-page contract is foreground-only. Lifecycle backgrounding is
    // routed directly to the paired Pilot executor; it never becomes a
    // background.schedule HostRequest or a repeating native timer.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm.readerPlaybackPilotEnabled) {
        if (!vm.readerPlaybackPilotEnabled) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.onReaderAppBackgrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.onReaderAppBackgrounded()
        }
    }

    fun popBackTo(route: ReaderRoute, fallbackPops: Int = 1) {
        val targetIndex = state.backStack.indexOfLast { it == route }
        val popCount = if (targetIndex >= 0) {
            (state.backStack.lastIndex - targetIndex).coerceAtLeast(0)
        } else {
            fallbackPops
        }.coerceAtLeast(1)
        repeat(popCount) {
            vm.dispatch(ReaderUiIntent.PopRoute)
        }
    }
    fun enterReaderFromBook(book: Book) {
        vm.dispatch(
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = book.sourceId.ifEmpty { book.origin.ifEmpty { book.bookUrl } },
                bookUrl = book.bookUrl,
                bookName = book.name
            )
        )
    }
    fun navigateTo(route: ReaderRoute) {
        when (route) {
            is ReaderRoute.TabShell -> vm.dispatch(ReaderUiIntent.SelectTab(route.tab))
            else -> vm.dispatch(ReaderUiIntent.PushRoute(route))
        }
    }
    fun navigateToRouteId(routeId: String) {
        navigateTo(DemoRouteRegistry.routeFor(routeId))
    }
    fun navigateFromReaderShell(route: ReaderRoute, targetRoute: String) {
        when (route) {
            is ReaderRoute.ImmersiveReading -> when (targetRoute) {
                RouteIds.IMMERSIVE_READING -> Unit
                RouteIds.READER_CONTROL -> vm.dispatch(
                    ReaderUiIntent.PushRoute(ReaderRoute.ReaderControl(context = route.context))
                )
                RouteIds.SOURCE_SWITCH -> vm.dispatch(
                    ReaderUiIntent.SourceSwitchOpen(
                        bookId = route.context?.bookUrl ?: "",
                        bookName = route.context?.bookName ?: "",
                        sourceId = route.context?.sourceId ?: ""
                    )
                )
                else -> navigateToRouteId(targetRoute)
            }
            is ReaderRoute.ReaderControl -> {
                val previous = state.backStack.dropLast(1).lastOrNull()
                when {
                    targetRoute == RouteIds.IMMERSIVE_READING && previous is ReaderRoute.ImmersiveReading ->
                        vm.dispatch(ReaderUiIntent.PopRoute)
                    targetRoute == RouteIds.SOURCE_SWITCH -> {
                        val ctx = route.context ?: state.readerContext
                        vm.dispatch(
                            ReaderUiIntent.SourceSwitchOpen(
                                bookId = ctx?.bookUrl ?: "",
                                bookName = ctx?.bookName ?: "",
                                sourceId = ctx?.sourceId ?: ""
                            )
                        )
                    }
                    targetRoute == route.id -> Unit
                    else -> navigateToRouteId(targetRoute)
                }
            }
            is ReaderRoute.SourceSwitchFlow -> {
                when (targetRoute) {
                    RouteIds.SOURCE_SWITCH,
                    RouteIds.READER_CONTROL -> vm.dispatch(ReaderUiIntent.PopRoute)
                    RouteIds.IMMERSIVE_READING -> {
                        vm.dispatch(ReaderUiIntent.PopRoute)
                        if (state.backStack.dropLast(1).lastOrNull() is ReaderRoute.ReaderControl) {
                            vm.dispatch(ReaderUiIntent.PopRoute)
                        }
                    }
                    else -> {
                        vm.dispatch(ReaderUiIntent.PopRoute)
                        navigateToRouteId(targetRoute)
                    }
                }
            }
            else -> navigateToRouteId(targetRoute)
        }
    }

    // 按路由分派专用 Close intent（重置对应状态切片），避免通用 PopRoute 造成状态泄漏。
    val handleBack: () -> Unit = {
        when (val route = state.backStack.lastOrNull()) {
            is ReaderRoute.SourceSwitchFlow -> vm.dispatch(ReaderUiIntent.SourceSwitchClose)
            is ReaderRoute.BookState -> {
                if (route.id == "book-detail") vm.dispatch(ReaderUiIntent.BookDetailClose)
                else vm.dispatch(ReaderUiIntent.PopRoute)
            }
            ReaderRoute.SettingsGeneral,
            ReaderRoute.SyncBackup,
            ReaderRoute.AboutFeedback,
            ReaderRoute.WebDavConfig,
            ReaderRoute.SourceManagement -> vm.dispatch(ReaderUiIntent.SettingsClose)
            ReaderRoute.SourceDetail -> vm.dispatch(ReaderUiIntent.SourceDetailClose)
            ReaderRoute.SourceEdit -> vm.dispatch(ReaderUiIntent.SourceEditCancel)
            ReaderRoute.ReaderFullFont,
            ReaderRoute.ReaderFullTheme,
            ReaderRoute.ReaderFullThemeEdit,
            ReaderRoute.ReaderFullLayout,
            ReaderRoute.ReaderFullPageTurn -> vm.dispatch(ReaderUiIntent.PopRoute)
            else -> vm.dispatch(ReaderUiIntent.PopRoute)
        }
    }

    // System back follows the overlay-first rule (MOTION_CONTRACT.md / FRONTEND_DEVELOPMENT_SLICE_MATRIX.md
    // Slice 4): Back closes the topmost overlay (Keyboard / Sheet / Dialog / MoreMenu / ReaderControl)
    // before popping the route. Hidden overlays have no hit area and don't intercept Back.
    BackHandler(enabled = state.backStack.isNotEmpty() || state.overlayState !is OverlayState.None || state.moreMenu.open) {
        when {
            state.overlayState is OverlayState.Keyboard -> vm.dispatch(ReaderUiIntent.CloseKeyboard)
            state.overlayState is OverlayState.Sheet -> vm.dispatch(ReaderUiIntent.CloseSheet)
            state.overlayState is OverlayState.Dialog -> vm.dispatch(ReaderUiIntent.CloseDialog)
            state.moreMenu.open -> vm.dispatch(ReaderUiIntent.CloseMoreMenu)
            else -> handleBack()
        }
    }

    // ── 隐藏状态栏副作用（hideStatusBar toggle 接线）─────────────────────
    // 观察 state.hideStatusBar，通过 WindowInsetsControllerCompat 隐藏/显示系统状态栏。
    // 仅在 reader 路由（ImmersiveReading / ReaderControl）下生效；离开 reader 时恢复。
    val hideStatusBarView = LocalView.current
    val currentRouteForStatusBar = state.currentRoute
    val isReaderRouteForStatusBar = currentRouteForStatusBar is ReaderRoute.ImmersiveReading ||
        currentRouteForStatusBar is ReaderRoute.ReaderControl
    val shouldHideStatusBar = state.hideStatusBar && isReaderRouteForStatusBar
    LaunchedEffect(shouldHideStatusBar) {
        val window = (hideStatusBarView.context as? ComponentActivity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, hideStatusBarView)
        if (shouldHideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    // ── Slice E: HostRequest effect collector ─────────────────────────────
    // Observes `pendingHostRequests` from the reducer (pure) and actually
    // dispatches each entry through the real HostAdapter. Deduplication by
    // dispatchId avoids re-execution on recomposition; timeout prevents a
    // stuck handler from blocking the queue indefinitely.
    //
    // Pattern follows the established `LaunchedEffect(key)` convention from
    // ReaderNavHost (keyed on state-derived values, one-shot side-effect).
    // Here the key is the pending queue size + the newest dispatchId, so the
    // effect re-fires only when a new entry is stamped in.
    val newestPending = state.pendingHostRequests.lastOrNull()
    LaunchedEffect(newestPending?.dispatchId) {
        val entry = newestPending ?: return@LaunchedEffect
        // Skip if the queue is empty (entry already completed by a prior cycle)
        // or if this is a re-fire for an already-processed id.
        val currentPending = vm.state.value.pendingHostRequests
        if (currentPending.none { it.dispatchId == entry.dispatchId }) return@LaunchedEffect

        val hostRequestTimeoutMs = hostRequestTimeoutMillis(entry.capability)
        val result = try {
            withTimeoutOrNull(hostRequestTimeoutMs) {
                val dispatcher = HostRequestDispatcher(ReaderCoreClient.get().hostAdapter())
                // CapabilityHandler is synchronous. Keep file pickers, TTS,
                // WebDAV and WorkManager waits off the Compose main thread so
                // Activity Result callbacks and timeout cancellation can run.
                withContext(Dispatchers.IO) { dispatcher.dispatch(entry) }
            }
        } catch (e: Exception) {
            HostRequestResult(
                dispatchId = entry.dispatchId,
                capability = entry.capability,
                success = false,
                errorCode = "INTERNAL",
                errorMessage = "dispatcher threw: ${e.message}"
            )
        }
        if (result == null) {
            // Timeout — fail the entry so the reducer clears it from pending.
            vm.dispatch(
                ReaderUiIntent.HostRequestError(
                    requestId = entry.dispatchId,
                    capability = entry.capability,
                    errorCode = "TIMEOUT",
                    errorMessage = "host request exceeded ${hostRequestTimeoutMs}ms"
                )
            )
        } else if (result.success) {
            vm.dispatch(
                ReaderUiIntent.HostRequestComplete(
                    requestId = result.dispatchId,
                    capability = result.capability,
                    resultJson = result.resultJson ?: "{}"
                )
            )
        } else {
            vm.dispatch(
                ReaderUiIntent.HostRequestError(
                    requestId = result.dispatchId,
                    capability = result.capability,
                    errorCode = result.errorCode ?: "INTERNAL",
                    errorMessage = result.errorMessage ?: ""
                )
            )
        }
    }

    // ── P3.1: WebDAV save effect collector ────────────────────────────────
    // Observes `webDavConfig.saveStatus == Saving` from the reducer (pure) and
    // actually persists the credential through the keystore-backed
    // [com.reader.android.AppProvider.webDavCredentialStore]. On completion,
    // dispatches [ReaderUiIntent.WebDavSaveResult] so the reducer transitions
    // to Saved / Error.
    val webDavSaveStatus = state.webDavConfig.saveStatus
    LaunchedEffect(webDavSaveStatus) {
        if (webDavSaveStatus != WebDavSaveStatus.Saving) return@LaunchedEffect
        val config = vm.state.value.webDavConfig
        runCatching {
            if (com.reader.android.AppProvider.isInitialized) {
                val credential = WebDavCredential(
                    serverUrl = config.serverUrl,
                    auth = AuthMethod.Basic(config.username, config.password)
                )
                com.reader.android.AppProvider.webDavCredentialStore.save("webdav.default", credential)
            }
        }.onSuccess {
            vm.dispatch(
                ReaderUiIntent.WebDavSaveResult(
                    success = true,
                    savedIdentifier = "webdav.default"
                )
            )
        }.onFailure { e ->
            vm.dispatch(
                ReaderUiIntent.WebDavSaveResult(
                    success = false,
                    message = e.message ?: "保存失败"
                )
            )
        }
    }

    // ── P3.2: WebDAV test effect collector ────────────────────────────────
    // Observes `webDavConfig.testStatus == Testing` from the reducer (pure)
    // and actually dispatches a `webdav.connect` HostRequest through the Core
    // bridge. On completion, dispatches [ReaderUiIntent.WebDavTestResult] so
    // the reducer transitions to Success / Error.
    val webDavTestStatus = state.webDavConfig.testStatus
    LaunchedEffect(webDavTestStatus) {
        if (webDavTestStatus != WebDavTestStatus.Testing) return@LaunchedEffect
        val config = vm.state.value.webDavConfig
        val url = config.serverUrl
        if (url.isEmpty()) {
            vm.dispatch(
                ReaderUiIntent.WebDavTestResult(
                    success = false,
                    message = "请先配置 WebDAV 服务器地址"
                )
            )
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        runCatching {
            val params = JSONObject().put("url", url)
            val request = HostRequest(1L, 1L, "webdav.connect", params.toString())
            val reply = ReaderCoreClient.get().hostAdapter().dispatch(request)
            when {
                reply?.isComplete() == true -> {
                    val result = JSONObject((reply as HostReply.Complete).resultJson())
                    val connected = result.getBoolean("connected")
                    if (connected) {
                        val latency = System.currentTimeMillis() - startMs
                        vm.dispatch(
                            ReaderUiIntent.WebDavTestResult(
                                success = true,
                                message = "",
                                latencyMs = latency
                            )
                        )
                    } else {
                        vm.dispatch(
                            ReaderUiIntent.WebDavTestResult(
                                success = false,
                                message = result.optString("message", "连接失败")
                            )
                        )
                    }
                }
                reply?.isError() == true -> {
                    val error = reply as HostReply.Error
                    vm.dispatch(
                        ReaderUiIntent.WebDavTestResult(
                            success = false,
                            message = error.message()
                        )
                    )
                }
                else -> {
                    vm.dispatch(
                        ReaderUiIntent.WebDavTestResult(
                            success = false,
                            message = "未知响应"
                        )
                    )
                }
            }
        }.onFailure { e ->
            vm.dispatch(
                ReaderUiIntent.WebDavTestResult(
                    success = false,
                    message = "错误: ${e.message}"
                )
            )
        }
    }

    val currentRoute = state.currentRoute
    if (currentRoute is ReaderRoute.SourceSwitchFlow) {
        FlowShellScreen(
            route = currentRoute,
            onBack = handleBack,
            onNavigate = { targetRoute -> navigateFromReaderShell(currentRoute, targetRoute) },
            sourceSwitch = state.sourceSwitch,
            onSelectSource = { sourceId ->
                vm.dispatch(ReaderUiIntent.SourceSwitchSelect(sourceId))
                vm.dispatch(ReaderUiIntent.SourceSwitchConfirm(sourceId))
            },
            onClose = { vm.dispatch(ReaderUiIntent.SourceSwitchCancel) }
        )
    } else if (currentRoute is ReaderRoute.ImmersiveReading ||
        currentRoute is ReaderRoute.ReaderControl
    ) {
        ReaderShellScreen(
            route = currentRoute,
            fallbackContext = state.readerContext,
            activeSession = state.activeSession,
            onBack = handleBack,
            onNavigate = { targetRoute -> navigateFromReaderShell(currentRoute, targetRoute) },
            onSessionToggle = { vm.dispatch(ReaderUiIntent.ToggleSessionPlaying) },
            onSessionStop = { vm.dispatch(ReaderUiIntent.StopSession) },
            moreMenuOpen = state.moreMenu.open && state.moreMenu.triggerId == "reader-control-more",
            dispatch = vm::dispatch,
            onStartTts = { text ->
                val ctx = state.readerContext
                vm.dispatch(ReaderUiIntent.StartTtsSession(
                    text = text,
                    chapterTitle = ctx?.bookName ?: "",
                    chapterIndex = ctx?.chapterIndex ?: 0
                ))
            },
            asyncResultState = state.asyncResult.state,
            bookOpenPilotEnabled = vm.readerBookOpenPilotEnabled,
            bookOpenDomainState = bookOpenDomainState,
            onBookOpenViewportLayoutReady = vm::onReaderBookOpenViewportLayoutReady,
            playbackPilotEnabled = vm.readerPlaybackPilotEnabled,
            playbackDomainState = playbackDomainState,
            onPlaybackPageLayoutReady = vm::onReaderPlaybackPageLayoutReady,
            onAsyncStateChange = { requestId, asyncState, value ->
                // Bridge ImmersiveReadingViewModel load state → ReaderUiState.asyncResult (M5).
                // The VM reports PENDING / COMPLETED / CANCELLED using context.entryRequestId;
                // the reducer's async-result guard discards stale results when a newer entry
                // supersedes the in-flight request.
                val currentRouteId = vm.state.value.currentRoute.routeId
                when (asyncState) {
                    AsyncResultStateValue.PENDING -> vm.dispatch(
                        ReaderUiIntent.StartAsyncRequest(
                            fromRoute = currentRouteId,
                            toRoute = RouteIds.IMMERSIVE_READING,
                            requestId = requestId
                        )
                    )
                    AsyncResultStateValue.COMPLETED -> vm.dispatch(
                        ReaderUiIntent.CompleteAsyncRequest(
                            requestId = requestId,
                            value = value,
                            currentRoute = currentRouteId
                        )
                    )
                    AsyncResultStateValue.CANCELLED -> vm.dispatch(
                        ReaderUiIntent.CancelAsyncRequest(requestId = requestId)
                    )
                    else -> Unit
                }
            }
        )
    } else {
        // P0-Fix8: route transition (AnimatedContent wrapping when(route))
        var prevStackSize by remember { mutableStateOf(state.backStack.size) }
        val routeDirection = when {
            state.backStack.size > prevStackSize -> RouteTransitionDirection.PUSH_FORWARD
            state.backStack.size < prevStackSize -> RouteTransitionDirection.POP_BACKWARD
            else -> RouteTransitionDirection.REPLACE
        }
        SideEffect { prevStackSize = state.backStack.size }
        val routeTransitionDuration = if (state.reducedMotion) 0 else 160
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                if (initialState is ReaderRoute.TabShell && targetState is ReaderRoute.TabShell) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    when (routeDirection) {
                        RouteTransitionDirection.PUSH_FORWARD ->
                            (slideInHorizontally(tween(routeTransitionDuration)) { it / 8 } + fadeIn(tween(routeTransitionDuration))) togetherWith
                                (slideOutHorizontally(tween(routeTransitionDuration)) { -it / 8 } + fadeOut(tween(routeTransitionDuration)))
                        RouteTransitionDirection.POP_BACKWARD ->
                            (slideInHorizontally(tween(routeTransitionDuration)) { -it / 8 } + fadeIn(tween(routeTransitionDuration))) togetherWith
                                (slideOutHorizontally(tween(routeTransitionDuration)) { it / 8 } + fadeOut(tween(routeTransitionDuration)))
                        RouteTransitionDirection.REPLACE ->
                            fadeIn(tween(routeTransitionDuration)) togetherWith fadeOut(tween(routeTransitionDuration))
                    }
                }
            },
            label = "route-transition",
            modifier = Modifier.fillMaxSize()
        ) { route ->
        when (route) {
        is ReaderRoute.Search -> {
            SearchScreen(
                onBack = handleBack,
                onBookClick = { book ->
                    vm.dispatch(
                        ReaderUiIntent.EnterReaderFromAction(
                            sourceId = book.sourceId.ifEmpty { book.origin.ifEmpty { book.bookUrl } },
                            bookUrl = book.bookUrl,
                            bookName = book.name
                        )
                    )
                }
            )
        }

        ReaderRoute.ImportSource -> {
            ImportBookSourceScreen(
                state = state.sourceImport,
                dispatch = vm::dispatch,
                onBack = handleBack
            )
        }

        ReaderRoute.BookBatchManagement -> {
            BookBatchManagementScreen(
                onBack = handleBack,
                onMoveGroup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) }
            )
        }

        ReaderRoute.GroupManagement -> {
            GroupManagementScreen(
                onBack = handleBack,
                onDone = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.LocalImport -> {
            LocalImportScreen(
                onBack = handleBack,
                onDone = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.BookshelfSearchSettings -> {
            BookshelfSearchSettingsScreen(
                onBack = handleBack
            )
        }

        ReaderRoute.SettingsGeneral -> {
            SettingsGeneralScreen(
                reducedMotion = state.reducedMotion,
                onReducedMotionChange = { vm.dispatch(ReaderUiIntent.SetReducedMotion(it)) },
                onBack = handleBack,
                appThemeMode = state.appThemeMode,
                onAppThemeModeChange = { mode -> vm.dispatch(ReaderUiIntent.UpdateAppThemeMode(mode = mode)) },
                autoUpdate = state.settings.autoCheckUpdate,
                onAutoUpdateChange = { vm.dispatch(ReaderUiIntent.SetReaderBehaviorToggle("autoCheckUpdate", it)) },
                backToTop = state.settings.tapBottomBarToTop,
                onBackToTopChange = { vm.dispatch(ReaderUiIntent.SetReaderBehaviorToggle("tapBottomBarToTop", it)) },
                crashLog = state.settings.crashLogEnabled,
                onCrashLogChange = { vm.dispatch(ReaderUiIntent.SetReaderBehaviorToggle("crashLogEnabled", it)) },
                onClearCache = { vm.dispatch(ReaderUiIntent.ClearCache) },
                onRestoreDefault = { vm.dispatch(ReaderUiIntent.RestoreDefaultSettings) },
                onOpenPermissionSettings = { vm.dispatch(ReaderUiIntent.OpenSystemPermissionSettings) },
                permissionFileAccess = state.permissions.fileAccess,
                permissionNotifications = state.permissions.notifications,
                permissionBattery = state.permissions.batteryOptimization
            )
        }

        ReaderRoute.AboutFeedback -> {
            val aboutContext = LocalContext.current
            AboutFeedbackScreen(
                onBack = handleBack,
                onCheckUpdate = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.AboutFeedback)) },
                onOpenRepo = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/reader/reader"))
                    aboutContext.startActivity(intent)
                },
                onOpenLicense = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.AboutFeedback)) },
                onContribute = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/reader/reader/contribute"))
                    aboutContext.startActivity(intent)
                }
            )
        }

        ReaderRoute.SyncBackup -> {
            SyncBackupScreen(
                onBack = handleBack,
                onSaveConfig = { vm.dispatch(ReaderUiIntent.SaveWebDavConfig) },
                onTestConnection = { vm.dispatch(ReaderUiIntent.TestWebDavConnection) },
                testStatus = state.webDavConfig.testStatus
            )
        }

        ReaderRoute.WebDavConfig -> {
            val webDavConfig = state.webDavConfig
            WebDavConfigScreen(
                onBack = handleBack,
                serverUrl = webDavConfig.serverUrl,
                username = webDavConfig.username,
                password = webDavConfig.password,
                syncDir = webDavConfig.syncDir,
                onServerUrlChange = { vm.dispatch(ReaderUiIntent.UpdateWebDavServer(it)) },
                onUsernameChange = { vm.dispatch(ReaderUiIntent.UpdateWebDavCredentials(it, webDavConfig.password)) },
                onPasswordChange = { vm.dispatch(ReaderUiIntent.UpdateWebDavCredentials(webDavConfig.username, it)) },
                onTestConnection = { vm.dispatch(ReaderUiIntent.TestWebDavConnection) },
                onSaveConfig = { vm.dispatch(ReaderUiIntent.SaveWebDavConfig) }
            )
        }

        ReaderRoute.SourceManagement -> {
            SourceManagementScreen(
                onBack = handleBack,
                onImportSource = { navigateToRouteId("source-import-options") },
                onToggleSource = { sourceId, enabled ->
                    vm.dispatch(ReaderUiIntent.SetSourceEnabled(sourceId, enabled))
                }
            )
        }

        ReaderRoute.RssSearch -> {
            RssSearchScreen(
                onBack = handleBack,
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) },
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) }
            )
        }

        ReaderRoute.RssAll -> {
            RssArticleHubScreen(
                title = "全部条目",
                activeMode = "全部",
                onBack = handleBack,
                onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSearch)) },
                onOpenSourceList = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenAll = {},
                onOpenStarred = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssStarred)) },
                onOpenRuleSubscription = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscription)) },
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) },
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssStarred -> {
            RssArticleHubScreen(
                title = "收藏",
                activeMode = "收藏",
                onBack = handleBack,
                onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSearch)) },
                onOpenSourceList = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenAll = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssAll)) },
                onOpenStarred = {},
                onOpenRuleSubscription = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscription)) },
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) },
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssRefreshing -> {
            RssRefreshingScreen(
                onBack = handleBack,
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) },
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssSubscriptionManagement -> {
            RssSubscriptionManagementScreen(
                onBack = handleBack,
                onCreateSource = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceEdit)) },
                onImportSource = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImport)) },
                onRuleSubscription = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscription)) },
                onManageGroups = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceGroups)) },
                onSourceActions = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceActions)) },
                onBatch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceBatch)) },
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExport)) }
            )
        }

        ReaderRoute.RssDetail -> {
            RssDetailScreen(
                onBack = handleBack,
                onBackToList = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenOriginal = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssOriginal)) },
                onManageSource = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssOriginal -> {
            RssOriginalScreen(
                onBack = handleBack,
                onBackToDetail = { popBackTo(ReaderRoute.RssDetail) },
                onOpenBrowser = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssOriginalBrowser)) }
            )
        }

        ReaderRoute.RssOriginalBrowser -> {
            RssSourceConfirmScreen(
                title = "系统浏览器",
                iconRes = R.drawable.reader_ic_globe,
                heading = "已准备打开原文链接",
                copy = "实际应用中这里会调用系统浏览器打开 github.com/minliny/Reader-UI/releases/latest，同时保留当前 RSS 阅读上下文。",
                cancelLabel = "返回原文页",
                confirmLabel = "回到正文",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssDetail, fallbackPops = 2) }
            )
        }

        ReaderRoute.RssSourceEdit -> {
            RssSourceEditScreen(
                onBack = handleBack,
                onDebug = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceImport -> {
            RssSourceImportScreen(
                onBack = handleBack,
                onOpenDetail = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImportDetail)) },
                onImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImportResult)) }
            )
        }

        ReaderRoute.RssSourceImportDetail -> {
            RssSourceImportDetailScreen(
                onBack = handleBack,
                onJoinImport = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceImportResult -> {
            RssSourceImportResultScreen(
                onBack = handleBack,
                onContinueImport = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDone = {
                    vm.dispatch(ReaderUiIntent.PopRoute)
                    vm.dispatch(ReaderUiIntent.PopRoute)
                }
            )
        }

        ReaderRoute.RssRuleSubscription -> {
            RssRuleSubscriptionScreen(
                onBack = handleBack,
                onOpenDetail = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionDetail)) },
                onCreate = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionEdit)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionDetail -> {
            RssRuleSubscriptionDetailScreen(
                onBack = handleBack,
                onEdit = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionEdit)) },
                onApply = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionApply)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionEdit -> {
            RssRuleSubscriptionEditScreen(
                onBack = handleBack,
                onTest = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionTest)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssRuleSubscriptionTest -> {
            RssRuleSubscriptionTestScreen(
                onBack = handleBack,
                onViewResult = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionDetail)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionApply -> {
            RssRuleSubscriptionApplyScreen(
                onBack = handleBack,
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImport)) }
            )
        }

        ReaderRoute.RssSourceGroups -> {
            RssSourceGroupsScreen(
                onBack = handleBack,
                onEditGroup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceGroupEdit)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceGroupEdit -> {
            RssSourceGroupEditScreen(
                onBack = handleBack,
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceActions -> {
            RssSourceActionsScreen(
                onBack = handleBack,
                onRefresh = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRefreshing)) },
                onEdit = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceEdit)) },
                onDebug = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onReadRecord = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssReadRecord)) },
                onVars = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceVars)) },
                onLogin = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLogin)) },
                onPin = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourcePin)) },
                onDisable = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDisable)) },
                onManageAll = { popBackTo(ReaderRoute.RssSubscriptionManagement) }
            )
        }

        ReaderRoute.RssSourceBatch -> {
            RssSourceBatchScreen(
                onBack = handleBack,
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExport)) },
                onDisable = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceBatchDisable)) },
                onDone = { popBackTo(ReaderRoute.RssSubscriptionManagement) }
            )
        }

        ReaderRoute.RssSourceExport -> {
            RssSourceExportScreen(
                onBack = handleBack,
                onPreview = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExportDetail)) },
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExportResult)) }
            )
        }

        ReaderRoute.RssSourceExportDetail -> {
            RssSourceExportDetailScreen(
                onBack = handleBack,
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExportResult)) }
            )
        }

        ReaderRoute.RssSourceExportResult -> {
            RssSourceConfirmScreen(
                title = "导出完成",
                iconRes = R.drawable.reader_ic_check,
                heading = "已生成导出文件",
                copy = "reader-rss-sources-20260626.json 已生成，包含已选订阅源、分组、启用状态和规则配置。",
                detail = "登录 Cookie 和账号凭据没有写入导出文件。",
                cancelLabel = "返回导出",
                confirmLabel = "完成",
                onCancel = { popBackTo(ReaderRoute.RssSourceExport) },
                onConfirm = { popBackTo(ReaderRoute.RssSubscriptionManagement, fallbackPops = 2) }
            )
        }

        ReaderRoute.RssSourceBatchDisable -> {
            RssSourceConfirmScreen(
                title = "批量禁用",
                iconRes = R.drawable.reader_ic_offline,
                heading = "禁用已选 2 个订阅源？",
                copy = "禁用后这些订阅源不会参与自动刷新、未读提醒和首页统计，已缓存条目和阅读记录会保留。",
                cancelLabel = "取消",
                confirmLabel = "确认禁用",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssSubscriptionManagement, fallbackPops = 2) }
            )
        }

        ReaderRoute.RssSourceDebug -> {
            RssSourceDebugScreen(
                onBack = handleBack,
                onEdit = {
                    if (state.backStack.dropLast(1).lastOrNull() == ReaderRoute.RssSourceEdit) {
                        vm.dispatch(ReaderUiIntent.PopRoute)
                    } else {
                        vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceEdit))
                    }
                },
                onDone = {
                    if (state.backStack.dropLast(1).lastOrNull() == ReaderRoute.RssSourceActions) {
                        vm.dispatch(ReaderUiIntent.PopRoute)
                    } else {
                        vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceActions))
                    }
                }
            )
        }

        ReaderRoute.RssSourceVars -> {
            RssSourceVarsScreen(
                onBack = handleBack,
                onEdit = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceEdit)) },
                onDebug = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onDone = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceLogin -> {
            RssSourceLoginScreen(
                onBack = handleBack,
                onWebLogin = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginWeb)) },
                onCookie = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginCookie)) },
                onTest = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onClear = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginClear)) },
                onDone = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceLoginWeb -> {
            RssSourceLoginWebScreen(
                onBack = handleBack,
                onDone = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginCookie)) }
            )
        }

        ReaderRoute.RssSourceLoginCookie -> {
            RssSourceLoginCookieScreen(
                onBack = handleBack,
                onSave = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceLoginClear -> {
            RssSourceConfirmScreen(
                title = "清除登录",
                iconRes = R.drawable.reader_ic_trash,
                heading = "清除当前源登录信息？",
                copy = "清除后该 RSS 源下次刷新会重新进入登录流程，不影响其他订阅源和已缓存文章。",
                cancelLabel = "取消",
                confirmLabel = "确认清除",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourcePin -> {
            RssSourceConfirmScreen(
                title = "置顶订阅源",
                iconRes = R.drawable.reader_ic_top,
                heading = "置顶 GitHub Releases？",
                copy = "置顶后该订阅源会显示在源列表和快捷入口最前面，不影响刷新规则和分组。",
                detail = "适合高频阅读的发布源、公告源或需要优先查看的订阅源。",
                cancelLabel = "取消",
                confirmLabel = "确认置顶",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceDisable -> {
            RssSourceConfirmScreen(
                title = "禁用订阅源",
                iconRes = R.drawable.reader_ic_offline,
                heading = "禁用已选订阅源？",
                copy = "禁用后不会参与自动刷新、未读提醒和 RSS 首页统计，已缓存条目和阅读记录会保留。",
                detail = "可以在订阅管理页重新启用。",
                cancelLabel = "取消",
                confirmLabel = "确认禁用",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssSubscriptionManagement, fallbackPops = 2) }
            )
        }

        ReaderRoute.RssReadRecord -> {
            RssReadRecordScreen(
                onBack = handleBack,
                onBackToList = { vm.dispatch(ReaderUiIntent.SelectTab(MainTab.RSS)) },
                onOpenDetail = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) },
                onClear = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRecordClear)) }
            )
        }

        ReaderRoute.RssRecordClear -> {
            RssSourceConfirmScreen(
                title = "清空阅读记录",
                iconRes = R.drawable.reader_ic_trash,
                heading = "清空 RSS 阅读记录？",
                copy = "只会清除 RSS 阅读历史，不会删除收藏、订阅源、未读状态或正文缓存。",
                cancelLabel = "取消",
                confirmLabel = "确认清空",
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { popBackTo(ReaderRoute.RssReadRecord) }
            )
        }

        is ReaderRoute.BookState -> {
            when (route.id) {
                "book-detail" -> {
                    val bookState = remember(route.book) {
                        route.book?.let { realBookDetailRouteState(it) } ?: demoBookDetailRouteState()
                    }
                    BookDetailScreen(
                        state = bookState,
                        onBack = handleBack,
                        onContinueReading = { enterReaderFromBook(bookState.book) },
                        onBookDirectory = { navigateTo(ReaderRoute.BookState("book-directory", route.book)) },
                        onSourceSwitch = {
                            vm.dispatch(
                                ReaderUiIntent.SourceSwitchOpen(
                                    bookId = route.book?.bookUrl ?: "",
                                    bookName = route.book?.name ?: "",
                                    sourceId = route.book?.origin ?: ""
                                )
                            )
                        },
                        onRemoveFromBookshelf = { vm.dispatch(ReaderUiIntent.PopRoute) }
                    )
                }
                "book-directory" -> {
                    val directoryState = remember(route.book) {
                        route.book?.let { realBookDirectoryRouteState(it) } ?: demoBookDirectoryRouteState()
                    }
                    BookDirectoryScreen(
                        state = directoryState,
                        onBack = handleBack,
                        onOpenChapter = { enterReaderFromBook(directoryState.book) }
                    )
                }
                "bookshelf-empty" -> {
                    MainTabShellFrame(
                        activeTab = MainTab.BOOKSHELF,
                        onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                    ) {
                        BookshelfEmptyRouteScreen(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
                            onDiscover = { vm.dispatch(ReaderUiIntent.SelectTab(MainTab.DISCOVER)) },
                            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
                            onBookBatchManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement)) },
                            onGroupManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) }
                        )
                    }
                }
                "sort-filter" -> {
                    MainTabShellFrame(
                        activeTab = MainTab.BOOKSHELF,
                        onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                    ) {
                        BookshelfSortFilterRouteScreen(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onOpenBookFromCover = { book -> enterReaderFromBook(book) },
                            onOpenBookFromAction = { book -> enterReaderFromBook(book) },
                            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
                            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
                            onBookBatchManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement)) },
                            onGroupManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) }
                        )
                    }
                }
                "bookshelf-cover-mode" -> {
                    MainTabShellFrame(
                        activeTab = MainTab.BOOKSHELF,
                        onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                    ) {
                        BookshelfCoverModeRouteScreen(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onOpenBookFromCover = { book -> enterReaderFromBook(book) },
                            onOpenBookFromAction = { book -> enterReaderFromBook(book) },
                            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
                            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
                            onBookBatchManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement)) },
                            onGroupManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) },
                            onSwitchToList = { navigateToRouteId("bookshelf-list-mode") }
                        )
                    }
                }
                "bookshelf-list-mode" -> {
                    MainTabShellFrame(
                        activeTab = MainTab.BOOKSHELF,
                        onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                    ) {
                        BookshelfListModeRouteScreen(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onOpenBookFromCover = { book -> enterReaderFromBook(book) },
                            onOpenBookFromAction = { book -> enterReaderFromBook(book) },
                            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
                            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
                            onBookBatchManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement)) },
                            onGroupManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) },
                            onSwitchToCover = { navigateToRouteId("bookshelf-cover-mode") }
                        )
                    }
                }
                "bookshelf-book-more-menu" -> {
                    MainTabShellFrame(
                        activeTab = MainTab.BOOKSHELF,
                        onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                    ) {
                        BookshelfBookMoreMenuRouteScreen(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onOpenBookFromCover = { book -> enterReaderFromBook(book) },
                            onOpenBookFromAction = { book -> enterReaderFromBook(book) },
                            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
                            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
                            onBookBatchManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement)) },
                            onGroupManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) },
                            onBookDetail = { navigateToRouteId("book-detail") },
                            onSourceSwitch = { navigateToRouteId("source-switch") },
                            onCacheBook = { navigateToRouteId("reader-book-cache") },
                            onAddToGroup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) },
                            onEditBook = { navigateToRouteId("source-rule-edit") },
                            onReplaceRule = { navigateToRouteId("content-replacement") },
                            onDeleteBook = { vm.dispatch(ReaderUiIntent.PopRoute) },
                            onDismiss = { vm.dispatch(ReaderUiIntent.PopRoute) }
                        )
                    }
                }
                else -> {
                    DemoRouteScreen(
                        routeId = route.id,
                        onBack = handleBack,
                        onNavigate = { navigateToRouteId(it) },
                        onDispatch = vm::dispatch,
                        overlayState = state.overlayState
                    )
                }
            }
        }

        is ReaderRoute.RssState -> {
            RssRemainingDemoRouteScreen(
                routeId = route.id,
                onBack = handleBack,
                onNavigate = { navigateToRouteId(it) }
            )
        }

        is ReaderRoute.RestoreState -> {
            fun navigateRestoreTarget(targetRouteId: String) {
                if (targetRouteId == RestoreRouteIds.SyncBackup && state.backStack.any { it == ReaderRoute.SyncBackup }) {
                    popBackTo(ReaderRoute.SyncBackup)
                } else {
                    navigateToRouteId(targetRouteId)
                }
            }
            RestoreScreen(
                state = restoreUiState,
                routeId = route.id,
                onBack = handleBack,
                onNavigate = ::navigateRestoreTarget,
                onScopeToggle = { restoreUiState = restoreUiState.toggleScope(it) },
                onConflictChoice = { conflictId, choice ->
                    restoreUiState = restoreUiState.chooseConflict(conflictId, choice)
                },
                onViewLog = { navigateToRouteId("source-logs") }
            )
        }

        is ReaderRoute.DiscoverState -> {
            val discoverRouteState = discoverDemoRouteState(route.id)
            if (discoverRouteState?.page == DiscoverDemoPage.Main) {
                MainTabShellFrame(
                    activeTab = MainTab.DISCOVER,
                    onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) }
                ) {
                    DiscoverDemoRouteScreen(
                        routeId = route.id,
                        onBack = handleBack,
                        onNavigate = { navigateToRouteId(it) },
                        onOpenBook = { sourceId, bookUrl, bookName ->
                            vm.dispatch(
                                ReaderUiIntent.EnterReaderFromAction(
                                    sourceId = sourceId,
                                    bookUrl = bookUrl,
                                    bookName = bookName
                                )
                            )
                        },
                        overlayState = state.overlayState
                    )
                }
            } else {
                DiscoverDemoRouteScreen(
                    routeId = route.id,
                    shell = discoverShellForRoute(route.id),
                    onBack = handleBack,
                    onNavigate = { navigateToRouteId(it) },
                    onOpenBook = { sourceId, bookUrl, bookName ->
                        vm.dispatch(
                            ReaderUiIntent.EnterReaderFromAction(
                                sourceId = sourceId,
                                bookUrl = bookUrl,
                                bookName = bookName
                            )
                        )
                    },
                    overlayState = state.overlayState
                )
            }
        }

        is ReaderRoute.SourceState -> {
            SourceDemoRouteScreen(
                routeId = route.id,
                onBack = handleBack,
                onNavigate = { navigateToRouteId(it) }
            )
        }

        ReaderRoute.ReaderFullFont -> {
            ReaderFontSettingsScreen(
                onBack = handleBack,
                context = state.readerContext,
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.ReaderFullTheme -> {
            ReaderThemeSettingsScreen(
                onBack = handleBack,
                context = state.readerContext,
                dispatch = vm::dispatch,
                onNavigate = { navigateToRouteId(it) }
            )
        }

        ReaderRoute.ReaderFullThemeEdit -> {
            ReaderThemeEditScreen(
                onBack = handleBack,
                context = state.readerContext,
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.ReaderFullLayout -> {
            ReaderLayoutSettingsScreen(
                onBack = handleBack,
                context = state.readerContext,
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.ReaderFullPageTurn -> {
            ReaderPageTurnSettingsScreen(
                onBack = handleBack,
                context = state.readerContext,
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.SourceDetail -> {
            SourceDetailScreen(
                onBack = handleBack,
                onEdit = { vm.dispatch(ReaderUiIntent.SourceEditOpen(sourceId = "", name = "", url = "")) },
                onDebug = { navigateToRouteId("source-debug") },
                onDelete = { vm.dispatch(ReaderUiIntent.PopRoute) },
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.SourceEdit -> {
            SourceEditScreen(
                onBack = handleBack,
                state = state.sourceEdit,
                dispatch = vm::dispatch
            )
        }

        ReaderRoute.ReaderSettings -> {
            ReaderSettingsScreen(
                onBack = handleBack,
                context = state.readerContext,
                onNavigate = { navigateToRouteId(it) }
            )
        }

        is ReaderRoute.Demo -> {
            DemoRouteScreen(
                routeId = route.id,
                onBack = handleBack,
                onNavigate = { navigateToRouteId(it) },
                onDispatch = vm::dispatch,
                overlayState = state.overlayState
            )
        }

        is ReaderRoute.TabShell -> {
            val discoverState = remember { DiscoverTabState() }
            val rssState = remember { RssTabState() }
            // P1-5: load real RSS subscriptions from RoomSubscriptionRepository
            // so the RSS tab renders real data instead of rssDemoSources().
            LaunchedEffect(Unit) {
                if (com.reader.android.AppProvider.isInitialized) {
                    runCatching {
                        rssState.subscriptions = com.reader.android.AppProvider.subscriptionRepository.getAll()
                    }
                }
            }
            MainTabShellFrame(
                activeTab = state.activeTab,
                onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) },
                appTopBar = {
                    when (state.activeTab) {
                        MainTab.BOOKSHELF -> BookshelfTabTopBar(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
                            onOpenMoreMenu = {
                                vm.dispatch(ReaderUiIntent.OpenMoreMenu(triggerId = "bookshelf-more"))
                            }
                        )
                        MainTab.DISCOVER -> DiscoverTabTopBar(state = discoverState)
                        MainTab.RSS -> RssTabTopBar(
                            state = rssState,
                            onManageSources = {
                                vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement))
                            }
                        )
                        MainTab.SETTINGS -> SettingsTabTopBar()
                    }
                },
                stateHost = {
                    when (state.activeTab) {
                        MainTab.BOOKSHELF -> BookshelfTabStateHost(
                            onBookBatchManagement = {
                                vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookBatchManagement))
                            },
                            onGroupManagement = {
                                vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement))
                            },
                            onLocalImport = {
                                vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport))
                            },
                            onBookDetail = { book ->
                                vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-detail", book)))
                            },
                            moreMenuOpen = state.moreMenu.open && state.moreMenu.triggerId == "bookshelf-more",
                            onCloseMoreMenu = { vm.dispatch(ReaderUiIntent.CloseMoreMenu) }
                        )
                        else -> Box(modifier = Modifier.size(0.dp))
                    }
                }
            ) {
                AnimatedContent(
                    targetState = state.activeTab,
                    transitionSpec = { tabSwitchTransition(reducedMotion) },
                    label = "app.tab.switch",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    TabContent(tab, vm, state.reducedMotion, discoverState, rssState)
                }
            }
        }
        else -> Unit
        }
        }
    }
}

/**
 * Main tab shell frame aligned with `frontend-demo/shared-shell-kit/kit.js`
 * `renderMainTabShell`. Renders 5 fixed slots:
 *
 * 1. `statusBar` — default zero-size Box placeholder (native Android uses system status bar
 *    inset; tab screens handle inset via their own TopBar `windowInsetsPadding`)
 * 2. `appTopBar` — tab screens inject their own TopBar via slot lambda (BookshelfTabTopBar /
 *    DiscoverTabTopBar / RssTabTopBar / SettingsTabTopBar); defaults to zero-size Box
 * 3. `contentRegion` — `content` trailing lambda (always real content via `AnimatedContent`)
 * 4. `stateHost` — tab screens inject state overlays (e.g. BookshelfTabStateHost); defaults to
 *    zero-size Box; per demo CSS `display:contents`, slot preserved as addressable placeholder
 * 5. `mainNav` — **self-rendering slot** (always `FloatingPillTabBar`); not exposed as lambda
 *    because main nav is the shell's defining fixture, not a per-route variable
 *
 * `statusBar` / `appTopBar` / `stateHost` default to `Box(Modifier.size(0.dp))` per demo
 * fixed-slot addressability contract (mirrors `display:contents` DOM anchor preservation).
 */
@Composable
private fun MainTabShellFrame(
    activeTab: MainTab,
    onSelect: (MainTab) -> Unit,
    statusBar: @Composable () -> Unit = { MainTabStatusBarSlot() },
    appTopBar: @Composable () -> Unit = { MainTabAppTopBarSlot() },
    stateHost: @Composable () -> Unit = { MainTabStateHostSlot() },
    content: @Composable () -> Unit
) {
    val viewportClass = rememberViewportClass()  // 新增：激活 ViewportClassAdapter
    Box(Modifier.fillMaxSize()) {
        val useLeftRail = viewportClass == ViewportClass.TABLET_EXPANDED ||
            viewportClass == ViewportClass.EXPANDED_WIDTH  // 改用 ViewportClass 判断
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (useLeftRail) 100.dp else 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                statusBar()
                appTopBar()
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
            // stateHost slot: overlay-positioned (demo CSS: display:contents; rendered as overlay when populated)
            stateHost()
        }
        // mainNav slot: self-rendered FloatingPillTabBar (shell's defining fixture, not injectable)
        FloatingPillTabBar(
            activeTab = activeTab,
            onSelect = onSelect,
            layout = if (useLeftRail) MainNavLayout.LeftRail else MainNavLayout.BottomPill,
            modifier = if (useLeftRail) {
                Modifier.align(Alignment.CenterStart)
            } else {
                Modifier.align(Alignment.BottomCenter)
            }
        )
    }
}

@Composable
private fun MainTabStatusBarSlot() {
    // statusBar slot placeholder — native Android uses system status bar inset; tab screens
    // handle inset via their own TopBar windowInsetsPadding. Zero-size Box preserves addressability.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
private fun MainTabAppTopBarSlot() {
    // appTopBar slot placeholder — tab screens inject their own TopBar via slot lambda.
    // Zero-size Box preserves addressability per demo contract.
    Box(modifier = Modifier.size(0.dp))
}

@Composable
private fun MainTabStateHostSlot() {
    // stateHost slot placeholder — tab screens inject state overlays via slot lambda.
    // Zero-size Box preserves addressability per demo `display:contents` contract.
    Box(modifier = Modifier.size(0.dp))
}

private fun discoverShellForRoute(routeId: String): DiscoverDemoRouteShell = when (routeId) {
    DiscoverDemoRouteIds.SOURCE_LOGIN -> DiscoverDemoRouteShell.Library
    DiscoverDemoRouteIds.RULE_TEST,
    DiscoverDemoRouteIds.SOURCE_BULK -> DiscoverDemoRouteShell.Settings
    else -> DiscoverDemoRouteShell.Auto
}

@Composable
private fun TabContent(
    tab: MainTab,
    vm: AppShellViewModel,
    reducedMotion: Boolean,
    discoverState: DiscoverTabState,
    rssState: RssTabState
) {
    when (tab) {
        MainTab.BOOKSHELF -> BookshelfScreen(
            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) },
            onOpenBookFromCover = { book ->
                vm.dispatch(
                    ReaderUiIntent.EnterReaderFromCover(
                        sourceId = book.sourceId.ifEmpty { book.origin.ifEmpty { book.bookUrl } },
                        bookUrl = book.bookUrl,
                        bookName = book.name
                    )
                )
            },
            onOpenBookFromAction = { book ->
                vm.dispatch(
                    ReaderUiIntent.EnterReaderFromAction(
                        sourceId = book.sourceId.ifEmpty { book.origin.ifEmpty { book.bookUrl } },
                        bookUrl = book.bookUrl,
                        bookName = book.name
                    )
                )
            },
            onLocalImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.LocalImport)) },
            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
            onDiscover = { vm.dispatch(ReaderUiIntent.SelectTab(MainTab.DISCOVER)) }
        )
        MainTab.DISCOVER -> {
            // P0-2: share the Activity-scoped BookshelfViewModel so the discover shelf-dot
            // reflects the real Core-owned bookshelf instead of the demo inShelf flag.
            val bookshelfVm: BookshelfViewModel = viewModel()
            val shelfBookUrls by bookshelfVm.shelfBookUrls.collectAsStateWithLifecycle()
            DiscoverScreen(
                state = discoverState,
                onOpenBook = { sourceId, bookUrl, bookName ->
                    vm.dispatch(
                        ReaderUiIntent.EnterReaderFromAction(
                            sourceId = sourceId,
                            bookUrl = bookUrl,
                            bookName = bookName
                        )
                    )
                },
                onOpenDiscoverControl = {
                    vm.dispatch(ReaderUiIntent.PushRoute(DemoRouteRegistry.routeFor("discover-control")))
                },
                shelfBookUrls = shelfBookUrls
            )
        }
        MainTab.RSS -> RssScreen(
            state = rssState,
            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSearch)) },
            onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) },
            onOpenAll = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssAll)) },
            onOpenStarred = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssStarred)) },
            onOpenRuleSubscription = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscription)) },
            onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) }
        )
        MainTab.SETTINGS -> SettingsScreen(
            reducedMotion = reducedMotion,
            onReducedMotionChange = { vm.dispatch(ReaderUiIntent.SetReducedMotion(it)) },
            onGeneralSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.SettingsGeneral)) },
            onBookshelfSettings = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.BookshelfSearchSettings)) },
            onSourceManagement = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.SourceManagement)) },
            onSyncBackup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.SyncBackup)) },
            onAboutFeedback = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.AboutFeedback)) }
        )
    }
}

internal fun appShellViewModelFactory(
    reducedMotionResolver: ReducedMotionResolver?,
    ttsProgressFlow: kotlinx.coroutines.flow.Flow<com.reader.android.data.adapter.TtsProgressUpdate?>? = null
) = viewModelFactory {
    initializer { AppShellViewModel(reducedMotionResolver, ttsProgressFlow) }
}

/** Timeout for a single HostRequest dispatch in the AppShell effect collector. */
private const val HOST_REQUEST_TIMEOUT_MS: Long = 10_000L
private const val FILE_SELECT_HOST_REQUEST_TIMEOUT_MS: Long = 120_000L

private fun hostRequestTimeoutMillis(capability: String): Long =
    if (capability == "file.select") FILE_SELECT_HOST_REQUEST_TIMEOUT_MS else HOST_REQUEST_TIMEOUT_MS

@Composable
private fun PushedPlaceholderRouteScreen(title: String, body: String, onBack: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = readerExtraColors().muted
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
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
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                    contentDescription = "返回",
                    tint = ink,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                style = ReaderTextStyles.backBarTitle,
                color = ink,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Spacer(Modifier.size(44.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = body,
                style = ReaderTextStyles.emptyBody,
                color = muted,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Demo-matched placeholder for tabs whose real screen lands in a later slice.
 *
 * Layout mirrors `.fd-top-bar` (58dp, 29sp serif title, status-bar inset) and centers a
 * single body line in the content area. Typography uses [ReaderTextStyles.appBarTitle] /
 * [ReaderTextStyles.emptyBody] so the placeholder reads as part of the demo design language
 * rather than a generic Material3 stub.
 */
@Composable
private fun PlaceholderTabScreen(title: String, body: String) {
    val ink = MaterialTheme.colorScheme.onBackground
    val muted = readerExtraColors().muted
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
                .padding(top = 6.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // fd-top-bar leading 44px slot (placeholder)
            Spacer(Modifier.size(44.dp))
            Text(
                text = title,
                style = ReaderTextStyles.appBarTitle,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // fd-top-bar trailing 44px slot (placeholder)
            Spacer(Modifier.size(44.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = ReaderTextStyles.emptyHeading,
                    color = ink,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = body,
                    style = ReaderTextStyles.emptyBody,
                    color = muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** `app.tab.switch` content fade: 80–120ms, no slide, no overshoot. Instant under reduced motion. */
private fun tabSwitchTransition(reducedMotion: Boolean) =
    if (reducedMotion) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        fadeIn(
            animationSpec = tween(
                durationMillis = effectiveDuration(AppMotionTokens.DurationTabSelect, false)
                    .inWholeMilliseconds.toInt()
                    .coerceIn(80, 120)
            )
        ) togetherWith fadeOut(
            animationSpec = tween(durationMillis = AppMotionTokens.DurationTabPress.inWholeMilliseconds.toInt())
        )
    }
