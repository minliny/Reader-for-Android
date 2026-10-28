package io.reader.android.runtime

import android.app.Application
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Manifest Application owns one directory gate, journal, actor and native transport for the process. */
internal class AppCoreOwner(application: Application, private val recoveryFeed: CoreProtocolRecoveryFeed) : CoreTransportLane {
    val root = File(application.filesDir, "Reader").canonicalFile
    val originals = File(root, "Originals")
    val staging = File(root, "ImportStaging")
    val journal by lazy { DurableJournal(File(root, "Operations")) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lane = ThreadLocal<Lane?>()
    private val publicationLease = AtomicReference<ReaderOperationOwner.ClientLease?>()
    private val actorDelegate = lazy {
        ReaderCoreEventActor(
            NativeCoreRuntimePort(File(root, "Core"), File(application.cacheDir, "ReaderCore")),
            hostHandler = ReaderCoreEventActor.HostHandler {
                ReaderCoreEventActor.HostReply.Error("UNKNOWN_METHOD",
                    "Android capability is not available in this product slice", false)
            },
        )
    }
    private val actor by actorDelegate
    @Volatile private var recoveryOwner: CoreProtocolRecoveryOwner? = null
    @Volatile private var lastActorClose: ReaderCoreEventActor.CloseReport? = null
    // The directory is claimed before any lazy bridge can cause Native handle creation.
    // Native destroy does not prove worker join: this directory is never released/reopened here.
    private val operations = ReaderOperationOwner.create(File(root, "Core"), closeAction = {
        if (actorDelegate.isInitialized()) {
            val report = actor.close()
            lastActorClose = report
            recoveryOwner?.publishClosedFromOwner(report)
        }
    })
    private val bridgeDelegate = lazy {
        val transport = ActorCoreTransport(this, actor, HostNonAdmissionJournalSink(journal))
        val protocolLane = object : ProtocolRecoveryLane {
            override fun assertHeld() = requireHeldLane()
            override suspend fun <T> exclusive(ref: CoreProtocolReference, block: suspend () -> T): T {
                // Only a real private route can authorize an action. Global/foreign
                // quarantine presentations intentionally have no action and no lease.
                val exactLease = transport.routeLease(ref) ?: error("ORIGINAL_PROTOCOL_ROUTE_MISSING")
                return originalMaintenance(exactLease, ref.originalId) {
                    transport.requireRoute(ref) // Revalidate AFTER obtaining the whole-turn gate.
                    block()
                }
            }
        }
        val recovery = CoreProtocolRecoveryOwner(actor, protocolLane, transport, JournalProtocolEvidence(journal), appScope, recoveryFeed)
        recoveryOwner = recovery // Feed attaches only now, never from status observation.
        startMaintenance(transport, recovery)
        transport
    }

    internal class Client internal constructor(internal val owner: AppCoreOwner, val lease: ReaderOperationOwner.ClientLease) {
        private val retired = AtomicBoolean(false)
        val root get() = owner.root
        val originals get() = owner.originals
        val staging get() = owner.staging
        val journal get() = owner.journal
        // Constructing a Library/client/status observer does not create Native.
        val transport: CoreTransport = object : CoreTransport {
            private fun bound(): CoreTransport {
                owner.requireLane(lease)
                return owner.bridgeDelegate.value.forClient(lease)
            }
            override val session get() = bound().session
            override fun request(method: String, params: JSONObject, timeoutMs: Long,
                beforeSend: (Long) -> Unit, terminal: ((JSONObject) -> Unit)?) =
                bound().request(method, params, timeoutMs, beforeSend, terminal)
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = bound().drainOriginalTerminal(requestId, timeoutMs)
            override fun close() { error("CLIENT_TRANSPORT_CANNOT_CLOSE_NATIVE") }
        }
        suspend fun <T> wholeTurn(block: suspend ReaderOperationOwner.Turn.() -> T): T {
            check(!retired.get()) { "LIBRARY_CLIENT_RETIRED" }
            return owner.operations.withTurn(lease) {
                val exactTurn = this
                withContext(Dispatchers.IO + owner.lane.asContextElement(
                    Lane(lease, Purpose.Business, { exactTurn.assertActive() }, exactTurn),
                )) {
                    try { block(exactTurn) }
                    finally { owner.publishIfInitialized() }
                }
            }
        }
        fun retire() {
            if (retired.compareAndSet(false, true)) owner.operations.retireClient(lease)
            // An admitted whole turn, its original callbacks and finally survive UI cancellation/retirement.
        }
        fun currentTurn(): ReaderOperationOwner.Turn {
            owner.requireBusinessLane(lease)
            return owner.lane.get()!!.turn ?: error("NO_BUSINESS_TURN")
        }
        fun registerSearchCleanup(turn: ReaderOperationOwner.Turn, sessionId: String, closeOriginal: suspend () -> Unit) {
            val id = "search:$sessionId"
            check(turn.lease == lease)
            turn.registerRetiringCleanup(id) {
                withContext(Dispatchers.IO + owner.lane.asContextElement(
                    Lane(lease, Purpose.Cleanup, { owner.operations.assertCleanupActive(lease, id) }, null),
                )) {
                    try { closeOriginal() } finally { owner.publishIfInitialized() }
                }
            }
        }
    }
    fun newClient(clientId: UUID = UUID.randomUUID(), localGeneration: Long = 0): Client {
        val lease = operations.activateClient(clientId, localGeneration)
        publicationLease.set(lease) // An actually issued lease, never a made-up entity/book owner.
        return Client(this, lease)
    }
    private enum class Purpose { Business, Maintenance, Cleanup }
    private class Lane(val lease: ReaderOperationOwner.ClientLease, val purpose: Purpose,
        val assertHeld: () -> Unit, val turn: ReaderOperationOwner.Turn?)
    override fun requireHeldLane() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "CORE_TRANSPORT_ON_MAIN" }
        val held = lane.get() ?: error("CORE_TRANSPORT_OUTSIDE_OWNER_LANE")
        held.assertHeld()
    }
    override fun requireLane(lease: ReaderOperationOwner.ClientLease) {
        requireHeldLane()
        check(lane.get()!!.lease == lease) { "FOREIGN_LIBRARY_CLIENT_LANE" }
    }
    internal fun requireBusinessLane(lease: ReaderOperationOwner.ClientLease) {
        requireLane(lease)
        check(lane.get()!!.purpose == Purpose.Business) { "NEW_BUSINESS_REQUIRES_ADMITTED_TURN" }
    }
    override fun requireRequestLane(lease: ReaderOperationOwner.ClientLease, method: String, params: JSONObject) {
        requireLane(lease)
        if (lane.get()!!.purpose != Purpose.Business) check(lane.get()!!.purpose == Purpose.Cleanup &&
            method == "search.content" && params.optBoolean("closeCursor") && params.optString("cursor").isNotBlank()) {
            "MAINTENANCE_CANNOT_DISPATCH_NEW_BUSINESS"
        }
    }
    override fun protocolReference(): CoreProtocolReference {
        requireHeldLane()
        check(actorDelegate.isInitialized())
        return CoreProtocolReference(actor.runtimeSession, "actor:${actor.runtimeSession}", null, null)
    }
    private suspend fun publishIfInitialized() {
        requireHeldLane()
        val recovery = recoveryOwner ?: return
        try { recovery.refreshOnHeldLane() }
        catch (_: Exception) { recovery.publishUnavailableFromOwner() }
        // Observation failure never dispatches Core, resumes admission or masks a business failure.
    }
    /** Sole actor.changes receiver. UI observes the projected StateFlow, never the channel. */
    private fun startMaintenance(transport: ActorCoreTransport, recovery: CoreProtocolRecoveryOwner) {
        appScope.launch {
            for (ignored in actor.changes) {
                val actualLease = publicationLease.get() ?: continue // Bridge creation requires a client lane, so normally present.
                val targets = try {
                    originalMaintenance(actualLease, "actor:${actor.runtimeSession}") {
                        transport.maintenanceKeys(actor.snapshot())
                    }
                } catch (_: Exception) {
                    recovery.publishUnavailableFromOwner()
                    continue
                }
                for (key in targets) {
                    try {
                        originalMaintenance(key.lease, key.originalId) {
                            try { transport.consumeFirstRetained(key) }
                            catch (error: Exception) { transport.retainMaintenanceFailure(key, error) }
                        }
                    } catch (_: Exception) { /* Closing gate admits no further maintenance. Original actor frames stay retained. */ }
                }
                try {
                    originalMaintenance(actualLease, "actor:${actor.runtimeSession}") { recovery.refreshOnHeldLane() }
                } catch (_: Exception) { recovery.publishUnavailableFromOwner() }
            }
        }
    }
    private suspend fun <T> originalMaintenance(lease: ReaderOperationOwner.ClientLease, originalId: String,
        block: suspend () -> T): T = operations.withOriginalMaintenance(lease, originalId) {
        val exact = this
        withContext(Dispatchers.IO + lane.asContextElement(Lane(lease, Purpose.Maintenance, { exact.assertActive() }, null))) { block() }
    }
    fun closeForExplicitOwner() = operations.close()
    fun lastActorCloseReport(): ReaderCoreEventActor.CloseReport? = lastActorClose
}
