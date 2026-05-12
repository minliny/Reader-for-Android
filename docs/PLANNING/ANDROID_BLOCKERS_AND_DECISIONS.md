# Reader for Android Blockers and Decisions

**Date**: 2026-05-13
**Purpose**: Pre-identified blocking points and user decision questions. Loop must consult this file before each iteration.

---

## Pre-identified Blockers / Decisions

| ID | Type | Severity | Area | Question / Blocker | Default Safe Action | Blocks Tasks | Status |
|----|------|----------|------|--------------------|---------------------|-------------|--------|
| BD-001 | USER_DECISION | P0 | Project Identity | Android package name (applicationId) | Use `com.reader.android` as temporary namespace; record as needing confirmation before release | P0-S1-001, all subsequent code tasks | OPEN |
| BD-002 | USER_DECISION | P0 | Project Identity | minSdk / targetSdk / compileSdk versions | Use minSdk=26, targetSdk=35, compileSdk=35 (current stable defaults) | P0-S1-001 | OPEN |
| BD-003 | USER_DECISION | P0 | Architecture | Compose vs XML UI toolkit | Default to Jetpack Compose (modern Android standard) | P0-S1-001, all UI tasks | OPEN |
| BD-004 | USER_DECISION | P1 | Architecture | Single module vs multi-module project | Default to single app module; multi-module can be introduced later | P0-S1-001 | OPEN |
| BD-005 | USER_DECISION | P1 | Storage | Room vs DataStore for local persistence | Write DATA_LAYER_DESIGN.md first (P1-S2-002); do not introduce dependency until doc approved | P1-S2-001 through P2-S6-* | OPEN |
| BD-006 | USER_DECISION | P1 | Networking | OkHttp vs kTor for HTTP client | Write HTTP_ADAPTER_DESIGN.md first (P2-S3-003-DOC); do not introduce dependency until doc approved | P2-S3-003, P2-S5-* | OPEN |
| BD-007 | USER_DECISION | P0 | Core Integration | How to bridge Swift Reader-Core to Kotlin Android (JSON contract vs KMP vs embedded HTTP vs other) | Default to JSON-level contract: define equivalent Kotlin DTOs, implement protocols natively, validate against Core conformance tests; record as BLOCKED_CORE_INTEGRATION_STRATEGY | P1-S3-*, all P2 tasks | OPEN |
| BD-008 | USER_DECISION | P1 | Security | Allow network access for real HTTP fetch | Default to NO network access until explicitly approved; fake data only for early stages | P2-S3-003, P2-S5-* | OPEN |
| BD-009 | USER_DECISION | P2 | Dynamic Runtime | QuickJS vs Hermes for JS engine | Deferred to S7; write design doc first (P3-S7-001-DOC) | P3-S7-003 | OPEN |
| BD-010 | USER_DECISION | P2 | WebDAV | WebDAV client library selection | Deferred to S11; write design doc first (P3-S11-001-DOC) | P3-S11-* | OPEN |
| BD-011 | USER_DECISION | P2 | Local Books | EPUB parser library selection | Deferred to S9; no decision needed now | P3-S9-002 | OPEN |
| BD-012 | USER_DECISION | P1 | Automation | Allow automatic git commit by loop | Default: allow local commit only; NEVER push; NEVER force push; NEVER amend | All loop tasks | OPEN |
| BD-013 | USER_DECISION | P1 | Automation | Allow automatic Gradle file creation/modification | Allow for S0-S1 project setup; disallow for adding new dependencies without user approval | P0-S1-001, P0-S1-002 | OPEN |
| BD-014 | USER_DECISION | P1 | Security | Allow reading local files (for local book feature) | Deferred to S9; not needed before | P3-S9-* | OPEN |
| BD-015 | USER_DECISION | P2 | Release | Open source release vs Play Store compliance strategy | Not blocking development; record as deferred | None (no current tasks) | OPEN |
| BD-016 | USER_DECISION | P1 | Security | Handling of cookies, tokens, credentials | No credential storage until S7 login adapter; never commit secrets; never log credentials | P3-S7-004 | OPEN |
| BD-017 | ENVIRONMENT | P0 | Build | Android SDK not verified on this machine | Mark P0-S1-001 as BLOCKED_ENV_ANDROID_SDK until SDK confirmed; planning docs can proceed without SDK | P0-S1-001, all Gradle-based tasks | OPEN |
| BD-018 | ENVIRONMENT | P1 | Build | Gradle wrapper not yet generated | Can be created by P0-S1-001 if Android SDK available; otherwise BLOCKED | P0-S1-001 | OPEN |
| BD-019 | ARCHITECTURE | P0 | Boundary | Android must not copy Legado source code | Gate: grep for Legado package names before commit | All tasks | OPEN (ongoing) |
| BD-020 | ARCHITECTURE | P0 | Boundary | Android must not rewrite Reader-Core internals | Gate: no parser/runtime internal reimplementation in Android code | All P2+ tasks | OPEN (ongoing) |
| BD-021 | ARCHITECTURE | P0 | Boundary | Android only accesses Reader-Core via public API/Facade/DTO/Error taxonomy | Gate: review imports before Core-bridge tasks | P1-S3-*, all P2 tasks | OPEN (ongoing) |
| BD-022 | CORE_GAP | P0 | Upstream | Reader-Core is Swift, Android is Kotlin — no direct binary integration possible | Record as known architectural constraint; do not attempt direct Swift→Android linking | P1-S3-*, P2-S3-* | OPEN |
| BD-023 | CORE_GAP | P1 | Upstream | Reader-Core has no Kotlin/Maven artifact | Android must implement equivalent DTOs and protocols natively in Kotlin | P1-S2-* | OPEN |
| BD-024 | CORE_GAP | P2 | Upstream | Reader-Core EXPERIMENTAL modules (Network, Cache, Adapters, JSRenderer) are not stable | Can reference contracts but should not depend on implementation stability | P2-S3-*, P2-S6-* | OPEN |
| BD-025 | USER_DECISION | P1 | Automation | Whether to allow loop to introduce new dependencies (Room, OkHttp, etc.) | Default: only write design docs; do NOT add dependencies without user approval after doc review | P1-S2-* (for Room/DataStore), P2-S3-* (for OkHttp) | OPEN |
| BD-026 | USER_DECISION | P2 | Feature | Whether to support WebView for JS-based book sources | Default: plan for it in design docs but do not implement until S7 | P3-S7-* | OPEN |
| BD-027 | USER_DECISION | P2 | Feature | Whether to support WebDAV backup/sync | Default: plan for it in design docs but do not implement until S11 | P3-S11-* | OPEN |

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
