# 全局状态模板 Stitch 提示词

## 目标

生成通用状态模板，供书架、搜索、详情、书源管理、设置等模块复用。

## Stitch 提示词

```text
请生成中文阅读 App 的通用状态模板组件/页面组。

基础要求：
- 画布尺寸：390 × 884。
- 移动端竖屏。
- 中文阅读 App。
- 适用于书架、搜索、详情、书源管理、设置等模块。
- 保持现有 docs/ui-design/drafts 风格：暖色纸张背景、柔和卡片、轻阴影、紧凑文案、蓝色强调色。
- 状态模板不要绑定单一页面。
- 保持统一插画/图标/文案/按钮风格。
- 输出可编辑图层，方便进入 Figma。

需要生成的状态：
1. loading
2. empty
3. error
4. network_error
5. delete_confirm
6. operation_success
7. operation_failed
8. permission_required

模板 1：loading
- 用于页面首次加载、列表加载、详情加载。
- 保留顶部栏占位。
- 主体使用骨架屏，避免全屏大 spinner。
- 骨架行应适用于列表、卡片、设置项。

模板 2：empty
- 用于无书籍、无书源、无搜索结果、无书签、无订阅源。
- 包含空状态图标、标题、说明、主操作按钮。
- 文案示例：暂无内容 / 还没有添加书源 / 没有找到结果。
- 不要绑定某个单独页面。

模板 3：error
- 用于普通加载失败、解析失败。
- 包含错误图标、短标题、错误说明、重试按钮。
- 可提供次入口：查看日志。

模板 4：network_error
- 用于网络不可用、请求超时。
- 包含网络图标、说明、重试、检查网络。

模板 5：delete_confirm
- 用于删除书源、删除规则、移除书籍、清空缓存、恢复默认前确认。
- 使用对话框或底表样式。
- 包含标题、对象名称、风险说明、取消、确认删除。
- 确认按钮使用克制警示色。

模板 6：operation_success
- 用于保存成功、复制成功、刷新成功、加入书架成功。
- 使用 Toast / Snackbar。
- 包含成功图标、短文案。

模板 7：operation_failed
- 用于保存失败、复制失败、刷新失败。
- 使用 Toast / Snackbar 或轻量错误条。
- 包含失败原因和可选重试入口。

模板 8：permission_required
- 用于存储、通知、TTS 或系统权限缺失。
- 包含权限说明、授权按钮、稍后按钮。

视觉规则：
- 图标/插画必须统一风格。
- 文案短句化。
- 页面级状态保留标题栏和必要返回。
- 列表级状态不破坏页面结构。
- 按钮风格与现有蓝色强调按钮一致。
- 不出现账户、登录、头像。

输出目标：
- 输出一组可复用状态模板。
- 如 Stitch 支持组件页，请整理为组件样例而非绑定单一业务页面。
```

## 参考资料

- `docs/ui-design/planning/UI_DESIGN_COMPLETION_PLAN_V1/05_GLOBAL_STATE_TEMPLATES.md`
- `docs/ui-design/STITCH_READINESS_IMAGE_ELEMENT_AUDIT.md`
- `docs/ui-design/drafts/`
