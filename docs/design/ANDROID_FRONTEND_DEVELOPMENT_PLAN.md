# Android Frontend Development Plan

Status: `ANDROID_FRONTEND_DEVELOPMENT_PLAN`

Date: 2026-07-04

Scope: 将 `Reader UI/frontend-demo/` 的 Android 前端骨架对齐工作拆成可派工、可验收、可持续更新的开发计划。本文档承接 `ANDROID_FRONTEND_DEMO_ALIGNMENT_SPEC.md` 和 `ANDROID_FRONTEND_DEMO_MAPPING_TABLE.md`，不替代逐行映射表，也不把 web demo 的 DOM/CSS/JS 当作 Android 可复制实现。

## Planning Baseline

当前规划可以支撑的不是“直接发版”，而是以下四类开发：

1. 原生 AppShell、四主 tab、pushed route、reader route 和 back stack 的继续开发。
2. 根据 demo 结构翻译 Compose 组件、token、图标、motion ID 和 route 语义。
3. 将现有 fixture/prototype 页面逐步接入真实 repository、Core bridge、Android intent、WebView、文件选择器和持久化。
4. 用 reducer/unit proof、route coverage、Gradle proof 和设备截图逐步关闭 `Partial` 行。

截至 2026-07-04 的当前事实：

- Mapping table 共 65 行：`Aligned 5`、`Partial 57`、`Deferred 3`、`Gap 0`。
- `node scripts/verify_demo_route_coverage.mjs` 已覆盖 demo route 分类：demo routes 131、native direct 65、typed family 65、context-bound 1、generic fallback 0。
- `screenshots/` 当前有 27 个文件，其中 22 个是编号验收图 `01` 到 `22`，5 个是 `tmp-*` 临时图，临时图不能作为完成证据。
- 编号验收图还缺 `23` 到 `56`，共 34 张；`13-rss-original-route-device.png` 在 RSS original route 变化后也需要刷新。

## Completion Model

`Aligned` 的含义：route/state/component 已经可以作为后续开发基座。除非验收命令或设备截图失败，不要重写这些行。

`Partial` 的含义：Android 已有页面或状态承载，但至少还缺一类收口工作：真实数据、持久化、交互状态、motion adapter、设备截图、runtime intent、WebView、文件选择器或 repository mutation。

`Deferred` 的含义：demo 已定义目标，但当前不应当伪装成完成。只有在对应阶段启动后，才允许把它拆成 `Partial` 或 `Aligned`。

可声明完成的最低门槛：

1. 对应 mapping row 的 native component 和 state owner 明确。
2. route ID 与 demo contract 一致，且 `verify_demo_route_coverage.mjs` 不退化。
3. 如果改导航或全局状态，补 `ReaderUiReducerTest` 或同级状态测试。
4. 如果改业务数据，补 repository/adapter 单测或可复现 fixture。
5. 如果改可见界面，补 compact portrait 设备截图，截图文件名写回 mapping table。
6. 通过相关 Gradle proof 与 `git diff --check`。

## Development Phases

### P0 - Baseline And Evidence Gate

目标：把现有骨架变成可持续开发的检查门。

范围：

- 固化 `ANDROID_FRONTEND_DEMO_MAPPING_TABLE.md` 的 65 行状态。
- 把 `node scripts/verify_demo_route_coverage.mjs` 作为每次前端骨架改动的必跑命令。
- 保留 `Aligned / Partial / Deferred` 三类状态，不用百分比掩盖缺口。
- 把 `tmp-*` 截图和正式编号截图分开管理。

完成条件：

- `node scripts/verify_demo_route_coverage.mjs` 输出 `generic fallback: 0`。
- `./gradlew :app:testDebugUnitTest` 通过或给出明确失败归因。
- `git diff --check` 通过。
- mapping table 中新增或变更的 route 都有 state owner、motion ID、data source 和 evidence。

非目标：

- 不在 P0 接真实业务数据。
- 不把未截图页面标成 `Aligned`。

### P1 - Shell, Navigation, Reader Entry

目标：稳定后续所有页面的导航和 reader 入口，不再返工主骨架。

范围：

- `ReaderUiState.kt`
- `ReaderUiIntent.kt`
- `ReaderUiReducer.kt`
- `AppShell.kt`
- `ReaderNavHost.kt`
- `FloatingPillTabBar.kt`
- `ImmersiveReadingScreen.kt`
- `ReaderControlScreen.kt`

当前状态：

- 四主 tab、tab switch 非 push、pushed route、reader entry、reader context、reduced motion 已具备测试基础。
- Reader control route 已拆出，但仍属于 `Partial`，还缺 compact portrait 证明、session/runtime 深度和 motion timing adapter。

完成条件：

- tab switch 不污染 back stack。
- `book-search`、`source-import-preview`、RSS/Settings/Bookshelf 管理 route 均通过 reducer push/pop proof。
- 从 cover/action 进入 `immersive-reading` 的 final state 保留正确 `ReaderContext` 和 motion ID。
- 中部 tap 才进入 reader control；reader entry final state 不自动显示 control sheet。

### P2 - Bookshelf And Book Search

目标：把书架和搜索从 demo fixture 骨架推进到可用的原生业务入口。

范围：

- `BookshelfScreen.kt`
- `BookshelfViewModel.kt`
- `BookshelfManagementScreens.kt`
- `BookshelfRouteScreens.kt`
- `SearchScreen.kt`
- `SearchViewModel.kt`
- `DemoCoverAssets.kt`
- `ReaderImage.kt`

当前状态：

- 书架结构、continue card、top bar、section header、filter popover、more menu、cover/list state 已有 Compose 实现。
- Search shell、idle state、suggestion、result row 已有 Compose 实现。
- 缺真实书架列表、封面渲染、搜索历史持久化、真实搜索结果设备证明、book focus menu。

开发顺序：

1. 接入真实 bookshelf repository 或明确的 Room/fixture fallback 分层，去掉只靠首字母封面的验收路径。
2. 完成 cover/list press、focus、selected 状态，并为 cover grid/list view 增补状态测试。
3. 将搜索历史、建议词和最近结果落到持久化或明确可替换 repository。
4. 用真实导入书源跑出搜索结果，并补 `book-search` result-row 设备截图。
5. book focus menu 等 overlay/focus 基础完成后再从 `Deferred` 切入。

完成条件：

- `screenshots/01-bookshelf-device.png` 刷新后没有空白裁切、主结构与 demo 一致。
- `screenshots/02-book-search-device.png`、`03-book-search-state-device.png` 保持有效。
- 新增真实搜索结果截图并写回 mapping table。
- 所有书架/搜索状态可以从 ViewModel 或 reducer 单一状态解释。

### P3 - Source Import, Settings, And Local Management

目标：关闭“页面有了但行为还是本地假数据”的管理类缺口。

范围：

- `ImportBookSourceScreen.kt`
- `ImportBookSourceViewModel.kt`
- `SourceDemoRouteScreen.kt`
- `SourceDemoRouteState.kt`
- `SettingsScreen.kt`
- `SettingsSubpageScreens.kt`
- `BookSourceRepository.kt`
- `DataStoreBookSourceRepository.kt`
- WebDAV、backup、permission、file picker 相关 adapter。

当前状态：

- source import preview、settings main tab、settings subpages、bookshelf batch/group/local import/search settings 都已是自定义 Compose prototype。
- 单书源 JSON 预览与导入路径存在；batch JSON 只预览，confirm 行为仍受单源 bridge 限制。
- settings/WebDAV/source management 多数是本地状态，缺真实保存、测试连接、权限 intent、诊断/反馈 intent 和设备证明。

开发顺序：

1. 先补 source import 单源成功/失败 e2e 证明，再决定 batch import bridge 设计。
2. 把 settings general、bookshelf/search settings 的开关、segment、清理动作接入持久化。
3. 接 WebDAV credential、test/save/restore、backup export/import 的真实 adapter。
4. 接 source management 的检测、编辑、日志、默认源/启停持久化。
5. 捕获 `48` 到 `56` 设备截图。

完成条件：

- `source-import-preview` 有空态、解析态、成功态、错误态证明。
- `settings-general`、`webdav-config`、`source-management` 不再只保存本地 Compose state。
- `screenshots/48-*.png` 到 `56-*.png` 齐全，并移除对应 visual evidence missing 文案。

### P4 - Discover And RSS Real Data Lane

目标：把 Discover/RSS 从结构型页面推进到真实数据和操作闭环。

范围：

- `DiscoverScreen.kt`
- `DiscoverDemoRouteScreen.kt`
- `DiscoverDemoRouteState.kt`
- `RssScreen.kt`
- `RssSearchScreen.kt`
- `RssSubscriptionManagementScreen.kt`
- `RssSourceEditScreen.kt`
- `RssSourceImportScreen.kt`
- `RssRuleSubscriptionScreen.kt`
- `RssSourceGroupsScreen.kt`
- `RssSourceActionsScreen.kt`
- `RssDetailScreen.kt`
- `RssOriginalScreen.kt`
- `SubscriptionRepository.kt`
- RSS source/article/parser adapters。

当前状态：

- Discover main、RSS main、RSS search、subscription management、source edit/debug/import、rule subscription、group/action/batch/export/detail/original 等 route 已有大量 Compose skeleton。
- 主要缺真实 repository、OPML/JSON import/export、source CRUD、refresh/error/empty 状态、debug/login/WebView/cookie、read record、browser/share intent 和大量设备截图。

开发顺序：

1. 先接 RSS source/article repository，让 RSS main/detail/search 不再完全依赖 local fixture。
2. 补 refresh/loading/empty/error 状态，并保持 `rss-refreshing` 与主刷新状态的职责清晰。
3. 接 OPML/JSON import、export artifact/share、source group membership、source enabled/pin/order persistence。
4. 接 login WebView、cookie extraction、original in-app WebView、system browser intent。
5. 捕获 `23` 到 `47` 设备截图，并刷新 `13-rss-original-route-device.png`。

完成条件：

- RSS main 可以从真实 repository 渲染 source 与 article。
- RSS import/export/debug/login 至少各有一条可复现成功或失败路径。
- RSS detail/original/browser 路由有真实 intent/WebView 或明确 mock boundary。
- `screenshots/23-*.png` 到 `47-*.png` 齐全。

### P5 - Reader Control, Session, Overlay, Focus

目标：关闭当前 3 个 `Deferred` 中的 reader session 与 overlay/focus 基础，并把 reader control 从结构实现推进到可交互 runtime。

范围：

- `ReaderControlScreen.kt`
- `ReaderGestures.kt`
- `ReaderShellFrames.kt`
- `ReaderUiState.activeSession`
- `OverlayState`
- `MotionController.kt`
- `ReaderMotionComponents.kt`
- TTS、auto page、chapter/progress/runtime adapters。

当前状态：

- Reader control screen 有 top overlay、bottom sheet、brightness rail、module nav、full/utility panels。
- `activeSession` 和 `OverlayState` 作为状态字段存在，但 session capsule、TTS/auto-page runtime、keyboard/sheet/dialog/focus discipline 尚未实现。

开发顺序：

1. 先做 overlay/focus foundation：sheet/dialog/keyboard back priority、hidden hit area 清理、focus restore、interrupt cancel/redirect。
2. 再接 reader control module 的真实 chapter/progress/theme/typography/brightness 数据。
3. 实现 auto page 与 TTS 互斥 session，补 session capsule enter/update/exit。
4. 将 book focus menu 从 `Deferred` 拆到 overlay 基础之上实现。

完成条件：

- Back 先关闭最上层 overlay，再 pop route。
- `activeSession` 同时只能有 auto page 或 TTS。
- session capsule 有 reducer proof、runtime proof 和设备截图。
- hidden overlay 不保留 hit area。

### P6 - Motion, Accessibility, Adaptive Layout, Performance

目标：把“看起来像”推进到“交互语义、动效降级、可访问和多设备形态都可验收”。

范围：

- `MotionTokens.kt`
- `MotionController.kt`
- `ReaderMotionComponents.kt`
- `ReducedMotionResolver.kt`
- `ViewportClassAdapter.kt`
- reusable UI components under `app/src/main/kotlin/com/reader/ui/components/`

开发顺序：

1. 给 dropdown、filter、segment、button、list row、card、reader module、overlay sheet/dialog 统一 motion adapter。
2. reduced motion 下保留颜色/状态反馈，取消不必要 displacement、scale、long duration。
3. 把 compact portrait 之外的 compact landscape、expanded width、fold half-opened 行为纳入 ViewportState。
4. 给关键列表和 reader surface 补性能监控与 jank 排查入口。
5. 补 content description、focus order、minimum hit target、screen reader label。

完成条件：

- motion ID 来自 demo contract 或本地映射表，不能随意新增无来源 ID。
- reduced motion 测试覆盖 tab、route、reader entry、overlay/session。
- 关键页面在 compact portrait 与至少一种非 portrait viewport 下不重叠、不裁字、不保留不可见点击区域。

### P7 - Device Evidence Closure

目标：把 compile/unit/route proof 补成可审计的设备视觉证明，避免把本地临时截图或代码推断当成完成证据。

范围：

- `screenshots/01-bookshelf-device.png`
- `screenshots/13-rss-original-route-device.png`
- `screenshots/23-*.png` 到 `47-*.png`
- `screenshots/48-*.png` 到 `56-*.png`
- `ANDROID_FRONTEND_DEMO_MAPPING_TABLE.md` 的 evidence 字段

开发顺序：

1. 设备在线后先确认 `adb devices -l` 有可用 device/emulator，offline 状态不得继续截图收口。
2. 刷新 `01-bookshelf-device.png` 和 `13-rss-original-route-device.png`，避免旧结构继续当证明。
3. 按 RSS `23` 到 `47`、bookshelf management `48` 到 `51`、settings `52` 到 `56` 顺序补图。
4. 每张截图只在对应 route、state、data boundary 确认后写回 mapping table。
5. 删除或忽略 `tmp-*` 临时图；临时图不进入完成统计。

完成条件：

- 编号截图从 `01` 到 `56` 不缺号。
- mapping table 中每个宣称有 visual evidence 的 row 都能找到对应文件。
- 设备截图没有明显空白、重叠、裁字、不可见主内容或错误 route。
- ADB offline、截图脚本失败、fixture 与目标状态不一致时，row 保持 `Partial`。

## Work Package Template

每个后续任务必须按下面格式开工和收口：

```text
目标：
范围文件：
对应 mapping rows：
非目标：
实现步骤：
验收命令：
设备截图：
完成后要更新的文档：
```

验收命令基线：

```bash
node scripts/verify_demo_route_coverage.mjs
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
git diff --check
```

如果没有在线 Android 设备，可以先收口代码与单测，但不能把需要设备截图的 mapping row 升级为 `Aligned`。

## Immediate Queue

1. P0：把本计划作为前端骨架开发入口，并在 alignment spec/mapping table 中引用。
2. P1：保持 route coverage 绿色；任何新增 route 先过 `verify_demo_route_coverage.mjs`。
3. P7：设备在线后优先捕获 `23` 到 `56`，同时刷新 `01` 和 `13`。
4. P2：书架真实 cover/list/search-result proof。
5. P3：source import 单源成功/错误闭环，然后评估 batch import bridge。
6. P4：RSS repository 与真实 source/article 渲染。
7. P5：overlay/focus foundation，随后 session capsule。

## Reporting Rules

- 报进度时同时给三组数：mapping status、route coverage、device evidence。
- `Partial` 不能按数量直接等价为完成度；必须说明它卡在 visual proof、data/runtime、motion/focus 还是 persistence。
- 临时截图、placeholder、local fixture、compile-only proof 只能作为中间证据。
- 跨平台 UI demo 证明的是设计/契约/验收形状，不证明 Android native runtime 已完成。
