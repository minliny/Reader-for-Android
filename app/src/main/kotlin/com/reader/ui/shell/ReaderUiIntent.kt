package com.reader.ui.shell

import com.reader.ui.shell.RouteIds.IMMERSIVE_READING

/**
 * Intents dispatched into [ReaderUiReducer]. Each carries a [requestId] so the reducer can
 * implement "latest intent wins" and the async-result guard
 * (`motion.async.resultGuard`: requestId / from / to / context).
 */
sealed class ReaderUiIntent {
    abstract val requestId: String

    /**
     * `app.tab.switch` — main tab switch. NOT a route push: only [ReaderUiState.activeTab]
     * changes and the back stack of pushed routes is cleared (interrupt rule).
     */
    data class SelectTab(
        val tab: MainTab,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `reader.entry.coverToImmersive` — enter immersive reading from a bookshelf cover. */
    data class EnterReaderFromCover(
        val sourceId: String,
        val bookUrl: String,
        val bookName: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `reader.entry.actionToImmersive` — enter from continue-reading / chapter row / search result. */
    data class EnterReaderFromAction(
        val sourceId: String,
        val bookUrl: String,
        val bookName: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `app.route.push` — push a non-tab route onto the back stack. */
    data class PushRoute(
        val route: ReaderRoute,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** `app.route.pop` — system back / explicit back. Pops the top non-tab route. */
    object PopRoute : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /**
     * Testable reduced-motion switch (MOTION_EFFECTS.md §8). Production also seeds this from
     * [com.reader.ui.motion.ReducedMotionResolver] at startup; this intent lets unit tests
     * and a debug toggle flip it without changing system settings.
     */
    data class SetReducedMotion(
        val enabled: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── 路由 ──────────────────────────────────────────────────────────────────

    /** `app.route.replace` —— 替换 currentRoute，不 push backStack，触发 motion.interrupt.cancel。 */
    data class ReplaceRoute(
        val route: ReaderRoute,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── ReaderContext 更新（S4）──────────────────────────────────────────────

    /** 更新当前章节序号。 */
    data class UpdateReaderChapter(
        val chapterIndex: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新当前页序号与阅读进度。 */
    data class UpdateReaderPage(
        val page: Int,
        val progress: Float,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新阅读主题 id。 */
    data class UpdateReaderTheme(
        val themeId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新 App 主题模式（system/light/dark），驱动 ReaderTheme 的 isNight 解析。 */
    data class UpdateAppThemeMode(
        val mode: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新亮度与自动亮度开关。 */
    data class UpdateReaderBrightness(
        val brightness: Float,
        val auto: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新排版参数（字号/行距/页边距）。 */
    data class UpdateReaderTypography(
        val fontSize: Float,
        val lineSpacing: Float,
        val pageMargin: Float,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── 控制层显隐（S5）──────────────────────────────────────────────────────

    /** 显示阅读器控制层（phase=ENTERING）。 */
    object ShowReaderControl : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 隐藏阅读器控制层（phase=LEAVING，然后 visible=false）。 */
    object HideReaderControl : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 切换当前激活的 reader module（directory/tts/appearance/settings）。 */
    data class SwitchReaderModule(
        val module: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新宽屏 dock offset（按 viewport class 保存）。 */
    data class UpdateDockOffset(
        val viewportClass: ViewportClass,
        val offset: Float,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── 文本选择（S6）────────────────────────────────────────────────────────

    /** 开始文本选择。 */
    data class StartSelection(
        val startOffset: Int,
        val endOffset: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新选区范围。 */
    data class UpdateSelectionRange(
        val startOffset: Int,
        val endOffset: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 结束文本选择。 */
    object EndSelection : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 显示选择工具栏。 */
    object ShowSelectionToolbar : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 隐藏选择工具栏。 */
    object HideSelectionToolbar : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── 会话状态（S7）────────────────────────────────────────────────────────

    /** 启动自动翻页会话（互斥：先清 TTS）。 */
    data class StartAutoPageSession(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 启动 TTS 朗读会话（互斥：先清 AUTO_PAGE）。 */
    data class StartTtsSession(
        val text: String,
        val chapterTitle: String = "",
        val chapterIndex: Int = 0,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 停止当前会话。 */
    object StopSession : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 切换会话播放/暂停。 */
    object ToggleSessionPlaying : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 更新自动翻页倒计时。 */
    data class UpdateCountdown(
        val seconds: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新 TTS 朗读进度（句序 + 章节序号）。 */
    data class UpdateTtsProgress(
        val sentenceIndex: Int,
        val chapterIndex: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── Overlay（S2）─────────────────────────────────────────────────────────

    /** 打开软键盘浮层。 */
    data class OpenKeyboard(
        val inputId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 关闭软键盘浮层。 */
    object CloseKeyboard : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 打开 Sheet 浮层。 */
    data class OpenSheet(
        val content: SheetContent,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 关闭 Sheet 浮层。 */
    object CloseSheet : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 打开 Dialog 浮层。 */
    data class OpenDialog(
        val content: DialogContent,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 关闭 Dialog 浮层。 */
    object CloseDialog : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── 更多菜单（S8）────────────────────────────────────────────────────────

    /** 打开更多菜单。 */
    data class OpenMoreMenu(
        val triggerId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 关闭更多菜单。 */
    object CloseMoreMenu : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── Viewport（S9/M6）─────────────────────────────────────────────────────

    /** 视口旋转/折叠准备阶段（冻结动效，触发 motion.interrupt.cancel）。 */
    object ViewportPrepare : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 视口重塑阶段。 */
    object ViewportReshape : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 视口稳定阶段。 */
    object ViewportSettle : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** 更新视口尺寸与类别。 */
    data class UpdateViewport(
        val viewportClass: ViewportClass,
        val widthDp: Int,
        val heightDp: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── 翻页（C2）────────────────────────────────────────────────────────────

    /** 翻到下一页。 */
    data class TurnPageNext(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 翻到上一页。 */
    data class TurnPagePrev(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── 章节跳转 ──────────────────────────────────────────────────────────────

    /** 跳转到指定章节（重置 page/progress）。 */
    data class JumpChapter(
        val chapterIndex: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── Motion interrupt（M4）────────────────────────────────────────────────

    /**
     * 主动触发 motion interrupt。注意：此类名与顶层 [MotionInterrupt] 状态类同名但分属
     * 不同作用域（`ReaderUiIntent.MotionInterrupt` vs 顶层 `MotionInterrupt`）。
     */
    data class MotionInterrupt(
        val kind: InterruptKind,
        val reason: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── AsyncResult（M5）─────────────────────────────────────────────────────

    /** 启动异步请求（reader entry / chapter load）。 */
    data class StartAsyncRequest(
        val fromRoute: String,
        val toRoute: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 完成异步请求（guard 校验 requestId 匹配后写入 value）。requestId 即异步请求 id。 */
    data class CompleteAsyncRequest(
        override val requestId: String,
        val value: Any?,
        val currentRoute: String
    ) : ReaderUiIntent()

    /** 取消异步请求。requestId 即异步请求 id。 */
    data class CancelAsyncRequest(
        override val requestId: String
    ) : ReaderUiIntent()

    // ── Motion phase（M2）────────────────────────────────────────────────────

    /** 更新 UI 侧动效阶段。 */
    data class UpdateMotionPhase(
        val phase: MotionPhase,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── P3: Source Import e2e ─────────────────────────────────────────────────

    /** P3: 解析书源 JSON → Preview 状态。 */
    data class ParseSourceImport(
        val json: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 解析完成，写入 Preview 状态。 */
    data class CompleteSourceImportParse(
        val entries: List<SourceImportPreviewEntry>,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 确认导入（从 Preview → Importing）。 */
    data class ConfirmSourceImport(
        val conflictMode: String = "跳过重复",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 导入完成（Importing → Done）。 */
    data class CompleteSourceImport(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 导入失败（Importing → Error）。 */
    data class FailSourceImport(
        val message: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 取消导入 / 关闭结果（回到 Idle）。 */
    object DismissSourceImportResult : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P3: WebDAV config ─────────────────────────────────────────────────────

    /** P3: 更新 WebDAV 服务器地址。 */
    data class UpdateWebDavServer(
        val serverUrl: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 更新 WebDAV 凭据。 */
    data class UpdateWebDavCredentials(
        val username: String,
        val password: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 测试 WebDAV 连接（→ Testing）。 */
    object TestWebDavConnection : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P3: WebDAV 测试结果。 */
    data class WebDavTestResult(
        val success: Boolean,
        val message: String,
        val latencyMs: Long = 0L,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 保存 WebDAV 配置（→ Saving）。 */
    object SaveWebDavConfig : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P3: WebDAV 保存结果。 */
    data class WebDavSaveResult(
        val success: Boolean,
        val savedIdentifier: String? = null,
        val message: String = "",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 吊销 WebDAV 配置。 */
    object RevokeWebDavConfig : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P3: 书源管理 ──────────────────────────────────────────────────────────

    /** P3.3: 切换书源启用状态（dispatch source.enable/disable）。 */
    data class SetSourceEnabled(
        val sourceId: String,
        val enabled: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── P3: 通用设置 action ───────────────────────────────────────────────────

    /** P3.4: 清理全部逻辑缓存（Core `cache.clear`, scope=cache）。 */
    data class ClearCache(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 从 live ReaderContext 读取 sourceId/bookId 并刷新本书缓存状态。 */
    data class RefreshCurrentBookCache(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /**
     * 从当前章节派生 Core 的半开区间。UI 不允许直接提供 sourceId/bookId
     * 或绝对章节范围，避免 fixture/过期身份越过当前阅读上下文。
     */
    data class PrefetchCurrentBookCache(
        val includeCurrentChapter: Boolean,
        val chapterCount: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 清理 live ReaderContext 对应的单本缓存。 */
    data class ClearCurrentBookCache(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Core cache command terminal success; stale request ids are ignored. */
    data class CacheActionSucceeded(
        val action: ReaderCacheAction,
        val snapshot: ReaderBookCacheUiState? = null,
        /** True when a mutation committed but no trustworthy post-mutation snapshot exists. */
        val projectionInvalidated: Boolean = false,
        /** Non-null only when the mutation committed and its follow-up status refresh failed. */
        val refreshError: String? = null,
        val message: String,
        override val requestId: String
    ) : ReaderUiIntent()

    /** Core cache command terminal failure; stale request ids are ignored. */
    data class CacheActionFailed(
        val message: String,
        override val requestId: String
    ) : ReaderUiIntent()

    /** P3.4: 恢复默认设置（重置 App 主题/语言/启动页/行为偏好）。 */
    object RestoreDefaultSettings : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P3: Permission ────────────────────────────────────────────────────────

    /** P3: 请求权限（触发系统 dialog）。 */
    data class RequestPermission(
        val kind: PermissionKind,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 权限已授予。 */
    data class PermissionGranted(
        val kind: PermissionKind,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 权限被拒绝。 */
    data class PermissionDenied(
        val kind: PermissionKind,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P3: 打开系统权限设置页。 */
    object OpenSystemPermissionSettings : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P4: RSS list ──────────────────────────────────────────────────────────

    /**
     * P4: 触发 RSS 列表加载。reducer 转入 [com.reader.ui.shell.RssListState.Loading]；
     * 实际拉取由 UI 层通过 Core bridge 发起（TODO(core-blocker): `rss.list` /
     * `rss.item.read` / `rss.subscription.*` / `rss.source.*` 待 Core 落地）。
     */
    object LoadRssList : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P4: RSS 列表加载成功（订阅源数量由 host 根据 Room 持久化数据回填）。 */
    data class RssListLoaded(
        val sourceCount: Int,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P4: RSS 列表为空（无订阅源）。 */
    object RssListEmpty : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P4: RSS 列表加载失败。 */
    data class RssListLoadFailed(
        val message: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P4: 重置 RSS 列表状态回 Idle。 */
    object DismissRssListResult : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── Slice D: HostRequest dispatch ──────────────────────────────────────

    /**
     * Slice D — 派发一个 HostRequest 到 host 层。Reducer 把它加入
     * [ReaderUiState.pendingHostRequests] 队列;UI 层观察队列并实际调用
     * [com.reader.host.HostAdapter.dispatch]。完成后 UI 派发
     * [HostRequestComplete] 或 [HostRequestError] 回 Reducer。
     *
     * @property capability host 能力名,如 `tts.system.start`、
     *   `permission.check`、`notification.show`、`share.invoke`、
     *   `clipboard.copy`、`device.vibrate`。
     * @property paramsJson 能力参数 JSON。
     */
    data class DispatchHostRequest(
        val capability: String,
        val paramsJson: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Slice D — HostRequest 成功完成。 */
    data class HostRequestComplete(
        override val requestId: String,
        val capability: String,
        val resultJson: String
    ) : ReaderUiIntent()

    /** Slice D — HostRequest 失败。 */
    data class HostRequestError(
        override val requestId: String,
        val capability: String,
        val errorCode: String,
        val errorMessage: String
    ) : ReaderUiIntent()

    /** Slice D — 清除 lastHostRequestResult。 */
    object ClearHostRequestResult : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P0: Source Switch 专用 intents ───────────────────────────────────────
    // 不再走通用 PushRoute，让 reducer 可追踪换源的 loading/results/selected 状态。

    /** P0: 打开换源页（→ Loading，并 push SourceSwitchFlow route）。
     *  bookId 为书唯一标识；bookName/sourceId 用于在无 readerContext 时
     *  （如从 book-detail 直接进入换源）构造 fallback ReaderContext，
     *  避免 FlowShell 回退到 demo 标题。 */
    data class SourceSwitchOpen(
        val bookId: String,
        val bookName: String = "",
        val sourceId: String = "",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 关闭换源页（→ Idle，并 pop route）。 */
    object SourceSwitchClose : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P0: 选择源（→ Results.selectedSourceId = sourceId）。 */
    data class SourceSwitchSelect(
        val sourceId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 确认切换到选中源（pop route + 重置 sourceSwitch = Idle）。 */
    data class SourceSwitchConfirm(
        val sourceId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 取消换源（pop route + 重置 sourceSwitch = Idle）。 */
    object SourceSwitchCancel : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P0: 换源结果已就绪（→ Results）。 */
    data class SourceSwitchResultsLoaded(
        val results: List<SourceSwitchResult>,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── P0: book-detail 专用 intents ──────────────────────────────────────────
    // 让 reducer 可追踪 book-detail 的加载/就绪/错误三态，满足
    // `book-detail-error-requires-error-pagestate` 契约（error 非空时 pageState 必须为 error）。

    /** P0: 打开 book-detail（→ Loading，并 push BookState("book-detail") route）。 */
    data class BookDetailOpen(
        val bookUrl: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: book-detail 数据加载完成（→ Ready）。 */
    data class BookDetailLoaded(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: book-detail 加载失败（→ Error，pageState=error）。 */
    data class BookDetailLoadFailed(
        val message: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 关闭 book-detail（→ Idle，并 pop route）。 */
    object BookDetailClose : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── P0: settings 专用 intents ─────────────────────────────────────────────
    // 让 reducer 可追踪 settings 内部子 tab 切换与 overlay 展开状态，满足
    // `settings-overlay-guard-tab-switch` 契约（overlay 展开时禁止 tab 切换）。

    /** P0: 打开 settings 子页（→ push 对应 route，更新 activeTab）。 */
    data class SettingsOpen(
        val tab: SettingsTab = SettingsTab.GENERAL,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 关闭 settings 子页（→ pop route）。 */
    object SettingsClose : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P0: 切换 settings 内部子 tab（被 overlay 守卫拦截时为 no-op）。 */
    data class SettingsTabSwitch(
        val tab: SettingsTab,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** P0: 展开 settings overlay（→ EXPANDED_OPTION，禁止 tab 切换）。 */
    object SettingsOverlayExpand : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** P0: 收起 settings overlay（→ NONE，恢复 tab 切换）。 */
    object SettingsOverlayCollapse : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── 阅读设置面板交互（hideStatusBar / 行为开关 / 单选）──────────────────────
    // 这些 intent 让 reducer 可追踪阅读器设置面板的交互状态，满足
    // "所有交互元素必须接线到 reducer dispatch" 的契约要求。

    /** 切换"隐藏状态栏"开关（由 WindowInsetsControllerCompat 实施）。 */
    data class SetHideStatusBar(
        val enabled: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新阅读行为开关（autoPage/volumeKey/landscape/keepScreenOn/footerInfo/touchFeedback/autoCache 等）。 */
    data class SetReaderBehaviorToggle(
        val key: String,
        val enabled: Boolean,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** 更新阅读设置单选项（pageTurnMethod/pageTurnAnimation/ttsRate/ttsVoice/ttsRange/ttsTimer 等）。 */
    data class SetReaderChoice(
        val key: String,
        val value: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    // ── W3: 书源编辑（source-edit）专用 intents ──────────────────────────────

    /** W3: 打开书源编辑页（push SourceEdit route + 进入 Editing 状态）。 */
    data class SourceEditOpen(
        val sourceId: String = "",
        val name: String = "",
        val url: String = "",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W3: 更新书源编辑表单字段。 */
    data class SourceEditUpdateField(
        val name: String? = null,
        val url: String? = null,
        val group: String? = null,
        val enabled: Boolean? = null,
        val comment: String? = null,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W3: 保存书源（-> Saving）。 */
    object SourceEditSave : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** W3: 取消书源编辑（pop route + 重置 sourceEdit = Idle）。 */
    object SourceEditCancel : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    /** W3: 书源保存完成（-> Saved 或 Error）。 */
    data class SourceEditResult(
        val success: Boolean,
        val message: String = "",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W3: 打开书源详情页（push SourceDetail route）。 */
    data class SourceDetailOpen(
        val sourceId: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W3: 关闭书源详情页（pop route）。 */
    object SourceDetailClose : ReaderUiIntent() {
        override val requestId: String = generateRequestId()
    }

    // ── W5: 内容替换规则 CRUD intents ────────────────────────────────────────

    /** W5: 添加替换规则。 */
    data class ReplaceRuleAdd(
        val name: String,
        val pattern: String,
        val replacement: String = "",
        val scope: String = "all",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W5: 更新替换规则。 */
    data class ReplaceRuleUpdate(
        val id: String,
        val name: String? = null,
        val pattern: String? = null,
        val replacement: String? = null,
        val scope: String? = null,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W5: 删除替换规则。 */
    data class ReplaceRuleDelete(
        val id: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W5: 切换替换规则启用状态。 */
    data class ReplaceRuleToggle(
        val id: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** W5: 加载替换规则列表（从 Core 拉取后回填）。 */
    data class ReplaceRulesLoaded(
        val rules: List<ReplaceRule>,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Visible native page entry: load the complete current rule set from Core. */
    data class LoadReplaceRules(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Correlated terminal result of [LoadReplaceRules]. */
    data class ReplaceRulesHydrated(
        val rules: List<ReplaceRule>,
        override val requestId: String
    ) : ReaderUiIntent()

    /** Exact private-storage token restored at ViewModel construction; never synthesized by UI. */
    data class RestoreReplaceUndoToken(
        val undoTokenJson: String,
        val undoOperation: String,
        val undoRuleId: Long,
        val undoExpiresAt: Long,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Native Core-backed editor create; separate from the legacy Shadow reducer intent. */
    data class PersistReplaceRuleCreate(
        val name: String,
        val pattern: String,
        val replacement: String = "",
        val scope: String = "all",
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Native Core-backed editor update; requires a numeric Core rule id. */
    data class PersistReplaceRuleUpdate(
        val id: String,
        val name: String? = null,
        val pattern: String? = null,
        val replacement: String? = null,
        val scope: String? = null,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Native Core-backed editor delete; requires a numeric Core rule id. */
    data class PersistReplaceRuleDelete(
        val id: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Native Core-backed editor enable/disable mutation. */
    data class PersistReplaceRuleToggle(
        val id: String,
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Uses only the opaque token retained from the latest Core replace.persist result. */
    data class UndoLastReplace(
        override val requestId: String = generateRequestId()
    ) : ReaderUiIntent()

    /** Core replace.persist terminal success. Token bytes are never supplied by UI controls. */
    data class ReplacePersistSucceeded(
        val action: ReaderReplaceMutationAction,
        val rule: ReplaceRule?,
        val deletedRuleId: Long?,
        val undoTokenJson: String,
        val undoOperation: String,
        val undoRuleId: Long,
        val undoExpiresAt: Long,
        val undoTokenPersisted: Boolean,
        override val requestId: String
    ) : ReaderUiIntent()

    /** Core replace.undo terminal success. */
    data class ReplaceUndoSucceeded(
        val operation: String,
        val ruleId: Long,
        val changed: Boolean,
        val restoredRule: ReplaceRule?,
        override val requestId: String
    ) : ReaderUiIntent()

    /** Core replace.persist / replace.undo terminal failure. */
    data class ReplaceCoreActionFailed(
        val message: String,
        override val requestId: String
    ) : ReaderUiIntent()
}

private val requestCounter = java.util.concurrent.atomic.AtomicLong(0)

/** Monotonic request id generator — unique per process, deterministic enough for tests. */
fun generateRequestId(): String =
    "req-" + requestCounter.incrementAndGet()
