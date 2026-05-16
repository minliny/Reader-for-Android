# Reader for Android UI-only AutoDev Queue

**Date**: 2026-05-16
**Based on**: Non-UI RC1 (tag: android-non-ui-rc1)
**Separate from**: ANDROID_AUTODEV_QUEUE.md (Non-UI queue, frozen)

## Queue Rules

- Only one READY task at a time
- Stage order: UI-S0 → UI-S1 → ... → UI-S11
- No backend capability expansion
- UI-only changes in `ui/` package
- Validate with `./gradlew test` + `./gradlew :app:assembleDebug`

---

## UI-S0: Audit & Matrix

| ID | Stage | Status | Task | Scope | Validation | Backend Dep | Notes |
|----|-------|--------|------|-------|------------|-------------|-------|
| UI-S0-P0-001 | UI-S0 | READY | Audit current UI against UI-only gap list | Review all 9 UI files vs 21 gaps, document current state per screen | ./gradlew :app:compileDebugKotlin | None | Doc-only |
| UI-S0-P0-002 | UI-S0 | TODO | Build UI capability matrix | Create UI-SCREEN_STATUS.md mapping each screen to backend + gap | ./gradlew :app:compileDebugKotlin | None | Doc-only |

## UI-S1: Bookshelf Polish

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S1-P0-001 | UI-S1 | TODO | Bookshelf grid/list with book cards | LazyColumn/LazyGrid, BookCard composable, cover placeholder | assembleDebug | ReadingProgress |
| UI-S1-P0-002 | UI-S1 | TODO | Bookshelf progress and group UI | Progress bar per book, group/sort selector | assembleDebug | ReadingProgress |
| UI-S1-P0-003 | UI-S1 | TODO | Bookshelf top bar and menu | TopAppBar actions, overflow menu | assembleDebug | None |

## UI-S2: Source Management Polish

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S2-P0-001 | UI-S2 | TODO | Source list interaction polish | Swipe-to-delete, enable/disable animation, confirm dialog | assembleDebug | BookSourceRepository |
| UI-S2-P0-002 | UI-S2 | TODO | Source import/export UI | Import file picker, export share intent, JSON preview | assembleDebug | BookSourceRepository |
| UI-S2-P0-003 | UI-S2 | TODO | Source validation status UI | Validation result chip/icon, error details | assembleDebug | SourceValidationResult |

## UI-S3: Search / Detail / TOC Polish

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S3-P0-001 | UI-S3 | TODO | Search result card polish | Improved card layout, kind badge, word count | assembleDebug | SearchParser |
| UI-S3-P0-002 | UI-S3 | TODO | Detail and TOC page polish | Cover area, metadata layout, chapter indicators | assembleDebug | BookInfoParser, TOCParser |

## UI-S4: Reader Polish

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S4-P0-001 | UI-S4 | TODO | Reader layout and typography | Font size binding, margin/padding, scroll position save | assembleDebug | ThemePreferences |
| UI-S4-P0-002 | UI-S4 | TODO | Reader controls | Top/bottom bar show/hide, chapter nav, progress % | assembleDebug | ReadingProgress |

## UI-S5: Reader Settings & Theme UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S5-P0-001 | UI-S5 | TODO | Reader settings panel | Bottom sheet: font size, line spacing, margin sliders | assembleDebug | ThemePreferences |
| UI-S5-P0-002 | UI-S5 | TODO | Theme management UI | Day/night toggle, color pickers, background transparency | assembleDebug | ThemeConfig, ColorModel, AlphaConfig |
| UI-S5-P0-003 | UI-S5 | TODO | Tap area config UI | Zone grid editor, action assignment per zone | assembleDebug | TapZoneConfig |

## UI-S6: Explore / RSS UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S6-P0-001 | UI-S6 | TODO | Explore/RSS subscription UI | Subscription list, add/remove, update status, category tabs | assembleDebug | SubscriptionRepository, ExploreMapping |

## UI-S7: Local Book Import UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S7-P0-001 | UI-S7 | TODO | Local book import UI | SAF file picker, format detection, metadata preview, import dialog | assembleDebug | LocalBookSource, TxtParser, EncodingDetector |

## UI-S8: TTS Control UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S8-P0-001 | UI-S8 | TODO | TTS control bar | Play/pause/stop, speed/pitch, queue status, error display | assembleDebug | TtsEngine, TtsQueue |

## UI-S9: WebDAV / Sync UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S9-P0-001 | UI-S9 | TODO | WebDAV settings and sync UI | Server config, connection test, backup/restore, sync status | assembleDebug | WebDavClient, WebDavAuth, BackupRestoreManager |
| UI-S9-P0-002 | UI-S9 | TODO | Remote book browser UI | File list, download entry, cache status | assembleDebug | RemoteFileListingParser, DownloadCacheManager |

## UI-S10: Dynamic Source UI

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S10-P0-001 | UI-S10 | TODO | Dynamic source login/cookie UI | WebView entry, cookie status, per-source management | assembleDebug | WebRuntimeAdapter, CookieStore |

## UI-S11: Final UI Parity Audit

| ID | Stage | Status | Task | Scope | Validation | Backend Dep |
|----|-------|--------|------|-------|------------|-------------|
| UI-S11-P0-001 | UI-S11 | TODO | Final UI parity audit | Legado UI comparison, all 21 gaps verified | assembleDebug | None |
| UI-S11-P0-002 | UI-S11 | TODO | Android UI RC freeze | Generate UI RC freeze doc, update README | assembleDebug | None |

---

## Current READY Task

**UI-S0-P0-001 Audit current UI against UI-only gap list**

Total: 23 tasks. Stage order: UI-S0 → UI-S11. Loop command: `.claude/commands/ui-loop.md`.
