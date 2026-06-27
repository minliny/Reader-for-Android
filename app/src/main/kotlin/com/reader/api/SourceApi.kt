package com.reader.api

import org.json.JSONObject

/**
 * Facade for `source.import`. Wraps the Core command + result correlation
 * in [ReaderCoreClient.sendAndAwait]; on a Core error returns a structured
 * [ImportResult] so callers can surface import failures without exceptions.
 */
class SourceApi(private val client: ReaderCoreClient) {

    suspend fun importBookSource(bookSourceJson: String): ImportResult {
        val bookSource = JSONObject(bookSourceJson)
        val params = JSONObject().apply {
            put("sourceId", bookSource.optString("bookSourceUrl"))
            put("name", bookSource.optString("bookSourceName"))
            put("baseUrl", bookSource.optString("bookSourceUrl"))
            put("bookSource", bookSource)
        }
        return try {
            val result = client.sendAndAwait(
                "source.import", params,
                timeoutMillis = IMPORT_TIMEOUT_MILLIS
            )
            ImportResult(success = true, data = result.toString())
        } catch (e: CoreException) {
            ImportResult(success = false, data = e.errorJson)
        } catch (e: CoreTimeoutException) {
            ImportResult(success = false, data = "{\"code\":\"TIMEOUT\",\"message\":\"${e.message}\"}")
        }
    }

    companion object {
        const val IMPORT_TIMEOUT_MILLIS = 30_000L
    }
}

data class ImportResult(val success: Boolean, val data: String)
