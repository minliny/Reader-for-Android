# Reader for Android Long-Term Task Queue

**Date**: 2026-05-29
**Status**: `ANDROID_LONG_TERM_ROADMAP_PLAN_READY`
**Depends on**: `ANDROID_LONG_TERM_ROADMAP.md`

---

## Task Queue

### Phase A: Stitch P1 Visual Polish

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-A-001 | Fix or defer StitchAppShell hardcoded sizes (P1-4) | P1 | DONE | StitchAppShell.kt | Reader-Core, network, CoreBridge | compileDebugKotlin | No (unless design change) |
| ANDROID-LT-A-002 | Resolve or defer component duplication (P1-6) | P2 | DONE | ReaderNativeComponents.kt, CommonComponents.kt | Reader-Core, network, CoreBridge | testDebugUnitTest | No |
| ANDROID-LT-A-003 | Phase A closure report | P1 | DONE | docs/ | Code | doc exists | No |

### Phase B: AppProvider DI Cleanup

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-B-001 | Audit all ViewModel FakeCoreBridge() instantiation points | P1 | DONE | None (read-only) | UI visuals | grep report | No |
| ANDROID-LT-B-002 | Add CoreBridge provider to AppProvider | P1 | DONE | AppProvider.kt | Reader-Core, network | compileDebugKotlin | No |
| ANDROID-LT-B-003 | Wire SearchViewModel through AppProvider | P1 | DONE | SearchScreen.kt, AppProvider.kt | Reader-Core, network, UI visuals | UiCapabilitySmokeTest | No |
| ANDROID-LT-B-004 | Wire BookDetailViewModel through AppProvider | P1 | DONE | BookDetailScreen.kt, AppProvider.kt | Reader-Core, network, UI visuals | UiCapabilitySmokeTest | No |
| ANDROID-LT-B-005 | Wire TOCViewModel through AppProvider | P1 | DONE | TOCScreen.kt, AppProvider.kt | Reader-Core, network, UI visuals | UiCapabilitySmokeTest | No |
| ANDROID-LT-B-006 | Wire ReaderViewModel through AppProvider | P1 | DONE | ReaderScreen.kt, AppProvider.kt | Reader-Core, network, UI visuals | UiCapabilitySmokeTest | No |
| ANDROID-LT-B-007 | Phase B closure report | P1 | DONE | docs/ | Code | doc exists | No |

### Phase C: Device Review

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-C-001 | Verify installDebug succeeds | P1 | READY | None | Reader-Core, network | installDebug output | Yes (device) |
| ANDROID-LT-C-002 | Manual App Shell walkthrough (Bookshelf→Search→Detail→TOC→Reader→Error) | P1 | BLOCKED (C-001) | None | Code changes, network, Prototype Gallery | Manual checklist | Yes (device) |
| ANDROID-LT-C-003 | Record device review findings | P1 | BLOCKED (C-001, C-002) | docs/ | Code | doc exists | Yes (review findings) |
| ANDROID-LT-C-004 | Phase C closure report | P1 | BLOCKED (C-003) | docs/ | Code | doc exists | No |

### Phase D: Controlled Online Single-Pass Smoke

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-D-001 | User authorizes READER_ALLOW_REAL_NETWORK=true | P1 | DONE | None | All code, all network | User authorized (2026-05-29) | Yes (auth) |
| ANDROID-LT-D-002 | Single-pass search smoke on live source | P2 | READY | None (fixture capture only) | High-frequency, retry, bypass anti-bot | Snapshot saved | Yes (network) |
| ANDROID-LT-D-003 | Save captured HTML to fixtures/ | P2 | BLOCKED (D-002) | fixtures/ | Parser changes | FixtureCompletenessValidatorTest | No |
| ANDROID-LT-D-004 | Phase D closure report | P2 | BLOCKED (D-003) | docs/ | Code | doc exists | No |

### Phase E: Reader Experience Stabilization

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-E-001 | Audit current reader UX gaps | P2 | DONE | None (read-only) | Code | gap list doc | No |
| ANDROID-LT-E-002 | Reading progress save/restore verification | P2 | DONE | ReaderScreen, progress adapters | Reader-Core, network | ReaderProgressLocalStateAdapter tests | No |
| ANDROID-LT-E-003 | Chapter cache verification | P2 | DONE | Cache adapters | Reader-Core, network | ReaderCacheLocalStateAdapter tests | No |
| ANDROID-LT-E-004 | Bookmark flow verification | P2 | DONE | Bookmark adapters | Reader-Core, network | ReaderBookmarkActionAdapter tests | No |
| ANDROID-LT-E-005 | TOC navigation polish | P2 | READY | TOCScreen, ReaderTocLocalStateJoiner | Reader-Core, network, UI visual redesign | UiCapabilitySmokeTest | No |
| ANDROID-LT-E-006 | Error recovery UX | P2 | READY | Error mappers, error states | Reader-Core, network | ReaderErrorModelTest | No |
| ANDROID-LT-E-007 | Phase E closure report | P2 | BLOCKED (E-002..006) | docs/ | Code | doc exists | No |

### Phase F: Pre-Release Hardening

| ID | Title | Priority | Status | Modifies | Forbidden | Verification | Human Confirm? |
|----|-------|----------|--------|----------|-----------|-------------|----------------|
| ANDROID-LT-F-001 | Release checklist audit | P1 | BLOCKED (Phase C, Phase E) | None (read-only) | Code | checklist doc | No |
| ANDROID-LT-F-002 | Clean-room compliance re-verify | P0 | BLOCKED (F-001) | None (grep audit) | Code | grep report | No |
| ANDROID-LT-F-003 | Doc consistency sweep | P1 | BLOCKED (F-001) | docs/ | Code | all docs updated | No |
| ANDROID-LT-F-004 | Final test baseline | P0 | BLOCKED (F-001) | None (test run) | Code | testDebugUnitTest + assembleDebug | No |
| ANDROID-LT-F-005 | Release gate sign-off | P0 | BLOCKED (F-002..004) | docs/ | Code | gate checklist complete | Yes (sign-off) |
| ANDROID-LT-F-006 | Phase F closure + RC tag | P1 | BLOCKED (F-005) | None (tag only) | Push without user request | git tag | Yes (tag) |

---

## Status Enum Definitions

| Status | Meaning |
|--------|---------|
| READY | No blockers, can execute immediately |
| BLOCKED | Blocked by another task (specified in dependencies) |
| BLOCKED_BY_DEVICE | Requires physical device or emulator connection |
| BLOCKED_BY_USER_NETWORK_AUTH | Requires explicit user `READER_ALLOW_REAL_NETWORK=true` |
| BLOCKED_BY_DESIGN_DECISION | Requires user design decision |
| IN_PROGRESS | Currently executing |
| DONE | Completed and verified |
| SKIPPED_NOT_NEEDED | Explicitly decided not needed |
| NEEDS_HUMAN_REVIEW | Completed but awaiting human verification |

---

## Current Ready Tasks

Phase A ✅ COMPLETE（3/3 DONE）
Phase B ✅ COMPLETE（7/7 DONE）
Phase E: E-001 DONE，E-002..E-006 READY

当前可执行任务：

| ID | Phase | Title | Needs Device? | Needs Network? |
|----|-------|-------|---------------|----------------|
| ANDROID-LT-E-002 | E | Reading progress save/restore verification | No | No |
| ANDROID-LT-E-003 | E | Chapter cache verification | No | No |
| ANDROID-LT-E-004 | E | Bookmark flow verification | No | No |
| ANDROID-LT-E-005 | E | TOC navigation polish | No | No |
| ANDROID-LT-E-006 | E | Error recovery UX | No | No |
| ANDROID-LT-C-001 | C | Verify installDebug succeeds | **Yes** | No |
| ANDROID-LT-D-002 | D | Single-pass search smoke on live source | No | **Yes** |

**Recommended next**: E-002（Reading progress save/restore verification）— 只读测试验证，无风险

**阻塞项**：
- C-001：jlink env issue（DevEco-Studio路径问题）
- D-002：需要用户确认 smoke 参数（source URL + query）
