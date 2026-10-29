package io.reader.android.runtime.ebookdraft

import android.content.Context
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import android.webkit.MimeTypeMap
import io.reader.android.runtime.ReaderEbookAssetBudget
import io.reader.android.runtime.OriginalSourcePublisher
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Caller has already obtained sourceBytes from the retained original/import authority. */
internal class AuthorizedEbookOriginal private constructor(
    private val file: File, val bookId: String, private val size: Long, private val stamp: Stamp,
) {
    internal val authorizedArchivePath: String get() { requireUnchanged(); return file.absolutePath }
    fun requireUnchanged() { check(inspect(file, size) == stamp) { "PRIVATE_ORIGINAL_CHANGED" } }

    private data class Stamp(val device: Long, val inode: Long, val bytes: Long, val modified: Long, val changed: Long)
    companion object {
        fun authorize(context: Context, bookId: String, sourceBytes: Long): AuthorizedEbookOriginal {
            require(bookId.matches(Regex("local:[a-f0-9]{64}")) && sourceBytes in 1..64L * 1024 * 1024)
            // No picker path, message path, cache path or arbitrary archive argument is accepted.
            val root = File(context.applicationContext.filesDir.canonicalFile, "Reader/Originals")
            check(root.absoluteFile == root.canonicalFile && OsConstants.S_ISDIR(Os.lstat(root.path).st_mode))
            val hash = bookId.removePrefix("local:")
            val file = File(root, "$hash.source")
            check(file.absoluteFile == file.canonicalFile)
            val before = inspect(file, sourceBytes)
            OriginalSourcePublisher.verify(file, hash, sourceBytes) // Existing SHA-256/private-original authority.
            check(inspect(file, sourceBytes) == before)
            return AuthorizedEbookOriginal(file, bookId, sourceBytes, before)
        }
        private fun inspect(file: File, size: Long): Stamp {
            val stat = Os.lstat(file.path)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_size == size)
            return Stamp(stat.st_dev, stat.st_ino, stat.st_size, stat.st_mtime, stat.st_ctime)
        }
    }
}

/**
 * Separate binary-asset status domain; exceptions from Core are preserved. No poll/runtime is created.
 * Future App-owned adapter must retain each admitted synchronous call through its real completion.
 */
internal fun interface EbookBinaryAssets {
    fun read(authorizedArchivePath: String, canonicalEntry: String, maximumBytes: Long): ByteArray
    fun retire() {}
}

internal data class EbookResource(val bytes: ByteArray, val mime: String)

/** Read-only resource lifetime is narrower than the same immutable App/client/reader lease. */
internal class ReaderEbookAssetProvider(
    val lease: EbookRenderLease, private val original: AuthorizedEbookOriginal,
    private val guard: EbookLeaseGuard, private val binary: EbookBinaryAssets,
) {
    private val retired = AtomicBoolean(false)
    private val readers = Semaphore(1, true) // JDK bounded concurrency; no archive/cache/parser implementation.
    private val waiting = AtomicInteger()
    private val budget = ReaderEbookAssetBudget()

    init { require(original.bookId == lease.bookId) }

    fun retire() { retired.set(true); binary.retire() } // Retire the exact render permit, not another runtime.

    fun readCanonicalEntry(entry: String): EbookResource {
        requireCurrent()
        requireCanonicalEbookEntry(entry)
        val mime = mime(entry)
        val maximum = if (mime.startsWith("image/")) 8L * 1024 * 1024 else 32L * 1024 * 1024
        budget.attempted()
        check(waiting.incrementAndGet() <= 8) { waiting.decrementAndGet(); "RESOURCE_WAIT_LIMIT" }
        var acquired = false
        try {
            acquired = readers.tryAcquire(5, TimeUnit.SECONDS)
            check(acquired) { "RESOURCE_WAIT_DEADLINE" }
            requireCurrent()
            original.requireUnchanged()
            // Admission precedes native decompression/allocation. The serial permit
            // keeps this remaining allowance stable until the read is accounted.
            val nativeMaximum = budget.nativeMaximum(maximum)
            val bytes = binary.read(original.authorizedArchivePath, entry, nativeMaximum)
            // Retirement or changed original discards output, not the still-running native call.
            original.requireUnchanged()
            requireCurrent()
            check(bytes.size.toLong() <= nativeMaximum)
            budget.returned(bytes.size.toLong(), nativeMaximum)
            // Fixed Core rejects empty archive entries. Do not turn asset status 8 into success here.
            return EbookResource(bytes, mime)
        } finally {
            waiting.decrementAndGet()
            if (acquired) readers.release()
        }
    }

    /** Parse a request URL once. AndroidX PathHandler suffix must not be decoded again. */
    fun canonicalEntry(url: Uri, method: String): String {
        requireCurrent()
        require(method == "GET" && url.scheme == "https" && url.encodedAuthority == ORIGIN_HOST
            && url.encodedQuery == null && url.encodedFragment == null)
        val prefix = "/publication/${lease.renderId}/"
        val encoded = url.encodedPath ?: error("PUBLICATION_PATH_REQUIRED")
        require(encoded.startsWith(prefix)) { "FOREIGN_RESOURCE_LEASE" }
        return Uri.decode(encoded.removePrefix(prefix)).also(::requireCanonicalEbookEntry)
    }

    private fun requireCurrent() {
        check(!retired.get()) { "RETIRED_EBOOK_RESOURCE_LEASE" }
        guard.requireCurrent(lease)
    }

    companion object {
        const val ORIGIN_HOST = "reader-ebook.invalid"
        const val ORIGIN = "https://$ORIGIN_HOST"
        private fun mime(entry: String): String = when (entry.substringAfterLast('.', "").lowercase()) {
            "xhtml" -> "application/xhtml+xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "svg" -> "image/svg+xml"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "otf" -> "font/otf"
            "ttf" -> "font/ttf"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(entry.substringAfterLast('.', "").lowercase())
                ?: "application/octet-stream"
        }
    }
}
