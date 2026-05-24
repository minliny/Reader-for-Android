package com.reader.android.ui.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reader.android.ui.appScreens
import com.reader.android.ui.bookshelf.BookshelfScreen
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderEmptyState
import com.reader.android.ui.components.ReaderErrorState
import com.reader.android.ui.components.ReaderListItem
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderMainTab
import com.reader.android.ui.components.ReaderMainTabBar
import com.reader.android.ui.components.ReaderOfflineState
import com.reader.android.ui.components.ReaderPermissionRequiredState
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.discover.DiscoverScreen
import com.reader.android.ui.discover.RssDetailScreen
import com.reader.android.ui.discover.RssListScreen
import com.reader.android.ui.discover.RssSubscriptionManagementScreen
import com.reader.android.ui.reader.ReaderScreen
import com.reader.android.ui.settings.BackupSettingsScreen
import com.reader.android.ui.settings.ProgressSyncStatusScreen
import com.reader.android.ui.settings.RemoteWebDavBooksScreen
import com.reader.android.ui.settings.WebDavConfigScreen
import com.reader.android.ui.theme.ReaderTheme

@Composable
fun ReaderPrototypeGallery() {
    var selectedEntry by remember { mutableStateOf(ReaderPrototypeCatalog.entries.first()) }

    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(title = "UI 原型预览")
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = ReaderTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
            ) {
                item {
                    PrototypeGalleryIntro(selectedEntry)
                }
                ReaderPrototypeCatalog.groupedEntries.forEach { (group, entries) ->
                    item { ReaderSectionHeader(title = group.title) }
                    items(entries, key = { it.id }) { entry ->
                        PrototypeEntryRow(
                            entry = entry,
                            selected = entry.id == selectedEntry.id,
                            onClick = { selectedEntry = entry }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.md))
                    ReaderSectionHeader(title = "当前预览")
                    ReaderPrototypeSurface(selectedEntry)
                    Spacer(modifier = Modifier.height(ReaderTheme.spacing.lg))
                }
            }
        }
    }
}

@Preview(
    name = "Reader UI Prototype Gallery",
    widthDp = 390,
    heightDp = 844,
    showBackground = true
)
@Composable
fun ReaderPrototypeGalleryPreview() {
    ReaderPrototypeGallery()
}

@Composable
private fun PrototypeGalleryIntro(selectedEntry: ReaderPrototypeEntry) {
    ReaderCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm)
    ) {
        Text("原型目录", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
        Text(
            "共 ${ReaderPrototypeCatalog.entries.size} 个页面，全部使用 UI fixture，不接真实网络。",
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.stateMessage
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        ReaderChip(text = "当前：${selectedEntry.title}", selected = true)
    }
}

@Composable
private fun PrototypeEntryRow(
    entry: ReaderPrototypeEntry,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        ReaderCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ReaderTheme.spacing.screenPadding),
            onClick = onClick,
            contentDescription = "当前选中原型，${entry.title}"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.bookTitle)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(entry.description, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
                }
                ReaderChip(text = "已选", selected = true)
            }
        }
    } else {
        ReaderListItem(
            title = entry.title,
            subtitle = entry.description,
            contentDescription = "打开原型，${entry.title}",
            onClick = onClick
        )
    }
}

@Composable
fun ReaderPrototypeSurface(entry: ReaderPrototypeEntry) {
    when (entry.id) {
        "bookshelf-cover" -> BookshelfScreen(bookshelfState = ReaderPrototypeFixtures.bookshelfCover)
        "bookshelf-list" -> BookshelfScreen(bookshelfState = ReaderPrototypeFixtures.bookshelfList)
        "bookshelf-empty" -> BookshelfScreen(bookshelfState = ReaderPrototypeFixtures.bookshelfEmpty)
        "reader-base" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[0])
        "reader-search" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[1])
        "reader-auto-scroll" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[2])
        "reader-replace" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[3])
        "reader-night" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[4])
        "reader-directory" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[5])
        "reader-tts" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[6])
        "reader-appearance" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[7])
        "reader-settings" -> ReaderScreen(runtimeState = ReaderPrototypeFixtures.readerStates[8])
        "discover-home" -> DiscoverScreen(discoverState = ReaderPrototypeFixtures.discover)
        "rss-list" -> RssListScreen(rssState = ReaderPrototypeFixtures.rssList)
        "rss-detail" -> RssDetailScreen(article = ReaderPrototypeFixtures.rssList.articles.first())
        "rss-subscriptions" -> RssSubscriptionManagementScreen(subscriptionState = ReaderPrototypeFixtures.rssSubscriptions)
        "webdav-config" -> WebDavConfigScreen(webDavState = ReaderPrototypeFixtures.webDavConfigured)
        "backup-settings" -> BackupSettingsScreen(backupState = ReaderPrototypeFixtures.backupEnabled)
        "progress-sync" -> ProgressSyncStatusScreen(progressSyncState = ReaderPrototypeFixtures.progressSyncConflict)
        "remote-webdav-books" -> RemoteWebDavBooksScreen(remoteState = ReaderPrototypeFixtures.remoteBooks)
        "state-loading" -> ReaderLoadingState(message = "加载中")
        "state-empty" -> ReaderEmptyState(title = "暂无内容", message = "请选择导入本地书或搜索书籍", actionText = "去搜索", onActionClick = {})
        "state-error" -> ReaderErrorState(title = "加载失败", message = "这是原型错误状态", retryText = "重试", onRetryClick = {})
        "state-offline" -> ReaderOfflineState(retryText = "重试", onRetryClick = {})
        "state-permission" -> ReaderPermissionRequiredState(title = "需要权限", message = "授权后才能继续读取本地文件", onActionClick = {})
        else -> ReaderPrototypeCard(entry)
    }
}

@Composable
private fun ReaderPrototypeCard(entry: ReaderPrototypeEntry) {
    ReaderCard(modifier = Modifier.fillMaxWidth()) {
        Text(entry.title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.pageTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Text(entry.group.title, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.bookMeta)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        when (entry.id) {
            "app-shell" -> AppShellPrototype()
            "search-home" -> SearchPrototype("搜索首页", "输入关键词开始搜索")
            "search-results" -> SearchPrototype("搜索结果", ReaderPrototypeFixtures.searchResults.results.joinToString { it.title })
            "search-empty" -> SearchPrototype("搜索空状态", ReaderPrototypeFixtures.searchEmpty.emptyMessage)
            "search-error" -> SearchPrototype("搜索错误状态", ReaderPrototypeFixtures.searchError.errorMessage.orEmpty())
            "book-detail" -> DetailPrototype(includeToc = false)
            "book-detail-toc" -> DetailPrototype(includeToc = true)
            "source-list" -> SourcePrototype("书源管理列表", "Fixture 书源 A · 已启用")
            "source-detail" -> SourcePrototype("书源详情", "搜索规则 / 目录规则 / 正文规则")
            "source-edit-import" -> SourcePrototype("书源编辑/导入状态", "空 JSON 本地提示，URL 状态校验")
            "source-test-disabled-error" -> SourcePrototype("书源测试/禁用/错误状态", "loading / success / failure / disabled")
            "sync-error" -> SyncErrorPrototype()
            "global-settings" -> GlobalSettingsPrototype()
            else -> Text("仅使用 UI fixture", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
        }
    }
}

@Composable
private fun AppShellPrototype() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("正式主模块", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        ReaderMainTabBar(
            tabs = appScreens.map { screen ->
                ReaderMainTab(
                    label = screen.label,
                    contentDescription = screen.label,
                    icon = screen.icon
                )
            },
            selectedIndex = 0,
            onTabSelected = {}
        )
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.xs)
        ) {
            appScreens.forEach { screen ->
                ReaderChip(text = screen.label, selected = screen.label == "书架")
            }
        }
    }
}

@Composable
private fun SearchPrototype(title: String, message: String) {
    Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
    Spacer(modifier = Modifier.height(ReaderTheme.spacing.xs))
    Text(message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
}

@Composable
private fun DetailPrototype(includeToc: Boolean) {
    val detail = ReaderPrototypeFixtures.bookDetail.detail
    Text(detail?.title.orEmpty(), color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
    Text(detail?.intro.orEmpty(), color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
    if (includeToc && detail != null) {
        Spacer(modifier = Modifier.height(ReaderTheme.spacing.sm))
        ReaderChip(text = "${detail.tocPreview.chapterCount} 章 · ${detail.tocPreview.latestChapterTitle}", selected = true)
    }
}

@Composable
private fun SourcePrototype(title: String, message: String) {
    Text(title, color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
    Text(message, color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
}

@Composable
private fun SyncErrorPrototype() {
    ReaderErrorState(
        title = ReaderPrototypeFixtures.webDavAuthError.error?.title ?: "同步错误",
        message = ReaderPrototypeFixtures.webDavAuthError.error?.message
    )
}

@Composable
private fun GlobalSettingsPrototype() {
    Text("全局设置", color = ReaderTheme.colors.controlInk, style = ReaderTheme.typography.sectionTitle)
    Text("阅读设置、同步入口、关于入口的 prototype 占位。", color = ReaderTheme.colors.bodyText, style = ReaderTheme.typography.stateMessage)
}
