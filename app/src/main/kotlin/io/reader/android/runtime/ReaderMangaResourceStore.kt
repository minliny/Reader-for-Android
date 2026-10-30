package io.reader.android.runtime

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Host-owned encoded resource store. A staged transfer is never visible as a
 * committed resource; the directory rename is the commit boundary.
 */
internal class ReaderMangaResourceStore(private val root: File) {
    data class Staged internal constructor(
        val identity: ReaderMangaTransferIdentity,
        val directory: File,
        val bytes: Long,
        val sha256: String,
        val contentType: String?,
    )

    data class Resource(
        val identity: ReaderMangaTransferIdentity,
        val bytes: Long,
        val sha256: String,
        val contentType: String?,
        val encoded: ByteArray,
    )

    init {
        root.mkdirs()
        cleanupAbandonedStages()
    }

    @Synchronized
    fun stage(identity: ReaderMangaTransferIdentity, chunks: List<ByteArray>, contentType: String?): Staged {
        require(chunks.isNotEmpty()) { "MANGA_RESOURCE_EMPTY" }
        val directory = File(root, ".stage-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "MANGA_RESOURCE_STAGE_CREATE" }
        val encoded = File(directory, ENCODED)
        try {
            FileOutputStream(encoded).use { output ->
                chunks.forEach { chunk ->
                    require(chunk.isNotEmpty() && chunk.size <= MAX_CHUNK_BYTES) { "MANGA_RESOURCE_CHUNK" }
                    output.write(chunk)
                }
                output.fd.sync()
            }
            val bytes = encoded.length()
            require(bytes in 1..MAX_BYTES) { "MANGA_RESOURCE_BYTES" }
            val sha256 = sha256(encoded)
            File(directory, META).writeText(
                "${identity.wireId}\n${identity.resourceRef}\n$bytes\n$sha256\n${contentType.orEmpty()}\n",
                Charsets.UTF_8,
            )
            FileOutputStream(File(directory, META), true).use { it.fd.sync() }
            return Staged(identity, directory, bytes, sha256, contentType)
        } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
    }

    @Synchronized
    fun commit(staged: Staged) {
        require(staged.directory.name.startsWith(".stage-")) { "MANGA_RESOURCE_STAGE" }
        check(staged.directory.parentFile?.canonicalFile == root.canonicalFile) { "MANGA_RESOURCE_ROOT" }
        val finalDirectory = File(root, key(staged.identity, staged.sha256))
        if (finalDirectory.exists()) {
            val existing = readMeta(finalDirectory)
            check(existing.first == staged.identity.wireId && existing.second == staged.identity.resourceRef &&
                existing.third == staged.bytes && existing.fourth == staged.sha256) { "MANGA_RESOURCE_CONFLICT" }
            staged.directory.deleteRecursively()
            return
        }
        check(staged.directory.renameTo(finalDirectory)) { "MANGA_RESOURCE_COMMIT" }
    }

    @Synchronized
    fun read(identity: ReaderMangaTransferIdentity, sha256: String, bytes: Long): Resource {
        require(sha256.matches(Regex("[0-9a-f]{64}")) && bytes in 1..MAX_BYTES) { "MANGA_RESOURCE_EXPECTED" }
        val directory = File(root, key(identity, sha256))
        val meta = readMeta(directory)
        check(meta.first == identity.wireId && meta.second == identity.resourceRef && meta.third == bytes && meta.fourth == sha256) {
            "MANGA_RESOURCE_IDENTITY"
        }
        val encoded = File(directory, ENCODED).readBytes()
        check(encoded.size.toLong() == bytes && sha256(encoded) == sha256) { "MANGA_RESOURCE_DIGEST" }
        return Resource(identity, bytes, sha256, meta.fifth.ifBlank { null }, encoded)
    }

    @Synchronized
    fun readByTransferId(wireId: String, sha256: String, bytes: Long): Resource {
        require(wireId.matches(Regex("rmt1:[0-9a-f]{64}"))) { "MANGA_TRANSFER_IDENTITY" }
        require(sha256.matches(Regex("[0-9a-f]{64}")) && bytes in 1..MAX_BYTES) { "MANGA_RESOURCE_EXPECTED" }
        val directory = findDirectory(wireId) { meta -> meta.third == bytes && meta.fourth == sha256 }
        val meta = readMeta(directory)
        val encoded = File(directory, ENCODED).readBytes()
        check(encoded.size.toLong() == bytes && sha256(encoded) == sha256) { "MANGA_RESOURCE_DIGEST" }
        return Resource(ReaderMangaTransferIdentity(wireId, meta.second), bytes, sha256,
            meta.fifth.ifBlank { null }, encoded)
    }

    @Synchronized
    fun putDecodedByTransferId(wireId: String, bytes: ByteArray): String {
        require(wireId.matches(Regex("rmt1:[0-9a-f]{64}")) && bytes.isNotEmpty() && bytes.size.toLong() <= MAX_PREPARED_BYTES) {
            "MANGA_DECODED_RESOURCE"
        }
        val directory = findDirectory(wireId) { true }
        val staged = File(directory, ".decoded-${UUID.randomUUID()}")
        FileOutputStream(staged).use { output -> output.write(bytes); output.fd.sync() }
        val decoded = File(directory, DECODED)
        check(staged.renameTo(decoded)) { "MANGA_DECODED_COMMIT" }
        return sha256(decoded)
    }

    @Synchronized
    fun readDecodedByTransferId(wireId: String): ByteArray {
        val directory = findDirectory(wireId) { true }
        val decoded = File(directory, DECODED)
        check(decoded.isFile && decoded.length() in 1..MAX_PREPARED_BYTES) { "MANGA_DECODED_ABSENT" }
        return decoded.readBytes()
    }

    @Synchronized
    fun evict(identity: ReaderMangaTransferIdentity, sha256: String): Boolean {
        val directory = File(root, key(identity, sha256))
        return !directory.exists() || directory.deleteRecursively()
    }

    private fun cleanupAbandonedStages() {
        root.listFiles().orEmpty().filter { it.name.startsWith(".stage-") }.forEach { it.deleteRecursively() }
    }

    private fun findDirectory(wireId: String, predicate: (Meta) -> Boolean): File =
        root.listFiles().orEmpty().firstOrNull { candidate ->
            !candidate.name.startsWith(".") && candidate.isDirectory && runCatching {
                val meta = readMeta(candidate)
                meta.first == wireId && predicate(meta)
            }.getOrDefault(false)
        } ?: error("MANGA_RESOURCE_ABSENT")

    private fun key(identity: ReaderMangaTransferIdentity, sha256: String): String =
        identity.wireId.removePrefix("rmt1:") + "-" + sha256

    private fun readMeta(directory: File): Meta {
        check(directory.isDirectory) { "MANGA_RESOURCE_ABSENT" }
        val values = File(directory, META).readLines(Charsets.UTF_8)
        check(values.size >= 5) { "MANGA_RESOURCE_META" }
        return Meta(values[0], values[1], values[2].toLongOrNull() ?: error("MANGA_RESOURCE_META"), values[3], values[4])
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Meta(val first: String, val second: String, val third: Long, val fourth: String, val fifth: String)

    companion object {
        private const val ENCODED = "encoded.bin"
        private const val META = "meta.txt"
        private const val DECODED = "decoded.bin"
        private const val MAX_CHUNK_BYTES = 1024 * 1024
        private const val MAX_BYTES = 16L * 1024 * 1024
        private const val MAX_PREPARED_BYTES = 16L * 1024 * 1024
    }
}
