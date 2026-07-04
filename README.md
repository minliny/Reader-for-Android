# Reader for Android

Android reading app — Compose + Material 3 native host for Reader-Core-Native and Reader UI Contract.

## Current architecture role

Reader for Android is the Compose native host app in the Contract-first Native UI Architecture.

Primary shared plan:

```text
docs/frontend-complete-app/CONTRACT_FIRST_NATIVE_UI_PLAN.md
```

Reader UI remains the upstream contract/schema/codegen source; this repo keeps a local development copy under `docs/frontend-complete-app/`.

Architecture direction:

```text
Reader UI Contract
  -> generated Kotlin route / state / event / motion / token / view-state types

Reader for Android
  -> Compose Native UI
  -> Kotlin reducer/ViewModel/coordinator
  -> Reader-Core-Native bridge
  -> Android Host Adapter

Reader-Core-Native
  -> business source of truth
```

This repo owns:

- Compose rendering and Android native interaction quality.
- Android navigation, adaptive layout, accessibility, lifecycle, and Material integration.
- Kotlin reducer/ViewModel for `navigation`、`readerMode`、`overlay`、`activeSession`、
  `focusTarget`、`loading/error`、`async guard`、`reducedMotion`。
- Reader-Core-Native bridge through the stable Core protocol.
- Android Host Adapter for OkHttp、WebView、Cookie、file/storage、Keystore、
  TextToSpeech、permission、notification、background task and share flows.
- Android reducer golden tests and device/emulator smoke evidence for shared slices.

This repo does not own:

- Book/source parsing business rules.
- Canonical progress and sync conflict strategy.
- Reader UI Contract schema/codegen source.
- iOS or HarmonyOS reducer behavior.

Modification direction:

- Consume Reader UI Contract generated Kotlin types when schema/codegen lands.
- Keep durable UI state in `ReaderUiReducer` / ViewModel, not scattered across screens.
- Keep drag offset, scroll pixel, layout measurement, pressed state and accessibility focus as local visual state only.
- Route Core-owned operations through Reader-Core-Native bridge.
- Route HTTP/WebView/Cookie/file/TTS/permission/background abilities through Host Adapter.
- Add reducer golden tests for every shared `UiEvent` sequence.

## Status

Legacy status below is retained for historical baseline and must be reconciled with current local UI work before using it as a release claim.

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

Single `:app` module, internal package layering. New work should align these packages with the shared Contract-first Native UI boundaries:

- `ui/` — Compose screens
- `ui/shell` — reducer/state/navigation shell ownership
- `data/model/` — Kotlin DTOs
- `data/bridge/` — CoreBridge contract + error taxonomy
- `data/adapter/` — Platform adapters (WebView, TTS, WebDAV, RSS, local book)
- `data/repository/` — BookSourceRepository (fake + DataStore)
- `data/network/` — OkHttp client + HTML/XML parsers
- `data/storage/` — ThemePreferences (DataStore) + AppDatabase (Room v3)

## Next phase: External UI Handoff

Historical Non-UI RC1 was tagged as `android-non-ui-rc1`. Current UI work should no longer be described as external UI tools only; the repo should consume Reader UI Contract artifacts and implement native Compose reducer/coordinator slices locally.

- **Handoff**: `docs/HANDOFF/ANDROID_EXTERNAL_UI_TOOL_HANDOFF.md` (integration contracts)
- **UI queue/plan/loop**: historical external-handoff wording is deprecated; current work implements bounded native Compose slices locally from Reader UI Contract.

## Docs

- `docs/frontend-complete-app/README.md` — Android-local frontend complete-app development entry
- `docs/RELEASE/ANDROID_NON_UI_RC1_FREEZE.md` — RC1 freeze
- `docs/HANDOFF/ANDROID_EXTERNAL_UI_TOOL_HANDOFF.md` — External UI handoff
- `docs/HANDOFF/ANDROID_UI_HANDOFF_FROM_NON_UI_RC1.md` — Backend capability handoff
- `docs/PLANNING/` — Planning, queue, blockers, matrix, gap list
