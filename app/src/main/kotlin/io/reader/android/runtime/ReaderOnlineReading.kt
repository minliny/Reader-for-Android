package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/** Actual Core v2 projection; these objects grant no local Original or ebook permission. */
class ReaderOnlineDirectory internal constructor(
    val id: String, val key: ShelfBookKey, val sourceVersion: String,
    val viewId: String, val catalogRevision: String, val structureRevision: String,
    internal val owner: Any, internal val lease: ReaderOperationOwner.ClientLease, internal val runtime: String,
)
class ReaderOnlineDirectoryNode internal constructor(
    val nodeId: String, val title: String, val kind: String, val chapterIndex: Int?,
    internal val directory: ReaderOnlineDirectory,
)
data class ReaderOnlineDirectoryPage(val directory: ReaderOnlineDirectory, val nodes: List<ReaderOnlineDirectoryNode>, val nextOffset: Int?)
class ReaderOnlineDirectoryTarget internal constructor(
    val directory: ReaderOnlineDirectory, val chapterIndex: Int, val scalarOffset: Int,
    internal val proofJson: String, internal val positionScopeJson: String?,
)
data class ReaderOnlineChapter internal constructor(
    val chapter: ReaderChapter, val restoreScalarOffset: Int, val sourceVersion: String,
    val progressRevision: String, val baseUrl: String?, val resumeOnly: Boolean,
)

class ReaderOnlineEntryUnavailable(val key: ShelfBookKey, val kind: String, val reason: String) :
    Exception("ONLINE_ENTRY_UNAVAILABLE:$kind/$reason")

/** One App-owned service; acquisition uses the SAME catalog original-call owner and HTTP handler. */
internal class ReaderOnlineReading(
    private val transport: CoreTransport, private val catalog: ReaderOnlineCatalog,
    private val sources: (String) -> List<ReaderSourceEntry>, private val assertHeld: () -> Unit,
    private val beforeRead: (ShelfBookKey) -> Unit,
) {
    private val owner = Any()
    private class View(val directory: ReaderOnlineDirectory) {
        val issued = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineDirectoryNode, Boolean>())
        val targets = Collections.newSetFromMap(IdentityHashMap<ReaderOnlineDirectoryTarget, Boolean>())
        var next: Int? = null
    }
    private val views = linkedMapOf<String, View>()
    private fun request(method: String, key: ShelfBookKey, payload: JSONObject = params(key)) = catalog.readStored(method, key, payload)
    private fun source(key: ShelfBookKey): ReaderSourceEntry {
        assertHeld(); ReaderOnlineReadingPolicy.remote(key)
        return sources(key.sourceId).single { it.sourceId == key.sourceId && it.enabled }.also {
            check(it.sourceVersion.isNotBlank()) { "ONLINE_READING_SOURCE_VERSION_REQUIRED" }
        }
    }
    private fun installed(key: ShelfBookKey): String {
        beforeRead(key) // A fresh shelf projection never bypasses an unknown source/add/removal original.
        val version = source(key).sourceVersion
        val book = request("bookshelf.get", key).getJSONObject("book")
        ReaderOnlineReadingPolicy.scope(book, key)
        check(!book.has("contentKind") || book.isNull("contentKind") || book.getString("contentKind") == "text") { "ONLINE_TEXT_SHELF_REQUIRED" }
        val cache = request("cache.book.status", key); ReaderOnlineReadingPolicy.scope(cache, key)
        check(cache.getBoolean("tocAvailable") && cache.getString("sourceVersion") == version) { "ONLINE_READING_INSTALLED_CATALOG_REQUIRED" }
        return version
    }
    fun directory(lease: ReaderOperationOwner.ClientLease, key: ShelfBookKey): ReaderOnlineDirectoryPage {
        val version = installed(key)
        val value = request("reading.directory.view.open.v2", key, params(key).put("query", "").put("descending", false)
            .put("collapsedNodeIds", JSONArray()).put("limit", 256))
        check(value.getString("status") == "ready"); ReaderOnlineReadingPolicy.scope(value, key)
        views.values.filter { it.directory.lease == lease && it.directory.key == key }.map { it.directory }.forEach(::close)
        check(views.size < 4) { "ONLINE_DIRECTORY_CAPACITY" }
        val directory = ReaderOnlineDirectory(UUID.randomUUID().toString(), key, version, value.getString("viewId"),
            value.getString("catalogRevision"), value.getString("structureRevision"), owner, lease, transport.session)
        views[directory.id] = View(directory)
        return page(value, directory)
    }
    fun next(lease: ReaderOperationOwner.ClientLease, directory: ReaderOnlineDirectory, offset: Int): ReaderOnlineDirectoryPage {
        val view = current(lease, directory)
        check(offset > 0 && view.next == offset) { "ONLINE_DIRECTORY_NOT_NEXT" }
        return page(request("reading.directory.view.page.v2", directory.key, viewParams(directory).put("offset", offset).put("limit", 256)), directory)
    }
    private fun page(value: JSONObject, directory: ReaderOnlineDirectory): ReaderOnlineDirectoryPage {
        identity(value, directory)
        val view = checkNotNull(views[directory.id]); val rows = value.getJSONArray("nodes")
        check(view.issued.size <= 50_000 - rows.length()) { "ONLINE_DIRECTORY_NODE_BUDGET" }
        val nodes = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ReaderOnlineDirectoryNode(row.getString("nodeId"), row.getString("title"), row.getString("kind"),
                if (!row.has("chapterIndex") || row.isNull("chapterIndex")) null else row.getInt("chapterIndex").also { check(it >= 0) }, directory)
        }
        view.issued.addAll(nodes)
        view.next = if (!value.has("nextOffset") || value.isNull("nextOffset")) null else value.getInt("nextOffset").also { check(it > 0) }
        return ReaderOnlineDirectoryPage(directory, nodes, view.next)
    }
    fun resolve(lease: ReaderOperationOwner.ClientLease, node: ReaderOnlineDirectoryNode): ReaderOnlineDirectoryTarget {
        val directory = node.directory; val view = current(lease, directory)
        check(node in view.issued && node.kind == "target" && node.chapterIndex != null && view.targets.size < 50_000) { "ONLINE_DIRECTORY_NODE_NOT_ISSUED_TARGET" }
        val value = request("reading.directory.target.resolve.v2", directory.key, viewParams(directory).put("nodeId", node.nodeId))
        identity(value, directory); check(value.getString("nodeId") == node.nodeId && value.getInt("chapterIndex") == node.chapterIndex)
        val proof = value.getJSONObject("directoryTargetProof"); ReaderOnlineReadingPolicy.scope(proof, directory.key)
        check(proof.getString("catalogRevision") == directory.catalogRevision && proof.getString("structureRevision") == directory.structureRevision &&
            proof.getString("nodeId") == node.nodeId && proof.getInt("chapterIndex") == node.chapterIndex && proof.getString("url").isNotBlank()) { "ONLINE_DIRECTORY_PROOF" }
        val exact = value.getString("kind") == "exact"
        check(exact || value.getString("kind") == "chapterStart") { "ONLINE_DIRECTORY_TARGET_KIND" }
        val offset = if (exact) value.getInt("chapterOffsetScalar").also { check(it >= 0) } else 0
        return ReaderOnlineDirectoryTarget(directory, checkNotNull(node.chapterIndex), offset, proof.toString(),
            if (exact) value.getJSONObject("positionScope").toString() else null).also { view.targets.add(it) }
    }
    fun open(lease: ReaderOperationOwner.ClientLease, key: ShelfBookKey, target: ReaderOnlineDirectoryTarget? = null): ReaderOnlineChapter {
        val version = installed(key)
        if (target != null) { val view = current(lease, target.directory); check(target in view.targets && target.directory.key == key && target.directory.sourceVersion == version) }
        val entryParams = params(key)
        target?.let { entryParams.put("chapterIndex", it.chapterIndex).put("directoryTargetProof", JSONObject(it.proofJson)) }
        val prepare = params(key).put("neighborOffset", 0)
        target?.let { prepare.put("chapterIndex", it.chapterIndex) }
        var entry = request("reading.entry.snapshot", key, entryParams)
        if (entry.optString("kind") == "missing" && entry.optString("reason") == "contentMissing" && target != null) {
            // Only an actual explicit issued directory target permits acquisition. No implicit fallback/refetch/upgrade.
            val status = request("cache.book.status", key); ReaderOnlineReadingPolicy.scope(status, key)
            check(status.getString("sourceVersion") == version)
            val rows = status.getJSONArray("chapters")
            val row = (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getInt("chapterIndex") == target.chapterIndex }
            check(row.optBoolean("navigable", true) && row.getString("url") == JSONObject(target.proofJson).getString("url"))
            val variables = JSONObject((status.optJSONObject("continuationVariables") ?: JSONObject()).toString())
            row.optJSONObject("variables")?.let { values -> values.keys().forEach { variables.put(it, values.getString(it)) } }
            val acquired = request("chapter.content", key, params(key).put("chapterIndex", target.chapterIndex).put("chapterTitle", row.getString("title"))
                .put("chapterUrl", row.getString("url")).put("variables", variables).put("directoryTargetProof", JSONObject(target.proofJson)))
            ReaderOnlineReadingPolicy.scope(acquired, key)
            check(acquired.get("content") is String) { "ONLINE_STRUCTURED_CONTENT_NOT_ADMITTED" }
            request("reading.entry.prepare", key, prepare)
            entry = request("reading.entry.snapshot", key, entryParams)
        }
        ReaderOnlineReadingPolicy.scope(entry, key)
        if (entry.optString("kind") != "ready") throw ReaderOnlineEntryUnavailable(key, entry.optString("kind"), entry.optString("reason"))
        if (entry.optBoolean("contentRefreshRequired") || entry.optBoolean("sourceCorrectionRequired"))
            throw ReaderOnlineEntryUnavailable(key, "ready", "explicitContentUpgradeRequired")
        val progress = request("reading.progress.get", key)
        check(source(key).sourceVersion == version) { "ONLINE_READING_SOURCE_CHANGED" }
        return ReaderOnlineReadingPolicy.chapter(key, version, entry, progress, target)
    }
    private fun current(lease: ReaderOperationOwner.ClientLease, directory: ReaderOnlineDirectory): View {
        assertHeld()
        check(directory.owner === owner && directory.lease == lease && directory.runtime == transport.session &&
            source(directory.key).sourceVersion == directory.sourceVersion) { "ONLINE_DIRECTORY_RETIRED" }
        return checkNotNull(views[directory.id]).also { check(it.directory === directory) }
    }
    private fun identity(value: JSONObject, directory: ReaderOnlineDirectory) {
        ReaderOnlineReadingPolicy.scope(value, directory.key)
        check(value.getString("viewId") == directory.viewId && value.getString("catalogRevision") == directory.catalogRevision &&
            value.getString("structureRevision") == directory.structureRevision) { "ONLINE_DIRECTORY_CHANGED" }
    }
    fun close(directory: ReaderOnlineDirectory) {
        assertHeld(); check(directory.owner === owner) { "FOREIGN_ONLINE_DIRECTORY" }
        views[directory.id]?.let { check(it.directory === directory); it.issued.clear(); it.targets.clear(); views.remove(directory.id) }
    }
    private fun viewParams(directory: ReaderOnlineDirectory) = params(directory.key).put("viewId", directory.viewId)
        .put("catalogRevision", directory.catalogRevision).put("structureRevision", directory.structureRevision)
    private fun params(key: ShelfBookKey) = JSONObject().put("sourceId", key.sourceId).put("bookId", key.bookId)
}
