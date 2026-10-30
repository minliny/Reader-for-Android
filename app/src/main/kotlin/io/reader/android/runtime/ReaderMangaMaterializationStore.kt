package io.reader.android.runtime

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Durable Host-side materialization token boundary; Core remains the authority for the final ACK. */
internal class ReaderMangaMaterializationStore(private val resourceStore: ReaderMangaResourceStore, private val root: File) {
    private data class Pending(val token: String, val chapterKey: String, val manifestVersion: String, val intent: String, val refs: List<String> = emptyList())

    init { root.mkdirs() }

    @Synchronized
    fun prepare(result: ReaderMangaMaterializationPrepareResult) {
        val pending = Pending(result.token, key(result.chapter), result.manifestVersion, result.intent.wire)
        write(File(root, ".pending-${result.token}"), JSONObject()
            .put("token", pending.token).put("chapter", pending.chapterKey)
            .put("manifestVersion", pending.manifestVersion).put("intent", pending.intent)
            .put("resourceRefs", JSONArray()).toString())
    }

    @Synchronized
    fun requireResources(token: String, chapter: ReaderMangaChapterIdentity, manifestVersion: String, refs: List<String>) {
        val pending = readPending(token)
        check(pending.chapterKey == key(chapter) && pending.manifestVersion == manifestVersion) { "MANGA_MATERIALIZATION_SCOPE" }
        check(refs.isNotEmpty() && refs.distinct().size == refs.size) { "MANGA_MATERIALIZATION_REFS" }
        refs.forEach { ref -> resourceStore.readDecodedByResourceRef(ref) }
        write(File(root, ".pending-$token"), JSONObject()
            .put("token", pending.token).put("chapter", pending.chapterKey)
            .put("manifestVersion", pending.manifestVersion).put("intent", pending.intent)
            .put("resourceRefs", JSONArray().also { refs.forEach(it::put) }).toString())
    }

    @Synchronized
    fun complete(result: ReaderMangaMaterializationCompleteResult, token: String) {
        val pendingFile = File(root, ".pending-$token")
        check(pendingFile.isFile) { "MANGA_MATERIALIZATION_TOKEN" }
        val pending = readPending(token)
        if (result.completed) {
            val finalFile = File(root, "materialized-${token}.json")
            write(finalFile, pendingFile.readText(Charsets.UTF_8))
            pending.refs.forEach(resourceStore::protectResourceRef)
        }
        pendingFile.delete()
    }

    @Synchronized
    fun abort(token: String) {
        val pendingFile = File(root, ".pending-$token")
        if (pendingFile.isFile) readPending(token).refs.forEach(resourceStore::unprotectResourceRef)
        pendingFile.delete()
    }

    private fun readPending(token: String): Pending {
        val value = JSONObject(File(root, ".pending-$token").readText(Charsets.UTF_8))
        val refs = value.optJSONArray("resourceRefs")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
        return Pending(value.getString("token"), value.getString("chapter"), value.getString("manifestVersion"), value.getString("intent"), refs)
    }

    private fun write(file: File, text: String) {
        val staging = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.tmp")
        FileOutputStream(staging).use { output -> output.write(text.toByteArray(Charsets.UTF_8)); output.fd.sync() }
        check(staging.renameTo(file)) { "MANGA_MATERIALIZATION_COMMIT" }
    }

    private fun key(chapter: ReaderMangaChapterIdentity): String = "${chapter.sourceId}\u0000${chapter.bookId}\u0000${chapter.chapterId}"
}
