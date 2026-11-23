package io.reader.android.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic owner/protocol tests only, NOT native/Core business evidence.
 * Wire shapes are fixed1fe runtime.ping and runtime.hostSmoke/host.smoke.echo.
 * No book, URL, socket, network or platform storage fixture is invented.
 */
class ReaderCoreEventActorTest {
    private class DiagnosticPort : ReaderCoreEventActor.RuntimePort {
        val commands = ConcurrentLinkedQueue<String>()
        val acknowledgements = ConcurrentLinkedQueue<Long>()
        val cancels = ConcurrentLinkedQueue<Long>()
        val calls = ConcurrentLinkedQueue<Pair<String, String>>()
        val callThreads = ConcurrentLinkedQueue<Thread>()
        private val events = ConcurrentLinkedQueue<String>()
        @Volatile var sendStatus: (JSONObject) -> ReaderCoreEventActor.NativeStatus = { ok("rc_runtime_send") }
        @Volatile var ackStatus: (Long) -> ReaderCoreEventActor.NativeStatus = { ok("rc_runtime_ack_event") }
        private fun record(call: String) {
            val actual = Thread.currentThread()
            calls.add(call to actual.name)
            callThreads.add(actual)
        }
        fun emit(event: String) { events.add(event) }
        override fun deliveryCapabilities(): Int { record("capabilities"); return 1 }
        override fun enableEventAcknowledgements(): ReaderCoreEventActor.NativeStatus { record("enable"); return ok("rc_runtime_enable_event_ack") }
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus {
            record("send"); commands.add(commandJson); return sendStatus(JSONObject(commandJson))
        }
        override fun poll(): String? { record("poll"); return events.poll() }
        override fun cancel(requestId: Long): ReaderCoreEventActor.NativeStatus {
            record("cancel"); cancels.add(requestId); return ok("rc_runtime_cancel")
        }
        override fun acknowledge(deliveryId: Long): ReaderCoreEventActor.NativeStatus {
            record("ack:$deliveryId"); acknowledgements.add(deliveryId); return ackStatus(deliveryId)
        }
        override fun close() { record("close") }
    }

    private data class Harness(val actor: ReaderCoreEventActor, val port: DiagnosticPort, val executor: ExecutorService,
        val factoryThread: AtomicReference<Thread>)

    private suspend fun harness(
        handler: ReaderCoreEventActor.HostHandler? = null,
        configure: (DiagnosticPort) -> Unit = {},
        block: suspend (Harness) -> Unit,
    ) {
        val port = DiagnosticPort().also(configure)
        val factoryThread = AtomicReference<Thread>()
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "reader-event-owner-test").also {
            check(factoryThread.compareAndSet(null, it)) { "Owner executor unexpectedly replaced its physical thread" }
        } }
        val dispatcher = executor.asCoroutineDispatcher()
        val actor = ReaderCoreEventActor(port, dispatcher, handler, pollIntervalMs = 1)
        try { actor.awaitReady(); withTimeout(5_000) { block(Harness(actor, port, executor, factoryThread)) } }
        finally {
            try { withContext(NonCancellable) { withTimeout(3_000) { actor.close() } } }
            finally { dispatcher.close() }
        }
    }

    private suspend inline fun <reified T : Throwable> fails(noinline block: suspend () -> Unit): T {
        try { block() } catch (error: Throwable) { if (error is T) return error; throw error }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private suspend fun waitFor(actor: ReaderCoreEventActor, predicate: (ReaderCoreEventActor.Snapshot) -> Boolean): ReaderCoreEventActor.Snapshot =
        withTimeout(3_000) {
            var snapshot = actor.snapshot()
            while (!predicate(snapshot)) { actor.changes.receive(); snapshot = actor.snapshot() }
            snapshot
        }

    @Test fun callerOwnsPreparationAndConsumptionWhileIdleActorOwnsEveryPortCall() = runBlocking {
        harness { (actor, port, _, factoryThread) ->
            val caller = Thread.currentThread()
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            val foreign = ReaderCoreEventActor.PreparedRequest("foreign-runtime", plan.requestId, plan.token,
                plan.method, plan.parametersJson, plan.kind)
            fails<IllegalStateException> { actor.dispatch(foreign) {} }
            val outcome = actor.dispatch(plan) {
                assertTrue(caller === Thread.currentThread())
                assertEquals(0, port.commands.size)
                assertEquals("{}", it.parametersJson)
            }
            assertTrue(outcome is ReaderCoreEventActor.DispatchOutcome.Accepted)
            fails<IllegalStateException> { actor.dispatch(plan) {} }
            // No request/drain call drives polling: idle actor sees this original.
            port.emit(ping(plan.requestId, 1))
            val delivery = (actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            assertEquals(ping(plan.requestId, 1), delivery.rawEvent)
            val acknowledged = actor.consume(delivery) {
                assertTrue(caller === Thread.currentThread())
                assertTrue(port.acknowledgements.isEmpty())
            }
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, acknowledged)
            assertEquals(listOf(1L), port.acknowledgements.toList())
            assertTrue(actor.snapshot().requests.isEmpty())
            actor.close()
            assertEquals(listOf("capabilities", "enable"), port.calls.take(2).map { it.first })
            val owner = factoryThread.get()
            val evidence = port.calls.zip(port.callThreads).map { (call, physical) ->
                "${call.first}:threadId=${physical.id}:identity=${System.identityHashCode(physical)}:label=${call.second}"
            }.distinct()
            println("PORT_THREAD_IDENTITY factoryId=${owner.id}:identity=${System.identityHashCode(owner)} calls=$evidence")
            assertTrue("Every Native call must use the exact Thread created by the executor factory: $evidence",
                port.callThreads.isNotEmpty() && port.callThreads.all { it === owner })
            assertEquals(1, port.calls.count { it.first == "close" })
        }
    }

    @Test fun exactSendRejectionsAreSeparateFromWireAndUnknownNeverBecomesRejection() = runBlocking {
        for ((status, code) in listOf(3 to 2, 3 to 5, 5 to 9, 6 to 10)) {
            val rejection = ReaderCoreEventActor.NativeStatus("rc_runtime_send", status, ReaderCoreEventActor.Diagnostic(code, "diagnostic"))
            assertTrue(ReaderCoreEventActor.knownPreAdmission(rejection))
        }
        for (status in listOf(
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", 4, ReaderCoreEventActor.Diagnostic(5, "duplicate")),
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", -1, ReaderCoreEventActor.Diagnostic(6, "panic")),
            ReaderCoreEventActor.NativeStatus("rc_runtime_ack_event", 5, ReaderCoreEventActor.Diagnostic(9, "stale")),
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", 5),
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", 6, ReaderCoreEventActor.Diagnostic(9, "wrong pair")),
        )) assertFalse(ReaderCoreEventActor.knownPreAdmission(status))
        harness(configure = { port -> port.sendStatus = {
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", 5, ReaderCoreEventActor.Diagnostic(9, "busy"))
        } }) { (actor, port, _) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            assertTrue(actor.dispatch(plan) {} is ReaderCoreEventActor.DispatchOutcome.Rejected)
            fails<IllegalStateException> { actor.observe(plan, 0) }
            assertTrue(actor.snapshot().deliveries.isEmpty())
            assertTrue(port.acknowledgements.isEmpty())
            actor.admissionRejectionRecorded(plan)
            val facts = actor.snapshot().faults
            assertTrue(facts.all { it.resolved })
            assertFalse(actor.snapshot().admissionOpen)
            actor.resumeAdmission(facts.map { it.id }.toSet())
            assertTrue(actor.snapshot().admissionOpen)
        }
        harness(configure = { port -> port.sendStatus = {
            ReaderCoreEventActor.NativeStatus("rc_runtime_send", 4, ReaderCoreEventActor.Diagnostic(5, "duplicate"))
        } }) { (actor, _, _) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            assertTrue(actor.dispatch(plan) {} is ReaderCoreEventActor.DispatchOutcome.Unknown)
            assertTrue(actor.observe(plan, 0) is ReaderCoreEventActor.Observation.WritePending)
            fails<IllegalStateException> { actor.admissionRejectionRecorded(plan) }
            assertFalse(actor.snapshot().admissionOpen)
        }
    }

    @Test fun rawEventAndConsumedStateSurviveFailuresWithoutRepeatingBusinessOnAckRetry() = runBlocking {
        val attempts = AtomicInteger()
        harness(configure = { port -> port.ackStatus = {
            if (attempts.getAndIncrement() == 0) ReaderCoreEventActor.NativeStatus("rc_runtime_ack_event", 4,
                ReaderCoreEventActor.Diagnostic(9, "unrelated stale BUSY")) else ok("rc_runtime_ack_event")
        } }) { (actor, port, _) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(plan) {}
            val raw = ping(plan.requestId, 11)
            port.emit(raw)
            val event = (actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            var businessCalls = 0
            fails<IOException> { actor.consume(event) { businessCalls++; throw IOException("durability not confirmed") } }
            assertEquals(raw, actor.snapshot().deliveries.single().delivery.rawEvent)
            assertTrue(port.acknowledgements.isEmpty())
            assertTrue(actor.consume(event) { businessCalls++ } is ReaderCoreEventActor.Acknowledgement.Failed)
            assertEquals(2, businessCalls)
            assertEquals(ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED, actor.snapshot().deliveries.single().phase)
            fails<IllegalStateException> { actor.consume(event) { businessCalls++ } }
            fails<IllegalStateException> { actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY) }
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, actor.retryAcknowledgement(event))
            assertEquals(2, businessCalls)
            assertEquals(listOf(11L, 11L), port.acknowledgements.toList())
            val snapshot = actor.snapshot()
            assertTrue(snapshot.requests.isEmpty() && snapshot.deliveries.isEmpty())
            assertFalse(snapshot.admissionOpen)
            actor.resumeAdmission(snapshot.faults.map { it.id }.toSet())
            assertTrue(actor.snapshot().admissionOpen)
        }
    }

    @Test fun readExpiryCancelsOnlyOriginalReadWhileWriteExpiryKeepsUnknownOwner() = runBlocking {
        harness { (actor, port, _) ->
            val read = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            val write = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(read) {}; actor.dispatch(write) {}
            assertTrue(actor.observe(read, 0) is ReaderCoreEventActor.Observation.ReadExpired)
            assertTrue(actor.observe(write, 0) is ReaderCoreEventActor.Observation.WritePending)
            assertEquals(listOf(read.requestId), port.cancels.toList())
            assertTrue(actor.snapshot().deliveries.isEmpty()) // cancel0 created no fake terminal.
            fails<IllegalArgumentException> { actor.cancelRead(write) }
            port.emit(error(read.requestId, 21, "CANCELLED"))
            val terminal = waitFor(actor) { it.deliveries.any { f -> f.delivery.requestId == read.requestId } }
                .deliveries.single().delivery
            actor.consume(terminal) {}
            val close = actor.close()
            assertEquals(write.requestId, close.retained.requests.single().request.requestId)
            assertFalse(close.nativeWorkerJoinConfirmed)
        }
    }

    @Test fun hostIoDoesNotBlockPumpAndPrimaryAckCannotRetireOlderUnackedHostDelivery() = runBlocking {
        val entered = CompletableDeferred<ReaderCoreEventActor.HostEnvelope>()
        val release = CompletableDeferred<Unit>()
        val handlerCalls = AtomicInteger()
        val primaryId = AtomicLong()
        val hostAckAttempts = AtomicInteger()
        harness(handler = ReaderCoreEventActor.HostHandler { envelope ->
            handlerCalls.incrementAndGet(); primaryId.set(envelope.requestId); entered.complete(envelope); release.await()
            ReaderCoreEventActor.HostReply.Complete("{\"diagnostic\":true}")
        }, configure = { port ->
            port.ackStatus = { id -> if (id == 31L && hostAckAttempts.getAndIncrement() == 0)
                ReaderCoreEventActor.NativeStatus("rc_runtime_ack_event", 4) else ok("rc_runtime_ack_event") }
            port.sendStatus = { command ->
                if (command.getString("method") == "host.complete") {
                    val primary = primaryId.get()
                    port.emit(result(primary, 33, "{\"diagnostic\":true}"))
                }
                ok("rc_runtime_send")
            }
        }) { (actor, port, _) ->
            val original = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(original) {}
            port.emit(host(original.requestId, 31, 7))
            withTimeout(2_000) { entered.await() }
            val handlerOwned = actor.snapshot().deliveries.single().delivery
            var wrongConsumerRan = false
            fails<IllegalStateException> { actor.consume(handlerOwned) { wrongConsumerRan = true } }
            assertFalse(wrongConsumerRan)
            val pingPlan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(pingPlan) {}; port.emit(ping(pingPlan.requestId, 32))
            actor.consume((actor.observe(pingPlan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery) {}
            release.complete(Unit)
            val primary = (actor.observe(original, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            actor.consume(primary) {}
            val snapshot = actor.snapshot()
            assertTrue(snapshot.requests.any { it.request === original })
            val oldHost = snapshot.deliveries.single().delivery
            assertEquals(31L, oldHost.deliveryId)
            assertEquals(ReaderCoreEventActor.Origin.HOST_REQUEST, oldHost.origin)
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, actor.retryAcknowledgement(oldHost))
            assertTrue(actor.snapshot().requests.isEmpty())
            assertEquals(1, handlerCalls.get())
            assertEquals(1, port.commands.count { JSONObject(it).getString("method") == "host.complete" })
        }
    }

    @Test fun controlErrorConsumptionCannotBecomeOriginalTerminalOrReopenAdmission() = runBlocking {
        val handlerCalls = AtomicInteger()
        val envelope = CompletableDeferred<ReaderCoreEventActor.HostEnvelope>()
        harness(handler = ReaderCoreEventActor.HostHandler {
            handlerCalls.incrementAndGet(); envelope.complete(it); ReaderCoreEventActor.HostReply.Complete("{}")
        }, configure = { port -> port.sendStatus = { command ->
            if (command.getString("method") == "host.complete") port.emit(error(command.getLong("requestId"), 42, "INVALID_PARAMS"))
            ok("rc_runtime_send")
        } }) { (actor, port, _) ->
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}; port.emit(host(plan.requestId, 41, 8))
            val snapshot = waitFor(actor) { it.deliveries.any { f -> f.delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR } }
            val control = snapshot.deliveries.single { it.delivery.origin == ReaderCoreEventActor.Origin.CONTROL_ERROR }.delivery
            assertTrue(control.requestId != plan.requestId)
            assertTrue(actor.observe(plan, 0) is ReaderCoreEventActor.Observation.WritePending)
            actor.consume(control) {}
            fails<IllegalStateException> { actor.resumeAdmission(actor.snapshot().faults.map { it.id }.toSet()) }
            fails<IllegalStateException> { actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY) }
            assertEquals(1, handlerCalls.get())
            assertEquals(1, port.commands.count { JSONObject(it).getString("method") == "host.complete" })
            // Only an actual original Core terminal can qualify explicit retirement.
            port.emit(error(plan.requestId, 43, "TIMEOUT"))
            actor.consume((actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery) {}
            actor.retireHostAfterOriginalTerminal(envelope.await())
            val recovered = actor.snapshot()
            assertTrue(recovered.requests.isEmpty())
            actor.resumeAdmission(recovered.faults.map { it.id }.toSet())
            assertTrue(actor.snapshot().admissionOpen)
        }
    }

    @Test fun rejectedControlPinsOneFixedResponseWithoutRepeatingIoOrAcknowledgingHostEvent() = runBlocking {
        for ((status, code) in listOf(5 to 9, 6 to 10)) {
            val handlerCalls = AtomicInteger()
            harness(handler = ReaderCoreEventActor.HostHandler {
                handlerCalls.incrementAndGet(); ReaderCoreEventActor.HostReply.Complete("{\"diagnostic\":true}")
            }, configure = { port -> port.sendStatus = { command ->
                if (command.getString("method") == "host.complete") ReaderCoreEventActor.NativeStatus("rc_runtime_send", status,
                    ReaderCoreEventActor.Diagnostic(code, "not admitted")) else ok("rc_runtime_send")
            } }) { (actor, port, _) ->
                val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
                actor.dispatch(plan) {}; port.emit(host(plan.requestId, 51, 9))
                val snapshot = waitFor(actor) { it.hostOperations.any { h -> h.phase == "CONTROL_NOT_ADMITTED" } }
                assertEquals("{\"diagnostic\":true}", (snapshot.hostOperations.single().fixedResponse as ReaderCoreEventActor.HostReply.Complete).resultJson)
                assertTrue(snapshot.hostOperations.single().controlRequestId != null)
                assertEquals(ReaderCoreEventActor.DeliveryPhase.RETAINED, snapshot.deliveries.single().phase)
                assertTrue(port.acknowledgements.isEmpty())
                assertFalse(snapshot.admissionOpen)
                actor.snapshot() // owner barrier, not another I/O attempt.
                assertEquals(1, handlerCalls.get())
                assertEquals(1, port.commands.count { JSONObject(it).getString("method") == "host.complete" })
            }
        }
        harness(configure = { port -> port.sendStatus = { command ->
            if (command.getString("method") == "host.complete") ReaderCoreEventActor.NativeStatus("rc_runtime_send", 5,
                ReaderCoreEventActor.Diagnostic(9, "not admitted")) else ok("rc_runtime_send")
        } }) { (actor, port, _) ->
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}; port.emit(host(plan.requestId, 52, 10))
            val work = waitFor(actor) { it.hostOperations.any { h -> h.phase == "AWAITING_OWNER" } }.hostOperations.single()
            actor.supplyOriginalHostResponse(work.envelope, ReaderCoreEventActor.HostReply.Complete("{}"))
            assertEquals("CONTROL_NOT_ADMITTED", actor.snapshot().hostOperations.single().phase)
            port.emit(error(plan.requestId, 53, "TIMEOUT"))
            actor.consume((actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery) {}
            actor.retireHostAfterOriginalTerminal(work.envelope)
            val snapshot = actor.snapshot()
            assertTrue(snapshot.faults.all { it.resolved })
            actor.resumeAdmission(snapshot.faults.map { it.id }.toSet())
            assertTrue(actor.snapshot().admissionOpen)
        }
    }

    @Test fun queuedReservationCancellationCannotOrphanConsumeOrDispatchAndCloseRejectsWaiters() = runBlocking {
        harness { (actor, port, executor) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(plan) {}; port.emit(ping(plan.requestId, 61))
            val event = (actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            val blocked = CountDownLatch(1); val release = CountDownLatch(1)
            executor.execute { blocked.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
            assertTrue(blocked.await(2, TimeUnit.SECONDS))
            val consumerCalls = AtomicInteger()
            val consumer = launch(start = CoroutineStart.UNDISPATCHED) { actor.consume(event) { consumerCalls.incrementAndGet() } }
            consumer.cancel(); release.countDown(); consumer.join()
            assertEquals(0, consumerCalls.get())
            assertEquals(ReaderCoreEventActor.DeliveryPhase.CONSUMER_FAILED, actor.snapshot().deliveries.single().phase)
            assertTrue(port.acknowledgements.isEmpty())
            actor.consume(event) {}
            val recovered = actor.snapshot(); actor.resumeAdmission(recovered.faults.map { it.id }.toSet())

            val unsent = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            val blockedAgain = CountDownLatch(1); val releaseAgain = CountDownLatch(1)
            executor.execute { blockedAgain.countDown(); check(releaseAgain.await(2, TimeUnit.SECONDS)) }
            assertTrue(blockedAgain.await(2, TimeUnit.SECONDS))
            val beforeSendCalls = AtomicInteger()
            val sender = launch(start = CoroutineStart.UNDISPATCHED) { actor.dispatch(unsent) { beforeSendCalls.incrementAndGet() } }
            sender.cancel(); releaseAgain.countDown(); sender.join()
            assertEquals(0, beforeSendCalls.get())
            assertEquals(1, port.commands.size)
            assertEquals(ReaderCoreEventActor.Phase.REJECTION_NOT_RECORDED, actor.snapshot().requests.single().phase)
            actor.admissionRejectionRecorded(unsent)
            actor.resumeAdmission(actor.snapshot().faults.filter { it.resolved }.map { it.id }.toSet())

            val observed = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(observed) {}
            val observationBlocked = CountDownLatch(1); val observationRelease = CountDownLatch(1)
            executor.execute { observationBlocked.countDown(); check(observationRelease.await(2, TimeUnit.SECONDS)) }
            assertTrue(observationBlocked.await(2, TimeUnit.SECONDS))
            val observer = launch(start = CoroutineStart.UNDISPATCHED) { actor.observe(observed, 10_000) }
            observer.cancel(); observationRelease.countDown(); observer.join()
            assertEquals(listOf(observed.requestId), port.cancels.toList())

            val closer = async(start = CoroutineStart.UNDISPATCHED) { actor.close() }
            val observers = (1..20).map { async { runCatching { actor.snapshot() } } }
            withTimeout(2_000) { observers.forEach { it.await() } }
            val report = closer.await()
            assertFalse(report.nativeWorkerJoinConfirmed)
            assertEquals(1, port.calls.count { it.first == "close" })
        }
        harness { (actor, port, _) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(plan) {}; port.emit(ping(plan.requestId, 62))
            val original = (actor.observe(plan, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val consumer = async { actor.consume(original) { entered.complete(Unit); release.await() } }
            entered.await()
            val closeObserver = async(start = CoroutineStart.UNDISPATCHED) { actor.close() }
            waitFor(actor) { !it.admissionOpen }
            assertFalse(closeObserver.isCompleted)
            closeObserver.cancelAndJoin() // cancels observation, not the native close owner.
            release.complete(Unit)
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, consumer.await())
            assertFalse(actor.close().nativeWorkerJoinConfirmed)
            val calls = port.calls.toList()
            val ackIndex = calls.indexOfFirst { it.first == "ack:62" }
            assertTrue(ackIndex >= 0 && ackIndex < calls.indexOfFirst { it.first == "close" })
        }
    }

    @Test fun malformedIntegerWireIsRetainedWithoutAckOrNewAdmission() = runBlocking {
        harness { (actor, port, _) ->
            val plan = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(plan) {}
            val unsent = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            val prepared = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val sender = async { actor.dispatch(unsent) { prepared.complete(Unit); release.await() } }
            prepared.await()
            val raw = "{\"type\":\"result\",\"protocolVersion\":1.0,\"requestId\":${plan.requestId},\"deliveryId\":71,\"data\":{\"pong\":true,\"method\":\"runtime.ping\"}}"
            port.emit(raw)
            val snapshot = waitFor(actor) { it.unparsedOriginals.isNotEmpty() }
            assertEquals(listOf(raw), snapshot.unparsedOriginals)
            assertFalse(snapshot.admissionOpen)
            assertTrue(port.acknowledgements.isEmpty())
            assertTrue(snapshot.deliveries.isEmpty())
            release.complete(Unit)
            val rejected = sender.await() as ReaderCoreEventActor.DispatchOutcome.Rejected
            assertEquals("ADMISSION_CLOSED_BEFORE_NATIVE", rejected.reason)
            assertEquals(1, port.commands.size)
            assertEquals(listOf(raw), actor.close().retained.unparsedOriginals)
        }
    }

    @Test fun cancelledTerminalAckBeforeOlderHostRetainsExactOldRouteWithoutIoOrAckReplay(): Unit = runBlocking {
        val handlerCalls = AtomicInteger()
        harness(handler = ReaderCoreEventActor.HostHandler {
            handlerCalls.incrementAndGet()
            throw AssertionError("Retired cancelled request must never start Host I/O")
        }, configure = { port ->
            port.ackStatus = { id -> if (id == 91L) ReaderCoreEventActor.NativeStatus("rc_runtime_ack_event", 4)
                else ok("rc_runtime_ack_event") }
        }) { (actor, port, _) ->
            val original = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            actor.dispatch(original) {}
            actor.cancelRead(original)
            // Models an earlier registered Host delivery whose JNI callback is delayed.
            port.emit(error(original.requestId, 92, "CANCELLED"))
            val terminal = (actor.observe(original, 2_000) as ReaderCoreEventActor.Observation.Terminal).delivery
            assertEquals(ReaderCoreEventActor.Acknowledgement.Succeeded, actor.consume(terminal) {})
            val retired = actor.inspectRetiredOriginal(original.requestId, original.token)
                ?: throw AssertionError("Exact old route disappeared after terminal ACK")
            assertEquals(1, actor.snapshot().retiredOriginalRouteCount)
            assertTrue(actor.inspectRetiredOriginal(original.requestId, "foreign-token") == null)
            assertTrue(actor.snapshot().requests.isEmpty())
            assertEquals(original.token, retired.requestToken)
            assertEquals(92L, retired.terminalDeliveryId)
            assertTrue(retired.terminalAcknowledged)

            val next = actor.prepare("runtime.ping", kind = ReaderCoreEventActor.Kind.READ_ONLY)
            assertTrue(actor.inspectRetiredOriginal(next.requestId, next.token) == null)
            port.emit(host(original.requestId, 91, 7))
            val snapshot = waitFor(actor) { state -> state.deliveries.any { it.delivery.deliveryId == 91L } }
            val held = snapshot.deliveries.single()
            assertEquals(original.requestId, held.delivery.requestId)
            assertEquals(original.token, held.delivery.requestToken)
            assertEquals(original.token, held.retiredOriginalRoute!!.requestToken)
            assertEquals(ReaderCoreEventActor.LateHostDisposition.CANCELLED_ORIGINAL_DISCARDED, held.lateHostDisposition)
            assertEquals(ReaderCoreEventActor.DeliveryPhase.CONSUMED_ACK_FAILED, held.phase)
            assertEquals(1L, held.retiredOriginalRoute!!.lateHostDiscardCount)
            assertEquals(0L, held.retiredOriginalRoute!!.lateHostAckCount)
            assertEquals(listOf(92L, 91L), port.acknowledgements.toList())
            assertFalse(snapshot.admissionOpen)
            assertTrue(snapshot.requests.single().request === next)
            assertFalse(next.terminal.isCompleted)
            assertEquals(0, handlerCalls.get())
            assertEquals(1, port.commands.size) // No host.complete/error, no new client dispatch.
            assertTrue(snapshot.unparsedOriginals.isEmpty())
            val afterOwnerTurn = actor.snapshot()
            assertEquals(1, port.acknowledgements.count { it == 91L }) // Failure did not auto-retry.
            assertEquals(91L, afterOwnerTurn.deliveries.single().delivery.deliveryId)
        }
    }

    companion object {
        private fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0)
        private fun result(id: Long, delivery: Long, data: String): String =
            "{\"type\":\"result\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"data\":$data}"
        private fun ping(id: Long, delivery: Long) = result(id, delivery, "{\"pong\":true,\"method\":\"runtime.ping\"}")
        private fun error(id: Long, delivery: Long, code: String) =
            "{\"type\":\"error\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"error\":{\"code\":\"$code\",\"message\":\"diagnostic\",\"retryable\":false}}"
        private fun host(id: Long, delivery: Long, operation: Long) =
            "{\"type\":\"host.request\",\"protocolVersion\":1,\"requestId\":$id,\"deliveryId\":$delivery,\"operationId\":$operation,\"capability\":\"host.smoke.echo\",\"params\":{}}"
    }
}
