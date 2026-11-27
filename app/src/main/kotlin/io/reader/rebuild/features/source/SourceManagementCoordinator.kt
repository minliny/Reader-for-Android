package io.reader.rebuild.features.source

import com.reader.core.protocol.EmptyParams
import com.reader.core.protocol.M2EmptyParams
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2ReadingOperationBinding
import com.reader.core.protocol.M2RuleSubData
import com.reader.core.protocol.M2RuleSubKeyParams
import com.reader.core.protocol.M2RuleSubPutParams
import com.reader.core.protocol.M2RuleSubUpdateApplyParams
import com.reader.core.protocol.M2RuleSubUpdateChangeKind
import com.reader.core.protocol.M2RuleSubUpdatePreviewData
import com.reader.core.protocol.M2RuleSubUpdatePreviewExpected
import com.reader.core.protocol.M2RuleSubUpdatePreviewParams
import com.reader.core.protocol.M2RulesMutation
import com.reader.core.protocol.M2RulesMutationApplyParams
import com.reader.core.protocol.M2RulesMutationQueryParams
import com.reader.core.protocol.M2RulesMutationReceipt
import com.reader.core.protocol.M2RulesMutationReceiptSourceImport
import com.reader.core.protocol.M2RulesMutationReceiptSourceDelete
import com.reader.core.protocol.M2RulesMutationReceiptSourceUpdate
import com.reader.core.protocol.M2RulesMutationRuleSubDelete
import com.reader.core.protocol.M2RulesMutationRuleSubPut
import com.reader.core.protocol.M2RulesMutationRuleSubUpdateApplyV1
import com.reader.core.protocol.M2RulesMutationSourceDelete
import com.reader.core.protocol.M2RulesMutationSourceImport
import com.reader.core.protocol.M2RulesMutationSourceUpdate
import com.reader.core.protocol.M2SourceCheckReportClearParams
import com.reader.core.protocol.M2SourceCheckRunParams
import com.reader.core.protocol.M2SourceCheckRunParamsLevelsItem
import com.reader.core.protocol.M2SourceCheckRunParamsMode
import com.reader.core.protocol.M2SourceCheckRunParamsReportVersion
import com.reader.core.protocol.M2SourceDeleteParams
import com.reader.core.protocol.M2SourceGroupCatalogParams
import com.reader.core.protocol.M2SourceImportParams
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.M2SourceListParamsCheckReportVersion
import com.reader.core.protocol.M2SourceUpdateParams
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderProtocolJson
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.host.network.NetworkAndWebHost
import io.reader.rebuild.host.network.SourceLoginSession
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal data class SourceImportPreview(val input: M2SourceImportParams, val existing: M2SourceListEntry?)
internal data class PendingSourceOperation(val operationId: String, val generation: Long)
internal class SourceOperationUnresolved(val original: M2RulesMutationApplyParams, cause: Throwable) :
    IllegalStateException("The original source operation requires verification", cause)

internal class SourceManagementCoordinator(
    private val core: CoreRuntimeActor,
    private val network: NetworkAndWebHost,
) {
    private val writes = Mutex()
    private var pending: M2RulesMutationApplyParams? = null
    private val unresolved = MutableStateFlow<PendingSourceOperation?>(null)
    val unresolvedOperation = unresolved.asStateFlow()

    private val tasks = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imports = MutableStateFlow(SourceImportBatch())
    val importBatch = imports.asStateFlow()
    private val checks = MutableStateFlow(SourceCheckBatch())
    val checkBatch = checks.asStateFlow()

    fun close() = tasks.cancel()

    suspend fun list(params: M2SourceListParams = M2SourceListParams()) = core.call(ReaderM2.sourceList, params)
    suspend fun groups() = core.call(ReaderM2.sourceGroupCatalog, M2SourceGroupCatalogParams())
    suspend fun groupMembers(groupId: String, enabledOnly: Boolean = false): List<M2SourceListEntry> {
        val members = groups().memberships.filter { it.groupId == groupId }.map { it.sourceId }.toSet()
        return list(M2SourceListParams(enabledOnly = enabledOnly)).sources.filter { it.sourceId in members }
    }

    suspend fun previewImportUrl(url: String): List<SourceImportPreview> =
        previewImport(network.fetch(M2HostHttpRequest(url = url, timeoutMs = 15_000)).body)

    suspend fun previewImport(document: String): List<SourceImportPreview> = withContext(Dispatchers.Default) {
        require(document.length <= 8 * 1024 * 1024)
        val parsed = ReaderProtocolJson.parseToJsonElement(document)
        val records = when (parsed) {
            is JsonObject -> listOf(parsed)
            is JsonArray -> parsed.map { requireNotNull(it as? JsonObject) }
            else -> error("A source document must contain one or more rule objects")
        }
        require(records.size in 1..10_000)
        val candidates = records.map { input ->
            val url = requireNotNull(input.sourceText("bookSourceUrl")?.takeIf(String::isNotBlank))
            requireNotNull(input.sourceText("bookSourceName")?.takeIf(String::isNotBlank))
            M2SourceImportParams(sourceId = url, bookSource = input)
        }
        require(candidates.distinctBy { it.sourceId }.size == candidates.size)
        val current = list().sources.associateBy { it.sourceId }
        candidates.map { SourceImportPreview(it, current[it.sourceId]) }
    }

    fun beginImport(previews: List<SourceImportPreview>): Long? {
        require(previews.isNotEmpty() && previews.all { it.existing == null })
        val previous = imports.value
        if (previous.running || previous.pendingOperationId != null || unresolved.value != null) return null
        val id = previous.id + 1
        if (!imports.compareAndSet(previous, SourceImportBatch(id = id, total = previews.size, running = true))) return null
        tasks.launch {
            try {
                for (preview in previews) {
                    val applied = try {
                        importSource(preview) is M2RulesMutationReceiptSourceImport
                    } catch (failure: SourceOperationUnresolved) {
                        imports.update { it.copy(pendingOperationId = failure.original.operationId) }
                        break
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        false
                    }
                    recordImport(applied)
                }
            } finally {
                imports.update { it.copy(running = false) }
            }
        }
        return id
    }

    private fun recordImport(applied: Boolean) {
        imports.update {
            it.copy(
                completed = it.completed + if (applied) 1 else 0,
                failed = it.failed + if (applied) 0 else 1,
                pendingOperationId = null,
            )
        }
    }

    suspend fun importSource(preview: SourceImportPreview): M2RulesMutationReceipt {
        require(preview.existing == null && preview.input.expectedVersion == null)
        val source = requireNotNull(preview.input.sourceId)
        return apply(M2RulesMutationSourceImport(params = preview.input)) {
            check(list(M2SourceListParams(sourceId = source)).sources.isEmpty()) { "Import preview is stale" }
        }
    }

    suspend fun edit(input: M2SourceImportParams, expectedVersion: String): M2RulesMutationReceipt {
        require(!input.sourceId.isNullOrBlank() && expectedVersion.matches(Regex("[a-f0-9]{64}")))
        require(input.expectedVersion == null || input.expectedVersion == expectedVersion)
        check("source.import.conditional.v1" in core.call(ReaderM1.coreInfo, EmptyParams()).capabilities)
        return apply(M2RulesMutationSourceImport(params = input.copy(expectedVersion = expectedVersion)))
    }

    suspend fun setEnabled(sourceId: String, enabled: Boolean): M2RulesMutationReceipt {
        return apply(M2RulesMutationSourceUpdate(params = M2SourceUpdateParams(sourceId, enabled)))
    }

    suspend fun delete(sourceIds: List<String>): M2RulesMutationReceipt {
        require(sourceIds.isNotEmpty() && sourceIds.distinct().size == sourceIds.size)
        return apply(M2RulesMutationSourceDelete(params = M2SourceDeleteParams(sourceIds)))
    }

    suspend fun check(params: M2SourceCheckRunParams) = core.call(ReaderM2.sourceCheckRun, params)

    fun beginCheck(
        entries: List<M2SourceListEntry>,
        levels: List<M2SourceCheckRunParamsLevelsItem>,
        keyword: String?,
    ): Boolean {
        require(entries.isNotEmpty() && levels.isNotEmpty())
        val previous = checks.value
        if (previous.working || !checks.compareAndSet(previous, SourceCheckBatch(
                entries.map { SourceReportRow(it.sourceId, it.name, null) }, working = true,
            ))) return false
        tasks.launch {
            try {
                for (entry in entries) {
                    if (checks.value.rows.none { it.sourceId == entry.sourceId }) continue
                    updateCheck(entry.sourceId) { it.copy(running = true) }
                    val report = try {
                        check(M2SourceCheckRunParams(
                            sourceIds = listOf(entry.sourceId),
                            reportVersion = M2SourceCheckRunParamsReportVersion.V2,
                            mode = M2SourceCheckRunParamsMode.SEARCH,
                            keyword = keyword, keywordOverride = keyword?.let { true },
                            timeoutMs = 15_000, levels = levels,
                        )).results.single { it.sourceId == entry.sourceId }.report
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        null
                    }
                    updateCheck(entry.sourceId) { it.copy(report = report, running = false, error = report == null) }
                }
            } finally {
                checks.update { batch -> batch.copy(working = false, rows = batch.rows.map { it.copy(running = false) }) }
            }
        }
        return true
    }

    private fun updateCheck(sourceId: String, update: (SourceReportRow) -> SourceReportRow) {
        checks.update { batch -> batch.copy(rows = batch.rows.map { if (it.sourceId == sourceId) update(it) else it }) }
    }

    private fun forgetReports(sourceId: String) {
        checks.update { batch -> batch.copy(rows = batch.rows.filterNot { it.sourceId == sourceId }) }
    }

    suspend fun reports(sourceId: String) = list(M2SourceListParams(
        includeCheckReports = true, checkReportVersion = M2SourceListParamsCheckReportVersion.V2, sourceId = sourceId,
    )).sources.singleOrNull()?.checkReports.orEmpty()

    suspend fun beginLogin(sourceId: String): SourceLoginSession {
        val entry = requireNotNull(list(M2SourceListParams(sourceId = sourceId)).sources.singleOrNull())
        require(entry.sourceId == sourceId && entry.enabled)
        val login = requireNotNull(entry.bookSource?.sourceText("loginUrl"))
        return network.beginLogin(sourceId, java.net.URI(entry.baseUrl).resolve(login).toASCIIString())
    }

    suspend fun finishLogin(session: SourceLoginSession) = network.finishLogin(session)

    suspend fun dismissLogin(session: SourceLoginSession) = network.revokeLogin(session)

    suspend fun clearLogin(sourceId: String) = withContext(NonCancellable) {
        val cookieFailure = runCatching { network.clearLogin(sourceId) }.exceptionOrNull()
        try {
            val reports = core.call(ReaderM2.sourceCheckReportClear, M2SourceCheckReportClearParams(listOf(sourceId)))
            check(reports.sourceIds.all { it == sourceId })
            forgetReports(sourceId)
            if (cookieFailure != null) throw cookieFailure
            reports
        } catch (error: Throwable) {
            if (cookieFailure != null && cookieFailure !== error) error.addSuppressed(cookieFailure)
            throw error
        }
    }

    suspend fun subscriptions() = core.call(ReaderM2.ruleSubList, M2EmptyParams())
    suspend fun saveSubscription(params: M2RuleSubPutParams): M2RulesMutationReceipt {
        require(params.type == 3L)
        return apply(M2RulesMutationRuleSubPut(params = params))
    }
    suspend fun saveSubscription(name: String, url: String, existing: M2RuleSubData?): M2RulesMutationReceipt {
        require(existing == null || existing.type == 3L)
        val id = existing?.id ?: Random.nextLong(1L, Long.MAX_VALUE / 1024)
        val params = M2RuleSubPutParams(id, name, url, 3L, existing?.customOrder, existing?.autoUpdate, existing?.update)
        return apply(M2RulesMutationRuleSubPut(params = params)) {
            check(core.call(ReaderM2.ruleSubGet, M2RuleSubKeyParams(id)).sub == existing) {
                "The subscription changed after editing began"
            }
        }
    }

    suspend fun deleteSubscription(id: Long) = apply(M2RulesMutationRuleSubDelete(params = M2RuleSubKeyParams(id)))
    suspend fun previewSubscription(id: Long, remoteDocument: String) =
        core.call(ReaderM2.ruleSubUpdatePreviewV1, M2RuleSubUpdatePreviewParams(id, remoteDocument))
    suspend fun fetchSubscriptionPreview(id: Long, url: String) =
        previewSubscription(id, network.fetch(M2HostHttpRequest(url = url, timeoutMs = 15_000)).body)

    suspend fun fetchSubscriptionPreview(sub: M2RuleSubData): M2RuleSubUpdatePreviewData {
        require(sub.type == 3L)
        val head = core.call(ReaderM2.rulesMutationQuery, M2RulesMutationQueryParams()).head
        val document = network.fetch(M2HostHttpRequest(url = sub.url, timeoutMs = 30_000)).body
        val expected = M2RuleSubUpdatePreviewExpected(
            M2RuleSubPutParams(sub.id, sub.name, sub.url, sub.type, sub.customOrder, sub.autoUpdate, sub.update),
            head?.let { M2ReadingOperationBinding(it.operationId, it.sequence, it.payloadFingerprint) },
        )
        return core.call(ReaderM2.ruleSubUpdatePreviewV1, M2RuleSubUpdatePreviewParams(sub.id, document, expected))
    }

    suspend fun confirmSubscription(preview: M2RuleSubUpdatePreviewData, sourceIds: List<String>): M2RulesMutationReceipt {
        val selectable = preview.changes.filter {
            it.kind in setOf(M2RuleSubUpdateChangeKind.ADDED, M2RuleSubUpdateChangeKind.UPDATE)
        }.map { it.sourceId }.toSet()
        require(sourceIds.isNotEmpty() && sourceIds.distinct().size == sourceIds.size && selectable.containsAll(sourceIds))
        return apply(M2RulesMutationRuleSubUpdateApplyV1(params = M2RuleSubUpdateApplyParams(preview.planId, sourceIds)))
    }

    suspend fun query(original: M2RulesMutationApplyParams) = core.call(ReaderM2.rulesMutationQuery,
        M2RulesMutationQueryParams(operationId = original.operationId, expected = original))

    suspend fun verifyPending(expectedOperationId: String): M2RulesMutationReceipt? = writes.withLock {
        val original = pending?.takeIf { it.operationId == expectedOperationId } ?: return@withLock null
        val receipt = query(original).receipt ?: return@withLock null
        withContext(NonCancellable) {
            pending = null
            unresolved.value = null
            if (imports.value.pendingOperationId == expectedOperationId) {
                recordImport(receipt is M2RulesMutationReceiptSourceImport)
            }
            settleSourceChange(original.mutation, receipt)
        }
        receipt
    }

    private suspend fun apply(mutation: M2RulesMutation, admit: suspend () -> Unit = {}): M2RulesMutationReceipt =
        writes.withLock {
            check(pending == null) { "Verify the previous source operation first" }
            val head = core.call(ReaderM2.rulesMutationQuery, M2RulesMutationQueryParams()).head
            admit()
            val sequence = (head?.sequence ?: 0L) + 1L
            val original = M2RulesMutationApplyParams("${UUID.randomUUID()}/$sequence", sequence, mutation)
            withContext(NonCancellable) {
                pending = original
                val result = try {
                    core.call(ReaderM2.rulesMutationApply, original)
                } catch (failure: Exception) {
                    runCatching { query(original).receipt }.getOrNull() ?: run {
                        unresolved.value = PendingSourceOperation(original.operationId, original.sequence)
                        throw SourceOperationUnresolved(original, failure)
                    }
                }
                pending = null
                settleSourceChange(mutation, result)
                result
            }
        }

    private suspend fun settleSourceChange(mutation: M2RulesMutation, result: M2RulesMutationReceipt) {
        when {
            result is M2RulesMutationReceiptSourceUpdate && !result.data.source.enabled -> {
                val source = result.data.source
                network.revokeLogin(source.sourceId)
                if (!source.bookSource?.sourceText("loginUrl").isNullOrBlank()) clearLogin(source.sourceId)
            }
            result is M2RulesMutationReceiptSourceDelete && mutation is M2RulesMutationSourceDelete -> {
                for (sourceId in mutation.params.sourceIds) {
                    network.revokeLogin(sourceId)
                    forgetReports(sourceId)
                }
            }
        }
    }

}
