package io.reader.rebuild.goldens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import com.reader.core.protocol.M2CacheBookStatusData
import com.reader.core.protocol.M2CacheChapterState
import com.reader.core.protocol.M2CacheChapterStatusData
import com.reader.core.protocol.M2CacheGlobalStatsData
import com.reader.core.protocol.M2ChangeBookSourceCandidate
import io.reader.rebuild.features.detail.BookDetailContent
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.downloads.DownloadContent
import io.reader.rebuild.features.downloads.DownloadRow
import io.reader.rebuild.features.reading.SourceSwitchContent
import io.reader.rebuild.features.reading.ReadingAppearance
import io.reader.rebuild.features.reading.ReadingSourceSheetContent
import io.reader.rebuild.features.search.SearchContent

private val onlineBook = BookReference(
    "https://pine.example", "https://pine.example/detail", "山间晚读", "林江",
    intro = "旅人停在山间，向每一道新来的风打听故友的消息。",
    lastChapter = "第十八章 · 冬雪初落", sourceName = "青松小说",
)
private val otherSource = onlineBook.copy(sourceId = "https://ink.example", bookId = "https://ink.example/detail",
    sourceName = "墨纸文库")
private val queue = listOf(M2CacheChapterState.COMPLETED, M2CacheChapterState.FAILED, M2CacheChapterState.IN_PROGRESS,
    M2CacheChapterState.QUEUED, M2CacheChapterState.MISSING) + List(11) { M2CacheChapterState.COMPLETED } +
    listOf(M2CacheChapterState.FAILED, M2CacheChapterState.IN_PROGRESS, M2CacheChapterState.IN_PROGRESS, M2CacheChapterState.MISSING)
private val status = M2CacheBookStatusData(
    sourceId = onlineBook.sourceId, bookId = onlineBook.bookId, tocAvailable = true, chapterCount = 20,
    chapters = queue.mapIndexed { index, state ->
        M2CacheChapterStatusData(chapterIndex = index.toLong(), title = "第 ${index + 1} 章", url = "https://pine.example/${index + 1}",
            state = state, cachedBytes = if (state == M2CacheChapterState.COMPLETED) 70_550 else 0, attempts = 1, maxAttempts = 3)
    }, cachedCount = 12, queuedCount = 1, inProgressCount = 3, completedCount = 12,
    failedCount = 2, cancelledCount = 0, missingCount = 2,
    globalStats = M2CacheGlobalStatsData(entryCount = 12, totalContentBytes = 846_600, queueEntryCount = 6, queuedCount = 1,
        inProgressCount = 3, completedCount = 12, failedCount = 2, cancelledCount = 0),
)

@OptIn(ExperimentalMaterial3Api::class)
internal fun onlineGoldenScenes() = listOf(
    AndroidGoldenScene("m2-global-search", "全局搜索历史、正在搜索、分源同名候选", "山间晚读，两来源") {
        GoldenPage("全局搜索") {
            SearchContent("山间晚读", listOf("山间晚读", "走到远方"), listOf(onlineBook, otherSource), running = true)
        }
    },
    AndroidGoldenScene("m2-online-detail", "在线详情存在最新章、换源与下载入口", "山间晚读；青松小说") {
        GoldenPage("书籍详情") { BookDetailContent(onlineBook, inShelf = false, onDownload = {}, onSwitch = {}) }
    },
    AndroidGoldenScene("m2-source-switch", "阅读器原生 Material 换源 Sheet", "青松小说与墨纸文库的同名书", dialog = true) {
        GoldenTextReader(ReadingAppearance())
        ModalBottomSheet(onDismissRequest = {}) {
            ReadingSourceSheetContent(onlineBook.title, true, {}, {}) {
                SourceSwitchContent(listOf(onlineBook, otherSource).map {
                    M2ChangeBookSourceCandidate(it.sourceId, it.bookId, it.title, it.author)
                })
            }
        }
    },
    AndroidGoldenScene("m2-downloads", "章节下载持续任务，分开展示终态", "12 完成、2 失败、3 进行中、3 未执行") {
        GoldenPage("下载管理") {
            DownloadContent(listOf(DownloadRow(onlineBook, status, working = true)), "共 826 KiB · 可清理 410 KiB")
        }
    },
)
