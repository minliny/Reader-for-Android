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
    val displaySource get() = sourceName?.takeIf(String::isNotBlank)?.let(::cleanDisplaySourceName)
        ?: if (local) "本地书籍" else sourceId
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

private fun isLeadingDecorativeCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF ||
        cp in 0x2600..0x27BF ||
        cp in 0x2300..0x23FF ||
        cp in 0x25A0..0x25FF ||
        cp in 0x2B00..0x2BFF ||
        cp in 0xFE00..0xFE0F ||
        cp == 0x200D ||
        cp == 0x20E3 ||
        cp in 0xE0020..0xE007F

private fun isTrailingPrefixSeparator(cp: Int): Boolean =
    cp <= 0x20 || cp == 0x3000 || cp == 0x00B7 || cp == 0x2022 || cp == 0x007C

private fun cleanDisplaySourceName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    var index = 0
    var strippedDecorative = false
    while (index < trimmed.length) {
        val cp = trimmed.codePointAt(index)
        val step = Character.charCount(cp)
        if (isLeadingDecorativeCodePoint(cp)) {
            strippedDecorative = true
            index += step
            continue
        }
        if (strippedDecorative && isTrailingPrefixSeparator(cp)) {
            index += step
            continue
        }
        break
    }
    val cleaned = trimmed.substring(index).trim()
    return cleaned.ifEmpty { trimmed }
}
