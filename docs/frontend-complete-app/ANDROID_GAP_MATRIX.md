# Android Complete App Gap Matrix

Status: `SLICE_0_1_ANDROID_SOURCE_AUDIT_REFRESHED`

Date: 2026-07-07

Target repo: `/Users/minliny/Documents/Reader for Android`

Parent matrix: `docs/frontend-complete-app/FRONTEND_COMPLETE_APP_GAP_MATRIX.md`

## 1. Purpose

This file turns the parent gap matrix into Android-specific work. This pass inspected the current Android repo source for Slice 0/1 roles and records concrete local paths, commands, and gaps. Device screenshots/recordings remain separate evidence and are not claimed here.

Android must implement native Compose UI. It must not ship `frontend-demo/` through WebView as the production app.

## 2. Android Preflight

| Check | Current local evidence | Required result |
| --- | --- | --- |
| Repo exists | `/Users/minliny/Documents/Reader for Android` exists. | Use this repo for Android implementation evidence. |
| Gradle entry exists | `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradlew` exist. Local commands: `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest`. | Build and test commands must be documented from this repo. |
| Reader UI contract dependency | `app/build.gradle.kts` declares `src/main/reader-ui-contract/kotlin` as the local generated Kotlin contract source-set entry. Reader UI generated Kotlin files from `/Users/minliny/Documents/Reader UI/generated/kotlin` are copied under that path with their `AUTO-GENERATED` headers intact, including `MotionSpecRegistry` and `TokenRegistry`. Current local snapshot has 84 motion specs and 117 tokens. Existing app code still uses local equivalents such as `RouteIds`, `ReaderUiIntent`, `ReaderUiState`, and `MotionIdConstants`. | Android must continue replacing local stand-ins with Reader UI generated Kotlin types or generated artifacts from the same schema. |
| Production UI entry | `app/src/main/kotlin/com/reader/ui/MainActivity.kt` -> `ReaderTheme` -> `ReaderApp` -> `AppShell`; `AppShell.kt` renders native Compose routes and `FloatingPillTabBar`. No production WebView wrapper for `frontend-demo` was found in the app shell path. | Production UI must be Compose/native, not WebView loading the demo. |

## 3. Required Android Files Or Equivalents

The audit should find these files or equivalent locally named modules:

| Required role | Current Android landing | Acceptance |
| --- | --- | --- |
| Contract import | Source-set path: `app/src/main/reader-ui-contract/kotlin` in `app/build.gradle.kts`; copied generated files include `Route.kt`, `UiEvent.kt`, `UiState.kt`, `ViewState.kt`, `Motion.kt`, `Token.kt`, Core/Host DTOs, supporting schema DTOs, `MotionSpecRegistry`, and `TokenRegistry`. Local equivalents still live in `app/src/main/kotlin/com/reader/ui/shell/ReaderUiState.kt`, `ReaderUiIntent.kt`, `ReaderUiReducer.kt`, and `app/src/main/kotlin/com/reader/ui/motion/MotionController.kt`. | RouteId, UiEvent, UiState, ViewState, MotionId, Token types compile in app code from generated contract types. Current gap: shell/reducer route/state stand-ins are still not migrated. |
| App reducer | `app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt` | Owns route, overlay, activeSession, focus, loading, async guard. |
| ViewModel / coordinator | `app/src/main/kotlin/com/reader/ui/shell/AppShellViewModel.kt` | Converts UiEvent to reducer actions and Core/Host effects. Current gap: reducer dispatch is local UI only for Slice 1; Core effect coordinator is not complete for vertical slices. |
| ViewState mapper | Route-level mapping currently lives in `app/src/main/kotlin/com/reader/ui/shell/AppShell.kt`, `ReaderNavHost.kt`, and route-specific state hosts such as `BookshelfTabStateHost`. | Native screens render ViewState or a lossless mapped DTO. Current gap: no generated `ViewState` DTO mapper yet. |
| Core bridge | `app/src/main/kotlin/com/reader/android/data/bridge/ReaderCoreBridge.kt`, `app/src/main/kotlin/com/reader/android/data/bridge/CoreBridge.kt`, JNI entry under `app/src/main/cpp/reader_jni.cpp`, Java runtime under `app/src/main/java/com/reader/core/*`. | Maps reducer effects to Reader-Core-Native commands/events. Current gap: not wired as Slice 1 AppShell effects. |
| Host Adapter | Java host boundary under `app/src/main/java/com/reader/host/*`; Android adapters under `app/src/main/kotlin/com/reader/android/data/adapter/*` including `AndroidWebRuntimeAdapter`, `AndroidTtsAdapter`, `AndroidPermissionRuntimeAdapter`, `AndroidPlatformRuntimeAdapters`, `ReaderShellHost`. | Owns HTTP, WebView, Cookie, file, permission, TTS, background, share, notification capability calls without mutating UI directly. |
| Token Adapter | `app/src/main/kotlin/com/reader/ui/tokens/ReaderTokenAdapter.kt`; existing Compose theme tokens in `app/src/main/kotlin/com/reader/ui/theme/ReaderTheme.kt`. | Adapter now reads generated `TokenRegistry` values for motion-duration tokens, including semantic duration names from generated `MotionSpecRegistry` such as `app.motion.duration.tabSwitch`. Current gap: local enum accessors remain as Android compatibility wrappers, and existing screens still need raw-value migration. |
| Motion Adapter | `app/src/main/kotlin/com/reader/ui/motion/ReaderMotionAdapter.kt`; runtime transaction controller in `MotionController.kt`; token constants in `MotionTokens.kt`. | Adapter now reads duration/easing/token metadata from generated `MotionSpecRegistry.spec(MotionId)`, with legacy Android string aliases for current shell callers. Current gap: Android still has naming stand-ins such as `tab.item.switch` vs generated `tab.switch`, plus local `MotionController` final-state/runtime table. |
| Evidence tests | `app/src/test/kotlin/com/reader/ui/shell/ReaderUiReducerTest.kt`, `app/src/test/kotlin/com/reader/ui/motion/MotionControllerTest.kt`, `app/src/test/kotlin/com/reader/ui/tokens/ReaderTokenAdapterTest.kt`, `app/src/test/kotlin/com/reader/ui/motion/ReaderMotionAdapterTest.kt`. Screenshots exist under `screenshots/`, but new Slice 1 recording evidence was not produced in this pass. | Proves native behavior, not browser demo behavior. |

## 4. Android P0 Matrix

| ID | Gap | Evidence to collect | Acceptance command or artifact |
| --- | --- | --- | --- |
| AND-P0-01 | Generated Kotlin contract source-set is populated and compiles | `app/src/main/reader-ui-contract/kotlin/*.kt` copied from Reader UI generated Kotlin with `AUTO-GENERATED` headers intact; `app/src/main/reader-ui-contract/README.md` records the source. `ReaderMotionAdapter` uses generated `MotionSpecRegistry`; `ReaderTokenAdapter` reads generated `TokenRegistry`. Remaining local stand-ins: `RouteIds`, `ReaderUiIntent`, `ReaderUiState`, `MotionIdConstants`, local token enum compatibility wrappers, and local `MotionController` runtime/final-state table. | Keep `./gradlew :app:compileDebugKotlin` green. Next: replace shell/reducer route/state DTOs with generated `RouteId`, `UiEvent`, `UiState`, `ViewState`; remove local motion/token aliases after generated contract naming is adopted by shell callers. |
| AND-P0-02 | AppShell + four main tabs implemented in native Compose; device evidence still incomplete for this slice | `AppShell.kt`, `FloatingPillTabBar.kt`, `ReaderApp.kt`, `MainActivity.kt`; reducer tests prove tab switch does not push route stack. | Add native recording `slice-1-android-cold-start.mov`, `slice-1-android-tab-switch.mov`, and state screenshots after build is green. |
| AND-P0-03 | Reducer exists and has JVM tests | `ReaderUiReducer.kt`; tests in `ReaderUiReducerTest.kt` cover tab switch, route push/pop, reader entry, activeSession mutex, reduced motion, async guard, source import, WebDAV state, RSS route ids. | Keep expanding to focus restore/loading golden cases as generated contract DTOs land. |
| AND-P0-04 | Bookshelf to immersive reading not proven | Compose route, reducer transition, Core bridge call, recording. | Open book enters `immersive-reading`, returns to source, repeated click keeps latest intent. |
| AND-P0-05 | Reader control layer not proven | Reader surface, overlay layer, gesture source, recording. | Control layer opens/hides without remounting reader context or changing text layout. |
| AND-P0-06 | TokenAdapter reads generated `TokenRegistry`; full adoption not proven | `ReaderTokenAdapter.kt`, `ReaderTokenAdapterTest.kt`, `ReaderTheme.kt`; tests cover `TokenRegistry.token("--reader-ds-motion-duration-tabSwitch")` and semantic `app.motion.duration.tabSwitch` resolution through generated registry value. | Add raw-value lint/grep gate and migrate screen code away from ad hoc values before claiming complete. |
| AND-P0-07 | MotionAdapter reads generated `MotionSpecRegistry`; full P0 coverage not complete | `ReaderMotionAdapter.kt`, `ReaderMotionAdapterTest.kt`, `MotionController.kt`, `MotionControllerTest.kt`; tests cover `MotionSpecRegistry.spec(MotionId.TabSwitch)` and generated `MotionId.AppRoutePushForward`. | Replace legacy aliases after contract naming is aligned and add Compose/device motion evidence. |
| AND-P0-08 | Core bridge source exists; Slice 1 UI effects are not wired to Core | `ReaderCoreBridge.kt`, `CoreBridge.kt`, `ReaderCoreRuntime.java`, `NativeCoreBridge.java`, protocol tests under `app/src/test/kotlin/com/reader/android/data/bridge/*`. | P0 UiEvents emit CoreCommand/HostRequest and stale results cannot overwrite current state. |
| AND-P0-09 | Host Adapter source and tests exist | `app/src/main/java/com/reader/host/*`, `app/src/main/kotlin/com/reader/android/data/adapter/*`, tests under `app/src/test/kotlin/com/reader/android/data/adapter/*` and `app/src/test/kotlin/com/reader/host/*`. | Confirm Slice 1/2 UI never mutates host capability state directly. |
| AND-P0-10 | Existing screenshot directory present, but no new Slice 1 recording from this pass yet | `screenshots/*.png`; command output from Gradle checks should be attached to the worker report. | Evidence exists for Slice 1 to Slice 5 before broad route migration. |

## 5. Android Route Implementation Matrix Template

Every implemented route should be tracked with this table:

| RouteId | Priority | Compose owner | ViewState input | UiEvent output | Core/Host effect | MotionIds | Token groups | Tests/evidence | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| bookshelf | P0 | `AppShell.kt` + `BookshelfScreen.kt` + `BookshelfTabStateHost` | Local `BookshelfTabStateHost`/demo-backed state, not generated ViewState yet | `ReaderUiIntent.SelectTab`, push search/import/more routes, reader entry intents | Core bookshelf snapshot not wired to AppShell reducer yet | `tab.item.switch`, `bookshelf.view.switch` pending adapter expansion | app shell, card/list/tab tokens via `ReaderTokenAdapter` + `ReaderTheme` | `ReaderUiReducerTest`, future Slice 1 recordings | Source audited; generated ViewState gap |
| immersive-reading | P0 | `ReaderShellScreen` / `ImmersiveReadingScreen.kt` | `ReaderContext` local DTO | `EnterReaderFromCover`, `EnterReaderFromAction`, page/session intents | content load/progress bridge not wired in Slice 1 | `reader.entry.*`, `reader.page.turn.next/prev` | reader typography/theme tokens | `ReaderUiReducerTest`, `ReaderMotionAdapterTest` | Skeleton audited; Core vertical gap |
| reader | P0 | `ReaderShellScreen` / `ReaderControlScreen.kt` | `ReaderContext`, `ReaderControlState`, `ActiveSession` | reader control/module/session intents | progress/session bridge not complete | `reader.control.hide`, `reader.module.switch`, session motions | reader control tokens via theme + adapter | `ReaderUiReducerTest`, `MotionControllerTest` | Skeleton audited; overlay evidence gap |
| discover | P0 Slice 1 tab | `AppShell.kt`, `DiscoverScreen.kt`, `DiscoverDemoRouteScreen.kt` | local demo route state | `SelectTab(MainTab.DISCOVER)` and pushed discover demo routes | source/search bridge deferred | `tab.item.switch` | app shell/list/card tokens | `ReaderUiReducerTest` | Source audited |
| rss | P0 Slice 1 tab | `AppShell.kt`, `RssScreen.kt`, RSS route screens | `RssTabState`/local route states | `SelectTab(MainTab.RSS)` and RSS push routes | RSS Core bridge deferred | `tab.item.switch` | app shell/list/rss tokens | `ReaderUiReducerTest` | Source audited |
| settings | P0 Slice 1 tab | `AppShell.kt`, `SettingsScreen.kt`, settings subpage screens | local settings state | `SelectTab(MainTab.SETTINGS)` and settings push routes | Host/settings adapters exist; reducer bridge deferred | `tab.item.switch` | app shell/list/settings tokens | `ReaderUiReducerTest` | Source audited |
| source-switch | P1 | `FlowShellScreen` from reader shell | `ReaderContext` local DTO | `PushRoute(SourceSwitchFlow)` | source switch/search bridge deferred | `reader.sourceSwitch.open-close` pending adapter expansion | source/reader tokens | future Slice 3/5 tests | Pending vertical slice |

## 6. Android Acceptance Minimum

Android cannot be marked frontend-complete until:

1. It compiles against Reader UI generated Kotlin contract types.
2. Reducer golden tests pass for P0 state rules.
3. P0 native screens render from ViewState or lossless mapped contract DTOs.
4. TokenAdapter and MotionAdapter are present and tested.
5. Core bridge and Host Adapter are connected for first vertical slices.
6. Device/simulator evidence exists for AppShell, reading entry, reader control layer, overlay/focus, session capsule, and orientation.

## 7. Current Local Verification

Commands run from `/Users/minliny/Documents/Reader for Android` on 2026-07-07:

| Command | Result | Notes |
| --- | --- | --- |
| `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` | PASS | Current run completed successfully in 9s. `:app:kspDebugKotlin`, `:app:compileDebugKotlin`, `:app:kspDebugUnitTestKotlin`, `:app:compileDebugUnitTestKotlin`, and `:app:testDebugUnitTest` all passed. The older KSP/cache blocker is no longer current. |
| `git diff --check` | PASS | No whitespace errors in current diff. |
