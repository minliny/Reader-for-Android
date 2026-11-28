package io.reader.rebuild.goldens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.reader.core.protocol.BookshelfEntryData
import com.reader.core.protocol.BookshelfReadingPositionData
import com.reader.core.theme.ReaderIconRegistry
import io.reader.rebuild.design.ReaderAction
import io.reader.rebuild.design.ReaderMessageOverlay
import io.reader.rebuild.design.ReaderTopBar
import io.reader.rebuild.design.RecoveryAction
import io.reader.rebuild.design.RecoveryNoticeState
import io.reader.rebuild.design.TransientToastState
import io.reader.rebuild.features.bookshelf.BookshelfContent
import io.reader.rebuild.features.detail.BookDetailContent
import io.reader.rebuild.features.library.BookReference
import io.reader.rebuild.features.reading.DirectoryContent
import io.reader.rebuild.features.reading.DirectoryEntry
import io.reader.rebuild.features.reading.ReaderPaperFrame
import io.reader.rebuild.features.reading.ReadingAppearance
import io.reader.rebuild.features.reading.ReadingControlsContent
import io.reader.rebuild.features.reading.StaticLayoutReadingSurface
import io.reader.rebuild.features.reading.TextSurfaceChapter
import io.reader.rebuild.features.shell.ReaderHomeMenu

private val localBooks = listOf(
    BookshelfEntryData(
        readingPosition = BookshelfReadingPositionData(2, 88, 1_800_000_000L),
        sourceName = "本地书籍", sourceId = "local", bookId = "golden-pine", title = "山间晚读",
        author = "林江", intro = "雨后的山路、松枝的墨绿，与两位故友的一场长谈。", kind = "TXT",
        addedAt = 1_800_000_000L, sortIndex = 0, currentChapterTitle = "第三章 · 山间回信", chapterCount = 12, readProgress = 21,
    ),
    BookshelfEntryData(
        sourceName = "本地书籍", sourceId = "local", bookId = "golden-travel",
        title = "沿着河流慢慢走到远方的旅人手记", author = "宋渡", kind = "EPUB",
        addedAt = 1_800_000_000L, sortIndex = 1, chapterCount = 9,
    ),
)
private val localBook = BookReference("local", "golden-pine", "山间晚读", "林江",
    intro = "雨后的山路、松枝的墨绿，与两位故友的一场长谈。", kind = "TXT")
private val directory = listOf(
    DirectoryEntry(0, "第一章 · 松间薄雾"),
    DirectoryEntry(1, "第二章 · 风过湖岸"),
    DirectoryEntry(2, "第三章 · 山间回信"),
    DirectoryEntry(3, "第四章 · 夜航的人"),
)
private val passage = TextSurfaceChapter(
    "　　山里刚下过雨。风绕过门廊，松针大把落在石上，一阵又一阵。\n\n" +
        "　　书桌的灯照着一页旧信。有人提到𣗋、Café，还有远方尚未融尽的雪。旅人合上书，走到窗前，" +
        "望见青灰色的水从桥下经过。\n\n　　天色渐渐亮了。山径深处一束清光落在路上，河面像一张展开的熟宣。" +
        "他写道：「等到下一封信抵达的时候，青松仍然站在那座无名的山上。」",
)

internal fun localGoldenScenes() = listOf(
    AndroidGoldenScene("shelf-day", "默认列表，日间", "山间晚读 TXT；旅人手记 EPUB") { GoldenShelf(localBooks) },
    AndroidGoldenScene("shelf-night", "默认列表，夜间", "山间晚读 TXT；旅人手记 EPUB", night = true) { GoldenShelf(localBooks) },
    AndroidGoldenScene("shelf-empty", "确认书架为空", "Core 返回零条记录") { GoldenShelf(emptyList()) },
    AndroidGoldenScene("shelf-recovery", "书架悬浮恢复条", "导入原操作待核对") {
        val state = remember { TransientToastState() }
        ReaderMessageOverlay(true, state, RecoveryNoticeState("golden-import", "导入仍待核对",
            "返回书架后可继续查验原操作。", 2, RecoveryAction("核对", 3)), { _, _, _ -> },
            Modifier.fillMaxSize()) { GoldenShelf(localBooks) }
    },
    AndroidGoldenScene("shelf-narrow-large", "窄屏与大字，标题最多两行", "含长标题的本地 EPUB",
        width = 344, fontScale = 1.4f) { GoldenShelf(localBooks) },
    AndroidGoldenScene("shelf-wide", "宽屏书架与详情分栏", "书架选中山间晚读", width = 820, height = 650) {
        GoldenShelf(localBooks, side = { BookDetailContent(localBook, inShelf = true) })
    },
    AndroidGoldenScene("shelf-cover-grid", "书架封面网格", "山间晚读 TXT；旅人手记 EPUB") {
        GoldenShelf(localBooks, cover = true)
    },
    AndroidGoldenScene("shelf-more-sheet", "书架操作底部 Sheet", "批量管理、本地导入、检查更新、下载管理、书架设置、书架数据管理", dialog = true) {
        GoldenPage("书架") {
            ReaderHomeMenu({}, {}, {}, initialOpen = true)
        }
    },
    AndroidGoldenScene("book-detail", "本地书详情", "山间晚读；林江；TXT") {
        GoldenPage("书籍详情") { BookDetailContent(localBook, inShelf = true) }
    },
    AndroidGoldenScene("book-directory", "本地图书完整目录", "四章，已定位第三章") {
        GoldenPage("目录") { DirectoryContent(directory, selected = 2) }
    },
    AndroidGoldenScene("reader-light", "StaticLayout 正文浅纸", "含 CJK 增补字符与组合字符的小说片段") {
        GoldenTextReader(ReadingAppearance(paperId = "day"))
    },
    AndroidGoldenScene("reader-dark", "StaticLayout 正文深纸", "含 CJK 增补字符与组合字符的小说片段", night = true) {
        GoldenTextReader(ReadingAppearance(paperId = "night"))
    },
    AndroidGoldenScene("reader-quick", "阅读控制岛 Quick", "山间晚读 / 第三章") {
        GoldenTextReader(ReadingAppearance(paperId = "day"), controlsExpanded = false)
    },
    AndroidGoldenScene("reader-full", "阅读控制岛 Full 和八款纸底", "山间晚读 / 第三章") {
        GoldenTextReader(ReadingAppearance(paperId = "day"), controlsExpanded = true)
    },
    AndroidGoldenScene("reader-light-status", "夜间 App 中浅纸状态区域", "暖白纸底",
        night = true, difference = "用真实 ReaderPaperFrame 固定 26dp 状态栏背景；系统状态栏图案和时钟由 Android 绘制。") {
        GoldenTextReader(ReadingAppearance(paperId = "warm"), statusInset = 26.dp)
    },
)

@Composable
private fun GoldenShelf(books: List<BookshelfEntryData>, side: (@Composable () -> Unit)? = null, cover: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        ReaderTopBar("书架") {
            ReaderAction(ReaderIconRegistry.search, "全局搜索", {})
            ReaderAction(ReaderIconRegistry.add, "导入本地书籍", {})
            ReaderHomeMenu({}, {}, {})
        }
        BookshelfContent(books, side = side, initialCover = cover)
    }
}

@Composable
internal fun GoldenPage(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ReaderTopBar(title, onBack = {})
        content()
    }
}

@Composable
internal fun GoldenTextReader(appearance: ReadingAppearance, controlsExpanded: Boolean? = null, statusInset: Dp = 0.dp) {
    ReaderPaperFrame(appearance, Modifier.fillMaxSize(), statusInset) {
        AndroidView(
            factory = { context ->
                StaticLayoutReadingSurface(context).apply { open(passage, 0, appearance.style) }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (controlsExpanded != null) {
            Box(Modifier.align(Alignment.BottomCenter).width(402.dp).padding(12.dp)) {
                ReadingControlsContent("山间晚读", "第三章 · 山间回信", appearance, controlsExpanded)
            }
        }
    }
}
