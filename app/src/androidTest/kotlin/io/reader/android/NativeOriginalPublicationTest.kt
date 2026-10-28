package io.reader.android

import android.os.Bundle
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileDescriptor
import java.util.UUID

/** Selector: io.reader.android.NativeOriginalPublicationTest#nativePrivateOriginalLinkAndDirectorySync
 * Filesystem probe only: no Activity, Core, picker, book or import transaction.
 */
@RunWith(AndroidJUnit4::class)
class NativeOriginalPublicationTest {
    @Test(timeout = 20_000)
    fun nativePrivateOriginalLinkAndDirectorySync() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "io.reader.minliny") { "Wrong target package: ${context.packageName}" }
        check(context.applicationInfo.uid == Process.myUid()) { "UID ${Process.myUid()} differs from target ${context.applicationInfo.uid}" }
        val staging = File(context.filesDir, "Reader/ImportStaging")
        val originals = File(context.filesDir, "Reader/Originals")
        check(staging.isDirectory && originals.isDirectory) { "Actual production directories must already exist" }
        val name = ".probe-${UUID.randomUUID()}"
        val source = File(staging, name)
        val target = File(originals, name)
        val bytes = byteArrayOf(0x52, 0x44, 0x52, 0x00)
        var descriptor: FileDescriptor? = null
        var identity: StructStat? = null
        var sourceCreated = false
        var targetCreated = false
        var written = 0
        var primary: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (error: Throwable) {
                if (primary == null) primary = error else primary!!.addSuppressed(error)
            }
        }
        mark("method-enter", "BEGIN")
        try {
            operation("source.open") {
                descriptor = Os.open(source.absolutePath,
                    OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_WRONLY or OsConstants.O_CLOEXEC, 0x180)
                sourceCreated = true
            }
            // Bind ownership before a write/fsync failure can require cleanup.
            operation("source.fstat") { identity = Os.fstat(requireNotNull(descriptor)) }
            operation("source.write") {
                written = Os.write(requireNotNull(descriptor), bytes, 0, bytes.size)
                check(written == bytes.size) { "Partial probe write: $written" }
            }
            operation("source.fsync") { Os.fsync(requireNotNull(descriptor)) }
            val closing = requireNotNull(descriptor)
            descriptor = null // Never retry an ambiguous native close.
            operation("source.close") { Os.close(closing) }
            operation("source.lstat") { sameIdentity(Os.lstat(source.absolutePath), requireNotNull(identity), written) }
            operation("target.link") { Os.link(source.absolutePath, target.absolutePath); targetCreated = true }
            operation("target.lstat") { sameIdentity(Os.lstat(target.absolutePath), requireNotNull(identity), written) }
            syncDirectory(originals, "originals")
        } catch (error: Throwable) {
            primary = error
        } finally {
            descriptor?.let { owned -> descriptor = null; cleanup { operation("cleanup.source.close") { Os.close(owned) } } }
            for ((path, created) in listOf(target to targetCreated, source to sourceCreated)) {
                if (created) cleanup {
                    val label = if (path == target) "target" else "source"
                    operation("cleanup.$label.lstat") {
                        sameIdentity(Os.lstat(path.absolutePath), requireNotNull(identity) { "Probe ownership unavailable; retain file" }, written)
                    }
                    operation("cleanup.$label.unlink") { Os.unlink(path.absolutePath) }
                }
            }
            if (targetCreated) cleanup { syncDirectory(originals, "cleanup.originals") }
            if (sourceCreated) cleanup { syncDirectory(staging, "cleanup.staging") }
        }
        primary?.let { throw it }
        mark("method-exit", "PASS")
    }

    private fun sameIdentity(actual: StructStat, owned: StructStat, expectedBytes: Int) {
        check(OsConstants.S_ISREG(actual.st_mode) && actual.st_dev == owned.st_dev &&
            actual.st_ino == owned.st_ino && actual.st_size == expectedBytes.toLong()) { "Probe identity changed; do not unlink" }
    }

    private fun syncDirectory(directory: File, label: String) {
        var descriptor: FileDescriptor? = null
        var primary: Throwable? = null
        try {
            operation("$label.open") { descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0) }
            operation("$label.fstat") { check(OsConstants.S_ISDIR(Os.fstat(requireNotNull(descriptor)).st_mode)) }
            operation("$label.fsync") { Os.fsync(requireNotNull(descriptor)) }
        } catch (error: Throwable) { primary = error }
        descriptor?.let { owned ->
            try { operation("$label.close") { Os.close(owned) } } catch (error: Throwable) {
                if (primary == null) primary = error else primary!!.addSuppressed(error)
            }
        }
        primary?.let { throw it }
    }

    private fun <T> operation(name: String, action: () -> T): T {
        mark(name, "BEGIN")
        try { return action().also { mark(name, "PASS") } } catch (error: Throwable) {
            try { mark(name, "FAIL", error) } catch (markerError: Throwable) { error.addSuppressed(markerError) }
            throw error
        }
    }

    private fun mark(operation: String, status: String, error: Throwable? = null) {
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("readerMethod", "nativePrivateOriginalLinkAndDirectorySync")
            putString("readerStep", operation); putString("operation", operation); putString("status", status)
            putInt("uid", Process.myUid())
            putInt("errno", (error as? ErrnoException)?.errno ?: 0)
            error?.let { putString("exception", it.javaClass.name) }
        })
    }
}
