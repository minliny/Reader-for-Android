package com.reader.ui.book

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reader.android.R
import com.reader.api.Book
import com.reader.ui.demo.demoCoverDrawableRes
import com.reader.ui.demo.demoCoverUrl
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

/**
 * Native route surfaces for `book-detail` and `book-directory`.
 *
 * Canonical source: `frontend-demo/render-runtime.js` `libraryScreen` and
 * `bookDirectoryScreen`, with structural dimensions from
 * `frontend-demo/styles/04-settings-source.css`.
 */
@Composable
fun BookDetailScreen(
    state: BookDetailRouteState = demoBookDetailRouteState(),
    onBack: () -> Unit,
    onContinueReading: () -> Unit,
    onBookDirectory: () -> Unit,
    onSourceSwitch: () -> Unit,
    onRemoveFromBookshelf: () -> Unit
) {
    BookLibraryScaffold(
        title = "书籍详情",
        onBack = onBack,
        bottomBar = {
            BookBottomActionRow(
                primaryLabel = "继续阅读",
                dangerLabel = "移除书架",
                onPrimary = onContinueReading,
                onDanger = onRemoveFromBookshelf
            )
        }
    ) {
        item {
            BookDetailHero(
                state = state,
                onSourceSwitch = onSourceSwitch
            )
        }
        item {
            BookSummaryCard(intro = state.intro)
        }
        item {
            BookChapterPreview(
                chapters = state.previewChapters,
                onBookDirectory = onBookDirectory,
                onOpenChapter = { onContinueReading() }
            )
        }
    }
}

@Composable
fun BookDirectoryScreen(
    state: BookDirectoryRouteState = demoBookDirectoryRouteState(),
    onBack: () -> Unit,
    onOpenChapter: (BookChapterRouteItem) -> Unit,
    onModeChange: (BookDirectoryMode) -> Unit = {}
) {
    var mode by remember(state.mode) { mutableStateOf(state.mode) }
    val visibleChapters = remember(mode, state.chapters) {
        if (mode == BookDirectoryMode.BOOKMARK) {
            state.chapters.filter { it.bookmarked }
        } else {
            state.chapters
        }
    }

    BookLibraryScaffold(title = "书籍目录", onBack = onBack) {
        item {
            BookDirectoryHeader(
                book = state.book,
                chapterCount = state.chapters.size
            )
        }
        item {
            BookDirectoryModeSwitch(
                mode = mode,
                onSelect = {
                    mode = it
                    onModeChange(it)
                }
            )
        }
        item {
            BookDirectoryRows(
                chapters = visibleChapters,
                onOpenChapter = onOpenChapter
            )
        }
    }
}

data class BookDetailRouteState(
    val book: Book,
    val sourceName: String,
    val sourceMeta: String,
    val intro: String,
    val previewChapters: List<BookChapterRouteItem>
)

data class BookDirectoryRouteState(
    val book: Book,
    val chapters: List<BookChapterRouteItem>,
    val mode: BookDirectoryMode = BookDirectoryMode.DIRECTORY
)

data class BookChapterRouteItem(
    val index: Int,
    val title: String,
    val cached: Boolean = false,
    val bookmarked: Boolean = false,
    val current: Boolean = false
)

enum class BookDirectoryMode {
    DIRECTORY,
    BOOKMARK
}

fun demoBookDetailRouteState(book: Book = demoLibraryBook()): BookDetailRouteState =
    BookDetailRouteState(
        book = book,
        sourceName = "优书网",
        sourceMeta = "20 分钟前更新",
        intro = book.intro.ifBlank {
            "灾变后的世界里，旧文明的回声仍在荒野中游荡。长夜之后，余火尚存，新的旅程从雨夜开始。"
        },
        previewChapters = demoBookPreviewChapters()
    )

fun demoBookDirectoryRouteState(book: Book = demoLibraryBook()): BookDirectoryRouteState =
    BookDirectoryRouteState(
        book = book,
        chapters = demoBookDirectoryChapters()
    )

fun demoLibraryBook(): Book =
    Book(
        bookUrl = "fixture://book/long-night",
        name = "长夜余火",
        author = "爱潜水的乌贼",
        coverUrl = demoCoverUrl("long-night"),
        intro = "灾变后的世界里，旧文明的回声仍在荒野中游荡。长夜之后，余火尚存，新的旅程从雨夜开始。",
        kind = "科幻 · 连载",
        wordCount = "83.6 万字",
        latestChapterTitle = "第 32 章 雨夜",
        origin = "优书网"
    )

fun demoBookPreviewChapters(): List<BookChapterRouteItem> = listOf(
    BookChapterRouteItem(index = 0, title = "第 30 章 旧日", cached = true),
    BookChapterRouteItem(index = 1, title = "第 31 章 归途", cached = true, bookmarked = true),
    BookChapterRouteItem(index = 2, title = "第 32 章 雨夜", bookmarked = true, current = true),
    BookChapterRouteItem(index = 3, title = "第 33 章 灯塔")
)

fun demoBookDirectoryChapters(): List<BookChapterRouteItem> =
    demoBookPreviewChapters() + listOf(
        BookChapterRouteItem(index = 4, title = "第 34 章 旧地图", cached = true),
        BookChapterRouteItem(index = 5, title = "第 35 章 夜行"),
        BookChapterRouteItem(index = 6, title = "第 36 章 灯塔之后", bookmarked = true)
    )

@Composable
private fun BookLibraryScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LibraryShellFrame(
        backTopBar = { BookBackTopBar(title = title, onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = if (bottomBar == null) 24.dp else 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        },
        bottomActionHost = {
            bottomBar?.invoke()
        }
    )
}

@Composable
private fun BookBackTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButtonFrame(
            iconRes = R.drawable.reader_ic_chevron_left,
            contentDescription = "返回",
            onClick = onBack
        )
        Text(
            text = title,
            style = ReaderTextStyles.backBarTitle,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun BookDetailHero(
    state: BookDetailRouteState,
    onSourceSwitch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = ReaderShapes.md, clip = false)
            .background(colors.surface, ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BookCoverFrame(
            book = state.book,
            shape = ReaderShapes.sm,
            modifier = Modifier
                .width(86.dp)
                .height(122.dp)
                .shadow(elevation = 8.dp, shape = ReaderShapes.sm, clip = false)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = state.book.name,
                style = ReaderTextStyles.continueTitle,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.book.author.ifBlank { "未知作者" },
                style = ReaderTextStyles.bookAuthor,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BookFact(label = "最新", value = state.book.latestChapterTitle.ifBlank { "第 32 章 雨夜" })
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "书源：${state.sourceName}",
                    style = ReaderTextStyles.tabLabel,
                    color = extra.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SmallPillButton(label = "更换书源", onClick = onSourceSwitch)
            }
            Text(
                text = state.sourceMeta,
                style = ReaderTextStyles.tabLabel,
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BookFact(label: String, value: String) {
    val extra = readerExtraColors()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ReaderTextStyles.tabLabel,
            color = extra.muted,
            modifier = Modifier.width(34.dp),
            maxLines = 1
        )
        Text(
            text = value,
            style = ReaderTextStyles.bookAuthor,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BookSummaryCard(intro: String) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("简介", style = ReaderTextStyles.sectionTitle, color = colors.onBackground)
        Text(
            text = intro,
            style = ReaderTextStyles.emptyBody,
            color = extra.muted,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookChapterPreview(
    chapters: List<BookChapterRouteItem>,
    onBookDirectory: () -> Unit,
    onOpenChapter: (BookChapterRouteItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, ReaderShapes.md)
            .border(1.dp, extra.hairline, ReaderShapes.md)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "章节信息",
                style = ReaderTextStyles.sectionTitle,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )
            InlineRouteButton(
                iconRes = R.drawable.reader_ic_directory,
                label = "完整目录",
                onClick = onBookDirectory
            )
        }
        chapters.forEach { chapter ->
            ChapterRow(
                chapter = chapter,
                onClick = { onOpenChapter(chapter) }
            )
        }
    }
}

@Composable
private fun BookDirectoryHeader(book: Book, chapterCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = book.name,
            style = ReaderTextStyles.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${book.author.ifBlank { "未知作者" }} · 共 $chapterCount 章",
            style = ReaderTextStyles.tabLabel,
            color = readerExtraColors().muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookDirectoryModeSwitch(
    mode: BookDirectoryMode,
    onSelect: (BookDirectoryMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DirectoryModeButton(
            label = "目录",
            selected = mode == BookDirectoryMode.DIRECTORY,
            onClick = { onSelect(BookDirectoryMode.DIRECTORY) },
            modifier = Modifier.weight(1f)
        )
        DirectoryModeButton(
            label = "书签",
            selected = mode == BookDirectoryMode.BOOKMARK,
            onClick = { onSelect(BookDirectoryMode.BOOKMARK) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BookDirectoryRows(
    chapters: List<BookChapterRouteItem>,
    onOpenChapter: (BookChapterRouteItem) -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, extra.hairline, ReaderShapes.lg)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), ReaderShapes.lg)
            .clip(ReaderShapes.lg)
    ) {
        chapters.forEach { chapter ->
            ChapterRow(chapter = chapter, onClick = { onOpenChapter(chapter) })
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: BookChapterRouteItem,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = chapter.title,
            style = ReaderTextStyles.bookAuthor,
            color = if (chapter.current) colors.primary else colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChapterMarkerSlot(
                iconRes = if (chapter.cached) R.drawable.reader_ic_check else R.drawable.reader_ic_download,
                active = chapter.cached,
                contentDescription = if (chapter.cached) "已缓存" else "未缓存"
            )
            ChapterMarkerSlot(
                iconRes = R.drawable.reader_ic_bookmark,
                active = chapter.bookmarked,
                contentDescription = if (chapter.bookmarked) "书签" else "无书签",
                invisibleWhenInactive = true
            )
        }
        if (chapter.current) {
            Text(
                text = "当前",
                style = ReaderTextStyles.tabLabel,
                color = colors.primary,
                modifier = Modifier
                    .background(colors.primary.copy(alpha = 0.1f), ReaderShapes.pill)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        } else {
            Spacer(Modifier.width(0.dp))
        }
    }
}

@Composable
private fun ChapterMarkerSlot(
    @DrawableRes iconRes: Int,
    active: Boolean,
    contentDescription: String,
    invisibleWhenInactive: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val tint = when {
        active -> colors.primary
        invisibleWhenInactive -> Color.Transparent
        else -> extra.muted
    }
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(
                color = if (active) colors.primary.copy(alpha = 0.1f) else extra.metaBackground.copy(alpha = 0.82f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun DirectoryModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .background(
                color = if (selected) extra.primaryDark else extra.metaBackground.copy(alpha = 0.7f),
                shape = ReaderShapes.md
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = ReaderTextStyles.tabLabel,
            color = if (selected) colors.onPrimary else extra.controlInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlineRouteButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 30.dp)
            .background(colors.primary.copy(alpha = 0.1f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(text = label, style = ReaderTextStyles.tabLabel, color = colors.primary)
        }
    }
}

@Composable
private fun SmallPillButton(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(colors.primary.copy(alpha = 0.1f), ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = ReaderTextStyles.tabLabel, color = colors.primary, maxLines = 1)
    }
}

@Composable
private fun BookBottomActionRow(
    primaryLabel: String,
    dangerLabel: String,
    onPrimary: () -> Unit,
    onDanger: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.96f))
            .border(1.dp, extra.hairline)
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BookActionButton(
            label = primaryLabel,
            primary = true,
            onClick = onPrimary,
            modifier = Modifier.weight(1f)
        )
        BookActionButton(
            label = dangerLabel,
            danger = true,
            onClick = onDanger,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BookActionButton(
    label: String,
    primary: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when {
        primary -> colors.primary
        danger -> colors.error.copy(alpha = 0.1f)
        else -> extra.metaBackground
    }
    val content = when {
        primary -> colors.onPrimary
        danger -> colors.error
        else -> colors.onBackground
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(background, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = ReaderTextStyles.continueAction, color = content, maxLines = 1)
    }
}

@Composable
private fun IconButtonFrame(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun BookCoverFrame(
    book: Book,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = modifier
            .clip(shape)
            .background(extra.surfaceSoft, shape)
            .border(1.dp, extra.hairline, shape),
        contentAlignment = Alignment.Center
    ) {
        val coverRes = demoCoverDrawableRes(book.coverUrl)
        if (coverRes != null) {
            Image(
                painter = painterResource(id = coverRes),
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (book.coverUrl.isNotBlank()) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.reader_cover_long_night),
                contentDescription = "${book.name}封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
