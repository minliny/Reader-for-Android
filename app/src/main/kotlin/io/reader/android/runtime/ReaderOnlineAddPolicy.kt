package io.reader.android.runtime

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Original SDK envelope/DTO checks; no source, HTML, URL, directory or document parser. */
internal object ReaderOnlineAddPolicy {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val PARENT = "android-onlineadd-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
    val id = Regex(PARENT + "(?:-[a-z0-9]{1,20})?")
    fun encode(value: JSONObject) = value.toString().also { check(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "ONLINE_ADD_RECORD_BUDGET" } }
    fun parse(bytes: ByteArray): JSONObject {
        check(bytes.size in 1..MAX_BYTES) { "ONLINE_ADD_RECORD_BUDGET" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val tokens = JSONTokener(text); val value = tokens.nextValue() as? JSONObject ?: error("ONLINE_ADD_RECORD_OBJECT")
        check(tokens.nextClean() == '\u0000'); record(value); return value
    }
    fun record(value: JSONObject) {
        check(value.getInt("journalSchema") == 1 && id.matches(value.getString("id")) && value.get("completed") is Boolean &&
            value.getString("kind") in setOf("onlineAdd", "onlineAddStage") && value.getString("sourceId").isNotBlank() &&
            value.getString("sourceId") != "local" && value.getString("bookId").isNotBlank() && value.getString("sourceVersion").isNotBlank()) { "ONLINE_ADD_RECORD_IDENTITY" }
        if (value.getString("kind") == "onlineAddStage") {
            check(Regex(PARENT).matches(value.getString("parentId")) && value.getString("id").startsWith(value.getString("parentId") + "-") && value.get("attempted") is Boolean)
            check(value.getString("method") in setOf("bookshelf.get", "reading.preparation", "book.toc", "reading.directory.view.open.v2", "reading.directory.view.page.v2", "reading.directory.target.resolve.v2", "chapter.content", "reading.entry.prepare", "bookshelf.add")) { "ONLINE_ADD_STAGE_METHOD" }
            val params = JSONObject(value.getString("parametersJson"))
            check(params.getString("sourceId") == value.getString("sourceId") && params.getString("bookId") == value.getString("bookId")) { "ONLINE_ADD_STAGE_SCOPE" }
        } else {
            check(Regex(PARENT).matches(value.getString("id")))
            val preview = JSONObject(value.getString("previewJson")); val add = JSONObject(value.getString("pendingAddJson"))
            val previewVersion = ReaderOnlineCatalogPolicy.optional(preview, "sourceVersion") ?: value.getString("sourceVersion")
            val previewKind = ReaderOnlineCatalogPolicy.optional(preview, "contentKind") ?: "text"
            check(preview.getString("sourceId") == value.getString("sourceId") && previewVersion == value.getString("sourceVersion") &&
                previewKind == "text" && key(add) == key(value) && add.get("requireReadable") == true &&
                preview.getJSONObject("book").getString("bookId") == value.getString("bookId") &&
                preview.getJSONObject("book").getString("title") == add.getString("title") &&
                preview.getJSONObject("book").getString("author") == add.getString("author")) { "ONLINE_ADD_PARENT_SNAPSHOT" }
        }
    }
    fun key(row: JSONObject) = ShelfBookKey(row.getString("sourceId"), row.getString("bookId"))
    fun scope(row: JSONObject, key: ShelfBookKey) { check(this.key(row) == key) { "ONLINE_ADD_SCOPE" } }
    fun sourceVersion(value: JSONObject, expected: String): String {
        val returned = ReaderOnlineCatalogPolicy.optional(value, "sourceVersion")
        check(returned == null || returned == expected) { "ONLINE_SOURCE_VERSION_CHANGED" }
        return returned ?: expected
    }
    fun readableChapterIndexes(toc: JSONObject): List<Int> {
        val chapters = toc.getJSONArray("toc")
        val known = (0 until chapters.length()).map { chapters.getJSONObject(it).getInt("index") }.toSet()
        val indexes = if (!toc.has("readableChapterIndexes") || toc.isNull("readableChapterIndexes")) {
            // Core/Harmony omit this optional field for a fully readable text TOC.
            known.toList()
        } else {
            val values = toc.getJSONArray("readableChapterIndexes")
            (0 until values.length()).map { values.getInt(it) }
        }
        check(indexes.isNotEmpty() && indexes.distinct().size == indexes.size && indexes.all { it >= 0 && it in known }) {
            "ONLINE_ADD_NO_READABLE_CHAPTER"
        }
        return indexes
    }
    fun original(record: JSONObject): JSONObject {
        this.record(record)
        val event = JSONObject(record.getString("originalTerminalJson"))
        check(record.getString("kind") == "onlineAddStage" && event.getLong("requestId") == record.getLong("requestId") &&
            event.getInt("protocolVersion") == 1 && event.getString("type") in setOf("result", "error") &&
            record.getLong("requestId") > 0 && record.getString("runtimeSession").isNotBlank()) { "ONLINE_ADD_ORIGINAL_TERMINAL" }
        return event
    }
    fun prepared(value: JSONObject, parent: JSONObject, pendingAdd: JSONObject): Long {
        val rows = value.getJSONArray("intents"); check(rows.length() == 1) { "ONLINE_ADD_PREPARATION_COUNT" }
        val intent = rows.getJSONObject(0); scope(intent, key(parent))
        check(intent.getInt("schemaVersion") == 1 && intent.getString("sourceVersion") == parent.getString("sourceVersion") &&
            intent.getString("reason") == "add" && intent.getString("state") == "active" &&
            ProgressJournalCandidatePolicy.sameObject(JSONObject(intent.getJSONObject("pendingAdd").toString()), JSONObject(pendingAdd.toString()))) { "ONLINE_ADD_PREPARATION_BINDING" }
        return intent.getLong("revision").also { check(it in 1 until 9_007_199_254_740_991L) }
    }
    fun confirmedAdd(parent: JSONObject, stage: JSONObject, revision: Long): ReaderOnlineAddResult {
        val event = original(stage); val data = CoreTerminal.result("bookshelf.add", event); scope(data, key(parent))
        val expected = JSONObject(parent.getString("pendingAddJson")).put("preparationRevision", revision)
        check(stage.getString("parentId") == parent.getString("id") && stage.getString("id") == parent.getString("id") + "-add" &&
            stage.getString("sourceVersion") == parent.getString("sourceVersion") && stage.getBoolean("completed") &&
            ProgressJournalCandidatePolicy.sameObject(JSONObject(stage.getString("parametersJson")), JSONObject(expected.toString())) &&
            stage.getString("method") == "bookshelf.add" && data.get("created") is Boolean && data.getLong("addedAt") >= 0 &&
            JSONObject(stage.getString("parametersJson")).get("requireReadable") == true) { "ONLINE_ADD_ACK" }
        return ReaderOnlineAddResult(parent.getString("id"), key(parent), ReaderOnlineAddConfirmation.ADDED, true,
            originalAddTerminalJson = stage.getString("originalTerminalJson"), created = data.getBoolean("created"), addedAt = data.getLong("addedAt"))
    }
}
