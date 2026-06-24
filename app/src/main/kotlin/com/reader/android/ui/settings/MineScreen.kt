package com.reader.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.BuildConfig
import com.reader.android.ui.components.ReaderAppTopBar
import com.reader.android.ui.components.ReaderCard
import com.reader.android.ui.components.ReaderChip
import com.reader.android.ui.components.ReaderIconToken
import com.reader.android.ui.components.ReaderIconButton
import com.reader.android.ui.components.ReaderLoadingState
import com.reader.android.ui.components.ReaderPermissionRequiredState
import com.reader.android.ui.components.ReaderSectionHeader
import com.reader.android.ui.components.ReaderSettingsGroup
import com.reader.android.ui.components.ReaderSettingsRow
import com.reader.android.ui.components.asImageVector
import com.reader.android.ui.theme.ReaderTheme

data class SettingsOverviewItem(
    val label: String,
    val value: String,
    val iconToken: ReaderIconToken,
    val tone: SettingsHomeTone
)

data class SettingsQuickEntry(
    val title: String,
    val meta: String,
    val target: SettingsTarget,
    val iconToken: ReaderIconToken,
    val tone: SettingsHomeTone
)

data class SettingsHomeRow(
    val title: String,
    val meta: String,
    val target: SettingsTarget,
    val state: String = "",
    val iconToken: ReaderIconToken,
    val tone: SettingsHomeTone
)

data class SettingsHomeSection(
    val title: String,
    val rows: List<SettingsHomeRow>
)

enum class SettingsTarget {
    SourceManagement,
    RssManagement,
    ReadingPreference,
    CacheManagement,
    General,
    BookshelfSearch,
    SourceSubscription,
    SyncBackup,
    PrivacyPermissions,
    AboutFeedback,
    PrototypeGallery
}

enum class SettingsHomeDisplayState {
    Default,
    LoadingOverview,
    NoBackup,
    PermissionNeeded
}

enum class SettingsHomeTone {
    Blue,
    Orange,
    Green,
    Purple
}

data class SettingsHomeState(
    val displayState: SettingsHomeDisplayState = SettingsHomeDisplayState.Default,
    val overviewTitle: String = "本地概览",
    val overviewItems: List<SettingsOverviewItem> = listOf(
        SettingsOverviewItem("本地书籍", "42", ReaderIconToken.Bookshelf, SettingsHomeTone.Blue),
        SettingsOverviewItem("订阅源", "12", ReaderIconToken.Rss, SettingsHomeTone.Orange),
        SettingsOverviewItem("书源可用", "8/12", ReaderIconToken.SourceSwitch, SettingsHomeTone.Green),
        SettingsOverviewItem("最近备份", "昨天", ReaderIconToken.Clock, SettingsHomeTone.Purple)
    ),
    val quickEntries: List<SettingsQuickEntry> = listOf(
        SettingsQuickEntry("书源管理", "8 个可用", SettingsTarget.SourceManagement, ReaderIconToken.Storage, SettingsHomeTone.Blue),
        SettingsQuickEntry("RSS/订阅管理", "12 个订阅源", SettingsTarget.RssManagement, ReaderIconToken.Rss, SettingsHomeTone.Orange),
        SettingsQuickEntry("阅读偏好", "默认行为", SettingsTarget.ReadingPreference, ReaderIconToken.Directory, SettingsHomeTone.Green),
        SettingsQuickEntry("缓存管理", "1.2 GB", SettingsTarget.CacheManagement, ReaderIconToken.Storage, SettingsHomeTone.Purple)
    ),
    val sections: List<SettingsHomeSection> = listOf(
        SettingsHomeSection(
            title = "全部设置",
            rows = listOf(
                SettingsHomeRow("通用", "语言、启动页、主题跟随系统", SettingsTarget.General, iconToken = ReaderIconToken.Settings, tone = SettingsHomeTone.Blue),
                SettingsHomeRow("阅读", "默认阅读行为、完整阅读设置", SettingsTarget.ReadingPreference, iconToken = ReaderIconToken.Directory, tone = SettingsHomeTone.Orange),
                SettingsHomeRow("书架与搜索", "展示方式、搜索范围、历史记录", SettingsTarget.BookshelfSearch, iconToken = ReaderIconToken.Search, tone = SettingsHomeTone.Green),
                SettingsHomeRow("书源与订阅", "书源、订阅源、来源检测", SettingsTarget.SourceSubscription, "可用", ReaderIconToken.SourceSwitch, SettingsHomeTone.Blue),
                SettingsHomeRow("同步与备份", "本地备份、WebDAV、导入导出", SettingsTarget.SyncBackup, "未设置", ReaderIconToken.Upload, SettingsHomeTone.Purple),
                SettingsHomeRow("隐私与权限", "存储、通知、隐私清理", SettingsTarget.PrivacyPermissions, "待授权", ReaderIconToken.Shield, SettingsHomeTone.Green),
                SettingsHomeRow("关于与反馈", "版本、更新日志、反馈", SettingsTarget.AboutFeedback, iconToken = ReaderIconToken.Info, tone = SettingsHomeTone.Orange)
            )
        )
    ),
    val bottomNavLabels: List<String> = listOf("书架", "发现", "RSS", "设置")
)

object SettingsHomeMapper {
    fun fromFixture(): SettingsHomeState = SettingsHomeState()

    fun loadingOverview(): SettingsHomeState =
        fromFixture().copy(displayState = SettingsHomeDisplayState.LoadingOverview)

    fun noBackup(): SettingsHomeState =
        fromFixture().copy(
            displayState = SettingsHomeDisplayState.NoBackup,
            overviewItems = fromFixture().overviewItems.map { item ->
                if (item.label == "最近备份") item.copy(value = "未设置") else item
            }
        )

    fun permissionNeeded(): SettingsHomeState =
        fromFixture().copy(displayState = SettingsHomeDisplayState.PermissionNeeded)
}

@Composable
fun SettingsRootScreen(
    settingsState: SettingsHomeState = SettingsHomeState(),
    onSearchClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSourceManagementClick: () -> Unit = {},
    onRssManagementClick: () -> Unit = {},
    onGlobalSettingsClick: () -> Unit = {},
    onGeneralSettingsClick: () -> Unit = {},
    onBookshelfSearchSettingsClick: () -> Unit = {},
    onReadingPreferenceClick: () -> Unit = {},
    onCacheManagementClick: () -> Unit = {},
    onPrivacyPermissionsClick: () -> Unit = {},
    onAboutFeedbackClick: () -> Unit = {},
    onSyncBackupClick: () -> Unit = {},
    onWebDavClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onProgressSyncClick: () -> Unit = {},
    onRemoteBooksClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPrototypeGalleryClick: (() -> Unit)? = null
) {
    ReaderTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderAppTopBar(
                title = "设置",
                actions = {
                    ReaderIconButton(
                        icon = ReaderIconToken.Search.asImageVector(),
                        contentDescription = "设置内搜索",
                        onClick = onSearchClick
                    )
                    ReaderIconButton(
                        icon = ReaderIconToken.More.asImageVector(),
                        contentDescription = "更多设置操作",
                        onClick = onMoreClick
                    )
                }
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = ReaderTheme.spacing.lg)
            ) {
                item {
                    SettingsOverviewCard(settingsState)
                }
                item {
                    ReaderSectionHeader(title = "常用管理")
                }
                items(settingsState.quickEntries, key = { it.target.name }) { entry ->
                    SettingsQuickEntryRow(
                        entry = entry,
                        onClick = {
                            dispatchSettingsTarget(
                                target = entry.target,
                                onSourceManagementClick = onSourceManagementClick,
                                onRssManagementClick = onRssManagementClick,
                                onGlobalSettingsClick = onGlobalSettingsClick,
                                onGeneralSettingsClick = onGeneralSettingsClick,
                                onBookshelfSearchSettingsClick = onBookshelfSearchSettingsClick,
                                onReadingPreferenceClick = onReadingPreferenceClick,
                                onCacheManagementClick = onCacheManagementClick,
                                onPrivacyPermissionsClick = onPrivacyPermissionsClick,
                                onAboutFeedbackClick = onAboutFeedbackClick,
                                onSyncBackupClick = onSyncBackupClick,
                                onWebDavClick = onWebDavClick,
                                onBackupClick = onBackupClick,
                                onProgressSyncClick = onProgressSyncClick,
                                onRemoteBooksClick = onRemoteBooksClick,
                                onAboutClick = onAboutClick,
                                onPrototypeGalleryClick = onPrototypeGalleryClick
                            )
                        }
                    )
                }
                settingsState.sections.forEach { section ->
                    item {
                        ReaderSettingsGroup(title = section.title) {
                            section.rows.forEach { row ->
                                ReaderSettingsRow(
                                    title = row.title,
                                    subtitle = row.meta,
                                    leading = { SettingsHomeIcon(token = row.iconToken, tone = row.tone) },
                                    trailing = {
                                        if (row.state.isNotBlank()) {
                                            ReaderChip(text = row.state, selected = row.state == "可用")
                                        }
                                    },
                                    onClick = {
                                        dispatchSettingsTarget(
                                            target = row.target,
                                            onSourceManagementClick = onSourceManagementClick,
                                            onRssManagementClick = onRssManagementClick,
                                            onGlobalSettingsClick = onGlobalSettingsClick,
                                            onGeneralSettingsClick = onGeneralSettingsClick,
                                            onBookshelfSearchSettingsClick = onBookshelfSearchSettingsClick,
                                            onReadingPreferenceClick = onReadingPreferenceClick,
                                            onCacheManagementClick = onCacheManagementClick,
                                            onPrivacyPermissionsClick = onPrivacyPermissionsClick,
                                            onAboutFeedbackClick = onAboutFeedbackClick,
                                            onSyncBackupClick = onSyncBackupClick,
                                            onWebDavClick = onWebDavClick,
                                            onBackupClick = onBackupClick,
                                            onProgressSyncClick = onProgressSyncClick,
                                            onRemoteBooksClick = onRemoteBooksClick,
                                            onAboutClick = onAboutClick,
                                            onPrototypeGalleryClick = onPrototypeGalleryClick
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                if (BuildConfig.DEBUG && onPrototypeGalleryClick != null) {
                    item {
                        ReaderSettingsGroup(title = "调试") {
                            ReaderSettingsRow(
                                title = "UI 原型预览",
                                subtitle = "打开 Reader UI Prototype Gallery",
                                onClick = onPrototypeGalleryClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOverviewCard(settingsState: SettingsHomeState) {
    val overviewItems = if (settingsState.displayState == SettingsHomeDisplayState.NoBackup) {
        settingsState.overviewItems.map { item ->
            if (item.label == "最近备份") item.copy(value = "未设置") else item
        }
    } else {
        settingsState.overviewItems
    }

    ReaderCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ReaderTheme.spacing.screenPadding, vertical = ReaderTheme.spacing.sm),
        contentDescription = "设置本地概览"
    ) {
        ReaderSectionHeader(title = settingsState.overviewTitle, modifier = Modifier.fillMaxWidth())
        when (settingsState.displayState) {
            SettingsHomeDisplayState.LoadingOverview -> {
                ReaderLoadingState(
                    message = "本地概览加载中",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                )
            }
            SettingsHomeDisplayState.PermissionNeeded -> {
                ReaderPermissionRequiredState(
                    title = "需要存储权限",
                    message = "授权后才能统计本地书籍、订阅源和最近备份状态。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                )
            }
            SettingsHomeDisplayState.Default,
            SettingsHomeDisplayState.NoBackup -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ReaderTheme.spacing.sm)
                ) {
                    overviewItems.forEach { item ->
                        SettingsOverviewMetric(item = item, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOverviewMetric(
    item: SettingsOverviewItem,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics { contentDescription = "${item.label}，${item.value}" },
        horizontalAlignment = Alignment.Start
    ) {
        SettingsHomeIcon(token = item.iconToken, tone = item.tone)
        Text(
            text = item.value,
            modifier = Modifier.padding(top = ReaderTheme.spacing.xs),
            color = item.tone.color(),
            style = ReaderTheme.typography.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.label,
            modifier = Modifier.padding(top = ReaderTheme.spacing.xs),
            color = ReaderTheme.colors.bodyText,
            style = ReaderTheme.typography.bookMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.value == "未设置") {
            Text(
                text = "还没有备份记录",
                color = ReaderTheme.colors.bodyText,
                style = ReaderTheme.typography.bookMeta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsQuickEntryRow(
    entry: SettingsQuickEntry,
    onClick: () -> Unit
) {
    ReaderSettingsRow(
        title = entry.title,
        subtitle = entry.meta,
        leading = { SettingsHomeIcon(token = entry.iconToken, tone = entry.tone) },
        trailing = { ReaderChip(text = "常用", selected = true) },
        onClick = onClick
    )
}

@Composable
private fun SettingsHomeIcon(
    token: ReaderIconToken,
    tone: SettingsHomeTone
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = "设置图标，${token.name}" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = token.asImageVector(),
            contentDescription = null,
            tint = tone.color(),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsHomeTone.color(): Color =
    when (this) {
        SettingsHomeTone.Blue -> ReaderTheme.colors.primary
        SettingsHomeTone.Orange -> Color(0xFFB54708)
        SettingsHomeTone.Green -> Color(0xFF1F7A4D)
        SettingsHomeTone.Purple -> Color(0xFF6F4FA3)
    }

private fun dispatchSettingsTarget(
    target: SettingsTarget,
    onSourceManagementClick: () -> Unit,
    onRssManagementClick: () -> Unit,
    onGlobalSettingsClick: () -> Unit,
    onGeneralSettingsClick: () -> Unit,
    onBookshelfSearchSettingsClick: () -> Unit,
    onReadingPreferenceClick: () -> Unit,
    onCacheManagementClick: () -> Unit,
    onPrivacyPermissionsClick: () -> Unit,
    onAboutFeedbackClick: () -> Unit,
    onSyncBackupClick: () -> Unit,
    onWebDavClick: () -> Unit,
    onBackupClick: () -> Unit,
    onProgressSyncClick: () -> Unit,
    onRemoteBooksClick: () -> Unit,
    onAboutClick: () -> Unit,
    onPrototypeGalleryClick: (() -> Unit)?
) {
    when (target) {
        SettingsTarget.SourceManagement -> onSourceManagementClick()
        SettingsTarget.RssManagement -> onRssManagementClick()
        SettingsTarget.General -> onGeneralSettingsClick()
        SettingsTarget.ReadingPreference -> onReadingPreferenceClick()
        SettingsTarget.BookshelfSearch -> onBookshelfSearchSettingsClick()
        SettingsTarget.CacheManagement -> onCacheManagementClick()
        SettingsTarget.SourceSubscription -> onSourceManagementClick()
        SettingsTarget.SyncBackup -> onSyncBackupClick()
        SettingsTarget.PrivacyPermissions -> onPrivacyPermissionsClick()
        SettingsTarget.AboutFeedback -> onAboutFeedbackClick()
        SettingsTarget.PrototypeGallery -> onPrototypeGalleryClick?.invoke()
    }
}

@Composable
fun MineScreen(
    onSourceManagementClick: () -> Unit = {},
    onRssManagementClick: () -> Unit = {},
    onGlobalSettingsClick: () -> Unit = {},
    onGeneralSettingsClick: () -> Unit = {},
    onBookshelfSearchSettingsClick: () -> Unit = {},
    onReadingPreferenceClick: () -> Unit = {},
    onCacheManagementClick: () -> Unit = {},
    onPrivacyPermissionsClick: () -> Unit = {},
    onAboutFeedbackClick: () -> Unit = {},
    onSyncBackupClick: () -> Unit = {},
    onWebDavClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onProgressSyncClick: () -> Unit = {},
    onRemoteBooksClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onPrototypeGalleryClick: (() -> Unit)? = null
) {
    SettingsRootScreen(
        onSourceManagementClick = onSourceManagementClick,
        onRssManagementClick = onRssManagementClick,
        onGlobalSettingsClick = onGlobalSettingsClick,
        onGeneralSettingsClick = onGeneralSettingsClick,
        onBookshelfSearchSettingsClick = onBookshelfSearchSettingsClick,
        onReadingPreferenceClick = onReadingPreferenceClick,
        onCacheManagementClick = onCacheManagementClick,
        onPrivacyPermissionsClick = onPrivacyPermissionsClick,
        onAboutFeedbackClick = onAboutFeedbackClick,
        onSyncBackupClick = onSyncBackupClick,
        onWebDavClick = onWebDavClick,
        onBackupClick = onBackupClick,
        onProgressSyncClick = onProgressSyncClick,
        onRemoteBooksClick = onRemoteBooksClick,
        onAboutClick = onAboutClick,
        onPrototypeGalleryClick = onPrototypeGalleryClick
    )
}
