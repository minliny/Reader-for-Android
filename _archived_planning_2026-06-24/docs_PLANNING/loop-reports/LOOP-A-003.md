# Loop Report: ANDROID-LT-A-003

**Date**: 2026-05-29
**Loop ID**: A-003
**HEAD**: `076860a`

---

## Task

Phase A closure report — `ANDROID-LT-A-003`

---

## Phase A Completion

All Phase A tasks are DONE. This report closes the phase.

## Phase A Summary

| Task | Title | Status | Action |
|------|-------|--------|--------|
| ANDROID-LT-A-001 | StitchAppShell hardcoded sizes (P1-4) | DONE | Deferred with rationale — comment added to StitchAppShell.kt |
| ANDROID-LT-A-002 | Component duplication (P1-6) | DONE | Deferred with rationale — KDoc cross-references added to both component files |

## P1-4: StitchAppShell hardcoded font sizes

**Deferral rationale**: Font sizes (24sp/18sp/14sp/12sp/10sp) are Stitch design spec values, not bugs. Mapping to ReaderTheme.typography tokens would cause visual changes requiring design re-approval.

**Code evidence**: Comment added to `StitchAppShell.kt`:
```
// Note: font sizes (24sp/18sp/14sp/12sp/10sp) are Stitch design spec values,
// intentionally not mapped to ReaderTheme.typography tokens.
```

**Visual review doc** (`STITCH_VISUAL_REVIEW_AFTER_CAPABILITY_SMOKE.md`): P1-4 already marked DEFERRED — now matches code.

## P1-6: Component duplication (ReaderIconButton, ReaderChip)

**Deferral rationale**: Two variants serve distinct purposes — general UI (CommonComponents) vs reader control layer (ReaderNativeComponents). Different rendering (Material circle vs soft touch, solid vs alpha-selected background) is deliberate design boundary.

**Code evidence**: KDoc cross-references added to:
- `CommonComponents.ReaderIconButton` → "For reader control layer icon buttons, see ReaderNativeComponents.ReaderIconButton"
- `CommonComponents.ReaderChip` → "For reader control layer chips, see ReaderNativeComponents.ReaderChip"
- `ReaderNativeComponents.ReaderIconButton` → "For general UI icon buttons, see CommonComponents.ReaderIconButton"
- `ReaderNativeComponents.ReaderChip` → "For general UI chips, see CommonComponents.ReaderChip"

**Visual review doc**: P1-6 already marked DEFERRED — now matches code.

## Phase Commits

| Hash | Message |
|------|---------|
| `f85b0ea` | docs: add StitchAppShell hardcoded font deferral note |
| `3742c37` | docs: mark ANDROID-LT-A-001 DONE in task queue |
| `7090df8` | docs: add LOOP-A-001 report |
| `34e6667` | docs: add KDoc cross-references for ReaderIconButton/ReaderChip variants |
| `7c0ba59` | docs: add LOOP-A-002 report |
| `1f7dfb8` | docs: mark ANDROID-LT-A-002 DONE in task queue |
| `64a7119` | docs: Phase A closure + mark ANDROID-LT-A-003 DONE |

---

## Verification

- `./gradlew :app:compileDebugKotlin` — PASS (both A-001 and A-002 changes compile cleanly)
- `./gradlew :app:testDebugUnitTest` — BLOCKED (jlink env issue — not code)
- `./gradlew :app:assembleDebug` — BLOCKED (jlink env issue — not code)

**Phase A status**: COMPLETE

---

## Next Phase

**Phase B: AppProvider DI Cleanup** — `ANDROID-LT-B-003` (Wire SearchViewModel through AppProvider) is the next READY task.

B-002 (CoreBridge provider) is DONE — `AppProvider.coreBridge` now provides FakeCoreBridge or RealCoreBridge based on network state. B-003..B-006 can now wire ViewModels to use it.