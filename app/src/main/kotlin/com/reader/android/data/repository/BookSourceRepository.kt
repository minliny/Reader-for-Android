package com.reader.android.data.repository

import com.reader.android.data.model.BookSource
import org.json.JSONArray
import org.json.JSONObject

interface BookSourceRepository {
    fun getAll(): List<BookSource>
    fun getEnabled(): List<BookSource>
    fun getByUrl(url: String): BookSource?
    fun add(source: BookSource)
    fun remove(url: String)
    fun setEnabled(url: String, enabled: Boolean)
    fun importJson(jsonString: String): Int

    /**
     * Exports all sources as a JSON array string. Mirrors [importJson] —
     * the output of `exportJson()` can be fed back into `importJson()`.
     */
    fun exportJson(): String
}

class FakeBookSourceRepository : BookSourceRepository {

    private val sources = mutableListOf<BookSource>()

    override fun getAll(): List<BookSource> = sources.toList()

    override fun getEnabled(): List<BookSource> = sources.filter { it.enabled }

    override fun getByUrl(url: String): BookSource? = sources.find { it.sourceUrl == url }

    override fun add(source: BookSource) {
        // Dedupe by sourceUrl to mirror DataStoreBookSourceRepository
        sources.removeAll { it.sourceUrl == source.sourceUrl }
        sources.add(source)
    }

    override fun remove(url: String) {
        sources.removeAll { it.sourceUrl == url }
    }

    override fun setEnabled(url: String, enabled: Boolean) {
        val idx = sources.indexOfFirst { it.sourceUrl == url }
        if (idx >= 0) {
            sources[idx] = sources[idx].copy(enabled = enabled)
        }
    }

    override fun importJson(jsonString: String): Int {
        val json = JSONArray(jsonString)
        var count = 0
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val source = parseSource(obj)
            sources.add(source)
            count++
        }
        return count
    }

    override fun exportJson(): String {
        val arr = JSONArray()
        sources.forEach { source -> arr.put(sourceToJson(source)) }
        return arr.toString()
    }

    private fun sourceToJson(source: BookSource): JSONObject = JSONObject().apply {
        put("sourceUrl", source.sourceUrl)
        put("sourceName", source.sourceName)
        source.sourceGroup?.let { put("sourceGroup", it) }
        put("enabled", source.enabled)
        source.sourceComment?.let { put("sourceComment", it) }
        source.searchUrl?.let { put("searchUrl", it) }
        source.searchCharset?.let { put("searchCharset", it) }
        source.searchMethod?.let { put("searchMethod", it) }
        source.bookInfoUrl?.let { put("bookInfoUrl", it) }
        source.tocUrl?.let { put("tocUrl", it) }
        source.tocCharset?.let { put("tocCharset", it) }
        source.contentUrl?.let { put("contentUrl", it) }
        source.contentCharset?.let { put("contentCharset", it) }
        source.header?.let { put("header", it) }
        source.loginUrl?.let { put("loginUrl", it) }
    }

    private fun parseSource(obj: JSONObject): BookSource {
        return BookSource(
            sourceUrl = obj.optString("sourceUrl", ""),
            sourceName = obj.optString("sourceName", "未命名"),
            sourceGroup = obj.nullableString("sourceGroup"),
            enabled = obj.optBoolean("enabled", true),
            sourceComment = obj.nullableString("sourceComment"),
            searchUrl = obj.nullableString("searchUrl"),
            searchCharset = obj.nullableString("searchCharset"),
            searchMethod = obj.nullableString("searchMethod"),
            bookInfoUrl = obj.nullableString("bookInfoUrl"),
            tocUrl = obj.nullableString("tocUrl"),
            tocCharset = obj.nullableString("tocCharset"),
            contentUrl = obj.nullableString("contentUrl"),
            contentCharset = obj.nullableString("contentCharset"),
            header = obj.nullableString("header"),
            loginUrl = obj.nullableString("loginUrl")
        )
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
