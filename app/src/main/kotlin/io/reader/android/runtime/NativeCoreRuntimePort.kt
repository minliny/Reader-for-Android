package io.reader.android.runtime

import com.reader.core.ReaderCoreException
import com.reader.core.ReaderCoreRuntime
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Draft adapter for fixed 1fe SDK. It is not compiled against the current a8 SDK. */
internal class NativeCoreRuntimePort(
    private val data: File,
    private val cache: File,
) : ReaderCoreEventActor.RuntimePort {
    private var runtime: ReaderCoreRuntime? = null
    private var closed = false

    // The constructor does not create a Native handle. Every method below must
    // be called exclusively by the one event actor, including initial creation.
    private fun runtime(): ReaderCoreRuntime {
        check(!closed) { "NATIVE_PORT_CLOSED" }
        return runtime ?: run {
            check(data.mkdirs() || data.isDirectory)
            check(cache.mkdirs() || cache.isDirectory)
            ReaderCoreRuntime(JSONObject().put("dataDirectory", data.absolutePath)
                .put("cacheDirectory", cache.absolutePath).toString()).also { runtime = it }
        }
    }

    override fun deliveryCapabilities(): Int = ReaderCoreRuntime.deliveryCapabilities()

    override fun enableEventAcknowledgements() = status("rc_runtime_enable_event_ack") {
        runtime().enableEventAcknowledgements()
    }

    override fun send(commandJson: String) = status("rc_runtime_send") { runtime().send(commandJson) }

    override fun poll(): String? = runtime().pollEvent(0, TimeUnit.MILLISECONDS)

    override fun cancel(requestId: Long) = status("rc_runtime_cancel") { runtime().cancel(requestId) }

    override fun acknowledge(deliveryId: Long) = status("rc_runtime_ack_event") { runtime().ackEvent(deliveryId) }

    private inline fun status(operation: String, call: () -> Unit): ReaderCoreEventActor.NativeStatus {
        return try {
            call()
            ReaderCoreEventActor.NativeStatus(operation, 0)
        } catch (error: ReaderCoreException) {
            // The Java entry point already captured the failed call's immediate
            // same-thread last_error. Never query again here or invent a domain
            // for an exception that did not provide one (create/cancel/panic).
            ReaderCoreEventActor.NativeStatus(error.operation() ?: "unknown", error.status(),
                error.lastError()?.let { ReaderCoreEventActor.Diagnostic(it.code(), it.message()) })
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Native destroy closes the callback gate; its return is not a worker join.
        runtime?.close()
        runtime = null
    }
}
