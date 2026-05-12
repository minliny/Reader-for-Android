# Reader for Android Long-Term Development Goals

**Date**: 2026-05-13
**Status**: DRAFT
**Depends On**: Reader-Core (HEAD: 5269081, main)

---

## 1. Project Positioning

Reader for Android is:

- **The Android App** for the Reader ecosystem — it is the Android UI, platform adapter, and user-facing application
- **A Reader-Core consumer** — it depends on Reader-Core public APIs (Facade, DTOs, Error Taxonomy), never internal classes
- **A platform adapter host** — it implements Android-specific adapters (OkHttp, WebView, QuickJS/Hermes, TTS, Room, DataStore, WorkManager)
- **Legado mainstream capability compatible** — it aims to reach feature parity with Legado's mainstream capabilities (dynamic JS, WebView, Cookie/Login, POST/API, Explore/RSS, local books, TTS, WebDAV backup)
- **Clean-room principle** — no Legado source code copying; all implementation is independent
- **No Reader-Core rewrite** — never duplicates Core internals (Parser, Runtime, CSS/XPath/Regex engines)

---

## 2. Long-Term Capability Goals

| Capability | Owner | Depends On Reader-Core | Android Responsibility | Target Stage | Acceptance |
|---|---|---|---|---|---|
| App Shell | Android | None | Gradle project, MainActivity, Theme, Navigation | S1 | App launches with scaffold UI |
| Bookshelf | Android | None (UI only) | Grid/list UI, book CRUD | S1 | Books displayed in shelf |
| Book Source Import | Android | BookSource DTO (decode) | JSON import UI, validation feedback | S4 | Import Legado-compatible JSON |
| Book Source Management | Android | BookSource DTO | Edit/enable/disable/delete UI, group management | S4 | CRUD on book sources |
| Search | Android | NonJSParserEngine (parseSearchResponse) | HTTP fetch, parser invocation, results UI | S5 | Search returns results from a book source |
| Book Detail | Android | NonJSParserEngine (parseBookInfoResponse) | HTTP fetch, parser invocation, detail UI | S5 | Book detail shows name, author, cover, intro |
| Chapter List (TOC) | Android | NonJSParserEngine (parseTOCResponse) | HTTP fetch, parser invocation, TOC UI | S5 | Chapter list displayed with navigation |
| Chapter Content | Android | NonJSParserEngine (parseContentResponse) | HTTP fetch, parser invocation, reader view | S5 | Chapter text rendered in reader |
| Reader UI | Android | None (UI only) | Text rendering, fonts, themes, pagination, scroll | S6 | Readable book text with basic settings |
| Reading Progress | Android | Progress sync schema | Local progress storage, chapter-position tracking | S6 | Progress saved and restored |
| Cache | Android | ReaderCoreCache (EXPERIMENTAL) | HTTP response cache, image cache, chapter preload | S6 | Offline chapter reading |
| WebView/JS | Android | WebViewRuntimeProtocol, JSExecutionAdapter | Android WebView integration, QuickJS/Hermes adapter | S7 | JS-based book sources work |
| Cookie/Login | Android | CookieJar protocol, LoginDescriptor model | OkHttp CookieJar, WebView login UI, credential storage | S7 | Login-required sources accessible |
| POST/API | Android | URL DSL models, HTTPRequest | OkHttp/kTor POST implementation, form/JSON body | S7 | POST-based search/content works |
| Explore/RSS | Android | ExploreRule pipeline, RSS/Subscription models | Explore UI, RSS fetch/parse, subscription management | S8 | Discover page with categories |
| Subscription | Android | Subscription models | Subscription list UI, update checking | S8 | Subscribed sources auto-update |
| Local Books | Android | LocalBook/TXT/EPUB contracts | File picker, TXT reader, EPUB parser, chapter split | S9 | Local TXT/EPUB files readable |
| TTS | Android | TTSAdapter contract | Android TTS engine, playback controls, highlight sync | S10 | Text read aloud with controls |
| WebDAV Backup | Android | WebDAV layout schema, backup models | WebDAV client, WorkManager scheduling, backup/restore UI | S11 | Backup to WebDAV server |
| Progress Cloud Sync | Android | ProgressCloudSync models, sync protocol | Sync trigger, conflict resolution, WebDAV transport | S12 | Progress syncs across devices |
| Remote WebDAV Books | Android | Remote WebDAV Books model | WebDAV book browser, remote file reader | S13 | Read books from WebDAV server |
| Import/Export | Android | BackupPackage models | Import/export UI, file sharing | S13 | Share backup files |
| Diagnostics | Android | MappedReaderError, StructuredErrorLog | Error display, log viewer, source health check | S14 | Source errors surfaced to user |
| Compatibility Matrix | Android | Capability Matrix schema | Android-side compatibility tracking UI | S14 | Source compatibility visible |
| Regression Tests | Android | Core conformance tests | Android UI tests, integration tests, adapter conformance | S14 | Automated test suite passes |

---

## 3. Development Stages

### S0: Android Empty Repo Baseline
- **Goal**: Establish project infrastructure, planning docs, loop configuration
- **Scope**: docs/PLANNING/, .claude/, initial repo structure
- **Non-goals**: Any code, any Gradle, any UI
- **Required files**: docs/PLANNING/*.md, .claude/loop.md, .claude/commands/reader-android-loop.md
- **Acceptance**: All docs created, loop configs valid
- **Blockers**: None (planning-only)
- **User decisions**: Android architecture choices (Compose vs XML, minSdk, package name)
- **Exit tag**: `ANDROID_S0_BASELINE_READY`

### S1: App Shell Minimal
- **Goal**: Create empty Android project with Gradle Kotlin DSL, app module, MainActivity
- **Scope**: Gradle wrapper, settings.gradle.kts, app/build.gradle.kts, AndroidManifest.xml, MainActivity, basic theme
- **Non-goals**: Any feature UI, any data layer, any Core integration
- **Required files**: build.gradle.kts, settings.gradle.kts, gradle/, app/src/main/**
- **Acceptance**: `./gradlew projects` passes, `./gradlew :app:assembleDebug` passes
- **Blockers**: User decisions on Compose vs XML, minSdk, package name, multi-module
- **Exit tag**: `ANDROID_S1_SHELL_READY`

### S2: Local Model/Data Baseline
- **Goal**: Define Kotlin domain models (DTOs matching Core), design persistence schema
- **Scope**: Kotlin data classes for BookSource, SearchResult, TOCItem, ContentPage; Room/DataStore design doc
- **Non-goals**: Actually introducing Room/DataStore dependency (design doc only)
- **Required files**: app/src/main/java/.../model/*.kt, docs/design/DATA_LAYER_DESIGN.md
- **Acceptance**: Kotlin DTOs compile, match Core DTO field sets
- **Blockers**: Swift↔Kotlin bridge strategy decision
- **Exit tag**: `ANDROID_S2_MODELS_READY`

### S3: Reader-Core Public API Bridge
- **Goal**: Define CoreBridge interface, document integration contract for Android
- **Scope**: ReaderCoreBridge interface (Kotlin), Core gap analysis, fake implementation for UI development
- **Non-goals**: Actual Core integration (Swift cannot directly link into Android)
- **Required files**: app/src/main/java/.../core/CoreBridge.kt, docs/design/CORE_BRIDGE_DESIGN.md
- **Acceptance**: Bridge interface compiles, fake impl returns mock data
- **Blockers**: Core integration strategy (JSON contract vs KMP vs embedded)
- **Exit tag**: `ANDROID_S3_BRIDGE_READY`

### S4: Book Source Management Minimal
- **Goal**: Book source import UI, list management, fake data flow
- **Scope**: Import screen, source list, enable/disable toggle, JSON validation
- **Non-goals**: Real HTTP fetch, real parser integration
- **Required files**: app/src/main/java/.../ui/sources/*.kt, app/src/main/java/.../data/SourceRepository.kt
- **Acceptance**: Source import from JSON, source list renders, toggle works
- **Blockers**: None (uses FakeCoreBridge)
- **Exit tag**: `ANDROID_S4_SOURCES_READY`

### S5: Search → Detail → TOC → Content Main Flow
- **Goal**: End-to-end fake main reading pipeline
- **Scope**: Search UI, book detail UI, TOC UI, reader content view, fake data flow through all stages
- **Non-goals**: Real HTTP, real parser, reader settings, progress persistence
- **Required files**: app/src/main/java/.../ui/search/*.kt, detail/*.kt, toc/*.kt, reader/*.kt
- **Acceptance**: Search → tap result → detail → tap TOC → content renders (with fake data)
- **Blockers**: None (uses FakeCoreBridge)
- **Exit tag**: `ANDROID_S5_MAIN_FLOW_READY`

### S6: Reader Experience
- **Goal**: Real reader UI with settings, progress model, basic cache
- **Scope**: Font size/theme settings, reading progress model, chapter preload, scroll/pagination
- **Non-goals**: TTS, WebView content, cloud sync
- **Required files**: app/src/main/java/.../ui/reader/*.kt, app/src/main/java/.../data/ProgressRepository.kt
- **Acceptance**: Reader renders real book text, settings persist, progress saved locally
- **Blockers**: Real HTTP fetch + Core bridge connection
- **Exit tag**: `ANDROID_S6_READER_READY`

### S7: WebView/JS/Cookie/Login Platform Adapter
- **Goal**: Implement Android WebView adapter, JS engine adapter, Cookie/Login flow
- **Scope**: Android WebView integration, QuickJS/Hermes adapter, OkHttp CookieJar, login UI
- **Non-goals**: Explore/RSS, TTS, WebDAV
- **Required files**: app/src/main/java/.../adapter/WebViewAdapter.kt, JSAdapter.kt, CookieAdapter.kt, ui/login/*.kt
- **Acceptance**: JS-based book sources can search and fetch content
- **Blockers**: JS engine selection (QuickJS vs Hermes), WebView security policy
- **Exit tag**: `ANDROID_S7_DYNAMIC_READY`

### S8: Explore/RSS/Subscription
- **Goal**: Discover page, RSS feed reader, subscription management
- **Scope**: Explore UI, RSS fetch/parse, subscription list, update checking
- **Non-goals**: Push notifications, background fetch (S11+)
- **Required files**: app/src/main/java/.../ui/explore/*.kt, ui/subscriptions/*.kt
- **Acceptance**: Explore page shows categories, RSS feed renders, subscriptions update
- **Blockers**: Real HTTP (already resolved by S7)
- **Exit tag**: `ANDROID_S8_EXPLORE_READY`

### S9: Local Books
- **Goal**: Local TXT/EPUB file import and reading
- **Scope**: File picker, TXT parser, EPUB parser (ZIP+XML), chapter split, local reader
- **Non-goals**: PDF, DOCX, MOBI
- **Required files**: app/src/main/java/.../adapter/LocalBookAdapter.kt, ui/local/*.kt
- **Acceptance**: User can open local TXT/EPUB and read
- **Blockers**: EPUB parsing library selection
- **Exit tag**: `ANDROID_S9_LOCAL_BOOKS_READY`

### S10: TTS
- **Goal**: Text-to-speech in reader
- **Scope**: Android TTS engine, playback controls, text highlighting sync
- **Non-goals**: Custom TTS engine, voice cloning
- **Required files**: app/src/main/java/.../adapter/TTSAdapter.kt, ui/reader/TTSControls.kt
- **Acceptance**: Reader reads text aloud with highlight tracking
- **Blockers**: TTS engine availability on device
- **Exit tag**: `ANDROID_S10_TTS_READY`

### S11: WebDAV Backup
- **Goal**: Backup/restore to WebDAV server
- **Scope**: WebDAV client, WorkManager scheduling, backup/restore UI, retention policy
- **Non-goals**: Progress sync (S12), remote reading (S13)
- **Required files**: app/src/main/java/.../adapter/WebDAVAdapter.kt, ui/backup/*.kt
- **Acceptance**: App backs up and restores to WebDAV server
- **Blockers**: WebDAV library selection
- **Exit tag**: `ANDROID_S11_WEBDAV_BACKUP_READY`

### S12: Progress Cloud Sync
- **Goal**: Reading progress syncs across devices via WebDAV
- **Scope**: Sync trigger, conflict resolution, progress merge, transport layer
- **Non-goals**: Real-time sync, collaborative reading
- **Required files**: app/src/main/java/.../data/SyncManager.kt, data/ConflictResolver.kt
- **Acceptance**: Progress from device A appears on device B
- **Blockers**: None (uses S11 WebDAV adapter)
- **Exit tag**: `ANDROID_S12_SYNC_READY`

### S13: Remote WebDAV Books
- **Goal**: Browse and read books directly from WebDAV server
- **Scope**: WebDAV file browser, remote book reader, streaming chapter fetch
- **Non-goals**: DRM, cloud-only book sources
- **Required files**: app/src/main/java/.../ui/remote/*.kt
- **Acceptance**: User browses WebDAV, opens remote book, reads chapters
- **Blockers**: None (uses S11 WebDAV adapter)
- **Exit tag**: `ANDROID_S13_REMOTE_READY`

### S14: Compatibility Matrix and Regression
- **Goal**: Compatibility tracking UI, automated regression test suite
- **Scope**: Source compatibility matrix UI, UI tests, integration tests, health diagnostics
- **Non-goals**: Full CI/CD pipeline
- **Required files**: app/src/test/**, app/src/androidTest/**, docs/COMPATIBILITY_REPORT.md
- **Acceptance**: Test suite passes, compatibility matrix renders
- **Blockers**: Test device/emulator availability
- **Exit tag**: `ANDROID_S14_COMPAT_READY`

### S15: Legado Mainstream Capability Release Candidate
- **Goal**: Feature-complete vs Legado mainstream capabilities, release-ready
- **Scope**: Final integration, polish, performance, accessibility, release build
- **Non-goals**: Feature parity with every Legado edge case
- **Required files**: All production code + tests + release config
- **Acceptance**: All S0-S14 acceptance criteria met, release APK builds
- **Blockers**: None expected at this stage
- **Exit tag**: `ANDROID_S15_RC_READY`

---

## 4. Architecture Constraints (Clean Room)

1. **No Legado source code** in this repository
2. **No Reader-Core internal access** (Parser internals, Runtime internals)
3. **Only Reader-Core public API** via Facade/DTO/Error taxonomy
4. **No rewriting Reader-Core** in Kotlin
5. **Android-specific code only**: UI, platform adapters, local persistence, Android SDK integration
6. **Core gaps must be recorded**, not bypassed

---

## 5. Dependency Strategy

| Dependency | Stage | Decision Status |
|------------|-------|----------------|
| Compose vs XML | S1 | USER_DECISION (default: Compose) |
| Kotlin + Coroutines | S1 | Implicit (Android standard) |
| Room | S2 | USER_DECISION (design doc first) |
| DataStore | S2 | USER_DECISION (design doc first) |
| OkHttp | S3 | USER_DECISION (deferred to S3) |
| QuickJS / Hermes | S7 | USER_DECISION (deferred to S7) |
| WebDAV client lib | S11 | USER_DECISION (deferred to S11) |
| EPUB parser lib | S9 | USER_DECISION (deferred to S9) |
