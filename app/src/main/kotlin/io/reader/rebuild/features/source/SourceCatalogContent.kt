package io.reader.rebuild.features.source

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2SourceGroupCatalogData
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import com.reader.core.theme.ReaderIconMapping
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle

internal sealed interface SourceCatalogAction {
    data object Import : SourceCatalogAction
    data object Reports : SourceCatalogAction
    data object Subscriptions : SourceCatalogAction
    data object Batch : SourceCatalogAction
    data class Select(val ids: Set<String>) : SourceCatalogAction
    data class Edit(val source: M2SourceListEntry) : SourceCatalogAction
    data class Enable(val ids: Set<String>, val enabled: Boolean) : SourceCatalogAction
    data class Check(val ids: Set<String>) : SourceCatalogAction
    data class Report(val source: M2SourceListEntry) : SourceCatalogAction
    data class Login(val source: M2SourceListEntry) : SourceCatalogAction
    data class ClearLogin(val source: M2SourceListEntry) : SourceCatalogAction
    data class Delete(val ids: Set<String>) : SourceCatalogAction
}

@Composable
internal fun SourceCatalogContent(
    sources: List<M2SourceListEntry>,
    groups: M2SourceGroupCatalogData?,
    query: String,
    onQuery: (String) -> Unit = {},
    groupId: String? = null,
    onGroup: (String?) -> Unit = {},
    selection: Set<String>? = null,
    onAction: (SourceCatalogAction) -> Unit = {},
) {
    val colors = LocalReaderColors.current
    val memberships = groups?.memberships.orEmpty().groupBy { it.sourceId }
    val groupNames = groups?.groups.orEmpty().associate { it.groupId to it.name }
    val needle = query.trim()
    val displayed = sources.filter { source ->
        (groupId == null || memberships[source.sourceId].orEmpty().any { it.groupId == groupId }) &&
            (needle.isEmpty() || source.name.contains(needle, ignoreCase = true) ||
                source.baseUrl.contains(needle, ignoreCase = true))
    }
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.sourcePageWidth().padding(horizontal = Metrics.screenInset.dp).testTag("source.catalog"),
            verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = Metrics.screenInset.dp),
                    horizontalArrangement = Arrangement.spacedBy(Metrics.spaceExtraSmall.dp),
                ) {
                    SourceShortcut("导入书源", Icons.add, Modifier.weight(1f)) { onAction(SourceCatalogAction.Import) }
                    SourceShortcut("批量报告", Icons.sourceHealthCheck, Modifier.weight(1f)) { onAction(SourceCatalogAction.Reports) }
                    SourceShortcut("规则订阅", Icons.sourceSubscription, Modifier.weight(1f)) { onAction(SourceCatalogAction.Subscriptions) }
                }
            }
            item {
                SourceFilters(query, onQuery, groups, groupId, onGroup, selection != null) {
                    onAction(SourceCatalogAction.Batch)
                }
            }
            if (selection != null) item {
                SourceSelection(selection, displayed.map { it.sourceId }.toSet(), onAction)
            }
            if (displayed.isEmpty()) item {
                ReaderPageState(PageState.Empty(if (needle.isEmpty() && groupId == null) "还没有书源" else "没有符合筛选的书源"))
            }
            items(displayed, key = { it.sourceId }) { source ->
                SourceRow(
                    source,
                    memberships[source.sourceId].orEmpty().mapNotNull { groupNames[it.groupId] }.joinToString(" · "),
                    selection, onAction,
                )
            }
            item { Box(Modifier.padding(bottom = Metrics.screenInset.dp)) }
        }
    }
}

@Composable
private fun SourceShortcut(label: String, icon: ReaderIconMapping, modifier: Modifier, action: () -> Unit) {
    val colors = LocalReaderColors.current
    Column(
        modifier.background(colors.accentTint, RoundedCornerShape(Metrics.cardRadius.dp)).clickable(onClick = action)
            .padding(vertical = Metrics.spaceExtraSmall.dp).testTag("source.shortcut.$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp),
    ) {
        ReaderIcon(icon, null, size = Metrics.sourceCheckActionSize.dp)
        Text(label, style = readerTextStyle(Metrics.sourceStatusText), color = colors.accent, maxLines = 1)
    }
}

@Composable
private fun SourceFilters(
    query: String,
    onQuery: (String) -> Unit,
    groups: M2SourceGroupCatalogData?,
    groupId: String?,
    onGroup: (String?) -> Unit,
    selecting: Boolean,
    onSelect: () -> Unit,
) {
    var showGroups by remember { mutableStateOf(false) }
    OutlinedTextField(
        query, onQuery, modifier = Modifier.fillMaxWidth().testTag("source.search"),
        shape = RoundedCornerShape(Metrics.inputRadius.dp), singleLine = true,
        textStyle = readerTextStyle(Metrics.sourceFilterFieldText),
        placeholder = { Text("搜索书源", style = readerTextStyle(Metrics.sourceFilterFieldText)) },
        leadingIcon = { ReaderIcon(Icons.search, null, size = Metrics.sourceLoginActionIconSize.dp) },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { showGroups = true }, modifier = Modifier.testTag("source.groups")) {
                ReaderIcon(Icons.filter, null, size = Metrics.sourceLoginActionIconSize.dp)
                Text(
                    groups?.groups?.firstOrNull { it.groupId == groupId }?.name ?: "全部分组",
                    Modifier.padding(start = Metrics.rowBlockSpacing.dp),
                    style = readerTextStyle(Metrics.sourceFilterFieldText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(showGroups, onDismissRequest = { showGroups = false }) {
                SourceMenuItem("全部分组") { showGroups = false; onGroup(null) }
                groups?.groups.orEmpty().forEach { group ->
                    SourceMenuItem(group.name) { showGroups = false; onGroup(group.groupId) }
                }
            }
        }
        SourceTextAction(if (selecting) "完成" else "批量", onSelect)
    }
}

@Composable
private fun SourceSelection(
    selection: Set<String>,
    displayed: Set<String>,
    onAction: (SourceCatalogAction) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = LocalReaderColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.accentTint, RoundedCornerShape(Metrics.inputRadius.dp))
            .padding(start = Metrics.spaceSmall.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已选 ${selection.size} 项", Modifier.weight(1f),
            style = readerTextStyle(Metrics.sourceStatusText), color = colors.accent,
        )
        SourceTextAction("全选结果", { onAction(SourceCatalogAction.Select(displayed)) })
        Box {
            SourceTextAction("批量操作", { open = true }, selection.isNotEmpty())
            DropdownMenu(open, onDismissRequest = { open = false }) {
                fun action(value: SourceCatalogAction) {
                    open = false
                    onAction(value)
                }
                SourceMenuItem("启用所选") { action(SourceCatalogAction.Enable(selection, true)) }
                SourceMenuItem("停用所选") { action(SourceCatalogAction.Enable(selection, false)) }
                SourceMenuItem("检测所选") { action(SourceCatalogAction.Check(selection)) }
                SourceMenuItem("删除所选", destructive = true) { action(SourceCatalogAction.Delete(selection)) }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: M2SourceListEntry,
    group: String,
    selection: Set<String>?,
    onAction: (SourceCatalogAction) -> Unit,
) {
    val colors = LocalReaderColors.current
    SourceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selection != null) {
                val selected = source.sourceId in selection
                val label = "选择${source.name}"
                Checkbox(
                    selected,
                    { checked ->
                        onAction(SourceCatalogAction.Select(
                            if (checked) selection + source.sourceId else selection - source.sourceId,
                        ))
                    },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            } else {
                Box(
                    Modifier.size(Metrics.sourceRowIconSize.dp)
                        .background(colors.accentTint, RoundedCornerShape(Metrics.inputRadius.dp)),
                    contentAlignment = Alignment.Center,
                ) { ReaderIcon(Icons.link, null, size = Metrics.sourceLoginActionIconSize.dp) }
            }
            Column(
                Modifier.weight(1f)
                    .clickable { onAction(SourceCatalogAction.Edit(source)) }
                    .heightIn(min = Metrics.minimumActionTarget.dp)
                    .padding(horizontal = Metrics.spaceExtraSmall.dp)
                    .testTag("source.row.${source.sourceId}"),
                verticalArrangement = Arrangement.spacedBy(Metrics.rowBlockSpacing.dp, Alignment.CenterVertically),
            ) {
                Text(source.name, color = colors.primaryText, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = readerTextStyle(Metrics.sourceRowTitle))
                Text(source.baseUrl, color = colors.secondaryText, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = readerTextStyle(Metrics.sourceStatusText))
                if (group.isNotEmpty()) SourceStatusBadge(group, colors.secondaryText)
            }
            if (selection == null) {
                val enabledLabel = "启用${source.name}"
                Switch(
                    source.enabled, { onAction(SourceCatalogAction.Enable(setOf(source.sourceId), it)) },
                    Modifier.semantics { contentDescription = enabledLabel }.testTag("source.toggle.${source.sourceId}"),
                )
                SourceRowMenu(source, onAction)
            }
        }
    }
}

@Composable
private fun SourceRowMenu(source: M2SourceListEntry, onAction: (SourceCatalogAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("source.more.${source.sourceId}")) {
            ReaderIcon(Icons.more, "${source.name}的更多操作")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            fun action(value: SourceCatalogAction) {
                open = false
                onAction(value)
            }
            SourceMenuItem("编辑书源") { action(SourceCatalogAction.Edit(source)) }
            SourceMenuItem("分层检测") { action(SourceCatalogAction.Check(setOf(source.sourceId))) }
            SourceMenuItem("查看检测报告") { action(SourceCatalogAction.Report(source)) }
            if (!source.bookSource?.sourceText("loginUrl").isNullOrBlank()) {
                SourceMenuItem("打开登录页", enabled = source.enabled) { action(SourceCatalogAction.Login(source)) }
                SourceMenuItem("清除登录态") { action(SourceCatalogAction.ClearLogin(source)) }
            }
            SourceMenuItem("删除书源", destructive = true) { action(SourceCatalogAction.Delete(setOf(source.sourceId))) }
        }
    }
}

@Composable
private fun SourceMenuItem(label: String, enabled: Boolean = true, destructive: Boolean = false, action: () -> Unit) {
    val colors = LocalReaderColors.current
    DropdownMenuItem(
        text = {
            Text(label, style = readerTextStyle(Metrics.rowLabel),
                color = if (destructive) colors.danger else colors.primaryText)
        },
        enabled = enabled, onClick = action,
    )
}
