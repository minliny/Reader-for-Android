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
}

class FakeBookSourceRepository : BookSourceRepository {

    private val sources = mutableListOf<BookSource>()

    override fun getAll(): List<BookSource> = sources.toList()

    override fun getEnabled(): List<BookSource> = sources.filter { it.enabled }

    override fun getByUrl(url: String): BookSource? = sources.find { it.sourceUrl == url }

    override fun add(source: BookSource) {
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
