package com.reader.android.ui.sync

enum class WebDavAuthState {
    NotConfigured,
    Configured,
    AuthError,
    Offline
}

enum class WebDavSyncState {
    Idle,
    Running,
    Success,
    Failure
}

enum class ProgressSyncState {
    Disabled,
    Idle,
    Running,
    Success,
    Failure,
    Conflict,
    Offline
}

data class WebDavAccountUiModel(
    val endpoint: String,
    val usernameMasked: String,
    val isConfigured: Boolean
)

data class SyncErrorUiState(
    val title: String,
    val message: String,
    val retryable: Boolean = true
)

data class WebDavConfigUiState(
    val account: WebDavAccountUiModel,
    val authState: WebDavAuthState,
    val syncState: WebDavSyncState = WebDavSyncState.Idle,
    val error: SyncErrorUiState? = null,
    val canConnect: Boolean = false,
    val notice: String = "WebDAV 未配置",
    val onConfigureWebDavContract: String = "onConfigureWebDav(endpoint, username, password)",
    val onTestWebDavConnectionContract: String = "onTestWebDavConnection()",
    val onDisconnectWebDavContract: String = "onDisconnectWebDav()"
)

data class BackupSettingsUiState(
    val autoBackupEnabled: Boolean,
    val webDavBackupEnabled: Boolean,
    val lastBackupTime: String,
    val notice: String,
    val onToggleBackupContract: String = "onToggleBackup(enabled)",
    val onRunBackupNowContract: String = "onRunBackupNow()"
)

data class ProgressSyncStatusUiState(
    val isEnabled: Boolean,
    val lastSyncTime: String,
    val syncState: ProgressSyncState,
    val conflictCount: Int = 0,
    val error: SyncErrorUiState? = null,
    val onToggleProgressSyncContract: String = "onToggleProgressSync(enabled)",
    val onResolveSyncConflictContract: String = "onResolveSyncConflict(choice)"
)

data class RemoteWebDavBookUiModel(
    val id: String,
    val title: String,
    val path: String,
    val sizeLabel: String,
    val syncState: WebDavSyncState
)

data class RemoteWebDavBooksUiState(
    val books: List<RemoteWebDavBookUiModel>,
    val isLoading: Boolean = false,
    val emptyMessage: String = "暂无远程书籍",
    val error: SyncErrorUiState? = null,
    val onOpenRemoteBookContract: String = "onOpenRemoteBook(book)"
) {
    val isEmpty: Boolean get() = books.isEmpty() && !isLoading && error == null
}

data class DiscoverItemUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Float? = null
)

data class DiscoverUiState(
    val items: List<DiscoverItemUiModel>,
    val isLoading: Boolean = false,
    val emptyMessage: String = "暂无推荐内容",
    val error: SyncErrorUiState? = null
) {
    val isEmpty: Boolean get() = items.isEmpty() && !isLoading && error == null
}

data class RssFeedUiModel(
    val id: String,
    val title: String,
    val updateCount: Int,
    val enabled: Boolean
)

data class RssArticleUiModel(
    val id: String,
    val feedId: String,
    val title: String,
    val description: String,
    val publishedAt: String
)

data class RssSubscriptionUiState(
    val feeds: List<RssFeedUiModel>,
    val onSubscribeRssContract: String = "onSubscribeRss(feed)",
    val onUnsubscribeRssContract: String = "onUnsubscribeRss(feed)"
)

data class RssListUiState(
    val feeds: List<RssFeedUiModel>,
    val selectedFeedId: String? = null,
    val articles: List<RssArticleUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val emptyMessage: String = "暂无 RSS 订阅",
    val error: SyncErrorUiState? = null,
    val offline: Boolean = false,
    val onRefreshRssContract: String = "onRefreshRss()",
    val onOpenRssArticleContract: String = "onOpenRssArticle(article)"
) {
    val isEmpty: Boolean get() = feeds.isEmpty() && articles.isEmpty() && !isLoading && error == null
}

object DiscoverRssWebDavMapper {
    fun discover(items: List<DiscoverItemUiModel> = DiscoverRssWebDavFixture.discoverItems): DiscoverUiState =
        DiscoverUiState(items = items)

    fun rssList(
        feeds: List<RssFeedUiModel> = DiscoverRssWebDavFixture.rssFeeds,
        articles: List<RssArticleUiModel> = DiscoverRssWebDavFixture.rssArticles,
        selectedFeedId: String? = DiscoverRssWebDavFixture.rssFeeds.firstOrNull()?.id
    ): RssListUiState =
        RssListUiState(feeds = feeds, selectedFeedId = selectedFeedId, articles = articles)

    fun rssLoading(): RssListUiState =
        RssListUiState(feeds = emptyList(), isLoading = true, emptyMessage = "正在刷新 RSS")

    fun rssEmpty(): RssListUiState =
        RssListUiState(feeds = emptyList(), articles = emptyList())

    fun rssError(message: String): RssListUiState =
        RssListUiState(
            feeds = emptyList(),
            error = SyncErrorUiState("RSS 加载失败", message)
        )

    fun rssOffline(): RssListUiState =
        RssListUiState(feeds = emptyList(), offline = true, error = SyncErrorUiState("当前离线", "RSS 无法刷新"))

    fun subscriptions(feeds: List<RssFeedUiModel> = DiscoverRssWebDavFixture.rssFeeds): RssSubscriptionUiState =
        RssSubscriptionUiState(feeds = feeds)

    fun webDavNotConfigured(): WebDavConfigUiState =
        WebDavConfigUiState(
            account = WebDavAccountUiModel(endpoint = "", usernameMasked = "", isConfigured = false),
            authState = WebDavAuthState.NotConfigured
        )

    fun webDavConfigured(): WebDavConfigUiState =
        WebDavConfigUiState(
            account = WebDavAccountUiModel(
                endpoint = "fixture-endpoint",
                usernameMasked = "fi***re",
                isConfigured = true
            ),
            authState = WebDavAuthState.Configured,
            syncState = WebDavSyncState.Success,
            canConnect = true,
            notice = "WebDAV 已配置"
        )

    fun webDavAuthError(): WebDavConfigUiState =
        webDavConfigured().copy(
            authState = WebDavAuthState.AuthError,
            syncState = WebDavSyncState.Failure,
            error = SyncErrorUiState("WebDAV 授权失败", "请重新检查账号或应用密码"),
            canConnect = false,
            notice = "WebDAV 授权失败"
        )

    fun webDavSyncRunning(): WebDavConfigUiState =
        webDavConfigured().copy(syncState = WebDavSyncState.Running, notice = "正在同步")

    fun backup(enabled: Boolean = true): BackupSettingsUiState =
        BackupSettingsUiState(
            autoBackupEnabled = enabled,
            webDavBackupEnabled = enabled,
            lastBackupTime = if (enabled) "UI fixture" else "未备份",
            notice = if (enabled) "备份已开启" else "备份已关闭"
        )

    fun progressSync(state: ProgressSyncState = ProgressSyncState.Idle): ProgressSyncStatusUiState =
        ProgressSyncStatusUiState(
            isEnabled = state != ProgressSyncState.Disabled,
            lastSyncTime = if (state == ProgressSyncState.Disabled) "未开启" else "UI fixture",
            syncState = state,
            conflictCount = if (state == ProgressSyncState.Conflict) 1 else 0,
            error = when (state) {
                ProgressSyncState.Failure -> SyncErrorUiState("同步失败", "稍后重试")
                ProgressSyncState.Conflict -> SyncErrorUiState("同步冲突", "请选择保留本地或远程进度")
                ProgressSyncState.Offline -> SyncErrorUiState("当前离线", "恢复网络后再同步")
                else -> null
            }
        )

    fun remoteBooks(
        books: List<RemoteWebDavBookUiModel> = DiscoverRssWebDavFixture.remoteBooks
    ): RemoteWebDavBooksUiState =
        RemoteWebDavBooksUiState(books = books)
}

object DiscoverRssWebDavFixture {
    val discoverItems = listOf(
        DiscoverItemUiModel("discover-local-1", "深空信号", "科幻 · 本地书籍", 0.25f),
        DiscoverItemUiModel("discover-local-2", "纸上群山", "幻想 · 本地书籍", 0.6f),
        DiscoverItemUiModel("discover-local-3", "雨线手记", "随笔 · UI fixture", null)
    )

    val rssFeeds = listOf(
        RssFeedUiModel("rss-feed-1", "订阅源 1", 2, enabled = true),
        RssFeedUiModel("rss-feed-2", "订阅源 2", 0, enabled = false)
    )

    val rssArticles = listOf(
        RssArticleUiModel(
            id = "rss-article-1",
            feedId = "rss-feed-1",
            title = "深空信号更新",
            description = "来自订阅源的章节更新与说明。",
            publishedAt = "UI fixture"
        )
    )

    val remoteBooks = listOf(
        RemoteWebDavBookUiModel(
            id = "remote-book-1",
            title = "远程书籍 A",
            path = "/reader/books/remote-a.txt",
            sizeLabel = "128 KB",
            syncState = WebDavSyncState.Success
        ),
        RemoteWebDavBookUiModel(
            id = "remote-book-2",
            title = "远程书籍 B",
            path = "/reader/books/remote-b.epub",
            sizeLabel = "1.2 MB",
            syncState = WebDavSyncState.Idle
        )
    )
}
