package com.reader.android.ui.booksource

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.data.model.BookSource
import com.reader.android.data.repository.BookSourceRepository
import com.reader.android.data.repository.FakeBookSourceRepository

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourceScreen() {
    val viewModel = remember { SourceManagementViewModel() }
    val sources = viewModel.sources

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("书源管理") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: import dialog */ }) {
                Icon(Icons.Filled.Add, contentDescription = "导入")
            }
        }
    ) { padding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无书源", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(sources, key = { it.sourceUrl }) { source ->
                    SourceItem(
                        source = source,
                        onToggle = { viewModel.toggleEnabled(source.sourceUrl) },
                        onDelete = { viewModel.delete(source.sourceUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: BookSource,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (source.enabled)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.sourceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = source.sourceGroup ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = source.sourceUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Checkbox(checked = source.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}
