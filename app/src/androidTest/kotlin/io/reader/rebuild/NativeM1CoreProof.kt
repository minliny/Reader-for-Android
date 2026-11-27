package io.reader.rebuild

import android.os.Process
import com.reader.core.ReaderStorageLayout
import com.reader.core.protocol.CoreStorageFormatInfoFormat
import com.reader.core.protocol.EmptyParams
import com.reader.core.protocol.HostCookieGetRequest
import com.reader.core.protocol.HostErrorCode
import com.reader.core.protocol.HostTimeNowRequest
import com.reader.core.protocol.M1CookieGetSmokeParams
import com.reader.core.protocol.M1TimeNowSmokeParams
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderHostCapability
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM1Error
import com.reader.core.protocol.ReadingProgressUpdateData
import com.reader.core.protocol.RuntimeStatusParams
import com.reader.core.protocol.TextReadingProgressGetParams
import com.reader.core.protocol.TextReadingProgressUpdateParams
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.HostCapabilities
import io.reader.rebuild.core.PageObservation
import io.reader.rebuild.features.reading.ReadingSessionCoordinator
import io.reader.rebuild.features.reading.TextChapter
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal object NativeM1CoreProof {
    const val EPUB_SHA = "35efc3246026578c8f56ea227964bb05a5d7cd8d49ee0f7794221f9ab3a4481e"
    const val CORE_PIN = "fa536a10c31ab5a35df2e2c1c1fbf70ebf3e1be9"
    const val LAYOUT = "reader-sqlite-47-remote"
    const val OTHER_SOURCE = "fixture-second-source"
    const val OTHER_CHAPTER = 3L
    const val OTHER_SCALAR = 19L

    fun storage(runtime: LibraryRuntime, fresh: Boolean): ReaderStorageLayout {
        check(runtime.observed.observation.freshStorage == fresh)
        check(!runtime.admitted.observation.freshStorage && !runtime.admitted.observation.requiresMigration)
        val layout = requireNotNull(runtime.admitted.observation.layout)
        check(layout.layoutId == LAYOUT && layout.schemaVersion == 47u)
        check(requireNotNull(layout.databaseIdentity).length == 64)
        val info = runtime.info
        check(info.sqliteUserVersion == 47L && info.buildIdentity.gitCommit == CORE_PIN && !info.buildIdentity.gitDirty)
        val storage = requireNotNull(info.storageFormat)
        val flags = requireNotNull(storage.capabilities)
        check(storage.format == CoreStorageFormatInfoFormat.LIBRARY47)
        check(flags.physicalBackup && flags.physicalRestore && flags.logicalRestoreRequiresCompleteCoverage)
        check(!flags.activeLibrarySync && !layout.syncLogReceipts)
        println("M1_STORAGE pid=${Process.myPid()} fresh=$fresh layout=${layout.layoutId}" +
            " userVersion=${info.sqliteUserVersion} identity=${layout.databaseIdentity} core=${info.buildIdentity.gitCommit}" +
            " backup=${flags.physicalBackup} restore=${flags.physicalRestore} completeCoverage=${flags.logicalRestoreRequiresCompleteCoverage}" +
            " sync=${flags.activeLibrarySync}")
        return layout
    }

    suspend fun saveAfterRetire(
        runtime: LibraryRuntime, reader: ReadingSessionCoordinator, chapter: TextChapter, scalar: Long, retire: () -> Unit,
    ): ReadingProgressUpdateData {
        val held = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val publications = AtomicInteger()
        val page = PageObservation<ReadingProgressUpdateData> { publications.incrementAndGet() }
        // Occupy the real single consumer in a preceding already-ACKed terminal
        // callback, making the next actual Core terminal wait for page retirement.
        runtime.core.submit(ReaderM1.coreInfo, EmptyParams(), PageObservation {
            held.complete(Unit)
            release.await(15, TimeUnit.SECONDS)
        })
        val save = try {
            withTimeout(5_000) { held.await() }
            val operation = reader.save(chapter, scalar, page)
            page.retire()
            retire()
            check(withTimeoutOrNull(100) { operation.await() } == null) { "Write settled before page retirement" }
            operation
        } finally {
            release.countDown()
        }
        val terminal = withTimeout(5_000) { save.await() }
        check(terminal.stored && terminal.chapterOffset == scalar)
        check(save.identity.runtimeId == runtime.core.runtimeId)
        val status = runtime.core.call(ReaderM1.runtimeStatus, RuntimeStatusParams(includeCapacity = true))
        check(status.capacity?.current?.pendingDeliveryCount == 0L)
        check(publications.get() == 0)
        check(runtime.core.call(ReaderM1.coreInfo, EmptyParams()).sqliteUserVersion == 47L)
        println("M1_LATE_ACK pid=${Process.myPid()} request=${save.identity.requestId} runtime=${save.identity.runtimeId}" +
            " scalar=${terminal.chapterOffset} priorPagePublications=0 pendingDelivery=0 subsequentRequest=ok")
        return terminal
    }

    suspend fun positionGuards(runtime: LibraryRuntime, chapter: TextChapter, terminal: ReadingProgressUpdateData) {
        val body = chapter.content
        val base = TextReadingProgressUpdateParams(
            bookId = body.bookId, sourceId = body.sourceId, chapterIndex = body.chapterIndex,
            chapterOffset = terminal.chapterOffset + 1, chapterProgress = 0.3,
            expectedProgressRevision = requireNotNull(terminal.progressRevision),
            expectedBodyVersion = requireNotNull(body.bodyVersion),
            expectedProcessingVersion = requireNotNull(body.processingVersion),
        )
        for ((attempt, reason) in listOf(
            base.copy(expectedBodyVersion = "stale-body") to "POSITION_CONTEXT_STALE",
            base.copy(expectedProcessingVersion = "stale-processing") to "POSITION_CONTEXT_STALE",
            base.copy(expectedProgressRevision = chapter.revision) to "READING_PROGRESS_CHANGED",
        )) {
            val failure = reject { runtime.core.call(ReaderM1.readingProgressUpdate, attempt) }
            check(failure.code == ReaderErrorCode.INVALID_PARAMS && failure.details?.reason == reason) { failure.toString() }
        }
        val other = runtime.core.call(ReaderM1.readingProgressUpdate, TextReadingProgressUpdateParams(
            bookId = body.bookId, sourceId = OTHER_SOURCE, chapterIndex = OTHER_CHAPTER,
            chapterOffset = OTHER_SCALAR, chapterProgress = 0.5,
        ))
        check(other.stored && other.sourceId == OTHER_SOURCE && other.chapterOffset == OTHER_SCALAR)
        checkPositions(runtime, body.bookId, body.chapterIndex, terminal.chapterOffset)
        println("M1_POSITION_GUARDS pid=${Process.myPid()} rejected=body,processing,revision" +
            " local=${terminal.chapterOffset} otherSource=$OTHER_SOURCE other=$OTHER_SCALAR")
    }

    suspend fun checkPositions(runtime: LibraryRuntime, bookId: String, chapter: Long, scalar: Long) {
        val local = runtime.core.call(ReaderM1.readingProgressGet, TextReadingProgressGetParams(bookId, "local")).progress
        check(local?.sourceId == "local" && local.chapterIndex == chapter && local.chapterOffset == scalar)
        val other = runtime.core.call(ReaderM1.readingProgressGet, TextReadingProgressGetParams(bookId, OTHER_SOURCE)).progress
        check(other?.sourceId == OTHER_SOURCE && other.chapterIndex == OTHER_CHAPTER && other.chapterOffset == OTHER_SCALAR)
    }

    private suspend fun reject(block: suspend () -> Unit): ReaderM1Error {
        try { block() } catch (rejection: CoreRejected) { return rejection.detail }
        error("Core accepted stale or unavailable input")
    }

    class HostProof : HostCapabilities {
        private val actual = SystemSurfaceHost(Clock.fixed(Instant.ofEpochMilli(1), ZoneOffset.UTC))
        private val dispatched = ConcurrentLinkedQueue<ReaderHostCapability>()
        private val entered = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<Unit>()
        override val available = actual.available

        override suspend fun reply(capability: ReaderHostCapability, request: String, replyId: Long): String {
            dispatched.add(capability)
            entered.complete(Unit)
            release.await()
            return actual.reply(capability, request, replyId)
        }

        suspend fun verify(runtime: LibraryRuntime) {
            val earlier = runtime.core.submit(ReaderM1.runtimeHostSmokeTimeNow,
                M1TimeNowSmokeParams(params = HostTimeNowRequest()))
            try {
                withTimeout(5_000) { entered.await() }
                val later = runtime.core.submit(ReaderM1.coreInfo, EmptyParams())
                check(withTimeout(5_000) { later.await() }.buildIdentity.gitCommit == CORE_PIN)
                check(earlier.identity.requestId < later.identity.requestId)
                check(withTimeoutOrNull(100) { earlier.await() } == null)
                release.complete(Unit)
                check(withTimeout(5_000) { earlier.await() }.unixMillis == 1L)
                val failure = reject {
                    runtime.core.call(ReaderM1.runtimeHostSmokeCookieGet,
                        M1CookieGetSmokeParams(params = HostCookieGetRequest(url = "https://example.invalid/")))
                }
                check(failure.code == ReaderErrorCode.INTERNAL && failure.details?.host?.diagnostics?.code ==
                    HostErrorCode.CAPABILITY_UNAVAILABLE)
                check(dispatched.toList() == listOf(ReaderHostCapability.TIME_NOW))
                println("M1_HOST pid=${Process.myPid()} supported=time.now omitted=cookie.get" +
                    " omittedSlotCalls=0 error=INTERNAL/CAPABILITY_UNAVAILABLE" +
                    " terminalOrder=${later.identity.requestId},${earlier.identity.requestId}")
            } finally {
                release.complete(Unit)
            }
        }
    }
}
