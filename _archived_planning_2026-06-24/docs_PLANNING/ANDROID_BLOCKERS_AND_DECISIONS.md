# Reader for Android Blockers and Decisions

**Date**: 2026-05-13
**Purpose**: Pre-identified blocking points and user decision questions. Loop must consult this file before each iteration.

---

## Pre-identified Blockers / Decisions

| ID | Type | Severity | Area | Question / Blocker | Default Safe Action | Blocks Tasks | Status |
|----|------|----------|------|--------------------|---------------------|-------------|--------|
| BD-001 | USER_DECISION | P0 | Project Identity | Android package name (applicationId) | RESOLVED: `com.reader.android` | P0-S1-001, all subsequent code tasks | RESOLVED (2026-05-13) |
| BD-002 | USER_DECISION | P0 | Project Identity | minSdk / targetSdk / compileSdk versions | RESOLVED: minSdk=26, targetSdk=35, compileSdk=35 | P0-S1-001 | RESOLVED (2026-05-13) |
| BD-003 | USER_DECISION | P0 | Architecture | Compose vs XML UI toolkit | RESOLVED: Jetpack Compose + Material 3 (modern Android standard) | P0-S1-001, all UI tasks | RESOLVED (2026-05-13) |
| BD-004 | USER_DECISION | P1 | Architecture | Single module vs multi-module project | RESOLVED: Single `:app` module with internal package layering; multi-module evaluation deferred to S3/S4 | P0-S1-001 | RESOLVED (2026-05-13) |
| BD-005 | USER_DECISION | P1 | Storage | Room vs DataStore for local persistence | RESOLVED: Room for structured data (sources, bookshelf, chapters, progress, cache), DataStore for preferences (theme, reading settings) | P1-S2-001 through P2-S6-* | RESOLVED (2026-05-13) |
| BD-006 | USER_DECISION | P1 | Networking | OkHttp vs kTor for HTTP client | RESOLVED: OkHttp | P2-S3-003, P2-S5-* | RESOLVED (2026-05-13) |
| BD-007 | USER_DECISION | P0 | Core Integration | How to bridge Swift Reader-Core to Kotlin Android | RESOLVED: JSON-level contract / bridge boundary — Android defines equivalent Kotlin DTOs, implements protocols natively, validates against Core conformance tests; no direct Swift↔Kotlin linking | P1-S3-*, all P2 tasks | RESOLVED (2026-05-13) |
| BD-008 | USER_DECISION | P1 | Security | Allow network access for real HTTP fetch | RESOLVED: Network access granted (2026-05-14) | P2-S3-003, P2-S5-* | RESOLVED (2026-05-14) |
| BD-009 | USER_DECISION | P2 | Dynamic Runtime | QuickJS vs Hermes for JS engine | UI: BLOCKED. Non-UI backend: DEFAULT_APPROVED (WebView-first adapter contract, fake impl, tests). QuickJS deferred | S7-NUI-P0-001/002 | SPLIT (NUI OK) |
| BD-010 | USER_DECISION | P2 | WebDAV | WebDAV client library selection | UI: BLOCKED. Non-UI backend: DEFAULT_APPROVED_FOR_CONTRACT (OkHttp-based client contract, fake transport, protocol models, tests) | S11-NUI-P0-* | SPLIT (NUI OK) |
| BD-011 | USER_DECISION | P2 | Local Books | EPUB parser library selection | UI: BLOCKED. Non-UI backend: DEFAULT_APPROVED_FOR_INVENTORY (TXT parser first, EPUB ZIP/XML inventory contract, synthetic fixture tests) | S9-NUI-P0-003 | SPLIT (NUI OK) |
| BD-012 | USER_DECISION | P1 | Automation | Allow automatic git commit by loop | RESOLVED: Local commit allowed; NEVER push without explicit user request | All loop tasks | RESOLVED (2026-05-14) |
| BD-013 | USER_DECISION | P1 | Automation | Allow automatic Gradle file creation/modification | RESOLVED: Allowed with dependency justification in report (per BD-025) | P0-S1-001, P0-S1-002 | RESOLVED (2026-05-14) |
| BD-014 | USER_DECISION | P1 | Security | Allow reading local files (for local book feature) | UI: BLOCKED. Non-UI backend: DEFAULT_APPROVED (SAF URI abstraction, LocalBookSource model, no filesystem scan) | S9-NUI-P0-001 | SPLIT (NUI OK) |
| BD-015 | USER_DECISION | P2 | Release | Open source release vs Play Store compliance strategy | Not blocking development; record as deferred | None (no current tasks) | OPEN |
| BD-016 | USER_DECISION | P1 | Security | Handling of cookies, tokens, credentials | UI: BLOCKED. Non-UI backend: DEFAULT_APPROVED (CookieScope/CookieRecord/CookieStore model, Room schema, tests; no login UI) | S7-NUI-P0-003 | SPLIT (NUI OK) |
| BD-017 | ENVIRONMENT | P0 | Build | Android SDK not verified on this machine | RESOLVED: JDK 17.0.19, Android SDK 35, build-tools 35.0.0, Gradle 8.11.1 all verified | P0-S1-001, all Gradle-based tasks | RESOLVED (2026-05-14) |
| BD-018 | ENVIRONMENT | P1 | Build | Gradle wrapper not yet generated | RESOLVED: Gradle 8.11.1 wrapper generated, ./gradlew verified | P0-S1-001 | RESOLVED (2026-05-14) |
| BD-019 | ARCHITECTURE | P0 | Boundary | Android must not copy Legado source code | Gate: grep for Legado package names before commit | All tasks | OPEN (ongoing) |
| BD-020 | ARCHITECTURE | P0 | Boundary | Android must not rewrite Reader-Core internals | Gate: no parser/runtime internal reimplementation in Android code | All P2+ tasks | OPEN (ongoing) |
| BD-021 | ARCHITECTURE | P0 | Boundary | Android only accesses Reader-Core via public API/Facade/DTO/Error taxonomy | Gate: review imports before Core-bridge tasks | P1-S3-*, all P2 tasks | OPEN (ongoing) |
| BD-022 | CORE_GAP | P0 | Upstream | Reader-Core is Swift, Android is Kotlin — no direct binary integration possible | Record as known architectural constraint; do not attempt direct Swift→Android linking | P1-S3-*, P2-S3-* | OPEN |
| BD-023 | CORE_GAP | P1 | Upstream | Reader-Core has no Kotlin/Maven artifact | Android must implement equivalent DTOs and protocols natively in Kotlin | P1-S2-* | OPEN |
| BD-024 | CORE_GAP | P2 | Upstream | Reader-Core EXPERIMENTAL modules (Network, Cache, Adapters, JSRenderer) are not stable | Can reference contracts but should not depend on implementation stability | P2-S3-*, P2-S6-* | OPEN |
| BD-025 | USER_DECISION | P1 | Automation | Whether to allow loop to introduce new dependencies (Room, OkHttp, etc.) | RESOLVED: Allowed for Android official/mainstream dependencies with minimal scope; each new dependency must be justified in report; design docs no longer gate dependency introduction | P1-S2-* (for Room/DataStore), P2-S3-* (for OkHttp) | RESOLVED (2026-05-13) |
| BD-026 | USER_DECISION | P2 | Feature | Whether to support WebView for JS-based book sources | Default: plan for it in design docs but do not implement until S7 | P3-S7-* | OPEN |
| BD-027 | USER_DECISION | P2 | Feature | Whether to support WebDAV backup/sync | Default: plan for it in design docs but do not implement until S11 | P3-S11-* | OPEN |
| BD-028 | USER_DECISION | P2 | Dependency | Whether to allow EPUB third-party library | DEFAULT: use java.util.zip (built-in) for ZIP + XmlPullParser (built-in) for OPF XML; no third-party EPUB lib | S9-NUI-P0-003, S9-NUI-P0-007, S9-NUI-P0-008 | OPEN (NUI OK with built-ins) |
| BD-029 | USER_DECISION | P2 | Dependency | Whether to allow XML parser library | DEFAULT: use Android built-in XmlPullParser; sufficient for RSS, OPF, WebDAV MultiStatus XML | S8-NUI-P0-001, S9-NUI-P0-007, S11-NUI-P0-006 | OPEN (NUI OK with built-ins) |
| BD-030 | USER_DECISION | P2 | Security | Whether to allow encrypted credential storage | DEFAULT: defer encrypted storage; CookieStore uses DataStore with no encryption for now; encrypt later | S7-NUI-P0-003, S11-NUI-P0-005 | OPEN (NUI OK without encryption) |
| BD-031 | USER_DECISION | P2 | Test | Whether to allow Android instrumentation tests (require emulator) | DEFAULT: instrumentation tests deferred; all current and planned tests are pure JUnit unit tests | S14-NUI-P0-002, S15-NUI-P0-003 | OPEN (NUI OK with unit tests only) |

---

## Block Preflight Checklist (run before each loop iteration)

1. **Git state check**: `git status --short` — if dirty from previous failed loop, assess and record
2. **P0 OPEN blockers**: scan this file for P0 OPEN blockers that affect current READY tasks
3. **Core path**: check if Reader-Core path exists at `/Users/minliny/Documents/Reader-Core`
4. **Android SDK**: check `echo $ANDROID_HOME` or `which sdkmanager` — if missing, Gradle tasks are BLOCKED_ENV_ANDROID_SDK
5. **User decision pending**: if a READY task requires a user decision marked OPEN in this file, skip it
6. **New dependency check**: if task would add a dependency (Room, OkHttp, kTor, etc.), verify it's allowed
7. **Network check**: if task requires network, verify BLOCKED_NETWORK_DECISION is resolved
8. **Sensitive data**: never read .env, never commit secrets, never log credentials

---

## Resolution Log

| Date | ID | Resolution | Resolved By |
|------|----|------------|-------------|
| 2026-05-13 | BD-017 | ANDROID_SDK not yet verified; Gradle tasks blocked | Audit |
| 2026-05-13 | BD-018 | Gradle wrapper not generated | Audit |
| 2026-05-13 | BD-012 | Auto-commit enabled for planning docs; first commit created (6a5da26) | Loop iteration 1 |
| 2026-05-13 | BD-001 | RESOLVED: applicationId = com.reader.android | User decision (S1 skeleton) |
| 2026-05-13 | BD-002 | RESOLVED: minSdk=26, targetSdk=35, compileSdk=35 | User decision (S1 skeleton) |
| 2026-05-13 | BD-003 | RESOLVED: Jetpack Compose + Material 3 | User decision (S1 skeleton) |
| 2026-05-13 | BD-004 | RESOLVED: Single :app module, package layering; multi-module deferred to S3/S4 | User decision (S1 skeleton) |
| 2026-05-13 | BD-005 | RESOLVED: Room + DataStore | User decision (S1 skeleton) |
| 2026-05-13 | BD-006 | RESOLVED: OkHttp | User decision (S1 skeleton) |
| 2026-05-13 | BD-007 | RESOLVED: JSON-level contract / bridge boundary | User decision (S1 skeleton) |
| 2026-05-13 | BD-025 | RESOLVED: Dependencies allowed with minimal scope + justification | User decision (S1 skeleton) |
| 2026-05-14 | BD-017 | RESOLVED: JDK 17.0.19, Android SDK 35, build-tools 35.0.0, Gradle 8.11.1 verified | S1-P0-002 env verification |
| 2026-05-14 | BD-018 | RESOLVED: Gradle wrapper 8.11.1 generated, ./gradlew verified | S1-P0-002 env verification |
