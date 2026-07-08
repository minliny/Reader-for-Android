# Reader UI 从 HTML Demo 复刻为 Android Compose 实施计划

> **目标**：将 `/Users/minliny/Documents/Reader UI/frontend-demo/` 的 HTML 单画布 demo（131 路由 × 5 Shell × 84 MotionId × 117 Token）复刻为 Android Jetpack Compose 原生实现，对齐 `contracts/SLICE_PLAN.md` 的 Slice 0–8 验收门。

> **架构约束**（来自 project_memory + ARCHITECTURE.md）：
> - 必须 Compose 原生组件 + Compose Navigation + 原生返回栈 + 手势 + WindowInsets + 键盘 + WindowSizeClass/折叠姿态 + TalkBack + 性能工具
> - 禁止 Web CSS/DOM/`data-*` 选择器/查询参数/demo 路由栈作为 Android 实现接口
> - 仅继承 Motion ID、状态字段、token 语义、互斥/中断/降级规则、最终状态约束、验收路径
> - 主标签限 4 个：书架/发现/RSS/设置；搜索/阅读页/书源管理不得作为主标签
> - 标签切换不得实现为二级路由 push
> - 阅读页最终状态必须沉浸，不自动打开控制层
> - 返回导航回到来源页；连续点击只保留最后一个目标
> - 不修改 Reader Core/网络/书源解析/数据层，除非当前 slice 需要最小 fixture

---

## 一、现状审计摘要

### 1.1 已完成（真实 Compose + 真实数据）

| 层 | 文件 | 状态 |
|---|---|---|
| Slice 0 契约集成 | `app/src/main/reader-ui-contract/kotlin/` 13 文件 + `MotionPolicy.kt` | ✅ |
| Slice 1 AppShell | [AppShell.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/AppShell.kt) `MainTabShellFrame` 5 槽 | ✅ |
| Slice 1 主标签 | BookshelfScreen / DiscoverScreen / RssScreen / SettingsScreen | ✅ 真实 Compose |
| Slice 1 标签栏 | [FloatingPillTabBar.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/FloatingPillTabBar.kt) BottomPill + LeftRail | ✅ |
| Slice 1 状态机 | [ReaderUiReducer.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt) 60+ intent | ✅ |
| Slice 2 书架→阅读 | [ImmersiveReadingScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/reading/ImmersiveReadingScreen.kt) 真实 BookApi + 异步结果链 | ✅ |
| Slice 2 搜索 | [SearchScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/search/SearchScreen.kt) 真实 BookApi.search | ✅ |
| Slice 4 TTS | AndroidTtsEngine + TtsSessionController + AppShellViewModel 桥接 ttsProgressFlow | ✅ |
| Slice 5 RSS 主标签 | [RssScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/rss/RssScreen.kt) AppProvider.subscriptionRepository | ✅ |
| Slice 5 书源导入 | [ImportBookSourceScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/source/ImportBookSourceScreen.kt) Core source.import | ✅ |
| Slice 6 WebDAV | SyncBackupScreen 真实 credential.load + webdav.connect | ✅ |
| Slice 7 Host Adapter | 26+ capability 注册（TTS 6 / RSS 12 / WebDAV 8） | ✅ |
| Motion 运行时 | [MotionController.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/MotionController.kt) 47 MotionId 契约 | ✅ |
| Motion 适配器 | [ReaderMotionAdapter.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/ReaderMotionAdapter.kt) 84 条 @SerialName | ✅ |
| Token 适配器 | [ReaderTokenAdapter.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/tokens/ReaderTokenAdapter.kt) 16 色/7 间距/4 尺寸/3 圆角/7 字号/5 z/16 时长 | ✅ |
| 主题 | [ReaderTheme.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/theme/ReaderTheme.kt) 606 行 light/dark + 47 ReaderExtraColors | ✅ |
| 组件库 | [ReaderMotionComponents.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/components/ReaderMotionComponents.kt) 1480 行 10 组件族 | ✅ |

### 1.2 关键缺口（真实 Compose，但数据为假 fixture 或 motion 未全接）

| 缺口类型 | 涉及文件 | 问题 |
|---|---|---|
| 假 fixture 数据 | DiscoverScreen（discoverDemoBooks）、ReaderControlScreen（sourceSwitchCandidates/readerPreviewParagraphs） | 需接 Core source.search / chapter.list |
| 假 fixture 数据 | RssDetail/RssSearch/RssOriginal/RssRuleSubscription/RssSourceActions/RssSubscriptionManagement/RssRemainingRouteScreens/RssSourceEdit/Debug/Groups/Import | 需接 Core rss.* / rss.subscription.* |
| 假 fixture 数据 | SettingsSubpageScreens 中 SettingsGeneral/AboutFeedback/SourceManagement/WebDavConfig（非 SyncBackup） | 需接 Core source.list / credential.set |
| 假 fixture 数据 | RestoreScreen（RestoreUiState 状态机但无 Core sync 绑定） | 需接 Core sync.snapshot/restore |
| 假 fixture 数据 | BookshelfRouteScreens / BookshelfManagementScreens（fixture:// URL） | 需接 Core bookshelf.* |
| 假 fixture 数据 | SourceDemoRouteScreen / DiscoverDemoRouteScreen | 需接 Core source.* / source.search |
| 手势未接入 | [ReaderGestures.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderGestures.kt) 6 个 modifier 已定义但未被 ImmersiveReadingScreen/ReaderControlScreen 调用 | 需在阅读屏接入 pageSwipe/fontSizePinch/controlHandle/dockDrag |
| 路由过渡未接入 | [ReaderNavHost.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderNavHost.kt) ReaderRouteTransition 已定义但 AppShell 直接用 AnimatedContent | 需让 push/pop 路由走 ReaderRouteTransition |
| Motion ID 占位 | ReaderGestures.kt L89 `READER_ENTRY_COVER_TO_IMMERSIVE` 占位 `reader.control.handle.press` | 需实现真实 handle.press Motion |
| 折叠依赖缺失 | [ViewportClassAdapter.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/ViewportClassAdapter.kt) 需要 `androidx.window:window:1.3.0` 但 build.gradle.kts 未加 | 当前 try-catch 回退 PORTRAIT |
| 9 个阅读覆盖层 | ReaderControlScreen 是单个 composable，未拆 9 个 overlay | 需拆 appearance/tts/settings/content-search/content-replacement/directory/auto-page/source-switch/night-state |
| 会话胶囊 | TTS/auto-page 会话胶囊未作为独立 composable | 需实现 capsule enter/update/switch/exit + voicePulse 960ms |
| FlowShell 源切换 | source-switch 路由未实现 FlowShell 3 槽（阅读延续+切换窗+结果） | 需实现 reader.sourceSwitch.open-close |
| 阅读全屏面板 | 9 个 reader-full-* 路由未实现 | 需实现全屏面板 + grabber 拖拽 |
| 源管理链 | 17 个 source-* 路由为 demo 占位 | 需接 Core source.save/delete/detect/debug.run |

---

## 二、复刻阶段划分（对齐 SLICE_PLAN.md）

### 阶段 A：基础设施加固（Slice 0/1 收尾）

**目标**：闭合 motion/手势/折叠/路由过渡的基础设施缺口，为后续 slice 提供稳定底座。

#### A1. 添加 androidx.window 依赖
- **文件**：`app/build.gradle.kts`
- **任务**：添加 `androidx.window:window:1.3.0` 依赖
- **验收**：[ViewportClassAdapter.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/ViewportClassAdapter.kt) 不再 try-catch 回退，能真实读取折叠姿态

#### A2. 接入 ReaderRouteTransition 到 AppShell
- **文件**：[AppShell.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/AppShell.kt) L1044-1082 `MainTabShellFrame` 的 `contentRegion`
- **任务**：将当前 `AnimatedContent`（仅 tabSwitchTransition）替换为对 push/pop 路由使用 [ReaderRouteTransition.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderNavHost.kt) 的 PUSH_FORWARD/POP_BACKWARD/REPLACE 三种 spec
- **约束**：标签切换仍走 `tabSwitchTransition`（不得实现为二级路由 push）；连续点击只保留最后一个目标（asyncRouteRequest 守卫已存在）
- **验收**：路由 push 从右滑入 1/8 偏移 + 淡入；pop 向右滑出；replace 仅淡入；reducedMotion 时全部 0ms

#### A3. 接入 ReaderGestures 到阅读屏
- **文件**：[ImmersiveReadingScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/reading/ImmersiveReadingScreen.kt)、[ReaderControlScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/reading/ReaderControlScreen.kt)
- **任务**：
  - `readerPageSwipe` 接入 ReadingTextFlow，触发 NextPage/PrevPage intent
  - `readerFontSizePinch` 接入阅读区，触发 UpdateTypography
  - `readerControlHandle` 接入控制层 grabber，触发 control.handle.press/drag/release
  - `readerDockDrag` 接入宽屏 dock（仅 expanded-width/tablet-expanded/compact-landscape）
  - `readerSliderDrag` 接入亮度滑块/章节进度
- **约束**：拖拽期不得触发 route.push / overlay.open / 修改 Core 状态；slider 拖拽 0ms easing 跟手
- **验收**：左右滑翻页（80px 阈值）；捏合调字号；grabber 拖拽（34px 阈值/18px 预览）；dock 拖拽不跨铰链/安全区

#### A4. 实现 reader.control.handle.press Motion ID
- **文件**：[ReaderGestures.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderGestures.kt) L89、[MotionController.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/MotionController.kt)
- **任务**：将 L89 占位的 `READER_ENTRY_COVER_TO_IMMERSIVE` 替换为真实的 `reader.control.handle.press` Motion 契约（320ms handleLongPress / 120ms handleSnap）
- **验收**：MotionController.contractFor("reader.control.handle.press") 返回正确时长；grabber 按下/释放动画符合 MOTION_SPEC §3.4

#### A5. 原始值 grep 检查（TOKEN_SPEC §4）
- **文件**：新建 `scripts/check-raw-tokens.sh` 或 gradle task
- **任务**：grep 检查 `app/src/main/kotlin/com/reader/ui/` 下的原始值：
  - 颜色 `Color\(0x[0-9a-fA-F]+\)` / `Color\(red:\s*\d`
  - 间距 `\d+\.dp`（除 ReaderTokenAdapter/ReaderTheme 外）
  - 圆角 `RoundedCornerShape\(\d+\.dp\)`
  - 时长 `tween\(\d+\)` / `durationMillis\s*=\s*\d+`
  - 缓动 `FastOutSlowInEasing` 直接使用
- **验收**：组件代码零原始值；TokenAdapter 覆盖 117 fixture token

---

### 阶段 B：Slice 2 收尾（书架 → 详情 → 阅读）

**目标**：闭合书架到沉浸阅读的完整真实数据链。

#### B1. BookshelfRouteScreens 接入真实 Core
- **文件**：[BookshelfRouteScreens.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/bookshelf/BookshelfRouteScreens.kt)
- **任务**：替换 `fixture://` URL 为 `AppProvider.bookshelfRepository` 真实数据；书架分组/排序/过滤接 Core bookshelf.list
- **验收**：书架 cover/list 模式切换真实数据；分组管理接 Core bookshelf.groups

#### B2. BookshelfManagementScreens 接入 Core 批量操作
- **文件**：[BookshelfManagementScreens.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/bookshelf/BookshelfManagementScreens.kt)
- **任务**：批量启用/禁用/检测/分组/删除接 Core bookshelf.batch.*
- **验收**：selection.item.toggle / selection.group.toggle / selection.toolbar.action motion 触发；destructive.confirm.commit 走 Dialog 确认

#### B3. BookDetailScreen 默认走真实 builder
- **文件**：[BookRouteScreens.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/book/BookRouteScreens.kt)
- **任务**：当前 dual builder（demoBookDetailRouteState / realBookDetailRouteState），改为 `route.book != null` 时默认 real，仅 fallback 走 demo
- **验收**：从书架点封面进详情，章节列表来自 Core chapter.list；目录预览 tocPreview 真实

#### B4. BookDirectoryScreen 接入 Core chapter.list
- **文件**：[BookRouteScreens.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/book/BookRouteScreens.kt) BookDirectoryScreen
- **任务**：全目录页接 Core chapter.list；章节下载状态接 ChapterCacheManager
- **验收**：章节跳转触发 content.load + reader.progress.update；下载状态 loading→complete→cached

---

### 阶段 C：Slice 3（阅读覆盖层 / 控制 dock / 阅读模式）

**目标**：实现 9 个阅读覆盖层 + 控制层 handle/dock + 互斥/焦点恢复。

#### C1. 拆分 9 个阅读覆盖层为独立 composable
- **文件**：新建/重构 [ReaderControlScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/reading/ReaderControlScreen.kt)
- **任务**：按 PAGE_REFERENCE §5 拆分：
  1. `reader-appearance-overlay-v2` → 主题调色板 + 字体选择 + stepper + 设置下拉
  2. `reader-tts-overlay-v2` → 播放/暂停 + 句子 stepper + 速度/音色/范围/定时下拉
  3. `reader-settings-overlay-v2` → 9 个 toggle（autoPage/tapMode/volumePage/pageAnimation/landscapeLock/keepScreenOn/statusInfo/hapticFeedback/cacheNext）
  4. `reader-search-overlay-v2` → 输入 + 结果列表
  5. `reader-replace-overlay-v2` → 替换规则列表 + per-rule toggle
  6. `reader-directory-overlay-v2` → 目录/书签切换 + 章节列表
  7. `reader-auto-scroll-overlay-v2` → 倒计时 + 速度
  8. `source-switch` → FlowShell 3 槽（见 C6）
  9. `reader-night-state-v2` → readerMode 派生（非 overlay）
- **约束**：每个 overlay 独立 `@Composable`，通过 `ui-state.overlay` 枚举调度；reader.module.switch 200ms 切换；overlay 互斥（一次一个，null 中间态过渡）
- **验收**：9 overlay 独立开/关；互斥规则成立；module.switch 走 null 中间态

#### C2. 控制层 handle 拖拽
- **文件**：[ReaderGestures.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderGestures.kt) `readerControlHandle`
- **任务**：实现 3 态（press/drag/release）+ 阈值 34px + 预览 18px；按下 320ms handleLongPress；释放 120ms handleSnap
- **验收**：拖拽 < 34px 视为 tap；≥ 50% 展开高度提交展开；≤ 50% 提交收起；速度 ≥ 400 dp/s 直接展开/收起

#### C3. Dock 拖拽（宽屏专用）
- **文件**：[ReaderGestures.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderGestures.kt) `readerDockDrag`
- **任务**：longPress 320ms + 拖拽；bounds = ReaderFrame + dock group；不跨铰链/安全区，clamp
- **约束**：仅 expanded-width/tablet-expanded/compact-landscape viewport class 显示 dock
- **验收**：dock 拖拽 clamp 在合法范围；折叠姿态变化时 dock 落合法位置

#### C4. Overlay 互斥 + transition-guard
- **文件**：[ReaderUiReducer.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt)
- **任务**：overlay 切换必须经 null 中间态（transition-guard）；关闭顺序：先关当前 overlay，再返回父页面
- **验收**：A overlay 开时点 B，先 A→null→B；不可同时两个 overlay

#### C5. 焦点恢复
- **文件**：[ReaderUiReducer.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt)、覆盖层关闭逻辑
- **任务**：close overlay → focusTarget 回到 overlay 触发器（reader.control.handle）；system back 同；route.pop → 上一页最后 focusTarget；TTS/auto-page 退出 → reader.control.handle
- **验收**：关闭 appearance overlay 后焦点回 grabber；关闭 TTS 会话后焦点回 grabber

#### C6. FlowShell 源切换
- **文件**：新建 `ui/reading/SourceSwitchFlowScreen.kt`
- **任务**：实现 FlowShell 3 槽（阅读延续 + 切换窗 + 结果）；reader.sourceSwitch.open-close 240ms；无全屏遮罩
- **约束**：源切换是阅读平面的一部分，不是全屏 block；候选列表接 Core source.search
- **验收**：源切换浮动浮入；选择候选后 reader.control.show 回到阅读；不阻断阅读延续

---

### 阶段 D：Slice 4（进度 / 会话 / 焦点 / TTS）

**目标**：实现会话胶囊 + TTS/auto-page 互斥 + 控制空间锚定。

#### D1. 会话胶囊 composable
- **文件**：新建 `ui/reading/ReaderSessionCapsule.kt`
- **任务**：实现 capsule.enter(160ms) / update(120ms) / exit(200ms) / switch(160ms)；仅在 activeSession=tts|auto-page 时渲染；倒计时数字局部更新不重播整个胶囊
- **约束**：TTS↔auto-page 切换走 completeThenReplace（先退 TTS 再进 auto-page）；capsule 切换无尺寸抖动
- **验收**：TTS 启动 → capsule.enter + control.hide；auto-page 启动同理；切换无抖动

#### D2. TTS/auto-page 互斥
- **文件**：[ReaderUiReducer.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt)
- **任务**：启动一个会话时停止另一个；activeSession 单值
- **验收**：TTS 播放中启动 auto-page → TTS stop → auto-page start

#### D3. 控制空间锚定
- **文件**：新建 `ui/reading/ReaderControlSpace.kt`
- **任务**：controlSpace.enter(180ms) / exit(180ms)；单主控制；锚定到阅读平面
- **验收**：capsule 切换到 controlSpace 无尺寸抖动；controlSpace 单主控制

#### D4. Voice pulse 960ms
- **文件**：[MotionKeyframes.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/motion/MotionKeyframes.kt)
- **任务**：实现 voicePulse 960ms `infiniteRepeatable` 脉动；reducedMotion 时停循环改色相
- **验收**：TTS 播放时音波图标脉动；reducedMotion 时静态色相反馈

#### D5. Capsule 倒计时 tick
- **文件**：`ReaderSessionCapsule.kt`
- **任务**：auto-page 倒计时 120ms tick 局部更新；不重播整个胶囊
- **验收**：倒计时数字变化仅更新数字，胶囊无重入动画

---

### 阶段 E：Slice 5（RSS / 书源 / 发现 / 搜索）

**目标**：闭合 RSS/书源/发现的真实数据链。

#### E1. RSS 子页面接入 Core rss.*
- **文件**：[RssDetailScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/rss/RssDetailScreen.kt)、RssSearchScreen、RssOriginalScreen、RssRuleSubscriptionScreen、RssSourceActionsScreen、RssSubscriptionManagementScreen、RssRemainingRouteScreens、RssSourceEditScreen、RssSourceDebugScreen、RssSourceGroupsScreen、RssSourceImportScreen
- **任务**：替换假 fixture 为 `AppProvider.subscriptionRepository` / `RssParser` / Core rss.list / rss.item.read / rss.subscription.*
- **验收**：RSS 详情接 Core rss.item.read；RSS 原文浏览器走 HostRequest webview.open；订阅管理接 rss.subscription.*

#### E2. 书源管理链接入 Core source.*
- **文件**：新建/重构 `ui/source/SourceManagementScreens.kt`（17 路由）
- **任务**：source-management / source-detail / source-add / source-edit / source-delete-confirm / source-detect / source-rule-edit / source-debug / source-debug-running / source-debug-result / source-logs / source-code-view / source-test-result / source-batch / source-groups / source-import-options / source-import-preview
- **约束**：destructive（delete）必须 Dialog 确认；debug 可执行
- **验收**：书源 CRUD 接 Core source.save/delete；debug 接 source.debug.run；批量接 source.batch.*

#### E3. 发现页接入 Core source.search
- **文件**：[DiscoverScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/discover/DiscoverScreen.kt)
- **任务**：替换 discoverDemoBooks 为 Core source.search；sourceType segment / filter / sort 接 Core source.search 参数
- **验收**：发现页真实搜索；filter.apply.commit / state.content.replace motion 触发

#### E4. DiscoverDemoRouteScreen / SourceDemoRouteScreen 转 real
- **文件**：[DiscoverDemoRouteScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/discover/DiscoverDemoRouteScreen.kt)、[SourceDemoRouteScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/source/SourceDemoRouteScreen.kt)
- **任务**：将 demo 路由页转为真实路由页或合并进 E2/E3
- **验收**：DemoRouteRegistry 中 Discover/Source 相关路由全部有真实 composable

---

### 阶段 F：Slice 6（同步 / 冲突 / 离线）

**目标**：闭合同步/备份/恢复/冲突/离线状态。

#### F1. RestoreScreen 接入 Core sync
- **文件**：[RestoreScreen.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/restore/RestoreScreen.kt)
- **任务**：restore-scopes / restore-preview / restore-running / restore-result / restore-confirm / restore-progress / restore-conflict 接 Core sync.snapshot / sync.restore
- **约束**：restore.loading 禁止 route.push（async guard）
- **验收**：恢复范围选择 → 预览 → 运行 → 结果完整链路；冲突解决走 5 种 resolution

#### F2. WebDavConfig 接入 credential.set
- **文件**：[SettingsSubpageScreens.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/settings/SettingsSubpageScreens.kt) WebDavConfig
- **任务**：WebDAV 配置保存走 HostRequest credential.set；连接测试已有（SyncBackup）
- **验收**：保存凭证后 credential.set 触发；重连走 webdav.connect

#### F3. 同步冲突解决（5 种 resolution）
- **文件**：RestoreScreen / 新建 `ui/settings/SyncConflictScreen.kt`
- **任务**：实现 sync.conflict.resolve 的 5 种 resolution（local_wins / remote_wins / merge / keep_both / manual）
- **验收**：冲突时弹 Dialog 选择 resolution；选择后 sync.conflict.resolve CoreCommand 触发

#### F4. 离线状态层
- **文件**：[AppShell.kt](file:///Users/minliny/Documents/Reader%20for%20Android/app/src/main/kotlin/com/reader/ui/shell/AppShell.kt) stateHost
- **任务**：网络不可用时 reducer 设 pageState=offline；不阻断本地查看和关闭页面
- **约束**：仅阻断网络依赖动作
- **验收**：离线时本地书架可看；网络恢复后状态消失

---

### 阶段 G：Slice 8（一致性验证 + 漂移防护）

**目标**：达成 CONTRACT_FIRST_NATIVE_UI_PLAN §10 七问全 yes。

#### G1. Reducer golden test
- **文件**：新建 `app/src/test/kotlin/com/reader/ui/shell/ReaderReducerGoldenTest.kt`
- **任务**：每个 slice 的关键 intent 序列生成 golden JSON 比对；覆盖 overlay 互斥 / async guard / 会话互斥 / 中断
- **验收**：golden test 全通过

#### G2. 原始值 grep CI 检查
- **文件**：新建 gradle task `checkRawTokens`
- **任务**：A5 的 grep 检查接入 CI
- **验收**：CI 在原始值出现时 fail

#### G3. TokenAdapter 覆盖率检查
- **文件**：新建 `app/src/test/kotlin/com/reader/ui/tokens/TokenAdapterCoverageTest.kt`
- **任务**：解析 ReaderTokenAdapter 实现，与 `token.fixtures.json` 比对，缺失则 fail
- **验收**：117 fixture token 全覆盖

#### G4. 设备冒烟测试录制
- **文件**：新建 `app/src/androidTest/kotlin/com/reader/DeviceSmokeTest.kt`
- **任务**：录制 `slice-8-android-device-smoke.mov`：冷启动 → 书架 → 开书 → 阅读 → 翻页 → 控制层 → readerMode → 进度更新 → TTS → 退出/重入 → 同步进度
- **约束**：必须真机或模拟器，60fps，≤30s
- **验收**：完整链路无崩溃；motion 符合契约

#### G5. TalkBack 焦点迁移录制
- **文件**：新建 `app/src/androidTest/kotlin/com/reader/TalkBackFocusTest.kt`
- **任务**：录制 `slice-8-android-talkback.mov` 验证焦点恢复
- **验收**：overlay 关闭后焦点回 grabber；route.pop 后焦点回上一页最后目标

#### G6. 折叠/方向录制
- **文件**：新建 `app/src/androidTest/kotlin/com/reader/FoldOrientationTest.kt`
- **任务**：录制 `slice-8-android-fold-orientation.mov` 验证三阶段 prepare→reshape→settle
- **验收**：折叠/展开不丢路由/会话/overlay/dock 偏移；body 不跳章

---

## 三、验收门（对齐 ACCEPTANCE.md §10 七问）

每个阶段合并前必须回答全 yes：

1. **状态归属**：DomainState（Core）/ UiState（Reducer 9 字段）/ EphemeralState（Native UI）？
2. **进入 schema**：RouteId/MotionId/Token/StateRule 是否在 `reader-ui-contract/kotlin/`？
3. **三平台生成类型通过**：`generated/kotlin/` 编译通过？
4. **Reducer golden test**：是否有 golden JSON 比对？
5. **UI 只渲染 ViewState**：组件是否仅消费 ViewState + 发 UiEvent？
6. **不绕过 Core/Host**：是否经 reducer + CoreCommand / HostCommand？
7. **三平台无漂移**：grep 无原始值 / TokenAdapter 覆盖 / MotionAdapter 覆盖？

---

## 四、执行顺序与并行约束

```
阶段 A（基础设施） ──┐
                     ├─→ 阶段 B（Slice 2 收尾） ──→ 阶段 C（Slice 3 覆盖层） ──→ 阶段 D（Slice 4 会话）
                     │
                     └─→ 阶段 E（Slice 5 RSS/书源/发现） ──→ 阶段 F（Slice 6 同步）
                                                                                      │
                                                                                      ↓
                                                                           阶段 G（Slice 8 验证）
```

- **A→B→C→D 串行**（阅读链依赖）
- **A→E→F 可与 B→C→D 并行**（RSS/书源链独立于阅读链）
- **G 必须在 B-F 全完成后**

---

## 五、不继承清单（MOTION_EFFECTS.md §11）

Android Compose 实现禁止继承以下来自 demo 的内容：

- ❌ Web CSS（`--fd-*` / `--reader-ds-*` 变量直接用）
- ❌ DOM 结构（`div.fd-*` class）
- ❌ `data-*` 选择器名（`data-book-cover` / `data-motion-pressed` 等）
- ❌ demo 路由栈（`window.history` / `goTo` / `goTab` 的 Web 实现）
- ❌ 坐标 / 断点 / scroll-container / z-index
- ❌ 键盘模拟（`data-keyboard-host` 假键盘）
- ❌ 折叠模拟（`visualViewport.resize`）
- ❌ 浏览器截图/manifest 作为平台证据

**只继承**：Motion ID 命名、状态字段（from/to/interrupt/finalState）、时长 token 值、中断规则、降级规则、互斥/async guard/transition-guard 规则、验收路径。

---

## 六、关键文件索引

### Demo 源（参考）
- 入口：`/Users/minliny/Documents/Reader UI/frontend-demo/index.html`
- 渲染器：`/Users/minliny/Documents/Reader UI/frontend-demo/render-runtime.js`（9200+ 行）
- Motion 契约：`/Users/minliny/Documents/Reader UI/frontend-demo/motion-controller.js`（1390 行）
- 路由契约：`/Users/minliny/Documents/Reader UI/frontend-demo/route-contract.js`（131 路由）
- Fixture：`/Users/minliny/Documents/Reader UI/frontend-demo/fixture.js`（单一数据源）
- Shell Kit：`/Users/minliny/Documents/Reader UI/frontend-demo/shared-shell-kit/kit.js`（5 shell）

### 合约源
- Slice 计划：`/Users/minliny/Documents/Reader UI/contracts/SLICE_PLAN.md`
- 路由矩阵：`/Users/minliny/Documents/Reader UI/contracts/ROUTE_COMPONENT_MATRIX.md`
- Motion 规格：`/Users/minliny/Documents/Reader UI/contracts/MOTION_SPEC.md`
- Token 规格：`/Users/minliny/Documents/Reader UI/contracts/TOKEN_SPEC.md`
- 页面参考：`/Users/minliny/Documents/Reader UI/contracts/PAGE_REFERENCE.md`
- Core/Host 边界：`/Users/minliny/Documents/Reader UI/contracts/CORE_HOST_BOUNDARY.md`
- 状态归属：`/Users/minliny/Documents/Reader UI/contracts/STATE_OWNERSHIP.md`
- 验收门：`/Users/minliny/Documents/Reader UI/contracts/ACCEPTANCE.md`
- 证据规格：`/Users/minliny/Documents/Reader UI/contracts/PLATFORM_EVIDENCE_SPEC.md`

### Android 现有实现
- AppShell：`app/src/main/kotlin/com/reader/ui/shell/AppShell.kt`
- Reducer：`app/src/main/kotlin/com/reader/ui/shell/ReaderUiReducer.kt`
- MotionController：`app/src/main/kotlin/com/reader/ui/motion/MotionController.kt`
- TokenAdapter：`app/src/main/kotlin/com/reader/ui/tokens/ReaderTokenAdapter.kt`
- 契约：`app/src/main/reader-ui-contract/kotlin/`
