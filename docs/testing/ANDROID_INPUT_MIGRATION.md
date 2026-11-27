# Android 测试输入承接（2026-10-21）

当前输入是本仓 Kotlin 生产源码和对应测试；已撤回的 Android 设计稿不再是需求或测试前提。迁移基线为 `ebdc6a94390e3568f123b58d7d6c0c2a82f11bb9`。独立工作树从该 HEAD 建立后，单独应用主工作树已有的 379 项设计删除；不改动主工作树的删除 WIP。

8 份原测试共 36 个方法：9 个保留、4 个迁移、23 个旧方法退出。逐方法决定、旧文件 SHA-256、替代覆盖及未决项见 [受控退役登记](../../tools/design-input-retirement.json)。历史测试只在本机私有证据中保留为 `.kt.txt`，不属于测试发现集。

| 原测试 | 当前承接 | 退出的旧前提 |
| --- | --- | --- |
| `ReaderIconTokenMappingTest` | 保留主 Tab 语义 token、所有 enum 到 ImageVector、设置图标 3 项 | HTML/JS 79 图标库存及对 JS 名称的映射 2 项 |
| `ReaderSharedComponentsStructureTest` | 保留组件 API、主题角色、semantics、集中图标适配及当前原生宿主源码锚点 9 项 | 组件 Markdown 存在、HTML shell 分类/数量、历史 CSS/五色值禁令；移除这些前提后仍保留实际 Kotlin API 要求 |
| `FrontendInputComposeCoverageTest` | 原源码/Preview 锚点逐条保留到 `AndroidComposeSourceCoverageTest`；实际状态断言继续由现有各功能 Mapper 测试承担 | 旧 spec、TypeScript 类型、事件拼写表及 HTML/报告相互比较；不拿 Reader-UI 的协议事件快照充当页面 fixture |
| `FrontendInputAssetLibraryInventoryTest` | 实际图标保证采用当前 Kotlin 图标测试 | 30 设计图、6 封面、60 截图、JS 库存/镜像、DOM 和报告宣称通过 3 项 |
| `FrontendInputComponentLibraryInventoryTest` | 实际组件 API 保证采用当前共享组件测试 | 49 演示卡、分区、17 演示图标、镜像/报告 3 项 |
| `FrontendInputFlowShellInventoryTest` | 当前 Kotlin Mapper/Preview 源码锚点和现有 `SourceSwitchFlowStructureTest` | HTML 文件集/渲染器、演示候选/筛选/步骤数量、报告 4 项；不把旧比较/检测演示定为当前产品要求 |
| `FrontendInputHtmlInventoryTest` | 无运行期职责，整体退出 | HTML 99/96、30 页分类、旧预览和 manifest 库存 1 项 |
| `FrontendInputPhaseCompletionGuardTest` | 当前操作分别记录真实回执及 `NOT_RUN` | 文档命令/阶段字句/数量/存量成功报告 4 项 |

`AndroidComposeSourceCoverageTest` 只检查源码/Preview 锚点，不执行 Mapper、不渲染 Compose、不证明状态或回调行为。现有 `*UiStateMappingTest` 中真实 Mapper、上下文保留和当前书籍归属等断言仍保留。原事件表测试没有验证过生产回调，退出后不宣称其行为已经得到替代验证。现有路由和 Preview 测试的静态证据也不升级为交互证据。

## 显式入口

所有入口默认关闭，未选保持 `NOT_RUN`。无参只显示清单，不执行。

| 入口 | 读取/嵌套 | 保护风险和本次依据 | 证据边界 |
| --- | --- | --- | --- |
| `python3 .github/scripts/check-design-inputs.py --case retired-inputs` | 标准库；读取退役登记，扫描本仓 `app`、`.github`、`tools` 和根构建脚本；不调用其他检查 | 本次删除/拆分的旧类、旧方法、旧输入和旧 CI 入口不得重新进入发现集；保留的测试声明不得丢失 | 仅输入/发现治理，不是 Android 测试通过 |
| `python3 .github/scripts/test-design-input-retirement.py --case restore-refusal` | 导入上述检查的 `inspect`，复制本仓源码到私有临时目录，逐个注入反例并清理；不执行 Android | 本次新增准入检查必须拒绝旧文件、改名旧类、旧方法、旧输入引用/目录、丢失保留声明和删减退役决定；防止退役名义下丢覆盖 | 仅准入反例，不是 JUnit/Compose |
| `actionlint -shellcheck= -pyflakes= .github/workflows/source.yml` | 当前手动 workflow 的 YAML/表达式检查；显式关闭 ShellCheck/pyflakes 嵌套，不触发 Actions | 本次更改选择项与条件分支，需防止 workflow 语法或表达式错误 | 仅 workflow 静态检查 |

CI 的 `retired-inputs`、`workflow-lint`、`secrets` 各自独立手动选择；只准备所选入口实际需要的工具。旧 `json` 只读取已撤回的 30 份演示 JSON，无剩余运行输入职责，选择器已退出；旧 `full` 聚合选择同样退出。`retired-inputs` 不隐式运行准入反例、workflow lint、secret scan 或任何 Android 测试。CI 从未运行过这批 JUnit，不作 CI 测试覆盖宣称。

准入检查绑定本次已审的原文件 SHA-256、方法身份及逐项承接/退出定义，不用历史数量替代职责。修改定义须审读实际变化后再显式更新定义摘要。

## 保留的真实缺口

- 迁移来源 `ebdc6a94` 的独立工作树没有 Git 跟踪的 Gradle 构建入口及 `AndroidManifest`。合入后的产品源码按当前 `io/reader/android` 源集和单独的验证入口检查。这批迁移回执的范围是源码，Kotlin/JUnit/Compose、APK、设备和用户验收均未执行。
- 当前源码缺 `ReaderTheme`、`BookComponents.kt`、`SearchAdapterShell.kt`、`BookDetailAdapterShell.kt` 和 `booksource/SourceManagementUiState.kt` 等前提。相应源码/API 断言仍保留，缺失不会因退役而被掩盖。
- `ReaderRouteHost` 的 `onSwitchSource` 当前仅返回上一页。真实 Core 换源、阅读位置保留、Compose 点击次数及迟到回调隔离尚无执行证据。
- 源码输入迁移完成、准入检查通过、JUnit 执行、产品行为和用户验收分别记账。私有本次结果保存在隔离工作区 `.reader-artifacts/android-input-migration-20261021/`。

## 2026-11-27 遗留生产树退役

- 删除 `app/src/main/kotlin/com`（80 个文件）与未被退役登记绑定的 `app/src/test/kotlin/com` 测试（58 个文件），合计 138 个文件 / 30,109 行。依据：Gradle `sourceSets` 从未编译该树，`io/reader/android` 已承接全部生产入口，仓库内除该树自身的路径断言外无任何引用。
- 保留 `ReaderIconTokenMappingTest.kt`、`ReaderSharedComponentsStructureTest.kt`、`AndroidComposeSourceCoverageTest.kt` 三个文件原位：它们是 `tools/design-input-retirement.json` 中 13 个 RETAIN/MIGRATE 方法的 `currentPath`，删除会使 `check-design-inputs.py --case retired-inputs` 失去保留声明。
- 这三个文件仍不在任何 `sourceSets` 内，不参与编译；`ReaderIconTokenMappingTest` 对已删除的 `com.reader.android.ui.appScreens` 的 import 因此失效，属已知残留，需人工决定是迁移到 `io/reader/android` 测试源集还是移入 `tools/` 证据目录。
- 删除后复跑 `python3 .github/scripts/check-design-inputs.py --case retired-inputs`：PASS。
