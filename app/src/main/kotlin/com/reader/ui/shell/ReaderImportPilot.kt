package com.reader.ui.shell

import com.reader.api.ReaderCoreClient
import io.reader.ui.runtime.ReaderUIEffect
import io.reader.ui.runtime.ReaderUIEffectKind
import io.reader.ui.runtime.ReaderUIState
import io.reader.ui.runtime.ReaderUITransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Experimental import Pilot coverage set. Production keeps these events in
 * Shadow; an explicit opt-in exercises one next Core effect per event
 * (`import.parse` / `import.persist` / `import.rollback`).
 */
internal val READER_UI_RUNTIME_IMPORT_PILOT_EVENTS: Set<String> = linkedSetOf(
    "import.start",
    "import.apply",
    "import.cancel"
)

/**
 * Result of dispatching an import event through the opt-in Pilot. Mirrors
 * [ReaderBookOpenPilotDispatch]: the coordinator either hands the caller the
 * Runtime transition (with the single next Core effect) or fails closed.
 */
internal sealed interface ReaderImportPilotDispatch {
    data object NotEnabled : ReaderImportPilotDispatch
    data class Applied(val transition: ReaderUITransition) : ReaderImportPilotDispatch
    data object FailedClosed : ReaderImportPilotDispatch
}

/**
 * Runtime advance returned by [ReaderImportRuntimeDriver.acceptImportResult].
 * Import is a single-step transaction per event; there is no next effect.
 */
internal data class ReaderImportRuntimeAdvance(
    val accepted: Boolean,
    val effects: List<ReaderUIEffect> = emptyList()
)

/**
 * Narrow seam the coordinator implements so the executor can accept Core
 * results and cancel an active import correlation without depending on the
 * concrete [io.reader.ui.runtime.ReaderUIRuntime].
 */
internal interface ReaderImportRuntimeDriver {
    val importRuntimeState: ReaderUIState

    fun acceptImportResult(
        coreType: String,
        correlationId: String,
        error: String? = null
    ): ReaderImportRuntimeAdvance

    fun cancelImport(correlationId: String): Boolean
}

/**
 * Correlation-scoped domain data for the one active import transaction.
 * Unlike [ReaderBookOpenDomainStore], import has no measured-layout or
 * multi-stage dependency; each event is a single Core command.
 */
internal data class ReaderImportDomainState(
    val activeCorrelationId: String? = null,
    val activeEvent: String? = null,
    val stage: String? = null,
    val error: String? = null
)

internal class ReaderImportDomainStore {
    private val mutableState = MutableStateFlow(ReaderImportDomainState())
    val state: StateFlow<ReaderImportDomainState> = mutableState.asStateFlow()

    @Synchronized
    fun begin(correlationId: String, event: String) {
        mutableState.value = ReaderImportDomainState(
            activeCorrelationId = correlationId,
            activeEvent = event,
            stage = "started"
        )
    }

    @Synchronized
    fun isCurrent(correlationId: String): Boolean =
        mutableState.value.activeCorrelationId == correlationId

    @Synchronized
    fun current(correlationId: String): ReaderImportDomainState? =
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
        transform: (ReaderImportDomainState) -> ReaderImportDomainState
    ): Boolean {
        val current = mutableState.value
        if (current.activeCorrelationId != correlationId) return false
        mutableState.value = transform(current)
        return true
    }
}

/** Narrow seam for a real Core command handle and its numeric request id. */
internal interface ReaderImportCommandHandle {
    val requestId: Long
    suspend fun await(): JSONObject
    fun cancel(): Boolean
}

internal interface ReaderImportCommandClient {
    fun begin(method: String, params: JSONObject): ReaderImportCommandHandle
}

/** Production adapter. `ReaderCoreClient` is resolved lazily only when Pilot is enabled. */
internal class ReaderCoreImportCommandClient(
    private val coreProvider: () -> ReaderCoreClient = ReaderCoreClient::get
) : ReaderImportCommandClient {
    override fun begin(method: String, params: JSONObject): ReaderImportCommandHandle {
        val handle = coreProvider().beginCommand(method, params)
        return object : ReaderImportCommandHandle {
            override val requestId: Long get() = handle.requestId
            override suspend fun await(): JSONObject = handle.await()
            override fun cancel(): Boolean = handle.cancel()
        }
    }
}

/**
 * The sole owner of import Core execution in Pilot mode. Runtime returns
 * exactly one next effect per import event; this executor is the only place
 * allowed to run it.
 */
internal open class ReaderImportEffectExecutor(
    private val domainStore: ReaderImportDomainStore,
    private val runtime: ReaderImportRuntimeDriver,
    private val commandClientFactory: () -> ReaderImportCommandClient = { ReaderCoreImportCommandClient() }
) {
    private val handles = mutableMapOf<String, ReaderImportCommandHandle>()
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
                handleFailure(effect, error.message ?: "IMPORT_INVALID_EFFECT")
                return@launch
            }
            val handle = try {
                commandClientFactory().begin(command.method, command.params)
            } catch (error: Exception) {
                handleFailure(effect, error.message ?: "IMPORT_COMMAND_START_FAILED")
                return@launch
            }

            synchronized(this@ReaderImportEffectExecutor) {
                if (!domainStore.isCurrent(correlationId)) {
                    handle.cancel()
                    return@launch
                }
                handles[correlationId] = handle
            }

            try {
                val result = handle.await()
                synchronized(this@ReaderImportEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (!domainStore.isCurrent(correlationId)) return@launch
                handleSuccess(effect, result)
            } catch (error: Exception) {
                synchronized(this@ReaderImportEffectExecutor) {
                    if (handles[correlationId] === handle) handles.remove(correlationId)
                }
                if (domainStore.isCurrent(correlationId)) {
                    handleFailure(effect, error.message ?: "IMPORT_CORE_FAILURE")
                }
            }
        }
    }

    @Synchronized
    open fun cancel(correlationId: String): Boolean {
        val invalidated = domainStore.cancel(correlationId)
        val runtimeCancelled = runtime.cancelImport(correlationId)
        val handle = handles.remove(correlationId)
        val coreCancelled = handle?.cancel() ?: false
        return invalidated || runtimeCancelled || coreCancelled
    }

    @Synchronized
    open fun cancelActive(): Boolean =
        domainStore.state.value.activeCorrelationId?.let(::cancel) ?: false

    private fun handleSuccess(effect: ReaderUIEffect, result: JSONObject) {
        val correlationId = requireNotNull(effect.correlationId)
        val advance = runtime.acceptImportResult(effect.type, correlationId)
        if (advance.accepted) domainStore.complete(correlationId)
    }

    private fun handleFailure(effect: ReaderUIEffect, message: String) {
        val correlationId = effect.correlationId ?: return
        val advance = runtime.acceptImportResult(effect.type, correlationId, error = message)
        if (advance.accepted) domainStore.fail(correlationId, message)
    }

    private data class CoreCommand(val method: String, val params: JSONObject)

    private fun buildCommand(effect: ReaderUIEffect): CoreCommand {
        val correlationId = requireNotNull(effect.correlationId)
        val state = requireNotNull(domainStore.current(correlationId)) { "stale import effect" }
        return when (effect.type) {
            "import.parse" -> CoreCommand(
                method = "import.parse",
                params = JSONObject().apply {
                    effect.payload["json"]?.let { put("json", it) }
                }
            )
            "import.persist" -> CoreCommand(
                method = "import.persist",
                params = JSONObject().apply {
                    effect.payload["conflictMode"]?.let { put("conflictMode", it) }
                }
            )
            "import.rollback" -> CoreCommand(
                method = "import.rollback",
                params = JSONObject()
            )
            else -> error("unsupported import Core stage: ${effect.type}")
        }
    }
}
