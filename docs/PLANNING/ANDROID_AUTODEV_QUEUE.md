# Reader for Android AutoDev Queue

**Date**: 2026-05-14
**Purpose**: Cron loop task queue — each iteration picks the first READY task

---

## Queue Format

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|---|---|---|---|---|---|---|---|---|

---

## Stage 0: Planning & Infrastructure

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P0-S0-001 | S0 | P0 | DONE | Create docs/PLANNING/ directory and all baseline planning docs | Write new .md files in docs/PLANNING/ | All docs exist and have required sections | None | None |
| P0-S0-002 | S0 | P0 | DONE | Create .claude/ loop config (loop.md, commands/, settings.json) | Write new files in .claude/ only | All config files exist and parse correctly | None | None |
| P0-S0-003 | S0 | P0 | DONE | Generate initial BLOCKERS_AND_DECISIONS.md with all pre-identified decision points | Write docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md | All P0/P1 decision points documented | None | None |
| P0-S0-004 | S0 | P0 | DONE | Verify git state clean, create initial project commit | git add, git commit (NO push) | git status clean, commit created | None | User must approve initial commit |
| P0-S1-001 | S1 | P0 | DONE | Create Gradle Kotlin DSL empty project skeleton | Create gradle/, build.gradle.kts, settings.gradle.kts, gradle.properties | ./gradlew projects passes | None | No (user decisions resolved 2026-05-13) |
| P0-S1-002 | S1 | P0 | DONE | Verify Android local build environment (JDK, SDK, Gradle wrapper) | Install/configure JDK 17+, Android SDK, ANDROID_HOME, generate gradle wrapper | java -version shows JDK 17+, ./gradlew projects passes | None | N/A (verified 2026-05-14) |
| P0-S1-003 | S1 | P0 | DONE | Create Compose App Shell with Scaffold, BottomNav, NavHost | Write Compose UI files only | App launches with navigation structure | None (compile-time verification blocked by env) | No |
| P0-S1-004 | S1 | P0 | DONE | Create placeholder pages (Bookshelf, BookSource, Settings, Reader) | Write Compose screen files only | All 4 placeholder screens render | None (compile-time verification blocked by env) | No |
| P0-S1-005 | S1 | P0 | DONE | App Shell Navigation Contract: verify scaffold, bottom nav, NavHost, 4 tab routes work at runtime | Verify AppNavigation.kt compiles, navigation graph is valid, all routes accessible | ./gradlew :app:assembleDebug ✅ | None | No |

## Stage 1-2: Model & Fake Data Flow

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P1-S2-001 | S2 | P1 | DONE | Define Kotlin domain models matching Core DTOs | Write Kotlin data classes in model/ package | compileDebugKotlin ✅ | None | No (follows Core DTO spec) |
| P1-S2-002 | S2 | P1 | DONE | Write DATA_LAYER_DESIGN.md design doc | Write docs/design/DATA_LAYER_DESIGN.md only | Doc covers Room vs DataStore tradeoffs | None | No (doc only, no code) |
| P1-S2-003 | S2 | P1 | DONE | Define FakeCoreBridge interface + fake impl | Write Kotlin interface + fake class | compileDebugKotlin ✅ | None | No |
| P1-S3-001 | S3 | P1 | DONE | Define ReaderCoreBridge contract in Kotlin | Write Kotlin interface only | compileDebugKotlin ✅ | None (Core bridge strategy resolved BD-007) | No |
| P1-S3-002 | S3 | P1 | DONE | Write CORE_BRIDGE_DESIGN.md documenting integration strategy | Write docs/design/CORE_BRIDGE_DESIGN.md only | Doc covers 3 strategies (JSON contract, KMP, embedded) with recommendation | None | No (doc only, analysis) |
| P1-S4-001 | S4 | P1 | DONE | Create BookSourceRepository (fake) with JSON import | Write Kotlin repository + JSON parser | compileDebugKotlin ✅ | None | No |
| P1-S4-002 | S4 | P1 | DONE | Create SourceManagementScreen | Write Compose UI for source list, enable/disable, delete | compileDebugKotlin ✅ | None | No |
| P1-S5-001 | S5 | P1 | DONE | Create SearchScreen with fake results | Write Compose UI + fake ViewModel | compileDebugKotlin ✅ | None | No |
| P1-S5-002 | S5 | P1 | DONE | Create BookDetailScreen with fake info | Write Compose UI + fake ViewModel | compileDebugKotlin ✅ | None | No |
| P1-S5-003 | S5 | P1 | DONE | Create TOCScreen with fake chapter list | Write Compose UI + fake ViewModel | compileDebugKotlin ✅ | None | No |
| P1-S5-004 | S5 | P1 | DONE | Create ReaderScreen with fake content | Write Compose UI + fake ViewModel | compileDebugKotlin ✅ | None | No |
| P1-S5-005 | S5 | P1 | DONE | Wire Search→Detail→TOC→Reader navigation with fake data | Modify navigation graph, pass fake IDs between screens | compileDebugKotlin ✅ | None | No |

## Stage 3: Design Docs (can be done in parallel with any stage)

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P2-S3-003-DOC | S3 | P1 | DONE | Write HTTP client adapter design doc (OkHttp vs kTor) | Write docs/design/HTTP_ADAPTER_DESIGN.md only | Doc covers tradeoffs and recommendation | None | No (doc only) |
| P3-S7-001-DOC | S7 | P3 | DONE | Write WebView/JS platform adapter design doc | Write docs/design/WEBVIEW_JS_ADAPTER_DESIGN.md only | Doc covers Android WebView, QuickJS vs Hermes, security | None | No (doc only, deferred to S7) |
| P3-S11-001-DOC | S11 | P3 | DONE | Write WebDAV three-way design doc | Write docs/design/WEBDAV_DESIGN.md only | Doc covers backup layout, client lib selection, scheduling | None | No (doc only, deferred to S11) |

## Stage 3-6: Real Integration (DONE)

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P2-S3-004 | S3 | P2 | DONE | Implement HTTPClient adapter using OkHttp | OkHttp dep + adapter class | compileDebugKotlin ✅ | None | No |
| P2-S3-005 | S3 | P2 | DONE | Implement BookSourceRepository (real: DataStore) | DataStore repo | compileDebugKotlin ✅ | None | No |
| P2-S5-006 | S5 | P2 | DONE | Wire real HTTP fetch + parse for search | SearchParser + SearchViewModel | compileDebugKotlin ✅ | None | No |
| P2-S5-007 | S5 | P2 | DONE | Wire real HTTP fetch + parse for detail | BookInfoParser + DetailViewModel | compileDebugKotlin ✅ | None | No |
| P2-S5-008 | S5 | P2 | DONE | Wire real HTTP fetch + parse for TOC | TOCParser + TOCViewModel | compileDebugKotlin ✅ | None | No |
| P2-S5-009 | S5 | P2 | DONE | Wire real HTTP fetch + parse for content | ContentParser + ReaderViewModel | compileDebugKotlin ✅ | None | No |
| P2-S6-001 | S6 | P2 | DONE | Implement ReadingProgressRepository | Room entity + DAO + AppDatabase | compileDebugKotlin ✅ (KSP) | None | No |
| P2-S6-002 | S6 | P2 | DONE | Implement reader font/theme settings | DataStore prefs + SettingsScreen | compileDebugKotlin ✅ | None | No |
| P2-S6-003 | S6 | P2 | DONE | Implement chapter content cache | CachedChapter + CacheManager | compileDebugKotlin ✅ | None | No |

## Stage 6.5: Baseline Hardening (CURRENT)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S6.5-P0-001 | S6.5 | P0 | DONE | Baseline capability matrix freeze | Create ANDROID_S1_S6_BASELINE.md | File exists, all sections populated | None |
| S6.5-P0-002 | S6.5 | P0 | DONE | Parser contract tests | 15 test cases across 4 parsers | ./gradlew test: 18 tests, 0 failures ✅ | None |
| S6.5-P0-003 | S6.5 | P0 | DONE | Bridge contract tests | 10 tests: FakeCoreBridge 4 + BridgeResult 2 + error codes/stages + ReaderError | ./gradlew test: 28 tests, 0 failures ✅ | None |
| S6.5-P0-004 | S6.5 | P0 | DONE | Repository + preferences persistence tests | 13 tests: BookSourceRepository CRUD (6) + ThemePreferences contract (7) | ./gradlew test: 48 tests, 0 failures ✅ | None |
| S6.5-P0-005 | S6.5 | P0 | DONE | Room + cache tests | 8 tests: ReadingProgress entity, CachedChapter entity, ChapterCacheManager + FakeDao | ./gradlew test: 56 tests, 0 failures ✅ | None |
| S6.5-P0-006 | S6.5 | P0 | DONE | Navigation route contract hardening | 11 tests: route uniqueness, URL encode/decode round-trip, argument names | ./gradlew test: 67 tests, 0 failures ✅ | None |
| S6.5-P0-007 | S6.5 | P0 | DONE | Fake/real mode boundary freeze | 9 structural tests: all 4 ViewModels accept useRealHttp flag | ./gradlew test: 76 tests, 0 failures ✅ | None |

## Stage 7 NUI: WebView/JS/Cookie Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S7-NUI-P0-001 | S7-NUI | P0 | DONE | Web runtime adapter contract | WebRuntimeAdapter + FakeWebRuntimeAdapter, 5 tests | ./gradlew test: 128 tests, 0 failures ✅ | None |
| S7-NUI-P0-002 | S7-NUI | P0 | DONE | JS execution contract tests | JsRequest/JsResponse/JsError model, 5 JsErrorType values | ./gradlew test: 133 tests, 0 failures ✅ | None |
| S7-NUI-P0-003 | S7-NUI | P0 | DONE | Cookie scope and storage contract | CookieStore interface + FakeCookieStore, per-source isolation, 6 tests | ./gradlew test: 139 tests, 0 failures ✅ | None |
| S7-NUI-P0-004 | S7-NUI | P0 | DONE | Dynamic source capability matrix | 12-cap doc: JS/Cookie/POST/WebView/offline/isolation | ./gradlew :app:compileDebugKotlin ✅ | None |

## Stage 8 NUI: Explore/RSS Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S8-NUI-P0-001 | S8-NUI | P0 | TODO | RSS model and parser | RSSFeed, RSSItem, RSS XML parser, fixtures, tests | ./gradlew test | None |
| S8-NUI-P0-002 | S8-NUI | P0 | TODO | Subscription update engine | Repository contract, update timestamp, fake HTTP, tests | ./gradlew test | None |
| S8-NUI-P0-003 | S8-NUI | P0 | TODO | Explore source contract | ExploreSource model, category/feed mapping, tests | ./gradlew test | None |

## Stage 9 NUI: Local Book Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S9-NUI-P0-001 | S9-NUI | P0 | TODO | Local book import contract | LocalBookSource, LocalBookMetadata, URI abstraction, tests | ./gradlew test | None |
| S9-NUI-P0-002 | S9-NUI | P0 | TODO | TXT parser baseline | Encoding strategy, chapter split, title detection, fixtures, tests | ./gradlew test | None |
| S9-NUI-P0-003 | S9-NUI | P0 | TODO | EPUB inventory contract | ZIP/XML manifest, metadata extraction, synthetic fixture tests | ./gradlew test | May need ZIP lib |
| S9-NUI-P0-004 | S9-NUI | P0 | TODO | Local book progress/cache integration | Map local chapters to ReadingProgress/ChapterCache, tests | ./gradlew test | None |

## Stage 10 NUI: TTS Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S10-NUI-P0-001 | S10-NUI | P0 | TODO | TTS engine contract | TtsEngine, TtsUtterance, TtsPlaybackState, FakeTtsEngine, tests | ./gradlew test | None |
| S10-NUI-P0-002 | S10-NUI | P0 | TODO | Android TTS adapter boundary | Platform adapter contract, no UI, tests | ./gradlew test | None |

## Stage 11 NUI: WebDAV Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S11-NUI-P0-001 | S11-NUI | P0 | TODO | WebDAV client contract | WebDavClient interface, PROPFIND/GET/PUT models, fake transport, tests | ./gradlew test | None |
| S11-NUI-P0-002 | S11-NUI | P0 | TODO | Backup package model | BackupManifest, BackupEntry, checksum/version, serialization, tests | ./gradlew test | None |
| S11-NUI-P0-003 | S11-NUI | P0 | TODO | Progress sync protocol | ProgressSyncRecord, conflict resolution, merge tests | ./gradlew test | None |
| S11-NUI-P0-004 | S11-NUI | P0 | TODO | Remote WebDAV books contract | RemoteBookRef, download/cache policy, tests | ./gradlew test | None |

## Stage 12 NUI: Cloud Sync Core (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S12-NUI-P0-001 | S12-NUI | P0 | TODO | Sync manager state machine | SyncState, SyncOperation, retry/backoff, tests | ./gradlew test | None |
| S12-NUI-P0-002 | S12-NUI | P0 | TODO | Conflict resolver | newer-wins, explicit conflict, mergeable progress, tests | ./gradlew test | None |

## Stage 13 NUI: Remote Reading Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S13-NUI-P0-001 | S13-NUI | P0 | TODO | Remote content provider contract | RemoteContentProvider, streaming/download, cache key, tests | ./gradlew test | None |
| S13-NUI-P0-002 | S13-NUI | P0 | TODO | Offline availability manager | Cache manifest, eviction policy, tests | ./gradlew test | None |

## Stage 14 NUI: Compatibility Matrix (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S14-NUI-P0-001 | S14-NUI | P0 | TODO | Legado capability matrix model | Capability enum/model, ownership, status, tests | ./gradlew test | None |
| S14-NUI-P0-002 | S14-NUI | P0 | TODO | Regression fixture registry | Fixture metadata, local-only, parser/source registry, tests | ./gradlew test | None |
| S14-NUI-P0-003 | S14-NUI | P0 | TODO | Non-UI release gate checklist | test/build/doc checklist | ./gradlew :app:assembleDebug | None |

## Stage 6-SETTINGS-NUI: Theme/Settings Backend (GAP — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S6-NUI-SET-001 | S6-SET | P0 | DONE | Theme model (day/night pair) | ThemeConfig + ThemePair data classes, 7 tests | ./gradlew test: 83 tests, 0 failures ✅ | None |
| S6-NUI-SET-002 | S6-SET | P0 | DONE | Text/background/accent color model | ColorModel: ARGB decomposition, hex parse, dark/light detection, presets | ./gradlew test: 92 tests, 0 failures ✅ | None |
| S6-NUI-SET-003 | S6-SET | P0 | DONE | Background transparency model | AlphaConfig: range validation, safe clamp, presets, percentage | ./gradlew test: 100 tests, 0 failures ✅ | None |
| S6-NUI-SET-004 | S6-SET | P0 | DONE | Click area config model | TapZone: 9-zone grid, TapAction 7 types, ClickAreaConfig with defaults | ./gradlew test: 109 tests, 0 failures ✅ | None |
| S6-NUI-SET-005 | S6-SET | P0 | DONE | Font family/typeface config | FontConfig: 4 FontFamily presets + custom typeface, 7 tests | ./gradlew test: 116 tests, 0 failures ✅ | None |

## Stage 6-CACHE-NUI: Cache Backend (GAP — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S6-NUI-CACHE-001 | S6-CACHE | P0 | DONE | Content prefetch contract | PrefetchStrategy: chaptersAhead/Behind, wifiOnly, maxCache, 3 presets | ./gradlew test: 123 tests, 0 failures ✅ | None |

## Stage 7 NUI extended: Dynamic Source Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S7-NUI-P0-005 | S7-NUI | P0 | DONE | POST/API request contract | PostRequestBody: 3 content types, form/json/raw builders, 6 tests | ./gradlew test: 145 tests, 0 failures ✅ | None |
| S7-NUI-P0-006 | S7-NUI | P0 | DONE | Request headers/cookie injection contract | RequestHeaders: UA, Cookie, custom headers, toMap(), 6 tests | ./gradlew test: 151 tests, 0 failures ✅ | None |
| S7-NUI-P0-007 | S7-NUI | P0 | DONE | Pagination/next-page content contract | PageRef + PaginationState, fromCurrentUrl builder, 5 tests | ./gradlew test: 156 tests, 0 failures ✅ | None |
| S7-NUI-P0-008 | S7-NUI | P0 | READY | Source validation result model | SourceValidationResult sealed class, errors/warnings, reachability test contract, tests | ./gradlew test | None |
| S7-NUI-P0-009 | S7-NUI | P0 | TODO | Parser fixture registry integration | Register parser fixtures per source type, fixture metadata, replay test contract | ./gradlew test | None |
| S7-NUI-P0-010 | S7-NUI | P0 | TODO | Web runtime error mapping | WebRuntimeError model, JS error ↔ ReaderErrorCode mapping, tests | ./gradlew test | None |
| S7-NUI-P0-011 | S7-NUI | P0 | TODO | Dynamic source offline replay contract | OfflineReplayRecord, cached JS response replay, no-network validation, tests | ./gradlew test | None |
| S7-NUI-P0-012 | S7-NUI | P0 | TODO | Per-source runtime isolation contract | RuntimeScope model, per-source JS context boundaries, cookie/key isolation, tests | ./gradlew test | None |

## Stage 9 NUI extended: Local Book Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S9-NUI-P0-005 | S9-NUI | P0 | TODO | TXT encoding detection | EncodingDetector (UTF-8/GBK/Big5/UTF-16), BOM handling, confidence scoring, tests | ./gradlew test | None |
| S9-NUI-P0-006 | S9-NUI | P0 | TODO | TXT chapter split edge cases | Multi-pattern chapter detection (第X章/Chapter X/Volume), fallback strategies, tests | ./gradlew test | None |
| S9-NUI-P0-007 | S9-NUI | P0 | TODO | EPUB OPF metadata parser | OPF XML parser (title/author/cover/date), namespace handling, tests | ./gradlew test | None |
| S9-NUI-P0-008 | S9-NUI | P0 | TODO | EPUB spine/manifest parser | Spine/manifest XML parser, itemref/item resolution, reading order, tests | ./gradlew test | None |
| S9-NUI-P0-009 | S9-NUI | P0 | TODO | Local book reimport/delete semantics | Reimport merge strategy, delete cascade (progress/cache), orphan cleanup, tests | ./gradlew test | None |
| S9-NUI-P0-010 | S9-NUI | P0 | TODO | Local book cache/progress compat tests | Verify local book chapters work with ReadingProgress/CachedChapter schema, tests | ./gradlew test | None |

## Stage 10 NUI extended: TTS Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S10-NUI-P0-003 | S10-NUI | P0 | TODO | TTS queue model | TtsQueue, utterance ordering, priority/insert, queue events, tests | ./gradlew test | None |
| S10-NUI-P0-004 | S10-NUI | P0 | TODO | TTS chapter integration contract | ChapterTextFeeder, boundary detection, chapter transition events, tests | ./gradlew test | None |
| S10-NUI-P0-005 | S10-NUI | P0 | TODO | TTS error mapping tests | TtsError ↔ ReaderErrorCode mapping, platform error wrapping, tests | ./gradlew test | None |

## Stage 11 NUI extended: WebDAV Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S11-NUI-P0-005 | S11-NUI | P0 | TODO | WebDAV auth model | AuthMethod sealed class (Basic/Digest/Bearer), credential model, tests | ./gradlew test | None |
| S11-NUI-P0-006 | S11-NUI | P0 | TODO | WebDAV XML response parser | MultiStatus XML parser, href/status/propstat extraction, error status mapping, tests | ./gradlew test | None |
| S11-NUI-P0-007 | S11-NUI | P0 | TODO | WebDAV retry/error mapping | RetryPolicy, status code → error mapping, backoff, tests | ./gradlew test | None |

## Stage 12 NUI extended: Cloud Sync (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S12-NUI-P0-003 | S12-NUI | P0 | TODO | Sync operation persistence | SyncOperationLog Room entity, last-sync timestamp, operation history, tests | ./gradlew test | None |
| S12-NUI-P0-004 | S12-NUI | P0 | TODO | Sync conflict test matrix | ConflictScenario enum, resolution strategy per scenario, matrix test suite | ./gradlew test | None |
| S12-NUI-P0-005 | S12-NUI | P0 | TODO | Backup restore contract | BackupRestoreManager, package validation, partial restore policy, tests | ./gradlew test | None |

## Stage 13 NUI extended: Remote Reading (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S13-NUI-P0-003 | S13-NUI | P0 | TODO | Remote file listing parser | WebDAV directory listing parser, file/dir model, sorting, tests | ./gradlew test | None |
| S13-NUI-P0-004 | S13-NUI | P0 | TODO | Remote download cache integration | DownloadCacheManager, temp file policy, cache key for remote URLs, tests | ./gradlew test | None |
| S13-NUI-P0-005 | S13-NUI | P0 | TODO | Offline availability tests | Offline read verification, stale content eviction, re-download trigger, tests | ./gradlew test | None |

## Stage 15 NUI extended: Release Candidate Gate (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S15-NUI-P0-001 | S15-NUI | P0 | TODO | Non-UI RC gate | All NUI tasks DONE, tests pass, assembleDebug pass | ./gradlew test + assembleDebug | None |
| S15-NUI-P0-002 | S15-NUI | P0 | TODO | Non-UI capability completion audit | Verify all 136 capabilities in COMPLETION_TARGET, generate audit report | Document exists, all gaps closed | None |
| S15-NUI-P0-003 | S15-NUI | P0 | TODO | Backend regression suite gate | All regression fixtures pass, no skipped tests, coverage ≥ target | ./gradlew test | None |
| S15-NUI-P0-004 | S15-NUI | P0 | TODO | No-UI parity declaration document | ANDROID_NON_UI_RELEASE_GATE.md, checklist complete, capability sign-off | Document exists | None |
| S15-NUI-P0-005 | S15-NUI | P0 | TODO | Remaining UI-only gap list | Enumerate capabilities with ui_required=yes, map to future UI stages | Document exists | None |

---

## Queue Selection Algorithm

Each loop iteration:
1. Sort by Priority (P0 > P1 > P2 > P3), then by Stage (S0 > S1 > ...), then by ID
2. Select first task with Status == READY
3. Skip tasks where Blockers field contains unresolved P0 blocker
4. If no READY task found, update blockers doc and stop

## Status Transition Rules

```
READY → IN_PROGRESS (when loop picks it up)
IN_PROGRESS → DONE (validation passes, changes committed)
IN_PROGRESS → BLOCKED (validation fails or blocker discovered)
BLOCKED → READY (when blocker is resolved by user)
DONE → (terminal)
SKIPPED → (terminal, with reason in Blockers)
```

## Commit Rules

- One commit per completed task
- Commit message: `[TaskID] Brief description`
- Example: `[P0-S0-001] Create baseline planning docs`
- NEVER include `git push`
- Validate before commit: `./gradlew :app:assembleDebug` or `./gradlew test` (when applicable)
- If validation fails, mark BLOCKED, do NOT commit

---

## Current Ready Tasks

**Next READY: S7-NUI-P0-001 Web runtime adapter contract**

Non-UI development mode. Total queue tasks: **71** (29 original + 42 gap fill).

Stage execution order: S6.5 → S6-SETTINGS-NUI → S6-CACHE-NUI → S7-NUI → S8-NUI → S9-NUI → S10-NUI → S11-NUI → S12-NUI → S13-NUI → S14-NUI → S15-NUI.

Completion target: 136 capabilities defined in `ANDROID_NON_UI_COMPLETION_TARGET.md`. All `ui_required=no` items must be DONE.

Loop command: `.claude/commands/loop.md`. S15-NUI-P0-004 (parity declaration) + S15-NUI-P0-005 (UI-only gap list) are final exit gates.
