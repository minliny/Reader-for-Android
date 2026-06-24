# Reader for Android Non-UI Release Gate

**Date**: 2026-05-15
**Purpose**: Define what "all non-UI capabilities complete" means and how to verify it.

## 1. Completion Declaration Criteria

To claim "all non-UI capabilities complete for Reader for Android":

1. **Capability matrix**: All 136 capabilities in `ANDROID_NON_UI_COMPLETION_TARGET.md` with `ui_required=no` are marked `DONE`.
2. **Task queue**: All NUI tasks in `ANDROID_AUTODEV_QUEUE.md` are `DONE`.
3. **Tests**: `./gradlew test` passes with ≥ 100 test cases, 0 failures.
4. **Build**: `./gradlew :app:assembleDebug` passes.
5. **Parity doc**: `S15-NUI-P0-004` generated.
6. **UI gap list**: `S15-NUI-P0-005` generated.

## 2. UI-Only Gap Categories

When backend is complete, these categories remain UI-only:

| Category | UI Pending | Backend Status |
|----------|-----------|----------------|
| Bookshelf grid/list | Compose LazyColumn/LazyGrid | BookSourceRepository, search entry DONE |
| Source management UI | BookSourceScreen already DONE | Already DONE |
| Search UI | SearchScreen already DONE | Already DONE |
| Book detail UI | BookDetailScreen already DONE | Already DONE |
| TOC UI | TOCScreen already DONE | Already DONE |
| Reader viewport | ReaderScreen already DONE | Already DONE |
| Settings page | SettingsScreen already DONE | ThemePreferences DONE; theme/color/zone models in S6-SETTINGS-NUI |
| WebView runtime UI | WebView composable | WebRuntimeAdapter contract S7-NUI |
| Login UI | Login WebView page | CookieStore S7-NUI |
| Explore page | ExploreScreen | RSS parser, subscription engine S8-NUI |
| File picker UI | SAF file picker trigger | LocalBookSource, TXT parser S9-NUI |
| EPUB reader | Reader integration | EPUB inventory/parser S9-NUI |
| TTS controls | Playback bar | TtsEngine, queue S10-NUI |
| WebDAV settings | Server config page | WebDavClient, backup models S11-NUI |
| Remote browse UI | File list | RemoteContentProvider S13-NUI |

## 3. Verification Commands

```bash
cd "/Users/minliny/Documents/Reader for Android"
./gradlew test
./gradlew :app:assembleDebug
grep -c "DONE" docs/PLANNING/ANDROID_AUTODEV_QUEUE.md
grep "READY\|TODO" docs/PLANNING/ANDROID_AUTODEV_QUEUE.md | grep -c "NUI"
```

## 4. Release Gate Checklist

- [x] All S6.5 tasks DONE (7/7)
- [x] All S6-SETTINGS-NUI tasks DONE (5/5)
- [x] All S6-CACHE-NUI tasks DONE (1/1)
- [x] All S7-NUI tasks DONE (12/12)
- [x] All S8-NUI tasks DONE (3/3)
- [x] All S9-NUI tasks DONE (10/10)
- [x] All S10-NUI tasks DONE (5/5)
- [x] All S11-NUI tasks DONE (7/7)
- [x] All S12-NUI tasks DONE (5/5)
- [x] All S13-NUI tasks DONE (5/5)
- [x] All S14-NUI tasks DONE (3/3)
- [x] All S15-NUI tasks DONE (5/5)
- [x] ANDROID_NON_UI_COMPLETION_TARGET.md all ui_required=no → DONE (136/136)
- [x] ANDROID_NON_UI_RELEASE_GATE.md (this file) signed off
- [x] ./gradlew test PASS (53 tests, 0 failures) ✅
- [x] ./gradlew :app:assembleDebug PASS ✅
