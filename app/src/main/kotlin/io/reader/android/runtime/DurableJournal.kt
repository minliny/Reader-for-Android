package io.reader.android.runtime

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File

/** Host-only operation identity/receipts. This is not a second business database. */
internal class DurableJournal(private val directory: File) {
    init { check(directory.mkdirs() || directory.isDirectory) }

    @Synchronized
    fun readAll(): List<JSONObject> {
        rejectOrphanCandidates()
        return directory.listFiles()
        .orEmpty().filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
        .map { if (it.name.endsWith(".bak")) File(directory, it.name.removeSuffix(".bak")) else it }
        .distinctBy { it.name }.sortedBy { it.name }.map { file ->
            // A corrupt record blocks mutation; it must never disappear via mapNotNull.
            readRecord(file)
                .also { check(it.getInt("journalSchema") == 1) { "UNSUPPORTED_HOST_JOURNAL" } }
        }
    }

    @Synchronized
    fun terminal(id: String, requestId: Long, session: String, event: JSONObject) {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        val base = File(directory, "$id.json")
        rejectOrphanCandidate(base)
        if (!base.exists() && !File(directory, "$id.json.bak").exists()) {
            check(File(File(directory, "Completed"), "$id.json").isFile) { "ORIGINAL_JOURNAL_MISSING" }
            return
        }
        val record = readRecord(base)
        // Querying an original receipt can advance the operation before its wire
        // callback arrives. Such a callback cannot overwrite the later journal.
        if (record.optBoolean("completed") || record.optLong("requestId", -1) != requestId ||
            record.optString("runtimeSession") != session) return
        if (record.getString("kind") == "import" && record.optString("inFlightStage").isEmpty()) return
        record.put("wireTerminal", event)
        write(record)
    }

    @Synchronized
    fun exact(id: String, includeCompleted: Boolean = false): JSONObject? {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        var base = File(directory, "$id.json")
        rejectOrphanCandidate(base)
        if (!base.exists() && !File(directory, "$id.json.bak").exists()) {
            if (!includeCompleted) return null
            base = File(File(directory, "Completed"), "$id.json")
            if (!base.exists() && !File(base.parentFile, base.name + ".bak").exists()) return null
        }
        return readRecord(base)
            .also { check(it.getInt("journalSchema") == 1 && it.getString("id") == id) { "ORIGINAL_JOURNAL_IDENTITY" } }
    }

    /** Query-only A4 source intent access. A retained .new is never opened by AtomicFile or promoted.
     * Unlike progressCandidateForReconcile this returns NO candidate terminal or completion authority. */
    @Synchronized
    fun sourceRulesOriginalForQuery(id: String): SourceRulesJournalOriginal? {
        require(id.matches(Regex("android-source-[a-fA-F0-9-]{36}"))) { "SOURCE_JOURNAL_ID" }
        val active = File(directory, "$id.json")
        val base = if (active.exists() || File(active.path + ".bak").exists() || File(active.path + ".new").exists()) active
            else File(File(directory, "Completed"), "$id.json")
        val retained = File(base.path + ".new").exists()
        val record = if (retained) {
            check(base.isFile && !File(base.path + ".bak").exists()) { "SOURCE_CANDIDATE_BASE_OR_BACKUP_CONFLICT_RETAINED" }
            // Only the committed base is decoded. All candidate bytes remain untouched and untrusted.
            SourceRulesReceiptPolicy.decodeRecord(readBounded(base, SourceRulesReceiptPolicy.MAX_RECORD_BYTES))
        } else {
            if (!base.exists() && !File(base.path + ".bak").exists()) return null
            readRecord(base)
        }
        SourceRulesReceiptPolicy.parameters(record)
        check(record.getString("id") == id) { "SOURCE_JOURNAL_IDENTITY" }
        return SourceRulesJournalOriginal(record.toString(), retained)
    }

    @Synchronized
    fun sourceRulesActiveForQuery(): List<SourceRulesJournalOriginal> = directory.listFiles().orEmpty()
        .filter { it.name.startsWith("android-source-") &&
            (it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".json.new")) }
        .map { it.name.removeSuffix(".new").removeSuffix(".bak").removeSuffix(".json") }
        .distinct().sorted().map { sourceRulesOriginalForQuery(it) ?: error("SOURCE_JOURNAL_MISSING") }

    /** Online-add base query only. A retained .new is flagged, never read/promoted/deleted. */
    @Synchronized
    fun onlineAddOriginalForQuery(id: String): ReaderOnlineAddJournalSnapshot? {
        require(ReaderOnlineAddPolicy.id.matches(id)) { "ONLINE_ADD_JOURNAL_ID" }
        val active = File(directory, "$id.json")
        val base = if (active.exists() || File(active.path + ".bak").exists() || File(active.path + ".new").exists()) active
            else File(File(directory, "Completed"), "$id.json")
        val retained = File(base.path + ".new").exists()
        val record = if (retained) {
            check(base.isFile && !File(base.path + ".bak").exists()) { "ONLINE_ADD_CANDIDATE_BASE_OR_BACKUP_CONFLICT" }
            ReaderOnlineAddPolicy.parse(readBounded(base, ReaderOnlineAddPolicy.MAX_BYTES))
        } else {
            if (!base.exists() && !File(base.path + ".bak").exists()) return null
            readRecord(base)
        }
        ReaderOnlineAddPolicy.record(record)
        check(record.getString("id") == id) { "ONLINE_ADD_JOURNAL_IDENTITY" }
        return ReaderOnlineAddJournalSnapshot(record.toString(), retained)
    }
    @Synchronized
    fun onlineAddActiveForQuery(): List<ReaderOnlineAddJournalSnapshot> = directory.listFiles().orEmpty()
        .filter { it.name.startsWith("android-onlineadd-") &&
            (it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".json.new")) }
        .map { it.name.removeSuffix(".new").removeSuffix(".bak").removeSuffix(".json") }
        .distinct().sorted().map { onlineAddOriginalForQuery(it) ?: error("ONLINE_ADD_JOURNAL_MISSING") }

    /** Manga has a distinct ACK schema. A .new remains untrusted; query only the committed base. */
    @Synchronized
    fun mangaProgressOriginalForQuery(id: String): ReaderMangaProgressJournalSnapshot? {
        require(ReaderMangaProgressPolicy.id.matches(id)) { "MANGA_PROGRESS_JOURNAL_ID" }
        val active = File(directory, "$id.json")
        val base = if (active.exists() || File(active.path + ".bak").exists() || File(active.path + ".new").exists()) active
            else File(File(directory, "Completed"), "$id.json")
        val retained = File(base.path + ".new").exists()
        val record = if (retained) {
            check(base.isFile && !File(base.path + ".bak").exists()) { "MANGA_PROGRESS_CANDIDATE_BASE_OR_BACKUP_CONFLICT" }
            ReaderMangaProgressPolicy.decodeRecord(readBounded(base))
        } else {
            if (!base.exists() && !File(base.path + ".bak").exists()) return null
            readRecord(base).also(ReaderMangaProgressPolicy::record)
        }
        check(record.getString("id") == id) { "MANGA_PROGRESS_JOURNAL_IDENTITY" }
        return ReaderMangaProgressJournalSnapshot(record.toString(), retained)
    }
    @Synchronized
    fun mangaProgressActiveForQuery(): List<ReaderMangaProgressJournalSnapshot> = directory.listFiles().orEmpty()
        .filter { it.name.startsWith("android-manga-progress-") &&
            (it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".json.new")) }
        .map { it.name.removeSuffix(".new").removeSuffix(".bak").removeSuffix(".json") }
        .distinct().sorted().map { mangaProgressOriginalForQuery(it) ?: error("MANGA_PROGRESS_JOURNAL_MISSING") }

    /** Exact read-only source of supported originals; never AtomicFile.openRead or candidate recovery. */
    @Synchronized
    internal fun progressOriginalsForContinuation(root: File, bookId: String): List<ReaderProgressOriginalSnapshot> {
        check(directory.canonicalFile == File(root, "Operations").canonicalFile) { "PROGRESS_INTENT_FOREIGN_JOURNAL" }
        ReaderProgressOriginBoundary.requireRealDirectory(directory)
        val files = checkNotNull(directory.listFiles()) { "PROGRESS_INTENT_JOURNAL_LIST_FAILED" }
            .filter { it.name.startsWith("android-progress-") &&
                (it.name.endsWith(".json") || it.name.endsWith(".json.bak") || it.name.endsWith(".json.new")) }
        check(files.none { it.name.endsWith(".bak") || it.name.endsWith(".new") }) { "PROGRESS_INTENT_BACKUP_OR_CANDIDATE" }
        return files.sortedBy { it.name }.mapNotNull { file ->
            ReaderProgressOriginBoundary.requireRegular(file)
            check(file.canonicalFile.parentFile == directory.canonicalFile && !File(File(directory, "Completed"), file.name).exists()) { "PROGRESS_INTENT_CONFLICTING_ORIGINAL" }
            val raw = readBounded(file)
            val record = ProgressJournalCandidatePolicy.parse(raw)
            check(record.getString("id") + ".json" == file.name) { "PROGRESS_INTENT_FILE_IDENTITY" }
            if (record.getString("bookId") != bookId || record.getJSONObject("parameters").getString("sourceId") != "local") null else ReaderProgressOriginalSnapshot.from(raw)
        }
    }

    /** Only explicit original-progress reconcile uses this. Ordinary reads still return base pending. */
    @Synchronized
    fun progressCandidateForReconcile(id: String, bookId: String): JSONObject? = progressCandidateForReconcile(id, ShelfBookKey("local", bookId))

    @Synchronized
    fun progressCandidateForReconcile(id: String, owner: ShelfBookKey): JSONObject? {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        val base = File(directory, "$id.json")
        if (!File(base.path + ".new").exists()) return null
        val candidate = qualifiedCandidate(base)
        check(candidate.id == id && candidate.bookId == owner.bookId && candidate.sourceId == owner.sourceId) { "ORIGINAL_PROGRESS_OWNER_MISMATCH" }
        val provenance = retainCandidate(candidate)
        return candidate.record().put("originalWireCandidate", provenance) // completed remains false.
    }

    private fun readRecord(base: File): JSONObject {
        if (File(base.path + ".new").exists()) {
            if (ReaderMangaProgressPolicy.id.matches(base.name.removeSuffix(".json"))) {
                check(base.isFile && !File(base.path + ".bak").exists()) { "MANGA_PROGRESS_CANDIDATE_BASE_OR_BACKUP_CONFLICT" }
                // Other services may scan active kinds, but must never parse this as text progress or promote its candidate.
                return ReaderMangaProgressPolicy.decodeRecord(readBounded(base))
            }
            qualifiedCandidate(base) // Validate, but never openRead, promote, delete or return the candidate as committed.
            return ProgressJournalCandidatePolicy.parse(readBounded(base))
        }
        return JSONObject(AtomicFile(base).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private fun qualifiedCandidate(base: File): ProgressJournalCandidatePolicy.Qualified {
        check(base.parentFile == directory && base.isFile && !File(base.path + ".bak").exists()) { "JOURNAL_CANDIDATE_OWNER_OR_BACKUP_CONFLICT" }
        return ProgressJournalCandidatePolicy.qualify(readBounded(base), readBounded(File(base.path + ".new")))
            .also { check(base.name == it.id + ".json") { "JOURNAL_CANDIDATE_FILE_IDENTITY" } }
    }

    private fun rejectOrphanCandidate(base: File) {
        check(!File(base.path + ".new").exists() || base.isFile) { "JOURNAL_ORPHAN_CANDIDATE_RETAINED" }
    }

    private fun rejectOrphanCandidates() {
        directory.listFiles().orEmpty().filter { it.name.endsWith(".json.new") }.forEach {
            rejectOrphanCandidate(File(directory, it.name.removeSuffix(".new")))
        }
    }

    private fun readBounded(file: File, limit: Int = ProgressJournalCandidatePolicy.MAX_BYTES): ByteArray = file.inputStream().use { input ->
        check(file.length() in 1..limit.toLong()) { "JOURNAL_CANDIDATE_BYTE_BUDGET" }
        val buffer = ByteArray(limit + 1)
        var count = 0
        while (count < buffer.size) {
            val amount = input.read(buffer, count, buffer.size - count)
            if (amount == -1) break
            check(amount > 0) { "JOURNAL_CANDIDATE_READ_STALLED" }
            count += amount
        }
        check(count in 1..limit) { "JOURNAL_CANDIDATE_BYTE_BUDGET" }
        buffer.copyOf(count)
    }

    private fun candidateProvenance(candidate: ProgressJournalCandidatePolicy.Qualified): Pair<File, ByteArray> =
        File(File(directory, "OriginalWireCandidates"), "${candidate.id}-${candidate.candidateSha256}.json") to provenanceBytes(candidate)

    private fun provenanceBytes(candidate: ProgressJournalCandidatePolicy.Qualified): ByteArray {
        val active = candidate.activeBytesCopy(); val next = candidate.candidateBytesCopy()
        return JSONObject().put("schemaVersion", 1).put("operationId", candidate.id)
            .put("activeSha256", candidate.activeSha256).put("candidateSha256", candidate.candidateSha256)
            .put("activeBytes", active.size).put("candidateBytes", next.size)
            .put("activeBase64", Base64.encodeToString(active, Base64.NO_WRAP))
            .put("candidateBase64", Base64.encodeToString(next, Base64.NO_WRAP)).toString().toByteArray(Charsets.UTF_8)
    }

    private fun retainCandidate(candidate: ProgressJournalCandidatePolicy.Qualified): JSONObject {
        val base = File(directory, candidate.id + ".json")
        check(candidate.unchanged(readBounded(base), readBounded(File(base.path + ".new")))) { "JOURNAL_CANDIDATE_CHANGED_BEFORE_RETENTION" }
        val (target, bytes) = candidateProvenance(candidate)
        check(bytes.size <= PROVENANCE_LIMIT)
        check(target.parentFile.mkdirs() || target.parentFile.isDirectory)
        if (target.exists()) {
            check(readBounded(target, PROVENANCE_LIMIT).contentEquals(bytes)) { "JOURNAL_CANDIDATE_PROVENANCE_CONFLICT" }
        } else {
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            var finished = false
            try {
                stream.write(bytes); stream.fd.sync(); atomic.finishWrite(stream); finished = true
            } catch (error: Throwable) {
                if (!finished) atomic.failWrite(stream)
                throw error
            }
            check(readBounded(target, PROVENANCE_LIMIT).contentEquals(bytes)) { "JOURNAL_CANDIDATE_PROVENANCE_PUBLICATION" }
        }
        syncDirectory(target.parentFile); syncDirectory(directory)
        return provenanceIdentity(candidate, target, bytes)
    }

    private fun provenanceIdentity(candidate: ProgressJournalCandidatePolicy.Qualified, file: File, bytes: ByteArray): JSONObject = JSONObject()
        .put("path", "OriginalWireCandidates/" + file.name).put("bytes", bytes.size).put("sha256", ProgressJournalCandidatePolicy.hash(bytes))
        .put("activeSha256", candidate.activeSha256).put("candidateSha256", candidate.candidateSha256)

    private fun requireCandidateCompletion(record: JSONObject): Boolean {
        val base = File(directory, record.getString("id") + ".json")
        if (!File(base.path + ".new").exists()) return false
        check(record.optString("kind") != ReaderMangaProgressPolicy.KIND) { "MANGA_PROGRESS_CANDIDATE_RETAINED" }
        val candidate = qualifiedCandidate(base)
        val (target, bytes) = candidateProvenance(candidate)
        check(target.isFile && readBounded(target, PROVENANCE_LIMIT).contentEquals(bytes)) { "JOURNAL_CANDIDATE_PROVENANCE_REQUIRED" }
        val expected = candidate.record().put("originalWireCandidate", provenanceIdentity(candidate, target, bytes)).put("completed", true)
        check(ProgressJournalCandidatePolicy.sameObject(record, expected)) { "JOURNAL_CANDIDATE_WRITE_REQUIRES_EXPLICIT_ORIGINAL_COMPLETION" }
        return true
    }

    @Synchronized
    fun write(record: JSONObject) {
        val id = record.getString("id")
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        val candidateCompletion = requireCandidateCompletion(record)
        // Completed records are retained as original evidence outside the active
        // scan. Frequent position saves must not reread every prior preview/ACK.
        val parent = if (record.optBoolean("completed")) File(directory, "Completed") else directory
        check(parent.mkdirs() || parent.isDirectory)
        val target = File(parent, "$id.json")
        val encoded = record.toString().toByteArray(Charsets.UTF_8)
        if (candidateCompletion && target.exists()) {
            check(readBounded(target).contentEquals(encoded)) { "ORIGINAL_COMPLETED_CANDIDATE_CONFLICT" }
        } else {
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                stream.write(encoded)
                stream.fd.sync()
                atomic.finishWrite(stream)
            } catch (error: Throwable) {
                atomic.failWrite(stream)
                throw error
            }
        }
        // finishWrite has committed the file. A later fsync failure must not
        // invoke failWrite and erase its durable original terminal evidence.
        if (candidateCompletion) check(readBounded(target).contentEquals(encoded)) { "ORIGINAL_CANDIDATE_COMPLETION_PUBLICATION" }
        syncDirectory(parent)
        if (parent != directory) {
            AtomicFile(File(directory, "$id.json")).delete()
            syncDirectory(directory)
        }
    }

    private companion object { const val PROVENANCE_LIMIT = ProgressJournalCandidatePolicy.MAX_BYTES * 3 }
}

internal fun syncDirectory(directory: File) {
    val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
    try {
        check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "JOURNAL_DIRECTORY_REQUIRED" }
        Os.fsync(descriptor)
    } finally { Os.close(descriptor) }
}

