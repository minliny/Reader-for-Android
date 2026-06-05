# 视觉 Token 草案

说明：当前文档多为方向性描述，缺少冻结级数值。未明确数值不得凭空编造，统一标记为 `NEEDS_MEASUREMENT` 或 `NEEDS_DESIGN_DECISION`。

| Token | 当前值 | 来源 | 状态 | 问题 |
| ----- | --- | -- | -- | -- |
| 背景色 | 柔和纸张背景、暖色阅读页 | `specs/reader.md`、`drafts/README.md` | `INFERRED` | 缺少色值。 |
| 正文文字色 | 适合长时间阅读的深色正文 | `specs/reader.md` | `INFERRED` | 缺少色值和不同主题值。 |
| 控制层遮罩色 | 半透明遮罩 | 现有图片与控制层描述 | `NEEDS_MEASUREMENT` | 缺少透明度和色值。 |
| 图标色 | 深色主图标、弱信息图标 | 现有图片 | `NEEDS_MEASUREMENT` | 缺少色值。 |
| 字体 | 中文阅读字体，默认/系统/宋体/黑体 | `specs/reader-appearance.md` | `NEEDS_DESIGN_DECISION` | 冻结包需要明确默认字体策略。 |
| 字号 | 正文 `18` 在界面快捷模块中出现 | `specs/reader-appearance.md` | `INFERRED` | 顶部栏、按钮、标签、正文各自字号未冻结。 |
| 字重 | 标题较重、正文常规 | 现有图片 | `NEEDS_MEASUREMENT` | 缺少数值。 |
| 行高 | 宽松行高 | `specs/reader.md` | `INFERRED` | 缺少数值。 |
| 圆角 | 控制面板、卡片、按钮均为圆角 | 现有图片 | `NEEDS_MEASUREMENT` | 缺少 dp 数值。 |
| 阴影 | 柔和卡片阴影 | `drafts/README.md` | `INFERRED` | 缺少阴影参数。 |
| 间距 | 分区式信息块 | `drafts/README.md` | `INFERRED` | 缺少横向/纵向间距数值。 |
| 顶部栏高度 | 未明确 | 无冻结级文档 | `NEEDS_MEASUREMENT` | 必须测量或人工定义。 |
| 底部栏高度 | 未明确 | 无冻结级文档 | `NEEDS_MEASUREMENT` | 四主按钮区域高度需冻结。 |
| 章节进度条高度 | 未明确 | 图片和 `specs/reader.md` | `NEEDS_MEASUREMENT` | 需区分控制层内部章节进度条与底部阅读细进度条。 |
| 快捷面板高度 | 未明确 | `reader-control-layer-modules.md` | `NEEDS_MEASUREMENT` | 控制层位置争议未解决。 |
| 亮度条尺寸 | 未明确 | `reader-control-auxiliary.md` | `NEEDS_MEASUREMENT` | 需明确右侧亮度条高度、宽度、位置。 |
| 遮罩透明度 | 未明确 | 现有图片 | `NEEDS_MEASUREMENT` | 需要冻结。 |
| 图标尺寸 | 未明确 | 现有图片 | `NEEDS_MEASUREMENT` | 四主按钮和顶部按钮需分别定义。 |
| 按钮间距 | 未明确 | 现有图片 | `NEEDS_MEASUREMENT` | 需冻结四主按钮间距。 |
| 安全区规则 | 未明确 | 无冻结级文档 | `NEEDS_DESIGN_DECISION` | 需确认状态栏和底部手势区是否包含在设计图内。 |
