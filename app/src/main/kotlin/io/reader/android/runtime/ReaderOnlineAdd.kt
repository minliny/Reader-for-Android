package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One App service and existing held lane. Metadata preview never becomes a ReadingSession. */
internal class ReaderOnlineAdd(
    private val transport: CoreTransport,
    journal: DurableJournal,
    private val assertHeld: () -> Unit,
    private val authorize: (ReaderOperationOwner.ClientLease, ReaderOnlineDetailPreview) -> ReaderOnlineAddAuthority,
    private val sources: (String) -> List<ReaderSourceEntry>,
    private val beforeNewStage: () -> Unit,
) {
    private val originals = ReaderOnlineAddStages(transport, journal)

    fun begin(lease: ReaderOperationOwner.ClientLease, preview: ReaderOnlineDetailPreview): ReaderOnlineAddResult {
        assertHeld(); beforeNewStage()
        pending().firstOrNull()?.let { throw ReaderOnlineAddUnconfirmed(it.operationId, "previous-intent") }
        val authority = authorize(lease, preview)
        check(authority.lease == lease && authority.runtimeSession == transport.session && preview.contentKind == ReaderOnlineContentKind.TEXT)
        check(preview.key.sourceId != "local" && preview.title.isNotBlank()) { "ONLINE_TEXT_PREVIEW_REQUIRED" }
        val raw = JSONObject(preview.originalResultJson)
        check(raw.getString("sourceId") == preview.key.sourceId && raw.getJSONObject("book").getString("bookId") == preview.key.bookId) { "ONLINE_ADD_PREVIEW_SCOPE" }
        check(raw.getString("sourceVersion") == preview.sourceVersion && raw.getString("contentKind") == "text")
        val add = JSONObject().put("sourceId", preview.key.sourceId).put("bookId", preview.key.bookId)
            .put("title", preview.title).put("author", preview.author).put("requireReadable", true).put("sortIndex", 0)
        listOf("coverUrl" to preview.coverUrl, "intro" to preview.intro, "kind" to preview.kind, "lastChapter" to preview.lastChapter)
            .forEach { (key, value) -> if (value != null) add.put(key, value) }
        val record = JSONObject().put("journalSchema", 1).put("kind", "onlineAdd")
            .put("id", "android-onlineadd-" + UUID.randomUUID()).put("sourceId", preview.key.sourceId).put("bookId", preview.key.bookId)
            .put("sourceVersion", preview.sourceVersion).put("previewJson", preview.originalResultJson)
            .put("pendingAddJson", add.toString()).put("completed", false)
        originals.persist(record) // Durable original user intent before preparation/network/admission.
        return advance(record)
    }
    /** Explicit continuation only: an existing attempted child without its original terminal is never replayed. */
    fun reconcile(id: String): ReaderOnlineAddResult {
        assertHeld()
        val snapshot = originals.load(id) ?: error("ONLINE_ADD_ORIGINAL_MISSING")
        if (snapshot.candidateRetained) throw ReaderOnlineAddUnconfirmed(id, "candidate-retained")
        val parent = JSONObject(snapshot.recordJson); check(parent.getString("kind") == "onlineAdd")
        if (parent.getBoolean("completed") && snapshot.canonicalCommitted) return result(parent)
        if (!snapshot.canonicalCommitted) originals.persist(parent)
        return advance(parent)
    }
    fun pending(): List<ReaderOnlineAddPending> {
        assertHeld()
        return originals.records().mapNotNull { snapshot ->
            val record = JSONObject(snapshot.recordJson)
            if (record.getString("kind") == "onlineAddStage") check(originals.load(record.getString("parentId")) != null) { "ONLINE_ADD_ORPHAN_STAGE" }
            if (record.getString("kind") != "onlineAdd" || record.getBoolean("completed")) null
            else ReaderOnlineAddPending(record.getString("id"), ReaderOnlineAddPolicy.key(record), snapshot.candidateRetained)
        }
    }
    fun observe(id: String): ReaderOnlineAddObservation {
        assertHeld()
        val snapshot = originals.load(id) ?: error("ONLINE_ADD_ORIGINAL_MISSING")
        val parent = JSONObject(snapshot.recordJson); check(parent.getString("kind") == "onlineAdd" && !parent.getBoolean("completed"))
        val key = ReaderOnlineAddPolicy.key(parent)
        // Only observations. No operationId/expected receipt query exists for begin/add in fixed Core.
        val preparation = transport.request("reading.preparation", params(key).put("action", "status"))
        val shelf = transport.request("bookshelf.get", params(key))
        return ReaderOnlineAddObservation(ReaderOnlineAddPending(id, key, snapshot.candidateRetained),
            preparation.toString(), shelf.toString(), originalConfirmed = false)
    }

    private fun advance(parent: JSONObject): ReaderOnlineAddResult {
        val id = parent.getString("id"); val key = ReaderOnlineAddPolicy.key(parent)
        val preview = JSONObject(parent.getString("previewJson")); val add = JSONObject(parent.getString("pendingAddJson"))
        val source = sources(key.sourceId).single { it.sourceId == key.sourceId && it.enabled }
        check(source.sourceVersion == parent.getString("sourceVersion")) { "ONLINE_ADD_SOURCE_CHANGED" }
        var lastStageId: String? = null
        fun call(label: String, method: String, parameters: JSONObject): Pair<JSONObject, JSONObject> {
            lastStageId = "$id-$label"
            // Existing original reads never dispatch again. Any genuinely new child must respect shared writers.
            val previous = originals.load("$id-$label")
            if (previous == null || !JSONObject(previous.recordJson).getBoolean("attempted")) beforeNewStage()
            return originals.call(parent, label, method, parameters)
        }
        try {
            val existing = call("existing", "bookshelf.get", params(key)).first
            check(existing.has("book")) { "ONLINE_ADD_EXISTING_PROJECTION" }
            if (!existing.isNull("book")) {
                ReaderOnlineAddPolicy.scope(existing.getJSONObject("book"), key)
                parent.put("completion", "alreadyPresent").put("completed", true); originals.persist(parent); originals.release(id)
                return result(parent) // Only a new intent's before-preparation observation, never an old add ACK.
            }
            val prepared = call("begin", "reading.preparation", params(key).put("action", "begin").put("reason", "add").put("pendingAdd", add)).first
            val revision = ReaderOnlineAddPolicy.prepared(prepared, parent, add)
            val variables = preview.optJSONObject("variables") ?: JSONObject()
            val tocParams = params(key).put("preparationRevision", revision).put("variables", variables)
            if (preview.has("tocUrl") && !preview.isNull("tocUrl")) tocParams.put("tocUrl", preview.getString("tocUrl"))
            val toc = call("toc", "book.toc", tocParams).first
            ReaderOnlineAddPolicy.scope(toc, key)
            check(toc.getString("sourceVersion") == source.sourceVersion && toc.optString("catalogDeferredReason").isEmpty()) { "ONLINE_ADD_CATALOG_NOT_ADMITTED" }
            val readable = toc.getJSONArray("readableChapterIndexes"); check(readable.length() > 0) { "ONLINE_ADD_NO_READABLE_CHAPTER" }
            val firstIndex = readable.getInt(0); check(firstIndex >= 0)
            val chapters = toc.getJSONArray("toc")
            val chapter = (0 until chapters.length()).map { chapters.getJSONObject(it) }.single { it.getInt("index") == firstIndex }
            if (parent.optString("viewRuntimeSession") != transport.session) {
                originals.requireNoUnknown(id) // Never bypass an old unknown add/content/preparation by refreshing a derived view.
                val next = parent.optInt("viewGeneration", 0) + 1
                check(next in 1..8) { "ONLINE_ADD_DERIVED_VIEW_GENERATION_BUDGET" }
                parent.put("viewRuntimeSession", transport.session).put("viewGeneration", next); originals.persist(parent)
            }
            val viewGeneration = parent.getInt("viewGeneration")
            var view = call("dir${viewGeneration}p0", "reading.directory.view.open.v2", params(key).put("query", "").put("descending", false)
                .put("collapsedNodeIds", JSONArray()).put("limit", 256)).first
            check(view.getString("status") == "ready"); ReaderOnlineAddPolicy.scope(view, key)
            val viewId = view.getString("viewId"); val catalog = view.getString("catalogRevision"); val structure = view.getString("structureRevision")
            var selected: JSONObject? = null; val visited = mutableSetOf<Int>(); var seen = 0
            while (selected == null) {
                ReaderOnlineAddPolicy.scope(view, key)
                check(view.getString("viewId") == viewId && view.getString("catalogRevision") == catalog && view.getString("structureRevision") == structure)
                val nodes = view.getJSONArray("nodes"); seen += nodes.length(); check(seen <= 50_000)
                selected = (0 until nodes.length()).map { nodes.getJSONObject(it) }.firstOrNull {
                    it.getString("kind") == "target" && it.optInt("chapterIndex", -1) == firstIndex
                }
                if (selected != null) break
                check(view.has("nextOffset") && !view.isNull("nextOffset")) { "ONLINE_ADD_DIRECTORY_TARGET_MISSING" }
                val offset = view.getInt("nextOffset"); check(offset > 0 && visited.add(offset))
                view = call("dir${viewGeneration}p$offset", "reading.directory.view.page.v2", params(key).put("viewId", viewId)
                    .put("catalogRevision", catalog).put("structureRevision", structure).put("offset", offset).put("limit", 256)).first
            }
            val target = call("resolve$viewGeneration", "reading.directory.target.resolve.v2", params(key).put("viewId", viewId)
                .put("catalogRevision", catalog).put("structureRevision", structure).put("nodeId", checkNotNull(selected).getString("nodeId"))).first
            ReaderOnlineAddPolicy.scope(target, key)
            val proof = target.getJSONObject("directoryTargetProof"); ReaderOnlineAddPolicy.scope(proof, key)
            check(target.getString("viewId") == viewId && target.getString("catalogRevision") == catalog && target.getString("structureRevision") == structure &&
                target.getString("nodeId") == checkNotNull(selected).getString("nodeId") && proof.getString("nodeId") == target.getString("nodeId") &&
                target.getInt("chapterIndex") == firstIndex && proof.getInt("chapterIndex") == firstIndex && proof.getString("url") == chapter.getString("url") &&
                proof.getString("catalogRevision") == catalog && proof.getString("structureRevision") == structure) { "ONLINE_ADD_DIRECTORY_PROOF" }
            val contentVariables = JSONObject((toc.optJSONObject("continuationVariables") ?: variables).toString())
            chapter.optJSONObject("variables")?.let { chapterVariables -> chapterVariables.keys().forEach { contentVariables.put(it, chapterVariables.getString(it)) } }
            val content = call("content", "chapter.content", params(key).put("preparationRevision", revision).put("chapterIndex", firstIndex)
                .put("chapterTitle", chapter.getString("title")).put("chapterUrl", chapter.getString("url"))
                .put("variables", contentVariables).put("directoryTargetProof", proof)).first
            ReaderOnlineAddPolicy.scope(content, key)
            check(content.get("content") is String && content.getString("content").any { !it.isWhitespace() } &&
                !content.optString("bodyVersion").isBlank() && !content.optString("processingVersion").isBlank()) { "ONLINE_ADD_CONTENT_NOT_READABLE" }
            val blocks = content.optJSONArray("blocks") ?: JSONArray()
            check((0 until blocks.length()).all { blocks.getJSONObject(it).getString("kind") == "text" }) { "ONLINE_ADD_UNSUPPORTED_BLOCK" }
            val ready = call("prepare", "reading.entry.prepare", params(key).put("chapterIndex", firstIndex).put("neighborOffset", 0)).first
            ReaderOnlineAddPolicy.scope(ready, key)
            check(ready.getString("kind") == "ready" && ready.getInt("chapterIndex") == firstIndex) { "ONLINE_ADD_CANONICAL_NOT_READY" }
            val finalAdd = JSONObject(parent.getString("pendingAddJson")).put("preparationRevision", revision)
            val originalAdd = call("add", "bookshelf.add", finalAdd).second
            val confirmed = ReaderOnlineAddPolicy.confirmedAdd(parent, originalAdd, revision)
            parent.put("completion", "added").put("addStageId", originalAdd.getString("id")).put("completed", true)
            originals.persist(parent); originals.release(id)
            return confirmed // Original durable ACK; no fresh shelf list is used to manufacture success.
        } catch (error: Exception) {
            if (error.coreProtocolPending() != null) throw error
            if (error is CoreCommandException) {
                parent.put("completion", "rejected").put("rejectionStageId", checkNotNull(lastStageId)).put("rejectionJson", error.error.toString()).put("completed", true)
                originals.persist(parent); originals.release(id)
                return result(parent) // No automatic Core cancel/rollback; any preparation state remains observable.
            }
            throw ReaderOnlineAddUnconfirmed(id, "admission", error)
        }
    }
    private fun result(parent: JSONObject): ReaderOnlineAddResult = when (parent.getString("completion")) {
        "alreadyPresent" -> {
            val prior = originals.load(parent.getString("id") + "-existing") ?: error("ONLINE_ADD_PRIOR_PROJECTION_MISSING")
            check(!prior.candidateRetained && prior.canonicalCommitted)
            val projection = CoreTerminal.result("bookshelf.get", ReaderOnlineAddPolicy.original(JSONObject(prior.recordJson)))
            ReaderOnlineAddPolicy.scope(projection.getJSONObject("book"), ReaderOnlineAddPolicy.key(parent))
            ReaderOnlineAddResult(parent.getString("id"), ReaderOnlineAddPolicy.key(parent), ReaderOnlineAddConfirmation.ALREADY_PRESENT_BEFORE_NEW_INTENT, true)
        }
        "rejected" -> {
            val stage = originals.load(parent.getString("rejectionStageId")) ?: error("ONLINE_ADD_REJECTION_ORIGINAL_MISSING")
            check(!stage.candidateRetained && stage.canonicalCommitted)
            val record = JSONObject(stage.recordJson); val event = ReaderOnlineAddPolicy.original(record)
            check(record.getString("parentId") == parent.getString("id") && event.getString("type") == "error" &&
                event.getJSONObject("error").toString() == parent.getString("rejectionJson")) { "ONLINE_ADD_REJECTION_ORIGINAL_MISMATCH" }
            ReaderOnlineAddResult(parent.getString("id"), ReaderOnlineAddPolicy.key(parent), ReaderOnlineAddConfirmation.REJECTED, true, rejectionJson = parent.getString("rejectionJson"))
        }
        "added" -> {
            val snapshot = originals.load(parent.getString("addStageId")) ?: error("ONLINE_ADD_ACK_MISSING")
            check(!snapshot.candidateRetained && snapshot.canonicalCommitted)
            val begin = originals.load(parent.getString("id") + "-begin") ?: error("ONLINE_ADD_PREPARATION_ACK_MISSING")
            check(!begin.candidateRetained && begin.canonicalCommitted)
            val prepared = CoreTerminal.result("reading.preparation", ReaderOnlineAddPolicy.original(JSONObject(begin.recordJson)))
            val revision = ReaderOnlineAddPolicy.prepared(prepared, parent, JSONObject(parent.getString("pendingAddJson")))
            ReaderOnlineAddPolicy.confirmedAdd(parent, JSONObject(snapshot.recordJson), revision)
        }
        else -> error("ONLINE_ADD_COMPLETION_KIND")
    }
    private fun params(key: ShelfBookKey) = JSONObject().put("sourceId", key.sourceId).put("bookId", key.bookId)
}
