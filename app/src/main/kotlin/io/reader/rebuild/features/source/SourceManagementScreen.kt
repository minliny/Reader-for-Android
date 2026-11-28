package io.reader.rebuild.features.source

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.reader.core.protocol.M2RulesMutationReceiptRejected
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubDelete
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubPut
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubUpdateApplyV1
import com.reader.core.protocol.M2RulesMutationReceiptSourceDelete
import com.reader.core.protocol.M2RulesMutationReceiptSourceImport
import com.reader.core.protocol.M2RulesMutationReceiptSourceUpdate
import com.reader.core.protocol.M2SourceGroupCatalogData
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.M2SourceListParamsCheckReportVersion
import io.reader.rebuild.design.PageState
import io.reader.rebuild.design.ReaderConfirmation
import io.reader.rebuild.design.ReaderConfirmationState
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.ReaderPageState
import io.reader.rebuild.design.ReaderTaskProgressState
import io.reader.rebuild.design.RecoveryAction
import io.reader.rebuild.design.RecoveryNoticeState
import io.reader.rebuild.design.TransientToastState
import io.reader.rebuild.host.storage.AuthorizedDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface SourcePageRoute {
    data object Catalog : SourcePageRoute
    data object Import : SourcePageRoute
    data object Reports : SourcePageRoute
    data object Subscriptions : SourcePageRoute
    data class Edit(val source: M2SourceListEntry) : SourcePageRoute
    data class Check(val ids: Set<String>) : SourcePageRoute
    data class Diagnostic(val sourceId: String, val name: String, val fromReports: Boolean) : SourcePageRoute
    data class Login(val source: M2SourceListEntry) : SourcePageRoute
}

private data class SourceCatalogState(
    val entries: List<M2SourceListEntry>,
    val groups: M2SourceGroupCatalogData,
)

private data class SourceConfirmation(val message: ReaderConfirmationState, val submit: suspend () -> Unit)

@Composable
internal fun SourceManagementScreen(
    coordinator: SourceManagementCoordinator,
    onBack: () -> Unit,
    onOpenDocument: ((AuthorizedDocument) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val toast = remember { TransientToastState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val checks by coordinator.checkBatch.collectAsState()
    val imports by coordinator.importBatch.collectAsState()
    val pending by coordinator.unresolvedOperation.collectAsState()
    var route by remember { mutableStateOf<SourcePageRoute>(SourcePageRoute.Catalog) }
    var catalog by remember { mutableStateOf<SourceCatalogState?>(null) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var subscriptionRevision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var childConfirmationVisible by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<SourceConfirmation?>(null) }
    var confirmationGeneration by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var reportFilter by remember { mutableStateOf(SourceReportFilter.ALL) }

    fun feedback(key: String, message: String) {
        toast.present(key, message, confirmation == null && !childConfirmationVisible &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val readingCatalog = route == SourcePageRoute.Catalog || route == SourcePageRoute.Reports || route is SourcePageRoute.Diagnostic
    LaunchedEffect(readingCatalog, revision, imports.running, checks.working) {
        if (!readingCatalog) return@LaunchedEffect
        failed = false
        try {
            val entries = coordinator.list(M2SourceListParams(
                includeCheckReports = true, checkReportVersion = M2SourceListParamsCheckReportVersion.V2,
            )).sources
            val groups = coordinator.groups()
            catalog = SourceCatalogState(entries, groups)
            if (group != null && groups.groups.none { it.groupId == group }) {
                group = null
                selection = selection?.let { emptySet() }
            }
            selection = selection?.intersect(entries.map { it.sourceId }.toSet())
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            failed = true
            if (catalog != null) feedback("source.read", "暂时无法刷新书源，已保留原列表")
        }
    }

    fun mutate(submit: suspend () -> Unit) {
        if (busy || pending != null || imports.running) {
            feedback("source.busy", "请等待当前书源操作完成")
            return
        }
        busy = true
        scope.launch {
            try {
                submit()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: SourceOperationUnresolved) {
                // The coordinator retains the operation for the recovery action.
            } catch (_: Exception) {
                feedback("source.mutation", "书源操作未能全部完成，正在核对当前状态")
            } finally {
                busy = false
                revision++
            }
        }
    }

    fun confirm(title: String, detail: String, action: String, submit: suspend () -> Unit) {
        confirmation = SourceConfirmation(
            ReaderConfirmationState(title, detail, action, (++confirmationGeneration).toLong()), submit,
        )
    }

    fun returnToCatalog() {
        route = SourcePageRoute.Catalog
    }

    fun back() {
        val current = route
        when {
            current == SourcePageRoute.Catalog -> if (selection != null) selection = null else onBack()
            current is SourcePageRoute.Diagnostic && current.fromReports -> route = SourcePageRoute.Reports
            else -> returnToCatalog()
        }
    }

    fun catalogAction(action: SourceCatalogAction) {
        when (action) {
            SourceCatalogAction.Import -> route = SourcePageRoute.Import
            SourceCatalogAction.Reports -> route = SourcePageRoute.Reports
            SourceCatalogAction.Subscriptions -> route = SourcePageRoute.Subscriptions
            SourceCatalogAction.Batch -> selection = if (selection == null) emptySet() else null
            is SourceCatalogAction.Select -> selection = action.ids
            is SourceCatalogAction.Edit -> route = SourcePageRoute.Edit(action.source)
            is SourceCatalogAction.Enable -> mutate {
                var rejected = 0
                for (id in action.ids) {
                    if (coordinator.setEnabled(id, action.enabled) !is M2RulesMutationReceiptSourceUpdate) rejected++
                }
                if (rejected == 0) {
                    feedback("source.toggle", if (action.enabled) "已启用所选书源" else "已停用所选书源")
                } else {
                    feedback("source.toggle", "有 ${rejected} 个书源未能更新")
                }
            }
            is SourceCatalogAction.Check -> if (checks.working) {
                route = SourcePageRoute.Reports
                feedback("source.check.running", "上一批检测仍在进行")
            } else {
                route = SourcePageRoute.Check(action.ids)
            }
            is SourceCatalogAction.Report -> route = SourcePageRoute.Diagnostic(
                action.source.sourceId, action.source.name, fromReports = false,
            )
            is SourceCatalogAction.Login -> route = SourcePageRoute.Login(action.source)
            is SourceCatalogAction.ClearLogin -> confirm(
                "清除登录态？", "将清除该书源的 Cookie 和检测报告，其他书源不受影响。", "清除",
            ) {
                coordinator.clearLogin(action.source.sourceId)
                feedback("source.login.clear", "已清除登录态和该书源的检测报告")
            }
            is SourceCatalogAction.Delete -> confirm(
                "删除所选书源？", "将删除 ${action.ids.size} 个书源，已有书架、缓存和阅读进度会保留。", "删除",
            ) {
                if (coordinator.delete(action.ids.toList()) is M2RulesMutationReceiptSourceDelete) {
                    selection = selection?.minus(action.ids)
                    feedback("source.delete", "已删除所选书源")
                } else {
                    feedback("source.delete", "删除请求未获确认")
                }
            }
        }
    }

    BackHandler(enabled = route !is SourcePageRoute.Login, onBack = ::back)
    val notice = pending?.let {
        RecoveryNoticeState(it.operationId, "需要核对书源操作", "核对已提交的操作结果。",
            it.generation, RecoveryAction("核对", it.generation))
    }
    ReaderMessageOverlay(
        ownerForeground = confirmation == null && !childConfirmationVisible,
        toast = toast,
        recovery = notice,
        onVerify = { operationId, generation, actionGeneration ->
            if (!busy && pending == PendingSourceOperation(operationId, generation) && generation == actionGeneration) {
                busy = true
                val verifyingRoute = route
                scope.launch {
                    try {
                        val receipt = coordinator.verifyPending(operationId)
                        when (receipt) {
                            null -> feedback("source.verify", "暂未收到可核对的结果")
                            is M2RulesMutationReceiptRejected -> feedback("source.verify", "原操作未生效")
                            else -> {
                                if (route == verifyingRoute) when (receipt) {
                                    is M2RulesMutationReceiptSourceImport -> {
                                        if (verifyingRoute is SourcePageRoute.Edit &&
                                            receipt.data.sourceId == verifyingRoute.source.sourceId
                                        ) returnToCatalog()
                                    }
                                    is M2RulesMutationReceiptRuleSubPut,
                                    is M2RulesMutationReceiptRuleSubDelete,
                                    is M2RulesMutationReceiptRuleSubUpdateApplyV1 -> subscriptionRevision++
                                    else -> Unit
                                }
                                feedback("source.verify", "已确认原操作")
                            }
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        feedback("source.verify", "暂时无法核对结果")
                    } finally {
                        busy = false
                        revision++
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        val current = catalog
        val storedReports = current?.entries.orEmpty().mapNotNull { it.latestSourceReport() }
        val currentRows = checks.rows + storedReports.filter { old -> checks.rows.none { it.sourceId == old.sourceId } }
        when (val screen = route) {
            SourcePageRoute.Catalog -> SourcePage("书源管理", ::back) {
                when {
                    current != null -> SourceCatalogContent(current.entries, current.groups, query,
                        onQuery = { query = it }, groupId = group,
                        onGroup = { group = it; selection = selection?.let { emptySet() } },
                        selection = selection, onAction = ::catalogAction,
                    )
                    failed -> ReaderPageState(PageState.Failure("无法读取书源", revision.toLong()),
                        onRetry = { revision++ })
                    else -> ReaderPageState(PageState.Loading)
                }
            }
            SourcePageRoute.Import -> SourceImportScreen(coordinator, onOpenDocument, ::back) {
                childConfirmationVisible = it
            }
            is SourcePageRoute.Edit -> SourceEditScreen(screen.source, coordinator, ::back)
            SourcePageRoute.Subscriptions -> key(subscriptionRevision) {
                SourceSubscriptionsScreen(coordinator, ::back) { childConfirmationVisible = it }
            }
            is SourcePageRoute.Login -> SourceLoginScreen(screen.source, coordinator, ::back) {
                feedback("source.login.finish", "无法同步此次登录态")
            }
            is SourcePageRoute.Check -> SourceCheckOptions(screen.ids.size, onBack = ::back) { levels, keyword ->
                val targets = current?.entries.orEmpty().filter { it.sourceId in screen.ids }
                if (targets.isNotEmpty()) {
                    coordinator.beginCheck(targets, levels, keyword)
                    route = SourcePageRoute.Reports
                }
            }
            SourcePageRoute.Reports -> SourcePage("批量检测报告", ::back) {
                val progress = checks.takeIf { it.rows.isNotEmpty() }?.let {
                    val completed = it.rows.count { row -> row.result == SourceReportFilter.PASSED }
                    val rejected = it.rows.count { row -> row.result == SourceReportFilter.FAILED }
                    val running = it.rows.count { row -> row.running }
                    ReaderTaskProgressState(if (it.working) "正在检测书源" else "检测结果", completed,
                        rejected, running, it.rows.size - completed - rejected - running)
                }
                SourceReportContent(currentRows, reportFilter, { reportFilter = it }, onSelect = { report ->
                    route = SourcePageRoute.Diagnostic(report.sourceId, report.name, fromReports = true)
                }, progress = progress)
            }
            is SourcePageRoute.Diagnostic -> SourcePage("层级诊断", ::back) {
                SourceDiagnosticContent(
                    currentRows.firstOrNull { it.sourceId == screen.sourceId } ?: SourceReportRow(screen.sourceId, screen.name, null),
                )
            }
        }
    }
    ReaderConfirmation(confirmation?.message, ownerForeground = true, onDismiss = { confirmation = null },
        onConfirm = { generation ->
            confirmation?.takeIf { it.message.generation == generation }?.let {
                confirmation = null
                mutate(it.submit)
            }
        })
}
