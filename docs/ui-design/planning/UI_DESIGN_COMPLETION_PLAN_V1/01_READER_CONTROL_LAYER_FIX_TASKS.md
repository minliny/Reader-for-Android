# 阅读控制层修正任务

对齐基准：`docs/ui-design/drafts/04-reader-controls.png`

修正原则：

- 底部工具栏位置对齐 `04-reader-controls.png`。
- 底部章节进度条位于底部工具栏上方。
- 不得出现贴屏幕底部的错误细进度条。
- 控制层不得改变正文排版。
- 快捷区不得挤压正文。
- 顶部栏和底部栏位置稳定。
- `16-4 / 18-2 / 19 / 22-1 / 25-1` 已完成高度对齐重画。
- 当前采用 `16-5 / 18-3 / 19-1 / 22-2 / 25-4`。
- 重画只调整相对高度，让它们与 `04-reader-controls.png` 对齐。
- 除相对高度之外，其他元素、文字、按钮、图标、模块结构、视觉风格均不得修改。

| 原图 | 对齐基准 | 问题 | 修正结果 | 是否阻塞冻结 | 当前采用文件名 |
| -- | ---- | -- | ---- | ------ | ------ |
| `docs/ui-design/drafts/16-4-reader-toc-quick-panel.png` | `04-reader-controls.png` | 目录快捷模块相对高度需与默认控制层统一。 | 已完成；只调整相对高度；底部工具栏对齐 04；正文不重排。 | 否 | `16-5-reader-toc-quick-panel-height-aligned.png` |
| `docs/ui-design/drafts/18-2-read-aloud-quick-panel-revised.png` | `04-reader-controls.png` | 朗读快捷模块相对高度需与默认控制层统一。 | 已完成；只调整相对高度；保留大尺寸语速/音色/范围/定时模块。 | 否 | `18-3-read-aloud-quick-panel-height-aligned.png` |
| `docs/ui-design/drafts/19-read-aloud-running-capsule.png` | `04-reader-controls.png` | 朗读运行态相对高度需明确。 | 已完成；只调整相对高度；运行胶囊不挤压正文；不得出现贴底细进度条。 | 否 | `19-1-read-aloud-running-capsule-height-aligned.png` |
| `docs/ui-design/drafts/22-1-reader-settings-quick-panel.png` | `04-reader-controls.png` | 设置快捷模块曾被指出控制层疑似下移。 | 已完成；只调整相对高度；底部工具栏、右侧亮度条、快捷区位置对齐 04。 | 否 | `22-2-reader-settings-quick-panel-height-aligned.png` |
| `docs/ui-design/drafts/25-1-reader-top-more-menu.png` | `04-reader-controls.png` | 顶部菜单状态相对高度需明确。 | 已完成；只调整相对高度；顶部菜单位置稳定；无贴底细进度条。 | 否 | `25-4-reader-top-more-menu-height-aligned.png` |

## 完成标准

- 五张图均已完成位置对齐。
- 与 `04-reader-controls.png` 对比，顶部栏、底部栏、正文层不漂移。
- 通过人工复审后，才可进入 `UI_FREEZE_READER_CONTROL_LAYER_V1`。
