package io.reader.android.runtime

import org.json.JSONObject

/** Core DTO/scalar admission only; no HTML, directory, rule or canonical-text parser. */
internal object ReaderOnlineReadingPolicy {
    val METHODS = setOf("bookshelf.get", "cache.book.status", "reading.directory.view.open.v2", "reading.directory.view.page.v2",
        "reading.directory.target.resolve.v2", "reading.entry.snapshot", "reading.entry.prepare", "chapter.content", "reading.progress.get")
    fun remote(key: ShelfBookKey) { require(key.sourceId.isNotBlank() && key.sourceId != "local" && key.bookId.isNotBlank()) }
    fun scope(value: JSONObject, key: ShelfBookKey) {
        check(value.getString("sourceId") == key.sourceId && value.getString("bookId") == key.bookId) { "ONLINE_READING_SCOPE" }
    }
    fun chapter(key: ShelfBookKey, version: String, entry: JSONObject, progress: JSONObject, target: ReaderOnlineDirectoryTarget?): ReaderOnlineChapter {
        remote(key); scope(entry, key)
        check(entry.getString("kind") == "ready" && !entry.optBoolean("contentRefreshRequired") && !entry.optBoolean("sourceCorrectionRequired") &&
            (!entry.has("documentWindow") || entry.isNull("documentWindow"))) { "ONLINE_ENTRY_NOT_COMPLETE_READY" }
        val index = entry.getInt("chapterIndex"); check(index >= 0 && (target == null || index == target.chapterIndex))
        val position = entry.getJSONObject("positionScope"); scope(position, key)
        check(position.getInt("chapterIndex") == index && position.getString("bodyVersion").isNotBlank() &&
            position.getString("processingVersion").isNotBlank()) { "ONLINE_ENTRY_POSITION_SCOPE" }
        check(entry.get("content") is String) { "ONLINE_TEXT_CONTENT_REQUIRED" }
        val text = entry.getString("content"); check(text.isNotBlank()) { "ONLINE_EMPTY_CONTENT_NOT_ADMITTED" }
        val blocks = entry.getJSONArray("blocks")
        check((0 until blocks.length()).all { blocks.getJSONObject(it).getString("kind") == "text" }) { "ONLINE_NON_TEXT_BLOCK_NOT_ADMITTED" }
        val revision = progress.getString("progressRevision")
        check(revision.matches(Regex("reader-progress-v1:[a-f0-9]{64}"))) { "ONLINE_PROGRESS_OBSERVATION_REQUIRED" }
        val found = progress.getBoolean("found")
        val stored = if (found) progress.getJSONObject("progress") else null
        val inEntry = if (entry.isNull("progress")) null else entry.getJSONObject("progress")
        check((stored == null) == (inEntry == null)) { "ONLINE_ENTRY_PROGRESS_CHANGED" }
        if (stored != null && inEntry != null) {
            scope(stored, key); scope(inEntry, key)
            for (field in listOf("chapterIndex", "chapterOffset", "updatedAt")) check(stored.get(field) == inEntry.get(field)) { "ONLINE_ENTRY_PROGRESS_CHANGED" }
        }
        val count = text.codePointCount(0, text.length)
        val offset = if (target != null) {
            check(!entry.optBoolean("resumeOnly")) { "ONLINE_EXPLICIT_TARGET_NOT_READABLE" }
            target.positionScopeJson?.let { check(ProgressJournalCandidatePolicy.sameObject(position, JSONObject(it))) { "ONLINE_TARGET_SCOPE_CHANGED" } }
            target.scalarOffset
        } else if (stored != null) {
            check(stored.getInt("chapterIndex") == index && stored.getString("bodyVersion") == position.getString("bodyVersion") &&
                stored.getString("processingVersion") == position.getString("processingVersion")) { "ONLINE_RESTORE_SCOPE_CHANGED" }
            stored.getLong("chapterOffset").also { check(it in 0..count.toLong()) }.toInt()
        } else 0
        check(offset in 0..count) { "ONLINE_RESTORE_SCALAR_RANGE" }
        return ReaderOnlineChapter(ReaderChapter(key.bookId, index, entry.getString("chapterTitle"), text,
            position.getString("bodyVersion"), position.getString("processingVersion"), sourceId = key.sourceId,
            presentationKind = ReaderTextPresentationKind.ONLINE_TEXT), offset, version, revision,
            if (entry.isNull("baseUrl")) null else entry.getString("baseUrl"), entry.optBoolean("resumeOnly"))
    }
}
