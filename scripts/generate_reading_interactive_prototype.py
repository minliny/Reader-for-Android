from pathlib import Path
from typing import Optional

from generate_reading_planned_drafts import CHAPTERS, base_css, reader_bg


OUT = Path(".stitch/designs")


def rows(limit: Optional[int] = None) -> str:
    data = CHAPTERS if limit is None else CHAPTERS[:limit]
    return "".join(
        f"<button class='row action' data-close='1'><span class='t'>{title}</span><span class='m'>{meta}</span></button>"
        for title, meta in data
    )


def quick_catalog() -> str:
    return f"""
      <div class="chiprow"><button class="chip on">目录</button><button class="chip">书签</button><button class="chip">笔记</button></div>
      <button class="card action block" data-overlay="catalog">
        <span class="eyebrow">当前章节</span>
        <strong>第 1 章 愚者</strong>
        <small>打开目录覆盖层</small>
      </button>
      <div class="card list">{rows(4)}</div>
      <button class="primary wide" data-page="catalogFull">完整目录</button>
    """


def quick_tts() -> str:
    return """
      <div class="card play-card">
        <button class="play" data-run="tts">▶</button>
        <div><strong>朗读未开始</strong><span>云端自然女声 · 标准语速</span></div>
      </div>
      <div class="card pad">
        <div class="row plain"><span class="t">语速</span><span class="m">1.0x</span></div>
        <div class="slider"></div>
      </div>
      <div class="grid2">
        <button class="tile" data-page="ttsSettings">定时<span>15 分钟</span></button>
        <button class="tile" data-page="ttsSettings">声音<span>清晰女声</span></button>
      </div>
    """


def quick_appearance() -> str:
    return """
      <div class="card pad">
        <div class="row plain"><span class="t">字号</span><span><button class="secondary">-</button><b class="number">17</b><button class="secondary">+</button></span></div>
      </div>
      <div class="card pad">
        <div class="chiprow"><button class="chip on">默认</button><button class="chip">护眼</button><button class="chip">夜间</button></div>
        <div class="swatches"><button class="swatch on" data-page="themePick"></button><button class="swatch green" data-page="themePick"></button><button class="swatch dark" data-page="themePick"></button><button class="swatch tan" data-page="themeEdit"></button></div>
      </div>
      <div class="grid2">
        <button class="tile" data-page="fontPick">字体<span>系统宋体</span></button>
        <button class="tile" data-page="animation">翻页<span>仿真滑动</span></button>
      </div>
    """


def quick_settings() -> str:
    return """
      <div class="grid2">
        <button class="tile" data-page="display">屏幕常亮<span>已开启</span></button>
        <button class="tile" data-page="gesture">音量翻页<span>关闭</span></button>
        <button class="tile" data-page="gesture">点击区域<span>标准</span></button>
        <button class="tile" data-page="layout">段落缩进<span>2 字</span></button>
        <button class="tile" data-page="info">信息显示<span>章节+进度</span></button>
        <button class="tile" data-page="settingsHome">更多设置<span>进入完整页</span></button>
      </div>
    """


def full_page_template(title: str, content: str) -> str:
    return f"""
      <section class="page" data-view="{title}">
        <div class="fullbar">
          <button class="back" data-back="reader">‹</button>
          <h1>{title}</h1>
          <button class="more">⋯</button>
        </div>
        {content}
      </section>
    """


def section(title: str, items: list[tuple[str, str, str]]) -> str:
    body = "".join(
        f"<button class='setting'><span><strong>{a}</strong><small>{b}</small></span><span>{c}</span></button>"
        for a, b, c in items
    )
    return f"<div class='section'><h2>{title}</h2>{body}</div>"


def build_html() -> str:
    full_pages = {
        "catalogFull": ("完整目录", section("章节", [(a, b, "进入") for a, b in CHAPTERS + [("第 6 章 观众", "未读"), ("第 7 章 正义", "未读")]])),
        "ttsSettings": ("朗读设置", section("声音", [("朗读声音", "自然女声", "更换"), ("语速", "1.0x", "调整"), ("定时关闭", "15 分钟", "开启")]) + section("偏好", [("自动跳过标题", "关闭", "关"), ("后台朗读", "开启", "开")])),
        "fontPick": ("字体选择", section("字体", [("系统宋体", "适合长文阅读", "已选"), ("系统黑体", "清晰紧凑", ""), ("霞鹜文楷", "柔和手写感", "")]) + section("字号", [("正文字号", "17sp", "调整"), ("标题字号", "22sp", "调整")])),
        "themePick": ("主题选择", section("主题", [("默认纸色", "暖纸背景", "已选"), ("护眼绿色", "低饱和浅绿", ""), ("夜间模式", "深色阅读", ""), ("羊皮纸", "复古纸面", "")])),
        "themeEdit": ("主题编辑", section("颜色", [("背景色", "暖纸色", "编辑"), ("正文色", "高对比墨色", "编辑"), ("高亮色", "浅金色", "编辑")]) + section("保存", [("保存为自定义主题", "同步到阅读外观", "保存")])),
        "layout": ("版式高级", section("版式", [("页边距", "左右 34dp", "调整"), ("行高", "34dp", "调整"), ("段间距", "17dp", "调整"), ("首行缩进", "2 字", "调整")])),
        "animation": ("翻页动画", section("动画", [("仿真滑动", "默认动画", "已选"), ("覆盖", "稳定低干扰", ""), ("无动画", "最快响应", "")])),
        "settingsHome": ("阅读设置", section("阅读行为", [("屏幕与显示", "亮度、常亮、方向", "进入"), ("翻页与手势", "点击区和音量键", "进入"), ("阅读辅助", "朗读、自动翻页辅助", "进入"), ("进度与信息", "状态栏和章末信息", "进入")])),
        "display": ("屏幕与显示", section("显示", [("屏幕常亮", "阅读时保持亮屏", "开"), ("跟随系统亮度", "关闭后可独立调节", "开"), ("屏幕方向", "跟随系统", "选择")])),
        "gesture": ("翻页与手势", section("手势", [("点击区域", "标准三分区", "设置"), ("音量键翻页", "关闭", "关"), ("左右滑动翻页", "开启", "开")])),
        "info": ("进度与信息", section("信息层", [("顶部弱信息", "章节名", "设置"), ("底部弱信息", "进度+电量", "设置"), ("章末统计", "开启", "开")])),
        "searchFull": ("内容搜索", section("搜索结果", [("第 1 章 愚者", "命中 3 处灰雾", "查看"), ("第 4 章 灰雾之上", "命中 6 处灰雾", "查看"), ("第 18 章 聚会", "命中 1 处灰雾", "查看")])),
        "replaceList": ("替换规则", section("规则", [("佛尔思 -> 佛尔思小姐", "当前书籍生效", "编辑"), ("贝克兰德 -> 贝克兰德城", "全局显示替换", "编辑")]) + "<button class='primary wide' data-page='replaceEdit'>新增规则</button>"),
        "replaceEdit": ("编辑规则", section("规则编辑", [("匹配文本", "佛尔思", "输入"), ("显示为", "佛尔思小姐", "输入"), ("作用范围", "当前书籍", "选择"), ("启用规则", "开启", "开")])),
        "autoSettings": ("自动翻页设置", section("自动翻页", [("翻页模式", "匀速滚动", "选择"), ("速度", "中速", "调整"), ("触边暂停", "开启", "开"), ("防误触", "开启", "开")])),
    }
    pages = "".join(full_page_template(title, content) for title, content in full_pages.values())
    page_map = "{" + ",".join(f"'{key}':'{value[0]}'" for key, value in full_pages.items()) + "}"

    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>阅读派生页面交互初稿</title>
  <style>
{base_css()}
    button{{font:inherit;color:inherit;border:0;background:transparent;padding:0;text-align:left}}
    .app{{position:relative;width:390px;height:884px;overflow:hidden;background:var(--paper)}}
    .screen{{display:block}}
    .screen.hidden,.page.hidden,.overlay.hidden,.run.hidden,.full-root.hidden{{display:none}}
    .panel-tabs button{{cursor:pointer}}
    .nav4 button{{border:1px solid var(--line);border-radius:12px;background:#fffefa;display:grid;place-items:center;font-size:15px;color:#5e554b}}
    .nav4 button.on{{background:#ddebe5;color:#2f6f69;border-color:#b9d4cb}}
    .left{{padding:0 2px}}
    .block{{display:block;width:100%;padding:10px 12px;margin:9px 0}}
    .block strong{{display:block;margin-top:4px;font-size:16px}}
    .block small,.eyebrow{{display:block;margin-top:4px;color:#8c8173;font-size:12px}}
    .list{{padding:0 12px;margin-bottom:9px}}
    .row.action,.setting,.tile,.chip,.swatch,.primary,.secondary,.play,.back,.more,.topbtn,.source{{cursor:pointer}}
    .row.action{{width:100%}}
    .row.plain{{border-bottom:0}}
    .wide{{width:100%;display:grid;place-items:center;text-align:center}}
    .pad{{padding:14px;margin-bottom:9px}}
    .play-card{{height:92px;display:grid;grid-template-columns:64px 1fr;align-items:center;padding:14px;margin-bottom:9px}}
    .play{{width:52px;height:52px;border-radius:26px;background:var(--accent);color:#fff;display:grid;place-items:center;font-size:20px;text-align:center}}
    .play-card span{{display:block;margin-top:6px;color:#8c8173;font-size:12px}}
    .number{{display:inline-block;min-width:28px;text-align:center;font-size:20px}}
    .swatches{{margin-top:12px}}
    .swatch.green{{background:#eef3e7}}.swatch.dark{{background:#1f211f}}.swatch.tan{{background:#f0e0c4}}
    .full-root{{position:absolute;inset:0;z-index:8;background:var(--paper2)}}
    .page{{width:390px;height:884px;overflow:hidden;background:var(--paper2);padding:42px 16px 18px}}
    .page:not(.active){{display:none}}
    .fullbar button{{font-size:24px;text-align:center}}
    .setting{{width:100%;min-height:52px}}
    .setting small{{display:block;margin-top:4px;font-size:12px;color:#8c8173}}
    .overlay{{position:absolute;inset:0;z-index:6}}
    .sheet h2{{margin:0 0 12px;font-size:18px}}
    .top-actions{{position:absolute;left:34px;right:34px;top:480px;z-index:4;display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px}}
    .top-actions button{{height:34px;border-radius:17px;background:rgba(255,253,248,.82);border:1px solid var(--line);text-align:center;font-size:12px;color:#5e554b}}
    .capsule button{{display:grid;place-items:center;text-align:center}}
  </style>
</head>
<body>
<div class="app">
  <main class="screen" id="readerView">
    {reader_bg()}
    <div class="top-actions">
      <button data-overlay="search">搜正文</button>
      <button data-overlay="replace">替换</button>
      <button data-overlay="auto">自动翻页</button>
    </div>
    <div class="topbar">
      <button class="topbtn">‹</button>
      <div><div class="book-title">诡秘之主</div><div class="book-meta">第 1 章 愚者 · 36%</div></div>
      <button class="source">换源</button>
      <button class="more">⋯</button>
    </div>
    <div class="panel">
      <div class="handle"></div>
      <div class="left" id="quickContent"></div>
      <div class="bright"><div>☀</div><div class="rail"></div><div>☾</div></div>
      <div class="nav4 panel-tabs">
        <button data-module="catalog">目</button>
        <button data-module="tts">听</button>
        <button data-module="appearance">界</button>
        <button data-module="settings">设</button>
      </div>
    </div>
  </main>

  <div class="run hidden" id="runCapsule">
    <div class="capsule"><div id="runIcon">▶</div><div><b id="runTitle">朗读中 · 自然女声</b><span>第 1 章 愚者</span></div><button>Ⅱ</button><button data-close-run="1">×</button></div>
  </div>

  <div class="overlay hidden" id="overlayRoot">
    <div class="overlay-scrim" data-close-overlay="1"></div>
    <div id="overlayContent"></div>
  </div>

  <div class="full-root hidden" id="fullRoot">
    {pages}
  </div>
</div>

<script>
const quick = {{
  catalog: `{quick_catalog()}`,
  tts: `{quick_tts()}`,
  appearance: `{quick_appearance()}`,
  settings: `{quick_settings()}`
}};
const pageMap = {page_map};
const overlay = {{
  catalog: `<div class="sheet bottom" style="height:580px;padding:16px"><div class="chiprow"><button class="chip on">目录</button><button class="chip">书签</button><button class="chip">笔记</button></div><div class="search" style="margin:14px 0">⌕ 搜索章节或书签</div><div class="card list">{rows()}</div><button class="primary wide" data-page="catalogFull">完整目录</button></div>`,
  search: `<div class="sheet top" style="height:660px;padding:14px"><div class="search">⌕ 灰雾</div><div style="margin:12px 2px;color:#8c8173;font-size:13px">当前书籍共 12 个结果</div><div class="section"><h2>第 1 章 · 第 12 段</h2><div style="font-size:15px;line-height:24px">他看见<mark>灰雾</mark>在眼前铺开，像一扇通往未知的门。</div></div><div class="section"><h2>第 4 章 · 第 20 段</h2><div style="font-size:15px;line-height:24px">钟声穿过<mark>灰雾</mark>，带着难以描述的回响。</div></div><button class="primary wide" data-page="searchFull">查看全部结果</button></div>`,
  replace: `<div class="sheet mid" style="height:430px;padding:16px"><h2>内容替换</h2><div class="search">原词：佛尔思</div><div class="search" style="margin-top:10px">显示为：佛尔思小姐</div><div class="section" style="margin-top:12px"><h2>预览</h2><div style="font-size:14px;line-height:24px;color:#8c8173">原文：佛尔思拿起笔记。</div><div style="font-size:15px;line-height:26px;margin-top:6px">替换后：佛尔思小姐拿起笔记。</div></div><button class="primary wide" data-page="replaceList">管理规则</button></div>`,
  auto: `<div class="sheet bottom" style="height:360px;padding:16px"><h2>自动翻页</h2><div class="chiprow"><button class="chip on">匀速滚动</button><button class="chip">分页定时</button><button class="chip">眼动辅助</button></div><div class="section" style="margin-top:14px"><button class="setting"><span><strong>速度</strong><small>约 38 秒/页</small></span><b>中速</b></button><div class="slider" style="margin:18px 2px 6px"></div></div><button class="primary wide" data-run="auto">开始自动翻页</button><button class="secondary wide" style="margin-top:8px" data-page="autoSettings">完整设置</button></div>`
}};

const quickContent = document.getElementById('quickContent');
const tabs = [...document.querySelectorAll('[data-module]')];
const overlayRoot = document.getElementById('overlayRoot');
const overlayContent = document.getElementById('overlayContent');
const fullRoot = document.getElementById('fullRoot');
const runCapsule = document.getElementById('runCapsule');

function setModule(name) {{
  quickContent.innerHTML = quick[name];
  tabs.forEach(tab => tab.classList.toggle('on', tab.dataset.module === name));
}}

function openOverlay(name) {{
  overlayContent.innerHTML = overlay[name];
  overlayRoot.classList.remove('hidden');
}}

function closeOverlay() {{
  overlayRoot.classList.add('hidden');
  overlayContent.innerHTML = '';
}}

function openPage(key) {{
  closeOverlay();
  const title = pageMap[key];
  fullRoot.classList.remove('hidden');
  document.querySelectorAll('.page').forEach(page => page.classList.toggle('active', page.dataset.view === title));
}}

function closePage() {{
  fullRoot.classList.add('hidden');
  document.querySelectorAll('.page').forEach(page => page.classList.remove('active'));
}}

function startRun(kind) {{
  closeOverlay();
  runCapsule.classList.remove('hidden');
  document.getElementById('runIcon').textContent = kind === 'auto' ? '↓' : '▶';
  document.getElementById('runTitle').textContent = kind === 'auto' ? '自动翻页 · 中速' : '朗读中 · 自然女声';
}}

document.addEventListener('click', event => {{
  const target = event.target.closest('button,[data-overlay],[data-page],[data-module],[data-run],[data-close-overlay],[data-close-run],[data-back],[data-close]');
  if (!target) return;
  if (target.dataset.module) setModule(target.dataset.module);
  if (target.dataset.overlay) openOverlay(target.dataset.overlay);
  if (target.dataset.page) openPage(target.dataset.page);
  if (target.dataset.run) startRun(target.dataset.run);
  if (target.dataset.closeOverlay || target.dataset.close) closeOverlay();
  if (target.dataset.closeRun) runCapsule.classList.add('hidden');
  if (target.dataset.back) closePage();
}});

setModule('catalog');
</script>
</body>
</html>"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "阅读派生页面交互初稿.html").write_text(build_html(), encoding="utf-8")


if __name__ == "__main__":
    main()
