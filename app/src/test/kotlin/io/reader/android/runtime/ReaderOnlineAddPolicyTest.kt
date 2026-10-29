package io.reader.android.runtime

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Finite real SDK packet shapes, no source rules/book content, IO, Native, HTTP or fabricated live ACK. NOT_RUN. */
class ReaderOnlineAddPolicyTest {
    @Test fun sameOriginalPreparationRevisionAndFullAddParametersAreRequiredForConfirmation() {
        val id = "android-onlineadd-00000000-0000-4000-8000-000000000001"
        val add = JSONObject().put("sourceId", "protocol-source").put("bookId", "protocol-book")
            .put("title", "protocol-title").put("author", "").put("requireReadable", true).put("sortIndex", 0)
        val preview = JSONObject().put("sourceId", "protocol-source") // Actual BookDetailData has no root bookId.
        preview.put("sourceVersion", "source-version").put("contentKind", "text")
            .put("book", JSONObject().put("bookId", "protocol-book").put("title", "protocol-title").put("author", ""))
        val parent = JSONObject().put("journalSchema", 1).put("kind", "onlineAdd").put("id", id)
            .put("sourceId", "protocol-source").put("bookId", "protocol-book").put("sourceVersion", "source-version")
            .put("previewJson", preview.toString()).put("pendingAddJson", add.toString()).put("completed", false)
        // Different insertion order is legitimate Core struct serialization, not an intent change.
        val echoed = JSONObject().put("requireReadable", true).put("author", "").put("title", "protocol-title")
            .put("bookId", "protocol-book").put("sourceId", "protocol-source").put("sortIndex", 0)
        val intent = JSONObject().put("schemaVersion", 1).put("sourceId", "protocol-source").put("bookId", "protocol-book")
            .put("sourceVersion", "source-version").put("reason", "add").put("state", "active").put("revision", 7).put("pendingAdd", echoed)
        assertEquals(7L, ReaderOnlineAddPolicy.prepared(JSONObject().put("intents", org.json.JSONArray().put(intent)), parent, add))
        val params = JSONObject(add.toString()).put("preparationRevision", 7)
        val stage = JSONObject().put("journalSchema", 1).put("kind", "onlineAddStage").put("id", "$id-add").put("parentId", id)
            .put("sourceId", "protocol-source").put("bookId", "protocol-book").put("sourceVersion", "source-version")
            .put("method", "bookshelf.add").put("parametersJson", params.toString()).put("attempted", true).put("completed", true)
            .put("requestId", 9).put("runtimeSession", "protocol-runtime")
        val terminal = JSONObject().put("protocolVersion", 1).put("requestId", 9).put("type", "result")
            .put("data", JSONObject().put("sourceId", "protocol-source").put("bookId", "protocol-book").put("created", true).put("addedAt", 100))
        stage.put("originalTerminalJson", terminal.toString())
        assertEquals(ReaderOnlineAddConfirmation.ADDED, ReaderOnlineAddPolicy.confirmedAdd(parent, stage, 7).confirmation)
        assertThrows(IllegalStateException::class.java) { ReaderOnlineAddPolicy.confirmedAdd(parent, stage, 8) }
        val foreign = JSONObject(stage.toString()).put("requestId", 10)
        assertThrows(IllegalStateException::class.java) { ReaderOnlineAddPolicy.confirmedAdd(parent, foreign, 7) }
        val unknown = JSONObject(stage.toString()).also { it.remove("originalTerminalJson") }
        assertThrows(org.json.JSONException::class.java) { ReaderOnlineAddPolicy.original(unknown) }
    }

    @Test fun legacyTextPreviewMayOmitOptionalVersionAndKindButForeignVersionStillFails() {
        val id = "android-onlineadd-00000000-0000-4000-8000-000000000003"
        val preview = JSONObject().put("sourceId", "protocol-source")
            .put("book", JSONObject().put("bookId", "protocol-book").put("title", "protocol-title").put("author", ""))
        val add = JSONObject().put("sourceId", "protocol-source").put("bookId", "protocol-book")
            .put("title", "protocol-title").put("author", "").put("requireReadable", true).put("sortIndex", 0)
        val parent = JSONObject().put("journalSchema", 1).put("kind", "onlineAdd").put("id", id)
            .put("sourceId", "protocol-source").put("bookId", "protocol-book").put("sourceVersion", "source-version")
            .put("previewJson", preview.toString()).put("pendingAddJson", add.toString()).put("completed", false)
        ReaderOnlineAddPolicy.record(parent)
        val foreign = JSONObject(preview.toString()).put("sourceVersion", "foreign")
        assertThrows(IllegalStateException::class.java) {
            ReaderOnlineAddPolicy.record(JSONObject(parent.toString()).put("previewJson", foreign.toString()))
        }
    }

    @Test fun omittedReadableIndexesAdmitAllTocEntriesAndForeignExplicitIndexesFail() {
        val toc = JSONObject().put("toc", org.json.JSONArray()
            .put(JSONObject().put("index", 3)).put(JSONObject().put("index", 7)))
        assertEquals(listOf(3, 7), ReaderOnlineAddPolicy.readableChapterIndexes(toc))
        val explicit = JSONObject(toc.toString()).put("readableChapterIndexes", org.json.JSONArray().put(7))
        assertEquals(listOf(7), ReaderOnlineAddPolicy.readableChapterIndexes(explicit))
        val foreign = JSONObject(toc.toString()).put("readableChapterIndexes", org.json.JSONArray().put(99))
        assertThrows(IllegalStateException::class.java) { ReaderOnlineAddPolicy.readableChapterIndexes(foreign) }
    }
}
