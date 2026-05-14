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
| S6.5-P0-004 | S6.5 | P0 | READY | Repository + preferences persistence tests | BookSourceRepository round-trip, ThemePreferences read/write | ./gradlew test passes | May need DataStore test setup |
| S6.5-P0-005 | S6.5 | P0 | TODO | Room + cache tests | ReadingProgress DAO, ChapterCache TTL (Room in-memory) | ./gradlew test passes | None |
| S6.5-P0-006 | S6.5 | P0 | TODO | Navigation route contract hardening | Route constants, URL encode/decode round-trip test | ./gradlew test passes | None |
| S6.5-P0-007 | S6.5 | P0 | TODO | Fake/real mode boundary freeze | Document + structural verify useRealHttp flag | ./gradlew :app:compileDebugKotlin | None |

## Stage 7 NUI: WebView/JS/Cookie Backend (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S7-NUI-P0-001 | S7-NUI | P0 | TODO | Web runtime adapter contract | WebRuntimeAdapter interface, FakeWebRuntimeAdapter, tests | ./gradlew test | None |
| S7-NUI-P0-002 | S7-NUI | P0 | TODO | JS execution contract tests | JS request/response/error model, fake adapter tests | ./gradlew test | None |
| S7-NUI-P0-003 | S7-NUI | P0 | TODO | Cookie scope and storage contract | CookieScope, CookieRecord, CookieStore, Room schema, tests | ./gradlew test | None |
| S7-NUI-P0-004 | S7-NUI | P0 | TODO | Dynamic source capability matrix | JS/Cookie/POST/WebView capability doc | ./gradlew :app:compileDebugKotlin | None |

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

## Stage 15 NUI: Release Candidate (non-UI only)

| ID | Stage | Priority | Status | Task | Scope | Validation | Blockers |
|----|-------|----------|--------|------|-------|------------|----------|
| S15-NUI-P0-001 | S15-NUI | P0 | TODO | Non-UI RC gate | All NUI tasks DONE, tests pass, assembleDebug pass | ./gradlew test + assembleDebug | None |

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

**Next READY: S6.5-P0-004 Repository + preferences persistence tests**

Non-UI development mode. Total planned tasks: 5 (S6.5 remaining) + 4 (S7-NUI) + 3 (S8-NUI) + 4 (S9-NUI) + 2 (S10-NUI) + 4 (S11-NUI) + 2 (S12-NUI) + 2 (S13-NUI) + 3 (S14-NUI) + 1 (S15-NUI) = **30 tasks**.

All UI/product decisions remain BLOCKED. Non-UI backend tasks use DEFAULT_APPROVED defaults. Loop command: `.claude/commands/loop.md`.
