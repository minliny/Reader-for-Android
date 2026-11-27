package io.reader.rebuild.core

import com.reader.core.ReaderCoreRuntime
import com.reader.core.protocol.HostErrorParams
import com.reader.core.protocol.ReaderCoreError
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderHostCapability
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM1Error
import com.reader.core.protocol.ReaderM1ErrorEvent
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.protocol.ReaderRPC
import com.reader.core.protocol.ReaderRuntimeConfig
import com.reader.core.protocol.RuntimeSetHostCapabilitiesParams
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

internal data class RequestIdentity(val runtimeId: UUID, val requestId: Long)

internal class CoreRejected(val detail: ReaderM1Error) : Exception(detail.message)

/** A page owns observation only. Its original operation owns settlement for the lifetime of the runtime. */
internal class PageObservation<R>(private val publish: (Result<R>) -> Unit) {
    private var active = true

    @Synchronized fun retire() { active = false }

    @Synchronized internal fun accept(result: Result<R>) {
        if (active) publish(result)
    }
}

internal class CoreOperation<R>(
    val identity: RequestIdentity,
    private val settlement: CompletableDeferred<R>,
) {
    suspend fun await(): R = settlement.await()
}

internal interface HostCapabilities {
    val available: Set<ReaderHostCapability>
    /** Decode the generated host payload and return the generated completion or failure command. */
    suspend fun reply(capability: ReaderHostCapability, request: String, replyId: Long): String
}

@Serializable
private data class EventHeader(
    val protocolVersion: Int,
    val type: String,
    val requestId: Long,
    val deliveryId: Long? = null,
    val capability: ReaderHostCapability? = null,
    val operationId: Long? = null,
)

internal class CoreRuntimeActor private constructor(
    private val binding: ReaderCoreRuntime,
    private val host: HostCapabilities,
    private val report: (Throwable) -> Unit,
) {
    val runtimeId: UUID = UUID.randomUUID()
    private val nextId = AtomicLong()
    private val state = Any()
    private val closed = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<RequestIdentity, Pending<*>>()
    // Core resumes the original command on accepted host.complete/host.error. Only
    // a rejected control command has a terminal under its own request identifier.
    private val hostReplies = ConcurrentHashMap<RequestIdentity, RequestIdentity>()
    @Volatile private var closing = false
    @Volatile private var stopping = false
    private val consumer = scope.launch { consume() }

    fun <P, R> submit(rpc: ReaderRPC<P, R>, params: P, page: PageObservation<R>? = null): CoreOperation<R> =
        synchronized(state) {
            check(!closing && !stopping) { "Core runtime is closing" }
            val id = RequestIdentity(runtimeId, nextId.incrementAndGet())
            val owner = Pending(rpc, page)
            pending[id] = owner
            try {
                binding.send(rpc.encode(id.requestId, params))
            } catch (failure: Exception) {
                pending.remove(id, owner)
                throw failure
            }
            CoreOperation(id, owner.result)
        }

    suspend fun <P, R> call(rpc: ReaderRPC<P, R>, params: P): R = submit(rpc, params).await()

    fun cancelRead(identity: RequestIdentity) = synchronized(state) {
        require(identity.runtimeId == runtimeId)
        check(!closing)
        if (pending.containsKey(identity)) binding.cancel(identity.requestId)
    }

    private fun consume() {
        try {
            while (!stopping) {
                val event = binding.pollEventBytes(2, TimeUnit.SECONDS) ?: continue
                accept(event.decodeToString())
            }
        } catch (failure: Exception) {
            if (!stopping) {
                synchronized(state) {
                    stopping = true
                    pending.values.forEach { it.result.completeExceptionally(failure) }
                }
                report(failure)
            }
        }
    }

    private fun accept(wire: String) {
        val event = ReaderProtocolJson.decodeFromString<EventHeader>(wire)
        check(event.protocolVersion == 1)
        if (event.requestId == 0L) {
            check(event.type == "error")
            val failure = rejected(wire)
            event.deliveryId?.let(binding::ackEvent)
            report(failure)
            return
        }
        val key = RequestIdentity(runtimeId, event.requestId)
        val owner = pending[key]
        val delivery = requireNotNull(event.deliveryId) { "Missing ACK identity in $wire" }
        when (event.type) {
            "result", "error" -> {
                if (owner == null) {
                    val original = hostReplies.remove(key)
                    val failure = if (event.type == "error") rejected(wire)
                        else IllegalStateException("Unexpected terminal $key")
                    binding.ackEvent(delivery)
                    if (original != null) pending.remove(original)?.result?.completeExceptionally(failure)
                    else report(failure)
                } else {
                    val settle = owner.decode(wire, event.type == "error", key)
                    binding.ackEvent(delivery)
                    try {
                        settle()
                    } finally {
                        pending.remove(key, owner)
                        hostReplies.entries.removeAll { it.value == key }
                    }
                }
            }
            "host.request" -> {
                val capability = requireNotNull(event.capability)
                val operationId = requireNotNull(event.operationId)
                checkNotNull(owner) { "Unowned host request $key" }
                scope.launch { replyToHost(key, capability, operationId, wire, delivery) }
            }
            else -> {
                binding.ackEvent(delivery)
                report(IllegalStateException("Unhandled Core event ${event.type} for $key"))
            }
        }
    }

    private suspend fun replyToHost(original: RequestIdentity, capability: ReaderHostCapability, operationId: Long,
        wire: String, delivery: Long) {
        val id = nextId.incrementAndGet()
        val completion = try {
            host.reply(capability, wire, id)
        } catch (failure: Exception) {
            ReaderM1.hostError.encode(id, HostErrorParams(operationId,
                ReaderCoreError(ReaderErrorCode.INTERNAL, failure.message ?: "Host request failed", false)))
        }
        try {
            synchronized(state) {
                if (!stopping) {
                    binding.ackEvent(delivery)
                    if (pending.containsKey(original)) {
                        val key = RequestIdentity(runtimeId, id)
                        hostReplies[key] = original
                        try { binding.send(completion) } catch (failure: Exception) {
                            hostReplies.remove(key)
                            pending.remove(original)?.result?.completeExceptionally(failure)
                            throw failure
                        }
                    }
                }
            }
        } catch (failure: Exception) {
            report(failure)
        }
    }

    suspend fun close() = withContext(NonCancellable + Dispatchers.IO) {
        val unsettled = synchronized(state) {
            if (closing) null else {
                closing = true
                pending.values.map { it.result }
            }
        }
        if (unsettled == null) {
            closed.await()
        } else {
            try {
                withTimeoutOrNull(5_000) { unsettled.joinAll() }
                synchronized(state) { stopping = true }
                binding.close()
                consumer.join()
                scope.cancel()
                pending.values.forEach { it.result.completeExceptionally(IllegalStateException("Core runtime closed")) }
                pending.clear()
                hostReplies.clear()
            } finally {
                closed.complete(Unit)
            }
        }
    }

    private class Pending<R>(private val rpc: ReaderRPC<*, R>, private val page: PageObservation<R>?) {
        val result = CompletableDeferred<R>()

        fun decode(wire: String, error: Boolean, key: RequestIdentity): () -> Unit {
            val decoded = runCatching {
                if (error) throw rejected(wire)
                val reply = rpc.decode(wire)
                check(reply.protocolVersion == 1 && reply.requestId == key.requestId && reply.type == "result")
                reply.data
            }
            return {
                decoded.fold(result::complete, result::completeExceptionally)
                runCatching { page?.accept(decoded) }
            }
        }
    }

    companion object {
        private fun rejected(wire: String) = CoreRejected(ReaderProtocolJson.decodeFromString<ReaderM1ErrorEvent>(wire).error)

        suspend fun open(config: ReaderRuntimeConfig, host: HostCapabilities, report: (Throwable) -> Unit): CoreRuntimeActor {
            var opened: CoreRuntimeActor? = null
            try {
                return withContext(Dispatchers.IO) {
                    check(ReaderCoreRuntime.abiVersion() == 1)
                    check(ReaderCoreRuntime.deliveryCapabilities() and ReaderCoreRuntime.DELIVERY_JSON_ACK != 0)
                    val binding = ReaderCoreRuntime(ReaderProtocolJson.encodeToString(config))
                    try { binding.enableEventAcknowledgements() } catch (failure: Throwable) {
                        binding.close()
                        throw failure
                    }
                    val runtime = CoreRuntimeActor(binding, host, report)
                    opened = runtime
                    val manifest = runtime.call(ReaderM1.runtimeSetHostCapabilities, RuntimeSetHostCapabilitiesParams(
                        capabilities = host.available.sortedBy { it.wireValue }, platform = "android",
                    ))
                    check(manifest.applied && manifest.capabilities == host.available.size.toLong() && manifest.lanes == 0L)
                    runtime
                }
            } catch (failure: Throwable) {
                opened?.close()
                throw failure
            }
        }
    }
}
