package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

/** Contract failures that could otherwise lose the original user operation. */
class ImportReceiptPolicyTest {
    private fun binding() = JSONObject().put("authorityEpoch", "original-epoch").put("sequence", 1L)
        .put("transactionId", "android-import-original").put("sourceId", "local")
        .put("bookId", "local:${"a".repeat(64)}").put("sourceSha256", "a".repeat(64))
        .put("sourceBytes", 3L).put("format", "txt")

    private fun record() = JSONObject().put("binding", binding()).put("receipts", JSONObject())
        .put("nextStage", "persist").put("inFlightStage", "persist")

    private fun receipt(stage: String, data: JSONObject = JSONObject().put("original", true)) = JSONObject()
        .put("stage", stage).put("outcome", "applied").put("semanticFingerprint", "semantic-$stage")
        .put("ackFingerprint", "ack-$stage").put("data", data).put("error", JSONObject.NULL)

    private fun snapshot(phase: String, terminal: Boolean, vararg values: JSONObject) = JSONObject()
        .put("binding", JSONObject(binding().toString())).put("phase", phase).put("terminal", terminal)
        .put("receipts", JSONArray(values.toList()))

    @Test fun persistAckAcceptsSameWireIntegersAndRetainsOriginalReceipt() {
        val result = ImportReceiptPolicy.accept(record(), snapshot("persisted", false, receipt("persist")), "persist")
        assertEquals("publish", result.getString("nextStage"))
        assertEquals("ack-persist", result.getJSONObject("receipts").getJSONObject("persist").getString("ackFingerprint"))
        assertFalse(result.has("inFlightStage"))
    }

    @Test fun foreignBindingCannotAcknowledgeOriginalImport() {
        val snapshot = snapshot("persisted", false, receipt("persist"))
        snapshot.getJSONObject("binding").put("transactionId", "another-operation")
        val original = record()
        assertThrows(IllegalStateException::class.java) { ImportReceiptPolicy.accept(original, snapshot, "persist") }
        assertEquals(0, original.getJSONObject("receipts").length())
    }

    @Test fun copiedFingerprintCannotHideChangedOriginalPayload() {
        val original = ImportReceiptPolicy.accept(record(), snapshot("persisted", false, receipt("persist")), "persist")
        val altered = receipt("persist", JSONObject().put("original", false))
        assertThrows(IllegalStateException::class.java) {
            ImportReceiptPolicy.accept(original, snapshot("persisted", false, altered), "persist")
        }
    }

    @Test fun missingRequestedStageDoesNotAcknowledgeUnknownPublish() {
        assertThrows(IllegalStateException::class.java) {
            ImportReceiptPolicy.accept(record(), snapshot("persisted", false, receipt("persist")), "publish")
        }
    }

    @Test fun finalizedTerminalRequiresExactOriginalBookAndOperation() {
        val wrongTerminal = JSONObject().put("transactionId", "android-import-original")
            .put("bookId", "local:${"b".repeat(64)}").put("stage", "finalize")
        assertThrows(IllegalStateException::class.java) {
            ImportReceiptPolicy.accept(record(), snapshot("finalized", true,
                receipt("persist"), receipt("publish"), receipt("finalize", wrongTerminal)), "finalize")
        }
    }

    @Test fun lateEarlierSnapshotCannotRegressDurableTerminal() {
        val terminal = JSONObject().put("transactionId", "android-import-original")
            .put("bookId", binding().getString("bookId")).put("stage", "finalize")
        val finalized = ImportReceiptPolicy.accept(record(), snapshot("finalized", true,
            receipt("persist"), receipt("publish"), receipt("finalize", terminal)), "finalize")
        val late = ImportReceiptPolicy.accept(finalized, snapshot("persisted", false, receipt("persist")), "persist")
        assertEquals("finalize", late.getString("terminalStage"))
        assertEquals("settle", late.getString("nextStage"))
    }

    private fun recovering(stage: String) = record().put("id", "android-import-original")
        .put("nextStage", stage).put("inFlightStage", stage).put("requestId", 1001L)
        .put("runtimeSession", "original-runtime").put("preview", JSONObject().put("originalPreview", true))
        .also { value ->
            val params = JSONObject().put("binding", binding())
            if (stage == "persist") params.put("parsed", value.getJSONObject("preview"))
            value.put("sentParameters", params)
        }

    private fun authority(head: Long = 0L) = JSONObject().put("protocolGeneration", 2)
        .put("baseSchemaVersion", 38).put("extensionSchema", 2).put("activeLimit", 8)
        .put("activeCount", 0).put("authorityEpoch", "original-epoch").put("headSequence", head)

    @Test fun absentPersistReplaysOnlyExplicitSameEpochNextSequenceWithoutInventingAck() {
        val original = recovering("persist")
        val query = JSONObject().put("authority", authority()).put("state", "absent")
            .put("operation", JSONObject.NULL).put("receipt", JSONObject.NULL)
        val allowed = ImportReceiptPolicy.recover(original, query, "persist", true)
        assertEquals("persist", allowed.getString("replayAuthorised"))
        assertEquals(0, allowed.getJSONObject("receipts").length())
        assertEquals(1001L, allowed.getJSONArray("transportHistory").getJSONObject(0).getLong("requestId"))
        ImportReceiptPolicy.sameBinding(original.getJSONObject("binding"), allowed.getJSONObject("binding"))
        assertThrows(PendingOperationException::class.java) { ImportReceiptPolicy.recover(original, query, "persist", false) }
        val gap = JSONObject(query.toString()).put("authority", authority(3L))
        assertThrows(PendingOperationException::class.java) { ImportReceiptPolicy.recover(original, gap, "persist", true) }
        val foreign = JSONObject(query.toString())
        foreign.getJSONObject("authority").put("authorityEpoch", "other-runtime-authority")
        assertThrows(IllegalStateException::class.java) { ImportReceiptPolicy.recover(original, foreign, "persist", true) }
    }

    @Test fun activeEarlierAcksAreAbsorbedBeforeExactPublishOrFinalizeReplay() {
        val original = recovering("publish")
        val query = JSONObject().put("authority", authority(1L)).put("state", "active")
            .put("operation", snapshot("persisted", false, receipt("persist"))).put("receipt", JSONObject.NULL)
        val allowed = ImportReceiptPolicy.recover(original, query, "publish", true)
        assertEquals("ack-persist", allowed.getJSONObject("receipts").getJSONObject("persist").getString("ackFingerprint"))
        assertEquals("publish", allowed.getString("replayAuthorised"))
        assertThrows(PendingOperationException::class.java) { ImportReceiptPolicy.recover(original, query, "publish", false) }
        val later = JSONObject(query.toString()).put("operation", snapshot("published", false, receipt("persist"), receipt("publish")))
        assertEquals("finalize", ImportReceiptPolicy.recover(recovering("finalize"), later, "finalize", true).getString("replayAuthorised"))
        query.getJSONObject("operation").getJSONObject("binding").put("transactionId", "different-import")
        assertThrows(IllegalStateException::class.java) { ImportReceiptPolicy.recover(original, query, "publish", true) }
    }

    @Test fun lostSettleAckReplaysOnlyWithDurableExactTerminalAndFingerprint() {
        val terminal = JSONObject().put("transactionId", "android-import-original")
            .put("bookId", binding().getString("bookId")).put("stage", "finalize")
        val original = ImportReceiptPolicy.accept(recovering("finalize"), snapshot("finalized", true,
            receipt("persist"), receipt("publish"), receipt("finalize", terminal)), "finalize")
            .put("inFlightStage", "settle").put("requestId", 1002L)
        original.put("sentParameters", JSONObject().put("binding", binding()).put("terminalStage", "finalize")
            .put("terminalReceiptFingerprint", "ack-finalize"))
        val allowed = ImportReceiptPolicy.settlementReplay(original, true)
        assertEquals("settle", allowed.getString("replayAuthorised"))
        assertEquals("ack-finalize", allowed.getJSONObject("receipts").getJSONObject("finalize").getString("ackFingerprint"))
        assertThrows(PendingOperationException::class.java) { ImportReceiptPolicy.settlementReplay(original, false) }
        val altered = JSONObject(original.toString())
        altered.getJSONObject("sentParameters").put("terminalReceiptFingerprint", "different-terminal")
        assertThrows(IllegalStateException::class.java) { ImportReceiptPolicy.settlementReplay(altered, true) }
    }
}
