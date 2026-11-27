package io.reader.rebuild.host.network

import io.reader.rebuild.core.CoreMangaAssets
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID

internal data class MangaResourceFile(
    val file: File,
    val bytes: Long,
    val sha256: String,
    val chunks: Int,
)

/** A private process lease covers all files named by this runtime's registered transfers. */
internal class MangaTransferStore(root: File) : AutoCloseable {
    private data class Slot(val file: File, var reservation: Long = 0, var result: MangaResourceFile? = null)

    private val lease = ScratchLease(root)
    private val outputs = mutableMapOf<String, Slot>()
    private val inspections = mutableMapOf<String, MangaResourceFile>()
    private val inputs = mutableSetOf<File>()
    private var reserved = 0L
    private var closed = false

    @Synchronized fun register(id: String) {
        check(!closed && UUID.fromString(id).toString() == id && outputs.size < 8 && id !in outputs)
        outputs[id] = Slot(File(lease.directory, "$id.image"))
    }

    @Synchronized fun prepareOutput(id: String, bytes: Long, extraBytes: Long): File {
        val slot = checkNotNull(outputs[id])
        require(slot.reservation == 0L && bytes in 1..CoreMangaAssets.ASSET_LIMIT && extraBytes >= 0)
        val count = bytes + extraBytes
        check(count <= DISK_LIMIT - reserved) { "Manga temporary file budget exhausted" }
        slot.reservation = count
        reserved += count
        return slot.file
    }

    @Synchronized fun publish(id: String, bytes: Long, sha256: String, chunks: Int) {
        val slot = checkNotNull(outputs[id])
        check(bytes in 1..slot.reservation && slot.file.length() == bytes)
        reserved -= slot.reservation - bytes
        slot.reservation = bytes
        slot.result = MangaResourceFile(slot.file, bytes, sha256, chunks)
    }

    @Synchronized fun result(id: String): MangaResourceFile? = outputs[id]?.result

    @Synchronized fun registerInspection(id: String, forTransfer: String) {
        check(!closed && UUID.fromString(id).toString() == id && id !in inspections && inspections.size < 8)
        inspections[id] = checkNotNull(outputs[forTransfer]?.result)
    }

    @Synchronized fun inspection(id: String, bytes: Long, sha256: String): MangaResourceFile =
        checkNotNull(inspections[id]).also { check(it.bytes == bytes && it.sha256 == sha256 && it.file.length() == bytes) }

    @Synchronized fun finishInspection(id: String) {
        inspections.remove(id)
    }

    @Synchronized fun discard(id: String) {
        val slot = outputs.remove(id) ?: return
        inspections.entries.removeAll { it.value.file == slot.file }
        reserved -= slot.reservation
        slot.file.delete()
    }

    @Synchronized fun temporaryInput(): File {
        check(!closed && MAX_INPUT <= DISK_LIMIT - reserved && inputs.size < 4)
        return File.createTempFile("input-", ".image", lease.directory).also {
            reserved += MAX_INPUT
            inputs.add(it)
        }
    }

    @Synchronized fun discardInput(file: File) {
        if (inputs.remove(file)) {
            file.delete()
            reserved -= MAX_INPUT
        }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        outputs.keys.toList().forEach(::discard)
        inputs.toList().forEach(::discardInput)
        lease.close()
    }

    private class ScratchLease(root: File) : AutoCloseable {
        val directory: File
        private val owner: RandomAccessFile
        private val lock: FileLock

        init {
            check(root.mkdirs() || root.isDirectory)
            val acquired = synchronized(acquisition) {
                RandomAccessFile(File(root, ".sessions"), "rw").use { parents ->
                    parents.channel.lock().use {
                        for (candidate in root.listFiles().orEmpty()) {
                            if (!candidate.isDirectory || candidate.canonicalFile.parentFile != root.canonicalFile ||
                                candidate.canonicalFile in active ||
                                runCatching { UUID.fromString(candidate.name).toString() }.getOrNull() != candidate.name
                            ) continue
                            runCatching {
                                RandomAccessFile(File(candidate, ".owner"), "rw").use { other ->
                                    try { other.channel.tryLock()?.use { candidate.deleteRecursively() } }
                                    catch (_: OverlappingFileLockException) { /* an active lease in this process */ }
                                }
                            }
                        }
                        val directory = File(root, UUID.randomUUID().toString())
                        check(directory.mkdir())
                        val owner = RandomAccessFile(File(directory, ".owner"), "rw")
                        try {
                            val lock = checkNotNull(owner.channel.tryLock())
                            active.add(directory.canonicalFile)
                            Triple(directory, owner, lock)
                        }
                        catch (error: Throwable) {
                            owner.close()
                            directory.deleteRecursively()
                            throw error
                        }
                    }
                }
            }
            directory = acquired.first
            owner = acquired.second
            lock = acquired.third
        }

        override fun close() {
            synchronized(acquisition) {
                val canonical = directory.canonicalFile
                try { directory.deleteRecursively() } finally {
                    try { lock.close() } finally {
                        try { owner.close() } finally { active.remove(canonical) }
                    }
                }
            }
        }

        companion object {
            private val acquisition = Any()
            // Avoid opening a second descriptor for a live process's fcntl
            // lock: closing that descriptor can release a process-wide lock.
            private val active = mutableSetOf<File>()
        }
    }

    companion object {
        private const val MAX_INPUT = 16L * 1024 * 1024
        private const val DISK_LIMIT = 192L * 1024 * 1024
    }
}
