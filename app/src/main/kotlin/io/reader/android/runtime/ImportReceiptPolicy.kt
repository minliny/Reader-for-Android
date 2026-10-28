package io.reader.android.runtime

import org.json.JSONObject
import org.json.JSONArray

/** Thin validation of Core f322's local_import_v2.rs DTO; no parsing or business decisions. */
internal object ImportReceiptPolicy {
    private val bindingFields = listOf("authorityEpoch", "sequence", "transactionId", "sourceId",
        "bookId", "sourceSha256", "sourceBytes", "format")
    private val stages = setOf("persist", "publish", "finalize", "rollback")

    fun authority(value: JSONObject, binding: JSONObject? = null) {
        check(value.getInt("protocolGeneration") == 2 && value.getInt("baseSchemaVersion") == 38 &&
            value.getInt("extensionSchema") == 2 && value.getInt("activeLimit") == 8 &&
            value.getInt("activeCount") in 0..8 && value.getString("authorityEpoch").isNotEmpty()) {
            "INCOMPATIBLE_IMPORT_AUTHORITY"
        }
        if (binding != null) check(value.getString("authorityEpoch") == binding.getString("authorityEpoch")) {
            "FOREIGN_IMPORT_AUTHORITY"
        }
    }

    fun sameBinding(expected: JSONObject, observed: JSONObject) {
        // org.json may decode the same JSON integer as Int or Long depending
        // on magnitude; compare its exact integral wire spelling instead.
        check(bindingFields.all { field ->
            val first = expected.get(field)
            val second = observed.get(field)
            if (field == "sequence" || field == "sourceBytes") first is Number && second is Number && first.toString() == second.toString()
            else first is String && second is String && first == second
        }) { "ORIGINAL_BINDING_CHANGED" }
    }

    /** Returns a new record; invalid snapshots never partially mutate the durable owner. */
    fun accept(record: JSONObject, snapshot: JSONObject, expectedStage: String?): JSONObject {
        val next = retireFlight(record)
        val binding = next.getJSONObject("binding")
        sameBinding(binding, snapshot.getJSONObject("binding"))
        val accumulated = next.getJSONObject("receipts")
        val seen = mutableSetOf<String>()
        val receipts = snapshot.getJSONArray("receipts")
        for (index in 0 until receipts.length()) {
            val receipt = receipts.getJSONObject(index)
            val stage = receipt.getString("stage")
            check(stage in stages && seen.add(stage)) { "INVALID_ORIGINAL_STAGE" }
            check(receipt.getString("semanticFingerprint").isNotEmpty() &&
                receipt.getString("ackFingerprint").isNotEmpty()) { "MISSING_ORIGINAL_FINGERPRINT" }
            when (receipt.getString("outcome")) {
                "applied" -> check(!receipt.isNull("data") && receipt.isNull("error"))
                "rejected" -> check(receipt.isNull("data") && !receipt.isNull("error"))
                else -> error("INVALID_ORIGINAL_OUTCOME")
            }
            val old = accumulated.optJSONObject(stage)
            // The fixed Core serializer emits a stable field order. Reject any
            // changed payload even if its opaque fingerprint has been copied.
            check(old == null || old.toString() == receipt.toString()) { "ORIGINAL_RECEIPT_CHANGED" }
            accumulated.put(stage, receipt)
        }
        check(expectedStage == null || expectedStage in seen) { "REQUESTED_ORIGINAL_ACK_MISSING" }
        fun outcome(stage: String) = accumulated.optJSONObject(stage)?.optString("outcome")
        val terminal = snapshot.getBoolean("terminal")
        when (snapshot.getString("phase")) {
            "persisted" -> {
                check(!terminal && outcome("persist") == "applied")
                next.put("nextStage", if (outcome("publish") == "rejected") "rollback" else "publish")
            }
            "published" -> {
                check(!terminal && outcome("persist") == "applied" && outcome("publish") == "applied")
                next.put("nextStage", "finalize")
            }
            "rejected" -> {
                check(terminal && outcome("persist") == "rejected")
                next.put("terminalStage", "persist").put("nextStage", "settle")
            }
            "finalized", "rolledBack" -> {
                val stage = if (snapshot.getString("phase") == "finalized") "finalize" else "rollback"
                check(terminal && outcome(stage) == "applied" && outcome("persist") == "applied")
                if (stage == "finalize") check(outcome("publish") == "applied")
                if (!record.isNull("terminalStage")) check(record.getString("terminalStage") == stage) {
                    "ORIGINAL_TERMINAL_CHANGED"
                }
                val data = accumulated.getJSONObject(stage).getJSONObject("data")
                check(data.getString("transactionId") == binding.getString("transactionId") &&
                    data.getString("bookId") == binding.getString("bookId") && data.getString("stage") == stage)
                next.put("terminalStage", stage).put("nextStage", "settle")
            }
            else -> error("INVALID_ORIGINAL_PHASE")
        }
        // A late older ACK can add its receipt, never regress a durable terminal.
        if (!record.isNull("terminalStage")) {
            next.put("terminalStage", record.getString("terminalStage")).put("nextStage", "settle")
        } else if (outcome("publish") == "applied" && !terminal) next.put("nextStage", "finalize")
        next.remove("inFlightStage")
        next.remove("wireTerminal")
        return next
    }

    /** One explicit recovery may replay only the same Core-arbitrated binding. */
    fun recover(record: JSONObject, query: JSONObject, stage: String, explicitRecovery: Boolean): JSONObject {
        val binding = record.getJSONObject("binding")
        val observedAuthority = query.optJSONObject("authority") ?: pending(record, stage)
        authority(observedAuthority, binding)
        val state = query.getString("state")
        if (state == "active") {
            if (binding.getLong("sequence") <= 0 || observedAuthority.getLong("headSequence") < binding.getLong("sequence")) {
                pending(record, stage)
            }
            val operation = query.optJSONObject("operation") ?: pending(record, stage)
            // Absorb all genuine earlier/later ACKs before deciding whether the
            // requested stage is already complete or needs exact arbitration.
            val next = accept(record, operation, null)
            val receipt = query.optJSONObject("receipt")
            if (receipt != null) {
                check(receipt.getString("stage") == stage &&
                    next.getJSONObject("receipts").getJSONObject(stage).toString() == receipt.toString()) {
                    "QUERY_ORIGINAL_RECEIPT_MISMATCH"
                }
            }
            if (next.getJSONObject("receipts").has(stage) || !next.isNull("terminalStage")) return next
            if (!explicitRecovery) pending(record, stage)
            val admissible = when (stage) {
                "publish" -> operation.getString("phase") == "persisted"
                "finalize" -> operation.getString("phase") == "published"
                "rollback" -> operation.getString("phase") in setOf("persisted", "published")
                else -> false
            }
            if (!admissible) pending(record, stage)
            verifyFrozenStage(record, stage)
            return next.put("nextStage", stage).put("replayAuthorised", stage)
        }
        if (state == "absent" && stage == "persist" && explicitRecovery &&
            query.isNull("operation") && query.isNull("receipt") &&
            record.getJSONObject("receipts").length() == 0) {
            val head = observedAuthority.getLong("headSequence")
            if (head !in 0 until Long.MAX_VALUE || binding.getLong("sequence") != head + 1 ||
                observedAuthority.getInt("activeCount") >= observedAuthority.getInt("activeLimit")) pending(record, stage)
            verifyFrozenStage(record, stage)
            // This is dispatch permission, not an invented persist ACK. Core's
            // Immediate transaction arbitrates an original worker/replay race.
            return retireFlight(record).put("nextStage", "persist").put("replayAuthorised", "persist")
                .put("persistReplayAttempted", true)
        }
        // Foreign/conflict/unknown/retired do not authorize any new stage.
        pending(record, stage)
    }

    fun settlementReplay(record: JSONObject, explicitRecovery: Boolean): JSONObject {
        if (!explicitRecovery || record.isNull("terminalStage")) pending(record, "settle")
        val stage = record.getString("terminalStage")
        val phase = when (stage) { "persist" -> "rejected"; "finalize" -> "finalized"; "rollback" -> "rolledBack"; else -> pending(record, "settle") }
        val values = JSONArray()
        for (name in listOf("persist", "publish", "finalize", "rollback")) {
            record.getJSONObject("receipts").optJSONObject(name)?.let { values.put(it) }
        }
        val snapshot = JSONObject().put("binding", record.getJSONObject("binding"))
            .put("phase", phase).put("terminal", true).put("receipts", values)
        val validated = accept(record, snapshot, stage)
        verifyFrozenStage(record, "settle")
        // A retired reply is accepted only because this exact original terminal
        // and ackFingerprint were already durable before the first settle send.
        return validated.put("nextStage", "settle").put("replayAuthorised", "settle")
    }

    private fun verifyFrozenStage(record: JSONObject, stage: String) {
        val sent = record.getJSONObject("sentParameters")
        sameBinding(record.getJSONObject("binding"), sent.getJSONObject("binding"))
        if (stage == "persist") check(sent.getJSONObject("parsed").toString() == record.getJSONObject("preview").toString()) {
            "ORIGINAL_IMPORT_PREVIEW_CHANGED"
        }
        if (stage == "settle") {
            val terminal = record.getString("terminalStage")
            check(sent.getString("terminalStage") == terminal && sent.getString("terminalReceiptFingerprint") ==
                record.getJSONObject("receipts").getJSONObject(terminal).getString("ackFingerprint")) {
                "ORIGINAL_SETTLEMENT_CHANGED"
            }
        }
    }

    private fun retireFlight(record: JSONObject): JSONObject {
        val next = JSONObject(record.toString())
        if (record.optString("inFlightStage").isNotEmpty() && record.has("requestId") && record.has("runtimeSession")) {
            val history = next.optJSONArray("transportHistory") ?: JSONArray()
            val attempt = JSONObject().put("stage", record.getString("inFlightStage"))
                .put("requestId", record.getLong("requestId")).put("runtimeSession", record.getString("runtimeSession"))
            record.optJSONObject("wireTerminal")?.let { attempt.put("terminal", it) }
            record.optJSONObject("sentParameters")?.optString("sourcePath")?.takeIf { it.isNotEmpty() }
                ?.let { attempt.put("sourcePath", it) }
            history.put(attempt)
            next.put("transportHistory", history)
        }
        next.remove("inFlightStage")
        next.remove("wireTerminal")
        return next
    }

    private fun pending(record: JSONObject, stage: String): Nothing =
        throw PendingOperationException(record.optString("id", "original-import"), stage)
}
