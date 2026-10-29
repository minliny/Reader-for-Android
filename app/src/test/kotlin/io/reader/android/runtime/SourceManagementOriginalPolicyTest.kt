package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Exact DTO/protocol qualification only; no filesystem/Core/HTTP or fsync claim. NOT RUN. */
class SourceManagementOriginalPolicyTest {
    @Test fun updateAndDeleteRequireTheirOwnExactOriginalInsteadOfImportOrFreshList() {
        val update = original("source.update", JSONObject().put("sourceId", "source-a").put("enabled", false))
        val business = SourceRulesReceiptPolicy.parameters(update).getJSONObject("mutation").getJSONObject("params")
        assertEquals(setOf("sourceId", "enabled"), business.keys().asSequence().toSet())
        val data = JSONObject().put("source", JSONObject().put("sourceId", "source-a").put("enabled", false)
            .put("sourceVersion", "a".repeat(64)).put("bookSource", JSONObject().put("enabled", false)))
        val receipt = receipt(update, data)
        assertEquals(SourceImportConfirmation.APPLIED, SourceRulesReceiptPolicy.receipt(update, receipt))
        assertNotNull(SourceRulesReceiptPolicy.qualifiedQuery(update, JSONObject().put("head", receipt.getJSONObject("binding")).put("receipt", receipt)))
        assertNull(SourceRulesReceiptPolicy.qualifiedQuery(update, JSONObject().put("head", receipt.getJSONObject("binding")).put("receipt", JSONObject.NULL)))
        expectRejected { SourceRulesReceiptPolicy.receipt(update, JSONObject(receipt.toString()).put("method", "source.import")) }
        expectRejected { SourceRulesReceiptPolicy.receipt(update, receipt(update, JSONObject().put("source", JSONObject(data.getJSONObject("source").toString()).put("sourceId", "source-b")))) }
        expectRejected { SourceRulesReceiptPolicy.parameters(JSONObject(update.toString()).put("originalBookSourceJson", "{}")) }
        val changed = original("source.update", JSONObject().put("sourceId", "source-a").put("enabled", true))
        expectRejected { SourceRulesReceiptPolicy.retainOriginal(update, changed) }
        val deletion = original("source.delete", JSONObject().put("sourceIds", JSONArray().put("source-a")))
        for (count in 0..1) assertEquals(SourceImportConfirmation.APPLIED,
            SourceRulesReceiptPolicy.receipt(deletion, receipt(deletion, JSONObject().put("deleted", count))))
        expectRejected { SourceRulesReceiptPolicy.receipt(deletion, receipt(deletion, JSONObject().put("deleted", 2))) }
        expectRejected { SourceRulesReceiptPolicy.parameters(original("source.delete", JSONObject().put("sourceIds", JSONArray().put("source-a").put("source-b")))) }
    }
    private fun original(method: String, business: JSONObject): JSONObject {
        val uuid = "11111111-2222-3333-4444-555555555555"
        return JSONObject().put("journalSchema", 1).put("kind", "sourceRules").put("method", method)
            .put("id", "android-source-$uuid").put("sourceId", "source-a").put("observedSourceVersion", "a".repeat(64))
            .put("hostCleanupRequired", method == "source.delete" || !business.optBoolean("enabled"))
            .put("hostCleanupConfirmed", false).put("completed", false)
            .put("parametersJson", JSONObject().put("operationId", "$uuid/7").put("sequence", 7)
                .put("mutation", JSONObject().put("method", method).put("params", business)).toString())
    }
    private fun receipt(record: JSONObject, data: JSONObject): JSONObject {
        val params = JSONObject(record.getString("parametersJson"))
        return JSONObject().put("binding", JSONObject().put("operationId", params.getString("operationId")).put("sequence", 7).put("payloadFingerprint", "b".repeat(64)))
            .put("method", record.getString("method")).put("outcome", "applied").put("data", data).put("ackFingerprint", "c".repeat(64))
    }
    private fun expectRejected(action: () -> Unit) { try { action(); fail("foreign or changed original was accepted") } catch (_: IllegalStateException) {} }
}
