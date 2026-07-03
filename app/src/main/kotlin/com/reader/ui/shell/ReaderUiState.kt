package com.reader.ui.shell

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
    val entryRequestId: String
)

/** Single running session. autoPage and tts are mutually exclusive (one activeSession). */
data class ActiveSession(val type: SessionType, val playing: Boolean)

enum class SessionType { AUTO_PAGE, TTS }

/**
 * Overlay state. Slice 4 will populate keyboard/sheet/dialog; Slice 1/2 only needs `None`
 * so the reducer can clear it on tab switch / route push (interrupt rule).
 */
sealed class OverlayState {
    object None : OverlayState()
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
 * Routes the reducer owns. Tab roots are NOT pushable — switching tabs only mutates
 * [ReaderUiState.activeTab] and never grows [ReaderUiState.backStack]
 * (FRONTEND_DEVELOPMENT_SLICE_MATRIX.md Slice 1: "不把 tab switch 写成 route push").
 *
 * `immersive-reading` is the final-state route for reader entry; `reader` (the control
 * layer) is deferred to Slice 3 and intentionally absent here so Slice 2 cannot
 * accidentally end on the control layer.
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

    /** Native fallback for demo-authored routes that do not yet have bespoke Compose screens. */
    data class Demo(val id: String) : ReaderRoute() {
        val routeId: String get() = id
    }
}

/** Canonical route ids (match `frontend-demo/route-contract.js` for the Slice 1/2 subset). */
object RouteIds {
    const val IMMERSIVE_READING = "immersive-reading"
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
        is ReaderRoute.Demo -> routeId
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
    val reducedMotion: Boolean = false
) {
    /** True when the rendered route is the immersive reading surface (no control layer). */
    val isImmersiveReading: Boolean
        get() = currentRoute is ReaderRoute.ImmersiveReading
}
