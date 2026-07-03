package com.reader.ui.shell

import com.reader.ui.motion.FixedReducedMotionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JUnit coverage for [ReaderUiReducer] — the contract rules from
 * `FRONTEND_DEVELOPMENT_SLICE_MATRIX.md` Slice 1/2 and `MOTION_EFFECTS.md` that the
 * acceptance criteria require to be explainable from final state alone.
 *
 * These tests run on the JVM (no device) — the reducer is intentionally free of Android /
 * Compose dependencies so the Slice 1/2 acceptance logic is verifiable in `./gradlew test`.
 */
class ReaderUiReducerTest {

    // ── Slice 1: AppShell + main tabs ──────────────────────────────────────────────

    @Test
    fun `main tab order is bookshelf discover rss settings`() {
        assertEquals(
            listOf("bookshelf", "discover", "rss", "settings"),
            MainTab.ORDER.map { it.routeId }
        )
        assertEquals(
            listOf("书架", "发现", "RSS", "设置"),
            MainTab.ORDER.map { it.label }
        )
    }

    @Test
    fun `search reader and source import are pushed routes not main tabs`() {
        val mainTabRoutes = MainTab.ORDER.map { it.routeId }

        assertFalse(mainTabRoutes.contains(RouteIds.BOOK_SEARCH))
        assertFalse(mainTabRoutes.contains(RouteIds.IMMERSIVE_READING))
        assertFalse(mainTabRoutes.contains(RouteIds.SOURCE_IMPORT_PREVIEW))
        assertFalse(mainTabRoutes.contains(RouteIds.BOOK_BATCH_MANAGEMENT))
        assertFalse(mainTabRoutes.contains(RouteIds.GROUP_MANAGEMENT))
        assertFalse(mainTabRoutes.contains(RouteIds.LOCAL_IMPORT))
        assertFalse(mainTabRoutes.contains(RouteIds.BOOKSHELF_SEARCH_SETTINGS))
        assertFalse(mainTabRoutes.contains(RouteIds.SETTINGS_GENERAL))
        assertFalse(mainTabRoutes.contains(RouteIds.ABOUT_FEEDBACK))
        assertFalse(mainTabRoutes.contains(RouteIds.SYNC_BACKUP))
        assertFalse(mainTabRoutes.contains(RouteIds.WEBDAV_CONFIG))
        assertFalse(mainTabRoutes.contains(RouteIds.SOURCE_MANAGEMENT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SEARCH))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_REFRESHING))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SUBSCRIPTION_MANAGEMENT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_DETAIL))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_ORIGINAL))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_ORIGINAL_BROWSER))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_EDIT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_IMPORT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RULE_SUBSCRIPTION))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_GROUPS))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_ACTIONS))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_BATCH))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_EXPORT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_EXPORT_DETAIL))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_EXPORT_RESULT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_BATCH_DISABLE))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_DEBUG))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_IMPORT_DETAIL))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_IMPORT_RESULT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RULE_SUBSCRIPTION_DETAIL))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RULE_SUBSCRIPTION_EDIT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RULE_SUBSCRIPTION_TEST))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RULE_SUBSCRIPTION_APPLY))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_GROUP_EDIT))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_VARS))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_LOGIN))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_LOGIN_WEB))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_LOGIN_COOKIE))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_LOGIN_CLEAR))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_PIN))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_SOURCE_DISABLE))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_READ_RECORD))
        assertFalse(mainTabRoutes.contains(RouteIds.RSS_RECORD_CLEAR))
        assertEquals("book-search", ReaderRoute.Search.routeId)
        assertEquals("source-import-preview", ReaderRoute.ImportSource.routeId)
        assertEquals("immersive-reading", RouteIds.IMMERSIVE_READING)
        assertEquals("book-batch-management", ReaderRoute.BookBatchManagement.routeId)
        assertEquals("group-management", ReaderRoute.GroupManagement.routeId)
        assertEquals("local-import", ReaderRoute.LocalImport.routeId)
        assertEquals("bookshelf-search-settings", ReaderRoute.BookshelfSearchSettings.routeId)
        assertEquals("settings-general", ReaderRoute.SettingsGeneral.routeId)
        assertEquals("about-feedback", ReaderRoute.AboutFeedback.routeId)
        assertEquals("sync-backup", ReaderRoute.SyncBackup.routeId)
        assertEquals("webdav-config", ReaderRoute.WebDavConfig.routeId)
        assertEquals("source-management", ReaderRoute.SourceManagement.routeId)
        assertEquals("rss-search", ReaderRoute.RssSearch.routeId)
        assertEquals("rss-refreshing", ReaderRoute.RssRefreshing.routeId)
        assertEquals("rss-subscription-management", ReaderRoute.RssSubscriptionManagement.routeId)
        assertEquals("rss-detail", ReaderRoute.RssDetail.routeId)
        assertEquals("rss-original", ReaderRoute.RssOriginal.routeId)
        assertEquals("rss-original-browser", ReaderRoute.RssOriginalBrowser.routeId)
        assertEquals("rss-source-edit", ReaderRoute.RssSourceEdit.routeId)
        assertEquals("rss-source-import", ReaderRoute.RssSourceImport.routeId)
        assertEquals("rss-rule-subscription", ReaderRoute.RssRuleSubscription.routeId)
        assertEquals("rss-source-groups", ReaderRoute.RssSourceGroups.routeId)
        assertEquals("rss-source-actions", ReaderRoute.RssSourceActions.routeId)
        assertEquals("rss-source-batch", ReaderRoute.RssSourceBatch.routeId)
        assertEquals("rss-source-export", ReaderRoute.RssSourceExport.routeId)
        assertEquals("rss-source-export-detail", ReaderRoute.RssSourceExportDetail.routeId)
        assertEquals("rss-source-export-result", ReaderRoute.RssSourceExportResult.routeId)
        assertEquals("rss-source-batch-disable", ReaderRoute.RssSourceBatchDisable.routeId)
        assertEquals("rss-source-debug", ReaderRoute.RssSourceDebug.routeId)
        assertEquals("rss-source-import-detail", ReaderRoute.RssSourceImportDetail.routeId)
        assertEquals("rss-source-import-result", ReaderRoute.RssSourceImportResult.routeId)
        assertEquals("rss-rule-subscription-detail", ReaderRoute.RssRuleSubscriptionDetail.routeId)
        assertEquals("rss-rule-subscription-edit", ReaderRoute.RssRuleSubscriptionEdit.routeId)
        assertEquals("rss-rule-subscription-test", ReaderRoute.RssRuleSubscriptionTest.routeId)
        assertEquals("rss-rule-subscription-apply", ReaderRoute.RssRuleSubscriptionApply.routeId)
        assertEquals("rss-source-group-edit", ReaderRoute.RssSourceGroupEdit.routeId)
        assertEquals("rss-source-vars", ReaderRoute.RssSourceVars.routeId)
        assertEquals("rss-source-login", ReaderRoute.RssSourceLogin.routeId)
        assertEquals("rss-source-login-web", ReaderRoute.RssSourceLoginWeb.routeId)
        assertEquals("rss-source-login-cookie", ReaderRoute.RssSourceLoginCookie.routeId)
        assertEquals("rss-source-login-clear", ReaderRoute.RssSourceLoginClear.routeId)
        assertEquals("rss-source-pin", ReaderRoute.RssSourcePin.routeId)
        assertEquals("rss-source-disable", ReaderRoute.RssSourceDisable.routeId)
        assertEquals("rss-read-record", ReaderRoute.RssReadRecord.routeId)
        assertEquals("rss-record-clear", ReaderRoute.RssRecordClear.routeId)
    }

    @Test
    fun `tab switch is not a route push - backStack stays empty`() {
        val initial = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val next = ReaderUiReducer.reduce(initial, ReaderUiIntent.SelectTab(MainTab.DISCOVER))

        assertEquals(MainTab.DISCOVER, next.activeTab)
        assertEquals(MainTab.DISCOVER, (next.currentRoute as ReaderRoute.TabShell).tab)
        // The contract: tab switch is NOT a route push, so backStack must remain empty.
        assertTrue("tab switch must not push onto backStack", next.backStack.isEmpty())
    }

    @Test
    fun `tab switch clears existing backStack - interrupt cancel`() {
        // Bookshelf -> reader (backStack has ImmersiveReading) -> switch to RSS.
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("fixture://", "fixture://book/a", "A")
        )
        assertTrue(withReader.backStack.isNotEmpty())

        val afterTabSwitch = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.SelectTab(MainTab.RSS, requestId = "req-tab")
        )

        assertTrue(afterTabSwitch.backStack.isEmpty())
        assertNull(afterTabSwitch.readerContext)
        assertNull(afterTabSwitch.activeSession)
        // motion.interrupt.cancel must be emitted so the final state is explainable.
        val interrupt = afterTabSwitch.motionInterrupt
        assertNotNull(interrupt)
        assertEquals(InterruptKind.CANCEL, interrupt!!.kind)
        assertEquals("req-tab", interrupt.requestId)
    }

    @Test
    fun `tab switch from pushed route clears route stack and lands on target tab shell`() {
        val withSearch = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.Search, requestId = "req-search")
        )

        val afterTabSwitch = ReaderUiReducer.reduce(
            withSearch,
            ReaderUiIntent.SelectTab(MainTab.SETTINGS, requestId = "req-settings")
        )

        assertEquals(MainTab.SETTINGS, afterTabSwitch.activeTab)
        assertEquals(MainTab.SETTINGS, (afterTabSwitch.currentRoute as ReaderRoute.TabShell).tab)
        assertTrue(afterTabSwitch.backStack.isEmpty())
        assertNull(afterTabSwitch.readerContext)
        assertNull(afterTabSwitch.activeSession)

        val interrupt = afterTabSwitch.motionInterrupt
        assertNotNull(interrupt)
        assertEquals(InterruptKind.CANCEL, interrupt!!.kind)
        assertEquals("req-settings", interrupt.requestId)
        assertEquals(ReaderRoute.Search.routeId, interrupt.from)
        assertEquals(MainTab.SETTINGS.routeId, interrupt.to)
    }

    @Test
    fun `re-selecting current tab with empty backStack is a no-op`() {
        val initial = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val next = ReaderUiReducer.reduce(initial, ReaderUiIntent.SelectTab(MainTab.BOOKSHELF))
        // Only pressed feedback is allowed (MOTION_EFFECTS §4) — no state change.
        assertEquals(initial, next)
        assertNull(next.motionInterrupt)
    }

    @Test
    fun `pushRoute adds non-tab route onto backStack`() {
        val initial = ReaderUiState()
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.PushRoute(ReaderRoute.Search, requestId = "req-search")
        )

        assertEquals(1, next.backStack.size)
        assertEquals(ReaderRoute.Search, next.currentRoute)
        assertEquals(ReaderRoute.Search, next.backStack.last())
    }

    @Test
    fun `source import route can be pushed and popped`() {
        val withImport = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.ImportSource, requestId = "req-import")
        )

        assertEquals(ReaderRoute.ImportSource, withImport.currentRoute)
        assertEquals(listOf(ReaderRoute.ImportSource), withImport.backStack)

        val afterBack = ReaderUiReducer.reduce(withImport, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.BOOKSHELF, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `rss routes can be pushed and popped from rss tab`() {
        val withRssSearch = ReaderUiReducer.reduce(
            ReaderUiState(activeTab = MainTab.RSS, currentRoute = ReaderRoute.TabShell(MainTab.RSS)),
            ReaderUiIntent.PushRoute(ReaderRoute.RssSearch, requestId = "req-rss-search")
        )

        assertEquals(ReaderRoute.RssSearch, withRssSearch.currentRoute)
        assertEquals(listOf(ReaderRoute.RssSearch), withRssSearch.backStack)

        val afterBack = ReaderUiReducer.reduce(withRssSearch, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.RSS, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `pushed route ids match frontend demo contract subset`() {
        assertEquals("book-search", ReaderRoute.Search.routeId)
        assertEquals("source-import-preview", ReaderRoute.ImportSource.routeId)
        assertEquals("immersive-reading", RouteIds.IMMERSIVE_READING)
        assertEquals("book-batch-management", ReaderRoute.BookBatchManagement.routeId)
        assertEquals("group-management", ReaderRoute.GroupManagement.routeId)
        assertEquals("local-import", ReaderRoute.LocalImport.routeId)
        assertEquals("bookshelf-search-settings", ReaderRoute.BookshelfSearchSettings.routeId)
        assertEquals("settings-general", ReaderRoute.SettingsGeneral.routeId)
        assertEquals("about-feedback", ReaderRoute.AboutFeedback.routeId)
        assertEquals("sync-backup", ReaderRoute.SyncBackup.routeId)
        assertEquals("webdav-config", ReaderRoute.WebDavConfig.routeId)
        assertEquals("source-management", ReaderRoute.SourceManagement.routeId)
        assertEquals("rss-search", ReaderRoute.RssSearch.routeId)
        assertEquals("rss-refreshing", ReaderRoute.RssRefreshing.routeId)
        assertEquals("rss-subscription-management", ReaderRoute.RssSubscriptionManagement.routeId)
        assertEquals("rss-detail", ReaderRoute.RssDetail.routeId)
        assertEquals("rss-original", ReaderRoute.RssOriginal.routeId)
        assertEquals("rss-original-browser", ReaderRoute.RssOriginalBrowser.routeId)
        assertEquals("rss-source-edit", ReaderRoute.RssSourceEdit.routeId)
        assertEquals("rss-source-import", ReaderRoute.RssSourceImport.routeId)
        assertEquals("rss-rule-subscription", ReaderRoute.RssRuleSubscription.routeId)
        assertEquals("rss-source-groups", ReaderRoute.RssSourceGroups.routeId)
        assertEquals("rss-source-actions", ReaderRoute.RssSourceActions.routeId)
        assertEquals("rss-source-batch", ReaderRoute.RssSourceBatch.routeId)
        assertEquals("rss-source-export", ReaderRoute.RssSourceExport.routeId)
        assertEquals("rss-source-export-detail", ReaderRoute.RssSourceExportDetail.routeId)
        assertEquals("rss-source-export-result", ReaderRoute.RssSourceExportResult.routeId)
        assertEquals("rss-source-batch-disable", ReaderRoute.RssSourceBatchDisable.routeId)
        assertEquals("rss-source-debug", ReaderRoute.RssSourceDebug.routeId)
        assertEquals("rss-source-import-detail", ReaderRoute.RssSourceImportDetail.routeId)
        assertEquals("rss-source-import-result", ReaderRoute.RssSourceImportResult.routeId)
        assertEquals("rss-rule-subscription-detail", ReaderRoute.RssRuleSubscriptionDetail.routeId)
        assertEquals("rss-rule-subscription-edit", ReaderRoute.RssRuleSubscriptionEdit.routeId)
        assertEquals("rss-rule-subscription-test", ReaderRoute.RssRuleSubscriptionTest.routeId)
        assertEquals("rss-rule-subscription-apply", ReaderRoute.RssRuleSubscriptionApply.routeId)
        assertEquals("rss-source-group-edit", ReaderRoute.RssSourceGroupEdit.routeId)
        assertEquals("rss-source-vars", ReaderRoute.RssSourceVars.routeId)
        assertEquals("rss-source-login", ReaderRoute.RssSourceLogin.routeId)
        assertEquals("rss-source-login-web", ReaderRoute.RssSourceLoginWeb.routeId)
        assertEquals("rss-source-login-cookie", ReaderRoute.RssSourceLoginCookie.routeId)
        assertEquals("rss-source-login-clear", ReaderRoute.RssSourceLoginClear.routeId)
        assertEquals("rss-source-pin", ReaderRoute.RssSourcePin.routeId)
        assertEquals("rss-source-disable", ReaderRoute.RssSourceDisable.routeId)
        assertEquals("rss-read-record", ReaderRoute.RssReadRecord.routeId)
        assertEquals("rss-record-clear", ReaderRoute.RssRecordClear.routeId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pushRoute rejects TabShell - tab switch is not a push`() {
        ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.TabShell(MainTab.DISCOVER))
        )
    }

    // ── Slice 2: bookshelf -> immersive reading ───────────────────────────────────

    @Test
    fun `cover entry final state is immersive-reading - not control layer`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "fixture://",
                bookUrl = "fixture://book/demo",
                bookName = "示例书籍",
                requestId = "req-cover"
            )
        )

        // Final state MUST be immersive-reading, never the control layer.
        assertTrue("final route must be ImmersiveReading", next.isImmersiveReading)
        // Control layer (top/bottom chrome) must NOT auto-open on entry.
        assertEquals(OverlayState.None, next.overlayState)
        assertNull("no session auto-starts on entry", next.activeSession)

        val ctx = next.readerContext
        assertNotNull(ctx)
        assertEquals(ReaderEntry.COVER_TO_IMMERSIVE, ctx!!.entry)
        assertEquals("fixture://book/demo", ctx.bookUrl)
        assertEquals("req-cover", ctx.entryRequestId)
    }

    @Test
    fun `action entry uses actionToImmersive motion id`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "fixture://",
                bookUrl = "fixture://book/demo",
                bookName = "示例书籍"
            )
        )

        assertTrue(next.isImmersiveReading)
        assertEquals(ReaderEntry.ACTION_TO_IMMERSIVE, next.readerContext!!.entry)
    }

    @Test
    fun `back from reader returns to source tab`() {
        // Bookshelf -> reader -> back should land on Bookshelf.
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(activeTab = MainTab.BOOKSHELF),
            ReaderUiIntent.EnterReaderFromCover("fixture://", "fixture://book/a", "A")
        )
        assertTrue(withReader.isImmersiveReading)

        val afterBack = ReaderUiReducer.reduce(withReader, ReaderUiIntent.PopRoute)

        assertFalse(afterBack.isImmersiveReading)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.BOOKSHELF, afterBack.activeTab)
        assertEquals(MainTab.BOOKSHELF, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
        // ReaderContext must be cleared — we've left the reader entirely.
        assertNull(afterBack.readerContext)
        assertNull(afterBack.activeSession)
    }

    @Test
    fun `back from reader returns to the originating tab - not always bookshelf`() {
        // Start from RSS tab -> enter reader -> back should land on RSS, not Bookshelf.
        val fromRss = ReaderUiState(activeTab = MainTab.RSS)
        val withReader = ReaderUiReducer.reduce(
            fromRss,
            ReaderUiIntent.EnterReaderFromAction("fixture://", "fixture://book/b", "B")
        )
        val afterBack = ReaderUiReducer.reduce(withReader, ReaderUiIntent.PopRoute)

        assertEquals(MainTab.RSS, afterBack.activeTab)
        assertEquals(MainTab.RSS, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `continuous multi-cover taps keep only the last target`() {
        // Tap cover A, then quickly tap cover B before A settles — only B should remain.
        val first = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "fixture://", bookUrl = "fixture://book/a", bookName = "A",
                requestId = "req-a"
            )
        )
        val second = ReaderUiReducer.reduce(
            first,
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "fixture://", bookUrl = "fixture://book/b", bookName = "B",
                requestId = "req-b"
            )
        )

        // Only one ImmersiveReading on the stack — the in-flight one was replaced.
        val readerCount = second.backStack.count { it is ReaderRoute.ImmersiveReading }
        assertEquals("only one immersive-reading entry must remain", 1, readerCount)
        assertEquals("fixture://book/b", second.readerContext!!.bookUrl)
        assertEquals("req-b", second.readerContext.entryRequestId)

        // The replacement must emit motion.interrupt.cancel (latest intent wins).
        val interrupt = second.motionInterrupt
        assertNotNull(interrupt)
        assertEquals(InterruptKind.CANCEL, interrupt!!.kind)
        assertEquals("req-b", interrupt.requestId)
        assertEquals(RouteIds.IMMERSIVE_READING, interrupt.from)
        assertEquals(RouteIds.IMMERSIVE_READING, interrupt.to)
    }

    @Test
    fun `popRoute on empty backStack is a no-op`() {
        val initial = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val next = ReaderUiReducer.reduce(initial, ReaderUiIntent.PopRoute)
        assertEquals(initial, next)
    }

    @Test
    fun `push then pop returns to previous route`() {
        // Bookshelf -> Search -> back should land on Bookshelf tab shell.
        val withSearch = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.Search)
        )
        assertEquals(ReaderRoute.Search, withSearch.currentRoute)

        val afterBack = ReaderUiReducer.reduce(withSearch, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.BOOKSHELF, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `search result action entry lands on immersive-reading`() {
        // Bookshelf -> Search -> tap a result -> ImmersiveReading.
        val withSearch = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.Search)
        )
        val withReader = ReaderUiReducer.reduce(
            withSearch,
            ReaderUiIntent.EnterReaderFromAction(
                sourceId = "src-1", bookUrl = "book-1", bookName = "Result"
            )
        )

        assertTrue(withReader.isImmersiveReading)
        // Back stack should be [Search, ImmersiveReading] — back from reader returns to Search.
        assertEquals(2, withReader.backStack.size)
        assertEquals(ReaderRoute.Search, withReader.backStack[0])
        assertTrue(withReader.backStack[1] is ReaderRoute.ImmersiveReading)
    }

    // ── Reduced motion ─────────────────────────────────────────────────────────────

    @Test
    fun `reduced motion is a first-class state field - default false`() {
        assertFalse(ReaderUiState().reducedMotion)
    }

    @Test
    fun `SetReducedMotion intent flips the flag`() {
        val initial = ReaderUiState(reducedMotion = false)
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.SetReducedMotion(enabled = true, requestId = "req-rm")
        )
        assertTrue(next.reducedMotion)

        val off = ReaderUiReducer.reduce(
            next,
            ReaderUiIntent.SetReducedMotion(enabled = false, requestId = "req-rm-off")
        )
        assertFalse(off.reducedMotion)
    }

    @Test
    fun `reduced motion flag survives tab switch and reader entry`() {
        val rmOn = ReaderUiReducer.reduce(
            ReaderUiState(reducedMotion = true),
            ReaderUiIntent.SelectTab(MainTab.SETTINGS)
        )
        assertTrue("reducedMotion must persist across tab switch", rmOn.reducedMotion)

        val withReader = ReaderUiReducer.reduce(
            rmOn,
            ReaderUiIntent.EnterReaderFromCover("fixture://", "fixture://book/x", "X")
        )
        assertTrue("reducedMotion must persist across reader entry", withReader.reducedMotion)
    }

    @Test
    fun `AppShellViewModel seeds reduced motion from resolver`() {
        val vm = AppShellViewModel(FixedReducedMotionResolver(true))

        assertTrue(vm.state.value.reducedMotion)
    }

    // ── Final-state explainability ─────────────────────────────────────────────────

    @Test
    fun `every final state carries route backStack context session overlay interrupt`() {
        val states = listOf(
            ReaderUiState(),
            ReaderUiReducer.reduce(ReaderUiState(), ReaderUiIntent.SelectTab(MainTab.DISCOVER)),
            ReaderUiReducer.reduce(ReaderUiState(), ReaderUiIntent.PushRoute(ReaderRoute.Search)),
            ReaderUiReducer.reduce(
                ReaderUiState(),
                ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
            ),
            ReaderUiReducer.reduce(
                ReaderUiState(),
                ReaderUiIntent.SetReducedMotion(true)
            )
        )

        states.forEach { state ->
            // The UI_PLATFORM_EVIDENCE_REQUESTS.md rejection rule: route / session / overlay
            // / focus / async result must be explainable from a single ReaderUiState.
            assertNotNull("currentRoute must be set", state.currentRoute)
            assertNotNull("backStack must be present (possibly empty)", state.backStack)
            assertNotNull("overlayState must be present", state.overlayState)
        }
    }
}
