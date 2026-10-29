package io.reader.android.runtime

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class ReaderCoreEventActorAssetTransferTest {
    private class Port : ReaderCoreEventActor.RuntimePort {
        val commands = ConcurrentLinkedQueue<JSONObject>()
        val events = ConcurrentLinkedQueue<String>()
        val begins = ConcurrentLinkedQueue<Triple<Long, Long, Long>>()
        val writes = ConcurrentLinkedQueue<ByteArray>()
        val releases = ConcurrentLinkedQueue<Long>()
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
        override fun assetReadChunk(requestId: Long, operationId: Long, assetId: Long, offset: Long, limit: Int) = byteArrayOf()
        override fun assetRelease(requestId: Long, operationId: Long, assetId: Long) { releases.add(assetId) }
        override fun close() = Unit
    }

    private fun hostRequest(requestId: Long, deliveryId: Long, operationId: Long) = JSONObject()
        .put("protocolVersion", 1).put("requestId", requestId).put("deliveryId", deliveryId)
        .put("type", "host.request").put("operationId", operationId)
        .put("capability", "manga.resource.transfer").put("params", JSONObject().put("stage", "input"))
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
            assertEquals("image/jpeg", result.getString("contentType"))
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

    private companion object {
        fun ok(operation: String) = ReaderCoreEventActor.NativeStatus(operation, 0)
    }
}
