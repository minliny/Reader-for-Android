package com.reader.android.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

class ThemePreferences(private val context: Context) {

    companion object {
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_FONT_SIZE = floatPreferencesKey("font_size")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_PAGE_MARGIN = floatPreferencesKey("page_margin")

        const val FONT_SIZE_DEFAULT = 18f
        const val LINE_SPACING_DEFAULT = 1.6f
        const val PAGE_MARGIN_DEFAULT = 20f
    }

    val darkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: false
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE] ?: FONT_SIZE_DEFAULT
    }

    val lineSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LINE_SPACING] ?: LINE_SPACING_DEFAULT
    }

    val pageMargin: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_MARGIN] ?: PAGE_MARGIN_DEFAULT
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun setFontSize(sp: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = sp.coerceIn(12f, 32f)
        }
    }

    suspend fun setLineSpacing(multiplier: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LINE_SPACING] = multiplier.coerceIn(1.0f, 3.0f)
        }
    }

    suspend fun setPageMargin(dp: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PAGE_MARGIN] = dp.coerceIn(8f, 48f)
        }
    }
}
