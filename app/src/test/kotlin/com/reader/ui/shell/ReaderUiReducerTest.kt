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
        assertFalse(mainTabRoutes.contains(RouteIds.READER_CONTROL))
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
        assertEquals("reader", ReaderRoute.ReaderControl().routeId)
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
        assertEquals("reader", ReaderRoute.ReaderControl().routeId)
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
    fun `reader control layer is explicit push over immersive-reading`() {
        val immersive = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover(
                sourceId = "fixture://",
                bookUrl = "fixture://book/demo",
                bookName = "示例书籍",
                requestId = "req-cover"
            )
        )
        val ctx = immersive.readerContext!!
        val withControl = ReaderUiReducer.reduce(
            immersive,
            ReaderUiIntent.PushRoute(ReaderRoute.ReaderControl(context = ctx), requestId = "req-control")
        )

        assertFalse("control layer is not immersive final state", withControl.isImmersiveReading)
        assertEquals(RouteIds.READER_CONTROL, withControl.currentRoute.routeId)
        assertEquals(2, withControl.backStack.size)
        assertTrue(withControl.backStack[0] is ReaderRoute.ImmersiveReading)
        assertTrue(withControl.backStack[1] is ReaderRoute.ReaderControl)
        assertEquals(ctx, withControl.readerContext)

        val afterBack = ReaderUiReducer.reduce(withControl, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.isImmersiveReading)
        assertEquals(ctx, afterBack.readerContext)
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

    // ── P5: Reader session (autoPage / TTS mutual exclusion) ─────────────────────

    @Test
    fun `StartAutoPageSession sets activeSession to AUTO_PAGE playing`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAutoPageSession
        )
        val session = next.activeSession
        assertNotNull(session)
        assertEquals(SessionType.AUTO_PAGE, session!!.type)
        assertTrue("auto-page must start playing", session.playing)
    }

    @Test
    fun `StartTtsSession sets activeSession to TTS playing`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "测试朗读文本")
        )
        val session = next.activeSession
        assertNotNull(session)
        assertEquals(SessionType.TTS, session!!.type)
        assertTrue("TTS must start playing", session.playing)
    }

    @Test
    fun `activeSession is mutually exclusive - starting TTS replaces AUTO_PAGE`() {
        val withAutoPage = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAutoPageSession
        )
        assertEquals(SessionType.AUTO_PAGE, withAutoPage.activeSession!!.type)

        val withTts = ReaderUiReducer.reduce(withAutoPage, ReaderUiIntent.StartTtsSession(text = "测试"))
        assertEquals(
            "TTS must replace autoPage — only one session at a time",
            SessionType.TTS,
            withTts.activeSession!!.type
        )
    }

    @Test
    fun `activeSession is mutually exclusive - starting AUTO_PAGE replaces TTS`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "测试朗读文本")
        )
        assertEquals(SessionType.TTS, withTts.activeSession!!.type)

        val withAutoPage = ReaderUiReducer.reduce(withTts, ReaderUiIntent.StartAutoPageSession)
        assertEquals(
            "autoPage must replace TTS — only one session at a time",
            SessionType.AUTO_PAGE,
            withAutoPage.activeSession!!.type
        )
    }

    // ── P0-5: DispatchHostRequest enqueued by session intents ──────────────

    @Test
    fun `StartTtsSession enqueues tts system start DispatchHostRequest`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "朗读内容")
        )
        assertEquals("pendingHostRequests must have 1 entry", 1, next.pendingHostRequests.size)
        val dispatch = next.pendingHostRequests.first()
        assertEquals("tts.system.start", dispatch.capability)
        assertTrue("params must contain text", dispatch.paramsJson.contains("朗读内容"))
    }

    @Test
    fun `StopSession enqueues tts system stop when TTS session active`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "朗读")
        )
        val stopped = ReaderUiReducer.reduce(withTts, ReaderUiIntent.StopSession)
        // StartTtsSession enqueues 1, StopSession enqueues 1 more
        assertEquals("pendingHostRequests must have 2 entries", 2, stopped.pendingHostRequests.size)
        val stopDispatch = stopped.pendingHostRequests.last()
        assertEquals("tts.system.stop", stopDispatch.capability)
    }

    @Test
    fun `ToggleSessionPlaying enqueues tts pause when playing`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "朗读")
        )
        assertTrue("must be playing initially", withTts.activeSession!!.playing)
        val toggled = ReaderUiReducer.reduce(withTts, ReaderUiIntent.ToggleSessionPlaying)
        assertFalse("must be paused after toggle", toggled.activeSession!!.playing)
        val pauseDispatch = toggled.pendingHostRequests.last()
        assertEquals("tts.system.pause", pauseDispatch.capability)
    }

    @Test
    fun `ToggleSessionPlaying enqueues tts resume when paused`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "朗读")
        )
        val paused = ReaderUiReducer.reduce(withTts, ReaderUiIntent.ToggleSessionPlaying)
        assertFalse("must be paused", paused.activeSession!!.playing)
        val resumed = ReaderUiReducer.reduce(paused, ReaderUiIntent.ToggleSessionPlaying)
        assertTrue("must be playing after resume", resumed.activeSession!!.playing)
        val resumeDispatch = resumed.pendingHostRequests.last()
        assertEquals("tts.system.resume", resumeDispatch.capability)
    }

    @Test
    fun `StopSession clears activeSession`() {
        val withSession = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAutoPageSession
        )
        assertNotNull(withSession.activeSession)

        val stopped = ReaderUiReducer.reduce(withSession, ReaderUiIntent.StopSession)
        assertNull("StopSession must clear activeSession", stopped.activeSession)
    }

    @Test
    fun `ToggleSessionPlaying flips playing flag without changing type`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "测试朗读文本")
        )
        assertTrue(withTts.activeSession!!.playing)

        val paused = ReaderUiReducer.reduce(withTts, ReaderUiIntent.ToggleSessionPlaying)
        assertEquals(SessionType.TTS, paused.activeSession!!.type)
        assertFalse("ToggleSessionPlaying must pause a playing session", paused.activeSession!!.playing)

        val resumed = ReaderUiReducer.reduce(paused, ReaderUiIntent.ToggleSessionPlaying)
        assertTrue("ToggleSessionPlaying must resume a paused session", resumed.activeSession!!.playing)
    }

    @Test
    fun `UpdateCountdown only affects AUTO_PAGE session`() {
        val withAutoPage = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAutoPageSession
        )
        val updated = ReaderUiReducer.reduce(
            withAutoPage,
            ReaderUiIntent.UpdateCountdown(seconds = 30)
        )
        assertEquals(30, updated.activeSession!!.countdownSeconds)
    }

    @Test
    fun `UpdateTtsProgress only affects TTS session`() {
        val withTts = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartTtsSession(text = "测试朗读文本")
        )
        val updated = ReaderUiReducer.reduce(
            withTts,
            ReaderUiIntent.UpdateTtsProgress(sentenceIndex = 5, chapterIndex = 2)
        )
        assertEquals(5, updated.activeSession!!.ttsSentenceIndex)
        assertEquals(2, updated.activeSession!!.ttsChapterIndex)
    }

    // ── P5: Overlay (keyboard / sheet / dialog) ──────────────────────────────────

    @Test
    fun `OpenKeyboard sets overlayState to Keyboard`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenKeyboard(inputId = "search-input")
        )
        assertTrue(next.overlayState is OverlayState.Keyboard)
        assertEquals("search-input", (next.overlayState as OverlayState.Keyboard).inputId)
    }

    @Test
    fun `CloseKeyboard clears keyboard overlay only`() {
        val withKeyboard = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenKeyboard(inputId = "search-input")
        )
        val closed = ReaderUiReducer.reduce(withKeyboard, ReaderUiIntent.CloseKeyboard)
        assertEquals(OverlayState.None, closed.overlayState)
    }

    @Test
    fun `CloseKeyboard does not clear Sheet overlay`() {
        val withSheet = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenSheet(SheetContent.BookshelfFilter("filter-1"))
        )
        val closed = ReaderUiReducer.reduce(withSheet, ReaderUiIntent.CloseKeyboard)
        assertEquals(
            "CloseKeyboard must not close a Sheet",
            withSheet.overlayState,
            closed.overlayState
        )
    }

    @Test
    fun `OpenSheet replaces existing overlay`() {
        val withKeyboard = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenKeyboard(inputId = "input")
        )
        val withSheet = ReaderUiReducer.reduce(
            withKeyboard,
            ReaderUiIntent.OpenSheet(SheetContent.ReaderSetting("directory"))
        )
        assertTrue(withSheet.overlayState is OverlayState.Sheet)
    }

    @Test
    fun `CloseSheet clears sheet overlay only`() {
        val withSheet = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenSheet(SheetContent.BookshelfFilter("filter"))
        )
        val closed = ReaderUiReducer.reduce(withSheet, ReaderUiIntent.CloseSheet)
        assertEquals(OverlayState.None, closed.overlayState)
    }

    @Test
    fun `OpenDialog and CloseDialog work as a pair`() {
        val withDialog = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenDialog(
                DialogContent.Confirm(title = "确认", message = "ok", onConfirm = {})
            )
        )
        assertTrue(withDialog.overlayState is OverlayState.Dialog)
        val closed = ReaderUiReducer.reduce(withDialog, ReaderUiIntent.CloseDialog)
        assertEquals(OverlayState.None, closed.overlayState)
    }

    // ── P5: Reader control layer phase (ENTERING / LEAVING / SETTLED) ─────────────

    @Test
    fun `ShowReaderControl sets visible=true phase=ENTERING`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ShowReaderControl
        )
        assertTrue(next.readerControl.visible)
        assertEquals(MotionPhase.ENTERING, next.readerControl.phase)
    }

    @Test
    fun `HideReaderControl sets phase=LEAVING but keeps visible true until SETTLED`() {
        val shown = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ShowReaderControl
        )
        val hidden = ReaderUiReducer.reduce(shown, ReaderUiIntent.HideReaderControl)
        assertEquals(
            "LEAVING phase must keep visible=true so the leave animation can play",
            true,
            hidden.readerControl.visible
        )
        assertEquals(MotionPhase.LEAVING, hidden.readerControl.phase)
    }

    @Test
    fun `UpdateMotionPhase to SETTLED after LEAVING collapses visible to false`() {
        val shown = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ShowReaderControl
        )
        val hidden = ReaderUiReducer.reduce(shown, ReaderUiIntent.HideReaderControl)
        val settled = ReaderUiReducer.reduce(
            hidden,
            ReaderUiIntent.UpdateMotionPhase(MotionPhase.SETTLED)
        )
        assertFalse(
            "After LEAVING -> SETTLED the control layer must be hidden",
            settled.readerControl.visible
        )
        assertEquals(MotionPhase.SETTLED, settled.readerControl.phase)
    }

    @Test
    fun `UpdateMotionPhase to SETTLED without prior LEAVING keeps visible state`() {
        val shown = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ShowReaderControl
        )
        val settled = ReaderUiReducer.reduce(
            shown,
            ReaderUiIntent.UpdateMotionPhase(MotionPhase.SETTLED)
        )
        assertTrue(
            "SETTLED without LEAVING must not hide a visible control layer",
            settled.readerControl.visible
        )
    }

    @Test
    fun `SwitchReaderModule updates activeModule without touching visible or phase`() {
        val shown = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ShowReaderControl
        )
        val switched = ReaderUiReducer.reduce(
            shown,
            ReaderUiIntent.SwitchReaderModule(module = "tts")
        )
        assertEquals("tts", switched.readerControl.activeModule)
        assertTrue(switched.readerControl.visible)
    }

    // ── P5: Text selection ───────────────────────────────────────────────────────

    @Test
    fun `StartSelection activates selection with toolbar hidden`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartSelection(startOffset = 0, endOffset = 10)
        )
        assertTrue(next.textSelection.active)
        assertEquals(0, next.textSelection.startOffset)
        assertEquals(10, next.textSelection.endOffset)
        assertFalse(next.textSelection.toolbarVisible)
    }

    @Test
    fun `UpdateSelectionRange updates offsets without touching toolbar`() {
        val started = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartSelection(startOffset = 0, endOffset = 5)
        )
        val updated = ReaderUiReducer.reduce(
            started,
            ReaderUiIntent.UpdateSelectionRange(startOffset = 2, endOffset = 12)
        )
        assertEquals(2, updated.textSelection.startOffset)
        assertEquals(12, updated.textSelection.endOffset)
    }

    @Test
    fun `ShowSelectionToolbar then HideSelectionToolbar toggle visibility`() {
        val started = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartSelection(0, 10)
        )
        val withToolbar = ReaderUiReducer.reduce(started, ReaderUiIntent.ShowSelectionToolbar)
        assertTrue(withToolbar.textSelection.toolbarVisible)

        val hidden = ReaderUiReducer.reduce(withToolbar, ReaderUiIntent.HideSelectionToolbar)
        assertFalse(hidden.textSelection.toolbarVisible)
    }

    @Test
    fun `EndSelection resets text selection to default`() {
        val started = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartSelection(0, 10)
        )
        val ended = ReaderUiReducer.reduce(started, ReaderUiIntent.EndSelection)
        assertFalse(ended.textSelection.active)
        assertFalse(ended.textSelection.toolbarVisible)
    }

    // ── P5: More menu ────────────────────────────────────────────────────────────

    @Test
    fun `OpenMoreMenu and CloseMoreMenu toggle more menu state`() {
        val opened = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.OpenMoreMenu(triggerId = "bookshelf-more")
        )
        assertTrue(opened.moreMenu.open)
        assertEquals("bookshelf-more", opened.moreMenu.triggerId)

        val closed = ReaderUiReducer.reduce(opened, ReaderUiIntent.CloseMoreMenu)
        assertFalse(closed.moreMenu.open)
        assertNull(closed.moreMenu.triggerId)
    }

    // ── P5: Viewport orientation (prepare / reshape / settle) ────────────────────

    @Test
    fun `ViewportPrepare sets orientationPhase to PREPARING and emits motion interrupt`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ViewportPrepare
        )
        assertEquals(OrientationPhase.PREPARING, next.viewport.orientationPhase)
        assertNotNull(next.motionInterrupt)
    }

    @Test
    fun `ViewportReshape sets orientationPhase to RESHAPING`() {
        val prepared = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ViewportPrepare
        )
        val reshaped = ReaderUiReducer.reduce(prepared, ReaderUiIntent.ViewportReshape)
        assertEquals(OrientationPhase.RESHAPING, reshaped.viewport.orientationPhase)
    }

    @Test
    fun `ViewportSettle returns orientationPhase to STABLE`() {
        val reshaped = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ViewportReshape
        )
        val settled = ReaderUiReducer.reduce(reshaped, ReaderUiIntent.ViewportSettle)
        assertEquals(OrientationPhase.STABLE, settled.viewport.orientationPhase)
    }

    @Test
    fun `UpdateViewport records viewportClass widthDp heightDp`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.UpdateViewport(
                viewportClass = ViewportClass.COMPACT_LANDSCAPE,
                widthDp = 800,
                heightDp = 360
            )
        )
        assertEquals(ViewportClass.COMPACT_LANDSCAPE, next.viewport.viewportClass)
        assertEquals(800, next.viewport.widthDp)
        assertEquals(360, next.viewport.heightDp)
    }

    // ── P5: AsyncResult guard (latest intent wins / stale discard) ───────────────

    @Test
    fun `StartAsyncRequest sets PENDING and emits COMPLETE_THEN_REPLACE interrupt`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-async-1"
            )
        )
        assertEquals(AsyncResultStateValue.PENDING, next.asyncResult.state)
        assertEquals("req-async-1", next.asyncResult.requestId)
        assertNotNull(next.motionInterrupt)
        assertEquals(
            InterruptKind.COMPLETE_THEN_REPLACE,
            next.motionInterrupt!!.kind
        )
    }

    @Test
    fun `CompleteAsyncRequest with matching requestId writes value and COMPLETED`() {
        val pending = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-async-1"
            )
        )
        val completed = ReaderUiReducer.reduce(
            pending,
            ReaderUiIntent.CompleteAsyncRequest(
                requestId = "req-async-1",
                value = "chapter-content",
                currentRoute = RouteIds.IMMERSIVE_READING
            )
        )
        assertEquals(AsyncResultStateValue.COMPLETED, completed.asyncResult.state)
        assertEquals("chapter-content", completed.asyncResult.value)
    }

    @Test
    fun `CompleteAsyncRequest with stale requestId marks DISCARDED not COMPLETED`() {
        val first = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-1"
            )
        )
        // Second request supersedes the first
        val second = ReaderUiReducer.reduce(
            first,
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-2"
            )
        )
        // Late completion of the first (stale) request
        val lateComplete = ReaderUiReducer.reduce(
            second,
            ReaderUiIntent.CompleteAsyncRequest(
                requestId = "req-1",
                value = "stale-content",
                currentRoute = RouteIds.IMMERSIVE_READING
            )
        )
        assertEquals(
            "stale result must be DISCARDED, not COMPLETED",
            AsyncResultStateValue.DISCARDED,
            lateComplete.asyncResult.state
        )
    }

    @Test
    fun `CancelAsyncRequest with matching requestId sets CANCELLED`() {
        val pending = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-1"
            )
        )
        val cancelled = ReaderUiReducer.reduce(
            pending,
            ReaderUiIntent.CancelAsyncRequest(requestId = "req-1")
        )
        assertEquals(AsyncResultStateValue.CANCELLED, cancelled.asyncResult.state)
    }

    @Test
    fun `CancelAsyncRequest with non-matching requestId is a no-op`() {
        val pending = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.StartAsyncRequest(
                fromRoute = "bookshelf",
                toRoute = RouteIds.IMMERSIVE_READING,
                requestId = "req-1"
            )
        )
        val cancelled = ReaderUiReducer.reduce(
            pending,
            ReaderUiIntent.CancelAsyncRequest(requestId = "req-other")
        )
        assertEquals(
            "cancel for a non-current requestId must not change state",
            AsyncResultStateValue.PENDING,
            cancelled.asyncResult.state
        )
    }

    // ── P5: ReaderContext updates (S4) ───────────────────────────────────────────

    @Test
    fun `UpdateReaderChapter mutates chapterIndex on existing context`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val updated = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderChapter(chapterIndex = 5)
        )
        assertEquals(5, updated.readerContext!!.chapterIndex)
    }

    @Test
    fun `UpdateReaderPage mutates page and progress`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val updated = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderPage(page = 3, progress = 0.42f)
        )
        assertEquals(3, updated.readerContext!!.page)
        assertEquals(0.42f, updated.readerContext!!.progress, 0.001f)
    }

    @Test
    fun `UpdateReaderTheme mutates themeId`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val updated = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderTheme(themeId = "warm-night")
        )
        assertEquals("warm-night", updated.readerContext!!.themeId)
    }

    @Test
    fun `UpdateReaderBrightness mutates brightness and auto`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val updated = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderBrightness(brightness = 0.8f, auto = false)
        )
        assertEquals(0.8f, updated.readerContext!!.brightness, 0.001f)
        assertFalse(updated.readerContext!!.brightnessAuto)
    }

    @Test
    fun `UpdateReaderTypography mutates fontSize lineSpacing pageMargin`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val updated = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderTypography(
                fontSize = 20f,
                lineSpacing = 1.7f,
                pageMargin = 24f
            )
        )
        assertEquals(20f, updated.readerContext!!.fontSize, 0.001f)
        assertEquals(1.7f, updated.readerContext!!.lineSpacing, 0.001f)
        assertEquals(24f, updated.readerContext!!.pageMargin, 0.001f)
    }

    @Test
    fun `TurnPageNext increments page and TurnPagePrev decrements with floor at 0`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val next = ReaderUiReducer.reduce(withReader, ReaderUiIntent.TurnPageNext)
        assertEquals(1, next.readerContext!!.page)

        val prev = ReaderUiReducer.reduce(next, ReaderUiIntent.TurnPagePrev)
        assertEquals(0, prev.readerContext!!.page)

        val floored = ReaderUiReducer.reduce(prev, ReaderUiIntent.TurnPagePrev)
        assertEquals(
            "page must not go below 0",
            0,
            floored.readerContext!!.page
        )
    }

    @Test
    fun `JumpChapter resets page and progress to 0`() {
        val withReader = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.EnterReaderFromCover("s", "b", "n")
        )
        val paged = ReaderUiReducer.reduce(
            withReader,
            ReaderUiIntent.UpdateReaderPage(page = 5, progress = 0.5f)
        )
        val jumped = ReaderUiReducer.reduce(
            paged,
            ReaderUiIntent.JumpChapter(chapterIndex = 3)
        )
        assertEquals(3, jumped.readerContext!!.chapterIndex)
        assertEquals(0, jumped.readerContext!!.page)
        assertEquals(0f, jumped.readerContext!!.progress, 0.001f)
    }

    // ── P5: ReplaceRoute (app.route.replace) ─────────────────────────────────────

    @Test
    fun `ReplaceRoute swaps currentRoute and emits motion interrupt cancel`() {
        val withSearch = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.Search)
        )
        val replaced = ReaderUiReducer.reduce(
            withSearch,
            ReaderUiIntent.ReplaceRoute(ReaderRoute.ImportSource, requestId = "req-replace")
        )
        assertEquals(ReaderRoute.ImportSource, replaced.currentRoute)
        assertNotNull(replaced.motionInterrupt)
        assertEquals(InterruptKind.CANCEL, replaced.motionInterrupt!!.kind)
        assertEquals("req-replace", replaced.motionInterrupt!!.requestId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ReplaceRoute rejects TabShell`() {
        ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.ReplaceRoute(ReaderRoute.TabShell(MainTab.DISCOVER))
        )
    }

    // ── P5: UpdateDockOffset ─────────────────────────────────────────────────────

    @Test
    fun `UpdateDockOffset records offset per viewportClass`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.UpdateDockOffset(
                viewportClass = ViewportClass.TABLET_EXPANDED,
                offset = 64f
            )
        )
        assertEquals(64f, next.readerControl.dockOffset[ViewportClass.TABLET_EXPANDED]!!)
    }

    // ── P5: MotionInterrupt intent ───────────────────────────────────────────────

    @Test
    fun `MotionInterrupt intent records interrupt with kind and requestId`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.MotionInterrupt(
                kind = InterruptKind.REDIRECT,
                reason = "user-switched-target"
            )
        )
        assertNotNull(next.motionInterrupt)
        assertEquals(InterruptKind.REDIRECT, next.motionInterrupt!!.kind)
    }

    // ── P5: tab switch clears session / overlay / control (interrupt rule) ──────

    @Test
    fun `tab switch clears activeSession and overlayState (interrupt rule)`() {
        val withSession = ReaderUiReducer.reduce(
            ReaderUiState(activeTab = MainTab.BOOKSHELF),
            ReaderUiIntent.StartTtsSession(text = "测试朗读文本")
        )
        val withOverlay = ReaderUiReducer.reduce(
            withSession,
            ReaderUiIntent.OpenSheet(SheetContent.BookshelfFilter("filter"))
        )
        assertNotNull(withOverlay.activeSession)
        assertTrue(withOverlay.overlayState !is OverlayState.None)

        val afterTabSwitch = ReaderUiReducer.reduce(
            withOverlay,
            ReaderUiIntent.SelectTab(MainTab.SETTINGS)
        )
        assertNull("tab switch must clear activeSession", afterTabSwitch.activeSession)
        assertEquals(
            "tab switch must clear overlayState",
            OverlayState.None,
            afterTabSwitch.overlayState
        )
    }

    @Test
    fun `popRoute clears overlayState`() {
        val withSearch = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.Search)
        )
        val withOverlay = ReaderUiReducer.reduce(
            withSearch,
            ReaderUiIntent.OpenKeyboard(inputId = "search")
        )
        val popped = ReaderUiReducer.reduce(withOverlay, ReaderUiIntent.PopRoute)
        assertEquals(
            "PopRoute must clear overlayState",
            OverlayState.None,
            popped.overlayState
        )
    }

    // ── P3: Source Import e2e state machine ─────────────────────────────────────

    @Test
    fun `ParseSourceImport transitions Idle to Parsing`() {
        val initial = ReaderUiState(sourceImport = SourceImportState.Idle)
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.ParseSourceImport(json = "[{}]")
        )
        assertEquals(SourceImportState.Parsing, next.sourceImport)
    }

    @Test
    fun `CompleteSourceImportParse transitions Parsing to Preview`() {
        val entries = listOf(
            SourceImportPreviewEntry("起点", "qidian.com", "测试", SourceImportTone.GOOD),
            SourceImportPreviewEntry("旧源", "old.com", "自定义", SourceImportTone.WARN)
        )
        val parsing = ReaderUiState(sourceImport = SourceImportState.Parsing)
        val next = ReaderUiReducer.reduce(
            parsing,
            ReaderUiIntent.CompleteSourceImportParse(entries)
        )
        assertEquals(SourceImportState.Preview::class, next.sourceImport::class)
        val preview = next.sourceImport as SourceImportState.Preview
        assertEquals(2, preview.entries.size)
        assertEquals("起点", preview.entries.first().name)
    }

    @Test
    fun `ConfirmSourceImport transitions Preview to Importing`() {
        val preview = ReaderUiState(
            sourceImport = SourceImportState.Preview(entries = emptyList())
        )
        val next = ReaderUiReducer.reduce(
            preview,
            ReaderUiIntent.ConfirmSourceImport(conflictMode = "跳过重复")
        )
        assertEquals(SourceImportState.Importing, next.sourceImport)
    }

    @Test
    fun `ConfirmSourceImport from non-Preview state is no-op`() {
        val idle = ReaderUiState(sourceImport = SourceImportState.Idle)
        val next = ReaderUiReducer.reduce(
            idle,
            ReaderUiIntent.ConfirmSourceImport()
        )
        assertEquals(SourceImportState.Idle, next.sourceImport)
    }

    @Test
    fun `CompleteSourceImport transitions Importing to Done`() {
        val importing = ReaderUiState(sourceImport = SourceImportState.Importing)
        val next = ReaderUiReducer.reduce(
            importing,
            ReaderUiIntent.CompleteSourceImport(imported = 5, skipped = 2, failed = 1)
        )
        assertEquals(SourceImportState.Done::class, next.sourceImport::class)
        val done = next.sourceImport as SourceImportState.Done
        assertEquals(5, done.imported)
        assertEquals(2, done.skipped)
        assertEquals(1, done.failed)
    }

    @Test
    fun `FailSourceImport transitions to Error with message`() {
        val importing = ReaderUiState(sourceImport = SourceImportState.Importing)
        val next = ReaderUiReducer.reduce(
            importing,
            ReaderUiIntent.FailSourceImport(message = "network error")
        )
        assertEquals(SourceImportState.Error::class, next.sourceImport::class)
        assertEquals("network error", (next.sourceImport as SourceImportState.Error).message)
    }

    @Test
    fun `DismissSourceImportResult resets to Idle`() {
        val done = ReaderUiState(
            sourceImport = SourceImportState.Done(imported = 3, skipped = 0, failed = 0)
        )
        val next = ReaderUiReducer.reduce(done, ReaderUiIntent.DismissSourceImportResult)
        assertEquals(SourceImportState.Idle, next.sourceImport)
    }

    @Test
    fun `source-import e2e full happy path Idle to Done`() {
        var state = ReaderUiState(sourceImport = SourceImportState.Idle)
        state = ReaderUiReducer.reduce(
            state, ReaderUiIntent.ParseSourceImport(json = "[{...}]")
        )
        assertEquals(SourceImportState.Parsing, state.sourceImport)
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CompleteSourceImportParse(
                listOf(SourceImportPreviewEntry("A", "a.com", "G", SourceImportTone.GOOD))
            )
        )
        assertTrue(state.sourceImport is SourceImportState.Preview)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.ConfirmSourceImport())
        assertEquals(SourceImportState.Importing, state.sourceImport)
        state = ReaderUiReducer.reduce(
            state,
            ReaderUiIntent.CompleteSourceImport(imported = 1, skipped = 0, failed = 0)
        )
        assertTrue(state.sourceImport is SourceImportState.Done)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DismissSourceImportResult)
        assertEquals(SourceImportState.Idle, state.sourceImport)
    }

    // ── P3: WebDAV config state ─────────────────────────────────────────────────

    @Test
    fun `UpdateWebDavServer sets serverUrl`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.UpdateWebDavServer("https://dav.example.com/reader/")
        )
        assertEquals(
            "https://dav.example.com/reader/",
            next.webDavConfig.serverUrl
        )
    }

    @Test
    fun `UpdateWebDavCredentials sets username and password`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.UpdateWebDavCredentials("user", "pass")
        )
        assertEquals("user", next.webDavConfig.username)
        assertEquals("pass", next.webDavConfig.password)
    }

    @Test
    fun `TestWebDavConnection sets testStatus to Testing`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.TestWebDavConnection
        )
        assertEquals(WebDavTestStatus.Testing, next.webDavConfig.testStatus)
    }

    @Test
    fun `WebDavTestResult success sets Success with latency`() {
        val testing = ReaderUiState(
            webDavConfig = WebDavConfigState(testStatus = WebDavTestStatus.Testing)
        )
        val next = ReaderUiReducer.reduce(
            testing,
            ReaderUiIntent.WebDavTestResult(success = true, message = "OK", latencyMs = 120L)
        )
        assertEquals(WebDavTestStatus.Success::class, next.webDavConfig.testStatus::class)
        assertEquals(120L, (next.webDavConfig.testStatus as WebDavTestStatus.Success).latencyMs)
    }

    @Test
    fun `WebDavTestResult failure sets Error with message`() {
        val testing = ReaderUiState(
            webDavConfig = WebDavConfigState(testStatus = WebDavTestStatus.Testing)
        )
        val next = ReaderUiReducer.reduce(
            testing,
            ReaderUiIntent.WebDavTestResult(success = false, message = "timeout")
        )
        assertEquals(WebDavTestStatus.Error::class, next.webDavConfig.testStatus::class)
        assertEquals("timeout", (next.webDavConfig.testStatus as WebDavTestStatus.Error).message)
    }

    @Test
    fun `SaveWebDavConfig sets saveStatus to Saving`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.SaveWebDavConfig
        )
        assertEquals(WebDavSaveStatus.Saving, next.webDavConfig.saveStatus)
    }

    @Test
    fun `WebDavSaveResult success sets Saved and savedIdentifier`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.WebDavSaveResult(
                success = true,
                savedIdentifier = "webdav-primary",
                message = ""
            )
        )
        assertEquals(WebDavSaveStatus.Saved, next.webDavConfig.saveStatus)
        assertEquals("webdav-primary", next.webDavConfig.savedIdentifier)
    }

    @Test
    fun `RevokeWebDavConfig resets entire WebDavConfigState`() {
        val configured = ReaderUiState(
            webDavConfig = WebDavConfigState(
                serverUrl = "https://dav.example.com",
                username = "user",
                password = "pass",
                savedIdentifier = "webdav-primary"
            )
        )
        val next = ReaderUiReducer.reduce(configured, ReaderUiIntent.RevokeWebDavConfig)
        assertEquals(WebDavConfigState(), next.webDavConfig)
        assertEquals(null, next.webDavConfig.savedIdentifier)
    }

    // ── P3: Permission state ────────────────────────────────────────────────────

    @Test
    fun `PermissionGranted updates the matching kind to GRANTED`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PermissionGranted(PermissionKind.NOTIFICATIONS)
        )
        assertEquals(PermissionStatus.GRANTED, next.permissions.notifications)
        // Other permissions stay UNKNOWN.
        assertEquals(PermissionStatus.UNKNOWN, next.permissions.fileAccess)
        assertEquals(PermissionStatus.UNKNOWN, next.permissions.batteryOptimization)
    }

    @Test
    fun `PermissionDenied updates the matching kind to DENIED`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PermissionDenied(PermissionKind.FILE_ACCESS)
        )
        assertEquals(PermissionStatus.DENIED, next.permissions.fileAccess)
    }

    @Test
    fun `PermissionGranted for battery optimization only updates that kind`() {
        val next = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PermissionGranted(PermissionKind.BATTERY_OPTIMIZATION)
        )
        assertEquals(PermissionStatus.GRANTED, next.permissions.batteryOptimization)
        assertEquals(PermissionStatus.UNKNOWN, next.permissions.notifications)
    }

    @Test
    fun `RequestPermission is a side-effect intent - reducer does not change state`() {
        val initial = ReaderUiState()
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.RequestPermission(PermissionKind.NOTIFICATIONS)
        )
        assertEquals(initial, next)
    }

    @Test
    fun `OpenSystemPermissionSettings is a side-effect intent - reducer does not change state`() {
        val initial = ReaderUiState()
        val next = ReaderUiReducer.reduce(initial, ReaderUiIntent.OpenSystemPermissionSettings)
        assertEquals(initial, next)
    }

    // ── P4: RSS list state ──────────────────────────────────────────────────────

    @Test
    fun `LoadRssList transitions Idle to Loading`() {
        val initial = ReaderUiState(rssList = RssListState.Idle)
        val next = ReaderUiReducer.reduce(initial, ReaderUiIntent.LoadRssList)
        assertEquals(RssListState.Loading, next.rssList)
    }

    @Test
    fun `RssListLoaded transitions Loading to Success with source count`() {
        val loading = ReaderUiState(rssList = RssListState.Loading)
        val next = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.RssListLoaded(sourceCount = 7)
        )
        assertEquals(RssListState.Success::class, next.rssList::class)
        assertEquals(7, (next.rssList as RssListState.Success).sourceCount)
    }

    @Test
    fun `RssListEmpty transitions Loading to Empty`() {
        val loading = ReaderUiState(rssList = RssListState.Loading)
        val next = ReaderUiReducer.reduce(loading, ReaderUiIntent.RssListEmpty)
        assertEquals(RssListState.Empty, next.rssList)
    }

    @Test
    fun `RssListLoadFailed transitions to Error with message`() {
        val loading = ReaderUiState(rssList = RssListState.Loading)
        val next = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.RssListLoadFailed(message = "rss.list not implemented in Core")
        )
        assertEquals(RssListState.Error::class, next.rssList::class)
        assertEquals(
            "rss.list not implemented in Core",
            (next.rssList as RssListState.Error).message
        )
    }

    @Test
    fun `DismissRssListResult resets to Idle`() {
        val error = ReaderUiState(
            rssList = RssListState.Error(message = "boom", retryable = true)
        )
        val next = ReaderUiReducer.reduce(error, ReaderUiIntent.DismissRssListResult)
        assertEquals(RssListState.Idle, next.rssList)
    }

    @Test
    fun `rss list state machine full cycle Idle Loading Success Idle`() {
        var state = ReaderUiState(rssList = RssListState.Idle)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.LoadRssList)
        assertEquals(RssListState.Loading, state.rssList)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.RssListLoaded(sourceCount = 3))
        assertTrue(state.rssList is RssListState.Success)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.DismissRssListResult)
        assertEquals(RssListState.Idle, state.rssList)
    }

    // ── P0: Source Switch 专用 reducer tests ─────────────────────────────────────

    @Test
    fun `SourceSwitchOpen enters Loading and pushes SourceSwitchFlow route`() {
        val initial = ReaderUiState()
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.SourceSwitchOpen(bookId = "fixture://book/demo", requestId = "req-open")
        )

        // 换源状态进入 Loading
        assertEquals(SourceSwitchState.Loading, next.sourceSwitch)
        // route 被推入 backStack
        assertTrue("SourceSwitchFlow should be pushed onto backStack",
            next.backStack.last() is ReaderRoute.SourceSwitchFlow)
        assertTrue("currentRoute should be SourceSwitchFlow",
            next.currentRoute is ReaderRoute.SourceSwitchFlow)
    }

    @Test
    fun `SourceSwitchResultsLoaded transitions Loading to Results`() {
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.SourceSwitchOpen(bookId = "fixture://book/demo")
        )
        assertEquals(SourceSwitchState.Loading, loading.sourceSwitch)

        val results = listOf(
            SourceSwitchResult(sourceId = "src-1", sourceName = "优书网", latestChapter = "第32章"),
            SourceSwitchResult(sourceId = "src-2", sourceName = "笔趣阁", latestChapter = "第32章")
        )
        val withResults = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.SourceSwitchResultsLoaded(results = results, requestId = "req-loaded")
        )

        val switchState = withResults.sourceSwitch
        assertTrue("Expected Results state, got ${switchState::class}", switchState is SourceSwitchState.Results)
        val resultsState = switchState as SourceSwitchState.Results
        assertEquals(2, resultsState.results.size)
        assertEquals("优书网", resultsState.results[0].sourceName)
        // 初始 selectedSourceId 应为 null（未选中任何源）
        assertNull("selectedSourceId should be null after results loaded", resultsState.selectedSourceId)
    }

    @Test
    fun `SourceSwitchSelect records the selected source id`() {
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.SourceSwitchOpen(bookId = "fixture://book/demo")
        )
        val results = listOf(
            SourceSwitchResult(sourceId = "src-1", sourceName = "优书网"),
            SourceSwitchResult(sourceId = "src-2", sourceName = "笔趣阁")
        )
        val withResults = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.SourceSwitchResultsLoaded(results = results)
        )

        // 选择 src-2
        val afterSelect = ReaderUiReducer.reduce(
            withResults,
            ReaderUiIntent.SourceSwitchSelect(sourceId = "src-2", requestId = "req-select")
        )

        val switchState = afterSelect.sourceSwitch as SourceSwitchState.Results
        assertEquals("src-2", switchState.selectedSourceId)
        // results 列表本身不应被改动
        assertEquals(2, switchState.results.size)
    }

    @Test
    fun `SourceSwitchSelect on non-Results state is a no-op`() {
        // Idle 状态下 Select 应为 no-op
        val idle = ReaderUiState(sourceSwitch = SourceSwitchState.Idle)
        val next = ReaderUiReducer.reduce(
            idle,
            ReaderUiIntent.SourceSwitchSelect(sourceId = "src-1")
        )
        assertEquals(SourceSwitchState.Idle, next.sourceSwitch)

        // Loading 状态下 Select 也应为 no-op
        val loading = ReaderUiState(sourceSwitch = SourceSwitchState.Loading)
        val nextLoading = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.SourceSwitchSelect(sourceId = "src-1")
        )
        assertEquals(SourceSwitchState.Loading, nextLoading.sourceSwitch)
    }

    @Test
    fun `SourceSwitchClose returns to Idle and pops route`() {
        // 先 open 进入 Loading 并 push route
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.SourceSwitchOpen(bookId = "fixture://book/demo")
        )
        assertTrue(loading.backStack.isNotEmpty())
        assertEquals(SourceSwitchState.Loading, loading.sourceSwitch)

        // close 应清状态并 pop route
        val afterClose = ReaderUiReducer.reduce(loading, ReaderUiIntent.SourceSwitchClose)

        assertEquals(SourceSwitchState.Idle, afterClose.sourceSwitch)
        assertTrue("backStack should be empty after close", afterClose.backStack.isEmpty())
        // currentRoute 应该回退到 tab shell
        assertTrue(afterClose.currentRoute is ReaderRoute.TabShell)
    }

    @Test
    fun `source-switch full cycle open loaded select close`() {
        var state = ReaderUiState()
        // 1. open → Loading
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SourceSwitchOpen(bookId = "book-1"))
        assertEquals(SourceSwitchState.Loading, state.sourceSwitch)
        assertTrue(state.currentRoute is ReaderRoute.SourceSwitchFlow)

        // 2. results loaded → Results
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SourceSwitchResultsLoaded(
            results = listOf(
                SourceSwitchResult(sourceId = "src-a", sourceName = "源A"),
                SourceSwitchResult(sourceId = "src-b", sourceName = "源B")
            )
        ))
        assertTrue(state.sourceSwitch is SourceSwitchState.Results)

        // 3. select → selectedSourceId 更新
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SourceSwitchSelect(sourceId = "src-b"))
        assertEquals("src-b", (state.sourceSwitch as SourceSwitchState.Results).selectedSourceId)

        // 4. close → Idle, route popped
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SourceSwitchClose)
        assertEquals(SourceSwitchState.Idle, state.sourceSwitch)
        assertTrue(state.backStack.isEmpty())
    }

    // ── P0: book-detail / settings route/reducer focused tests ──────────────────

    @Test
    fun `book-detail route can be pushed and popped`() {
        val initial = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-detail"), requestId = "req-detail")
        )

        assertEquals("book-detail", next.currentRoute.routeId)
        assertEquals(1, next.backStack.size)
        assertEquals("book-detail", next.backStack.last().routeId)

        // pop 回到 bookshelf tab
        val afterBack = ReaderUiReducer.reduce(next, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.BOOKSHELF, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `book-directory route can be pushed from book-detail`() {
        val withDetail = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-detail"))
        )
        assertEquals("book-detail", withDetail.currentRoute.routeId)

        val withDirectory = ReaderUiReducer.reduce(
            withDetail,
            ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-directory"), requestId = "req-dir")
        )
        assertEquals("book-directory", withDirectory.currentRoute.routeId)
        assertEquals(2, withDirectory.backStack.size)
        assertEquals("book-detail", withDirectory.backStack[0].routeId)
        assertEquals("book-directory", withDirectory.backStack[1].routeId)

        // pop 回到 book-detail
        val afterBack = ReaderUiReducer.reduce(withDirectory, ReaderUiIntent.PopRoute)
        assertEquals("book-detail", afterBack.currentRoute.routeId)
        assertEquals(1, afterBack.backStack.size)
    }

    @Test
    fun `settings-general route can be pushed and popped from settings tab`() {
        val initial = ReaderUiState(activeTab = MainTab.SETTINGS, currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS))
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.PushRoute(ReaderRoute.SettingsGeneral, requestId = "req-settings")
        )

        assertEquals(RouteIds.SETTINGS_GENERAL, next.currentRoute.routeId)
        assertEquals(1, next.backStack.size)

        val afterBack = ReaderUiReducer.reduce(next, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.SETTINGS, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `sync-backup route can be pushed and popped from settings tab`() {
        val initial = ReaderUiState(activeTab = MainTab.SETTINGS, currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS))
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.PushRoute(ReaderRoute.SyncBackup, requestId = "req-backup")
        )

        assertEquals(RouteIds.SYNC_BACKUP, next.currentRoute.routeId)
        assertEquals(1, next.backStack.size)

        val afterBack = ReaderUiReducer.reduce(next, ReaderUiIntent.PopRoute)
        assertTrue(afterBack.backStack.isEmpty())
        assertEquals(MainTab.SETTINGS, (afterBack.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `settings subpage route push does not change activeTab`() {
        val initial = ReaderUiState(activeTab = MainTab.SETTINGS, currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS))
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.PushRoute(ReaderRoute.AboutFeedback)
        )

        assertEquals("activeTab must stay SETTINGS after pushing settings subpage",
            MainTab.SETTINGS, next.activeTab)
        assertEquals(RouteIds.ABOUT_FEEDBACK, next.currentRoute.routeId)
    }

    @Test
    fun `book-detail push then settings subpage push preserves backStack order`() {
        val withDetail = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-detail"))
        )
        val withSettings = ReaderUiReducer.reduce(
            withDetail,
            ReaderUiIntent.PushRoute(ReaderRoute.SettingsGeneral)
        )

        assertEquals(2, withSettings.backStack.size)
        assertEquals("book-detail", withSettings.backStack[0].routeId)
        assertEquals(RouteIds.SETTINGS_GENERAL, withSettings.backStack[1].routeId)
        assertEquals(RouteIds.SETTINGS_GENERAL, withSettings.currentRoute.routeId)
    }

    // ── B2: book-detail 专用 reducer (BookDetailOpen/Loaded/LoadFailed/Close) ─────
    // 契约：state-rule.fixtures.json `book-detail-error-requires-error-pagestate`
    // （error 非空时 pageState 必须为 error 或 source-unavailable）。

    @Test
    fun `BookDetailOpen enters Loading and pushes book-detail route`() {
        val initial = ReaderUiState(activeTab = MainTab.BOOKSHELF)
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.BookDetailOpen(bookUrl = "fixture://book/demo", requestId = "req-open")
        )

        assertEquals(BookDetailPageState.Loading, next.bookDetail)
        assertEquals("book-detail", next.currentRoute.routeId)
        assertEquals(1, next.backStack.size)
        assertEquals("book-detail", next.backStack.last().routeId)
    }

    @Test
    fun `BookDetailLoaded transitions Loading to Ready (data loaded shows detail)`() {
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.BookDetailOpen(bookUrl = "fixture://book/demo")
        )
        assertEquals(BookDetailPageState.Loading, loading.bookDetail)

        // data loaded → Ready（显示详情）
        val ready = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.BookDetailLoaded(requestId = "req-loaded")
        )
        assertEquals(BookDetailPageState.Ready, ready.bookDetail)
        // Loaded 不改路由，仍为 book-detail
        assertEquals("book-detail", ready.currentRoute.routeId)
    }

    @Test
    fun `BookDetailLoadFailed transitions to Error pageState satisfying error-requires-error-pagestate contract`() {
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.BookDetailOpen(bookUrl = "fixture://book/demo")
        )

        // error → error pageState（契约：error 非空时 pageState 必须为 error）
        val failed = ReaderUiReducer.reduce(
            loading,
            ReaderUiIntent.BookDetailLoadFailed(message = "book.load not implemented", requestId = "req-fail")
        )

        assertTrue("Expected Error pageState, got ${failed.bookDetail::class}",
            failed.bookDetail is BookDetailPageState.Error)
        assertEquals(
            "book.load not implemented",
            (failed.bookDetail as BookDetailPageState.Error).message
        )
    }

    @Test
    fun `BookDetailClose pops route and resets bookDetail to Idle (close returns)`() {
        val loading = ReaderUiReducer.reduce(
            ReaderUiState(activeTab = MainTab.BOOKSHELF),
            ReaderUiIntent.BookDetailOpen(bookUrl = "fixture://book/demo")
        )
        assertEquals(BookDetailPageState.Loading, loading.bookDetail)

        // close → 回退，route popped，bookDetail 重置为 Idle
        val afterClose = ReaderUiReducer.reduce(loading, ReaderUiIntent.BookDetailClose)

        assertEquals(BookDetailPageState.Idle, afterClose.bookDetail)
        assertTrue(afterClose.backStack.isEmpty())
        assertEquals(MainTab.BOOKSHELF, (afterClose.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `book-detail back stack order preserved when pushing sub-route after BookDetailOpen`() {
        val withDetail = ReaderUiReducer.reduce(
            ReaderUiState(),
            ReaderUiIntent.BookDetailOpen(bookUrl = "fixture://book/demo")
        )
        assertEquals("book-detail", withDetail.backStack.last().routeId)

        // 再 push book-directory 子路由 → back stack 顺序 [book-detail, book-directory]
        val withDirectory = ReaderUiReducer.reduce(
            withDetail,
            ReaderUiIntent.PushRoute(ReaderRoute.BookState("book-directory"), requestId = "req-dir")
        )

        assertEquals(2, withDirectory.backStack.size)
        assertEquals("book-detail", withDirectory.backStack[0].routeId)
        assertEquals("book-directory", withDirectory.backStack[1].routeId)
        assertEquals("book-directory", withDirectory.currentRoute.routeId)

        // pop 回到 book-detail
        val afterBack = ReaderUiReducer.reduce(withDirectory, ReaderUiIntent.PopRoute)
        assertEquals("book-detail", afterBack.currentRoute.routeId)
        assertEquals(1, afterBack.backStack.size)
    }

    // ── B3: settings 专用 reducer (SettingsOpen/Close/TabSwitch/Overlay*) ────────
    // 契约：state-rule.fixtures.json `settings-overlay-guard-tab-switch`
    // （settings.overlay == expandedOption 时禁止 tab 切换）。

    @Test
    fun `SettingsOpen pushes settings sub-route and records activeTab`() {
        val initial = ReaderUiState(
            activeTab = MainTab.SETTINGS,
            currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
        )

        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.SYNC_BACKUP, requestId = "req-open")
        )

        assertEquals(SettingsTab.SYNC_BACKUP, next.settings.activeTab)
        assertEquals(RouteIds.SYNC_BACKUP, next.currentRoute.routeId)
        assertEquals(1, next.backStack.size)
        assertEquals(RouteIds.SYNC_BACKUP, next.backStack.last().routeId)
    }

    @Test
    fun `SettingsOpen for ABOUT tab pushes about-feedback route`() {
        val initial = ReaderUiState(
            activeTab = MainTab.SETTINGS,
            currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
        )
        val next = ReaderUiReducer.reduce(
            initial,
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.ABOUT)
        )
        assertEquals(SettingsTab.ABOUT, next.settings.activeTab)
        assertEquals(RouteIds.ABOUT_FEEDBACK, next.currentRoute.routeId)
    }

    @Test
    fun `SettingsTabSwitch updates activeTab when overlay is NONE (tab switch changes UiState)`() {
        val withSettings = ReaderUiReducer.reduce(
            ReaderUiState(
                activeTab = MainTab.SETTINGS,
                currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
            ),
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.GENERAL)
        )
        assertEquals(SettingsTab.GENERAL, withSettings.settings.activeTab)

        // tab switch → UiState 切换（overlay NONE 时允许）
        val switched = ReaderUiReducer.reduce(
            withSettings,
            ReaderUiIntent.SettingsTabSwitch(tab = SettingsTab.ABOUT, requestId = "req-switch")
        )
        assertEquals(SettingsTab.ABOUT, switched.settings.activeTab)
    }

    @Test
    fun `SettingsTabSwitch is no-op when overlay is EXPANDED_OPTION (settings-overlay-guard-tab-switch)`() {
        val withSettings = ReaderUiReducer.reduce(
            ReaderUiState(
                activeTab = MainTab.SETTINGS,
                currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
            ),
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.GENERAL)
        )
        // 展开 overlay
        val expanded = ReaderUiReducer.reduce(withSettings, ReaderUiIntent.SettingsOverlayExpand)
        assertEquals(SettingsOverlay.EXPANDED_OPTION, expanded.settings.overlay)

        // tab 切换应被 async guard 拦截（no-op）
        val switched = ReaderUiReducer.reduce(
            expanded,
            ReaderUiIntent.SettingsTabSwitch(tab = SettingsTab.ABOUT)
        )
        assertEquals("activeTab must not change when overlay is EXPANDED_OPTION",
            SettingsTab.GENERAL, switched.settings.activeTab)
    }

    @Test
    fun `SettingsOverlayCollapse releases guard and allows tab switch`() {
        val withSettings = ReaderUiReducer.reduce(
            ReaderUiState(
                activeTab = MainTab.SETTINGS,
                currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
            ),
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.GENERAL)
        )
        val expanded = ReaderUiReducer.reduce(withSettings, ReaderUiIntent.SettingsOverlayExpand)
        // 收起 overlay 后 tab 切换应恢复
        val collapsed = ReaderUiReducer.reduce(expanded, ReaderUiIntent.SettingsOverlayCollapse)
        assertEquals(SettingsOverlay.NONE, collapsed.settings.overlay)

        val switched = ReaderUiReducer.reduce(
            collapsed,
            ReaderUiIntent.SettingsTabSwitch(tab = SettingsTab.SYNC_BACKUP)
        )
        assertEquals(SettingsTab.SYNC_BACKUP, switched.settings.activeTab)
    }

    @Test
    fun `SelectTab on settings route with overlay EXPANDED is no-op (guard via SelectTab)`() {
        // settings 路由 + overlay 展开 → SelectTab 应被守卫拦截
        val guarded = ReaderUiState(
            activeTab = MainTab.SETTINGS,
            currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS),
            settings = SettingsState(activeTab = SettingsTab.GENERAL, overlay = SettingsOverlay.EXPANDED_OPTION)
        )

        val next = ReaderUiReducer.reduce(guarded, ReaderUiIntent.SelectTab(MainTab.BOOKSHELF))

        assertEquals("activeTab must stay SETTINGS when overlay guard is active",
            MainTab.SETTINGS, next.activeTab)
        assertEquals(SettingsOverlay.EXPANDED_OPTION, next.settings.overlay)
    }

    @Test
    fun `SettingsClose pops route and returns to settings tab (close returns)`() {
        val withSettings = ReaderUiReducer.reduce(
            ReaderUiState(
                activeTab = MainTab.SETTINGS,
                currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
            ),
            ReaderUiIntent.SettingsOpen(tab = SettingsTab.SYNC_BACKUP)
        )
        assertEquals(RouteIds.SYNC_BACKUP, withSettings.currentRoute.routeId)

        // close → 回退到 settings tab
        val afterClose = ReaderUiReducer.reduce(withSettings, ReaderUiIntent.SettingsClose)

        assertTrue(afterClose.backStack.isEmpty())
        assertEquals(MainTab.SETTINGS, (afterClose.currentRoute as ReaderRoute.TabShell).tab)
    }

    @Test
    fun `settings full cycle open tabswitch expand collapse close`() {
        var state = ReaderUiState(
            activeTab = MainTab.SETTINGS,
            currentRoute = ReaderRoute.TabShell(MainTab.SETTINGS)
        )
        // 1. open settings-general
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SettingsOpen(tab = SettingsTab.GENERAL))
        assertEquals(SettingsTab.GENERAL, state.settings.activeTab)
        assertEquals(RouteIds.SETTINGS_GENERAL, state.currentRoute.routeId)

        // 2. tab switch to ABOUT（overlay NONE → 允许）
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SettingsTabSwitch(tab = SettingsTab.ABOUT))
        assertEquals(SettingsTab.ABOUT, state.settings.activeTab)

        // 3. expand overlay → tab switch 被守卫拦截
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SettingsOverlayExpand)
        val blockedState = ReaderUiReducer.reduce(state, ReaderUiIntent.SettingsTabSwitch(tab = SettingsTab.GENERAL))
        assertEquals(SettingsTab.ABOUT, blockedState.settings.activeTab)

        // 4. collapse → close
        state = ReaderUiReducer.reduce(blockedState, ReaderUiIntent.SettingsOverlayCollapse)
        state = ReaderUiReducer.reduce(state, ReaderUiIntent.SettingsClose)
        assertTrue(state.backStack.isEmpty())
    }
}
