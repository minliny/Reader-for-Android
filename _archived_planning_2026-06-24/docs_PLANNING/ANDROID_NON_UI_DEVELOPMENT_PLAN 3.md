# Reader for Android Non-UI Development Plan

**Date**: 2026-05-14
**Commit**: efddb2b

## 1. Mission

在不开发 UI 前端的前提下，持续推进 Reader for Android 的 backend、adapter、parser、storage、sync、contract、test 能力。

## 2. Scope

**Allowed**: domain/model, parser, bridge, repository, storage, cache, adapter, import/export, sync, protocol client, fixture, test, contract docs, capability matrix, queue/loop config

**Forbidden**: Compose UI, new pages, buttons, visual styles, frontend interaction, navigation UI changes, cron, Reader-Core modifications, network-based test fixtures

## 3. Current Baseline

| Metric | Value |
|--------|-------|
| Commit | efddb2b |
| Tests | 18 (0 failures) |
| `./gradlew test` | BUILD SUCCESSFUL |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| S1-S6 | DONE |
| S6.5 | 2/7 DONE (001, 002) |

## 4. Non-UI Task Roadmap

---

### S6.5 Baseline Hardening (IN PROGRESS)

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S6.5-P0-001 | Baseline matrix freeze | DONE | docs |
| S6.5-P0-002 | Parser contract tests | DONE | tests |
| S6.5-P0-003 | Bridge contract tests | READY | ReaderCoreBridge, FakeCoreBridge, BridgeResult, error codes |
| S6.5-P0-004 | Repository + preferences persistence tests | TODO | BookSourceRepository, ThemePreferences, DataStore |
| S6.5-P0-005 | Room + cache tests | TODO | ReadingProgress DAO, CachedChapter, ChapterCache TTL |
| S6.5-P0-006 | Navigation route contract hardening | TODO | Route constants, URL encode/decode |
| S6.5-P0-007 | Fake/real mode boundary freeze | TODO | Document + verify useRealHttp flag |

---

### S7 Non-UI WebView/JS/Cookie Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S7-NUI-P0-001 | Web runtime adapter contract | TODO | WebRuntimeAdapter interface, FakeWebRuntimeAdapter, tests |
| S7-NUI-P0-002 | JS execution contract tests | TODO | JS request/response/error model, fake adapter tests |
| S7-NUI-P0-003 | Cookie scope and storage contract | TODO | CookieScope, CookieRecord, CookieStore model, Room schema, tests |
| S7-NUI-P0-004 | Dynamic source capability matrix | TODO | Capability doc: JS, Cookie, POST, WebView HTML snapshot |

---

### S8 Non-UI Explore/RSS Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S8-NUI-P0-001 | RSS model and parser | TODO | RSSFeed, RSSItem, RSSSubscription, XML parser, tests |
| S8-NUI-P0-002 | Subscription update engine | TODO | Repository contract, update timestamp, fake HTTP client, tests |
| S8-NUI-P0-003 | Explore source contract | TODO | ExploreSource model, category/feed mapping, parser contract, tests |

---

### S9 Non-UI Local Book Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S9-NUI-P0-001 | Local book import contract | TODO | LocalBookSource, LocalBookMetadata, URI abstraction, tests |
| S9-NUI-P0-002 | TXT parser baseline | TODO | Encoding strategy, chapter split, title detection, fixtures, tests |
| S9-NUI-P0-003 | EPUB inventory contract | TODO | ZIP/XML manifest inventory, metadata extraction, synthetic fixture tests |
| S9-NUI-P0-004 | Local book progress/cache integration | TODO | Map local chapters to ReadingProgress/ChapterCache, tests |

---

### S10 Non-UI TTS Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S10-NUI-P0-001 | TTS engine contract | TODO | TtsEngine interface, TtsUtterance, TtsPlaybackState, FakeTtsEngine, tests |
| S10-NUI-P0-002 | Android TTS adapter boundary | TODO | Platform adapter contract, no UI, tests |

---

### S11 Non-UI WebDAV Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S11-NUI-P0-001 | WebDAV client contract | TODO | WebDavClient interface, PROPFIND/GET/PUT models, fake transport, tests |
| S11-NUI-P0-002 | Backup package model | TODO | BackupManifest, BackupEntry, checksum/version, serialization tests |
| S11-NUI-P0-003 | Progress sync protocol | TODO | ProgressSyncRecord, conflict resolution policy, merge tests |
| S11-NUI-P0-004 | Remote WebDAV books contract | TODO | RemoteBookRef, download/cache policy, tests |

---

### S12 Non-UI Cloud Sync Core

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S12-NUI-P0-001 | Sync manager state machine | TODO | SyncState, SyncOperation, retry/backoff, tests |
| S12-NUI-P0-002 | Conflict resolver | TODO | newer-wins, explicit conflict, mergeable progress, tests |

---

### S13 Non-UI Remote Reading Backend

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S13-NUI-P0-001 | Remote content provider contract | TODO | RemoteContentProvider, streaming/download mode, cache key, tests |
| S13-NUI-P0-002 | Offline availability manager | TODO | Cache manifest, eviction policy, tests |

---

### S14 Non-UI Compatibility Matrix and Regression Infra

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S14-NUI-P0-001 | Legado capability matrix model | TODO | Capability enum/model, ownership, status fields, tests |
| S14-NUI-P0-002 | Regression fixture registry | TODO | Fixture metadata, local-only policy, parser/source registry, tests |
| S14-NUI-P0-003 | Non-UI release gate checklist | TODO | test/build/doc checklist |

---

### S15 Release Candidate Preparation

| ID | Task | Status | Scope |
|----|------|--------|-------|
| S15-NUI-P0-001 | Non-UI RC gate | TODO | All NUI tasks complete, tests pass, assembleDebug pass |

## 5. Task Dependencies

```
S6.5-P0-003~007 (hardening) → S7-NUI → S8-NUI → S9-NUI → S10-NUI → S11-NUI → S12-NUI → S13-NUI → S14-NUI → S15-NUI
```

- S6.5 must complete before any S7+ task.
- Within each stage, tasks execute in order.
- All tasks use fake transports/adapters unless a real adapter is required by the contract.
- No task may depend on UI work.

## 6. Exit Criteria

- S6.5 all DONE
- S7-NUI through S14-NUI all DONE
- `./gradlew test` passes (≥ 50 tests expected)
- `./gradlew :app:assembleDebug` passes
- No backend P0 blockers
- docs/PLANNING capability matrix updated
