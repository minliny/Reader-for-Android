# 设置首页前端输入件

来源页面：`../UI设计图.png` 与 `../文字稿.md`

文件：
- `../../../frontend-input/component-library/library.css`：公共组件库样式。
- `../../../frontend-input/contracts.d.ts`：共享 TypeScript 输入契约。
- `components.css`：设置首页页面布局样式，统一使用 `st-` 前缀。
- `components.html`：可摘取的组件结构说明。
- `fixture.json`：页面数据结构和示例数据，供工程管线读取。
- `fixture.js`：浏览器直开时使用的 fixture 镜像。
- `render.js`：页面级渲染入口，暴露 `SettingsHomeInput.renderSettingsHome` 和 `SettingsHomeInput.renderSettingsHomeStateMatrix`。
- `preview.html`：fixture 驱动的默认预览。
- `state-matrix.html`：默认、概览加载、无备份、权限缺失状态矩阵。
- `COMPONENT_SPEC.md`：props、states、events 和验收标准。

组件拆分：
- `SettingsStatusBar`
- `SettingsTopBar`
- `LocalOverviewCard`
- `QuickEntryGrid`
- `QuickEntryCard`
- `SettingsSection`
- `SettingsListItem`
- `MainNav`

公共库复用：
- `rl-app-frame`
- `rl-status-bar`
- `rl-app-top-bar`
- `rl-icon`
- `rl-badge`
- `rl-bottom-nav`（公共 `MainNav` 样式）

前端接入：
- 使用 `fixture.json` 生成业务类型。
- 对照 `../../../frontend-input/contracts.d.ts` 实现 `SettingsHomeFixture`、`SettingsHomeState` 和 `SettingsHomeEvent`。
- 页面专属布局保持 `st-` 前缀，基础控件语义回流公共库。
