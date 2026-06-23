#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "docs/ui-design/90-审计与索引/01-图片文字稿总册.md"
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg"}
EXCLUDED_PARTS = {".git", ".gradle", ".stitch", "build", "dist", "node_modules"}


def image_files() -> list[Path]:
    result: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in IMAGE_SUFFIXES:
            continue
        if any(part in EXCLUDED_PARTS for part in path.parts):
            continue
        result.append(path.relative_to(ROOT))
    return sorted(result)


def draft_descriptions() -> dict[str, str]:
    readme = (ROOT / "docs/ui-design/91-历史归档/drafts/旧全局草稿目录/README.md").read_text(encoding="utf-8")
    return {
        name: description
        for name, description in re.findall(r"^\| `([^`]+\.(?:png|svg))` \| (.+?) \|$", readme, re.M)
    }


def dimensions(path: Path) -> tuple[str, str]:
    full_path = ROOT / path
    if path.suffix.lower() == ".svg":
        text = full_path.read_text(encoding="utf-8")
        view_box = re.search(r'viewBox="[^"]*?([\d.]+)\s+([\d.]+)"', text)
        if view_box:
            width, height = float(view_box.group(1)), float(view_box.group(2))
        else:
            width_match = re.search(r'width="([\d.]+)', text)
            height_match = re.search(r'height="([\d.]+)', text)
            width = float(width_match.group(1)) if width_match else 0
            height = float(height_match.group(1)) if height_match else 0
    else:
        with Image.open(full_path) as image:
            width, height = image.size
    if not width or not height:
        return "尺寸由源文件决定", "未知"
    ratio = width / height
    orientation = "竖屏" if ratio < 0.8 else "横屏" if ratio > 1.25 else "近方形"
    return f"{int(width)}×{int(height)} px，{orientation}，宽高比 {width / height:.3f}:1", orientation


def family_for(name: str) -> str:
    lowered = name.lower()
    rules = [
        ("bookshelf", "书架"),
        ("book-search", "书籍搜索"),
        ("search-result", "书籍搜索"),
        ("book-info", "书籍详情"),
        ("book-detail", "书籍详情"),
        ("content-search", "书内搜索"),
        ("auto-page", "自动翻页"),
        ("replacement", "内容替换"),
        ("toc", "目录与书签"),
        ("directory", "目录与书签"),
        ("read-aloud", "朗读"),
        ("tts", "朗读"),
        ("appearance", "阅读外观"),
        ("theme", "阅读外观"),
        ("font", "阅读外观"),
        ("reader-settings", "阅读设置"),
        ("reader-control", "阅读控制层"),
        ("reader-dark", "阅读夜间态"),
        ("reader-light", "沉浸阅读"),
        ("reader-immersive", "沉浸阅读"),
        ("reader-night", "阅读夜间态"),
        ("source-switch", "阅读换源"),
        ("source-check", "书源检测"),
        ("source-", "书源管理"),
        ("brightness", "阅读亮度"),
        ("chapter-progress", "章节进度"),
        ("top-more", "阅读更多菜单"),
        ("refresh-feedback", "阅读刷新反馈"),
        ("debug-info", "调试信息"),
        ("discovery", "发现"),
        ("rss", "RSS"),
        ("settings-home", "设置首页"),
        ("app-settings", "App 设置"),
        ("global-state", "全局状态"),
        ("derived-states", "派生状态总览"),
    ]
    for needle, family in rules:
        if needle in lowered:
            return family
    return "设计辅助资产"


def asset_status(path: Path, description: str) -> str:
    value = path.as_posix().lower()
    if "_rejected" in value or "废弃" in description or "不采用" in description:
        return "EXCLUDED：废弃稿，仅记录差异，禁止作为实现依据"
    if "_superseded" in value or "_progress_restore_backup" in value:
        return "HISTORY_ONLY：被替代或备份版本，仅用于追溯"
    if "_contact_sheets" in value:
        return "CONTACT_SHEET：审图拼图，不是独立页面"
    if ".stitch/" in value:
        return "STITCH_DRAFT：生成初稿，必须服从正式规格与正式设计图"
    if "91-历史归档/freezes" in value:
        return "FREEZE_CANDIDATE：冻结候选归档，采用状态以冻结决议为准"
    if any(token in description for token in ["上一版", "历史", "中间候选", "内容参考", "流程示意", "结构规划"]):
        return "REFERENCE_ONLY：参考或历史资产，不独立定义最终视觉"
    return "CURRENT_OR_CANDIDATE：正式目录资产；最终采用级别以图片索引和权威规格为准"


def typography_for(family: str, orientation: str) -> str:
    if family in {"沉浸阅读", "阅读夜间态", "阅读控制层", "目录与书签", "朗读", "书内搜索", "自动翻页", "内容替换", "阅读外观", "阅读设置", "阅读换源", "阅读亮度", "章节进度", "阅读更多菜单"}:
        return (
            "逻辑手机基准 390×884 dp；正文 18sp、行高约 1.75；页/面板标题 18–20sp，"
            "章节与组标题 14–16sp，正文/按钮 14sp，辅助信息 11–12sp；触控区至少 44×44dp。"
        )
    if orientation == "横屏":
        return (
            "横向画布按同一语义字号缩放：主标题 20–22sp，分组标题 16sp，正文 14sp，"
            "辅助信息 12sp；控件最小高度 44dp，控制面板不得非等比拉伸。"
        )
    return (
        "逻辑手机基准 390×884 dp；页面主标题 22sp，顶栏标题 20sp，分组标题 16sp，"
        "正文/按钮 14sp，说明与元信息 12sp；行高 1.35–1.55；触控区至少 44×44dp。"
    )


def style_for(family: str) -> str:
    if family in {"沉浸阅读", "阅读夜间态", "阅读控制层", "目录与书签", "朗读", "书内搜索", "自动翻页", "内容替换", "阅读外观", "阅读设置", "阅读换源", "阅读亮度", "章节进度", "阅读更多菜单"}:
        return (
            "暖白纸张底色、深灰正文、低饱和蓝绿强调色；覆盖层为不透明暖色卡片，"
            "轻描边、低阴影、8–16dp 圆角；阅读正文保持低干扰和长时可读性。"
        )
    if family == "设计辅助资产":
        return "保持源图的标注/拼图用途；不从该图提取产品色彩、圆角、阴影或组件 token。"
    return (
        "暖白页面、柔和卡片和分区信息块；深灰主文字、灰褐次文字、蓝绿主操作色，"
        "卡片约 8dp 圆角、1dp 暖灰分隔线，避免玻璃效果和重阴影。"
    )


def ui_for(family: str, description: str) -> str:
    details = {
        "书架": "顶部“书架”与搜索/更多；筛选标签；继续阅读、最近更新、书籍封面或列表；底部书架/发现/RSS/设置导航。",
        "书籍搜索": "返回、页面标题、搜索框、范围/分组筛选、历史或结果列表；结果卡显示封面、书名、作者、来源摘要和阅读/加入操作。",
        "书籍详情": "返回与更多、封面、书名作者、来源与字数、开始阅读/加入书架、章节预览、简介和书源更新。",
        "沉浸阅读": "书名/章节、时间、正文、阅读百分比和章节进度；控制层关闭时不显示实体按钮。",
        "阅读夜间态": "沿用沉浸阅读结构，改为深色纸张和低亮度文字，夜间入口切换为日间模式语义。",
        "阅读控制层": "顶部返回/书名章节/换源/更多；搜索、自动翻页、替换快捷项；章节进度与上一/下一章；目录/朗读/界面/设置；右侧亮度。",
        "书内搜索": "关闭/返回、搜索输入、范围标签、选项开关、命中数量和按章节排列的摘要，高亮关键词但不只依赖颜色。",
        "自动翻页": "翻页速度、翻页模式、更多选项、开始/暂停/继续/停止；运行态收敛为悬浮胶囊。",
        "内容替换": "当前书籍规则、规则开关、匹配与替换内容、预览/测试、导入和新增规则。",
        "目录与书签": "目录/书签标签、搜索与更多、章节层级、当前章节、缓存/书签标识和阅读进度定位。",
        "朗读": "朗读状态、播放/暂停/停止、语速、音色、范围、定时及运行胶囊；不使用章节跳转图标冒充播放控制。",
        "阅读外观": "字体、字号、主题、颜色、边距、缩进、行距、信息位置、翻页动画与实时预览。",
        "阅读设置": "屏幕与显示、翻页与手势、阅读辅助、进度与信息、预设；使用开关、分段控件、步进器和选择行。",
        "阅读换源": "候选来源列表、检测中进度、成功/警告/失败结果和切换操作，保留当前书籍上下文。",
        "书源管理": "书源概览、搜索筛选、启用状态、健康状态、详情/新增/编辑表单、规则入口和底部主要操作。",
        "书源检测": "书源身份、总进度、分步骤状态、耗时、警告/错误详情、取消/重试/编辑入口。",
        "调试信息": "请求/响应/解析/日志标签、状态码和耗时、脱敏头部与正文、复制或查看原始内容操作。",
        "阅读亮度": "亮度滑杆、系统亮度状态和切换反馈；系统模式下禁用手动值但保留当前亮度可见性。",
        "章节进度": "章节名、页内百分比、滑杆、上一章/下一章与拖动预览，提交后定位正文。",
        "阅读更多菜单": "刷新本章、章节信息、调试信息等维护操作；菜单锚定顶部更多按钮。",
        "阅读刷新反馈": "在阅读页原位显示短时刷新中/成功/失败反馈，不重排正文。",
        "发现": "搜索、内容类型、当前来源、来源分类、推荐内容、榜单更新及底部主导航。",
        "RSS": "订阅概览、状态筛选、订阅源标签、最新订阅条目及底部主导航。",
        "设置首页": "本地概览、常用管理入口、全局设置分组和底部主导航。",
        "App 设置": "设置分组、设置行、当前值、开关/步进器/跳转箭头、缓存或权限状态及危险操作。",
        "全局状态": "宿主页中的加载、空、错误、网络错误、权限、删除确认和操作成功/失败反馈模板。",
        "派生状态总览": "并列展示多个页面或状态，用箭头/标注解释状态关系；每个子画面仍服从对应页面规格。",
        "设计辅助资产": "该图用于结构标注、候选比较、备份或拼图审阅，不新增可实现的独立产品页面。",
    }
    return f"{description}。{details.get(family, details['设计辅助资产'])}"


def interaction_for(family: str) -> tuple[str, str, str]:
    rules = {
        "书架": ("点击书籍进入详情，继续阅读直达阅读页；搜索和筛选更新列表；长按/更多打开书籍操作。", "书籍详情、书籍搜索、发现、RSS、设置、沉浸阅读。", "列表/网格切换 180–220ms；筛选交叉淡化；封面按压缩放至 0.98；数据加载用骨架屏。"),
        "书籍搜索": ("输入后搜索；范围和分组改变查询；点击结果进入详情，主按钮开始阅读或加入书架。", "书籍详情、沉浸阅读、书架。", "结果 180ms 淡入；搜索中保留布局并显示进度；空/错状态原位切换。"),
        "书籍详情": ("章节可定位；展开简介；切换书源；开始阅读续接本地进度。", "沉浸阅读、完整目录、来源切换、书架。", "章节展开 200ms；加入书架状态渐变；封面与标题不做夸张共享元素。"),
        "阅读控制层": ("点击正文中央开关控制层；四个主模块只替换面板主内容；遮罩/返回按层级关闭。", "目录、朗读、界面、设置模块及各完整功能页。", "控制层 220ms 淡入+8dp 上移；模块内容 160ms 交叉淡化；亮度与进度连续跟手。"),
        "书内搜索": ("输入即时或提交搜索；点击命中定位正文；完整页面入口保留查询和范围。", "沉浸阅读、完整书内搜索页。", "覆盖层 220ms 上移；命中高亮 150ms；搜索中防抖并显示稳定进度。"),
        "自动翻页": ("设置参数后开始；运行胶囊支持暂停/继续和停止；设置页保存持久默认值。", "沉浸阅读、自动翻页设置页。", "开始后面板 200ms 收敛为胶囊；翻页采用所选动画；暂停时停止计时。"),
        "内容替换": ("切换规则即时预览；新增/编辑/测试后保存；无效规则不得静默生效。", "替换规则列表、规则编辑、沉浸阅读。", "覆盖层 220ms 上移；测试结果 160ms 淡入；保存成功短时反馈。"),
        "目录与书签": ("切换目录/书签；搜索过滤；点击章节定位；书签增删即时反馈。", "沉浸阅读、完整目录页。", "面板 220ms 上移；标签指示器 180ms；定位当前章节后列表平滑滚动。"),
        "朗读": ("播放、暂停、继续、停止；参数进入设置；离开页面时按产品策略继续后台或确认停止。", "沉浸阅读、朗读设置页。", "面板收敛为胶囊 200ms；播放状态图标 150ms 变换；进度按时间连续更新。"),
        "阅读外观": ("调整后实时预览；字体/主题/版式/动画进入完整页；保存或取消返回原模块。", "字体、主题、主题编辑、版式高级、翻页动画、沉浸阅读。", "预览 120–180ms 更新；主题颜色 200ms 交叉淡化；滑杆连续跟手。"),
        "阅读设置": ("设置行进入二级页或直接切换；预设可应用、管理和恢复默认。", "屏幕与显示、翻页与手势、阅读辅助、进度与信息、预设。", "开关 150ms；二级页标准水平导航；恢复默认先确认再显示结果。"),
        "阅读换源": ("选择来源后检测；成功才切换，失败保留原来源和阅读位置。", "沉浸阅读、书源详情/编辑。", "检测进度连续更新；成功 180ms 切换来源；失败原位展开恢复操作。"),
        "书源管理": ("筛选、启停、新增、编辑、检测和删除；未保存离开与破坏性操作必须确认。", "书源详情、新增、编辑、检测、调试信息、错误日志。", "列表状态 150ms；检测页进度连续更新；表单错误 160ms 原位出现。"),
        "书源检测": ("允许取消；完成后查看结果、重试、调试或编辑；错误信息必须脱敏。", "检测结果、调试信息、错误日志、编辑书源。", "逐项状态 150ms 变更；总进度连续；完成后摘要淡入，失败不自动消失。"),
        "调试信息": ("标签切换内容；复制前保持脱敏；原始敏感数据不得展示。", "书源检测结果、错误日志、书源详情。", "标签内容 160ms 交叉淡化；复制成功 toast 2 秒后淡出。"),
        "发现": ("切换内容类型、来源和来源内分类；刷新；点击内容进入详情或阅读。", "书籍详情、书籍搜索、书源管理、RSS、书架、设置。", "分类指示器 180ms；内容列表 180ms 淡入；下拉刷新跟手。"),
        "RSS": ("筛选订阅状态；点击条目进入详情；刷新和管理订阅。", "RSS 详情、订阅管理、书架、发现、设置。", "筛选 180ms；未读状态 150ms；刷新保持列表位置。"),
        "设置首页": ("点击设置行进入对应管理页；概览卡跳转到相关数据页。", "阅读设置、书源管理、RSS/订阅、缓存、同步备份、隐私权限、关于反馈。", "标准页面导航 200–250ms；开关 150ms；危险操作使用确认对话框。"),
        "App 设置": ("设置行切换或进入子页；清理、备份、恢复和权限操作显示明确结果。", "设置首页、系统权限页、WebDAV/备份页。", "开关 150ms；数值变化 120ms；长任务显示进度且可取消；结果反馈 2 秒或需手动关闭。"),
        "全局状态": ("重试、授权、前往设置、确认/取消删除或执行推荐下一步；焦点进入状态标题。", "返回宿主页内容态、系统设置或上一级页面。", "加载循环；状态替换 160ms 淡化；确认框 180ms 缩放+淡入；不可操作错误不自动消失。"),
        "沉浸阅读": ("中央点击打开控制层；左右热区翻页；边缘手势按设置执行。", "阅读控制层、上一/下一页。", "翻页动画由用户设置决定；控制层关闭时 180ms 淡出。"),
        "阅读夜间态": ("交互与沉浸阅读一致；日/夜切换不改变阅读位置。", "沉浸阅读、阅读控制层。", "主题色 200ms 交叉淡化，避免全白闪屏。"),
    }
    return rules.get(
        family,
        ("仅按图中标注解释状态关系；若为历史/辅助资产，不承载独立可点击实现。", "跳转服从对应正式页面规格；辅助资产本身无路由。", "辅助图本身无运行时动效；所示页面动效服从对应正式规格。"),
    )


def write_catalog(images: list[Path]) -> None:
    descriptions = draft_descriptions()
    lines = [
        "# UI 图片逐图文字稿总册",
        "",
        "> 生成/复核日期：2026-06-13  ",
        "> 范围：仓库内全部 PNG/JPG/JPEG/WebP/GIF/SVG（排除构建产物）  ",
        "> 原则：每张图片均有独立条目；历史、废弃、备份和拼图同样登记，但不得越权成为实现依据。",
        "",
        "## 使用规则",
        "",
        "- 手机竖屏统一逻辑基准为 `390×884dp`；导出像素只用于识别比例，不直接等同 dp。",
        "- 本总册补齐逐图实现语义；产品边界以 `01-全局规范/` 与对应页面包中的结构、组件、交互、状态稿为准。",
        "- `EXCLUDED`、`HISTORY_ONLY`、`CONTACT_SHEET` 和 `REFERENCE_ONLY` 不得作为最终视觉或路由依据。",
        "- 所有数值均为实现目标；历史图片中的偏差应按文字稿和当前权威 token 修正。",
        "",
        f"审计图片总数：**{len(images)}**。",
        "",
    ]
    for path in images:
        name = path.name
        description = descriptions.get(name, name.rsplit(".", 1)[0].replace("-", " "))
        family = family_for(name)
        canvas, orientation = dimensions(path)
        interaction, routes, motion = interaction_for(family)
        lines.extend(
            [
                f"## `{path.as_posix()}`",
                "",
                f"- **资产状态**：{asset_status(path, description)}",
                f"- **画布与比例**：{canvas}；实现时按安全区和窗口尺寸响应，不裁切关键内容。",
                f"- **文字与 UI**：{ui_for(family, description)}",
                f"- **风格与色彩**：{style_for(family)}",
                f"- **字号与尺寸**：{typography_for(family, orientation)}",
                f"- **交互逻辑**：{interaction}",
                f"- **跳转页面**：{routes}",
                f"- **状态与动效**：{motion}",
                "",
            ]
        )
    CATALOG.write_text("\n".join(lines), encoding="utf-8")


def section_block(text: str, image_path: Path) -> str:
    marker = f"## `{image_path.as_posix()}`"
    start = text.find(marker)
    if start == -1:
        return ""
    next_start = text.find("\n## `", start + len(marker))
    return text[start:] if next_start == -1 else text[start:next_start]


def main() -> int:
    images = image_files()
    if "--write" in sys.argv:
        write_catalog(images)
    text = CATALOG.read_text(encoding="utf-8") if CATALOG.exists() else ""
    required_labels = [
        "资产状态",
        "画布与比例",
        "文字与 UI",
        "风格与色彩",
        "字号与尺寸",
        "交互逻辑",
        "跳转页面",
        "状态与动效",
    ]

    failures: list[str] = []
    for image in images:
        block = section_block(text, image)
        if not block:
            failures.append(f"MISSING_ENTRY {image}")
            continue
        for label in required_labels:
            if not re.search(rf"^- \*\*{re.escape(label)}\*\*：\S", block, re.M):
                failures.append(f"MISSING_FIELD {image} {label}")

    catalog_paths = {
        Path(match)
        for match in re.findall(r"^## `([^`]+\.(?:png|jpg|jpeg|webp|gif|svg))`", text, re.M | re.I)
    }
    for stale in sorted(catalog_paths - set(images)):
        failures.append(f"STALE_ENTRY {stale}")

    print(f"images={len(images)} catalog_entries={len(catalog_paths)} failures={len(failures)}")
    for failure in failures:
        print(failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
