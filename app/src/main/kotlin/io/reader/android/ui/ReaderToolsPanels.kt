package io.reader.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reading tools share one host; all reads, writes and cursor ownership stay in the model. */
@Composable
internal fun ReaderToolsPanel(state: ReaderUiState, model: ReaderViewModel) {
    val reading = state.reading ?: return
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("reader_tools_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.readerPanel == ReaderPanel.Bookmarks) "书签" else "正文搜索",
                style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = model::closeReaderPanel, modifier = Modifier.testTag("reader_close_panel")) { Text("关闭") }
        }
        if (state.readerPanel == ReaderPanel.Bookmarks) {
            Button(onClick = { model.createVisibleBookmark(reading.identity) },
                enabled = !state.busy && state.pendingBookmarks.isEmpty() && state.pendingPosition == null,
                modifier = Modifier.testTag("reader_add_bookmark")) { Text("添加当前位置") }
            state.bookmarkError?.let {
                ReaderPageState("书签操作未完成", it, "重新读取书签", model::loadBookmarks)
            }
            if (!state.bookmarksReady && state.busy) LoadingLine()
            else if (state.bookmarksReady && state.bookmarks.isEmpty()) ReaderPageState("暂无书签", "添加书签，随时回到当前阅读位置。")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("reader_bookmark_list")) {
                items(state.bookmarks, key = { it.id }) { bookmark ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.fillMaxWidth().clickable(enabled = !state.busy && state.pendingPosition == null) {
                            model.openBookmark(bookmark, reading.identity)
                        }.padding(vertical = 8.dp).testTag("reader_bookmark_${bookmark.id}")) {
                            Text(bookmark.chapterTitle, style = MaterialTheme.typography.titleMedium)
                            if (bookmark.excerpt.isNotEmpty()) Text(bookmark.excerpt, maxLines = 3,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text("第 ${bookmark.chapterIndex + 1} 章", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { model.requestDeleteBookmark(bookmark, reading.identity) },
                            enabled = !state.busy && state.pendingBookmarks.isEmpty(),
                            modifier = Modifier.testTag("reader_delete_bookmark_${bookmark.id}")) { Text("删除书签") }
                    }
                    HorizontalDivider()
                }
            }
        } else {
            val keyboard = LocalSoftwareKeyboardController.current
            val submit = { keyboard?.hide(); model.searchContent() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = state.searchQuery, onValueChange = model::setContentSearchQuery,
                    label = { Text("搜索正文") }, singleLine = true, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.weight(1f).testTag("reader_content_query"))
                TextButton(onClick = submit, enabled = state.searchQuery.isNotBlank() && !state.searchBusy && !state.busy && state.pendingSearchClose == null,
                    modifier = Modifier.testTag("reader_content_search_submit")) { Text("搜索") }
            }
            state.searchError?.let { ReaderPageState("正文搜索未完成", it) }
            if (state.searchBusy) LoadingLine()
            else if (state.searchReady && state.searchResults.isEmpty()) ReaderPageState("没有找到匹配内容")
            else if (!state.searchReady && state.searchError == null) ReaderPageState("搜索本书正文", "输入关键词后开始搜索。")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("reader_content_results")) {
                items(state.searchResults, key = { it.id }) { hit ->
                    Column(Modifier.fillMaxWidth().clickable(enabled = !state.busy && !state.searchBusy && state.pendingPosition == null && state.searchSession != null) {
                        keyboard?.hide(); model.openContentSearchHit(hit, reading.identity)
                    }.padding(vertical = 14.dp).testTag("reader_content_hit_${hit.id}")) {
                        Text(hit.chapterTitle, style = MaterialTheme.typography.titleMedium)
                        Text(hit.snippet, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                if (state.searchHasMore) item {
                    TextButton(onClick = model::nextContentSearch, enabled = !state.searchBusy && !state.busy,
                        modifier = Modifier.testTag("reader_content_search_next")) { Text("加载更多结果") }
                }
            }
        }
    }
}
