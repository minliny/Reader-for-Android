# Reader for Android

原生 Kotlin / Compose Host，业务事实与持久化由 Rust Reader-Core 持有。
功能和视觉参考、运行 Core、交付产物分别绑定，不互相代替验收。

固定产品参考见 `scripts/reference-baseline.json`，运行依赖见
`scripts/core-dependency.json`。当前工作阶段及结果只维护在 Reader 工作区唯一
`DEVELOPMENT_BACKLOG.md`，本文件不维护另一套待办或通过清单。

## 源码边界

- 当前生产 Kotlin 在 `app/src/main/kotlin/io/reader/android`。
- `app/src/core/java` 从固定 Core Git 对象同步，不能手改；配套 JNI 由独立入口构建。
- `app/src/main/kotlin/com`、原设计资料和旧结构测试作为历史输入保留，当前 Gradle
  sourceSets 不将其混入生产或所选测试。是否退役按原迁移任务分别处理。
- Core 负责解析、书架、章节、位置、事务和原操作回执；Android 提供 SAF 原书、
  网络、窗口、系统媒体与渲染。显示偏好和短生命周期页面状态由 Android 持有。
- 本地导入持久保存原书和原操作 journal；超时保留未决，显式恢复核对原绑定。
  普通进度未知写入不重派。可见位置按原生行坐标转换到 Core scalar，绑定正文、
  处理版本、CAS revision 和阅读 session。
- EPUB/MOBI 原文由受限 WebView 渲染，沿用 Core canonical 坐标与既有 iOS DOM
  适配；资源通过同一 App/actor 读取私有原件，不另开 Core runtime。平台适配固定
  `androidx.webkit:webkit:1.18.0`（Apache-2.0），来源为
  [AndroidX WebKit](https://developer.android.com/jetpack/androidx/releases/webkit)。
  每个实际 WebView 使用独立能力与布局身份；格式和真机验收仍在唯一待办分别记录。

## 构建与准确验证入口

工具版本由 Gradle 文件和两个依赖 JSON 固定；JDK 17 通过 `READER_JAVA_HOME`
选择。本机支持既有 Homebrew JDK17，Linux 可使用已有 `JAVA_HOME`。SDK 通过
`ANDROID_HOME` 或忽略的 `local.properties` 提供；不在构建中自动安装 SDK。

- `python3 scripts/build-core.py`：只构建配套 arm64 JNI；使用固定 Git 快照、单一
  Cargo target、规范共享构建租约及有界执行。Core 不变时复用 `.build/core-build.json`。
- `python3 scripts/build.py`：只构建普通 debug APK，将输入、Native/AAR 配对、签名
  与包身份绑定到 `.artifacts/<run>/manifest.json`；不执行测试或设备动作。
- `python3 scripts/test.py --only <完整类或类#方法> --rationale <具体风险>`：仅运行
  点名 JVM 方法，缺 selector 停止；首次缺依赖可显式允许固定依赖下载。运行结果
  及报告归属独立保存，不以 Gradle exit0 替代准确 XML 核对。
- `scripts/device.py` 的 install/launch 均要求显式 manifest 与 serial。USB/无线按
  同一物理身份共享短租约；安装仅覆盖同签名应用并保护数据，不卸载或清数据。

开发产物不声明产品版本，独立维护安装计数器和 buildId。源码检查、JVM 方法、
Native/APK、安装、原生交互和用户验收分别留证。最低系统声明和 ELF 对齐不代替
相应系统或 16KB 设备运行结果。

本机资源吃紧时可在 FnOS 的本任务私有环境构建，仍绑定同一源码和依赖；取回
产物核验字节。USB 安装与交互由设备所在主机持有准确目标租约执行。规则以工作区
受控 `AGENTS.md` 为准。
