package com.reader.android.ui.reader

import com.reader.android.data.model.TOCItem

/**
 * Maps Core [TOCItem] tree → flat [ReaderTocEntryUiModel] list.
 * Computes level from tree depth. Only depends on public Core DTO.
 */
object CoreTocToReaderTocMapper {

    fun map(tocItems: List<TOCItem>): List<ReaderTocEntryUiModel> {
        val result = mutableListOf<ReaderTocEntryUiModel>()
        var index = 0
        walk(items = tocItems, parentLevel = 0, parentPath = "", result = result, index = { index++ })
        return result
    }

    private fun walk(
        items: List<TOCItem>,
        parentLevel: Int,
        parentPath: String,
        result: MutableList<ReaderTocEntryUiModel>,
        index: () -> Int
    ) {
        for (item in items) {
            val level = parentLevel + 1
            val path = if (parentPath.isNotEmpty()) "$parentPath / ${item.title}" else item.title
            val entry = ReaderTocEntryUiModel(
                title = item.title.ifBlank { "未命名章节" },
                level = level,
                isCurrent = false,   // joined later by ReaderTocLocalStateJoiner
                hasBookmark = false,  // joined later
                progress = null,     // joined later
                url = item.url       // used for TOC→Content bridge
            )
            result.add(entry)
            walk(item.children, level, path, result, index)
        }
    }

    /** Returns only leaf chapters (those with non-blank url). */
    fun flattenChapters(tocItems: List<TOCItem>): List<TOCItem> {
        val result = mutableListOf<TOCItem>()
        for (item in tocItems) {
            if (item.url.isNotBlank()) result.add(item)
            result.addAll(flattenChapters(item.children))
        }
        return result
    }
}
