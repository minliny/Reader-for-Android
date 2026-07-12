package com.reader.ui.shell

import io.reader.ui.runtime.GeneratedRuntimeTypedPayloadContracts
import io.reader.ui.runtime.ReaderUIJSONPayload
import io.reader.ui.runtime.ReaderUIJSONResult
import io.reader.ui.runtime.ReaderUIRuntimeException
import io.reader.ui.runtime.validateReaderUITypedResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

/** Lossless bridge shared by the dormant typed Pilot executors. */
internal fun ReaderUIJSONPayload.toCanonicalJSONObject(): JSONObject =
    JSONObject(JsonObject(this).toString())

/**
 * Core is allowed to return transport-private fields, but none may cross the
 * Reader-UI boundary. Project to the declared result schema first, then run
 * the same generated validator used by Reader-UI itself.
 */
internal fun projectAndValidateReaderUiResult(
    event: String,
    effectType: String,
    rawResult: JSONObject
): ReaderUIJSONResult {
    val contract = GeneratedRuntimeTypedPayloadContracts.byEvent[event]
        ?: throw ReaderUIRuntimeException(
            "INVALID_TYPED_CONTRACT",
            "No typed contract for $event"
        )
    val resultContract = contract.resultSchemas[effectType]
        ?: throw ReaderUIRuntimeException(
            "UNDECLARED_TYPED_RESULT",
            "$event does not declare a result for $effectType"
        )
    val source = try {
        Json.parseToJsonElement(rawResult.toString()).jsonObject
    } catch (error: Exception) {
        throw ReaderUIRuntimeException(
            "INVALID_JSON_RESULT",
            error.message ?: "$event:$effectType returned malformed JSON"
        )
    }

    val schema = resultContract.schema.jsonObject
    val topLevelBranches = schema["oneOf"] as? JsonArray
    val candidates = (topLevelBranches?.map { it } ?: listOf(resultContract.schema))
        .mapNotNull { branch -> projectJsonValue(source, branch) as? JsonObject }
        .distinctBy(JsonObject::toString)

    val accepted = candidates.filter { candidate ->
        try {
            validateReaderUITypedResult(event, effectType, candidate)
            true
        } catch (error: ReaderUIRuntimeException) {
            if (error.code != "INVALID_TYPED_RESULT") throw error
            false
        }
    }
    if (accepted.size != 1) {
        throw ReaderUIRuntimeException(
            "INVALID_TYPED_RESULT",
            "$event:$effectType Core result does not project to exactly one declared schema"
        )
    }
    return accepted.single()
}

private fun projectJsonValue(source: JsonElement, rawSchema: JsonElement): JsonElement? {
    if (source === JsonNull) return JsonNull
    val schema = rawSchema as? JsonObject ?: return source
    (schema["oneOf"] as? JsonArray)?.let { branches ->
        val candidates = branches.mapNotNull { projectJsonValue(source, it) }.distinctBy(JsonElement::toString)
        return candidates.singleOrNull() ?: candidates.firstOrNull()
    }
    return when ((schema["type"] as? JsonPrimitive)?.contentOrNull) {
        "object" -> {
            val sourceObject = source as? JsonObject ?: return source
            val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
            val additional = schema["additionalProperties"]
            JsonObject(buildMap {
                properties.forEach { (name, childSchema) ->
                    sourceObject[name]?.let { child ->
                        projectJsonValue(child, childSchema)?.let { put(name, it) }
                    }
                }
                if (additional is JsonObject) {
                    sourceObject.forEach { (name, child) ->
                        if (name !in properties) {
                            projectJsonValue(child, additional)?.let { put(name, it) }
                        }
                    }
                } else if ((additional as? JsonPrimitive)?.booleanOrNull == true) {
                    sourceObject.forEach { (name, child) -> if (name !in properties) put(name, child) }
                }
            })
        }
        "array" -> {
            val sourceArray = source as? JsonArray ?: return source
            val itemSchema = schema["items"] ?: return sourceArray
            JsonArray(sourceArray.mapNotNull { projectJsonValue(it, itemSchema) })
        }
        else -> source
    }
}
