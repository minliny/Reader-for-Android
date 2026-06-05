# Final 图片候选清单

状态说明：

- `CANDIDATE_OK`：可作为候选 final，但仍需正式冻结确认。
- `NEEDS_REDRAW`：图片存在底部进度条、控制层位置、尺寸或规范冲突，需要重画。
- `NEEDS_HEIGHT_ONLY_REDRAW`：需要重画/重生成，但只允许调整相对高度，其他元素不修改。
- `NEEDS_DECISION`：存在多张同状态或边界不清，需要人工选择。
- `NOT_FOUND`：未找到对应图片。

| 状态 ID | 状态说明 | 首选候选图 | 备选图 | 是否可直接冻结 | 问题 | 建议 |
| ----- | ---- | ----- | --- | ------- | -- | -- |
| `reader_immersive` | 阅读沉浸页 | `docs/ui-design/drafts/03-reader-immersive.png` | 无 | `CANDIDATE_OK` | 已确认 `03` 不变。 | 作为候选进入预冻结清单。 |
| `reader_overlay_default` | 阅读控制层默认态 | `docs/ui-design/drafts/04-reader-controls.png` | 无 | `CANDIDATE_OK` | 已确认 `04` 不变。 | 作为候选进入预冻结清单。 |
| `reader_overlay_progress_drag` | 章节进度拖动辅助态 | `docs/ui-design/drafts/27-reader-chapter-progress-flow.png` | 无 | `NEEDS_DECISION` | 现有图是流程/辅助示意，不是标准手机 final 截图。 | 决定是否需要单独标准手机图。 |
| `reader_overlay_brightness` | 亮度辅助状态 | `docs/ui-design/drafts/26-reader-brightness-system-state.png` | 无 | `NEEDS_DECISION` | 现有图为横向辅助状态示意，不是标准手机 final 截图。 | 决定是否仅作为说明图，或补标准手机状态图。 |
| `reader_overlay_directory` | 目录/书签快捷模块 | `docs/ui-design/drafts/16-5-reader-toc-quick-panel-height-aligned.png` | `docs/ui-design/drafts/16-4-reader-toc-quick-panel.png` | `CANDIDATE_OK` | 已按 `04-reader-controls.png` 完成相对高度对齐；`16-toc-overlay` 是目录覆盖层，不等同快捷模块。 | 作为候选进入预冻结清单。 |
| `reader_overlay_tts` | 朗读快捷模块 | `docs/ui-design/drafts/18-3-read-aloud-quick-panel-height-aligned.png` | `docs/ui-design/drafts/18-2-read-aloud-quick-panel-revised.png` | `CANDIDATE_OK` | 已按 `04-reader-controls.png` 完成相对高度对齐；`18-1` 是上一版，必须排除。 | 作为候选进入预冻结清单。 |
| `reader_tts_running` | 朗读运行胶囊 | `docs/ui-design/drafts/19-1-read-aloud-running-capsule-height-aligned.png` | `docs/ui-design/drafts/19-read-aloud-running-capsule.png` | `CANDIDATE_OK` | 已按 `04-reader-controls.png` 完成相对高度对齐；是否纳入 V1 仍需冻结时确认。 | 如纳入 V1，使用高度对齐版。 |
| `reader_tts_settings` | 朗读设置页 | `docs/ui-design/drafts/20-read-aloud-settings.png` | 无 | `CANDIDATE_OK` | 是否纳入阅读控制层 V1 未确认。 | 人工确认是否作为 V1 附属完整页进入。 |
| `reader_overlay_appearance` | 界面快捷模块 | `docs/ui-design/drafts/21-1-reader-appearance-quick-panel.png` | 无 | `CANDIDATE_OK` | 已确认 `34` 不使用；快捷界面以 `21-1` 为候选。 | 快捷界面和完整界面全页面必须分状态命名。 |
| `reader_appearance_full_font` | 完整字体选择页面 | `docs/ui-design/drafts/35-reader-appearance-font-selection-standard.png` | 无 | `CANDIDATE_OK` | 已确认 `35-39` 作为完整界面全页面候选。 | 进入完整界面全页面候选组。 |
| `reader_appearance_full_theme` | 完整主题选择页面 | `docs/ui-design/drafts/36-reader-appearance-theme-selection-standard.png` | 无 | `CANDIDATE_OK` | 已确认 `35-39` 作为完整界面全页面候选。 | 进入完整界面全页面候选组。 |
| `reader_appearance_full_theme_edit` | 完整主题编辑页面 | `docs/ui-design/drafts/37-reader-appearance-theme-edit-standard.png` | 无 | `CANDIDATE_OK` | 已确认 `35-39` 作为完整界面全页面候选。 | 进入完整界面全页面候选组。 |
| `reader_appearance_full_layout` | 完整版式高级页面 | `docs/ui-design/drafts/38-reader-appearance-layout-advanced-standard.png` | 无 | `CANDIDATE_OK` | 已确认 `35-39` 作为完整界面全页面候选。 | 进入完整界面全页面候选组。 |
| `reader_appearance_full_page_turn` | 完整翻页动画页面 | `docs/ui-design/drafts/39-reader-appearance-page-turn-animation-standard.png` | 无 | `CANDIDATE_OK` | 已确认 `35-39` 作为完整界面全页面候选。 | 进入完整界面全页面候选组。 |
| `reader_overlay_settings` | 设置快捷模块 | `docs/ui-design/drafts/22-2-reader-settings-quick-panel-height-aligned.png` | `docs/ui-design/drafts/22-1-reader-settings-quick-panel.png` | `CANDIDATE_OK` | 已按 `04-reader-controls.png` 完成相对高度对齐；`22-1` 为高度对齐前版本。 | 作为候选进入预冻结清单。 |
| `reader_overlay_auto_scroll` | 自动翻页设置覆盖层 | `docs/ui-design/drafts/10-auto-page-turn-setup-overlay.png` | `docs/ui-design/drafts/11-auto-page-turn-running-capsule.png` | `CANDIDATE_OK` | 属于临时覆盖层，不是四主按钮快捷模块；是否纳入 V1 需确认。 | 作为入口覆盖层候选，运行胶囊单独决策。 |
| `reader_overlay_content_search` | 内容搜索覆盖层 | `docs/ui-design/drafts/08-content-search-overlay.png` | `docs/ui-design/drafts/09-content-search-page.png` | `CANDIDATE_OK` | 完整搜索页是否纳入 V1 未确认。 | V1 优先纳入覆盖层，完整页作为延展。 |
| `reader_overlay_replace` | 内容替换覆盖层 | `docs/ui-design/drafts/13-replacement-quick-overlay.png` | `docs/ui-design/drafts/14-replacement-rules.png` | `CANDIDATE_OK` | 完整规则页是否纳入 V1 未确认。 | V1 优先纳入快捷覆盖层。 |
| `reader_top_more_menu` | 顶部更多菜单 | `docs/ui-design/drafts/25-4-reader-top-more-menu-height-aligned.png` | `docs/ui-design/drafts/25-1-reader-top-more-menu.png`、`docs/ui-design/drafts/25-2-reader-refresh-feedback.png`、`docs/ui-design/drafts/25-3-reader-debug-info-sheet.png` | `CANDIDATE_OK` | 已按 `04-reader-controls.png` 完成相对高度对齐；是否纳入 V1 需冻结时确认。 | 如纳入 V1，使用高度对齐版。 |

## 复制到候选目录的图片

候选图已复制到：

`docs/ui-design/freezes/_preflight/UI_FREEZE_READER_CONTROL_LAYER_V1_PREP/image-candidates/`

`image-candidates/_superseded/` 只保存高度对齐前的旧候选复制件，不作为 final 来源。
