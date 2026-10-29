package io.reader.android.runtime

/** Display metadata only. It cannot enter local ReaderChapter/Original/progress/reading-session APIs. */
data class ReaderRemoteShelfBook(val key: ShelfBookKey, val title: String, val author: String, val sourceName: String?)
