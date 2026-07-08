package com.reader.host

import org.json.JSONObject

private const val SOURCE_VARIABLE_NAMESPACE = "source.variables"
private const val DEFAULT_SOURCE_KEY = "_default"
private const val DEFAULT_VARIABLE_KEY = "_default"
private const val INTERNAL = "INTERNAL"

class SourceVariableStore(
    private val persistence: HostPersistence
) {
    fun get(sourceKey: String, variableKey: String): String? =
        persistence.get(SOURCE_VARIABLE_NAMESPACE, storageKey(sourceKey, variableKey))?.value

    fun put(sourceKey: String, variableKey: String, value: String): HostPersistenceEntry =
        persistence.put(
            SOURCE_VARIABLE_NAMESPACE,
            storageKey(sourceKey, variableKey),
            value,
            valueBase64 = null,
            expectedRevision = null
        )

    private fun storageKey(sourceKey: String, variableKey: String): String =
        "${sourceKey.ifBlank { DEFAULT_SOURCE_KEY }}::$variableKey"
}

/**
 * Compatibility fallback for source.getVariable when a bridge layer routes it
 * to Android Host. Current Rust Core keeps source variables local, but this
 * handler prevents an Android-side runtime rebuild from turning the callback
 * into an unsupported HostRequest.
 */
class SourceGetVariableHandler(
    private val store: SourceVariableStore
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params = try {
            JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val sourceKey = params.sourceKey()
        val variableKey = params.variableKey()
        val value = store.get(sourceKey, variableKey)
        val result = JSONObject()
        result.put("found", value != null)
        if (value == null) {
            result.put("value", JSONObject.NULL)
        } else {
            result.put("value", value)
        }
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "source.getVariable"
    }
}

/**
 * Compatibility fallback for source.setVariable. Values are stored in
 * HostPersistence, so a new HostRuntime/handler instance can recover them.
 */
class SourceSetVariableHandler(
    private val store: SourceVariableStore
) : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        val params = try {
            JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val sourceKey = params.sourceKey()
        val variableKey = params.variableKey()
        val value = when {
            params.has("value") && !params.isNull("value") -> params.optString("value", "")
            params.has("variable") && !params.isNull("variable") -> params.optString("variable", "")
            else -> ""
        }
        val entry = store.put(sourceKey, variableKey, value)
        val result = JSONObject()
        result.put("stored", true)
        result.put("revision", entry.revision)
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "source.setVariable"
    }
}

/**
 * Android currently has no source-login header store equivalent to Legado's
 * BaseSource login header map. Return an empty map instead of an unsupported
 * capability so login fallback scripts degrade without failing the Host bus.
 */
class SourceLoginHeaderMapHandler : CapabilityHandler {

    override fun handle(request: HostRequest): HostReply {
        try {
            JSONObject(request.paramsJson())
        } catch (e: Exception) {
            return HostReply.error(INTERNAL, "invalid $CAPABILITY params: ${e.message}", false)
        }
        val result = JSONObject()
        result.put("found", false)
        result.put("headers", JSONObject())
        return HostReply.complete(result.toString())
    }

    companion object {
        const val CAPABILITY = "source.getLoginHeaderMap"
    }
}

private fun JSONObject.sourceKey(): String =
    optString("sourceId", "").takeIf { it.isNotBlank() }
        ?: optString("sourceUrl", "").takeIf { it.isNotBlank() }
        ?: optString("bookSourceUrl", "").takeIf { it.isNotBlank() }
        ?: DEFAULT_SOURCE_KEY

private fun JSONObject.variableKey(): String =
    optString("key", "").takeIf { it.isNotBlank() }
        ?: optString("name", "").takeIf { it.isNotBlank() }
        ?: DEFAULT_VARIABLE_KEY
