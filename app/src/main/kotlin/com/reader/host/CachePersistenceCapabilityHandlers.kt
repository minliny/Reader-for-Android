package com.reader.host

import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

private fun hostIso8601(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

private fun compositeKey(namespace: String, key: String): String = "$namespace:$key"

/**
 * Host-owned volatile cache. Default impl is an in-memory [ConcurrentHashMap];
 * production may swap for a disk-backed LRU. Core emits `cache.get` /
 * `cache.put` and the Host decides eviction policy.
 */
data class HostCacheEntry(
    val value: String?,
    val valueBase64: String?,
    val expiresAtMillis: Long?
)

interface HostCache {
    fun get(namespace: String, key: String): HostCacheEntry?
    fun put(namespace: String, key: String, value: String?, valueBase64: String?, ttlMillis: Long? = null): HostCacheEntry
    fun remove(namespace: String, key: String): Boolean
    fun size(): Int
}

/**
 * In-memory [HostCache] backed by [ConcurrentHashMap]. Honors optional TTL:
 * entries past their expiry are treated as absent on [get] and evicted
 * lazily. Thread-safe.
 */
class DefaultHostCache : HostCache {
    private val store = ConcurrentHashMap<String, HostCacheEntry>()

    override fun get(namespace: String, key: String): HostCacheEntry? {
        val composite = compositeKey(namespace, key)
        val entry = store[composite] ?: return null
        entry.expiresAtMillis?.let { exp ->
            if (System.currentTimeMillis() > exp) {
                store.remove(composite)
                return null
            }
        }
        return entry
    }

    override fun put(namespace: String, key: String, value: String?, valueBase64: String?, ttlMillis: Long?): HostCacheEntry {
        val expiresAt = ttlMillis?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() + it
        }
        val entry = HostCacheEntry(value, valueBase64, expiresAt)
        store[compositeKey(namespace, key)] = entry
        return entry
    }

    override fun remove(namespace: String, key: String): Boolean = store.remove(compositeKey(namespace, key)) != null

    override fun size(): Int = store.size
}

/**
 * `cache.get` capability handler. Core sends `{namespace, key}`; returns
 * `{hit:false}` or `{hit:true, value|valueBase64, expiresAt?}`.
 */
class CacheGetHandler(
    private val cache: HostCache
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val namespace = params.optString("namespace", "")
        val key = params.optString("key", "")
        if (namespace.isEmpty() || key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank namespace and key", false)
        }
        val entry = cache.get(namespace, key)
        val result = JSONObject()
        if (entry == null) {
            result.put("hit", false)
        } else {
            result.put("hit", true)
            entry.value?.let { result.put("value", it) }
            entry.valueBase64?.let { result.put("valueBase64", it) }
            entry.expiresAtMillis?.let { result.put("expiresAt", hostIso8601(it)) }
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cache.get"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * `cache.put` capability handler. Core sends
 * `{namespace, key, value|valueBase64, ttlMillis?}`; returns
 * `{stored:true, expiresAt?}`.
 */
class CachePutHandler(
    private val cache: HostCache
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val namespace = params.optString("namespace", "")
        val key = params.optString("key", "")
        if (namespace.isEmpty() || key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank namespace and key", false)
        }
        val value = if (params.has("value") && !params.isNull("value")) params.getString("value") else null
        val valueBase64 = if (params.has("valueBase64") && !params.isNull("valueBase64")) params.getString("valueBase64") else null
        if ((value == null) == (valueBase64 == null)) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires exactly one of value or valueBase64", false)
        }
        val ttl: Long? = if (params.has("ttlMillis") && !params.isNull("ttlMillis")) {
            params.optLong("ttlMillis", 0).takeIf { it > 0 }
        } else null
        val entry = cache.put(namespace, key, value, valueBase64, ttl)
        val result = JSONObject()
        result.put("stored", true)
        entry.expiresAtMillis?.let { result.put("expiresAt", hostIso8601(it)) }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "cache.put"
        private const val INTERNAL = "INTERNAL"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// persistence.get / persistence.put
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Host-owned persistent key-value store. Mirrors [HostCache] but with
 * durability guarantees: entries survive process restarts. Production impl
 * is DataStore/SharedPreferences; tests use [DefaultHostCache] as a volatile
 * double (persistence semantics are verified at the instrumented tier).
 */
data class HostPersistenceEntry(
    val value: String?,
    val valueBase64: String?,
    val revision: String
)

class HostPersistenceRevisionMismatch(
    val expected: String,
    val actual: String
) : IllegalStateException("revision mismatch: expected $expected, got $actual")

interface HostPersistence {
    fun get(namespace: String, key: String): HostPersistenceEntry?
    fun put(namespace: String, key: String, value: String?, valueBase64: String?, expectedRevision: String?): HostPersistenceEntry
    fun remove(namespace: String, key: String): Boolean
}

/**
 * `persistence.get` capability handler. Core sends `{namespace, key}`;
 * returns `{found:false}` or `{found:true, value|valueBase64, revision?}`.
 */
class PersistenceGetHandler(
    private val store: HostPersistence
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val namespace = params.optString("namespace", "")
        val key = params.optString("key", "")
        if (namespace.isEmpty() || key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank namespace and key", false)
        }
        val entry = store.get(namespace, key)
        val result = JSONObject()
        if (entry == null) {
            result.put("found", false)
        } else {
            result.put("found", true)
            entry.value?.let { result.put("value", it) }
            entry.valueBase64?.let { result.put("valueBase64", it) }
            result.put("revision", entry.revision)
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "persistence.get"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * `persistence.put` capability handler. Core sends
 * `{namespace, key, value|valueBase64, expectedRevision?}`; returns
 * `{stored:true, revision?}`.
 */
class PersistencePutHandler(
    private val store: HostPersistence
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val namespace = params.optString("namespace", "")
        val key = params.optString("key", "")
        if (namespace.isEmpty() || key.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank namespace and key", false)
        }
        val value = if (params.has("value") && !params.isNull("value")) params.getString("value") else null
        val valueBase64 = if (params.has("valueBase64") && !params.isNull("valueBase64")) params.getString("valueBase64") else null
        if ((value == null) == (valueBase64 == null)) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires exactly one of value or valueBase64", false)
        }
        val expectedRevision = params.optString("expectedRevision", "").takeIf { it.isNotBlank() }
        val entry = try {
            store.put(namespace, key, value, valueBase64, expectedRevision)
        } catch (e: HostPersistenceRevisionMismatch) {
            return HostReply.error("CONFLICT", e.message ?: "revision mismatch", false)
        }
        val result = JSONObject()
        result.put("stored", true)
        result.put("revision", entry.revision)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "persistence.put"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * In-memory [HostPersistence] double. The constructor keeps the old
 * [HostCache]-based call sites working, but persistence state has its own
 * revision table because Core's contract requires optimistic revisions.
 */
class HostCachePersistenceAdapter(@Suppress("UNUSED_PARAMETER") cache: HostCache) : HostPersistence {
    private val store = ConcurrentHashMap<String, HostPersistenceEntry>()

    override fun get(namespace: String, key: String): HostPersistenceEntry? =
        store[compositeKey(namespace, key)]

    override fun put(namespace: String, key: String, value: String?, valueBase64: String?, expectedRevision: String?): HostPersistenceEntry {
        val composite = compositeKey(namespace, key)
        val current = store[composite]
        val currentRevision = current?.revision ?: "0"
        if (expectedRevision != null && expectedRevision != currentRevision) {
            throw HostPersistenceRevisionMismatch(expectedRevision, currentRevision)
        }
        val nextRevision = ((currentRevision.toLongOrNull() ?: 0L) + 1L).toString()
        val entry = HostPersistenceEntry(value, valueBase64, nextRevision)
        store[composite] = entry
        return entry
    }

    override fun remove(namespace: String, key: String): Boolean =
        store.remove(compositeKey(namespace, key)) != null
}

class SharedPreferencesHostPersistence(
    private val prefs: SharedPreferences
) : HostPersistence {
    private fun base(namespace: String, key: String): String = "host.persistence.${compositeKey(namespace, key)}"

    override fun get(namespace: String, key: String): HostPersistenceEntry? {
        val base = base(namespace, key)
        if (!prefs.contains("$base.kind")) return null
        val kind = prefs.getString("$base.kind", "value") ?: "value"
        val revision = prefs.getString("$base.revision", "0") ?: "0"
        val payload = prefs.getString("$base.payload", null) ?: return null
        return if (kind == "valueBase64") {
            HostPersistenceEntry(null, payload, revision)
        } else {
            HostPersistenceEntry(payload, null, revision)
        }
    }

    override fun put(namespace: String, key: String, value: String?, valueBase64: String?, expectedRevision: String?): HostPersistenceEntry {
        val base = base(namespace, key)
        val currentRevision = prefs.getString("$base.revision", "0") ?: "0"
        if (expectedRevision != null && expectedRevision != currentRevision) {
            throw HostPersistenceRevisionMismatch(expectedRevision, currentRevision)
        }
        val nextRevision = ((currentRevision.toLongOrNull() ?: 0L) + 1L).toString()
        val kind = if (valueBase64 != null) "valueBase64" else "value"
        val payload = valueBase64 ?: value ?: ""
        prefs.edit()
            .putString("$base.kind", kind)
            .putString("$base.payload", payload)
            .putString("$base.revision", nextRevision)
            .apply()
        return if (kind == "valueBase64") {
            HostPersistenceEntry(null, payload, nextRevision)
        } else {
            HostPersistenceEntry(payload, null, nextRevision)
        }
    }

    override fun remove(namespace: String, key: String): Boolean {
        val base = base(namespace, key)
        val existed = prefs.contains("$base.kind")
        prefs.edit()
            .remove("$base.kind")
            .remove("$base.payload")
            .remove("$base.revision")
            .apply()
        return existed
    }
}
