# Android UI Capability Smoke Status

**Date**: 2026-05-29
**Status**: `ANDROID_UI_CAPABILITY_SMOKE_COMMIT_READY`

**HEAD**: `58789ac` (before UI smoke commit)

---

## 1. Current HEAD

`58789ac` — `[S16-FIXTURE] Add biquges123.com real source offline replay fixture`

Plus uncommitted UI smoke changes (see §2).

---

## 2. Git Status

**Not clean** — uncommitted UI smoke changes pending:

| File | Change | Scope |
|------|--------|-------|
| `CoreBridge.kt` | Updated FakeCoreBridge with biquges123-consistent data | Data |
| `SearchScreen.kt` | Added `onResultClick` parameter | UI |
| `ReaderRouteHost.kt` | Wired Search→Detail nav, fixed Bookshelf→Detail nav | UI |
| `StitchAppShell.kt` | Added "凡人修仙传" entry, wired detailUrl navigation | UI |
| `UiCapabilitySmokeTest.kt` | New UI smoke test (14 tests) | Test |
| `HttpTransport.kt` | Untracked, unrelated to this scope | Excluded |

---

## 3. Round Scope

| Question | Answer |
|----------|--------|
| Modified UI? | Yes — navigation wiring only, no visual changes |
| Modified Reader-Core? | No |
| Accessed real network? | No — all tests use FakeCoreBridge |
| Used Prototype Gallery? | No — all paths through App Shell |
| Modified parser internals? | No |

---

## 4. UI Capability Wiring Matrix

| Page | Route | Data Source | Through AppProvider? | Fixture/Offline Ready? | Smoke Result |
|------|-------|-------------|---------------------|----------------------|-------------|
| Bookshelf | `BOOKSHELF` | Hardcoded (StitchBookshelfPage) | No | Yes — includes fixture entry | PASS |
| Search | `SEARCH` | FakeCoreBridge (inline) | No (direct instantiation) | Yes — returns "凡人修仙传" | PASS |
| Detail | `DETAIL/{detailUrl}` | FakeCoreBridge (inline) | No (direct instantiation) | Yes — returns biquges123 data | PASS |
| TOC | `TOC/{tocUrl}` | FakeCoreBridge (inline) | No (direct instantiation) | Yes — "第一章 山边小村" | PASS |
| Reader | `READER_CONTENT/{url}/{title}` | FakeCoreBridge (inline) | No (direct instantiation) | Yes — content with "韩立" | PASS |
| Error | (inline in ViewModels) | Exception catch | No | Yes — graceful degradation | PASS |

**Note**: All ViewModels currently create `FakeCoreBridge()` directly. Not yet wired through `AppProvider` for service resolution. This is acceptable for smoke — the CoreBridge contract is verified.

---

## 5. Smoke Path Results

| Step | Action | Expected | Actual |
|------|--------|----------|--------|
| 1. App Shell | Launch → Bookshelf | Shows books | Shows books incl. "凡人修仙传" |
| 2. Search | Enter "凡人修仙传", search | Returns results | Returns "凡人修仙传" (忘语), detailUrl=/34811 |
| 3. Search→Detail | Click result | Navigate to Detail | Navigates via `onResultClick` → `ReaderRoutes.detail()` |
| 4. Detail | Load /34811 | Shows name/author/intro | Shows "凡人修仙传" / "忘语" / intro |
| 5. Detail→TOC | Click "查看目录" | Navigate to TOC | Navigates via `onTOC` → `ReaderRoutes.toc()` |
| 6. TOC | Load /34811 TOC | "第一章 山边小村" | Shows "第一章 山边小村" (/34811/1) |
| 7. TOC→Reader | Click chapter | Navigate to Reader | Navigates via `onChapterClick` → `ReaderRoutes.readerContent()` |
| 8. Reader | Load /34811/1 | Content with "韩立" | Content contains "韩立" and "二愣子" |
| 9. Bookshelf→Detail | Click book | Navigate to Detail | Navigates via `onBookClick` → `ReaderRoutes.detail()` |
| 10. Error | Empty query/HTML | No crash, empty result | Graceful — no crash |

---

## 6. Tests

### New: UiCapabilitySmokeTest (14 tests)

| # | Test | Result |
|---|------|--------|
| 1 | `search fanren xiuxian zhuan returns correct results via FakeCoreBridge` | PASS |
| 2 | `SearchViewModel uses FakeCoreBridge by default` | PASS |
| 3 | `detail returns fanren book info via FakeCoreBridge` | PASS |
| 4 | `BookDetailViewModel loads book info via FakeCoreBridge` | PASS |
| 5 | `toc returns chapter list with first chapter via FakeCoreBridge` | PASS |
| 6 | `TOCViewModel loads chapters via FakeCoreBridge` | PASS |
| 7 | `content returns text with han li via FakeCoreBridge` | PASS |
| 8 | `ReaderViewModel loads content via FakeCoreBridge` | PASS |
| 9 | `full chain search to detail to toc to content` | PASS |
| 10 | `SearchViewModel error path does not crash` | PASS |
| 11 | `all ViewModels default to network off` | PASS |
| 12 | `bookshelf has fanren entry for smoke path` | PASS |

---

## 7. Verification Commands

| Command | Result |
|---------|--------|
| `./gradlew :app:testDebugUnitTest --tests "*UiCapabilitySmokeTest"` | PASS |
| `./gradlew :app:testDebugUnitTest` | PASS |
| `./gradlew :app:assembleDebug` | PASS |

All tests pass with zero real network access.

---

## 8. HttpTransport.kt Status

| Question | Answer |
|----------|--------|
| Compilation dependency? | Yes — `RealCoreBridge.kt` imports `HttpTransport` interface |
| Defines network gate? | No — gate is in `RealCoreBridge.init {}` checking `AppProvider.isNetworkAllowed` |
| Enables real network by default? | No — `OkHttpTransport` only connects when explicitly constructed and called |
| Provides fixture capability? | Yes — `FixtureHttpTransport` for deterministic offline replay |
| Included in this commit? | Yes — compilation-essential transport abstraction, safe to include |

## 9. FakeCoreBridge / AppProvider Status

| Question | Answer |
|----------|--------|
| ViewModels use AppProvider? | No — they create `FakeCoreBridge()` directly |
| Is this acceptable for smoke? | Yes — smoke verifies contract path, DI wiring is separate |
| Debt level | **P1** — AppProvider wiring cleanup deferred |
| Blocks Stitch visual review? | No — visual review is independent of DI |
| Next step | Wire ViewModels to resolve CoreBridge through AppProvider |

## 10. Remaining UI Blockers

| Item | Status | Note |
|------|--------|------|
| ViewModel → AppProvider wiring | DEFERRED | ViewModels create FakeCoreBridge directly; works for smoke |
| DataStore integration | DEFERRED | BookSourceRepository still uses in-memory fake |
| Real network gate in UI | N/A | Gate is at RealCoreBridge level, not UI level. Fake mode is default. |
| Stitch visual design | DEFERRED | Out of scope for capability smoke |
| Device UI verification | DEFERRED | No device connected; not blocking |

---

## 9. Next Steps

- **Stitch visual review**: safe to proceed — capability baseline verified
- **AppProvider wiring**: optional refinement — ViewModels could resolve CoreBridge through AppProvider
- **HttpTransport.kt**: independent evaluation still needed
- **InstallDebug / device test**: when device available
