package io.reader.rebuild.host.storage

import com.reader.core.NativeCoreBridge
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.UUID

internal data class StagedDocument(val transactionId: UUID, val file: File, val sha256: String) {
    val bookId get() = "local:$sha256"
}

internal class AuthorizedArchive internal constructor(val file: File) {
    fun read(entry: String, maximumBytes: Long): ByteArray {
        require(entry.isNotBlank() && '\\' !in entry && '\u0000' !in entry)
        require(entry.split('/').none { it.isEmpty() || it == "." || it == ".." })
        return NativeCoreBridge.readEpubEntry(file.absolutePath, entry, maximumBytes)
    }
}

internal class FileAndAssetHost(private val root: File) {
    private val staging = File(root, "staging")
    private val originals = File(root, "originals")
    val journals = File(root, "import-journal")

    fun stage(name: String, open: () -> InputStream): StagedDocument {
        require(name.isNotBlank() && name != "." && name != ".." && name.none { it in "/\\\u0000" })
        val transaction = UUID.randomUUID()
        val directory = File(staging, transaction.toString())
        ensureDirectory(directory)
        val file = File(directory, name)
        try {
            val sha = open().use { input ->
                FileOutputStream(file).use { output ->
                    val hash = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val length = input.read(buffer)
                        if (length < 0) break
                        output.write(buffer, 0, length)
                        hash.update(buffer, 0, length)
                    }
                    output.fd.sync()
                    hex(hash.digest())
                }
            }
            return StagedDocument(transaction, file, sha)
        } catch (failure: Exception) {
            file.delete()
            directory.delete()
            throw failure
        }
    }

    fun retain(document: StagedDocument): AuthorizedArchive = synchronized(publicationLock) {
        ensureDirectory(originals)
        val target = original(document.sha256)
        val lock = File(originals, ".publication.lock")
        FileChannel.open(lock.toPath(), CREATE, WRITE).use { channel ->
            channel.lock().use {
                if (Files.exists(target.toPath(), NOFOLLOW_LINKS)) {
                    check(Files.isRegularFile(target.toPath(), NOFOLLOW_LINKS) && sha256(target) == document.sha256)
                } else {
                    check(sha256(document.file) == document.sha256)
                    Files.move(document.file.toPath(), target.toPath(), ATOMIC_MOVE)
                }
            }
        }
        AuthorizedArchive(target)
    }

    fun authorize(sha: String): AuthorizedArchive = original(sha).let { file ->
        check(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && sha256(file) == sha) { "Original unavailable" }
        AuthorizedArchive(file)
    }

    fun cleanStage(document: StagedDocument) {
        check(!document.file.exists() || document.file.delete()) { "Staged original cleanup pending" }
        val directory = requireNotNull(document.file.parentFile)
        check(!directory.exists() || directory.delete()) { "Staged directory cleanup pending" }
    }

    fun writeJournal(id: UUID, contents: String) {
        ensureDirectory(journals)
        val pending = Files.createTempFile(journals.toPath(), ".pending-", ".json").toFile()
        try {
            FileOutputStream(pending).use { output ->
                output.write(contents.encodeToByteArray())
                output.fd.sync()
            }
            Files.move(pending.toPath(), journal(id).toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            pending.delete()
        }
    }

    fun removeJournal(id: UUID) = Files.deleteIfExists(journal(id).toPath())

    private fun journal(id: UUID) = File(journals, "$id.json")

    private fun original(sha: String): File {
        require(SHA.matches(sha))
        return File(originals, "$sha.source")
    }

    companion object {
        private val SHA = Regex("[0-9a-f]{64}")
        private val publicationLock = Any()
        private fun ensureDirectory(directory: File) = check(directory.mkdirs() || directory.isDirectory)
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        fun sha256(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    hash.update(buffer, 0, length)
                }
            }
            return hex(hash.digest())
        }
    }
}
