package com.reader.android.data.storage

import android.content.Context

/**
 * Persists the exact opaque token returned by Core `replace.persist`.
 * Implementations must never synthesize or rewrite token fields.
 */
internal interface ReaderReplaceUndoTokenStore {
    fun load(): String?

    /** Returns true only after the token is durably available to a new process. */
    fun save(tokenJson: String): Boolean

    /** Returns true only after a previously stored token is durably removed. */
    fun clear(): Boolean
}

internal object NoOpReaderReplaceUndoTokenStore : ReaderReplaceUndoTokenStore {
    override fun load(): String? = null

    override fun save(tokenJson: String): Boolean = false

    override fun clear(): Boolean = true
}

internal class InMemoryReaderReplaceUndoTokenStore(
    initialTokenJson: String? = null
) : ReaderReplaceUndoTokenStore {
    private var tokenJson: String? = initialTokenJson

    @Synchronized
    override fun load(): String? = tokenJson

    @Synchronized
    override fun save(tokenJson: String): Boolean {
        require(tokenJson.isNotBlank()) { "replace undo token must not be blank" }
        this.tokenJson = tokenJson
        return true
    }

    @Synchronized
    override fun clear(): Boolean {
        tokenJson = null
        return true
    }
}

internal class SharedPreferencesReaderReplaceUndoTokenStore(
    context: Context
) : ReaderReplaceUndoTokenStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun load(): String? = preferences.getString(KEY_TOKEN_JSON, null)
        ?.takeIf(String::isNotBlank)

    override fun save(tokenJson: String): Boolean {
        require(tokenJson.isNotBlank()) { "replace undo token must not be blank" }
        return preferences.edit().putString(KEY_TOKEN_JSON, tokenJson).commit()
    }

    override fun clear(): Boolean = preferences.edit().remove(KEY_TOKEN_JSON).commit()

    private companion object {
        const val PREFERENCES_NAME = "reader_replace_undo"
        const val KEY_TOKEN_JSON = "core_issued_token_json"
    }
}
