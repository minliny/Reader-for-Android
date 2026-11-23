package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException

/** Reader-owned native v2 view. Closing a search does not revoke this separate owner. */
class ReaderMangaDirectory internal constructor(
    val id: String, val chapter: ReaderMangaChapterRef, val sourceVersion: String,
    val viewId: String, val catalogRevision: String, val structureRevision: String,
    internal val owner: Any, internal val lease: ReaderOperationOwner.ClientLease, internal val runtime: String,
)

class ReaderMangaDirectoryNode internal constructor(
    val directory: ReaderMangaDirectory, val nodeId: String, val title: String, val kind: String, val chapterIndex: Int?,
    val depth: Int = 0, val parentId: String? = null, val hasChildren: Boolean = false, val expanded: Boolean = false,
)

data class ReaderMangaDirectoryPage(val directory: ReaderMangaDirectory, val nodes: List<ReaderMangaDirectoryNode>, val nextOffset: Int?)

/** This exact issued view lost node/target authority. The original cause retains protocol recovery identity. */
class ReaderMangaDirectoryUnavailable(val directory: ReaderMangaDirectory, cause: Throwable) :
    Exception("MANGA_DIRECTORY_VIEW_UNAVAILABLE", cause)

/** Native proof plus its actual issuing view; constructing a lookalike never grants Library admission. */
class ReaderMangaDirectoryTarget internal constructor(
    val directory: ReaderMangaDirectory, val nodeId: String, val chapterIndex: Int, val url: String,
    val sourceRuleVersion: String, internal val proofJson: String,
)

/** Finite DTO adaptation over the catalog's existing original-call owner. No shelf/text entry or Native close RPC. */
internal class ReaderMangaDirectoryOwner(
    private val transport: CoreTransport,
    private val catalog: ReaderOnlineCatalog,
    private val sources: (String) -> List<ReaderSourceEntry>,
    private val assertHeld: () -> Unit,
    private val requireAdmission: () -> Unit,
    private val pageLimit: Int = 256,
) {
    private val owner = Any()
    private val lock = Any()
    private var retired = false
    private class View(val directory: ReaderMangaDirectory) {
        val issued = Collections.newSetFromMap(IdentityHashMap<ReaderMangaDirectoryNode, Boolean>())
        val targets = Collections.newSetFromMap(IdentityHashMap<ReaderMangaDirectoryTarget, Boolean>())
        val ids = mutableSetOf<String>()
        var next: Int? = null
        var total: Int? = null
    }
    private val views = linkedMapOf<String, View>()
    /** Failed views lose node/target authority but keep this bounded opaque scope for explicit reopen. */
    private val issuedDirectories = Collections.newSetFromMap(IdentityHashMap<ReaderMangaDirectory, Boolean>())

    init { require(pageLimit in 1..256) }

    fun open(lease: ReaderOperationOwner.ClientLease, request: ReaderMangaChapterRequest, sourceVersion: String): ReaderMangaDirectoryPage {
        assertHeld(); requireAdmission(); source(request.chapter, sourceVersion)
        synchronized(lock) {
            check(!retired) { "MANGA_DIRECTORY_OWNER_RETIRED" }
            check(issuedDirectories.size < 4 || issuedDirectories.any { it.lease == lease && it.chapter == request.chapter }) { "MANGA_DIRECTORY_CAPACITY" }
        }
        val value = catalog.readMangaDirectory("reading.directory.view.open.v2", request.chapter,
            params(request.chapter).put("query", "").put("descending", false).put("collapsedNodeIds", JSONArray())
                .put("chapterIndex", request.chapterIndex).put("limit", pageLimit))
        check(value.getString("status") == "ready") { "MANGA_DIRECTORY_UNAVAILABLE:${value.optString("reason")}" }
        scope(value, request.chapter)
        val directory = ReaderMangaDirectory(UUID.randomUUID().toString(), request.chapter, sourceVersion,
            string(value, "viewId"), string(value, "catalogRevision"), string(value, "structureRevision"), owner, lease, transport.session)
        requireAdmission(); source(request.chapter, sourceVersion)
        synchronized(lock) {
            check(!retired) { "MANGA_DIRECTORY_OWNER_RETIRED" }
            issuedDirectories.filter { it.lease == lease && it.chapter == request.chapter }.forEach { remove(it); issuedDirectories.remove(it) }
            check(issuedDirectories.size < 4) { "MANGA_DIRECTORY_CAPACITY" }
            issuedDirectories.add(directory)
            views[directory.id] = View(directory)
        }
        return try { page(value, directory, 0) } catch (failure: Throwable) { invalidate(directory); throw failure }
    }

    fun reopen(lease: ReaderOperationOwner.ClientLease, directory: ReaderMangaDirectory, currentChapterIndex: Int): ReaderMangaDirectoryPage {
        assertHeld(); requireAdmission()
        check(currentChapterIndex >= 0 && directory.owner === owner && directory.lease == lease && directory.runtime == transport.session) { "MANGA_DIRECTORY_REOPEN_SCOPE" }
        synchronized(lock) { check(!retired && directory in issuedDirectories) { "MANGA_DIRECTORY_REOPEN_NOT_ISSUED" } }
        source(directory.chapter, directory.sourceVersion)
        return open(lease, ReaderMangaChapterRequest(directory.chapter, currentChapterIndex), directory.sourceVersion)
    }

    fun next(lease: ReaderOperationOwner.ClientLease, directory: ReaderMangaDirectory, offset: Int): ReaderMangaDirectoryPage {
        current(lease, directory)
        synchronized(lock) { check(offset > 0 && checkNotNull(views[directory.id]).next == offset) { "MANGA_DIRECTORY_NOT_NEXT" } }
        return try {
            val value = catalog.readMangaDirectory("reading.directory.view.page.v2", directory.chapter,
                viewParams(directory).put("offset", offset).put("limit", pageLimit))
            current(lease, directory)
            page(value, directory, offset)
        } catch (failure: Throwable) { unavailable(directory, failure) }
    }

    fun resolve(lease: ReaderOperationOwner.ClientLease, node: ReaderMangaDirectoryNode): ReaderMangaChapterRequest {
        val directory = node.directory; current(lease, directory)
        synchronized(lock) {
            val view = checkNotNull(views[directory.id])
            check(node in view.issued && node.kind == "target" && node.chapterIndex != null && view.targets.size < 50_000) {
                "MANGA_DIRECTORY_NODE_NOT_ISSUED_TARGET"
            }
        }
        return try {
            val value = catalog.readMangaDirectory("reading.directory.target.resolve.v2", directory.chapter,
                viewParams(directory).put("nodeId", node.nodeId))
            current(lease, directory); identity(value, directory)
            check(value.getString("nodeId") == node.nodeId && integer(value, "chapterIndex") == node.chapterIndex) { "MANGA_DIRECTORY_RESOLVED_NODE" }
            check(value.getString("kind") == "chapterStart") { "MANGA_DIRECTORY_IMAGE_CHAPTER_START_REQUIRED" }
            val proof = value.getJSONObject("directoryTargetProof"); scope(proof, directory.chapter)
            check(proof.getString("catalogRevision") == directory.catalogRevision && proof.getString("structureRevision") == directory.structureRevision &&
                proof.getString("nodeId") == node.nodeId && integer(proof, "chapterIndex") == node.chapterIndex) { "MANGA_DIRECTORY_PROOF_SCOPE" }
            val target = ReaderMangaDirectoryTarget(directory, node.nodeId, checkNotNull(node.chapterIndex), string(proof, "url"), directory.sourceVersion, proof.toString())
            synchronized(lock) { check(!retired && views[directory.id]?.directory === directory); checkNotNull(views[directory.id]).targets.add(target) }
            ReaderMangaChapterRequest(directory.chapter, target.chapterIndex, directoryTarget = target)
        } catch (failure: Throwable) { unavailable(directory, failure) }
    }

    /** Revalidate the issued object immediately before the real chapter publisher consumes its native proof. */
    fun requireTarget(lease: ReaderOperationOwner.ClientLease, request: ReaderMangaChapterRequest) {
        val target = request.directoryTarget ?: return
        current(lease, target.directory)
        synchronized(lock) {
            check(target in checkNotNull(views[target.directory.id]).targets && request.chapter == target.directory.chapter &&
                request.chapterIndex == target.chapterIndex && target.sourceRuleVersion == target.directory.sourceVersion) { "MANGA_DIRECTORY_TARGET_NOT_ISSUED" }
        }
    }

    fun close(directory: ReaderMangaDirectory) {
        check(directory.owner === owner) { "FOREIGN_MANGA_DIRECTORY" }
        synchronized(lock) { remove(directory); issuedDirectories.remove(directory) }
    }

    private fun invalidate(directory: ReaderMangaDirectory) = synchronized(lock) { remove(directory) }

    private fun unavailable(directory: ReaderMangaDirectory, cause: Throwable): Nothing {
        invalidate(directory)
        if (cause is CancellationException || cause !is Exception) throw cause
        throw ReaderMangaDirectoryUnavailable(directory, cause)
    }

    /** Local revocation is safe even while an admitted original finishes on the App-owned lane. */
    fun retireAll() = synchronized(lock) {
        retired = true
        views.values.forEach { it.issued.clear(); it.targets.clear() }
        views.clear()
        issuedDirectories.clear()
    }

    private fun remove(directory: ReaderMangaDirectory) {
        views[directory.id]?.let { view ->
            check(view.directory === directory) { "MANGA_DIRECTORY_IDENTITY" }
            view.issued.clear(); view.targets.clear(); views.remove(directory.id)
        }
    }

    private fun current(lease: ReaderOperationOwner.ClientLease, directory: ReaderMangaDirectory) {
        assertHeld(); requireAdmission()
        check(directory.owner === owner && directory.lease == lease && directory.runtime == transport.session) { "MANGA_DIRECTORY_RETIRED" }
        source(directory.chapter, directory.sourceVersion)
        synchronized(lock) { check(!retired && views[directory.id]?.directory === directory) { "MANGA_DIRECTORY_RETIRED" } }
    }

    private fun source(chapter: ReaderMangaChapterRef, version: String) {
        require(chapter.sourceId.isNotBlank() && chapter.sourceId != "local" && chapter.bookId.isNotBlank()) { "MANGA_DIRECTORY_REMOTE_SCOPE" }
        val source = sources(chapter.sourceId).singleOrNull()
        check(version.isNotBlank() && source != null && source.sourceId == chapter.sourceId && source.enabled && source.sourceVersion == version) { "MANGA_DIRECTORY_SOURCE_CHANGED" }
    }

    private fun page(value: JSONObject, directory: ReaderMangaDirectory, offset: Int): ReaderMangaDirectoryPage {
        identity(value, directory)
        val total = integer(value, "visibleTotal").also { check(it in 0..50_000) { "MANGA_DIRECTORY_NODE_BUDGET" } }
        val rows = value.getJSONArray("nodes"); check(rows.length() <= pageLimit && offset <= total && rows.length() <= total - offset)
        val nodes = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index); val kind = row.getString("kind").also { check(it in setOf("group", "target", "disabled")) }
            val chapterIndex = if (!row.has("chapterIndex") || row.isNull("chapterIndex")) null else integer(row, "chapterIndex").also { check(it >= 0) }
            check(kind != "target" || chapterIndex != null) { "MANGA_DIRECTORY_TARGET_INDEX" }
            ReaderMangaDirectoryNode(directory, string(row, "nodeId"), row.get("title") as? String ?: error("MANGA_DIRECTORY_TITLE"), kind, chapterIndex,
                integer(row, "depth").also { check(it >= 0) }, if (!row.has("parentId") || row.isNull("parentId")) null else string(row, "parentId"),
                row.get("hasChildren") as? Boolean ?: error("MANGA_DIRECTORY_HAS_CHILDREN"), row.get("expanded") as? Boolean ?: error("MANGA_DIRECTORY_EXPANDED"))
        }
        val next = if (!value.has("nextOffset") || value.isNull("nextOffset")) null else integer(value, "nextOffset")
        check(next == null && offset + rows.length() == total || next != null && rows.length() > 0 && next == offset + rows.length() && next < total) { "MANGA_DIRECTORY_PAGE_OFFSET" }
        synchronized(lock) {
            val view = checkNotNull(views[directory.id]); check(!retired && view.directory === directory)
            check(view.total == null || view.total == total) { "MANGA_DIRECTORY_TOTAL_CHANGED" }
            check(nodes.map { it.nodeId }.distinct().size == nodes.size && nodes.none { it.nodeId in view.ids }) { "MANGA_DIRECTORY_DUPLICATE_NODE" }
            view.issued.addAll(nodes); view.ids.addAll(nodes.map { it.nodeId }); view.next = next; view.total = total
        }
        return ReaderMangaDirectoryPage(directory, Collections.unmodifiableList(nodes), next)
    }

    private fun identity(value: JSONObject, directory: ReaderMangaDirectory) {
        scope(value, directory.chapter)
        check(value.getString("viewId") == directory.viewId && value.getString("catalogRevision") == directory.catalogRevision &&
            value.getString("structureRevision") == directory.structureRevision) { "MANGA_DIRECTORY_CHANGED" }
    }
    private fun scope(value: JSONObject, chapter: ReaderMangaChapterRef) {
        check(value.getString("sourceId") == chapter.sourceId && value.getString("bookId") == chapter.bookId) { "MANGA_DIRECTORY_SCOPE" }
    }
    private fun string(value: JSONObject, key: String) = (value.get(key) as? String)?.also { check(it.isNotBlank()) } ?: error("MANGA_DIRECTORY_STRING:$key")
    private fun integer(value: JSONObject, key: String): Int = ReaderMangaProgressPolicy.integer(value, key).also { check(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
    private fun params(chapter: ReaderMangaChapterRef) = JSONObject().put("sourceId", chapter.sourceId).put("bookId", chapter.bookId)
    private fun viewParams(directory: ReaderMangaDirectory) = params(directory.chapter).put("viewId", directory.viewId)
        .put("catalogRevision", directory.catalogRevision).put("structureRevision", directory.structureRevision)
}
