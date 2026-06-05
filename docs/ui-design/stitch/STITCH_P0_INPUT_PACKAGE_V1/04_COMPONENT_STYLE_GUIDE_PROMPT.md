# 组件风格指南 Stitch 提示词

## 目标

要求 Stitch 从现有设计图中整理组件样例与视觉 token，作为后续页面生成的基础。

## Stitch 提示词

```text
请基于现有 docs/ui-design/drafts 设计图，为中文阅读 App 生成一份可编辑组件风格指南。

基础要求：
- 移动端阅读 App。
- 遵循现有 docs/ui-design/drafts 风格。
- 不重新发明视觉系统。
- 不改变 App Shell。
- 输出可编辑图层和组件样例。
- 如果无法精确提取某项 token，请标记为「需要人工测量」。

需要整理的组件样例：
1. 主底栏
2. 顶栏
3. 阅读控制层顶部栏
4. 阅读控制层底部栏
5. 章节进度条
6. 四主按钮
7. 书籍卡片
8. 搜索框
9. 列表行
10. 设置项 row
11. 开关 switch
12. 滑块 slider
13. 分段控件
14. 弹层 / bottom sheet
15. Toast / Snackbar
16. 确认弹窗
17. 空状态图标
18. 错误状态图标

需要输出的 token：
- 颜色 token
- 字体 token
- 间距 token
- 圆角 token
- 阴影 token
- 图标尺寸
- 栏高
- 面板高度

组件要求：
- 主底栏固定为：书架 / 发现 / RSS / 设置。
- 阅读控制层四主按钮固定为：目录 / 朗读 / 界面 / 设置。
- 主页面和阅读控制层是两套不同导航结构，不要混用。
- 主页面不要出现阅读亮度条和章节进度条。
- 阅读控制层不要出现 App 主底栏。

Token 提取要求：
- 对每个 token 给出推荐值。
- 若只能从图片推断，请标记「推断值」。
- 若无法精确提取，请标记「需要人工测量」。
- 不要虚构精确数值。

输出目标：
- 一页或多页组件样例。
- 一份 token 表。
- 一份需要人工测量的项目清单。
```

## 参考资料

- `docs/ui-design/drafts/01-bookshelf-cover.png`
- `docs/ui-design/drafts/02-bookshelf-list.png`
- `docs/ui-design/drafts/04-reader-controls.png`
- `docs/ui-design/drafts/16-5-reader-toc-quick-panel-height-aligned.png`
- `docs/ui-design/drafts/18-3-read-aloud-quick-panel-height-aligned.png`
- `docs/ui-design/drafts/21-1-reader-appearance-quick-panel.png`
- `docs/ui-design/drafts/22-2-reader-settings-quick-panel-height-aligned.png`
- `docs/ui-design/drafts/41-1-discovery-source-category.png`
- `docs/ui-design/drafts/42-rss-home.png`
- `docs/ui-design/drafts/43-settings-home.png`
- `docs/ui-design/STITCH_READINESS_IMAGE_ELEMENT_AUDIT.md`
