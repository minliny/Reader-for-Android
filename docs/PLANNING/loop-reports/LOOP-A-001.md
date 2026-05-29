# Loop Report: ANDROID-LT-A-001

**Date**: 2026-05-29
**Loop ID**: A-001
**HEAD**: `22676ca`

---

## Task

Fix or defer StitchAppShell hardcoded sizes (P1-4) — `ANDROID-LT-A-001`

## Actions

- Read `StitchAppShell.kt` to identify hardcoded font sizes
- Read `STITCH_VISUAL_REVIEW_AFTER_CAPABILITY_SMOKE.md` to confirm P1-4 was already marked DEFERRED
- Read `ANDROID_LONG_TERM_LOOP_EXECUTION_PLAN.md` for LOOP-A-001 instructions
- Added deferral comment to `StitchAppShell.kt` top of Bookshelf section

## Change

```diff
// ── Bookshelf: dual List/Cover mode from Stitch HTML ──
+// Note: font sizes (24sp/18sp/14sp/12sp/10sp) are Stitch design spec values,
// intentionally not mapped to ReaderTheme.typography tokens.
```

**Files modified**: `StitchAppShell.kt` (1-line comment addition)

## Rationale

Per the execution plan: "Defer方案。Stitch 自定义字号是设计意图，不是 bug。强行映射会造成视觉变化。"

P1-4 is already marked DEFERRED_WITH_RATIONALE in the visual review doc. This task closes the loop by adding the corresponding code-level comment.

## Verification

- `./gradlew :app:compileDebugKotlin` — PASS (no output = success)
- `./gradlew :app:testDebugUnitTest` — BLOCKED (jlink error: DevEco-Studio jlink not found at `/Applications/DevEco-Studio.app/Contents/jbr/Contents/Home/bin/jlink`)
- `./gradlew :app:assembleDebug` — BLOCKED (same jlink error)

**Verification status**: COMPILE_PASS / TEST_BLOCKED_BY_ENV (not code issue)

The jlink issue is an environment/toolchain problem (DevEco-Studio path conflict with Android SDK). Code change is correct.

## Commit

```
a4c2f1a docs: add StitchAppShell hardcoded font deferral note
```