# 前端设计稿输入规格

目标：把 UI 设计图还原结果交付为前端可消费的设计稿输入件，而不只是一张静态截图。

## 当前输入包

- `02-主标签页/书架/frontend-input/`：书架封面模式。
- `02-主标签页/设置/frontend-input/`：设置首页。
- `03-书架链路/书架空状态/frontend-input/`：书架空状态。
- `04-阅读链路/阅读控制层/frontend-input/`：阅读控制层。
- `frontend-input/tokens.css`：跨页面共享设计 token。
- `frontend-input/contracts.d.ts`：TypeScript 数据、状态和事件契约。
- `frontend-input/manifest.json`：可验证页面、视口、截图和验收指标清单。
- `frontend-input/validate-frontend-inputs.js`：本地渲染复验脚本。
- `frontend-input/component-library/`：公共组件库输入包和转换优先级。

## 交付标准

- 静态视觉预览：`preview.html` 可直接用浏览器打开。
- 数据输入契约：`fixture.json` 描述组件字段，`fixture.js` 提供 file 协议可运行镜像。
- 前端渲染入口：`render.js` 暴露页面级渲染函数和状态矩阵渲染函数。
- 状态矩阵：`state-matrix.html` 覆盖默认态、空态、加载态或模块展开态。
- 样式隔离：书架使用 `bs-` 前缀，阅读控制层使用 `rc-` 前缀。
- 公共组件：新页面优先使用 `component-library` 的 `rl-` 语义，再补页面专属结构。
- 视觉验收：渲染页应保持目标手机画布尺寸、核心文本存在、图片资源无缺失、请求失败为 0。

## 工程接入建议

前端实现时可把 `fixture.json` 转成 TypeScript interface，把 `render.js` 的 DOM 模板拆成 React/Vue/Compose 组件。`components.html` 保留为低成本拷贝片段，`preview.html` 和 `state-matrix.html` 用于验收走查。

## 复验命令

仓库不内置前端依赖。若当前 Node 环境已安装 Playwright，可执行：

```sh
node docs/ui-design/frontend-input/validate-frontend-inputs.js
```

在 Codex 桌面内置 runtime 下，可使用：

```sh
NODE_PATH=/Users/minliny/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules /Users/minliny/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node docs/ui-design/frontend-input/validate-frontend-inputs.js
```
