# Android UI Handoff from Non-UI RC1

**Date**: 2026-05-16
**From**: Non-UI RC1 (commit 4417bc7)
**To**: UI development phase

## 1. Handoff Goal

UI 阶段**只需接入已有 backend/adapter/repository/storage 能力**。所有 parser、repository、adapter、sync、cache 已完成并测试。**不应重新造后端。**

## 2. Available Backend Capabilities

### Book Source
- `BookSourceRepository` (interface + DataStore impl + Fake impl)
- JSON import/export with validation
- Enable/disable/delete CRUD
- SourceValidationResult model

### Search / Detail / TOC / Content
- `SearchParser`, `BookInfoParser`, `TOCParser`, `ContentParser` — 4 HTML parsers with contract tests
- `HttpClient` (OkHttp wrapper with headers/cookie injection)
- `PostRequestBody` (FORM/JSON/raw)
- `RequestHeaders` (UA/cookie/custom)
- `PaginationState` / `PageRef`
- ViewModels already support `useRealHttp` dual-mode switching

### Settings / Theme / Tap / Font
- `ThemePreferences` (DataStore: dark mode, font size, line spacing, margin)
- `ThemePair` / `ThemeConfig` (day/night pair with colors)
- `ColorModel` (ARGB + hex parse/detect)
- `AlphaConfig` (transparency + presets)
- `TapZoneConfig` / `ClickAreaConfig` (9-zone grid with actions)
- `FontConfig` / `FontFamily` (system/serif/sans/mono + custom)

### Cache / Progress
- `ReadingProgress` Room entity + DAO
- `CachedChapter` Room entity + DAO + `ChapterCacheManager` (TTL-based)
- `PrefetchStrategy` (chapters ahead/behind, wifi-only)
- `AppDatabase` v3 (Room, 3 entities)

### Dynamic Source Backend
- `WebRuntimeAdapter` interface + `FakeWebRuntimeAdapter`
- `JsRequest` / `JsResponse` / `JsError` / `JsErrorType`
- `CookieStore` interface + `FakeCookieStore` + `CookieScope` / `CookieRecord`
- `WebRuntimeErrorMapper` (JsErrorType → ReaderErrorCode)
- `RuntimeScope` (per-source isolation)
- `OfflineReplayRecord` / `OfflineReplayContract`

### RSS / Explore Backend
- `RssParser` + `RssFeed` / `RssItem` / `RssSubscription`
- `SubscriptionRepository` interface + Fake impl
- `ExploreSource` / `ExploreMapping`

### Local Book Backend
- `LocalBookSource` / `LocalBookMetadata` / `LocalBookImportResult`
- `LocalBookFormat` / `LocalChapterRef`
- `TxtParser` (chapter detection, encoding BOM)
- `EncodingDetector` (UTF-8/GBK/Big5/UTF-16)
- `EpubInventory` / `EpubContainerParser` / `OpfParser`
- `LocalBookProgressMapper` (local chapters → Room schema)
- `LocalBookLifecycle` (reimport/delete semantics)

### TTS Backend
- `TtsEngine` interface + `FakeTtsEngine`
- `TtsUtterance` / `TtsPlaybackState`
- `AndroidTtsAdapter` interface + Fake impl
- `TtsQueue` (priority + state tracking)
- `ChapterTextFeeder` (paragraph split + boundary events)
- `TtsErrorMapper` (TtsErrorType → ReaderErrorCode)

### WebDAV / Backup / Sync
- `WebDavClient` interface + `FakeWebDavClient`
- `WebDavAuth` (`AuthMethod`: Basic/Digest/Bearer)
- `WebDavXmlParser` + `WebDavResource`
- `WebDavRetryPolicy` + `WebDavErrorMapper`
- `BackupManifest` / `BackupEntry` (JSON serialization)
- `BackupRestoreManager` + `RestorePolicy`
- `ProgressSyncProtocol` / `SyncConflictResolver`
- `SyncManager` (state machine + retry)

### Remote Reading
- `RemoteContentProvider` / `ContentMode` / cache key
- `RemoteFileListingParser` + `RemoteFileEntry`
- `DownloadCacheManager` (remote chapter caching)
- `OfflineAvailabilityManager` (eviction: LRU/FIFO/TIME)

### Infrastructure
- `CapabilityMatrix` / `CapabilityEntry` (programmatic tracking)
- `RegressionFixtureRegistry` + `FixtureRegistry`
- `ReaderCoreBridge` / `BridgeResult` / `ReaderErrorCode` / `ReaderFailureStage`

## 3. UI-only Gap List

21 gaps documented in `docs/PLANNING/ANDROID_UI_ONLY_GAP_LIST.md`. Summary:

| Category | Gap |
|----------|-----|
| Bookshelf | Grid/list UI |
| Source | Import dialog, edit form |
| Search | Results refinement UI |
| Reader | Viewport, theme switcher, tap zone binding |
| Settings | Full layout |
| Dynamic | WebView composable, login UI |
| Explore | ExploreScreen, subscription list |
| Local Book | File picker, EPUB reader integration |
| TTS | Playback controls bar |
| WebDAV | Server config, backup/restore UI, sync status |
| Remote | File browser, download progress |

## 4. Integration Rules

- UI **不应**直接访问底层 parser（应通过 ViewModel/repository）
- UI **不应**绕过 Room/DataStore（应通过 repository/adapter）
- UI **不应**绕过 bridge contract
- UI **不应**访问真实网络作为测试 fixture
- UI 新增能力必须补测试或至少补 contract validation
- 所有 fake adapter 均可替换为真实实现

## 5. Suggested UI Stage Order

1. **UI-RC1** Bookshelf polish (+ search entry, cover display)
2. **UI-RC2** Source management polish (+ import dialog, edit form)
3. **UI-RC3** Reader settings/theme UI (+ font, color, tap zones)
4. **UI-RC4** Explore/RSS UI (+ subscription list)
5. **UI-RC5** Local book import UI (+ file picker, EPUB reader)
6. **UI-RC6** TTS control UI (+ playback bar)
7. **UI-RC7** WebDAV settings/sync UI (+ server config)
8. **UI-RC8** Final Legado parity UI audit
