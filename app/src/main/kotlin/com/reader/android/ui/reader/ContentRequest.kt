package com.reader.android.ui.reader

/**
 * TOC → Content bridge model. Carries all context needed to load chapter content.
 */
data class ContentRequest(
    val chapterUrl: String,
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val bookTitle: String = "",
    val sourceName: String = "默认来源"
) {
    val isValid: Boolean get() = chapterUrl.isNotBlank()
    val hasPrevChapter: Boolean get() = chapterIndex > 0
    val hasNextChapter: Boolean get() = chapterIndex < totalChapters - 1
}
