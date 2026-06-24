# Loop Report: ANDROID-LT-E-001

**Date**: 2026-05-29
**Loop ID**: E-001
**HEAD**: `03dba8b`

---

## Task

Audit current reader UX gaps — `ANDROID-LT-E-001`

**性质**：只读审计，不改代码，不访问网络，不修改 Reader-Core。

---

## Subsystem Audit Results

### 1. 阅读进度 (Reading Progress) — PARTIALLY AVAILABLE

| 文件 | 状态 |
|------|------|
| `ReaderProgressSaveAdapter.kt` | TODO 未连接 Room DAO，仅内存状态 |
| `ReaderProgressLocalStateAdapter.kt` | 纯内存，进程重启后丢失 |
| `ReaderRuntimeState.kt` | UI 模型完整 |
| `ReadingProgressDao` + `@Entity` | Room 层完整但未连接 |

**Legado 差距**：Legado 每次翻页/滚动停止时立即持久化到 SQLite，进程重启后恢复精确位置。本 app 进度完全丢失。

**P0 问题**：`ReaderProgressSaveAdapter` 从不调用 `ReadingProgressDao.upsert()`，进度在进程死亡或重启后丢失。

---

### 2. 章节缓存 (Chapter Cache) — PARTIALLY AVAILABLE

| 文件 | 状态 |
|------|------|
| `ReaderCacheSaveAdapter.kt` | TODO 仅内存 `MutableMap`，未连接 Room |
| `ReaderCacheLocalStateAdapter.kt` | 内存缓存状态，未连接 DAO |
| `CachedChapterDao` + `@Entity` | Room 层完整但未连接 |

**Legado 差距**：Legado 缓存每次成功的章节内容到 Room，再次打开时先检查缓存。本 app 每次都重新从网络获取。

**P0 问题**：`ReaderCacheSaveAdapter.write()` 只写内存，重启后清零；`read()` 从不查 `ChapterCacheManager.get()`。

---

### 3. 书签 (Bookmark) — PARTIALLY AVAILABLE

| 文件 | 状态 |
|------|------|
| `ReaderBookmarkActionAdapter.kt` | TODO 仅内存 Map，未连接 Room |
| `ReaderBookmarkLocalStateAdapter.kt` | 内存书签状态 |
| `BookmarkDao` + `@Entity` | Room 层完整但未连接 |

**Legado 差距**：Legado 书签立即持久化到 Room，支持多书签、段落位置、备注。本 app 重启后全部丢失。

**P0 问题**：`add/remove/toggle` 写内存 Map，重启丢失。
**P1 问题**：段落级书签位置和笔记字段未实现。

---

### 4. 目录导航 (TOC Navigation) — PARTIALLY AVAILABLE

| 文件 | 状态 |
|------|------|
| `ReaderDirectoryAdapterShell.kt` | 默认 FAKE 模式；REAL 模式引用了不存在的类 |
| `ReaderTocLocalStateJoiner.kt` | 使用内存进度数据（非 Room） |
| `CoreTocToReaderTocMapper.kt` | 树→平面映射，完整 |

**Legado 差距**：Legado 懒加载目录组，可折叠树，显示当前章/书签/进度。本 app 使用 fixture 假数据。

**P0 问题**：`ReaderTocFacadeErrorMapper`（代码中引用）不存在 — REAL 模式会 `ClassNotFoundException`。
**P0 问题**：`ReaderLocalStateProvider`（代码中引用）不存在 — REAL 模式会 `ClassNotFoundException`。
**P1 问题**：`enableRealMode()` 从未被调用，目录始终使用假数据。

---

### 5. 内容展示 (Content Display) — AVAILABLE (UI) / PARTIALLY AVAILABLE (load)

| 文件 | 状态 |
|------|------|
| `ReaderContentStateMapper.kt` | 内容→UI 映射，完整 |
| `ContentAdapterShell.kt` | 默认 FAKE 模式；REAL 模式引用了不存在的类 |
| `ReaderControlBase.kt` | UI 完整（顶栏/底栏/浮层/进度条） |

**Legado 差距**：Legado 支持字体/大小/行高/背景色（纸/羊皮/暗）、TTS（语音/速度）、自动滚动、regex 替换。本 app UI 静态，无 TTS/自动滚动/替换引擎。

**P0 问题**：`ContentFacadeResultMapper`（代码中引用）不存在 — REAL 内容模式会 `ClassNotFoundException`。
**P0 问题**：`ContentAdapterShell.enableRealMode()` 从未被调用，始终使用假内容。
**P1 问题**：`ReaderTtsState` 和 `ReaderAutoScrollState` 模型存在但零实现。

---

### 6. 错误恢复 (Error Recovery) — PARTIALLY AVAILABLE

| 文件 | 状态 |
|------|------|
| `ReaderErrorModelTest.kt` | 测试完整，验证错误模型正确 |
| `ContentFacadeErrorMapper.kt` | 处理 4/9 错误码，5 个降级到"内容不可用" |
| `SearchFacadeErrorMapper.kt` | 处理 7/7 错误码（完整） |

**Legado 差距**：Legado 自动换源（NETWORK/PARSE 失败时尝试下一个）、指数退避重试（3 次，2s/4s/8s）、详细分阶段错误消息。本 app 显示通用错误，无自动恢复。

**P1 问题**：`ContentAdapterShell` 和 `ReaderDirectoryAdapterShell` 捕获所有异常并包装为 `UNKNOWN` — 富类型的 `ReaderError` 分类未被利用。
**P1 问题**：无重试机制，失败需用户手动刷新。
**P2 问题**：无换源路径。

---

## P0/P1 问题汇总

### P0（阻断性）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| P0-1 | `ReaderProgressSaveAdapter` 从不持久化进度到 Room | `ReaderProgressSaveAdapter.kt` | 进度丢失 |
| P0-2 | `ReaderCacheSaveAdapter` 从不写 Room | `ReaderCacheSaveAdapter.kt` | 缓存不生效 |
| P0-3 | `ReaderBookmarkActionAdapter` 从不持久化书签到 Room | `ReaderBookmarkActionAdapter.kt` | 书签丢失 |
| P0-4 | `ContentFacadeResultMapper` 不存在 | `ContentAdapterShell.kt` | REAL 内容模式崩溃 |
| P0-5 | `ReaderTocFacadeErrorMapper` 不存在 | `ReaderDirectoryAdapterShell.kt` | REAL TOC 模式崩溃 |
| P0-6 | `ReaderLocalStateProvider` 不存在 | `ReaderDirectoryAdapterShell.kt` | REAL 模式崩溃 |
| P0-7 | `ContentAdapterShell` 始终 FAKE 模式 | `ContentAdapterShell.kt` | 真实内容加载不可用 |
| P0-8 | `ReaderDirectoryAdapterShell` 始终 FAKE 模式 | `ReaderDirectoryAdapterShell.kt` | 真实目录加载不可用 |

### P1（重要）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| P1-1 | `ContentFacadeErrorMapper` 5 个错误码未处理 | `ContentFacadeErrorMapper.kt` | 错误消息不可操作 |
| P1-2 | 无重试机制 | 所有 adapter | 失败需手动刷新 |
| P1-3 | 无换源/故障转移 | 所有 adapter | 源失败=不可读 |
| P1-4 | TTS 零实现（模型存在） | `ReaderTtsState` | 无语音朗读 |
| P1-5 | 自动滚动零实现（模型存在） | `ReaderAutoScrollState` | 无自动滚动 |
| P1-6 | 替换规则引擎零实现 | `ReaderReplaceRuleUiModel` | 无内容正则替换 |
| P1-7 | 书签无段落位置/笔记 | `BookmarkEntity.kt` | 功能不完整 |
| P1-8 | `enableRealMode()` 从未被调用 | `ContentAdapterShell`, `ReaderDirectoryAdapterShell` | 始终假数据 |

---

## 总体评估

所有 6 个子系统均处于"部分可用"状态。UI 层实现完整，但所有 adapter 业务层均使用内存假数据，未连接 Room 持久层。核心功能（真实内容加载、真实目录）需要多个缺失的映射器才能运行。

**所有 TODO 均标注"connect to Room DAO when DI/runtime is available"** — Phase B 刚完成 DI 清理（AppProvider.coreBridge），但 adapter 层尚未接入 Room DAO。

**建议**：E-002..E-006 的验证任务应先聚焦于连接 Room（progress/cache/bookmark adapter → Room），然后再处理 UI/UX polish。

---

## 验证

N/A — 只读审计，无代码修改。

## Commit

N/A — 无代码变更。

---

## 下一步

**E-002**（Reading progress save/restore verification）：验证 `ReaderProgressLocalStateAdapter` 是否可正确保存/恢复进度。

**注意**：E-002..E-006 的验证任务依赖 E-001 的审计结果。目前确认：
- Room DAO 层完整
- Adapter 层全部只使用内存，未连接 Room
- 多个缺失的映射器导致 REAL 模式会崩溃

建议先处理 P0 问题（连接 Room；实现缺失的映射器），再进行 E-002..E-006 验证。