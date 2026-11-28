package io.reader.rebuild.features.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.theme.ReaderDesignMetrics
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.readerTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderHomeMenu(
    onSources: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit = {},
    onCheckUpdates: () -> Unit = {},
    onShelfSettings: () -> Unit = onSettings,
    onShelfData: () -> Unit = {},
    initialOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initialOpen) }
    ReaderAction(ReaderIconRegistry.more, "书架操作", { open = true })
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(Modifier.padding(bottom = 20.dp)) {
                Text("书架操作", Modifier.padding(horizontal = 24.dp), style = readerTextStyle(ReaderDesignMetrics.pageTitle))
                listOf(
                    Triple(ReaderIconRegistry.check, "批量管理", onShelfData),
                    Triple(ReaderIconRegistry.folder, "本地导入", onImport),
                    Triple(ReaderIconRegistry.refresh, "检查更新", onCheckUpdates),
                    Triple(ReaderIconRegistry.download, "下载管理", onDownloads),
                    Triple(ReaderIconRegistry.settings, "书架设置", onShelfSettings),
                    Triple(ReaderIconRegistry.storage, "书架数据管理", onShelfData),
                ).forEach { (icon, label, action) ->
                    TextButton(onClick = { open = false; action() }, Modifier.fillMaxWidth()) {
                        ReaderIcon(icon, null)
                        Text(label, Modifier.padding(start = 12.dp), style = readerTextStyle(ReaderDesignMetrics.rowLabel))
                    }
                }
            }
        }
    }
}
