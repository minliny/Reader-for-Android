package com.reader.android.data.adapter

import com.reader.api.ReaderCoreClient
import java.net.URI
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class AndroidSlice10CapabilityStatus {
    CORE_PROTOCOL_AVAILABLE,
    CORE_QUERY_ONLY,
    LOCAL_BRIDGE_AVAILABLE,
    BLOCKED_MISSING_CORE_PROTOCOL,
    BLOCKED_MISSING_TRANSACTION_PROTOCOL,
    BLOCKED_MISSING_HOST_PROTOCOL,
    BLOCKED_DEVICE_EVIDENCE
}

data class AndroidSlice10CapabilityEntry(
    val capability: String,
    val status: AndroidSlice10CapabilityStatus,
    val detail: String
)

/**
 * Executable Android admission matrix for Slice 10. A route in ScreenGraph is
 * not treated as a runtime capability unless this table admits its Core/Host
 * boundary. Blocked rows are intentional production failures, not TODO success.
 */
object AndroidSlice10CapabilityMatrix {
    val entries: List<AndroidSlice10CapabilityEntry> = listOf(
        AndroidSlice10CapabilityEntry("bookmark.crud", AndroidSlice10CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core bookmark.* CRUD; locator jump remains separate"),
        AndroidSlice10CapabilityEntry("search.history", AndroidSlice10CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core-only search.history.*; no Room fallback"),
        AndroidSlice10CapabilityEntry("content.edit", AndroidSlice10CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core content-edit.put/get/list/delete"),
        AndroidSlice10CapabilityEntry("replace.rule", AndroidSlice10CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Core replace-rule.* plus existing opt-in runtime pilot"),
        AndroidSlice10CapabilityEntry("txt.toc.rule", AndroidSlice10CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Core txt-toc-rule.*; management UI not yet admitted"),
        AndroidSlice10CapabilityEntry("dict.rule", AndroidSlice10CapabilityStatus.CORE_QUERY_ONLY, "Core exposes dict-rule.query but no CRUD/version protocol"),
        AndroidSlice10CapabilityEntry("source.switch", AndroidSlice10CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Existing source.switch pilot remains evidence-gated"),
        AndroidSlice10CapabilityEntry("http.tts", AndroidSlice10CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core CRUD + safe request descriptor; audio playback Host contract missing"),
        AndroidSlice10CapabilityEntry("bookmark.locator", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_CORE_PROTOCOL, "Bookmark DTO has chapter index/position but no canonical locator round-trip"),
        AndroidSlice10CapabilityEntry("reading.history", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_CORE_PROTOCOL, "ReadRecord list/clear/version contract not frozen for Reader UI"),
        AndroidSlice10CapabilityEntry("content.edit.transaction", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_TRANSACTION_PROTOCOL, "No version/conflict/rollback transaction in content-edit V1"),
        AndroidSlice10CapabilityEntry("cover.change", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_TRANSACTION_PROTOCOL, "No frozen cover search/change/cache rollback DTO"),
        AndroidSlice10CapabilityEntry("chapter.reviews", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_CORE_PROTOCOL, "No frozen review list/paging/chapter locator DTO"),
        AndroidSlice10CapabilityEntry("media.session", AndroidSlice10CapabilityStatus.BLOCKED_MISSING_HOST_PROTOCOL, "No media-key/audio-focus/background playback Host contract"),
        AndroidSlice10CapabilityEntry("slice10.device", AndroidSlice10CapabilityStatus.BLOCKED_DEVICE_EVIDENCE, "No attached Android device or required recordings")
    )

    init {
        check(entries.map { it.capability }.toSet().size == entries.size)
    }

    fun status(capability: String): AndroidSlice10CapabilityStatus? =
        entries.singleOrNull { it.capability == capability }?.status
}

fun interface Slice10CoreCommandClient {
    suspend fun send(method: String, params: JSONObject): JSONObject
}

class ReaderCoreSlice10CommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : Slice10CoreCommandClient {
    override suspend fun send(method: String, params: JSONObject): JSONObject =
        coreProvider().sendAndAwait(method, params)
}

enum class Slice10FailureCode {
    INVALID_INPUT,
    CORE_UNAVAILABLE,
    CORE_REJECTED,
    CORE_PROTOCOL_MISMATCH,
    UNSAFE_HTTP_DESCRIPTOR,
    CAPABILITY_BLOCKED
}

data class Slice10Failure(
    val code: Slice10FailureCode,
    val message: String,
    val retryable: Boolean = false
)

sealed interface Slice10Outcome<out T> {
    data class Success<T>(val value: T) : Slice10Outcome<T>
    data class Failed(val failure: Slice10Failure) : Slice10Outcome<Nothing>
}

data class CoreBookmarkSnapshot(
    val time: Long,
    val bookName: String,
    val bookAuthor: String,
    val chapterIndex: Int,
    val chapterPosition: Int,
    val chapterName: String,
    val excerpt: String,
    val note: String
)

data class CoreContentEditSnapshot(
    val editId: Long,
    val bookId: String,
    val chapterIndex: Int,
    val editedContent: String,
    val editedAt: Long
)

data class CoreRuleSummary(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val order: Int
)

data class CoreHttpTtsSummary(
    val id: Long,
    val name: String,
    val contentType: String?,
    val cookieJarEnabled: Boolean
)

data class CoreCacheChapterStatus(
    val chapterIndex: Int,
    val title: String,
    val url: String,
    val state: String,
    val cachedBytes: Long,
    val attempts: Int,
    val maxAttempts: Int,
    val lastError: String?
)

data class CoreCacheGlobalStats(
    val entryCount: Long,
    val totalContentBytes: Long,
    val oldestCachedAt: Long?,
    val newestCachedAt: Long?,
    val queueEntryCount: Long,
    val queuedCount: Long,
    val inProgressCount: Long,
    val completedCount: Long,
    val failedCount: Long,
    val cancelledCount: Long
)

data class CoreBookCacheStatus(
    val sourceId: String,
    val bookId: String,
    val tocAvailable: Boolean,
    val chapterCount: Int,
    val chapters: List<CoreCacheChapterStatus>,
    val cachedCount: Long,
    val queuedCount: Long,
    val inProgressCount: Long,
    val completedCount: Long,
    val failedCount: Long,
    val cancelledCount: Long,
    val missingCount: Long,
    val globalStats: CoreCacheGlobalStats
)

data class CoreBookPrefetchResult(
    val sourceId: String,
    val bookId: String,
    val startInclusive: Int,
    val endExclusive: Int,
    val chapterCount: Int,
    val prefetchedCount: Long,
    val queuedIndexes: List<Int>,
    val alreadyQueuedIndexes: List<Int>,
    val skippedCachedIndexes: List<Int>
)

enum class CoreCacheClearScope(val wireValue: String) {
    ALL("all"),
    CACHE("cache"),
    BOOK("book")
}

data class CoreCacheClearResult(
    val scope: CoreCacheClearScope,
    val cacheEntriesRemoved: Long,
    val chapterEntriesRemoved: Long,
    val queueEntriesRemoved: Long,
    val removedContentBytes: Long
)

enum class CoreReplacePersistOperation(val wireValue: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete")
}

data class CorePersistedReplaceRule(
    val id: Long,
    val name: String,
    val group: String?,
    val pattern: String,
    val replacement: String,
    val rawScope: String?,
    val scopeTitle: Boolean,
    val scopeContent: Boolean,
    val excludeScope: String?,
    val enabled: Boolean,
    val isRegex: Boolean,
    val timeoutMillisecond: Long,
    val order: Int
)

data class CoreReplacePersistResult(
    val operation: CoreReplacePersistOperation,
    val rule: CorePersistedReplaceRule?,
    val deletedRuleId: Long?,
    val deleted: Boolean?,
    /** Exact opaque token returned by Core; Android never reconstructs it. */
    val undoToken: CoreReplaceUndoToken,
    val undoExpiresAt: Long
)

data class CoreReplaceUndoToken(
    /** Exact object returned by Core. It is copied, stored, and sent back without reconstruction. */
    val json: JSONObject,
    val transactionId: String,
    val revision: String,
    val operation: CoreReplacePersistOperation,
    val ruleId: Long,
    val issuedAt: Long,
    val expiresAt: Long,
    val before: CorePersistedReplaceRule?,
    val after: CorePersistedReplaceRule?
)

data class CoreReplaceUndoResult(
    val transactionId: String,
    val revision: String,
    val operation: String,
    val ruleId: Long,
    val changed: Boolean,
    val undoneAt: Long,
    val restoredRule: CorePersistedReplaceRule?
)

/**
 * Credential-bearing headers are deliberately private and never included in
 * [toString]. The descriptor may only be serialized at the Host dispatch edge.
 */
class SecureHttpTtsDescriptor internal constructor(
    val method: String,
    val url: String,
    private val headers: JSONObject,
    private val body: String?,
    val contentType: String?,
    val concurrentRate: String?
) {
    fun toHostExecuteParams(): JSONObject = JSONObject()
        .put("method", method)
        .put("url", url)
        .put("headers", JSONObject(headers.toString()))
        .apply { body?.let { put("body", it) } }

    override fun toString(): String =
        "SecureHttpTtsDescriptor(method=$method, url=${redactedUrl(url)}, headers=REDACTED, body=${if (body == null) "none" else "REDACTED"})"

    private fun redactedUrl(value: String): String = runCatching {
        val uri = URI(value)
        "${uri.scheme}://${uri.host ?: "host"}${uri.path.orEmpty()}"
    }.getOrDefault("url:REDACTED")
}

/** Core-only Slice 10 query/write bridge. It owns no Android database. */
class CoreSlice10Service(
    private val core: Slice10CoreCommandClient
) {
    suspend fun listBookmarks(
        bookName: String? = null,
        bookAuthor: String? = null
    ): Slice10Outcome<List<CoreBookmarkSnapshot>> {
        if ((bookName == null) != (bookAuthor == null)) {
            return invalid("bookName and bookAuthor must be supplied together")
        }
        val params = JSONObject().apply {
            bookName?.takeIf { it.isNotBlank() }?.let { put("bookName", it) }
            bookAuthor?.takeIf { it.isNotBlank() }?.let { put("bookAuthor", it) }
        }
        return call("bookmark.list", params) { result ->
            val rows = result.getJSONArray("bookmarks")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreBookmarkSnapshot(
                    time = row.getLong("time"),
                    bookName = row.getString("bookName"),
                    bookAuthor = row.optString("bookAuthor", ""),
                    chapterIndex = row.getInt("chapterIndex"),
                    chapterPosition = row.getInt("chapterPos"),
                    chapterName = row.optString("chapterName", ""),
                    excerpt = row.optString("bookText", ""),
                    note = row.optString("content", "")
                )
            }
        }
    }

    suspend fun putContentEdit(
        bookId: String,
        chapterIndex: Int,
        editedContent: String,
        editedAt: Long
    ): Slice10Outcome<CoreContentEditSnapshot> {
        if (bookId.isBlank() || chapterIndex < 0 || editedContent.isBlank() || editedAt < 0L) {
            return invalid("content edit fields are invalid")
        }
        return call(
            "content-edit.put",
            JSONObject()
                .put("bookId", bookId)
                .put("chapterIndex", chapterIndex)
                .put("editedContent", editedContent)
                .put("editedAt", editedAt)
        ) { result -> parseEdit(result.getJSONObject("edit")) }
    }

    suspend fun listContentEdits(bookId: String): Slice10Outcome<List<CoreContentEditSnapshot>> {
        if (bookId.isBlank()) return invalid("bookId must be non-blank")
        return call("content-edit.list", JSONObject().put("bookId", bookId)) { result ->
            val rows = result.getJSONArray("edits")
            (0 until rows.length()).map { parseEdit(rows.getJSONObject(it)) }
        }
    }

    suspend fun listReplaceRules(enabledOnly: Boolean? = null): Slice10Outcome<List<CorePersistedReplaceRule>> =
        call(
            "replace-rule.list",
            JSONObject().apply { enabledOnly?.let { put("enabledOnly", it) } }
        ) { result ->
            val rows = result.getJSONArray("rules")
            (0 until rows.length()).map { index ->
                parsePersistedReplaceRule(rows.getJSONObject(index))
            }
        }

    /**
     * Restores only an exact token previously written by [CoreReplacePersistResult].
     * Core remains the authenticity authority and revalidates the canonical revision on undo.
     */
    fun decodePersistedUndoToken(tokenJson: String): Slice10Outcome<CoreReplaceUndoToken> {
        if (tokenJson.isBlank()) return invalid("undoToken must be a non-empty object")
        return try {
            Slice10Outcome.Success(parseReplaceUndoToken(JSONObject(tokenJson)))
        } catch (error: Exception) {
            invalid("persisted undoToken is invalid")
        }
    }

    suspend fun cacheBookStatus(
        sourceId: String,
        bookId: String
    ): Slice10Outcome<CoreBookCacheStatus> {
        if (sourceId.isBlank() || bookId.isBlank()) {
            return invalid("sourceId and bookId must be non-blank")
        }
        return call(
            "cache.book.status",
            JSONObject().put("sourceId", sourceId).put("bookId", bookId)
        ) { result ->
            check(result.getString("sourceId") == sourceId)
            check(result.getString("bookId") == bookId)
            val rows = result.getJSONArray("chapters")
            val chapters = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreCacheChapterStatus(
                    chapterIndex = row.getInt("chapterIndex"),
                    title = row.optString("title", ""),
                    url = row.optString("url", ""),
                    state = row.getString("state"),
                    cachedBytes = row.getLong("cachedBytes"),
                    attempts = row.getInt("attempts"),
                    maxAttempts = row.getInt("maxAttempts"),
                    lastError = row.optionalString("lastError")
                )
            }
            val global = result.getJSONObject("globalStats")
            CoreBookCacheStatus(
                sourceId = sourceId,
                bookId = bookId,
                tocAvailable = result.getBoolean("tocAvailable"),
                chapterCount = result.getInt("chapterCount"),
                chapters = chapters,
                cachedCount = result.getLong("cachedCount"),
                queuedCount = result.getLong("queuedCount"),
                inProgressCount = result.getLong("inProgressCount"),
                completedCount = result.getLong("completedCount"),
                failedCount = result.getLong("failedCount"),
                cancelledCount = result.getLong("cancelledCount"),
                missingCount = result.getLong("missingCount"),
                globalStats = CoreCacheGlobalStats(
                    entryCount = global.getLong("entryCount"),
                    totalContentBytes = global.getLong("totalContentBytes"),
                    oldestCachedAt = global.optionalLong("oldestCachedAt"),
                    newestCachedAt = global.optionalLong("newestCachedAt"),
                    queueEntryCount = global.getLong("queueEntryCount"),
                    queuedCount = global.getLong("queuedCount"),
                    inProgressCount = global.getLong("inProgressCount"),
                    completedCount = global.getLong("completedCount"),
                    failedCount = global.getLong("failedCount"),
                    cancelledCount = global.getLong("cancelledCount")
                )
            )
        }
    }

    suspend fun prefetchBookCache(
        sourceId: String,
        bookId: String,
        startInclusive: Int,
        endExclusive: Int,
        priority: Int = 0,
        requestedAt: Long = 0L
    ): Slice10Outcome<CoreBookPrefetchResult> {
        if (
            sourceId.isBlank() ||
            bookId.isBlank() ||
            startInclusive < 0 ||
            endExclusive <= startInclusive ||
            requestedAt < 0L
        ) {
            return invalid("cache prefetch selector, range, or requestedAt is invalid")
        }
        val range = JSONArray().put(startInclusive).put(endExclusive)
        return call(
            "cache.book.prefetch",
            JSONObject()
                .put("sourceId", sourceId)
                .put("bookId", bookId)
                .put("chapterRange", range)
                .put("priority", priority)
                .put("requestedAt", requestedAt)
        ) { result ->
            check(result.getString("sourceId") == sourceId)
            check(result.getString("bookId") == bookId)
            val echoedRange = result.getJSONArray("chapterRange").intPair()
            check(echoedRange.first == startInclusive && echoedRange.second == endExclusive)
            CoreBookPrefetchResult(
                sourceId = sourceId,
                bookId = bookId,
                startInclusive = echoedRange.first,
                endExclusive = echoedRange.second,
                chapterCount = result.getInt("chapterCount"),
                prefetchedCount = result.getLong("prefetchedCount"),
                queuedIndexes = result.getJSONArray("queuedIndexes").intList(),
                alreadyQueuedIndexes = result.getJSONArray("alreadyQueuedIndexes").intList(),
                skippedCachedIndexes = result.getJSONArray("skippedCachedIndexes").intList()
            )
        }
    }

    suspend fun clearCache(
        scope: CoreCacheClearScope,
        sourceId: String? = null,
        bookId: String? = null
    ): Slice10Outcome<CoreCacheClearResult> {
        val params = when (scope) {
            CoreCacheClearScope.ALL,
            CoreCacheClearScope.CACHE -> {
                if (sourceId != null || bookId != null) {
                    return invalid("${scope.wireValue} scope does not accept a book selector")
                }
                JSONObject().put("scope", scope.wireValue)
            }
            CoreCacheClearScope.BOOK -> {
                if (sourceId.isNullOrBlank() || bookId.isNullOrBlank()) {
                    return invalid("book scope requires sourceId and bookId")
                }
                JSONObject()
                    .put("scope", scope.wireValue)
                    .put("sourceId", sourceId)
                    .put("bookId", bookId)
            }
        }
        return call("cache.clear", params) { result ->
            check(result.getString("scope") == scope.wireValue)
            CoreCacheClearResult(
                scope = scope,
                cacheEntriesRemoved = result.getLong("cacheEntriesRemoved"),
                chapterEntriesRemoved = result.getLong("chapterEntriesRemoved"),
                queueEntriesRemoved = result.getLong("queueEntriesRemoved"),
                removedContentBytes = result.getLong("removedContentBytes")
            )
        }
    }

    suspend fun persistReplace(
        operation: CoreReplacePersistOperation,
        params: JSONObject,
        transactionId: String,
        undoTtlSeconds: Int = 300
    ): Slice10Outcome<CoreReplacePersistResult> {
        if (params.length() == 0 || transactionId.isBlank() || undoTtlSeconds !in 1..86_400) {
            return invalid("replace persist params, transactionId, or undo TTL is invalid")
        }
        if (
            operation != CoreReplacePersistOperation.CREATE &&
            (!params.has("id") || runCatching { params.getLong("id") }.getOrNull() == null)
        ) {
            return invalid("replace update/delete requires a numeric Core rule id")
        }
        val sentParams = JSONObject(params.toString())
        return call(
            "replace.persist",
            JSONObject()
                .put("operation", operation.wireValue)
                .put("params", sentParams)
                .put("transactionId", transactionId)
                .put("undoTtlSeconds", undoTtlSeconds)
        ) { result ->
            check(result.getString("operation") == operation.wireValue)
            val data = result.getJSONObject("data")
            val rule = when (operation) {
                CoreReplacePersistOperation.CREATE,
                CoreReplacePersistOperation.UPDATE -> parsePersistedReplaceRule(data.getJSONObject("rule"))
                CoreReplacePersistOperation.DELETE -> null
            }
            val deletedRuleId = if (operation == CoreReplacePersistOperation.DELETE) {
                data.getLong("id")
            } else {
                null
            }
            val deleted = if (operation == CoreReplacePersistOperation.DELETE) {
                data.getBoolean("deleted")
            } else {
                null
            }
            val token = parseReplaceUndoToken(result.getJSONObject("undoToken"))
            check(token.operation == operation)
            check(token.transactionId == transactionId)
            val affectedRuleId = rule?.id ?: deletedRuleId
            check(token.ruleId == affectedRuleId)
            CoreReplacePersistResult(
                operation = operation,
                rule = rule,
                deletedRuleId = deletedRuleId,
                deleted = deleted,
                undoToken = token,
                undoExpiresAt = token.expiresAt
            )
        }
    }

    suspend fun undoReplace(undoToken: JSONObject): Slice10Outcome<CoreReplaceUndoResult> {
        if (undoToken.length() == 0) return invalid("undoToken must be a non-empty object")
        val completeToken = try {
            parseReplaceUndoToken(undoToken)
        } catch (error: Exception) {
            return invalid("undoToken is incomplete or invalid")
        }
        return call(
            "replace.undo",
            JSONObject().put("undoToken", JSONObject(completeToken.json.toString()))
        ) { result ->
            result.requireOnlyKeys(REPLACE_UNDO_RESULT_KEYS)
            val transactionId = result.getString("transactionId")
            val revision = result.getString("revision")
            val operation = result.getString("operation")
            val ruleId = result.getLong("ruleId")
            check(transactionId == completeToken.transactionId)
            check(revision == completeToken.revision)
            check(operation == completeToken.operation.wireValue)
            check(ruleId == completeToken.ruleId)
            val changed = result.getBoolean("changed")
            val restoredRule = if (!result.has("restoredRule")) {
                null
            } else {
                parsePersistedReplaceRule(result.getJSONObject("restoredRule"))
            }
            when (completeToken.operation) {
                CoreReplacePersistOperation.CREATE -> check(restoredRule == null)
                CoreReplacePersistOperation.UPDATE,
                CoreReplacePersistOperation.DELETE -> {
                    check(restoredRule == null || restoredRule == completeToken.before)
                    if (changed) check(restoredRule != null)
                }
            }
            CoreReplaceUndoResult(
                transactionId = transactionId,
                revision = revision,
                operation = operation,
                ruleId = ruleId,
                changed = changed,
                undoneAt = result.getLong("undoneAt"),
                restoredRule = restoredRule
            )
        }
    }

    suspend fun listTxtTocRules(enabledOnly: Boolean? = null): Slice10Outcome<List<CoreRuleSummary>> =
        call(
            "txt-toc-rule.list",
            JSONObject().apply { enabledOnly?.let { put("enabledOnly", it) } }
        ) { result ->
            val rows = result.getJSONArray("rules")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreRuleSummary(
                    id = row.getLong("id"),
                    name = row.getString("name"),
                    enabled = row.getBoolean("enable"),
                    order = row.optInt("serialNumber", -1)
                )
            }
        }

    suspend fun listHttpTts(): Slice10Outcome<List<CoreHttpTtsSummary>> =
        call("http-tts.list", JSONObject()) { result ->
            val rows = result.getJSONArray("items")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                // UI list deliberately receives neither url/header/js/login data.
                CoreHttpTtsSummary(
                    id = row.getLong("id"),
                    name = row.getString("name"),
                    contentType = row.optString("contentType", "").takeIf { it.isNotBlank() },
                    cookieJarEnabled = row.optBoolean("enabledCookieJar", false)
                )
            }
        }

    suspend fun buildHttpTtsRequest(
        id: Long,
        text: String
    ): Slice10Outcome<SecureHttpTtsDescriptor> {
        if (id <= 0L || text.isBlank()) return invalid("HttpTTS id and text are required")
        return call(
            "http-tts.build-request",
            JSONObject().put("id", id).put("text", text)
        ) { result ->
            val method = result.getString("method").uppercase()
            if (method !in setOf("GET", "POST")) error("unsupported HttpTTS method")
            val url = result.getString("url")
            val uri = URI(url)
            if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
                error("unsafe HttpTTS URL")
            }
            val headers = result.optJSONObject("headers") ?: JSONObject()
            SecureHttpTtsDescriptor(
                method = method,
                url = url,
                headers = headers,
                body = result.optString("body", "").takeIf { it.isNotEmpty() },
                contentType = result.optString("contentType", "").takeIf { it.isNotBlank() },
                concurrentRate = result.optString("concurrentRate", "").takeIf { it.isNotBlank() }
            )
        }
    }

    fun blocked(capability: String): Slice10Outcome.Failed {
        val entry = AndroidSlice10CapabilityMatrix.entries.singleOrNull { it.capability == capability }
            ?: return Slice10Outcome.Failed(
                Slice10Failure(Slice10FailureCode.CAPABILITY_BLOCKED, "unknown Slice 10 capability")
            )
        return Slice10Outcome.Failed(
            Slice10Failure(Slice10FailureCode.CAPABILITY_BLOCKED, entry.detail)
        )
    }

    private suspend fun <T> call(
        method: String,
        params: JSONObject,
        parse: (JSONObject) -> T
    ): Slice10Outcome<T> {
        val result = try {
            core.send(method, params)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalStateException) {
            return Slice10Outcome.Failed(
                Slice10Failure(Slice10FailureCode.CORE_UNAVAILABLE, "Reader Core 尚未就绪", true)
            )
        } catch (error: Exception) {
            return Slice10Outcome.Failed(
                Slice10Failure(Slice10FailureCode.CORE_REJECTED, "Reader Core 拒绝了该操作")
            )
        }
        return try {
            Slice10Outcome.Success(parse(result))
        } catch (error: Exception) {
            Slice10Outcome.Failed(
                Slice10Failure(Slice10FailureCode.CORE_PROTOCOL_MISMATCH, "Reader Core 返回结构不完整")
            )
        }
    }

    private fun parseEdit(row: JSONObject): CoreContentEditSnapshot = CoreContentEditSnapshot(
        editId = row.getLong("editId"),
        bookId = row.getString("bookId"),
        chapterIndex = row.getInt("chapterIndex"),
        editedContent = row.getString("editedContent"),
        editedAt = row.getLong("editedAt")
    )

    private fun parsePersistedReplaceRule(row: JSONObject): CorePersistedReplaceRule =
        CorePersistedReplaceRule(
            id = row.getLong("id"),
            name = row.getString("name"),
            group = row.optionalString("group"),
            pattern = row.getString("pattern"),
            replacement = row.getString("replacement"),
            rawScope = row.optionalString("scope"),
            scopeTitle = row.getBoolean("scopeTitle"),
            scopeContent = row.getBoolean("scopeContent"),
            excludeScope = row.optionalString("excludeScope"),
            enabled = row.getBoolean("isEnabled"),
            isRegex = row.getBoolean("isRegex"),
            timeoutMillisecond = row.getLong("timeoutMillisecond"),
            order = row.getInt("order")
        ).also {
            row.requireOnlyKeys(REPLACE_RULE_RESULT_KEYS)
        }

    private fun parseReplaceUndoToken(value: JSONObject): CoreReplaceUndoToken {
        val token = JSONObject(value.toString())
        token.requireOnlyKeys(REPLACE_UNDO_TOKEN_KEYS)
        check(token.getInt("schemaVersion") == 1)
        val transactionId = token.getString("transactionId")
        val revision = token.getString("revision")
        val operation = CoreReplacePersistOperation.entries.single {
            it.wireValue == token.getString("operation")
        }
        val ruleId = token.getLong("ruleId")
        val issuedAt = token.getLong("issuedAt")
        val expiresAt = token.getLong("expiresAt")
        check(transactionId.isNotBlank() && transactionId.length <= 128)
        check(transactionId.none(Char::isISOControl))
        check(REVISION_REGEX.matches(revision))
        check(expiresAt > issuedAt)

        val before = token.optionalStrictRule("before")
        val after = token.optionalStrictRule("after")
        check(before == null || before.id == ruleId)
        check(after == null || after.id == ruleId)
        when (operation) {
            CoreReplacePersistOperation.CREATE -> check(before == null && after != null)
            CoreReplacePersistOperation.UPDATE -> check(before != null && after != null && before != after)
            CoreReplacePersistOperation.DELETE -> check(before != null && after == null)
        }
        return CoreReplaceUndoToken(
            json = token,
            transactionId = transactionId,
            revision = revision,
            operation = operation,
            ruleId = ruleId,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            before = before,
            after = after
        )
    }

    private fun <T> invalid(message: String): Slice10Outcome<T> = Slice10Outcome.Failed(
        Slice10Failure(Slice10FailureCode.INVALID_INPUT, message)
    )

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optionalLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun JSONObject.optionalObject(name: String): JSONObject? =
        if (!has(name) || isNull(name)) null else JSONObject(getJSONObject(name).toString())

    private fun JSONObject.optionalStrictRule(name: String): CorePersistedReplaceRule? =
        if (!has(name)) null else parsePersistedReplaceRule(getJSONObject(name))

    private fun JSONObject.requireOnlyKeys(allowed: Set<String>) {
        check(keys().asSequence().all { it in allowed })
    }

    private fun JSONArray.intPair(): Pair<Int, Int> {
        check(length() == 2)
        return getInt(0) to getInt(1)
    }

    private fun JSONArray.intList(): List<Int> =
        (0 until length()).map(::getInt)

    private companion object {
        val REVISION_REGEX = Regex("^[0-9a-f]{64}$")
        val REPLACE_UNDO_TOKEN_KEYS = setOf(
            "schemaVersion",
            "transactionId",
            "revision",
            "operation",
            "ruleId",
            "issuedAt",
            "expiresAt",
            "before",
            "after"
        )
        val REPLACE_RULE_RESULT_KEYS = setOf(
            "id",
            "name",
            "group",
            "pattern",
            "replacement",
            "scope",
            "scopeTitle",
            "scopeContent",
            "excludeScope",
            "isEnabled",
            "isRegex",
            "timeoutMillisecond",
            "order"
        )
        val REPLACE_UNDO_RESULT_KEYS = setOf(
            "transactionId",
            "revision",
            "operation",
            "ruleId",
            "changed",
            "undoneAt",
            "restoredRule"
        )
    }
}
