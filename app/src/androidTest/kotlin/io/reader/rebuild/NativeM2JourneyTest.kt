package io.reader.rebuild

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reader.core.protocol.BookshelfGetParams
import com.reader.core.protocol.M2BookTocParams
import com.reader.core.protocol.M2CacheBookPrefetchParams
import com.reader.core.protocol.M2CacheBookStatusParams
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2CacheDownloadTaskParams
import com.reader.core.protocol.M2CacheDownloadTaskResultStatus
import com.reader.core.protocol.M2ChapterContentDataVia
import com.reader.core.protocol.M2ChapterContentParams
import com.reader.core.protocol.M2CookieGetSmokeParams
import com.reader.core.protocol.M2CookieSetSmokeParams
import com.reader.core.protocol.M2DownloadOperationReceiptPhase
import com.reader.core.protocol.M2HostCookieGetRequest
import com.reader.core.protocol.M2HostCookieRecord
import com.reader.core.protocol.M2HostCookieSetRequest
import com.reader.core.protocol.M2HostHttpRequest
import com.reader.core.protocol.M2HostHttpSession
import com.reader.core.protocol.M2HostMediaDownloadRequest
import com.reader.core.protocol.M2HostWebViewDocumentHtml
import com.reader.core.protocol.M2HostWebViewDocumentUrl
import com.reader.core.protocol.M2HostWebViewEvaluateJavaScriptRequest
import com.reader.core.protocol.M2HttpExecuteSmokeParams
import com.reader.core.protocol.M2MediaDownloadSmokeParams
import com.reader.core.protocol.M2RuleSubPutParams
import com.reader.core.protocol.M2RuleSubUpdateChangeKind
import com.reader.core.protocol.M2RulesMutationReceiptRejected
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubPut
import com.reader.core.protocol.M2RulesMutationReceiptRuleSubUpdateApplyV1
import com.reader.core.protocol.M2RulesMutationReceiptSourceDelete
import com.reader.core.protocol.M2RulesMutationReceiptSourceImport
import com.reader.core.protocol.M2RulesMutationReceiptSourceUpdate
import com.reader.core.protocol.M2SourceCheckRunParams
import com.reader.core.protocol.M2SourceCheckRunParamsLevelsItem
import com.reader.core.protocol.M2SourceCheckRunParamsMode
import com.reader.core.protocol.M2SourceCheckRunParamsReportVersion
import com.reader.core.protocol.M2SourceImportParams
import com.reader.core.protocol.M2SourceListEntry
import com.reader.core.protocol.M2SourceListParams
import com.reader.core.protocol.M2SourceSwitchBookKey
import com.reader.core.protocol.M2SourceSwitchChapterData
import com.reader.core.protocol.M2SourceSwitchCommitParams
import com.reader.core.protocol.M2SourceSwitchTargetBookData
import com.reader.core.protocol.M2WebViewEvaluateJavaScriptSmokeParams
import com.reader.core.protocol.ReaderErrorCode
import com.reader.core.protocol.ReaderM1
import com.reader.core.protocol.ReaderM2
import com.reader.core.protocol.ReaderProtocolJson
import com.reader.core.protocol.TextReadingProgressGetParams
import io.reader.rebuild.core.CoreRejected
import io.reader.rebuild.core.CoreRuntimeActor
import io.reader.rebuild.core.HostCapabilityRouter
import io.reader.rebuild.features.downloads.DownloadCoordinator
import io.reader.rebuild.features.downloads.DownloadJournal
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.reading.OnlineBookKey
import io.reader.rebuild.features.reading.OnlineReadingCoordinator
import io.reader.rebuild.features.reading.SourceSwitchCoordinator
import io.reader.rebuild.features.reading.requireOnlineText
import io.reader.rebuild.features.search.SearchCoordinator
import io.reader.rebuild.features.shell.ReaderActivity
import io.reader.rebuild.features.source.SourceManagementCoordinator
import io.reader.rebuild.host.network.NetworkAndWebHost
import io.reader.rebuild.host.storage.LibraryRuntime
import io.reader.rebuild.host.system.SystemSurfaceHost
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeM2JourneyTest {
    private val runner = InstrumentationRegistry.getInstrumentation()
    private val context = runner.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val origin = "http://127.0.0.1:${requireNotNull(args.getString("readerM2Port")).toInt().also { require(it in 1024..65535) }}"
    private val home = File(context.filesDir, "instrumentation-m2/${requireNotNull(args.getString("readerM2Run"))
        .also { require(it.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) }}")
    private val title = "受控回环书"
    private val alpha = Source("$origin/book/alpha")
    private val beta = Source("$origin/book/beta")

    @Test fun writeThroughRealAndroidHostAndCore() = runBlocking {
        check(home.mkdirs()) { "Use a new instrumentation run directory" }
        hosted { core, network ->
            val sources = SourceManagementCoordinator(core, network)
            verifyHost(core)
            val preview = sources.previewImport(core.http("$origin/seed").body)
            check(preview.size == 2 && preview.all { it.existing == null })
            for (item in preview) check(sources.importSource(item) is M2RulesMutationReceiptSourceImport)
            check(sources.groups().groups.single { it.name == "Android 真机回环" }.let {
                sources.groupMembers(it.groupId).map(M2SourceListEntry::sourceId).toSet() == setOf(alpha.id, beta.id)
            })

            val version = requireNotNull(sources.list(M2SourceListParams(sourceId = alpha.id)).sources.single().sourceVersion)
            check(sources.edit(M2SourceImportParams(sourceId = alpha.id, name = "已编辑回环"), version) is
                M2RulesMutationReceiptSourceImport)
            check(sources.edit(M2SourceImportParams(sourceId = alpha.id, name = "过期编辑"), version) is
                M2RulesMutationReceiptRejected)
            for (source in listOf(alpha, beta)) {
                val report = sources.check(M2SourceCheckRunParams(
                    reportVersion = M2SourceCheckRunParamsReportVersion.V2,
                    mode = M2SourceCheckRunParamsMode.SEARCH, sourceIds = listOf(source.id),
                    keyword = title, keywordOverride = true, levels = M2SourceCheckRunParamsLevelsItem.entries))
                check(report.results.single().sourceId == source.id)
                check(sources.reports(source.id).single().report.steps.map { it.level.wireValue } ==
                    listOf("L1", "L2", "L3", "L4", "L5"))
            }
            verifyLogin(core, sources, network)
            check(sources.clearLogin(alpha.id).clearedCount >= 1)
            check(sources.reports(alpha.id).isEmpty() && sources.reports(beta.id).isNotEmpty())
            check(core.http("$origin/cookies", alpha.id).body.isEmpty())
            val activeLogin = sources.beginLogin(alpha.id)
            check(sources.setEnabled(alpha.id, false) is M2RulesMutationReceiptSourceUpdate)
            check(activeLogin.navigation.value.retired && core.http("$origin/cookies", alpha.id).body.isEmpty())
            check(sources.setEnabled(alpha.id, true) is M2RulesMutationReceiptSourceUpdate)

            val subscription = 8042L
            check(sources.saveSubscription(M2RuleSubPutParams(subscription, name = "回环规则",
                url = "$origin/rules/v1", type = 3L)) is M2RulesMutationReceiptRuleSubPut)
            val v1 = sources.fetchSubscriptionPreview(subscription, "$origin/rules/v1")
            val initial = v1.changes.single { it.kind == M2RuleSubUpdateChangeKind.ADDED }
            check(sources.confirmSubscription(v1, listOf(initial.sourceId)) is M2RulesMutationReceiptRuleSubUpdateApplyV1)
            val v2 = sources.fetchSubscriptionPreview(subscription, "$origin/rules/v2")
            val added = v2.changes.single { it.kind == M2RuleSubUpdateChangeKind.ADDED }
            check(added.sourceId == "$origin/book/extra")
            check(sources.confirmSubscription(v2, listOf(added.sourceId)) is M2RulesMutationReceiptRuleSubUpdateApplyV1)
            println("M2_NATIVE_SOURCE pid=${Process.myPid()} import=2 group=derived conditionalConflict=true levels=L1-L5 scopedClear=true subscription=2")

            val search = SearchCoordinator(core)
            val books = try {
                control(core, "hold-search")
                val stopped = search.start(title)
                val original = try {
                    waitForGate(core)
                    search.stopDisplaying()
                    check(search.inFlight && runCatching { search.retry() }.isFailure)
                    control(core, "release")
                    withTimeout(15_000) { stopped.await() }
                } finally {
                    control(core, "release")
                }
                check(original.size >= 2 && search.results.value.isEmpty())
                val terminals = search.retry().await()
                check(terminals.size >= 2 && terminals.all { it.result.isSuccess })
                check(original.map { it.identity }.intersect(terminals.map { it.identity }.toSet()).isEmpty())
                check(search.results.value.map { it.sourceId }.containsAll(listOf(alpha.id, beta.id)))
                terminals.filter { it.sourceId in setOf(alpha.id, beta.id) }.associate { result ->
                    val data = result.result.getOrThrow()
                    check(data.sourceId == result.sourceId)
                    result.sourceId to BookReference(data.sourceId, data.books.single(), sourceName = null)
                }
            } finally {
                search.close()
            }
            val reading = OnlineReadingCoordinator(core)
            val originalBook = requireNotNull(books[alpha.id])
            check(originalBook.bookId == alpha.bookId && originalBook.detailParams.bookUrl == alpha.bookId)
            val detail = reading.detail(originalBook.detailParams)
            check(detail.book.bookId == alpha.bookId && detail.book.author == "测试作者")
            reading.addToShelf(detail)
            val catalog = reading.refreshDirectory(alpha.directory())
            check(catalog.entries.size == 4)
            val page = reading.open(catalog, 0)
            check(page.text.contains("中文𠀀与Café"))
            check(reading.save(page, 14).await().stored)
            val cached = reading.open(catalog, 0, cacheOnly = true)
            check(cached.scalar == 14L)
            control(core, "offline")
            try {
                expectRejection { reading.refresh(cached) }
                val retained = reading.open(catalog, 0, cacheOnly = true)
                check(retained.scalar == 14L && retained.body.content == cached.body.content &&
                    retained.body.bodyVersion == cached.body.bodyVersion)
                expectRejection { reading.open(catalog, 1) }
            } finally {
                control(core, "online")
            }
            val target = reading.detail(requireNotNull(books[beta.id]).detailParams)
            val nextCatalog = reading.refreshDirectory(beta.directory())
            val switched = SourceSwitchCoordinator(reading).switch(cached, M2SourceSwitchCommitParams(
                from = M2SourceSwitchBookKey(alpha.id, alpha.bookId),
                target = M2SourceSwitchTargetBookData(beta.id, beta.bookId, target.book.title, target.book.author),
                newToc = nextCatalog.entries.map { M2SourceSwitchChapterData(it.url, it.title, it.url, it.index) },
                currentChapterTitle = cached.body.chapterTitle, currentChapterIndex = 0, updatedAt = 1_800_000_000_000L),
                scalar = 14)
            check(switched.scalar == 14L && switched.catalog.book == OnlineBookKey(beta.id, beta.bookId))
            println("M2_NATIVE_ONLINE pid=${Process.myPid()} search=true searchReferenceDetail=true toc=4 onlineBody=true failedRefreshRetains=true offlineCache=true switchedScalar=${switched.scalar}")

            verifyDownloads(core, reading)
            check(sources.delete(listOf(beta.id)) is M2RulesMutationReceiptSourceDelete)
            check(sources.list(M2SourceListParams(sourceId = beta.id)).sources.isEmpty())
            check(core.call(ReaderM1.bookshelfGet, BookshelfGetParams(beta.id, beta.bookId)).book != null)
            check(core.call(ReaderM1.readingProgressGet, TextReadingProgressGetParams(beta.bookId, beta.id)).progress?.chapterOffset == 14L)
            File(home, "writer-pid").writeText(Process.myPid().toString())
        }
    }

    @Test fun coldProcessRestoresExactSourceScalarAndOriginalDownload() = runBlocking {
        val writer = File(home, "writer-pid").readText().toInt()
        check(writer != Process.myPid())
        hosted { core, _ ->
            val reading = OnlineReadingCoordinator(core)
            check(core.call(ReaderM1.bookshelfGet, BookshelfGetParams(beta.id, beta.bookId)).book != null)
            val progress = requireNotNull(core.call(ReaderM1.readingProgressGet,
                TextReadingProgressGetParams(beta.bookId, beta.id)).progress)
            check(progress.chapterIndex == 0L && progress.chapterOffset == 14L)
            val chapter = reading.chapter(M2ChapterContentParams(cacheOnly = true, sourceId = beta.id,
                bookId = beta.bookId, chapterIndex = 0, chapterTitle = "第一章", chapterUrl = "${beta.id}/chapter/1"))
            check(chapter.via == M2ChapterContentDataVia.CACHE && chapter.requireOnlineText().contains("中文𠀀与Café"))
            val downloads = DownloadCoordinator(reading, DownloadJournal(File(home, "downloads.json")))
            try {
                val original = ReaderProtocolJson.decodeFromString<M2CacheBookPrefetchParams>(File(home, "download-request").readText())
                val receipt = requireNotNull(downloads.queryPrefetch(original).receipt)
                check(receipt.phase == M2DownloadOperationReceiptPhase.SETTLED &&
                    receipt.binding.operationId == original.operationId &&
                    receipt.binding.payloadFingerprint == File(home, "download-payload").readText())
                println("M2_NATIVE_COLD pid=${Process.myPid()} writer=$writer source=beta scalar=14 cache=true action=${receipt.binding.operationId} exactPayload=true")
            } finally {
                downloads.close()
            }
        }
    }

    private suspend fun verifyDownloads(core: CoreRuntimeActor, reading: OnlineReadingCoordinator) {
        val downloads = DownloadCoordinator(reading, DownloadJournal(File(home, "downloads.json")))
        try {
            val statusInput = M2CacheBookStatusParams(beta.id, beta.bookId, includeChapterStates = true)
            val complete = downloads.begin(M2CacheBookPrefetchParams(sourceId = beta.id, bookId = beta.bookId,
                chapterRange = listOf(1L, 2L))).await()
            check(complete.prefetchedCount == 1L &&
                downloads.status(statusInput).chapters.single { it.chapterIndex == 1L }.state == M2CacheChapterState.COMPLETED)
            val range = M2CacheBookPrefetchParams(sourceId = beta.id, bookId = beta.bookId, chapterRange = listOf(3L, 4L),
                operationId = "${UUID.randomUUID()}/2", sequence = 2L)
            control(core, "hold")
            val original = downloads.begin(range)
            val requestId = original.identity().requestId
            try {
                waitForGate(core)
                downloads.retirePanel()
                check(downloads.isWorking(beta.id, beta.bookId))
                val pending = requireNotNull(downloads.queryPrefetch(range).receipt)
                check(pending.phase == M2DownloadOperationReceiptPhase.PENDING)
                val chapter = M2CacheDownloadTaskParams(beta.id, beta.bookId, 3)
                check(downloads.pause(chapter).status == M2CacheDownloadTaskResultStatus.PAUSED)
                check(downloads.resume(chapter).status == M2CacheDownloadTaskResultStatus.PENDING)
                check(downloads.cancel(chapter).status == M2CacheDownloadTaskResultStatus.CANCELLED)
                control(core, "release")
                val failure = expectRejection { original.await() }
                check(failure.detail.code == ReaderErrorCode.INTERNAL && "READING_PREPARATION_queue_owner" in failure.detail.message)
                val settled = downloads.recoverPrefetch(range)
                check(settled.binding == pending.binding && settled.phase == M2DownloadOperationReceiptPhase.SETTLED)
                val status = downloads.status(statusInput)
                check(status.chapters.single { it.chapterIndex == 3L }.state == M2CacheChapterState.CANCELLED)
                File(home, "download-request").writeText(ReaderProtocolJson.encodeToString(range))
                File(home, "download-payload").writeText(settled.binding.payloadFingerprint)
                println("M2_NATIVE_DOWNLOAD request=$requestId panelRetiredOwner=true lifecycle=paused,resumed,cancelled completed=1 exactPayload=${settled.binding.payloadFingerprint}")
            } finally {
                control(core, "release")
            }
        } finally {
            downloads.close()
        }
    }

    private suspend fun verifyHost(core: CoreRuntimeActor) {
        val encoded = core.call(ReaderM2.runtimeHostSmokeHttpExecute, M2HttpExecuteSmokeParams(params =
            M2HostHttpRequest(url = "$origin/echo", method = "POST", body = "中文", charset = "GBK")))
        check(encoded.body == "d6d0cec4")
        val status = expectRejection { core.http("$origin/503") }
        check(status.detail.details?.host?.diagnostics?.code?.wireValue == "HTTP_ERROR") { status.detail.toString() }
        check(status.detail.details?.host?.diagnostics?.details?.get("status") == JsonPrimitive(503))
        control(core, "hold")
        try {
            val original = core.submit(ReaderM2.runtimeHostSmokeHttpExecute, M2HttpExecuteSmokeParams(params =
                M2HostHttpRequest(url = "$origin/slow")))
            waitForGate(core)
            core.cancelRead(original.identity)
            val failure = expectRejection { withTimeout(5_000) { original.await() } }
            check(failure.detail.code == ReaderErrorCode.CANCELLED)
        } finally {
            control(core, "release")
        }
        val media = core.call(ReaderM2.runtimeHostSmokeMediaDownload, M2MediaDownloadSmokeParams(params =
            M2HostMediaDownloadRequest(url = "$origin/media", rangeStart = 7, rangeEnd = 131086, maxBytes = 132_000)))
        check(media.statusCode == 206L && media.byteLength == 131080L)
        check(File(requireNotNull(media.tempPath)).readBytes().contentEquals(ByteArray(131080) { ((it + 7) % 251).toByte() }))
        println("M2_NATIVE_HTTP pid=${Process.myPid()} gbk=${encoded.body} http503=true partial=${media.byteLength} cancelled=true")
        val web = core.call(ReaderM2.runtimeHostSmokeWebViewEvaluateJavaScript, M2WebViewEvaluateJavaScriptSmokeParams(
            params = M2HostWebViewEvaluateJavaScriptRequest(
                document = M2HostWebViewDocumentHtml(body = "<title>Reader 原生后台求值</title>", baseUrl = "$origin/ready"),
                javaScript = "document.title", timeoutMillis = 8_000)))
        check(web.value == JsonPrimitive("Reader 原生后台求值"))
        println("M2_NATIVE_HOST pid=${Process.myPid()} gbk=${encoded.body} http503=true partial=${media.byteLength} webEval=true cancelled=true")
    }

    private suspend fun verifyLogin(core: CoreRuntimeActor, sources: SourceManagementCoordinator, network: NetworkAndWebHost) {
        val seed = M2HostCookieSetRequest(M2HostCookieRecord("fromCore", "seed", path = "/"), "$origin/cookies", alpha.id)
        check(core.call(ReaderM2.runtimeHostSmokeCookieSet, M2CookieSetSmokeParams(params = seed)).stored)
        val session = sources.beginLogin(alpha.id)
        var activity: Activity? = null
        try {
            activity = runner.startActivitySync(Intent(context, ReaderActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val hostActivity = activity
            withContext(Dispatchers.Main) { hostActivity.setContentView(session.view) }
            check(ReaderProtocolJson.decodeFromString<String>(session.view.javaScript("document.title")) == "Reader 回环登录")
            val cookies = ReaderProtocolJson.decodeFromString<String>(session.view.javaScript("document.cookie"))
            check("fromCore=seed" in cookies && "browser_alpha=fromWeb" in cookies)
            val scoped = core.call(ReaderM2.runtimeHostSmokeCookieGet, M2CookieGetSmokeParams(params =
                M2HostCookieGetRequest(url = "$origin/cookies", sessionId = alpha.id))).cookies
            check(scoped.any { it.name == "browser_alpha" && it.value == "fromWeb" })
            val echoed = core.http("$origin/cookies", alpha.id).body
            check("fromCore=seed" in echoed && "browser_alpha=fromWeb" in echoed)
            check(core.http("$origin/cookies", beta.id).body.isEmpty())
            val background = core.call(ReaderM2.runtimeHostSmokeWebViewEvaluateJavaScript,
                M2WebViewEvaluateJavaScriptSmokeParams(params = M2HostWebViewEvaluateJavaScriptRequest(
                    document = M2HostWebViewDocumentUrl(url = "$origin/ready"), javaScript = "document.cookie",
                    timeoutMillis = 8_000)))
            check(background.value == JsonPrimitive(""))
            val firstUrl = "${alpha.id}/login"
            val secondUrl = "$firstUrl?step=2"
            session.navigate(secondUrl)
            withTimeout(10_000) { session.navigation.first { it.url == secondUrl && it.canGoBack && !it.loading } }
            withContext(Dispatchers.Main) { session.goBack() }
            withTimeout(10_000) { session.navigation.first { it.url == firstUrl && it.canGoForward && !it.loading } }
            withContext(Dispatchers.Main) { session.goForward() }
            withTimeout(10_000) { session.navigation.first { it.url == secondUrl && !it.loading } }
            val pageTime = session.view.javaScript("performance.timeOrigin")
            withContext(Dispatchers.Main) { session.refresh() }
            withTimeout(10_000) {
                while (session.view.javaScript("performance.timeOrigin") in setOf("null", pageTime)) delay(40)
            }
            network.finishLogin(session)
            check(session.navigation.value.retired && "browser_alpha=fromWeb" in core.http("$origin/cookies", alpha.id).body)
            println("M2_NATIVE_LOGIN pid=${Process.myPid()} platformWebView=true hostToWeb=true webToOkHttp=true sourceIsolation=true backgroundProfileIsolated=true history=true reload=true browserFinished=true")
        } finally {
            network.revokeLogin(alpha.id)
            withContext(Dispatchers.Main) { activity?.finish() }
        }
    }

    private suspend fun WebView.javaScript(script: String): String = withContext(Dispatchers.Main) {
        withTimeout(10_000) {
            suspendCancellableCoroutine { result ->
                evaluateJavascript(script) { value -> if (result.isActive) result.resume(value) }
            }
        }
    }

    private suspend fun control(core: CoreRuntimeActor, action: String) {
        check(core.http("$origin/test/$action").body == "ok")
    }

    private suspend fun waitForGate(core: CoreRuntimeActor) = withTimeout(15_000) {
        while (core.http("$origin/test/seen").body != "yes") delay(50)
    }

    private suspend fun CoreRuntimeActor.http(url: String, source: String? = null) =
        call(ReaderM2.runtimeHostSmokeHttpExecute, M2HttpExecuteSmokeParams(params =
            M2HostHttpRequest(url = url, session = source?.let { M2HostHttpSession(it) })))

    private suspend fun expectRejection(action: suspend () -> Unit): CoreRejected {
        try { action() } catch (failure: CoreRejected) { return failure }
        error("Expected Core to reject the original request")
    }

    private suspend fun hosted(block: suspend (CoreRuntimeActor, NetworkAndWebHost) -> Unit) {
        check(context.packageName == "io.reader.minliny.rebuild.android.dev")
        val network = NetworkAndWebHost.android(context, File(home, "network"))
        val errors = ConcurrentLinkedQueue<Throwable>()
        try {
            val runtime = LibraryRuntime.open(File(home, "library"), HostCapabilityRouter(SystemSurfaceHost(), network), errors::add)
            try {
                check(runtime.info.buildIdentity.gitCommit == "14fdd8f91549a30a7b7235088d3c524d128201d1")
                val layout = requireNotNull(runtime.admitted.observation.layout)
                check(runtime.info.sqliteUserVersion == 47L && layout.layoutId == "reader-sqlite-47-remote")
                println("M2_NATIVE_TARGET pid=${Process.myPid()} api=${Build.VERSION.SDK_INT} pageSize=${Os.sysconf(OsConstants._SC_PAGESIZE)}" +
                    " webView=${WebView.getCurrentWebViewPackage()?.versionName} layout=${layout.layoutId}")
                try {
                    block(runtime.core, network)
                } catch (failure: CoreRejected) {
                    println("M2_NATIVE_REJECT pid=${Process.myPid()} detail=${failure.detail}")
                    throw failure
                }
            } finally {
                runtime.close()
            }
            check(errors.isEmpty()) { errors.joinToString() }
        } finally {
            network.close()
        }
    }

    private inner class Source(val id: String) {
        val bookId = "$id/detail"
        fun directory() = M2BookTocParams(sourceId = id, bookId = bookId, tocUrl = "$id/toc")
    }
}
