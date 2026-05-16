# Android External UI Tool Handoff

**Date**: 2026-05-16
**Based on**: Non-UI RC1 (tag: android-non-ui-rc1, commit 0fb0d54)

## 1. Overall

UI pages, visual design, layouts, and interaction flows are handled by **external UI tools**.

This repository provides the completed **Non-UI backend** and **UI integration contracts** only. The local model does NOT implement UI pages.

## 2. Non-UI Backend Baseline

| Metric | Value |
|--------|-------|
| Tag | `android-non-ui-rc1` |
| Non-UI tasks | 71/71 DONE |
| Non-UI capabilities | 136/136 |
| Tests | 331, 0 failures |
| `./gradlew test` | PASS |
| `./gradlew :app:assembleDebug` | PASS |

## 3. Responsibilities

### External UI Tool
- Bookshelf page visuals
- Source management page visuals
- Search/detail/TOC page visuals
- Reader page visuals
- Reading settings/theme management visuals
- Explore/RSS page visuals
- Local book import page visuals
- TTS control bar visuals
- WebDAV/sync settings page visuals
- Dynamic source/cookie/login page visuals
- Legado visual style parity

### This Repository (Local Model)
- Backend capabilities (done)
- ViewModel state contracts
- Repository / Adapter / Bridge interfaces
- Integration boundary for external UI output
- Compile verification
- Test verification
- Does NOT design UI

## 4. UI Integration Contracts

### Bookshelf

| Aspect | Detail |
|--------|--------|
| Backend | `ReadingProgressDao` (Room), `BookSourceRepository` |
| Inputs (data → UI) | `List<ReadingProgress>` from DAO, `List<BookSource>` from repository |
| Actions (UI → backend) | `upsert(progress)`, `delete(progress)` |
| Existing UI file | `ui/bookshelf/BookshelfScreen.kt` (placeholder with search FAB) |
| Gap | Grid/list, book cards, progress bar, group/sort bar, top menu |

### Source Management

| Aspect | Detail |
|--------|--------|
| Backend | `BookSourceRepository`, `SourceValidationResult` |
| Inputs | `List<BookSource>` from `repository.getAll()`, `SourceValidationResult` from validator |
| Actions | `add(source)`, `remove(url)`, `setEnabled(url, enabled)`, `importJson(json)` |
| Existing UI file | `ui/booksource/BookSourceScreen.kt` (functional list + ViewModel) |
| Gap | Import/export dialog, validation status UI, delete confirm |

### Search

| Aspect | Detail |
|--------|--------|
| Backend | `SearchViewModel` (uses `SearchParser` or `HttpClient`) |
| Inputs | `List<SearchResultItem>` from `viewModel.results` |
| Actions | `viewModel.onQueryChange(text)`, `viewModel.search()` |
| Existing UI file | `ui/search/SearchScreen.kt` (functional) |
| Gap | Card polish, filter UI |

### Detail

| Aspect | Detail |
|--------|--------|
| Backend | `BookDetailViewModel` (uses `BookInfoParser` or `HttpClient`) |
| Inputs | `BookInfo?` from `viewModel.bookInfo` |
| Actions | `viewModel.load(detailUrl)`, navigate to TOC |
| Existing UI file | `ui/detail/BookDetailScreen.kt` (functional) |
| Gap | Cover image, metadata layout, polish |

### TOC

| Aspect | Detail |
|--------|--------|
| Backend | `TOCViewModel` (uses `TOCParser` or `HttpClient`) |
| Inputs | `List<TOCItem>` from `viewModel.chapters` |
| Actions | `viewModel.load(tocUrl)`, navigate to reader |
| Existing UI file | `ui/toc/TOCScreen.kt` (functional) |
| Gap | Jump indicator, polish |

### Reader

| Aspect | Detail |
|--------|--------|
| Backend | `ReaderViewModel` (uses `ContentParser`), `ReadingProgress`, `PrefetchStrategy` |
| Inputs | `ContentPage?` from `viewModel.content`, `ReadingProgress` from DAO |
| Actions | `viewModel.load(url, title)`, `progressDao.upsert(progress)` |
| Existing UI file | `ui/reader/ReaderScreen.kt` (functional text + nav) |
| Gap | Settings panel, tap zones, theme switching, typography binding |

### Settings / Theme

| Aspect | Detail |
|--------|--------|
| Backend | `ThemePreferences` (DataStore), `ThemeConfig`, `ColorModel`, `AlphaConfig`, `TapZoneConfig`, `FontConfig` |
| Inputs | Flow from DataStore: fontSize, lineSpacing, pageMargin, darkMode. ThemePair, ColorModel, AlphaConfig presets, TapZone actions, FontFamily |
| Actions | `prefs.setFontSize(sp)`, `setDarkMode(enabled)`, `setLineSpacing()`, `setPageMargin()` |
| Existing UI file | `ui/settings/SettingsScreen.kt` (basic sliders) |
| Gap | Color pickers, theme switcher, tap zone editor, font selector |

### Explore / RSS

| Aspect | Detail |
|--------|--------|
| Backend | `SubscriptionRepository`, `RssParser`, `ExploreMapping` |
| Inputs | `List<RssSubscription>` from repo, `List<ExploreCategory>` from mapping |
| Actions | `repo.add(sub)`, `repo.remove(url)`, `repo.markUpdated(url, guid)` |
| Existing UI file | None (new screen needed) |
| Gap | Full subscription list UI, add/remove, update status, category tabs |

### Local Book

| Aspect | Detail |
|--------|--------|
| Backend | `LocalBookSource`, `LocalBookMetadata`, `TxtParser`, `EncodingDetector`, `LocalBookProgressMapper` |
| Inputs | `LocalBookSource` from SAF URI, `LocalBookMetadata` from parser |
| Actions | Import flow via `LocalBookProgressMapper`, progress via `ReadingProgress` |
| Existing UI file | None (new screen needed) |
| Gap | SAF file picker, format detection, metadata preview, import progress |

### TTS

| Aspect | Detail |
|--------|--------|
| Backend | `TtsEngine`, `TtsQueue`, `ChapterTextFeeder`, `AndroidTtsAdapter`, `TtsErrorMapper` |
| Inputs | `TtsPlaybackState` from engine, `List<QueueItem>` from queue |
| Actions | `engine.speak(utterance)`, `pause()`, `resume()`, `stop()` |
| Existing UI file | None (new component needed) |
| Gap | Play/pause/stop bar, queue status, speed/pitch, chapter entry |

### WebDAV / Sync

| Aspect | Detail |
|--------|--------|
| Backend | `WebDavClient`, `WebDavAuth`, `BackupRestoreManager`, `SyncManager`, `ProgressSyncProtocol`, `RemoteFileListingParser` |
| Inputs | `WebDavCredential`, `BackupManifest`, `SyncState`, `List<RemoteFileEntry>` |
| Actions | `client.execute(request)`, `syncMgr.start(type)`, `restoreMgr.validate(manifest)` |
| Existing UI file | None (new screen needed) |
| Gap | Server config, connection test, backup/restore, sync status, remote browser |

### Dynamic Source / Cookie / Login

| Aspect | Detail |
|--------|--------|
| Backend | `WebRuntimeAdapter`, `CookieStore`, `RuntimeScope`, `JsRequest/JsResponse`, `OfflineReplayRecord` |
| Inputs | `CookieScope` from store, `JsResponse` from adapter |
| Actions | `adapter.loadHtml(url, html)`, `cookieStore.save(url, cookies)` |
| Existing UI file | None (new screen needed) |
| Gap | WebView entry, cookie status, per-source management |

## 5. Forbidden for Local Model

- Generate UI pages
- Modify Compose layouts
- Modify visual styles
- Design interactions
- Add UI tasks to loop
- Start UI cron loop

## 6. Allowed for Local Model

- Supplement UI integration contract docs
- Document state models needed by external UI
- Check whether external UI output compiles
- Do minimal glue fixes (no new backend)
- Run tests and assembleDebug
- Record integration gaps

## 7. External UI Artifact Intake Rules

When external UI tool outputs designs or code:
1. Audit file scope first
2. Do NOT allow overwriting backend
3. Do NOT break Non-UI contracts
4. Must pass `./gradlew test`
5. Must pass `./gradlew :app:assembleDebug`
6. If backend extension is needed, record as integration gap — do NOT implement
