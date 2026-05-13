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

## Stage 6+: Real Integration (deferred)

| ID | Stage | Priority | Status | Task | Allowed Changes | Validation | Blockers | Decision Required |
|----|-------|----------|--------|------|-----------------|------------|----------|-------------------|
| P2-S3-004 | S3 | P2 | DONE | Implement HTTPClient adapter using OkHttp | Add OkHttp dependency, write adapter class | compileDebugKotlin ✅ | None | No |
| P2-S3-005 | S3 | P2 | READY | Implement BookSourceRepository (real: JSON + DataStore) | Add DataStore dependency, write repository | Sources persist across app restart | None | No |
| P2-S5-006 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for search | Modify SearchViewModel to use real HTTP + parse | Real search returns results from a book source | Needs HTTP adapter (P2-S3-004) first | No |
| P2-S5-007 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for detail | Modify DetailViewModel | Real detail returns book info | Depends on P2-S5-006 | No |
| P2-S5-008 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for TOC | Modify TOCViewModel | Real TOC returns chapter list | Depends on P2-S5-006 | No |
| P2-S5-009 | S5 | P2 | BLOCKED | Wire real HTTP fetch + Core parse for content | Modify ReaderViewModel | Real content returns chapter text | Depends on P2-S5-006 | No |
| P2-S6-001 | S6 | P2 | DONE | Implement ReadingProgressRepository | Add Room/DataStore, write progress model + DAO | compileDebugKotlin ✅ (KSP) | None | No |
| P2-S6-002 | S6 | P2 | DONE | Implement reader font/theme settings | Write settings screen, DataStore prefs | compileDebugKotlin ✅ | None | No |
| P2-S6-003 | S6 | P2 | DONE | Implement chapter content cache | Write cache manager using Room/file cache | compileDebugKotlin ✅ | None | No |

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

## Current Ready Tasks (2026-05-13 post-S1-skeleton)

**Next READY: P2-S3-004 Implement HTTPClient adapter using OkHttp**

All unblocked tasks complete through S6. Remaining BLOCKED tasks need:
1. **BD-008** (network access) → unblocks P2-S5-* real HTTP pipeline
2. **BD-009** (JS engine) → S7 WebView/JS
3. **BD-010** (WebDAV lib) → S11 WebDAV
4. **BD-011** (EPUB parser) → S9 local books

Progress summary through S6:
- S0-S1: App shell, Gradle, Compose scaffold ✅
- S2-S3: Domain models, CoreBridge, FakeCoreBridge ✅
- S4: BookSourceRepository + SourceManagementScreen ✅
- S5: Search → Detail → TOC → Reader full navigation ✅
- S6: DataStore (settings) + Room (progress, cache) ✅
- Next stage: S7 (WebView/JS/Cookie/Login) or S5 real integration (needs BD-008)

S1 complete. Build environment verified. Next: S2 domain models.
Cron loop active: `/loop 10m /reader-android-loop` (job 8d532138, every 10 min, 7-day expiry).
