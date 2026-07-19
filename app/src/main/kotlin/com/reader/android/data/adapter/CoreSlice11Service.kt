package com.reader.android.data.adapter

import com.reader.api.ReaderCoreClient
import java.net.URI
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class AndroidSlice11CapabilityStatus {
    CORE_PROTOCOL_AVAILABLE,
    CORE_QUERY_ONLY,
    LOCAL_BRIDGE_AVAILABLE,
    BLOCKED_MISSING_DSL_CONTRACT,
    BLOCKED_MISSING_PROFILE_ISOLATION,
    BLOCKED_MISSING_CONTINUATION_PROTOCOL,
    BLOCKED_MISSING_PROTECTED_DOWNLOAD_PROTOCOL,
    BLOCKED_DEVICE_EVIDENCE
}

data class AndroidSlice11CapabilityEntry(
    val capability: String,
    val status: AndroidSlice11CapabilityStatus,
    val detail: String
)

/**
 * Executable Android admission matrix for Slice 11.
 *
 * Existing routes and decorative screens are deliberately not counted as
 * runtime support. A row is admitted only when Android can call a frozen Core
 * method or execute a frozen Host descriptor without creating a second rule,
 * credential, profile, RSS, or source owner.
 */
object AndroidSlice11CapabilityMatrix {
    val entries: List<AndroidSlice11CapabilityEntry> = listOf(
        AndroidSlice11CapabilityEntry("source.crud", AndroidSlice11CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core source.import/list/delete/export is the only source store"),
        AndroidSlice11CapabilityEntry("source.check.run", AndroidSlice11CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core owns the diagnostic pipeline and emits http.execute descriptors"),
        AndroidSlice11CapabilityEntry("source.debug.replay", AndroidSlice11CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Core source.debug exposes stage/rule/error-kind logs for supplied corpus responses"),
        AndroidSlice11CapabilityEntry("rule-sub.crud", AndroidSlice11CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core rule-sub.put/get/list/delete; Android projects credential-safe summaries"),
        AndroidSlice11CapabilityEntry("rss.subscription", AndroidSlice11CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core rss.subscription.* is the only subscription and unread-count store"),
        AndroidSlice11CapabilityEntry("rss.items", AndroidSlice11CapabilityStatus.LOCAL_BRIDGE_AVAILABLE, "Core rss.subscription.items and scoped rss.item.read"),
        AndroidSlice11CapabilityEntry("rss.favorite", AndroidSlice11CapabilityStatus.CORE_QUERY_ONLY, "Core exposes rss.favorite.list but the Reader UI favorite mutation effects do not map to frozen Core methods"),
        AndroidSlice11CapabilityEntry("webview.basic", AndroidSlice11CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Existing UI-bound WebView open/close/evaluate Host path remains evidence-gated"),
        AndroidSlice11CapabilityEntry("anti-bot.detect", AndroidSlice11CapabilityStatus.CORE_PROTOCOL_AVAILABLE, "Android can detect a challenge and fail the HTTP request retryably"),
        AndroidSlice11CapabilityEntry("dsl.version-and-location", AndroidSlice11CapabilityStatus.BLOCKED_MISSING_DSL_CONTRACT, "No frozen DSL version/compatibility set or source-range diagnostic DTO"),
        AndroidSlice11CapabilityEntry("webview.profile", AndroidSlice11CapabilityStatus.BLOCKED_MISSING_PROFILE_ISOLATION, "Android WebView Host rejects profileId because isolated profile sessions are not implemented"),
        AndroidSlice11CapabilityEntry("challenge.continuation", AndroidSlice11CapabilityStatus.BLOCKED_MISSING_CONTINUATION_PROTOCOL, "Challenge detection has no frozen success/cancel/timeout continuation and replay handle"),
        AndroidSlice11CapabilityEntry("rss.protected-download", AndroidSlice11CapabilityStatus.BLOCKED_MISSING_PROTECTED_DOWNLOAD_PROTOCOL, "No frozen credential/profile-bound media descriptor or offline integrity result"),
        AndroidSlice11CapabilityEntry("slice11.device", AndroidSlice11CapabilityStatus.BLOCKED_DEVICE_EVIDENCE, "Physical-device corpus, profile isolation, challenge, and protected-download recordings are absent")
    )

    init {
        check(entries.map { it.capability }.toSet().size == entries.size)
    }

    fun status(capability: String): AndroidSlice11CapabilityStatus? =
        entries.singleOrNull { it.capability == capability }?.status
}

fun interface Slice11CoreCommandClient {
    suspend fun send(method: String, params: JSONObject): JSONObject
}

class ReaderCoreSlice11CommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : Slice11CoreCommandClient {
    override suspend fun send(method: String, params: JSONObject): JSONObject =
        coreProvider().sendAndAwait(method, params)
}

enum class Slice11FailureCode {
    INVALID_INPUT,
    CORE_UNAVAILABLE,
    CORE_REJECTED,
    CORE_PROTOCOL_MISMATCH,
    CAPABILITY_BLOCKED
}

data class Slice11Failure(
    val code: Slice11FailureCode,
    val message: String,
    val retryable: Boolean = false
)

sealed interface Slice11Outcome<out T> {
    data class Success<T>(val value: T) : Slice11Outcome<T>
    data class Failed(val failure: Slice11Failure) : Slice11Outcome<Nothing>
}

data class CoreSourceSummary(
    val sourceId: String,
    val name: String,
    val displayOrigin: String,
    val enabled: Boolean,
    val exploreEnabled: Boolean
)

data class CoreSourceCheckSummary(
    val sourceId: String,
    val available: Boolean,
    val levelsPassed: List<String>,
    val failureReason: String?,
    val durationMillis: Long
)

data class CoreRuleSubSummary(
    val id: Long,
    val name: String,
    val displayUrl: String,
    val type: Int,
    val customOrder: Int,
    val autoUpdate: Boolean,
    val updatedAt: Long
)

data class CoreRssSubscriptionSummary(
    val subscriptionId: String,
    val title: String,
    val displayFeedUrl: String,
    val enabled: Boolean,
    val lastFetchAt: Long?,
    val unreadCount: Int
)

data class CoreRssRefreshSummary(
    val subscription: CoreRssSubscriptionSummary,
    val count: Int,
    val unreadCount: Int,
    val newCount: Int,
    val fetched: Boolean,
    val notModified: Boolean
)

/**
 * A source export may contain headers, cookies, login URLs, or JS. It stays
 * opaque until the explicit file/share edge and is redacted from diagnostics.
 */
class SecureSourceExport internal constructor(
    private val data: String,
    val count: Int,
    val format: String
) {
    fun toUtf8Bytes(): ByteArray = data.toByteArray(Charsets.UTF_8)

    override fun toString(): String =
        "SecureSourceExport(count=$count, format=$format, data=REDACTED)"
}

/**
 * RSS links can contain signed query values. The original link is available
 * only to the explicit browser/download edge and is never rendered by
 * [toString].
 */
class SecureRssArticle internal constructor(
    val subscriptionId: String,
    val title: String,
    val summary: String?,
    val author: String?,
    val publishedAt: String?,
    val guid: String,
    val read: Boolean,
    private val link: String?
) {
    fun externalHttpUrl(): String? = link

    override fun toString(): String =
        "SecureRssArticle(subscriptionId=$subscriptionId, title=$title, guid=$guid, read=$read, link=REDACTED)"
}

/** Core-only Slice 11 bridge. It owns no Android database or rule parser. */
class CoreSlice11Service(
    private val core: Slice11CoreCommandClient
) {
    suspend fun listSources(enabledOnly: Boolean? = null): Slice11Outcome<List<CoreSourceSummary>> =
        call(
            "source.list",
            JSONObject().apply { enabledOnly?.let { put("enabledOnly", it) } }
        ) { result ->
            val rows = result.getJSONArray("sources")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreSourceSummary(
                    sourceId = row.getString("sourceId"),
                    name = row.getString("name"),
                    displayOrigin = safeDisplayUrl(row.optString("baseUrl", "")),
                    enabled = row.getBoolean("enabled"),
                    exploreEnabled = row.optBoolean("enabledExplore", false)
                )
            }
        }

    suspend fun importLegadoSource(bookSourceJson: String): Slice11Outcome<CoreSourceSummary> {
        val source = try {
            JSONObject(bookSourceJson)
        } catch (error: Exception) {
            return invalid("bookSource must be one JSON object")
        }
        val sourceUrl = source.optString("bookSourceUrl", "").trim()
        val sourceName = source.optString("bookSourceName", "").trim()
        if (sourceUrl.isEmpty() || sourceName.isEmpty()) {
            return invalid("bookSourceUrl and bookSourceName are required")
        }
        if (validatedHttpUri(sourceUrl) == null) return invalid("bookSourceUrl must be a credential-free HTTP(S) URL")
        return call(
            "source.import",
            JSONObject()
                .put("sourceId", sourceUrl)
                .put("name", sourceName)
                .put("baseUrl", sourceUrl)
                .put("bookSource", source)
        ) { result ->
            check(result.getBoolean("imported"))
            CoreSourceSummary(
                sourceId = result.getString("sourceId"),
                name = result.getString("name"),
                displayOrigin = safeDisplayUrl(sourceUrl),
                enabled = source.optBoolean("enabled", true),
                exploreEnabled = source.optBoolean("enabledExplore", false)
            )
        }
    }

    suspend fun deleteSources(sourceIds: List<String>): Slice11Outcome<Int> {
        val normalized = sourceIds.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalized.isEmpty() || normalized.size != sourceIds.size) {
            return invalid("sourceIds must be non-blank and unique")
        }
        return call(
            "source.delete",
            JSONObject().put("sourceIds", JSONArray(normalized))
        ) { result -> result.getInt("deleted") }
    }

    suspend fun exportSources(sourceIds: List<String>? = null): Slice11Outcome<SecureSourceExport> {
        if (sourceIds != null && sourceIds.any { it.isBlank() }) return invalid("sourceIds must be non-blank")
        val params = JSONObject().put("format", "json")
        sourceIds?.distinct()?.let { params.put("sourceIds", JSONArray(it)) }
        return call("source.export", params) { result ->
            val data = result.getString("data")
            JSONArray(data)
            SecureSourceExport(
                data = data,
                count = result.getInt("count"),
                format = result.getString("format")
            )
        }
    }

    suspend fun runSourceCheck(
        sourceIds: List<String>,
        keyword: String = "我的",
        levels: List<String> = emptyList(),
        timeoutMillis: Int = 180_000
    ): Slice11Outcome<List<CoreSourceCheckSummary>> {
        val ids = sourceIds.map(String::trim).filter(String::isNotEmpty).distinct()
        val acceptedLevels = setOf("L1", "L2", "L3", "L4", "L5")
        if (ids.isEmpty() || ids.size != sourceIds.size || keyword.isBlank() || timeoutMillis <= 0) {
            return invalid("source check input is incomplete")
        }
        if (levels.any { it !in acceptedLevels } || levels.distinct().size != levels.size) {
            return invalid("source check levels must be unique L1-L5 values")
        }
        return call(
            "source.check.run",
            JSONObject()
                .put("sourceIds", JSONArray(ids))
                .put("keyword", keyword)
                .put("timeoutMs", timeoutMillis)
                .put("levels", JSONArray(levels))
        ) { result ->
            val rows = result.getJSONArray("results")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                CoreSourceCheckSummary(
                    sourceId = row.getString("sourceId"),
                    available = row.getBoolean("available"),
                    levelsPassed = row.getJSONArray("levelsPassed").stringValues(),
                    failureReason = row.optString("failureReason", "").takeIf(String::isNotBlank),
                    durationMillis = row.getLong("durationMs")
                )
            }
        }
    }

    suspend fun listRuleSubscriptions(): Slice11Outcome<List<CoreRuleSubSummary>> =
        call("rule-sub.list", JSONObject()) { result ->
            val rows = result.getJSONArray("subs")
            (0 until rows.length()).map { parseRuleSub(rows.getJSONObject(it)) }
        }

    suspend fun putRuleSubscription(
        id: Long,
        name: String,
        url: String,
        type: Int,
        customOrder: Int,
        autoUpdate: Boolean,
        updatedAt: Long
    ): Slice11Outcome<CoreRuleSubSummary> {
        if (id <= 0L || name.isBlank() || updatedAt < 0L || validatedHttpUri(url) == null) {
            return invalid("rule subscription fields or URL are invalid")
        }
        return call(
            "rule-sub.put",
            JSONObject()
                .put("id", id)
                .put("name", name.trim())
                .put("url", url.trim())
                .put("type", type)
                .put("customOrder", customOrder)
                .put("autoUpdate", autoUpdate)
                .put("update", updatedAt)
        ) { result -> parseRuleSub(result.getJSONObject("sub")) }
    }

    suspend fun deleteRuleSubscription(id: Long): Slice11Outcome<Boolean> {
        if (id <= 0L) return invalid("rule subscription id must be positive")
        return call("rule-sub.delete", JSONObject().put("id", id)) { result ->
            check(result.getLong("id") == id)
            result.getBoolean("deleted")
        }
    }

    suspend fun listRssSubscriptions(): Slice11Outcome<List<CoreRssSubscriptionSummary>> =
        call("rss.subscription.list", JSONObject()) { result ->
            val rows = result.getJSONArray("subscriptions")
            (0 until rows.length()).map { parseRssSubscription(rows.getJSONObject(it)) }
        }

    suspend fun addRssSubscription(
        subscriptionId: String,
        feedUrl: String,
        title: String = "",
        siteUrl: String? = null,
        enabled: Boolean = true
    ): Slice11Outcome<CoreRssSubscriptionSummary> {
        if (subscriptionId.isBlank() || validatedHttpUri(feedUrl) == null) {
            return invalid("subscriptionId and a credential-free HTTP(S) feedUrl are required")
        }
        if (siteUrl != null && validatedHttpUri(siteUrl) == null) return invalid("siteUrl must be HTTP(S)")
        return call(
            "rss.subscription.add",
            JSONObject()
                .put("subscriptionId", subscriptionId.trim())
                .put("feedUrl", feedUrl.trim())
                .put("title", title.trim())
                .put("enabled", enabled)
                .apply { siteUrl?.let { put("siteUrl", it.trim()) } }
        ) { result -> parseRssSubscription(result.getJSONObject("subscription")) }
    }

    suspend fun updateRssSubscription(
        subscriptionId: String,
        feedUrl: String? = null,
        title: String? = null,
        enabled: Boolean? = null
    ): Slice11Outcome<CoreRssSubscriptionSummary> {
        if (subscriptionId.isBlank()) return invalid("subscriptionId is required")
        if (feedUrl != null && validatedHttpUri(feedUrl) == null) return invalid("feedUrl must be HTTP(S)")
        if (feedUrl == null && title == null && enabled == null) return invalid("at least one RSS field must change")
        return call(
            "rss.subscription.update",
            JSONObject().put("subscriptionId", subscriptionId.trim()).apply {
                feedUrl?.let { put("feedUrl", it.trim()) }
                title?.let { put("title", it.trim()) }
                enabled?.let { put("enabled", it) }
            }
        ) { result -> parseRssSubscription(result.getJSONObject("subscription")) }
    }

    suspend fun deleteRssSubscription(subscriptionId: String): Slice11Outcome<Boolean> {
        if (subscriptionId.isBlank()) return invalid("subscriptionId is required")
        return call(
            "rss.subscription.delete",
            JSONObject().put("subscriptionId", subscriptionId.trim())
        ) { result ->
            check(result.getString("subscriptionId") == subscriptionId.trim())
            result.getBoolean("deleted")
        }
    }

    suspend fun refreshRssSubscription(
        subscriptionId: String,
        evaluatedAt: Long = 0L
    ): Slice11Outcome<CoreRssRefreshSummary> {
        if (subscriptionId.isBlank() || evaluatedAt < 0L) return invalid("RSS refresh input is invalid")
        return call(
            "rss.subscription.refresh",
            JSONObject().put("subscriptionId", subscriptionId.trim()).put("evaluatedAt", evaluatedAt)
        ) { result ->
            CoreRssRefreshSummary(
                subscription = parseRssSubscription(result.getJSONObject("subscription")),
                count = result.getInt("count"),
                unreadCount = result.getInt("unreadCount"),
                newCount = result.getInt("newCount"),
                fetched = result.getBoolean("fetched"),
                notModified = result.getBoolean("notModified")
            )
        }
    }

    suspend fun listRssItems(
        subscriptionId: String,
        unreadOnly: Boolean = false,
        limit: Int? = null
    ): Slice11Outcome<List<SecureRssArticle>> {
        if (subscriptionId.isBlank() || (limit != null && limit <= 0)) return invalid("RSS list input is invalid")
        return call(
            "rss.subscription.items",
            JSONObject()
                .put("subscriptionId", subscriptionId.trim())
                .put("unreadOnly", unreadOnly)
                .apply { limit?.let { put("limit", it) } }
        ) { result ->
            val rows = result.getJSONArray("items")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val link = row.optString("link", "").takeIf(String::isNotBlank)
                if (link != null && validatedHttpUri(link) == null) error("unsafe RSS article link")
                SecureRssArticle(
                    subscriptionId = row.getString("subscriptionId"),
                    title = row.getString("title"),
                    summary = row.optString("description", "").takeIf(String::isNotBlank),
                    author = row.optString("author", "").takeIf(String::isNotBlank),
                    publishedAt = row.optString("pubDate", "").takeIf(String::isNotBlank),
                    guid = row.getString("guid"),
                    read = row.getBoolean("read"),
                    link = link
                )
            }
        }
    }

    suspend fun listRssFavorites(
        subscriptionId: String? = null,
        limit: Int? = null
    ): Slice11Outcome<List<SecureRssArticle>> {
        if (subscriptionId != null && subscriptionId.isBlank()) return invalid("subscriptionId must be non-blank")
        if (limit != null && limit <= 0) return invalid("favorite limit must be positive")
        return call(
            "rss.favorite.list",
            JSONObject().apply {
                subscriptionId?.let { put("subscriptionId", it.trim()) }
                limit?.let { put("limit", it) }
            }
        ) { result ->
            val rows = result.getJSONArray("favorites")
            (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val link = row.optString("link", "").takeIf(String::isNotBlank)
                if (link != null && validatedHttpUri(link) == null) error("unsafe RSS favorite link")
                SecureRssArticle(
                    subscriptionId = row.getString("subscriptionId"),
                    title = row.getString("title"),
                    summary = row.optString("summary", "").takeIf(String::isNotBlank),
                    author = null,
                    publishedAt = row.optString("publishedAt", "").takeIf(String::isNotBlank),
                    guid = row.getString("guid"),
                    read = true,
                    link = link
                )
            }
        }
    }

    suspend fun markRssItemRead(
        subscriptionId: String,
        guid: String,
        read: Boolean
    ): Slice11Outcome<Int?> {
        if (subscriptionId.isBlank() || guid.isBlank()) return invalid("subscriptionId and guid are required")
        return call(
            "rss.item.read",
            JSONObject()
                .put("subscriptionId", subscriptionId.trim())
                .put("guid", guid.trim())
                .put("read", read)
        ) { result ->
            check(result.getBoolean("marked"))
            check(result.getString("subscriptionId") == subscriptionId.trim())
            check(result.getString("guid") == guid.trim())
            check(result.getBoolean("read") == read)
            if (result.has("unreadCount") && !result.isNull("unreadCount")) result.getInt("unreadCount") else null
        }
    }

    fun blocked(capability: String): Slice11Outcome.Failed {
        val entry = AndroidSlice11CapabilityMatrix.entries.singleOrNull { it.capability == capability }
            ?: return Slice11Outcome.Failed(
                Slice11Failure(Slice11FailureCode.CAPABILITY_BLOCKED, "unknown Slice 11 capability")
            )
        return Slice11Outcome.Failed(
            Slice11Failure(Slice11FailureCode.CAPABILITY_BLOCKED, entry.detail)
        )
    }

    private suspend fun <T> call(
        method: String,
        params: JSONObject,
        parse: (JSONObject) -> T
    ): Slice11Outcome<T> {
        val result = try {
            core.send(method, params)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalStateException) {
            return Slice11Outcome.Failed(
                Slice11Failure(Slice11FailureCode.CORE_UNAVAILABLE, "Reader Core 尚未就绪", retryable = true)
            )
        } catch (error: Exception) {
            return Slice11Outcome.Failed(
                Slice11Failure(Slice11FailureCode.CORE_REJECTED, "Reader Core 拒绝了该操作")
            )
        }
        return try {
            Slice11Outcome.Success(parse(result))
        } catch (error: Exception) {
            Slice11Outcome.Failed(
                Slice11Failure(Slice11FailureCode.CORE_PROTOCOL_MISMATCH, "Reader Core 返回结构不完整")
            )
        }
    }

    private fun parseRuleSub(row: JSONObject): CoreRuleSubSummary = CoreRuleSubSummary(
        id = row.getLong("id"),
        name = row.getString("name"),
        displayUrl = safeDisplayUrl(row.getString("url")),
        type = row.getInt("type"),
        customOrder = row.getInt("customOrder"),
        autoUpdate = row.getBoolean("autoUpdate"),
        updatedAt = row.getLong("update")
    )

    private fun parseRssSubscription(row: JSONObject): CoreRssSubscriptionSummary =
        CoreRssSubscriptionSummary(
            subscriptionId = row.getString("subscriptionId"),
            title = row.getString("title"),
            displayFeedUrl = safeDisplayUrl(row.getString("feedUrl")),
            enabled = row.getBoolean("enabled"),
            lastFetchAt = if (row.has("lastFetchAt") && !row.isNull("lastFetchAt")) row.getLong("lastFetchAt") else null,
            unreadCount = row.getInt("unreadCount")
        )

    private fun validatedHttpUri(raw: String): URI? = runCatching {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase() ?: return@runCatching null
        if (scheme !in setOf("http", "https")) return@runCatching null
        if (uri.host.isNullOrBlank() || !uri.userInfo.isNullOrBlank()) return@runCatching null
        uri
    }.getOrNull()

    private fun safeDisplayUrl(raw: String): String {
        val uri = validatedHttpUri(raw) ?: return "不可显示的地址"
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.path.orEmpty().takeIf { it.isNotBlank() } ?: "/"
        return "${requireNotNull(uri.scheme).lowercase()}://${uri.host}$port$path"
    }

    private fun JSONArray.stringValues(): List<String> =
        (0 until length()).map { getString(it) }

    private fun <T> invalid(message: String): Slice11Outcome<T> = Slice11Outcome.Failed(
        Slice11Failure(Slice11FailureCode.INVALID_INPUT, message)
    )
}
