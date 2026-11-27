package io.reader.rebuild

import com.reader.core.ReaderStorageProbe
import com.reader.core.protocol.*
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.HostCapabilities
import io.reader.rebuild.core.PageObservation
import io.reader.rebuild.features.bookshelf.LocalImportCoordinator
import io.reader.rebuild.features.reading.ReadingSessionCoordinator
import io.reader.rebuild.host.storage.AuthorizedDocument
import io.reader.rebuild.host.storage.FileAndAssetHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val CORE_PIN = "d81f396dbee8a2a8f12c4c03fced063d701c8e33"
private val hostFixtures get() = File(requireNotNull(System.getProperty("reader.hostFixtures")))
private val epubFixture get() = File(requireNotNull(System.getProperty("reader.fixture")))
private val txtFixture get() = File(hostFixtures, "local-reading.txt")
private fun source(file: File) = AuthorizedDocument(file.name, file::inputStream)

// The Android compile bootclasspath excludes java.lang.ProcessHandle. These tests
// are host-only and pin the observed PID of the current JDK process via reflection.
private fun processId(): Long {
    val process = Class.forName("java.lang.ProcessHandle")
    return process.getMethod("pid").invoke(process.getMethod("current").invoke(null)) as Long
}

@Serializable
private data class Lifecycle(
    val localSourceId: String, val otherSourceId: String, val otherChapterIndex: Long, val otherScalar: Long,
    val hostSupported: String, val hostOmitted: String, val unavailableDiagnostic: String,
    val stalePositionReason: String, val staleProgressReason: String, val imageScalarWidth: Long,
)

@Serializable
private data class ScalarSelection(val prefix: String, val selectedText: String,
    val startScalar: Int, val endScalar: Int, val startUtf16: Int, val endUtf16: Int)

@Serializable
private data class ColdProof(val book: String, val original: String, val scalar: Long, val chapter: Long,
    val layout: String, val identity: String)

class NativeCoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun idleCloseWakesNativePollAndBothCallers() = runBlocking {
        val errors = ConcurrentLinkedQueue<Throwable>()
        val library = LibraryRuntime.open(File(temp.newFolder(), "library"), SystemSurfaceHost(), errors::add)
        try {
            // Let the consumer return to its idle poll after the last RPC.
            delay(50)
            val started = System.nanoTime()
            coroutineScope { awaitAll(async { library.close() }, async { library.close() }) }
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            check(errors.isEmpty()) { errors.toString() }
            println("Idle close with two callers: ${elapsed}ms")
            check(elapsed < 1_000) { "Idle close waited ${elapsed}ms for the native poll" }
        } finally {
            library.close()
        }
    }

    @Test fun freshImportConflictAndColdRead() = runBlocking {
        val home = temp.newFolder("fresh")
        val db = File(home, "library")
        val files = FileAndAssetHost(home)
        check(ReaderStorageProbe.read(db.absolutePath).observation.freshStorage)
        val errors = ConcurrentLinkedQueue<Throwable>()
        val library = LibraryRuntime.open(db, SystemSurfaceHost(), errors::add)
        val layout = requireNotNull(library.admitted.observation.layout)
        val cases = ReaderProtocolJson.decodeFromString<Lifecycle>(File(hostFixtures, "reading-lifecycle.json").readText())
        val scalarFixture = ReaderProtocolJson.decodeFromString<ScalarSelection>(File(hostFixtures, "scalar-selection.json").readText())
        val proof = try {
            check(layout.layoutId == "reader-sqlite-47-remote")
            verifyCapabilities(library, 47)
            val imports = LocalImportCoordinator(library.core, files)
            val receipt = imports.import(source(txtFixture))
            check(receipt.created && receipt.sourceId == cases.localSourceId)
            val original = FileAndAssetHost.sha256(txtFixture)
            check(FileAndAssetHost.sha256(files.authorize(original).file) == original)
            check(!imports.import(source(txtFixture)).created)
            check(files.journals.listFiles().orEmpty().isEmpty())
            val coordinator = ReadingSessionCoordinator(library.core, files)
            val observed = coordinator.text(receipt.bookId, 0)
            val chapter = observed.content.content
            val position = chapter.indexOf(scalarFixture.prefix + scalarFixture.selectedText)
            check(position >= 0)
            val start = position + scalarFixture.startUtf16
            val end = position + scalarFixture.endUtf16
            check(chapter.substring(start, end) == scalarFixture.selectedText)
            check(chapter.codePointCount(position, start) == scalarFixture.startScalar)
            check(chapter.codePointCount(position, end) == scalarFixture.endScalar)
            val scalar = chapter.codePointCount(0, start).toLong()
            check(scalar > 0 && chapter.offsetByCodePoints(0, scalar.toInt()) == start)
            val oldPage = AtomicInteger()
            val observer = PageObservation<ReadingProgressUpdateData> { oldPage.incrementAndGet() }
            val fence = ProcessBuilder("python3", File(hostFixtures, "hold_storage_write.py").absolutePath,
                File(db, "reader-core.sqlite3").absolutePath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            val save = try {
                check(fence.inputStream.bufferedReader().readLine() == "locked")
                val operation = coordinator.save(observed, scalar, observer)
                observer.retire()
                check(withTimeoutOrNull(100) { operation.await() } == null)
                fence.outputStream.use { it.write('\n'.code) }
                check(fence.waitFor(5, TimeUnit.SECONDS) && fence.exitValue() == 0)
                operation
            } finally {
                if (fence.isAlive) fence.destroyForcibly()
            }
            val terminal = withTimeout(5_000) { save.await() }
            check(terminal.stored && terminal.chapterOffset == scalar && oldPage.get() == 0)
            val status = library.core.call(ReaderM1.runtimeStatus, RuntimeStatusParams(includeCapacity = true))
            check(status.capacity?.current?.pendingDeliveryCount == 0L)
            check(library.core.call(ReaderM1.coreInfo, EmptyParams()).sqliteUserVersion == 47L)
            val base = TextReadingProgressUpdateParams(bookId = receipt.bookId, sourceId = cases.localSourceId,
                chapterIndex = 0, chapterOffset = scalar + 1, chapterProgress = 0.3,
                expectedProgressRevision = requireNotNull(terminal.progressRevision),
                expectedBodyVersion = requireNotNull(observed.content.bodyVersion),
                expectedProcessingVersion = requireNotNull(observed.content.processingVersion))
            for ((attempt, reason) in listOf(
                base.copy(expectedBodyVersion = "stale-body") to cases.stalePositionReason,
                base.copy(expectedProcessingVersion = "stale-processing") to cases.stalePositionReason,
                base.copy(expectedProgressRevision = observed.revision) to cases.staleProgressReason,
            )) {
                val failure = reject { library.core.call(ReaderM1.readingProgressUpdate, attempt) }
                check(failure.code == ReaderErrorCode.INVALID_PARAMS && failure.details?.reason == reason)
            }
            val other = library.core.call(ReaderM1.readingProgressUpdate, TextReadingProgressUpdateParams(
                bookId = receipt.bookId, sourceId = cases.otherSourceId, chapterIndex = cases.otherChapterIndex,
                chapterOffset = cases.otherScalar, chapterProgress = 0.5,
            ))
            check(other.stored && other.sourceId == cases.otherSourceId && other.chapterOffset == cases.otherScalar)
            val otherReadback = library.core.call(ReaderM1.readingProgressGet,
                TextReadingProgressGetParams(receipt.bookId, cases.otherSourceId)).progress
            check(otherReadback?.sourceId == cases.otherSourceId && otherReadback.chapterIndex == cases.otherChapterIndex &&
                otherReadback.chapterOffset == cases.otherScalar)
            val untouched = coordinator.text(receipt.bookId, 0)
            check(untouched.savedScalar == scalar)
            check(errors.isEmpty()) { errors.toString() }
            println("PASS fresh Library47 variant=${layout.layoutId} source=local scalar=$scalar second=${other.chapterOffset} " +
                "conflicts=3 write=${save.identity} stalePage=${oldPage.get()} pendingDelivery=0 duplicate=preserved")
            ColdProof(receipt.bookId, original, scalar, 0, layout.layoutId, requireNotNull(layout.databaseIdentity))
        } finally {
            library.close()
        }
        processCold(home, db, proof)
    }

    @Test fun existing47FixtureAdmittedAndPreserved() = runBlocking {
        val home = temp.newFolder("existing")
        val db = File(home, "library").apply { mkdirs() }
        val python = """
            import sqlite3, sys
            with sqlite3.connect(sys.argv[1]) as db:
                db.executescript(b''.join(open(part, 'rb').read() for part in sys.argv[2:]).decode('utf-8'))
                db.execute('INSERT INTO library_identity_meta(id,library_id) VALUES(1,?)', ('1'*32,))
                db.execute('INSERT INTO library_identity_authority(id,restore_nonce) VALUES(1,?)', ('2'*32,))
        """.trimIndent()
        val seed = ProcessBuilder("python3", "-c", python, File(db, "reader-core.sqlite3").absolutePath,
            File(hostFixtures, "library47-schema.1.sql").absolutePath,
            File(hostFixtures, "library47-schema.2.sql").absolutePath).inheritIO().start()
        check(seed.waitFor(10, TimeUnit.SECONDS) && seed.exitValue() == 0)
        val before = ReaderStorageProbe.read(db.absolutePath).observation
        val layout = requireNotNull(before.layout)
        check(!before.freshStorage && layout.schemaVersion == 47u && layout.databaseIdentity?.length == 64)
        val old = CoreRuntimeActor.open(ReaderRuntimeConfig(dataDirectory = db.absolutePath), SystemSurfaceHost()) { throw it }
        val files = FileAndAssetHost(home)
        val proof = try {
            val shelf = LocalImportCoordinator(old, files).import(source(txtFixture))
            val reader = ReadingSessionCoordinator(old, files)
            val saved = reader.save(reader.text(shelf.bookId, 0), 5).await()
            check(saved.stored)
            ColdProof(shelf.bookId, FileAndAssetHost.sha256(txtFixture), 5, 0, layout.layoutId, requireNotNull(layout.databaseIdentity))
        } finally { old.close() }
        processCold(home, db, proof)
    }

    @Test fun hostCapabilitiesAndReverseTerminals() = runBlocking {
        val nativeHost = SystemSurfaceHost(Clock.fixed(Instant.ofEpochMilli(1), ZoneOffset.UTC))
        val calls = ConcurrentLinkedQueue<ReaderHostCapability>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val host = object : HostCapabilities by nativeHost {
            override suspend fun reply(capability: ReaderHostCapability, request: String, replyId: Long): String {
                calls.add(capability)
                entered.complete(Unit)
                release.await()
                return nativeHost.reply(capability, request, replyId)
            }
        }
        val errors = ConcurrentLinkedQueue<Throwable>()
        val library = LibraryRuntime.open(File(temp.newFolder(), "library"), host, errors::add)
        try {
            val earlier = library.core.submit(ReaderM1.runtimeHostSmokeTimeNow, M1TimeNowSmokeParams(params = HostTimeNowRequest()))
            withTimeout(5_000) { entered.await() }
            val later = library.core.submit(ReaderM1.coreInfo, EmptyParams())
            check(withTimeout(5_000) { later.await() }.buildIdentity.gitCommit == CORE_PIN)
            check(earlier.identity.requestId < later.identity.requestId)
            check(withTimeoutOrNull(100) { earlier.await() } == null)
            release.complete(Unit)
            check(withTimeout(5_000) { earlier.await() }.unixMillis == 1L)
            val rejected = reject { library.core.call(ReaderM1.runtimeHostSmokeCookieGet,
                M1CookieGetSmokeParams(params = HostCookieGetRequest(url = "https://example.invalid/"))) }
            check(rejected.code == ReaderErrorCode.INTERNAL)
            check(rejected.details?.host?.diagnostics?.code == HostErrorCode.CAPABILITY_UNAVAILABLE)
            check(calls.toList() == listOf(ReaderHostCapability.TIME_NOW))
            check(errors.isEmpty()) { errors.toString() }
            println("PASS host positive/time.now negative/INTERNAL/CAPABILITY_UNAVAILABLE omitted calls=0; " +
                "terminal order=${later.identity.requestId},${earlier.identity.requestId}")
        } finally {
            release.complete(Unit)
            library.close()
        }
    }

    @Test fun ebookProjectionAndAuthorizedAssetThroughProductionBridge() = runBlocking {
        val home = temp.newFolder()
        val files = FileAndAssetHost(home)
        val library = LibraryRuntime.open(File(home, "library"), SystemSurfaceHost()) { throw it }
        try {
            check(FileAndAssetHost.sha256(epubFixture) == "35efc3246026578c8f56ea227964bb05a5d7cd8d49ee0f7794221f9ab3a4481e")
            val receipt = LocalImportCoordinator(library.core, files).import(source(epubFixture))
            val ebook = ReadingSessionCoordinator(library.core, files).ebook(receipt.bookId, 1)
            check(ebook.descriptor.chapterStartScalar == 65L && ebook.descriptor.chapterEndScalar == 962L)
            val image = ebook.text.content.blocks.orEmpty().filterIsInstance<ReadingDocumentBlockDataImage>().single()
            check(image.endScalar - image.startScalar == 1L)
            check(ebook.xhtml.contains("𠀀与Café"))
            check(ebook.archive.read("EPUB/images/path.png", 512_000).take(4).map { it.toInt() and 0xff } == listOf(137, 80, 78, 71))
            check(runCatching { ebook.archive.read("../path.png", 512_000) }.isFailure)
            println("PASS EPUB fixture, Core same-XHTML chapter [65,962), authorized bounded image bytes; WebView separately instrumented")
        } finally { library.close() }
    }

    private fun processCold(home: File, db: File, proof: ColdProof) {
        val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Xcheck:jni", "-Djava.library.path=${System.getProperty("java.library.path")}",
            "-cp", requireNotNull(System.getProperty("reader.cold.classpath")), ColdReader::class.java.name,
            home.absolutePath, db.absolutePath, ReaderProtocolJson.encodeToString(proof),
            processId().toString()).inheritIO().start()
        check(process.waitFor(25, TimeUnit.SECONDS) && process.exitValue() == 0)
    }

    private suspend fun reject(block: suspend () -> Unit): ReaderM1Error {
        try { block() } catch (failure: CoreRejected) { return failure.detail }
        error("Core accepted stale or unavailable input")
    }
}

private fun verifyCapabilities(library: LibraryRuntime, version: Long) {
    check(library.info.buildIdentity.gitCommit == CORE_PIN && !library.info.buildIdentity.gitDirty)
    val storage = requireNotNull(library.info.storageFormat)
    val flags = requireNotNull(storage.capabilities)
    check(library.info.sqliteUserVersion == version && storage.format == CoreStorageFormatInfoFormat.LIBRARY47)
    check(flags.physicalBackup && flags.physicalRestore && flags.logicalRestoreRequiresCompleteCoverage)
    check(flags.activeLibrarySync == library.admitted.observation.layout!!.syncLogReceipts)
}

internal object ColdReader {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        check(processId().toString() != args[3])
        val proof = ReaderProtocolJson.decodeFromString<ColdProof>(args[2])
        val library = LibraryRuntime.open(File(args[1]), SystemSurfaceHost()) { throw it }
        try {
            verifyCapabilities(library, 47)
            val layout = requireNotNull(library.observed.observation.layout)
            check(!library.observed.observation.freshStorage && layout.layoutId == proof.layout && layout.databaseIdentity == proof.identity)
            val files = FileAndAssetHost(File(args[0]))
            val reader = ReadingSessionCoordinator(library.core, files)
            val restored = reader.text(proof.book, proof.chapter)
            check(restored.savedScalar == proof.scalar)
            val shelf = library.core.call(ReaderM1.bookshelfGet, BookshelfGetParams("local", proof.book))
            check(shelf.book?.bookId == proof.book && shelf.original?.sha256 == proof.original)
            check(FileAndAssetHost.sha256(files.authorize(proof.original).file) == proof.original)
            println("PASS independent JVM pid=${processId()} reopening layout=${proof.layout} original=${proof.original} scalar=${proof.scalar}")
        } finally { library.close() }
    }
}
