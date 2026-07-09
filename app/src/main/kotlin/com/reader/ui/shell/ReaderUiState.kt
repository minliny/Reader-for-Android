package com.reader.ui.shell

import com.reader.api.Book
import io.reader.ui.contract.RouteShell

/**
 * Single source of truth for the App Shell UI state, aligned with
 * `docs/cross-platform-ui/CROSS_PLATFORM_STATE_MATRIX.md` and the reducer rule in
 * `docs/ui-handoff/MOTION_PLATFORM_MAPPING.md`:
 *
 * > 动画必须由单一 UI state/reducer 驱动，避免多个 `Animatable` 在打断时各自收尾。
 *
 * All route / session / overlay / focus / async-result outcomes MUST be explainable from
 * an instance of [ReaderUiState] (UI_PLATFORM_EVIDENCE_REQUESTS.md rejection rule).
 */

/**
 * The four canonical main tabs. Order is fixed by
 * `docs/cross-platform-ui/CROSS_PLATFORM_UI_BASELINE.md`:
 * 1. 书架 (Bookshelf)  2. 发现 (Discover)  3. RSS  4. 设置 (Settings).
 *
 * Search, reader, bookshelf-management, local-import, settings subpages, and source
 * management are explicitly NOT main tabs.
 */
enum class MainTab(val routeId: String, val label: String) {
    BOOKSHELF("bookshelf", "书架"),
    DISCOVER("discover", "发现"),
    RSS("rss", "RSS"),
    SETTINGS("settings", "设置");

    companion object {
        val ORDER: List<MainTab> = listOf(BOOKSHELF, DISCOVER, RSS, SETTINGS)
    }
}

/**
 * Reader entry path — drives which Motion ID fires on entry
 * (`reader.entry.coverToImmersive` vs `reader.entry.actionToImmersive`).
 */
enum class ReaderEntry(val motionId: String) {
    COVER_TO_IMMERSIVE("reader.entry.coverToImmersive"),
    ACTION_TO_IMMERSIVE("reader.entry.actionToImmersive")
}

/**
 * Reader-scoped context. Survives orientation, fold, control-layer show/hide, and session
 * start (STATE_MATRIX / MOTION_EFFECTS.md §6). Reinitialized only on a fresh reader entry.
 */
data class ReaderContext(
    val sourceId: String,
    val bookUrl: String,
    val bookName: String,
    val entry: ReaderEntry,
    val chapterIndex: Int = 0,
    /** requestId of the entry intent that created this context — used by async-result guard. */
    val entryRequestId: String,
    // 阅读进度与排版状态（S4）：跨旋转/折叠/控制层显隐/会话启停保留，仅在全新 reader entry 时重置
    /** 当前页序号。 */
    val page: Int = 0,
    /** 当前章节阅读进度 0f..1f。 */
    val progress: Float = 0f,
    /** 主题 id：paper/warm/green/blue/paper-night/warm-night/green-night/blue-night。 */
    val themeId: String = "paper",
    /** 屏幕亮度 0f..1f。 */
    val brightness: Float = 0.5f,
    /** 是否自动亮度。 */
    val brightnessAuto: Boolean = true,
    /** 正文字号 sp。 */
    val fontSize: Float = 18f,
    /** 行距倍数。 */
    val lineSpacing: Float = 1.55f,
    /** 页边距 dp。 */
    val pageMargin: Float = 16f
)

/** Single running session. autoPage and tts are mutually exclusive (one activeSession). */
data class ActiveSession(
    val type: SessionType,
    val playing: Boolean,
    /** 自动翻页倒计时秒数（AUTO_PAGE 专用）。 */
    val countdownSeconds: Int = 0,
    /** TTS 当前句序（TTS 专用）。 */
    val ttsSentenceIndex: Int = 0,
    /** TTS 当前章节序号（TTS 专用）。 */
    val ttsChapterIndex: Int = 0
)

enum class SessionType { AUTO_PAGE, TTS, NONE }

/**
 * Overlay state. Slice 4 will populate keyboard/sheet/dialog; Slice 1/2 only needs `None`
 * so the reducer can clear it on tab switch / route push (interrupt rule).
 */
sealed class OverlayState {
    object None : OverlayState()
    /** 软键盘浮层（输入框聚焦时）。 */
    data class Keyboard(val inputId: String) : OverlayState()
    /** 底部/侧边 Sheet 浮层。 */
    data class Sheet(val content: SheetContent) : OverlayState()
    /** 居中 Dialog 浮层。 */
    data class Dialog(val content: DialogContent) : OverlayState()
}

/** Sheet 浮层内容契约（S2）。 */
sealed class SheetContent {
    /** 阅读器设置面板（module: directory/tts/appearance/settings）。 */
    data class ReaderSetting(val module: String) : SheetContent()
    /** 书架筛选面板。 */
    data class BookshelfFilter(val filterId: String) : SheetContent()
}

/** Dialog 浮层内容契约（S2）。 */
sealed class DialogContent {
    /** 通用确认弹窗。 */
    data class Confirm(
        val title: String,
        val message: String,
        val onConfirm: () -> Unit
    ) : DialogContent()
    /** 书源切换确认弹窗。 */
    data class SourceSwitch(val sourceId: String) : DialogContent()
}

/** Interrupt kind, mirroring `motion.interrupt.cancel / redirect / completeThenReplace`. */
enum class InterruptKind(val motionId: String) {
    CANCEL("motion.interrupt.cancel"),
    REDIRECT("motion.interrupt.redirect"),
    COMPLETE_THEN_REPLACE("motion.interrupt.completeThenReplace")
}

/**
 * Latest interrupt record. Old transitions cancel or redirect to the latest intent target;
 * the record makes the final state explainable (UI_PLATFORM_EVIDENCE_REQUESTS.md).
 */
data class MotionInterrupt(
    val requestId: String,
    val from: String,
    val to: String,
    val kind: InterruptKind
)

/**
 * 动效阶段（M2）—— UI 侧三态。reducer 据此驱动 ENTERING/LEAVING/SETTLED，
 * 避免多个 Animatable 在打断时各自收尾。
 *
 * 注意：与 `com.reader.ui.motion.MotionPhase`（RUNNING/INTERRUPTED/SETTLED，
 * 描述 MotionController transaction 阶段）是不同枚举，分属不同包，互不冲突。
 */
enum class MotionPhase { ENTERING, LEAVING, SETTLED }

/**
 * Routes the reducer owns. Tab roots are NOT pushable — switching tabs only mutates
 * [ReaderUiState.activeTab] and never grows [ReaderUiState.backStack]
 * (FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 1: "不把 tab switch 写成 route push").
 *
 * `immersive-reading` is the final-state route for reader entry. `reader` and its reader
 * module routes are explicit pushed routes opened from immersive tap zones; they never
 * auto-open on entry.
 */
sealed class ReaderRoute {
    /** Main tab shell is the back-stack root; never pushed/popped. */
    data class TabShell(val tab: MainTab) : ReaderRoute() {
        val routeId: String get() = tab.routeId
    }

    /**
     * `immersive-reading`. Final state of `reader.entry.coverToImmersive` /
     * `reader.entry.actionToImmersive`. Carries the [ReaderContext] and entry requestId so
     * the async-result guard can discard stale chapter loads.
     */
    data class ImmersiveReading(val context: ReaderContext) : ReaderRoute() {
        val routeId: String get() = RouteIds.IMMERSIVE_READING
    }

    /** `reader` control layer and reader module routes opened over the same reading surface. */
    data class ReaderControl(
        val id: String = RouteIds.READER_CONTROL,
        val context: ReaderContext? = null
    ) : ReaderRoute() {
        val routeId: String get() = id
    }

    /**
     * `source-switch` is a FlowShell route, not a ReaderShell module panel. It keeps the
     * reader context so the flow can render above the previous reading/control layer.
     */
    data class SourceSwitchFlow(
        val context: ReaderContext? = null
    ) : ReaderRoute() {
        val routeId: String get() = RouteIds.SOURCE_SWITCH
    }

    /** Pushed from Bookshelf top bar — not a main tab (Slice 1). */
    object Search : ReaderRoute() {
        const val routeId: String = RouteIds.BOOK_SEARCH
    }

    /** Pushed from Bookshelf top bar — not a main tab (Slice 1). */
    object ImportSource : ReaderRoute() {
        const val routeId: String = RouteIds.SOURCE_IMPORT_PREVIEW
    }

    /** `book-batch-management` from the bookshelf more menu. */
    object BookBatchManagement : ReaderRoute() {
        const val routeId: String = RouteIds.BOOK_BATCH_MANAGEMENT
    }

    /** `group-management` from the bookshelf more menu. */
    object GroupManagement : ReaderRoute() {
        const val routeId: String = RouteIds.GROUP_MANAGEMENT
    }

    /** `local-import` from the bookshelf more menu / empty state. */
    object LocalImport : ReaderRoute() {
        const val routeId: String = RouteIds.LOCAL_IMPORT
    }

    /** `bookshelf-search-settings` from bookshelf display settings. */
    object BookshelfSearchSettings : ReaderRoute() {
        const val routeId: String = RouteIds.BOOKSHELF_SEARCH_SETTINGS
    }

    /** `settings-general` from the settings main tab. */
    object SettingsGeneral : ReaderRoute() {
        const val routeId: String = RouteIds.SETTINGS_GENERAL
    }

    /** `about-feedback` from the settings main tab. */
    object AboutFeedback : ReaderRoute() {
        const val routeId: String = RouteIds.ABOUT_FEEDBACK
    }

    /** `sync-backup` from the settings main tab. */
    object SyncBackup : ReaderRoute() {
        const val routeId: String = RouteIds.SYNC_BACKUP
    }

    /** `webdav-config` from sync/backup settings. */
    object WebDavConfig : ReaderRoute() {
        const val routeId: String = RouteIds.WEBDAV_CONFIG
    }

    /** `source-management` from the settings main tab. */
    object SourceManagement : ReaderRoute() {
        const val routeId: String = RouteIds.SOURCE_MANAGEMENT
    }

    /** `rss-search` from the RSS main tab search entry. */
    object RssSearch : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SEARCH
    }

    /** `rss-all` from the RSS mode nav. */
    object RssAll : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_ALL
    }

    /** `rss-starred` from the RSS mode nav. */
    object RssStarred : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_STARRED
    }

    /** `rss-refreshing` from RSS refresh/source actions. */
    object RssRefreshing : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_REFRESHING
    }

    /** `rss-subscription-management` from RSS manage/source actions. */
    object RssSubscriptionManagement : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SUBSCRIPTION_MANAGEMENT
    }

    /** `rss-detail` from an RSS article row. */
    object RssDetail : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_DETAIL
    }

    /** `rss-original` from RSS detail original-link actions. */
    object RssOriginal : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_ORIGINAL
    }

    /** `rss-original-browser` from the RSS original preview page. */
    object RssOriginalBrowser : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_ORIGINAL_BROWSER
    }

    /** `rss-source-edit` from RSS subscription management. */
    object RssSourceEdit : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_EDIT
    }

    /** `rss-source-import` from RSS subscription management. */
    object RssSourceImport : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_IMPORT
    }

    /** `rss-source-import-detail` from the RSS import preview list. */
    object RssSourceImportDetail : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_IMPORT_DETAIL
    }

    /** `rss-source-import-result` from the RSS import confirmation action. */
    object RssSourceImportResult : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_IMPORT_RESULT
    }

    /** `rss-rule-subscription` from RSS subscription management. */
    object RssRuleSubscription : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RULE_SUBSCRIPTION
    }

    /** `rss-rule-subscription-detail` from RSS rule subscription rows. */
    object RssRuleSubscriptionDetail : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RULE_SUBSCRIPTION_DETAIL
    }

    /** `rss-rule-subscription-edit` from RSS rule subscription detail/create. */
    object RssRuleSubscriptionEdit : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RULE_SUBSCRIPTION_EDIT
    }

    /** `rss-rule-subscription-test` from RSS rule subscription edit. */
    object RssRuleSubscriptionTest : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RULE_SUBSCRIPTION_TEST
    }

    /** `rss-rule-subscription-apply` from RSS rule subscription detail. */
    object RssRuleSubscriptionApply : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RULE_SUBSCRIPTION_APPLY
    }

    /** `rss-source-groups` from RSS subscription management. */
    object RssSourceGroups : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_GROUPS
    }

    /** `rss-source-group-edit` from RSS source groups. */
    object RssSourceGroupEdit : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_GROUP_EDIT
    }

    /** `rss-source-actions` from an RSS source row. */
    object RssSourceActions : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_ACTIONS
    }

    /** `rss-source-batch` from the RSS batch row. */
    object RssSourceBatch : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_BATCH
    }

    /** `rss-source-export` from the RSS batch row. */
    object RssSourceExport : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_EXPORT
    }

    /** `rss-source-export-detail` from the RSS export preview list. */
    object RssSourceExportDetail : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_EXPORT_DETAIL
    }

    /** `rss-source-export-result` from the RSS export action. */
    object RssSourceExportResult : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_EXPORT_RESULT
    }

    /** `rss-source-batch-disable` from RSS batch actions. */
    object RssSourceBatchDisable : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_BATCH_DISABLE
    }

    /** `rss-source-debug` from RSS source edit / source actions. */
    object RssSourceDebug : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_DEBUG
    }

    /** `rss-source-vars` from RSS source actions. */
    object RssSourceVars : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_VARS
    }

    /** `rss-source-login` from RSS source actions. */
    object RssSourceLogin : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_LOGIN
    }

    /** `rss-source-login-web` from RSS source login. */
    object RssSourceLoginWeb : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_LOGIN_WEB
    }

    /** `rss-source-login-cookie` from RSS source login/web login. */
    object RssSourceLoginCookie : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_LOGIN_COOKIE
    }

    /** `rss-source-login-clear` from RSS source login. */
    object RssSourceLoginClear : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_LOGIN_CLEAR
    }

    /** `rss-source-pin` from RSS source actions. */
    object RssSourcePin : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_PIN
    }

    /** `rss-source-disable` from RSS source actions. */
    object RssSourceDisable : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_SOURCE_DISABLE
    }

    /** `rss-read-record` from RSS source/detail actions. */
    object RssReadRecord : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_READ_RECORD
    }

    /** `rss-record-clear` from RSS read-record. */
    object RssRecordClear : ReaderRoute() {
        const val routeId: String = RouteIds.RSS_RECORD_CLEAR
    }

    /** Bookshelf/book demo states that collapse into the bookshelf/book native flow. */
    data class BookState(val id: String, val book: Book? = null) : ReaderRoute() {
        val routeId: String get() = id
    }

    /** RSS demo states that collapse into the RSS native flow. */
    data class RssState(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }

    /** Restore demo states that collapse into one restore native flow. */
    data class RestoreState(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }

    /** Discover demo states that collapse into the Discover native state model. */
    data class DiscoverState(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }

    /** Source management/debug demo states that collapse into source native flows. */
    data class SourceState(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }

    /** Native fallback for demo-authored routes that do not yet have bespoke Compose screens. */
    data class Demo(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }
}

/** Canonical route ids (match `frontend-demo/route-contract.js` for the Slice 1/2 subset). */
object RouteIds {
    const val IMMERSIVE_READING = "immersive-reading"
    const val READER_CONTROL = "reader"
    const val READER_TOC_BOOKMARKS = "toc-bookmarks"
    const val READER_APPEARANCE = "reader-appearance"
    const val READER_TTS = "tts"
    const val READER_AUTO_PAGE = "auto-page"
    const val READER_CONTENT_SEARCH = "content-search"
    const val READER_CONTENT_REPLACEMENT = "content-replacement"
    const val READER_SETTINGS = "reader-settings"
    const val READER_FULL_DIRECTORY = "reader-full-directory"
    const val READER_FULL_TTS = "reader-full-tts"
    const val READER_FULL_APPEARANCE = "reader-full-appearance"
    const val READER_FULL_SETTINGS = "reader-full-settings"
    const val READER_BOOK_CACHE = "reader-book-cache"
    const val READER_DEBUG_INFO = "reader-debug-info"
    const val SOURCE_SWITCH = "source-switch"
    const val BOOK_SEARCH = "book-search"
    const val SOURCE_IMPORT_PREVIEW = "source-import-preview"
    const val BOOK_BATCH_MANAGEMENT = "book-batch-management"
    const val GROUP_MANAGEMENT = "group-management"
    const val LOCAL_IMPORT = "local-import"
    const val BOOKSHELF_SEARCH_SETTINGS = "bookshelf-search-settings"
    const val SETTINGS_GENERAL = "settings-general"
    const val ABOUT_FEEDBACK = "about-feedback"
    const val SYNC_BACKUP = "sync-backup"
    const val WEBDAV_CONFIG = "webdav-config"
    const val SOURCE_MANAGEMENT = "source-management"
    const val RSS_SEARCH = "rss-search"
    const val RSS_ALL = "rss-all"
    const val RSS_STARRED = "rss-starred"
    const val RSS_REFRESHING = "rss-refreshing"
    const val RSS_SUBSCRIPTION_MANAGEMENT = "rss-subscription-management"
    const val RSS_DETAIL = "rss-detail"
    const val RSS_ORIGINAL = "rss-original"
    const val RSS_ORIGINAL_BROWSER = "rss-original-browser"
    const val RSS_SOURCE_EDIT = "rss-source-edit"
    const val RSS_SOURCE_IMPORT = "rss-source-import"
    const val RSS_SOURCE_IMPORT_DETAIL = "rss-source-import-detail"
    const val RSS_SOURCE_IMPORT_RESULT = "rss-source-import-result"
    const val RSS_RULE_SUBSCRIPTION = "rss-rule-subscription"
    const val RSS_RULE_SUBSCRIPTION_DETAIL = "rss-rule-subscription-detail"
    const val RSS_RULE_SUBSCRIPTION_EDIT = "rss-rule-subscription-edit"
    const val RSS_RULE_SUBSCRIPTION_TEST = "rss-rule-subscription-test"
    const val RSS_RULE_SUBSCRIPTION_APPLY = "rss-rule-subscription-apply"
    const val RSS_SOURCE_GROUPS = "rss-source-groups"
    const val RSS_SOURCE_GROUP_EDIT = "rss-source-group-edit"
    const val RSS_SOURCE_ACTIONS = "rss-source-actions"
    const val RSS_SOURCE_BATCH = "rss-source-batch"
    const val RSS_SOURCE_EXPORT = "rss-source-export"
    const val RSS_SOURCE_EXPORT_DETAIL = "rss-source-export-detail"
    const val RSS_SOURCE_EXPORT_RESULT = "rss-source-export-result"
    const val RSS_SOURCE_BATCH_DISABLE = "rss-source-batch-disable"
    const val RSS_SOURCE_DEBUG = "rss-source-debug"
    const val RSS_SOURCE_VARS = "rss-source-vars"
    const val RSS_SOURCE_LOGIN = "rss-source-login"
    const val RSS_SOURCE_LOGIN_WEB = "rss-source-login-web"
    const val RSS_SOURCE_LOGIN_COOKIE = "rss-source-login-cookie"
    const val RSS_SOURCE_LOGIN_CLEAR = "rss-source-login-clear"
    const val RSS_SOURCE_PIN = "rss-source-pin"
    const val RSS_SOURCE_DISABLE = "rss-source-disable"
    const val RSS_READ_RECORD = "rss-read-record"
    const val RSS_RECORD_CLEAR = "rss-record-clear"
}

val ReaderRoute.routeId: String
    get() = when (this) {
        is ReaderRoute.TabShell -> tab.routeId
        is ReaderRoute.ImmersiveReading -> RouteIds.IMMERSIVE_READING
        is ReaderRoute.ReaderControl -> routeId
        is ReaderRoute.SourceSwitchFlow -> routeId
        ReaderRoute.Search -> ReaderRoute.Search.routeId
        ReaderRoute.ImportSource -> ReaderRoute.ImportSource.routeId
        ReaderRoute.BookBatchManagement -> ReaderRoute.BookBatchManagement.routeId
        ReaderRoute.GroupManagement -> ReaderRoute.GroupManagement.routeId
        ReaderRoute.LocalImport -> ReaderRoute.LocalImport.routeId
        ReaderRoute.BookshelfSearchSettings -> ReaderRoute.BookshelfSearchSettings.routeId
        ReaderRoute.SettingsGeneral -> ReaderRoute.SettingsGeneral.routeId
        ReaderRoute.AboutFeedback -> ReaderRoute.AboutFeedback.routeId
        ReaderRoute.SyncBackup -> ReaderRoute.SyncBackup.routeId
        ReaderRoute.WebDavConfig -> ReaderRoute.WebDavConfig.routeId
        ReaderRoute.SourceManagement -> ReaderRoute.SourceManagement.routeId
        ReaderRoute.RssSearch -> ReaderRoute.RssSearch.routeId
        ReaderRoute.RssAll -> ReaderRoute.RssAll.routeId
        ReaderRoute.RssStarred -> ReaderRoute.RssStarred.routeId
        ReaderRoute.RssRefreshing -> ReaderRoute.RssRefreshing.routeId
        ReaderRoute.RssSubscriptionManagement -> ReaderRoute.RssSubscriptionManagement.routeId
        ReaderRoute.RssDetail -> ReaderRoute.RssDetail.routeId
        ReaderRoute.RssOriginal -> ReaderRoute.RssOriginal.routeId
        ReaderRoute.RssOriginalBrowser -> ReaderRoute.RssOriginalBrowser.routeId
        ReaderRoute.RssSourceEdit -> ReaderRoute.RssSourceEdit.routeId
        ReaderRoute.RssSourceImport -> ReaderRoute.RssSourceImport.routeId
        ReaderRoute.RssSourceImportDetail -> ReaderRoute.RssSourceImportDetail.routeId
        ReaderRoute.RssSourceImportResult -> ReaderRoute.RssSourceImportResult.routeId
        ReaderRoute.RssRuleSubscription -> ReaderRoute.RssRuleSubscription.routeId
        ReaderRoute.RssRuleSubscriptionDetail -> ReaderRoute.RssRuleSubscriptionDetail.routeId
        ReaderRoute.RssRuleSubscriptionEdit -> ReaderRoute.RssRuleSubscriptionEdit.routeId
        ReaderRoute.RssRuleSubscriptionTest -> ReaderRoute.RssRuleSubscriptionTest.routeId
        ReaderRoute.RssRuleSubscriptionApply -> ReaderRoute.RssRuleSubscriptionApply.routeId
        ReaderRoute.RssSourceGroups -> ReaderRoute.RssSourceGroups.routeId
        ReaderRoute.RssSourceGroupEdit -> ReaderRoute.RssSourceGroupEdit.routeId
        ReaderRoute.RssSourceActions -> ReaderRoute.RssSourceActions.routeId
        ReaderRoute.RssSourceBatch -> ReaderRoute.RssSourceBatch.routeId
        ReaderRoute.RssSourceExport -> ReaderRoute.RssSourceExport.routeId
        ReaderRoute.RssSourceExportDetail -> ReaderRoute.RssSourceExportDetail.routeId
        ReaderRoute.RssSourceExportResult -> ReaderRoute.RssSourceExportResult.routeId
        ReaderRoute.RssSourceBatchDisable -> ReaderRoute.RssSourceBatchDisable.routeId
        ReaderRoute.RssSourceDebug -> ReaderRoute.RssSourceDebug.routeId
        ReaderRoute.RssSourceVars -> ReaderRoute.RssSourceVars.routeId
        ReaderRoute.RssSourceLogin -> ReaderRoute.RssSourceLogin.routeId
        ReaderRoute.RssSourceLoginWeb -> ReaderRoute.RssSourceLoginWeb.routeId
        ReaderRoute.RssSourceLoginCookie -> ReaderRoute.RssSourceLoginCookie.routeId
        ReaderRoute.RssSourceLoginClear -> ReaderRoute.RssSourceLoginClear.routeId
        ReaderRoute.RssSourcePin -> ReaderRoute.RssSourcePin.routeId
        ReaderRoute.RssSourceDisable -> ReaderRoute.RssSourceDisable.routeId
        ReaderRoute.RssReadRecord -> ReaderRoute.RssReadRecord.routeId
        ReaderRoute.RssRecordClear -> ReaderRoute.RssRecordClear.routeId
        is ReaderRoute.BookState -> routeId
        is ReaderRoute.RssState -> routeId
        is ReaderRoute.RestoreState -> routeId
        is ReaderRoute.DiscoverState -> routeId
        is ReaderRoute.SourceState -> routeId
        is ReaderRoute.Demo -> routeId
    }

/**
 * 将 [ReaderRoute] 映射到合同 [RouteShell] 枚举，供 MotionPolicyAdapter 解析 motion policy 使用。
 *
 * 映射规则对齐 generated/kotlin/Route.kt 的 RouteId → RouteShell 表：
 * - 主 tab（书架/发现/RSS/设置）→ MainTabShell
 * - 阅读器沉浸/控制层/换源 → ReaderShell / FlowShell
 * - 书籍详情/目录/搜索/书源相关 → LibraryShell
 * - 设置/同步/WebDAV/源管理 → SettingsShell
 */
fun ReaderRoute.shell(): RouteShell = when (this) {
    is ReaderRoute.TabShell -> RouteShell.MainTabShell
    is ReaderRoute.ImmersiveReading -> RouteShell.ReaderShell
    is ReaderRoute.ReaderControl -> RouteShell.ReaderShell
    is ReaderRoute.SourceSwitchFlow -> RouteShell.FlowShell
    ReaderRoute.Search -> RouteShell.LibraryShell
    ReaderRoute.ImportSource -> RouteShell.LibraryShell
    ReaderRoute.BookBatchManagement -> RouteShell.LibraryShell
    ReaderRoute.GroupManagement -> RouteShell.LibraryShell
    ReaderRoute.LocalImport -> RouteShell.LibraryShell
    ReaderRoute.BookshelfSearchSettings -> RouteShell.LibraryShell
    ReaderRoute.SettingsGeneral -> RouteShell.SettingsShell
    ReaderRoute.AboutFeedback -> RouteShell.SettingsShell
    ReaderRoute.SyncBackup -> RouteShell.SettingsShell
    ReaderRoute.WebDavConfig -> RouteShell.SettingsShell
    ReaderRoute.SourceManagement -> RouteShell.SettingsShell
    ReaderRoute.RssSearch -> RouteShell.LibraryShell
    ReaderRoute.RssAll -> RouteShell.LibraryShell
    ReaderRoute.RssStarred -> RouteShell.LibraryShell
    ReaderRoute.RssRefreshing -> RouteShell.LibraryShell
    ReaderRoute.RssSubscriptionManagement -> RouteShell.LibraryShell
    ReaderRoute.RssDetail -> RouteShell.LibraryShell
    ReaderRoute.RssOriginal -> RouteShell.LibraryShell
    ReaderRoute.RssOriginalBrowser -> RouteShell.LibraryShell
    ReaderRoute.RssSourceEdit -> RouteShell.LibraryShell
    ReaderRoute.RssSourceImport -> RouteShell.LibraryShell
    ReaderRoute.RssSourceImportDetail -> RouteShell.LibraryShell
    ReaderRoute.RssSourceImportResult -> RouteShell.LibraryShell
    ReaderRoute.RssRuleSubscription -> RouteShell.LibraryShell
    ReaderRoute.RssRuleSubscriptionDetail -> RouteShell.LibraryShell
    ReaderRoute.RssRuleSubscriptionEdit -> RouteShell.LibraryShell
    ReaderRoute.RssRuleSubscriptionTest -> RouteShell.LibraryShell
    ReaderRoute.RssRuleSubscriptionApply -> RouteShell.LibraryShell
    ReaderRoute.RssSourceGroups -> RouteShell.LibraryShell
    ReaderRoute.RssSourceGroupEdit -> RouteShell.LibraryShell
    ReaderRoute.RssSourceActions -> RouteShell.LibraryShell
    ReaderRoute.RssSourceBatch -> RouteShell.LibraryShell
    ReaderRoute.RssSourceExport -> RouteShell.LibraryShell
    ReaderRoute.RssSourceExportDetail -> RouteShell.LibraryShell
    ReaderRoute.RssSourceExportResult -> RouteShell.LibraryShell
    ReaderRoute.RssSourceBatchDisable -> RouteShell.LibraryShell
    ReaderRoute.RssSourceDebug -> RouteShell.LibraryShell
    ReaderRoute.RssSourceVars -> RouteShell.LibraryShell
    ReaderRoute.RssSourceLogin -> RouteShell.LibraryShell
    ReaderRoute.RssSourceLoginWeb -> RouteShell.LibraryShell
    ReaderRoute.RssSourceLoginCookie -> RouteShell.LibraryShell
    ReaderRoute.RssSourceLoginClear -> RouteShell.LibraryShell
    ReaderRoute.RssSourcePin -> RouteShell.LibraryShell
    ReaderRoute.RssSourceDisable -> RouteShell.LibraryShell
    ReaderRoute.RssReadRecord -> RouteShell.LibraryShell
    ReaderRoute.RssRecordClear -> RouteShell.LibraryShell
    is ReaderRoute.BookState -> RouteShell.LibraryShell
    is ReaderRoute.RssState -> RouteShell.LibraryShell
    is ReaderRoute.RestoreState -> RouteShell.SettingsShell
    is ReaderRoute.DiscoverState -> RouteShell.MainTabShell
    is ReaderRoute.SourceState -> RouteShell.SettingsShell
    is ReaderRoute.Demo -> RouteShell.LibraryShell
}

/**
 * 文本选择状态（S6）。记录当前阅读器内文本选区的起止 offset 与工具栏可见性。
 */
data class TextSelectionState(
    val active: Boolean = false,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val toolbarVisible: Boolean = false
)

/**
 * 视口类别（M6）。驱动宽屏 dock offset、控制层布局、reader 分栏策略。
 */
enum class ViewportClass { PORTRAIT, COMPACT_LANDSCAPE, TABLET_EXPANDED, EXPANDED_WIDTH, HALF_OPENED }

/**
 * 旋转/折叠阶段（M6）。与 [MotionIds.VIEWPORT_ORIENTATION_PREPARE/RESHAPE/SETTLE] 对应。
 */
enum class OrientationPhase { STABLE, PREPARING, RESHAPING, SETTLING }

/**
 * 阅读器控制层状态（S5）。visible/phase 驱动 ENTERING/LEAVING 动效，
 * activeModule 标记当前激活的 reader module（directory/tts/appearance/settings），
 * dockOffset 按 [ViewportClass] 保存宽屏 dock 偏移。
 */
data class ReaderControlState(
    val visible: Boolean = false,
    val phase: MotionPhase = MotionPhase.SETTLED,
    /** directory/tts/appearance/settings。 */
    val activeModule: String = "directory",
    /** 宽屏 dock offset 按 viewport class 保存（S5 dock drag release）。 */
    val dockOffset: Map<ViewportClass, Float> = emptyMap()
)

/**
 * 异步结果守卫状态值（M5）。IDLE/PENDING/COMPLETED/CANCELLED/DISCARDED/SUPERSEDED。
 */
enum class AsyncResultStateValue { IDLE, PENDING, COMPLETED, CANCELLED, DISCARDED, SUPERSEDED }

/**
 * 异步结果守卫状态（M5）。跟踪 reader entry / chapter load 等异步请求，
 * 使 stale 结果可被 discard/supersede（MOTION_CONTRACT.md §async result guard）。
 */
data class AsyncResultState(
    val requestId: String? = null,
    val state: AsyncResultStateValue = AsyncResultStateValue.IDLE,
    val value: Any? = null
)

/**
 * 视口状态（M6）。记录当前 viewportClass、旋转阶段、宽高、折叠特征。
 * foldFeature 用 Any? 以避免直接依赖 androidx.window FoldingFeature。
 */
data class ViewportState(
    val viewportClass: ViewportClass = ViewportClass.PORTRAIT,
    val orientationPhase: OrientationPhase = OrientationPhase.STABLE,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    /** FoldingFeature，为避免直接依赖 androidx.window 用 Any?。 */
    val foldFeature: Any? = null
)

/**
 * 更多菜单状态（S8）。书架/阅读器顶部 more 菜单的开关与触发源。
 */
data class MoreMenuState(
    val open: Boolean = false,
    val triggerId: String? = null
)

// ── P3: Source Import / WebDAV / Permission state slices ─────────────────────

/**
 * P3: 书源导入 e2e 状态机。
 *
 * 契约要求：source-import 空/解析/成功/错误 四态可在 final state 解释。
 * 状态流转：Idle → Parsing → Preview → Importing → Done | Error
 */
sealed class SourceImportState {
    /** 初始空态：用户尚未粘贴 JSON。 */
    object Idle : SourceImportState()
    /** 解析中：JSON 正在解析为预览条目。 */
    object Parsing : SourceImportState()
    /** 解析完成：展示预览条目（新增/重复/异常），等待用户确认导入。 */
    data class Preview(
        val entries: List<SourceImportPreviewEntry>,
        val conflictMode: String = "跳过重复"
    ) : SourceImportState()
    /** 导入中：Core 正在写入书源。 */
    object Importing : SourceImportState()
    /** 导入完成：展示汇总（成功数 / 跳过数 / 失败数）。 */
    data class Done(val imported: Int, val skipped: Int, val failed: Int) : SourceImportState()
    /** 错误：解析失败或 Core 返回错误。 */
    data class Error(val message: String, val retryable: Boolean = true) : SourceImportState()
}

/** P3: 书源导入预览条目。 */
data class SourceImportPreviewEntry(
    val name: String,
    val url: String,
    val group: String,
    val tone: SourceImportTone
)

/** P3: 预览条目色调（新增=good / 重复=muted / 异常=warn）。 */
enum class SourceImportTone { GOOD, MUTED, WARN }

/**
 * P4: RSS 列表 UI 状态。
 *
 * 契约要求：RSS 列表 final state 可解释（loading / empty / success / error）。
 * 状态流转：Idle → Loading → Success | Empty | Error
 *
 * TODO(core-blocker): Core 协议尚未暴露 `rss.list` / `rss.item.read` /
 * `rss.subscription.*` / `rss.source.*`。当前 reducer 只能驱动 UI 状态
 * （Loading/Error/Empty）；Success 列表数据需等待 Core 方法落地后通过
 * Core bridge 拉取，本仓不缓存 RSS 文章内容（DomainState by Core）。
 * 订阅元数据通过 [com.reader.android.data.network.RoomSubscriptionRepository]
 * 持久化，与 Core 文章内容解耦。
 */
sealed class RssListState {
    /** 初始态：尚未发起加载。 */
    object Idle : RssListState()
    /** 加载中：Core bridge 调用 rss.list（待 Core 落地）。 */
    object Loading : RssListState()
    /** 加载成功：展示订阅源列表。 */
    data class Success(val sourceCount: Int) : RssListState()
    /** 空态：无订阅源。 */
    object Empty : RssListState()
    /** 错误：Core 返回错误或超时。 */
    data class Error(val message: String, val retryable: Boolean = true) : RssListState()
}

/**
 * P0: 换源（source-switch）UI 状态机。
 *
 * 契约要求：source-switch 的 loading/results/selected 三态可在 final state 解释。
 * 状态流转：Idle → Loading → Results(loaded, selected=null) → Results(loaded, selected=sourceId) → Idle
 *
 * 由专用 intent 驱动（SourceSwitchOpen / SourceSwitchResultsLoaded / SourceSwitchSelect /
 * SourceSwitchClose），不再走通用 PushRoute，保证 reducer 可追踪换源专属状态。
 */
sealed class SourceSwitchState {
    /** 初始空态：未进入换源流程。 */
    object Idle : SourceSwitchState()
    /** 加载中：换源页已打开，正在拉取可用书源列表。 */
    object Loading : SourceSwitchState()
    /** 结果已就绪：展示可用书源列表，selected 标记当前选中的源 id（null=未选）。 */
    data class Results(
        val results: List<SourceSwitchResult> = emptyList(),
        val selectedSourceId: String? = null
    ) : SourceSwitchState()
}

/** P0: 换源结果条目。 */
data class SourceSwitchResult(
    val sourceId: String,
    val sourceName: String,
    /** 最近章节信息，用于对比。 */
    val latestChapter: String = "",
    /** 字数统计，用于对比。 */
    val wordCount: Int = 0,
    /** 加载速度评级（1-5），用于对比。 */
    val speedLevel: Int = 3
)

/**
 * P3: WebDAV 配置状态。
 *
 * 契约要求：settings/WebDAV intent 接真实 adapter。
 * 表单字段 + 测试/保存状态。
 */
data class WebDavConfigState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val syncDir: String = "/reader/backup",
    val testStatus: WebDavTestStatus = WebDavTestStatus.Idle,
    val saveStatus: WebDavSaveStatus = WebDavSaveStatus.Idle,
    val savedIdentifier: String? = null
)

/** P3: WebDAV 连接测试状态。 */
sealed class WebDavTestStatus {
    object Idle : WebDavTestStatus()
    object Testing : WebDavTestStatus()
    data class Success(val latencyMs: Long) : WebDavTestStatus()
    data class Error(val message: String) : WebDavTestStatus()
}

/** P3: WebDAV 配置保存状态。 */
sealed class WebDavSaveStatus {
    object Idle : WebDavSaveStatus()
    object Saving : WebDavSaveStatus()
    object Saved : WebDavSaveStatus()
    data class Error(val message: String) : WebDavSaveStatus()
}

/**
 * P3: 权限状态。
 *
 * 契约要求：settings/权限 intent 接真实 adapter。
 * 每种权限映射到当前状态。
 */
data class PermissionState(
    val notifications: PermissionStatus = PermissionStatus.UNKNOWN,
    val fileAccess: PermissionStatus = PermissionStatus.UNKNOWN,
    val batteryOptimization: PermissionStatus = PermissionStatus.UNKNOWN
)

/** P3: 权限当前状态。 */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    UNKNOWN
}

/** P3: 权限种类（用于 intent）。 */
enum class PermissionKind {
    NOTIFICATIONS,
    FILE_ACCESS,
    BATTERY_OPTIMIZATION
}

/**
 * The single UI state. Every field the contract requires is present:
 * `activeTab`, `currentRoute`, `backStack`, `ReaderContext`, `activeSession`,
 * `overlayState`, `motionInterrupt` (UI_PLATFORM_EVIDENCE_REQUESTS.md).
 */
data class ReaderUiState(
    val activeTab: MainTab = MainTab.BOOKSHELF,
    val currentRoute: ReaderRoute = ReaderRoute.TabShell(MainTab.BOOKSHELF),
    val backStack: List<ReaderRoute> = emptyList(),
    val readerContext: ReaderContext? = null,
    val activeSession: ActiveSession? = null,
    val overlayState: OverlayState = OverlayState.None,
    val motionInterrupt: MotionInterrupt? = null,
    val reducedMotion: Boolean = false,
    // 新增字段（S2/S4/S5/S6/S7/S8/M2/M5/M6）
    /** 当前动效阶段（M2）。 */
    val motionPhase: MotionPhase = MotionPhase.SETTLED,
    /** 文本选择状态（S6）。 */
    val textSelection: TextSelectionState = TextSelectionState(),
    /** 阅读器控制层状态（S5）。 */
    val readerControl: ReaderControlState = ReaderControlState(),
    /** 异步结果守卫状态（M5）。 */
    val asyncResult: AsyncResultState = AsyncResultState(),
    /** 视口/折叠状态（M6）。 */
    val viewport: ViewportState = ViewportState(),
    /** 更多菜单状态（S8）。 */
    val moreMenu: MoreMenuState = MoreMenuState(),
    // P3 state slices
    /** 书源导入 e2e 状态。 */
    val sourceImport: SourceImportState = SourceImportState.Idle,
    /** WebDAV 配置状态。 */
    val webDavConfig: WebDavConfigState = WebDavConfigState(),
    /** 权限状态。 */
    val permissions: PermissionState = PermissionState(),
    /** P4: RSS 列表 UI 状态（Loading/Empty/Success/Error）。 */
    val rssList: RssListState = RssListState.Idle,
    /** P0: 换源 UI 状态（Idle/Loading/Results）。 */
    val sourceSwitch: SourceSwitchState = SourceSwitchState.Idle,
    /** Slice D: 待派发的 HostRequest 队列（effect-saga 模式）。 */
    val pendingHostRequests: List<HostRequestDispatch> = emptyList(),
    /** Slice D: 最近完成的 HostRequest 结果（用于 UI 反馈）。 */
    val lastHostRequestResult: HostRequestResult? = null
) {
    /** True when the rendered route is the immersive reading surface (no control layer). */
    val isImmersiveReading: Boolean
        get() = currentRoute is ReaderRoute.ImmersiveReading
}
