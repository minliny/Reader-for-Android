package io.reader.rebuild.features.source

import com.reader.core.protocol.M2SourceCheckReport
import com.reader.core.protocol.M2SourceCheckReportStepsItemStatus as Status
import com.reader.core.protocol.M2SourceListEntry

internal enum class SourceReportFilter(val label: String) {
    ALL("全部"),
    PASSED("通过"),
    FAILED("失败"),
    NOT_EXECUTED("未执行"),
}

internal data class SourceReportRow(
    val sourceId: String,
    val name: String,
    val report: M2SourceCheckReport?,
    val error: Boolean = false,
    val running: Boolean = false,
    val stale: Boolean = false,
) {
    val result: SourceReportFilter
        get() = when {
            stale -> SourceReportFilter.NOT_EXECUTED
            error || report?.steps?.any { it.status == Status.FAIL } == true -> SourceReportFilter.FAILED
            report?.steps?.any {
                it.requested && it.status in setOf(Status.BLOCKED, Status.CANCELLED, Status.NOT_SUPPORTED)
            } == true -> SourceReportFilter.NOT_EXECUTED
            report?.steps?.any { it.status == Status.PASS } == true -> SourceReportFilter.PASSED
            else -> SourceReportFilter.NOT_EXECUTED
        }
}

internal data class SourceCheckBatch(val rows: List<SourceReportRow> = emptyList(), val working: Boolean = false)

internal data class SourceImportBatch(
    val id: Long = 0,
    val total: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val running: Boolean = false,
    val pendingOperationId: String? = null,
)

internal fun M2SourceListEntry.latestSourceReport(): SourceReportRow? =
    checkReports?.maxByOrNull { it.checkedAtMs }?.let {
        SourceReportRow(sourceId, name, it.report, stale = it.stale)
    }
