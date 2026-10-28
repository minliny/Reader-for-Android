package io.reader.android.runtime

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** Host-only operation identity/receipts. This is not a second business database. */
internal class DurableJournal(private val directory: File) {
    init { check(directory.mkdirs() || directory.isDirectory) }

    @Synchronized
    fun readAll(): List<JSONObject> = directory.listFiles()
        .orEmpty().filter { it.name.endsWith(".json") || it.name.endsWith(".json.bak") }
        .map { if (it.name.endsWith(".bak")) File(directory, it.name.removeSuffix(".bak")) else it }
        .distinctBy { it.name }.sortedBy { it.name }.map { file ->
            // A corrupt record blocks mutation; it must never disappear via mapNotNull.
            JSONObject(AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
                .also { check(it.getInt("journalSchema") == 1) { "UNSUPPORTED_HOST_JOURNAL" } }
        }

    @Synchronized
    fun terminal(id: String, requestId: Long, session: String, event: JSONObject) {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        val base = File(directory, "$id.json")
        if (!base.exists() && !File(directory, "$id.json.bak").exists()) {
            check(File(File(directory, "Completed"), "$id.json").isFile) { "ORIGINAL_JOURNAL_MISSING" }
            return
        }
        val record = JSONObject(AtomicFile(base).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
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
        if (!base.exists() && !File(directory, "$id.json.bak").exists()) {
            if (!includeCompleted) return null
            base = File(File(directory, "Completed"), "$id.json")
            if (!base.exists() && !File(base.parentFile, base.name + ".bak").exists()) return null
        }
        return JSONObject(AtomicFile(base).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            .also { check(it.getInt("journalSchema") == 1 && it.getString("id") == id) { "ORIGINAL_JOURNAL_IDENTITY" } }
    }

    @Synchronized
    fun write(record: JSONObject) {
        val id = record.getString("id")
        require(id.matches(Regex("[a-zA-Z0-9-]{1,100}")))
        // Completed records are retained as original evidence outside the active
        // scan. Frequent position saves must not reread every prior preview/ACK.
        val parent = if (record.optBoolean("completed")) File(directory, "Completed") else directory
        check(parent.mkdirs() || parent.isDirectory)
        val atomic = AtomicFile(File(parent, "$id.json"))
        val stream = atomic.startWrite()
        try {
            stream.write(record.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
        // finishWrite has committed the file. A later fsync failure must not
        // invoke failWrite and erase its durable original terminal evidence.
        syncDirectory(parent)
        if (parent != directory) {
            AtomicFile(File(directory, "$id.json")).delete()
            syncDirectory(directory)
        }
    }
}

internal fun syncDirectory(directory: File) {
    val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
    try {
        check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "JOURNAL_DIRECTORY_REQUIRED" }
        Os.fsync(descriptor)
    } finally { Os.close(descriptor) }
}

