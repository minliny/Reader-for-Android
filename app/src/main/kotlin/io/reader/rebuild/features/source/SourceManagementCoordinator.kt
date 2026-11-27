package io.reader.rebuild.features.source

import com.reader.core.protocol.EmptyParams
import com.reader.core.protocol.M2EmptyParams
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2RuleSubKeyParams
import com.reader.core.protocol.M2RuleSubPutParams
import com.reader.core.protocol.M2RuleSubUpdateApplyParams
import com.reader.core.protocol.M2RuleSubUpdateChangeKind
import com.reader.core.protocol.M2RuleSubUpdatePreviewData
import com.reader.core.protocol.M2RuleSubUpdatePreviewParams
import com.reader.core.protocol.M2RulesMutation
import com.reader.core.protocol.M2RulesMutationApplyParams
import com.reader.core.protocol.M2RulesMutationQueryParams
import com.reader.core.protocol.M2RulesMutationReceipt
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
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class SourceImportPreview(val input: M2SourceImportParams, val existing: M2SourceListEntry?)
internal class SourceOperationUnresolved(val original: M2RulesMutationApplyParams, cause: Throwable) :
    IllegalStateException("The original source operation requires verification", cause)

internal class SourceManagementCoordinator(
    private val core: CoreRuntimeActor,
    private val network: NetworkAndWebHost,
) {
    private val writes = Mutex()
    private var pending: M2RulesMutationApplyParams? = null

    suspend fun list(params: M2SourceListParams = M2SourceListParams()) = core.call(ReaderM2.sourceList, params)
    suspend fun groups() = core.call(ReaderM2.sourceGroupCatalog, M2SourceGroupCatalogParams())
    suspend fun groupMembers(groupId: String, enabledOnly: Boolean = false): List<M2SourceListEntry> {
        val members = groups().memberships.filter { it.groupId == groupId }.map { it.sourceId }.toSet()
        return list(M2SourceListParams(enabledOnly = enabledOnly)).sources.filter { it.sourceId in members }
    }

    suspend fun previewImport(document: String): List<SourceImportPreview> {
        require(document.length <= 8 * 1024 * 1024)
        val parsed = ReaderProtocolJson.parseToJsonElement(document)
        val records = when (parsed) {
            is JsonObject -> listOf(parsed)
            is JsonArray -> parsed.map { requireNotNull(it as? JsonObject) }
            else -> error("A source document must contain one or more rule objects")
        }
        require(records.size in 1..10_000)
        val candidates = records.map { input ->
            val url = requireNotNull(input.text("bookSourceUrl")?.takeIf(String::isNotBlank))
            requireNotNull(input.text("bookSourceName")?.takeIf(String::isNotBlank))
            M2SourceImportParams(sourceId = url, bookSource = input)
        }
        require(candidates.distinctBy { it.sourceId }.size == candidates.size)
        val current = list().sources.associateBy { it.sourceId }
        return candidates.map { SourceImportPreview(it, current[it.sourceId]) }
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
        val result = apply(M2RulesMutationSourceUpdate(params = M2SourceUpdateParams(sourceId, enabled)))
        if (result is M2RulesMutationReceiptSourceUpdate && !result.data.source.enabled) {
            network.revokeLogin(sourceId)
            if (!result.data.source.bookSource?.text("loginUrl").isNullOrBlank()) clearLogin(sourceId)
        }
        return result
    }

    suspend fun delete(sourceIds: List<String>): M2RulesMutationReceipt {
        require(sourceIds.isNotEmpty() && sourceIds.distinct().size == sourceIds.size)
        val result = apply(M2RulesMutationSourceDelete(params = M2SourceDeleteParams(sourceIds)))
        if (result is M2RulesMutationReceiptSourceDelete) {
            for (sourceId in sourceIds) network.revokeLogin(sourceId)
        }
        return result
    }

    suspend fun check(params: M2SourceCheckRunParams) = core.call(ReaderM2.sourceCheckRun, params)
    suspend fun reports(sourceId: String) = list(M2SourceListParams(
        includeCheckReports = true, checkReportVersion = M2SourceListParamsCheckReportVersion.V2, sourceId = sourceId,
    )).sources.singleOrNull()?.checkReports.orEmpty()

    suspend fun beginLogin(sourceId: String): io.reader.rebuild.host.network.SourceLoginSession {
        val entry = requireNotNull(list(M2SourceListParams(sourceId = sourceId)).sources.singleOrNull())
        require(entry.sourceId == sourceId && entry.enabled)
        val login = requireNotNull(entry.bookSource?.text("loginUrl"))
        return network.beginLogin(sourceId, java.net.URI(entry.baseUrl).resolve(login).toASCIIString())
    }

    suspend fun clearLogin(sourceId: String) = withContext(NonCancellable) {
        val cookieFailure = runCatching { network.clearLogin(sourceId) }.exceptionOrNull()
        try {
            val reports = core.call(ReaderM2.sourceCheckReportClear, M2SourceCheckReportClearParams(listOf(sourceId)))
            check(reports.sourceIds.all { it == sourceId })
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
    suspend fun deleteSubscription(id: Long) = apply(M2RulesMutationRuleSubDelete(params = M2RuleSubKeyParams(id)))
    suspend fun previewSubscription(id: Long, remoteDocument: String) =
        core.call(ReaderM2.ruleSubUpdatePreviewV1, M2RuleSubUpdatePreviewParams(id, remoteDocument))
    suspend fun fetchSubscriptionPreview(id: Long, url: String) =
        previewSubscription(id, network.fetch(M2HostHttpRequest(url = url, timeoutMs = 15_000)).body)

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
        query(original).receipt?.also { pending = null }
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
                    runCatching { query(original).receipt }.getOrNull() ?: throw SourceOperationUnresolved(original, failure)
                }
                pending = null
                result
            }
        }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
