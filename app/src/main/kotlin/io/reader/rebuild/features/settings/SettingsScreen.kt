package io.reader.rebuild.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderPaperThemes
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.reading.ReadingAppearance

@Composable
internal fun SettingsScreen(
    theme: AppAppearance,
    reading: ReadingAppearance,
    shelfSort: ShelfSort,
    keepAwake: Boolean,
    onTheme: (AppAppearance) -> Unit,
    onReading: (ReadingAppearance) -> Unit,
    onSort: (ShelfSort) -> Unit,
    onKeepAwake: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
        verticalArrangement = Arrangement.spacedBy(Metrics.spaceLarge.dp)) {
        Column {
            Text("外观", style = readerTextStyle(Metrics.sectionHeading))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                AppAppearance.entries.forEach { item ->
                    FilterChip(theme == item, { onTheme(item) }, label = { Text(item.title) })
                }
            }
        }
        Column {
            Text("通用", style = readerTextStyle(Metrics.sectionHeading))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("阅读时保持屏幕常亮", Modifier.weight(1f), style = readerTextStyle(Metrics.rowLabel))
                Switch(keepAwake, onKeepAwake)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                FilterChip(!reading.scrolling, { onReading(reading.copy(scrolling = false)) }, label = { Text("分页") })
                FilterChip(reading.scrolling, { onReading(reading.copy(scrolling = true)) }, label = { Text("滚动") })
            }
        }
        Column {
            Text("书架", style = readerTextStyle(Metrics.sectionHeading))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                ShelfSort.entries.forEach { sort ->
                    FilterChip(shelfSort == sort, { onSort(sort) }, label = { Text(sort.title) })
                }
            }
        }
        Column {
            Text("默认阅读纸底", style = readerTextStyle(Metrics.sectionHeading))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp)) {
                ReaderPaperThemes.all.forEach { paper ->
                    FilterChip(reading.paper.id == paper.id, { onReading(reading.copy(paperId = paper.id)) },
                        label = { Text(paper.displayName) })
                }
            }
        }
    }
}
