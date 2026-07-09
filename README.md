# Reader for Android

Android reading app — Compose + Material 3 native host for Reader-Core-Native and Reader UI Contract.

## P0 链路闭环交付（2026-07-10）

Reader for Android 完成 Contract-first Native UI Architecture 的 P0 链路全闭环。5 条 P0 链路（bookshelf / reader / source-switch / book-detail / settings）× A-F 六列全部 ✅，矩阵 120/120 全绿。

### 交付成果

**B1 — MotionPolicyAdapter 接入 + source-switch reducer（3 commits）**
- `MotionPolicyAdapter` 接入 `AppShellViewModel`，生产环境调用 `MotionPolicyAdapter.resolve`
- source-switch 专用 reducer intent 落地（`ReaderUiReducer` 中 handler 非 stub）
- `FlowShell` smoke 接线验证

**B2-B4 — reducer + focused tests + token 清理（2 commits）**
- book-detail / settings reducer intent 补齐
- 24 focused tests 落地（覆盖各链路 reducer / motion / screen）
- raw `Color(0x` / `zIndex` 字面量全清理，统一引用 `ReaderTheme.tokens.*` 语义 token

**B6 — BookshelfRoute + 非 P0 文件清理（1 commit）**
- `BookshelfRouteScreens` 接线
- 非 P0 文件 raw `Color` 清理

### 验收

- `./gradlew compileDebugKotlin`：BUILD SUCCESSFUL
- `./gradlew test`：899 tests，0 failures
- P0 链路矩阵：120/120 全绿（退出码 0）

### 遗留

- `SourceSwitchScreenSmokeTest` 需设备/模拟器运行（instrumented test）
- `ReaderControlScreen` 残留 raw `dp` 标注为 demo 布局常量（非 token 化目标）

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
