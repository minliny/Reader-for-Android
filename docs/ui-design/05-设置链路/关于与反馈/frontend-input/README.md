# 关于与反馈 frontend-input

## 来源

- 设计图：`../UI设计图.png`
- 文字稿：`../文字稿.md`

## 目标

展示版本、更新、反馈、开源许可和隐私协议入口。

## 复用公共组件

- `SettingGroupCard`
- `SettingRow`
- `PrimaryActionButton`
- `ErrorState`

## 新增公共组件

- `VersionCard`
- `LinkRow`
- `FeedbackEntry`
- `UpdateButton`

## 状态覆盖

- `default`
- `loading`
- `error`
- `confirm`
- `offline`

## 禁止项

- 不显示主底部导航。
- 不新增账号、社区、会员入口。
- 离线时只阻断联网动作。
