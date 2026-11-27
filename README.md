# Reader Android M1／M2 开发宿主与 M3 原生底层

开发身份为 `io.reader.minliny.rebuild.android.dev`。应用以列表书架为入口，接通本地文件导入、在线搜索、详情、目录、统一阅读器、书源管理 v9 和下载管理。书架在 600dp 起提供详情分栏。应用外观、书架排序和阅读排版保存为平台偏好。

## 目录与所有权

- `app/src/main/java/com/reader/core` 保存逐字节同步的 Core Java JNI 绑定。`core/generated` 保存生成协议。`core` 持有单消费者队列、Host 回包路由和原运行时 ACK。
- `host/storage` 提供 SAF 授权、沙箱暂存、按 SHA-256 留存的原书和有界 EPUB 读取。`host/network` 执行 OkHttp、来源隔离的 CookieJar、Android WebView 与分段媒体写入。Android WebView 的 `MULTI_PROFILE` 能力决定能否上报后台 WebView 和打开独立人工登录。源登录状态由来源隔离。关闭会话时，宿主先销毁网页、撤销原租约，再清理该受控 profile 的 Cookie 与网页数据。提供完整清理回执的 WebView 可在同一用途复用已清洁 profile。较旧 WebView 的受控 UUID profile 留待下次进程启动清理，每进程最多分配 128 个名称。用户提供的 HTTP 书源与 HTTPS 书源均可由宿主读取。
- `design` 消费 Reader-UI 语义令牌、度量和图标，提供五类消息组件。电子书脚本、Noto 字体和共享 SVG 从锁定的 Reader-UI 对象同步。阅读器采用同一 `ReadingSurface`，文本在 Android `StaticLayout` 分页，电子书使用私有 HTTPS 壳和限定来源的消息端口。
- `features/shell` 装配应用级 Runtime 与协调器，持有类型化页面路径，并通过 AndroidX Activity 接入系统返回及 Predictive Back。
- `features` 通过生成 RPC 协调导入、搜索、在线阅读、换源、书源和下载。页面只持有观察权。Core 保存书库和操作终态，下载 journal 保存待核原参数。Library47 远端布局通过 `source.group.catalog` 消费 Core 派生分组。
- M3 漫画使用 Core 队列实际授权的 request／operation 独立资产上下文，单块不超过 1 MiB，Core 资产声明不超过 128 MiB。Host 把压缩资产写入私有目录，由进程锁和 192 MiB 临时文件预算管理。Coil 3、telephoto 在本机 Gradle 只读缓存及 Android 工作树依赖缓存中均不可用，采用 API 26 可用的 Android `BitmapRegionDecoder` 分片与进程级 24 MP 分配预算。绘制切片使用逻辑页的原图坐标，章内页序与进度由 Core 确认。生产 UI 路径延续 M1／M2。
- `app/src/test` 运行独立主机验证，Compose 黄金快照使用 Robolectric API 35 的原生图形 Bitmap 渲染和逐像素比对。`app/src/androidTest` 保存独立 Runner 测试和 EPUB 夹具。产品包不包含测试类或测试 EPUB。

## 依赖

| 依赖 | 固定版本 | 许可 |
| --- | --- | --- |
| OkHttp | 5.4.0 | Apache-2.0 |
| AndroidX Activity Compose | 1.11.0 | Apache-2.0 |
| AndroidX Core Kotlin Extensions | 1.17.0 | Apache-2.0 |
| Compose UI、Foundation | 1.10.0 | Apache-2.0 |
| Compose Material 3 | 1.4.0 | Apache-2.0 |
| Compose Material Icons Extended | 1.7.8 | Apache-2.0 |
| Compose Compiler Gradle plugin | 2.2.21 | Apache-2.0 |
| AndroidX Media3 Session、ExoPlayer | 1.8.0 | Apache-2.0 |
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
scripts/build-native.sh .build/native-m3 android
scripts/build-native.sh .build/native-m3-host host
scripts/gradle.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check-unsigned-apks.py \
  app/build/outputs/apk/debug/app-debug-unsigned.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Android M3 原生漫画测试在指定 16 KB AVD 和开发 App／Runner 上运行。使用 `app/src/androidTest/host/run-m3-manga.sh <serial> <run-key> <absolute-evidence-path>` 启动独立回环服务并分别验证写入与独立进程冷启。

`READER_GRADLE_BIN` 可指向已安装的 Gradle 8.13。`READER_CORE_SOURCE` 可指向同一 pin 的干净 Core 源。默认 Core 快照为 `.build/core-source/<sha>`。`sync-pinned.py --check` 对照 `pins.json` 重新读取锁定的 Git 对象，逐项核对生成物、字体、图标和夹具。

主机测试使用生产 Kotlin 桥接、真实 macOS JNI、受控回环 HTTP 和独立 JVM。主机上的 WebView RPC 使用受控替身回包。StaticLayout 原生图形测试使用 Robolectric API 35，不构成设备或 AVD 取证。调试包与 Runner 默认无签名。图像门禁在 `app/src/test` 运行。验收环境已有配对签装双包时，`app/src/androidTest/host/run-m2.sh` 接收准确序列、测试运行标识与证据目录，启动仅绑定回环的 HTTP 夹具，并收集生产宿主及独立进程冷启日志。

对已安装同一开发身份的隔离 AVD，`python3 app/src/androidTest/host/run-product-ui.py --adb /path/to/adb --serial <serial> --avd <AVD> --output <new-directory>` 使用 Android 框架输入和 UiAutomator 验证书源导入、全局搜索、在线详情、正文与目录。此入口只导入当次受控回环源，并经产品确认流按原输入核对和清理这两条书源。
