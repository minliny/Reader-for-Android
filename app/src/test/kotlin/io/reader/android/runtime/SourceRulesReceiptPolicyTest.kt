package io.reader.android.runtime

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Real org.json protocol DTOs only: no Core, Android I/O, network or book content. */
class SourceRulesReceiptPolicyTest {
    @Test fun numericBookSourceWireViewRetainsOriginalRawAndRejectsChangedIntent() {
        for ((literal, changed) in listOf("1.0" to "2.0", "1e3" to "2e3", "-0.0" to "1.0")) {
            val raw = sourceJson(literal)
            val record = original(raw)
            val parametersJson = record.getString("parametersJson")
            val decoded = SourceRulesReceiptPolicy.parameters(record)
            assertEquals(raw, record.getString("originalBookSourceJson"))
            assertEquals(parametersJson, record.getString("parametersJson"))
            assertEquals(parametersJson, decoded.toString())
            // Use the library's own wire roundtrip, never a test number matcher or JSON substitute.
            val view = JSONObject(JSONObject(raw).toString())
            assertEquals(view.toString(), decoded.getJSONObject("mutation").getJSONObject("params").getJSONObject("bookSource").toString())
            val foreign = copy(record).put("originalBookSourceJson", sourceJson(changed))
            val failure = assertThrows(IllegalStateException::class.java) { SourceRulesReceiptPolicy.parameters(foreign) }
            assertEquals("SOURCE_ORIGINAL_JSON_CHANGED", failure.message)
            assertEquals(raw, record.getString("originalBookSourceJson"))
            assertFalse(record.getBoolean("completed"))
        }
    }

    @Test fun originalQueryRetainsNullAndRejectsForeignHeadWireAndReceipt() {
        val record = original(sourceJson("1.0"))
        val unchanged = record.toString()
        val empty = query(null, null)
        assertNull(SourceRulesReceiptPolicy.qualifiedQuery(record, empty))
        assertEquals(1L, SourceRulesReceiptPolicy.nextSequence(empty))
        val receipt = appliedReceipt(record)
        val head = receipt.getJSONObject("binding")
        assertNull(SourceRulesReceiptPolicy.qualifiedQuery(record, query(copy(head), null)))
        assertEquals(2L, SourceRulesReceiptPolicy.nextSequence(query(copy(head), null)))
        assertSame(receipt, SourceRulesReceiptPolicy.qualifiedQuery(record, query(copy(head), receipt)))
        assertEquals(SourceImportConfirmation.APPLIED, SourceRulesReceiptPolicy.receipt(record, receipt))
        assertThrows(IllegalStateException::class.java) {
            SourceRulesReceiptPolicy.qualifiedQuery(record, JSONObject().put("receipt", JSONObject.NULL))
        }
        assertThrows(IllegalStateException::class.java) { SourceRulesReceiptPolicy.qualifiedQuery(record, query(null, receipt)) }
        val foreignHead = copy(head).put("payloadFingerprint", "c".repeat(64))
        assertThrows(IllegalStateException::class.java) { SourceRulesReceiptPolicy.qualifiedQuery(record, query(foreignHead, receipt)) }

        val foreignOperation = copy(receipt).apply { getJSONObject("binding").put("operationId", "22345678-1234-4234-8123-123456789abc/1") }
        val foreignSequence = copy(receipt).apply { getJSONObject("binding").put("operationId", "$OPERATION/2").put("sequence", 2) }
        val foreignSource = copy(receipt).apply { getJSONObject("data").put("sourceId", "another-protocol-source") }
        val notImported = copy(receipt).apply { getJSONObject("data").put("imported", false) }
        for (foreign in listOf(foreignOperation, foreignSequence, foreignSource, notImported,
            copy(receipt).put("method", "source.update"))) {
            assertThrows(IllegalStateException::class.java) {
                SourceRulesReceiptPolicy.qualifiedQuery(record, query(copy(foreign.getJSONObject("binding")), foreign))
            }
        }
        val incomplete = copy(receipt).apply { remove("ackFingerprint") }
        assertThrows(JSONException::class.java) { SourceRulesReceiptPolicy.qualifiedQuery(record, query(copy(head), incomplete)) }

        val known = copy(record).put("receiptJson", receipt.toString())
        for (changed in listOf(copy(receipt).put("ackFingerprint", "d".repeat(64)),
            copy(receipt).apply { getJSONObject("binding").put("payloadFingerprint", "e".repeat(64)) })) {
            val failure = assertThrows(IllegalStateException::class.java) {
                SourceRulesReceiptPolicy.qualifiedQuery(known, query(copy(changed.getJSONObject("binding")), changed))
            }
            assertEquals("SOURCE_ORIGINAL_RECEIPT_CHANGED", failure.message)
        }
        val wire = JSONObject().put("protocolVersion", 1).put("requestId", 71).put("deliveryId", 101)
            .put("type", "result").put("data", receipt)
        assertSame(receipt, SourceRulesReceiptPolicy.wireReceipt(record, wire))
        assertThrows(IllegalStateException::class.java) { SourceRulesReceiptPolicy.wireReceipt(record, copy(wire).put("requestId", 72)) }
        assertEquals(unchanged, record.toString())
        assertEquals(SourceImportConfirmation.UNKNOWN, SourceRulesReceiptPolicy.confirmation(record))
        assertFalse(record.has("receiptJson"))
        assertFalse(record.getBoolean("completed"))
    }

    private fun sourceJson(numberLiteral: String) = """{"bookSourceName":"numeric protocol DTO","opaqueNumber":$numberLiteral}"""
    private fun original(raw: String): JSONObject {
        val parametersJson = JSONObject().put("operationId", "$OPERATION/1").put("sequence", 1)
            .put("mutation", JSONObject().put("method", "source.import").put("params",
                JSONObject().put("sourceId", SOURCE).put("bookSource", JSONObject(raw)))).toString()
        return JSONObject().put("journalSchema", 1).put("kind", "sourceRules")
            .put("id", "android-source-$OPERATION").put("sourceId", SOURCE).put("method", "source.import")
            .put("originalBookSourceJson", raw).put("parametersJson", parametersJson).put("completed", false)
            .put("requestId", 71).put("runtimeSession", "32345678-1234-4234-8123-123456789abc")
    }
    /** Opaque fingerprints are supplied protocol fixtures; no Core hash is computed or claimed. */
    private fun appliedReceipt(record: JSONObject) = JSONObject()
        .put("binding", JSONObject().put("operationId", "$OPERATION/1").put("sequence", 1).put("payloadFingerprint", "a".repeat(64)))
        .put("method", "source.import").put("outcome", "applied")
        .put("data", JSONObject().put("sourceId", record.getString("sourceId")).put("name", "numeric protocol DTO").put("imported", true))
        .put("ackFingerprint", "b".repeat(64))
    private fun query(head: JSONObject?, receipt: JSONObject?) = JSONObject()
        .put("head", head ?: JSONObject.NULL).put("receipt", receipt ?: JSONObject.NULL)
    private fun copy(value: JSONObject) = JSONObject(value.toString())
    private companion object {
        const val OPERATION = "12345678-1234-4234-8123-123456789abc"
        const val SOURCE = "numeric-protocol-source"
    }
}
