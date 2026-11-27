# Reader Android M1／M2 开发宿主

开发身份为 `io.reader.minliny.rebuild.android.dev`。本波交付 Core 队列、生产网络宿主、Compose 设计与消息组件、静态文本排版和业务协调器。完整 Compose 业务页与黄金快照归下一波。

## 目录与所有权

- `app/src/main/java/com/reader/core` 保存逐字节同步的 Core Java JNI 绑定。`core/generated` 保存生成协议。`core` 持有单消费者队列、Host 回包路由和原运行时 ACK。
- `host/storage` 提供 SAF 授权、沙箱暂存、按 SHA-256 留存的原书和有界 EPUB 读取。`host/network` 执行 OkHttp、来源隔离的 CookieJar、Android WebView 与分段媒体写入。Android WebView 的 `MULTI_PROFILE` 能力决定能否上报后台 WebView 和打开独立人工登录。源登录状态无法跨 profile 混用。用户提供的 HTTP 书源与 HTTPS 书源均可由宿主读取。
- `design` 消费 Reader-UI 语义令牌、度量和图标，提供五类消息组件。电子书脚本、Noto 字体和共享 SVG 从锁定的 Reader-UI 对象同步。阅读器采用同一 `ReadingSurface`，文本在 Android `StaticLayout` 分页，电子书使用私有 HTTPS 壳和限定来源的消息端口。
- `features` 通过生成 RPC 协调导入、搜索、在线阅读、换源、书源和下载。页面只持有观察权。Core 保存书库和操作终态，下载 journal 保存待核原参数。Library47 远端布局通过 `source.group.catalog` 消费 Core 派生分组。
- `app/src/test` 运行独立主机验证。`app/src/androidTest` 保存独立 Runner 测试和 EPUB 夹具。产品包不包含测试类或测试 EPUB。

## 依赖

| 依赖 | 固定版本 | 许可 |
| --- | --- | --- |
| OkHttp | 5.4.0 | Apache-2.0 |
| Compose UI、Foundation | 1.10.0 | Apache-2.0 |
| Compose Material 3 | 1.4.0 | Apache-2.0 |
| Compose Material Icons Extended | 1.7.8 | Apache-2.0 |
| Compose Compiler Gradle plugin | 2.2.21 | Apache-2.0 |
| Lifecycle Runtime Compose | 2.9.4 | Apache-2.0 |
| AndroidSVG AAR | 1.4 | Apache-2.0 |
| Robolectric，仅主机测试 | 4.16.1 | MIT |
| Reader-UI Noto Sans SC、Noto Serif SC | `pins.json` 的 UI 提交 | OFL-1.1 |

OkHttp 5.4.0 的 Android AAR 声明最低 compileSdk 36，与当前 AGP 和 SDK 相符。OkHttp 5.5.1 的 Android AAR 声明 37。

Compose 解析到 `androidx.graphics:graphics-path:1.0.1`（Apache-2.0）。双包核验同时检查 Graphics Path 与 Core JNI 的 ELF 三个 LOAD 段和 16 KB ZIP 偏移。

## 复现

Mac 构建输入为 JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK 36、Build Tools 36.1.0、NDK 26.3.11579264、CMake 3.22.1、Rust（按 Core pin 声明）。

```sh
python3 scripts/sync-pinned.py --check
python3 scripts/check-layout.py
scripts/build-native.sh .build/native-m2 android
scripts/build-native.sh .build/native-m2 host
scripts/gradle.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check-unsigned-apks.py \
  app/build/outputs/apk/debug/app-debug-unsigned.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`READER_GRADLE_BIN` 可指向已安装的 Gradle 8.13。`READER_CORE_SOURCE` 可指向同一 pin 的干净 Core 源。默认 Core 快照为 `.build/core-source/<sha>`。`sync-pinned.py --check` 对照 `pins.json` 重新读取锁定的 Git 对象，逐项核对生成物、字体、图标和夹具。

主机测试使用生产 Kotlin 桥接、真实 macOS JNI、受控回环 HTTP 和独立 JVM。主机上的 WebView RPC 使用受控替身回包。StaticLayout 原生图形测试使用 Robolectric API 35，不构成设备或 AVD 取证。调试包与 Runner 默认无签名。
