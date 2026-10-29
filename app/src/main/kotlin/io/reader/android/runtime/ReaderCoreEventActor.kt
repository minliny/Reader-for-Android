package io.reader.android.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Unwired A0 foundation. The future 1fe adapter owns the native handle; ONLY this
 * actor may call its RuntimePort. No dependency on the currently pinned a8 SDK.
 *
 * prepare -> caller/whole-turn durable beforeSend -> one dispatch. Consumers run
 * on their caller, never in the poll actor. A retained original is handed to an
 * active observer or to a later original-owner maintenance turn via changes +
 * snapshot; no maintenance coroutine may become a second native poller.
 *
 * Port methods are synchronous/nonblocking (poll is a zero-timeout poll). send
 * must capture status + last_error immediately on the SAME native call thread.
 * No port method may wait for a business gate, UI thread, or Host handler.
 *
 * OPEN RESOURCE LIMIT: retired originals retain minimal identities until close;
 * successful controls retain theirs until exact control-error ACK or close. Their COUNT is not bounded by
 * Core delivery credits once earlier events were ACKed. Fixed1fe has no success
 * control event and may publish a control error after the primary terminal, so
 * no TTL/count eviction is justified. This unwired draft does not claim bounded
 * long-running A4 operation; a finite identity lifecycle is a separate adoption
 * condition. Do not solve it by dropping old evidence or changing Rust here.
 */
internal class ReaderCoreEventActor(
    private val port: RuntimePort,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val hostHandler: HostHandler? = null,
    private val hostDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = 20,
) {
    interface RuntimePort {
        fun deliveryCapabilities(): Int
        fun enableEventAcknowledgements(): NativeStatus
        fun send(commandJson: String): NativeStatus
        fun poll(): String?
        fun cancel(requestId: Long): NativeStatus
        fun acknowledge(deliveryId: Long): NativeStatus
        /** Core-owned response assets; the default fails closed for old bindings. */
        fun assetBegin(requestId: Long, operationId: Long, declaredBytes: Long): Long =
            error("CORE_ASSET_BRIDGE_UNAVAILABLE")
        fun assetWrite(requestId: Long, operationId: Long, assetId: Long, bytes: ByteArray): Long =
            error("CORE_ASSET_BRIDGE_UNAVAILABLE")
        fun assetCommit(requestId: Long, operationId: Long, assetId: Long): Long =
            error("CORE_ASSET_BRIDGE_UNAVAILABLE")
        fun assetReadChunk(requestId: Long, operationId: Long, assetId: Long,
            offset: Long, limit: Int): ByteArray = error("CORE_ASSET_BRIDGE_UNAVAILABLE")
        fun assetRelease(requestId: Long, operationId: Long, assetId: Long): Unit =
            error("CORE_ASSET_BRIDGE_UNAVAILABLE")
        fun close()
    }

    data class Diagnostic(val code: Int, val message: String)
    data class NativeStatus(val operation: String, val status: Int, val diagnostic: Diagnostic? = null)
    enum class Kind { READ_ONLY, DURABLE_WRITE }
    enum class Origin { ORIGINAL_TERMINAL, HOST_REQUEST, CONTROL_ERROR }
    enum class Phase { PREPARED, PREPARING, SENT, SEND_UNKNOWN, REJECTION_NOT_RECORDED, TERMINAL_RETAINED }
    enum class DeliveryPhase { RETAINED, CONSUMING, CONSUMER_FAILED, CONSUMED_ACK_FAILED, QUARANTINED }
    enum class LateHostDisposition { CANCELLED_ORIGINAL_DISCARDED, QUARANTINED }

    class PreparedRequest internal constructor(
        val runtimeSession: String, val requestId: Long, val token: String,
        val method: String, val parametersJson: String, val kind: Kind,
    ) {
        internal val terminal = CompletableDeferred<Delivery>()
    }

    /** Immutable wire bytes as received, not a shared mutable JSONObject. */
    class Delivery internal constructor(
        val runtimeSession: String, val deliveryId: Long, val requestId: Long,
        val requestToken: String, val origin: Origin, val rawEvent: String,
        internal val identity: String = UUID.randomUUID().toString(),
    )

    sealed interface DispatchOutcome {
        data class Accepted(val request: PreparedRequest) : DispatchOutcome
        data class Rejected(val request: PreparedRequest, val reason: String, val status: NativeStatus?) : DispatchOutcome
        data class Unknown(val request: PreparedRequest, val status: NativeStatus?, val rootClass: String?) : DispatchOutcome
    }
    sealed interface Observation {
        data class Terminal(val delivery: Delivery) : Observation
        data class ReadExpired(val request: PreparedRequest) : Observation
        data class WritePending(val request: PreparedRequest) : Observation
    }
    data class Fault(
        val id: String, val kind: String, val requestId: Long?, val deliveryId: Long?,
        val status: NativeStatus?, val rootClass: String?, val resolved: Boolean = false,
        val hostOperationId: Long? = null,
    )
    sealed interface Acknowledgement {
        data object Succeeded : Acknowledgement
        data class Failed(val fault: Fault) : Acknowledgement
    }
    data class HostEnvelope(
        val runtimeSession: String, val requestId: Long, val requestToken: String,
        val operationId: Long, val capability: String, val parametersJson: String,
    )
    sealed interface HostReply {
        data class Complete(val resultJson: String) : HostReply
        data class Error(val code: String, val message: String, val retryable: Boolean) : HostReply
        /** Immutable bytes captured by a Host; the actor owns Core asset transfer. */
        data class AssetTransfer(
            val chunks: List<ByteArray>,
            val contentType: String? = null,
            val width: Int? = null,
            val height: Int? = null,
        ) : HostReply
    }
    fun interface HostHandler { suspend fun execute(original: HostEnvelope): HostReply }
    data class RequestSummary(val request: PreparedRequest, val phase: Phase, val readRetired: Boolean)
    /** Historical identity only: never retains PreparedRequest, params, terminal raw or consumer. */
    data class RetiredOriginalRoute(
        val runtimeSession: String, val requestId: Long, val requestToken: String, val kind: Kind,
        val terminalDeliveryId: Long, val terminalType: String, val terminalErrorCode: String?,
        val terminalAcknowledged: Boolean,
        val lateHostDiscardCount: Long = 0, val lateHostAckCount: Long = 0,
    )
    data class RetainedDelivery(val delivery: Delivery, val phase: DeliveryPhase,
        val retiredOriginalRoute: RetiredOriginalRoute? = null, val lateHostDisposition: LateHostDisposition? = null)
    data class HostSummary(
        val envelope: HostEnvelope, val phase: String, val fixedResponse: HostReply?, val controlRequestId: Long?,
    )
    data class Snapshot(
        val admissionOpen: Boolean, val requests: List<RequestSummary>, val deliveries: List<RetainedDelivery>,
        val hostOperations: List<HostSummary>, val faults: List<Fault>, val unparsedOriginals: List<String>,
        val controlRoutingIdentityCount: Int,
        val retiredOriginalRouteCount: Int = 0,
    )
    data class CloseReport(
        val retained: Snapshot, val nativeCloseFailure: String?, val hostHandlersDrained: Boolean,
        // The fixed Rust Drop closes its callback gate but detaches worker handles.
        val nativeWorkerJoinConfirmed: Boolean = false,
    )

    private data class RequestState(val plan: PreparedRequest, var phase: Phase = Phase.PREPARED,
        var readRetired: Boolean = false, var terminalDelivery: Long? = null, var terminalAcked: Boolean = false,
        var terminalType: String? = null, var terminalErrorCode: String? = null)
    private data class Route(val requestId: Long, val requestToken: String)
    private data class Frame(val delivery: Delivery, val primary: Route,
        var phase: DeliveryPhase = DeliveryPhase.RETAINED, var claim: String? = null,
        val lateHostDisposition: LateHostDisposition? = null)
    private data class HostKey(val requestId: Long, val operationId: Long)
    private data class HostWork(val envelope: HostEnvelope, val deliveries: MutableList<Long>,
        var phase: String = "RUNNING", var job: Job? = null, var response: HostReply? = null, var controlId: Long? = null)
    // Successful control calls have NO success event. Keep only identity tombstones
    // to route possible late control errors; never expose them as waiting for success.
    private data class Control(val id: Long, val primary: Route, val key: HostKey)

    val runtimeSession: String = UUID.randomUUID().toString()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mailbox = Channel<() -> Unit>(Channel.BUFFERED)
    private val changed = Channel<Unit>(Channel.CONFLATED)
    val changes: ReceiveChannel<Unit> get() = changed
    private val initialized = CompletableDeferred<Unit>()
    private val closed = CompletableDeferred<CloseReport>()
    private val waitingCalls = ConcurrentHashMap.newKeySet<CompletableDeferred<*>>()
    private val requests = linkedMapOf<Long, RequestState>()
    // OPEN like control history: exact routes persist until close; no TTL/count eviction.
    private val retiredOriginalRoutes = linkedMapOf<Long, RetiredOriginalRoute>()
    private val frames = linkedMapOf<Long, Frame>()
    private val hosts = linkedMapOf<HostKey, HostWork>()
    private val controls = linkedMapOf<Long, Control>()
    private val faults = linkedMapOf<String, Fault>()
    private val unparsed = mutableListOf<String>()
    private var nextId = 1_000L
    private var closing = false
    private var nativeClosed = false
    private var pollingSuspended = false

    init {
        require(pollIntervalMs in 1..100)
        scope.launch {
            try {
                check(port.deliveryCapabilities() and 1 != 0) { "DELIVERY_ACK_UNAVAILABLE" }
                val selected = port.enableEventAcknowledgements()
                check(selected.operation == "rc_runtime_enable_event_ack" && selected.status == 0) { "DELIVERY_ACK_NOT_ENABLED" }
                initialized.complete(Unit)
                var idle = false
                while (!nativeClosed) {
                    val action = if (idle) receiveOrIdleTick()
                        else mailbox.tryReceive().getOrNull()
                    action?.invoke()
                    if (closing && frames.values.none { it.phase == DeliveryPhase.CONSUMING }) finishClose()
                    if (nativeClosed) break
                    val received = if (pollingSuspended) false else pollOnce()
                    idle = action == null && !received
                    if (!idle) yield()
                }
            } catch (error: Throwable) {
                initialized.completeExceptionally(error)
                fail("ACTOR_STOPPED", root = error)
                finishClose()
            } finally {
                mailbox.close()
                changed.close()
                scope.cancel()
            }
        }
    }

    suspend fun awaitReady() = initialized.await()

    /** Typed same-port asset action. Never await a business gate from this actor.
     * The caller's App-owned whole turn stays alive until the synchronous call
     * really returns, even if its UI observer was cancelled meanwhile. */
    internal suspend fun readEbookAsset(request: ReaderEbookNativeAssetRead): ByteArray = withContext(NonCancellable) {
        call {
            requireAdmission()
            request.requireNativeAdmission(runtimeSession) // Full lease and short actual-admission fence immediately before Native.
            val assets = port as? ReaderEbookBinaryRuntimePort ?: error("BINARY_ASSET_PORT_UNAVAILABLE")
            val bytes = assets.readEpubEntry(request.authorizedArchivePath, request.canonicalEntry, request.maximumBytes)
            request.requireNativeAdmission(runtimeSession) // Retirement discards output, never cancels Native.
            check(bytes.size.toLong() <= request.maximumBytes) { "NATIVE_ASSET_BOUND_EXCEEDED" }
            bytes
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun receiveOrIdleTick(): (() -> Unit)? = select {
        // A timer clause does NOT cancel a destructive receive. withTimeout's
        // prompt cancellation can otherwise remove a queued action and lose
        // it before returning to this actor, stranding its Deferred forever.
        mailbox.onReceiveCatching { it.getOrNull() }
        onTimeout(pollIntervalMs) { null }
    }

    suspend fun prepare(method: String, parametersJson: String = "{}", kind: Kind): PreparedRequest = call {
        requireAdmission()
        require(method.isNotBlank() && method !in setOf("host.complete", "host.error", "runtime.cancel", "runtime.shutdown"))
        JSONObject(parametersJson) // Existing JSON parser validates object shape; preserve caller's exact immutable string.
        PreparedRequest(runtimeSession, allocateId(), UUID.randomUUID().toString(), method, parametersJson, kind).also {
            requests[it.requestId] = RequestState(it)
            notifyChanged()
        }
    }

    /** beforeSend executes on the caller/whole-turn owner, outside the actor. */
    suspend fun dispatch(plan: PreparedRequest, beforeSend: suspend (PreparedRequest) -> Unit): DispatchOutcome {
        currentCoroutineContext().ensureActive()
        var reserved = false
        try {
            // The acquired lease must never be lost between enqueue and await
            // cancellation. Assignment occurs inside the non-cancellable boundary.
            withContext(NonCancellable) {
                call { requireAdmission(); state(plan).also { check(it.phase == Phase.PREPARED); it.phase = Phase.PREPARING } }
                reserved = true
            }
            currentCoroutineContext().ensureActive()
            beforeSend(plan)
            currentCoroutineContext().ensureActive()
        } catch (error: Throwable) {
            if (!reserved) throw error
            val rejected = withContext(NonCancellable) { call { rejectBeforeNative(plan, error) } }
            if (error is CancellationException) throw error
            return rejected
        }
        // Once the original durable intent exists, caller cancellation cannot lose
        // the one dispatch's admission fact. The surrounding whole turn owns it.
        return withContext(NonCancellable) { call { sendOriginal(plan) } }
    }

    suspend fun observe(plan: PreparedRequest, timeoutMs: Long): Observation {
        require(timeoutMs >= 0)
        try {
            call { check(state(plan).phase in setOf(Phase.SENT, Phase.SEND_UNKNOWN, Phase.TERMINAL_RETAINED)) { "NO_ORIGINAL_WIRE_EXPECTED" } }
            val delivery = withTimeoutOrNull(timeoutMs) { plan.terminal.await() }
            if (delivery != null) return Observation.Terminal(delivery)
            if (plan.kind == Kind.READ_ONLY) { cancelRead(plan); return Observation.ReadExpired(plan) }
            return Observation.WritePending(plan)
        } catch (cancelled: CancellationException) {
            if (plan.kind == Kind.READ_ONLY) withContext(NonCancellable) { cancelRead(plan) }
            throw cancelled
        }
    }

    /** Exact no-Native outcome for a cancelled pure observer whose plan never acquired dispatch reservation. */
    suspend fun rejectPreparedReadObserver(plan: PreparedRequest): DispatchOutcome.Rejected = call {
        check(plan.kind == Kind.READ_ONLY && state(plan).phase == Phase.PREPARED) { "READ_OBSERVER_ALREADY_RESERVED_OR_SENT" }
        rejectBeforeNative(plan, CancellationException("READ_OBSERVER_RETIRED_BEFORE_SEND"))
    }

    /** cancel=0 may be a no-op; it never creates a terminal or retires raw events. */
    suspend fun cancelRead(plan: PreparedRequest) = call {
        require(plan.kind == Kind.READ_ONLY) { "DURABLE_WRITE_CANNOT_BE_CANCELLED_BY_OBSERVER" }
        val state = state(plan)
        if (!state.readRetired) {
            state.readRetired = true
            hosts.filterKeys { it.requestId == plan.requestId }.values.forEach { it.job?.cancel() }
            try {
                val status = port.cancel(plan.requestId)
                if (status.operation != "rc_runtime_cancel" || status.status != 0) fail("READ_CANCEL", plan.requestId, status = status)
            } catch (error: Throwable) { fail("READ_CANCEL", plan.requestId, root = error) }
            notifyChanged()
        }
    }

    /** The block must consume/persist THIS original wire. It runs only on caller context. */
    suspend fun consume(delivery: Delivery, consumer: suspend (Delivery) -> Unit): Acknowledgement {
        var claim: String? = null
        try {
            withContext(NonCancellable) {
                claim = call {
                    val frame = frame(delivery)
                    check(delivery.origin != Origin.HOST_REQUEST) { "HOST_DELIVERY_OWNED_BY_HOST_HANDLER" }
                    check(frame.phase == DeliveryPhase.RETAINED || frame.phase == DeliveryPhase.CONSUMER_FAILED) { "ORIGINAL_ALREADY_CONSUMED_OR_CLAIMED" }
                    UUID.randomUUID().toString().also { frame.claim = it; frame.phase = DeliveryPhase.CONSUMING }
                }
            }
            currentCoroutineContext().ensureActive()
            consumer(delivery)
        }
        catch (error: Throwable) {
            if (claim != null) withContext(NonCancellable) { call {
                val frame = frame(delivery); check(frame.claim == claim)
                frame.claim = null; frame.phase = DeliveryPhase.CONSUMER_FAILED
                fail("ORIGINAL_CONSUMER", delivery.requestId, delivery.deliveryId, root = error)
            } }
            throw error
        }
        return withContext(NonCancellable) { call {
            val frame = frame(delivery); check(frame.claim == claim)
            frame.claim = null
            acknowledgeConsumed(frame)
        } }
    }

    /** Explicit ACK-only retry: NEVER re-executes the business consumer or Host I/O. */
    suspend fun retryAcknowledgement(delivery: Delivery): Acknowledgement = call {
        val frame = frame(delivery)
        check(frame.phase == DeliveryPhase.CONSUMED_ACK_FAILED) { "ORIGINAL_NOT_CONSUMED" }
        acknowledgeConsumed(frame)
    }

    /** Caller must first durably record the separate known non-admission outcome. */
    suspend fun admissionRejectionRecorded(plan: PreparedRequest) = call {
        check(state(plan).phase == Phase.REJECTION_NOT_RECORDED)
        resolveRequestFaults(plan.requestId, setOf("SEND_NOT_ADMITTED", "BEFORE_SEND_NOT_DISPATCHED", "ADMISSION_CLOSED_BEFORE_NATIVE"))
        requests.remove(plan.requestId)
        notifyChanged()
    }

    /** Recovery does not automatically reopen admission; the owner explicitly accepts resolved fault IDs. */
    suspend fun resumeAdmission(resolvedFaultIds: Set<String>) = call {
        check(!closing && resolvedFaultIds.isNotEmpty())
        check(hosts.values.none { it.phase in setOf("AWAITING_OWNER", "CONTROL_ERROR", "CONTROL_NOT_ADMITTED", "CONTROL_SEND_UNKNOWN") }) {
            "ORIGINAL_HOST_OUTCOME_UNRESOLVED"
        }
        resolvedFaultIds.forEach { check(faults[it]?.resolved == true) { "UNRESOLVED_OR_FOREIGN_FAULT" } }
        resolvedFaultIds.forEach(faults::remove)
        notifyChanged()
    }

    suspend fun retryPolling() = call {
        check(pollingSuspended)
        pollingSuspended = false
        val completed = pollOnce()
        if (!pollingSuspended) faults.replaceAll { _, fault -> if (fault.kind == "NATIVE_POLL") fault.copy(resolved = true) else fault }
        completed
    }

    suspend fun snapshot(): Snapshot = call { snapshotOnActor() }

    /** O(1) immutable identity inspection; no admission, Native call or route registration. */
    suspend fun inspectRetiredOriginal(requestId: Long, requestToken: String): RetiredOriginalRoute? = call {
        retiredOriginalRoutes[requestId]?.takeIf { it.requestToken == requestToken }
    }

    /** Explicit owner recovery of an unhandled/uncertain Host outcome, not another I/O execution. */
    suspend fun supplyOriginalHostResponse(envelope: HostEnvelope, response: HostReply) = call {
        val work = hosts[HostKey(envelope.requestId, envelope.operationId)] ?: error("ORIGINAL_HOST_MISSING")
        check(work.envelope == envelope && work.phase == "AWAITING_OWNER" && work.response == null)
        work.response = freezeReply(response)
        sendHostResponse(work)
    }

    /**
     * Explicit maintenance only AFTER the actual original terminal was consumed
     * and ACKed. A fresh read, observer timeout or control error cannot qualify.
     * Uncertain Host I/O without a fixed outcome remains blocked for its owner.
     */
    suspend fun retireHostAfterOriginalTerminal(envelope: HostEnvelope) = call {
        val key = HostKey(envelope.requestId, envelope.operationId)
        val work = hosts[key] ?: error("ORIGINAL_HOST_MISSING")
        val original = requests[envelope.requestId] ?: error("ORIGINAL_HOST_REQUEST_OWNER_MISSING")
        check(work.envelope == envelope && original.plan.token == envelope.requestToken && original.terminalAcked)
        check(work.job == null && work.response != null && work.phase in
            setOf("CONTROL_ERROR", "CONTROL_NOT_ADMITTED", "CONTROL_SEND_UNKNOWN")) { "ORIGINAL_HOST_OUTCOME_UNRESOLVED" }
        check(frames.values.none { it.delivery.origin == Origin.CONTROL_ERROR && it.delivery.requestId == work.controlId }) {
            "ORIGINAL_CONTROL_ERROR_NOT_CONSUMED"
        }
        work.phase = "ORIGINAL_TERMINAL_RECORDED"
        work.deliveries.toList().forEach { frames[it]?.let(::acknowledgeConsumed) }
        retireIfFinished(envelope.requestId)
        notifyChanged()
    }

    /** Call only after the whole-turn owner drains business work. In-flight consumers finish before destroy. */
    suspend fun close(): CloseReport {
        if (!closed.isCompleted) {
            try { mailbox.send { closing = true; notifyChanged() } }
            catch (_: Exception) { /* initialization/actor failure already owns close */ }
        }
        return closed.await()
    }

    private suspend fun <T> call(block: () -> T): T {
        initialized.await()
        check(!closed.isCompleted) { "EVENT_ACTOR_CLOSED" }
        val result = CompletableDeferred<T>()
        waitingCalls.add(result)
        try {
            mailbox.send {
                waitingCalls.remove(result)
                try { result.complete(block()) }
                catch (error: Throwable) { result.completeExceptionally(error) }
            }
            return result.await()
        } finally {
            waitingCalls.remove(result)
        }
    }

    private fun requireAdmission() { check(!closing && faults.isEmpty()) { "EVENT_ACTOR_ADMISSION_BLOCKED" } }
    private fun state(plan: PreparedRequest): RequestState = requests[plan.requestId]?.also {
        check(plan.runtimeSession == runtimeSession && it.plan === plan) { "FOREIGN_ORIGINAL_REQUEST" }
    } ?: error("ORIGINAL_REQUEST_NOT_RETAINED")
    private fun frame(delivery: Delivery): Frame = frames[delivery.deliveryId]?.also {
        check(delivery.runtimeSession == runtimeSession && it.delivery === delivery) { "FOREIGN_ORIGINAL_DELIVERY" }
    } ?: error("ORIGINAL_DELIVERY_NOT_RETAINED")
    private fun allocateId(): Long { check(nextId < Long.MAX_VALUE); return nextId++ }
    private fun notifyChanged() { changed.trySend(Unit) }
    private fun fail(kind: String, requestId: Long? = null, deliveryId: Long? = null,
        status: NativeStatus? = null, root: Throwable? = null, operationId: Long? = null): Fault =
        Fault(UUID.randomUUID().toString(), kind, requestId, deliveryId, status, root?.javaClass?.simpleName,
            hostOperationId = operationId).also {
            faults[it.id] = it; notifyChanged()
        }
    private fun resolveDeliveryFaults(deliveryId: Long) {
        faults.replaceAll { _, fault -> if (fault.deliveryId == deliveryId && fault.kind != "HOST_CONTROL_ERROR")
            fault.copy(resolved = true) else fault }
    }
    private fun resolveRequestFaults(requestId: Long, kinds: Set<String>) {
        faults.replaceAll { _, fault -> if (fault.requestId == requestId && fault.kind in kinds)
            fault.copy(resolved = true) else fault }
    }
    private fun resolveHostOutcomeFaults(envelope: HostEnvelope) {
        faults.replaceAll { _, fault -> if (fault.requestId == envelope.requestId &&
            fault.hostOperationId == envelope.operationId && fault.kind in setOf("HOST_HANDLER_UNAVAILABLE", "HOST_OUTCOME_UNKNOWN"))
            fault.copy(resolved = true) else fault }
    }
    private fun rejectBeforeNative(plan: PreparedRequest, error: Throwable): DispatchOutcome.Rejected {
        state(plan).phase = Phase.REJECTION_NOT_RECORDED
        fail("BEFORE_SEND_NOT_DISPATCHED", plan.requestId, root = error)
        return DispatchOutcome.Rejected(plan, "BEFORE_SEND_NOT_DISPATCHED", null)
    }
    private fun sendOriginal(plan: PreparedRequest): DispatchOutcome {
        val request = state(plan)
        check(request.phase == Phase.PREPARING) { "ORIGINAL_ALREADY_DISPATCHED" }
        if (closing || faults.isNotEmpty()) {
            request.phase = Phase.REJECTION_NOT_RECORDED
            fail("ADMISSION_CLOSED_BEFORE_NATIVE", plan.requestId)
            return DispatchOutcome.Rejected(plan, "ADMISSION_CLOSED_BEFORE_NATIVE", null)
        }
        return try {
            val status = port.send(command(plan.method, plan.requestId, plan.parametersJson))
            when {
                status.operation == "rc_runtime_send" && status.status == 0 -> {
                    request.phase = Phase.SENT; DispatchOutcome.Accepted(plan)
                }
                knownPreAdmission(status) -> {
                    request.phase = Phase.REJECTION_NOT_RECORDED
                    fail("SEND_NOT_ADMITTED", plan.requestId, status = status)
                    DispatchOutcome.Rejected(plan, "SEND_NOT_ADMITTED", status)
                }
                else -> {
                    request.phase = Phase.SEND_UNKNOWN
                    fail("SEND_UNKNOWN", plan.requestId, status = status)
                    DispatchOutcome.Unknown(plan, status, null)
                }
            }
        } catch (error: Throwable) {
            request.phase = Phase.SEND_UNKNOWN
            fail("SEND_UNKNOWN", plan.requestId, root = error)
            DispatchOutcome.Unknown(plan, null, error.javaClass.simpleName)
        }.also { notifyChanged() }
    }

    private fun pollOnce(): Boolean {
        return try {
            val raw = port.poll() ?: return false
            // Retain bytes BEFORE parsing. Malformed/unowned originals remain here.
            unparsed.add(raw)
            try { receive(raw); unparsed.removeAt(unparsed.lastIndex) }
            catch (error: Throwable) { fail("WIRE_CONTRACT", root = error) }
            true
        } catch (error: Throwable) {
            pollingSuspended = true
            fail("NATIVE_POLL", root = error)
            false
        }
    }

    private fun receive(raw: String) {
        val event = JSONObject(raw)
        check(integer(event, "protocolVersion") == 1L)
        val deliveryId = integer(event, "deliveryId").also { check(it > 0 && it !in frames) }
        val requestId = integer(event, "requestId").also { check(it > 0) }
        val type = event.opt("type") as? String ?: error("EVENT_TYPE_REQUIRED")
        val control = controls[requestId]
        if (control != null) {
            check(type == "error" && event.opt("error") is JSONObject) { "CONTROL_HAS_NO_SUCCESS_EVENT" }
            val delivery = Delivery(runtimeSession, deliveryId, requestId, control.primary.requestToken, Origin.CONTROL_ERROR, raw)
            frames[deliveryId] = Frame(delivery, control.primary)
            hosts[control.key]?.phase = "CONTROL_ERROR"
            fail("HOST_CONTROL_ERROR", requestId, deliveryId, operationId = control.key.operationId)
            notifyChanged()
            return
        }
        val request = requests[requestId]
        if (request == null) {
            val retired = retiredOriginalRoutes[requestId] ?: error("UNOWNED_EVENT")
            receiveRetiredHost(retired, event, type, deliveryId, raw)
            return
        }
        check(request.phase in setOf(Phase.SENT, Phase.SEND_UNKNOWN, Phase.TERMINAL_RETAINED)) { "EVENT_BEFORE_OR_WITHOUT_SEND" }
        when (type) {
            "result", "error" -> {
                check(event.opt(if (type == "result") "data" else "error") is JSONObject)
                check(request.terminalDelivery == null) { "DUPLICATE_ORIGINAL_TERMINAL" }
                val delivery = Delivery(runtimeSession, deliveryId, requestId, request.plan.token, Origin.ORIGINAL_TERMINAL, raw)
                frames[deliveryId] = Frame(delivery, Route(requestId, request.plan.token))
                request.terminalDelivery = deliveryId; request.phase = Phase.TERMINAL_RETAINED
                request.terminalType = type
                request.terminalErrorCode = if (type == "error") event.getJSONObject("error").opt("code") as? String else null
                request.plan.terminal.complete(delivery)
            }
            "host.request" -> {
                val operationId = integer(event, "operationId").also { check(it > 0) }
                val capability = event.opt("capability") as? String ?: error("HOST_CAPABILITY_REQUIRED")
                val params = event.opt("params") as? JSONObject ?: error("HOST_PARAMS_REQUIRED")
                val delivery = Delivery(runtimeSession, deliveryId, requestId, request.plan.token, Origin.HOST_REQUEST, raw)
                val envelope = HostEnvelope(runtimeSession, requestId, request.plan.token, operationId, capability, params.toString())
                val key = HostKey(requestId, operationId)
                val previous = hosts[key]
                check(previous == null || previous.envelope == envelope) { "HOST_OPERATION_ID_REBOUND" }
                frames[deliveryId] = Frame(delivery, Route(requestId, request.plan.token))
                if (previous != null) {
                    previous.deliveries.add(deliveryId)
                    if (previous.phase == "RESPONSE_ACCEPTED_NO_SUCCESS_EVENT" || previous.phase == "READ_RETIRED")
                        acknowledgeConsumed(frames.getValue(deliveryId))
                } else {
                    val work = HostWork(envelope, mutableListOf(deliveryId))
                    hosts[key] = work
                    if (request.readRetired) { work.phase = "READ_RETIRED"; acknowledgeConsumed(frames.getValue(deliveryId)) }
                    else startHost(work)
                }
            }
            else -> error("UNSUPPORTED_EVENT_TYPE")
        }
        notifyChanged()
    }

    private fun receiveRetiredHost(route: RetiredOriginalRoute, event: JSONObject, type: String,
        deliveryId: Long, raw: String) {
        check(route.runtimeSession == runtimeSession && type == "host.request") { "RETIRED_ORIGINAL_FRAME_CONTRACT" }
        val operationId = integer(event, "operationId").also { check(it > 0) }
        check(event.opt("capability") is String && event.opt("params") is JSONObject)
        val discarded = route.terminalAcknowledged && route.terminalType == "error" &&
            route.terminalErrorCode == "CANCELLED" && deliveryId < route.terminalDeliveryId
        val disposition = if (discarded) LateHostDisposition.CANCELLED_ORIGINAL_DISCARDED else LateHostDisposition.QUARANTINED
        val delivery = Delivery(runtimeSession, deliveryId, route.requestId, route.requestToken, Origin.HOST_REQUEST, raw)
        val frame = Frame(delivery, Route(route.requestId, route.requestToken),
            phase = if (discarded) DeliveryPhase.RETAINED else DeliveryPhase.QUARANTINED,
            lateHostDisposition = disposition)
        frames[deliveryId] = frame
        if (discarded) {
            // The original cancellation already ended Core's Host operation.
            // This internal owner records discard before ACK, without Host I/O,
            // any new client callback, or a fabricated host.complete success.
            retiredOriginalRoutes[route.requestId] = route.copy(lateHostDiscardCount = nextDiagnosticCount(route.lateHostDiscardCount))
            acknowledgeConsumed(frame) // Failure retains exact raw/route/consumed phase for ACK-only recovery.
        } else {
            // Other terminal outcomes or impossible delivery order prove no discard qualification.
            fail("LATE_HOST_QUARANTINED", route.requestId, deliveryId, operationId = operationId)
        }
        notifyChanged()
    }

    private fun startHost(work: HostWork) {
        val handler = hostHandler
        if (handler == null) {
            work.phase = "AWAITING_OWNER"
            fail("HOST_HANDLER_UNAVAILABLE", work.envelope.requestId, operationId = work.envelope.operationId)
            return
        }
        var response: HostReply? = null
        var failure: Throwable? = null
        val job = scope.launch(hostDispatcher, start = CoroutineStart.LAZY) {
            try { response = freezeReply(handler.execute(work.envelope)) }
            catch (error: Throwable) { failure = error }
        }
        work.job = job
        // Completion runs even when cancellation wins before the Host coroutine
        // starts. A finally inside an unstarted coroutine would never run.
        job.invokeOnCompletion { stopped ->
            scope.launch(hostDispatcher + NonCancellable) {
                try { mailbox.send { finishHost(work.envelope, response, failure ?: stopped) } }
                catch (_: Exception) { /* closed report retains this unresolved Host identity */ }
            }
        }
        job.start()
    }
    private fun finishHost(envelope: HostEnvelope, response: HostReply?, error: Throwable?) {
        val work = hosts[HostKey(envelope.requestId, envelope.operationId)] ?: return
        check(work.envelope == envelope && work.phase == "RUNNING")
        work.job = null
        if (requests[envelope.requestId]?.readRetired == true) {
            work.phase = "READ_RETIRED"
            work.deliveries.toList().forEach { frames[it]?.let(::acknowledgeConsumed) }
        } else if (error != null || response == null) {
            work.phase = "AWAITING_OWNER"
            fail("HOST_OUTCOME_UNKNOWN", envelope.requestId, root = error, operationId = envelope.operationId)
        } else {
            work.response = response
            sendHostResponse(work)
        }
        notifyChanged()
    }
    private fun sendHostResponse(work: HostWork) {
        check(work.controlId == null) { "HOST_RESPONSE_ALREADY_DISPATCHED" }
        val original = requests[work.envelope.requestId]?.plan ?: error("ORIGINAL_HOST_REQUEST_OWNER_MISSING")
        val id = allocateId()
        work.controlId = id
        controls[id] = Control(id, Route(original.requestId, original.token), HostKey(work.envelope.requestId, work.envelope.operationId))
        val reply = work.response ?: error("FIXED_HOST_RESPONSE_REQUIRED")
        val params = JSONObject().put("operationId", work.envelope.operationId)
        val method = when (reply) {
            is HostReply.Complete -> { params.put("result", JSONObject(reply.resultJson)); "host.complete" }
            is HostReply.Error -> { params.put("error", JSONObject().put("code", reply.code).put("message", reply.message)
                .put("retryable", reply.retryable)); "host.error" }
            is HostReply.AssetTransfer -> {
                try {
                    params.put("result", transferAsset(work.envelope, reply))
                    "host.complete"
                } catch (error: Throwable) {
                    params.put("error", JSONObject().put("code", "INTERNAL")
                        .put("message", "MANGA_ASSET_TRANSFER_FAILED")
                        .put("retryable", false))
                    "host.error"
                }
            }
        }
        try {
            val status = port.send(command(method, id, params.toString()))
            if (status.operation == "rc_runtime_send" && status.status == 0) {
                work.phase = "RESPONSE_ACCEPTED_NO_SUCCESS_EVENT"
                resolveHostOutcomeFaults(work.envelope)
                // The handler actually finished and its fixed response was admitted.
                // This is not an ACK on a queue-to-queue move, nor business success.
                work.deliveries.toList().forEach { frames[it]?.let(::acknowledgeConsumed) }
            } else {
                work.phase = if (knownPreAdmission(status)) "CONTROL_NOT_ADMITTED" else "CONTROL_SEND_UNKNOWN"
                fail(work.phase, id, status = status)
            }
        } catch (error: Throwable) {
            work.phase = "CONTROL_SEND_UNKNOWN"
            fail(work.phase, id, root = error)
        }
        notifyChanged()
    }

    /** Runs only on the poll actor thread; Host handlers never receive RuntimePort. */
    private fun transferAsset(envelope: HostEnvelope, reply: HostReply.AssetTransfer): JSONObject {
        val chunks = reply.chunks
        check(chunks.isNotEmpty()) { "MANGA_ASSET_EMPTY" }
        val total = chunks.sumOf { it.size.toLong() }
        check(total in 1..(128L * 1024 * 1024)) { "MANGA_ASSET_TOTAL_BUDGET" }
        val assetId = port.assetBegin(envelope.requestId, envelope.operationId, total)
        check(assetId > 0) { "MANGA_ASSET_BEGIN_ID" }
        var committed = false
        try {
            var written = 0L
            chunks.forEach { chunk ->
                check(chunk.size <= 1024 * 1024) { "MANGA_ASSET_CHUNK_BUDGET" }
                val next = port.assetWrite(envelope.requestId, envelope.operationId, assetId, chunk)
                written += chunk.size
                check(next == written) { "MANGA_ASSET_WRITE_RECEIPT" }
            }
            check(port.assetCommit(envelope.requestId, envelope.operationId, assetId) == total) {
                "MANGA_ASSET_COMMIT_RECEIPT"
            }
            committed = true
            return JSONObject().put("assetId", assetId)
                .put("operationId", envelope.operationId).put("bytes", total)
                .apply {
                    reply.contentType?.let { put("contentType", it) }
                    reply.width?.let { put("width", it) }
                    reply.height?.let { put("height", it) }
                }
        } finally {
            if (!committed) runCatching {
                port.assetRelease(envelope.requestId, envelope.operationId, assetId)
            }
        }
    }

    private fun acknowledgeConsumed(frame: Frame): Acknowledgement {
        // Prepare optional diagnostic metadata before ACK. Count saturation is
        // not an event qualification and cannot turn a successful ACK into failure.
        val acknowledgedRoute = if (frame.lateHostDisposition == LateHostDisposition.CANCELLED_ORIGINAL_DISCARDED) {
            val route = retiredOriginalRoutes.getValue(frame.primary.requestId)
            check(route.requestToken == frame.primary.requestToken)
            route.copy(lateHostAckCount = nextDiagnosticCount(route.lateHostAckCount))
        } else null
        // Internal Host consumption and explicit caller consumption share this
        // transition. Core ACK is NOT idempotent; never call it after success.
        frame.phase = DeliveryPhase.CONSUMED_ACK_FAILED
        return try {
            val status = port.acknowledge(frame.delivery.deliveryId)
            if (status.operation != "rc_runtime_ack_event" || status.status != 0)
                Acknowledgement.Failed(fail("DELIVERY_ACK", frame.delivery.requestId, frame.delivery.deliveryId, status))
            else {
                if (acknowledgedRoute != null) retiredOriginalRoutes[acknowledgedRoute.requestId] = acknowledgedRoute
                frames.remove(frame.delivery.deliveryId)
                resolveDeliveryFaults(frame.delivery.deliveryId)
                if (frame.delivery.origin == Origin.ORIGINAL_TERMINAL) {
                    requests[frame.primary.requestId]?.terminalAcked = true
                    // Actual original terminal consumption proves a failed read
                    // cancellation is no longer waiting, unlike cancel(status0).
                    resolveRequestFaults(frame.primary.requestId, setOf("SEND_UNKNOWN", "READ_CANCEL"))
                } else if (frame.delivery.origin == Origin.CONTROL_ERROR) {
                    controls.remove(frame.delivery.requestId)
                    if (requests[frame.primary.requestId] == null)
                        resolveRequestFaults(frame.delivery.requestId, setOf("HOST_CONTROL_ERROR"))
                }
                retireIfFinished(frame.primary.requestId)
                notifyChanged()
                Acknowledgement.Succeeded
            }
        } catch (error: Throwable) {
            Acknowledgement.Failed(fail("DELIVERY_ACK", frame.delivery.requestId, frame.delivery.deliveryId, root = error))
        }
    }
    private fun retireIfFinished(requestId: Long) {
        val request = requests[requestId] ?: return
        if (!request.terminalAcked || frames.values.any { it.primary.requestId == requestId && it.primary.requestToken == request.plan.token } ||
            hosts.any { it.key.requestId == requestId && it.value.phase !in
                setOf("RESPONSE_ACCEPTED_NO_SUCCESS_EVENT", "READ_RETIRED", "ORIGINAL_TERMINAL_RECORDED") }) return
        hosts.filterKeys { it.requestId == requestId }.values.forEach { work ->
            if (work.phase == "ORIGINAL_TERMINAL_RECORDED") resolveHostOutcomeFaults(work.envelope)
            work.controlId?.let { resolveRequestFaults(it, setOf("HOST_CONTROL_ERROR", "CONTROL_NOT_ADMITTED", "CONTROL_SEND_UNKNOWN")) }
        }
        val terminalId = checkNotNull(request.terminalDelivery)
        val terminalType = checkNotNull(request.terminalType)
        check(requestId !in retiredOriginalRoutes) { "ORIGINAL_REQUEST_ID_REBOUND" }
        retiredOriginalRoutes[requestId] = RetiredOriginalRoute(runtimeSession, requestId, request.plan.token,
            request.plan.kind, terminalId, terminalType, request.terminalErrorCode, terminalAcknowledged = true)
        requests.remove(requestId)
        hosts.entries.removeAll { it.key.requestId == requestId }
        // Small original/control identities survive; parameters/consumer/terminal raw do not.
    }
    // Pure diagnostic counters saturate; they never grant or revoke discard/ACK eligibility.
    private fun nextDiagnosticCount(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

    private fun snapshotOnActor() = Snapshot(!closing && faults.isEmpty(),
        requests.values.map { RequestSummary(it.plan, it.phase, it.readRetired) },
        frames.values.map { RetainedDelivery(it.delivery, it.phase,
            retiredOriginalRoutes[it.primary.requestId]?.takeIf { route -> route.requestToken == it.primary.requestToken }, it.lateHostDisposition) },
        hosts.values.map { HostSummary(it.envelope, it.phase, it.response, it.controlId) }, faults.values.toList(), unparsed.toList(), controls.size, retiredOriginalRoutes.size)
    private fun finishClose() {
        if (nativeClosed) return
        closing = true
        val drained = hosts.values.none { it.phase == "RUNNING" }
        val retained = snapshotOnActor()
        hosts.values.forEach { it.job?.cancel() }
        var failure: String? = null
        try { port.close() } catch (error: Throwable) { failure = error.javaClass.simpleName }
        nativeClosed = true
        closed.complete(CloseReport(retained, failure, drained))
        val error = IllegalStateException("EVENT_ACTOR_CLOSED")
        requests.values.forEach { it.plan.terminal.completeExceptionally(error) }
        mailbox.close()
        waitingCalls.toList().forEach { it.completeExceptionally(error) }
        waitingCalls.clear()
        while (mailbox.tryReceive().isSuccess) { /* reject queued commands; never execute after destroy */ }
        controls.clear()
        retiredOriginalRoutes.clear()
    }

    companion object {
        /** Fixed 1fe FFI pre-admission domains; protocol/panic/foreign errors stay unknown. */
        fun knownPreAdmission(status: NativeStatus): Boolean = status.operation == "rc_runtime_send" &&
            when (status.status) {
                3 -> status.diagnostic?.code == 2 || status.diagnostic?.code == 5
                5 -> status.diagnostic?.code == 9
                6 -> status.diagnostic?.code == 10
                else -> false
            }
        private fun command(method: String, id: Long, params: String) = JSONObject().put("protocolVersion", 1)
            .put("requestId", id).put("method", method).put("params", JSONObject(params)).toString()
        private fun integer(value: JSONObject, key: String): Long = when (val number = value.opt(key)) {
            is Byte, is Short, is Int, is Long -> (number as Number).toLong()
            // Binding IDs are positive signed longs. Never wrap a wider u64 or
            // coerce a JSON float/string; preserve that original as a fault.
            else -> error("EXACT_EVENT_INTEGER_REQUIRED:$key")
        }
        private fun freezeReply(reply: HostReply): HostReply = when (reply) {
            is HostReply.Complete -> { JSONObject(reply.resultJson); reply.copy() }
            is HostReply.Error -> {
                require(reply.code in setOf("UNKNOWN_METHOD", "INVALID_PARAMS", "INVALID_PROTOCOL_VERSION", "CANCELLED", "TIMEOUT",
                    "INVALID_MESSAGE", "INTERNAL", "TRANSACTION_PENDING", "BUSY", "RESOURCE_EXHAUSTED", "BOOK_ALREADY_ON_SHELF"))
                reply.copy()
            }
            is HostReply.AssetTransfer -> {
                val chunks = reply.chunks.map { chunk ->
                    require(chunk.size <= 1024 * 1024) { "MANGA_ASSET_CHUNK_BUDGET" }
                    chunk.copyOf()
                }
                require(chunks.isNotEmpty()) { "MANGA_ASSET_EMPTY" }
                require(chunks.sumOf { it.size.toLong() } <= 128L * 1024 * 1024) {
                    "MANGA_ASSET_TOTAL_BUDGET"
                }
                require(reply.width == null || reply.width > 0) { "MANGA_ASSET_WIDTH" }
                require(reply.height == null || reply.height > 0) { "MANGA_ASSET_HEIGHT" }
                reply.copy(chunks = chunks)
            }
        }
    }
}
