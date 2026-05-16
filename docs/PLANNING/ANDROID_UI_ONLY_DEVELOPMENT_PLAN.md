# Reader for Android UI-only Development Plan

**Date**: 2026-05-16
**Based on**: Non-UI RC1 (tag: android-non-ui-rc1, commit 0fb0d54)

## 1. Mission

在 Non-UI RC1 后端基础上，推进 Compose UI、交互动线、页面接入、视觉一致性和 Legado 对标体验。不新增后端能力，不破坏已有 backend contract。

## 2. Baseline

| Metric | Value |
|--------|-------|
| Non-UI tag | `android-non-ui-rc1` |
| HEAD | `0fb0d54` |
| Tests | 331, 0 failures |
| Backend completion | 71/71 tasks, 136/136 non-ui capabilities |
| Remaining scope | UI-only (21 gaps) |
| Existing UI files | 9 (Navigation + 7 screens + App) |

## 3. Existing UI Pre-Assessment

| Screen | Status | Backend Connected | Polish Needed |
|--------|--------|-----------------|---------------|
| BookshelfScreen | Placeholder (search FAB only) | No bookshelf backend | Full grid/list UI |
| BookSourceScreen | Functional (list + ViewModel) | Yes (FakeBookSourceRepo) | Import dialog, validation UI |
| SearchScreen | Functional (search bar + results) | Yes (fake/real dual mode) | Card polish, filter UI |
| BookDetailScreen | Functional (detail + TOC button) | Yes (fake/real dual mode) | Cover display, layout |
| TOCScreen | Functional (chapter list) | Yes (fake/real dual mode) | Polish, jump indicator |
| ReaderScreen | Functional (text + nav) | Yes (fake/real dual mode) | Settings panel, tap zones, theme |
| SettingsScreen | Functional (sliders) | Yes (DataStore) | Theme picker, tap zone config |
| AppNavigation | Functional (full S5 flow) | Yes | New route entries |

## 4. UI Development Rules

- UI 通过 ViewModel / repository / adapter contract 接入后端
- UI 不直接调用 parser
- UI 不直接访问 Room DAO
- UI 不绕过 DataStore
- UI 不访问真实网络作为测试 fixture
- UI 新增交互必须有状态设计（loading/empty/error/success）
- UI 页面不得硬编码假数据，除非明确是 preview/fake mode 占位
- 每轮必须保持 `./gradlew test` 和 `assembleDebug` 通过
- UI 开发不修改 Reader-Core
- UI 开发不扩展后端能力；发现后端缺口只记录，不直接开发

## 5. UI-only Stages

### UI-S0: Audit & Matrix
- Audit current UI against UI-only gap list
- Build UI capability matrix
- Identify which gaps need new screens vs. polish existing

### UI-S1: Bookshelf Polish
- Bookshelf grid/list toggle
- Book cards with cover placeholder, title, author, progress
- Reading progress indicator
- Search entry (existing FAB → polish)
- Group/sort bar
- Empty/loading/error states
- Top bar menu
- Backend: ReadingProgress DAO, BookSourceRepository

### UI-S2: Source Management Polish
- Source list interaction polish
- Enable/disable/delete with confirmation
- JSON import/export entry points
- Source validation result display
- Group/search/sort UI
- Backend: BookSourceRepository, SourceValidationResult

### UI-S3: Search / Detail / TOC Polish
- Search result card polish
- Book detail page layout polish
- TOC volume/chapter hierarchy polish
- Loading/empty/error states across all three
- Backend: SearchParser, BookInfoParser, TOCParser (via ViewModels)

### UI-S4: Reader Polish
- Text rendering polish (font size from settings)
- Top/bottom control bars
- Previous/next chapter navigation
- Progress display
- Cache status indicator
- Error/retry state
- Backend: ContentParser, ReaderViewModel, ReadingProgress

### UI-S5: Reader Settings & Theme UI
- Reading settings bottom sheet/panel
- Font size, line spacing, margin sliders (already in DataStore)
- Day/night theme toggle (already in DataStore)
- Background color/text color/accent picker (ThemeConfig)
- Background transparency slider (AlphaConfig)
- Click area config (TapZoneConfig)
- Font family selector (FontConfig)
- Backend: ThemePreferences, ThemeConfig, AlphaConfig, TapZoneConfig, FontConfig

### UI-S6: Explore / RSS UI
- RSS subscription list screen
- Subscription add/remove
- Update status indicator
- Explore source category view
- Empty/loading/error states
- Backend: SubscriptionRepository, RssParser, ExploreSource, ExploreMapping

### UI-S7: Local Book Import UI
- File picker entry point (SAF)
- Import progress dialog
- TXT/EPUB format detection
- Metadata preview before import
- Import error display
- Backend: LocalBookSource, LocalBookMetadata, TxtParser, EncodingDetector

### UI-S8: TTS Control UI
- Playback control bar (play/pause/stop)
- Queue status indicator
- Chapter reading entry point
- Error display
- Speech rate/pitch (from existing model)
- Backend: TtsEngine, TtsQueue, ChapterTextFeeder

### UI-S9: WebDAV / Sync UI
- WebDAV server config screen
- Connection test button
- Backup trigger + restore entry
- Progress sync status
- Conflict resolution UI
- Remote book browser entry
- Backend: WebDavClient, WebDavAuth, BackupRestoreManager, SyncManager

### UI-S10: Dynamic Source UI
- WebView login page entry
- Cookie status display
- Per-source cookie management
- Dynamic source capability indicator
- Backend: WebRuntimeAdapter, CookieStore, RuntimeScope

### UI-S11: Final UI Parity Audit
- Legado UI comparison matrix
- All 21 UI-only gaps verified closed
- UI RC freeze document
- Final README update

## 6. Task Breakdown Summary

| Stage | Est. Tasks | Description |
|-------|-----------|-------------|
| UI-S0 | 2 | Audit + capability matrix |
| UI-S1 | 3 | Bookshelf grid/progress/menu |
| UI-S2 | 3 | Source list/import/validation UI |
| UI-S3 | 2 | Search + Detail + TOC polish |
| UI-S4 | 2 | Reader layout + controls |
| UI-S5 | 3 | Settings panel + theme + tap zones |
| UI-S6 | 1 | Explore/RSS subscription UI |
| UI-S7 | 1 | Local book import UI |
| UI-S8 | 1 | TTS controls |
| UI-S9 | 2 | WebDAV settings + sync UI |
| UI-S10 | 1 | Dynamic source login/cookie UI |
| UI-S11 | 2 | Parity audit + RC freeze |
| **Total** | **23** | |

## 7. Validation

Per task:
- `git diff --check`
- `./gradlew test`
- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:assembleDebug`

## 8. Exit Criteria

- 21 UI-only gaps closed or out_of_scope
- All UI-S0 through UI-S11 tasks DONE
- `./gradlew test` PASS (331+ tests)
- `./gradlew :app:assembleDebug` PASS
- README updated
- Android UI RC freeze document generated
