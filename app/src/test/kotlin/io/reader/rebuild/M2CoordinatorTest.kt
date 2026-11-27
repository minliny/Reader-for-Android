package io.reader.rebuild

import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.M2BookDetailBookInput
import com.reader.core.protocol.M2BookDetailParams
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2CacheBookPrefetchData
import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2CacheDownloadTaskParams
import com.reader.core.protocol.M2CacheDownloadTaskResultStatus
import com.reader.core.protocol.M2ChapterContentDataVia
import com.reader.core.protocol.M2ChapterContentParams
import com.reader.core.protocol.M2CookieSetSmokeParams
import com.reader.core.protocol.M2DownloadOperationReceiptPhase
import com.reader.core.protocol.M2HostCookieRecord
import com.reader.core.protocol.M2HostCookieSetRequest
import com.reader.core.protocol.M2RuleSubUpdateChangeKind
import com.reader.core.protocol.M2RulesMutationReceiptRejected
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubDelete
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubPut
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubUpdateApplyV1
import com.reader.core.protocol.M2RulesMutationReceiptSourceDelete
import com.reader.core.protocol.M2RulesMutationReceiptSourceImport
import com.reader.core.protocol.M2SourceCheckRunParams
import com.reader.core.protocol.M2SourceCheckRunParamsLevelsItem
import com.reader.core.protocol.M2SourceCheckRunParamsMode
import com.reader.core.protocol.M2SourceCheckRunParamsReportVersion
import com.reader.core.protocol.M2SourceImportParams
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.M2SourceSwitchBookKey
import com.reader.core.protocol.M2SourceSwitchChapterData
import com.reader.core.protocol.M2SourceSwitchCommitParams
import com.reader.core.protocol.M2SourceSwitchTargetBookData
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.protocol.TextReadingProgressGetParams
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.features.downloads.DownloadCoordinator
import io.reader.rebuild.features.downloads.DownloadJournal
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.reading.OnlineBookKey
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import io.reader.rebuild.features.reading.SourceSwitchCoordinator
import io.reader.rebuild.features.search.SearchCoordinator
import io.reader.rebuild.features.source.SourceManagementCoordinator
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class M2CoordinatorTest {
    @Test fun sourceV9TransactionsPreserveCoreFacts() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            val (library, network) = openM2(home)
            val core = library.core
            val sources = SourceManagementCoordinator(core, network)
            try {
                val first = fixture.source("alpha")
                val second = fixture.source("beta")
                for (source in listOf(first, second)) sources.importFixture(source)
                val sourceGroup = sources.groups().groups.single { it.name == "M2 回环" }
                check(sources.groupMembers(sourceGroup.groupId).map { it.sourceId }.toSet() == setOf(first.id, second.id))
                val version = requireNotNull(sources.list(M2SourceListParams(sourceId = first.id)).sources.single().sourceVersion)
                val edit = sources.edit(M2SourceImportParams(sourceId = first.id, name = "条件编辑"), version)
                check(edit is M2RulesMutationReceiptSourceImport)
                check(sources.edit(M2SourceImportParams(sourceId = first.id, name = "过期编辑"), version) is
                    M2RulesMutationReceiptRejected)
                check(sources.list(M2SourceListParams(sourceId = first.id)).sources.single().name == "条件编辑")
                for (source in listOf(first, second)) {
                    val checked = sources.check(M2SourceCheckRunParams(
                        reportVersion = M2SourceCheckRunParamsReportVersion.V2, mode = M2SourceCheckRunParamsMode.SEARCH,
                        sourceIds = listOf(source.id), keyword = M2_BOOK, keywordOverride = true,
                        levels = M2SourceCheckRunParamsLevelsItem.entries))
                    check(checked.results.single().sourceId == source.id)
                    check(sources.reports(source.id).single().report.steps.map { it.level.wireValue } ==
                        listOf("L1", "L2", "L3", "L4", "L5"))
                }
                val report = sources.clearLogin(first.id)
                check(report.clearedCount >= 1)
                check(sources.reports(first.id).isEmpty() && sources.reports(second.id).isNotEmpty())
                val stored = core.call(ReaderM2.runtimeHostSmokeCookieSet,
                    M2CookieSetSmokeParams(params = M2HostCookieSetRequest(M2HostCookieRecord("first", "fixture"),
                        first.base, first.id)))
                check(stored.stored)
                sources.setEnabled(first.id, false)
                check(sources.list(M2SourceListParams(sourceId = first.id)).sources.single().enabled == false)
                sources.setEnabled(first.id, true)

                val rulesUrl = fixture.origin + "/rules"
                val created = sources.saveSubscription("规则回环", rulesUrl, null)
                check(created is M2RulesMutationReceiptRuleSubPut)
                val subscription = created.data.sub
                val edited = sources.saveSubscription("规则回环·修订", rulesUrl, subscription)
                check(edited is M2RulesMutationReceiptRuleSubPut && edited.data.sub.id == subscription.id)
                check(runCatching { sources.fetchSubscriptionPreview(subscription) }.exceptionOrNull() is CoreRejected)
                val initial = sources.fetchSubscriptionPreview(edited.data.sub)
                val import = sources.confirmSubscription(initial, initial.changes.filter {
                    it.kind == M2RuleSubUpdateChangeKind.ADDED
                }.map { it.sourceId })
                check(import is M2RulesMutationReceiptRuleSubUpdateApplyV1)
                fixture.subscriptionUpdate = true
                val currentSubscription = sources.subscriptions().subs.single { it.id == subscription.id }
                val secondVersion = sources.fetchSubscriptionPreview(currentSubscription)
                val added = secondVersion.changes.single { it.kind == M2RuleSubUpdateChangeKind.ADDED }
                check(added.sourceId == fixture.source("additional").id)
                check(sources.confirmSubscription(secondVersion, listOf(added.sourceId)) is M2RulesMutationReceiptRuleSubUpdateApplyV1)
                check(sources.deleteSubscription(subscription.id) is M2RulesMutationReceiptRuleSubDelete)
                check(sources.subscriptions().subs.none { it.id == subscription.id } &&
                    sources.list(M2SourceListParams(sourceId = added.sourceId)).sources.single().sourceId == added.sourceId)

                val online = OnlineReadingCoordinator(core)
                val detail = online.detail(first.detail())
                online.addToShelf(detail)
                val catalog = online.refreshDirectory(first.directory())
                val chapter = online.open(catalog, 0)
                check(online.save(chapter, 7).await().stored)
                val originalLocation = core.call(ReaderM1.readingProgressGet,
                    TextReadingProgressGetParams(first.bookId, first.id)).progress
                check(sources.delete(listOf(first.id)) is M2RulesMutationReceiptSourceDelete)
                check(core.call(ReaderM1.bookshelfGet, BookshelfGetParams(first.id, first.bookId)).book != null)
                val originalEntry = catalog.entries.single { it.index == 0L }
                val retained = online.chapter(M2ChapterContentParams(cacheOnly = true, sourceId = first.id,
                    bookId = first.bookId, chapterTitle = originalEntry.title, chapterIndex = originalEntry.index,
                    chapterUrl = originalEntry.url, variables = originalEntry.variables))
                check(retained.via == M2ChapterContentDataVia.CACHE &&
                    retained.content == chapter.body.content && retained.bodyVersion == chapter.body.bodyVersion)
                check(originalLocation?.chapterOffset == 7L &&
                    core.call(ReaderM1.readingProgressGet, TextReadingProgressGetParams(first.bookId, first.id)).progress == originalLocation)
                println("ANDROID_M2_SOURCE derivedGroup=2 editConflict=true reports=L1-L5 scopedClear=true " +
                    "subscription=2 versionGuard=true subscriptionDeletionKeepsSource=true deletePreservedScalar=7")
            } finally {
                sources.close()
                closeM2(library, network)
                home.deleteRecursively()
            }
        }
    }

    @Test fun stoppedSearchKeepsItsOwnersAndOnlineSwitchPreservesScalar() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            val (library, network) = openM2(home)
            val search = SearchCoordinator(library.core)
            try {
                val sources = SourceManagementCoordinator(library.core, network)
                val first = fixture.source("alpha")
                val other = fixture.source("beta")
                val all = listOf(first, other, fixture.source("gamma"), fixture.source("delta"), fixture.source("omega"))
                for (source in all) sources.importFixture(source)
                val gates = all.associate { (it.path + "/search") to fixture.hold(it.path + "/search") }
                val original = try {
                    val starting = search.start(M2_BOOK)
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (gates.values.count { it.hit.count == 0L } < 4 && System.nanoTime() < deadline) Thread.sleep(20)
                    check(gates.values.count { it.hit.count == 0L } == 4)
                    search.stopDisplaying()
                    check(search.inFlight && runCatching { search.retry() }.isFailure)
                    gates.keys.forEach(fixture::release)
                    withTimeout(7_000) { starting.await() }
                } finally { gates.keys.forEach(fixture::release) }
                check(original.size == 5 && original.all { it.result.isSuccess } && search.results.value.isEmpty())
                val retried = search.retry().await()
                check(retried.size == 5 && retried.all { it.result.isSuccess })
                check(original.map { it.identity }.intersect(retried.map { it.identity }.toSet()).isEmpty())

                val online = OnlineReadingCoordinator(library.core)
                val hit = retried.single { it.sourceId == first.id }.result.getOrThrow().books.single()
                val reference = BookReference(first.id, hit, null)
                check(reference.bookId == first.bookId && reference.detailParams.bookUrl == first.bookId)
                val detail = online.detail(reference.detailParams)
                check(detail.book.author == "测试作者")
                online.addToShelf(detail)
                val book = reference.fromDetail(detail)
                val firstCatalog = online.refreshDirectory(M2BookTocParams(sourceId = book.sourceId,
                    bookId = book.bookId, tocUrl = book.tocUrl, variables = book.variables))
                val initial = online.open(firstCatalog, 0)
                check(online.save(initial, 14).await().stored)
                val cached = online.open(firstCatalog, 0, cacheOnly = true)
                check(cached.scalar == 14L)
                fixture.status(first.path + "/chapter/1", 503)
                fixture.status(first.path + "/chapter/2", 503)
                expectCoreFailure { online.refresh(cached) }
                check(online.open(firstCatalog, 0, cacheOnly = true).scalar == 14L)
                expectCoreFailure { online.open(firstCatalog, 1) }
                fixture.status(first.path + "/chapter/1", null)
                fixture.status(first.path + "/chapter/2", null)
                val target = online.detail(other.detail())
                val incoming = online.refreshDirectory(other.directory())
                val switcher = SourceSwitchCoordinator(online)
                val switched = switcher.switch(cached, M2SourceSwitchCommitParams(
                    from = M2SourceSwitchBookKey(first.id, first.bookId),
                    target = M2SourceSwitchTargetBookData(other.id, other.bookId, target.book.title, target.book.author),
                    newToc = incoming.entries.map { M2SourceSwitchChapterData(it.url, it.title, it.url, it.index) },
                    currentChapterTitle = cached.body.chapterTitle, currentChapterIndex = 0, updatedAt = 1_800_000_000_000L),
                    scalar = 14)
                check(switched.scalar == 14L && switched.catalog.book == OnlineBookKey(other.id, other.bookId))
                check(online.open(switched.catalog, 0, cacheOnly = true).scalar == 14L)
                println("ANDROID_M2_ONLINE original=${original.map { it.identity.requestId }} retried=${retried.map { it.identity.requestId }} searchReferenceDetail=true offlineScalar=14 switchedScalar=${switched.scalar}")
            } finally { search.close(); closeM2(library, network); home.deleteRecursively() }
        }
    }

    @Test fun lateDownloadCancellationAndIndependentJvmRecovery() = runBlocking {
        val home = m2Temp()
        M2Loopback().use { fixture ->
            try {
                val (library, network) = openM2(home)
                val online = OnlineReadingCoordinator(library.core)
                val journal = DownloadJournal(File(home, "downloads.json"))
                val downloads = DownloadCoordinator(online, journal)
                val source = fixture.source("alpha")
                try {
                    SourceManagementCoordinator(library.core, network).importFixture(source)
                    online.addToShelf(online.detail(source.detail()))
                    val directory = online.refreshDirectory(source.directory())
                    check(online.save(online.open(directory, 1), 11).await().stored)
                    val path = source.path + "/chapter/1"
                    val held = fixture.hold(path)
                    val input = source.download(0, 1, 1)
                    val original = downloads.begin(input)
                    try {
                        held.waitForRequest()
                        val active = requireNotNull(downloads.queryPrefetch(input).receipt)
                        check(active.phase == M2DownloadOperationReceiptPhase.PENDING)
                        val controls = M2CacheDownloadTaskParams(source.id, source.bookId, 0)
                        check(downloads.pause(controls).status == M2CacheDownloadTaskResultStatus.PAUSED)
                        check(downloads.resume(controls).status == M2CacheDownloadTaskResultStatus.PENDING)
                        check(downloads.cancel(controls).status == M2CacheDownloadTaskResultStatus.CANCELLED)
                        downloads.retirePanel()
                        fixture.release(path)
                        val failure = expectCoreFailure { original.await() }
                        check(failure.detail.code == ReaderErrorCode.INTERNAL &&
                            "READING_PREPARATION_queue_owner" in failure.detail.message)
                        val hits = fixture.count(path)
                        val recovered = downloads.recoverPrefetch(input)
                        check(recovered.binding == active.binding && recovered.phase == M2DownloadOperationReceiptPhase.SETTLED)
                        check((recovered.data as M2CacheBookPrefetchData).prefetchedCount == 0L && fixture.count(path) == hits)
                        val queue = downloads.status(M2CacheBookStatusParams(source.id, source.bookId, includeChapterStates = true))
                        check(queue.chapters.first { it.chapterIndex == 0L }.state == M2CacheChapterState.CANCELLED)
                        println("ANDROID_M2_DOWNLOAD_CANCEL payload=${active.binding.payloadFingerprint} pending→settled fetched=0")
                    } finally { fixture.release(path) }
                } finally { downloads.close(); closeM2(library, network) }

                val coldPath = source.path + "/chapter/4"
                val gate = fixture.hold(coldPath)
                try {
                    val writer = coldProcess(home, source, "write")
                    gate.waitForRequest()
                    File(home, "host-arrived").writeText("true")
                    writer.awaitOK()
                } finally { fixture.release(coldPath) }
                val before = fixture.count(coldPath)
                coldProcess(home, source, "read").awaitOK()
                check(fixture.count(coldPath) == before + 1)
            } finally { home.deleteRecursively() }
        }
    }

    private fun coldProcess(home: File, source: SourceFixture, phase: String): Process =
        ProcessBuilder(File(System.getProperty("java.home"), "bin/java").absolutePath,
            "-Xcheck:jni", "-Djava.library.path=${System.getProperty("java.library.path")}",
            "-cp", requireNotNull(System.getProperty("reader.cold.classpath")), M2ColdDownload::class.java.name,
            phase, home.absolutePath, source.origin, source.name).inheritIO().start()

    private fun Process.awaitOK() {
        if (!waitFor(26, TimeUnit.SECONDS)) { destroyForcibly(); error("Cold JVM did not conclude") }
        check(exitValue() == 0)
    }
}

internal suspend fun SourceManagementCoordinator.importFixture(source: SourceFixture) {
    check(importSource(previewImport(ReaderProtocolJson.encodeToString(source.rule)).single()) is
        M2RulesMutationReceiptSourceImport)
}

internal fun SourceFixture.detail() = M2BookDetailParams(sourceId = id,
    book = M2BookDetailBookInput(bookId = bookId, title = M2_BOOK, author = "测试作者"), bookUrl = bookId)

internal fun SourceFixture.directory() = M2BookTocParams(sourceId = id, bookId = bookId, tocUrl = toc)

internal fun SourceFixture.download(first: Long, end: Long, sequence: Long) =
    M2CacheBookPrefetchParams(sourceId = id, bookId = bookId, chapterRange = listOf(first, end),
        operationId = "${UUID.randomUUID()}/$sequence", sequence = sequence)
