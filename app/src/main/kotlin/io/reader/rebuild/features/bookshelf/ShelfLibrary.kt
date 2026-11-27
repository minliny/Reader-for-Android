package io.reader.rebuild.features.bookshelf

import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.BookshelfListParams
import com.reader.core.protocol.BookshelfListParamsContentKind
import com.reader.core.protocol.BookshelfListParamsSortBy
import com.reader.core.protocol.BookshelfListParamsSortDirection
import com.reader.core.protocol.BookshelfRemoveParams
import com.reader.core.protocol.ReaderM1
import io.reader.rebuild.core.CoreRuntimeActor

internal class ShelfLibrary(private val core: CoreRuntimeActor) {
    suspend fun list(sort: BookshelfListParamsSortBy? = null,
        direction: BookshelfListParamsSortDirection? = null): List<BookshelfEntryData> {
        val books = mutableListOf<BookshelfEntryData>()
        var revision: String? = null
        do {
            val page = core.call(ReaderM1.bookshelfList, BookshelfListParams(
                contentKind = BookshelfListParamsContentKind.TEXT, sortBy = sort, sortDirection = direction,
                pageProjection = true,
                offset = books.size.toLong(), limit = 128, projectionRevision = revision,
            ))
            check(page.changed != true) { "书架刚刚变化，请重新读取" }
            revision = page.projectionRevision ?: revision
            books += page.books
            if (page.books.isEmpty()) break
        } while (books.size < page.total)
        return books
    }

    suspend fun get(key: BookshelfGetParams) = core.call(ReaderM1.bookshelfGet, key).book

    suspend fun remove(key: BookshelfGetParams) {
        val receipt = core.call(ReaderM1.bookshelfRemove, BookshelfRemoveParams(key.sourceId, key.bookId))
        check(receipt.removed || get(key) == null) { "移出书架尚未确认" }
    }
}
