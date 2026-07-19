package com.reader.ui.shell

import com.reader.android.data.adapter.CoreBookCacheStatus
import com.reader.android.data.adapter.CoreCacheClearScope
import com.reader.android.data.adapter.CorePersistedReplaceRule
import com.reader.android.data.adapter.CoreReplacePersistOperation
import com.reader.android.data.adapter.CoreSlice10Service
import com.reader.android.data.adapter.ReaderCoreSlice10CommandClient
import com.reader.android.data.adapter.Slice10Outcome
import com.reader.android.data.storage.NoOpReaderReplaceUndoTokenStore
import com.reader.android.data.storage.ReaderReplaceUndoTokenStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Single native owner for the visible cache and replacement rollback actions.
 * It derives selectors from [ReaderUiState], executes Core once, and returns a
 * correlated terminal intent. It never consumes ScreenGraph payload fixtures.
 */
internal class ReaderCacheUndoEffectExecutor(
    private val service: CoreSlice10Service = CoreSlice10Service(ReaderCoreSlice10CommandClient()),
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val clockEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val transactionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val undoTokenStore: ReaderReplaceUndoTokenStore = NoOpReaderReplaceUndoTokenStore
) {
    fun execute(
        intent: ReaderUiIntent,
        stateBefore: ReaderUiState,
        scope: CoroutineScope,
        dispatch: (ReaderUiIntent) -> Unit
    ): Boolean = when (intent) {
        is ReaderUiIntent.RefreshCurrentBookCache -> {
            scope.launch { refreshBook(intent.requestId, stateBefore, dispatch) }
            true
        }
        is ReaderUiIntent.PrefetchCurrentBookCache -> {
            scope.launch { prefetchBook(intent, stateBefore, dispatch) }
            true
        }
        is ReaderUiIntent.ClearCurrentBookCache -> {
            scope.launch { clearBook(intent.requestId, stateBefore, dispatch) }
            true
        }
        is ReaderUiIntent.ClearCache -> {
            scope.launch { clearGlobal(intent.requestId, dispatch) }
            true
        }
        is ReaderUiIntent.LoadReplaceRules -> {
            scope.launch { loadReplaceRules(intent.requestId, dispatch) }
            true
        }
        is ReaderUiIntent.PersistReplaceRuleCreate,
        is ReaderUiIntent.PersistReplaceRuleUpdate,
        is ReaderUiIntent.PersistReplaceRuleDelete,
        is ReaderUiIntent.PersistReplaceRuleToggle -> {
            scope.launch { persistReplace(intent, stateBefore, dispatch) }
            true
        }
        is ReaderUiIntent.UndoLastReplace -> {
            scope.launch { undoReplace(intent.requestId, stateBefore, dispatch) }
            true
        }
        else -> false
    }

    /**
     * Restores only the exact private-storage value previously saved after a
     * successful Core persist. Invalid and expired values are removed instead
     * of being reconstructed or exposed as undoable UI state.
     */
    fun restorePersistedUndoTokenIntent(): ReaderUiIntent.RestoreReplaceUndoToken? {
        val tokenJson = runCatching(undoTokenStore::load).getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val token = when (val decoded = service.decodePersistedUndoToken(tokenJson)) {
            is Slice10Outcome.Success -> decoded.value
            is Slice10Outcome.Failed -> {
                runCatching(undoTokenStore::clear)
                return null
            }
        }
        if (clockEpochSeconds() >= token.expiresAt) {
            runCatching(undoTokenStore::clear)
            return null
        }
        return ReaderUiIntent.RestoreReplaceUndoToken(
            undoTokenJson = token.json.toString(),
            undoOperation = token.operation.wireValue,
            undoRuleId = token.ruleId,
            undoExpiresAt = token.expiresAt
        )
    }

    private suspend fun refreshBook(
        requestId: String,
        state: ReaderUiState,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        val identity = liveIdentity(state) ?: return dispatch.cacheFailed(
            requestId,
            "当前没有可用的书源与书籍标识"
        )
        when (val outcome = service.cacheBookStatus(identity.sourceId, identity.bookId)) {
            is Slice10Outcome.Success -> dispatch(
                ReaderUiIntent.CacheActionSucceeded(
                    action = ReaderCacheAction.STATUS,
                    snapshot = outcome.value.toUiState(),
                    message = "已刷新：${outcome.value.cachedCount}/${outcome.value.chapterCount} 章已缓存",
                    requestId = requestId
                )
            )
            is Slice10Outcome.Failed -> dispatch.cacheFailed(requestId, outcome.failure.message)
        }
    }

    private suspend fun prefetchBook(
        intent: ReaderUiIntent.PrefetchCurrentBookCache,
        state: ReaderUiState,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        val identity = liveIdentity(state) ?: return dispatch.cacheFailed(
            intent.requestId,
            "当前没有可用的书源与书籍标识"
        )
        val status = state.bookCache.takeIf {
            it.sourceId == identity.sourceId &&
                it.bookId == identity.bookId &&
                it.tocAvailable &&
                it.chapterCount > 0
        } ?: return dispatch.cacheFailed(intent.requestId, "请先刷新本书缓存状态")
        if (intent.chapterCount !in 1..128) {
            return dispatch.cacheFailed(intent.requestId, "单次缓存章节数必须在 1 到 128 之间")
        }
        val start = identity.chapterIndex + if (intent.includeCurrentChapter) 0 else 1
        val end = (start + intent.chapterCount).coerceAtMost(status.chapterCount)
        if (start !in 0 until status.chapterCount || end <= start) {
            return dispatch.cacheFailed(intent.requestId, "当前章节之后没有可缓存内容")
        }
        val action = if (intent.includeCurrentChapter) {
            ReaderCacheAction.PREFETCH_CURRENT
        } else {
            ReaderCacheAction.PREFETCH_NEXT
        }
        when (
            val outcome = service.prefetchBookCache(
                sourceId = identity.sourceId,
                bookId = identity.bookId,
                startInclusive = start,
                endExclusive = end,
                requestedAt = clockMillis()
            )
        ) {
            is Slice10Outcome.Failed -> dispatch.cacheFailed(intent.requestId, outcome.failure.message)
            is Slice10Outcome.Success -> {
                val refreshed = service.cacheBookStatus(identity.sourceId, identity.bookId)
                val message = "已完成 ${outcome.value.prefetchedCount} 章缓存（$start..${end - 1}）"
                dispatch.cacheMutationCompleted(
                    action = action,
                    refreshed = refreshed,
                    message = message,
                    requestId = intent.requestId
                )
            }
        }
    }

    private suspend fun clearBook(
        requestId: String,
        state: ReaderUiState,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        val identity = liveIdentity(state) ?: return dispatch.cacheFailed(
            requestId,
            "当前没有可用的书源与书籍标识"
        )
        when (
            val outcome = service.clearCache(
                CoreCacheClearScope.BOOK,
                identity.sourceId,
                identity.bookId
            )
        ) {
            is Slice10Outcome.Failed -> dispatch.cacheFailed(requestId, outcome.failure.message)
            is Slice10Outcome.Success -> {
                val refreshed = service.cacheBookStatus(identity.sourceId, identity.bookId)
                val message = "已清理本书 ${outcome.value.chapterEntriesRemoved} 个章节缓存"
                dispatch.cacheMutationCompleted(
                    action = ReaderCacheAction.CLEAR_BOOK,
                    refreshed = refreshed,
                    message = message,
                    requestId = requestId
                )
            }
        }
    }

    private suspend fun clearGlobal(
        requestId: String,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        when (val outcome = service.clearCache(CoreCacheClearScope.CACHE)) {
            is Slice10Outcome.Success -> dispatch(
                ReaderUiIntent.CacheActionSucceeded(
                    action = ReaderCacheAction.CLEAR_GLOBAL,
                    projectionInvalidated = true,
                    message = "已清理 ${outcome.value.cacheEntriesRemoved} 个缓存项",
                    requestId = requestId
                )
            )
            is Slice10Outcome.Failed -> dispatch.cacheFailed(requestId, outcome.failure.message)
        }
    }

    private suspend fun loadReplaceRules(
        requestId: String,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        when (val outcome = service.listReplaceRules()) {
            is Slice10Outcome.Failed -> dispatch.replaceFailed(requestId, outcome.failure.message)
            is Slice10Outcome.Success -> dispatch(
                ReaderUiIntent.ReplaceRulesHydrated(
                    rules = outcome.value.map { it.toUiRule() },
                    requestId = requestId
                )
            )
        }
    }

    private suspend fun persistReplace(
        intent: ReaderUiIntent,
        state: ReaderUiState,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        val requestId = intent.requestId
        val command = runCatching { buildPersistCommand(intent, state) }.getOrElse { error ->
            return dispatch.replaceFailed(requestId, error.message ?: "替换规则参数无效")
        }
        val transactionId = runCatching(transactionIdFactory).getOrNull()
            ?.takeIf { it.isNotBlank() && it != requestId }
            ?: return dispatch.replaceFailed(requestId, "无法生成唯一的替换事务标识")
        when (
            val outcome = service.persistReplace(
                operation = command.operation,
                params = command.params,
                transactionId = transactionId
            )
        ) {
            is Slice10Outcome.Failed -> dispatch.replaceFailed(requestId, outcome.failure.message)
            is Slice10Outcome.Success -> {
                val result = outcome.value
                val tokenJson = result.undoToken.json.toString()
                val tokenPersisted = runCatching { undoTokenStore.save(tokenJson) }
                    .getOrDefault(false)
                if (!tokenPersisted) {
                    // The previous durable token is no longer the latest transaction.
                    // Remove it so a restart cannot expose stale undo state.
                    runCatching(undoTokenStore::clear)
                }
                dispatch(
                    ReaderUiIntent.ReplacePersistSucceeded(
                        action = command.action,
                        rule = result.rule?.toUiRule(),
                        deletedRuleId = result.deletedRuleId,
                        undoTokenJson = tokenJson,
                        undoOperation = result.operation.wireValue,
                        undoRuleId = result.undoToken.ruleId,
                        undoExpiresAt = result.undoExpiresAt,
                        undoTokenPersisted = tokenPersisted,
                        requestId = requestId
                    )
                )
            }
        }
    }

    private suspend fun undoReplace(
        requestId: String,
        state: ReaderUiState,
        dispatch: (ReaderUiIntent) -> Unit
    ) {
        val token = state.replaceMutation.undoTokenJson
            ?.takeIf(String::isNotBlank)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return dispatch.replaceFailed(requestId, "没有 Core 签发的可撤销操作")
        when (val outcome = service.undoReplace(token)) {
            is Slice10Outcome.Failed -> dispatch.replaceFailed(requestId, outcome.failure.message)
            is Slice10Outcome.Success -> {
                runCatching(undoTokenStore::clear)
                dispatch(
                    ReaderUiIntent.ReplaceUndoSucceeded(
                        operation = outcome.value.operation,
                        ruleId = outcome.value.ruleId,
                        changed = outcome.value.changed,
                        restoredRule = outcome.value.restoredRule?.toUiRule(),
                        requestId = requestId
                    )
                )
            }
        }
    }

    private data class LiveBookIdentity(
        val sourceId: String,
        val bookId: String,
        val chapterIndex: Int
    )

    private fun liveIdentity(state: ReaderUiState): LiveBookIdentity? =
        state.readerContext?.let { context ->
            val sourceId = context.sourceId.trim()
            val bookId = context.bookUrl.trim()
            if (sourceId.isEmpty() || bookId.isEmpty() || context.chapterIndex < 0) null
            else LiveBookIdentity(sourceId, bookId, context.chapterIndex)
        }

    private data class PersistCommand(
        val operation: CoreReplacePersistOperation,
        val action: ReaderReplaceMutationAction,
        val params: JSONObject
    )

    private fun buildPersistCommand(intent: ReaderUiIntent, state: ReaderUiState): PersistCommand =
        when (intent) {
            is ReaderUiIntent.PersistReplaceRuleCreate -> {
                require(intent.name.isNotBlank() && intent.pattern.isNotBlank()) {
                    "规则名称和模式不能为空"
                }
                val (scopeTitle, scopeContent) = scopeFlags(intent.scope)
                PersistCommand(
                    operation = CoreReplacePersistOperation.CREATE,
                    action = ReaderReplaceMutationAction.CREATE,
                    params = JSONObject()
                        .put("name", intent.name)
                        .put("pattern", intent.pattern)
                        .put("replacement", intent.replacement)
                        .put("scopeTitle", scopeTitle)
                        .put("scopeContent", scopeContent)
                        .put("isEnabled", true)
                        .put("isRegex", true)
                        .put("timeoutMillisecond", 3_000L)
                        .put("order", state.replaceRules.size)
                )
            }
            is ReaderUiIntent.PersistReplaceRuleUpdate -> {
                val id = intent.id.requireCoreRuleId()
                val params = JSONObject().put("id", id)
                intent.name?.let { params.put("name", it) }
                intent.pattern?.let { params.put("pattern", it) }
                intent.replacement?.let { params.put("replacement", it) }
                intent.scope?.let { scope ->
                    val (scopeTitle, scopeContent) = scopeFlags(scope)
                    params.put("scopeTitle", scopeTitle).put("scopeContent", scopeContent)
                }
                PersistCommand(
                    operation = CoreReplacePersistOperation.UPDATE,
                    action = ReaderReplaceMutationAction.UPDATE,
                    params = params
                )
            }
            is ReaderUiIntent.PersistReplaceRuleDelete -> PersistCommand(
                operation = CoreReplacePersistOperation.DELETE,
                action = ReaderReplaceMutationAction.DELETE,
                params = JSONObject().put("id", intent.id.requireCoreRuleId())
            )
            is ReaderUiIntent.PersistReplaceRuleToggle -> {
                val rule = state.replaceRules.singleOrNull { it.id == intent.id }
                    ?: error("找不到要切换的 Core 替换规则")
                PersistCommand(
                    operation = CoreReplacePersistOperation.UPDATE,
                    action = ReaderReplaceMutationAction.TOGGLE,
                    params = JSONObject()
                        .put("id", intent.id.requireCoreRuleId())
                        .put("isEnabled", !rule.enabled)
                )
            }
            else -> error("不支持的替换规则操作")
        }

    private fun String.requireCoreRuleId(): Long =
        toLongOrNull()?.takeIf { it >= 0L } ?: error("该规则还没有 Core 数字标识")

    private fun scopeFlags(scope: String): Pair<Boolean, Boolean> = when (scope) {
        "all" -> true to true
        "title" -> true to false
        "content" -> false to true
        else -> error("不支持的替换范围")
    }

    private fun CoreBookCacheStatus.toUiState(): ReaderBookCacheUiState = ReaderBookCacheUiState(
        phase = ReaderCoreActionPhase.SUCCEEDED,
        sourceId = sourceId,
        bookId = bookId,
        tocAvailable = tocAvailable,
        chapterCount = chapterCount,
        chapters = chapters.map { chapter ->
            ReaderCacheChapterUiState(
                chapterIndex = chapter.chapterIndex,
                title = chapter.title,
                state = chapter.state,
                cachedBytes = chapter.cachedBytes,
                lastError = chapter.lastError
            )
        },
        cachedCount = cachedCount,
        queuedCount = queuedCount,
        inProgressCount = inProgressCount,
        failedCount = failedCount,
        missingCount = missingCount,
        totalContentBytes = globalStats.totalContentBytes
    )

    private fun CorePersistedReplaceRule.toUiRule(): ReplaceRule = ReplaceRule(
        id = id.toString(),
        name = name,
        pattern = pattern,
        replacement = replacement,
        enabled = enabled,
        scope = when {
            scopeTitle && scopeContent -> "all"
            scopeTitle -> "title"
            else -> "content"
        }
    )

    private fun ((ReaderUiIntent) -> Unit).cacheFailed(requestId: String, message: String) {
        invoke(ReaderUiIntent.CacheActionFailed(message = message, requestId = requestId))
    }

    private fun ((ReaderUiIntent) -> Unit).cacheMutationCompleted(
        action: ReaderCacheAction,
        refreshed: Slice10Outcome<CoreBookCacheStatus>,
        message: String,
        requestId: String
    ) {
        when (refreshed) {
            is Slice10Outcome.Success -> invoke(
                ReaderUiIntent.CacheActionSucceeded(
                    action = action,
                    snapshot = refreshed.value.toUiState(),
                    message = message,
                    requestId = requestId
                )
            )
            is Slice10Outcome.Failed -> invoke(
                ReaderUiIntent.CacheActionSucceeded(
                    action = action,
                    projectionInvalidated = true,
                    refreshError = "$message；缓存状态刷新失败：${refreshed.failure.message}",
                    message = message,
                    requestId = requestId
                )
            )
        }
    }

    private fun ((ReaderUiIntent) -> Unit).replaceFailed(requestId: String, message: String) {
        invoke(ReaderUiIntent.ReplaceCoreActionFailed(message = message, requestId = requestId))
    }
}
