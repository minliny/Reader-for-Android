package com.reader.android.data.adapter

import com.reader.api.ReaderCoreClient
import java.net.URI
import kotlinx.coroutines.CancellationException
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

    suspend fun listReplaceRules(enabledOnly: Boolean? = null): Slice10Outcome<List<CoreRuleSummary>> =
        call(
            "replace-rule.list",
            JSONObject().apply { enabledOnly?.let { put("enabledOnly", it) } }
        ) { result ->
            val rows = result.getJSONArray("rules")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreRuleSummary(
                    id = row.getLong("id"),
                    name = row.getString("name"),
                    enabled = row.getBoolean("isEnabled"),
                    order = row.optInt("order", 0)
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

    private fun <T> invalid(message: String): Slice10Outcome<T> = Slice10Outcome.Failed(
        Slice10Failure(Slice10FailureCode.INVALID_INPUT, message)
    )
}
