# 冻结顺序建议

| 冻结包 | 覆盖模块 | 当前成熟度 | 前置条件 | 是否可立即准备 |
| --- | ---- | ----- | ---- | ------- |
| `UI_FREEZE_READER_CONTROL_LAYER_V1` | 阅读沉浸、默认控制层、四主按钮快捷模块、顶部菜单、亮度/章节进度辅助 | 最接近冻结 | 完成 5 张仅调整相对高度的重画图并复审 | 否 |
| `UI_FREEZE_READER_APPEARANCE_V1` | 界面快捷区、字体、主题、主题编辑、版式、翻页动画 | 较成熟 | 确认 `21-1` 与 `35-39` 状态边界 | 可准备 |
| `UI_FREEZE_READER_TOC_BOOKMARK_V1` | 目录覆盖层、书签、搜索、完整目录 | 较成熟 | 补加载/空状态可选 | 可准备 |
| `UI_FREEZE_READER_TTS_V1` | 朗读快捷区、运行胶囊、朗读设置、错误态 | 部分成熟 | `18-3/19-1` 已完成高度对齐；补 TTS 错误态并确认朗读设置页是否纳入 | 否 |
| `UI_FREEZE_READER_SETTINGS_V1` | 阅读设置首页、显示、行为、辅助、预设 | 不完整 | 补完整页面设计图 | 否 |
| `UI_FREEZE_SOURCE_MANAGEMENT_V1` | 书源列表、详情、新增、编辑、检测、日志 | 缺失 | 补 spec 和全套图 | 否 |
| `UI_FREEZE_APP_SHELL_V1` | 主底栏、发现、RSS、设置、状态模板 | 部分成熟 | 底栏固定为 `书架 / 发现 / RSS / 设置`；补设置首页和全局状态模板 | 否 |
| `UI_FREEZE_BOOKSHELF_SEARCH_DETAIL_V1` | 书架、搜索、书籍详情及状态 | 部分成熟 | 补 loading/empty/error/确认弹窗 | 否 |

## 推荐顺序

1. `UI_FREEZE_READER_CONTROL_LAYER_V1`
2. `UI_FREEZE_READER_APPEARANCE_V1`
3. `UI_FREEZE_READER_TOC_BOOKMARK_V1`
4. `UI_FREEZE_READER_TTS_V1`
5. `UI_FREEZE_READER_SETTINGS_V1`
6. `UI_FREEZE_SOURCE_MANAGEMENT_V1`
7. `UI_FREEZE_APP_SHELL_V1`
8. `UI_FREEZE_BOOKSHELF_SEARCH_DETAIL_V1`
