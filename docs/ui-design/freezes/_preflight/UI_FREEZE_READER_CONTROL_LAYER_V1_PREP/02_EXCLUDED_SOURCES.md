# 排除来源规则

以下目录不得作为正式冻结包 final 图片来源：

| 路径 | 排除原因 | 允许用途 |
| --- | --- | --- |
| `docs/ui-design/drafts/_contact_sheets/` | 联系图、候选图、排查图，不是单屏 final 设计稿。 | 仅用于人工对比和历史排查。 |
| `docs/ui-design/drafts/_progress_restore_backup/` | 处理底部进度条前的备份，不是 final 来源。 | 仅用于回滚或对比。 |
| `docs/ui-design/drafts/_rejected/` | 已明确否决或废弃。 | 仅用于说明为什么不采用。 |

## 明确排除图片

| 图片 | 排除原因 |
| --- | --- |
| `docs/ui-design/drafts/18-1-read-aloud-quick-panel.png` | 朗读快捷区上一版，包含段落信息且语速/音色/范围/定时按钮过小。文档已指定 `18-3-read-aloud-quick-panel-height-aligned.png` 为当前采用版参考。 |
| `docs/ui-design/drafts/_rejected/22-1-reader-settings-main-panel.png` | 已否决；改变了四主按钮、快捷区、章节进度和亮度条的空间关系。 |
| `docs/ui-design/drafts/_rejected/41-reader-directory-full-page-flow.png` | 已否决；目录视觉和跳转逻辑偏离原始方案。 |

## 外观图组决策

`28-34` 与 `35-39` 的使用边界已人工确认：

- `28-33`：来源于 `21` 拆解，不使用。
- `34`：不使用。
- `35-39`：作为完整界面全页面候选。

当前状态：`DECIDED`

冻结前排除：

- `docs/ui-design/drafts/28-reader-appearance-main-panel.png`
- `docs/ui-design/drafts/29-reader-appearance-font-selection.png`
- `docs/ui-design/drafts/30-reader-appearance-theme-selection.png`
- `docs/ui-design/drafts/31-reader-appearance-theme-edit.png`
- `docs/ui-design/drafts/32-reader-appearance-layout-advanced.png`
- `docs/ui-design/drafts/33-reader-appearance-page-turn-animation.png`
- `docs/ui-design/drafts/34-reader-appearance-main-panel-standard.png`

冻结前候选：

- `docs/ui-design/drafts/35-reader-appearance-font-selection-standard.png`
- `docs/ui-design/drafts/36-reader-appearance-theme-selection-standard.png`
- `docs/ui-design/drafts/37-reader-appearance-theme-edit-standard.png`
- `docs/ui-design/drafts/38-reader-appearance-layout-advanced-standard.png`
- `docs/ui-design/drafts/39-reader-appearance-page-turn-animation-standard.png`

说明：`35-39` 作为完整界面全页面候选；`21-1` 仍是阅读控制层内的界面快捷模块候选。正式冻结时应区分“快捷界面”和“完整界面全页面”。
