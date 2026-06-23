# Reader for Android UI-Only Gap List

**Date**: 2026-05-16
**Purpose**: Enumerate all capabilities requiring UI development. Backend contracts are complete for all listed items.

## UI-Only Gaps (Backend Complete)

| ID | Capability | Backend Status | UI Pending |
|----|-----------|---------------|------------|
| UI-01 | Bookshelf grid/list | BookSourceRepository, search entry | Compose LazyColumn/LazyGrid |
| UI-02 | Book cover display | BookInfo DTO, coverUrl field | Image loading, placeholder |
| UI-03 | Source import dialog | JSON import/export, validation | File picker, import UI |
| UI-04 | Source edit form | BookSource model, CRUD | Edit form UI |
| UI-05 | Search results refinement | SearchParser, SearchViewModel | Filter/sort UI |
| UI-06 | Reader viewport | ContentParser, ReaderViewModel | Text rendering, pagination |
| UI-07 | Reader theme switching | ThemePair, ThemeConfig, ColorModel | Theme switcher UI |
| UI-08 | Reader tap zones | TapZoneConfig, ClickAreaConfig | Gesture detection binding |
| UI-09 | Settings page full | ThemePreferences, FontConfig, AlphaConfig | Full settings layout |
| UI-10 | WebView runtime UI | WebRuntimeAdapter, Fake impl | WebView composable |
| UI-11 | Login page | CookieStore, CookieScope | Login WebView/UI |
| UI-12 | Explore page | RSS parser, ExploreSource, Subscription engine | ExploreScreen composable |
| UI-13 | Subscription management | SubscriptionRepository | Subscription list UI |
| UI-14 | File picker | LocalBookSource, SAF URI abstraction | SAF picker trigger |
| UI-15 | EPUB reader | EpubInventory, OpfParser, manifest/spine | Reader integration |
| UI-16 | TTS playback controls | TtsEngine, TtsQueue, AndroidTtsAdapter | Playback bar UI |
| UI-17 | WebDAV server config | WebDavClient, WebDavAuth, WebDavCredential | Settings page |
| UI-18 | Backup/restore UI | BackupManifest, BackupRestoreManager | Backup trigger UI |
| UI-19 | Progress sync UI | ProgressSyncProtocol, SyncManager | Sync status/trigger |
| UI-20 | Remote file browser | RemoteFileListingParser, RemoteBookRef | File list UI |
| UI-21 | Download progress | DownloadCacheManager, OfflineAvailability | Progress indicator |

## Summary

- **21 UI-only gaps** listed above
- All corresponding backend models, parsers, repositories, adapters, and contracts are **DONE and tested**
- No backend work blocked by UI — all contracts support the future UI integration
- 331 tests, 0 failures, assembleDebug passing
