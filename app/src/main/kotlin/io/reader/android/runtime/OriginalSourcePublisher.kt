package io.reader.android.runtime

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.DigestInputStream
import java.security.MessageDigest

/** Host-only publication of an exact retained original; Core still owns the import transaction. */
internal class OriginalSourcePublisher(private val originals: File) {
    fun publish(staged: File, hash: String, size: Long) {
        expected(hash, size)
        operation("originals.mkdir") { check(originals.mkdirs() || originals.isDirectory) }
        operation("originals.lstat") {
            check(OsConstants.S_ISDIR(Os.lstat(originals.absolutePath).st_mode)) { "ORIGINAL_DIRECTORY_REQUIRED" }
        }
        // Never unlink the lock file: all publisher instances/processes must
        // retain the same lock inode, including after a crash or a failed copy.
        val lockFile = File(originals, ".original-source-publisher.lock")
        operation("publication.owner") {
            operation("publication.lock.open") { RandomAccessFile(lockFile, "rw") }.use { owner ->
                val lock = operation("publication.lock.tryLock") {
                    owner.channel.tryLock() ?: throw IOException("SOURCE_PUBLICATION_BUSY")
                }
                operation("publication.lock.release") {
                    lock.use { publishLocked(staged, File(originals, "$hash.source"), hash, size) }
                }
            }
        }
    }

    private fun publishLocked(staged: File, target: File, hash: String, size: Long) {
        if (existsNoFollow(target)) {
            verifyNamed(target, hash, size, "target")
            return
        }
        inspect(staged, size, "source")
        val atomic = AtomicFile(target)
        var output: FileOutputStream? = null
        var finishAttempted = false
        try {
            val stream = operation("candidate.startWrite") { atomic.startWrite() }
            output = stream
            val digest = MessageDigest.getInstance("SHA-256")
            val copied = operation("source.copy") {
                DigestInputStream(FileInputStream(staged), digest).use { it.copyTo(stream) }
            }
            operation("candidate.identity") {
                check(copied == size && hex(digest.digest()) == hash) { "ORIGINAL_SOURCE_HASH_OR_SIZE" }
            }
            operation("candidate.fsync") { stream.fd.sync() }
            // AtomicFile replaces its base on finishWrite. Recheck under the
            // publication lock so an existing original is never overwritten.
            if (existsNoFollow(target)) {
                verifyNamed(target, hash, size, "target")
                operation("candidate.discard") { atomic.failWrite(stream) }
                output = null
                syncOriginals()
                return
            }
            // AndroidX finishWrite logs some failures rather than throwing.
            // Its return is not proof: verify the formal file and fsync its dir.
            // From this point no failure may call failWrite on a committed file.
            finishAttempted = true
            operation("candidate.finishWrite") { atomic.finishWrite(stream) }
            output = null
            verifyNamed(target, hash, size, "target")
            syncOriginals()
        } catch (failure: Throwable) {
            if (!finishAttempted && output != null) {
                try { operation("candidate.failWrite") { atomic.failWrite(output) } }
                catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            }
            throw failure
        }
        // The staged original remains owned by its existing import journal;
        // only that owner may retire it after the original Core settle ACK.
    }

    private fun existsNoFollow(file: File): Boolean = operation("target.lstat") {
        try { Os.lstat(file.absolutePath); true }
        catch (error: ErrnoException) { if (error.errno == OsConstants.ENOENT) false else throw error }
    }

    private fun syncOriginals() {
        val descriptor = operation("originals.directory.open") {
            Os.open(originals.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        }
        var primary: Throwable? = null
        try {
            operation("originals.directory.fstat") {
                check(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) { "ORIGINAL_DIRECTORY_REQUIRED" }
            }
            operation("originals.directory.fsync") { Os.fsync(descriptor) }
        }
        catch (error: Throwable) { primary = error; throw error }
        finally {
            try { operation("originals.directory.close") { Os.close(descriptor) } }
            catch (close: Throwable) { if (primary == null) throw close else primary.addSuppressed(close) }
        }
    }

    /** Safe diagnostic fields only; the cause never enters user-visible messages. */
    internal class Failure(val operation: String, cause: Exception) : IOException("SOURCE_PUBLICATION_FAILED: $operation", cause) {
        private val root = rootCause(cause)
        val rootClass: String = root.javaClass.simpleName
        val errno: Int? = generateSequence<Throwable>(cause) { it.cause }
            .filterIsInstance<ErrnoException>().firstOrNull()?.errno
    }

    companion object {
        fun verify(file: File, hash: String, size: Long) = verifyNamed(file, hash, size, "source")

        private fun expected(hash: String, size: Long) {
            require(hash.matches(Regex("[a-f0-9]{64}")) && size >= 0) { "INVALID_ORIGINAL_DESCRIPTOR" }
        }

        private fun inspect(file: File, size: Long, role: String) = operation("$role.lstat") {
            val stat = Os.lstat(file.absolutePath)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_size == size) { "ORIGINAL_SOURCE_IDENTITY" }
        }

        private fun verifyNamed(file: File, hash: String, size: Long, role: String) {
            expected(hash, size)
            inspect(file, size, role)
            val digest = MessageDigest.getInstance("SHA-256")
            operation("$role.read") {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) digest.update(buffer, 0, count)
                    }
                }
            }
            operation("$role.identity") { check(hex(digest.digest()) == hash) { "ORIGINAL_SOURCE_HASH" } }
        }

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun rootCause(error: Throwable): Throwable {
            var root = error
            while (root.cause != null && root.cause !== root) root = root.cause!!
            return root
        }

        private inline fun <T> operation(name: String, action: () -> T): T = try { action() }
        catch (error: Exception) { throw if (error is Failure) error else Failure(name, error) }
    }
}
