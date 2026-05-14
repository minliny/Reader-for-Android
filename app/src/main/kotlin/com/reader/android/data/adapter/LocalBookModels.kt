package com.reader.android.data.adapter

data class LocalBookSource(
    val uri: String,
    val displayName: String,
    val format: LocalBookFormat,
    val mimeType: String? = null,
    val fileSize: Long = 0
)

enum class LocalBookFormat {
    TXT, EPUB, UNKNOWN
}

data class LocalBookMetadata(
    val title: String,
    val author: String? = null,
    val chapters: List<LocalChapterRef> = emptyList(),
    val format: LocalBookFormat = LocalBookFormat.UNKNOWN,
    val encoding: String? = null
)

data class LocalChapterRef(
    val index: Int,
    val title: String,
    val startOffset: Long = 0,
    val endOffset: Long = 0
)

data class LocalBookImportResult(
    val success: Boolean,
    val source: LocalBookSource,
    val metadata: LocalBookMetadata? = null,
    val errorMessage: String? = null
)
