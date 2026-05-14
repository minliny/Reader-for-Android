# Android S6.5 Baseline Hardening Plan

**Date**: 2026-05-14
**Depends on**: ANDROID_S1_S6_BASELINE.md

## 1. Mission

Freeze S1-S6 MVP baseline. Close test coverage gap before advancing to S7+ features. Every parser, repository, cache, and navigation contract must have a minimum passing test.

## 2. Non-goals

- S7 WebView/JS dynamic source implementation
- S7 Cookie/Login implementation
- S8 Explore/RSS
- S9 TXT/EPUB local books
- S10 TTS
- S11 WebDAV backup/sync
- Cron /loop automation
- Multi-module refactor
- DI framework introduction (Hilt/Koin)
- UI/instrumentation tests (needs emulator)

## 3. Task Breakdown

---

### S6.5-P0-001 Baseline capability matrix freeze

**Status**: DONE
**Goal**: Create ANDROID_S1_S6_BASELINE.md documenting every S1-S6 capability with files, status, verification, and test gap.
**Scope**: docs/PLANNING/ANDROID_S1_S6_BASELINE.md
**Forbidden changes**: No code changes. No new dependencies.
**Acceptance**: Baseline doc exists with capability matrix, architecture, verification baseline, test gaps, risks, exit criteria.
**Validation**: File present, all sections populated.
**Dependencies**: None.

---

### S6.5-P0-002 Parser contract tests

**Status**: READY
**Goal**: Add minimum unit tests for SearchParser, BookInfoParser, TOCParser, ContentParser using local HTML fixture strings. No network access.
**Scope**:
- `app/src/test/kotlin/com/reader/android/data/network/SearchParserTest.kt`
- `app/src/test/kotlin/com/reader/android/data/network/BookInfoParserTest.kt`
- `app/src/test/kotlin/com/reader/android/data/network/TOCParserTest.kt`
- `app/src/test/kotlin/com/reader/android/data/network/ContentParserTest.kt`
**Forbidden changes**: No modification to parser source logic. No network calls. No new dependencies.
**Acceptance**:
- Each parser has ≥ 2 test cases (success path + empty/missing input).
- All tests pass via `./gradlew test`.
- Test fixtures are minimal inline HTML strings.
**Validation**: `./gradlew test`
**Dependencies**: None.

---

### S6.5-P0-003 Bridge contract tests

**Status**: TODO
**Goal**: Add unit tests for BridgeResult success/failure, error code mapping, and ReaderCoreBridge interface contract. No network.
**Scope**:
- `app/src/test/kotlin/com/reader/android/data/bridge/BridgeContractTest.kt`
**Forbidden changes**: No modification to bridge interface. No new dependencies.
**Acceptance**:
- BridgeResult.Success and BridgeResult.Failure construction tested.
- All 7 ReaderErrorCode values tested for correct type.
- FakeCoreBridge method signatures verified.
**Validation**: `./gradlew test`
**Dependencies**: None.

---

### S6.5-P0-004 Repository and preferences persistence tests

**Status**: TODO
**Goal**: Add minimum round-trip tests for BookSourceRepository and ThemePreferences using DataStore. May use Robolectric or a simple in-memory substitute if DataStore test setup is complex.
**Scope**:
- `app/src/test/kotlin/com/reader/android/data/repository/BookSourceRepositoryTest.kt`
- `app/src/test/kotlin/com/reader/android/data/storage/ThemePreferencesTest.kt`
**Forbidden changes**: No modification to repository or preferences source logic. No new dependencies without justification.
**Acceptance**:
- BookSource import JSON → getAll() round-trip test.
- ThemePreferences setFontSize → fontSize flow test.
- If DataStore test is infeasible without full Android context, record the gap and write a structural contract test instead.
**Validation**: `./gradlew test`
**Dependencies**: May require `testImplementation("androidx.datastore:datastore-preferences-core:1.1.1")` or Robolectric.

---

### S6.5-P0-005 Room and cache tests

**Status**: TODO
**Goal**: Add minimum tests for ReadingProgress DAO and ChapterCache using Room in-memory database.
**Scope**:
- `app/src/test/kotlin/com/reader/android/data/storage/ReadingProgressDaoTest.kt`
- `app/src/test/kotlin/com/reader/android/data/storage/ChapterCacheDaoTest.kt`
**Forbidden changes**: No modification to DAO or cache logic. No real file I/O.
**Acceptance**:
- ReadingProgress upsert + getByUrl round-trip test.
- CachedChapter put + get + evictOlderThan TTL test.
- Use Room.inMemoryDatabaseBuilder for test database.
**Validation**: `./gradlew test`
**Dependencies**: None (Room already supports in-memory testing).

---

### S6.5-P0-006 Navigation route contract hardening

**Status**: TODO
**Goal**: Verify Search→Detail→TOC→Reader navigation route constants, URL encoding/decoding round-trip, and back navigation structure. No UI tests needed — route constant tests only.
**Scope**:
- `app/src/test/kotlin/com/reader/android/ui/NavigationRouteTest.kt`
**Forbidden changes**: No modification to AppNavigation.kt unless route constants are missing.
**Acceptance**:
- All route constants (SEARCH, DETAIL, TOC, READER_CONTENT) are defined and unique.
- URLEncoder/URLDecoder round-trip preserves valid URLs.
- Navigation argument names documented.
**Validation**: `./gradlew test`
**Dependencies**: None.

---

### S6.5-P0-007 Fake/real mode boundary freeze

**Status**: TODO
**Goal**: Document the fake/real switching contract for all 4 ViewModels. Record the current `useRealHttp` boolean pattern and note future DI direction. Optionally add a structural test verifying each ViewModel constructor accepts the flag.
**Scope**:
- Code review of SearchViewModel, BookDetailViewModel, TOCViewModel, ReaderViewModel
- Update ANDROID_S1_S6_BASELINE.md with fake/real mode documentation
**Forbidden changes**: No DI introduction. No breaking changes to ViewModels.
**Acceptance**:
- All 4 ViewModels documented as supporting `useRealHttp: Boolean = false`.
- Future DI/interface extraction direction noted.
**Validation**: `./gradlew :app:compileDebugKotlin`
**Dependencies**: None.

## 4. Execution Order

```
S6.5-P0-001 (DONE) → S6.5-P0-002 (READY) → S6.5-P0-003 → S6.5-P0-004 → S6.5-P0-005 → S6.5-P0-006 → S6.5-P0-007
```

Only one task READY at a time. Each task must pass `./gradlew test` before marking DONE.

## 5. Exit Gate

All S6.5 tasks DONE → `./gradlew test` passes with ≥ 20 total tests → S1-S6 baseline frozen → S7/S9/S11 decisions remain BLOCKED → S8/S10 become candidates.
