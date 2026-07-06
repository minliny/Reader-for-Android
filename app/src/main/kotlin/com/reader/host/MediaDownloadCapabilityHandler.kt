package com.reader.host

import org.json.JSONArray
import org.json.JSONObject

/**
 * [CapabilityHandler] adapter for the `media.download` lane. Wraps
 * [MediaDownloadHandler] (a utility class that does NOT implement
 * [CapabilityHandler]) so it can be registered in [HostAdapter] and dispatched
 * via the standard
 * `host.request -> HostAdapter.dispatch -> handler -> host.complete` round-trip.
 *
 * Core emits a `host.request` with `capability = "media.download"` and params
 * matching `HostMediaDownloadRequest` (see `crates/reader-contract/src/host.rs`).
 * This adapter parses the params JSON into a `Map<String, Any?>`, delegates to
 * [MediaDownloadHandler.handle], and serializes the returned
 * `Map<String, Any?>` back to JSON for the [HostReply.complete] result.
 *
 * **Lazy executor construction**: [OkHttpMediaDownloadExecutor] throws
 * [NotImplementedError] in its `init` block (fail-closed stub). To avoid
 * crashing [ReaderCoreClient.init] at registration time, the adapter accepts a
 * [handlerProvider] lambda that is only invoked on the first `host.request`.
 * The default provider constructs [OkHttpMediaDownloadExecutor], so the
 * `NotImplementedError` is caught in [handle] and mapped to a structured
 * `NOT_IMPLEMENTED` error — Core fails closed without crashing the host.
 *
 * **Proof tier**: handler/router — mirrors [AntiBotCapabilityHandler].
 */
class MediaDownloadCapabilityHandler(
    private val handlerProvider: () -> MediaDownloadHandler =
        { MediaDownloadHandler(OkHttpMediaDownloadExecutor()) }
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        // Lazily construct the handler + executor; OkHttpMediaDownloadExecutor
        // throws NotImplementedError in its init block, so catch it here and
        // map to a structured NOT_IMPLEMENTED error.
        val handler: MediaDownloadHandler
        try {
            handler = handlerProvider()
        } catch (e: NotImplementedError) {
            return HostReply.error(
                NOT_IMPLEMENTED,
                "$CAPABILITY executor not implemented: ${e.message}",
                false
            )
        }

        // Parse params JSON into a Map<String, Any?> for MediaDownloadHandler.
        val params: Map<String, Any?>
        try {
            params = jsonToMap(JSONObject(request.paramsJson()))
        } catch (e: Exception) {
            return HostReply.error(
                INTERNAL,
                "invalid $CAPABILITY params: ${e.message}",
                false
            )
        }

        // Delegate to the wrapped handler; map validation + executor failures
        // to structured host.error replies.
        return try {
            val result = handler.handle(params)
            HostReply.complete(mapToJson(result).toString())
        } catch (e: IllegalArgumentException) {
            HostReply.error(
                INTERNAL,
                "$CAPABILITY invalid params: ${e.message}",
                false
            )
        } catch (e: NotImplementedError) {
            HostReply.error(
                NOT_IMPLEMENTED,
                "$CAPABILITY executor not implemented: ${e.message}",
                false
            )
        } catch (e: Exception) {
            HostReply.error(
                INTERNAL,
                "$CAPABILITY failed: ${e.message}",
                true
            )
        }
    }

    companion object {
        /** Capability string matching Core's `HostCapability::MediaDownload`. */
        const val CAPABILITY = MediaDownloadHandler.CAPABILITY

        private const val INTERNAL = "INTERNAL"
        private const val NOT_IMPLEMENTED = "NOT_IMPLEMENTED"

        /**
         * Convert a [JSONObject] to a `Map<String, Any?>` for
         * [MediaDownloadHandler.handle]. Nested objects and arrays are
         * converted recursively; `JSONObject.NULL` becomes `null`.
         */
        private fun jsonToMap(json: JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            for (key in json.keys()) {
                map[key] = jsonToValue(json.get(key))
            }
            return map
        }

        private fun jsonToValue(value: Any?): Any? = when (value) {
            is JSONObject -> jsonToMap(value)
            is JSONArray -> (0 until value.length()).map { jsonToValue(value.get(it)) }
            JSONObject.NULL -> null
            else -> value
        }

        /**
         * Serialize a `Map<String, Any?>` result from [MediaDownloadHandler.handle]
         * back to a [JSONObject] for [HostReply.complete]. Nested maps and lists
         * are serialized recursively; `null` becomes `JSONObject.NULL`.
         */
        private fun mapToJson(map: Map<String, Any?>): JSONObject {
            val json = JSONObject()
            for ((key, value) in map) {
                when (value) {
                    null -> json.put(key, JSONObject.NULL)
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        json.put(key, mapToJson(value as Map<String, Any?>))
                    }
                    is List<*> -> {
                        val arr = JSONArray()
                        value.forEach { arr.put(it) }
                        json.put(key, arr)
                    }
                    else -> json.put(key, value)
                }
            }
            return json
        }
    }
}
