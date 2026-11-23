package io.reader.android.runtime

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException

class ReaderCoreEventActorAssetTransferTest {
    private class Port : ReaderCoreEventActor.RuntimePort {
        val commands = ConcurrentLinkedQueue<JSONObject>()
        val events = ConcurrentLinkedQueue<String>()
        val begins = ConcurrentLinkedQueue<Triple<Long, Long, Long>>()
        val writes = ConcurrentLinkedQueue<ByteArray>()
        val reads = ConcurrentLinkedQueue<Triple<Long, Int, Long>>()
        val readReplies = ConcurrentLinkedQueue<ByteArray>()
        val releases = ConcurrentLinkedQueue<Long>()
        val releaseOwners = ConcurrentLinkedQueue<Long>()
        var failCommit = false
        override fun deliveryCapabilities() = 1
        override fun enableEventAcknowledgements() = ok("rc_runtime_enable_event_ack")
        override fun send(commandJson: String): ReaderCoreEventActor.NativeStatus {
            commands.add(JSONObject(commandJson)); return ok("rc_runtime_send")
        }
        override fun poll() = events.poll()
        override fun cancel(requestId: Long) = ok("rc_runtime_cancel")
        override fun acknowledge(deliveryId: Long) = ok("rc_runtime_ack_event")
        override fun assetBegin(requestId: Long, operationId: Long, declaredBytes: Long): Long {
            begins.add(Triple(requestId, operationId, declaredBytes)); return 99L
        }
        override fun assetWrite(requestId: Long, operationId: Long, assetId: Long, bytes: ByteArray): Long {
            writes.add(bytes.copyOf()); return writes.sumOf { it.size }.toLong()
        }
        override fun assetCommit(requestId: Long, operationId: Long, assetId: Long): Long {
            if (failCommit) error("COMMIT_FAILED")
            return writes.sumOf { it.size }.toLong()
        }
        override fun assetReadChunk(requestId: Long, operationId: Long, assetId: Long, offset: Long, limit: Int): ByteArray {
            reads.add(Triple(offset, limit, assetId))
            return readReplies.poll()?.copyOf() ?: byteArrayOf()
        }
        override fun assetRelease(requestId: Long, operationId: Long, assetId: Long) {
            releases.add(assetId); releaseOwners.add(operationId)
        }
        override fun close() = Unit
    }

    private fun hostRequest(requestId: Long, deliveryId: Long, operationId: Long, stage: String = "input") = JSONObject()
        .put("protocolVersion", 1).put("requestId", requestId).put("deliveryId", deliveryId)
        .put("type", "host.request").put("operationId", operationId)
        .put("capability", "manga.resource.transfer").put("params", JSONObject().put("stage", stage))
        .toString()

    @Test fun actorTransfersAssetOnOwnerThreadAndCompletesWithOpaqueReceipt() = runBlocking {
        val port = Port()
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                ReaderCoreEventActor.HostReply.AssetTransfer(
                    listOf(byteArrayOf(1, 2), byteArrayOf(3)), "image/jpeg",
                )
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}
            port.events.add(hostRequest(plan.requestId, 21, 77))
            withTimeout(3_000) {
                while (actor.snapshot().hostOperations.firstOrNull { it.envelope.operationId == 77L }?.phase != "RESPONSE_ACCEPTED_NO_SUCCESS_EVENT") {
                    actor.changes.receive()
                }
            }
            assertEquals(listOf(Triple(plan.requestId, 77L, 3L)), port.begins.toList())
            assertEquals(listOf(listOf(1.toByte(), 2.toByte()), listOf(3.toByte())), port.writes.map { it.toList() }.toList())
            assertTrue(port.releases.isEmpty())
            val command = port.commands.first { it.getString("method") == "host.complete" }
            assertEquals("host.complete", command.getString("method"))
            val result = command.getJSONObject("params").getJSONObject("result")
            assertEquals(99L, result.getLong("assetId"))
            assertEquals(77L, result.getLong("operationId"))
            assertEquals(3L, result.getLong("bytes"))
            assertTrue("Core asset transfer receipt must not widen the fixed InputReceipt", !result.has("contentType"))
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    @Test fun actorReleasesAssetWhenCommitFailsAndSendsHostError() = runBlocking {
        val port = Port().also { it.failCommit = true }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                ReaderCoreEventActor.HostReply.AssetTransfer(listOf(byteArrayOf(1, 2, 3)))
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}
            port.events.add(hostRequest(plan.requestId, 31, 88))
            withTimeout(3_000) {
                while (port.commands.none { it.optString("method") == "host.error" }) actor.changes.receive()
            }
            val command = port.commands.first { it.getString("method") == "host.error" }
            assertEquals("host.error", command.getString("method"))
            assertEquals("MANGA_ASSET_TRANSFER_FAILED", command.getJSONObject("params")
                .getJSONObject("error").getString("message"))
            assertEquals(listOf(99L), port.releases.toList())
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    @Test fun actorReadsExactAssetOnOwnerThenConsumesAndReleasesOnce() = runBlocking {
        val port = Port().also {
            it.readReplies.add(byteArrayOf(1, 2)); it.readReplies.add(byteArrayOf(3, 4));
            it.readReplies.add(byteArrayOf(5)); it.readReplies.add(byteArrayOf())
        }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        val consumed = CompletableDeferred<List<Byte>>()
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                // Host operation 77 owns the delivery; Core asset operation 88
                // owns the output bytes and is the identity used for release.
                ReaderCoreEventActor.HostReply.AssetConsume(99L, 88L, 5L) { bytes ->
                    consumed.complete(bytes.toList())
                    ReaderCoreEventActor.HostReply.Complete("{}")
                }
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}
            port.events.add(hostRequest(plan.requestId, 41, 77, stage = "output"))
            withTimeout(3_000) {
                while (actor.snapshot().hostOperations.firstOrNull { it.envelope.operationId == 77L }?.phase != "RESPONSE_ACCEPTED_NO_SUCCESS_EVENT") {
                    actor.changes.receive()
                }
            }
            assertEquals(listOf<Byte>(1, 2, 3, 4, 5), consumed.await())
            assertEquals(listOf(Triple(0L, 5, 99L), Triple(2L, 3, 99L),
                Triple(4L, 1, 99L), Triple(5L, 1, 99L)), port.reads.toList())
            assertEquals(listOf(99L), port.releases.toList())
            assertEquals(listOf(88L), port.releaseOwners.toList())
            assertEquals("host.complete", port.commands.first { it.optString("method") == "host.complete" }.getString("method"))
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    @Test fun actorRejectsNonEofAssetAndReleasesExactlyOnce() = runBlocking {
        val port = Port().also { it.readReplies.add(byteArrayOf(1, 2, 3)); it.readReplies.add(byteArrayOf(4)) }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        var consumerCalled = false
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                ReaderCoreEventActor.HostReply.AssetConsume(99L, 77L, 3L) {
                    consumerCalled = true
                    ReaderCoreEventActor.HostReply.Complete("{}")
                }
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}
            port.events.add(hostRequest(plan.requestId, 42, 77, stage = "output"))
            withTimeout(3_000) {
                while (port.commands.none { it.optString("method") == "host.error" }) actor.changes.receive()
            }
            val command = port.commands.first { it.optString("method") == "host.error" }
            assertEquals("MANGA_ASSET_CONSUME_FAILED", command.getJSONObject("params").getJSONObject("error").getString("message"))
            assertEquals(false, consumerCalled)
            assertEquals(listOf(99L), port.releases.toList())
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    @Test fun actorReleasesAfterConsumerFailureAndCancellation() = runBlocking {
        val port = Port().also {
            it.readReplies.add(byteArrayOf(1, 2)); it.readReplies.add(byteArrayOf());
            it.readReplies.add(byteArrayOf(3, 4)); it.readReplies.add(byteArrayOf())
        }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        val outcomes = ConcurrentLinkedQueue<String>()
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                val operation = it.operationId
                ReaderCoreEventActor.HostReply.AssetConsume(99L, operation, 2L) {
                    if (operation == 77L) throw IllegalStateException("decode failed")
                    throw CancellationException("decode cancelled")
                }
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val first = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(first) {}
            port.events.add(hostRequest(first.requestId, 43, 77, stage = "output"))
            withTimeout(3_000) {
                while (port.commands.count { it.optString("method") == "host.error" } < 1) actor.changes.receive()
            }
            outcomes.add(port.commands.first { it.optString("method") == "host.error" }
                .getJSONObject("params").getJSONObject("error").getString("message"))
            val second = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(second) {}
            port.events.add(hostRequest(second.requestId, 44, 78, stage = "output"))
            withTimeout(3_000) {
                while (port.commands.count { it.optString("method") == "host.error" } < 2) actor.changes.receive()
            }
            outcomes.add(port.commands.last { it.optString("method") == "host.error" }
                .getJSONObject("params").getJSONObject("error").getString("message"))
            assertEquals(listOf("MANGA_ASSET_CONSUME_FAILED", "MANGA_ASSET_CONSUME_FAILED"), outcomes.toList())
            assertEquals(listOf(99L, 99L), port.releases.toList())
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    @Test fun actorCloseCancelsActiveAssetConsumerAndReleasesExactlyOnce() = runBlocking {
        val port = Port().also {
            it.readReplies.add(byteArrayOf(1, 2)); it.readReplies.add(byteArrayOf())
        }
        val executor = Executors.newSingleThreadExecutor { Thread(it, "asset-owner") }
        val dispatcher = executor.asCoroutineDispatcher()
        val entered = CompletableDeferred<Unit>()
        val continueConsumer = CompletableDeferred<Unit>()
        val actor = ReaderCoreEventActor(port, dispatcher,
            hostHandler = ReaderCoreEventActor.HostHandler {
                ReaderCoreEventActor.HostReply.AssetConsume(99L, it.operationId, 2L) {
                    entered.complete(Unit)
                    continueConsumer.await()
                    ReaderCoreEventActor.HostReply.Complete("{}")
                }
            }, pollIntervalMs = 1)
        try {
            actor.awaitReady()
            val plan = actor.prepare("runtime.hostSmoke", kind = ReaderCoreEventActor.Kind.DURABLE_WRITE)
            actor.dispatch(plan) {}
            port.events.add(hostRequest(plan.requestId, 45, 79, stage = "output"))
            withTimeout(3_000) { entered.await() }
            val report = withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            assertFalse(report.hostHandlersDrained)
            assertEquals(listOf(99L), port.releases.toList())
            assertEquals(1, port.releases.size)
            assertTrue(port.commands.none { it.optString("method") == "host.complete" })
        } finally {
            continueConsumer.cancel()
            withContext(kotlinx.coroutines.NonCancellable) { actor.close() }
            dispatcher.close(); executor.shutdownNow()
        }
    }

    private companion object {
        fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0)
    }
}
