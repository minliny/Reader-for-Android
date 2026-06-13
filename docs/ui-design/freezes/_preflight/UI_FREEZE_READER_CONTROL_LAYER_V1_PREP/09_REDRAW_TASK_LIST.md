# 重画或重导出任务清单

优先级：

- `P0`：阻塞正式冻结包。
- `P1`：影响一致性，但可在 P0 解决后处理。

## 已完成的历史高度对齐

以下图片曾按 `04-reader-controls.png` 完成相对高度对齐，但不满足当前 `04-2` freeze 标准。
它们只保留内容或交互参考，不再作为当前采用版。

| 历史图 | 当前采用图 | 影响状态 | 完成标准 | 优先级 |
| -- | -- | ---- | ---- | --- |
| `docs/ui-design/drafts/16-4-reader-toc-quick-panel.png` | `docs/ui-design/drafts/16-5-reader-toc-quick-panel-height-aligned.png` | `reader_overlay_directory` | 历史高度对齐完成；只保留内容参考。 | `REFERENCE_ONLY` |
| `docs/ui-design/drafts/18-2-read-aloud-quick-panel-revised.png` | `docs/ui-design/drafts/18-3-read-aloud-quick-panel-height-aligned.png` | `reader_overlay_tts` | 历史高度对齐完成；只保留内容参考。 | `REFERENCE_ONLY` |
| `docs/ui-design/drafts/19-read-aloud-running-capsule.png` | `docs/ui-design/drafts/19-1-read-aloud-running-capsule-height-aligned.png` | `reader_tts_running` | 仅调整相对高度；运行胶囊不挤压正文。 | `DONE` |
| `docs/ui-design/drafts/22-1-reader-settings-quick-panel.png` | `docs/ui-design/drafts/22-2-reader-settings-quick-panel-height-aligned.png` | `reader_overlay_settings` | 历史高度对齐完成；只保留内容参考。 | `REFERENCE_ONLY` |
| `docs/ui-design/drafts/25-1-reader-top-more-menu.png` | `docs/ui-design/drafts/25-4-reader-top-more-menu-height-aligned.png` | `reader_top_more_menu` | 仅调整相对高度；顶部菜单位置稳定。 | `DONE` |

## 已完成 P0 标准重绘

| 内容参考图 | 目标图 | 影响状态 | 重画要求 | 优先级 |
| -- | -- | -- | -- | -- |
| `16-5-reader-toc-quick-panel-height-aligned.png` | `16-6-reader-toc-quick-panel-04-2-standard.png` | `reader_overlay_directory` | 保留目录字段和状态语义，完整继承 `04-2` 固定外壳与 token。 | `DONE` |
| `18-3-read-aloud-quick-panel-height-aligned.png` | `18-4-read-aloud-quick-panel-04-2-standard.png` | `reader_overlay_tts` | 保留朗读字段和状态语义，完整继承 `04-2` 固定外壳与 token。 | `DONE` |
| `21-1-reader-appearance-quick-panel.png` | `21-2-reader-appearance-quick-panel-04-2-standard.png` | `reader_overlay_appearance` | 保留界面字段和状态语义，完整继承 `04-2` 固定外壳与 token。 | `DONE` |
| `22-2-reader-settings-quick-panel-height-aligned.png` | `22-3-reader-settings-quick-panel-04-2-standard.png` | `reader_overlay_settings` | 保留设置字段和状态语义，完整继承 `04-2` 固定外壳与 token。 | `DONE` |

## 待决 P1

| 图片 | 问题 | 影响状态 | 重画要求 | 优先级 |
| -- | -- | ---- | ---- | --- |
| `docs/ui-design/drafts/26-reader-brightness-system-state.png` | 横向辅助示意，不是标准手机 final。 | `reader_overlay_brightness` | 决定是否需要重画标准手机状态图；亮度条不得影响正文布局。 | `P1` |
| `docs/ui-design/drafts/27-reader-chapter-progress-flow.png` | 横向辅助示意，不是标准手机 final。 | `reader_overlay_progress_drag` | 决定是否需要重画标准手机状态图；章节进度提示必须为浮层。 | `P1` |

## 已确认不调整

以下图片不做调整：

- `docs/ui-design/drafts/03-reader-immersive.png`
- `docs/ui-design/drafts/04-2-reader-controls-opaque-icon-aligned.png`

## 已确认不使用

`28-34` 不进入后续冻结候选，不需要重画：

- `docs/ui-design/drafts/28-reader-appearance-main-panel.png`
- `docs/ui-design/drafts/29-reader-appearance-font-selection.png`
- `docs/ui-design/drafts/30-reader-appearance-theme-selection.png`
- `docs/ui-design/drafts/31-reader-appearance-theme-edit.png`
- `docs/ui-design/drafts/32-reader-appearance-layout-advanced.png`
- `docs/ui-design/drafts/33-reader-appearance-page-turn-animation.png`
- `docs/ui-design/drafts/34-reader-appearance-main-panel-standard.png`
