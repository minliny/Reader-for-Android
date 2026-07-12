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
 * Experimental RSS Pilot coverage set. Production keeps the seven lifecycle
 * events in Shadow; explicit opt-in emits one next Core effect
 * (`rss.feed.refresh` / `rss.subscription.persist` /
 * `rss.subscription.remove` / `rss.entry.read` / `rss.favorite.persist` /
 * `rss.favorite.remove`).
 */
internal val READER_UI_RUNTIME_RSS_PILOT_EVENTS: Set<String> = linkedSetOf(
    "rss.refresh",
    "rss.subscription.add",
    "rss.subscription.delete",
    "rss.subscription.edit",
    "rss.entry.open",
    "rss.favorite.add",
    "rss.favorite.remove"
)

internal val READER_UI_RUNTIME_RSS_PILOT_CORE_EFFECTS: Set<String> = setOf(
    "rss.feed.refresh",
    "rss.subscription.persist",
    "rss.subscription.remove",
    "rss.entry.read",
    "rss.favorite.persist",
    "rss.favorite.remove"
)

/**
 * Runtime mode for the RSS Pilot cohort. `Shadow` retains the R7 live-shadow
 * diagnostic path; `Pilot` routes each RSS event through the Runtime and the
 * sole [ReaderRssEffectExecutor] before any native side-effect.
 */
internal enum class ReaderRssPilotMode {
    SHADOW,
    PILOT
}

/**
 * Result of dispatching an RSS event through the opt-in Pilot. Mirrors
 * [ReaderImportPilotDispatch]: the coordinator either hands the caller the
 * Runtime transition (with the single next Core effect) or fails closed.
 */
internal sealed interface ReaderRssPilotDispatch {
    data object NotEnabled : ReaderRssPilotDispatch
    data class Dispatched(val transition: ReaderUITransition) : ReaderRssPilotDispatch
    data object FailedClosed : ReaderRssPilotDispatch
}

/**
 * Runtime advance returned by [ReaderRssPilotRuntimeDriver.acceptRssResult].
 * RSS is a single-step transaction per event; there is no next effect.
 */
internal data class ReaderRssRuntimeAdvance(
    val accepted: Boolean,
    val effects: List<ReaderUIEffect> = emptyList()
)

/**
 * Narrow seam the coordinator implements so the executor can accept Core
 * results and cancel an active RSS correlation without depending on the
 * concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal interface ReaderRssPilotRuntimeDriver {
    val rssRuntimeState: ReaderUIState

    fun acceptRssResult(
        coreType: String,
        correlationId: String,
        result: ReaderUIJSONResult = emptyMap(),
        error: String? = null
    ): ReaderRssRuntimeAdvance

    fun cancelRss(correlationId: String): Boolean
}

/**
 * Correlation-scoped domain data for the one active RSS transaction. Like
 * [ReaderImportDomainState], RSS has no measured-layout or multi-stage
 * dependency; each event is a single Core command.
 */
internal data class ReaderRssDomainState(
    val activeCorrelationId: String? = null,
    val activeEvent: String? = null,
    val stage: String? = null,
    val error: String? = null
)

internal class ReaderRssDomainStore {
    private val mutableState = MutableStateFlow(ReaderRssDomainState())
    val state: StateFlow<ReaderRssDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(correlationId: String, event: String) {
        mutableState.value = ReaderRssDomainState(
            activeCorrelationId = correlationId,
            activeEvent = event,
            stage = "started"
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderRssDomainState? =
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
        transform: (ReaderRssDomainState) -> ReaderRssDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderRssCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderRssCommandClient {
    fun begin(method: String, params: JSONObject): ReaderRssCommandHandle
}

/** Production adapter. `ReaderCoreClient` is resolved lazily only when Pilot is enabled. */
internal class ReaderCoreRssCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderRssCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderRssCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderRssCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/**
 * The sole owner of RSS Core execution in Pilot mode. Runtime returns exactly
 * one next effect per RSS event; this executor is the only place allowed to
 * run it. Mirrors [ReaderImportEffectExecutor] / [ReaderReplaceRuleEffectExecutor].
 */
internal open class ReaderRssEffectExecutor(
    private val domainStore: ReaderRssDomainStore,
    private val runtime: ReaderRssPilotRuntimeDriver,
    private val commandClientFactory: () -> ReaderRssCommandClient = { ReaderCoreRssCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderRssCommandHandle>()
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
                handleFailure(effect, error.message ?: "RSS_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "RSS_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderRssEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[correlationId] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderRssEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result)
            } catch (error: Exception) {
                synchronized(this@ReaderRssEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "RSS_CORE_FAILURE")
                }
            }
        }
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelRss(correlationId)
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
        val advance = runtime.acceptRssResult(effect.type, correlationId, projected)
        if (advance.accepted) domainStore.complete(correlationId)
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptRssResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        requireNotNull(domainStore.current(correlationId)) { "stale RSS effect" }
        require(effect.type in READER_UI_RUNTIME_RSS_PILOT_CORE_EFFECTS) {
            "unsupported RSS Core stage: ${effect.type}"
        }
        return CoreCommand(effect.type, effect.jsonPayload.toCanonicalJSONObject())
    }
}

/**
 * Narrow seam for dispatching a canonical RSS event to the Runtime. The
 * coordinator implements this so [ReaderRssPilotCoordinator] can share the
 * same Runtime instance (and test override) as the other Pilot cohorts
 * without depending on the concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal fun interface ReaderRssRuntimeDispatcher {
    fun dispatch(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderUITransition
}

/**
 * Owns the RSS Pilot dispatch flow: validate the event is in the Pilot cohort,
 * dispatch through the Runtime, enforce the single-Core-effect boundary, and
 * fail-closed on any violation. The coordinator delegates its
 * `dispatchRssPilot` entry point here so the RSS cohort remains independent
 * from the other Pilot cohorts.
 */
internal class ReaderRssPilotCoordinator(
    private val runtimeDispatcher: ReaderRssRuntimeDispatcher,
    private val driver: ReaderRssPilotRuntimeDriver,
    private val onObservation: (ReaderUiRuntimeShadowObservation) -> Unit,
    private val onMetricsCovered: () -> Unit,
    private val onMetricsError: (Exception) -> Unit
) {
    @Synchronized
    fun dispatchRssPilot(
        event: String,
        payload: ReaderUIJSONPayload,
        correlationId: String?
    ): ReaderRssPilotDispatch {
        if (event !in READER_UI_RUNTIME_RSS_PILOT_EVENTS) return ReaderRssPilotDispatch.NotEnabled
        onMetricsCovered()
        var runtimeDispatchCompleted = false
        return try {
            val transition = runtimeDispatcher.dispatch(event, payload, correlationId)
            runtimeDispatchCompleted = true
            val firstEffect = transition.effects.singleOrNull()
            if (
                firstEffect?.kind != ReaderUIEffectKind.CORE ||
                firstEffect.correlationId != correlationId
            ) {
                throw io.reader.ui.runtime.ReaderUIRuntimeException(
                    "RSS_EFFECT_BOUNDARY",
                    "$event must emit exactly one correlated next Core effect"
                )
            }
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
            ReaderRssPilotDispatch.Dispatched(transition)
        } catch (error: Exception) {
            if (runtimeDispatchCompleted) correlationId?.let(driver::cancelRss)
            onMetricsError(error)
            ReaderRssPilotDispatch.FailedClosed
        }
    }
}
