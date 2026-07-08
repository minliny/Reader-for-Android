package com.reader.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reader.android.R
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
import com.reader.ui.bookshelf.BookshelfSearchSettingsScreen
import com.reader.ui.bookshelf.GroupManagementScreen
import com.reader.ui.bookshelf.LocalImportScreen
import com.reader.ui.demo.DemoRouteRegistry
import com.reader.ui.demo.DemoRouteScreen
import com.reader.ui.discover.DiscoverDemoPage
import com.reader.ui.discover.DiscoverDemoRouteIds
import com.reader.ui.discover.DiscoverDemoRouteScreen
import com.reader.ui.discover.DiscoverDemoRouteShell
import com.reader.ui.discover.DiscoverScreen
import com.reader.ui.discover.DiscoverTabState
import com.reader.ui.discover.DiscoverTabTopBar
import com.reader.ui.discover.discoverDemoRouteState
import com.reader.ui.motion.AppMotionTokens
import com.reader.ui.motion.ReducedMotionResolver
import com.reader.ui.motion.effectiveDuration
import com.reader.ui.reading.FlowShellScreen
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
            if (com.reader.android.AppProvider.isInitialized) {
                com.reader.android.AppProvider.ttsSessionController.progressFlow
            } else null
        )
    )
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val reducedMotion = state.reducedMotion
    var restoreUiState by remember { mutableStateOf(RestoreUiState()) }

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
                sourceId = book.origin.ifEmpty { book.bookUrl },
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
                    ReaderUiIntent.PushRoute(ReaderRoute.SourceSwitchFlow(route.context))
                )
                else -> navigateToRouteId(targetRoute)
            }
            is ReaderRoute.ReaderControl -> {
                val previous = state.backStack.dropLast(1).lastOrNull()
                when {
                    targetRoute == RouteIds.IMMERSIVE_READING && previous is ReaderRoute.ImmersiveReading ->
                        vm.dispatch(ReaderUiIntent.PopRoute)
                    targetRoute == RouteIds.SOURCE_SWITCH -> vm.dispatch(
                        ReaderUiIntent.PushRoute(ReaderRoute.SourceSwitchFlow(route.context ?: state.readerContext))
                    )
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

    // System back follows the overlay-first rule (MOTION_CONTRACT.md / FRONTEND_DEVELOPMENT_SLICE_MATRIX.md
    // Slice 4): Back closes the topmost overlay (Keyboard / Sheet / Dialog / MoreMenu / ReaderControl)
    // before popping the route. Hidden overlays have no hit area and don't intercept Back.
    BackHandler(enabled = state.backStack.isNotEmpty() || state.overlayState !is OverlayState.None || state.moreMenu.open) {
        when {
            state.overlayState is OverlayState.Keyboard -> vm.dispatch(ReaderUiIntent.CloseKeyboard)
            state.overlayState is OverlayState.Sheet -> vm.dispatch(ReaderUiIntent.CloseSheet)
            state.overlayState is OverlayState.Dialog -> vm.dispatch(ReaderUiIntent.CloseDialog)
            state.moreMenu.open -> vm.dispatch(ReaderUiIntent.CloseMoreMenu)
            else -> vm.dispatch(ReaderUiIntent.PopRoute)
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

        val result = try {
            withTimeoutOrNull(HOST_REQUEST_TIMEOUT_MS) {
                val dispatcher = HostRequestDispatcher(ReaderCoreClient.get().hostAdapter())
                dispatcher.dispatch(entry)
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
                    errorMessage = "host request exceeded ${HOST_REQUEST_TIMEOUT_MS}ms"
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

    val currentRoute = state.currentRoute
    if (currentRoute is ReaderRoute.SourceSwitchFlow) {
        FlowShellScreen(
            route = currentRoute,
            onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
            onNavigate = { targetRoute -> navigateFromReaderShell(currentRoute, targetRoute) }
        )
    } else if (currentRoute is ReaderRoute.ImmersiveReading ||
        currentRoute is ReaderRoute.ReaderControl
    ) {
        ReaderShellScreen(
            route = currentRoute,
            fallbackContext = state.readerContext,
            activeSession = state.activeSession,
            onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
            onNavigate = { targetRoute -> navigateFromReaderShell(currentRoute, targetRoute) },
            onSessionToggle = { vm.dispatch(ReaderUiIntent.ToggleSessionPlaying) },
            onSessionStop = { vm.dispatch(ReaderUiIntent.StopSession) },
            onStartTts = { text ->
                val ctx = state.readerContext
                vm.dispatch(ReaderUiIntent.StartTtsSession(
                    text = text,
                    chapterTitle = ctx?.bookName ?: "",
                    chapterIndex = ctx?.chapterIndex ?: 0
                ))
            },
            asyncResultState = state.asyncResult.state,
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
    } else when (val route = currentRoute) {
        is ReaderRoute.Search -> {
            SearchScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onBookClick = { book ->
                    vm.dispatch(
                        ReaderUiIntent.EnterReaderFromAction(
                            sourceId = book.origin.ifEmpty { book.bookUrl },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.BookBatchManagement -> {
            BookBatchManagementScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onMoveGroup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.GroupManagement)) }
            )
        }

        ReaderRoute.GroupManagement -> {
            GroupManagementScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDone = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.LocalImport -> {
            LocalImportScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDone = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.BookshelfSearchSettings -> {
            BookshelfSearchSettingsScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.SettingsGeneral -> {
            SettingsGeneralScreen(
                reducedMotion = state.reducedMotion,
                onReducedMotionChange = { vm.dispatch(ReaderUiIntent.SetReducedMotion(it)) },
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.AboutFeedback -> {
            AboutFeedbackScreen(onBack = { vm.dispatch(ReaderUiIntent.PopRoute) })
        }

        ReaderRoute.SyncBackup -> {
            SyncBackupScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onWebDavConfig = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.WebDavConfig)) }
            )
        }

        ReaderRoute.WebDavConfig -> {
            WebDavConfigScreen(onBack = { vm.dispatch(ReaderUiIntent.PopRoute) })
        }

        ReaderRoute.SourceManagement -> {
            SourceManagementScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onImportSource = { navigateToRouteId("source-import-options") }
            )
        }

        ReaderRoute.RssSearch -> {
            RssSearchScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) },
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) }
            )
        }

        ReaderRoute.RssAll -> {
            RssArticleHubScreen(
                title = "全部条目",
                activeMode = "全部",
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenArticle = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssDetail)) },
                onManageSources = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssSubscriptionManagement -> {
            RssSubscriptionManagementScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onBackToList = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenOriginal = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssOriginal)) },
                onManageSource = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSubscriptionManagement)) }
            )
        }

        ReaderRoute.RssOriginal -> {
            RssOriginalScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDebug = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceImport -> {
            RssSourceImportScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenDetail = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImportDetail)) },
                onImport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImportResult)) }
            )
        }

        ReaderRoute.RssSourceImportDetail -> {
            RssSourceImportDetailScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onJoinImport = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceImportResult -> {
            RssSourceImportResultScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onContinueImport = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDone = {
                    vm.dispatch(ReaderUiIntent.PopRoute)
                    vm.dispatch(ReaderUiIntent.PopRoute)
                }
            )
        }

        ReaderRoute.RssRuleSubscription -> {
            RssRuleSubscriptionScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onOpenDetail = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionDetail)) },
                onCreate = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionEdit)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionDetail -> {
            RssRuleSubscriptionDetailScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onEdit = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionEdit)) },
                onApply = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionApply)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionEdit -> {
            RssRuleSubscriptionEditScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onTest = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionTest)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssRuleSubscriptionTest -> {
            RssRuleSubscriptionTestScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onViewResult = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssRuleSubscriptionDetail)) }
            )
        }

        ReaderRoute.RssRuleSubscriptionApply -> {
            RssRuleSubscriptionApplyScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onCancel = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onConfirm = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceImport)) }
            )
        }

        ReaderRoute.RssSourceGroups -> {
            RssSourceGroupsScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onEditGroup = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceGroupEdit)) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceGroupEdit -> {
            RssSourceGroupEditScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onSave = { vm.dispatch(ReaderUiIntent.PopRoute) }
            )
        }

        ReaderRoute.RssSourceActions -> {
            RssSourceActionsScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExport)) },
                onDisable = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceBatchDisable)) },
                onDone = { popBackTo(ReaderRoute.RssSubscriptionManagement) }
            )
        }

        ReaderRoute.RssSourceExport -> {
            RssSourceExportScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onPreview = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExportDetail)) },
                onExport = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceExportResult)) }
            )
        }

        ReaderRoute.RssSourceExportDetail -> {
            RssSourceExportDetailScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onEdit = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceEdit)) },
                onDebug = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onDone = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceLogin -> {
            RssSourceLoginScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onWebLogin = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginWeb)) },
                onCookie = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginCookie)) },
                onTest = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceDebug)) },
                onClear = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginClear)) },
                onDone = { popBackTo(ReaderRoute.RssSourceActions) }
            )
        }

        ReaderRoute.RssSourceLoginWeb -> {
            RssSourceLoginWebScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onDone = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.RssSourceLoginCookie)) }
            )
        }

        ReaderRoute.RssSourceLoginCookie -> {
            RssSourceLoginCookieScreen(
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                        onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                        onContinueReading = { enterReaderFromBook(bookState.book) },
                        onBookDirectory = { navigateTo(ReaderRoute.BookState("book-directory", route.book)) },
                        onSourceSwitch = { navigateToRouteId(RouteIds.SOURCE_SWITCH) },
                        onRemoveFromBookshelf = { vm.dispatch(ReaderUiIntent.PopRoute) }
                    )
                }
                "book-directory" -> {
                    val directoryState = remember(route.book) {
                        route.book?.let { realBookDirectoryRouteState(it) } ?: demoBookDirectoryRouteState()
                    }
                    BookDirectoryScreen(
                        state = directoryState,
                        onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                else -> {
                    DemoRouteScreen(
                        routeId = route.id,
                        onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                        onNavigate = { navigateToRouteId(it) }
                    )
                }
            }
        }

        is ReaderRoute.RssState -> {
            RssRemainingDemoRouteScreen(
                routeId = route.id,
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
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
                        onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                        onNavigate = { navigateToRouteId(it) },
                        onOpenBook = { sourceId, bookUrl, bookName ->
                            vm.dispatch(
                                ReaderUiIntent.EnterReaderFromAction(
                                    sourceId = sourceId,
                                    bookUrl = bookUrl,
                                    bookName = bookName
                                )
                            )
                        }
                    )
                }
            } else {
                DiscoverDemoRouteScreen(
                    routeId = route.id,
                    shell = discoverShellForRoute(route.id),
                    onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                    onNavigate = { navigateToRouteId(it) },
                    onOpenBook = { sourceId, bookUrl, bookName ->
                        vm.dispatch(
                            ReaderUiIntent.EnterReaderFromAction(
                                sourceId = sourceId,
                                bookUrl = bookUrl,
                                bookName = bookName
                            )
                        )
                    }
                )
            }
        }

        is ReaderRoute.SourceState -> {
            SourceDemoRouteScreen(
                routeId = route.id,
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onNavigate = { navigateToRouteId(it) }
            )
        }

        is ReaderRoute.Demo -> {
            DemoRouteScreen(
                routeId = route.id,
                onBack = { vm.dispatch(ReaderUiIntent.PopRoute) },
                onNavigate = { navigateToRouteId(it) }
            )
        }

        is ReaderRoute.TabShell -> {
            val discoverState = remember { DiscoverTabState() }
            val rssState = remember { RssTabState() }
            MainTabShellFrame(
                activeTab = state.activeTab,
                onSelect = { vm.dispatch(ReaderUiIntent.SelectTab(it)) },
                appTopBar = {
                    when (state.activeTab) {
                        MainTab.BOOKSHELF -> BookshelfTabTopBar(
                            onSearch = { vm.dispatch(ReaderUiIntent.PushRoute(ReaderRoute.Search)) }
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
                            }
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useLeftRail = maxWidth >= 600.dp
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
                        sourceId = book.origin.ifEmpty { book.bookUrl },
                        bookUrl = book.bookUrl,
                        bookName = book.name
                    )
                )
            },
            onOpenBookFromAction = { book ->
                vm.dispatch(
                    ReaderUiIntent.EnterReaderFromAction(
                        sourceId = book.origin.ifEmpty { book.bookUrl },
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

private fun appShellViewModelFactory(
    reducedMotionResolver: ReducedMotionResolver?,
    ttsProgressFlow: kotlinx.coroutines.flow.Flow<com.reader.android.data.adapter.TtsProgressUpdate?>? = null
) = viewModelFactory {
    initializer { AppShellViewModel(reducedMotionResolver, ttsProgressFlow) }
}

/** Timeout for a single HostRequest dispatch in the AppShell effect collector. */
private const val HOST_REQUEST_TIMEOUT_MS: Long = 10_000L

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
