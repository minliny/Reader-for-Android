package com.reader.ui.shell

import io.reader.ui.runtime.ReaderUIJSONPayload
import io.reader.ui.runtime.ReaderUIJSONResult
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray

/** Reads the canonical Reader-UI R14 fixtures; Android keeps no copied DTOs. */
internal object ReaderUiTypedContractFixtures {
    private val payloads: JSONArray by lazy { fixture("runtime-payload-contract.fixtures.json") }
    private val results: JSONArray by lazy { fixture("runtime-result-contract.fixtures.json") }

    fun payload(id: String): ReaderUIJSONPayload = jsonObject(find(payloads, id), "payload")

    fun result(id: String): ReaderUIJSONResult = jsonObject(find(results, id), "result")

    private fun jsonObject(fixture: org.json.JSONObject, field: String) =
        Json.parseToJsonElement(fixture.getJSONObject(field).toString()).jsonObject

    private fun find(fixtures: JSONArray, id: String): org.json.JSONObject {
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.getJSONObject(index)
            if (fixture.getString("id") == id) return fixture
        }
        error("Reader-UI canonical fixture not found: $id")
    }

    private fun fixture(name: String): JSONArray {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val uiRoot = generateSequence(start) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(File(directory, "Reader-UI"), File(directory, "../Reader-UI"))
            }
            .firstOrNull { File(it, "contracts/fixtures/$name").isFile }
            ?: error("Reader-UI canonical fixture not found from $start: $name")
        return JSONArray(File(uiRoot, "contracts/fixtures/$name").readText())
    }
}
