# Reader for Android — Loop Execution Plan

**Date**: 2026-05-29
**Status**: `ANDROID_LONG_TERM_LOOP_EXECUTION_PLAN_READY`
**Prerequisites**: Device connected, network authorized

---

## Execution Order

```
C-001 (installDebug) ──→ C-002 (手动走查) ──→ C-003/C-004 (收口)
         ↓
A-001 (字号处理) ──→ A-002 (组件去重) ──→ A-003 (Phase A 收口)
         ↓
B-001 (DI 审计) ──→ B-002 (AppProvider) ──→ B-003~006 (逐个接入) ──→ B-007 (Phase B 收口)
         ↓
D-002 (live smoke) ──→ D-003 (保存 fixture) ──→ D-004 (Phase D 收口)
         ↓
E-001 (UX 审计) ──→ E-002~006 (逐个稳定) ──→ E-007 (Phase E 收口)
         ↓
F-001~006 (发布硬化)
```

每个 Phase 内串行，Phase 间可交错（例如 C 做完就做 A，不一定要等 D）。

---

## LOOP-C-001：installDebug 验证

**目标**：确认 `./gradlew :app:installDebug` 在设备上成功，App Shell 正常启动。

**只读审计**：
- `app/build.gradle.kts`（applicationId、minSdk、targetSdk）
- `AndroidManifest.xml`（MainActivity、intent filter）
- `MainActivity.kt`
- `ReaderAndroidApp.kt`
- `AppNavigation.kt`

**执行**：
```bash
./gradlew :app:installDebug
```

**判定**：
- 安装成功 → 记录设备型号、Android 版本、是否启动
- 安装失败 → 记录错误，检查 JAVA_HOME、SDK、设备连接
- 启动 crash → 记录 logcat，定位原因

**禁止**：
- 修改 Reader-Core
- 访问真实网络
- 修改 UI 代码（除非 installDebug 本身失败需要修 build 配置）

**验证**：
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

**人工确认**：需要你在设备上看一眼 App 是否正常启动。

**产物**：`docs/PLANNING/loop-reports/LOOP-C-001.md`

---

## LOOP-A-001：StitchAppShell 硬编码字号

**目标**：修复或正式 defer P1-4（StitchAppShell 硬编码字号不统一）。

**当前问题**：`StitchAppShell.kt` 中 BookListItem/BookCoverItem/Header 使用硬编码 `fontSize = 18.sp`、`14.sp`、`24.sp` 等，未引用 `ReaderTheme.typography` token。

**只读审计**：
- `StitchAppShell.kt`（全文，标记所有硬编码字号位置）
- `ReaderTheme.kt` / `ReaderTypography.kt`（对比可用 token）

**修复策略**（选其一）：
1. **映射方案**：将字号映射到最接近的 ReaderTheme token（`18.sp` → `bookTitle` 14sp 偏小，`sectionTitle` 16sp 偏小，`readerBody` 18sp 匹配但语义不对）
2. **Defer 方案**：在文件顶部加注释说明这些是 Stitch 设计规范自定义字号，不强制映射

**推荐**：Defer 方案。Stitch 自定义字号是设计意图，不是 bug。强行映射会造成视觉变化。

**具体操作**：
- 在 `StitchAppShell.kt` 顶部 `// ── Bookshelf` 注释区加一行：`// Note: font sizes (24sp/18sp/14sp/10sp) are Stitch design spec values, intentionally not mapped to ReaderTheme.typography tokens.`
- 更新 STITCH_VISUAL_REVIEW_AFTER_CAPABILITY_SMOKE.md 中 P1-4 状态为 DEFERRED_WITH_RATIONALE

**禁止**：修改字号值、修改 layout 层级、修改 ReaderTheme

**验证**：
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

**产物**：`docs/PLANNING/loop-reports/LOOP-A-001.md`

---

## LOOP-A-002：组件重复定义

**目标**：分析 ReaderIconButton / ReaderChip 双份定义，决定保留哪个或正式 defer。

**当前问题**：
- `ReaderIconButton` 在 `CommonComponents.kt`（Material3 IconButton 封装）和 `ReaderNativeComponents.kt`（纯 Box + clickable）各有一份
- `ReaderChip` 同样双份，selected 状态渲染不同（CommonComponents 用 solid background，NativeComponents 用 alpha 0.12f）

**只读审计**：
```bash
grep -rn "ReaderIconButton\|ReaderChip" app/src/main/kotlin/ --include="*.kt" | grep -v "test/"
```
统计每个变体的调用方数量和位置。

**修复策略**（选其一）：
1. **保守方案**：哪个调用方多保留哪个，另一个加 `@Deprecated` 指向保留版
2. **Defer 方案**：两个变体服务不同场景（CommonComponents 面向通用 UI，NativeComponents 面向阅读器控件），不算真正的重复，加注释说明用途边界

**推荐**：Defer 方案。两个变体的使用场景确实不同——通用组件 vs 阅读器控件层。加注释说明各自用途即可。

**具体操作**：
- 在 `CommonComponents.kt` 的 `ReaderIconButton` 上加 KDoc：`/** General-purpose icon button. For reader control layer, see [ReaderNativeComponents.ReaderIconButton]. */`
- 在 `ReaderNativeComponents.kt` 的 `ReaderIconButton` 上加 KDoc：`/** Reader control layer icon button. For general UI, see [CommonComponents.ReaderIconButton]. */`
- 同理处理 ReaderChip
- 更新 STITCH_VISUAL_REVIEW_AFTER_CAPABILITY_SMOKE.md 中 P1-6 状态为 DEFERRED_WITH_RATIONALE

**禁止**：删除任一变体、合并 API、新增基类

**验证**：
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

**产物**：`docs/PLANNING/loop-reports/LOOP-A-002.md`

---

## LOOP-B-001：ViewModel DI 审计

**目标**：找到所有直接 `FakeCoreBridge()` 创建点，输出审计报告。

**只读审计**：
```bash
grep -rn "FakeCoreBridge()" app/src/main/kotlin/ --include="*.kt"
grep -rn "HttpClient()" app/src/main/kotlin/com/reader/android/ui/ --include="*.kt"
grep -rn "SearchParser()\|BookInfoParser()\|TOCParser()\|ContentParser()" app/src/main/kotlin/com/reader/android/ui/ --include="*.kt"
```

**输出**：每个实例化点的文件、行号、所属 ViewModel、当前是否可切换 fake/real。

**示例输出格式**：
| 文件 | 行 | 类 | 当前模式 | 是否可切换 |
|------|----|----|---------|-----------|
| SearchScreen.kt | 32 | SearchViewModel | `FakeCoreBridge()` | 否（硬编码） |
| BookDetailScreen.kt | 36 | BookDetailViewModel | `FakeCoreBridge()` | 否 |
| TOCScreen.kt | 32 | TOCViewModel | `FakeCoreBridge()` | 否 |
| ReaderScreen.kt | 51 | ReaderViewModel | `FakeCoreBridge()` | 否 |

**不改代码。只输出审计报告。**

**产物**：`docs/PLANNING/loop-reports/LOOP-B-001.md`

---

## LOOP-D-002：单次受控在线 Smoke

**目标**：用 `READER_ALLOW_REAL_NETWORK=true` 执行一次真实网络 search，保存 snapshot。

**前置条件**：
- D-001 已完成（网络授权 ✅）
- 当前 HEAD 干净
- 你确认 smoke 参数：source URL、query

**默认 smoke 参数**：
- source：biquges123.com（已有机 fixture 的源）
- query：凡人修仙传
- 策略：单 URL 单次访问，不重试，不绕过反爬

**执行**：
```bash
READER_ALLOW_REAL_NETWORK=true ./gradlew :app:testDebugUnitTest --tests "*RealCoreBridgeSmokeTest" --no-daemon
```

**如果成功**：
- HTML response 保存到 `fixtures/real-source/<source-id>/search/`
- 对比已有 fixture，检查页面结构是否变化
- 更新 manifest.json 的 capturedAt

**如果失败**（TLS/403/anti-bot）：
- 记录 BLOCKED_SOURCE_UNREACHABLE
- 不重试
- 不换源轰炸
- 回退到已有 offline fixture

**禁止**：
- 高频重试
- 绕过反爬
- 修改 parser
- 修改 Reader-Core

**验证**：
```bash
./gradlew :app:testDebugUnitTest --tests "*FixtureCompletenessValidatorTest"
./gradlew :app:testDebugUnitTest
```

**人工确认**：需要你确认 smoke 参数，并在旁边看着网络访问。

**产物**：`docs/PLANNING/loop-reports/LOOP-D-002.md`

---

## LOOP-E-001：阅读器 UX 差距审计

**目标**：对比当前 Reader 实现与 Legado 阅读体验，输出差距清单。

**只读审计范围**：

1. **阅读进度**：
   - `ReaderProgressSaveAdapter.kt`
   - `ReaderProgressLocalStateAdapter.kt`
   - `ReaderRuntimeState.kt`
   - Room `readingProgressDao`

2. **章节缓存**：
   - `ReaderCacheSaveAdapter.kt`
   - `ReaderCacheLocalStateAdapter.kt`
   - Room `cachedChapterDao`

3. **书签**：
   - `ReaderBookmarkActionAdapter.kt`
   - `ReaderBookmarkLocalStateAdapter.kt`
   - Room `bookmarkDao`

4. **目录导航**：
   - `ReaderDirectoryAdapterShell.kt`
   - `ReaderTocLocalStateJoiner.kt`
   - `CoreTocToReaderTocMapper.kt`

5. **内容展示**：
   - `ReaderContentStateMapper.kt`
   - `ContentAdapterShell.kt`
   - `ReaderControlBase.kt`（控制层状态机）

6. **错误恢复**：
   - `ReaderErrorModelTest.kt`
   - `ContentFacadeErrorMapper.kt`
   - `SearchFacadeErrorMapper.kt`

**输出**：每个子系统的当前状态、是否可用、与 Legado 行为差距、P0/P1 问题。

**不改代码。只输出审计报告。**

**产物**：`docs/PLANNING/loop-reports/LOOP-E-001.md`

---

## 每轮 Loop 执行模板

```
1. PRE-CHECK
   git rev-parse --short HEAD
   git status --short              # 必须干净
   echo "Task: LOOP-<PHASE>-<NUM>"

2. EXECUTE（按上方任务描述）

3. VERIFY
   ./gradlew :app:testDebugUnitTest          # 必须 PASS
   ./gradlew :app:assembleDebug              # 必须 PASS
   ./gradlew :app:testDebugUnitTest --tests "*UiCapabilitySmokeTest"  # UI 相关任务必须

4. COMMIT（如修改了代码）
   git add <only task files>
   git commit -m "type: short description"
   # NEVER push

5. REPORT
   - 更新 ANDROID_LONG_TERM_TASK_QUEUE.md（标记 DONE + 解锁后续任务）
   - 输出 loop-reports/LOOP-<PHASE>-<NUM>.md
```

---

## 推荐第一轮

**LOOP-C-001**：installDebug 验证。

原因：
- 设备刚就绪，先验证 build→install→launch 链路
- 不改代码，纯验证
- 如果失败，后续 A/B/D 可以做；如果成功，Phase C 全链解锁

你说开始就开。
