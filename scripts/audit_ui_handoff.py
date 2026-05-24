#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
RAW_HTML = ROOT / "docs/ui-handoff/stitch-export/raw/control-layer-base-v2-layout-refined.raw.html"
NORMALIZED_HTML = ROOT / "docs/ui-handoff/normalized-html/control-layer-base-v2.html"
CSS_FILES = [
    ROOT / "docs/ui-handoff/css/reader-design-tokens.css",
    ROOT / "docs/ui-handoff/css/reader-components.css",
    ROOT / "docs/ui-handoff/css/reader-control-layer.css",
    ROOT / "docs/ui-handoff/css/reader-handoff-boundary.css",
]
AUDIT_REPORT = ROOT / "docs/ui-handoff/audits/control-layer-base-v2-normalized-audit.md"
OVERLAY_AUDIT_REPORT = ROOT / "docs/ui-handoff/audits/reader-control-overlays-v2-normalized-audit.md"
OVERLAY_PAGES = {
    "搜索": ROOT / "docs/ui-handoff/normalized-html/reader-search-overlay-v2.html",
    "自动翻页": ROOT / "docs/ui-handoff/normalized-html/reader-auto-scroll-overlay-v2.html",
    "内容替换": ROOT / "docs/ui-handoff/normalized-html/reader-replace-overlay-v2.html",
    "夜间状态": ROOT / "docs/ui-handoff/normalized-html/reader-night-state-v2.html",
    "目录书签": ROOT / "docs/ui-handoff/normalized-html/reader-directory-overlay-v2.html",
    "朗读": ROOT / "docs/ui-handoff/normalized-html/reader-tts-overlay-v2.html",
    "界面": ROOT / "docs/ui-handoff/normalized-html/reader-appearance-overlay-v2.html",
    "设置": ROOT / "docs/ui-handoff/normalized-html/reader-settings-overlay-v2.html",
}
QUICK_OVERLAYS = ["搜索", "自动翻页", "内容替换"]
BOTTOM_OVERLAYS = ["目录书签", "朗读", "界面", "设置"]


FULL_AUDIT_REPORT = ROOT / "docs/ui-handoff/audits/full-frontend-normalized-html-audit.md"
FULL_REQUIRED_HTML = [
    "app-shell.html", "main-tabs.html",
    "bookshelf-cover-mode.html", "bookshelf-list-mode.html", "bookshelf-empty.html", "bookshelf-group-management.html", "bookshelf-book-more-menu.html", "local-book-import.html",
    "search-home.html", "search-results.html", "search-loading.html", "search-empty.html", "search-error.html", "book-detail.html", "book-detail-toc-preview.html", "source-switch-results.html",
    "source-management-list.html", "source-detail.html", "source-add.html", "source-edit.html", "source-import.html", "source-test-result.html", "source-disabled-error.html",
    "discover-home.html", "rss-list.html", "rss-detail.html", "rss-subscription-management.html", "rss-empty.html", "rss-error.html",
    "global-settings.html", "reading-settings-entry.html", "source-settings-entry.html", "sync-settings-entry.html", "about-version.html",
    "webdav-config.html", "backup-settings.html", "progress-sync-status.html", "remote-webdav-books.html", "sync-error.html",
    "global-loading.html", "global-empty.html", "global-error.html", "offline-state.html", "permission-required.html",
]
CORE_READER_HTML = [
    "control-layer-base-v2.html", "reader-search-overlay-v2.html", "reader-auto-scroll-overlay-v2.html", "reader-replace-overlay-v2.html", "reader-night-state-v2.html", "reader-directory-overlay-v2.html", "reader-tts-overlay-v2.html", "reader-appearance-overlay-v2.html", "reader-settings-overlay-v2.html",
]
REQUIRED_COMPONENTS = [
    "app-top-bar.html", "app-bottom-nav.html", "book-card.html", "book-list-item.html", "book-action-sheet.html", "search-box.html", "search-result-item.html", "book-detail-header.html", "book-source-chip.html", "source-list-item.html", "settings-row.html", "settings-switch-row.html", "settings-dropdown-row.html", "state-loading.html", "state-empty.html", "state-error.html", "webdav-status-card.html", "rss-item.html",
]


TARGET_COLORS = [
    "#fff8f4",
    "#53433f",
    "#3f4d52",
    "#366179",
    "#e9ded6",
    "#f7ebe1",
]
FLOATING_BG_OPTIONS = ["#efe2d8", "#eadbd0"]
OLD_COLORS = ["#fdf6ec", "#eae1da", "#f5ece6", "#efe7e0", "#8b5000"]
OLD_CLASSES = [
    "bg-surface-container",
    "bg-surface-container-high",
    "bg-surface-container-highest",
    "text-on-surface",
    "text-on-surface-variant",
    "shadow-lg",
    "shadow-md",
]
CSS_LINKS = [
    "../css/reader-design-tokens.css",
    "../css/reader-components.css",
    "../css/reader-control-layer.css",
    "../css/reader-handoff-boundary.css",
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def visible_quick_text(html: str) -> str:
    start = html.find('class="reader-floating-quick-actions')
    end = html.find('class="reader-floating-page-control')
    if start == -1 or end == -1 or end <= start:
        return ""
    start = html.rfind("<div", 0, start)
    end = html.rfind("<section", 0, end)
    block = html[start:end]
    block = re.sub(r'aria-label="[^"]*"', "", block)
    block = re.sub(r"<span[^>]*>[^<]*</span>", "", block)
    block = re.sub(r"<[^>]+>", "", block)
    return re.sub(r"\s+", "", block)


def status(ok: bool) -> str:
    return "通过" if ok else "未通过"


def has_all(text: str, needles: list[str]) -> bool:
    return all(needle in text for needle in needles)


def has_none(text: str, needles: list[str]) -> bool:
    return all(needle not in text for needle in needles)


def main() -> int:
    html = read(NORMALIZED_HTML)
    raw = read(RAW_HTML)
    css_bundle = "\n".join(read(path) for path in CSS_FILES)
    combined = html + "\n" + css_bundle

    checks: list[tuple[str, bool, str]] = []

    checks.append(("normalized HTML 存在", NORMALIZED_HTML.exists(), str(NORMALIZED_HTML.relative_to(ROOT))))
    checks.append(("raw HTML 已保存", RAW_HTML.exists(), str(RAW_HTML.relative_to(ROOT))))
    for link in CSS_LINKS:
        checks.append((f"normalized HTML 引用 {Path(link).name}", link in html, link))

    for color in TARGET_COLORS:
        checks.append((f"目标色出现 {color}", color in combined.lower(), color))
    checks.append(("浮动层目标色出现 #efe2d8 或 #eadbd0", any(c in combined.lower() for c in FLOATING_BG_OPTIONS), "浮动控件背景"))

    for old_color in OLD_COLORS:
        checks.append((f"normalized/CSS 无旧颜色 {old_color}", old_color not in combined.lower(), old_color))
    for old_class in OLD_CLASSES:
        checks.append((f"normalized/CSS 无旧类 {old_class}", old_class not in combined, old_class))

    quick_text = visible_quick_text(html)
    quick_labels_clear = all(label not in quick_text for label in ["搜索", "自动翻页", "内容替换", "夜间模式"])
    checks.append(("快捷按钮无可见文字标签", quick_labels_clear, f"visible_text={quick_text or '空'}"))
    checks.append(("无弹窗结构", not re.search(r"\b(dialog|modal|popover|popup|overlay-panel)\b", html, re.I), "未发现弹窗类结构"))
    checks.append(("页内控制条使用 chevron_left", "chevron_left" in html, "chevron_left"))
    checks.append(("页内控制条使用 chevron_right", "chevron_right" in html, "chevron_right"))
    checks.append(("无 skip_previous / skip_next", "skip_previous" not in html and "skip_next" not in html, "章节跳转图标未出现"))
    checks.append(("底栏包含目录 / 朗读 / 界面 / 设置", all(x in html for x in ["目录", "朗读", "界面", "设置"]), "底栏四项"))
    checks.append(("亮度条存在", "reader-floating-brightness" in html and "brightness_auto" in html, "亮度条"))
    checks.append(("Source chip 为描边 chip", "reader-source-chip" in html and "--reader-primary" in css_bundle, "reader-source-chip"))
    checks.append(("无 bottom-21", "bottom-21" not in combined, "布局 token"))
    checks.append(("无硬编码 bottom-[156px] / bottom-[88px]", "bottom-[156px]" not in combined and "bottom-[88px]" not in combined, "布局 token"))

    failed = [item for item in checks if not item[1]]
    conclusion = "CONTROL_LAYER_BASE_V2_NORMALIZED_AUDIT_READY" if not failed else "CONTROL_LAYER_BASE_V2_NORMALIZED_AUDIT_NOT_READY"

    raw_residuals = []
    for token in OLD_COLORS + OLD_CLASSES:
        if token in raw:
            raw_residuals.append(token)

    lines = [
        "# Control Layer Base V2 Normalized Audit",
        "",
        "## 1. 总体结论",
        "",
        conclusion,
        "",
        "## 2. 文件检查",
        "",
        "| 检查项 | 结果 | 备注 |",
        "|---|---|---|",
    ]
    for name, ok, note in checks:
        lines.append(f"| {name} | {status(ok)} | `{note}` |")

    lines.extend(
        [
            "",
            "## 3. Raw 参考残留",
            "",
            "raw HTML 作为 Stitch 参考源保留，允许旧颜色和旧类残留；normalized HTML/CSS 不允许继承这些旧值。",
            "",
            f"- raw 残留：{', '.join(raw_residuals) if raw_residuals else '无'}",
            "",
            "## 4. 失败项",
            "",
            "无。" if not failed else "\n".join(f"- {name}: {note}" for name, _, note in failed),
            "",
        ]
    )

    AUDIT_REPORT.parent.mkdir(parents=True, exist_ok=True)
    AUDIT_REPORT.write_text("\n".join(lines), encoding="utf-8")

    overlay_checks: list[tuple[str, bool, str]] = []
    overlay_html: dict[str, str] = {name: read(path) for name, path in OVERLAY_PAGES.items()}
    all_overlay_combined = "\n".join(overlay_html.values()) + "\n" + css_bundle

    for name, path in OVERLAY_PAGES.items():
        page = overlay_html[name]
        overlay_checks.append((f"{name} normalized HTML 存在", path.exists(), str(path.relative_to(ROOT))))
        for link in CSS_LINKS:
            overlay_checks.append((f"{name} 引用 {Path(link).name}", link in page, link))
        overlay_checks.append((f"{name} 保留顶部结构", has_all(page, ["arrow_back", "深空信号", "refresh", "swap_horiz", "more_vert"]), "顶部三段式"))
        overlay_checks.append((f"{name} 保留顶部第二行", has_all(page, ["第一章：阿长与《山海经》", "reader-source-chip", "本地书籍"]), "meta row"))
        overlay_checks.append((f"{name} 保留底栏四项", has_all(page, ["目录", "朗读", "界面", "设置"]), "底栏四项"))
        overlay_checks.append((f"{name} 无旧颜色", has_none(page, OLD_COLORS), "旧颜色"))
        overlay_checks.append((f"{name} 无旧类", has_none(page, OLD_CLASSES), "旧类"))
        overlay_checks.append((f"{name} 快捷按钮无可见文字标签", all(label not in visible_quick_text(page) for label in ["搜索", "自动翻页", "内容替换", "夜间模式", "日间模式"]), "快捷区可见文本"))

    for name in QUICK_OVERLAYS:
        page = overlay_html[name]
        overlay_checks.append((f"{name} 快捷弹窗隐藏亮度条", "reader-floating-brightness" not in page, "亮度条"))
        overlay_checks.append((f"{name} 快捷弹窗保留圆形快捷方式", "reader-floating-quick-actions" in page, "quick actions"))
        overlay_checks.append((f"{name} 快捷弹窗保留页内控制条", "reader-floating-page-control" in page, "page control"))
        overlay_checks.append((f"{name} 快捷弹窗保留底栏", "reader-control-bottom-bar" in page, "bottom bar"))
        overlay_checks.append((f"{name} 使用 QuickActionOverlay", "reader-overlay-panel-quick-action" in page, "quick overlay"))

    for name in BOTTOM_OVERLAYS:
        page = overlay_html[name]
        overlay_checks.append((f"{name} 底栏弹窗隐藏亮度条", "reader-floating-brightness" not in page, "亮度条"))
        overlay_checks.append((f"{name} 底栏弹窗隐藏圆形快捷方式", "reader-floating-quick-actions" not in page, "quick actions"))
        overlay_checks.append((f"{name} 底栏弹窗隐藏页内控制条", "reader-floating-page-control" not in page, "page control"))
        overlay_checks.append((f"{name} 底栏弹窗保留底栏", "reader-control-bottom-bar" in page, "bottom bar"))
        overlay_checks.append((f"{name} 使用 BottomFunctionOverlay", "reader-overlay-panel-bottom-function" in page, "bottom overlay"))

    night = overlay_html["夜间状态"]
    overlay_checks.append(("夜间模式无弹窗", "reader-overlay-panel" not in night, "night state"))
    overlay_checks.append(("夜间状态第四个快捷图标变为日间模式", "light_mode" in night and "dark_mode" not in night, "light_mode"))

    replace = overlay_html["内容替换"]
    overlay_checks.append(("内容替换只显示当前书籍规则", has_all(replace, ["当前书籍：深空信号", "匹配规则 3 条", "净化广告段落", "合并异常断行", "修正常见乱码"]) and has_none(replace, ["全局规则库", "书源管理", "规则市场", "换源列表"]), "replace overlay"))

    toc = overlay_html["目录书签"]
    overlay_checks.append(("目录页包含目录 / 书签按钮", has_all(toc, ["目录", "书签", "reader-toc-tabs"]), "toc tabs"))
    overlay_checks.append(("目录页有分级小字", "第一本 / 第一卷 /" in toc, "章节分级"))
    overlay_checks.append(("目录页有右侧常驻进度条", "reader-toc-progress" in toc, "toc progress"))
    overlay_checks.append(("目录页有固定标识列", has_all(toc, ["reader-toc-row", "bookmark", "my_location"]), "书签标识 / 当前阅读标识"))

    tts = overlay_html["朗读"]
    overlay_checks.append(("朗读页无 skip_previous / skip_next", has_none(tts, ["skip_previous", "skip_next"]), "tts icons"))
    overlay_checks.append(("朗读页分为 4 组", tts.count("reader-tts-group") >= 4 and has_all(tts, ["朗读", "正在朗读", "当前章节", "主播放控制", "00:38", "朗读参数", "定时关闭", "朗读音色"]), "tts groups"))

    appearance = overlay_html["界面"]
    overlay_checks.append(("界面页分为文字 / 段落 / 界面", has_all(appearance, ["文字", "段落", "界面", "字体", "字号", "缩进", "行距", "信息", "翻页动画", "主题"]), "appearance groups"))

    settings = overlay_html["设置"]
    required_settings = ["屏幕方向", "屏幕超时", "隐藏状态栏", "单双页", "进度条行为", "文字两端对齐", "文字底部对齐", "音量键翻页", "单手翻页"]
    forbidden_settings = ["字体", "字号", "字距", "繁简", "缩进", "行距", "段距", "四角信息", "翻页动画", "主题", "WebDAV", "书源管理", "RSS", "全局 App 设置"]
    overlay_checks.append(("设置页只包含指定 9 项", has_all(settings, required_settings) and has_none(settings, forbidden_settings), "settings list"))

    overlay_checks.append(("所有页面无 Light / Theme", has_none(all_overlay_combined, ["Light", "Theme"]), "英文旧标签"))
    overlay_checks.append(("所有页面无 skip_previous / skip_next", has_none(all_overlay_combined, ["skip_previous", "skip_next"]), "skip icons"))
    overlay_checks.append(("所有页面无旧颜色", has_none(all_overlay_combined, OLD_COLORS), "old colors"))
    overlay_checks.append(("所有页面无旧类", has_none(all_overlay_combined, OLD_CLASSES), "old classes"))

    overlay_failed = [item for item in overlay_checks if not item[1]]
    overlay_conclusion = "READER_CONTROL_OVERLAYS_V2_NORMALIZED_AUDIT_READY" if not overlay_failed else "READER_CONTROL_OVERLAYS_V2_NORMALIZED_AUDIT_NOT_READY"
    overlay_lines = [
        "# Reader Control Overlays V2 Normalized Audit",
        "",
        "## 1. 总体结论",
        "",
        overlay_conclusion,
        "",
        "## 2. 检查表",
        "",
        "| 检查项 | 结果 | 备注 |",
        "|---|---|---|",
    ]
    for name, ok, note in overlay_checks:
        overlay_lines.append(f"| {name} | {status(ok)} | `{note}` |")
    overlay_lines.extend(
        [
            "",
            "## 3. 失败项",
            "",
            "无。" if not overlay_failed else "\n".join(f"- {name}: {note}" for name, _, note in overlay_failed),
            "",
        ]
    )
    OVERLAY_AUDIT_REPORT.parent.mkdir(parents=True, exist_ok=True)
    OVERLAY_AUDIT_REPORT.write_text("\n".join(overlay_lines), encoding="utf-8")

    full_checks: list[tuple[str, bool, str]] = []
    all_required = FULL_REQUIRED_HTML + CORE_READER_HTML
    html_texts = {name: read(ROOT / "docs/ui-handoff/normalized-html" / name) for name in all_required}
    for name in FULL_REQUIRED_HTML:
        path = ROOT / "docs/ui-handoff/normalized-html" / name
        text = html_texts[name]
        full_checks.append((f"新增页面存在 {name}", path.exists(), str(path.relative_to(ROOT))))
        full_checks.append((f"{name} 引用 design tokens", "../css/reader-design-tokens.css" in text, "reader-design-tokens.css"))
        full_checks.append((f"{name} 引用 components css", "../css/reader-components.css" in text, "reader-components.css"))
        full_checks.append((f"{name} 引用 handoff boundary css", "../css/reader-handoff-boundary.css" in text, "reader-handoff-boundary.css"))
        full_checks.append((f"{name} 使用 reader-* 语义类", "reader-" in text, "reader semantic classes"))
        full_checks.append((f"{name} 无旧 Stitch 类", has_none(text, OLD_CLASSES), "old classes"))
        full_checks.append((f"{name} 无旧颜色", has_none(text, OLD_COLORS), "old colors"))

    for name in CORE_READER_HTML:
        text = html_texts[name]
        full_checks.append((f"阅读控制层已通过页面存在 {name}", bool(text), name))
        full_checks.append((f"阅读控制层已通过页面无旧类 {name}", has_none(text, OLD_CLASSES), name))
        full_checks.append((f"阅读控制层已通过页面无旧色 {name}", has_none(text, OLD_COLORS), name))

    for name in REQUIRED_COMPONENTS:
        path = ROOT / "docs/ui-handoff/components" / name
        full_checks.append((f"组件存在 {name}", path.exists(), str(path.relative_to(ROOT))))
        full_checks.append((f"组件使用 reader-* {name}", "reader-" in read(path), name))

    screen_matrix = read(ROOT / "docs/ui-handoff/SCREEN_MATRIX.md")
    route_map = read(ROOT / "docs/ui-handoff/ROUTE_MAP.md")
    state_matrix = read(ROOT / "docs/ui-handoff/STATE_MATRIX.md")
    compose_map = read(ROOT / "docs/ui-handoff/ANDROID_COMPOSE_MAPPING.md")

    for name in all_required:
        full_checks.append((f"SCREEN_MATRIX 覆盖 {name}", name in screen_matrix, name))
    for route in ["/bookshelf", "/search", "/discover", "/sources", "/settings", "/settings/webdav", "/reader", "/book/detail"]:
        full_checks.append((f"ROUTE_MAP 覆盖 {route}", route in route_map, route))
    for state in ["loading", "empty", "error", "offline", "permission required"]:
        full_checks.append((f"STATE_MATRIX 覆盖 {state}", state in state_matrix, state))
    for comp in ["ReaderAppShell", "MainTabBar", "BookshelfScreen", "BookCard", "BookListItem", "SearchScreen", "SearchResultItem", "BookDetailScreen", "SourceManagementScreen", "SourceListItem", "DiscoverScreen", "RssListScreen", "SettingsScreen", "WebDavSettingsScreen", "StatePage"]:
        full_checks.append((f"ANDROID_COMPOSE_MAPPING 覆盖 {comp}", comp in compose_map, comp))

    global_settings = html_texts["global-settings.html"]
    reader_settings = html_texts["reader-settings-overlay-v2.html"]
    full_checks.append(("全局设置包含 WebDAV / 同步 / 书源入口", has_all(global_settings, ["WebDAV 设置入口", "同步设置入口", "书源设置入口", "备份恢复入口", "关于版本"]), "global settings"))
    full_checks.append(("阅读页底栏设置未混入全局设置", has_none(reader_settings, ["WebDAV", "书源管理", "RSS", "备份恢复", "关于版本"]), "reader settings overlay"))
    for label, names in [("搜索", ["search-home.html", "search-results.html", "book-detail.html"]), ("详情", ["book-detail.html", "book-detail-toc-preview.html"]), ("书源", ["source-management-list.html", "source-detail.html"]), ("发现", ["discover-home.html", "rss-list.html"]), ("WebDAV", ["webdav-config.html", "remote-webdav-books.html", "sync-error.html"] )]:
        full_checks.append((f"{label} 页面覆盖", all((ROOT / "docs/ui-handoff/normalized-html" / n).exists() for n in names), ", ".join(names)))

    registered = set(FULL_REQUIRED_HTML + CORE_READER_HTML)
    actual = {p.name for p in (ROOT / "docs/ui-handoff/normalized-html").glob("*.html")}
    full_checks.append(("无未登记 normalized HTML", actual.issubset(registered), ", ".join(sorted(actual - registered)) or "无"))

    full_failed = [item for item in full_checks if not item[1]]
    full_conclusion = "FULL_FRONTEND_NORMALIZED_HTML_AUDIT_READY" if not full_failed else "FULL_FRONTEND_NORMALIZED_HTML_AUDIT_NOT_READY"
    full_lines = ["# Full Frontend Normalized HTML Audit", "", "## 1. 总体结论", "", full_conclusion, "", "## 2. 检查表", "", "| 检查项 | 结果 | 备注 |", "|---|---|---|"]
    for name, ok, note in full_checks:
        full_lines.append(f"| {name} | {status(ok)} | `{note}` |")
    full_lines.extend(["", "## 3. P0 / P1 汇总", "", "- P0：0" if not full_failed else f"- P0：{len(full_failed)}", "- P1：0", "", "## 4. 失败项", "", "无。" if not full_failed else "\n".join(f"- {name}: {note}" for name, _, note in full_failed), ""])
    FULL_AUDIT_REPORT.write_text("\n".join(full_lines), encoding="utf-8")

    total_failed = len(failed) + len(overlay_failed) + len(full_failed)
    print(full_conclusion)
    print(f"base_failed={len(failed)}")
    print(f"overlay_failed={len(overlay_failed)}")
    print(f"full_failed={len(full_failed)}")
    print(f"report={FULL_AUDIT_REPORT.relative_to(ROOT)}")
    return 0 if total_failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
