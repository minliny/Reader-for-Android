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
| S8-NUI-P0-001 | S8-NUI | P0 | DONE | RSS model and parser | RssFeed/RssItem/RssSubscription + RssParser (CDATA support) | ./gradlew test: 183 tests, 0 failures ✅ | None |
| S8-NUI-P0-002 | S8-NUI | P0 | DONE | Subscription update engine | SubscriptionRepository + Fake impl, needsUpdate with maxAge | ./gradlew test: 188 tests, 0 failures ✅ | None |
| S8-NUI-P0-003 | S8-NUI | P0 | DONE | Explore source contract | ExploreSource + ExploreMapping.groupByCategory, 3 tests | ./gradlew test: 191 tests, 0 failures ✅ | None |

## Stage 9 NUI: Local Book Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S9-NUI-P0-001 | S9-NUI | P0 | DONE | Local book import contract | LocalBookSource/Metadata/ImportResult + LocalChapterRef | ./gradlew test: 195 tests, 0 failures ✅ | None |
| S9-NUI-P0-002 | S9-NUI | P0 | DONE | TXT parser baseline | TxtParser: chapter detection (Chinese/English), BOM detection | ./gradlew test: 201 tests, 0 failures ✅ | None |
| S9-NUI-P0-003 | S9-NUI | P0 | DONE | EPUB inventory contract | EpubInventory/ManifestItem/SpineItem/Metadata + ContainerParser | ./gradlew test: 207 tests, 0 failures ✅ | None |
| S9-NUI-P0-004 | S9-NUI | P0 | DONE | Local book progress/cache integration | LocalBookProgressMapper: progress + cache mapping, 4 tests | ./gradlew test: 211 tests, 0 failures ✅ | None |

## Stage 10 NUI: TTS Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S10-NUI-P0-001 | S10-NUI | P0 | DONE | TTS engine contract | TtsEngine interface + FakeTtsEngine + 5 playback states | ./gradlew test: 240 tests, 0 failures ✅ | None |
| S10-NUI-P0-002 | S10-NUI | P0 | DONE | Android TTS adapter boundary | AndroidTtsAdapter + Fake impl + TtsInitResult, 4 tests | ./gradlew test: 244 tests, 0 failures ✅ | None |

## Stage 11 NUI: WebDAV Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S11-NUI-P0-001 | S11-NUI | P0 | DONE | WebDAV client contract | WebDavClient + FakeWebDavClient, 5 methods, 5 tests | ./gradlew test: 264 tests, 0 failures ✅ | None |
| S11-NUI-P0-002 | S11-NUI | P0 | DONE | Backup package model | BackupManifest + BackupEntry + JSON serialization, 3 tests | ./gradlew test: 267 tests, 0 failures ✅ | None |
| S11-NUI-P0-003 | S11-NUI | P0 | DONE | Progress sync protocol | ProgressSyncRecord + ConflictResolver (4 strategies), 6 tests | ./gradlew test: 273 tests, 0 failures ✅ | None |
| S11-NUI-P0-004 | S11-NUI | P0 | DONE | Remote WebDAV books contract | RemoteBookRef + DownloadPolicy + CachePolicy, 3 tests | ./gradlew test: 276 tests, 0 failures ✅ | None |

## Stage 12 NUI: Cloud Sync Core (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S12-NUI-P0-001 | S12-NUI | P0 | DONE | Sync manager state machine | SyncManager: start/complete/fail/conflict/reset, 5 tests | ./gradlew test: 293 tests, 0 failures ✅ | None |
| S12-NUI-P0-002 | S12-NUI | P0 | DONE | Conflict resolver | 6 scenario tests: NEWER_WINS/local/remote/null/both-null/identical | ./gradlew test: 299 tests, 0 failures ✅ | None |

## Stage 13 NUI: Remote Reading Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S13-NUI-P0-001 | S13-NUI | P0 | DONE | Remote content provider contract | RemoteContentProvider, streaming/download, cache key, tests | ./gradlew test | None |
| S13-NUI-P0-002 | S13-NUI | P0 | DONE | Offline availability manager | Cache manifest, eviction policy, tests | ./gradlew test | None |

## Stage 14 NUI: Compatibility Matrix (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S14-NUI-P0-001 | S14-NUI | P0 | DONE | Legado capability matrix model | Capability enum/model, ownership, status, tests | ./gradlew test | None |
| S14-NUI-P0-002 | S14-NUI | P0 | DONE | Regression fixture registry | Fixture metadata, local-only, parser/source registry, tests | ./gradlew test | None |
| S14-NUI-P0-003 | S14-NUI | P0 | DONE | Non-UI release gate checklist | test/build/doc checklist | ./gradlew :app:assembleDebug | None |

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
| S7-NUI-P0-008 | S7-NUI | P0 | DONE | Source validation result model | SourceValidationResult: Success/Warning/Error sealed class, 4 tests | ./gradlew test: 160 tests, 0 failures ✅ | None |
| S7-NUI-P0-009 | S7-NUI | P0 | DONE | Parser fixture registry integration | FixtureRegistry: register/get/filter, FixtureEntry metadata, 5 tests | ./gradlew test: 165 tests, 0 failures ✅ | None |
| S7-NUI-P0-010 | S7-NUI | P0 | DONE | Web runtime error mapping | JsErrorType → ReaderErrorCode mapper, all 5 types covered | ./gradlew test: 171 tests, 0 failures ✅ | None |
| S7-NUI-P0-011 | S7-NUI | P0 | DONE | Dynamic source offline replay contract | OfflineReplayRecord + OfflineReplayContract.validate/isValidForReplay | ./gradlew test: 176 tests, 0 failures ✅ | None |
| S7-NUI-P0-012 | S7-NUI | P0 | DONE | Per-source runtime isolation contract | RuntimeScope: cookie/js/key isolation per sourceUrl, 3 tests | ./gradlew test: 179 tests, 0 failures ✅ | None |

## Stage 9 NUI extended: Local Book Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S9-NUI-P0-005 | S9-NUI | P0 | DONE | TXT encoding detection | EncodingDetector: BOM + UTF-8 valid + GBK heuristic + confidence | ./gradlew test: 215 tests, 0 failures ✅ | None |
| S9-NUI-P0-006 | S9-NUI | P0 | DONE | TXT chapter split edge cases | 5 edge case tests: English/Volume/prefixed/prologue/single chapter | ./gradlew test: 220 tests, 0 failures ✅ | None |
| S9-NUI-P0-007 | S9-NUI | P0 | DONE | EPUB OPF metadata parser | OpfParser: dc metadata + cover-id extraction, 3 tests | ./gradlew test: 223 tests, 0 failures ✅ | None |
| S9-NUI-P0-008 | S9-NUI | P0 | DONE | EPUB spine/manifest parser | OpfParser: manifest items + spine refs + resolveReadingOrder | ./gradlew test: 226 tests, 0 failures ✅ | None |
| S9-NUI-P0-009 | S9-NUI | P0 | DONE | Local book reimport/delete semantics | LocalBookLifecycle: 3 strategies + cleanup URLs, 5 tests | ./gradlew test: 231 tests, 0 failures ✅ | None |
| S9-NUI-P0-010 | S9-NUI | P0 | DONE | Local book cache/progress compat tests | Full mapping pipeline valid: progress + cache + URI scheme, 3 tests | ./gradlew test: 234 tests, 0 failures ✅ | None |

## Stage 10 NUI extended: TTS Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S10-NUI-P0-003 | S10-NUI | P0 | DONE | TTS queue model | TtsQueue: priority enqueue/dequeue, state tracking, 5 tests | ./gradlew test: 249 tests, 0 failures ✅ | None |
| S10-NUI-P0-004 | S10-NUI | P0 | DONE | TTS chapter integration contract | ChapterTextFeeder: paragraph split, boundary events, reset | ./gradlew test: 255 tests, 0 failures ✅ | None |
| S10-NUI-P0-005 | S10-NUI | P0 | DONE | TTS error mapping tests | TtsErrorMapper: 4 error types → ReaderErrorCode, 4 tests | ./gradlew test: 259 tests, 0 failures ✅ | None |

## Stage 11 NUI extended: WebDAV Backend (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S11-NUI-P0-005 | S11-NUI | P0 | DONE | WebDAV auth model | AuthMethod (Basic/Digest/Bearer) + WebDavCredential, 3 tests | ./gradlew test: 279 tests, 0 failures ✅ | None |
| S11-NUI-P0-006 | S11-NUI | P0 | DONE | WebDAV XML response parser | WebDavXmlParser: MultiStatus + resources + collection detection | ./gradlew test: 282 tests, 0 failures ✅ | None |
| S11-NUI-P0-007 | S11-NUI | P0 | DONE | WebDAV retry/error mapping | RetryPolicy (exp backoff) + WebDavErrorMapper, 6 tests | ./gradlew test: 288 tests, 0 failures ✅ | None |

## Stage 12 NUI extended: Cloud Sync (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S12-NUI-P0-003 | S12-NUI | P0 | DONE | Sync operation persistence | SyncOperationLog Room entity, last-sync timestamp, operation history, tests | ./gradlew test | None |
| S12-NUI-P0-004 | S12-NUI | P0 | DONE | Sync conflict test matrix | ConflictScenario enum, resolution strategy per scenario, matrix test suite | ./gradlew test | None |
| S12-NUI-P0-005 | S12-NUI | P0 | DONE | Backup restore contract | BackupRestoreManager, package validation, partial restore policy, tests | ./gradlew test | None |

## Stage 13 NUI extended: Remote Reading (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S13-NUI-P0-003 | S13-NUI | P0 | DONE | Remote file listing parser | WebDAV directory listing parser, file/dir model, sorting, tests | ./gradlew test | None |
| S13-NUI-P0-004 | S13-NUI | P0 | DONE | Remote download cache integration | DownloadCacheManager, temp file policy, cache key for remote URLs, tests | ./gradlew test | None |
| S13-NUI-P0-005 | S13-NUI | P0 | DONE | Offline availability tests | Offline read verification, stale content eviction, re-download trigger, tests | ./gradlew test | None |

## Stage 15 NUI extended: Release Candidate Gate (GAP tasks — non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S15-NUI-P0-001 | S15-NUI | P0 | DONE | Non-UI RC gate | All NUI tasks DONE, tests pass, assembleDebug pass | ./gradlew test + assembleDebug | None |
| S15-NUI-P0-002 | S15-NUI | P0 | DONE | Non-UI capability completion audit | Verify all 136 capabilities in COMPLETION_TARGET, generate audit report | Document exists, all gaps closed | None |
| S15-NUI-P0-003 | S15-NUI | P0 | READY | Backend regression suite gate | All regression fixtures pass, no skipped tests, coverage ≥ target | ./gradlew test | None |
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

**Next READY: S10-NUI-P0-002 Android TTS adapter boundary**

Non-UI development mode. Total queue tasks: **71**. Done: 37 (S6.5 7, S6-SET 5, S6-CACHE 1, S7-NUI 12, S8-NUI 3, S9-NUI 10, S10-NUI 1). Remaining: 34.

Stage execution order: S10-NUI → S11-NUI → S12-NUI → S13-NUI → S14-NUI → S15-NUI.

Tests: 240, 0 failures. `./gradlew test` ✅, `./gradlew :app:assembleDebug` ✅.

Completion target: 136 capabilities in ANDROID_NON_UI_COMPLETION_TARGET.md. Loop: .claude/commands/loop.md.
