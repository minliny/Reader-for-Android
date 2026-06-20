package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.state.ReaderUiState
import com.reader.android.ui.theme.ReaderTheme

class SourceManagementViewModel(
    private val repository: BookSourceRepository = FakeBookSourceRepository().apply {
        importJson(SAMPLE_SOURCES_JSON)
    }
) {
    var sources by mutableStateOf(repository.getAll())
        private set

    fun toggleEnabled(url: String) {
        val current = repository.getByUrl(url) ?: return
        repository.setEnabled(url, !current.enabled)
        sources = repository.getAll()
    }

    fun delete(url: String) {
        repository.remove(url)
        sources = repository.getAll()
    }

    companion object {
        val SAMPLE_SOURCES_JSON = """
        [
          {"sourceUrl":"https://www.biquge.com","sourceName":"笔趣阁","sourceGroup":"中文","searchUrl":"/search?q=key","tocUrl":"/toc","contentUrl":"/content"},
          {"sourceUrl":"https://www.quanshu.com","sourceName":"全书网","sourceGroup":"中文","searchUrl":"/search?keyword=key","tocUrl":"/toc","contentUrl":"/content"},
          {"sourceUrl":"https://www.xs4all.com","sourceName":"第四书库","sourceGroup":"备用","enabled":false,"searchUrl":"/search?key=key"}
        ]
        """.trimIndent()
    }
}

@Composable
fun BookSourceScreen(uiState: ReaderUiState? = null) {
    val viewModel = remember { SourceManagementViewModel() }
    val sourceState = SourceManagementMapper.fromSources(viewModel.sources)

    ReaderTheme {
        when (uiState) {
            is ReaderUiState.Loading -> { ReaderLoadingState(modifier = Modifier.fillMaxSize()) }
            is ReaderUiState.Error -> { ReaderErrorState(title = "加载失败", message = uiState.message, modifier = Modifier.fillMaxSize()) }
            else -> {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReaderAppTopBar(title = "书源管理")
                if (sourceState.isEmpty) {
                    ReaderEmptyState(
                        title = "暂无书源",
                        message = sourceState.emptyMessage,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(sourceState.sources, key = { it.id }) { source ->
                            SourceItem(
                                source = source,
                                onToggle = { viewModel.toggleEnabled(source.url) },
                                onDelete = { viewModel.delete(source.url) }
                            )
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { /* TODO: import dialog */ },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = ReaderIconToken.Add.asImageVector(),
                    contentDescription = "导入"
                )
            }
        }
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: SourceUiModel,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                color = ReaderTheme.colors.controlInk,
                style = ReaderTheme.typography.bookTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${source.group} · ${source.status.label}",
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta
            )
            Text(
                text = source.url,
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
            checked = source.enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ReaderTheme.colors.paperBg,
                checkedTrackColor = ReaderTheme.colors.primary,
                uncheckedThumbColor = ReaderTheme.colors.controlInk,
                uncheckedTrackColor = ReaderTheme.colors.mutedTrack
            )
        )
        ReaderIconButton(
            icon = ReaderIconToken.Delete.asImageVector(),
            contentDescription = "删除${source.name}",
            onClick = onDelete
        )
    }
}
