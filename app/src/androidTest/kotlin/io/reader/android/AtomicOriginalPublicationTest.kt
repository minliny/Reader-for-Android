package io.reader.android

import android.os.Bundle
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reader.android.runtime.OriginalSourcePublisher
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Selector: io.reader.android.AtomicOriginalPublicationTest#nativeAtomicPublicationPreservesStagedAndExistingOriginals
 * Production publisher with four-byte filesystem fixtures only. No Activity/Core/SAF/import.
 */
@RunWith(AndroidJUnit4::class)
class AtomicOriginalPublicationTest {
    @Test(timeout = 30_000)
    fun nativeAtomicPublicationPreservesStagedAndExistingOriginals() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "io.reader.minliny" && context.applicationInfo.uid == Process.myUid()) { "Exact target App UID is required" }
        val staging = File(context.filesDir, "Reader/ImportStaging")
        val originals = File(context.filesDir, "Reader/Originals")
        check(staging.isDirectory && originals.isDirectory)
        val token = ".probe-${UUID.randomUUID()}"
        val source = File(staging, token)
        val root = File(originals, token)
        val owned = linkedMapOf<File, StructStat>()
        val bytes = byteArrayOf(0x52, 0x00, 0x7f, 0x01)
        val other = byteArrayOf(0x13, 0x37, 0x00, 0x02)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        var primary: Throwable? = null
        fun remember(file: File) { owned.putIfAbsent(file, Os.lstat(file.absolutePath)) }
        fun directory(file: File) { Os.mkdir(file.absolutePath, 0x1c0); remember(file) }
        fun exclusive(file: File, content: ByteArray) {
            val descriptor = Os.open(file.absolutePath, OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY or OsConstants.O_CLOEXEC, 0x180)
            owned[file] = Os.fstat(descriptor)
            FileOutputStream(descriptor).use { it.write(content); it.fd.sync() }
            owned[file] = Os.lstat(file.absolutePath)
        }
        fun capturePublished(directory: File, expectedHash: String) {
            val allowed = setOf("$expectedHash.source", "$expectedHash.source.new", ".original-source-publisher.lock")
            for (file in directory.listFiles() ?: error("Fixture directory became unreadable")) {
                check(file.name in allowed) { "Unexpected fixture output: ${file.name}" }
                remember(file)
            }
        }
        fun publish(directory: File, expectedHash: String) {
            var failure: Throwable? = null
            try { OriginalSourcePublisher(directory).publish(source, expectedHash, bytes.size.toLong()) }
            catch (error: Throwable) { failure = error }
            try { capturePublished(directory, expectedHash) } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            failure?.let { throw it }
        }
        fun reject(directory: File, expectedHash: String, phase: String, expectedOperation: String) {
            var rejection: Exception? = null
            try { publish(directory, expectedHash) } catch (error: Exception) { rejection = error }
            val failure = checkNotNull(rejection) { "$phase unexpectedly accepted" }
            check(failure is OriginalSourcePublisher.Failure && failure.operation == expectedOperation &&
                failure.rootClass == "IllegalStateException") { "$phase failed for an unexpected cause: $failure" }
            marker(phase, failure)
        }
        marker("method-enter")
        try {
            directory(root)
            val success = File(root, "success").also(::directory)
            val collision = File(root, "collision").also(::directory)
            val wrongHash = File(root, "wrong-hash").also(::directory)
            exclusive(source, bytes)
            marker("publish-success-begin")
            publish(success, hash)
            val target = File(success, "$hash.source")
            OriginalSourcePublisher.verify(target, hash, bytes.size.toLong())
            check(source.readBytes().contentEquals(bytes) && target.readBytes().contentEquals(bytes))
            marker("publish-success-pass")
            val first = Os.lstat(target.absolutePath)
            marker("publish-idempotent-begin")
            publish(success, hash)
            val second = Os.lstat(target.absolutePath)
            check(first.st_dev == second.st_dev && first.st_ino == second.st_ino) { "Idempotent publication replaced original inode" }
            check(source.readBytes().contentEquals(bytes) && target.readBytes().contentEquals(bytes))
            marker("publish-idempotent-pass")

            val conflicting = File(collision, "$hash.source")
            marker("collision-begin")
            exclusive(conflicting, other)
            reject(collision, hash, "collision-rejected", "target.identity")
            check(source.readBytes().contentEquals(bytes) && conflicting.readBytes().contentEquals(other)) { "Conflict changed source or existing original" }
            val wrong = "0".repeat(64)
            marker("wrong-hash-begin")
            reject(wrongHash, wrong, "wrong-hash-rejected", "candidate.identity")
            check(source.readBytes().contentEquals(bytes))
            check(!File(wrongHash, "$wrong.source").exists() &&
                wrongHash.listFiles()!!.none { it.name.endsWith(".new") }) { "Failed hash left a formal original or .new" }
        } catch (error: Throwable) {
            primary = error
        } finally {
            for ((file, identity) in owned.entries.sortedByDescending { it.key.toPath().nameCount }) {
                try {
                    val actual = Os.lstat(file.absolutePath)
                    val directory = OsConstants.S_ISDIR(identity.st_mode)
                    check(actual.st_dev == identity.st_dev && actual.st_ino == identity.st_ino &&
                        (if (directory) OsConstants.S_ISDIR(actual.st_mode) else OsConstants.S_ISREG(actual.st_mode) && actual.st_size == identity.st_size)) {
                        "Fixture identity changed; retaining ${file.name}"
                    }
                    Os.remove(file.absolutePath)
                } catch (error: Throwable) { if (primary == null) primary = error else primary!!.addSuppressed(error) }
            }
            for (directory in listOf(staging, originals)) {
                try { sync(directory) } catch (error: Throwable) { if (primary == null) primary = error else primary!!.addSuppressed(error) }
            }
        }
        primary?.let { throw it }
        marker("method-exit")
    }

    private fun sync(directory: File) {
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        FileInputStream(descriptor).use {
            check(OsConstants.S_ISDIR(Os.fstat(it.fd).st_mode))
            Os.fsync(it.fd)
        }
    }

    private fun marker(phase: String, rejection: Exception? = null) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("readerMethod", "nativeAtomicPublicationPreservesStagedAndExistingOriginals")
            putString("readerStep", phase); putInt("uid", Process.myUid())
            rejection?.let { putString("rejectionClass", it.javaClass.name) }
            (rejection as? OriginalSourcePublisher.Failure)?.let {
                putString("rejectionOperation", it.operation); putString("rejectionRootClass", it.rootClass)
            }
        })
    }
}
