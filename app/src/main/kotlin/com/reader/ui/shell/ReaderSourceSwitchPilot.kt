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
 * Experimental source-switch Pilot coverage set. Production keeps all six
 * events in Shadow; explicit opt-in exercises two semantic groups:
 * - overlay group (effectPolicy: "none"): 4 overlay/route events
 * - effect group (effectPolicy: "exactly-once"): 2 effectful events.
 *
 * Each effectful event emits exactly one next Core effect
 * (`source.switch.commit` / `source.switch.rollback`).
 */
internal val READER_UI_RUNTIME_SOURCE_SWITCH_PILOT_EVENTS: Set<String> = linkedSetOf(
    "source.switch.open",
    "source.switch.cancel",
    "source.switch.confirm",
    "source.switch.rollback",
    "reader.sourceSwitch.open",
    "reader.sourceSwitch.close"
)

/** Overlay/route cohort — no Core effects, just route/overlay projection. */
internal val READER_UI_RUNTIME_SOURCE_SWITCH_OVERLAY_PILOT_EVENTS: Set<String> = linkedSetOf(
    "source.switch.open",
    "source.switch.cancel",
    "reader.sourceSwitch.open",
    "reader.sourceSwitch.close"
)

/** Effect cohort — each event emits exactly one next Core effect. */
internal val READER_UI_RUNTIME_SOURCE_SWITCH_EFFECT_PILOT_EVENTS: Set<String> = linkedSetOf(
    "source.switch.confirm",
    "source.switch.rollback"
)

/**
 * Result of dispatching a source switch event through the opt-in Pilot. The
 * overlay cohort projects route/overlay changes and returns the production
 * state; the effect cohort hands the caller the Runtime transition (with the
 * single next Core effect) or fails closed.
 */
internal sealed interface ReaderSourceSwitchPilotDispatch {
    data object NotEnabled : ReaderSourceSwitchPilotDispatch
    /** Overlay cohort: route/overlay projected, native reducer must not run. */
    data class OverlayApplied(
        val productionState: ReaderUiState,
        val changed: Boolean
    ) : ReaderSourceSwitchPilotDispatch
    /** Effect cohort: single Core effect validated, executor must run it. */
    data class EffectApplied(val transition: ReaderUITransition) : ReaderSourceSwitchPilotDispatch
    data object FailedClosed : ReaderSourceSwitchPilotDispatch
}

/**
 * Runtime advance returned by [ReaderSourceSwitchRuntimeDriver.acceptSourceSwitchResult].
 * Source switch is a single-step transaction per event; there is no next effect.
 */
internal data class ReaderSourceSwitchRuntimeAdvance(
    val accepted: Boolean,
    val effects: List<ReaderUIEffect> = emptyList()
)

/**
 * Narrow seam the coordinator implements so the executor can accept Core
 * results and cancel an active source switch correlation without depending on
 * the concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal interface ReaderSourceSwitchRuntimeDriver {
    val sourceSwitchRuntimeState: ReaderUIState

    fun acceptSourceSwitchResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult = emptyMap(),
        error: String? = null
    ): ReaderSourceSwitchRuntimeAdvance

    fun cancelSourceSwitch(correlationId: String): Boolean
}

/**
 * Correlation-scoped domain data for the one active source switch transaction.
 * Like [ReaderImportDomainState], source switch has no measured-layout or
 * multi-stage dependency; each event is a single Core command.
 */
internal data class ReaderSourceSwitchDomainState(
    val activeCorrelationId: String? = null,
    val activeEvent: String? = null,
    val stage: String? = null,
    val error: String? = null,
    val sourceId: String? = null
)

internal class ReaderSourceSwitchDomainStore {
    private val mutableState = MutableStateFlow(ReaderSourceSwitchDomainState())
    val state: StateFlow<ReaderSourceSwitchDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(correlationId: String, event: String, sourceId: String? = null) {
        mutableState.value = ReaderSourceSwitchDomainState(
            activeCorrelationId = correlationId,
            activeEvent = event,
            stage = "started",
            sourceId = sourceId
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderSourceSwitchDomainState? =
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
        transform: (ReaderSourceSwitchDomainState) -> ReaderSourceSwitchDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderSourceSwitchCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderSourceSwitchCommandClient {
    fun begin(method: String, params: JSONObject): ReaderSourceSwitchCommandHandle
}

/** Production adapter. `ReaderCoreClient` is resolved lazily only when Pilot is enabled. */
internal class ReaderCoreSourceSwitchCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderSourceSwitchCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderSourceSwitchCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderSourceSwitchCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/**
 * The sole owner of source switch Core execution in Pilot mode. Runtime returns
 * exactly one next effect per source switch event; this executor is the only
 * place allowed to run it.
 */
internal open class ReaderSourceSwitchEffectExecutor(
    private val domainStore: ReaderSourceSwitchDomainStore,
    private val runtime: ReaderSourceSwitchRuntimeDriver,
    private val commandClientFactory: () -> ReaderSourceSwitchCommandClient = { ReaderCoreSourceSwitchCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderSourceSwitchCommandHandle>()
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
                handleFailure(effect, error.message ?: "SOURCE_SWITCH_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "SOURCE_SWITCH_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderSourceSwitchEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[correlationId] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderSourceSwitchEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result)
            } catch (error: Exception) {
                synchronized(this@ReaderSourceSwitchEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "SOURCE_SWITCH_CORE_FAILURE")
                }
            }
        }
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelSourceSwitch(correlationId)
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
        val advance = runtime.acceptSourceSwitchResult(effect.type, correlationId, projected)
        if (advance.accepted) domainStore.complete(correlationId)
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptSourceSwitchResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        requireNotNull(domainStore.current(correlationId)) { "stale source switch effect" }
        require(effect.type == "source.switch.commit" || effect.type == "source.switch.rollback") {
            "unsupported source switch Core stage: ${effect.type}"
        }
        return CoreCommand(effect.type, effect.jsonPayload.toCanonicalJSONObject())
    }
}
