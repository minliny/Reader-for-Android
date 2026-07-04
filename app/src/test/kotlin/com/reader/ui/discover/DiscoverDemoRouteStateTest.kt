package com.reader.ui.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverDemoRouteStateTest {
    private val requestedRoutes = setOf(
        "discover-control",
        "discover-sort",
        "discover-entry-ranking",
        "discover-entry-bestseller",
        "discover-entry-category",
        "discover-entry-finished",
        "discover-entry-latest",
        "discover-entry-new",
        "discover-entry-booklist",
        "discover-filter-keyword",
        "discover-filter-male",
        "discover-filter-female",
        "discover-sort-popularity",
        "discover-sort-update",
        "discover-sort-collection",
        "discover-sort-finished",
        "discover-sort-words",
        "discover-no-results",
        "discover-loading",
        "discover-refreshing",
        "discover-infinite-loading",
        "discover-page-two",
        "discover-cache-confirm",
        "discover-cache-toast",
        "discover-login-return",
        "discover-switching-source",
        "discover-switched-source",
        "discover-entry-error",
        "discover-empty",
        "discover-error",
        "discover-source-login",
        "discover-rule-test",
        "discover-source-bulk"
    )

    @Test
    fun requestedRouteIdsCoverDiscoverDemoScope() {
        assertEquals(33, discoverDemoRouteIds().size)
        assertEquals(requestedRoutes, discoverDemoRouteIds())
    }

    @Test
    fun everyRequestedRouteResolvesToDiscoverState() {
        requestedRoutes.forEach { routeId ->
            val state = discoverDemoRouteState(routeId)

            assertNotNull("route should resolve: $routeId", state)
            assertEquals(routeId, state!!.routeId)
        }
    }

    @Test
    fun entryFilterAndSortRoutesInjectCanonicalContext() {
        val category = discoverDemoRouteState(DiscoverDemoRouteIds.ENTRY_CATEGORY)!!
        val keyword = discoverDemoRouteState(DiscoverDemoRouteIds.FILTER_KEYWORD)!!
        val update = discoverDemoRouteState(DiscoverDemoRouteIds.SORT_UPDATE)!!
        val sortOpen = discoverDemoRouteState(DiscoverDemoRouteIds.SORT)!!
        val switched = discoverDemoRouteState(DiscoverDemoRouteIds.SWITCHED_SOURCE)!!

        assertEquals("分类", category.activeEntry)
        assertEquals(32, category.total)
        assertEquals("关键词", keyword.activeFilter)
        assertEquals(9, keyword.total)
        assertEquals("更新", update.activeSort)
        assertEquals(25, update.total)
        assertTrue(sortOpen.sortOpen)
        assertEquals("起点导入", switched.source.name)
        assertEquals(listOf("畅销", "分类", "新书", "完本"), switched.entries)
        assertEquals("更新", switched.activeSort)
        assertEquals(24, switched.total)
    }

    @Test
    fun loadingRefreshAndPagedRoutesPreserveListState() {
        val loading = discoverDemoRouteState(DiscoverDemoRouteIds.LOADING)!!
        val refreshing = discoverDemoRouteState(DiscoverDemoRouteIds.REFRESHING)!!
        val loginReturn = discoverDemoRouteState(DiscoverDemoRouteIds.LOGIN_RETURN)!!
        val infinite = discoverDemoRouteState(DiscoverDemoRouteIds.INFINITE_LOADING)!!
        val pageTwo = discoverDemoRouteState(DiscoverDemoRouteIds.PAGE_TWO)!!

        assertTrue(loading.loading)
        assertTrue(loading.books.isEmpty())
        assertEquals("正在刷新当前列表", refreshing.refreshingText)
        assertEquals("登录成功，正在刷新当前发现入口", loginReturn.refreshingText)
        assertTrue(infinite.infiniteLoading)
        assertEquals(6, infinite.books.size)
        assertTrue(pageTwo.pageTwo)
        assertEquals(38, pageTwo.total)
    }

    @Test
    fun emptyErrorNoResultAndDialogStatesKeepDemoTargets() {
        val empty = discoverDemoRouteState(DiscoverDemoRouteIds.EMPTY)!!
        val error = discoverDemoRouteState(DiscoverDemoRouteIds.ERROR)!!
        val noResults = discoverDemoRouteState(DiscoverDemoRouteIds.NO_RESULTS)!!
        val confirm = discoverDemoRouteState(DiscoverDemoRouteIds.CACHE_CONFIRM)!!
        val toast = discoverDemoRouteState(DiscoverDemoRouteIds.CACHE_TOAST)!!

        assertEquals(DiscoverDemoMainMode.Empty, empty.mainMode)
        assertEquals(setOf("source-management", "source-import-options"), empty.actionTargets())

        assertEquals(DiscoverDemoMainMode.Error, error.mainMode)
        assertEquals(setOf("discover-refreshing", "discover-control", "discover-rule-test"), error.actionTargets())
        assertFalse(error.books.isEmpty())

        assertEquals(DiscoverDemoMainMode.NoResults, noResults.mainMode)
        assertEquals(setOf("discover", "discover-control", "discover-refreshing"), noResults.actionTargets())

        assertEquals(setOf("discover-control", "discover-cache-toast"), confirm.actionTargets())
        assertEquals("已清除优书网发现缓存", toast.toast)
    }

    @Test
    fun sourceLoginRuleTestAndBulkPagesKeepDemoActionTargets() {
        val sourceLogin = discoverDemoRouteState(DiscoverDemoRouteIds.SOURCE_LOGIN)!!
        val ruleTest = discoverDemoRouteState(DiscoverDemoRouteIds.RULE_TEST)!!
        val bulk = discoverDemoRouteState(DiscoverDemoRouteIds.SOURCE_BULK)!!

        assertEquals(DiscoverDemoPage.SourceLogin, sourceLogin.page)
        assertEquals(
            setOf("discover-login-return", "discover-control"),
            discoverDemoActionTargets(DiscoverDemoRouteIds.SOURCE_LOGIN)
        )

        assertEquals(DiscoverDemoPage.RuleTest, ruleTest.page)
        assertEquals(9, ruleTest.fields.size)
        assertEquals(setOf("run-discover-rule-test", "discover-control"), ruleTest.actionTargets())

        assertEquals(DiscoverDemoPage.SourceBulk, bulk.page)
        assertEquals(5, bulk.bulkSources.size)
        assertEquals(
            setOf(
                "discover-control",
                "select-all-discover-sources",
                "enable-discover-sources",
                "disable-discover-sources",
                "refresh-discover-sources"
            ),
            bulk.actionTargets()
        )
    }

    @Test
    fun unsupportedRouteReturnsNull() {
        assertNull(discoverDemoRouteState("discover"))
        assertNull(discoverDemoRouteState("source-management"))
    }
}
