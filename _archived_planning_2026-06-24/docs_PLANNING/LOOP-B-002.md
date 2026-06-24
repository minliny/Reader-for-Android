# Loop Report: ANDROID-LT-B-002

**Date**: 2026-05-29
**Loop ID**: B-002
**HEAD**: `7171266`

---

## Task

Add CoreBridge provider to AppProvider — `ANDROID-LT-B-002`

---

## Changes

**AppProvider.kt** — Added:
- `private var _coreBridge: CoreBridge? = null` runtime field
- `coreBridge` property returning `FakeCoreBridge()` when network disabled, `RealCoreBridge(OkHttpTransport())` when enabled
- `initForBridge(bridge: CoreBridge)` for test injection
- Reset `_coreBridge = null` in `close()`

**New imports**: `CoreBridge`, `FakeCoreBridge`, `RealCoreBridge`, `OkHttpTransport`

**Files modified**: `AppProvider.kt` (+27 lines)

---

## Design

- `AppProvider.coreBridge` is the single source of truth for bridge provision
- Network-disabled → `FakeCoreBridge` (deterministic, no I/O)
- Network-enabled → `RealCoreBridge(OkHttpTransport())` (live network, guarded by `isNetworkAllowed` init check)
- Tests inject via `initForBridge(bridge)` to override the default

This means the existing `useRealHttp` flag in ViewModels will now work correctly once ViewModels are wired to use `AppProvider.coreBridge` instead of inline `FakeCoreBridge()`.

---

## Verification

- `./gradlew :app:compileDebugKotlin` — PASS (no output)
- `./gradlew :app:testDebugUnitTest` — BLOCKED (jlink env issue)
- `./gradlew :app:assembleDebug` — BLOCKED (jlink env issue)

**Verification status**: COMPILE_PASS / TEST_BLOCKED_BY_ENV (not code)

## Commit

```
7b6c3f1 feat: add CoreBridge provider to AppProvider
```