# Reader for Android Long-Term Roadmap

**Date**: 2026-05-29
**Status**: `ANDROID_LONG_TERM_ROADMAP_PLAN_READY`
**Replaces**: `ANDROID_LONG_TERM_DEVELOPMENT_GOALS.md` (2026-05-13 DRAFT)

---

## 1. Current Baseline

| Field | Value |
|-------|-------|
| HEAD | `ed7cd06` — `fix: polish stitch visual consistency` |
| Parent chain | `d12426b` (UI smoke) → `58789ac` (S16 fixture) → ... |
| git status | Clean |
| testDebugUnitTest | PASS |
| assembleDebug | PASS |
| Network access default | 0 (off) |
| Reader-Core modified | Never |
| Prototype Gallery as proof | No longer used |

### Completed Milestones

| Milestone | Status | Commit |
|-----------|--------|--------|
| Non-UI 136/136 capabilities | DONE | Multiple |
| Non-UI Release Gate 14/14 | DONE | — |
| S16 Real Source Offline Replay | DONE | `58789ac` |
| UI Capability Smoke | DONE | `d12426b` |
| Stitch Visual Review P0=0 | DONE | `ed7cd06` |

---

## 2. Global Capability Matrix

| Module | Status | Key Files | Test Coverage | Remaining Issues | Risk | Next Phase |
|--------|--------|-----------|---------------|------------------|------|------------|
| Non-UI capability | DONE (136/136) | AppProvider, CoreBridge, parsers, repositories | Full | None | Low | — |
| Real source offline replay | DONE | Biquges123OfflineReplayTest, fixtures | Full | None | Low | Controlled online smoke |
| Controlled online gate | IMPLEMENTED | RealCoreBridge.init{}, AppProvider.isNetworkAllowed | Gate tested | Needs user auth for live test | Low | Phase D |
| UI capability smoke | DONE | Search→Detail→TOC→Reader chain | UiCapabilitySmokeTest 12/12 | None | Low | — |
| Stitch visual consistency | P1 (2 of 6 deferred) | TOCScreen, StitchAppShell, ReaderRouteHost | Visual review passed | P1-4 sizes, P1-6 duplication | Low | Phase A |
| AppProvider DI | P1 debt | SearchViewModel, BookDetailViewModel, etc. | No DI tests | ViewModels new FakeCoreBridge() directly | Medium | Phase B |
| Device review | PENDING | All UI routes | N/A | No device connected | Low | Phase C |
| Reader UX stabilization | NOT STARTED | ReaderScreen, progress, cache, bookmarks | Partial (unit) | UX not validated on device | Medium | Phase E |
| S7+ advanced capabilities | NOT STARTED | WebView, JS, Explore, TTS, WebDAV | None | Needs user decisions on BD-009~011 | High | Deferred |

---

## 3. Phase Roadmap

### Phase A: Stitch P1 Visual Polish

**Goal**: Fix the 2 remaining P1 deferred visual issues (P1-4 StitchAppShell sizes, P1-6 component duplication), or formally defer with rationale.

| Field | Value |
|-------|-------|
| Depends on | Stitch Visual Review (ed7cd06) |
| Blocks | Device review (Phase C) |
| Max tasks | ~4 |
| Network required | No |
| Reader-Core changes | No |
| Loop-safe | Yes |

### Phase B: AppProvider DI Cleanup

**Goal**: ViewModels resolve CoreBridge through AppProvider, not direct `FakeCoreBridge()`. Keep fake/fixture/real paths switchable.

| Field | Value |
|-------|-------|
| Depends on | Phase A (no hard dep, but cleaner after polish) |
| Blocks | Release hardening (Phase F) |
| Max tasks | ~6 |
| Network required | No |
| Reader-Core changes | No |
| Loop-safe | Yes |

### Phase C: Device Review Preparation

**Goal**: `installDebug` on connected device, manual walkthrough of App Shell → Search → Detail → TOC → Reader.

| Field | Value |
|-------|-------|
| Depends on | Phase A + Phase B |
| Blocks | Release hardening (Phase F) |
| Max tasks | ~4 |
| Network required | No (offline fixture data) |
| Reader-Core changes | No |
| Loop-safe | No — BLOCKED_BY_DEVICE until device connected |

### Phase D: Controlled Online Single-Pass Smoke

**Goal**: One-shot live network test with explicit user authorization (`READER_ALLOW_REAL_NETWORK=true`). Capture snapshot, save fixture. One URL per visit. No retry storms.

| Field | Value |
|-------|-------|
| Depends on | User explicit authorization |
| Blocks | None (can be done independently) |
| Max tasks | ~3 |
| Network required | Yes — controlled single pass only |
| Reader-Core changes | No |
| Loop-safe | No — BLOCKED_BY_USER_NETWORK_AUTH |

### Phase E: Reader Experience Stabilization

**Goal**: Reading progress save/restore, chapter cache, bookmarks, TOC navigation polish, error recovery. Align with Legado reader UX baseline.

| Field | Value |
|-------|-------|
| Depends on | Phase B (DI for progress/cache repos) |
| Blocks | Release hardening (Phase F) |
| Max tasks | ~10 |
| Network required | No (local state + offline) |
| Reader-Core changes | No |
| Loop-safe | Yes |

### Phase F: Pre-Release Hardening

**Goal**: Final test baseline, release gate checklist, doc consistency, clean workspace, RC tag.

| Field | Value |
|-------|-------|
| Depends on | Phase C + Phase E |
| Blocks | S7+ advanced capabilities |
| Max tasks | ~6 |
| Network required | No |
| Reader-Core changes | No |
| Loop-safe | Yes |

### Deferred: S7+ Advanced Capabilities

Beyond Phase F, the original S7-S15 stages (WebView/JS, Explore/RSS, Local Books, TTS, WebDAV, Sync, Remote, RC) require user decisions on blockers BD-009 through BD-015. These are NOT in the immediate roadmap and will be planned separately.

---

## 4. Gate Definitions

### UI Visual Gate
- P0 = 0
- P1 fixed or explicitly deferred with rationale
- UiCapabilitySmokeTest PASS
- assembleDebug PASS

### DI Cleanup Gate
- No ViewModel directly instantiates `FakeCoreBridge()`
- AppProvider can switch between fake / fixture / real (gated) bridge
- testDebugUnitTest PASS

### Device Review Gate
- installDebug succeeds
- App Shell launches without crash
- Prototype Gallery NOT used as proof
- Search → Detail → TOC → Reader manually verified

### Real Network Gate
- Default: `AppProvider.isNetworkAllowed = false`
- Only enabled via `READER_ALLOW_REAL_NETWORK=true`
- One URL, one visit per smoke session
- Snapshot saved to fixtures/
- No retry, no high-frequency access
- Ordinary tests never access real network

### Release Gate
- testDebugUnitTest PASS
- assembleDebug PASS
- Release checklist complete
- Docs updated and consistent
- git clean
- No uncontrolled network dependency
- Clean-room compliance verified (no Legado source, no Core internals)

---

## 5. Risk Register

| ID | Risk | Severity | Mitigation |
|----|------|----------|------------|
| R-01 | Device unavailable for review | P1 | Phase A+B can proceed without device |
| R-02 | Controlled online source blocks again | P2 | Fall back to fixture; never retry-storm |
| R-03 | AppProvider DI breaks existing smoke tests | P1 | Incremental refactor, run smoke after each change |
| R-04 | DataStore needs Android Context in tests | P2 | FakeBookSourceRepository already works; deferred |
| R-05 | Clean-room boundary violation | P0 | Ongoing grep gates before each commit |

---

## 6. Human Decision Points

| ID | Decision | Phase | Default |
|----|----------|-------|---------|
| HD-01 | Authorize controlled online single-pass smoke | Phase D | Wait for explicit user command |
| HD-02 | Connect device for installDebug review | Phase C | Proceed with non-device phases |
| HD-03 | Choose StitchAppShell size strategy (map to tokens vs keep custom) | Phase A | Keep Stitch design spec sizes |
| HD-04 | Choose WebView/JS engine (BD-009) | S7 deferred | WebView-first, QuickJS deferred |
| HD-05 | Choose release channel (open source / Play Store) | Phase F | Deferred |

---

## 7. Next Recommended Task

**ANDROID-LT-A-001**: Fix StitchAppShell hardcoded sizes (P1-4)
- Or: Formally defer with design rationale
- No network, no Core changes, loop-safe
