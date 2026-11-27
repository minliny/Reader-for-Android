# Reader Android M1 开发宿主

本工程提供 Reader Android 的 Library47、导入与阅读进度、Core 异步回执及原生 EPUB 阅读表面。开发身份为 `io.reader.minliny.rebuild.android.dev`。业务页和交付签名由后续波次承接。

## 目录与所有权

- `app/src/main/java/com/reader/core` 保存逐字节同步的 Core Java JNI 绑定。`core/generated` 保存生成协议，`core` 持有单消费者队列、Host 回包分发和原运行时 ACK。
- `host/storage` 持有 SAF 授权适配、沙箱暂存、按内容 SHA-256 留存的 `.source`、导入 journal 和有界 EPUB 读取。`host/system` 提供实际协商的 `time.now` 插槽。
- `design/generated` 保存同期生成的色表、度量、图标映射。`features/bookshelf` 通过生成 RPC 协调导入，`features/reading` 协调正文、版本、进度与 `EbookWebViewAdapter`。`features/shell` 是启动壳。
- `app/src/main/assets/reader_ebook_runtime` 只打包 Reader-UI 清单里的三个生产文件。`app/src/test` 是主机 JNI 测试，`app/src/androidTest` 是独立仪器测试和 EPUB 夹具入口。

依赖方向为 `features → core / host / design` 和 `host → core`。页面只持有观察权。读取可显式取消，已经派发的写入由原操作取得终态并由原运行时释放配额。已派发而结果未知的导入阶段会保留原 journal 和可能被引用的原书。

电子书使用私有 HTTPS 壳、来源受限的 WebView 消息端口和共享脚本。平台适配仅接受当前主框架、会话、文档及布局代次的事件，资源请求只从当前授权原书读取，出版物 iframe 禁止执行脚本。`ReadingSurface` 负责打开、重排、定位、选区与退场。

## 复现

Mac 构建输入为 JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK 36、Build Tools 36.1.0、NDK 26.3.11579264、CMake 3.22.1、Rust（按 Core pin 声明）。报告目录由外层选择。

```sh
python3 scripts/sync-pinned.py --check
python3 scripts/check-layout.py
scripts/build-native.sh .build/native android
scripts/build-native.sh .build/native host
scripts/gradle.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check-unsigned-apks.py \
  app/build/outputs/apk/debug/app-debug-unsigned.apk \
  app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

`READER_GRADLE_BIN` 可指定已安装的 Gradle 8.13。`READER_CORE_SOURCE` 可指定同一 pin 的干净 Core 源；脚本默认在 `.build/core-source` 准备独立 detached 快照。构建使用 `pins.json` 与 `pinned-inputs.sha256.json`。仅在明确升 pin 并核对上游改动时用 `sync-pinned.py --write` 重投影。`--check` 会重新读取锁定的 Git 对象，并逐项核对入参、生成物和夹具字节。

主机用例执行正式生产桥接、真实 macOS JNI 和两个独立冷启 JVM。设备用例通过生产 WebView 走同一份跨章 EPUB，安装和运行需要单独授权的开发运行环境及对应的包签名。
