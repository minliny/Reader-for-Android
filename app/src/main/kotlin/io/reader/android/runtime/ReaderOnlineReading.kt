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
    private class Entry(val lease: ReaderOperationOwner.ClientLease)
    private class Window(val value: ReaderOnlineReadingWindow) {
        val states = IdentityHashMap<ReaderOnlineNeighbor, ReaderOnlineNeighborState>()
    }
    private val entries = IdentityHashMap<ReaderOnlineChapter, Entry>()
    private val windows = IdentityHashMap<ReaderOnlineReadingWindow, Window>()
    private fun request(method: String, key: ShelfBookKey, payload: JSONObject = params(key)) = catalog.readStored(method, key, payload)
    /** App-owned retained acquisition outcome must block saves even after its UI waiter was cancelled. */
    fun requireNoUnknown(key: ShelfBookKey) {
        assertHeld(); ReaderOnlineReadingPolicy.remote(key); beforeRead(key)
        catalog.pending().firstOrNull()?.let { throw ReaderOnlineOriginalPending(it) }
    }
    private fun source(key: ShelfBookKey): ReaderSourceEntry? {
        assertHeld(); ReaderOnlineReadingPolicy.remote(key)
        val values = sources(key.sourceId)
        check(values.size <= 1 && values.all { it.sourceId == key.sourceId }) { "ONLINE_SOURCE_IDENTITY" }
        return values.singleOrNull() // Disabled/missing source revokes fetching, not existing cached bodies.
    }
    private fun canAcquire(key: ShelfBookKey, version: String): Boolean = source(key)?.let {
        it.enabled && version.isNotBlank() && it.sourceVersion == version
    } == true
    private fun cachedVersion(key: ShelfBookKey, requireCatalog: Boolean = true): String {
        val cache = request("cache.book.status", key, params(key).put("includeGlobalStats", false).put("includeChapterStates", false))
        ReaderOnlineReadingPolicy.scope(cache, key)
        check(!requireCatalog || cache.getBoolean("tocAvailable")) { "ONLINE_READING_INSTALLED_CATALOG_REQUIRED" }
        return cache.optString("sourceVersion", "") // Legacy absence is not invented source/network authority.
    }
    private fun installed(key: ShelfBookKey, requireCatalog: Boolean = true): String {
        requireNoUnknown(key) // A fresh shelf projection never bypasses an unknown original.
        val book = request("bookshelf.get", key).getJSONObject("book")
        ReaderOnlineReadingPolicy.scope(book, key)
        check(!book.has("contentKind") || book.isNull("contentKind") || book.getString("contentKind") == "text") { "ONLINE_TEXT_SHELF_REQUIRED" }
        return cachedVersion(key, requireCatalog)
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
        val version = installed(key, requireCatalog = target != null)
        if (target != null) { val view = current(lease, target.directory); check(target in view.targets && target.directory.key == key && target.directory.sourceVersion == version) }
        val entryParams = params(key)
        target?.let { entryParams.put("chapterIndex", it.chapterIndex).put("directoryTargetProof", JSONObject(it.proofJson)) }
        val prepare = params(key).put("neighborOffset", 0)
        target?.let { prepare.put("chapterIndex", it.chapterIndex) }
        var entry = request("reading.entry.snapshot", key, entryParams)
        if (entry.optString("kind") == "missing" && entry.optString("reason") == "contentMissing" && target != null) {
            // Only an actual explicit issued directory target permits acquisition. No implicit fallback/refetch/upgrade.
            if (!canAcquire(key, version)) throw ReaderOnlineEntryUnavailable(key, "missing", "sourceUnavailableForAcquisition")
            val status = request("cache.book.status", key); ReaderOnlineReadingPolicy.scope(status, key)
            check(status.optString("sourceVersion", "") == version)
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
        // Keep the progress read as the final entry observation. The cache-version
        // guard still runs in the same acquisition turn, before progress is
        // admitted, so existing reading receipts retain their stable boundary.
        check(cachedVersion(key, requireCatalog = target != null) == version) { "ONLINE_READING_CACHED_CATALOG_CHANGED" }
        val progress = request("reading.progress.get", key)
        return retainEntry(lease, ReaderOnlineReadingPolicy.chapter(key, version, entry, progress, target))
    }
    private fun retainEntry(lease: ReaderOperationOwner.ClientLease, opened: ReaderOnlineChapter): ReaderOnlineChapter {
        entries.keys.filter { entries[it]?.lease == lease }.toList().forEach { retire(lease, it) }
        check(entries.size < 4) { "ONLINE_ENTRY_CAPACITY" }
        entries[opened] = Entry(lease)
        return opened
    }
    fun retire(lease: ReaderOperationOwner.ClientLease, opened: ReaderOnlineChapter) {
        assertHeld()
        val entry = entries[opened] ?: return
        check(entry.lease == lease) { "FOREIGN_ONLINE_ENTRY" }
        windows.keys.filter { it.opened === opened }.toList().forEach { it.retire(); windows.remove(it) }
        entries.remove(opened)
    }
    private fun entry(lease: ReaderOperationOwner.ClientLease, opened: ReaderOnlineChapter): Entry {
        assertHeld(); requireNoUnknown(opened.chapter.key); val value = checkNotNull(entries[opened]) { "ONLINE_ENTRY_RETIRED" }
        check(value.lease == lease) { "ONLINE_ENTRY_CHANGED" }
        return value
    }
    /** Called after actual UI Native Ready; begin/read is the existing Core network permission. */
    fun window(lease: ReaderOperationOwner.ClientLease, opened: ReaderOnlineChapter, schedule: ReaderOnlineWindowSchedule): ReaderOnlineReadingWindowSnapshot {
        schedule.requireOpen(); entry(lease, opened); val key = opened.chapter.key
        check(!opened.resumeOnly) { "ONLINE_RESUME_ONLY_HAS_NO_READ_WINDOW" }
        val snapshot = request("reading.entry.snapshot", key, params(key).put("chapterIndex", opened.chapter.index)
            .put("windowScalarLimit", 1).put("positionContext", JSONObject().put("bodyVersion", opened.chapter.bodyVersion)
                .put("processingVersion", opened.chapter.processingVersion).put("anchors", JSONArray())))
        schedule.requireOpen(); ReaderOnlineReadingPolicy.scope(snapshot, key)
        check(snapshot.getString("kind") == "ready" && !snapshot.optBoolean("resumeOnly") && !snapshot.optBoolean("contentRefreshRequired") &&
            !snapshot.optBoolean("sourceCorrectionRequired")) { "ONLINE_WINDOW_CURRENT_NOT_READY" }
        val scope = snapshot.getJSONObject("positionScope"); ReaderOnlineReadingPolicy.scope(scope, key)
        check(scope.getInt("chapterIndex") == opened.chapter.index && scope.getString("bodyVersion") == opened.chapter.bodyVersion &&
            scope.getString("processingVersion") == opened.chapter.processingVersion) { "ONLINE_WINDOW_CURRENT_SCOPE_CHANGED" }
        val navigation = snapshot.getJSONObject("navigation")
        check(navigation.getJSONObject("current").getInt("index") == opened.chapter.index)
        // The current entry was restored cache-only. This independent Ready-owned rolling
        // window now obtains Core's real read intent, rather than inheriting the old Add intent.
        schedule.requireOpen()
        val revision = if (canAcquire(key, opened.sourceVersion)) beginRead(key, opened.sourceVersion) { schedule.requireOpen() } else null
        schedule.requireOpen()
        val id = UUID.randomUUID().toString(); val neighbors = mutableListOf<ReaderOnlineNeighbor>()
        // Core's before is catalog order, after is forward order. Project only its issued readable rows;
        // no chapterIndex +/- arithmetic, synthetic URL, fresh catalog or Host parser.
        for (side in listOf("before", "after")) {
            val rows = navigation.getJSONArray(side)
            val indexes = if (side == "before") (rows.length() - 1 downTo 0).toList() else (0 until rows.length()).toList()
            indexes.take(2).forEachIndexed { rank, index ->
                val row = rows.getJSONObject(index); check(row.getBoolean("navigable")) { "ONLINE_WINDOW_UNREADABLE_NEIGHBOR" }
                neighbors.add(ReaderOnlineNeighbor(id, (rank + 1) * if (side == "before") -1 else 1,
                    row.getInt("index").also { check(it >= 0) }, row.getString("title"), owner))
            }
        }
        schedule.requireOpen(); val status = cacheStatus(key, opened.sourceVersion); schedule.requireOpen()
        val initial = neighbors.map { neighbor ->
            val row = cacheRow(status, neighbor.chapterIndex)
            ReaderOnlineNeighborState(neighbor, "unprepared", "notPrepared", row.getString("state"), row.getLong("cachedBytes"), null, null)
        }
        entry(lease, opened)
        windows.keys.filter { it.opened === opened }.toList().forEach { it.retire(); windows.remove(it) }
        val value = ReaderOnlineReadingWindow(id, key, opened.chapter.sessionToken, opened.chapter.index, opened.chapter.bodyVersion,
            opened.chapter.processingVersion, opened.sourceVersion, navigation.getString("revision"), revision,
            neighbors, owner, lease, transport.session, opened, schedule)
        val window = Window(value); initial.forEach { window.states[it.neighbor] = it }; windows[value] = window
        return ReaderOnlineReadingWindowSnapshot(value, initial)
    }
    private fun beginRead(key: ShelfBookKey, version: String, current: () -> Unit): Long? {
        current()
        if (!canAcquire(key, version)) return null
        current()
        val intentData = request("reading.preparation", key, params(key).put("action", "begin").put("reason", "read"))
        current()
        val intents = intentData.getJSONArray("intents"); check(intents.length() == 1)
        val intent = intents.getJSONObject(0); ReaderOnlineReadingPolicy.scope(intent, key)
        if (intent.getString("sourceVersion") != version || intent.getString("reason") != "read" ||
            intent.getString("state") != "active" || (intent.has("pendingAdd") && !intent.isNull("pendingAdd"))) return null
        return intent.getLong("revision").also { check(it > 0) }
    }
    private fun currentWindow(lease: ReaderOperationOwner.ClientLease, value: ReaderOnlineReadingWindow, neighbor: ReaderOnlineNeighbor? = null): Window {
        entry(lease, value.opened)
        check(value.owner === owner && value.lease == lease && value.runtimeSession == transport.session && !value.isRetired &&
            value.key == value.opened.chapter.key && value.sessionToken == value.opened.chapter.sessionToken &&
            value.bodyVersion == value.opened.chapter.bodyVersion && value.processingVersion == value.opened.chapter.processingVersion) { "ONLINE_WINDOW_RETIRED" }
        val window = checkNotNull(windows[value]); check(window.value === value)
        if (neighbor != null) check(neighbor.owner === owner && neighbor.windowId == value.id && value.neighbors.any { it === neighbor }) { "ONLINE_NEIGHBOR_NOT_ISSUED" }
        return window
    }
    private fun cacheStatus(key: ShelfBookKey, version: String): JSONObject = request("cache.book.status", key,
        params(key).put("includeGlobalStats", false).put("includeChapterStates", true)).also {
        ReaderOnlineReadingPolicy.scope(it, key); check(it.optString("sourceVersion", "") == version)
    }
    private fun neighborState(key: ShelfBookKey, version: String, catalogRevision: String, neighbor: ReaderOnlineNeighbor,
        prepared: JSONObject, status: JSONObject): ReaderOnlineNeighborState {
        ReaderOnlineReadingPolicy.scope(prepared, key); check(prepared.getInt("chapterIndex") == neighbor.chapterIndex)
        val cache = cacheRow(status, neighbor.chapterIndex)
        var body: String? = null; var processing: String? = null
        if (prepared.getString("kind") == "ready") {
            val bounded = request("reading.entry.snapshot", key, params(key).put("chapterIndex", neighbor.chapterIndex).put("windowScalarLimit", 1))
            ReaderOnlineReadingPolicy.scope(bounded, key)
            check(bounded.getString("kind") == "ready" && !bounded.optBoolean("resumeOnly") && !bounded.optBoolean("contentRefreshRequired") &&
                !bounded.optBoolean("sourceCorrectionRequired") && bounded.getInt("chapterIndex") == neighbor.chapterIndex &&
                bounded.getJSONObject("navigation").getString("revision") == catalogRevision) { "ONLINE_NEIGHBOR_SCOPE_CHANGED" }
            val position = bounded.getJSONObject("positionScope"); ReaderOnlineReadingPolicy.scope(position, key)
            check(position.getInt("chapterIndex") == neighbor.chapterIndex); body = position.getString("bodyVersion"); processing = position.getString("processingVersion")
            check(body.isNotBlank() && processing.isNotBlank() && status.optString("sourceVersion", "") == version)
        }
        return ReaderOnlineNeighborState(neighbor, prepared.getString("kind"), prepared.optString("reason"), cache.getString("state"),
            cache.getLong("cachedBytes"), body, processing)
    }
    private fun cacheRow(status: JSONObject, index: Int): JSONObject {
        val chapters = status.getJSONArray("chapters")
        return (0 until chapters.length()).map { chapters.getJSONObject(it) }.single { it.getInt("chapterIndex") == index }.also {
            check(it.getBoolean("navigable") && it.getLong("cachedBytes") >= 0) { "ONLINE_NEIGHBOR_NOT_READABLE" }
        }
    }
    private fun checkWindowCatalog(value: ReaderOnlineReadingWindow) {
        val snapshot = request("reading.entry.snapshot", value.key, params(value.key).put("chapterIndex", value.chapterIndex).put("windowScalarLimit", 1))
        ReaderOnlineReadingPolicy.scope(snapshot, value.key)
        check(snapshot.getString("kind") == "ready" && !snapshot.optBoolean("resumeOnly") && !snapshot.optBoolean("contentRefreshRequired") &&
            !snapshot.optBoolean("sourceCorrectionRequired") && snapshot.getJSONObject("navigation").getString("revision") == value.catalogRevision) { "ONLINE_WINDOW_CATALOG_CHANGED" }
        val scope = snapshot.getJSONObject("positionScope"); ReaderOnlineReadingPolicy.scope(scope, value.key)
        check(scope.getInt("chapterIndex") == value.chapterIndex && scope.getString("bodyVersion") == value.bodyVersion &&
            scope.getString("processingVersion") == value.processingVersion) { "ONLINE_WINDOW_BODY_CHANGED" }
    }
    /** One Core-owned optional-cache target per held turn; never a permanent offline-download intent. */
    fun warm(lease: ReaderOperationOwner.ClientLease, value: ReaderOnlineReadingWindow, neighbor: ReaderOnlineNeighbor, explicitMove: Boolean = false): ReaderOnlineNeighborState {
        val window = currentWindow(lease, value, neighbor)
        checkWindowCatalog(value); currentWindow(lease, value, neighbor)
        var prepared = request("reading.entry.prepare", value.key, params(value.key).put("chapterIndex", value.chapterIndex).put("neighborOffset", neighbor.offset))
        currentWindow(lease, value, neighbor)
        check(prepared.getInt("chapterIndex") == neighbor.chapterIndex) { "ONLINE_NEIGHBOR_CHANGED" }
        if (prepared.optString("kind") == "missing" && prepared.optString("reason") == "contentMissing" &&
            (explicitMove || value.preparationRevision != null)) {
            currentWindow(lease, value, neighbor)
            val revision = value.preparationRevision ?: beginRead(value.key, value.sourceVersion) { currentWindow(lease, value, neighbor) }
                ?: throw ReaderOnlineEntryUnavailable(value.key, "missing", "preparationPermissionUnavailable")
            currentWindow(lease, value, neighbor)
            val status = cacheStatus(value.key, value.sourceVersion); currentWindow(lease, value, neighbor)
            val row = cacheRow(status, neighbor.chapterIndex)
            if (!canAcquire(value.key, value.sourceVersion)) throw ReaderOnlineEntryUnavailable(value.key, "missing", "sourceUnavailableForAcquisition")
            val variables = JSONObject((status.optJSONObject("continuationVariables") ?: JSONObject()).toString())
            row.optJSONObject("variables")?.let { values -> values.keys().forEach { variables.put(it, values.getString(it)) } }
            currentWindow(lease, value, neighbor)
            val fetched = request("chapter.content", value.key, params(value.key).put("chapterIndex", neighbor.chapterIndex)
                .put("chapterTitle", row.getString("title")).put("chapterUrl", row.getString("url")).put("variables", variables)
                .put("preparationRevision", revision))
            ReaderOnlineReadingPolicy.scope(fetched, value.key)
            check(fetched.get("content") is String && fetched.getString("bodyVersion").isNotBlank() && fetched.getString("processingVersion").isNotBlank()) { "ONLINE_OPTIONAL_TEXT_CACHE_RESULT" }
            currentWindow(lease, value, neighbor)
            prepared = request("reading.entry.prepare", value.key, params(value.key).put("chapterIndex", value.chapterIndex).put("neighborOffset", neighbor.offset))
        }
        currentWindow(lease, value, neighbor)
        val status = cacheStatus(value.key, value.sourceVersion); currentWindow(lease, value, neighbor)
        val next = neighborState(value.key, value.sourceVersion, value.catalogRevision, neighbor, prepared, status)
        val old = window.states[neighbor]
        if (old?.ready == true && next.ready) check(old.bodyVersion == next.bodyVersion && old.processingVersion == next.processingVersion) { "ONLINE_NEIGHBOR_BODY_CHANGED_RELOAD_WINDOW" }
        currentWindow(lease, value, neighbor); window.states[neighbor] = next; return next
    }
    /** Explicit next/previous after the caller's old-position original save ACK. No inherited offset. */
    fun move(lease: ReaderOperationOwner.ClientLease, value: ReaderOnlineReadingWindow, neighbor: ReaderOnlineNeighbor): ReaderOnlineChapter {
        currentWindow(lease, value, neighbor)
        val state = warm(lease, value, neighbor, explicitMove = true)
        if (!state.ready) throw ReaderOnlineEntryUnavailable(value.key, state.kind, state.reason)
        val snapshot = request("reading.entry.snapshot", value.key, params(value.key).put("chapterIndex", neighbor.chapterIndex))
        ReaderOnlineReadingPolicy.scope(snapshot, value.key)
        check(snapshot.getString("kind") == "ready" && !snapshot.optBoolean("resumeOnly") &&
            !snapshot.optBoolean("contentRefreshRequired") && !snapshot.optBoolean("sourceCorrectionRequired") &&
            snapshot.getInt("chapterIndex") == neighbor.chapterIndex) { "ONLINE_MOVE_ENTRY_SCOPE" }
        check(snapshot.getJSONObject("navigation").getString("revision") == value.catalogRevision)
        val position = snapshot.getJSONObject("positionScope")
        ReaderOnlineReadingPolicy.scope(position, value.key)
        check(position.getInt("chapterIndex") == neighbor.chapterIndex &&
            position.getString("bodyVersion") == state.bodyVersion && position.getString("processingVersion") == state.processingVersion) {
            "ONLINE_MOVE_POSITION_SCOPE"
        }
        currentWindow(lease, value, neighbor)
        val progress = request("reading.progress.get", value.key); currentWindow(lease, value, neighbor)
        return retainEntry(lease, ReaderOnlineReadingPolicy.neighborChapter(value.key, value.sourceVersion, snapshot, progress, neighbor.chapterIndex))
    }
    private fun current(lease: ReaderOperationOwner.ClientLease, directory: ReaderOnlineDirectory): View {
        assertHeld()
        check(directory.owner === owner && directory.lease == lease && directory.runtime == transport.session &&
            cachedVersion(directory.key) == directory.sourceVersion) { "ONLINE_DIRECTORY_RETIRED" }
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
