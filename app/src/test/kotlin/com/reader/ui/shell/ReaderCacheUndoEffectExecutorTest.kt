package com.reader.ui.shell

import com.reader.android.data.adapter.CoreSlice10Service
import com.reader.android.data.adapter.Slice10CoreCommandClient
import com.reader.android.data.storage.InMemoryReaderReplaceUndoTokenStore
import com.reader.android.data.storage.ReaderReplaceUndoTokenStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCacheUndoEffectExecutorTest {

    @Test
    fun `AppShell dispatch closes reader cache action through Core and visible state`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("cache.book.status", method)
            cacheStatus(
                sourceId = params.getString("sourceId"),
                bookId = params.getString("bookId"),
                chapterCount = 7
            )
        })
        lateinit var vm: AppShellViewModel

        coroutineScope {
            vm = AppShellViewModel(
                readerUiRuntimeCoordinator = ReaderUiRuntimeCoordinator(
                    bookOpenPilotEnabled = false,
                    playbackPilotEnabled = false
                ),
                readerCacheUndoEffectExecutor = ReaderCacheUndoEffectExecutor(service),
                readerCacheUndoScope = this
            )
            vm.dispatch(
                ReaderUiIntent.EnterReaderFromAction(
                    sourceId = "source-live",
                    bookUrl = "book-live",
                    bookName = "Live book",
                    requestId = "book-open-live"
                )
            )
            vm.dispatch(ReaderUiIntent.RefreshCurrentBookCache(requestId = "status-live"))
        }

        assertEquals(
            "cache error=${vm.state.value.bookCache.error}",
            ReaderCoreActionPhase.SUCCEEDED,
            vm.state.value.bookCache.phase
        )
        assertEquals("source-live", vm.state.value.bookCache.sourceId)
        assertEquals("book-live", vm.state.value.bookCache.bookId)
        assertEquals(7, vm.state.value.bookCache.chapterCount)
        assertTrue(vm.state.value.bookCache.message!!.contains("0/7"))
    }

    @Test
    fun `prefetch selector is derived from live reader context and Core status`() = runBlocking {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            calls += method to JSONObject(params.toString())
            when (method) {
                "cache.book.prefetch" -> JSONObject()
                    .put("sourceId", params.getString("sourceId"))
                    .put("bookId", params.getString("bookId"))
                    .put("chapterRange", JSONArray(params.getJSONArray("chapterRange").toString()))
                    .put("chapterCount", 10)
                    .put("prefetchedCount", 2)
                    .put("queuedIndexes", JSONArray())
                    .put("alreadyQueuedIndexes", JSONArray())
                    .put("skippedCachedIndexes", JSONArray())
                "cache.book.status" -> cacheStatus(
                    sourceId = params.getString("sourceId"),
                    bookId = params.getString("bookId"),
                    chapterCount = 10
                )
                else -> error("unexpected method $method")
            }
        })
        val executor = ReaderCacheUndoEffectExecutor(service, clockMillis = { 777L })
        val state = liveReaderState(chapterIndex = 3).copy(
            bookCache = ReaderBookCacheUiState(
                phase = ReaderCoreActionPhase.SUCCEEDED,
                sourceId = "source-live",
                bookId = "book-live",
                tocAvailable = true,
                chapterCount = 10
            )
        )
        val intent = ReaderUiIntent.PrefetchCurrentBookCache(
            includeCurrentChapter = false,
            chapterCount = 2,
            requestId = "cache-live-1"
        )
        val terminal = mutableListOf<ReaderUiIntent>()

        coroutineScope {
            assertTrue(executor.execute(intent, state, this, terminal::add))
        }

        assertEquals(listOf("cache.book.prefetch", "cache.book.status"), calls.map { it.first })
        val params = calls.first().second
        assertEquals("source-live", params.getString("sourceId"))
        assertEquals("book-live", params.getString("bookId"))
        assertEquals(4, params.getJSONArray("chapterRange").getInt(0))
        assertEquals(6, params.getJSONArray("chapterRange").getInt(1))
        assertEquals(777L, params.getLong("requestedAt"))
        val succeeded = terminal.single() as ReaderUiIntent.CacheActionSucceeded
        assertEquals(ReaderCacheAction.PREFETCH_NEXT, succeeded.action)
        assertEquals("source-live", succeeded.snapshot!!.sourceId)
    }

    @Test
    fun `replace undo uses only token returned by preceding Core persist`() = runBlocking {
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val durableTransactionId = "2b640da8-55b1-4524-ae2f-e2a8c0063a6f"
        val coreIssuedToken = JSONObject()
            .put("schemaVersion", 1)
            .put("transactionId", durableTransactionId)
            .put("revision", "b".repeat(64))
            .put("operation", "create")
            .put("ruleId", 91L)
            .put("issuedAt", 100L)
            .put("expiresAt", 400L)
            .put("after", persistedRule(91L))
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            calls += method to JSONObject(params.toString())
            when (method) {
                "replace.persist" -> JSONObject()
                    .put("operation", "create")
                    .put("data", JSONObject().put("rule", persistedRule(91L)))
                    .put("undoToken", JSONObject(coreIssuedToken.toString()))
                "replace.undo" -> JSONObject()
                    .put("transactionId", durableTransactionId)
                    .put("revision", "b".repeat(64))
                    .put("operation", "create")
                    .put("ruleId", 91L)
                    .put("changed", true)
                    .put("undoneAt", 120L)
                else -> error("unexpected method $method")
            }
        })
        val tokenStore = InMemoryReaderReplaceUndoTokenStore()
        val executor = ReaderCacheUndoEffectExecutor(
            service = service,
            clockEpochSeconds = { 150L },
            transactionIdFactory = { durableTransactionId },
            undoTokenStore = tokenStore
        )
        val add = ReaderUiIntent.PersistReplaceRuleCreate(
            name = "weather",
            pattern = "rain",
            replacement = "sun",
            scope = "content",
            requestId = "persist-live-1"
        )
        val persistedEvents = mutableListOf<ReaderUiIntent>()

        coroutineScope {
            assertTrue(executor.execute(add, ReaderUiState(), this, persistedEvents::add))
        }
        val runningPersist = ReaderUiReducer.reduce(ReaderUiState(), add)
        val persisted = ReaderUiReducer.reduce(runningPersist, persistedEvents.single())
        assertEquals(coreIssuedToken.toString(), persisted.replaceMutation.undoTokenJson)
        assertTrue(persisted.replaceMutation.canUndo)
        assertTrue(persisted.replaceMutation.undoTokenPersisted)
        assertEquals(coreIssuedToken.toString(), tokenStore.load())
        assertEquals("91", persisted.replaceRules.single().id)

        val undo = ReaderUiIntent.UndoLastReplace(requestId = "undo-live-1")
        val undoEvents = mutableListOf<ReaderUiIntent>()
        coroutineScope {
            assertTrue(executor.execute(undo, persisted, this, undoEvents::add))
        }

        assertEquals(listOf("replace.persist", "replace.undo"), calls.map { it.first })
        assertEquals(durableTransactionId, calls.first().second.getString("transactionId"))
        assertNotEquals(add.requestId, calls.first().second.getString("transactionId"))
        assertEquals(
            coreIssuedToken.toString(),
            calls.last().second.getJSONObject("undoToken").toString()
        )
        val runningUndo = ReaderUiReducer.reduce(persisted, undo)
        val undone = ReaderUiReducer.reduce(runningUndo, undoEvents.single())
        assertTrue(undone.replaceRules.isEmpty())
        assertFalse(undone.replaceMutation.canUndo)
        assertNull(tokenStore.load())
        assertEquals("已撤销上次规则修改", undone.replaceMutation.message)
    }

    @Test
    fun `visible replace load hydrates complete persisted Core rules`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, _ ->
            assertEquals("replace-rule.list", method)
            JSONObject().put(
                "rules",
                JSONArray()
                    .put(persistedRule(11L, name = "first", replacement = "one"))
                    .put(persistedRule(12L, name = "second", replacement = "two"))
            )
        })
        lateinit var vm: AppShellViewModel

        coroutineScope {
            vm = AppShellViewModel(
                readerCacheUndoEffectExecutor = ReaderCacheUndoEffectExecutor(service),
                readerCacheUndoScope = this
            )
            vm.dispatch(ReaderUiIntent.LoadReplaceRules(requestId = "replace-load-live"))
        }

        assertEquals(ReaderCoreActionPhase.SUCCEEDED, vm.state.value.replaceMutation.phase)
        assertEquals(listOf("11", "12"), vm.state.value.replaceRules.map { it.id })
        assertEquals(listOf("rain", "rain"), vm.state.value.replaceRules.map { it.pattern })
        assertEquals(listOf("one", "two"), vm.state.value.replaceRules.map { it.replacement })
        assertEquals(listOf("content", "content"), vm.state.value.replaceRules.map { it.scope })
    }

    @Test
    fun `failed persistence of a new undo token clears an older durable token`() = runBlocking {
        var storedToken: String? = "old-token"
        val failingStore = object : ReaderReplaceUndoTokenStore {
            override fun load(): String? = storedToken
            override fun save(tokenJson: String): Boolean = false
            override fun clear(): Boolean {
                storedToken = null
                return true
            }
        }
        val transactionId = "42ed8b97-8c06-4ac0-9f05-80ecf45bcabb"
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, _ ->
            assertEquals("replace.persist", method)
            val rule = persistedRule(93L)
            JSONObject()
                .put("operation", "create")
                .put("data", JSONObject().put("rule", rule))
                .put(
                    "undoToken",
                    JSONObject()
                        .put("schemaVersion", 1)
                        .put("transactionId", transactionId)
                        .put("revision", "9".repeat(64))
                        .put("operation", "create")
                        .put("ruleId", 93L)
                        .put("issuedAt", 100L)
                        .put("expiresAt", 400L)
                        .put("after", JSONObject(rule.toString()))
                )
        })
        val executor = ReaderCacheUndoEffectExecutor(
            service = service,
            transactionIdFactory = { transactionId },
            undoTokenStore = failingStore
        )
        val intent = ReaderUiIntent.PersistReplaceRuleCreate(
            name = "weather",
            pattern = "rain",
            replacement = "sun",
            scope = "content",
            requestId = "persist-save-failed"
        )
        val terminal = mutableListOf<ReaderUiIntent>()

        coroutineScope { executor.execute(intent, ReaderUiState(), this, terminal::add) }

        val succeeded = terminal.single() as ReaderUiIntent.ReplacePersistSucceeded
        assertFalse(succeeded.undoTokenPersisted)
        assertNull(storedToken)
    }

    @Test
    fun `persisted Core undo token is restored into a new ViewModel without reconstruction`() {
        val token = JSONObject()
            .put("schemaVersion", 1)
            .put("transactionId", "restart-token")
            .put("revision", "f".repeat(64))
            .put("operation", "create")
            .put("ruleId", 91L)
            .put("issuedAt", 100L)
            .put("expiresAt", 400L)
            .put("after", persistedRule(91L))
        val tokenStore = InMemoryReaderReplaceUndoTokenStore(token.toString())
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            error("restoring UI state must not call Core")
        })

        val vm = AppShellViewModel(
            readerCacheUndoEffectExecutor = ReaderCacheUndoEffectExecutor(
                service = service,
                clockEpochSeconds = { 150L },
                undoTokenStore = tokenStore
            )
        )

        assertEquals(token.toString(), vm.state.value.replaceMutation.undoTokenJson)
        assertEquals("restart-token", JSONObject(vm.state.value.replaceMutation.undoTokenJson!!).getString("transactionId"))
        assertTrue(vm.state.value.replaceMutation.undoTokenPersisted)
        assertTrue(vm.state.value.replaceMutation.canUndo)
    }

    @Test
    fun `expired persisted undo token is cleared instead of exposed`() {
        val token = JSONObject()
            .put("schemaVersion", 1)
            .put("transactionId", "expired-token")
            .put("revision", "a".repeat(64))
            .put("operation", "create")
            .put("ruleId", 91L)
            .put("issuedAt", 100L)
            .put("expiresAt", 101L)
            .put("after", persistedRule(91L))
        val tokenStore = InMemoryReaderReplaceUndoTokenStore(token.toString())

        val vm = AppShellViewModel(
            readerCacheUndoEffectExecutor = ReaderCacheUndoEffectExecutor(
                service = CoreSlice10Service(Slice10CoreCommandClient { _, _ -> JSONObject() }),
                clockEpochSeconds = { 101L },
                undoTokenStore = tokenStore
            )
        )

        assertFalse(vm.state.value.replaceMutation.canUndo)
        assertNull(tokenStore.load())
    }

    @Test
    fun `prefetch commit plus refresh failure invalidates the old projection visibly`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            when (method) {
                "cache.book.prefetch" -> JSONObject()
                    .put("sourceId", params.getString("sourceId"))
                    .put("bookId", params.getString("bookId"))
                    .put("chapterRange", JSONArray(params.getJSONArray("chapterRange").toString()))
                    .put("chapterCount", 10)
                    .put("prefetchedCount", 1)
                    .put("queuedIndexes", JSONArray())
                    .put("alreadyQueuedIndexes", JSONArray())
                    .put("skippedCachedIndexes", JSONArray())
                "cache.book.status" -> error("refresh unavailable")
                else -> error("unexpected method $method")
            }
        })
        val executor = ReaderCacheUndoEffectExecutor(service, clockMillis = { 777L })
        val before = liveReaderState(chapterIndex = 3).copy(
            bookCache = ReaderBookCacheUiState(
                phase = ReaderCoreActionPhase.SUCCEEDED,
                sourceId = "source-live",
                bookId = "book-live",
                tocAvailable = true,
                chapterCount = 10,
                chapters = listOf(ReaderCacheChapterUiState(3, "old", "cached", 12L)),
                cachedCount = 1
            )
        )
        val intent = ReaderUiIntent.PrefetchCurrentBookCache(
            includeCurrentChapter = true,
            chapterCount = 1,
            requestId = "prefetch-partial"
        )
        val terminal = mutableListOf<ReaderUiIntent>()

        coroutineScope { executor.execute(intent, before, this, terminal::add) }
        val after = ReaderUiReducer.reduce(
            ReaderUiReducer.reduce(before, intent),
            terminal.single()
        )

        assertEquals(ReaderCoreActionPhase.PARTIAL, after.bookCache.phase)
        assertTrue(after.bookCache.chapters.isEmpty())
        assertEquals(0L, after.bookCache.cachedCount)
        assertTrue(after.bookCache.error!!.contains("状态刷新失败"))
        assertTrue(after.bookCache.message!!.contains("已完成 1 章缓存"))
    }

    @Test
    fun `global cache clear commits and explicitly clears every old projection`() = runBlocking {
        val service = CoreSlice10Service(Slice10CoreCommandClient { method, params ->
            assertEquals("cache.clear", method)
            assertEquals("cache", params.getString("scope"))
            JSONObject()
                .put("scope", "cache")
                .put("cacheEntriesRemoved", 3L)
                .put("chapterEntriesRemoved", 4L)
                .put("queueEntriesRemoved", 2L)
                .put("removedContentBytes", 99L)
        })
        val executor = ReaderCacheUndoEffectExecutor(service)
        val before = ReaderUiState(
            bookCache = ReaderBookCacheUiState(
                phase = ReaderCoreActionPhase.SUCCEEDED,
                sourceId = "stale-source",
                bookId = "stale-book",
                chapterCount = 7,
                chapters = listOf(ReaderCacheChapterUiState(0, "stale", "cached", 99L)),
                cachedCount = 1,
                totalContentBytes = 99L
            )
        )
        val intent = ReaderUiIntent.ClearCache(requestId = "clear-global")
        val terminal = mutableListOf<ReaderUiIntent>()

        coroutineScope { executor.execute(intent, before, this, terminal::add) }
        val after = ReaderUiReducer.reduce(
            ReaderUiReducer.reduce(before, intent),
            terminal.single()
        )

        assertEquals(ReaderCoreActionPhase.SUCCEEDED, after.bookCache.phase)
        assertNull(after.bookCache.sourceId)
        assertNull(after.bookCache.bookId)
        assertTrue(after.bookCache.chapters.isEmpty())
        assertEquals(0L, after.bookCache.cachedCount)
        assertEquals(0L, after.bookCache.totalContentBytes)
        assertTrue(after.bookCache.message!!.contains("已清理 3 个缓存项"))
    }

    @Test
    fun `undo without a Core issued token fails before Core dispatch`() = runBlocking {
        var calls = 0
        val service = CoreSlice10Service(Slice10CoreCommandClient { _, _ ->
            calls += 1
            JSONObject()
        })
        val executor = ReaderCacheUndoEffectExecutor(service)
        val terminal = mutableListOf<ReaderUiIntent>()

        coroutineScope {
            executor.execute(
                ReaderUiIntent.UndoLastReplace(requestId = "undo-missing"),
                ReaderUiState(),
                this,
                terminal::add
            )
        }

        assertEquals(0, calls)
        assertEquals("没有 Core 签发的可撤销操作", (terminal.single() as ReaderUiIntent.ReplaceCoreActionFailed).message)
    }

    @Test
    fun `stale cache result cannot replace another live book projection`() {
        val loading = ReaderUiReducer.reduce(
            liveReaderState(chapterIndex = 0),
            ReaderUiIntent.RefreshCurrentBookCache(requestId = "status-old")
        )
        val switched = loading.copy(
            readerContext = loading.readerContext!!.copy(sourceId = "source-new", bookUrl = "book-new")
        )
        val staleSnapshot = ReaderBookCacheUiState(
            sourceId = "source-live",
            bookId = "book-live",
            chapterCount = 5
        )

        val reduced = ReaderUiReducer.reduce(
            switched,
            ReaderUiIntent.CacheActionSucceeded(
                action = ReaderCacheAction.STATUS,
                snapshot = staleSnapshot,
                message = "stale",
                requestId = "status-old"
            )
        )

        assertEquals(switched, reduced)
    }

    private fun liveReaderState(chapterIndex: Int): ReaderUiState = ReaderUiState(
        readerContext = ReaderContext(
            sourceId = "source-live",
            bookUrl = "book-live",
            bookName = "Live book",
            entry = ReaderEntry.ACTION_TO_IMMERSIVE,
            chapterIndex = chapterIndex,
            entryRequestId = "book-open-live"
        )
    )

    private fun persistedRule(
        id: Long,
        name: String = "weather",
        replacement: String = "sun"
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("pattern", "rain")
        .put("replacement", replacement)
        .put("scopeTitle", false)
        .put("scopeContent", true)
        .put("isEnabled", true)
        .put("isRegex", true)
        .put("timeoutMillisecond", 3000L)
        .put("order", 0)

    private fun cacheStatus(sourceId: String, bookId: String, chapterCount: Int): JSONObject =
        JSONObject()
            .put("sourceId", sourceId)
            .put("bookId", bookId)
            .put("tocAvailable", true)
            .put("chapterCount", chapterCount)
            .put("chapters", JSONArray())
            .put("cachedCount", 0)
            .put("queuedCount", 0)
            .put("inProgressCount", 0)
            .put("completedCount", 0)
            .put("failedCount", 0)
            .put("cancelledCount", 0)
            .put("missingCount", chapterCount)
            .put(
                "globalStats",
                JSONObject()
                    .put("entryCount", 0)
                    .put("totalContentBytes", 0L)
                    .put("oldestCachedAt", JSONObject.NULL)
                    .put("newestCachedAt", JSONObject.NULL)
                    .put("queueEntryCount", 0)
                    .put("queuedCount", 0)
                    .put("inProgressCount", 0)
                    .put("completedCount", 0)
                    .put("failedCount", 0)
                    .put("cancelledCount", 0)
            )
}
