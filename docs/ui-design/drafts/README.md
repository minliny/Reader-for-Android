# 阅读器 UI 设计示意图

本目录保存当前已接受的 UI 方向示意图。

## 风格基线

- 主视觉方向：柔和卡片 + 分区式信息块
- 阅读页基线：适合长时间阅读的极简沉浸页
- 阅读覆盖层：作为图层叠加在已确认的阅读页之上，不重排正文

## 图片保存规则

- 已接受的主示意图不得被新生成图片覆盖。
- 为已有页面补充状态、标签页、菜单或弹窗时，使用派生编号命名：`原编号-序号-页面说明.png`。
- 示例：`16-1-toc-overlay-bookmark-tab.png` 表示 `16-toc-overlay.png` 的书签标签状态；`17-1-toc-full-bookmark-tab.png` 表示 `17-toc-full.png` 的书签标签状态。
- 派生图必须在本索引中单独登记，并在对应规格文档中说明它和主图的关系。
- 排查用临时图不进入正式索引，应放入 `_debug/` 或 `_contact_sheets/`，不得作为设计依据。

## 示意图索引

| 文件 | 页面 |
| --- | --- |
| `01-bookshelf-cover.png` | 书架首页，封面模式 |
| `02-bookshelf-list.png` | 书架首页，列表模式 |
| `03-reader-immersive.png` | 阅读沉浸页基线 |
| `04-reader-controls.png` | 阅读控制层 |
| `04-1-reader-control-layer-module-plan.svg` | 阅读控制层模块规划图 |
| `05-book-search.png` | 书籍搜索页 |
| `06-book-search-results.png` | 书籍搜索结果页 |
| `07-book-info.png` | 书籍信息页 |
| `08-content-search-overlay.png` | 阅读中内容搜索覆盖层 |
| `09-content-search-page.png` | 完整内容搜索页 |
| `10-auto-page-turn-setup-overlay.png` | 自动翻页设置覆盖层 |
| `11-auto-page-turn-running-capsule.png` | 自动翻页运行胶囊 |
| `12-auto-page-turn-settings.png` | 自动翻页设置页 |
| `13-replacement-quick-overlay.png` | 内容替换快捷覆盖层 |
| `14-replacement-rules.png` | 替换规则管理页 |
| `15-replacement-rule-edit.png` | 替换规则编辑页 |
| `16-toc-overlay.png` | 目录覆盖层 |
| `16-1-toc-overlay-bookmark-tab.png` | 目录覆盖层，书签标签状态 |
| `16-2-toc-overlay-search-state.png` | 目录覆盖层，搜索输入状态 |
| `16-3-toc-overlay-more-menu.png` | 目录覆盖层，三点菜单状态 |
| `16-4-reader-toc-quick-panel.png` | 阅读控制层，目录快捷模块，高度对齐前版本 |
| `16-5-reader-toc-quick-panel-height-aligned.png` | 阅读控制层，目录快捷模块当前采用版，高度对齐 04 |
| `17-toc-full.png` | 完整目录页 |
| `17-1-toc-full-bookmark-tab.png` | 完整目录页，书签标签状态 |
| `18-read-aloud-overlay.png` | 朗读控制覆盖层 |
| `18-1-read-aloud-quick-panel.png` | 阅读控制层，朗读快捷模块上一版 |
| `18-2-read-aloud-quick-panel-revised.png` | 阅读控制层，朗读快捷模块高度对齐前版本 |
| `18-3-read-aloud-quick-panel-height-aligned.png` | 阅读控制层，朗读快捷模块当前采用版，高度对齐 04 |
| `19-read-aloud-running-capsule.png` | 朗读运行胶囊，高度对齐前版本 |
| `19-1-read-aloud-running-capsule-height-aligned.png` | 朗读运行胶囊当前采用版，高度对齐 04 |
| `20-read-aloud-settings.png` | 朗读设置页 |
| `21-reader-appearance-flow.png` | 阅读界面流程示意图 |
| `21-1-reader-appearance-quick-panel.png` | 阅读控制层，界面快捷模块 |
| `22-reader-settings-flow.png` | 阅读设置流程示意图 |
| `22-1-reader-settings-quick-panel.png` | 阅读控制层，设置快捷模块，高度对齐前版本 |
| `22-2-reader-settings-quick-panel-height-aligned.png` | 阅读控制层，设置快捷模块当前采用版，高度对齐 04 |
| `23-reader-night-mode-state.png` | 阅读夜间模式状态示意图 |
| `24-reader-source-switch-flow.png` | 阅读页换源流程示意图 |
| `24-1-reader-source-switch-list.png` | 阅读页换源，来源列表 |
| `24-2-reader-source-checking-state.png` | 阅读页换源，来源检查中 |
| `24-3-reader-source-check-result.png` | 阅读页换源，来源检查结果 |
| `25-reader-top-more-menu-flow.png` | 阅读页顶部更多菜单示意图 |
| `25-1-reader-top-more-menu.png` | 阅读页顶部更多菜单展开，高度对齐前版本 |
| `25-4-reader-top-more-menu-height-aligned.png` | 阅读页顶部更多菜单展开当前采用版，高度对齐 04 |
| `25-2-reader-refresh-feedback.png` | 阅读页刷新本章轻反馈 |
| `25-3-reader-debug-info-sheet.png` | 阅读页调试信息底表 |
| `26-reader-brightness-system-state.png` | 阅读亮度系统模式状态示意图 |
| `27-reader-chapter-progress-flow.png` | 阅读章节进度控制示意图 |
| `28-reader-appearance-main-panel.png` | 阅读界面主面板，来源于 21 拆解 |
| `29-reader-appearance-font-selection.png` | 阅读界面字体选择，来源于 21 拆解 |
| `30-reader-appearance-theme-selection.png` | 阅读界面主题选择，来源于 21 拆解 |
| `31-reader-appearance-theme-edit.png` | 阅读界面主题编辑，来源于 21 拆解 |
| `32-reader-appearance-layout-advanced.png` | 阅读界面版式高级，来源于 21 拆解 |
| `33-reader-appearance-page-turn-animation.png` | 阅读界面翻页动画，来源于 21 拆解 |
| `34-reader-appearance-main-panel-standard.png` | 阅读界面主面板标准比例图，基于 21 拆解重绘 |
| `35-reader-appearance-font-selection-standard.png` | 阅读界面字体选择标准比例图，基于 21 拆解重绘 |
| `36-reader-appearance-theme-selection-standard.png` | 阅读界面主题选择标准比例图，基于 21 拆解重绘 |
| `37-reader-appearance-theme-edit-standard.png` | 阅读界面主题编辑标准比例图，基于 21 拆解重绘 |
| `38-reader-appearance-layout-advanced-standard.png` | 阅读界面版式高级标准比例图，基于 21 拆解重绘 |
| `39-reader-appearance-page-turn-animation-standard.png` | 阅读界面翻页动画标准比例图，基于 21 拆解重绘 |
| `40-reader-appearance-font-management-states.png` | 字体选择/管理全屏多状态示意图 |
| `41-discovery-default.png` | 发现页默认态上一版，未体现信息源与来源分类切换 |
| `41-1-discovery-source-category.png` | 发现页默认态当前采用版，信息源与来源分类切换 |
| `42-rss-home.png` | RSS/订阅首页主页面暂定版 |
| `43-settings-home.png` | 设置首页主页面暂定版 |
| `44-source-management-home.png` | 书源管理首页默认态暂定版 |

## 说明

- 书架卡片不展示书源。
- 书籍搜索结果会将相同书名和作者合并为一个书籍实体，书源切换作为属性或操作展示。
- 内容搜索独立于书籍搜索，只搜索当前书籍内部。
- 目录章节行目前只显示缓存和书签状态，已移除未读标记。
- `_rejected/41-reader-directory-full-page-flow.png` 是后续误生成的目录方案，不属于已接受示意图，不作为目录或书签页面设计依据。
- `_rejected/22-1-reader-settings-main-panel.png` 是被否决的阅读设置快捷面板方案：它改变了最开始阅读控制层中四个主按钮、快捷区、章节进度和亮度条的空间关系，不作为设计依据。
