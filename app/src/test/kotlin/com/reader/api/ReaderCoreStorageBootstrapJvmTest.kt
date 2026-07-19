package com.reader.api

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCoreStorageBootstrapJvmTest {

    @Test
    fun `startup advertises closed Core capabilities before storage restore`() = runBlocking {
        val calls = mutableListOf<Pair<String, JSONObject>>()

        val result = bootstrapCoreStorage(
            registeredCapabilities = setOf(
                "persistence.put",
                "persistence.get",
                "http.execute",
                "reader-ui-only.capability"
            )
        ) { method, params, _ ->
            calls += method to JSONObject(params.toString())
            when (method) {
                "runtime.setHostCapabilities" -> JSONObject()
                    .put("applied", true)
                    .put("capabilities", 3)
                    .put("lanes", 0)
                "runtime.storage.restore" -> JSONObject()
                    .put("restored", true)
                    .put("revision", "host-rev-4")
                    .put("schemaVersion", 6)
                else -> error("unexpected method=$method")
            }
        }

        assertEquals(
            listOf("runtime.setHostCapabilities", "runtime.storage.restore"),
            calls.map { it.first }
        )
        val manifest = calls.first().second
        assertEquals("android", manifest.getString("platform"))
        assertEquals(
            listOf("http.execute", "persistence.get", "persistence.put"),
            (0 until manifest.getJSONArray("capabilities").length())
                .map { manifest.getJSONArray("capabilities").getString(it) }
        )
        assertFalse(manifest.toString().contains("reader-ui-only.capability"))
        assertTrue(result.restored)
        assertEquals("host-rev-4", result.revision)
        assertEquals(6, result.schemaVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startup fails closed without both persistence routes`(): Unit = runBlocking {
        bootstrapCoreStorage(setOf("persistence.get")) { _, _, _ ->
            error("command must not run")
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `startup rejects malformed restore result`(): Unit = runBlocking {
        bootstrapCoreStorage(setOf("persistence.get", "persistence.put")) { method, _, _ ->
            if (method == "runtime.setHostCapabilities") {
                JSONObject().put("applied", true).put("capabilities", 2).put("lanes", 0)
            } else {
                JSONObject().put("restored", false)
            }
        }
    }
}
