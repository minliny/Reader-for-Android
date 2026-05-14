# Reader for Android S1-S6 Baseline

**Date**: 2026-05-14
**Commit**: 52578b4

## 1. 总体结论

**状态：BASELINE_READY**

| Metric | Value |
|--------|-------|
| Commits | 40 |
| Kotlin source files | 23 (main) + 1 (test) |
| Tests | 3 (ProjectSkeletonTest) |
| `./gradlew test` | BUILD SUCCESSFUL, 0 failures |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Gradle | 8.11.1 |
| AGP | 8.7.3 |
| JDK | 17.0.19 |
| Next stage readiness | S6.5 Baseline Hardening (test gap), NOT S7 |

S1-S6 code is real, compilable, and assembles to a valid APK. Test coverage is below threshold (3 unit tests for 23 source files). S6.5 must address parser contract tests, persistence tests, and navigation contract tests before advancing to S7.

## 2. Capability Matrix

### S1: App Shell

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| Gradle Kotlin DSL project | settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, gradle.properties | DONE | `./gradlew projects` ✅ | 0 | Low |
| :app module | app/build.gradle.kts | DONE | `./gradlew :app:assembleDebug` ✅ | 0 | Low |
| Compose + Material 3 | app/build.gradle.kts (compose-bom:2024.12.01) | DONE | `./gradlew :app:assembleDebug` ✅ | 0 | Low |
| MainActivity | app/.../MainActivity.kt | DONE | `./gradlew :app:assembleDebug` ✅ | 0 | Low |
| AppNavigation (4 tabs + flow) | app/.../ui/AppNavigation.kt | DONE | `./gradlew :app:assembleDebug` ✅ | 0 | Med — navigation params not tested |
| Tab screens (4 placeholders → real) | ui/bookshelf/, booksource/, reader/, settings/ | DONE | `./gradlew :app:assembleDebug` ✅ | 0 | Low — bookshelf is search entry only |

### S2: Domain Models

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| BookInfo | data/model/DomainModels.kt | DONE | `./gradlew :app:compileDebugKotlin` ✅ | 0 | Low |
| BookSource | data/model/DomainModels.kt | DONE | compile ✅ | 0 | Low |
| SearchQuery | data/model/DomainModels.kt | DONE | compile ✅ | 0 | Low |
| SearchResultItem | data/model/DomainModels.kt | DONE | compile ✅ | 0 | Low |
| TOCItem | data/model/DomainModels.kt | DONE | compile ✅ | 0 | Low |
| ContentPage | data/model/DomainModels.kt | DONE | compile ✅ | 0 | Low |

### S3: Core Bridge + Real Data Layer

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| ReaderCoreBridge contract | data/bridge/ReaderCoreBridge.kt | DONE | compile ✅ | 0 | Med — no error mapping tests |
| FakeCoreBridge | data/bridge/CoreBridge.kt | DONE | compile ✅ | 0 | Low |
| BridgeResult (Success/Failure) | data/bridge/ReaderCoreBridge.kt | DONE | compile ✅ | 0 | Med — no failure path tests |
| Error codes (7 types) | data/bridge/ReaderCoreBridge.kt | DONE | compile ✅ | 0 | Med — no error mapping coverage |
| OkHttp HttpClient adapter | data/network/HttpClient.kt | DONE | compile ✅ | 0 | Med — no connection/error tests |
| BookSourceRepository (fake) | data/repository/BookSourceRepository.kt | DONE | compile ✅ | 0 | Low |
| BookSourceRepository (DataStore) | data/repository/DataStoreBookSourceRepository.kt | DONE | compile ✅ | 0 | **High — no persistence round-trip test** |

### S4: Book Source Management

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| Source list + enable/disable + delete | ui/booksource/BookSourceScreen.kt | DONE | compile ✅ | 0 | Med — no ViewModel test |
| SourceManagementViewModel | ui/booksource/BookSourceScreen.kt | DONE | compile ✅ | 0 | Med — no state transition test |
| JSON sample preload | ui/booksource/BookSourceScreen.kt | DONE | compile ✅ | 0 | Low |

### S5: Main Reading Flow

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| SearchScreen | ui/search/SearchScreen.kt | DONE | compile ✅ | 0 | Med — no ViewModel test |
| BookDetailScreen | ui/detail/BookDetailScreen.kt | DONE | compile ✅ | 0 | Med — no ViewModel test |
| TOCScreen | ui/toc/TOCScreen.kt | DONE | compile ✅ | 0 | Med — no ViewModel test |
| ReaderScreen | ui/reader/ReaderScreen.kt | DONE | compile ✅ | 0 | Med — no ViewModel test |
| Navigation (Search→Detail→TOC→Reader) | ui/AppNavigation.kt | DONE | compile ✅ | 0 | **High — URL-encoded args not tested** |
| SearchParser (real HTTP HTML) | data/network/SearchParser.kt | DONE | compile ✅ | 0 | **High — no parser contract test** |
| BookInfoParser (real HTTP HTML) | data/network/BookInfoParser.kt | DONE | compile ✅ | 0 | **High — no parser contract test** |
| TOCParser (real HTTP HTML) | data/network/TOCParser.kt | DONE | compile ✅ | 0 | **High — no parser contract test** |
| ContentParser (real HTTP HTML) | data/network/ContentParser.kt | DONE | compile ✅ | 0 | **High — no parser contract test** |
| Fake/real mode switching | All *ViewModel classes | DONE | compile ✅ | 0 | **High — no mode boundary test** |

### S6: Reader Experience + Persistence

| Capability | Files | Status | Verification | Test | Risk |
|-----------|-------|--------|-------------|------|------|
| ThemePreferences (dark mode) | data/storage/ThemePreferences.kt | DONE | compile ✅ | 0 | Med — no DataStore round-trip test |
| Font size / line spacing / margin | data/storage/ThemePreferences.kt | DONE | compile ✅ | 0 | Med |
| SettingsScreen (sliders + switch) | ui/settings/SettingsScreen.kt | DONE | compile ✅ | 0 | Med |
| Room AppDatabase (v2) | data/storage/ReadingProgress.kt | DONE | compile ✅ (KSP) | 0 | Med — no DAO test |
| ReadingProgress entity/DAO | data/storage/ReadingProgress.kt | DONE | compile ✅ (KSP) | 0 | **High — no DAO read/write test** |
| CachedChapter entity/DAO | data/storage/ChapterCache.kt | DONE | compile ✅ (KSP) | 0 | **High — no cache put/get/evict test** |
| ChapterCacheManager (7-day TTL) | data/storage/ChapterCache.kt | DONE | compile ✅ (KSP) | 0 | **High — no TTL eviction test** |

## 3. Current Architecture

### Single :app module, internal package layering

```
app/src/main/kotlin/com/reader/android/
├── MainActivity.kt                    # Entry point, ComponentActivity + setContent
├── ui/
│   ├── ReaderAndroidApp.kt            # MaterialTheme wrapper
│   ├── AppNavigation.kt               # Bottom nav + flow routes
│   ├── bookshelf/BookshelfScreen.kt   # Bookshelf (search entry)
│   ├── booksource/BookSourceScreen.kt # Source management (list + ViewModel)
│   ├── search/SearchScreen.kt         # Search (ViewModel with fake/real HTTP)
│   ├── detail/BookDetailScreen.kt     # Book detail (ViewModel with fake/real HTTP)
│   ├── toc/TOCScreen.kt              # Chapter list (ViewModel with fake/real HTTP)
│   ├── reader/ReaderScreen.kt         # Content reader (ViewModel with fake/real HTTP)
│   └── settings/SettingsScreen.kt     # Preferences (DataStore-backed)
├── data/
│   ├── model/DomainModels.kt          # 6 Kotlin DTOs
│   ├── bridge/
│   │   ├── CoreBridge.kt              # FakeCoreBridge implementation
│   │   └── ReaderCoreBridge.kt        # Bridge contract + error taxonomy
│   ├── repository/
│   │   ├── BookSourceRepository.kt    # Interface + Fake impl
│   │   └── DataStoreBookSourceRepository.kt  # Real DataStore impl
│   ├── network/
│   │   ├── HttpClient.kt              # OkHttp wrapper
│   │   ├── SearchParser.kt            # HTML → List<SearchResultItem>
│   │   ├── BookInfoParser.kt          # HTML → BookInfo
│   │   ├── TOCParser.kt               # HTML → List<TOCItem>
│   │   └── ContentParser.kt           # HTML → ContentPage
│   └── storage/
│       ├── ThemePreferences.kt        # DataStore preferences
│       ├── ReadingProgress.kt         # Room entity + DAO + AppDatabase
│       └── ChapterCache.kt            # Room entity + DAO + CacheManager
└── test/
    └── ProjectSkeletonTest.kt         # 3 skeleton tests
```

**Multi-module assessment**: NOT needed at S6.5. Current single-module structure with internal package layering is adequate. Re-evaluate at S8-S9 when dynamic feature modules (JS engine, TTS) are introduced.

## 4. Verification Baseline

| Command | Result | Date |
|---------|--------|------|
| `./gradlew projects` | BUILD SUCCESSFUL, :app present | 2026-05-14 |
| `./gradlew test` | BUILD SUCCESSFUL, 3 tests, 0 failures | 2026-05-14 |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | 2026-05-14 |
| `java -version` | OpenJDK 17.0.19 | 2026-05-14 |
| `./gradlew --version` | Gradle 8.11.1 | 2026-05-14 |

## 5. Test Coverage Gap

| Priority | Gap | Impact | Target S6.5 task |
|----------|-----|--------|-----------------|
| P0 | SearchParser contract tests | Parser regressions break search silently | S6.5-P0-002 DONE (3 tests) |
| P0 | BookInfoParser contract tests | Parser regressions break detail silently | S6.5-P0-002 DONE (4 tests) |
| P0 | TOCParser contract tests | Parser regressions break TOC silently | S6.5-P0-002 DONE (4 tests) |
| P0 | ContentParser contract tests | Parser regressions break reader silently | S6.5-P0-002 DONE (4 tests) |
| P0 | ReaderCoreBridge error mapping tests | Error taxonomy not enforced | S6.5-P0-003 |
| P1 | BookSourceRepository persistence round-trip | Data loss silently accepted | S6.5-P0-004 |
| P1 | ThemePreferences read/write | Settings not persisted silently | S6.5-P0-004 |
| P1 | ReadingProgress DAO tests | Progress loss not detected | S6.5-P0-005 |
| P1 | ChapterCache put/get/evict tests | Cache corruption not detected | S6.5-P0-005 |
| P1 | Navigation route contract tests | Broken navigation params not caught | S6.5-P0-006 |
| P2 | Fake/real mode switching boundary tests | Interface not enforced | S6.5-P0-007 |

## 6. Baseline Risks

### P0 — Would block S7+
- None currently. Build and test infrastructure are stable.

### P1 — Affects development quality
- **Test coverage < 5%** of source files. All 23 source files lack contract/unit tests.
- **4 HTML parsers untested** — any regex change can silently break book source compatibility.
- **Room/DataStore untested** — no persistence round-trip tests.
- **Navigation URL-encoding untested** — route parameter corruption not caught at compile time.
- **Fake/real HTTP boundary** — ViewModels hardcode the `useRealHttp` boolean; no interface enforcement.

### P2 — Can be deferred
- BookshelfScreen is still a search entry placeholder (no actual bookshelf grid).
- No DI framework (Hilt/Koin) — ViewModels instantiate dependencies directly.
- No Compose UI tests (device/emulator needed).

## 7. Exit Criteria for S6.5

- [x] S1-S6 baseline document frozen (this file)
- [ ] Task queue updated with S6.5 hardening tasks
- [ ] Blocker/decision document updated
- [ ] Parser contract tests (Search/BookInfo/TOC/Content) passing
- [ ] Bridge error mapping tests passing
- [ ] Repository/persistence round-trip tests passing
- [ ] Cache TTL eviction test passing
- [ ] Navigation route contract confirmed
- [ ] `./gradlew test` passes with all new tests
- [ ] `./gradlew :app:assembleDebug` passes
