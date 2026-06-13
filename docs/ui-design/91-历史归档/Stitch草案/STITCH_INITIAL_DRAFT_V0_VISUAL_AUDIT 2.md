# Stitch Initial Draft v0 视觉审计

> 复核日期：2026-06-12
>
> 输入包状态：`STITCH_INITIAL_DRAFT_V0_INPUT_PACKAGE_READY`
>
> 视觉审计状态：`READY_FOR_FULL_VISUAL_AUDIT`
>
> 边界：本轮完成输入包恢复与证据校验，尚未完成逐屏 PASS / PARTIAL / FAIL 视觉审计。

## 1. Git 前置检查

- 当前分支：`main`。
- `git status --short`：48 个 tracked 变更、65 个 untracked 项，共 113 项。
- 本轮未清理、未 stash、未提交，也未处理无关工作区变更。

## 2. 来源项目结论

- 唯一当前来源项目：`9736408155228191581`。
- 历史项目 `14245818599411324705`：Stitch API 返回 `NOT_FOUND`，仅保留为历史记录。
- 13 个目标 screen 均在项目 `9736408155228191581` 内重新生成并可按 screen ID 读取。
- 原有 8 张本地 PNG 无法恢复原始 screen 映射，因此未作为最终证据沿用；13 张文件均由本轮明确 screen 的 `screenshot.downloadUrl` 重新导出。
- 未跨项目混用截图。

## 3. 13 张目标截图

| # | Screen | 文件 | Screen ID | 状态 |
| --- | --- | --- | --- | --- |
| 1 | 书架 | `bookshelf.png` | `a2b252a266144be19cd1b202d0f9c4e3` | 存在、可追溯 |
| 2 | 书籍详情 | `book-detail.png` | `e31438e72ad1411dbfe15b2f80dc8db4` | 存在、可追溯 |
| 3 | 阅读沉浸页 | `reader-light.png` | `2247a05deed74a81ba6d78f30b2064ba` | 存在、可追溯 |
| 4 | 搜索弹层 | `reader-search-overlay.png` | `a2ac8f4877114477a31fdd018989707a` | 存在、可追溯 |
| 5 | 自动翻页弹层 | `reader-auto-page-overlay.png` | `6851fe604a2a4aee88122f9b2d6103da` | 存在、可追溯 |
| 6 | 目录/书签弹层 | `reader-toc-bookmark-overlay.png` | `04cfe89ab7d54c4cae891f7f6cef3d61` | 存在、可追溯 |
| 7 | 书源管理 | `source-management.png` | `7d361bb7d9454516ad8fee6c4ce93ee2` | 存在、可追溯 |
| 8 | 设置 | `settings.png` | `59326435f2a24c308033cfd75b11c1c1` | 存在、可追溯 |
| 9 | 发现 | `discovery.png` | `b64b1b3bc3804fec8a6247a947625d1d` | 存在、可追溯 |
| 10 | RSS | `rss.png` | `66eadd62c01a433ebf993c0f22f5d55d` | 存在、可追溯 |
| 11 | 49/50 派生状态 | `derived-states.png` | `602d3bf4d7b34e2ba0ae023a101a7ddb` | 存在、可追溯 |
| 12 | DARK 初稿 | `reader-dark-initial.png` | `0876ce1490344fec8896a4d5ff074961` | 存在、可追溯 |
| 13 | DARK token 修订稿 | `reader-dark-token-v0.png` | `f23b716c16154ba68c42f2e601709e8b` | 存在、可追溯 |

汇总：存在 13、缺失 0、待确认 0。

## 4. Manifest 完整性

文件：`.stitch/designs/initial-draft-v0/manifest.json`

结论：`MANIFEST_COMPLETE_FOR_VISUAL_AUDIT`。

- `projectId`：13/13，全部为 `9736408155228191581`。
- `screenId`：13/13。
- `sourceFile`：13/13，且目录中实际存在。
- `sha256`：13/13。
- `exportedAt`：13/13。
- `stitchUrl` 或明确来源说明：13/13；本包使用 API screen resource、screenshot file resource 和下载方式作为来源说明。
- `auditStatus`：13/13 为 `READY_FOR_VISUAL_AUDIT`。

## 5. 文件与去重校验

- PNG 数量：`13 / 13`。
- 可读取 PNG：`13 / 13`。
- SHA-256 数量：`13 / 13`。
- 唯一 SHA-256：`13 / 13`。
- 字节级重复：`0`。
- DARK 初稿与 token 修订稿 SHA-256 不同，像素快速检查也确认冷蓝灰与暖棕方向不同。

## 6. 恢复方式

- 是否恢复原 screen：`NO`。现有无来源 PNG 与远端历史 screen 无法建立可靠一一映射。
- 是否重新生成：`YES`。在唯一项目 `9736408155228191581` 中重新生成 13 个明确命名的 screen，并逐屏导出。

## 7. 输入包门禁

- 是否解除输入包 BLOCKED：`YES`。
- 是否可以进入完整视觉审计：`YES`。
- 是否可以进入 Stitch 修正轮：`NO`，需先完成完整视觉审计并形成确认后的修正清单。
- 是否可以进入开发交付：`NO`，尚未完成逐屏验收、修正闭环与设计冻结。

## 8. 完整视觉审计预检候选

以下仅是输入恢复时的快速像素观察，不代替完整审计：

1. `derived-states.png` 含英文说明文字，需核对“所有用户可见界面文字使用中文”的约束。
2. `reader-dark-token-v0.png` 原始画布高度明显大于常规移动 screen，缩放后正文偏小，需检查 viewport、字号和可比性。
3. 13 张导出图宽度不完全一致，需在审计中区分 Stitch 内容高度导致的缩略图尺寸与真实响应式问题。
4. 搜索、自动翻页、目录/书签三个 overlay 需统一 surface、圆角、间距和关闭方式。
5. DARK 初稿与 token 修订稿需按相同阅读场景检查色温、正文对比度、四角信息和安全区。

## 9. 下一步

以当前 manifest 为唯一输入索引，执行 13 张逐屏完整视觉审计；将每张标记为 PASS / PARTIAL / FAIL，并据此更新修正队列。完成修正、复验和设计冻结前，不进入开发交付。

## 10. 最终状态

`STITCH_INITIAL_DRAFT_V0_INPUT_PACKAGE_READY`
