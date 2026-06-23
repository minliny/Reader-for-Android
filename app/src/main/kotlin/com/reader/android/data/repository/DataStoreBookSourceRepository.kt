package com.reader.android.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reader.android.data.model.BookSource
import com.reader.android.data.storage.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

class DataStoreBookSourceRepository(context: Context) : BookSourceRepository {

    private val dataStore = context.dataStore

    companion object {
        private val KEY_SOURCES = stringPreferencesKey("book_sources_json")
    }

    val sourcesFlow: Flow<List<BookSource>> = dataStore.data.map { prefs ->
        val json = prefs[KEY_SOURCES] ?: "[]"
        parseSourceList(json)
    }

    private var cached: List<BookSource> = emptyList()

    override fun getAll(): List<BookSource> = cached

    override fun getEnabled(): List<BookSource> = cached.filter { it.enabled }

    override fun getByUrl(url: String): BookSource? = cached.find { it.sourceUrl == url }

    override fun add(source: BookSource) {
        cached = (cached.filterNot { it.sourceUrl == source.sourceUrl } + source)
            .sortedBy { it.sourceName }
        saveBlocking()
    }

    override fun remove(url: String) {
        cached = cached.filter { it.sourceUrl != url }
        saveBlocking()
    }

    override fun setEnabled(url: String, enabled: Boolean) {
        cached = cached.map { if (it.sourceUrl == url) it.copy(enabled = enabled) else it }
        saveBlocking()
    }

    override fun importJson(jsonString: String): Int {
        val newSources = parseSourceList(jsonString)
        val byUrl = linkedMapOf<String, BookSource>()
        cached.forEach { byUrl[it.sourceUrl] = it }
        newSources.forEach { byUrl[it.sourceUrl] = it }
        cached = byUrl.values.sortedBy { it.sourceName }
        saveBlocking()
        return newSources.size
    }

    suspend fun load() {
        val prefs = dataStore.data.first()
        val json = prefs[KEY_SOURCES] ?: "[]"
        cached = parseSourceList(json)
    }

    fun loadBlocking() {
        runBlocking { load() }
    }

    suspend fun save() {
        dataStore.edit { prefs ->
            prefs[KEY_SOURCES] = toJson(cached)
        }
    }

    fun saveBlocking() {
        runBlocking { save() }
    }

    private fun parseSourceList(json: String): List<BookSource> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            BookSource(
                sourceUrl = obj.optString("sourceUrl", ""),
                sourceName = obj.optString("sourceName", "未命名"),
                sourceGroup = nullable(obj, "sourceGroup"),
                enabled = obj.optBoolean("enabled", true),
                sourceComment = nullable(obj, "sourceComment"),
                searchUrl = nullable(obj, "searchUrl"),
                searchCharset = nullable(obj, "searchCharset"),
                searchMethod = nullable(obj, "searchMethod"),
                bookInfoUrl = nullable(obj, "bookInfoUrl"),
                tocUrl = nullable(obj, "tocUrl"),
                tocCharset = nullable(obj, "tocCharset"),
                contentUrl = nullable(obj, "contentUrl"),
                contentCharset = nullable(obj, "contentCharset"),
                header = nullable(obj, "header"),
                loginUrl = nullable(obj, "loginUrl")
            )
        }
    }

    private fun toJson(sources: List<BookSource>): String {
        val arr = JSONArray()
        sources.forEach { source ->
            val obj = JSONObject().apply {
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
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun nullable(obj: JSONObject, key: String): String? {
        return if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
    }
}
