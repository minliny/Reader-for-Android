package com.reader.android.ui.adapter

import com.reader.android.ui.fixtures.ReaderUiFixtures
import com.reader.android.ui.state.ReaderActionState
import com.reader.android.ui.state.ReaderEmptyState
import com.reader.android.ui.state.ReaderListItemState
import com.reader.android.ui.state.ReaderListState
import com.reader.android.ui.state.ReaderScreenState
import com.reader.android.ui.state.ReaderUiState

enum class ReaderIntegrationLevel {
    READY_EXISTING_FLOW,
    READY_FAKE_TO_STATE,
    NEEDS_ADAPTER,
    BLOCKED_BY_CORE_GAP,
    BLOCKED_BY_DESIGN_DECISION
}

enum class ReaderScreenKey(val label: String) {
    BOOKSHELF("Bookshelf"),
    SEARCH("Search"),
    BOOK_DETAIL("BookDetail"),
    READER("ReaderScreen"),
    SOURCE_MANAGEMENT("SourceManagement"),
    SOURCE_DETAIL("SourceDetail"),
    SOURCE_EDIT("SourceEdit"),
    SOURCE_IMPORT("SourceImport"),
    DISCOVER("Discover"),
    RSS("RSS"),
    WEBDAV_CONFIG("WebDAVConfig"),
    BACKUP_SETTINGS("BackupSettings"),
    PROGRESS_SYNC_STATUS("ProgressSyncStatus"),
    GLOBAL_SETTINGS_STATE_PAGE("GlobalSettings / StatePage")
}

data class ReaderAdapterContract(
    val screen: ReaderScreenKey,
    val level: ReaderIntegrationLevel,
    val adapterName: String,
    val fixtureName: String,
    val allowRealDataIntegration: Boolean,
    val blockedReason: String? = null
)

sealed interface ReaderAdapterInput {
    data object Fixture : ReaderAdapterInput
    data class Blocked(val reason: String) : ReaderAdapterInput
}

interface ReaderScreenAdapter {
    val contract: ReaderAdapterContract
    fun map(input: ReaderAdapterInput = ReaderAdapterInput.Fixture): ReaderScreenState
}

object BookshelfAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.BOOKSHELF,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "BookshelfAdapter",
        "bookshelfBooks",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        listScreen(contract, "书架", ReaderUiFixtures.bookshelfBooks.map {
            ReaderListItemState(it.id, it.title, it.author, "${(it.progress * 100).toInt()}%")
        })
}

object SearchAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.SEARCH,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "SearchAdapter",
        "searchResults",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        listScreen(contract, "搜索", ReaderUiFixtures.searchResults.map {
            ReaderListItemState(it.id, it.title, it.author, it.category)
        })
}

object BookDetailAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.BOOK_DETAIL,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "BookDetailAdapter",
        "bookDetail",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState {
        val detail = ReaderUiFixtures.bookDetail
        return contentScreen(contract, "书籍详情", "${detail.title} · ${detail.author}", detail.intro)
    }
}

object ReaderContentAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.READER,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "ReaderContentAdapter",
        "readerChapter",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState {
        val chapter = ReaderUiFixtures.readerChapter
        return contentScreen(contract, chapter.title, chapter.contentPreview, "progress=${chapter.progress}")
    }
}

object SourceManagementAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.SOURCE_MANAGEMENT,
        ReaderIntegrationLevel.READY_EXISTING_FLOW,
        "SourceManagementAdapter",
        "sources",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        listScreen(contract, "书源管理", ReaderUiFixtures.sources.map {
            ReaderListItemState(it.id, it.name, it.group, if (it.enabled) "enabled" else "disabled")
        })
}

object SourceDetailAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.SOURCE_DETAIL,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "SourceDetailAdapter",
        "sources",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState {
        val source = ReaderUiFixtures.sources.first()
        return contentScreen(contract, "书源详情", source.name, source.group)
    }
}

object SourceEditAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.SOURCE_EDIT,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "SourceEditAdapter",
        "sources",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        contentScreen(contract, "编辑书源", "表单状态", "只读 UI fixture")
}

object SourceImportAdapter : ReaderScreenAdapter {
    private const val reason = "Source import method and permission strategy require design decision."

    override val contract = ReaderAdapterContract(
        ReaderScreenKey.SOURCE_IMPORT,
        ReaderIntegrationLevel.BLOCKED_BY_DESIGN_DECISION,
        "SourceImportAdapter",
        "blockedSourceImport",
        allowRealDataIntegration = false,
        blockedReason = reason
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        ReaderScreenState(
            screenKey = contract.screen.name,
            title = "导入书源",
            uiState = ReaderUiState.Disabled(reason),
            summary = "blocked",
            blockedReason = reason
        )
}

object DiscoverAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.DISCOVER,
        ReaderIntegrationLevel.READY_FAKE_TO_STATE,
        "DiscoverAdapter",
        "bookshelfBooks",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        listScreen(contract, "发现", ReaderUiFixtures.bookshelfBooks.map {
            ReaderListItemState(it.id, it.title, it.author, "fixture")
        })
}

object RssAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.RSS,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "RssAdapter",
        "rssSources",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        listScreen(contract, "RSS", ReaderUiFixtures.rssSources.map {
            ReaderListItemState(it.id, it.title, "今日更新 ${it.updateCount} 篇")
        })
}

object WebDavConfigAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.WEBDAV_CONFIG,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "WebDavConfigAdapter",
        "webDavStatus",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState {
        val status = ReaderUiFixtures.webDavStatus
        return contentScreen(contract, "WebDAV 配置", status.serverLabel, "configured=${status.configured}")
    }
}

object BackupSettingsAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.BACKUP_SETTINGS,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "BackupSettingsAdapter",
        "webDavStatus",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        contentScreen(contract, "备份设置", "自动备份: fixture", "WebDAV 备份: fixture")
}

object ProgressSyncStatusAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.PROGRESS_SYNC_STATUS,
        ReaderIntegrationLevel.NEEDS_ADAPTER,
        "ProgressSyncStatusAdapter",
        "globalErrors",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        contentScreen(contract, "进度同步", "同步状态 fixture", "只读状态")
}

object GlobalSettingsStatePageAdapter : ReaderScreenAdapter {
    override val contract = ReaderAdapterContract(
        ReaderScreenKey.GLOBAL_SETTINGS_STATE_PAGE,
        ReaderIntegrationLevel.READY_EXISTING_FLOW,
        "GlobalSettingsStatePageAdapter",
        "globalErrors",
        allowRealDataIntegration = false
    )

    override fun map(input: ReaderAdapterInput): ReaderScreenState =
        contentScreen(contract, "全局设置 / 状态页", "ThemePreferences flow exists", "StatePage fixture")
}

object ReaderScreenAdapterRegistry {
    val all: List<ReaderScreenAdapter> = listOf(
        BookshelfAdapter,
        SearchAdapter,
        BookDetailAdapter,
        ReaderContentAdapter,
        SourceManagementAdapter,
        SourceDetailAdapter,
        SourceEditAdapter,
        SourceImportAdapter,
        DiscoverAdapter,
        RssAdapter,
        WebDavConfigAdapter,
        BackupSettingsAdapter,
        ProgressSyncStatusAdapter,
        GlobalSettingsStatePageAdapter
    )

    val contracts: List<ReaderAdapterContract> = all.map { it.contract }
}

private fun listScreen(
    contract: ReaderAdapterContract,
    title: String,
    items: List<ReaderListItemState>
): ReaderScreenState =
    ReaderScreenState(
        screenKey = contract.screen.name,
        title = title,
        uiState = if (items.isEmpty()) ReaderUiState.Empty else ReaderUiState.ImportSuccess(contract.fixtureName),
        listState = ReaderListState(
            items = items,
            empty = ReaderEmptyState(title = "暂无内容")
        ),
        actions = listOf(ReaderActionState("refresh", "刷新", enabled = false)),
        metadata = metadataFor(contract)
    )

private fun contentScreen(
    contract: ReaderAdapterContract,
    title: String,
    summary: String,
    detail: String
): ReaderScreenState =
    ReaderScreenState(
        screenKey = contract.screen.name,
        title = title,
        uiState = ReaderUiState.ImportSuccess(contract.fixtureName),
        summary = summary,
        metadata = metadataFor(contract) + ("detail" to detail)
    )

private fun metadataFor(contract: ReaderAdapterContract): Map<String, String> =
    mapOf(
        "level" to contract.level.name,
        "fixture" to contract.fixtureName,
        "allowRealDataIntegration" to contract.allowRealDataIntegration.toString()
    )
