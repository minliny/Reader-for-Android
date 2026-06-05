# Stitch 就绪度 — 图片元素设计审计

> 审计维度：UI 组件/元素是否足够 Stitch 复刻风格
> 日期：2026-06-05

---

| 元素类型 | 是否已有样例图 | 是否统一 | 是否足够给 Stitch | 缺口 | 优先级 |
| ---- | ------- | ---- | ------------ | -- | --- |
| 主底栏（四 tab） | PARTIAL（分散在各图中） | NEEDS_DECISION | PARTIAL | 在 01/02/04/41/42/43 中有出现，但无独立组件规范或尺寸标注。Stitch 可提取但需确认选中态图标样式。 | P1 |
| 顶栏 | SUFFICIENT | YES | YES | 各页面顶栏风格一致：返回 + 标题 + 操作图标。Stitch 可直接参照。 | -- |
| 阅读控制层顶部栏 | SUFFICIENT | YES | YES | 04 及衍生图中风格一致。 | -- |
| 阅读控制层底部栏 | SUFFICIENT | YES | YES | 04 已确认基准，16-5/18-3/22-2/25-4 已对齐，19-1 独立胶囊。 | -- |
| 章节进度条 | SUFFICIENT | YES | YES | 04 + 27 覆盖。 | -- |
| 四主按钮（目录/朗读/界面/设置） | SUFFICIENT | YES | YES | 04 和图 16-5/18-3/21-1/22-2 覆盖默认和高亮态。Stitch 可直接提取。 | -- |
| 图标体系 | PARTIAL | INCONSISTENT | PARTIAL | 各图标分散在 40+ 张图中，无独立图标清单/样式指南。Stitch 能提取但可能不一致（如换源图标在 04 和 24-1 中可能出现不同样式）。 | P1 |
| 书籍卡片 | SUFFICIENT | YES | YES | 01/02/06/07 中风格一致。 | -- |
| 搜索框 | SUFFICIENT | YES | YES | 05/06/08/09 中风格一致。 | -- |
| 列表行 | SUFFICIENT | YES | YES | 01/02/14/16/17 中风格一致（圆角卡片/分隔行）。 | -- |
| 设置项 row | SUFFICIENT | YES | YES | 22-1/22-2/43 及 reader-settings spec 中风格一致。 | -- |
| Switch 开关 | SUFFICIENT | YES | YES | 13/14/22-1/24-1 中出现，样式一致。 | -- |
| Slider 滑块 | SUFFICIENT | YES | YES | 04（亮度）/10（翻页速度）/20（语速）中出现，样式一致。 | -- |
| 分段控件 | SUFFICIENT | YES | YES | 21-1（行距/页边距）/05（搜索范围）中出现。 | -- |
| Bottom sheet / 弹层 | SUFFICIENT | YES | YES | 13/24-1/24-2/24-3 覆盖（圆角顶、暖色卡片、紧凑列表）。 | -- |
| 全页设置页 | MISSING | N/A | NO | 阅读内完整设置页（4+ 子页）无图。书源管理全部页面无图。 | P0 |
| 空状态插画/图标 | MISSING | N/A | NO | 书架/搜索/替换/书源管理/内容搜索全缺空状态图。 | P0 |
| Loading 状态 | MISSING | N/A | NO | 所有模块缺加载状态图。 | P0 |
| Error 状态 | MISSING | N/A | NO | 所有模块缺错误态图（除换源 24-2/24-3 有类似态）。 | P0 |
| 删除/确认弹窗 | MISSING | N/A | NO | 删除书源/移除书架/清空缓存等全缺确认弹窗图。 | P1 |
| Toast / Snackbar | MISSING | N/A | NO | 25-2（刷新反馈）是唯一轻量反馈。缺通用 toast/snackbar 设计。 | P1 |
| 颜色 token | PARTIAL | INCONSISTENT | PARTIAL | 从各图可推断（暖色卡、米色背景、柔和纸张色），但无正式提取的 token 清单。Stitch 能提取但可能有细微偏差。 | P2 |
| 字体 token | PARTIAL | INCONSISTENT | PARTIAL | 字体选择/字号均有图，但无正式字号/行高/字重体系。 | P2 |
| 间距 token | PARTIAL | INCONSISTENT | PARTIAL | 从图中可看出页边距和间距规律，但无正式 token。 | P2 |
| 圆角/阴影 token | PARTIAL | INCONSISTENT | PARTIAL | 卡片圆角和阴影风格在各图中一致，但无正式提取。 | P2 |

---

## 关键判断

1. **Stitch 可直接提取的组件**：底栏（需人工确认选中态）、顶栏、四主按钮、书籍卡片、搜索框、列表行、Switch、Slider、分段控件、Bottom sheet。核心交互组件在 40+ 张图中都有呈现且风格统一。
2. **Stitch 无法生成的组件**：空状态（无参考图）、loading（无参考图）、error 态（无参考图）、确认弹窗（无参考图）。
3. **视觉 token 未提取但不阻塞**：颜色/字体/间距/圆角 token 未正式提取，但 Stitch 可以从多张图中学习模式。如需精确前端复刻，仍需补充 token 文档。
4. **主要元素缺口不阻塞阅读模块初稿**，但会阻塞全 App 涉及状态切换的页面。
