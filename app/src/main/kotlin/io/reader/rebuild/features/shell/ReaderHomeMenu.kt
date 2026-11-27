package io.reader.rebuild.features.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle

@Composable
internal fun ReaderHomeMenu(onSources: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ReaderAction(ReaderIconRegistry.more, "更多", { open = true })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(
                Triple(ReaderIconRegistry.sourceHealthCheck, "书源管理", onSources),
                Triple(ReaderIconRegistry.download, "下载管理", onDownloads),
                Triple(ReaderIconRegistry.settings, "设置", onSettings),
            ).forEach { (icon, label, action) ->
                DropdownMenuItem(text = { Text(label, style = readerTextStyle(ReaderDesignMetrics.rowLabel)) },
                    leadingIcon = { ReaderIcon(icon, null) }, onClick = { open = false; action() })
            }
        }
    }
}
