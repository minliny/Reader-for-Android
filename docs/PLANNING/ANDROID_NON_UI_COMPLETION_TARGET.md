# Reader for Android Non-UI Completion Target

**Date**: 2026-05-15
**Purpose**: Define what "all non-UI capabilities complete" means. Every `ui_required=no` item must have a corresponding loop task.

## Capability Matrix

Legend:
- `ui_required`: `yes` = cannot be done without UI changes; `no` = purely backend
- `current_status`: `DONE` / `PARTIAL` / `TODO` / `BLOCKED`
- `included_in_loop`: whether a queue task currently exists

---

### A. Foundation Engineering & Quality Gate

| ID | Capability | Ownership | UI? | Status | Files | Loop Task | Gap |
|----|-----------|-----------|-----|--------|-------|-----------|-----|
| A-01 | Gradle project + :app module | app | no | DONE | build.gradle.kts, settings.gradle.kts | S1 DONE | - |
| A-02 | Compose shell (no feature UI) | app | no | DONE | MainActivity, ReaderAndroidApp | S1 DONE | - |
| A-03 | `./gradlew test` baseline | test | no | DONE | 28 tests, 0 failures | S6.5 ongoing | - |
| A-04 | `./gradlew :app:assembleDebug` baseline | app | no | DONE | - | S1 DONE | - |
| A-05 | Fixture-only test policy | docs | no | TODO | - | **GAP** | Need A-05-NUI |
| A-06 | No-network test rule | docs | no | TODO | - | **GAP** | Need A-06-NUI |
| A-07 | No-UI loop rule | docs | no | DONE | .claude/commands/loop.md | DONE | - |
| A-08 | Release gate checklist | docs | no | TODO | - | S15-NUI-P0-001 | - |

**Gaps to add**: A-05-NUI (fixture policy document), A-06-NUI (no-network test rule doc)

---

### B. Domain / DTO / Error Model

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| B-01 | BookInfo DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-02 | BookSource DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-03 | SearchQuery DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-04 | SearchResultItem DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-05 | TOCItem DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-06 | ContentPage DTO | no | DONE | DomainModels.kt | S2 DONE |
| B-07 | ReadingProgress entity | no | DONE | ReadingProgress.kt | S6 DONE |
| B-08 | CachedChapter entity | no | DONE | ChapterCache.kt | S6 DONE |
| B-09 | ReaderErrorCode enum | no | DONE | ReaderCoreBridge.kt | S3 DONE |
| B-10 | ReaderFailureStage enum | no | DONE | ReaderCoreBridge.kt | S3 DONE |
| B-11 | BridgeResult sealed class | no | DONE | ReaderCoreBridge.kt | S3 DONE |
| B-12 | Sync error model | no | TODO | - | S12-NUI-P0-001 |
| B-13 | WebDAV error model | no | TODO | - | S11-NUI-P0-001 |
| B-14 | Local book error model | no | TODO | - | S9-NUI-P0-001 |
| B-15 | TTS error model | no | TODO | - | S10-NUI-P0-001 |
| B-16 | POST/API request model | no | TODO | - | **GAP** S7-NUI-P0-005 |
| B-17 | Pagination/next-page model | no | TODO | - | **GAP** S7-NUI-P0-007 |
| B-18 | Source validation result model | no | TODO | - | **GAP** S7-NUI-P0-008 |

**Gaps to add**: B-16, B-17, B-18 need new tasks

---

### C. Book Source / Parser Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| C-01 | Source import JSON | no | DONE | BookSourceRepository.kt | S4 DONE |
| C-02 | Source export JSON | no | DONE | DataStoreBookSourceRepository.kt | S3 DONE |
| C-03 | Source enable/disable/delete repo | no | DONE | BookSourceRepository.kt | S4 DONE |
| C-04 | SearchParser | no | DONE | SearchParser.kt + test | S5 DONE + S6.5-P0-002 |
| C-05 | BookInfoParser | no | DONE | BookInfoParser.kt + test | S5 DONE + S6.5-P0-002 |
| C-06 | TOCParser | no | DONE | TOCParser.kt + test | S5 DONE + S6.5-P0-002 |
| C-07 | ContentParser | no | DONE | ContentParser.kt + test | S5 DONE + S6.5-P0-002 |
| C-08 | HttpClient (OkHttp) | no | DONE | HttpClient.kt | S3 DONE |
| C-09 | Request headers/user-agent injection | no | TODO | - | **GAP** S7-NUI-P0-006 |
| C-10 | Cookie injection in requests | no | TODO | - | S7-NUI-P0-003 (partial) |
| C-11 | Redirect handling | no | PARTIAL | HttpClient.kt (followRedirects=true) | Covered |
| C-12 | Encoding handling | no | TODO | - | **GAP** S9-NUI-P0-005 (TXT), partial in parsers |
| C-13 | Parser fixture registry | no | TODO | - | **GAP** S14-NUI-P0-002 (partial) |
| C-14 | Parser conformance test registry | no | TODO | - | S14-NUI-P0-002 |

**Gaps to add**: C-09, C-13 dedicated task

---

### D. Dynamic Source Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| D-01 | WebRuntimeAdapter contract | no | TODO | - | S7-NUI-P0-001 |
| D-02 | FakeWebRuntimeAdapter | no | TODO | - | S7-NUI-P0-001 |
| D-03 | JS evaluation request/response model | no | TODO | - | S7-NUI-P0-002 |
| D-04 | JS error model | no | TODO | - | S7-NUI-P0-002 |
| D-05 | CookieScope model | no | TODO | - | S7-NUI-P0-003 |
| D-06 | CookieRecord model | no | TODO | - | S7-NUI-P0-003 |
| D-07 | CookieStore contract | no | TODO | - | S7-NUI-P0-003 |
| D-08 | Per-source cookie isolation | no | TODO | - | S7-NUI-P0-003 |
| D-09 | Dynamic source capability matrix doc | no | TODO | - | S7-NUI-P0-004 |
| D-10 | Web runtime error mapping | no | TODO | - | **GAP** S7-NUI-P0-010 |
| D-11 | Dynamic source offline replay contract | no | TODO | - | **GAP** S7-NUI-P0-011 |
| D-12 | Per-source runtime isolation | no | TODO | - | **GAP** S7-NUI-P0-012 |

**Gaps to add**: D-10, D-11, D-12

---

### E. Explore / RSS Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| E-01 | RSSFeed model | no | TODO | - | S8-NUI-P0-001 |
| E-02 | RSSItem model | no | TODO | - | S8-NUI-P0-001 |
| E-03 | RSSSubscription model | no | TODO | - | S8-NUI-P0-001 |
| E-04 | RSS XML parser | no | TODO | - | S8-NUI-P0-001 |
| E-05 | Subscription update engine | no | TODO | - | S8-NUI-P0-002 |
| E-06 | ExploreSource backend model | no | TODO | - | S8-NUI-P0-003 |
| E-07 | Category/feed mapping | no | TODO | - | S8-NUI-P0-003 |

**Covered**: All E-01 through E-07 have tasks. No gaps.

---

### F. Local Book Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| F-01 | LocalBookSource model | no | TODO | - | S9-NUI-P0-001 |
| F-02 | LocalBookMetadata model | no | TODO | - | S9-NUI-P0-001 |
| F-03 | LocalBookImportResult model | no | TODO | - | S9-NUI-P0-001 |
| F-04 | URI abstraction (SAF) | no | TODO | - | S9-NUI-P0-001 |
| F-05 | TXT parser | no | TODO | - | S9-NUI-P0-002 |
| F-06 | TXT encoding strategy/detection | no | TODO | - | **GAP** S9-NUI-P0-005 |
| F-07 | TXT chapter split rules | no | TODO | - | S9-NUI-P0-002 (partial) |
| F-08 | TXT chapter split edge cases | no | TODO | - | **GAP** S9-NUI-P0-006 |
| F-09 | TXT metadata inference | no | TODO | - | S9-NUI-P0-002 |
| F-10 | EPUB ZIP inventory | no | TODO | - | S9-NUI-P0-003 |
| F-11 | EPUB OPF metadata parser | no | TODO | - | **GAP** S9-NUI-P0-007 |
| F-12 | EPUB spine/manifest parser | no | TODO | - | **GAP** S9-NUI-P0-008 |
| F-13 | EPUB chapter extraction contract | no | TODO | - | S9-NUI-P0-003 (partial) |
| F-14 | Local book progress mapping | no | TODO | - | S9-NUI-P0-004 |
| F-15 | Local book cache mapping | no | TODO | - | S9-NUI-P0-004 |
| F-16 | Local book reimport/delete semantics | no | TODO | - | **GAP** S9-NUI-P0-009 |
| F-17 | Local book cache/progress compat tests | no | TODO | - | **GAP** S9-NUI-P0-010 |

**Gaps to add**: F-06, F-08, F-11, F-12, F-16, F-17

---

### G. Reader / Cache / Progress Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| G-01 | ReadingProgress DAO | no | DONE | ReadingProgress.kt | S6 DONE |
| G-02 | Progress save/load | no | DONE | ReadingProgress.kt | S6 DONE |
| G-03 | Progress merge policy | no | TODO | - | S12-NUI-P0-002 |
| G-04 | ChapterCache DAO | no | DONE | ChapterCache.kt | S6 DONE |
| G-05 | Cache TTL eviction | no | DONE | ChapterCache.kt | S6 DONE |
| G-06 | Cache invalidation policy | no | PARTIAL | ChapterCache.kt | S6.5-P0-005 (test gap) |
| G-07 | Cache key strategy doc | no | TODO | - | **GAP** S13-NUI-P0-001 (partial) |
| G-08 | Next/previous chapter contract | no | PARTIAL | ReaderScreen.kt (nextPageUrl) | Covered |
| G-09 | Content prefetch contract | no | TODO | - | **GAP** S6-NUI-CACHE-001 |

**Gaps to add**: G-07 (cache key doc), G-09 (prefetch contract)

---

### H. Settings / Theme Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| H-01 | DataStore settings persistence | no | DONE | ThemePreferences.kt | S6 DONE |
| H-02 | Dark mode flag | no | DONE | ThemePreferences.kt | S6 DONE |
| H-03 | Font size setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-04 | Line spacing setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-05 | Page margin setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-06 | Theme model (day/night pair) | no | TODO | - | **GAP** S6-NUI-SET-001 |
| H-07 | Text/background/accent color model | no | TODO | - | **GAP** S6-NUI-SET-002 |
| H-08 | Background transparency model | no | TODO | - | **GAP** S6-NUI-SET-003 |
| H-09 | Click area config model | no | TODO | - | **GAP** S6-NUI-SET-004 |
| H-10 | Font family/typeface config | no | TODO | - | **GAP** S6-NUI-SET-005 |

**Gaps to add**: H-06 through H-10 (5 new tasks). These are purely backend config models, not UI.

---

### I. TTS Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| I-01 | TtsEngine contract | no | TODO | - | S10-NUI-P0-001 |
| I-02 | TtsUtterance model | no | TODO | - | S10-NUI-P0-001 |
| I-03 | TtsPlaybackState model | no | TODO | - | S10-NUI-P0-001 |
| I-04 | FakeTtsEngine | no | TODO | - | S10-NUI-P0-001 |
| I-05 | AndroidTtsAdapter boundary | no | TODO | - | S10-NUI-P0-002 |
| I-06 | TTS queue model | no | TODO | - | **GAP** S10-NUI-P0-003 |
| I-07 | TTS chapter integration contract | no | TODO | - | **GAP** S10-NUI-P0-004 |
| I-08 | TTS error mapping | no | TODO | - | **GAP** S10-NUI-P0-005 |
| I-09 | Pause/resume/stop state machine | no | TODO | - | S10-NUI-P0-001 (partial) |

**Gaps to add**: I-06, I-07, I-08

---

### J. WebDAV / Backup Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| J-01 | WebDavClient contract | no | TODO | - | S11-NUI-P0-001 |
| J-02 | PROPFIND model | no | TODO | - | S11-NUI-P0-001 |
| J-03 | GET/PUT/DELETE models | no | TODO | - | S11-NUI-P0-001 |
| J-04 | Fake transport | no | TODO | - | S11-NUI-P0-001 |
| J-05 | Auth model (Basic/Digest/Bearer) | no | TODO | - | **GAP** S11-NUI-P0-005 |
| J-06 | WebDAV XML response parser | no | TODO | - | **GAP** S11-NUI-P0-006 |
| J-07 | WebDAV retry/error mapping | no | TODO | - | **GAP** S11-NUI-P0-007 |
| J-08 | BackupManifest model | no | TODO | - | S11-NUI-P0-002 |
| J-09 | BackupEntry model | no | TODO | - | S11-NUI-P0-002 |
| J-10 | Checksum/version fields | no | TODO | - | S11-NUI-P0-002 |
| J-11 | Backup restore contract | no | TODO | - | **GAP** S12-NUI-P0-005 |

**Gaps to add**: J-05, J-06, J-07, J-11

---

### K. Cloud Sync / Remote Reading Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| K-01 | SyncManager state machine | no | TODO | - | S12-NUI-P0-001 |
| K-02 | SyncState/SyncOperation model | no | TODO | - | S12-NUI-P0-001 |
| K-03 | Retry/backoff model | no | TODO | - | S12-NUI-P0-001 |
| K-04 | Conflict resolver | no | TODO | - | S12-NUI-P0-002 |
| K-05 | Sync operation persistence | no | TODO | - | **GAP** S12-NUI-P0-003 |
| K-06 | Sync conflict test matrix | no | TODO | - | **GAP** S12-NUI-P0-004 |
| K-07 | RemoteContentProvider contract | no | TODO | - | S13-NUI-P0-001 |
| K-08 | Streaming/download mode | no | TODO | - | S13-NUI-P0-001 |
| K-09 | Remote file listing parser | no | TODO | - | **GAP** S13-NUI-P0-003 |
| K-10 | Remote download cache integration | no | TODO | - | **GAP** S13-NUI-P0-004 |
| K-11 | Offline availability manager | no | TODO | - | S13-NUI-P0-002 |
| K-12 | Offline availability tests | no | TODO | - | **GAP** S13-NUI-P0-005 |

**Gaps to add**: K-05, K-06, K-09, K-10, K-12

---

### L. Compatibility / Matrix / Release Gate

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| L-01 | Capability matrix model | no | TODO | - | S14-NUI-P0-001 |
| L-02 | Ownership matrix | no | TODO | - | S14-NUI-P0-001 |
| L-03 | Status matrix | no | TODO | - | S14-NUI-P0-001 |
| L-04 | Regression fixture registry | no | TODO | - | S14-NUI-P0-002 |
| L-05 | Backend RC gate | no | TODO | - | S15-NUI-P0-001 |
| L-06 | No-UI completion audit | no | TODO | - | **GAP** S15-NUI-P0-002 |
| L-07 | Backend regression suite gate | no | TODO | - | **GAP** S15-NUI-P0-003 |
| L-08 | No-UI parity declaration doc | no | TODO | - | **GAP** S15-NUI-P0-004 |
| L-09 | Remaining UI-only gap list | no | TODO | - | **GAP** S15-NUI-P0-005 |

**Gaps to add**: L-06, L-07, L-08, L-09

---

## Summary

| Category | Total | DONE | TODO | GAP (no task) |
|----------|-------|------|------|---------------|
| A. Foundation | 8 | 4 | 2 | 2 |
| B. Domain/DTO/Error | 18 | 11 | 5 | 3 |
| C. Source/Parser | 14 | 9 | 3 | 2 |
| D. Dynamic Backend | 12 | 0 | 9 | 3 |
| E. Explore/RSS | 7 | 0 | 7 | 0 |
| F. Local Book | 17 | 0 | 10 | 6 |
| G. Reader/Cache/Progress | 9 | 5 | 2 | 2 |
| H. Settings/Theme | 10 | 5 | 0 | 5 |
| I. TTS | 9 | 0 | 5 | 3 |
| J. WebDAV/Backup | 11 | 0 | 7 | 4 |
| K. Cloud Sync/Remote | 12 | 0 | 7 | 5 |
| L. Release Gate | 9 | 0 | 5 | 4 |
| **TOTAL** | **136** | **34** | **62** | **39** |

**Original loop had tasks for 62 of 136 capabilities. 39 capabilities had NO task. 34 already DONE.**

**New tasks needed**: 39 (plus original 26 TODO) = **65 total remaining tasks**.

**Conclusion: INSUFFICIENT** — original 30 tasks do not cover all non-UI capabilities.
