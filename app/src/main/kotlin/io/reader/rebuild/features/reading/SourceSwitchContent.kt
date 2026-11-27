package io.reader.rebuild.features.reading

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2ChangeBookSourceCandidate
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun SourceSwitchContent(candidates: List<M2ChangeBookSourceCandidate>,
    onSelect: (M2ChangeBookSourceCandidate) -> Unit = {}) {
    val colors = LocalReaderColors.current
    if (candidates.isEmpty()) ReaderPageState(PageState.Empty("没有其他可用来源"))
    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
        items(candidates, key = { "${it.sourceId}\u0000${it.bookUrl}" }) { item ->
            Row(Modifier.fillMaxWidth().heightIn(min = Metrics.sourceSwitchRowHeight.dp)
                .clickable { onSelect(item) }.padding(horizontal = Metrics.screenInset.dp, vertical = Metrics.spaceExtraSmall.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.bookName, style = readerTextStyle(Metrics.rowLabel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(item.author, item.sourceId).joinToString(" · "),
                        style = readerTextStyle(Metrics.rowDetail), color = colors.secondaryText, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                ReaderIcon(ReaderIconRegistry.sourceSwitch, "切换到该来源")
            }
            HorizontalDivider(color = colors.divider)
        }
    }
}
