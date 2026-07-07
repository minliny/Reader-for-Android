package com.reader.host

import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Host-owned log sink. Default impl writes to `android.util.Log` on-device
 * and to `System.out` on JVM; tests capture into an in-memory buffer.
 */
interface HostLogger {
    fun emit(level: String, message: String, metadata: JSONObject?)
}

/**
 * Default [HostLogger]: routes to [System.out] (JVM) / `android.util.Log`
 * (Android, via the conditional below). Never throws — logging is
 * best-effort.
 */
class DefaultHostLogger : HostLogger {
    override fun emit(level: String, message: String, metadata: JSONObject?) {
        val tag = "ReaderCore"
        val meta = metadata?.toString() ?: ""
        // android.util.Log is only available on the Android runtime; on JVM
        // tests we fall back to stdout so the same handler can be exercised.
        try {
            val clz = Class.forName("android.util.Log")
            val method = clz.getMethod("println", Int::class.javaPrimitiveType, String::class.java, String::class.java)
            val priority = when (level.lowercase()) {
                "verbose" -> 2
                "debug" -> 3
                "info" -> 4
                "warn" -> 5
                "error" -> 6
                else -> 4
            }
            method.invoke(null, priority, tag, "$message $meta")
        } catch (e: ClassNotFoundException) {
            // JVM test runtime — fall back to stdout.
            println("[$level] $tag: $message $meta")
        } catch (e: Exception) {
            println("[$level] $tag: $message $meta")
        }
    }
}

/**
 * Capturing [HostLogger] for tests. Records every emission so tests can
 * assert on level + message.
 */
class CapturingHostLogger : HostLogger {
    data class LogEntry(val level: String, val message: String, val metadata: JSONObject?)

    val entries = mutableListOf<LogEntry>()

    override fun emit(level: String, message: String, metadata: JSONObject?) {
        entries.add(LogEntry(level, message, metadata))
    }
}

/**
 * `log.emit` capability handler. Core sends `{level, message, fields?}`;
 * returns `host.complete` with `{emitted: true}`. Never fails — logging is
 * best-effort, so even if the sink throws we return success (the log line
 * is dropped, not the Core operation).
 */
class LogEmitHandler(
    private val logger: HostLogger = DefaultHostLogger()
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params: JSONObject
        try {
            params = JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val level = params.optString("level", "info")
        val message = params.optString("message", "")
        if (message.isEmpty()) {
            return HostReply.error(INTERNAL, "$CAPABILITY requires non-blank message", false)
        }
        val metadata = params.optJSONObject("fields") ?: params.optJSONObject("metadata")
        try {
            logger.emit(level, message, metadata)
        } catch (e: Exception) {
            // Logging is best-effort — swallow sink failures.
        }
        val result = JSONObject()
        result.put("emitted", true)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "log.emit"
        private const val INTERNAL = "INTERNAL"
    }
}

/**
 * `time.now` capability handler. Core sends `{clock?, timezone?}`; returns
 * `host.complete` with `{unixMillis, iso8601, timezone?}`. Always succeeds — the host clock is the
 * source of truth.
 */
class TimeNowHandler : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params = try {
            JSONObject(request.paramsJson())
        } catch (e: Exception) {
            JSONObject()
        }
        val now = System.currentTimeMillis()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(now))
        val result = JSONObject()
        result.put("unixMillis", now)
        result.put("iso8601", iso)
        val timezone = params.optString("timezone", "")
        if (timezone.isNotBlank()) {
            result.put("timezone", timezone)
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "time.now"
    }
}

/**
 * `system.info` capability handler. Core sends `{keys?}`; returns
 * `host.complete` with `{info: {...}}`. Used by Core
 * to adjust behavior per host (e.g. feature flags for old API levels).
 */
class SystemInfoHandler(
    private val appVersionName: String = "0.1.0",
    private val appVersionCode: Int = 1
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params = try {
            JSONObject(request.paramsJson())
        } catch (e: Exception) {
            JSONObject()
        }
        val info = JSONObject()
        info.put("platform", "android")
        // Build.VERSION fields are stubbed (null/0) on JVM tests; guard
        // each read so the handler returns a best-effort snapshot rather
        // than crashing. On a real device the stubs are replaced with the
        // actual values by the Android runtime.
        info.put("osVersion", safeBuildString { Build.VERSION.SDK_INT.toString() })
        info.put("osRelease", safeBuildString { Build.VERSION.RELEASE })
        info.put("manufacturer", safeBuildString { Build.MANUFACTURER })
        info.put("model", safeBuildString { Build.MODEL })
        info.put("appVersionName", appVersionName)
        info.put("appVersionCode", appVersionCode)
        info.put("abi", safeBuildString { Build.SUPPORTED_ABIS.firstOrNull() ?: "" })
        val keys = params.optJSONArray("keys")
        val filtered = if (keys != null && keys.length() > 0) {
            JSONObject().also { out ->
                for (idx in 0 until keys.length()) {
                    val key = keys.optString(idx, "")
                    if (key.isNotBlank() && info.has(key)) {
                        out.put(key, info.get(key))
                    }
                }
            }
        } else {
            info
        }
        val result = JSONObject()
        result.put("info", filtered)
        return HostReply.complete(result.toString())
    }

    /**
     * Wraps [supplier] so a JVM test runtime (where `android.os.Build` is a
     * stub returning null/0) does not crash the handler. On a real device
     * the supplier runs unchanged.
     */
    private fun safeBuildString(supplier: () -> String): String =
        try { supplier().takeIf { it.isNotEmpty() } ?: "unknown" } catch (e: Exception) { "unknown" }

    companion object {
        const val CAPABILITY = "system.info"
    }
}
