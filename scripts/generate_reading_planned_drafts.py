from pathlib import Path


OUT = Path(".stitch/designs")


CHAPTERS = [
    ("第 1 章 愚者", "已读 100%"),
    ("第 2 章 塔罗会", "已读 82%"),
    ("第 3 章 值夜者", "未读"),
    ("第 4 章 灰雾之上", "未读"),
    ("第 5 章 占卜家", "未读"),
]


def base_css() -> str:
    return """
    :root {
      --paper:#f7f0e4; --paper2:#f8f5ef; --surface:#fffdf8; --soft:#fbf6ee;
      --line:#e7ded0; --line2:#ddd2c3; --ink:#1c1812; --muted:#665d52;
      --faint:#8c8173; --accent:#4b817a; --accent2:#ddebe5;
      --gold:#f4dfa8; --danger:#9f4d43;
    }
    *{box-sizing:border-box}
    html,body{margin:0;width:390px;height:884px;overflow:hidden;background:var(--paper);font-family:"Noto Sans SC","PingFang SC",system-ui,sans-serif;color:var(--ink);letter-spacing:0}
    .screen{position:relative;width:390px;height:884px;overflow:hidden;background:radial-gradient(circle at 50% 48%,rgba(255,255,255,.34),transparent 58%),linear-gradient(90deg,rgba(97,75,45,.08),transparent 9%,transparent 91%,rgba(97,75,45,.08)),var(--paper)}
    .reader{position:absolute;left:34px;top:126px;width:322px;bottom:64px;overflow:hidden;font-family:"Noto Serif SC","Songti SC","STSong",serif;color:#24201a}
    .reader h1{margin:0 0 32px;font-size:22px;line-height:32px;font-weight:600}
    .reader p{margin:0 0 17px;font-size:17px;line-height:34px;text-indent:2em;word-break:break-all}
    .topbar{position:absolute;left:13px;top:43px;z-index:3;width:364px;height:59px;border:1px solid #d9cec0;border-radius:14px;background:rgba(255,253,248,.985);box-shadow:0 7px 19px rgba(44,35,24,.16);display:grid;grid-template-columns:42px 1fr 50px 30px;align-items:center;gap:4px;padding:0 17px}
    .topbtn,.more{width:34px;height:42px;border:0;background:transparent;color:var(--ink);font-size:24px}
    .book-title{font-size:20px;line-height:24px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .book-meta{margin-top:2px;font-size:13px;line-height:17px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
    .source{font-size:12px;text-align:center;color:var(--ink)}
    .panel{position:absolute;left:5px;top:528px;z-index:3;width:380px;height:346px;border:1px solid #d9cec0;border-radius:18px;background:rgba(255,253,248,.985);box-shadow:0 12px 32px rgba(44,35,24,.2);overflow:hidden}
    .handle{position:absolute;top:12px;left:171px;width:38px;height:4px;border-radius:999px;background:#b5aa9d}
    .left{position:absolute;left:10px;top:34px;width:288px;height:297px;overflow:hidden}
    .bright{position:absolute;right:10px;top:34px;width:62px;height:226px;border:1px solid var(--line);border-radius:16px;background:#fffefa;display:grid;grid-template-rows:34px 1fr 34px;place-items:center;color:#5e554b}
    .rail{width:4px;height:136px;background:#e6dac8;border-radius:9px;position:relative}.rail:after{content:"";position:absolute;left:0;bottom:0;width:4px;height:82px;background:var(--accent);border-radius:9px}
    .nav4{position:absolute;right:10px;bottom:15px;width:62px;height:56px;display:grid;grid-template-columns:1fr 1fr;gap:8px}.nav4 div{border:1px solid var(--line);border-radius:12px;background:#fffefa;display:grid;place-items:center;font-size:15px;color:#5e554b}.nav4 .on{background:#ddebe5;color:#2f6f69;border-color:#b9d4cb}
    .card{border:1px solid var(--line);border-radius:14px;background:rgba(255,254,250,.8)}
    .chiprow{display:flex;gap:8px;align-items:center}.chip{height:32px;padding:0 13px;border-radius:16px;background:#f4efe6;color:#786f63;font-size:13px;font-weight:500;display:grid;place-items:center}.chip.on{background:#ddebe5;color:#2f6f69;font-weight:650}
    .row{display:grid;grid-template-columns:1fr auto;align-items:center;min-height:38px;border-bottom:1px solid rgba(231,222,208,.75);gap:8px}.row:last-child{border-bottom:0}.row .t{font-size:14px;color:#3d352c;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.row .m{font-size:12px;color:#8c8173}
    .primary{height:42px;border:0;border-radius:21px;background:var(--accent);color:white;font-size:14px;font-weight:650;padding:0 18px}.secondary{height:34px;border:0;border-radius:17px;background:#f4efe6;color:#5e554b;font-size:13px;padding:0 14px}
    .slider{height:4px;background:#e7ded0;border-radius:8px;position:relative}.slider:before{content:"";position:absolute;left:0;top:0;height:4px;width:58%;background:var(--accent);border-radius:8px}.slider:after{content:"";position:absolute;left:56%;top:-6px;width:16px;height:16px;border-radius:50%;background:var(--accent);box-shadow:0 2px 8px rgba(0,0,0,.18)}
    .overlay-scrim{position:absolute;inset:0;background:rgba(34,28,20,.18);z-index:4}.sheet{position:absolute;left:14px;right:14px;z-index:5;border:1px solid var(--line);border-radius:20px;background:var(--surface);box-shadow:0 18px 44px rgba(44,35,24,.24);overflow:hidden}.sheet.top{top:86px}.sheet.mid{top:180px}.sheet.bottom{bottom:16px}
    .search{height:44px;border:1px solid var(--line);border-radius:14px;background:#fffefa;display:flex;align-items:center;gap:8px;padding:0 13px;color:#8c8173;font-size:14px}
    .full{width:390px;height:884px;overflow:hidden;background:var(--paper2);padding:42px 16px 18px}.fullbar{height:50px;display:grid;grid-template-columns:36px 1fr 40px;align-items:center;margin-bottom:12px}.fullbar h1{margin:0;font-size:21px;text-align:center}.section{border:1px solid var(--line);border-radius:16px;background:var(--surface);padding:14px;margin-bottom:12px}.section h2{margin:0 0 10px;font-size:14px;color:#8c8173}.setting{min-height:52px;display:grid;grid-template-columns:1fr auto;align-items:center;border-bottom:1px solid rgba(231,222,208,.8);gap:12px}.setting:last-child{border-bottom:0}.setting strong{font-size:15px}.setting span{display:block;margin-top:4px;font-size:12px;color:#8c8173}.switch{width:42px;height:24px;border-radius:12px;background:#dce9e4;position:relative}.switch:after{content:"";position:absolute;right:2px;top:2px;width:20px;height:20px;border-radius:50%;background:#4b817a}
    .capsule{position:absolute;left:55px;top:604px;z-index:5;width:280px;height:44px;border:1px solid var(--line);border-radius:18px;background:var(--surface);box-shadow:0 10px 26px rgba(44,35,24,.18);display:grid;grid-template-columns:28px 1fr 34px 34px;align-items:center;gap:8px;padding:0 12px;font-size:14px}.capsule b{font-size:14px}.capsule span{font-size:12px;color:#8c8173}
    .swatches{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.swatch{height:48px;border-radius:12px;border:1px solid var(--line);background:#f7f0e4}.swatch.on{outline:2px solid var(--accent)}
    .grid2{display:grid;grid-template-columns:1fr 1fr;gap:10px}.tile{min-height:52px;border:1px solid var(--line);border-radius:14px;background:#fffefa;padding:10px;font-size:13px}.tile span{display:block;margin-top:5px;color:#8c8173;font-size:12px}
    mark{background:#f4dfa8;color:#3d352c;border-radius:4px;padding:0 2px}
    """


def reader_bg() -> str:
    return """
    <div class="reader">
      <h1>第一章 愚者</h1>
      <p>深红色的月光照进房间，照在书桌边缘，也照在摊开的笔记上。</p>
      <p>克莱恩缓慢睁开眼睛，陌生的记忆像潮水一样涌来，他听见远处钟声回荡。</p>
      <p>空气里有纸张和墨水的味道，窗外的街道安静得像被灰雾轻轻盖住。</p>
      <p>他低头看见自己写下的句子，意识到这不是一场普通的梦。</p>
    </div>
    """


def control_shell(content: str, active: str) -> str:
    nav = "".join(f"<div class=\"{'on' if n == active else ''}\">{n}</div>" for n in ["目", "听", "界", "设"])
    return f"""
    {reader_bg()}
    <div class="topbar"><button class="topbtn">‹</button><div><div class="book-title">诡秘之主</div><div class="book-meta">第 1 章 愚者 · 36%</div></div><div class="source">换源</div><button class="more">⋯</button></div>
    <div class="panel"><div class="handle"></div><div class="left">{content}</div><div class="bright"><div>☀</div><div class="rail"></div><div>☾</div></div><div class="nav4">{nav}</div></div>
    """


def wrap(title: str, body: str, full: bool = False) -> str:
    return f"""<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{title}</title><style>{base_css()}</style></head><body><div class="{'full' if full else 'screen'}">{body}</div></body></html>"""


def quick_catalog() -> str:
    rows = "".join(f"<div class='row'><div class='t'>{c}</div><div class='m'>{m}</div></div>" for c, m in CHAPTERS[:4])
    return f"<div style='padding:0 2px'><div class='chiprow' style='margin-bottom:10px'><div class='chip on'>目录</div><div class='chip'>书签</div><div class='chip'>笔记</div></div><div class='card' style='padding:10px 12px;margin-bottom:9px'><div style='font-size:13px;color:#8c8173'>当前章节</div><div style='margin-top:4px;font-size:16px;font-weight:650'>第 1 章 愚者</div></div><div class='card' style='padding:0 12px'>{rows}</div></div>"


def quick_tts() -> str:
    return "<div style='padding:2px'><div class='card' style='height:92px;display:grid;grid-template-columns:64px 1fr;align-items:center;padding:14px'><button class='primary' style='width:52px;height:52px;border-radius:26px;padding:0;font-size:20px'>▶</button><div><b>朗读未开始</b><span style='display:block;margin-top:6px;color:#8c8173;font-size:12px'>云端自然女声 · 标准语速</span></div></div><div class='card' style='padding:14px;margin-top:9px'><div class='row'><div class='t'>语速</div><div class='m'>1.0x</div></div><div class='slider' style='margin:16px 2px 8px'></div></div><div class='grid2' style='margin-top:9px'><div class='tile'>定时<span>15 分钟</span></div><div class='tile'>声音<span>清晰女声</span></div></div></div>"


def quick_appearance() -> str:
    return "<div style='padding:2px'><div class='card' style='padding:14px;margin-bottom:9px'><div class='row'><div class='t'>字号</div><div style='display:flex;align-items:center;gap:14px'><button class='secondary'>-</button><b style='font-size:20px'>17</b><button class='secondary'>+</button></div></div></div><div class='card' style='padding:12px;margin-bottom:9px'><div class='chiprow'><div class='chip on'>默认</div><div class='chip'>护眼</div><div class='chip'>夜间</div></div><div class='swatches' style='margin-top:12px'><div class='swatch on'></div><div class='swatch' style='background:#eef3e7'></div><div class='swatch' style='background:#1f211f'></div><div class='swatch' style='background:#f0e0c4'></div></div></div><div class='grid2'><div class='tile'>字体<span>系统宋体</span></div><div class='tile'>翻页<span>仿真滑动</span></div></div></div>"


def quick_settings() -> str:
    return "<div class='grid2' style='padding:2px'><div class='tile'>屏幕常亮<span>已开启</span></div><div class='tile'>音量翻页<span>关闭</span></div><div class='tile'>点击区域<span>标准</span></div><div class='tile'>段落缩进<span>2 字</span></div><div class='tile'>信息显示<span>章节+进度</span></div><div class='tile'>更多设置<span>进入完整页</span></div></div>"


def overlay_catalog() -> str:
    rows = "".join(f"<div class='row'><div class='t'>{c}</div><div class='m'>{m}</div></div>" for c, m in CHAPTERS)
    return f"{reader_bg()}<div class='overlay-scrim'></div><div class='sheet bottom' style='height:580px;padding:16px'><div class='chiprow'><div class='chip on'>目录</div><div class='chip'>书签</div><div class='chip'>笔记</div></div><div class='search' style='margin:14px 0'>⌕ 搜索章节或书签</div><div class='card' style='padding:0 12px'>{rows}</div><button class='primary' style='width:100%;margin-top:14px'>回到当前章节</button></div>"


def overlay_search() -> str:
    rows = ["他看见<mark>灰雾</mark>在眼前铺开，像一扇通往未知的门。", "钟声穿过<mark>灰雾</mark>，带着难以描述的回响。", "那片<mark>灰雾</mark>之后，似乎有人正在注视。"]
    cards = "".join(f"<div class='section' style='margin-bottom:10px'><div style='font-size:12px;color:#8c8173;margin-bottom:6px'>第 {i+1} 章 · 第 {12+i*8} 段</div><div style='font-size:15px;line-height:24px'>{r}</div></div>" for i, r in enumerate(rows))
    return f"{reader_bg()}<div class='overlay-scrim'></div><div class='sheet top' style='height:660px;padding:14px'><div class='search'>⌕ 灰雾</div><div style='margin:12px 2px;color:#8c8173;font-size:13px'>当前书籍共 12 个结果</div>{cards}</div>"


def overlay_replace() -> str:
    return f"{reader_bg()}<div class='overlay-scrim'></div><div class='sheet mid' style='height:430px;padding:16px'><h2 style='margin:0 0 12px;font-size:18px'>内容替换</h2><div class='search'>原词：佛尔思</div><div class='search' style='margin-top:10px'>显示为：佛尔思小姐</div><div class='section' style='margin-top:12px'><h2>预览</h2><div style='font-size:14px;line-height:24px;color:#8c8173'>原文：佛尔思拿起笔记。</div><div style='font-size:15px;line-height:26px;margin-top:6px'>替换后：佛尔思小姐拿起笔记。</div></div><button class='primary' style='width:100%;margin-top:12px'>保存显示替换</button></div>"


def overlay_autopage() -> str:
    return f"{reader_bg()}<div class='overlay-scrim'></div><div class='sheet bottom' style='height:360px;padding:16px'><h2 style='margin:0 0 12px;font-size:18px'>自动翻页</h2><div class='chiprow'><div class='chip on'>匀速滚动</div><div class='chip'>分页定时</div><div class='chip'>眼动辅助</div></div><div class='section' style='margin-top:14px'><div class='setting'><div><strong>速度</strong><span>约 38 秒/页</span></div><b>中速</b></div><div class='slider' style='margin:18px 2px 6px'></div></div><button class='primary' style='width:100%;margin-top:8px'>开始自动翻页</button></div>"


def capsule(kind: str) -> str:
    text = "朗读中 · 自然女声" if kind == "tts" else "自动翻页 · 中速"
    icon = "▶" if kind == "tts" else "↓"
    return f"{reader_bg()}<div class='capsule'><div>{icon}</div><div><b>{text}</b><span>第 1 章 愚者</span></div><div>Ⅱ</div><div>×</div></div>"


def full_page(title: str, sections: list[tuple[str, list[tuple[str, str, str]]]]) -> str:
    blocks = []
    for h, rows in sections:
        normalized = [(r[0], r[1], r[2] if len(r) > 2 else "进入") for r in rows]
        inner = "".join(f"<div class='setting'><div><strong>{a}</strong><span>{b}</span></div><div>{c}</div></div>" for a, b, c in normalized)
        blocks.append(f"<div class='section'><h2>{h}</h2>{inner}</div>")
    return wrap(title, f"<div class='fullbar'><div style='font-size:24px'>‹</div><h1>{title}</h1><div style='text-align:right'>⋯</div></div>{''.join(blocks)}", True)


def write(name: str, html: str) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"{name}.html").write_text(html, encoding="utf-8")


def main() -> None:
    drafts = {
        "目录与书签-目录快捷模块-初稿": wrap("目录与书签-目录快捷模块-初稿", control_shell(quick_catalog(), "目")),
        "目录与书签-目录覆盖层-初稿": wrap("目录与书签-目录覆盖层-初稿", overlay_catalog()),
        "朗读-快捷模块-初稿": wrap("朗读-快捷模块-初稿", control_shell(quick_tts(), "听")),
        "朗读-运行胶囊-初稿": wrap("朗读-运行胶囊-初稿", capsule("tts")),
        "阅读外观-快捷模块-初稿": wrap("阅读外观-快捷模块-初稿", control_shell(quick_appearance(), "界")),
        "阅读设置-快捷模块-初稿": wrap("阅读设置-快捷模块-初稿", control_shell(quick_settings(), "设")),
        "内容搜索-覆盖层-初稿": wrap("内容搜索-覆盖层-初稿", overlay_search()),
        "内容替换-快捷覆盖层-初稿": wrap("内容替换-快捷覆盖层-初稿", overlay_replace()),
        "自动翻页-设置覆盖层-初稿": wrap("自动翻页-设置覆盖层-初稿", overlay_autopage()),
        "自动翻页-运行胶囊-初稿": wrap("自动翻页-运行胶囊-初稿", capsule("auto")),
    }
    for name, html in drafts.items():
        write(name, html)

    fulls = {
        "目录与书签-完整目录页-初稿": [("章节", CHAPTERS + [("第 6 章 观众", "未读"), ("第 7 章 正义", "未读")])],
        "朗读-设置页-初稿": [("声音", [("朗读声音", "自然女声", "更换"), ("语速", "1.0x", "调整"), ("定时关闭", "15 分钟", "开启")]), ("偏好", [("自动跳过标题", "关闭", "<div class='switch'></div>"), ("后台朗读", "开启", "<div class='switch'></div>")])],
        "阅读外观-字体选择-初稿": [("字体", [("系统宋体", "适合长文阅读", "已选"), ("系统黑体", "清晰紧凑", ""), ("霞鹜文楷", "柔和手写感", "")]), ("字号", [("正文字号", "17sp", "调整"), ("标题字号", "22sp", "调整")])],
        "阅读外观-主题选择-初稿": [("主题", [("默认纸色", "暖纸背景", "已选"), ("护眼绿色", "低饱和浅绿", ""), ("夜间模式", "深色阅读", ""), ("羊皮纸", "复古纸面", "")])],
        "阅读外观-主题编辑-初稿": [("颜色", [("背景色", "暖纸色", "编辑"), ("正文色", "高对比墨色", "编辑"), ("高亮色", "浅金色", "编辑")]), ("保存", [("保存为自定义主题", "同步到阅读外观", "保存")])],
        "阅读外观-版式高级-初稿": [("版式", [("页边距", "左右 34dp", "调整"), ("行高", "34dp", "调整"), ("段间距", "17dp", "调整"), ("首行缩进", "2 字", "调整")])],
        "阅读外观-翻页动画-初稿": [("动画", [("仿真滑动", "默认动画", "已选"), ("覆盖", "稳定低干扰", ""), ("无动画", "最快响应", "")])],
        "阅读设置-首页-初稿": [("阅读行为", [("屏幕与显示", "亮度、常亮、方向", "进入"), ("翻页与手势", "点击区和音量键", "进入"), ("阅读辅助", "朗读、自动翻页辅助", "进入"), ("进度与信息", "状态栏和章末信息", "进入")])],
        "阅读设置-屏幕与显示-初稿": [("显示", [("屏幕常亮", "阅读时保持亮屏", "<div class='switch'></div>"), ("跟随系统亮度", "关闭后可独立调节", "<div class='switch'></div>"), ("屏幕方向", "跟随系统", "选择")])],
        "阅读设置-翻页与手势-初稿": [("手势", [("点击区域", "标准三分区", "设置"), ("音量键翻页", "关闭", "<div class='switch'></div>"), ("左右滑动翻页", "开启", "<div class='switch'></div>")])],
        "阅读设置-阅读辅助-初稿": [("辅助", [("段落高亮", "关闭", "<div class='switch'></div>"), ("长按查词", "开启", "<div class='switch'></div>"), ("阅读计时", "开启", "<div class='switch'></div>")])],
        "阅读设置-进度与信息-初稿": [("信息层", [("顶部弱信息", "章节名", "设置"), ("底部弱信息", "进度+电量", "设置"), ("章末统计", "开启", "<div class='switch'></div>")])],
        "内容搜索-完整搜索页-初稿": [("搜索结果", [("第 1 章 愚者", "命中 3 处灰雾", "查看"), ("第 4 章 灰雾之上", "命中 6 处灰雾", "查看"), ("第 18 章 聚会", "命中 1 处灰雾", "查看")])],
        "内容替换-规则管理页-初稿": [("规则", [("佛尔思 -> 佛尔思小姐", "当前书籍生效", "编辑"), ("贝克兰德 -> 贝克兰德城", "全局显示替换", "编辑")])],
        "内容替换-规则编辑页-初稿": [("规则编辑", [("匹配文本", "佛尔思", "输入"), ("显示为", "佛尔思小姐", "输入"), ("作用范围", "当前书籍", "选择"), ("启用规则", "开启", "<div class='switch'></div>")])],
        "自动翻页-设置页-初稿": [("自动翻页", [("翻页模式", "匀速滚动", "选择"), ("速度", "中速", "调整"), ("触边暂停", "开启", "<div class='switch'></div>"), ("防误触", "开启", "<div class='switch'></div>")])],
    }
    for name, sections in fulls.items():
        write(name, full_page(name.replace("-初稿", ""), sections))


if __name__ == "__main__":
    main()
