package io.reader.android.ui

import io.reader.android.runtime.ReaderMangaDirectory
import io.reader.android.runtime.ReaderMangaDirectoryNode

/** Reader-owned projection of issued native directory nodes; never text positions or fabricated targets. */
internal data class ReaderMangaDirectoryUiState(
    val directory: ReaderMangaDirectory? = null,
    val nodes: List<ReaderMangaDirectoryNode> = emptyList(),
    val nextOffset: Int? = null,
    val visible: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: ReaderMangaDirectoryNotice? = null,
)

internal data class ReaderMangaDirectoryNotice(val id: Long, val message: String)
