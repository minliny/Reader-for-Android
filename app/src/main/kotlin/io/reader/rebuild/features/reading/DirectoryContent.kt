package io.reader.rebuild.features.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

internal data class DirectoryEntry(val index: Long, val title: String)

@Composable
internal fun DirectoryContent(entries: List<DirectoryEntry>, selected: Long?, onSelect: (Long) -> Unit = {}) {
    val colors = LocalReaderColors.current
    if (entries.isEmpty()) ReaderPageState(PageState.Empty("暂无章节"))
    else LazyColumn {
        items(entries, key = { it.index }) { entry ->
            val active = selected == entry.index
            Text(entry.title, Modifier.fillMaxWidth().heightIn(min = Metrics.minimumActionTarget.dp)
                .background(if (active) colors.accentTint else colors.surface)
                .clickable { onSelect(entry.index) }.padding(Metrics.cardPadding.dp),
                style = readerTextStyle(Metrics.rowLabel), color = if (active) colors.accent else colors.primaryText)
            HorizontalDivider(color = colors.divider)
        }
    }
}
