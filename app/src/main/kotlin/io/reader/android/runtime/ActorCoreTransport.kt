package io.reader.android.runtime

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** The bridge needs only these four gate/identity facts, not an Android Application or Context. */
internal interface CoreTransportLane {
    fun requireHeldLane()
    fun requireLane(lease: ReaderOperationOwner.ClientLease)
    fun requireRequestLane(lease: ReaderOperationOwner.ClientLease, method: String, params: JSONObject)
    fun protocolReference(): CoreProtocolReference
}

/** One actor/registry. All mutable protocol and business-consumer state is confined to the App owner gate. */
internal class ActorCoreTransport(
    private val owner: CoreTransportLane,
    private val actor: ReaderCoreEventActor,
    private val nonAdmissionSink: HostNonAdmissionSink,
) : ProtocolRecoveryBindings {
    data class OriginalKey(val lease: ReaderOperationOwner.ClientLease, val originalId: String)
    private class Original(val route: Route, val plan: ReaderCoreEventActor.PreparedRequest,
        val terminalSink: ((JSONObject) -> Unit)?) {
        var consumerAttempted = false
        var consumerReturned = false
        var retainedDelivery: ReaderCoreEventActor.Delivery? = null
        var nonAdmission: HostNonAdmissionFact? = null
        var failure: String? = null
        var observerPending = false // A real write deadline is presentation state, never a consumer-attempt gate.
    }
    /** Small identity survives every registered request, including late Host frames after terminal ACK.
     * Its lifetime/growth has no bounded claim; it retains no parameters or callback. */
    private class Route(val key: OriginalKey, val session: String, val requestId: Long, val token: String) {
        val faultIds = linkedSetOf<String>() // Gate only. Never read by routeLease's cross-gate lookup.
        var terminalAcked = false
        var nonAdmissionRecorded = false
        var hostRelated = false
        fun ref(deliveryId: Long? = null) = CoreProtocolReference(session, key.originalId, requestId, token, deliveryId)
    }
    private val originals = ConcurrentHashMap<Long, Original>()
    private val routes = ConcurrentHashMap<String, Route>() // All registered identities; never enumerated for snapshots.
    private val requestRoutes = ConcurrentHashMap<Long, Route>()
    private val faultRoutes = linkedMapOf<String, Route>() // Current retained/resolved-but-unaccepted faults only, gate confined.
    private val maintenanceFailures = linkedMapOf<OriginalKey, String>()

    fun forClient(lease: ReaderOperationOwner.ClientLease): CoreTransport = object : CoreTransport {
        override val session: String get() { owner.requireLane(lease); return actor.runtimeSession }
        override fun request(method: String, params: JSONObject, timeoutMs: Long,
            beforeSend: (Long) -> Unit, terminal: ((JSONObject) -> Unit)?): JSONObject {
            owner.requireRequestLane(lease, method, params)
            require(timeoutMs > 0)
            // Conservative whole-turn retention is intentional in this version.
            // Actor READ_ONLY deadlines work; VM retirement cancellation is still OPEN.
            val kind = if (terminal == null) ReaderCoreEventActor.Kind.READ_ONLY else ReaderCoreEventActor.Kind.DURABLE_WRITE
            return runBlocking {
                val plan = try { actor.prepare(method, params.toString(), kind) }
                catch (error: IllegalStateException) {
                    if (error.message !in setOf("EVENT_ACTOR_ADMISSION_BLOCKED", "EVENT_ACTOR_CLOSED")) throw error
                    throw CoreProtocolPending(owner.protocolReference(), CoreProtocolStage.OWNER_QUARANTINED, error)
                }
                val key = OriginalKey(lease, "core:${plan.runtimeSession}:${plan.requestId}:${plan.token}")
                val route = Route(key, plan.runtimeSession, plan.requestId, plan.token)
                val original = Original(route, plan, terminal)
                check(routes.putIfAbsent(plan.token, route) == null)
                check(requestRoutes.putIfAbsent(plan.requestId, route) == null)
                check(originals.putIfAbsent(plan.requestId, original) == null)
                when (val sent = actor.dispatch(plan) { beforeSend(it.requestId) }) {
                    is ReaderCoreEventActor.DispatchOutcome.Rejected -> {
                        val fact = HostNonAdmissionFact.from(key, plan, sent)
                        original.nonAdmission = fact
                        synchronizeRoutes(actor.snapshot())
                        try { nonAdmissionSink.record(fact) }
                        catch (error: Exception) {
                            original.failure = "NON_ADMISSION_RECORD_FAILED"
                            throw pending(original, CoreProtocolStage.HOST_NON_ADMISSION_UNRECORDED, error)
                        }
                        actor.admissionRejectionRecorded(plan)
                        nonAdmissionRecorded(fact) // Separate Host fact; business intent stays untouched.
                        throw pending(original, CoreProtocolStage.HOST_NON_ADMISSION_RECORDED)
                    }
                    is ReaderCoreEventActor.DispatchOutcome.Unknown -> {
                        original.failure = "SEND_UNKNOWN"
                        throw pending(original, CoreProtocolStage.ORIGINAL_UNCERTAIN)
                    }
                    is ReaderCoreEventActor.DispatchOutcome.Accepted -> Unit
                }
                when (val observed = actor.observe(plan, timeoutMs)) {
                    is ReaderCoreEventActor.Observation.Terminal -> {
                        consumeFirst(original, observed.delivery)
                        CoreTerminal.result(method, JSONObject(observed.delivery.rawEvent))
                    }
                    is ReaderCoreEventActor.Observation.ReadExpired -> throw CoreReadTimeoutException(method)
                    is ReaderCoreEventActor.Observation.WritePending -> {
                        original.observerPending = true
                        throw pending(original, CoreProtocolStage.ORIGINAL_UNCERTAIN)
                    }
                }
            }
        }
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) {
            owner.requireLane(lease)
            require(timeoutMs in 0..1_000)
            runBlocking {
                val original = originals[requestId] ?: return@runBlocking
                check(original.route.key.lease == lease) { "FOREIGN_DRAIN_ORIGINAL" }
                val frame = actor.snapshot().deliveries.singleOrNull { matches(original.route, it.delivery) &&
                    it.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL }
                if (frame != null) {
                    requireFirstConsumption(original, frame)
                    consumeFirst(original, frame.delivery)
                } else if (timeoutMs > 0) when (val observed = actor.observe(original.plan, timeoutMs)) {
                    is ReaderCoreEventActor.Observation.Terminal -> consumeFirst(original, observed.delivery)
                    else -> Unit
                }
            }
        }
        override fun close() { error("CLIENT_TRANSPORT_CANNOT_CLOSE_NATIVE") }
    }

    /** Immutable route lookup only. The caller must recheck it AFTER obtaining the exact original maintenance gate. */
    fun routeLease(ref: CoreProtocolReference): ReaderOperationOwner.ClientLease? = route(ref)?.key?.lease
    fun requireRoute(ref: CoreProtocolReference): OriginalKey {
        val route = route(ref) ?: error("ORIGINAL_PROTOCOL_ROUTE_RETIRED_OR_FOREIGN")
        owner.requireLane(route.key.lease)
        return route.key
    }
    private fun route(ref: CoreProtocolReference): Route? = ref.requestToken?.let(routes::get)?.takeIf {
        it.session == ref.runtimeSession && it.key.originalId == ref.originalId && it.requestId == ref.originalRequestId
    }
    private fun deliveryRoute(delivery: ReaderCoreEventActor.Delivery): Route? = routes[delivery.requestToken]?.takeIf {
        it.session == delivery.runtimeSession &&
            (delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR || it.requestId == delivery.requestId)
    }
    private fun matches(route: Route, delivery: ReaderCoreEventActor.Delivery) = deliveryRoute(delivery) === route

    fun maintenanceKeys(snapshot: ReaderCoreEventActor.Snapshot): List<OriginalKey> {
        owner.requireHeldLane()
        return snapshot.deliveries.filter { it.phase == ReaderCoreEventActor.DeliveryPhase.RETAINED &&
            it.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL }.mapNotNull { frame ->
            val original = originals[frame.delivery.requestId]
            original?.takeIf { firstConsumptionEligible(it, frame) }?.route?.key
        }.distinct() // Unknown frames are not consumed; problems() explicitly quarantines them.
    }
    suspend fun consumeFirstRetained(key: OriginalKey) {
        owner.requireLane(key.lease)
        val original = originals.values.singleOrNull { it.route.key == key } ?: return
        val frame = actor.snapshot().deliveries.singleOrNull { matches(original.route, it.delivery) &&
            it.delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL } ?: return
        requireFirstConsumption(original, frame)
        consumeFirst(original, frame.delivery)
    }
    private fun firstConsumptionEligible(original: Original, frame: ReaderCoreEventActor.RetainedDelivery): Boolean {
        val delivery = frame.delivery
        return frame.phase == ReaderCoreEventActor.DeliveryPhase.RETAINED && !original.consumerAttempted &&
            delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL && matches(original.route, delivery) &&
            original.plan.runtimeSession == delivery.runtimeSession && original.plan.requestId == delivery.requestId &&
            original.plan.token == delivery.requestToken
        // SEND_UNKNOWN / observerPending describe observation/dispatch, never a failed consumer.
    }
    private fun requireFirstConsumption(original: Original, frame: ReaderCoreEventActor.RetainedDelivery) {
        check(firstConsumptionEligible(original, frame)) { "EXPLICIT_ORIGINAL_EVENT_RECOVERY_REQUIRED" }
    }
    private suspend fun consumeFirst(original: Original, delivery: ReaderCoreEventActor.Delivery) {
        owner.requireLane(original.route.key.lease)
        check(!original.consumerAttempted && delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL &&
            matches(original.route, delivery)) { "ORIGINAL_EVENT_IDENTITY_OR_PHASE" }
        original.retainedDelivery = delivery
        synchronizeRoutes(actor.snapshot())
        try {
            val ack = actor.consume(delivery) {
                original.consumerAttempted = true
                original.terminalSink?.invoke(JSONObject(it.rawEvent))
                original.consumerReturned = true
            }
            if (ack is ReaderCoreEventActor.Acknowledgement.Failed) {
                original.failure = "ACK_FAILED"
                throw pending(original, CoreProtocolStage.CONSUMED_ACK_FAILED)
            }
            acknowledgementCompleted(original.route.ref(delivery.deliveryId), delivery)
        } catch (error: Exception) {
            if (original.failure == null) original.failure = "CONSUMER_OR_PROTOCOL_FAILED"
            if (error is CoreProtocolPending) throw error
            throw pending(original, CoreProtocolStage.CONSUMER_QUALIFICATION_REQUIRED, error)
        }
    }
    fun retainMaintenanceFailure(key: OriginalKey, failure: Exception) {
        owner.requireHeldLane()
        maintenanceFailures[key] = failure.javaClass.name
    }

    /** This is the only private protocol registry adapter; it does not query Core business state. */
    override fun problems(snapshot: ReaderCoreEventActor.Snapshot): List<ProtocolProblem> {
        owner.requireHeldLane()
        synchronizeRoutes(snapshot)
        val result = mutableListOf<ProtocolProblem>()
        val accounted = linkedSetOf<String>()
        for (frame in snapshot.deliveries) {
            val route = deliveryRoute(frame.delivery)
            val ref = route?.ref(frame.delivery.deliveryId) ?: quarantineRef("delivery:${frame.delivery.deliveryId}", frame.delivery)
            if (route == null) { result += ProtocolProblem.Quarantined(ref); continue }
            val original = originals[route.requestId]
            val related = snapshot.faults.filter { it.deliveryId == frame.delivery.deliveryId }
            if (related.any { it.kind !in KNOWN_FAULTS }) {
                result += ProtocolProblem.Quarantined(ref)
                continue
            }
            accounted += related.map { it.id }
            when (frame.phase) {
                ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED -> {
                    // Actor attests that a control/Host consumer already returned.
                    // For a business sink also require our exact original callback completion.
                    result += if (frame.delivery.origin != ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL || original?.consumerReturned == true)
                        ProtocolProblem.AckOnly(ref, frame.delivery) else ProtocolProblem.ConsumerUnqualified(ref)
                }
                ReaderCoreEventActor.DeliveryPhase.CONSUMER_FAILED -> result += ProtocolProblem.ConsumerUnqualified(ref)
                ReaderCoreEventActor.DeliveryPhase.CONSUMING -> result += ProtocolProblem.Uncertain(ref)
                ReaderCoreEventActor.DeliveryPhase.RETAINED -> when (frame.delivery.origin) {
                    ReaderCoreEventActor.Origin.CONTROL_ERROR -> result += ProtocolProblem.ControlError(ref, frame.delivery)
                    ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL -> if (original == null || original.consumerAttempted)
                        result += ProtocolProblem.ConsumerUnqualified(ref)
                    ReaderCoreEventActor.Origin.HOST_REQUEST -> Unit // Internal Host handler exclusively owns its consumption.
                }
                // The coordinated late-Host actor patch adds QUARANTINED. It
                // retains its raw/frame/old route; this bridge never consumes it.
                else -> result += ProtocolProblem.Quarantined(ref)
            }
        }
        for (summary in snapshot.requests) {
            val original = originals[summary.request.requestId]
            if (original == null) {
                val retainedRoute = routes[summary.request.token]
                if (retainedRoute?.terminalAcked == true && retainedRoute.requestId == summary.request.requestId &&
                    retainedRoute.session == summary.request.runtimeSession) continue // Awaiting exact Host/fault retirement only.
                result += ProtocolProblem.Quarantined(quarantineRef("request:${summary.request.requestId}")); continue
            }
            if (original.plan !== summary.request) {
                result += ProtocolProblem.Quarantined(quarantineRef("request:${summary.request.requestId}")); continue
            }
            if (summary.phase == ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED) {
                val fact = original.nonAdmission
                result += if (fact != null) ProtocolProblem.NonAdmission(original.route.ref(), summary.request, fact)
                    else ProtocolProblem.Quarantined(original.route.ref())
                accounted += snapshot.faults.filter { it.requestId == summary.request.requestId && it.kind in NON_ADMISSION_FAULTS }.map { it.id }
            } else if (summary.phase in setOf(ReaderCoreEventActor.Phase.PREPARED, ReaderCoreEventActor.Phase.PREPARING)) {
                result += ProtocolProblem.Quarantined(original.route.ref())
            } else if (original.failure == "SEND_UNKNOWN" || original.observerPending) result += ProtocolProblem.Uncertain(original.route.ref())
        }
        snapshot.hostOperations.forEach { host ->
            val route = routes[host.envelope.requestToken]
            if (route == null || route.session != host.envelope.runtimeSession || route.requestId != host.envelope.requestId)
                result += ProtocolProblem.Quarantined(quarantineRef("host:${host.envelope.operationId}"))
        }
        val resolvedByRoute = linkedMapOf<Route, MutableSet<String>>()
        for (fault in snapshot.faults) {
            if (fault.id in accounted) continue
            val route = faultRoutes[fault.id]
            if (route == null || fault.kind !in KNOWN_FAULTS) {
                result += ProtocolProblem.Quarantined(quarantineRef("fault:${fault.id}")); continue
            }
            val ref = route.ref(fault.deliveryId)
            when {
                fault.resolved -> resolvedByRoute.getOrPut(route) { linkedSetOf() }.add(fault.id)
                fault.kind == "ORIGINAL_CONSUMER" -> result += ProtocolProblem.ConsumerUnqualified(ref)
                fault.kind in setOf("SEND_UNKNOWN", "READ_CANCEL") -> result += ProtocolProblem.Uncertain(ref)
                else -> result += ProtocolProblem.Quarantined(ref)
            }
        }
        resolvedByRoute.forEach { (route, ids) -> result += ProtocolProblem.Resolved(route.ref(), ids.toSet(), route.nonAdmissionRecorded) }
        snapshot.unparsedOriginals.forEachIndexed { index, _ -> result += ProtocolProblem.Quarantined(quarantineRef("unparsed:$index")) }
        return result.distinct()
    }

    private fun synchronizeRoutes(snapshot: ReaderCoreEventActor.Snapshot) {
        // These indexes cover only the current actor snapshot. Historical small
        // routes stay reachable by exact identity, never via a full-history scan.
        val framesByDelivery = snapshot.deliveries.associateBy { it.delivery.deliveryId }
        val hostsByControl = snapshot.hostOperations.filter { it.controlRequestId != null }.associateBy { it.controlRequestId }
        val activeRequestIds = snapshot.requests.mapTo(linkedSetOf()) { it.request.requestId }
        snapshot.deliveries.forEach { frame -> deliveryRoute(frame.delivery)?.let { activeRequestIds.add(it.requestId) } }
        snapshot.hostOperations.forEach { host -> routes[host.envelope.requestToken]?.takeIf {
            it.session == host.envelope.runtimeSession && it.requestId == host.envelope.requestId
        }?.let { route -> route.hostRelated = true; activeRequestIds.add(route.requestId) } }
        val currentFaultIds = snapshot.faults.mapTo(linkedSetOf()) { it.id }
        val priorFaults = faultRoutes.entries.iterator()
        while (priorFaults.hasNext()) {
            val entry = priorFaults.next()
            if (entry.key !in currentFaultIds) {
                entry.value.faultIds.remove(entry.key) // Touch only the original route linked to that disappeared fault.
                priorFaults.remove()
            }
        }
        for (fault in snapshot.faults) {
            val frame = fault.deliveryId?.let(framesByDelivery::get)
            val controlHost = fault.requestId?.let(hostsByControl::get)
            val route = frame?.let { deliveryRoute(it.delivery) }
                ?: controlHost?.let { host -> routes[host.envelope.requestToken]?.takeIf {
                    it.session == host.envelope.runtimeSession && it.requestId == host.envelope.requestId
                } }
                ?: fault.requestId?.let(requestRoutes::get)
                ?: faultRoutes[fault.id] // A resolved fault may outlive its frame/request, but retains this exact prior binding.
            if (route != null) {
                route.faultIds.add(fault.id)
                faultRoutes[fault.id] = route
            }
        }
        // Only currently unresolved large originals are considered for release.
        // Successful history is represented by small indexed routes, not this collection.
        originals.values.toList().forEach { original ->
            val route = original.route
            if (route.requestId !in activeRequestIds && route.faultIds.isEmpty() && !route.hostRelated &&
                original.plan.kind == ReaderCoreEventActor.Kind.READ_ONLY && original.terminalSink == null) {
                originals.remove(route.requestId, original)
                maintenanceFailures.remove(route.key)
                // Keep every small route for exact late-frame ownership; growth remains OPEN.
            }
        }
    }
    override fun acknowledgementCompleted(ref: CoreProtocolReference, delivery: ReaderCoreEventActor.Delivery) {
        val route = route(ref) ?: error("ORIGINAL_ACK_ROUTE_MISSING")
        owner.requireHeldLane()
        check(matches(route, delivery) && ref.deliveryId == delivery.deliveryId)
        if (delivery.origin == ReaderCoreEventActor.Origin.ORIGINAL_TERMINAL) {
            val original = originals[route.requestId] ?: error("ORIGINAL_ACK_CONSUMER_MISSING")
            check(original.consumerReturned && original.retainedDelivery === delivery)
            route.terminalAcked = true
            originals.remove(route.requestId, original) // Drop parameters/callback only; exact fault route survives.
            maintenanceFailures.remove(route.key)
        }
        // Control/Host ACK never retires the original business callback or manufactures its terminal.
    }
    override fun nonAdmissionRecorded(fact: HostNonAdmissionFact) {
        val route = routes[fact.requestToken] ?: error("NON_ADMISSION_ROUTE_MISSING")
        owner.requireHeldLane()
        check(route.key == fact.key && route.session == fact.runtimeSession && route.requestId == fact.requestId)
        route.nonAdmissionRecorded = true
        originals.remove(route.requestId) // Original business journal intent is deliberately untouched.
    }
    private fun quarantineRef(id: String, delivery: ReaderCoreEventActor.Delivery? = null) = CoreProtocolReference(
        actor.runtimeSession, "actor:$id", null, delivery?.requestToken, delivery?.deliveryId)
    private fun pending(original: Original, stage: CoreProtocolStage, cause: Throwable? = null) = CoreProtocolPending(
        original.route.ref(original.retainedDelivery?.deliveryId), stage, cause)
    private companion object {
        val NON_ADMISSION_FAULTS = setOf("SEND_NOT_ADMITTED", "BEFORE_SEND_NOT_DISPATCHED", "ADMISSION_CLOSED_BEFORE_NATIVE")
        val KNOWN_FAULTS = NON_ADMISSION_FAULTS + setOf("DELIVERY_ACK", "ORIGINAL_CONSUMER", "SEND_UNKNOWN", "READ_CANCEL",
            "HOST_CONTROL_ERROR", "HOST_HANDLER_UNAVAILABLE", "HOST_OUTCOME_UNKNOWN", "CONTROL_NOT_ADMITTED", "CONTROL_SEND_UNKNOWN")
    }
}

internal data class HostNonAdmissionFact(
    val id: String, val key: ActorCoreTransport.OriginalKey,
    val runtimeSession: String, val requestId: Long, val requestToken: String,
    val method: String, val parametersSha256: String, val reason: String,
    val status: ReaderCoreEventActor.NativeStatus?,
) {
    companion object {
        fun from(key: ActorCoreTransport.OriginalKey, plan: ReaderCoreEventActor.PreparedRequest,
            rejected: ReaderCoreEventActor.DispatchOutcome.Rejected) = HostNonAdmissionFact(
            "core-rejection-${plan.runtimeSession}-${plan.requestId}", key, plan.runtimeSession, plan.requestId, plan.token,
            plan.method, MessageDigest.getInstance("SHA-256").digest(plan.parametersJson.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }, rejected.reason, rejected.status,
        )
    }
}

internal fun interface HostNonAdmissionSink { fun record(fact: HostNonAdmissionFact) }

/** Separate Host protocol evidence, using the sole shared journal. Never replaces the business intent. */
internal class HostNonAdmissionJournalSink(private val journal: DurableJournal) : HostNonAdmissionSink {
    override fun record(fact: HostNonAdmissionFact) {
        val diagnostic = fact.status?.diagnostic
        journal.write(JSONObject().put("journalSchema", 1).put("kind", "coreHostNonAdmission")
            .put("id", fact.id).put("completed", true).put("runtimeSession", fact.runtimeSession)
            .put("requestId", fact.requestId).put("requestToken", fact.requestToken)
            .put("method", fact.method).put("parametersSha256", fact.parametersSha256).put("reason", fact.reason)
            .put("ownerId", fact.key.lease.ownerId.toString()).put("clientId", fact.key.lease.clientId.toString())
            .put("ownerGeneration", fact.key.lease.ownerGeneration).put("localGeneration", fact.key.lease.localGeneration)
            .put("nativeStatus", fact.status?.status ?: JSONObject.NULL)
            .put("nativeOperation", fact.status?.operation ?: JSONObject.NULL)
            .put("diagnosticCode", diagnostic?.code ?: JSONObject.NULL)
            .put("diagnosticMessage", diagnostic?.message ?: JSONObject.NULL))
    }
}
