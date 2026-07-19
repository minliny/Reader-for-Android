package com.reader.ui.settings

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.reader.android.AppProvider
import com.reader.android.R
import com.reader.android.data.adapter.AuthMethod
import com.reader.android.data.adapter.CoreSlice11Service
import com.reader.android.data.adapter.ReaderCoreSlice11CommandClient
import com.reader.android.data.adapter.Slice11Outcome
import com.reader.android.data.adapter.WebDavCredential
import com.reader.ui.shell.PermissionStatus
import com.reader.ui.shell.ReaderBookCacheUiState
import com.reader.ui.shell.ReaderCoreActionPhase
import com.reader.ui.shell.SettingsShellFrame
import com.reader.ui.shell.WebDavTestStatus
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import com.reader.ui.motion.MotionController

@Composable
fun SettingsGeneralScreen(
    reducedMotion: Boolean,
    onReducedMotionChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    /** App 主题模式（system/light/dark），来自 ReaderUiState.appThemeMode。 */
    appThemeMode: String = "system",
    /** 派发主题模式变更到 reducer（UpdateAppThemeMode）。 */
    onAppThemeModeChange: (String) -> Unit = {},
    /** 行为开关：自动检查更新。 */
    autoUpdate: Boolean = true,
    onAutoUpdateChange: (Boolean) -> Unit = {},
    /** 行为开关：点击底栏回顶部。 */
    backToTop: Boolean = true,
    onBackToTopChange: (Boolean) -> Unit = {},
    /** 行为开关：崩溃日志。 */
    crashLog: Boolean = true,
    onCrashLogChange: (Boolean) -> Unit = {},
    /** 缓存清理回调。 */
    onClearCache: () -> Unit = {},
    /** Core cache.clear 的可见执行状态。 */
    cacheState: ReaderBookCacheUiState = ReaderBookCacheUiState(),
    /** 恢复默认回调。 */
    onRestoreDefault: () -> Unit = {},
    /** 打开系统权限设置页。 */
    onOpenPermissionSettings: () -> Unit = {},
    /** 权限状态。 */
    permissionFileAccess: PermissionStatus = PermissionStatus.UNKNOWN,
    permissionNotifications: PermissionStatus = PermissionStatus.UNKNOWN,
    permissionBattery: PermissionStatus = PermissionStatus.UNKNOWN
) {
    // 主题模式 ↔ 段控件标签映射：system→跟随系统 / light→浅色 / dark→深色。
    val themeLabel = when (appThemeMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    }

    SettingsSubpageScaffold(title = "通用设置", onBack = onBack) {
        item {
            SettingsSubSection(title = "基础偏好") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_palette,
                    title = "App主题",
                    side = {
                        SettingsSubSegment(
                            options = listOf("跟随系统", "浅色", "深色"),
                            selected = themeLabel,
                            onSelected = { label ->
                                val mode = when (label) {
                                    "浅色" -> "light"
                                    "深色" -> "dark"
                                    else -> "system"
                                }
                                onAppThemeModeChange(mode)
                            },
                            reducedMotion = reducedMotion
                        )
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_globe,
                    title = "语言",
                    side = { SettingsSubValue("简体中文", chevron = true) }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_home,
                    title = "启动时打开",
                    side = { SettingsSubValue("书架", chevron = true) }
                )
            }
        }
        item {
            SettingsSubSection(title = "行为与反馈") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "自动检查更新",
                    side = { SettingsSubSwitch(autoUpdate) { onAutoUpdateChange(!autoUpdate) } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_top,
                    title = "点击当前底栏回顶部",
                    side = { SettingsSubSwitch(backToTop) { onBackToTopChange(!backToTop) } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_motion,
                    title = "减少动态效果",
                    side = { SettingsSubSwitch(reducedMotion) { onReducedMotionChange(!reducedMotion) } }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bug,
                    title = "崩溃日志",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge(if (crashLog) "已开启" else "已关闭", if (crashLog) SettingsSubTone.Good else SettingsSubTone.Muted)
                            SettingsSubSwitch(crashLog) { onCrashLogChange(!crashLog) }
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_play,
                    title = "动画效果",
                    side = { SettingsSubValue("标准", chevron = true) }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_trash,
                    title = cacheState.error ?: cacheState.message ?: "缓存清理",
                    onClick = { if (!cacheState.busy) onClearCache() },
                    side = {
                        SettingsSubActionLabel(
                            when (cacheState.phase) {
                                ReaderCoreActionPhase.RUNNING -> "清理中"
                                ReaderCoreActionPhase.SUCCEEDED -> "再次清理"
                                ReaderCoreActionPhase.PARTIAL -> "刷新状态"
                                ReaderCoreActionPhase.FAILED -> "重试"
                                ReaderCoreActionPhase.IDLE -> "清理缓存"
                            }
                        )
                    }
                )
            }
        }
        item {
            SettingsSubSection(title = "系统权限") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_folder,
                    title = "文件访问",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge(permissionLabel(permissionFileAccess), permissionTone(permissionFileAccess))
                            SettingsSubActionLabel("去设置", onClick = onOpenPermissionSettings)
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_bell,
                    title = "通知权限",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge(permissionLabel(permissionNotifications), permissionTone(permissionNotifications))
                            SettingsSubActionLabel("去设置", onClick = onOpenPermissionSettings)
                        }
                    }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_battery,
                    title = "电池优化",
                    side = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            SettingsSubBadge(permissionLabel(permissionBattery), permissionTone(permissionBattery))
                            SettingsSubActionLabel("去设置", onClick = onOpenPermissionSettings)
                        }
                    }
                )
            }
        }
        item {
            SettingsSubDangerAction(
                iconRes = R.drawable.reader_ic_refresh,
                title = "恢复默认",
                meta = "恢复后将重置 App 主题、语言、启动页面和行为偏好。",
                onClick = onRestoreDefault
            )
        }
    }
}

/** 权限状态 → 中文标签。 */
private fun permissionLabel(status: PermissionStatus): String = when (status) {
    PermissionStatus.GRANTED -> "已授权"
    PermissionStatus.DENIED -> "未授权"
    PermissionStatus.UNKNOWN -> "未确定"
}

/** 权限状态 → Badge tone。 */
private fun permissionTone(status: PermissionStatus): SettingsSubTone = when (status) {
    PermissionStatus.GRANTED -> SettingsSubTone.Good
    PermissionStatus.DENIED -> SettingsSubTone.Warn
    PermissionStatus.UNKNOWN -> SettingsSubTone.Muted
}

@Composable
fun AboutFeedbackScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onContribute: () -> Unit = {}
) {
    SettingsSubpageScaffold(title = "关于与反馈", onBack = onBack) {
        item {
            SettingsSubSection(title = "项目信息") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "检查更新",
                    onClick = onCheckUpdate,
                    side = { SettingsSubValue("已是最新") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_code,
                    title = "源码仓库",
                    onClick = onOpenRepo,
                    side = { SettingsSubChevron() }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_link,
                    title = "开源许可",
                    onClick = onOpenLicense,
                    side = { SettingsSubChevron() }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_mail,
                    title = "参与贡献",
                    onClick = onContribute,
                    side = { SettingsSubChevron() }
                )
            }
        }
    }
}

/** Native settings primitives rendered without switch, segment, button, or navigation callbacks. */
@Composable
internal fun SettingsCanonicalReadOnlyContent(mode: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (mode) {
            "general" -> {
                SettingsSubSection(title = "基础偏好") {
                    SettingsSubRow(
                        iconRes = R.drawable.reader_ic_palette,
                        title = "App主题",
                        side = { SettingsSubValue("跟随系统") }
                    )
                    SettingsSubDivider()
                    SettingsSubRow(
                        iconRes = R.drawable.reader_ic_globe,
                        title = "语言",
                        side = { SettingsSubValue("简体中文") }
                    )
                }
                SettingsSubSection(title = "行为与反馈") {
                    SettingsSubRow(
                        iconRes = R.drawable.reader_ic_refresh,
                        title = "自动检查更新",
                        side = { SettingsSubBadge("已开启", SettingsSubTone.Good) }
                    )
                    SettingsSubDivider()
                    SettingsSubRow(
                        iconRes = R.drawable.reader_ic_motion,
                        title = "减少动态效果",
                        side = { SettingsSubValue("跟随系统") }
                    )
                }
            }
            "developer-motion" -> SettingsSubSection(title = "开发者动效") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_motion,
                    title = "动效策略",
                    side = { SettingsSubValue("合同驱动") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_code,
                    title = "Reduced Motion",
                    side = { SettingsSubValue("跟随系统") }
                )
            }
            "about-feedback" -> SettingsSubSection(title = "项目信息") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "检查更新",
                    side = { SettingsSubValue("已是最新") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_code,
                    title = "源码仓库",
                    side = { SettingsSubValue("Reader") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_link,
                    title = "开源许可",
                    side = { SettingsSubValue("开源") }
                )
            }
            else -> error("Unsupported canonical settings mode: $mode")
        }
    }
}

@Composable
fun SyncBackupScreen(
    onBack: () -> Unit,
    /** P3.1: 保存 WebDAV 配置回调（dispatch SaveWebDavConfig）。 */
    onSaveConfig: () -> Unit,
    /** P3.2: 测试 WebDAV 连接回调（dispatch TestWebDavConnection）。 */
    onTestConnection: () -> Unit,
    /** P3.2: 测试状态（从 vm.state.webDavConfig.testStatus 读取）。 */
    testStatus: WebDavTestStatus = WebDavTestStatus.Idle
) {
    val backups = remember { settingsBackupItems() }
    var webDavCredential by remember { mutableStateOf<WebDavCredential?>(null) }

    // P1-6: Load the real WebDAV credential (if any) from the keystore-backed
    // store. The identifier "webdav.default" matches the one used by
    // AndroidWebDavClient + WebDavCredentialProvider so all three (credential
    // UI / webdav.* handlers / credential.resolve) share the same credential.
    LaunchedEffect(Unit) {
        if (AppProvider.isInitialized) {
            runCatching {
                webDavCredential = AppProvider.webDavCredentialStore.load("webdav.default")
            }
        }
    }

    val serverUrl = webDavCredential?.serverUrl ?: "未配置"
    val account = when (val auth = webDavCredential?.auth) {
        is AuthMethod.Basic -> auth.username
        is AuthMethod.Digest -> auth.username
        is AuthMethod.Bearer -> "Bearer Token"
        null -> "未配置"
    }
    val passwordDisplay = if (webDavCredential != null) "******" else "未配置"

    SettingsSubpageScaffold(title = "同步与备份", onBack = onBack) {
        item {
            SettingsSubSection(title = "WebDAV 配置") {
                SettingsInputRow(R.drawable.reader_ic_link, "服务器地址", serverUrl)
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_people, "账号", account)
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_shield, "密码", passwordDisplay)
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_folder, "同步目录", "/ReaderBackup/ReaderAndroid")
            }
        }
        item {
            SettingsSectionActions(
                actions = listOf(
                    SettingsSubAction(R.drawable.reader_ic_refresh, "测试网络连通性", onClick = onTestConnection),
                    SettingsSubAction(R.drawable.reader_ic_check, "保存配置", onClick = onSaveConfig)
                )
            )
        }
        // P3.2: 连通性测试结果区从 testStatus 渲染。
        when (testStatus) {
            is WebDavTestStatus.Idle -> { /* 不渲染 */ }
            is WebDavTestStatus.Testing -> {
                item {
                    SettingsSubSection(title = "连通性测试") {
                        SettingsInputRow(R.drawable.reader_ic_link, "结果", "测试中...")
                    }
                }
            }
            is WebDavTestStatus.Success -> {
                item {
                    SettingsSubSection(title = "连通性测试") {
                        SettingsInputRow(R.drawable.reader_ic_link, "结果", "连接成功 (${testStatus.latencyMs}ms)")
                    }
                }
            }
            is WebDavTestStatus.Error -> {
                item {
                    SettingsSubSection(title = "连通性测试") {
                        SettingsInputRow(R.drawable.reader_ic_link, "结果", "连接失败: ${testStatus.message}")
                    }
                }
            }
        }
        item {
            SettingsBackupList(backups = backups)
        }
    }
}

@Composable
fun WebDavConfigScreen(
    onBack: () -> Unit,
    /** P3.6: 表单字段（从 vm.state.webDavConfig 读取）。 */
    serverUrl: String = "",
    username: String = "",
    password: String = "",
    syncDir: String = "/ReaderBackup/ReaderAndroid",
    /** P3.6: 字段变更回调（dispatch UpdateWebDavServer / UpdateWebDavCredentials）。 */
    onServerUrlChange: (String) -> Unit = {},
    onUsernameChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onSyncDirChange: (String) -> Unit = {},
    /** P3.6: 测试 / 保存回调。 */
    onTestConnection: () -> Unit = {},
    onSaveConfig: () -> Unit = {}
) {
    SettingsSubpageScaffold(title = "WebDAV 配置", onBack = onBack) {
        item {
            SettingsSubSection(title = "连接信息") {
                SettingsInputRow(R.drawable.reader_ic_link, "服务器地址", serverUrl.ifEmpty { "https://dav.example.com/reader/backup" })
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_people, "账号", username.ifEmpty { "未配置" })
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_shield, "密码", if (password.isEmpty()) "未配置" else "******")
                SettingsSubDivider()
                SettingsInputRow(R.drawable.reader_ic_folder, "同步目录", syncDir)
            }
        }
        item {
            SettingsSectionActions(
                actions = listOf(
                    SettingsSubAction(R.drawable.reader_ic_refresh, "测试网络连通性", onClick = onTestConnection),
                    SettingsSubAction(R.drawable.reader_ic_check, "保存配置", onClick = onSaveConfig)
                )
            )
        }
    }
}

@Composable
fun SourceManagementScreen(
    onBack: () -> Unit,
    onImportSource: () -> Unit,
    /** P3.3: 切换单个书源启用的回调。 */
    onToggleSource: (String, Boolean) -> Unit = { _, _ -> },
    /** P3.7: 批量操作回调。 */
    onDetectAll: () -> Unit = {},
    onViewSourceDetail: () -> Unit = {},
    onEditSource: () -> Unit = {},
    onViewSourceLogs: () -> Unit = {},
    /** P3.7: 批量启用开关状态（暂从 vm state 读取，无则默认 true）。 */
    batchEnabled: Boolean = true,
    onBatchEnabledChange: (Boolean) -> Unit = {}
) {
    var sources by remember { mutableStateOf<List<SettingsSourceItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (val outcome = CoreSlice11Service(ReaderCoreSlice11CommandClient()).listSources()) {
            is Slice11Outcome.Failed -> {
                sources = emptyList()
                error = outcome.failure.message
                loading = false
            }
            is Slice11Outcome.Success -> {
                sources = outcome.value.map { source ->
                    SettingsSourceItem(
                        id = source.sourceId,
                        title = source.name,
                        meta = source.displayOrigin,
                        status = if (source.enabled) "已启用" else "已停用",
                        tone = if (source.enabled) SettingsSubTone.Good else SettingsSubTone.Muted,
                        enabled = source.enabled
                    )
                }
                error = null
                loading = false
            }
        }
    }
    SettingsSubpageScaffold(title = "书源管理", onBack = onBack) {
        item {
            SettingsMetricGrid(
                metrics = listOf(
                    SettingsMetric(R.drawable.reader_ic_source, "个书源", sources.size.toString()),
                    SettingsMetric(R.drawable.reader_ic_check, "个启用", sources.count { it.enabled }.toString()),
                    SettingsMetric(R.drawable.reader_ic_warning, "个异常", "—"),
                    SettingsMetric(R.drawable.reader_ic_clock, "检测状态", "未运行")
                )
            )
        }
        item { SettingsSubSearchBox("搜索框：搜索书源名称或域名") }
        item {
            SettingsChipRow(listOf("全部", "已启用", "异常", "未检测", "自定义"), active = "全部")
        }
        item {
            SettingsChipRow(listOf("全部分组", "玄幻书源", "起点导入", "测试书源"), active = "全部分组")
        }
        item {
            SettingsSubSection(title = "批量操作") {
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_refresh,
                    title = "检测",
                    onClick = onDetectAll,
                    side = { SettingsSubActionLabel("开始检测") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_info,
                    title = "详情",
                    onClick = onViewSourceDetail,
                    side = { SettingsSubActionLabel("查看") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_edit,
                    title = "编辑",
                    onClick = onEditSource,
                    side = { SettingsSubActionLabel("编辑") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_log,
                    title = "错误日志",
                    onClick = onViewSourceLogs,
                    side = { SettingsSubActionLabel("查看") }
                )
                SettingsSubDivider()
                SettingsSubRow(
                    iconRes = R.drawable.reader_ic_source,
                    title = "启用开关",
                    side = { SettingsSubSwitch(batchEnabled) { onBatchEnabledChange(!batchEnabled) } }
                )
            }
        }
        when {
            loading -> item {
                Text(
                    text = "正在从 Reader Core 加载书源…",
                    style = settingsSubMetaStyle(),
                    color = readerExtraColors().muted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            error != null -> item {
                Text(
                    text = error ?: "书源加载失败",
                    style = settingsSubMetaStyle(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            sources.isEmpty() -> item {
                Text(
                    text = "暂无书源，请先导入",
                    style = settingsSubMetaStyle(),
                    color = readerExtraColors().muted,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            else -> item { SettingsSourceList(sources = sources, onToggleSource = onToggleSource) }
        }
        item {
            SettingsSubFloatingAction(
                iconRes = R.drawable.reader_ic_add,
                label = "新增",
                onClick = onImportSource
            )
        }
    }
}

@Composable
private fun SettingsSubpageScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    SettingsShellFrame(
        backTopBar = { SettingsSubTopBar(title = title, onBack = onBack) },
        settingsContent = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    )
}

@Composable
private fun SettingsSubTopBar(title: String, onBack: () -> Unit) {
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
private fun SettingsSubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = settingsSubSectionStyle(), color = colors.onBackground)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.82f), ReaderShapes.md)
                .border(1.dp, extra.hairline.copy(alpha = 0.72f), ReaderShapes.md),
            content = content
        )
    }
}

@Composable
private fun SettingsSubRow(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    side: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsSubIcon(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = settingsSubTitleStyle(),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = settingsSubMetaStyle(),
                    color = extra.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        side()
    }
}

@Composable
private fun SettingsInputRow(@DrawableRes iconRes: Int, title: String, value: String) {
    SettingsSubRow(
        iconRes = iconRes,
        title = title,
        side = {
            Text(
                text = value,
                style = settingsSubControlStyle(),
                color = readerExtraColors().muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.48f)
            )
        }
    )
}

@Composable
private fun SettingsSubSegment(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    reducedMotion: Boolean = false
) {
    Row(
        modifier = Modifier
            .background(readerExtraColors().metaBackground.copy(alpha = 0.72f), ReaderShapes.pill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 26.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        ReaderShapes.pill
                    )
                    .clickable {
                        onSelected(option)
                        // 绑定 segment.item.switch 动效。
                        MotionController.start(
                            motionId = "segment.item.switch",
                            from = "segment.previous",
                            to = "segment.next",
                            durationMs = 120L,
                            reducedMotion = reducedMotion
                        )
                    }
                    .padding(horizontal = 7.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = settingsSubControlStyle(),
                    color = if (active) MaterialTheme.colorScheme.onPrimary else readerExtraColors().muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsSubValue(value: String, chevron: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = settingsSubControlStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (chevron) SettingsSubChevron()
    }
}

@Composable
private fun SettingsSubChevron() {
    Icon(
        painter = painterResource(id = R.drawable.reader_ic_chevron),
        contentDescription = null,
        tint = readerExtraColors().muted,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
private fun SettingsSubActionLabel(label: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), ReaderShapes.pill)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = settingsSubControlStyle(),
            color = readerExtraColors().primaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSubSwitch(checked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 22.dp)
            .background(if (checked) colors.primary else extra.hairline.copy(alpha = 0.68f), ReaderShapes.pill)
            .clickable(onClick = onClick)
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
private fun SettingsSubBadge(label: String, tone: SettingsSubTone) {
    val colors = MaterialTheme.colorScheme
    val extra = readerExtraColors()
    val foreground = when (tone) {
        SettingsSubTone.Good -> extra.forest
        SettingsSubTone.Warn -> extra.danger
        SettingsSubTone.Info -> extra.primaryDark
        SettingsSubTone.Muted -> extra.muted
    }
    val background = when (tone) {
        SettingsSubTone.Good -> extra.forest.copy(alpha = 0.10f)
        SettingsSubTone.Warn -> extra.danger.copy(alpha = 0.10f)
        SettingsSubTone.Info -> colors.primary.copy(alpha = 0.10f)
        SettingsSubTone.Muted -> extra.metaBackground.copy(alpha = 0.72f)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 22.dp)
            .background(background, ReaderShapes.pill)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = settingsSubBadgeStyle(), color = foreground, maxLines = 1)
    }
}

@Composable
private fun SettingsSubDangerAction(
    @DrawableRes iconRes: Int,
    title: String,
    meta: String,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(colors.error.copy(alpha = 0.08f), ReaderShapes.md)
            .border(1.dp, colors.error.copy(alpha = 0.20f), ReaderShapes.md)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = colors.error, modifier = Modifier.size(17.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = settingsSubTitleStyle(), color = colors.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, style = settingsSubMetaStyle(), color = colors.error.copy(alpha = 0.74f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsSectionActions(actions: List<SettingsSubAction>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), ReaderShapes.md)
                    .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.md)
                    .clickable(enabled = action.onClick != null, onClick = action.onClick ?: {})
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painter = painterResource(id = action.iconRes), contentDescription = null, tint = readerExtraColors().primaryDark, modifier = Modifier.size(16.dp))
                Text(text = action.title, style = settingsSubControlStyle(), color = readerExtraColors().primaryDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SettingsBackupList(backups: List<SettingsBackupItem>) {
    SettingsSubSection(title = "恢复数据") {
        backups.forEachIndexed { index, item ->
            if (index > 0) SettingsSubDivider()
            SettingsSubRow(
                iconRes = item.iconRes,
                title = item.title,
                meta = "${item.source} · ${item.time} · ${item.type}",
                side = {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsSubBadge(item.badge, item.tone)
                        Text(text = item.size, style = settingsSubMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsMetricGrid(metrics: List<SettingsMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 58.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), ReaderShapes.md)
                            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.md)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsSubIcon(iconRes = metric.iconRes)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = metric.value, style = settingsSubTitleStyle(), color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                            Text(text = metric.label, style = settingsSubMetaStyle(), color = readerExtraColors().muted, maxLines = 1)
                        }
                    }
                }
                repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingsSubSearchBox(placeholder: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f), ReaderShapes.pill)
            .border(1.dp, readerExtraColors().hairline.copy(alpha = 0.62f), ReaderShapes.pill)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = R.drawable.reader_ic_search), contentDescription = null, tint = readerExtraColors().muted, modifier = Modifier.size(17.dp))
        Text(text = placeholder, style = settingsSubControlStyle(), color = readerExtraColors().muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingsChipRow(items: List<String>, active: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(5).forEach { item ->
            val selected = item == active
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 30.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else readerExtraColors().metaBackground.copy(alpha = 0.84f),
                        ReaderShapes.pill
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    style = settingsSubControlStyle(),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else readerExtraColors().navInactive,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsSourceList(
    sources: List<SettingsSourceItem>,
    @Suppress("UNUSED_PARAMETER") onToggleSource: (String, Boolean) -> Unit = { _, _ -> }
) {
    SettingsSubSection(title = "书源列表") {
        sources.forEachIndexed { index, source ->
            if (index > 0) SettingsSubDivider()
            SettingsSubRow(
                iconRes = R.drawable.reader_ic_source_stack,
                title = source.title,
                meta = source.meta,
                side = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SettingsSubBadge(source.status, source.tone)
                        // Core exposes source import/list/delete/export but no
                        // frozen partial enabled-state mutation. Keep this row
                        // read-only instead of mutating an Android shadow copy.
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSubFloatingAction(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(MaterialTheme.colorScheme.primary, ReaderShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text = label, style = settingsSubTitleStyle(), color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

@Composable
private fun SettingsSubIcon(@DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), ReaderShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = readerExtraColors().primaryDark,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingsSubDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(readerExtraColors().hairline.copy(alpha = 0.42f))
    )
}

private data class SettingsSubAction(
    @DrawableRes val iconRes: Int,
    val title: String,
    val onClick: (() -> Unit)? = null
)

private data class SettingsBackupItem(
    @DrawableRes val iconRes: Int,
    val source: String,
    val title: String,
    val time: String,
    val type: String,
    val size: String,
    val badge: String,
    val tone: SettingsSubTone
)

private data class SettingsMetric(
    @DrawableRes val iconRes: Int,
    val label: String,
    val value: String
)

private data class SettingsSourceItem(
    val id: String = "",
    val title: String,
    val meta: String,
    val status: String,
    val tone: SettingsSubTone,
    val enabled: Boolean
)

private enum class SettingsSubTone { Good, Warn, Info, Muted }

private fun settingsBackupItems() = listOf(
    SettingsBackupItem(R.drawable.reader_ic_sync, "WebDAV", "自动备份", "2026-06-23 08:00", "完整备份", "12.8 MB", "最新", SettingsSubTone.Good),
    SettingsBackupItem(R.drawable.reader_ic_folder, "本地", "手动备份", "2026-06-23 10:30", "完整备份", "12.8 MB", "本机", SettingsSubTone.Info),
    SettingsBackupItem(R.drawable.reader_ic_sync, "WebDAV", "夜间备份", "2026-06-21 22:30", "书架与设置", "8.6 MB", "局部", SettingsSubTone.Warn),
    SettingsBackupItem(R.drawable.reader_ic_folder, "本地", "阅读进度快照", "2026-06-20 09:40", "阅读进度", "2.4 MB", "进度", SettingsSubTone.Muted)
)

private fun settingsSubSectionStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(900)
)

private fun settingsSubTitleStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.CHAPTER_TITLE.value,
    lineHeight = 16.sp,
    fontWeight = FontWeight(850)
)

private fun settingsSubMetaStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 14.sp,
    fontWeight = FontWeight(500)
)

private fun settingsSubControlStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.ACTION_LABEL.value,
    lineHeight = 13.sp,
    fontWeight = FontWeight(850)
)

private fun settingsSubBadgeStyle() = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = ReaderTypeToken.TOP_BAR_SUBTITLE.value,
    lineHeight = 12.sp,
    fontWeight = FontWeight(850)
)
