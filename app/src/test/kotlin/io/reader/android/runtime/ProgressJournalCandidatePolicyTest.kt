package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProgressJournalCandidatePolicyTest {
    @Test fun retainedOriginalWireQualifiesWithoutPromotionAndRejectsConflictingOrIncompleteCandidate() {
        // Frozen actual3267 stopped journal bytes, not a simulated book/source/URL.
        val base = actualActive.toByteArray(Charsets.UTF_8)
        val candidate = actualCandidate.toByteArray(Charsets.UTF_8)
        val originalBase = base.copyOf(); val originalCandidate = candidate.copyOf()
        val qualified = ProgressJournalCandidatePolicy.qualify(base, candidate)
        assertEquals(739, base.size); assertEquals(1300, candidate.size)
        assertEquals("574b36dff73ad4dbd9c7efcccf3227527e468fe77a9fae837f27a3ce3a90abd2", qualified.activeSha256)
        assertEquals("60c798f57c349bff0a1019f3c33217afac7d2cf16395574eed21cad0b9609200", qualified.candidateSha256)
        assertFalse(qualified.record().getBoolean("completed"))
        assertFalse(JSONObject(actualActive).has("wireTerminal"))
        assertEquals(1012L, qualified.record().getLong("requestId"))
        assertEquals(13L, qualified.record().getJSONObject("wireTerminal").getLong("deliveryId"))
        assertArrayEquals(originalBase, base); assertArrayEquals(originalCandidate, candidate)
        qualified.activeBytesCopy().fill(0); qualified.candidateBytesCopy().fill(0)
        assertArrayEquals(originalBase, qualified.activeBytesCopy()); assertArrayEquals(originalCandidate, qualified.candidateBytesCopy())
        base.fill(0); candidate.fill(0)
        assertArrayEquals(originalBase, qualified.activeBytesCopy()); assertArrayEquals(originalCandidate, qualified.candidateBytesCopy())
        fun reject(change: (JSONObject) -> Unit) = rejected {
            val changed = JSONObject(actualCandidate).apply(change).toString().toByteArray(Charsets.UTF_8)
            ProgressJournalCandidatePolicy.qualify(originalBase, changed)
        }
        reject { it.put("runtimeSession", "retired-other-dispatch") }
        reject { it.put("completed", true) }
        reject { it.getJSONObject("parameters").put("chapterOffset", 1) }
        reject { it.getJSONObject("wireTerminal").put("requestId", 1013) }
        reject { it.getJSONObject("wireTerminal").put("protocolVersion", 1.5) }
        reject { it.getJSONObject("wireTerminal").put("deliveryId", 0) }
        reject { it.getJSONObject("wireTerminal").put("type", "error") }
        reject { it.getJSONObject("wireTerminal").getJSONObject("data").put("chapterOffset", 1) }
        reject { it.getJSONObject("wireTerminal").getJSONObject("data").put("stored", "true") }
        reject { it.getJSONObject("wireTerminal").getJSONObject("data").put("processingVersion", "changed") }
        rejected { ProgressJournalCandidatePolicy.qualify(originalBase, originalCandidate.copyOf(originalCandidate.size - 2)) }
        rejected { ProgressJournalCandidatePolicy.qualify(originalBase, originalCandidate + " trailing".toByteArray()) }
        rejected { ProgressJournalCandidatePolicy.qualify(originalBase, byteArrayOf(0xc3.toByte(), 0x28)) }
        rejected { ProgressJournalCandidatePolicy.qualify(originalBase, ByteArray(ProgressJournalCandidatePolicy.MAX_BYTES + 1)) }
        // No fake Completed record or fresh progress.get is used to qualify the original wire.
        assertFalse(qualified.record().getBoolean("completed"))
    }

    private fun rejected(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        throw AssertionError("Conflicting or incomplete original candidate was accepted")
    }

    private val actualActive = """{"journalSchema":1,"kind":"progress","id":"android-progress-3851fa03-2892-4487-8cda-9e8875409311","bookId":"local:a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef","parameters":{"sourceId":"local","bookId":"local:a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef","chapterIndex":3,"chapterOffset":0,"chapterProgress":0,"expectedBodyVersion":"415874d4786a5439c1573e59b44c5d4cfe532881698dc19094c1e7c766671aac","expectedProcessingVersion":"76a2ba63994809ce07bf9f73e31bcd85ad96b78dcba6ac57b0f5a011e97fcc14","expectedProgressRevision":"reader-progress-v1:cec125191e9e6313955ec6702403f8ae48acd1839ecbed53efb0036cab1e034b"},"completed":false,"requestId":1012,"runtimeSession":"7eedfbbe-4e17-469c-b8e9-bef9c783afc7"}"""
    private val actualCandidate = """{"journalSchema":1,"kind":"progress","id":"android-progress-3851fa03-2892-4487-8cda-9e8875409311","bookId":"local:a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef","parameters":{"sourceId":"local","bookId":"local:a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef","chapterIndex":3,"chapterOffset":0,"chapterProgress":0,"expectedBodyVersion":"415874d4786a5439c1573e59b44c5d4cfe532881698dc19094c1e7c766671aac","expectedProcessingVersion":"76a2ba63994809ce07bf9f73e31bcd85ad96b78dcba6ac57b0f5a011e97fcc14","expectedProgressRevision":"reader-progress-v1:cec125191e9e6313955ec6702403f8ae48acd1839ecbed53efb0036cab1e034b"},"completed":false,"requestId":1012,"runtimeSession":"7eedfbbe-4e17-469c-b8e9-bef9c783afc7","wireTerminal":{"type":"result","protocolVersion":1,"requestId":1012,"data":{"bodyVersion":"415874d4786a5439c1573e59b44c5d4cfe532881698dc19094c1e7c766671aac","bookId":"local:a25dcde710240f9c2c03d7b7b98aa09277d83392f3dc0da9779f6bc78e7c0cef","chapterIndex":3,"chapterOffset":0,"chapterProgress":0,"processingVersion":"76a2ba63994809ce07bf9f73e31bcd85ad96b78dcba6ac57b0f5a011e97fcc14","progressRevision":"reader-progress-v1:87d4f15dce0a9abdd5f366a4ed21341ff96b52923e48d5c9dac2d4c4c3007221","sourceId":"local","stored":true,"updatedAt":1793252640},"deliveryId":13}}"""
}
