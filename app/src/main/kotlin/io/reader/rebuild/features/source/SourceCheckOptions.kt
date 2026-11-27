package io.reader.rebuild.features.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2SourceCheckReportStepsItemLevel
import com.reader.core.protocol.M2SourceCheckRunParamsLevelsItem as CheckLevel
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun SourceCheckOptions(
    count: Int,
    onBack: () -> Unit,
    onStart: (List<CheckLevel>, String?) -> Unit,
) {
    val colors = LocalReaderColors.current
    var levels by remember { mutableStateOf(CheckLevel.entries.toSet()) }
    var keyword by remember { mutableStateOf("") }
    SourcePage("分层检测", onBack) {
        Column(
            Modifier.sourcePageWidth().verticalScroll(rememberScrollState()).padding(Metrics.screenInset.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
        ) {
            Text("已选 $count 个书源", color = colors.secondaryText, style = readerTextStyle(Metrics.rowDetail))
            SourceCard {
                CheckLevel.entries.forEach { level ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = level in levels,
                            onCheckedChange = { selected -> levels = if (selected) levels + level else levels - level },
                        )
                        Text(
                            "${level.wireValue} · ${M2SourceCheckReportStepsItemLevel.valueOf(level.name).label}",
                            style = readerTextStyle(Metrics.rowLabel),
                            color = colors.primaryText,
                        )
                    }
                }
            }
            OutlinedTextField(
                keyword, { keyword = it }, Modifier.fillMaxWidth().testTag("source.check.keyword"),
                label = { Text("搜索关键词", style = readerTextStyle(Metrics.rowDetail)) },
                placeholder = { Text("留空使用书源默认关键词", style = readerTextStyle(Metrics.rowDetail)) },
                singleLine = true, textStyle = readerTextStyle(Metrics.sourceFilterFieldText),
            )
            Button(
                onClick = { onStart(CheckLevel.entries.filter(levels::contains), keyword.trim().ifEmpty { null }) },
                enabled = levels.isNotEmpty(), modifier = Modifier.testTag("source.check.confirm"),
            ) { Text("开始检测", style = readerTextStyle(Metrics.rowLabel)) }
        }
    }
}
