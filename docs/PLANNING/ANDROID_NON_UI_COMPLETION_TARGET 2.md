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
| A-05 | Fixture-only test policy | docs | no | DONE | docs/PLANNING/ANDROID_REAL_SOURCE_CLOSURE_PLAN.md §7 | S16-NUI-P0-008 |
| A-06 | No-network test rule | docs | no | DONE | AppProvider.isNetworkAllowed gate | S16-NUI-P0-002 |
| A-07 | No-UI loop rule | docs | no | DONE | .claude/commands/loop.md | DONE |
| A-08 | Release gate checklist | docs | no | DONE | ANDROID_NON_UI_RELEASE_GATE.md | S15-NUI-P0-001 |

**No gaps** — all 8 A capabilities complete.

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
| B-12 | Sync error model | no | DONE | SyncManager error handling | S12-NUI-P0-001 |
| B-13 | WebDAV error model | no | DONE | WebDavErrorMapper + RetryPolicy | S11-NUI-P0-007 |
| B-14 | Local book error model | no | DONE | LocalBookLifecycle error paths | S9-NUI-P0-001 |
| B-15 | TTS error model | no | DONE | TtsErrorMapper: 4 types → ReaderErrorCode | S10-NUI-P0-005 |
| B-16 | POST/API request model | no | DONE | PostRequestBody (3 content types) | S7-NUI-P0-005 |
| B-17 | Pagination/next-page model | no | DONE | PageRef + PaginationState | S7-NUI-P0-007 |
| B-18 | Source validation result model | no | DONE | SourceValidationResult sealed class | S7-NUI-P0-008 |

**No gaps** — all 18 B capabilities complete.

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
| C-09 | Request headers/user-agent injection | no | DONE | RequestHeaders: UA, Cookie, custom headers | S7-NUI-P0-006 |
| C-10 | Cookie injection in requests | no | DONE | RequestHeaders + CookieStore | S7-NUI-P0-006 |
| C-11 | Redirect handling | no | DONE | HttpClient (followRedirects=true) | S3 DONE |
| C-12 | Encoding handling | no | DONE | EncodingDetector (BOM + GBK heuristic) | S9-NUI-P0-005 |
| C-13 | Parser fixture registry | no | DONE | FixtureRegistry + FixtureEntry metadata | S7-NUI-P0-009 |
| C-14 | Parser conformance test registry | no | DONE | FixtureRegistry parser/source registry | S14-NUI-P0-002 |

**No gaps** — all 14 C capabilities complete.

---

### D. Dynamic Source Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| D-01 | WebRuntimeAdapter contract | no | DONE | WebRuntimeAdapter.kt | S7-NUI-P0-001 |
| D-02 | FakeWebRuntimeAdapter | no | DONE | FakeWebRuntimeAdapter.kt | S7-NUI-P0-001 |
| D-03 | JS evaluation request/response model | no | DONE | JsRequest.kt, JsResponse.kt | S7-NUI-P0-002 |
| D-04 | JS error model | no | DONE | JsError.kt, JsErrorType enum | S7-NUI-P0-002 |
| D-05 | CookieScope model | no | DONE | CookieScope.kt | S7-NUI-P0-003 |
| D-06 | CookieRecord model | no | DONE | CookieRecord.kt | S7-NUI-P0-003 |
| D-07 | CookieStore contract | no | DONE | CookieStore.kt | S7-NUI-P0-003 |
| D-08 | Per-source cookie isolation | no | DONE | CookieStore per-source scope | S7-NUI-P0-003 |
| D-09 | Dynamic source capability matrix doc | no | DONE | docs/design/DYNAMIC_SOURCE_CAPABILITY_MATRIX.md | S7-NUI-P0-004 |
| D-10 | Web runtime error mapping | no | DONE | JsErrorType→ReaderErrorCode mapper | S7-NUI-P0-010 |
| D-11 | Dynamic source offline replay contract | no | DONE | OfflineReplayRecord, OfflineReplayContract | S7-NUI-P0-011 |
| D-12 | Per-source runtime isolation | no | DONE | RuntimeScope per-source isolation | S7-NUI-P0-012 |

---

### E. Explore / RSS Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| E-01 | RSSFeed model | no | DONE | RssFeed.kt | S8-NUI-P0-001 |
| E-02 | RSSItem model | no | DONE | RssItem.kt | S8-NUI-P0-001 |
| E-03 | RSSSubscription model | no | DONE | RSSSubscription.kt | S8-NUI-P0-001 |
| E-04 | RSS XML parser | no | DONE | RssParser.kt (CDATA support) | S8-NUI-P0-001 |
| E-05 | Subscription update engine | no | DONE | SubscriptionRepository + Fake impl | S8-NUI-P0-002 |
| E-06 | ExploreSource backend model | no | DONE | ExploreSource.kt | S8-NUI-P0-003 |
| E-07 | Category/feed mapping | no | DONE | ExploreMapping.groupByCategory | S8-NUI-P0-003 |

**Covered**: All E-01 through E-07 have tasks. No gaps.

---

### F. Local Book Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| F-01 | LocalBookSource model | no | DONE | LocalBookSource.kt | S9-NUI-P0-001 |
| F-02 | LocalBookMetadata model | no | DONE | LocalBookMetadata.kt | S9-NUI-P0-001 |
| F-03 | LocalBookImportResult model | no | DONE | LocalBookImportResult.kt | S9-NUI-P0-001 |
| F-04 | URI abstraction (SAF) | no | DONE | LocalChapterRef (SAF URI) | S9-NUI-P0-001 |
| F-05 | TXT parser | no | DONE | TxtParser.kt | S9-NUI-P0-002 |
| F-06 | TXT encoding strategy/detection | no | DONE | EncodingDetector.kt | S9-NUI-P0-005 |
| F-07 | TXT chapter split rules | no | DONE | TxtParser chapter detection | S9-NUI-P0-002 |
| F-08 | TXT chapter split edge cases | no | DONE | 5 edge case tests | S9-NUI-P0-006 |
| F-09 | TXT metadata inference | no | DONE | TxtParser metadata inference | S9-NUI-P0-002 |
| F-10 | EPUB ZIP inventory | no | DONE | EpubInventory.kt | S9-NUI-P0-003 |
| F-11 | EPUB OPF metadata parser | no | DONE | OpfParser.kt | S9-NUI-P0-007 |
| F-12 | EPUB spine/manifest parser | no | DONE | OpfParser.kt (manifest + spine) | S9-NUI-P0-008 |
| F-13 | EPUB chapter extraction contract | no | DONE | EpubInventory chapter extraction | S9-NUI-P0-003 |
| F-14 | Local book progress mapping | no | DONE | LocalBookProgressMapper.kt | S9-NUI-P0-004 |
| F-15 | Local book cache mapping | no | DONE | LocalBookProgressMapper.kt | S9-NUI-P0-004 |
| F-16 | Local book reimport/delete semantics | no | DONE | LocalBookLifecycle (3 strategies) | S9-NUI-P0-009 |
| F-17 | Local book cache/progress compat tests | no | DONE | Full mapping pipeline tests | S9-NUI-P0-010 |

**No gaps** — all 17 F capabilities complete.

---

### G. Reader / Cache / Progress Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| G-01 | ReadingProgress DAO | no | DONE | ReadingProgress.kt | S6 DONE |
| G-02 | Progress save/load | no | DONE | ReadingProgress.kt | S6 DONE |
| G-03 | Progress merge policy | no | DONE | 6 scenario conflict resolver tests | S12-NUI-P0-002 |
| G-04 | ChapterCache DAO | no | DONE | ChapterCache.kt | S6 DONE |
| G-05 | Cache TTL eviction | no | DONE | ChapterCache.kt | S6 DONE |
| G-06 | Cache invalidation policy | no | DONE | ChapterCache.kt | S6.5-P0-005 |
| G-07 | Cache key strategy doc | no | DONE | RemoteContentProvider cache key | S13-NUI-P0-001 |
| G-08 | Next/previous chapter contract | no | DONE | nextPageUrl in ReaderScreen | S5 DONE |
| G-09 | Content prefetch contract | no | DONE | PrefetchStrategy (chaptersAhead/Behind, wifiOnly) | S6-NUI-CACHE-001 |

**No gaps** — all 9 G capabilities complete.

---

### H. Settings / Theme Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| H-01 | DataStore settings persistence | no | DONE | ThemePreferences.kt | S6 DONE |
| H-02 | Dark mode flag | no | DONE | ThemePreferences.kt | S6 DONE |
| H-03 | Font size setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-04 | Line spacing setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-05 | Page margin setting | no | DONE | ThemePreferences.kt | S6 DONE |
| H-06 | Theme model (day/night pair) | no | DONE | ThemeConfig + ThemePair | S6-NUI-SET-001 |
| H-07 | Text/background/accent color model | no | DONE | ColorModel: ARGB decomposition | S6-NUI-SET-002 |
| H-08 | Background transparency model | no | DONE | AlphaConfig: range validation | S6-NUI-SET-003 |
| H-09 | Click area config model | no | DONE | TapZone 9-zone + TapAction 7 types | S6-NUI-SET-004 |
| H-10 | Font family/typeface config | no | DONE | FontConfig: 4 FontFamily presets | S6-NUI-SET-005 |

**No gaps** — all 10 H capabilities complete.

---

### I. TTS Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| I-01 | TtsEngine contract | no | DONE | TtsEngine.kt + FakeTtsEngine | S10-NUI-P0-001 |
| I-02 | TtsUtterance model | no | DONE | TtsUtterance.kt | S10-NUI-P0-001 |
| I-03 | TtsPlaybackState model | no | DONE | TtsPlaybackState (5 states) | S10-NUI-P0-001 |
| I-04 | FakeTtsEngine | no | DONE | FakeTtsEngine.kt | S10-NUI-P0-001 |
| I-05 | AndroidTtsAdapter boundary | no | DONE | AndroidTtsAdapter + TtsInitResult | S10-NUI-P0-002 |
| I-06 | TTS queue model | no | DONE | TtsQueue: priority enqueue/dequeue | S10-NUI-P0-003 |
| I-07 | TTS chapter integration contract | no | DONE | ChapterTextFeeder: paragraph split | S10-NUI-P0-004 |
| I-08 | TTS error mapping | no | DONE | TtsErrorMapper: 4 types → ReaderErrorCode | S10-NUI-P0-005 |
| I-09 | Pause/resume/stop state machine | no | DONE | TtsEngine interface states | S10-NUI-P0-001 |

**No gaps** — all 9 I capabilities complete.

---

### J. WebDAV / Backup Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| J-01 | WebDavClient contract | no | DONE | WebDavClient + FakeWebDavClient | S11-NUI-P0-001 |
| J-02 | PROPFIND model | no | DONE | WebDavClient 5 methods | S11-NUI-P0-001 |
| J-03 | GET/PUT/DELETE models | no | DONE | WebDavClient 5 methods | S11-NUI-P0-001 |
| J-04 | Fake transport | no | DONE | FakeWebDavClient.kt | S11-NUI-P0-001 |
| J-05 | Auth model (Basic/Digest/Bearer) | no | DONE | AuthMethod + WebDavCredential | S11-NUI-P0-005 |
| J-06 | WebDAV XML response parser | no | DONE | WebDavXmlParser: MultiStatus | S11-NUI-P0-006 |
| J-07 | WebDAV retry/error mapping | no | DONE | RetryPolicy + WebDavErrorMapper | S11-NUI-P0-007 |
| J-08 | BackupManifest model | no | DONE | BackupManifest.kt | S11-NUI-P0-002 |
| J-09 | BackupEntry model | no | DONE | BackupEntry.kt | S11-NUI-P0-002 |
| J-10 | Checksum/version fields | no | DONE | BackupEntry checksum fields | S11-NUI-P0-002 |
| J-11 | Backup restore contract | no | DONE | ProgressSyncRecord + ConflictResolver | S11-NUI-P0-003 |

**No gaps** — all 11 J capabilities complete.

---

### K. Cloud Sync / Remote Reading Backend

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| K-01 | SyncManager state machine | no | DONE | SyncManager.kt | S12-NUI-P0-001 |
| K-02 | SyncState/SyncOperation model | no | DONE | SyncOperationLog entity | S12-NUI-P0-003 |
| K-03 | Retry/backoff model | no | DONE | SyncManager state machine | S12-NUI-P0-001 |
| K-04 | Conflict resolver | no | DONE | 6 scenario ConflictResolver | S12-NUI-P0-002 |
| K-05 | Sync operation persistence | no | DONE | SyncOperationLog Room entity | S12-NUI-P0-003 |
| K-06 | Sync conflict test matrix | no | DONE | ConflictScenario enum, resolution matrix | S12-NUI-P0-004 |
| K-07 | RemoteContentProvider contract | no | DONE | RemoteContentProvider.kt | S13-NUI-P0-001 |
| K-08 | Streaming/download mode | no | DONE | RemoteContentProvider streaming | S13-NUI-P0-001 |
| K-09 | Remote file listing parser | no | DONE | WebDAV directory listing parser | S13-NUI-P0-003 |
| K-10 | Remote download cache integration | no | DONE | DownloadCacheManager | S13-NUI-P0-004 |
| K-11 | Offline availability manager | no | DONE | Cache manifest + eviction policy | S13-NUI-P0-002 |
| K-12 | Offline availability tests | no | DONE | Offline read verification + stale eviction | S13-NUI-P0-005 |

**No gaps** — all 12 K capabilities complete.

---

### L. Compatibility / Matrix / Release Gate

| ID | Capability | UI? | Status | Files | Loop Task |
|----|-----------|-----|--------|-------|-----------|
| L-01 | Capability matrix model | no | DONE | Capability enum/model | S14-NUI-P0-001 |
| L-02 | Ownership matrix | no | DONE | Capability ownership fields | S14-NUI-P0-001 |
| L-03 | Status matrix | no | DONE | Capability status tracking | S14-NUI-P0-001 |
| L-04 | Regression fixture registry | no | DONE | Fixture metadata registry | S14-NUI-P0-002 |
| L-05 | Backend RC gate | no | DONE | test/build/doc checklist | S14-NUI-P0-003 |
| L-06 | No-UI completion audit | no | DONE | ANDROID_NON_UI_COMPLETION_TARGET.md | S15-NUI-P0-002 |
| L-07 | Backend regression suite gate | no | DONE | ./gradlew test (all stages) | S15-NUI-P0-003 |
| L-08 | No-UI parity declaration doc | no | DONE | ANDROID_NON_UI_RELEASE_GATE.md | S15-NUI-P0-004 |
| L-09 | Remaining UI-only gap list | no | DONE | ANDROID_NON_UI_RELEASE_GATE.md §2 | S15-NUI-P0-005 |

**No gaps** — all 9 L capabilities complete.

---

## Summary

| Category | Total | DONE | TODO | GAP (no task) |
|----------|-------|------|------|---------------|
| A. Foundation | 8 | 8 | 0 | 0 |
| B. Domain/DTO/Error | 18 | 18 | 0 | 0 |
| C. Source/Parser | 14 | 14 | 0 | 0 |
| D. Dynamic Backend | 12 | 12 | 0 | 0 |
| E. Explore/RSS | 7 | 7 | 0 | 0 |
| F. Local Book | 17 | 17 | 0 | 0 |
| G. Reader/Cache/Progress | 9 | 9 | 0 | 0 |
| H. Settings/Theme | 10 | 10 | 0 | 0 |
| I. TTS | 9 | 9 | 0 | 0 |
| J. WebDAV/Backup | 11 | 11 | 0 | 0 |
| K. Cloud Sync/Remote | 12 | 12 | 0 | 0 |
| L. Release Gate | 9 | 9 | 0 | 0 |
| **TOTAL** | **136** | **136** | **0** | **0** |

**All 136 non-UI capabilities are DONE.** Capability matrix is fully synchronized with the task queue.
Sync date: 2026-05-28.
