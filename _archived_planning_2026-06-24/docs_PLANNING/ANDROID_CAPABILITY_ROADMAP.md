# Reader for Android Capability Roadmap

**Date**: 2026-05-13
**Purpose**: Priority-tiered roadmap of all tasks, sized for 10-minute loop execution

---

## Priority Definitions

| Tier | Description | Trigger |
|------|-------------|---------|
| P0 | Must complete before any code work; infrastructure + planning | Immediate |
| P1 | Core app structure, fake data flows, UI scaffolding without real Core | After P0 |
| P2 | Real Reader-Core integration, main reading pipeline, persistence | After P1 + Core bridge decision |
| P3 | Advanced features (WebView, JS, WebDAV, TTS, local books) | After P2 |

---

## P0: Infrastructure & Planning (Current Stage)

| ID | Task | Est. Loops | Status |
|----|------|-----------|--------|
| P0-S0-001 | Create docs/PLANNING/ directory and baseline docs | 1 | DONE (this doc) |
| P0-S0-002 | Create .claude/ loop config (loop.md, commands/, settings.json) | 1 | READY |
| P0-S0-003 | Generate initial BLOCKERS_AND_DECISIONS.md | 1 | READY |
| P0-S0-004 | Verify git state clean, initial commit of planning docs | 1 | READY |
| P0-S1-001 | Create Gradle Kotlin DSL empty project skeleton (settings.gradle.kts, build.gradle.kts, gradle wrapper) | 1-2 | PARTIAL_ENV_BLOCKED (files created, compilation blocked by no JDK/SDK) |
| P0-S1-002 | Verify Android local build environment (JDK, SDK, Gradle wrapper) | 1 | BLOCKED (requires user action) |
| P0-S1-003 | Create Compose App Shell (Scaffold, BottomNav, NavHost) | 1-2 | DONE (ReaderAndroidApp, AppNavigation created) |
| P0-S1-004 | Create placeholder pages (Bookshelf, Sources, Search, Settings, Reader) | 1 | DONE (4 placeholder screens created) |

---

## P1: Local Model & Fake Data Flow (UI-First)

| ID | Task | Est. Loops | Status |
|----|------|-----------|--------|
| P1-S2-001 | Define Kotlin domain models matching Core DTOs (BookSource, SearchResultItem, TOCItem, ContentPage, BookInfo) | 1 | BLOCKED (env: no JDK/SDK for compilation) |
| P1-S2-002 | Write DATA_LAYER_DESIGN.md (Room vs DataStore decision doc, no code) | 1 | DONE |
| P1-S2-003 | Define FakeCoreBridge interface + fake implementation returning mock data | 1 | BLOCKED (env: needs compilation) |
| P1-S3-001 | Define ReaderCoreBridge contract in Kotlin | 1 | BLOCKED (env: needs compilation) |
| P1-S3-002 | Write CORE_BRIDGE_DESIGN.md documenting integration strategy decision and gap analysis | 1 | DONE |
| P1-S4-001 | Create BookSourceRepository (fake) with JSON import | 1 | BLOCKED (depends on S2) |
| P1-S4-002 | Create SourceManagementScreen (list, enable/disable, delete) | 1-2 | BLOCKED (depends on S1) |
| P1-S5-001 | Create SearchScreen with fake search results | 1 | BLOCKED (depends on S1 + S4) |
| P1-S5-002 | Create BookDetailScreen with fake book info | 1 | BLOCKED (depends on S1) |
| P1-S5-003 | Create TOCScreen with fake chapter list | 1 | BLOCKED (depends on S1) |
| P1-S5-004 | Create ReaderScreen with fake chapter content | 1 | BLOCKED (depends on S1) |
| P1-S5-005 | Wire Search→Detail→TOC→Reader navigation with fake data | 1 | BLOCKED (depends on prior S5 tasks) |

---

## P2: Real Integration & Reading Pipeline

| ID | Task | Est. Loops | Status |
|----|------|-----------|--------|
| P2-S3-003 | Implement HTTPClient adapter using OkHttp (or kTor) | 2-3 | BLOCKED (needs OkHttp/kTor decision + Core bridge strategy) |
| P2-S3-004 | Implement BookSourceRepository (real: JSON storage + import) | 2 | BLOCKED (depends on DataStore/Room decision) |
| P2-S5-006 | Wire real HTTP fetch + Core parse for search | 2 | BLOCKED (depends on Core bridge) |
| P2-S5-007 | Wire real HTTP fetch + Core parse for book detail | 1 | BLOCKED (depends on P2-S5-006) |
| P2-S5-008 | Wire real HTTP fetch + Core parse for TOC | 1 | BLOCKED (depends on P2-S5-006) |
| P2-S5-009 | Wire real HTTP fetch + Core parse for content | 1 | BLOCKED (depends on P2-S5-006) |
| P2-S6-001 | Implement ReadingProgressRepository (Room/DataStore) | 2 | BLOCKED (depends on storage decision) |
| P2-S6-002 | Implement reader font/theme settings with DataStore | 1 | BLOCKED |
| P2-S6-003 | Implement chapter content cache | 1-2 | BLOCKED |
| P2-S6-004 | Implement scroll position save/restore | 1 | BLOCKED |

---

## P3: Advanced Features

| ID | Task | Est. Loops | Status |
|----|------|-----------|--------|
| P3-S7-001 | Write WebView/JS platform adapter design doc | 1 | DONE (WEBVIEW_JS_ADAPTER_DESIGN.md) |
| P3-S7-002 | Implement Android WebView Runtime adapter | 2-3 | BLOCKED (deferred to S7) |
| P3-S7-003 | Implement QuickJS/Hermes JS adapter | 3-4 | BLOCKED (deferred to S7) |
| P3-S7-004 | Implement Cookie/Login adapter | 2 | BLOCKED (deferred to S7) |
| P3-S8-001 | Implement ExploreScreen with real Explore pipeline | 2 | BLOCKED (deferred to S8) |
| P3-S8-002 | Implement RSS/Subscription management | 2 | BLOCKED (deferred to S8) |
| P3-S9-001 | Implement local file picker + TXT reader | 2 | BLOCKED (deferred to S9) |
| P3-S9-002 | Implement EPUB parser (ZIP + XML) | 3 | BLOCKED (deferred to S9) |
| P3-S10-001 | Implement TTS adapter + playback UI | 2-3 | BLOCKED (deferred to S10) |
| P3-S11-001 | Write WebDAV three-way design doc | 1 | DONE (WEBDAV_DESIGN.md) |
| P3-S11-002 | Implement WebDAV client adapter | 3 | BLOCKED (deferred to S11) |
| P3-S11-003 | Implement backup/restore UI + WorkManager scheduling | 2-3 | BLOCKED (deferred to S11) |
| P3-S12-001 | Implement progress cloud sync manager | 2 | BLOCKED (deferred to S12) |
| P3-S13-001 | Implement remote WebDAV book browser | 2 | BLOCKED (deferred to S13) |
| P3-S14-001 | Create compatibility matrix UI | 1-2 | BLOCKED (deferred to S14) |
| P3-S14-002 | Create UI test suite skeleton | 2 | BLOCKED (deferred to S14) |

---

## Task Sizing Rules

Each task must:
1. Be completable in 1-3 loop iterations (10-30 minutes)
2. Have a clear validation command
3. Not depend on user input during execution (decisions pre-made or deferred)
4. Produce a meaningful, committable diff
5. Not break existing functionality

---

## Current Ready Tasks (for next loop)

1. **P0-S1-002**: Verify Android local build environment (install JDK 17+, Android SDK, generate Gradle wrapper) — **requires user action**
2. After env ready: re-validate P0-S1-001 (`./gradlew :app:assembleDebug`)
