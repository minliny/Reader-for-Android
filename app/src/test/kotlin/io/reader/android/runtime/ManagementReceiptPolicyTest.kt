package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementReceiptPolicyTest {
    private val uuid = "12345678-1234-4234-8123-123456789abc"

    private fun binding(sequence: Long = 1) = JSONObject().put("operationId", "$uuid/$sequence")
        .put("sequence", sequence).put("payloadFingerprint", "a".repeat(64))

    private fun group(id: Long = -7, order: Int = Int.MIN_VALUE) = JSONObject().put("groupId", id).put("groupName", "Original")
        .put("order", order).put("enableRefresh", false).put("show", true)

    private fun record(method: String = "book-group.update", params: JSONObject = JSONObject().put("groupId", -7)
        .put("groupName", "Original").put("order", Int.MIN_VALUE).put("enableRefresh", false).put("show", true)) =
        JSONObject().put("kind", ManagementReceiptPolicy.KIND).put("id", "android-management-$uuid").put("method", method).put("completed", false)
            .put("parametersJson", JSONObject().put("operationId", "$uuid/1").put("sequence", 1)
                .put("mutation", JSONObject().put("method", method).put("params", params)).toString())

    private fun receipt(method: String = "book-group.update", data: JSONObject = JSONObject().put("group", group())) =
        JSONObject().put("binding", binding()).put("method", method).put("outcome", "applied").put("data", data)
            .put("ackFingerprint", "b".repeat(64))

    private fun query(receipt: JSONObject? = null, head: JSONObject? = null, allowed: Boolean = false) = JSONObject()
        .put("head", head ?: JSONObject.NULL).put("receipt", receipt ?: JSONObject.NULL).put("canApplySameOriginal", allowed)

    @Test fun permanentHeadOwnsSequenceAndSafeLimit() {
        assertEquals(1L, ManagementReceiptPolicy.nextSequence(query()))
        assertEquals(10L, ManagementReceiptPolicy.nextSequence(query(head = binding(9))))
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.nextSequence(query(head = binding(9_007_199_254_740_991L)))
        }
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.nextSequence(query(head = binding().put("sequence", 2)))
        }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.nextSequence(query(allowed = true)) }
    }

    @Test fun immutableOriginalDtoCannotBeChangedByDispatchCopy() {
        val record = record()
        val original = record.getString("parametersJson")
        val sent = ManagementReceiptPolicy.parameters(record)
        sent.getJSONObject("mutation").getJSONObject("params").put("groupName", "New caller draft")
        assertEquals(original, record.getString("parametersJson"))
        assertEquals("Original", ManagementReceiptPolicy.parameters(record).getJSONObject("mutation").getJSONObject("params").getString("groupName"))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.parameters(record().put("method", "book-group.delete")) }
    }

    @Test fun onlyOriginalWireAndBindingCanQualify() {
        val record = record().put("requestId", 1001L)
        val event = JSONObject().put("protocolVersion", 1).put("requestId", 1001L).put("type", "result").put("data", receipt())
        ManagementReceiptPolicy.wireReceipt(record, event)
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.wireReceipt(record, JSONObject(event.toString()).put("protocolVersion", 1.0))
        }
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.wireReceipt(record, JSONObject(event.toString()).put("requestId", 1001.0))
        }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.wireReceipt(record, JSONObject(event.toString()).put("requestId", 1002L)) }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.wireReceipt(record, JSONObject(event.toString()).put("type", "error")) }
        for (field in listOf("operationId", "sequence", "payloadFingerprint")) {
            val changed = receipt()
            changed.getJSONObject("binding").put(field, when (field) {
                "operationId" -> "87654321-1234-4234-8123-123456789abc/1"
                "sequence" -> 2
                else -> "INVALID"
            })
            assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(record, changed) }
        }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(record, receipt().put("method", "book-group.create")) }
        assertThrows(Exception::class.java) { ManagementReceiptPolicy.receipt(record, JSONObject().put("groups", org.json.JSONArray())) }
    }

    @Test fun onlyCurrentCoreEligibilityPermitsSameOriginalReplay() {
        val record = record()
        assertTrue(ManagementReceiptPolicy.canReplay(record, query(allowed = true)))
        assertFalse(ManagementReceiptPolicy.canReplay(record, query()))
        assertFalse(ManagementReceiptPolicy.canReplay(record, query(head = binding(2))))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.canReplay(record, query(head = binding(2), allowed = true)) }
        val originalReceipt = receipt()
        assertFalse(ManagementReceiptPolicy.canReplay(record, query(originalReceipt, binding(), true)))
        // Even an absent Core head cannot authorize reapplying a known original ACK.
        assertFalse(ManagementReceiptPolicy.canReplay(record.put("receiptJson", originalReceipt.toString()), query(allowed = true)))
    }

    @Test fun durableTerminalPinsAllocatedIdAndOriginalDeleteFalse() {
        val create = record("book-group.create", JSONObject().put("groupName", "Original").put("order", Int.MIN_VALUE)
            .put("enableRefresh", false).put("show", true))
        val original = receipt("book-group.create")
        create.put("receiptJson", original.toString())
        assertEquals(-7L, ManagementReceiptPolicy.receipt(create, original).group?.groupId)
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.receipt(create, JSONObject(original.toString()).put("ackFingerprint", "c".repeat(64)))
        }
        val differentAllocatedId = JSONObject(original.toString()).also { it.getJSONObject("data").getJSONObject("group").put("groupId", -8) }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(create, differentAllocatedId) }
        val deletion = record("book-group.delete", JSONObject().put("groupId", Long.MIN_VALUE))
        val deleted = receipt("book-group.delete", JSONObject().put("groupId", Long.MIN_VALUE).put("deleted", false))
        deletion.put("receiptJson", deleted.toString())
        assertEquals(false, ManagementReceiptPolicy.receipt(deletion, deleted).deletion?.deleted)
        val differentDeleteFact = JSONObject(deleted.toString()).also { it.getJSONObject("data").put("deleted", true) }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(deletion, differentDeleteFact) }
        assertThrows(IllegalStateException::class.java) {
            ManagementReceiptPolicy.receipt(deletion, JSONObject(deleted.toString()).put("ackFingerprint", "d".repeat(64)))
        }
    }

    @Test fun fullCoreScalarRangesAndAssignmentIdentityArePreserved() {
        for ((id, order) in listOf(Long.MIN_VALUE to Int.MIN_VALUE, Long.MAX_VALUE to Int.MAX_VALUE)) {
            val decoded = ManagementReceiptPolicy.group(group(id, order))
            assertEquals(id, decoded.groupId)
            assertEquals(order, decoded.order)
        }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.group(group().put("order", Int.MAX_VALUE.toLong() + 1)) }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.group(group().put("groupId", "-7")) }
        val assignment = record("bookshelf.group.assign", JSONObject().put("sourceId", "source-original").put("bookId", "same-id").put("group", ""))
        val ack = receipt("bookshelf.group.assign", JSONObject().put("book", JSONObject().put("sourceId", "source-original").put("bookId", "same-id")))
        assertEquals(null, ManagementReceiptPolicy.receipt(assignment, ack).assignment?.group)
        val foreign = JSONObject(ack.toString()).also { it.getJSONObject("data").getJSONObject("book").put("sourceId", "other-source") }
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(assignment, foreign) }
    }

    @Test fun explicitRejectedReceiptIsTerminalButRemovalRequiresSeparateOwner() {
        val rejected = receipt().put("outcome", "rejected").put("data", JSONObject().put("error", JSONObject()
            .put("code", "INVALID_PARAMS").put("message", "Original group was absent")))
        val result = ManagementReceiptPolicy.receipt(record(), rejected)
        assertEquals(ManagementConfirmation.REJECTED, result.confirmation)
        assertEquals("Original group was absent", result.rejection?.message)
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.receipt(record(), rejected.put("outcome", "unknown")) }
        val removal = record("bookshelf.remove", JSONObject().put("sourceId", "local").put("bookId", "original"))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.parameters(removal) }
    }

    @Test fun knownWarmReceiptAndRequestGenerationCannotBeDowngradedByOldDisk() {
        assertTrue(ManagementReceiptPolicy.isActive(record()))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.isActive(record().put("completed", "true")) }
        val disk = record().put("requestId", 1001L).put("runtimeSession", "old-runtime")
            .put("wireTerminal", JSONObject().put("requestId", 1001L).put("type", "error"))
        val history = org.json.JSONArray().put(JSONObject().put("requestId", 1009L))
        val warm = record().put("requestId", 1009L).put("runtimeSession", "current-runtime")
            .put("receiptJson", receipt().toString()).put("dispatchHistory", history)
        val merged = ManagementReceiptPolicy.retainOriginal(disk, warm)!!
        assertTrue(merged === warm)
        assertEquals(1009L, merged.getLong("requestId"))
        assertEquals("current-runtime", merged.getString("runtimeSession"))
        assertEquals(history.toString(), merged.getJSONArray("dispatchHistory").toString())
        assertFalse(merged.has("wireTerminal"))
        assertEquals(ManagementConfirmation.APPLIED, ManagementReceiptPolicy.receipt(merged, JSONObject(merged.getString("receiptJson"))).confirmation)
        assertFalse(ManagementReceiptPolicy.canReplay(merged, query(allowed = true)))
        val late = JSONObject(disk.toString()).put("requestId", 1009L).put("runtimeSession", "current-runtime")
            .put("wireTerminal", JSONObject().put("requestId", 1009L).put("type", "result"))
        ManagementReceiptPolicy.retainOriginal(late, warm)
        assertEquals(1009L, warm.getJSONObject("wireTerminal").getLong("requestId"))
        val changed = JSONObject(disk.toString()).put("parametersJson", record("book-group.delete", JSONObject().put("groupId", -7)).getString("parametersJson"))
        assertThrows(IllegalStateException::class.java) { ManagementReceiptPolicy.retainOriginal(changed, warm) }
    }
}
