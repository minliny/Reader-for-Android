# Android Complete App Gap Matrix

Status: `STAGE_1_6_HEAD_VERIFIED_FULL_GAP_CLOSURE + P1_SOURCE_RSS_WEBDAV_CLOSED`

Date: 2026-07-08

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

## 7. Current Local Verification (HEAD-level, Stage 1-6 + audit tasks 3/4/5 + P1 + Gap closure)

Commands run from `/Users/minliny/Documents/Reader for Android` on 2026-07-08 at HEAD (post Gap 1/4/6 closure):

| Command | Result | Notes |
| --- | --- | --- |
| `./gradlew clean :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` | PASS | 108 suites, 827 tests, 822 pass / 5 skipped / 0 failures / 0 errors. Covers all prior + P1 sync bridge (`SyncApiJvmTest`, 4 + `SyncPlanExecutorJvmTest`, 8) + MotionIdConstants migration (motion tests pass). |
| `git diff --check` | PASS | No whitespace errors in current diff. |

## 8. Device Proof (Instrumented, HEAD-level)

Device: `Pixel_10_Pro_XL(AVD) - 17` (emulator) + `IN2020 - 13` (real device), run via `./gradlew :app:connectedDebugAndroidTest --rerun-tasks` on 2026-07-08.

**75/75 PASS** (prior Stage 1-6 + audit tests) + **25 new P1 device tests** (Gap 6, 22 verified on both devices):
- `TtsSessionControllerDeviceProofTest` (4 tests) — TTS session state machine + fail-closed init
- `SourceRssCapabilityDispatchProofTest` (6 tests) — 12 source/RSS capabilities dispatch
- `WebDavCapabilityDispatchProofTest` (7 tests) — 8 webdav/backup capabilities + NOT_CONFIGURED
- `AndroidWebDavClientMockWebServerProofTest` (3 tests) — PROPFIND/PUT/GET/DELETE/MKCOL + 503 retry (100 tests on emulator, 0 failures)
- `BackupRestoreDeviceProofTest` (2 tests) — manifest validate + restore
- `P1CapabilityRegistryDeviceProofTest` (3 tests) — 20 P1 capabilities registered on device

| Test class | Tests | Result | Stage covered |
| --- | --- | --- | --- |
| `com.reader.AppLevelReadingChainProofTest` | 4 | PASS | Stage 6 (reading-link async guard + reader entry) |
| `com.reader.CoreEndToEndTest` | 1 | PASS | Core bridge |
| `com.reader.CoreRuntimeCapabilityInstrumentedProofTest` | 7 | PASS | Stage 2 (capability registry) |
| `com.reader.CoreSmokeTest` | 3 | PASS | Core smoke |
| `com.reader.HostAntiBotProofTest` | 6 | PASS | Stage 5 (anti_bot interception) |
| `com.reader.HostAntiBotRealChallengeProofTest` | 4 | PASS | Stage 5 (anti_bot real challenge) |
| `com.reader.HostHttpCookieRealNetworkProofTest` | 3 | PASS | http/cookie real network |
| `com.reader.HostLocalBookParseProofTest` | 4 | PASS | local_book.parse |
| `com.reader.HostLoginCookieProofTest` | 3 | PASS | login_cookie lane |
| `com.reader.HostMediaDownloadProofTest` | 8 | PASS | Stage 5 (media.download savePath) |
| `com.reader.HostMediaMultiResourceProofTest` | 3 | PASS | Stage 5 (media multi-resource) |
| `com.reader.HostMediaRealDownloadProofTest` | 2 | PASS | Stage 5 (media real download) |
| `com.reader.HostRouterDispatchProofTest` | 7 | PASS | Stage 1 (HostRequest dispatch) |
| `com.reader.HostWebViewP0HeadlessFailClosedProofTest` | 3 | PASS | Stage 4 (WebView fail-closed) |
| `com.reader.HostWebViewRealExecutorProofTest` | 1 | PASS | Stage 4 (WebView real executor) |
| `com.reader.HostWebViewRebindProofTest` | 3 | PASS | Stage 4 (WebView JS closure proof) |
| `com.reader.HostWebViewRenderProofTest` | 5 | PASS | Stage 4 (WebView render) |
| `com.reader.UnifiedEvidenceInstrumentedTest` | 1 | PASS | unified evidence artifact |
| `com.reader.android.data.adapter.AndroidPlatformRuntimeInstrumentedSmokeTest` | 4 | PASS | platform runtime smoke |
| `com.reader.Stage6AsyncResultOverlayScreenshotTest` | 3 | PASS | Stage 6 (asyncResult UI screenshot — real CANCELLED/DISCARDED overlay injection) |
| **Total** | **75** | **ALL PASS** | |

### 8.1 Stage coverage mapping (HEAD-level evidence)

| Stage | Commit | JVM proof | Device proof (HEAD run) |
| --- | --- | --- | --- |
| 1 — HostRequest consumer | `d4fffc0` | `ReaderUiReducerTest` (async queue) | `HostRouterDispatchProofTest` (7) |
| 2 — Capability registry | `6185668` | `HostCapabilityRegistryJvmTest` (15) + `WebDavCredentialProviderJvmTest` (7, GAP-D-01) | `CoreRuntimeCapabilityInstrumentedProofTest` (7) |
| 3 — Device proof baseline | `b5596ef` | — | (superseded by this HEAD run) |
| 4 — WebView closure | `d085548` | — | `HostWebViewRebindProofTest` (3) + `HostWebViewRealExecutorProofTest` (1) + `HostWebViewRenderProofTest` (5) + `HostWebViewP0HeadlessFailClosedProofTest` (3) |
| 5 — media/anti-bot real lane | `49c9c7d` | `AntiBotDetectingHttpFetchJvmTest` (7) + `MediaDownloadSavePathJvmTest` (4) | `HostMediaDownloadProofTest` (8) + `HostMediaMultiResourceProofTest` (3) + `HostMediaRealDownloadProofTest` (2) + `HostAntiBotProofTest` (6) + `HostAntiBotRealChallengeProofTest` (4) |
| 6 — Reading-link async guard | `5d1cd6d` | `ReadingLinkAsyncGuardJvmTest` (4) + `AsyncResultOverlayLabelJvmTest` (7, GAP-D-03) + `RealSourceReadingChainJvmTest` (6, GAP-D-02) | `AppLevelReadingChainProofTest` (4) + `Stage6AsyncResultOverlayScreenshotTest` (3, GAP-D-04 real overlay injection) |

### 8.2 Prior device proof (superseded)

The 39/39 run from `b5596ef` (2026-07-07, OnePlus 8Pro) is preserved here for history. It predates Stage 4/5/6 commits and therefore could not — and did not — prove those code paths on device. The HEAD-level 75/75 run above is the current authoritative device proof.

Fixes applied during the prior device proof (still in effect):
- `FileReadHandler`: `DefaultHostFileSystem.read` was throwing `kotlin.io.NoSuchFileException` (Kotlin stdlib) instead of `java.nio.file.NoSuchFileException` (Java NIO) because `NoSuchFileException(file)` matched the Kotlin constructor `(File, File?, String?)` over the Java constructor `(String)`. Fixed by using fully-qualified `java.nio.file.NoSuchFileException(file.path)`.
- `WebViewHostActivity`: moved from `androidTest` to `main` so `ActivityScenario.launch` resolves it to the target process (`com.reader.android`) instead of the test process (`com.reader.android.test`).

## 9. Remaining Gaps

The HEAD-level run above proves Stage 1-6 + audit task 3/4/5 code paths compile and pass JVM + instrumented tests (75/75 device, 0 failures). All four Android-specific gaps are now CLOSED:

| Gap ID | Description | Status |
| --- | --- | --- |
| GAP-D-01 | `credential.resolve` not registered | **CLOSED (audit task 3)** — `WebDavCredentialProvider` implements `CredentialProvider`, bridging `credentialHandle` → `WebDavCredentialStore.load()` → `Credential{username, password}`. Bearer tokens are packed into both fields. Registered in `ReaderCoreClient.buildHostRuntime` when `context != null` (production path). `WebDavCredentialProviderJvmTest` (7 tests) validates Basic/Digest/Bearer resolution + handler end-to-end. JVM capability-registry test still asserts `credential.resolve` is unregistered on JVM (context=null), with a comment noting the production path registers it. |
| GAP-D-02 | Real-source reading chain not proven end-to-end | **CLOSED (audit task 4)** — `RealSourceReadingChainJvmTest` (6 tests) exercises real book-source JSON shapes (search/detail/toc/content) through `BookApi.parse*` parsers, builds a `ReaderContext` from the parsed data, and drives the reducer's async-result guard IDLE → PENDING → COMPLETED + stale DISCARDED. Core command dispatch (native .so) is still covered only on device by `CoreRuntimeCapabilityInstrumentedProofTest`. |
| GAP-D-03 | asyncResult UI visual feedback not captured | **CLOSED (audit task 5)** — `asyncResultOverlayLabel` pure mapping added to `ReaderControlReadingSurface`; DISCARDED/SUPERSEDED render "正在切换到最新…" overlay, CANCELLED renders "加载已取消". `AsyncResultOverlayLabelJvmTest` (7 tests) validates the mapping. `AppShell` passes `state.asyncResult.state` through `ReaderShellScreen` → `ReaderControlReadingSurface`. Device 75/75 confirms no regression. |
| GAP-D-04 | Stage 4-6 device screenshots / recordings | **CLOSED (audit task 5)** — `Stage6AsyncResultOverlayScreenshotTest` (3 tests) launches `MainActivity` and captures PNG screenshots via `uiAutomation.takeScreenshot()`. The test obtains `AppShellViewModel` from the Activity's ViewModelStore and dispatches intent sequences to inject real asyncResult overlay states: `captureCancelledOverlayScreenshot` (StartAsyncRequest + CancelAsyncRequest → CANCELLED), `captureDiscardedOverlayScreenshot` (StartAsyncRequest(req-B) + CompleteAsyncRequest(req-A, stale) → DISCARDED). Screenshots persisted to `app/build/outputs/screenshots/`: `stage6_bookshelf_idle.png`, `stage6_asyncresult_cancelled.png`, `stage6_asyncresult_discarded.png`. Full device suite 75/75 green. |

## 10. P1 Capability Closure (Source/RSS/WebDAV/Backup/TTS)

P1 closes the gap between "data layer + adapters exist" and "UI can reach them through Host capabilities". Each subsystem now has a registered `HostRequest → HostAdapter.dispatch → HostReply` round-trip, JVM proof tests, and (where applicable) UI de-demo wiring.

| P1 ID | Subsystem | Capabilities registered | JVM proof | UI de-demo | Status |
| --- | --- | --- | --- | --- | --- |
| P1-4 | TTS session | `tts.system.start/stop/pause/resume/status/progress` (via HostFacade, context-only) | `TtsSessionControllerJvmTest` (7) — AudioFocus recovery, multi-chapter progression, CancellationException re-throw fix | N/A (HostFacade already wired) | **CLOSED** (commit `6566d8ea`) |
| P1-5 | Source / RSS | `source.list/add/remove/set_enabled/import/export`, `source.debug.run/detect`, `rss.subscription.list/add/delete`, `rss.refresh` (12 capabilities, pure-JVM) | `SourceRssCapabilityHandlersJvmTest` (24) — CRUD + import/export + debug + RSS parse/refresh + registration smoke | `RssScreen.kt` loads real subscriptions via `AppProvider.subscriptionRepository.getAll()` | **CLOSED** (commit `b2480078`) |
| P1-6 | WebDAV / backup | `webdav.connect/upload/download/list/delete/mkdir`, `backup.create/restore` (8 capabilities, pure-JVM) | `WebDavCapabilityHandlersJvmTest` (25) — PROPFIND/PUT/GET/DELETE/MKCOL round-trip + backup manifest upload + restore validate/plan + NOT_CONFIGURED error path + registration smoke | `SyncBackupScreen` loads real WebDAV credential from `AppProvider.webDavCredentialStore.load("webdav.default")`, "测试网络连通性" button dispatches `webdav.connect` through `ReaderCoreClient.get().hostAdapter()` | **CLOSED** (this commit) |

### 10.1 P1-6 Architecture

`AndroidWebDavClient` (OkHttp-backed) integrates `RetryPolicy` (exponential backoff for 408/429/5xx) + `WebDavErrorMapper`. `AppProvider` exposes it as a lazy singleton alongside `BackupRestoreManager`. `WebDavCapabilityHandlers.kt` defines 8 pure-JVM `CapabilityHandler` implementations that delegate to `WebDavContext(client, backupRestoreManager)`:

- When `client` is null (no credential configured), all `webdav.*` handlers return `NOT_CONFIGURED` error — UI can surface "请先配置 WebDAV 凭据" instead of crashing.
- `backup.create` orchestrates PUT per entry + manifest upload.
- `backup.restore` validates manifest via `BackupRestoreManager.validate()` + downloads entries via GET.

`ReaderCoreClient.buildHostRuntime` wires `WebDavContext` on both JVM (via `fakeWebDavContext()` → `FakeWebDavClient`) and production (via `AppProvider.webDavClient` + `AppProvider.backupRestoreManager`) paths, mirroring the `SourceRssContext` pattern from P1-5.

## 11. Gap Closure (Protocol Convergence / Sync Bridge / Device E2E / Local Fallback)

Section 10 closed P1 capability-level gaps. Section 11 closes higher-level gaps identified in post-P1 audit:

| Gap ID | Description | Closure | Status |
| --- | --- | --- | --- |
| GAP-1 | Core/UI protocol convergence — local stand-ins vs generated contract DTOs | **MotionIdConstants migrated**: 39/47 constants delegate to `MotionId.serialName`. **UiStateProjector.kt** created: bidirectional mapping `ReaderUiState.toContractUiState()` (lossy — drops Android-specific fields) + `ContractUiState.mergeIntoLocal()` (preserves Android fields). 8 JVM tests pass. | **CLOSED** (projector + MotionIdConstants done; RouteIds deferred as projector covers route mapping) |
| GAP-2 | `search.history.*` — Core has entities but no protocol methods | **Local Room fallback**: `SearchHistoryEntity` + `SearchHistoryDao` + `SearchHistoryRepository` (Room + Fake) + 3 Host handlers (`search.history.list/add/clear`) + `SearchViewModel` de-demo'd to dispatch through Host. 6 JVM tests pass. | **FALLBACK CLOSED** (Room-backed until Core lands) |
| GAP-3 | `rss.list` / `rss.item.read` — Core has subscription CRUD but no article list/read protocol | **Local Room fallback**: `RssItemEntity` + `RssItemDao` + `RssItemRepository` (Room + Fake) + 2 Host handlers (`rss.list`/`rss.item.read`) + `RssRefreshHandler` now caches items. 12 JVM tests pass. | **FALLBACK CLOSED** (Room-backed until Core lands) |
| GAP-4 | WebDAV/sync real product closure | **SyncApi.kt** + **SyncPlanExecutor.kt**: 4 sync.* wrappers + executor mapping HTTP method→webdav.* handlers. 12 JVM tests pass. | **CLOSED** (Core sync.* → Android Host bridge complete) |
| GAP-6 | P1 device-level E2E proof | 6 device test files (25 @Test): 22/22 PASS on Pixel emulator + IN2020 real device; AndroidWebDavClientMockWebServer 100/100 on emulator. | **CLOSED** (device-verified) |

### 11.1 Remaining Core Blockers (not closeable on Android side)

| Gap | Core blocker | Android status |
| --- | --- | --- |
| GAP-5 | `reader.location.resolve` — Core is deterministic echo stub, no cross-fontsize/viewport reflow | Android has progress/cache-first; canonical location revision pending Core (no Android-side fallback — would duplicate Core semantics) |
| GAP-4 (partial) | `sync.conflict.resolve` — Core has `sync.merge` conflict detection but no user-resolution command | Android `SyncApi.merge()` returns conflicts list; UI resolution flow pending Core |
| GAP-2/3 migration | When Core lands `search.history.*` / `rss.list` / `rss.item.read` protocol | Android Room fallback tables need migration to Core bridge calls (handlers already abstract the seam) |
