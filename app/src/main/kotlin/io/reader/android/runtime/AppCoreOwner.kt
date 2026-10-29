package io.reader.android.runtime

import android.app.Application
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    private val readObservers = ReaderReadObservers()
    private val publicationLease = AtomicReference<ReaderOperationOwner.ClientLease?>()
    private val applicationContext = application.applicationContext
    private val ebookAssets = ReaderEbookAssetRegistry()
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
        val transport = ActorCoreTransport(this, actor, HostNonAdmissionJournalSink(journal), CoreReadRequestLane { lease ->
            requireLane(lease)
            lane.get()!!.observer
        })
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

    // One shared service transport resolves ONLY the actually held lane, including old-original maintenance.
    // Construction has no session access and never fabricates a lease or creates Native.
    private val serviceTransport: CoreTransport = object : CoreTransport {
        private fun bound(): CoreTransport {
            requireHeldLane()
            lane.get()!!.observer?.ensureActive()
            return bridgeDelegate.value.forClient(lane.get()!!.lease)
        }
        override val session get() = bound().session
        override fun request(method: String, params: JSONObject, timeoutMs: Long, beforeSend: (Long) -> Unit,
            terminal: ((JSONObject) -> Unit)?) = bound().request(method, params, timeoutMs, beforeSend, terminal)
        override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = bound().drainOriginalTerminal(requestId, timeoutMs)
        override fun close() { error("SHARED_SERVICE_CANNOT_CLOSE_NATIVE") }
    }
    private val bookmarkService by lazy { requireHeldLane(); ReaderBookmarks(serviceTransport, journal) }
    private val managementService by lazy { requireHeldLane(); ReaderBookshelfManagement(serviceTransport, journal) }

    internal class Client internal constructor(internal val owner: AppCoreOwner, val lease: ReaderOperationOwner.ClientLease) {
        private val retired = AtomicBoolean(false)
        val root get() = owner.root
        val originals get() = owner.originals
        val staging get() = owner.staging
        val journal get() = owner.journal
        val bookmarks: ReaderBookmarks get() { owner.requireLane(lease); return owner.bookmarkService }
        val management: ReaderBookshelfManagement get() { owner.requireLane(lease); return owner.managementService }
        // Constructing a Library/client/status observer does not create Native.
        val transport: CoreTransport = object : CoreTransport {
            private fun bound(): CoreTransport {
                owner.requireLane(lease)
                owner.lane.get()!!.observer?.ensureActive()
                return owner.bridgeDelegate.value.forClient(lease)
            }
            override val session get() = bound().session
            override fun request(method: String, params: JSONObject, timeoutMs: Long,
                beforeSend: (Long) -> Unit, terminal: ((JSONObject) -> Unit)?) =
                bound().request(method, params, timeoutMs, beforeSend, terminal)
            override fun drainOriginalTerminal(requestId: Long, timeoutMs: Long) = bound().drainOriginalTerminal(requestId, timeoutMs)
            override fun close() { error("CLIENT_TRANSPORT_CANNOT_CLOSE_NATIVE") }
        }
        suspend fun <T> wholeTurn(mode: WholeTurnMode, block: suspend ReaderOperationOwner.Turn.() -> T,
            protectedFinally: suspend ReaderOperationOwner.Turn.() -> Unit): T {
            check(!retired.get()) { "LIBRARY_CLIENT_RETIRED" }
            if (mode == WholeTurnMode.RETAIN_ORIGINAL) return owner.operations.withTurn(lease) {
                val exactTurn = this
                withContext(Dispatchers.IO + owner.lane.asContextElement(
                    Lane(lease, Purpose.Business, { exactTurn.assertActive() }, exactTurn),
                )) {
                    try { block(exactTurn) }
                    finally { try { protectedFinally(exactTurn) } finally { owner.publishIfInitialized() } }
                }
            }
            val caller = currentCoroutineContext()[Job] ?: error("READ_OBSERVER_REQUIRES_CALLER_JOB")
            val observer = owner.readObservers.open(lease, caller)
            return try {
                owner.operations.withObservedRead(lease, observer, body = {
                    val exactTurn = this
                    withContext(Dispatchers.IO + owner.lane.asContextElement(
                        Lane(lease, Purpose.Business, { exactTurn.assertActive() }, exactTurn, observer),
                    )) { block(exactTurn) }
                }, protectedFinally = {
                    val exactTurn = this
                    withContext(Dispatchers.IO + owner.lane.asContextElement(
                        Lane(lease, Purpose.TurnFinally, { exactTurn.assertActive() }, exactTurn),
                    )) { try { protectedFinally(exactTurn) } finally { owner.publishIfInitialized() } }
                })
            } finally { observer.closeObservation() }
        }
        /** Real owner authority; safe for UI/IO observation, not permission to dispatch a native action. */
        fun assertCurrentAdmission() {
            check(!retired.get()) { "LIBRARY_CLIENT_RETIRED" }
            owner.operations.assertCurrentAdmission(lease)
        }
        fun requireEbookCurrent(render: io.reader.android.runtime.ebookdraft.EbookRenderLease) {
            assertCurrentAdmission()
            check(render.ownerId == lease.ownerId && render.clientId == lease.clientId &&
                render.ownerGeneration == lease.ownerGeneration) { "FOREIGN_EBOOK_CLIENT" }
            check(owner.actorDelegate.isInitialized()) { "EBOOK_RUNTIME_NOT_PREPARED" }
            // isInitialized + immutable UUID read cannot create Native or call the actor/mailbox.
            check(owner.actorDelegate.value.runtimeSession == render.runtimeSession) { "FOREIGN_EBOOK_RUNTIME" }
            assertCurrentAdmission()
        }

        /** Called only inside the Library's already-admitted presentation turn.
         * sourceBytes must come from the original finalized import/retained source
         * authority. No WebView/SAF path or unverified file length grants authority. */
        fun authorizeEbookAssets(render: io.reader.android.runtime.ebookdraft.EbookRenderLease,
            sourceBytes: Long, guard: io.reader.android.runtime.ebookdraft.EbookLeaseGuard): ReaderEbookOwnedPublication {
            owner.requireBusinessLane(lease)
            check(render.ownerId == lease.ownerId && render.clientId == lease.clientId &&
                render.ownerGeneration == lease.ownerGeneration && render.runtimeSession == owner.actor.runtimeSession)
            guard.requireCurrent(render)
            val original = io.reader.android.runtime.ebookdraft.AuthorizedEbookOriginal.authorize(
                owner.applicationContext, render.bookId, sourceBytes)
            val permit = owner.ebookAssets.issue(render, lease, owner.publicationLease, retired) { assertCurrentAdmission() }
            val assets = ReaderEbookOwnedAssets(render, original, permit, guard, object : ReaderEbookAssetTurn {
                override suspend fun read(original: ReaderEbookNativeAssetRead): ByteArray {
                    // WebView shouldInterceptRequest is a separate IO callback.
                    // Re-entering this whole-turn mutex from its own lane would deadlock.
                    check(owner.lane.get() == null) { "NESTED_EBOOK_RESOURCE_OWNER_TURN" }
                    return wholeTurn(WholeTurnMode.RETAIN_ORIGINAL,
                        block = { owner.actor.readEbookAsset(original) }, protectedFinally = {})
                }
            })
            return ReaderEbookOwnedPublication(original, assets)
        }

        fun retire() {
            if (retired.compareAndSet(false, true)) {
                owner.readObservers.retire(lease) // Direct Job cancellation; never waits for the read's gate.
                owner.operations.retireClient(lease)
            }
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
        readObservers.activate(lease) // Monotonic owner generation prevents a delayed old activation.
        publicationLease.updateAndGet { current ->
            if (current == null || current.ownerGeneration < lease.ownerGeneration) lease else current
        } // Monotonic mirror only; operations remains the admission authority.
        return Client(this, lease)
    }
    private enum class Purpose { Business, TurnFinally, Maintenance, Cleanup }
    private class Lane(val lease: ReaderOperationOwner.ClientLease, val purpose: Purpose,
        val assertHeld: () -> Unit, val turn: ReaderOperationOwner.Turn?,
        val observer: ReaderReadObservers.ReadObserver? = null)
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
        if (lane.get()!!.purpose != Purpose.Business) check(lane.get()!!.purpose in setOf(Purpose.Cleanup, Purpose.TurnFinally) &&
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
    fun closeForExplicitOwner(): kotlinx.coroutines.Deferred<ReaderOperationOwner.CloseState> {
        ebookAssets.retire()
        return operations.close()
    }
    fun lastActorCloseReport(): ReaderCoreEventActor.CloseReport? = lastActorClose
}
