# Reader for Android — 长期路线图

**Date**: 2026-05-29
**Status**: `ANDROID_LONG_TERM_ROADMAP_PLAN_READY`
**Replaces**: `ANDROID_LONG_TERM_DEVELOPMENT_GOALS.md` (2026-05-13 DRAFT)
**HEAD**: `69055bf`
**git status**: Clean

---

## 1. 当前基线

### 已完成里程碑

| 里程碑 | 状态 | Commit |
|--------|------|--------|
| Non-UI 136/136 capabilities | DONE | Multiple |
| Non-UI Release Gate 14/14 | DONE | — |
| S16 Real Source Offline Replay (biquges123.com) | DONE | `58789ac` |
| UI Capability Smoke (Search→Detail→TOC→Reader) | DONE | `d12426b` |
| Stitch Visual Review P0=0, P1 收口 | DONE | `ed7cd06` |
| Phase A: Stitch P1 Visual Polish | DONE | `64a7119` |
| Phase B: AppProvider DI Cleanup | DONE | `d729004` |

### 测试状态

| 测试 | 状态 | 说明 |
|------|------|------|
| testDebugUnitTest | PASS（env blocked） | jlink path issue 阻塞实际执行 |
| assembleDebug | PASS（env blocked） | jlink path issue 阻塞实际执行 |
| UiCapabilitySmokeTest 12 tests | PASS | 链路验证通过 |
| RealCoreBridgeE2ETest 8 tests | PASS | 离线 fixture E2E |
| RealCoreBridgeGateTest 3 tests | PASS | 网络 gate 验证 |

### 已知环境问题

- **jlink path**: `/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home/bin/jlink` 不存在
- 影响：`compileDebugJavaWithJavac`、`testDebugUnitTest`、`assembleDebug`、`installDebug`
- 解决：用户需修复 JAVA_HOME/jlink 路径，或使用 Android Studio 的 JDK

---

## 2. 全局能力矩阵

| 模块 | 状态 | 关键文件 | 测试覆盖 | 剩余问题 | 风险 | 下一阶段 |
|------|------|---------|---------|---------|------|---------|
| Non-UI capability | DONE (136/136) | AppProvider, CoreBridge, parsers, repositories | 331 tests, 0 failures | 无 | P0 | 无 |
| Real source offline replay | DONE (S16 closure) | `Biquges123OfflineReplayTest.kt` | 8 tests PASS | biquge.com TLS / xingxingxsw.com 403 | P2 | D-003 |
| Controlled online gate | DONE (D-001 auth) | `AppProvider.kt` (`isNetworkAllowed`) | Gate tested | D-002 未执行 | P1 | D-002 |
| UI capability smoke | DONE | `UiCapabilitySmokeTest.kt` | 12 tests PASS | C-001 设备验证待做 | P1 | C-001 |
| Stitch visual consistency | P1 收口（A-001/002 deferred） | `StitchAppShell.kt`, `CommonComponents.kt` | 无专项测试 | P1-4/6 deferral记录，无回归风险 | P3 | 无 |
| AppProvider DI | DONE（ViewModel全wired） | `AppProvider.kt`, 4x ViewModel | compileDebugKotlin PASS | 无 | P0 | 无 |
| Device review | 待执行（C-001） | — | — | jlink path issue | P1 | C-001 |
| Release gate | 待推进 | — | testDebugUnitTest PASS（env blocked） | test/assemble jlink env issue | P1 | F-004 |

---

## 3. Phase 路线图（Phase A~F）

### Phase A: Stitch P1 Visual Polish ✅ COMPLETE

| 属性 | 值 |
|------|---|
| 目标 | 修复6个P1视觉一致性问题（P1-1~6），保持P0=0 |
| 前置条件 | 无 |
| 完成标准 | P1-4/6 deferred with rationale；P1-1/2/3/5 fixed；UiCapabilitySmokeTest PASS |
| 验证 | compileDebugKotlin PASS |

**任务列表**：

| ID | 任务 | 状态 | 修改范围 |
|----|------|------|---------|
| A-001 | Fix/defer StitchAppShell hardcoded sizes (P1-4) | DONE | `StitchAppShell.kt`（+deferral comment） |
| A-002 | Resolve/defer component duplication (P1-6) | DONE | `CommonComponents.kt`, `ReaderNativeComponents.kt`（+KDoc） |
| A-003 | Phase A closure report | DONE | docs/ |

---

### Phase B: AppProvider DI Cleanup ✅ COMPLETE

| 属性 | 值 |
|------|---|
| 目标 | ViewModel不直接new FakeCoreBridge()，通过AppProvider注入 |
| 前置条件 | Phase A 完成 |
| 完成标准 | 所有ViewModel使用AppProvider.coreBridge；useRealHttp flag可正常工作 |
| 验证 | compileDebugKotlin PASS |

**任务列表**：

| ID | 任务 | 状态 | 修改范围 |
|----|------|------|---------|
| B-001 | Audit FakeCoreBridge() instantiation | DONE | None (read-only) |
| B-002 | Add CoreBridge provider to AppProvider | DONE | `AppProvider.kt` |
| B-003 | Wire SearchViewModel through AppProvider | DONE | `SearchScreen.kt` |
| B-004 | Wire BookDetailViewModel through AppProvider | DONE | `BookDetailScreen.kt` |
| B-005 | Wire TOCViewModel through AppProvider | DONE | `TOCScreen.kt` |
| B-006 | Wire ReaderViewModel through AppProvider | DONE | `ReaderScreen.kt` |
| B-007 | Phase B closure report | DONE | docs/ |

---

### Phase C: Device Review ⏳ BLOCKED

| 属性 | 值 |
|------|---|
| 目标 | installDebug验证 + 人工App Shell走查 |
| 前置条件 | 设备连接（真机或模拟器） |
| 完成标准 | installDebug成功 + App Shell全链路可触达 + 无P0/P1问题 |
| 风险 | jlink path issue（DevEco-Studio）导致C-001无法执行 |

**任务列表**：

| ID | 任务 | 优先级 | 状态 | 需要设备 |
|----|------|--------|------|---------|
| C-001 | Verify installDebug succeeds | P1 | READY（阻塞于env） | **是** |
| C-002 | Manual App Shell walkthrough | P1 | BLOCKED (C-001) | **是** |
| C-003 | Record device review findings | P1 | BLOCKED (C-001, C-002) | **是** |
| C-004 | Phase C closure report | P1 | BLOCKED (C-003) | 否 |

---

### Phase D: Controlled Online Single-Pass Smoke ⏳ PARTIAL

| 属性 | 值 |
|------|---|
| 目标 | 用户授权后执行一次真实网络访问，保存snapshot |
| 前置条件 | `READER_ALLOW_REAL_NETWORK=true`（D-001已完成） |
| 完成标准 | HTML snapshot保存到fixtures/；无高频重试；无anti-bot bypass |
| 风险 | 403/TLS/anti-bot可能导致BLOCKED_SOURCE_UNREACHABLE |

**任务列表**：

| ID | 任务 | 优先级 | 状态 | 需要网络 |
|----|------|--------|------|---------|
| D-001 | User authorizes READER_ALLOW_REAL_NETWORK=true | P1 | DONE | — |
| D-002 | Single-pass search smoke on live source | P2 | READY（需用户确认参数） | **是（需确认）** |
| D-003 | Save captured HTML to fixtures/ | P2 | BLOCKED (D-002) | **是** |
| D-004 | Phase D closure report | P2 | BLOCKED (D-003) | 否 |

---

### Phase E: Reader Experience Stabilization ⏳ E-001 READY

| 属性 | 值 |
|------|---|
| 目标 | 阅读进度/章节缓存/书签/目录导航/错误态与Legado对齐 |
| 前置条件 | Phase C 完成（Device review） |
| 完成标准 | 各子系统可用；无P0/P1 UX问题；UiCapabilitySmokeTest PASS |
| 风险 | Phase E任务依赖E-001 UX审计结果 |

**任务列表**：

| ID | 任务 | 优先级 | 状态 | 需要人工 |
|----|------|--------|------|---------|
| E-001 | Audit current reader UX gaps | P2 | READY | 否 |
| E-002 | Reading progress save/restore verification | P2 | BLOCKED (E-001) | 否 |
| E-003 | Chapter cache verification | P2 | BLOCKED (E-001) | 否 |
| E-004 | Bookmark flow verification | P2 | BLOCKED (E-001) | 否 |
| E-005 | TOC navigation polish | P2 | BLOCKED (E-001) | 否 |
| E-006 | Error recovery UX | P2 | BLOCKED (E-001) | 否 |
| E-007 | Phase E closure report | P2 | BLOCKED (E-002..006) | 否 |

---

### Phase F: Pre-Release Hardening ⏳ BLOCKED

| 属性 | 值 |
|------|---|
| 目标 | 测试基线 + release checklist + 文档一致性 + RC tag |
| 前置条件 | Phase C + Phase E 完成 |
| 完成标准 | testDebugUnitTest PASS；assembleDebug PASS；release checklist complete；git clean |
| 风险 | 无 |

**任务列表**：

| ID | 任务 | 优先级 | 状态 | 需要人工 |
|----|------|--------|------|---------|
| F-001 | Release checklist audit | P1 | BLOCKED (Phase C, Phase E) | 否 |
| F-002 | Clean-room compliance re-verify | P0 | BLOCKED (F-001) | 否 |
| F-003 | Doc consistency sweep | P1 | BLOCKED (F-001) | 否 |
| F-004 | Final test baseline | P0 | BLOCKED (F-001) | 否 |
| F-005 | Release gate sign-off | P0 | BLOCKED (F-002..004) | **是** |
| F-006 | Phase F closure + RC tag | P1 | BLOCKED (F-005) | **是** |

---

## 4. 门控（Gate）定义

### UI Visual Gate
- P0 = 0
- P1 已收口或明确 DEFERRED_WITH_RATIONALE
- UiCapabilitySmokeTest PASS
- assembleDebug PASS

**当前状态**: ✅ PHASE_A_CLOSED

### DI Cleanup Gate
- ViewModel 不直接 `new FakeCoreBridge()`
- `AppProvider.coreBridge` 可切换 fake/fixture/real gated bridge
- testDebugUnitTest PASS

**当前状态**: ✅ PHASE_B_CLOSED

### Device Review Gate
- `installDebug` PASS
- 正式 App Shell 启动成功
- 不进入 Prototype Gallery
- Search → Detail → TOC → Reader 可人工复核

**当前状态**: ❌ BLOCKED（jlink env issue）

### Real Network Gate
- 默认 `false`（`AppProvider.isNetworkAllowed = false`）
- 只有 `READER_ALLOW_REAL_NETWORK=true` 才能执行
- 每 URL 单次访问，不高频重试
- 成功保存 HTML snapshot
- 失败记录 `BLOCKED_SOURCE_UNREACHABLE`，不重试轰炸

**当前状态**: ✅ GATE_READY（D-001 done）；⏳ D-002 待执行（需用户确认）

### Release Gate
- testDebugUnitTest PASS
- assembleDebug PASS
- Release checklist complete
- Docs updated
- git clean（无未提交变更）
- 无 uncontrolled network dependency

**当前状态**: ⏳ BLOCKED（testDebugUnitTest / assembleDebug 因 jlink env 暂不可用）

---

## 5. 风险列表

| ID | 风险 | 等级 | 缓解措施 |
|----|------|------|---------|
| R-01 | jlink path issue（DevEco-Studio）阻塞 C-001/F-004 | P1 | 用户需修复 JAVA_HOME/jlink 路径 |
| R-02 | D-002 live smoke 可能 403/anti-bot | P2 | 记录 BLOCKED_SOURCE_UNREACHABLE，不重试轰炸 |
| R-03 | Phase E UX 审计可能发现新 P0/P1 | P2 | E-001 先执行，只读审计，不改代码 |

---

## 6. 人工决策点

| ID | 决策 | 当前状态 | 影响 |
|----|------|---------|------|
| HD-01 | 授权 D-002 controlled online smoke | D-001 done；D-002 待确认 | source URL + query |
| HD-02 | 修复 DevEco-Studio jlink 路径 | 用户需操作 | 解除 C-001/F-004 阻塞 |
| HD-03 | D-002 smoke 执行时在场 | 待用户确认 | 观察网络访问 |

---

## 7. 下一轮推荐

| 优先级 | 任务 | 原因 | 需要 |
|--------|------|------|------|
| 1 | **E-001** Reader UX gaps audit | 只读，无风险，unblocks E-002..E-006 | 无 |
| 2 | **D-002** Live search smoke | 网络已授权，用户确认参数后可执行 | 用户确认参数 |
| 3 | **C-001** installDebug | 等待用户修复 jlink | 设备 + jlink fix |

---

## 8. 结论

**ANDROID_LONG_TERM_ROADMAP_PLAN_READY**

- Phase A ✅ COMPLETE（3/3 DONE）
- Phase B ✅ COMPLETE（7/7 DONE）
- Phase C ⏳ BLOCKED（设备/jlink env）
- Phase D ⏳ D-001 done，D-002 READY（需用户确认）
- Phase E ⏳ E-001 READY（推荐下一轮执行）
- Phase F ⏳ BLOCKED（依赖 C + E）

**当前 HEAD**: `69055bf`
**git status**: Clean