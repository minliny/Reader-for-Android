package io.reader.android.ui

import android.view.ViewGroup
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.reader.android.R
import io.reader.android.runtime.ReaderBookmark
import io.reader.android.runtime.ReaderContentSearchHit

private sealed interface ToolRow {
    val key: String
    val owner: Long
    val bookId: String
    val panel: ReaderPanel

    data class Bookmark(val bookmark: ReaderBookmark, override val owner: Long,
        val canOpen: Boolean, val canDelete: Boolean) : ToolRow {
        override val key = "reader_bookmark_${bookmark.id}"
        override val bookId = bookmark.bookId
        override val panel = ReaderPanel.Bookmarks
    }
    data class Hit(val hit: ReaderContentSearchHit, override val owner: Long,
        val sessionId: String?, val enabled: Boolean) : ToolRow {
        override val key = "reader_content_hit_${hit.id}"
        override val bookId = hit.bookId
        override val panel = ReaderPanel.Search
    }
    data class More(override val owner: Long, override val bookId: String,
        val sessionId: String, val cursor: String, val enabled: Boolean) : ToolRow {
        override val key = "reader_content_search_next:$sessionId:$cursor"
        override val panel = ReaderPanel.Search
    }
}

private enum class ToolAction { Open, Delete, More }

/** Native list ownership; ComposeView keeps the existing typography and row controls. */
@Composable
internal fun ReaderToolsRecycler(state: ReaderUiState, model: ReaderViewModel, modifier: Modifier,
    beforeSearchJump: (() -> Unit)? = null) {
    val reading = state.reading ?: return
    val panel = state.readerPanel ?: return
    val parent = rememberCompositionContext()
    key(reading.identity, panel) {
        AndroidView(
            factory = { context -> RecyclerView(context).apply {
                id = if (panel == ReaderPanel.Bookmarks) R.id.reader_native_bookmark_list else R.id.reader_native_search_list
                layoutManager = LinearLayoutManager(context)
                adapter = ToolAdapter(parent, model, reading.identity, reading.book.id, panel, beforeSearchJump)
                itemAnimator = null // Existing rows have no item-transition animation.
                isVerticalScrollBarEnabled = false
            } },
            modifier = modifier,
            onReset = null,
            onRelease = { list ->
                (list.adapter as? ToolAdapter)?.release()
                list.stopScroll()
                list.adapter = null
                list.recycledViewPool.clear()
            },
            update = { list -> (list.adapter as? ToolAdapter)?.bind(state) },
        )
    }
}

private class ToolAdapter(private val parent: CompositionContext, private val model: ReaderViewModel,
    private val owner: Long, private val bookId: String, private val panel: ReaderPanel,
    private val beforeSearchJump: (() -> Unit)?) :
    ListAdapter<ToolRow, ToolHolder>(object : DiffUtil.ItemCallback<ToolRow>() {
        override fun areItemsTheSame(oldItem: ToolRow, newItem: ToolRow): Boolean =
            oldItem::class == newItem::class && oldItem.key == newItem.key && oldItem.owner == newItem.owner && oldItem.bookId == newItem.bookId
        override fun areContentsTheSame(oldItem: ToolRow, newItem: ToolRow) = oldItem == newItem
    }) {
    private var released = false
    private val holders = mutableSetOf<ToolHolder>()

    fun bind(state: ReaderUiState) {
        val reading = state.reading ?: return
        if (released || reading.identity != owner || reading.book.id != bookId || state.readerPanel != panel) return
        val rows: List<ToolRow> = if (panel == ReaderPanel.Bookmarks) state.bookmarks.map {
            ToolRow.Bookmark(it, owner, !state.busy && state.pendingPosition == null,
                !state.busy && state.pendingBookmarks.isEmpty())
        } else buildList {
            val sessionId = state.searchSession?.id
            state.searchResults.forEach { add(ToolRow.Hit(it, owner, sessionId,
                !state.busy && !state.searchBusy && state.pendingPosition == null && sessionId != null)) }
            val cursor = state.searchCursor
            if (state.searchHasMore && sessionId != null && cursor != null)
                add(ToolRow.More(owner, bookId, sessionId, cursor, !state.busy && !state.searchBusy))
        }
        submitList(rows)
    }

    override fun onCreateViewHolder(parentView: ViewGroup, viewType: Int): ToolHolder =
        ToolHolder(ComposeView(parentView.context).apply { id = View.generateViewId() }, parent, ::act).also { holders += it }

    override fun onBindViewHolder(holder: ToolHolder, position: Int) = holder.bind(getItem(position))
    override fun onViewRecycled(holder: ToolHolder) { holder.retire(); super.onViewRecycled(holder) }
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) { release(); super.onDetachedFromRecyclerView(recyclerView) }

    private fun act(row: ToolRow, action: ToolAction) {
        if (released) return
        val current = model.state.value
        val reading = current.reading ?: return
        if (current.route != ReaderRoute.Reading || reading.identity != row.owner ||
            reading.book.id != row.bookId || current.readerPanel != row.panel) return
        when (row) {
            is ToolRow.Bookmark -> if (!current.busy && current.bookmarks.any { it == row.bookmark }) when (action) {
                ToolAction.Open -> if (current.pendingPosition == null) model.openBookmark(row.bookmark, row.owner)
                ToolAction.Delete -> if (current.pendingBookmarks.isEmpty()) model.requestDeleteBookmark(row.bookmark, row.owner)
                ToolAction.More -> Unit
            }
            is ToolRow.Hit -> if (action == ToolAction.Open && !current.busy && !current.searchBusy &&
                current.pendingPosition == null && row.sessionId != null && current.searchSession?.id == row.sessionId &&
                row.hit.sessionId == row.sessionId && current.searchResults.any { it == row.hit }) {
                beforeSearchJump?.invoke()
                model.openContentSearchHit(row.hit, row.owner)
            }
            is ToolRow.More -> if (action == ToolAction.More && !current.busy && !current.searchBusy &&
                current.searchHasMore && current.searchSession?.id == row.sessionId && current.searchCursor == row.cursor)
                model.nextContentSearch()
        }
    }

    fun release() {
        if (released) return
        released = true
        holders.forEach { it.release() }
        holders.clear()
    }
}

private class ToolHolder(private val view: ComposeView, parent: CompositionContext,
    private val action: (ToolRow, ToolAction) -> Unit) : RecyclerView.ViewHolder(view) {
    private class Binding(val row: ToolRow)
    private var binding by mutableStateOf<Binding?>(null)
    private var released = false

    init {
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        view.setParentCompositionContext(parent)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        view.setContent {
            val captured = binding
            if (captured != null) ToolRowContent(captured.row) { selected ->
                if (!released && binding === captured && view.isAttachedToWindow) action(captured.row, selected)
            }
        }
    }

    fun bind(row: ToolRow) {
        if (released) return
        binding = Binding(row)
        view.tag = row.key
    }
    fun retire() { binding = null; view.tag = null }
    fun release() {
        if (released) return
        released = true
        retire()
        view.disposeComposition()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ToolRowContent(row: ToolRow, action: (ToolAction) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }) {
        when (row) {
            is ToolRow.Bookmark -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.fillMaxWidth().clickable(enabled = row.canOpen) { action(ToolAction.Open) }
                    .padding(vertical = 8.dp).testTag(row.key)) {
                    Text(row.bookmark.chapterTitle, style = MaterialTheme.typography.titleMedium)
                    if (row.bookmark.excerpt.isNotEmpty()) Text(row.bookmark.excerpt, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text("第 ${row.bookmark.chapterIndex + 1} 章", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { action(ToolAction.Delete) }, enabled = row.canDelete,
                    modifier = Modifier.testTag("reader_delete_bookmark_${row.bookmark.id}")) { Text("删除书签") }
            }
            is ToolRow.Hit -> Column(Modifier.fillMaxWidth().clickable(enabled = row.enabled) { action(ToolAction.Open) }
                .padding(vertical = 14.dp).testTag(row.key)) {
                Text(row.hit.chapterTitle, style = MaterialTheme.typography.titleMedium)
                Text(row.hit.snippet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is ToolRow.More -> TextButton(onClick = { action(ToolAction.More) }, enabled = row.enabled,
                modifier = Modifier.testTag("reader_content_search_next")) { Text("加载更多结果") }
        }
        if (row !is ToolRow.More) HorizontalDivider()
    }
}
