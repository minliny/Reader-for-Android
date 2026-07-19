package com.reader.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader.android.R
import com.reader.ui.shell.ReaderContext
import com.reader.ui.shell.ReaderUiIntent
import com.reader.ui.theme.ReaderShapes
import com.reader.ui.theme.ReaderTextStyles
import com.reader.ui.theme.readerExtraColors
import io.reader.ui.contract.ReaderAppearanceFont
import io.reader.ui.contract.ReaderAppearanceSpecRegistry
import io.reader.ui.contract.ReaderAppearanceStepper
import io.reader.ui.contract.ReaderAppearanceTheme
import kotlin.math.pow
import kotlin.math.round

/** Reader 2 / Full / AppearanceContent 的共享投影，供两套历史全屏入口复用。 */
internal val readerFullAppearanceFonts: List<ReaderAppearanceFont> =
    ReaderAppearanceSpecRegistry.fonts.filterNot { it.importAction }

internal val readerFullAppearanceThemes: List<ReaderAppearanceTheme> =
    ReaderAppearanceSpecRegistry.themes

internal fun readerFullAppearanceStepper(id: String): ReaderAppearanceStepper =
    requireNotNull(ReaderAppearanceSpecRegistry.stepper(id)) {
        "Missing Reader Appearance stepper: $id"
    }

internal fun nextReaderAppearanceValue(current: Float, spec: ReaderAppearanceStepper): Float {
    val raw = if (current + spec.step > spec.maximum + 0.0001) {
        spec.minimum
    } else {
        (current + spec.step).coerceAtMost(spec.maximum)
    }
    val factor = 10.0.pow(spec.precision)
    return (round(raw * factor) / factor).toFloat()
}

/**
 * W4: 字体设置全屏页（reader-full-font）。
 * 从阅读器外观面板"自定义字体"入口推入。渲染字号/行距/字距/字体选择/自定义字体管理。
 */
@Composable
fun ReaderFontSettingsScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    val fontSizeSpec = readerFullAppearanceStepper("fontSize")
    val lineHeightSpec = readerFullAppearanceStepper("lineHeight")
    val letterSpacingSpec = readerFullAppearanceStepper("letterSpacing")
    FullSettingsScaffold(title = "字体设置", onBack = onBack) {
        FullSettingsSection("字号") {
            FullSettingsRow(title = "正文字号", value = "${(context?.fontSize ?: fontSizeSpec.defaultValue.toFloat()).toInt()}px")
            FullSettingsDivider()
            FullSettingsRow(title = "字号步进", value = "${fontSizeSpec.step.toInt()}px")
        }
        FullSettingsSection("字体族") {
            var selectedFontId by remember { mutableStateOf(ReaderAppearanceSpecRegistry.defaults.fontId) }
            readerFullAppearanceFonts.forEachIndexed { index, font ->
                FullSettingsRow(
                    title = font.label,
                    value = if (font.id == selectedFontId) "已选" else "",
                    onClick = {
                        selectedFontId = font.id
                        dispatch(ReaderUiIntent.SetReaderChoice("fontFamily", font.id))
                    }
                )
                if (index < readerFullAppearanceFonts.lastIndex) FullSettingsDivider()
            }
        }
        FullSettingsSection("自定义字体") {
            FullSettingsRow(title = "已导入字体", value = "2 个", onClick = { })
            FullSettingsDivider()
            FullSettingsRow(
                title = ReaderAppearanceSpecRegistry.fonts.first { it.importAction }.label,
                value = "",
                onClick = { }
            )
        }
        FullSettingsSection("排版微调") {
            var lineSpacing by remember {
                mutableStateOf(context?.lineSpacing ?: lineHeightSpec.defaultValue.toFloat())
            }
            var letterSpacing by remember { mutableStateOf(letterSpacingSpec.defaultValue.toFloat()) }
            FullSettingsRow(
                title = lineHeightSpec.label,
                value = String.format("%.${lineHeightSpec.precision}f", lineSpacing),
                onClick = {
                    lineSpacing = nextReaderAppearanceValue(lineSpacing, lineHeightSpec)
                    dispatch(ReaderUiIntent.UpdateReaderTypography(
                        fontSize = context?.fontSize ?: fontSizeSpec.defaultValue.toFloat(),
                        lineSpacing = lineSpacing,
                        pageMargin = context?.pageMargin ?: 16f
                    ))
                }
            )
            FullSettingsDivider()
            FullSettingsRow(
                title = letterSpacingSpec.label,
                value = String.format("%.${letterSpacingSpec.precision}f", letterSpacing),
                onClick = {
                    letterSpacing = nextReaderAppearanceValue(letterSpacing, letterSpacingSpec)
                    dispatch(ReaderUiIntent.SetReaderChoice("letterSpacing", letterSpacing.toString()))
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * W4: 主题设置全屏页（reader-full-theme）。
 * 从阅读器外观面板"阅读主题"入口推入。渲染主题色板/日夜切换/自定义背景。
 */
@Composable
fun ReaderThemeSettingsScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    dispatch: (ReaderUiIntent) -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    FullSettingsScaffold(title = "主题设置", onBack = onBack) {
        FullSettingsSection("阅读主题") {
            val currentThemeId = context?.themeId ?: ReaderAppearanceSpecRegistry.defaults.dayThemeId
            readerFullAppearanceThemes.forEach { theme ->
                FullSettingsRow(
                    title = theme.label,
                    value = if (currentThemeId == theme.id) "已选" else "",
                    onClick = {
                        dispatch(ReaderUiIntent.UpdateReaderTheme(themeId = theme.id))
                    }
                )
            }
        }
        FullSettingsSection("自定义主题") {
            FullSettingsRow(title = "新建自定义主题", value = "", onClick = { onNavigate("reader-full-theme-edit") })
            FullSettingsDivider()
            FullSettingsRow(title = "导入主题", value = "", onClick = { })
        }
        FullSettingsSection("背景") {
            FullSettingsRow(title = "纯色背景", value = "默认", onClick = { })
            FullSettingsDivider()
            FullSettingsRow(title = "图片背景", value = "无", onClick = { })
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * W4: 主题编辑全屏页（reader-full-theme-edit）。
 * 从主题设置页"新建自定义主题"入口推入。渲染主题颜色编辑器/预览/保存。
 */
@Composable
fun ReaderThemeEditScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    var themeName by remember { mutableStateOf("自定义主题") }
    var bgColor by remember { mutableStateOf("#F8F4EC") }
    var textColor by remember { mutableStateOf("#1F1B17") }
    var accentColor by remember { mutableStateOf("#366179") }

    FullSettingsScaffold(title = "主题编辑", onBack = onBack) {
        FullSettingsSection("主题信息") {
            FullSettingsRow(title = "主题名称", value = themeName, onClick = {
                themeName = "自定义主题-${System.currentTimeMillis() % 1000}"
            })
        }
        FullSettingsSection("颜色") {
            FullSettingsRow(title = "背景色", value = bgColor, onClick = {
                bgColor = listOf("#F8F4EC", "#FBF0DF", "#E7F0E2", "#E9F1F4").random()
            })
            FullSettingsDivider()
            FullSettingsRow(title = "文字颜色", value = textColor, onClick = {
                textColor = listOf("#1F1B17", "#2B241D", "#332C25").random()
            })
            FullSettingsDivider()
            FullSettingsRow(title = "强调色", value = accentColor, onClick = {
                accentColor = listOf("#366179", "#F48B13", "#367A4D").random()
            })
        }
        FullSettingsSection("预览") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color(
                            android.graphics.Color.parseColor(bgColor)
                        ),
                        shape = ReaderShapes.md
                    )
                    .border(1.dp, readerExtraColors().hairline, ReaderShapes.md)
                    .clip(ReaderShapes.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这是一段预览文字",
                    style = ReaderTextStyles.readerBody,
                    color = androidx.compose.ui.graphics.Color(
                        android.graphics.Color.parseColor(textColor)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * W4: 版面设置全屏页（reader-full-layout）。
 * 从阅读器外观面板"页面空间"入口推入。渲染边距/缩进/分栏/翻页区域。
 */
@Composable
fun ReaderLayoutSettingsScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    var pageMargin by remember { mutableStateOf(context?.pageMargin ?: 16f) }
    var indent by remember { mutableStateOf(2) }
    var columnMode by remember { mutableStateOf("单栏") }

    FullSettingsScaffold(title = "版面设置", onBack = onBack) {
        FullSettingsSection("页面空间") {
            FullSettingsRow(
                title = "页边距",
                value = "${pageMargin.toInt()}dp",
                onClick = {
                    pageMargin = if (pageMargin >= 32f) 8f else pageMargin + 4f
                    dispatch(ReaderUiIntent.UpdateReaderTypography(
                        fontSize = context?.fontSize ?: 18f,
                        lineSpacing = context?.lineSpacing
                            ?: readerFullAppearanceStepper("lineHeight").defaultValue.toFloat(),
                        pageMargin = pageMargin
                    ))
                }
            )
            FullSettingsDivider()
            FullSettingsRow(
                title = "段落缩进",
                value = "$indent 字符",
                onClick = { indent = if (indent >= 4) 0 else indent + 1 }
            )
        }
        FullSettingsSection("分栏") {
            val columns = listOf("单栏", "双栏", "自适应")
            columns.forEach { label ->
                FullSettingsRow(
                    title = label,
                    value = if (columnMode == label) "已选" else "",
                    onClick = { columnMode = label }
                )
            }
        }
        FullSettingsSection("翻页区域") {
            FullSettingsRow(title = "左侧区域", value = "上一页", onClick = { })
            FullSettingsDivider()
            FullSettingsRow(title = "右侧区域", value = "下一页", onClick = { })
            FullSettingsDivider()
            FullSettingsRow(title = "中间区域", value = "菜单", onClick = { })
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * W4: 翻页设置全屏页（reader-full-page-turn）。
 * 从阅读器设置面板"翻页方式"入口推入。渲染翻页方式/动画/音量键/自动翻页。
 */
@Composable
fun ReaderPageTurnSettingsScreen(
    onBack: () -> Unit,
    context: ReaderContext? = null,
    dispatch: (ReaderUiIntent) -> Unit = {}
) {
    var turnMethod by remember { mutableStateOf("左右区域") }
    var turnAnimation by remember { mutableStateOf("覆盖") }
    var volumeKey by remember { mutableStateOf(false) }
    var autoPage by remember { mutableStateOf(false) }
    var autoPageInterval by remember { mutableStateOf(8) }

    FullSettingsScaffold(title = "翻页设置", onBack = onBack) {
        FullSettingsSection("点击翻页方式") {
            val methods = listOf("左右区域", "上下区域", "全屏滚动", "禁用点击")
            methods.forEach { label ->
                FullSettingsRow(
                    title = label,
                    value = if (turnMethod == label) "已选" else "",
                    onClick = {
                        turnMethod = label
                        dispatch(ReaderUiIntent.SetReaderChoice(key = "pageTurnMethod", value = label))
                    }
                )
            }
        }
        FullSettingsSection("翻页动画") {
            val animations = listOf("覆盖", "仿真", "滑动", "无动画")
            animations.forEach { label ->
                FullSettingsRow(
                    title = label,
                    value = if (turnAnimation == label) "已选" else "",
                    onClick = {
                        turnAnimation = label
                        dispatch(ReaderUiIntent.SetReaderChoice(key = "pageTurnAnimation", value = label))
                    }
                )
            }
        }
        FullSettingsSection("硬件按键") {
            FullToggleRow(
                title = "音量键翻页",
                checked = volumeKey,
                onToggle = {
                    volumeKey = it
                    dispatch(ReaderUiIntent.SetReaderBehaviorToggle(key = "volumeKey", enabled = it))
                }
            )
        }
        FullSettingsSection("自动翻页") {
            FullToggleRow(
                title = "自动翻页",
                checked = autoPage,
                onToggle = {
                    autoPage = it
                    dispatch(ReaderUiIntent.SetReaderBehaviorToggle(key = "autoPage", enabled = it))
                }
            )
            FullSettingsDivider()
            FullSettingsRow(
                title = "翻页间隔",
                value = "$autoPageInterval 秒",
                onClick = {
                    autoPageInterval = if (autoPageInterval >= 30) 5 else autoPageInterval + 5
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Shared helpers for full-screen settings pages ──────────────────────────────

@Composable
private fun FullSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(extra.paper)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .defaultMinSize(minHeight = 54.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 42.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.reader_ic_chevron_left),
                    contentDescription = "返回",
                    tint = extra.controlInk
                )
            }
            Text(
                text = title,
                style = ReaderTextStyles.appBarTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun FullSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = ReaderTextStyles.sectionTitle,
        color = readerExtraColors().muted,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
    val extra = readerExtraColors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colorScheme.surface, shape = ReaderShapes.md)
            .border(width = 1.dp, color = extra.hairline, shape = ReaderShapes.md)
            .clip(ReaderShapes.md),
        content = content
    )
}

@Composable
private fun FullSettingsRow(
    title: String,
    value: String = "",
    onClick: (() -> Unit)? = null
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = ReaderTextStyles.tabLabel,
                color = extra.muted
            )
        }
    }
}

@Composable
private fun FullSettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(readerExtraColors().hairline)
    )
}

@Composable
private fun FullToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val extra = readerExtraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = ReaderTextStyles.continueAction,
            color = extra.controlInk,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .background(
                    color = if (checked) extra.controlPrimary else extra.controlDisabledBg,
                    shape = CircleShape
                )
                .clip(CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = if (checked) 22.dp else 2.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color.White,
                        shape = CircleShape
                    )
            )
        }
    }
}
