package com.reader.ui.shell

import com.reader.api.ReaderCoreClient
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIJSONResult
import io.reader.ui.runtime.ReaderUIState
import io.reader.ui.runtime.ReaderUITransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Experimental replace-rule Pilot coverage set. Production keeps the three
 * lifecycle events in Shadow; explicit opt-in emits one next Core effect
 * (`replace.apply` / `replace.persist` / `replace.validate`).
 */
internal val READER_UI_RUNTIME_REPLACE_RULE_PILOT_EVENTS: Set<String> = linkedSetOf(
    "reader.replace.apply",
    "reader.replace.create",
    "reader.replace.validate"
)

/**
 * Result of dispatching a replace rule event through the opt-in Pilot. Mirrors
 * [ReaderImportPilotDispatch]: the coordinator either hands the caller the
 * Runtime transition (with the single next Core effect) or fails closed.
 */
internal sealed interface ReaderReplaceRulePilotDispatch {
    data object NotEnabled : ReaderReplaceRulePilotDispatch
    data class Applied(val transition: ReaderUITransition) : ReaderReplaceRulePilotDispatch
    data object FailedClosed : ReaderReplaceRulePilotDispatch
}

/**
 * Runtime advance returned by [ReaderReplaceRuleRuntimeDriver.acceptReplaceRuleResult].
 * Replace rule is a single-step transaction per event; there is no next effect.
 */
internal data class ReaderReplaceRuleRuntimeAdvance(
    val accepted: Boolean,
    val effects: List<ReaderUIEffect> = emptyList()
)

/**
 * Narrow seam the coordinator implements so the executor can accept Core
 * results and cancel an active replace rule correlation without depending on
 * the concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal interface ReaderReplaceRuleRuntimeDriver {
    val replaceRuleRuntimeState: ReaderUIState

    fun acceptReplaceRuleResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult = emptyMap(),
        error: String? = null
    ): ReaderReplaceRuleRuntimeAdvance

    fun cancelReplaceRule(correlationId: String): Boolean
}

/**
 * Correlation-scoped domain data for the one active replace rule transaction.
 * Like [ReaderImportDomainState], replace rule has no measured-layout or
 * multi-stage dependency; each event is a single Core command.
 */
internal data class ReaderReplaceRuleDomainState(
    val activeCorrelationId: String? = null,
    val activeEvent: String? = null,
    val stage: String? = null,
    val error: String? = null,
    val ruleId: String? = null
)

internal class ReaderReplaceRuleDomainStore {
    private val mutableState = MutableStateFlow(ReaderReplaceRuleDomainState())
    val state: StateFlow<ReaderReplaceRuleDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(correlationId: String, event: String, ruleId: String? = null) {
        mutableState.value = ReaderReplaceRuleDomainState(
            activeCorrelationId = correlationId,
            activeEvent = event,
            stage = "started",
            ruleId = ruleId
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderReplaceRuleDomainState? =
        mutableState.value.takeIf { it.activeCorrelationId == correlationId }

    @Synchronized
    fun complete(correlationId: String): Boolean = updateCurrent(correlationId) {
        it.copy(activeCorrelationId = null, stage = "completed", error = null)
    }

    @Synchronized
    fun fail(correlationId: String, message: String): Boolean = updateCurrent(correlationId) {
        it.copy(activeCorrelationId = null, stage = "failed", error = message)
    }

    @Synchronized
    fun cancel(correlationId: String): Boolean = updateCurrent(correlationId) {
        it.copy(activeCorrelationId = null, stage = "cancelled", error = null)
    }

    private fun updateCurrent(
        correlationId: String,
        transform: (ReaderReplaceRuleDomainState) -> ReaderReplaceRuleDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderReplaceRuleCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderReplaceRuleCommandClient {
    fun begin(method: String, params: JSONObject): ReaderReplaceRuleCommandHandle
}

/** Production adapter. `ReaderCoreClient` is resolved lazily only when Pilot is enabled. */
internal class ReaderCoreReplaceRuleCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderReplaceRuleCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderReplaceRuleCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderReplaceRuleCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/**
 * The sole owner of replace rule Core execution in Pilot mode. Runtime returns
 * exactly one next effect per replace rule event; this executor is the only
 * place allowed to run it.
 */
internal open class ReaderReplaceRuleEffectExecutor(
    private val domainStore: ReaderReplaceRuleDomainStore,
    private val runtime: ReaderReplaceRuleRuntimeDriver,
    private val commandClientFactory: () -> ReaderReplaceRuleCommandClient = { ReaderCoreReplaceRuleCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderReplaceRuleCommandHandle>()
    private val issuedStages = mutableSetOf<String>()

    @Synchronized
    open fun start(event: String, firstEffect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = firstEffect.correlationId ?: return
        domainStore.begin(correlationId, event)
        execute(firstEffect, scope)
    }

    @Synchronized
    open fun execute(effect: ReaderUIEffect, scope: CoroutineScope) {
        val correlationId = effect.correlationId ?: return
        if (effect.kind != ReaderUIEffectKind.CORE || !domainStore.isCurrent(correlationId)) return
        val stageKey = "$correlationId:${effect.type}"
        if (!issuedStages.add(stageKey)) return

        scope.launch {
            val command = try {
                buildCommand(effect)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "REPLACE_RULE_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "REPLACE_RULE_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderReplaceRuleEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[correlationId] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderReplaceRuleEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result)
            } catch (error: Exception) {
                synchronized(this@ReaderReplaceRuleEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "REPLACE_RULE_CORE_FAILURE")
                }
            }
        }
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelReplaceRule(correlationId)
        val handle = handles.remove(correlationId)
        val coreCancelled = handle?.cancel() ?: false
        return invalidated || runtimeCancelled || coreCancelled
    }

    @Synchronized
    open fun cancelActive(): Boolean =
        domainStore.state.value.activeCorrelationId?.let(::cancel) ?: false

    private fun handleSuccess(effect: ReaderUIEffect, result: JSONObject) {
        val correlationId = requireNotNull(effect.correlationId)
        val event = requireNotNull(domainStore.current(correlationId)?.activeEvent)
        val projected = projectAndValidateReaderUiResult(event, effect.type, result)
        val advance = runtime.acceptReplaceRuleResult(effect.type, correlationId, projected)
        if (advance.accepted) domainStore.complete(correlationId)
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptReplaceRuleResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        requireNotNull(domainStore.current(correlationId)) { "stale replace rule effect" }
        require(effect.type in setOf("replace.apply", "replace.persist", "replace.validate")) {
            "unsupported replace rule Core stage: ${effect.type}"
        }
        return CoreCommand(effect.type, effect.jsonPayload.toCanonicalJSONObject())
    }
}
