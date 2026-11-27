package io.reader.rebuild.goldens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reader.core.protocol.M2RuleSubUpdateChange
import com.reader.core.protocol.M2RuleSubUpdateChangeKind
import com.reader.core.protocol.M2RuleSubUpdatePreviewData
import com.reader.core.protocol.M2SourceCheckReport
import com.reader.core.protocol.M2SourceCheckReportMode
import com.reader.core.protocol.M2SourceCheckReportStepsItem
import com.reader.core.protocol.M2SourceCheckReportStepsItemLevel
import com.reader.core.protocol.M2SourceCheckReportStepsItemStatus
import com.reader.core.protocol.M2SourceCheckReportVersion
import com.reader.core.protocol.M2SourceGroupCatalogData
import com.reader.core.protocol.M2SourceGroupMembership
import com.reader.core.protocol.M2SourceGroupRecord
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.theme.ReaderDesignMetrics
import io.reader.rebuild.design.LocalReaderColors
import io.reader.rebuild.design.readerTextStyle
import io.reader.rebuild.features.source.SourceBrowserContent
import io.reader.rebuild.features.source.SourceBrowserState
import io.reader.rebuild.features.source.SourceCatalogContent
import io.reader.rebuild.features.source.SourceDiagnosticContent
import io.reader.rebuild.features.source.SourcePage
import io.reader.rebuild.features.source.SourceReportContent
import io.reader.rebuild.features.source.SourceReportRow
import io.reader.rebuild.features.source.SourceSubscriptionDiffContent

private val pine = M2SourceListEntry(sourceId = "https://pine.example", name = "青松小说",
    baseUrl = "https://pine.example", enabled = true, enabledExplore = false)
private val ink = M2SourceListEntry(sourceId = "https://ink.example", name = "墨纸文库",
    baseUrl = "https://ink.example", enabled = false, enabledExplore = false)
private val catalogs = M2SourceGroupCatalogData(revision = 1, groups = listOf(
    M2SourceGroupRecord("local", "本地导入"), M2SourceGroupRecord("checked", "已检测")),
    memberships = listOf(M2SourceGroupMembership(pine.sourceId, "checked", false),
        M2SourceGroupMembership(ink.sourceId, "local", false)))

private val checkReport = M2SourceCheckReport(
    version = M2SourceCheckReportVersion.V2,
    mode = M2SourceCheckReportMode.SEARCH,
    traceId = "golden-check-trace",
    inputDigest = "golden-check-source",
    steps = M2SourceCheckReportStepsItemLevel.entries.take(5).mapIndexed { index, level ->
        val failed = index == 4
        M2SourceCheckReportStepsItem(level = level, requested = true, executed = true,
            status = if (failed) M2SourceCheckReportStepsItemStatus.FAIL else M2SourceCheckReportStepsItemStatus.PASS,
            durationMs = (index + 1) * 12L, reason = if (failed) "未匹配到正文选择器" else null)
    },
)
private val reportRow = SourceReportRow(pine.sourceId, pine.name, checkReport)
private val subscription = M2RuleSubUpdatePreviewData("golden-diff", 4L, "golden-digest", 1_800_000_000_000L, listOf(
    M2RuleSubUpdateChange(pine.sourceId, pine.name, M2RuleSubUpdateChangeKind.UPDATE),
    M2RuleSubUpdateChange(ink.sourceId, ink.name, M2RuleSubUpdateChangeKind.LOCAL_ONLY),
    M2RuleSubUpdateChange("https://leaf.example", "一叶书房", M2RuleSubUpdateChangeKind.ADDED),
))

internal fun sourceGoldenScenes() = listOf(
    AndroidGoldenScene("m2-source-management", "书源 v9 派生分组、搜索与启停", "青松小说／墨纸文库；本地导入／已检测") {
        SourcePage("书源管理", {}) { SourceCatalogContent(listOf(pine, ink), catalogs, "") }
    },
    AndroidGoldenScene("m2-source-report", "批量分层检测报告列表", "青松 L1–L4 成功、L5 失败；墨纸未执行") {
        SourcePage("检测报告", {}) {
            SourceReportContent(listOf(reportRow, SourceReportRow(ink.sourceId, ink.name, null)))
        }
    },
    AndroidGoldenScene("m2-source-report-detail", "原报告的逐源分层诊断", "青松小说 L1–L5") {
        SourcePage("分层检测诊断", {}) { SourceDiagnosticContent(reportRow) }
    },
    AndroidGoldenScene("m2-subscription-diff", "规则订阅差异，原样显示只在本地的条目", "更新／仅本地／新增") {
        SourcePage("核对规则订阅更新", {}) {
            SourceSubscriptionDiffContent(subscription, setOf(pine.sourceId, "https://leaf.example"))
        }
    },
    AndroidGoldenScene("m2-source-login", "浏览器式登录，浏览器顶底栏", "离线登录文档",
        difference = "生产 Android 浏览器顶底栏；内容槽为稳定离线正文。真实 WebView 与 Cookie 单独运行 instrumentation。") {
        SourceBrowserContent("青松小说登录", SourceBrowserState("https://pine.example/login", secure = true,
            canGoBack = true)) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text("受控书源登录页面", color = LocalReaderColors.current.primaryText,
                    style = readerTextStyle(ReaderDesignMetrics.pageTitle))
            }
        }
    },
)
