# Reader for Android

Android reading app — Compose + Material 3. Consumer of Reader-Core public API (JSON-level contract).

## Status

**Non-UI RC1** — Backend complete. UI integration remains.

| Metric | Value |
|--------|-------|
| Stage | Non-UI RC1 (backend frozen) |
| `./gradlew test` | BUILD SUCCESSFUL, 331 tests, 0 failures |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| Commits | 112 |
| Main Kotlin files | 72 |
| Non-UI queue tasks | 71/71 DONE |
| Next phase | UI integration (21 UI-only gaps) |

## Quick start

```bash
cd "/Users/minliny/Documents/Reader for Android"
./gradlew test
./gradlew :app:assembleDebug
```

## Capability summary

- **S1-S6**: App Shell, Navigation, DTOs, CoreBridge, OkHttp, Room v3, DataStore, Search→Detail→TOC→Reader flow (fake/real dual mode)
- **S6.5**: Baseline hardening (7/7)
- **S6-SETTINGS**: Theme, Color, Alpha, TapZone, Font config
- **S7-NUI**: WebView/JS/Cookie/POST backend (12 adapters/contracts)
- **S8-NUI**: RSS parser + subscription engine
- **S9-NUI**: TXT/EPUB local book backend (10 parsers/contracts)
- **S10-NUI**: TTS engine/adapter/queue/feeder
- **S11-NUI**: WebDAV client/backup/sync/auth
- **S12-NUI**: Sync manager/conflict/persistence
- **S13-NUI**: Remote content/cache/listing/offline
- **S14-NUI**: Capability matrix/fixture registry
- **S15-NUI**: RC gate/audit/parity/UI gap list

## Architecture

Single `:app` module, internal package layering:
- `ui/` — Compose screens
- `data/model/` — Kotlin DTOs
- `data/bridge/` — CoreBridge contract + error taxonomy
- `data/adapter/` — Platform adapters (WebView, TTS, WebDAV, RSS, local book)
- `data/repository/` — BookSourceRepository (fake + DataStore)
- `data/network/` — OkHttp client + HTML/XML parsers
- `data/storage/` — ThemePreferences (DataStore) + AppDatabase (Room v3)

## Next phase: External UI Handoff

Non-UI RC1 tagged as `android-non-ui-rc1`. UI development is by **external UI tools**.

- **Handoff**: `docs/HANDOFF/ANDROID_EXTERNAL_UI_TOOL_HANDOFF.md` (integration contracts)
- **UI queue/plan/loop**: DEPRECATED — local model does NOT implement UI

## Docs

- `docs/RELEASE/ANDROID_NON_UI_RC1_FREEZE.md` — RC1 freeze
- `docs/HANDOFF/ANDROID_EXTERNAL_UI_TOOL_HANDOFF.md` — External UI handoff
- `docs/HANDOFF/ANDROID_UI_HANDOFF_FROM_NON_UI_RC1.md` — Backend capability handoff
- `docs/PLANNING/` — Planning, queue, blockers, matrix, gap list
