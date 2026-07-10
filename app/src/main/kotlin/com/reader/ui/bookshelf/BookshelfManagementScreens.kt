package com.reader.ui.bookshelf

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader.ui.tokens.ReaderTypeToken
import com.reader.android.R
import com.reader.ui.shell.LibraryShellFrame
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors

@Composable
fun BookBatchManagementScreen(
    onBack: () -> Unit,
    onMoveGroup: () -> Unit
) {
    val books = remember { bookshelfBatchBooks() }
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            books.forEach { put(it.title, it.selected) }
        }
    }
    val selectedCount = books.count { selected[it.title] == true }

    BookshelfManagementScaffold(
        title = "批量管理",
        onBack = onBack,
        bottom = {
            BookshelfManagementBottomActions(
                secondary = "移动分组",
                primary = "删除所选",
                dangerPrimary = true,
                onSecondary = onMoveGroup,
                onPrimary = {}
            )
        }
    ) {
        item {
            BookshelfBatchSummary(
                selectedCount = selectedCount,
                onSelectAll = { books.forEach { selected[it.title] = true } }
            )
        }
        item {
            BookshelfBookBatchList(
                books = books,
                selected = { selected[it.title] == true },
                onToggle = { book -> selected[book.title] = !(selected[book.title] == true) }
            )
        }
    }
}

@Composable
fun GroupManagementScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val groups = remember { bookshelfGroups() }
    val assignments = remember { bookshelfAssignments() }
    BookshelfManagementScaffold(
        title = "分组管理",
        onBack = onBack,
        bottom = {
            BookshelfManagementBottomActions(
                secondary = "新建分组",
                primary = "完成",
                onSecondary = {},
                onPrimary = onDone
            )
        }
    ) {
        item {
            BookshelfGroupList(groups = groups)
        }
        item {
            BookshelfAssignmentList(assignments = assignments)
        }
    }
}

@Composable
fun LocalImportScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val imports = remember { localImportItems() }
    BookshelfManagementScaffold(
        title = "本地书导入",
        onBack = onBack,
        bottom = {
            BookshelfManagementBottomActions(
                secondary = "继续选择",
                primary = "完成导入",
                onSecondary = {},
                onPrimary = onDone
            )
        }
    ) {
        item {
            LocalImportEntryCard()
        }
        item {
            BookshelfManagementList(title = "导入设置") {
                BookshelfManagementRow(
                    iconRes = R.drawable.reader_ic_folder,
                    title = "导入分组",
                    meta = "默认分组",
                    side = { BookshelfSmallPill(label = "更改") }
                )
                BookshelfManagementDivider()
                BookshelfManagementRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "重复书籍",
                    meta = "保留原书，仅导入新文件",
                    side = { BookshelfSmallPill(label = "更改") }
                )
            }
        }
        item {
            BookshelfImportResultList(imports = imports)
        }
    }
}

@Composable
fun BookshelfSearchSettingsScreen(
    onBack: () -> Unit
) {
    var defaultView by remember { mutableStateOf("封面") }
    var showUpdateBadge by remember { mutableStateOf(true) }
    var mergeSameAuthor by remember { mutableStateOf(true) }
    var searchHistory by remember { mutableStateOf(true) }

    SettingsShellFrame(
        backTopBar = { BookshelfManagementTopBar(title = "书架与搜索", onBack = onBack) },
        settingsContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BookshelfSettingsSection(title = "书架") {
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_grid,
                            title = "默认展示",
                            side = {
                                BookshelfSegment(
                                    options = listOf("封面", "列表"),
                                    selected = defaultView,
                                    onSelected = { defaultView = it }
                                )
                            }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_columns,
                            title = "封面列数",
                            side = { BookshelfStepper(value = "3列") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_folder,
                            title = "默认分组",
                            side = { BookshelfSelectValue(value = "全部") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_badge,
                            title = "显示更新标记",
                            side = {
                                BookshelfSwitch(
                                    checked = showUpdateBadge,
                                    onToggle = { showUpdateBadge = !showUpdateBadge }
                                )
                            }
                        )
                    }
                }
                item {
                    BookshelfSettingsSection(title = "排序与筛选") {
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_sort,
                            title = "书架排序",
                            side = { BookshelfSelectValue(value = "最近更新") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_list,
                            title = "展示范围",
                            side = { BookshelfSelectValue(value = "全部") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_refresh,
                            title = "更新状态",
                            side = { BookshelfSelectValue(value = "不限") }
                        )
                    }
                }
                item {
                    BookshelfSettingsSection(title = "搜索") {
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_search,
                            title = "搜索范围",
                            side = { BookshelfSelectValue(value = "全局") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_sort,
                            title = "结果排序",
                            side = { BookshelfSelectValue(value = "相关度") }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_people,
                            title = "合并同名同作者",
                            side = {
                                BookshelfSwitch(
                                    checked = mergeSameAuthor,
                                    onToggle = { mergeSameAuthor = !mergeSameAuthor }
                                )
                            }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_clock,
                            title = "搜索历史",
                            side = {
                                BookshelfSwitch(
                                    checked = searchHistory,
                                    onToggle = { searchHistory = !searchHistory }
                                )
                            }
                        )
                        BookshelfManagementDivider()
                        BookshelfSettingsRow(
                            iconRes = R.drawable.reader_ic_list,
                            title = "搜索历史数量",
                            side = { BookshelfSelectValue(value = "20条") }
                        )
                    }
                }
                item {
                    BookshelfSettingsActionRow()
                }
            }
        },
        bottomActionHost = {
            BookshelfManagementBottomActions(
                secondary = "取消",
                primary = "保存",
                onSecondary = onBack,
                onPrimary = onBack
            )
        }
    )
}

@Composable
private fun BookshelfManagementScaffold(
    title: String,
    onBack: () -> Unit,
    bottom: @Composable () -> Unit,
    content: LazyListScope.() -> Unit
) {
    LibraryShellFrame(
        backTopBar = { BookshelfManagementTopBar(title = title, onBack = onBack) },
        contentRegion = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        },
        bottomActionHost = { bottom() }
    )
}

@Composable
private fun BookshelfManagementTopBar(title: String, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .padding(top = 6.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                contentDescription = "返回",
                tint = colors.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
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
private fun BookshelfBatchSummary(selectedCount: Int, onSelectAll: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = "已选 ${selectedCount} 本",
                style = bookshelfManagementTitleStyle().copy(fontSize = ReaderTypeToken.SECTION_TITLE.value, lineHeight = 18.sp),
                color = colors.onBackground,
                maxLines = 1
            )
            Text(
                text = "长按书籍或从更多菜单进入，选择后统一移动分组、删除或取消选择。",
                style = bookshelfManagementMetaStyle(),
                color = extra.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        BookshelfSmallPill(label = "全选", onClick = onSelectAll)
    }
}

@Composable
private fun BookshelfBookBatchList(
    books: List<BookshelfManageBook>,
    selected: (BookshelfManageBook) -> Boolean,
    onToggle: (BookshelfManageBook) -> Unit
) {
    BookshelfManagementList(title = "书架书籍") {
        books.forEachIndexed { index, book ->
            BookshelfBookBatchRow(
                book = book,
                selected = selected(book),
                onToggle = { onToggle(book) }
            )
            if (index != books.lastIndex) BookshelfManagementDivider()
        }
    }
}

@Composable
private fun BookshelfBookBatchRow(
    book: BookshelfManageBook,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 66.dp)
            .background(if (selected) colors.primary.copy(alpha = 0.055f) else colors.surface.copy(alpha = 0f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookshelfSelectionToggle(selected = selected)
        BookshelfMiniCover(title = book.title)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = book.title,
                style = bookshelfManagementTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${book.author} · ${book.chapter}",
                style = bookshelfManagementMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BookshelfSmallEmphasis(label = book.group)
    }
}

@Composable
private fun BookshelfGroupList(groups: List<BookshelfGroupItem>) {
    BookshelfManagementList(title = "分组列表") {
        groups.forEachIndexed { index, group ->
            BookshelfManagementRow(
                iconRes = R.drawable.reader_ic_list,
                title = group.name,
                meta = group.meta,
                side = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BookshelfSmallPill(label = group.action)
                        if (index > 0) BookshelfIconPill(iconRes = R.drawable.reader_ic_trash)
                    }
                }
            )
            if (index != groups.lastIndex) BookshelfManagementDivider()
        }
    }
}

@Composable
private fun BookshelfAssignmentList(assignments: List<BookshelfAssignmentItem>) {
    BookshelfManagementList(title = "书籍归属") {
        assignments.forEachIndexed { index, item ->
            BookshelfManagementRow(
                iconRes = R.drawable.reader_ic_book_open,
                title = item.title,
                meta = item.meta,
                side = { BookshelfSmallEmphasis(label = item.group) }
            )
            if (index != assignments.lastIndex) BookshelfManagementDivider()
        }
    }
}

@Composable
private fun LocalImportEntryCard() {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
            .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookshelfIconCircle(iconRes = R.drawable.reader_ic_folder, size = 38)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "选择本地书文件",
                style = bookshelfManagementTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "选择后识别分组并确认导入",
                style = bookshelfManagementMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BookshelfSmallPill(label = "选择")
    }
}

@Composable
private fun BookshelfImportResultList(imports: List<LocalImportItem>) {
    BookshelfManagementList(title = "待导入文件") {
        imports.forEachIndexed { index, item ->
            BookshelfManagementRow(
                iconRes = if (item.tone == BookshelfImportTone.Danger) R.drawable.reader_ic_warning else R.drawable.reader_ic_book_open,
                title = item.title,
                meta = item.meta,
                tone = item.tone,
                side = { BookshelfImportState(label = item.state, tone = item.tone) }
            )
            if (index != imports.lastIndex) BookshelfManagementDivider()
        }
    }
}

@Composable
private fun BookshelfManagementList(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = bookshelfSectionStyle(),
            color = colors.onBackground,
            modifier = Modifier.padding(start = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.92f), ReaderShapes.md)
                .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md),
            content = content
        )
    }
}

@Composable
private fun BookshelfManagementRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    tone: BookshelfImportTone? = null,
    side: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when (tone) {
        BookshelfImportTone.Warn -> extra.accent.copy(alpha = 0.08f)
        BookshelfImportTone.Danger -> colors.error.copy(alpha = 0.07f)
        else -> colors.surface.copy(alpha = 0f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookshelfIconCircle(iconRes = iconRes, size = 30)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = bookshelfManagementTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = meta,
                style = bookshelfManagementMetaStyle(),
                color = extra.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        side()
    }
}

@Composable
private fun BookshelfSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    BookshelfManagementList(title = title, content = content)
}

@Composable
private fun BookshelfSettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    side: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookshelfIconCircle(iconRes = iconRes, size = 30)
        Text(
            text = title,
            style = bookshelfManagementTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        side()
    }
}

@Composable
private fun BookshelfSettingsActionRow() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(colors.error.copy(alpha = 0.08f), ReaderShapes.md)
            .border(1.dp, colors.error.copy(alpha = 0.20f), ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_trash),
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = "清空搜索历史",
            style = bookshelfManagementTitleStyle(),
            color = colors.error,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookshelfSegment(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(readerExtraColors().metaBackground.copy(alpha = 0.72f), ReaderShapes.pill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val active = selected == option
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        ReaderShapes.pill
                    )
                    .clickable { onSelected(option) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = bookshelfControlStyle(),
                    color = if (active) MaterialTheme.colorScheme.onPrimary else readerExtraColors().muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BookshelfStepper(value: String) {
    Row(
        modifier = Modifier
            .background(readerExtraColors().metaBackground.copy(alpha = 0.72f), ReaderShapes.pill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookshelfStepperButton(label = "-")
        Text(
            text = value,
            style = bookshelfControlStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp),
            maxLines = 1
        )
        BookshelfStepperButton(label = "+")
    }
}

@Composable
private fun BookshelfStepperButton(label: String) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = bookshelfControlStyle(), color = readerExtraColors().primaryDark)
    }
}

@Composable
private fun BookshelfSelectValue(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = value,
            style = bookshelfControlStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            painter = painterResource(id = R.drawable.reader_ic_chevron),
            contentDescription = null,
            tint = readerExtraColors().muted,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun BookshelfSwitch(checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .clickable(onClick = onToggle)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.surface, ReaderShapes.pill)
        )
    }
}

@Composable
private fun BookshelfManagementBottomActions(
    secondary: String,
    primary: String,
    dangerPrimary: Boolean = false,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background.copy(alpha = 0.94f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(extra.hairline.copy(alpha = 0.38f))
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BookshelfBottomButton(
                label = secondary,
                primary = false,
                danger = false,
                onClick = onSecondary,
                modifier = Modifier.weight(1f)
            )
            BookshelfBottomButton(
                label = primary,
                primary = true,
                danger = dangerPrimary,
                onClick = onPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BookshelfBottomButton(
    label: String,
    primary: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val background = when {
        danger -> colors.error
        primary -> colors.primary
        else -> colors.surface.copy(alpha = 0.92f)
    }
    val foreground = when {
        primary -> colors.onPrimary
        else -> extra.primaryDark
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(background, ReaderShapes.pill)
            .border(1.dp, if (primary) background else extra.hairline, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Default,
                fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
                lineHeight = 16.sp,
                fontWeight = FontWeight(850)
            ),
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookshelfMiniCover(title: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 52.dp)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.sm)
            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.55f), ReaderShapes.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(1),
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight(900)
            ),
            color = readerExtraColors().primaryDark,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BookshelfSelectionToggle(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(if (selected) colors.primary else colors.surface.copy(alpha = 0.72f), ReaderShapes.pill)
            .border(1.dp, if (selected) colors.primary else extra.hairline, ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                painter = painterResource(id = R.drawable.reader_ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun BookshelfIconCircle(@DrawableRes iconRes: Int, size: Int) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(colors.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().primaryDark,
            modifier = Modifier.size((size * 0.56f).dp)
        )
    }
}

@Composable
private fun BookshelfSmallPill(label: String, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(colors.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = bookshelfControlStyle(),
            color = extra.primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BookshelfIconPill(@DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun BookshelfSmallEmphasis(label: String) {
    Text(
        text = label,
        style = bookshelfControlStyle(),
        color = readerExtraColors().primaryDark,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BookshelfImportState(label: String, tone: BookshelfImportTone) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val foreground = when (tone) {
        BookshelfImportTone.Good -> extra.primaryDark
        BookshelfImportTone.Warn -> extra.accent
        BookshelfImportTone.Danger -> colors.error
    }
    Text(
        text = label,
        style = bookshelfControlStyle(),
        color = foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BookshelfManagementDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.42f))
    )
}

private data class BookshelfManageBook(
    val title: String,
    val author: String,
    val chapter: String,
    val group: String,
    val selected: Boolean
)

private data class BookshelfGroupItem(
    val name: String,
    val meta: String,
    val action: String
)

private data class BookshelfAssignmentItem(
    val title: String,
    val meta: String,
    val group: String
)

private data class LocalImportItem(
    val title: String,
    val meta: String,
    val state: String,
    val tone: BookshelfImportTone
)

private enum class BookshelfImportTone { Good, Warn, Danger }

private fun bookshelfBatchBooks() = listOf(
    BookshelfManageBook("长夜余火", "爱潜水的乌贼", "第 32 章 雨夜", "追更", selected = true),
    BookshelfManageBook("诡秘之主", "爱潜水的乌贼", "第 1426 章", "默认", selected = true),
    BookshelfManageBook("明朝那些事儿", "当年明月", "第 218 章", "本地书", selected = true),
    BookshelfManageBook("三体", "刘慈欣", "65%", "追更", selected = false),
    BookshelfManageBook("人间词话", "王国维", "卷上 · 境界", "默认", selected = false),
    BookshelfManageBook("Android 开发笔记", "本地文档", "Compose Shell 结构", "本地书", selected = false)
)

private fun bookshelfGroups() = listOf(
    BookshelfGroupItem("默认分组", "8 本 · 当前分组", "管理"),
    BookshelfGroupItem("追更", "5 本 · 置顶显示", "管理"),
    BookshelfGroupItem("本地书", "2 本 · 导入书籍", "管理"),
    BookshelfGroupItem("资料", "3 本 · 可重命名", "管理")
)

private fun bookshelfAssignments() = listOf(
    BookshelfAssignmentItem("长夜余火", "爱潜水的乌贼 · 当前分组", "默认分组"),
    BookshelfAssignmentItem("诡秘之主", "爱潜水的乌贼 · 当前分组", "追更"),
    BookshelfAssignmentItem("明朝那些事儿", "当年明月 · 当前分组", "本地书"),
    BookshelfAssignmentItem("三体", "刘慈欣 · 当前分组", "资料")
)

private fun localImportItems() = listOf(
    LocalImportItem("雨夜.epub", "作者已识别 · 加入默认分组", "可导入", BookshelfImportTone.Good),
    LocalImportItem("旧书扫描.txt", "编码 UTF-8 · 章节识别中", "72%", BookshelfImportTone.Warn),
    LocalImportItem("缺失章节.mobi", "格式不支持 · 可移除后重选", "失败", BookshelfImportTone.Danger)
)

private fun bookshelfSectionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun bookshelfManagementTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun bookshelfManagementMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun bookshelfControlStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)
