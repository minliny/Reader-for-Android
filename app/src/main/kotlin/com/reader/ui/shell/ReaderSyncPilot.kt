package com.reader.ui.shell

import com.reader.api.ReaderCoreClient
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIJSONPayload
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
 * Seven covered Sync events retained for default-Shadow observation and for
 * isolated opt-in seam tests. They are not a production Pilot cohort: Android
 * has no production caller/executor wiring for this path.
 */
internal val READER_UI_RUNTIME_SYNC_PILOT_EVENTS: Set<String> = linkedSetOf(
    "sync.run",
    "webdav.config.test",
    "sync.start",
    "sync.progress",
    "sync.complete",
    "sync.conflict",
    "sync.resolve"
)

/** Expected Core effect sequence per sync event, mirroring runtime-actions.json. */
internal val READER_UI_RUNTIME_SYNC_PILOT_CORE_SEQUENCE: Map<String, List<String>> = mapOf(
    "sync.run" to listOf("sync.snapshot", "sync.push"),
    "webdav.config.test" to listOf("sync.push"),
    "sync.start" to listOf("sync.snapshot"),
    "sync.progress" to listOf("sync.push"),
    "sync.complete" to listOf("sync.push"),
    "sync.conflict" to listOf("sync.conflict.detect"),
    "sync.resolve" to listOf("sync.conflict.resolve")
)

/** Optional Host request per sync event, mirroring runtime-actions.json. */
internal val READER_UI_RUNTIME_SYNC_PILOT_HOST_REQUEST: Map<String, String> = mapOf(
    "webdav.config.test" to "http.execute"
)

/** Events carrying a stale-result guard; a superseded correlation must not advance. */
internal val READER_UI_RUNTIME_SYNC_PILOT_STALE_GUARD_EVENTS: Set<String> = setOf(
    "sync.start",
    "sync.conflict"
)

/** Events carrying a rollback path; cancel clears the active correlation. */
internal val READER_UI_RUNTIME_SYNC_PILOT_ROLLBACK_EVENTS: Set<String> = setOf(
    "sync.resolve"
)

/** Runtime mode for the dormant opt-in Sync seam; production remains Shadow. */
internal enum class ReaderSyncPilotMode {
    SHADOW,
    PILOT
}

/**
 * Result of dispatching a sync event through the opt-in Pilot. Mirrors
 * [ReaderRssPilotDispatch]: the coordinator either hands the caller the
 * Runtime transition (with the emitted Core/Host effects) or fails closed.
 */
internal sealed interface ReaderSyncPilotDispatch {
    data object NotEnabled : ReaderSyncPilotDispatch
    data class Dispatched(val transition: ReaderUITransition) : ReaderSyncPilotDispatch
    data object FailedClosed : ReaderSyncPilotDispatch
}

/**
 * Runtime advance returned by [ReaderSyncPilotRuntimeDriver.acceptSyncResult].
 * Sync is a single-step transaction per event (the Core sequence is emitted
 * atomically); there is no next effect after the executor completes.
 */
internal data class ReaderSyncRuntimeAdvance(
    val accepted: Boolean,
    val completed: Boolean = false,
    val effects: List<ReaderUIEffect> = emptyList()
)

/**
 * Narrow seam the coordinator implements so the executor can accept Core
 * results and cancel an active sync correlation without depending on the
 * concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal interface ReaderSyncPilotRuntimeDriver {
    val syncRuntimeState: ReaderUIState

    fun acceptSyncResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult = emptyMap(),
        error: String? = null
    ): ReaderSyncRuntimeAdvance

    fun cancelSync(correlationId: String): Boolean
}

/**
 * Correlation-scoped domain data for the one active sync transaction. Like
 * [ReaderRssDomainState], sync has no measured-layout or multi-stage
 * dependency; each event emits its Core sequence atomically.
 */
internal data class ReaderSyncDomainState(
    val activeCorrelationId: String? = null,
    val activeEvent: String? = null,
    val stage: String? = null,
    val error: String? = null
)

internal class ReaderSyncDomainStore {
    private val mutableState = MutableStateFlow(ReaderSyncDomainState())
    val state: StateFlow<ReaderSyncDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(correlationId: String, event: String) {
        mutableState.value = ReaderSyncDomainState(
            activeCorrelationId = correlationId,
            activeEvent = event,
            stage = "started"
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderSyncDomainState? =
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
        transform: (ReaderSyncDomainState) -> ReaderSyncDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderSyncCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderSyncCommandClient {
    fun begin(method: String, params: JSONObject): ReaderSyncCommandHandle
}

/**
 * Adapter reserved for explicit seam tests/future integration. Production
 * does not currently construct this adapter or [ReaderSyncEffectExecutor].
 */
internal class ReaderCoreSyncCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderSyncCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderSyncCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderSyncCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/**
 * Executor for the explicit opt-in seam. It is deliberately not wired into
 * production dispatch and therefore does not establish runtime authority.
 */
internal open class ReaderSyncEffectExecutor(
    private val domainStore: ReaderSyncDomainStore,
    private val runtime: ReaderSyncPilotRuntimeDriver,
    private val commandClientFactory: () -> ReaderSyncCommandClient = { ReaderCoreSyncCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderSyncCommandHandle>()
    private val issuedStages = mutableSetOf<String>()

    @Synchronized
    open fun start(event: String, effects: List<ReaderUIEffect>, scope: CoroutineScope) {
        val correlationId = effects.firstOrNull()?.correlationId ?: return
        domainStore.begin(correlationId, event)
        effects.forEach { execute(it, scope) }
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
                handleFailure(effect, error.message ?: "SYNC_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "SYNC_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderSyncEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[stageKey] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderSyncEffectExecutor) {
                    if (handles[stageKey] === handle) handles.remove(stageKey)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result)
            } catch (error: Exception) {
                synchronized(this@ReaderSyncEffectExecutor) {
                    if (handles[stageKey] === handle) handles.remove(stageKey)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "SYNC_CORE_FAILURE")
                }
            }
        }
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelSync(correlationId)
        val activeHandles = handles.filterKeys { it.startsWith("$correlationId:") }.values.toList()
        handles.keys.removeAll { it.startsWith("$correlationId:") }
        val coreCancelled = activeHandles.any(ReaderSyncCommandHandle::cancel)
        return invalidated || runtimeCancelled || coreCancelled
    }

    @Synchronized
    open fun cancelActive(): Boolean =
        domainStore.state.value.activeCorrelationId?.let(::cancel) ?: false

    private fun handleSuccess(effect: ReaderUIEffect, result: JSONObject) {
        val correlationId = requireNotNull(effect.correlationId)
        val event = requireNotNull(domainStore.current(correlationId)?.activeEvent)
        val projected = projectAndValidateReaderUiResult(event, effect.type, result)
        val advance = runtime.acceptSyncResult(effect.type, correlationId, projected)
        if (advance.accepted && advance.completed) domainStore.complete(correlationId)
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptSyncResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        requireNotNull(domainStore.current(correlationId)) { "stale sync effect" }
        require(READER_UI_RUNTIME_SYNC_PILOT_CORE_SEQUENCE.values.flatten().contains(effect.type)) {
            "unsupported sync Core stage: ${effect.type}"
        }
        return CoreCommand(effect.type, effect.jsonPayload.toCanonicalJSONObject())
    }
}

/**
 * Narrow seam for dispatching a canonical sync event to the Runtime. The
 * coordinator implements this so [ReaderSyncPilotCoordinator] can share the
 * same Runtime instance (and test override) as the other Pilot cohorts
 * without depending on the concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal fun interface ReaderSyncRuntimeDispatcher {
    fun dispatch(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderUITransition
}

/**
 * Isolated opt-in coordinator retained to prove boundary behavior before a
 * future production integration. No production caller currently constructs
 * this coordinator; Sync remains declared default-Shadow.
 */
internal class ReaderSyncPilotCoordinator(
    private val runtimeDispatcher: ReaderSyncRuntimeDispatcher,
    private val driver: ReaderSyncPilotRuntimeDriver,
    private val onObservation: (ReaderUiRuntimeShadowObservation) -> Unit,
    private val onMetricsCovered: () -> Unit,
    private val onMetricsError: (Exception) -> Unit
) {
    @Synchronized
    fun dispatchSyncPilot(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderSyncPilotDispatch {
        if (event !in READER_UI_RUNTIME_SYNC_PILOT_EVENTS) return ReaderSyncPilotDispatch.NotEnabled
        onMetricsCovered()
        var runtimeDispatchCompleted = false
        return try {
            val transition = runtimeDispatcher.dispatch(event, payload, correlationId)
            runtimeDispatchCompleted = true
            validateSyncEffectBoundary(event, transition.effects, correlationId)
            onObservation(
                ReaderUiRuntimeShadowObservation(
                    event = event,
                    correlationId = correlationId,
                    runtimeState = transition.state,
                    runtimeEffects = transition.effects,
                    mismatches = emptyList(),
                    cancelledCorrelationIds = transition.cancelledCorrelationIds,
                    mode = ReaderUiRuntimeDispatchMode.PILOT
                )
            )
            ReaderSyncPilotDispatch.Dispatched(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(driver::cancelSync)
            onMetricsError(error)
            ReaderSyncPilotDispatch.FailedClosed
        }
    }

    private fun validateSyncEffectBoundary(
        event: String,
        effects: List<ReaderUIEffect>,
        correlationId: String?
    ) {
        val expectedCore = READER_UI_RUNTIME_SYNC_PILOT_CORE_SEQUENCE[event]
            ?: error("sync pilot event $event has no expected coreSequence")
        val expectedHost = READER_UI_RUNTIME_SYNC_PILOT_HOST_REQUEST[event]

        val coreEffects = effects.filter { it.kind == ReaderUIEffectKind.CORE }
        val hostEffects = effects.filter { it.kind == ReaderUIEffectKind.HOST }

        if (coreEffects.map { it.type } != expectedCore) {
            throw io.reader.ui.runtime.ReaderUIRuntimeException(
                "SYNC_EFFECT_BOUNDARY",
                "$event must emit Core sequence $expectedCore, got ${coreEffects.map { it.type }}"
            )
        }
        if (expectedHost != null) {
            if (hostEffects.size != 1 || hostEffects.single().type != expectedHost) {
                throw io.reader.ui.runtime.ReaderUIRuntimeException(
                    "SYNC_EFFECT_BOUNDARY",
                    "$event must emit exactly one Host effect $expectedHost, got ${hostEffects.map { it.type }}"
                )
            }
        } else {
            if (hostEffects.isNotEmpty()) {
                throw io.reader.ui.runtime.ReaderUIRuntimeException(
                    "SYNC_EFFECT_BOUNDARY",
                    "$event must not emit Host effects, got ${hostEffects.map { it.type }}"
                )
            }
        }
        if (effects.any { it.correlationId != correlationId }) {
            throw io.reader.ui.runtime.ReaderUIRuntimeException(
                "SYNC_EFFECT_BOUNDARY",
                "$event emitted an effect with a drifted correlationId"
            )
        }
    }
}
