package io.reader.rebuild.features.settings

import android.content.SharedPreferences
import com.reader.core.protocol.BookshelfListParamsSortBy
import com.reader.core.protocol.BookshelfListParamsSortDirection
import io.reader.rebuild.features.reading.ReadingAppearance

internal enum class AppAppearance(val title: String) { SYSTEM("跟随系统"), DAY("日间"), NIGHT("夜间") }

internal enum class ShelfSort(
    val title: String,
    val sort: BookshelfListParamsSortBy,
    val direction: BookshelfListParamsSortDirection,
) {
    ADDED("加入时间", BookshelfListParamsSortBy.ADDED_AT, BookshelfListParamsSortDirection.DESCENDING),
    RECENT("最近阅读", BookshelfListParamsSortBy.LAST_READ_AT, BookshelfListParamsSortDirection.DESCENDING),
    TITLE("书名", BookshelfListParamsSortBy.TITLE, BookshelfListParamsSortDirection.ASCENDING),
}

internal class ReaderPreferences(private val store: SharedPreferences) {
    fun theme(): AppAppearance = AppAppearance.entries.firstOrNull { it.name == store.getString("theme", null) }
        ?: AppAppearance.SYSTEM

    fun setTheme(value: AppAppearance) { store.edit().putString("theme", value.name).apply() }

    fun shelfSort() = ShelfSort.entries.firstOrNull { it.name == store.getString("shelfSort", null) } ?: ShelfSort.ADDED
    fun setShelfSort(value: ShelfSort) { store.edit().putString("shelfSort", value.name).apply() }
    fun keepAwake() = store.getBoolean("keepAwake", false)
    fun setKeepAwake(value: Boolean) { store.edit().putBoolean("keepAwake", value).apply() }

    fun reading(): ReadingAppearance {
        val defaults = ReadingAppearance()
        return ReadingAppearance(
            paperId = store.getString("paper", null) ?: defaults.paperId,
            font = store.getFloat("font", defaults.font.toFloat()).toDouble().coerceIn(12.0, 36.0),
            line = store.getFloat("line", defaults.line.toFloat()).toDouble().coerceIn(1.25, 2.6),
            scrolling = store.getBoolean("scrolling", false),
            spacing = store.getFloat("spacing", 0f).toDouble().coerceIn(0.0, 4.0),
        )
    }

    fun setReading(value: ReadingAppearance) {
        store.edit().putString("paper", value.paperId).putFloat("font", value.font.toFloat())
            .putFloat("line", value.line.toFloat()).putBoolean("scrolling", value.scrolling)
            .putFloat("spacing", value.spacing.toFloat()).apply()
    }
}
