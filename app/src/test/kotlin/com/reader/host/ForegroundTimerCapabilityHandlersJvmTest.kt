package com.reader.host

import com.reader.android.data.adapter.AndroidEncryptedCredentialRecord
import com.reader.android.data.adapter.FakeAndroidTtsAdapter
import com.reader.android.data.adapter.FakePermissionRuntimeAdapter
import com.reader.android.data.adapter.WebDavCredentialStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTimerCapabilityHandlersJvmTest {

    @Test
    fun `strict arm fires once and same generation may rearm only after fire`() {
        val scheduler = ManualTimerScheduler()
        val fires = mutableListOf<ForegroundTimerFire>()
        val registry = ForegroundTimerRegistry(scheduler, fires::add)
        val handler = ForegroundTimerArmHandler(registry)

        val first = handler.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 3))
        assertTrue(first.isComplete())
        assertTrue(JSONObject((first as HostReply.Complete).resultJson()).getBoolean("armed"))
        assertEquals(3L, registry.activeGeneration("auto-1"))

        val duplicate = handler.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 3))
        assertFalse(JSONObject((duplicate as HostReply.Complete).resultJson()).getBoolean("armed"))
        assertTrue(JSONObject(duplicate.resultJson()).getBoolean("duplicate"))
        assertEquals(1, scheduler.tasks.size)

        scheduler.fire(0)
        assertEquals(listOf(ForegroundTimerFire("auto-1", "auto-1", 3)), fires)
        assertNull(registry.activeGeneration("auto-1"))

        val rearm = handler.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 3))
        assertTrue(JSONObject((rearm as HostReply.Complete).resultJson()).getBoolean("armed"))
        scheduler.fire(1)
        assertEquals(2, fires.size)
    }

    @Test
    fun `old generation cancel never kills replacement timer and explicit cancel closes generation`() {
        val scheduler = ManualTimerScheduler()
        val fires = mutableListOf<ForegroundTimerFire>()
        val registry = ForegroundTimerRegistry(scheduler, fires::add)
        val arm = ForegroundTimerArmHandler(registry)
        val cancel = ForegroundTimerCancelHandler(registry)

        assertTrue(arm.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 4)).isComplete())
        val replacement = arm.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 5))
            as HostReply.Complete
        assertEquals(4L, JSONObject(replacement.resultJson()).getLong("replacedGeneration"))
        assertTrue(scheduler.tasks[0].cancelled)
        assertEquals(5L, registry.activeGeneration("auto-1"))

        val staleCancel = cancel.handle(request(ForegroundTimerCancelHandler.CAPABILITY, generation = 4))
            as HostReply.Complete
        val staleResult = JSONObject(staleCancel.resultJson())
        assertFalse(staleResult.getBoolean("cancelled"))
        assertFalse(staleResult.getBoolean("generationMatched"))
        assertEquals(5L, staleResult.getLong("activeGeneration"))
        assertFalse(scheduler.tasks[1].cancelled)

        val matched = cancel.handle(request(ForegroundTimerCancelHandler.CAPABILITY, generation = 5))
            as HostReply.Complete
        assertTrue(JSONObject(matched.resultJson()).getBoolean("cancelled"))
        assertTrue(scheduler.tasks[1].cancelled)
        scheduler.fire(1)
        assertTrue(fires.isEmpty())

        val lateRearm = arm.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 5))
        assertTrue(lateRearm.isError())
        assertEquals("STALE_GENERATION", (lateRearm as HostReply.Error).code())
        val staleArm = arm.handle(request(ForegroundTimerArmHandler.CAPABILITY, generation = 4))
        assertEquals("STALE_GENERATION", (staleArm as HostReply.Error).code())
    }

    @Test
    fun `timer DTO rejects identity bounds flags fractional values and unknown fields`() {
        val handler = ForegroundTimerArmHandler(ForegroundTimerRegistry(ManualTimerScheduler()))
        val invalid = listOf(
            timerJson().put("correlationId", "different"),
            timerJson().put("delayMs", 249),
            timerJson().put("delayMs", 3_600_001),
            timerJson().put("delayMs", 250.5),
            timerJson().put("generation", 0),
            timerJson().put("generation", 1.5),
            timerJson().put("oneShot", false),
            timerJson().put("foregroundOnly", false),
            timerJson().put("unexpected", true)
        )
        invalid.forEachIndexed { index, params ->
            val reply = handler.handle(HostRequest(1L, index.toLong() + 1L, handlerCapability(), params.toString()))
            assertTrue("invalid case $index must fail", reply.isError())
            assertEquals("INVALID_PARAMS", (reply as HostReply.Error).code())
            assertFalse(reply.retryable())
        }
    }

    @Test
    fun `HostFacade registers and dispatches both canonical foreground timer handlers`() {
        val scheduler = ManualTimerScheduler()
        val registry = ForegroundTimerRegistry(scheduler)
        val facade = facade(registry)
        val runtime = HostRuntime.over(object : HostTransport {
            override fun pollEventJson(timeoutMillis: Long): String? = null
            override fun sendCommand(commandJson: String) = Unit
        })
        facade.registerHandlers(runtime)

        assertTrue(runtime.adapter().isRegistered(ForegroundTimerArmHandler.CAPABILITY))
        assertTrue(runtime.adapter().isRegistered(ForegroundTimerCancelHandler.CAPABILITY))
        assertTrue(runtime.adapter().dispatch(
            request(ForegroundTimerArmHandler.CAPABILITY, generation = 9)
        ).isComplete())
        assertTrue(runtime.adapter().dispatch(
            request(ForegroundTimerCancelHandler.CAPABILITY, generation = 9)
        ).isComplete())
    }

    private fun handlerCapability(): String = ForegroundTimerArmHandler.CAPABILITY

    private fun request(capability: String, generation: Long): HostRequest = HostRequest(
        1L,
        generation,
        capability,
        timerJson(generation).toString()
    )

    private fun timerJson(generation: Long = 1L): JSONObject = JSONObject()
        .put("timerId", "auto-1")
        .put("correlationId", "auto-1")
        .put("delayMs", 250L)
        .put("generation", generation)
        .put("oneShot", true)
        .put("foregroundOnly", true)

    private fun facade(registry: ForegroundTimerRegistry): HostFacade = HostFacade(
        context = null,
        tts = FakeAndroidTtsAdapter(),
        permission = FakePermissionRuntimeAdapter(),
        notification = null,
        webDav = null,
        credentials = WebDavCredentialStore(FakeTimerCredentialKeystore()),
        downloadCache = null,
        foregroundTimerRegistry = registry
    )

    private class ManualTimerScheduler : ForegroundTimerScheduler {
        data class Task(
            val delayMs: Long,
            val callback: () -> Unit,
            var cancelled: Boolean = false
        )

        val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, callback: () -> Unit): ForegroundTimerCancellation {
            val task = Task(delayMs, callback)
            tasks += task
            return ForegroundTimerCancellation {
                val wasActive = !task.cancelled
                task.cancelled = true
                wasActive
            }
        }

        fun fire(index: Int) {
            val task = tasks[index]
            if (!task.cancelled) task.callback()
        }
    }

    private class FakeTimerCredentialKeystore : WebDavCredentialStore.CredentialKeystore {
        override fun save(identifier: String, secret: String): AndroidEncryptedCredentialRecord =
            AndroidEncryptedCredentialRecord(identifier, "fake", secret, "", "")

        override fun load(record: AndroidEncryptedCredentialRecord): String = record.cipherTextBase64

        override fun revoke(record: AndroidEncryptedCredentialRecord): Boolean = true
    }
}
