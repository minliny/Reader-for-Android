# Stitch Initial Draft v0 修正队列

> 更新日期：2026-06-12
>
> 输入包门禁：`STITCH_INITIAL_DRAFT_V0_INPUT_PACKAGE_READY`
>
> 当前阶段：`READY_FOR_FULL_VISUAL_AUDIT`

## 已关闭的 P0 输入 Blocker

| 原问题 | 关闭证据 | 状态 |
| --- | --- | --- |
| 只有 8/13 张截图 | 目录实测 13 个 PNG | `CLOSED` |
| 缺书籍详情、设置、发现、RSS、DARK 初稿 | 5 张已在同一项目重新生成并导出 | `CLOSED` |
| project ID 冲突 | 当前来源统一为 `9736408155228191581`；历史 ID 标记 `NOT_FOUND` | `CLOSED` |
| 原 8 张来源不可追溯 | 13 张均由明确 screen 重新导出，不沿用无来源文件 | `CLOSED` |
| manifest 缺 screen ID、导出时间、来源 | 13/13 已回填 | `CLOSED` |
| DARK 缺成对对照 | 冷蓝灰初稿与暖棕 token 修订稿均存在且 SHA 不同 | `CLOSED` |

## 当前 P0 门禁

| 范围 | 问题 | 关闭条件 | 阻塞 Stitch 修正 | 阻塞开发交付 |
| --- | --- | --- | --- | --- |
| 全部 13 screen | 尚未执行完整逐屏视觉审计 | 13 张完成 PASS / PARTIAL / FAIL 结论 | YES | YES |
| 完整审计结果 | 尚未形成批准的修正范围 | 审计 finding 转为可执行修正项并确认优先级 | YES | YES |
| 修正后验收 | 尚未完成修正、复验和设计冻结 | P0/P1 闭环并形成冻结基线 | NO | YES |

## 视觉审计候选

这些条目来自输入恢复阶段的快速像素检查，完整审计后再确认优先级。

| Screen | 候选问题 | 当前状态 |
| --- | --- | --- |
| 49/50 派生状态 | 顶部存在英文说明，不满足全中文约束 | `VERIFY_IN_FULL_AUDIT` |
| DARK token 修订稿 | 画布过长、缩放后正文偏小，与初稿可比性不足 | `VERIFY_IN_FULL_AUDIT` |
| 阅读 overlays | 搜索、自动翻页、目录/书签的 surface 与交互语言需统一 | `VERIFY_IN_FULL_AUDIT` |
| 全部 screen | 导出宽度不完全一致，需区分内容高度与 viewport 问题 | `VERIFY_IN_FULL_AUDIT` |
| DARK 对照 | 需核对对比度、四角信息、安全区和正文密度 | `VERIFY_IN_FULL_AUDIT` |

## 执行顺序

1. 以 `.stitch/designs/initial-draft-v0/manifest.json` 为唯一输入索引。
2. 执行 13 张逐屏完整视觉审计。
3. 将确认问题按 P0 / P1 / P2 更新到本队列。
4. 在项目 `9736408155228191581` 中执行 Stitch 修正，不跨项目混用。
5. 重新导出受影响 screen，更新 manifest、SHA-256 与导出时间。
6. 完成人工验收和设计冻结后，才允许开发交付。

## 门禁结论

- 是否解除输入包 BLOCKED：`YES`。
- 是否可以进入完整视觉审计：`YES`。
- 是否可以进入 Stitch 修正轮：`NO`。
- 是否可以进入开发交付：`NO`。
- 最终状态：`STITCH_INITIAL_DRAFT_V0_INPUT_PACKAGE_READY`。
