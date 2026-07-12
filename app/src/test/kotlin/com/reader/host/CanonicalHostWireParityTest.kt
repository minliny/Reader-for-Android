package com.reader.host

import io.reader.ui.contract.HostRequestType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Data-driven Android parity gate over the canonical Reader UI fixture pair.
 * The fixtures are read from the composite-build source repo, so Android
 * cannot silently keep a stale copied payload/result manifest.
 */
class CanonicalHostWireParityTest {
    private val requestFixtures: JSONArray by lazy { fixture("host-request.fixtures.json") }
    private val resultFixtures: JSONArray by lazy { fixture("host-result.fixtures.json") }

    @Test
    fun `canonical fixture sets and Android manifest are the same 58 types`() {
        assertEquals(58, requestFixtures.length())
        assertEquals(58, resultFixtures.length())
        assertEquals(58, HostRequestType.entries.size)

        val requests = fixtureTypes(requestFixtures)
        val results = fixtureTypes(resultFixtures)
        assertEquals(requests, results)
        assertEquals(requests, CanonicalHostWireContract.capabilities())
        assertEquals(requests, ReaderUi25HostCapabilityManifest.capabilities)
    }

    @Test
    fun `all 58 canonical request payloads pass the Android wire boundary`() {
        for (index in 0 until requestFixtures.length()) {
            val fixture = requestFixtures.getJSONObject(index)
            val type = fixture.getString("type")
            val failure = CanonicalHostWireContract.validateRequest(
                HostRequest(1L, (index + 1).toLong(), type, fixture.getJSONObject("payload").toString())
            )
            assertNull("$type canonical payload must be accepted: $failure", failure)
        }
    }

    @Test
    fun `all 58 successful results are exact projections without host-private scope`() {
        for (index in 0 until resultFixtures.length()) {
            val fixture = resultFixtures.getJSONObject(index)
            val type = fixture.getString("type")
            val expected = fixture.getJSONObject("result")
            val noisy = JSONObject(expected.toString())
                .put("requestId", 91)
                .put("operationId", 92)
                .put("hostDebug", "must-not-cross-wire")
            if (!noisy.has("generation")) noisy.put("generation", 93)

            val reply = CanonicalHostWireContract.projectSuccess(type, noisy.toString())
            assertTrue("$type must remain a successful canonical projection", reply.isComplete)
            val actual = JSONObject((reply as HostReply.Complete).resultJson())
            assertTrue("$type projection drift: expected=$expected actual=$actual", expected.similar(actual))
        }
    }

    @Test
    fun `HostAdapter applies request validation and result projection for every fixture`() {
        val adapter = HostAdapter()
        val resultsByType = (0 until resultFixtures.length()).associate { index ->
            val fixture = resultFixtures.getJSONObject(index)
            fixture.getString("type") to fixture.getJSONObject("result")
        }
        resultsByType.forEach { (type, result) ->
            adapter.register(type) {
                val noisy = JSONObject(result.toString())
                    .put("requestId", 7)
                    .put("operationId", 8)
                if (!noisy.has("generation")) noisy.put("generation", 9)
                HostReply.complete(noisy.toString())
            }
        }

        for (index in 0 until requestFixtures.length()) {
            val fixture = requestFixtures.getJSONObject(index)
            val type = fixture.getString("type")
            val reply = adapter.dispatch(
                HostRequest(1L, (index + 1).toLong(), type, fixture.getJSONObject("payload").toString())
            )
            assertTrue("$type must dispatch through the canonical boundary", reply.isComplete)
            val actual = JSONObject((reply as HostReply.Complete).resultJson())
            assertTrue(resultsByType.getValue(type).similar(actual))
        }
    }

    @Test
    fun `historical alias fields are rejected before platform dispatch`() {
        val aliases = listOf(
            "tts.system.start" to JSONObject().put("text", "x").put("speechRate", 1.0),
            "permission.check" to JSONObject().put("kind", "storage"),
            "notification.show" to JSONObject().put("id", "n").put("title", "t").put("message", "b"),
            "device.vibrate" to JSONObject().put("durationMillis", 50),
            "device.screen.keep-on" to JSONObject().put("keepOn", true),
            "background.schedule" to JSONObject().put("taskTag", "rss").put("kind", "sync"),
            "credential.get" to JSONObject().put("identifier", "secret"),
            "storage.path" to JSONObject().put("kind", "cache")
        )
        aliases.forEachIndexed { index, (type, payload) ->
            val failure = CanonicalHostWireContract.validateRequest(
                HostRequest(1L, (index + 1).toLong(), type, payload.toString())
            )
            assertNotNull("$type historical alias must fail closed", failure)
            assertTrue(failure is HostReply.Error)
            assertEquals("INVALID_PARAMS", (failure as HostReply.Error).code())
        }
    }

    private fun fixture(name: String): JSONArray {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val uiRoot = generateSequence(start) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(
                    File(directory, "Reader-UI"),
                    File(directory, "../Reader-UI")
                )
            }
            .firstOrNull { File(it, "contracts/fixtures/$name").isFile }
            ?: error("Reader-UI canonical fixture not found from $start: $name")
        return JSONArray(File(uiRoot, "contracts/fixtures/$name").readText())
    }

    private fun fixtureTypes(fixtures: JSONArray): Set<String> = linkedSetOf<String>().apply {
        for (index in 0 until fixtures.length()) add(fixtures.getJSONObject(index).getString("type"))
    }
}
