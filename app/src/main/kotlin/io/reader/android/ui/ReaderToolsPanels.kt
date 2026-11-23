package io.reader.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
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
            ReaderToolsRecycler(state, model,
                Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("reader_bookmark_list"))
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
            ReaderToolsRecycler(state, model,
                Modifier.fillMaxWidth().heightIn(max = 330.dp).testTag("reader_content_results"),
                beforeSearchJump = { keyboard?.hide() })
        }
    }
}
