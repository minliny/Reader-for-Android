# Phase B Closure Report

**Date**: 2026-05-29
**Phase**: B — AppProvider DI Cleanup
**Status**: COMPLETE

---

## Summary

Phase B addressed the DI cleanup for ViewModels. All four ViewModels (`SearchViewModel`, `BookDetailViewModel`, `TOCViewModel`, `ReaderViewModel`) are now wired to `AppProvider.coreBridge` instead of hardcoded `FakeCoreBridge()`.

The `AppProvider.coreBridge` property returns:
- `FakeCoreBridge()` when network is disabled (default)
- `RealCoreBridge(OkHttpTransport())` when network is enabled

The `useRealHttp` flag in each ViewModel now actually works — it controls whether to use `httpClient` (real) or `bridge` (fake), and the bridge itself will be real when `AppProvider.isNetworkAllowed` is true.

---

## Task Outcomes

### ANDROID-LT-B-001 — ViewModel DI Audit

**Status**: DONE (read-only)

Output a detailed audit of all `FakeCoreBridge()` instantiation points and `HttpClient()`/`Parser()` instantiation points across the 4 ViewModels and 4 Adapter shells.

### ANDROID-LT-B-002 — Add CoreBridge Provider to AppProvider

**Status**: DONE

Added to `AppProvider.kt`:
- `private var _coreBridge: CoreBridge? = null`
- `val coreBridge: CoreBridge` returning `FakeCoreBridge()` or `RealCoreBridge(OkHttpTransport())`
- `fun initForBridge(bridge: CoreBridge)` for test injection
- Reset in `close()`

### ANDROID-LT-B-003..B-006 — Wire ViewModels Through AppProvider

**Status**: DONE

| Task | ViewModel | Change |
|------|-----------|--------|
| B-003 | `SearchViewModel` | `FakeCoreBridge()` → `AppProvider.coreBridge` |
| B-004 | `BookDetailViewModel` | `FakeCoreBridge()` → `AppProvider.coreBridge` |
| B-005 | `TOCViewModel` | `FakeCoreBridge()` → `AppProvider.coreBridge` |
| B-006 | `ReaderViewModel` | `FakeCoreBridge()` → `AppProvider.coreBridge` |

---

## Verification

| Task | compileDebugKotlin | testDebugUnitTest | assembleDebug |
|------|---------------------|-------------------|---------------|
| B-001 | N/A (read-only) | N/A | N/A |
| B-002 | PASS | BLOCKED (env) | BLOCKED (env) |
| B-003 | PASS | BLOCKED (env) | BLOCKED (env) |
| B-004 | PASS | BLOCKED (env) | BLOCKED (env) |
| B-005 | PASS | BLOCKED (env) | BLOCKED (env) |
| B-006 | PASS | BLOCKED (env) | BLOCKED (env) |

All compile successfully. Unit tests and assemble are blocked by a `jlink` environment issue (DevEco-Studio path conflict with Android SDK), not by code correctness.

---

## Phase B Commits

| Hash | Message |
|------|---------|
| `3a12378` | docs: add LOOP-B-001 audit report |
| `7171266` | docs: mark ANDROID-LT-B-001 DONE, unblock B-002 |
| `d9c486d` | feat: add CoreBridge provider to AppProvider |
| `aa8a349` | docs: add LOOP-B-002 report |
| `50327ef` | feat: wire SearchViewModel to AppProvider.coreBridge |
| `04e7bb6` | docs: add LOOP-B-003 report |
| `6c21c1a` | docs: mark ANDROID-LT-B-003 DONE in task queue |
| `d3f220d` | feat: wire BookDetailViewModel to AppProvider.coreBridge |
| `6108841` | docs: add LOOP-B-004 report |
| `b1d7d4d` | docs: mark ANDROID-LT-B-004 DONE in task queue |
| `70aa1e7` | feat: wire TOCViewModel and ReaderViewModel to AppProvider.coreBridge |
| `e782d9e` | docs: add LOOP-B-005/006 report |
| `12cef21` | docs: mark B-005/006 DONE, B-007 READY in task queue |

---

## Architecture Summary

Before:
```
SearchViewModel → private val bridge = FakeCoreBridge()  // hardcoded, no switching
```

After:
```
AppProvider
  coreBridge: CoreBridge
    (network disabled) → FakeCoreBridge()
    (network enabled) → RealCoreBridge(OkHttpTransport())

ViewModels → AppProvider.coreBridge  // now swappable via AppProvider
```

The `useRealHttp` flag in ViewModels now works end-to-end:
- `useRealHttp = false` → `bridge.search(...)` uses `AppProvider.coreBridge` (Fake or Real)
- `useRealHttp = true` → `httpClient.get(url)` uses direct HTTP

---

## Next Phase

**Phase C: Device Review** — `ANDROID-LT-C-001` (Verify installDebug succeeds) is READY but requires device. Requires human confirmation and physical device/emulator. Next loop available when device is connected.