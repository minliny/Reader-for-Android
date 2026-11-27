package io.reader.rebuild.features.library

import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.M2BookDetailBookInput
import com.reader.core.protocol.M2BookDetailData
import com.reader.core.protocol.M2BookDetailParams
import com.reader.core.protocol.M2BookSearchBookData

internal data class BookReference(
    val sourceId: String,
    val bookId: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val intro: String? = null,
    val lastChapter: String? = null,
    val sourceName: String? = null,
    val kind: String? = null,
    val variables: Map<String, String>? = null,
    val tocUrl: String? = null,
) {
    val local get() = sourceId == "local"
    val key get() = BookshelfGetParams(sourceId, bookId)
    val displaySource get() = sourceName?.takeIf(String::isNotBlank) ?: if (local) "本地书籍" else sourceId
    val detailParams get() = M2BookDetailParams(
        sourceId = sourceId,
        book = M2BookDetailBookInput(bookId, title, author, coverUrl, intro, kind, lastChapter, variables),
        bookUrl = bookId.takeUnless { local },
    )

    constructor(book: BookshelfEntryData) : this(book.sourceId, book.bookId, book.title, book.author,
        book.coverUrl, book.intro, book.lastChapter, book.sourceName, book.kind)

    constructor(sourceId: String, book: M2BookSearchBookData, sourceName: String?) : this(sourceId,
        book.bookId, book.title, book.author, book.coverUrl, book.intro, book.lastChapter, sourceName, book.kind, book.variables, book.tocUrl)

    fun fromDetail(detail: M2BookDetailData): BookReference = with(detail.book) {
        BookReference(detail.sourceId, bookId, title, author, coverUrl, intro, lastChapter,
            this@BookReference.sourceName, kind, detail.variables ?: this@BookReference.variables, detail.tocUrl ?: tocUrl)
    }
}
