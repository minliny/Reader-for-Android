# Reader for Android AutoDev Queue

**Date**: 2026-05-13
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
| P0-S1-001 | S1 | P0 | BLOCKED | Create Gradle Kotlin DSL empty project skeleton | Create gradle/, build.gradle.kts, settings.gradle.kts, gradle.properties | ./gradlew projects passes | BLOCKED_ENV_ANDROID_SDK, BLOCKED_USER_DECISION: package name, minSdk, Compose vs XML, multi-module | Yes: package name, minSdk, Compose vs XML, multi-module |
| P0-S1-002 | S1 | P0 | BLOCKED | Create app module with Manifest, MainActivity, Theme | Create app/ directory with build.gradle.kts, manifest, source files | ./gradlew :app:assembleDebug passes | Depends on P0-S1-001 | No (uses defaults from S1-001 decision) |
| P0-S1-003 | S1 | P0 | BLOCKED | Create Compose App Shell with Scaffold, BottomNav, NavHost | Write Compose UI files only | App launches with navigation structure | Depends on P0-S1-002 | No |
| P0-S1-004 | S1 | P0 | BLOCKED | Create placeholder pages (Bookshelf, Sources, Search, Settings, Reader) | Write Compose screen files only | All 5 placeholder screens render | Depends on P0-S1-003 | No |

## Stage 1-2: Model & Fake Data Flow

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P1-S2-001 | S2 | P1 | BLOCKED | Define Kotlin domain models matching Core DTOs | Write Kotlin data classes in model/ package | Kotlin compiles, all Core DTO fields present | Depends on P0-S1-001 (needs Gradle module) | No (follows Core DTO spec) |
| P1-S2-002 | S2 | P1 | DONE | Write DATA_LAYER_DESIGN.md design doc | Write docs/design/DATA_LAYER_DESIGN.md only | Doc covers Room vs DataStore tradeoffs | None | No (doc only, no code) |
| P1-S2-003 | S2 | P1 | BLOCKED | Define FakeCoreBridge interface + fake impl | Write Kotlin interface + fake class | Compiles, returns mock data for all methods | Depends on P1-S2-001 | No |
| P1-S3-001 | S3 | P1 | BLOCKED | Define ReaderCoreBridge contract in Kotlin | Write Kotlin interface only | Interface covers search/detail/TOC/content | BLOCKED_CORE_INTEGRATION_STRATEGY | Yes: how to bridge Swift Core to Kotlin |
| P1-S3-002 | S3 | P1 | DONE | Write CORE_BRIDGE_DESIGN.md documenting integration strategy | Write docs/design/CORE_BRIDGE_DESIGN.md only | Doc covers 3 strategies (JSON contract, KMP, embedded) with recommendation | None | No (doc only, analysis) |
| P1-S4-001 | S4 | P1 | BLOCKED | Create BookSourceRepository (fake) with JSON import | Write Kotlin repository + JSON parser | Import Legado-compatible JSON, list sources | Depends on S1 + S2 | No |
| P1-S4-002 | S4 | P1 | BLOCKED | Create SourceManagementScreen | Write Compose UI for source list, enable/disable, delete | UI renders source list, toggle works, delete works | Depends on S1 + P1-S4-001 | No |
| P1-S5-001 | S5 | P1 | BLOCKED | Create SearchScreen with fake results | Write Compose UI + fake ViewModel | Search box, results list render with fake data | Depends on S1 + FakeCoreBridge | No |
| P1-S5-002 | S5 | P1 | BLOCKED | Create BookDetailScreen with fake info | Write Compose UI + fake ViewModel | Detail screen shows name, author, cover, intro | Depends on S1 + FakeCoreBridge | No |
| P1-S5-003 | S5 | P1 | BLOCKED | Create TOCScreen with fake chapter list | Write Compose UI + fake ViewModel | Chapter list renders, tap navigates to reader | Depends on S1 + FakeCoreBridge | No |
| P1-S5-004 | S5 | P1 | BLOCKED | Create ReaderScreen with fake content | Write Compose UI + fake ViewModel | Content text renders in scrollable view | Depends on S1 + FakeCoreBridge | No |
| P1-S5-005 | S5 | P1 | BLOCKED | Wire Search→Detail→TOC→Reader navigation with fake data | Modify navigation graph, pass fake IDs between screens | Full fake flow: search→detail→TOC→reader | Depends on P1-S5-001 through 004 | No |

## Stage 3: Design Docs (can be done in parallel with any stage)

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P2-S3-003-DOC | S3 | P1 | DONE | Write HTTP client adapter design doc (OkHttp vs kTor) | Write docs/design/HTTP_ADAPTER_DESIGN.md only | Doc covers tradeoffs and recommendation | None | No (doc only) |
| P3-S7-001-DOC | S7 | P3 | DONE | Write WebView/JS platform adapter design doc | Write docs/design/WEBVIEW_JS_ADAPTER_DESIGN.md only | Doc covers Android WebView, QuickJS vs Hermes, security | None | No (doc only, deferred to S7) |
| P3-S11-001-DOC | S11 | P3 | DONE | Write WebDAV three-way design doc | Write docs/design/WEBDAV_DESIGN.md only | Doc covers backup layout, client lib selection, scheduling | None | No (doc only, deferred to S11) |

## Stage 6+: Real Integration (deferred)

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P2-S3-004 | S3 | P2 | BLOCKED | Implement HTTPClient adapter using OkHttp | Add OkHttp dependency, write adapter class | HTTP fetch succeeds against test URL | BLOCKED_NETWORK_DECISION, depends on OkHttp decision | Yes: network access permission |
| P2-S3-005 | S3 | P2 | BLOCKED | Implement BookSourceRepository (real: JSON + DataStore) | Add DataStore dependency, write repository | Sources persist across app restart | Depends on storage decision | No |
| P2-S5-006 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for search | Modify SearchViewModel to use real HTTP + parse | Real search returns results from a book source | BLOCKED_CORE_BRIDGE | No |
| P2-S5-007 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for detail | Modify DetailViewModel | Real detail returns book info | Depends on P2-S5-006 | No |
| P2-S5-008 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for TOC | Modify TOCViewModel | Real TOC returns chapter list | Depends on P2-S5-006 | No |
| P2-S5-009 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for content | Modify ReaderViewModel | Real content returns chapter text | Depends on P2-S5-006 | No |
| P2-S6-001 | S6 | P2 | BLOCKED | Implement ReadingProgressRepository | Add Room/DataStore, write progress model + DAO | Progress saved and restored on app restart | Depends on storage decision | No |
| P2-S6-002 | S6 | P2 | BLOCKED | Implement reader font/theme settings | Write settings screen, DataStore prefs | Font size change reflects in reader | Depends on S1 + storage decision | No |
| P2-S6-003 | S6 | P2 | BLOCKED | Implement chapter content cache | Write cache manager using Room/file cache | Cached chapter loads without network | Depends on storage decision + real HTTP | No |

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
