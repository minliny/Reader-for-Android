package io.reader.rebuild.features.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2RuleSubData
import com.reader.core.protocol.M2RuleSubUpdatePreviewData
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubDelete
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubUpdateApplyV1
import com.reader.core.theme.ReaderDesignMetrics as Metrics
import com.reader.core.theme.ReaderIconRegistry as Icons
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderIcon
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.readerTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface SubscriptionRoute {
    data object List : SubscriptionRoute
    data class Edit(val subscription: M2RuleSubData?) : SubscriptionRoute
    data class Diff(val preview: M2RuleSubUpdatePreviewData) : SubscriptionRoute
}

@Composable
internal fun SourceSubscriptionsScreen(
    coordinator: SourceManagementCoordinator,
    onBack: () -> Unit,
    onConfirmationVisibility: (Boolean) -> Unit,
) {
    val colors = LocalReaderColors.current
    val scope = rememberCoroutineScope()
    val unresolved by coordinator.unresolvedOperation.collectAsState()
    var route by remember { mutableStateOf<SubscriptionRoute>(SubscriptionRoute.List) }
    var records by remember { mutableStateOf<List<M2RuleSubData>?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var confirmationGeneration by remember { mutableLongStateOf(0L) }
    var deleting by remember { mutableStateOf<M2RuleSubData?>(null) }
    var confirmingDiff by remember { mutableStateOf<Set<String>?>(null) }
    SourceConfirmationVisibility(deleting != null || route is SubscriptionRoute.Diff && confirmingDiff != null,
        onConfirmationVisibility)

    LaunchedEffect(route, revision, unresolved) {
        if (route != SubscriptionRoute.List) return@LaunchedEffect
        try {
            records = coordinator.subscriptions().subs.filter { it.type == 3L }
                .sortedWith(compareBy({ it.customOrder }, { it.name }))
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            issue = if (records == null) "无法读取规则订阅" else "暂时无法刷新订阅，已保留原列表"
        }
    }

    fun doWork(errorText: String, job: suspend () -> Unit) {
        if (busy || unresolved != null) return
        busy = true
        issue = null
        scope.launch {
            try {
                job()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: SourceOperationUnresolved) {
                // The outer source host presents recovery for the original receipt.
            } catch (_: Exception) {
                issue = errorText
            } finally {
                busy = false
            }
        }
    }

    fun backToList() {
        confirmingDiff = null
        issue = null
        route = SubscriptionRoute.List
    }

    BackHandler(enabled = route != SubscriptionRoute.List, onBack = ::backToList)
    when (val current = route) {
        is SubscriptionRoute.Edit -> SubscriptionEditor(current.subscription, coordinator, ::backToList)
        is SubscriptionRoute.Diff -> {
            var selected by remember(current.preview.planId) {
                mutableStateOf(current.preview.changes.filter { it.canApply }.map { it.sourceId }.toSet())
            }
            SourcePage("订阅版本差异", ::backToList) {
                Column(Modifier.fillMaxWidth()) {
                    issue?.let { Text(it, Modifier.padding(Metrics.screenInset.dp), color = colors.danger,
                        style = readerTextStyle(Metrics.rowDetail)) }
                    SourceSubscriptionDiffContent(current.preview, selected, onSelect = { id, checked ->
                        selected = if (checked) selected + id else selected - id
                    }, onConfirm = {
                        confirmationGeneration++
                        confirmingDiff = selected
                    }, enabled = !busy && unresolved == null)
                }
            }
            ReaderConfirmation(
                confirmingDiff?.let {
                    ReaderConfirmationState("应用订阅变更？", "将按此预览应用 ${it.size} 项书源变更。", "应用", confirmationGeneration)
                },
                ownerForeground = true,
                onDismiss = { confirmingDiff = null },
                onConfirm = {
                    val confirmed = confirmingDiff.orEmpty()
                    confirmingDiff = null
                    if (confirmed.isNotEmpty()) doWork("无法应用所选变更。可重新预览后再试。") {
                        check(coordinator.confirmSubscription(current.preview, confirmed.toList())
                            is M2RulesMutationReceiptRuleSubUpdateApplyV1)
                        backToList()
                    }
                },
            )
        }
        SubscriptionRoute.List -> SourcePage("规则订阅", onBack) {
            val list = records
            if (list == null) {
                ReaderPageState(issue?.let { PageState.Failure(it, revision.toLong()) } ?: PageState.Loading,
                    onRetry = { revision++ })
            } else {
                LazyColumn(
                    Modifier.sourcePageWidth().padding(Metrics.screenInset.dp).testTag("source.subscription.list"),
                    verticalArrangement = Arrangement.spacedBy(Metrics.spaceSmall.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SourceTextAction("新增订阅", { route = SubscriptionRoute.Edit(null) }, !busy && unresolved == null)
                            SourceTextAction("刷新", { revision++ }, !busy)
                            if (busy) CircularProgressIndicator()
                        }
                    }
                    issue?.let { item { Text(it, style = readerTextStyle(Metrics.rowDetail), color = colors.danger) } }
                    if (list.isEmpty()) item { ReaderPageState(PageState.Empty("还没有规则订阅")) }
                    items(list, key = { it.id }) { subscription ->
                        SourceCard(Modifier.testTag("source.subscription.row.${subscription.id}")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ReaderIcon(Icons.sourceSubscription, null, size = Metrics.sourceCheckActionSize.dp)
                                Text(subscription.name, Modifier.padding(start = Metrics.spaceExtraSmall.dp),
                                    style = readerTextStyle(Metrics.sourceRowTitle), color = colors.primaryText)
                            }
                            Text(subscription.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = readerTextStyle(Metrics.sourceStatusText), color = colors.secondaryText)
                            Row {
                                val allowed = !busy && unresolved == null
                                SourceTextAction("版本差异", {
                                    doWork("暂时无法预览订阅版本。请核对链接后重试。") {
                                        route = SubscriptionRoute.Diff(coordinator.fetchSubscriptionPreview(subscription))
                                    }
                                }, allowed)
                                SourceTextAction("编辑", { route = SubscriptionRoute.Edit(subscription) }, allowed)
                                SourceTextAction("删除", {
                                    confirmationGeneration++
                                    deleting = subscription
                                }, allowed)
                            }
                        }
                    }
                }
            }
        }
    }
    ReaderConfirmation(
        deleting?.let {
            ReaderConfirmationState("删除规则订阅？", "已通过此订阅导入的书源会保留。", "删除订阅", confirmationGeneration)
        },
        ownerForeground = true,
        onDismiss = { deleting = null },
        onConfirm = {
            val confirmed = deleting
            deleting = null
            if (confirmed != null) doWork("订阅删除未完成，已保留原列表。") {
                check(coordinator.deleteSubscription(confirmed.id) is M2RulesMutationReceiptRuleSubDelete)
                revision++
            }
        },
    )
}
