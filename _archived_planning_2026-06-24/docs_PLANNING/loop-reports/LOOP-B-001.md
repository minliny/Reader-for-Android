# Loop Report: ANDROID-LT-B-001

**Date**: 2026-05-29
**Loop ID**: B-001
**HEAD**: `64a7119`

---

## Task

Audit all ViewModel FakeCoreBridge() instantiation points — `ANDROID-LT-B-001`

---

## Read-Only Audit

This task makes no code changes. Output only.

## Findings

### FakeCoreBridge() Instantiation Points

| File | Line | Class | Bridge Type | Can Switch? |
|------|------|-------|-------------|--------------|
| `SearchScreen.kt` | 32 | `SearchViewModel` | `FakeCoreBridge()` | No — hardcoded |
| `BookDetailScreen.kt` | 36 | `BookDetailViewModel` | `FakeCoreBridge()` | No — hardcoded |
| `TOCScreen.kt` | 34 | `TOCViewModel` | `FakeCoreBridge()` | No — hardcoded |
| `ReaderScreen.kt` | 52 | `ReaderViewModel` | `FakeCoreBridge()` | No — hardcoded |
| `SearchAdapterShell.kt` | 20 | `SearchAdapterShell` | `FakeCoreBridge()` | No — lazy val |
| `BookDetailAdapterShell.kt` | 19 | `BookDetailAdapterShell` | `FakeCoreBridge()` | No — lazy val |
| `ReaderDirectoryAdapterShell.kt` | 19 | `ReaderDirectoryAdapterShell` | `FakeCoreBridge()` | No — lazy val |
| `ContentAdapterShell.kt` | 18 | `ContentAdapterShell` | `FakeCoreBridge()` | No — lazy val |

### Parser + HttpClient Instantiation Points

| File | Line | Class | HttpClient | Parser |
|------|------|-------|------------|--------|
| `SearchScreen.kt` | 33 | `SearchViewModel` | `HttpClient()` | `SearchParser()` |
| `BookDetailScreen.kt` | 37 | `BookDetailViewModel` | `HttpClient()` | `BookInfoParser()` |
| `TOCScreen.kt` | 35 | `TOCViewModel` | `HttpClient()` | `TOCParser()` |
| `ReaderScreen.kt` | 53 | `ReaderViewModel` | `HttpClient()` | `ContentParser()` |

### Key Observation

All four ViewModels (`SearchViewModel`, `BookDetailViewModel`, `TOCViewModel`, `ReaderViewModel`) have:
- `useRealHttp: Boolean = false` parameter
- `HttpClient()` and parser instances already created
- `if (useRealHttp) { httpClient.get(...) } else { bridge.search(...) }` pattern in their `search()` / `loadContent()` methods

However, `bridge` is always `FakeCoreBridge()` regardless of `useRealHttp`. The `useRealHttp` flag only switches between using `httpClient` (real) or `bridge` (fake) for the actual call.

**Conclusion**: ViewModels are structured to support real http, but the bridge is hardcoded to fake. To make `useRealHttp` actually functional, the ViewModels would need `CoreBridge` injected rather than instantiated inline, and the flag would need to be wired to a real bridge provider.

Adapter shells (`SearchAdapterShell`, `BookDetailAdapterShell`, `ReaderDirectoryAdapterShell`, `ContentAdapterShell`) do not have a `useRealHttp` flag — they are purely fake.

### Switch Capability Summary

| Component | Has real/fake switch? | Currently switchable? |
|-----------|------------------------|----------------------|
| `SearchViewModel` | Yes (`useRealHttp`) | No — bridge hardcoded to `FakeCoreBridge()` |
| `BookDetailViewModel` | Yes (`useRealHttp`) | No — bridge hardcoded to `FakeCoreBridge()` |
| `TOCViewModel` | Yes (`useRealHttp`) | No — bridge hardcoded to `FakeCoreBridge()` |
| `ReaderViewModel` | Yes (`useRealHttp`) | No — bridge hardcoded to `FakeCoreBridge()` |
| `SearchAdapterShell` | No | N/A |
| `BookDetailAdapterShell` | No | N/A |
| `ReaderDirectoryAdapterShell` | No | N/A |
| `ContentAdapterShell` | No | N/A |

**Files audited**:
- `app/src/main/kotlin/com/reader/android/ui/search/SearchScreen.kt`
- `app/src/main/kotlin/com/reader/android/ui/detail/BookDetailScreen.kt`
- `app/src/main/kotlin/com/reader/android/ui/toc/TOCScreen.kt`
- `app/src/main/kotlin/com/reader/android/ui/reader/ReaderScreen.kt`
- `app/src/main/kotlin/com/reader/android/ui/search/SearchAdapterShell.kt`
- `app/src/main/kotlin/com/reader/android/ui/detail/BookDetailAdapterShell.kt`
- `app/src/main/kotlin/com/reader/android/ui/reader/ReaderDirectoryAdapterShell.kt`
- `app/src/main/kotlin/com/reader/android/ui/reader/ContentAdapterShell.kt`

---

## Verification

N/A — read-only audit, no code changes.

## Commit

N/A — no changes made.

---

## Next Step

`ANDROID-LT-B-002`: Add CoreBridge provider to AppProvider. This unblocks by injecting `CoreBridge` rather than instantiating `FakeCoreBridge()` inline, enabling the `useRealHttp` switch to actually function.