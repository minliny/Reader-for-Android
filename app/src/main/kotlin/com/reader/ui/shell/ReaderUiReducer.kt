package com.reader.ui.shell

import org.json.JSONObject

/**
 * Pure reducer for [ReaderUiState]. No Android / Compose dependencies — fully unit-testable.
 *
 * Contract rules implemented (from `MOTION_CONTRACT.md`, `MOTION_EFFECTS.md`,
 * `FRONTEND_DEVELOPMENT_SLICE_MATRIX.md` Slice 1/2, `MOTION_PLATFORM_MAPPING.md`):
 *
 * - Tab switch is NOT a route push; back stack of pushed routes is cleared (interrupt).
 * - Reader entry final state is `immersive-reading`, never the control layer.
 * - Back from reader returns to the source tab.
 * - Continuous multi-cover taps keep only the last target (latest intent wins).
 * - Reduced motion is a first-class state field, not a silent render flag.
 * - Every final state uniquely explains route / back stack / ReaderContext / session /
 *   overlay / interrupt.
 */
object ReaderUiReducer {

    fun reduce(state: ReaderUiState, intent: ReaderUiIntent): ReaderUiState = when (intent) {
        is ReaderUiIntent.SelectTab -> selectTab(state, intent)

        is ReaderUiIntent.EnterReaderFromCover ->
            enterReader(
                state,
                entry = ReaderEntry.COVER_TO_IMMERSIVE,
                sourceId = intent.sourceId,
                bookUrl = intent.bookUrl,
                bookName = intent.bookName,
                requestId = intent.requestId
            )

        is ReaderUiIntent.EnterReaderFromAction ->
            enterReader(
                state,
                entry = ReaderEntry.ACTION_TO_IMMERSIVE,
                sourceId = intent.sourceId,
                bookUrl = intent.bookUrl,
                bookName = intent.bookName,
                requestId = intent.requestId
            )

        is ReaderUiIntent.PushRoute -> pushRoute(state, intent)

        ReaderUiIntent.PopRoute -> popRoute(state)

        is ReaderUiIntent.SetReducedMotion -> state.copy(reducedMotion = intent.enabled)

        // ── 路由 ──
        is ReaderUiIntent.ReplaceRoute -> replaceRoute(state, intent)

        // ── ReaderContext 更新（S4）──
        is ReaderUiIntent.UpdateReaderChapter -> state.copy(
            readerContext = state.readerContext?.copy(chapterIndex = intent.chapterIndex)
        )
        is ReaderUiIntent.UpdateReaderPage -> state.copy(
            readerContext = state.readerContext?.copy(page = intent.page, progress = intent.progress)
        )
        is ReaderUiIntent.UpdateReaderTheme -> state.copy(
            readerContext = state.readerContext?.copy(themeId = intent.themeId)
        )
        is ReaderUiIntent.UpdateReaderBrightness -> state.copy(
            readerContext = state.readerContext?.copy(
                brightness = intent.brightness, brightnessAuto = intent.auto
            )
        )
        is ReaderUiIntent.UpdateReaderTypography -> state.copy(
            readerContext = state.readerContext?.copy(
                fontSize = intent.fontSize,
                lineSpacing = intent.lineSpacing,
                pageMargin = intent.pageMargin
            )
        )

        // ── 控制层显隐（S5）── MotionId: reader.control.hide / reader.module.switch / reader.control.dock.release
        ReaderUiIntent.ShowReaderControl -> state.copy(
            readerControl = state.readerControl.copy(visible = true, phase = MotionPhase.ENTERING)
        )
        ReaderUiIntent.HideReaderControl -> state.copy(
            // LEAVING 阶段保持 visible=true 让离场动画播放；UpdateMotionPhase(SETTLED) 时才收尾 visible=false。
            // 契约：reader.control.hide finalState=immersiveReadingHotZonesRestored，
            // 即控制层在动画期间仍可见，hot zones 在 SETTLED 后才恢复。
            readerControl = state.readerControl.copy(phase = MotionPhase.LEAVING)
        )
        is ReaderUiIntent.SwitchReaderModule -> state.copy(
            readerControl = state.readerControl.copy(activeModule = intent.module)
        )
        is ReaderUiIntent.UpdateDockOffset -> state.copy(
            readerControl = state.readerControl.copy(
                dockOffset = state.readerControl.dockOffset + (intent.viewportClass to intent.offset)
            )
        )

        // ── 文本选择（S6）──
        is ReaderUiIntent.StartSelection -> state.copy(
            textSelection = TextSelectionState(
                active = true,
                startOffset = intent.startOffset,
                endOffset = intent.endOffset,
                toolbarVisible = false
            )
        )
        is ReaderUiIntent.UpdateSelectionRange -> state.copy(
            textSelection = state.textSelection.copy(
                startOffset = intent.startOffset, endOffset = intent.endOffset
            )
        )
        ReaderUiIntent.EndSelection -> state.copy(textSelection = TextSelectionState())
        ReaderUiIntent.ShowSelectionToolbar -> state.copy(
            textSelection = state.textSelection.copy(toolbarVisible = true)
        )
        ReaderUiIntent.HideSelectionToolbar -> state.copy(
            textSelection = state.textSelection.copy(toolbarVisible = false)
        )

        // ── 会话状态（S7）── MotionId: reader.session.autoPage.start / reader.session.tts.start
        // autoPage 与 tts 互斥：设置 activeSession 自动清掉另一种。
        // P0-5: session intents also enqueue DispatchHostRequest so the AppShell
        // effect collector drives the real TTS engine via HostAdapter.
        ReaderUiIntent.StartAutoPageSession -> state.copy(
            activeSession = ActiveSession(SessionType.AUTO_PAGE, playing = true)
        )
        is ReaderUiIntent.StartTtsSession -> {
            val ttsDispatch = HostRequestDispatch(
                dispatchId = generateDispatchId("tts-start"),
                capability = "tts.system.start",
                paramsJson = JSONObject()
                    .put("text", intent.text)
                    .put("utteranceId", intent.requestId)
                    .put("chapterTitle", intent.chapterTitle)
                    .put("chapterIndex", intent.chapterIndex)
                    .toString()
            )
            state.copy(
                activeSession = ActiveSession(SessionType.TTS, playing = true),
                pendingHostRequests = state.pendingHostRequests + ttsDispatch
            )
        }
        ReaderUiIntent.StopSession -> {
            val stopDispatch = state.activeSession?.takeIf { it.type == SessionType.TTS }?.let {
                HostRequestDispatch(
                    dispatchId = generateDispatchId("tts-stop"),
                    capability = "tts.system.stop",
                    paramsJson = "{}"
                )
            }
            state.copy(
                activeSession = null,
                pendingHostRequests = if (stopDispatch != null) state.pendingHostRequests + stopDispatch else state.pendingHostRequests
            )
        }
        ReaderUiIntent.ToggleSessionPlaying -> {
            val session = state.activeSession
            val newPlaying = !(session?.playing ?: false)
            val dispatch = if (session?.type == SessionType.TTS) {
                if (newPlaying) {
                    HostRequestDispatch(
                        dispatchId = generateDispatchId("tts-resume"),
                        capability = "tts.system.resume",
                        paramsJson = "{}"
                    )
                } else {
                    HostRequestDispatch(
                        dispatchId = generateDispatchId("tts-pause"),
                        capability = "tts.system.pause",
                        paramsJson = "{}"
                    )
                }
            } else null
            state.copy(
                activeSession = session?.copy(playing = newPlaying),
                pendingHostRequests = if (dispatch != null) state.pendingHostRequests + dispatch else state.pendingHostRequests
            )
        }
        is ReaderUiIntent.UpdateCountdown -> state.copy(
            activeSession = state.activeSession?.copy(countdownSeconds = intent.seconds)
        )
        is ReaderUiIntent.UpdateTtsProgress -> state.copy(
            activeSession = state.activeSession?.copy(
                ttsSentenceIndex = intent.sentenceIndex, ttsChapterIndex = intent.chapterIndex
            )
        )

        // ── Overlay（S2）──
        is ReaderUiIntent.OpenKeyboard -> state.copy(
            overlayState = OverlayState.Keyboard(intent.inputId)
        )
        ReaderUiIntent.CloseKeyboard -> state.copy(
            overlayState = if (state.overlayState is OverlayState.Keyboard) OverlayState.None else state.overlayState
        )
        is ReaderUiIntent.OpenSheet -> state.copy(
            overlayState = OverlayState.Sheet(intent.content)
        )
        ReaderUiIntent.CloseSheet -> state.copy(
            overlayState = if (state.overlayState is OverlayState.Sheet) OverlayState.None else state.overlayState
        )
        is ReaderUiIntent.OpenDialog -> state.copy(
            overlayState = OverlayState.Dialog(intent.content)
        )
        ReaderUiIntent.CloseDialog -> state.copy(
            overlayState = if (state.overlayState is OverlayState.Dialog) OverlayState.None else state.overlayState
        )

        // ── 更多菜单（S8）──
        is ReaderUiIntent.OpenMoreMenu -> state.copy(
            moreMenu = MoreMenuState(open = true, triggerId = intent.triggerId)
        )
        ReaderUiIntent.CloseMoreMenu -> state.copy(
            moreMenu = MoreMenuState(open = false, triggerId = null)
        )

        // ── Viewport（S9/M6）── MotionId: viewport.orientation.prepare/reshape/settle
        ReaderUiIntent.ViewportPrepare -> viewportPrepare(state, intent.requestId)
        ReaderUiIntent.ViewportReshape -> state.copy(
            viewport = state.viewport.copy(orientationPhase = OrientationPhase.RESHAPING)
        )
        ReaderUiIntent.ViewportSettle -> state.copy(
            viewport = state.viewport.copy(orientationPhase = OrientationPhase.STABLE)
        )
        is ReaderUiIntent.UpdateViewport -> state.copy(
            viewport = state.viewport.copy(
                viewportClass = intent.viewportClass,
                widthDp = intent.widthDp,
                heightDp = intent.heightDp
            )
        )

        // ── 翻页（C2）── MotionId: reader.page.turn.next/prev
        ReaderUiIntent.TurnPageNext -> state.copy(
            readerContext = state.readerContext?.copy(page = state.readerContext.page + 1)
        )
        ReaderUiIntent.TurnPagePrev -> state.copy(
            readerContext = state.readerContext?.copy(
                page = (state.readerContext.page - 1).coerceAtLeast(0)
            )
        )

        // ── 章节跳转 ──
        is ReaderUiIntent.JumpChapter -> state.copy(
            readerContext = state.readerContext?.copy(
                chapterIndex = intent.chapterIndex, page = 0, progress = 0f
            )
        )

        // ── Motion interrupt（M4）── 设置 motionInterrupt 字段
        is ReaderUiIntent.MotionInterrupt -> state.copy(
            motionInterrupt = MotionInterrupt(
                requestId = intent.requestId,
                from = state.currentRoute.routeId,
                to = state.currentRoute.routeId,
                kind = intent.kind
            )
        )

        // ── AsyncResult（M5）── MotionId: motion.async.resultGuard
        is ReaderUiIntent.StartAsyncRequest -> state.copy(
            asyncResult = AsyncResultState(
                requestId = intent.requestId,
                state = AsyncResultStateValue.PENDING,
                value = null
            ),
            motionInterrupt = MotionInterrupt(
                requestId = intent.requestId,
                from = intent.fromRoute,
                to = intent.toRoute,
                kind = InterruptKind.COMPLETE_THEN_REPLACE
            )
        )
        is ReaderUiIntent.CompleteAsyncRequest -> completeAsyncRequest(state, intent)
        is ReaderUiIntent.CancelAsyncRequest -> cancelAsyncRequest(state, intent)

        // ── Motion phase（M2）──
        is ReaderUiIntent.UpdateMotionPhase -> updateMotionPhase(state, intent.phase)

        // ── P3: Source Import e2e ──
        is ReaderUiIntent.ParseSourceImport -> state.copy(
            sourceImport = SourceImportState.Parsing
        )
        is ReaderUiIntent.CompleteSourceImportParse -> state.copy(
            sourceImport = SourceImportState.Preview(entries = intent.entries)
        )
        is ReaderUiIntent.ConfirmSourceImport -> {
            // Only valid from Preview state; otherwise no-op.
            if (state.sourceImport !is SourceImportState.Preview) {
                state
            } else {
                state.copy(sourceImport = SourceImportState.Importing)
            }
        }
        is ReaderUiIntent.CompleteSourceImport -> state.copy(
            sourceImport = SourceImportState.Done(
                imported = intent.imported,
                skipped = intent.skipped,
                failed = intent.failed
            )
        )
        is ReaderUiIntent.FailSourceImport -> state.copy(
            sourceImport = SourceImportState.Error(
                message = intent.message,
                retryable = true
            )
        )
        ReaderUiIntent.DismissSourceImportResult -> state.copy(
            sourceImport = SourceImportState.Idle
        )

        // ── P3: WebDAV config ──
        is ReaderUiIntent.UpdateWebDavServer -> state.copy(
            webDavConfig = state.webDavConfig.copy(serverUrl = intent.serverUrl)
        )
        is ReaderUiIntent.UpdateWebDavCredentials -> state.copy(
            webDavConfig = state.webDavConfig.copy(
                username = intent.username,
                password = intent.password
            )
        )
        ReaderUiIntent.TestWebDavConnection -> state.copy(
            webDavConfig = state.webDavConfig.copy(testStatus = WebDavTestStatus.Testing)
        )
        is ReaderUiIntent.WebDavTestResult -> state.copy(
            webDavConfig = state.webDavConfig.copy(
                testStatus = if (intent.success) {
                    WebDavTestStatus.Success(latencyMs = intent.latencyMs)
                } else {
                    WebDavTestStatus.Error(message = intent.message)
                }
            )
        )
        ReaderUiIntent.SaveWebDavConfig -> state.copy(
            webDavConfig = state.webDavConfig.copy(saveStatus = WebDavSaveStatus.Saving)
        )
        is ReaderUiIntent.WebDavSaveResult -> state.copy(
            webDavConfig = state.webDavConfig.copy(
                saveStatus = if (intent.success) {
                    WebDavSaveStatus.Saved
                } else {
                    WebDavSaveStatus.Error(message = intent.message)
                },
                savedIdentifier = intent.savedIdentifier ?: state.webDavConfig.savedIdentifier
            )
        )
        ReaderUiIntent.RevokeWebDavConfig -> state.copy(
            webDavConfig = WebDavConfigState()
        )

        // ── P3: Permission ──
        is ReaderUiIntent.PermissionGranted -> state.copy(
            permissions = updatePermission(state.permissions, intent.kind, PermissionStatus.GRANTED)
        )
        is ReaderUiIntent.PermissionDenied -> state.copy(
            permissions = updatePermission(state.permissions, intent.kind, PermissionStatus.DENIED)
        )
        // RequestPermission and OpenSystemPermissionSettings are side-effect intents;
        // the reducer does not change state (the platform layer handles the dialog).
        is ReaderUiIntent.RequestPermission -> state
        ReaderUiIntent.OpenSystemPermissionSettings -> state

        // ── P4: RSS list ──
        // TODO(core-blocker): Core protocol does not yet expose `rss.list` /
        // `rss.item.read` / `rss.subscription.*` / `rss.source.*`. The reducer
        // drives the UI state machine only; actual article content is fetched
        // through the Core bridge once those methods land.
        ReaderUiIntent.LoadRssList -> state.copy(rssList = RssListState.Loading)
        is ReaderUiIntent.RssListLoaded -> state.copy(
            rssList = RssListState.Success(sourceCount = intent.sourceCount)
        )
        ReaderUiIntent.RssListEmpty -> state.copy(rssList = RssListState.Empty)
        is ReaderUiIntent.RssListLoadFailed -> state.copy(
            rssList = RssListState.Error(
                message = intent.message,
                retryable = true
            )
        )
        ReaderUiIntent.DismissRssListResult -> state.copy(rssList = RssListState.Idle)

        // ── Slice D: HostRequest dispatch ──
        is ReaderUiIntent.DispatchHostRequest -> state.copy(
            pendingHostRequests = state.pendingHostRequests + HostRequestDispatch(
                dispatchId = intent.requestId,
                capability = intent.capability,
                paramsJson = intent.paramsJson
            )
        )
        is ReaderUiIntent.HostRequestComplete -> {
            val dispatch = state.pendingHostRequests.firstOrNull { it.dispatchId == intent.requestId }
            state.copy(
                pendingHostRequests = state.pendingHostRequests.filterNot { it.dispatchId == intent.requestId },
                lastHostRequestResult = HostRequestResult(
                    dispatchId = intent.requestId,
                    capability = intent.capability,
                    success = true,
                    resultJson = intent.resultJson
                )
            ).also {
                // Suppress unused warning; dispatch is available for logging if needed.
                @Suppress("UNUSED_VARIABLE") dispatch
            }
        }
        is ReaderUiIntent.HostRequestError -> state.copy(
            pendingHostRequests = state.pendingHostRequests.filterNot { it.dispatchId == intent.requestId },
            lastHostRequestResult = HostRequestResult(
                dispatchId = intent.requestId,
                capability = intent.capability,
                success = false,
                errorCode = intent.errorCode,
                errorMessage = intent.errorMessage
            )
        )
        ReaderUiIntent.ClearHostRequestResult -> state.copy(lastHostRequestResult = null)

        // ── P0: Source Switch 专用 reducer ──────────────────────────────────
        // 不再走通用 PushRoute：SourceSwitchOpen 同时 push route 并进入 Loading，
        // 让 reducer 可追踪换源专属状态。SourceSwitchClose 同时 pop route 并回到 Idle。
        is ReaderUiIntent.SourceSwitchOpen -> {
            // 优先复用 readerContext（reader shell 内进入换源）；
            // 无 readerContext 时（如从 book-detail 直接进入换源），用 intent 的
            // bookId/bookName/sourceId 构造 fallback context，避免 FlowShell 回退到
            // demo 标题。注意：不写入 state.readerContext —— 换源并非全新 reader entry，
            // popRoute 时该 fallback context 随 route 一起释放。
            val context = state.readerContext ?: ReaderContext(
                sourceId = intent.sourceId.ifEmpty { intent.bookId },
                bookUrl = intent.bookId,
                bookName = intent.bookName,
                entry = ReaderEntry.ACTION_TO_IMMERSIVE,
                entryRequestId = intent.requestId
            )
            val route = ReaderRoute.SourceSwitchFlow(context = context)
            state.copy(
                backStack = state.backStack + route,
                currentRoute = route,
                sourceSwitch = SourceSwitchState.Loading,
                motionInterrupt = null
            )
        }
        ReaderUiIntent.SourceSwitchClose -> {
            // 同时 pop route 并清换源状态
            val newBackStack = state.backStack.dropLast(1)
            val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(state.activeTab)
            state.copy(
                backStack = newBackStack,
                currentRoute = newRoute,
                sourceSwitch = SourceSwitchState.Idle,
                motionInterrupt = null
            )
        }
        is ReaderUiIntent.SourceSwitchSelect -> {
            // 仅在 Results 状态下更新选中源；其他状态为 no-op
            val current = state.sourceSwitch
            if (current is SourceSwitchState.Results) {
                state.copy(
                    sourceSwitch = current.copy(selectedSourceId = intent.sourceId)
                )
            } else {
                state
            }
        }
        is ReaderUiIntent.SourceSwitchResultsLoaded -> {
            state.copy(
                sourceSwitch = SourceSwitchState.Results(
                    results = intent.results,
                    selectedSourceId = null
                )
            )
        }

        // ── P0: book-detail 专用 reducer ──────────────────────────────────
        // 不再走通用 PushRoute：BookDetailOpen 同时 push route 并进入 Loading，
        // 让 reducer 可追踪 book-detail 的加载/就绪/错误三态。
        // 契约：error 非空时 pageState 必须为 error 或 source-unavailable
        // （state-rule.fixtures.json `book-detail-error-requires-error-pagestate`）。
        is ReaderUiIntent.BookDetailOpen -> {
            val route = ReaderRoute.BookState("book-detail")
            state.copy(
                backStack = state.backStack + route,
                currentRoute = route,
                bookDetail = BookDetailPageState.Loading,
                motionInterrupt = null
            )
        }
        ReaderUiIntent.BookDetailClose -> {
            // 同时 pop route 并清 book-detail 状态
            val newBackStack = state.backStack.dropLast(1)
            val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(state.activeTab)
            state.copy(
                backStack = newBackStack,
                currentRoute = newRoute,
                bookDetail = BookDetailPageState.Idle,
                motionInterrupt = null
            )
        }
        is ReaderUiIntent.BookDetailLoaded -> state.copy(
            bookDetail = BookDetailPageState.Ready
        )
        is ReaderUiIntent.BookDetailLoadFailed -> state.copy(
            // pageState=error 满足 `book-detail-error-requires-error-pagestate` 契约
            bookDetail = BookDetailPageState.Error(message = intent.message)
        )

        // ── P0: settings 专用 reducer ─────────────────────────────────────
        // 让 reducer 可追踪 settings 内部子 tab 切换与 overlay 展开状态。
        // 契约：settings.overlay == EXPANDED_OPTION 时禁止 tab 切换
        // （state-rule.fixtures.json `settings-overlay-guard-tab-switch`）。
        is ReaderUiIntent.SettingsOpen -> {
            val route = when (intent.tab) {
                SettingsTab.GENERAL -> ReaderRoute.SettingsGeneral
                SettingsTab.SYNC_BACKUP -> ReaderRoute.SyncBackup
                SettingsTab.ABOUT -> ReaderRoute.AboutFeedback
            }
            state.copy(
                backStack = state.backStack + route,
                currentRoute = route,
                settings = state.settings.copy(activeTab = intent.tab),
                motionInterrupt = null
            )
        }
        ReaderUiIntent.SettingsClose -> {
            val newBackStack = state.backStack.dropLast(1)
            val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(state.activeTab)
            state.copy(
                backStack = newBackStack,
                currentRoute = newRoute,
                motionInterrupt = null
            )
        }
        is ReaderUiIntent.SettingsTabSwitch -> {
            // settings-overlay-guard-tab-switch: overlay == EXPANDED_OPTION 时禁止 tab 切换
            if (state.settings.overlay == SettingsOverlay.EXPANDED_OPTION) {
                state
            } else {
                state.copy(settings = state.settings.copy(activeTab = intent.tab))
            }
        }
        ReaderUiIntent.SettingsOverlayExpand -> state.copy(
            settings = state.settings.copy(overlay = SettingsOverlay.EXPANDED_OPTION)
        )
        ReaderUiIntent.SettingsOverlayCollapse -> state.copy(
            settings = state.settings.copy(overlay = SettingsOverlay.NONE)
        )

        // ── 阅读设置面板交互 ──────────────────────────────────────────────────
        // hideStatusBar / 行为开关 / 单选项：reducer 纯状态更新，
        // 平台副作用（WindowInsetsControllerCompat）由 UI 层 LaunchedEffect 观察 state 触发。
        is ReaderUiIntent.SetHideStatusBar -> state.copy(hideStatusBar = intent.enabled)
        is ReaderUiIntent.SetReaderBehaviorToggle -> state.copy(
            readerBehaviorToggles = state.readerBehaviorToggles + (intent.key to intent.enabled)
        )
        is ReaderUiIntent.SetReaderChoice -> state.copy(
            readerChoices = state.readerChoices + (intent.key to intent.value)
        )
    }

    /**
     * `app.tab.switch`. Clears any pushed routes (Search / ImportSource / reader) — tab
     * switch is an interrupt (motion.interrupt.cancel) and must not leave dangling overlays
     * or a stale back stack.
     *
     * 契约守卫（state-rule.fixtures.json `settings-overlay-guard-tab-switch`）：
     * 当 currentRoute 是 settings / settings-general / global-settings 且
     * settings.overlay == EXPANDED_OPTION 时，tab 切换被 async guard 拦截（no-op）。
     */
    private fun selectTab(state: ReaderUiState, intent: ReaderUiIntent.SelectTab): ReaderUiState {
        if (intent.tab == state.activeTab && state.backStack.isEmpty()) {
            // Re-selecting the current tab: only pressed feedback is allowed (MOTION_EFFECTS §4).
            return state
        }
        // settings-overlay-guard-tab-switch: settings overlay 展开时禁止 tab 切换
        val onSettingsRoute = state.currentRoute.routeId in SETTINGS_OVERLAY_GUARD_ROUTES
        if (onSettingsRoute && state.settings.overlay == SettingsOverlay.EXPANDED_OPTION) {
            return state
        }
        val cancelled = state.backStack.isNotEmpty()
        return state.copy(
            activeTab = intent.tab,
            currentRoute = ReaderRoute.TabShell(intent.tab),
            backStack = emptyList(),
            readerContext = null,
            activeSession = null,
            overlayState = OverlayState.None,
            motionInterrupt = if (cancelled) {
                MotionInterrupt(
                    requestId = intent.requestId,
                    from = state.currentRoute.routeId,
                    to = ReaderRoute.TabShell(intent.tab).routeId,
                    kind = InterruptKind.CANCEL
                )
            } else null
        )
    }

    /**
     * Reader entry (`reader.entry.coverToImmersive` / `reader.entry.actionToImmersive`).
     *
     * Final state: route = `immersive-reading`, ReaderContext set, control layer NOT shown.
     * Latest intent wins: if a previous in-flight `immersive-reading` is on top of the
     * stack (continuous multi-cover tap), it is replaced (motion.interrupt.cancel) rather
     * than stacked — so "连续点击只保留最后目标".
     */
    private fun enterReader(
        state: ReaderUiState,
        entry: ReaderEntry,
        sourceId: String,
        bookUrl: String,
        bookName: String,
        requestId: String
    ): ReaderUiState {
        val ctx = ReaderContext(
            sourceId = sourceId,
            bookUrl = bookUrl,
            bookName = bookName,
            entry = entry,
            entryRequestId = requestId
        )
        val newRoute = ReaderRoute.ImmersiveReading(ctx)
        val topIsInFlightReader = state.backStack.lastOrNull() is ReaderRoute.ImmersiveReading
        val newBackStack: List<ReaderRoute> = if (topIsInFlightReader) {
            // Replace the in-flight entry: latest intent wins, old one cancelled.
            state.backStack.dropLast(1) + newRoute
        } else {
            state.backStack + newRoute
        }
        return state.copy(
            backStack = newBackStack,
            currentRoute = newRoute,
            readerContext = ctx,
            // Entry starts in immersive reading — never auto-open the control layer.
            activeSession = null,
            overlayState = OverlayState.None,
            motionInterrupt = if (topIsInFlightReader) {
                MotionInterrupt(
                    requestId = requestId,
                    from = RouteIds.IMMERSIVE_READING,
                    to = RouteIds.IMMERSIVE_READING,
                    kind = InterruptKind.CANCEL
                )
            } else null
        )
    }

    /** `app.route.push` for non-tab routes (Search / ImportSource). */
    private fun pushRoute(state: ReaderUiState, intent: ReaderUiIntent.PushRoute): ReaderUiState {
        val route = intent.route
        // TabShell is not pushable — guard against misuse.
        require(route !is ReaderRoute.TabShell) { "TabShell is not pushable; use SelectTab." }
        return state.copy(
            backStack = state.backStack + route,
            currentRoute = route,
            motionInterrupt = null
        )
    }

    /**
     * `app.route.pop`. Pops the top non-tab route and returns to the source (the active tab
     * or the previous pushed route). ReaderContext is cleared when the reader is fully
     * popped (you've left the reader).
     */
    private fun popRoute(state: ReaderUiState): ReaderUiState {
        if (state.backStack.isEmpty()) return state
        val newBackStack = state.backStack.dropLast(1)
        val newRoute = newBackStack.lastOrNull() ?: ReaderRoute.TabShell(state.activeTab)
        val readerFullyPopped = newBackStack.none { it is ReaderRoute.ImmersiveReading }
        return state.copy(
            backStack = newBackStack,
            currentRoute = newRoute,
            readerContext = if (readerFullyPopped) null else state.readerContext,
            activeSession = if (readerFullyPopped) null else state.activeSession,
            overlayState = OverlayState.None,
            motionInterrupt = null
        )
    }

    // ── 新增 helper（S2/S5/S8/M2/M4/M5/M6）─────────────────────────────────────
    // 注意：reducer 保持纯函数。MotionController.start() 的实际调用由 ViewModel
    // dispatch 层根据 motionInterrupt / motionPhase 字段观察触发，不在此处引入副作用。

    /**
     * `app.route.replace`（MotionId: app.route.replace）。替换 currentRoute，
     * 不 push backStack；若 backStack 栈顶是旧 currentRoute 则同步替换，
     * 触发 motion.interrupt.cancel 使最终状态可解释。
     */
    private fun replaceRoute(state: ReaderUiState, intent: ReaderUiIntent.ReplaceRoute): ReaderUiState {
        val route = intent.route
        require(route !is ReaderRoute.TabShell) { "TabShell is not replaceable; use SelectTab." }
        val oldRoute = state.currentRoute
        // 若栈顶就是旧 currentRoute，原地替换；否则只换 currentRoute。
        val newBackStack = if (state.backStack.isNotEmpty() && state.backStack.last() === oldRoute) {
            state.backStack.dropLast(1) + route
        } else {
            state.backStack
        }
        return state.copy(
            backStack = newBackStack,
            currentRoute = route,
            motionInterrupt = MotionInterrupt(
                requestId = intent.requestId,
                from = oldRoute.routeId,
                to = route.routeId,
                kind = InterruptKind.CANCEL
            )
        )
    }

    /**
     * `viewport.orientation.prepare`（MotionId: viewport.orientation.prepare）。
     * 冻结动效阶段，触发 motion.interrupt.cancel。
     */
    private fun viewportPrepare(state: ReaderUiState, requestId: String): ReaderUiState {
        return state.copy(
            viewport = state.viewport.copy(orientationPhase = OrientationPhase.PREPARING),
            motionInterrupt = MotionInterrupt(
                requestId = requestId,
                from = state.currentRoute.routeId,
                to = state.currentRoute.routeId,
                kind = InterruptKind.CANCEL
            )
        )
    }

    /**
     * `motion.async.resultGuard` 完成分支（M5）。requestId 匹配则 COMPLETED 并写入 value；
     * 不匹配（stale）则 DISCARDED，保证 "latest intent wins"。
     */
    private fun completeAsyncRequest(
        state: ReaderUiState,
        intent: ReaderUiIntent.CompleteAsyncRequest
    ): ReaderUiState {
        val isCurrent = state.asyncResult.requestId == intent.requestId
        val newAsync = if (isCurrent) {
            AsyncResultState(
                requestId = intent.requestId,
                state = AsyncResultStateValue.COMPLETED,
                value = intent.value
            )
        } else {
            // stale 结果：标记 DISCARDED，保留原 requestId 供审计
            state.asyncResult.copy(state = AsyncResultStateValue.DISCARDED)
        }
        return state.copy(asyncResult = newAsync)
    }

    /**
     * `motion.async.resultGuard` 取消分支（M5）。requestId 匹配则 CANCELLED；
     * 不匹配则不改动（避免误取消更新的请求）。
     */
    private fun cancelAsyncRequest(
        state: ReaderUiState,
        intent: ReaderUiIntent.CancelAsyncRequest
    ): ReaderUiState {
        val isCurrent = state.asyncResult.requestId == intent.requestId
        if (!isCurrent) return state
        return state.copy(
            asyncResult = state.asyncResult.copy(state = AsyncResultStateValue.CANCELLED)
        )
    }

    /**
     * 更新 UI 侧动效阶段（M2）。当进入 SETTLED 且控制层处于 LEAVING 时，
     * 收尾隐藏控制层（visible=false），与 HideReaderControl 的 LEAVING→SETTLED 轨迹呼应。
     */
    private fun updateMotionPhase(state: ReaderUiState, phase: MotionPhase): ReaderUiState {
        val newReaderControl = if (
            phase == MotionPhase.SETTLED &&
            state.readerControl.phase == MotionPhase.LEAVING
        ) {
            state.readerControl.copy(visible = false, phase = MotionPhase.SETTLED)
        } else {
            state.readerControl
        }
        return state.copy(motionPhase = phase, readerControl = newReaderControl)
    }

    /** P3: 更新指定权限种类的状态。 */
    private fun updatePermission(
        current: PermissionState,
        kind: PermissionKind,
        status: PermissionStatus
    ): PermissionState = when (kind) {
        PermissionKind.NOTIFICATIONS -> current.copy(notifications = status)
        PermissionKind.FILE_ACCESS -> current.copy(fileAccess = status)
        PermissionKind.BATTERY_OPTIMIZATION -> current.copy(batteryOptimization = status)
    }

    private fun generateDispatchId(prefix: String): String = "$prefix-${System.nanoTime()}"

    /**
     * settings-overlay-guard-tab-switch 守卫覆盖的路由集合。
     *
     * 当 currentRoute.routeId 在此集合内且 settings.overlay == EXPANDED_OPTION 时，
     * SelectTab 为 no-op（async guard，state-rule.fixtures.json）。
     */
    internal val SETTINGS_OVERLAY_GUARD_ROUTES: Set<String> = setOf(
        MainTab.SETTINGS.routeId,
        RouteIds.SETTINGS_GENERAL,
        "global-settings"
    )
}
