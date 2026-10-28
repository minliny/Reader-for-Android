package io.reader.android.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest

/** Protocol identity, never inferred from the current page/book or a new Core read. */
data class CoreProtocolReference(
    val runtimeSession: String,
    val originalId: String,
    val originalRequestId: Long?,
    val requestToken: String?,
    val deliveryId: Long? = null,
)

internal enum class CoreProtocolStage {
    CONSUMED_ACK_FAILED, RESOLVED_FAULTS_AWAIT_ACCEPTANCE,
    HOST_NON_ADMISSION_UNRECORDED, HOST_NON_ADMISSION_RECORDED,
    HOST_CONTROL_ERROR_RETAINED, CONSUMER_QUALIFICATION_REQUIRED, ORIGINAL_UNCERTAIN, OWNER_QUARANTINED,
}

/** Separate domain. Must be caught BEFORE business PendingOperationException classification. */
internal class CoreProtocolPending(
    val original: CoreProtocolReference,
    val stage: CoreProtocolStage,
    cause: Throwable? = null,
) : Exception("CORE_PROTOCOL_PENDING:${stage.name}", cause)

/** Finds only a real typed cause, never parses message text or aliases a business PendingOperationException. */
internal fun Throwable.coreProtocolPending(): CoreProtocolPending? {
    val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is CoreProtocolPending) return current
        current = current.cause
    }
    return null
}

internal enum class CoreProtocolAction { ACK_SAME_DELIVERY, ACCEPT_RESOLVED_FAULTS, RECORD_CONTROL_ERROR, RECORD_NON_ADMISSION }
internal data class CoreProtocolPresentation(
    val presentationId: String, val original: CoreProtocolReference, val stage: CoreProtocolStage,
    val action: CoreProtocolAction?, val busy: Boolean = false, val actionFailed: Boolean = false,
)
internal data class CoreProtocolRecoveryView(
    val initialized: Boolean = false, val admissionOpen: Boolean = false,
    val items: List<CoreProtocolPresentation> = emptyList(),
)

/** Cheap Application field: observing it never touches a lazy AppCoreOwner/bridge/Native runtime. */
internal class CoreProtocolRecoveryFeed {
    internal val mutableView = MutableStateFlow(CoreProtocolRecoveryView())
    val view: StateFlow<CoreProtocolRecoveryView> = mutableView.asStateFlow()
    private data class Handler(val owner: Any, val invoke: (String) -> Unit)
    private val handler = AtomicReference<Handler?>()
    internal fun attach(owner: Any, invoke: (String) -> Unit) {
        check(handler.compareAndSet(null, Handler(owner, invoke))) { "PROTOCOL_RECOVERY_OWNER_ALREADY_ATTACHED" }
    }
    fun requestAction(presentationId: String): Boolean {
        val active = handler.get() ?: return false // Empty/uninitialized state cannot create Native.
        active.invoke(presentationId)
        return true
    }
}

/** Private frozen action input. UI receives only its presentationId, never a Delivery or editable fault set. */
internal sealed interface ProtocolProblem {
    val ref: CoreProtocolReference
    data class AckOnly(override val ref: CoreProtocolReference, val delivery: ReaderCoreEventActor.Delivery) : ProtocolProblem
    data class Resolved(override val ref: CoreProtocolReference, val faultIds: Set<String>, val nonAdmissionRecorded: Boolean) : ProtocolProblem
    data class ControlError(override val ref: CoreProtocolReference, val delivery: ReaderCoreEventActor.Delivery) : ProtocolProblem
    data class NonAdmission(override val ref: CoreProtocolReference, val plan: ReaderCoreEventActor.PreparedRequest,
        val fact: HostNonAdmissionFact) : ProtocolProblem
    data class ConsumerUnqualified(override val ref: CoreProtocolReference) : ProtocolProblem
    data class Uncertain(override val ref: CoreProtocolReference) : ProtocolProblem
    data class Quarantined(override val ref: CoreProtocolReference) : ProtocolProblem
}

/** AppCoreOwner adapter: exact original/actor-protocol binding, under the ONE operationMutex. */
internal interface ProtocolRecoveryLane {
    suspend fun <T> exclusive(ref: CoreProtocolReference, block: suspend () -> T): T
    fun assertHeld()
}
internal interface ProtocolRecoveryBindings {
    /** Only actor snapshot + original bridge/journal facts. No CoreTransport.request or business reconciliation. */
    fun problems(snapshot: ReaderCoreEventActor.Snapshot): List<ProtocolProblem>
    /** Protocol registry only. Retire a business sink route ONLY for ORIGINAL_TERMINAL, never for control/Host ACK. */
    fun acknowledgementCompleted(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery)
    /** Protocol registry only: preserve the business intent/journal exactly as it is. */
    fun nonAdmissionRecorded(fact: HostNonAdmissionFact)
}
internal interface ProtocolEvidenceSink {
    fun recordControlError(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery)
    fun recordNonAdmission(fact: HostNonAdmissionFact)
}

/**
 * Protocol action owner. It has NO business CoreTransport and NO business terminal
 * consumer callback, so its actions cannot replay imports/bookmarks/progress.
 * Existing sole .changes maintenance invokes refreshOnHeldLane; this class does
 * not consume .changes itself and does not create a poller.
 */
internal class CoreProtocolRecoveryOwner(
    private val actor: ReaderCoreEventActor,
    private val lane: ProtocolRecoveryLane,
    private val bindings: ProtocolRecoveryBindings,
    private val evidence: ProtocolEvidenceSink,
    private val appScope: CoroutineScope,
    private val feed: CoreProtocolRecoveryFeed,
) {
    private class Ticket(val id: String, val problem: ProtocolProblem) {
        val claimed = AtomicBoolean(false)
        val retired = AtomicBoolean(false)
    }
    private val tickets = AtomicReference<Map<String, Ticket>>(emptyMap())
    private val mutableView get() = feed.mutableView
    val view: StateFlow<CoreProtocolRecoveryView> get() = feed.view
    init { feed.attach(this, ::invoke) }

    /** Publication occurs only on the existing whole-turn / exact original maintenance lane. */
    suspend fun refreshOnHeldLane() {
        lane.assertHeld()
        val snapshot = actor.snapshot() // Actor memory only; no Native business command/read.
        val problems = bindings.problems(snapshot).distinct().map {
            if (it is ProtocolProblem.Resolved) it.copy(faultIds = it.faultIds.toSet()) else it
        }
        val old = tickets.get().values.toList()
        val next = problems.map { problem ->
            old.singleOrNull { it.problem == problem && !it.retired.get() }
                ?: Ticket(UUID.randomUUID().toString(), problem)
        }
        tickets.set(next.associateBy { it.id })
        mutableView.value = CoreProtocolRecoveryView(true, snapshot.admissionOpen, next.map(::presentation))
    }

    /** A failed memory observation is quarantined, never converted into an implicit Native restart. */
    fun publishUnavailableFromOwner() {
        tickets.set(emptyMap())
        val ref = CoreProtocolReference(actor.runtimeSession, "actor:${actor.runtimeSession}", null, null)
        mutableView.value = CoreProtocolRecoveryView(true, false, listOf(CoreProtocolPresentation(
            UUID.randomUUID().toString(), ref, CoreProtocolStage.OWNER_QUARANTINED, null,
        )))
    }

    /** Called only by this App owner's close completion; no snapshot RPC can succeed after actor close. */
    fun publishClosedFromOwner(report: ReaderCoreEventActor.CloseReport) {
        check(!report.retained.admissionOpen)
        check(report.retained.requests.all { it.request.runtimeSession == actor.runtimeSession })
        tickets.set(emptyMap())
        val ref = CoreProtocolReference(actor.runtimeSession, "actor:${actor.runtimeSession}", null, null)
        mutableView.value = CoreProtocolRecoveryView(true, false, listOf(CoreProtocolPresentation(
            UUID.randomUUID().toString(), ref, CoreProtocolStage.OWNER_QUARANTINED, null,
        )))
        // Full original frames/faults stay in the App owner's CloseReport. No
        // directory reopen or native-worker-joined claim follows this projection.
    }

    /** VM checks its host generation first; this App-scope action survives later VM retirement. */
    fun invoke(presentationId: String) {
        val ticket = tickets.get()[presentationId] ?: return
        if (action(ticket.problem) == null || !ticket.claimed.compareAndSet(false, true)) return
        mutableView.update { state -> state.copy(items = state.items.map {
            if (it.presentationId == presentationId) it.copy(busy = true) else it
        }) }
        appScope.launch {
            var failed = false
            try {
                lane.exclusive(ticket.problem.ref) {
                    if (tickets.get()[presentationId] !== ticket) return@exclusive
                    val current = bindings.problems(actor.snapshot())
                    check(ticket.problem in current) { "PROTOCOL_PRESENTATION_QUALIFICATION_CHANGED" }
                    perform(ticket.problem)
                    ticket.retired.set(true)
                    retireTicket(ticket)
                    refreshOnHeldLane()
                }
            } catch (_: Exception) {
                failed = true // Original actor frame/fault and journal remain authoritative.
            } finally {
                if (failed) {
                    ticket.retired.set(true)
                    retireTicket(ticket)
                    // No automatic action retry. A fresh presentation requires
                    // authoritative refresh on the existing owner maintenance lane.
                    mutableView.update { state -> state.copy(items = state.items.map {
                        if (it.presentationId == presentationId) it.copy(busy = false, action = null, actionFailed = true) else it
                    }) }
                    // Observation only, no action retry. If the owner is closing
                    // this also fails conservatively and the disabled notice stays.
                    try { lane.exclusive(ticket.problem.ref) { refreshOnHeldLane() } } catch (_: Exception) { }
                }
            }
        }
    }

    private suspend fun perform(problem: ProtocolProblem) {
        lane.assertHeld()
        check(problem.ref.runtimeSession == actor.runtimeSession)
        when (problem) {
            is ProtocolProblem.AckOnly -> {
                val before = actor.snapshot()
                val frame = exactFrame(problem.ref, problem.delivery)
                check(frame.phase == ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED)
                // One explicit continue intent, two separate protocol facts. Freeze
                // eligible ORIGINAL IDs before ACK; never accept a new/foreign fault.
                val offeredIds = before.faults.filter { fault ->
                    (fault.deliveryId == problem.delivery.deliveryId && fault.kind in setOf("DELIVERY_ACK", "ORIGINAL_CONSUMER", "HOST_CONTROL_ERROR")) ||
                        (problem.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL &&
                            fault.requestId == problem.ref.originalRequestId && fault.kind in setOf("SEND_UNKNOWN", "READ_CANCEL"))
                }.map { it.id }.toSet()
                // No consumer, no protocol record rewrite and no business request.
                check(actor.retryAcknowledgement(problem.delivery) is ReaderCoreEventActor.Acknowledgement.Succeeded) {
                    "ORIGINAL_EVENT_ACK_STILL_PENDING"
                }
                bindings.acknowledgementCompleted(problem.ref, problem.delivery)
                val afterAck = actor.snapshot()
                bindings.problems(afterAck) // Preserve the exact small original route after dropping a large sink.
                val resolvedOfferedIds = offeredIds.filter { id -> afterAck.faults.singleOrNull { it.id == id }?.resolved == true }.toSet()
                val hostOutcomeStillUnknown = afterAck.hostOperations.any { it.phase in setOf(
                    "AWAITING_OWNER", "CONTROL_ERROR", "CONTROL_NOT_ADMITTED", "CONTROL_SEND_UNKNOWN") }
                if (resolvedOfferedIds.isNotEmpty() && !hostOutcomeStillUnknown) {
                    actor.resumeAdmission(resolvedOfferedIds)
                }
                // ACK failure never reaches resume. New/unresolved faults remain;
                // the actual snapshot admissionOpen determines the rendered state.
            }
            is ProtocolProblem.Resolved -> {
                check(problem.faultIds.isNotEmpty())
                val snapshot = actor.snapshot()
                problem.faultIds.forEach { id ->
                    check(snapshot.faults.singleOrNull { it.id == id }?.resolved == true) { "FAULT_NOT_ACTUALLY_RESOLVED" }
                }
                actor.resumeAdmission(problem.faultIds) // Exactly the IDs offered by this presentation.
                // Other unresolved faults may still block; publish actual admissionOpen.
            }
            is ProtocolProblem.ControlError -> {
                val frame = exactFrame(problem.ref, problem.delivery)
                check(frame.phase == ReaderCoreEventActor.DeliveryPhase.RETAINED &&
                    frame.delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR)
                val ack = actor.consume(problem.delivery) { evidence.recordControlError(problem.ref, it) }
                check(ack is ReaderCoreEventActor.Acknowledgement.Succeeded) { "CONTROL_ERROR_CONSUMED_ACK_PENDING" }
                bindings.problems(actor.snapshot())
                // Does NOT set an original business terminal, return success for
                // host.complete, or invoke retireHostAfterOriginalTerminal.
            }
            is ProtocolProblem.NonAdmission -> {
                check(problem.plan.runtimeSession == problem.ref.runtimeSession &&
                    problem.plan.requestId == problem.ref.originalRequestId && problem.plan.token == problem.ref.requestToken)
                val request = actor.snapshot().requests.single { it.request === problem.plan }
                check(request.phase == ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED)
                problem.fact.status?.let { check(ReaderCoreEventActor.knownPreAdmission(it)) }
                if (problem.fact.status == null) {
                    check(problem.fact.reason in setOf("BEFORE_SEND_NOT_DISPATCHED", "ADMISSION_CLOSED_BEFORE_NATIVE"))
                    check(actor.snapshot().faults.any { it.requestId == problem.plan.requestId && it.kind == problem.fact.reason })
                }
                check(problem.fact.runtimeSession == problem.plan.runtimeSession && problem.fact.requestId == problem.plan.requestId &&
                    problem.fact.requestToken == problem.plan.token && problem.fact.method == problem.plan.method)
                val hash = MessageDigest.getInstance("SHA-256").digest(problem.plan.parametersJson.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                check(problem.fact.parametersSha256 == hash)
                evidence.recordNonAdmission(problem.fact)
                actor.admissionRejectionRecorded(problem.plan)
                bindings.nonAdmissionRecorded(problem.fact)
                bindings.problems(actor.snapshot())
                // Business original intent stays; accepting the resolved fault
                // IDs is a NEW explicit presentation/action, never implicit here.
            }
            else -> error("PROTOCOL_QUALIFICATION_NOT_AVAILABLE")
        }
    }

    private fun retireTicket(ticket: Ticket) {
        tickets.updateAndGet { current -> if (current[ticket.id] === ticket) current - ticket.id else current }
    }

    private suspend fun exactFrame(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery): ReaderCoreEventActor.RetainedDelivery {
        check(ref.deliveryId == delivery.deliveryId && ref.runtimeSession == delivery.runtimeSession && ref.requestToken == delivery.requestToken)
        return actor.snapshot().deliveries.single { it.delivery === delivery }
    }

    private fun action(problem: ProtocolProblem): CoreProtocolAction? = when (problem) {
        is ProtocolProblem.AckOnly -> CoreProtocolAction.ACK_SAME_DELIVERY
        is ProtocolProblem.Resolved -> CoreProtocolAction.ACCEPT_RESOLVED_FAULTS
        is ProtocolProblem.ControlError -> CoreProtocolAction.RECORD_CONTROL_ERROR
        is ProtocolProblem.NonAdmission -> CoreProtocolAction.RECORD_NON_ADMISSION
        else -> null // No generic consumer/business retry.
    }
    private fun presentation(ticket: Ticket): CoreProtocolPresentation {
        val stage = when (val problem = ticket.problem) {
            is ProtocolProblem.AckOnly -> CoreProtocolStage.CONSUMED_ACK_FAILED
            is ProtocolProblem.Resolved -> if (problem.nonAdmissionRecorded) CoreProtocolStage.HOST_NON_ADMISSION_RECORDED
                else CoreProtocolStage.RESOLVED_FAULTS_AWAIT_ACCEPTANCE
            is ProtocolProblem.ControlError -> CoreProtocolStage.HOST_CONTROL_ERROR_RETAINED
            is ProtocolProblem.NonAdmission -> CoreProtocolStage.HOST_NON_ADMISSION_UNRECORDED
            is ProtocolProblem.ConsumerUnqualified -> CoreProtocolStage.CONSUMER_QUALIFICATION_REQUIRED
            is ProtocolProblem.Quarantined -> CoreProtocolStage.OWNER_QUARANTINED
            is ProtocolProblem.Uncertain -> CoreProtocolStage.ORIGINAL_UNCERTAIN
        }
        return CoreProtocolPresentation(ticket.id, ticket.problem.ref, stage, action(ticket.problem), busy = ticket.claimed.get())
    }
}

/** Separate Host protocol facts. No wireTerminal field and no business journal mutation. */
internal class JournalProtocolEvidence(private val journal: DurableJournal) : ProtocolEvidenceSink {
    override fun recordControlError(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery) {
        check(delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR)
        val wire = JSONObject(delivery.rawEvent)
        check(wire.getString("type") == "error" && wire.getLong("requestId") == delivery.requestId && wire.get("error") is JSONObject)
        val id = "core-control-${delivery.runtimeSession}-${delivery.deliveryId}"
        val existing = journal.exact(id, includeCompleted = true)
        if (existing != null) {
            check(existing.getString("kind") == "coreHostControlError" &&
                existing.getString("runtimeSession") == delivery.runtimeSession &&
                existing.getString("requestToken") == delivery.requestToken &&
                existing.getLong("deliveryId") == delivery.deliveryId &&
                existing.getLong("controlRequestId") == delivery.requestId &&
                existing.getString("originalId") == ref.originalId && existing.getString("rawWire") == delivery.rawEvent)
            return
        }
        journal.write(JSONObject().put("journalSchema", 1).put("kind", "coreHostControlError").put("id", id).put("completed", true)
            .put("runtimeSession", delivery.runtimeSession).put("requestToken", delivery.requestToken)
            .put("deliveryId", delivery.deliveryId).put("controlRequestId", delivery.requestId)
            .put("originalRequestId", ref.originalRequestId ?: JSONObject.NULL).put("originalId", ref.originalId)
            .put("rawWire", delivery.rawEvent))
    }
    override fun recordNonAdmission(fact: HostNonAdmissionFact) {
        val existing = journal.exact(fact.id, includeCompleted = true)
        if (existing != null) {
            check(existing.getString("kind") == "coreHostNonAdmission" &&
                existing.getString("runtimeSession") == fact.runtimeSession && existing.getLong("requestId") == fact.requestId &&
                existing.getString("requestToken") == fact.requestToken && existing.getString("method") == fact.method &&
                existing.getString("parametersSha256") == fact.parametersSha256 && existing.getString("reason") == fact.reason)
            check(existing.opt("nativeStatus") == (fact.status?.status ?: JSONObject.NULL) &&
                existing.opt("nativeOperation") == (fact.status?.operation ?: JSONObject.NULL) &&
                existing.opt("diagnosticCode") == (fact.status?.diagnostic?.code ?: JSONObject.NULL) &&
                existing.opt("diagnosticMessage") == (fact.status?.diagnostic?.message ?: JSONObject.NULL))
            return
        }
        HostNonAdmissionJournalSink(journal).record(fact)
    }
}
