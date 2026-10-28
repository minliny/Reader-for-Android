package io.reader.android

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.NativeCoreBridge
import com.reader.core.ReaderCoreException
import com.reader.core.ReaderCoreRuntime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * UNWIRED DRAFT, deliberately outside androidTest.
 * Selector: io.reader.android.NativeDeliveryContractProbeTest#pinnedJniDeliveryAcknowledgementAndDiagnosticDomains
 * Exactly one ping and one malformed send. No Activity, production Core directory,
 * books, import, network, cancel or Host continuation. Probe directories remain.
 */
@RunWith(AndroidJUnit4::class)
class NativeDeliveryContractProbeTest {
    @Test(timeout = 20_000)
    fun pinnedJniDeliveryAcknowledgementAndDiagnosticDomains() {
        marker("method-enter")
        val thread = Thread.currentThread().id
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("io.reader.minliny", context.packageName)
        assertEquals("Probe must execute under the target App UID", context.applicationInfo.uid, Process.myUid())
        assertEquals("Probe requires the admitted new binding pin", PIN, BuildConfig.CORE_COMMIT)
        assertTrue(Process.is64Bit())
        assertTrue("arm64-v8a" in Build.SUPPORTED_64_BIT_ABIS)
        assertEquals(1, ReaderCoreRuntime.abiVersion())
        val capability = ReaderCoreRuntime.deliveryCapabilities()
        assertTrue("Real JNI must advertise JSON delivery ACK", capability and ReaderCoreRuntime.DELIVERY_JSON_ACK != 0)
        marker("delivery-capability-ACK", Bundle().apply { putInt("capabilities", capability); putString("declaredCorePin", PIN) })

        val parent = File(context.cacheDir, "A0NativeDeliveryProbes")
        assertTrue(parent.mkdirs() || parent.isDirectory)
        val relative = "A0NativeDeliveryProbes/${UUID.randomUUID()}"
        val probe = File(context.cacheDir, relative)
        assertTrue("Unique probe must not reuse an existing runtime directory", probe.mkdir())
        val data = File(probe, "data")
        val cache = File(probe, "cache")
        assertTrue(data.mkdir())
        assertTrue(cache.mkdir())
        marker("private-probe-created", Bundle().apply { putString("probeCacheRelativePath", relative) })

        try {
            ReaderCoreRuntime(JSONObject().put("dataDirectory", data.absolutePath)
                .put("cacheDirectory", cache.absolutePath).toString()).use { runtime ->
                runtime.enableEventAcknowledgements()
                marker("delivery-mode-enabled")
                assertEquals(thread, Thread.currentThread().id)
                runtime.sendCommand("runtime.ping", REQUEST_ID, "{}")
                marker("original-ping-dispatched", Bundle().apply { putLong("requestId", REQUEST_ID) })
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var raw: String? = null
                while (raw == null && SystemClock.elapsedRealtime() < deadline)
                    raw = runtime.pollEvent(100, TimeUnit.MILLISECONDS)
                val wire = raw ?: throw AssertionError("Original ping terminal exceeded its bounded poll")
                val event = JSONObject(wire)
                assertEquals(1, event.getInt("protocolVersion"))
                assertEquals(REQUEST_ID, exactPositiveLong(event, "requestId"))
                assertEquals("result", event.getString("type"))
                val deliveryId = exactPositiveLong(event, "deliveryId")
                val result = event.getJSONObject("data")
                assertEquals(true, result.get("pong"))
                assertEquals("runtime.ping", result.getString("method"))
                // Full original terminal is consumed here; no queue-to-queue ACK.
                marker("original-ping-terminal-consumed", Bundle().apply {
                    putLong("requestId", REQUEST_ID); putLong("deliveryId", deliveryId)
                    putString("originalWireSha256", sha(wire.toByteArray(Charsets.UTF_8))); putString("originalWire", wire)
                })

                // The only malformed send comes AFTER the last successful send/poll,
                // so ACKs are exercised with a genuinely nonzero same-thread C slot.
                assertEquals(thread, Thread.currentThread().id)
                val sendFailure = expectedCoreFailure { runtime.send("{") }
                assertEquals("rc_runtime_send", sendFailure.operation())
                assertEquals(3, sendFailure.status())
                assertFalse(sendFailure.isBusy)
                assertFalse(sendFailure.isResourceExhausted)
                val sendDiagnostic = sendFailure.lastError() ?: throw AssertionError("Fresh send diagnostic is absent")
                assertEquals(5, sendDiagnostic.code()) // C InvalidMessage, not Rust enum ordinal.
                assertEquals("invalid command JSON", sendDiagnostic.message())
                assertEquals(thread, Thread.currentThread().id)
                marker("malformed-send-original-diagnostic", Bundle().apply {
                    putString("operation", sendFailure.operation()); putInt("status", sendFailure.status())
                    putInt("errorCode", sendDiagnostic.code()); putString("diagnostic", sendDiagnostic.message())
                })

                runtime.ackEvent(deliveryId)
                marker("original-delivery-ACK", Bundle().apply { putLong("deliveryId", deliveryId) })
                val duplicate = expectedCoreFailure { runtime.ackEvent(deliveryId) }
                assertEquals("rc_runtime_ack_event", duplicate.operation())
                assertEquals(4, duplicate.status())
                assertNull("ACK must not attribute the earlier send slot to its own error", duplicate.lastError())
                assertFalse(duplicate.isBusy)
                assertFalse(duplicate.isResourceExhausted)
                assertEquals(thread, Thread.currentThread().id)
                // This is an actual same-thread JNI peek, not a source-derived claim.
                val slot = NativeCoreBridge.lastError()
                assertEquals(5, slot.code())
                assertEquals("invalid command JSON", slot.message())
                marker("duplicate-ACK-rejected-independent-diagnostic", Bundle().apply {
                    putLong("deliveryId", deliveryId); putString("operation", duplicate.operation()); putInt("status", duplicate.status())
                    putBoolean("ackLastErrorNull", duplicate.lastError() == null)
                    putInt("actualCSlotCode", slot.code()); putString("actualCSlotDiagnostic", slot.message())
                })
            }
            marker("native-close-returned", Bundle().apply { putBoolean("nativeWorkerJoinConfirmed", false) })
            assertTrue("Retain runtime directories because destroy does not establish worker join", data.isDirectory && cache.isDirectory)
            marker("method-exit")
        } finally {
            // Never delete data/cache after destroy; the fixed Rust Drop may detach workers.
            marker("private-probe-retained", Bundle().apply {
                putString("probeCacheRelativePath", relative); putBoolean("directoryRetained", probe.isDirectory)
                putBoolean("nativeWorkerJoinConfirmed", false)
            })
        }
    }

    private fun expectedCoreFailure(call: () -> Unit): ReaderCoreException {
        try { call() } catch (error: ReaderCoreException) { return error }
        throw AssertionError("The exact native call must fail; no automatic replay is allowed")
    }

    private fun marker(step: String, fields: Bundle = Bundle()) {
        fields.putString("readerMethod", METHOD)
        fields.putString("readerStep", step)
        fields.putInt("readerProcessPid", Process.myPid())
        fields.putInt("readerProcessUid", Process.myUid())
        fields.putLong("readerJavaThreadId", Thread.currentThread().id)
        fields.putLong("readerElapsedMs", SystemClock.elapsedRealtime())
        InstrumentationRegistry.getInstrumentation().sendStatus(2, fields)
    }

    private fun exactPositiveLong(event: JSONObject, key: String): Long {
        val result = when (val value = event.get(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw AssertionError("Original wire integer required: $key")
        }
        assertTrue("Original wire identity must be positive: $key", result > 0)
        return result
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        private const val PIN = "1fe86c34e426e0288523850e3b87600f1efcaf3d"
        private const val REQUEST_ID = 470_002L // Unique within this newly-created private runtime.
        private const val METHOD = "pinnedJniDeliveryAcknowledgementAndDiagnosticDomains"
    }
}
