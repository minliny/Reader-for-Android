# 关于与反馈组件规格

## Global

- 全局对象：`window.AboutFeedbackInput`
- 命名空间：`af-` 页面标识，通用结构使用 `sk-`
- 默认画布：`836 x 1881`

## Public Components

### VersionCard

- Props：`version`、`status`
- Acceptance：必须展示 `当前版本` 和更新状态。

### LinkRow

- Props：`title`、`meta`
- Acceptance：协议、许可和帮助链接必须只作为行入口，不新增其他主流程。

### FeedbackEntry

- Props：`title`、`meta`
- Acceptance：必须明确是问题或建议反馈。

### UpdateButton

- Props：`label`、`status`
- Acceptance：检查更新失败或离线时必须保留当前版本信息。

## Acceptance

- 必须覆盖 `当前版本`、`检查更新`、`问题反馈`、`开源许可`、`隐私协议`。
- 状态矩阵必须覆盖 default、loading、error、offline。
