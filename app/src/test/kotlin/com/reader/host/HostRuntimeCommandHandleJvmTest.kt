package com.reader.host

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostRuntimeCommandHandleJvmTest {

    @Test
    fun `handle retains runtime requestId and awaits its matching result`() {
        val transport = ScriptedTransport()
        transport.onSend = { commandJson ->
            val command = JSONObject(commandJson)
            if (command.getString("method") == "runtime.ping") {
                transport.enqueue(
                    JSONObject()
                        .put("type", "result")
                        .put("requestId", command.getLong("requestId"))
                        .put("data", JSONObject().put("pong", true))
                        .toString()
                )
            }
        }
        val runtime = HostRuntime.over(transport, 10L).start()

        try {
            val handle = runtime.beginCommand("runtime.ping", "{}")

            assertEquals(2000L, handle.requestId())
            val result = handle.await(1_000L)
            assertTrue(result.isSuccess())
            assertEquals(handle.requestId(), result.requestId())
            assertTrue(JSONObject(result.dataJson()).getBoolean("pong"))
            assertEquals(0, runtime.pendingCount())
            assertTrue(handle.isDone())
            assertFalse("a completed handle cannot cancel a different request", handle.cancel())
        } finally {
            runtime.stop()
        }
    }

    @Test
    fun `cancel targets the exact handle request and completes the same waiter`() {
        val transport = ScriptedTransport()
        val runtime = HostRuntime.over(transport, 10L).start()

        try {
            val handle = runtime.beginCommand("book.toc", "{\"bookId\":\"book-1\"}")
            assertEquals(1, runtime.pendingCount())

            assertTrue(handle.cancel())
            assertEquals(0, runtime.pendingCount())

            val cancelled = handle.await(1_000L)
            assertTrue(cancelled.isError())
            assertEquals(handle.requestId(), cancelled.requestId())
            assertEquals("CANCELLED", JSONObject(cancelled.errorJson()).getString("code"))
            assertFalse("cancellation is idempotent per handle", handle.cancel())

            assertEquals(2, transport.sent.size)
            val original = JSONObject(transport.sent[0])
            val cancel = JSONObject(transport.sent[1])
            assertEquals("book.toc", original.getString("method"))
            assertEquals(handle.requestId(), original.getLong("requestId"))
            assertEquals("runtime.cancel", cancel.getString("method"))
            assertTrue(cancel.getLong("requestId") != handle.requestId())
            assertEquals(
                handle.requestId(),
                cancel.getJSONObject("params").getLong("requestId")
            )
        } finally {
            runtime.stop()
        }
    }

    private class ScriptedTransport : HostTransport {
        val sent = CopyOnWriteArrayList<String>()
        private val events = LinkedBlockingQueue<String>()
        var onSend: (String) -> Unit = {}

        override fun pollEventJson(timeoutMillis: Long): String? = try {
            events.poll(timeoutMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }

        override fun sendCommand(commandJson: String) {
            sent += commandJson
            onSend(commandJson)
        }

        fun enqueue(eventJson: String) {
            events.put(eventJson)
        }
    }
}
