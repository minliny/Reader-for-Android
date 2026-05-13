# Reader for Android - Code Wiki

**项目名称**: Reader for Android  
**文档版本**: 1.0  
**最后更新**: 2026-05-13

---

## 目录

1. [项目概述](#项目概述)
2. [整体架构](#整体架构)
3. [主要模块详解](#主要模块详解)
4. [关键类与函数](#关键类与函数)
5. [依赖关系](#依赖关系)
6. [项目运行方式](#项目运行方式)
7. [开发路线图](#开发路线图)
8. [决策记录](#决策记录)

---

## 项目概述

### 项目简介

Reader for Android 是一个基于 Reader-Core（Swift编写）的Android阅读应用，用于从网络书源获取和阅读小说。项目采用 UI-First 开发策略，分阶段实现核心功能。

### 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **构建工具**: Gradle Kotlin DSL
- **目标平台**: Android (minSdk 26+)

### 核心特性

- 多书源支持与管理
- 小说搜索、目录获取、内容阅读
- 本地持久化与缓存
- WebView/JS动态渲染支持
- WebDAV备份与同步

---

## 整体架构

### 架构设计原则

Reader for Android 采用分层架构，与 Reader-Core 保持接口契约一致，通过 JSON 级别的契约进行集成（而非二进制集成）。

### 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ Bookshelf    │ │ Search       │ │ Reader               │ │
│  │ Sources      │ │ BookDetail   │ │ Settings             │ │
│  └──────────────┘ └──────────────┘ └──────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                   Presentation Layer                         │
│                  (ViewModel + State)                         │
├─────────────────────────────────────────────────────────────┤
│                     Domain Layer                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │               ReaderCoreBridge                         │ │
│  │  (Contract between Android and Reader-Core)           │ │
│  └───────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│                      Data Layer                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐ │
│  │ Repositories │ │   Adapters   │ │    Persistence       │ │
│  │              │ │ (HTTP, JS,   │ │ (Room, DataStore,    │ │
│  │              │ │  WebDAV)     │ │  File System)        │ │
│  └──────────────┘ └──────────────┘ └──────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 与 Reader-Core 的集成策略

采用 **JSON级别契约** 策略（Strategy A）：

- **Reader-Core** 定义 DTO 模式和协议契约
- **Android** 独立实现等效的 Kotlin DTO 和协议
- 通过 Core 一致性测试验证行为对等性
- 不依赖 Swift 工具链，无跨编译复杂性

---

## 主要模块详解

### 1. Core Bridge 模块

**文件**: [CORE_BRIDGE_DESIGN.md](file:///workspace/docs/design/CORE_BRIDGE_DESIGN.md)

**职责**:
- 定义与 Reader-Core 的接口契约
- 实现 Kotlin DTO 与 Swift DTO 的对等映射
- 提供 FakeCoreBridge 用于早期 UI 开发

**关键组件**:
- `BookSource` - 书源数据模型
- `SearchResultItem` - 搜索结果项
- `TOCItem` - 目录项
- `ContentPage` - 内容页
- `BookInfo` - 书籍信息
- `MappedReaderError` - 错误类型

### 2. Data Layer 模块

**文件**: [DATA_LAYER_DESIGN.md](file:///workspace/docs/design/DATA_LAYER_DESIGN.md)

**职责**:
- 本地数据持久化策略
- Repository 模式实现
- 混合存储方案（Room + DataStore + 文件系统）

**存储分配**:

| 数据类型 | 存储方案 | 原因 |
|---------|---------|------|
| BookSource列表 | Room | 需要过滤、排序、分组查询 |
| 书架书籍元数据 | Room | 需要关系查询、排序 |
| 阅读进度 | DataStore | 简单键值对，高频率写入 |
| 用户设置 | DataStore | 经典键值对场景 |
| 章节缓存 | 文件系统 | 大二进制数据，LRU淘汰 |
| 搜索历史 | DataStore | 小列表，简单序列化 |
| Cookie/会话 | EncryptedSharedPreferences | 小数据，敏感信息 |
| 备份元数据 | Room | 结构化，需要列表/过滤 |

### 3. HTTP Adapter 模块

**文件**: [HTTP_ADAPTER_DESIGN.md](file:///workspace/docs/design/HTTP_ADAPTER_DESIGN.md)

**职责**:
- 实现 `HTTPClient` 协议
- Cookie 管理与隔离
- 网络错误映射
- 请求/响应拦截

**技术选型**: OkHttp

**理由**:
- Android 标准 HTTP 客户端
- 内置 `CookieJar` 接口
- 连接池、HTTP/2、响应缓存
- 成熟稳定，生产验证

**关键组件**:
- `OkHttpAdapter` - OkHttp 封装
- `ScopedCookieJarAdapter` - 作用域 Cookie 隔离
- `NetworkErrorMapper` - 错误映射器

### 4. WebView / JS Adapter 模块

**文件**: [WEBVIEW_JS_ADAPTER_DESIGN.md](file:///workspace/docs/design/WEBVIEW_JS_ADAPTER_DESIGN.md)

**职责**:
- JavaScript 执行引擎
- WebView 浏览器引擎
- 沙箱安全约束
- 登录流程处理

**双引擎架构**:

| 协议 | 引擎 | 用途 |
|-----|------|------|
| `JSExecutionAdapter` | QuickJS | 简单轻量，无需 DOM |
| `RuntimeJavaScriptExecutorProtocol` | QuickJS | 沙箱执行，上下文隔离，令牌生成 |
| `RuntimeWebViewExecutorProtocol` | Android WebView | 完整 DOM、导航、交互、登录流程 |

### 5. WebDAV Sync 模块

**文件**: [WEBDAV_DESIGN.md](file:///workspace/docs/design/WEBDAV_DESIGN.md)

**职责**:
- WebDAV 备份与同步
- 三方同步策略
- 冲突处理
- 后台同步调度

**技术选型**: OkHttp + 手动 WebDAV

**WebDAV 目录布局**:
```
/ReaderSync/
  manifest.json
  devices/{deviceId}.json
  sources/book_sources/{sourceId}.json
  bookshelf/books/{bookId}.json
  progress/latest/{progressId}.json
  progress/devices/{deviceId}/{progressId}.json
  progress/history/{progressId}/{timestamp}.json
  snapshots/sync_state/{deviceId}_sync_state.json
```

---

## 关键类与函数

### 核心契约类

#### ReaderCoreBridge
```kotlin
// 待实现
interface ReaderCoreBridge {
    fun search(bookSource: BookSource, keyword: String): List<SearchResultItem>
    fun getBookInfo(bookSource: BookSource, bookUrl: String): BookInfo
    fun getTOC(bookSource: BookSource, tocUrl: String): List<TOCItem>
    fun getContent(bookSource: BookSource, contentUrl: String): ContentPage
}
```

#### FakeCoreBridge
```kotlin
// 待实现 - 早期 UI 开发使用
class FakeCoreBridge : ReaderCoreBridge {
    // 返回硬编码的模拟数据
}
```

### 网络层类

#### OkHttpAdapter
```kotlin
// 待实现
class OkHttpAdapter(
    private val client: OkHttpClient,
    private val cookieJar: ScopedCookieJar? = null
) : HTTPClient {
    override suspend fun send(request: HTTPRequest): HTTPResponse
}
```

#### ScopedCookieJarAdapter
```kotlin
// 待实现
class ScopedCookieJarAdapter : ScopedCookieJar {
    private val jars = ConcurrentHashMap<CookieJarScopeKey, CookieJar>()
    // 委托 get/set/clear 到对应作用域的 CookieJar
}
```

### JS 执行类

#### QuickJSJSAdapter
```kotlin
// 待实现
class QuickJSJSAdapter(
    private val sandbox: RuntimeJavaScriptSandbox = .default
) : JSExecutionAdapter, RuntimeJavaScriptExecutorProtocol {
    override suspend fun execute(
        script: String,
        context: RuntimeJavaScriptContext
    ): RuntimeJavaScriptExecutionResult
}
```

#### AndroidWebViewAdapter
```kotlin
// 待实现
class AndroidWebViewAdapter(
    private val configuration: WebViewConfiguration
) : RuntimeWebViewExecutorProtocol {
    override suspend fun execute(
        request: RuntimeWebViewRequest
    ): RuntimeWebViewResult
}
```

### WebDAV 同步类

#### AndroidWebDAVSyncTransport
```kotlin
// 待实现
class AndroidWebDAVSyncTransport(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val credentials: WebDAVCredentials
) : SyncTransportProtocol {
    override suspend fun push(path: String, data: ByteArray, etag: String?): SyncTransportResponse
    override suspend fun pull(path: String): SyncTransportResponse
    override suspend fun list(path: String): List<SyncTransportItem>
    override suspend fun delete(path: String, etag: String?): SyncTransportResponse
    override suspend fun head(path: String): SyncTransportResponse
    override suspend fun probe(): Boolean
}
```

---

## 依赖关系

### 计划中的依赖

| 依赖 | 用途 | 阶段 |
|-----|------|------|
| OkHttp | HTTP 客户端 | P2-S3 |
| kotlinx.serialization | JSON 序列化 | P1-S2 |
| Room | 结构化数据存储 | P2-S9 |
| DataStore | 键值对存储 | P2-S6 |
| WorkManager | 后台任务调度 | P3-S11 |
| AndroidX Security | 加密存储 | P3-S11 |
| quickjs-android | JS 引擎 | P3-S7 |

### 依赖关系图

```
UI Layer
    ↓
Presentation Layer (ViewModel)
    ↓
Domain Layer (ReaderCoreBridge)
    ↓
Data Layer
    ├─ Repositories
    ├─ Adapters (OkHttp, QuickJS, WebView, WebDAV)
    └─ Persistence (Room, DataStore, Files)
```

---

## 项目运行方式

### 项目结构（计划）

```
/workspace/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/
│   │   │   │   └── com/reader/android/
│   │   │   │       ├── ui/              # UI 层 (Compose)
│   │   │   │       ├── viewmodel/       # ViewModel 层
│   │   │   │       ├── domain/          # 领域层
│   │   │   │       ├── data/            # 数据层
│   │   │   │       │   ├── repository/  # Repository
│   │   │   │       │   ├── adapter/     # 适配器
│   │   │   │       │   └── persistence/ # 持久化
│   │   │   │       └── di/              # 依赖注入
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── gradle/
├── docs/
│   ├── PLANNING/
│   └── design/
├── settings.gradle.kts
└── build.gradle.kts
```

### 构建命令（计划）

```bash
# 调试构建
./gradlew assembleDebug

# 发布构建
./gradlew assembleRelease

# 运行测试
./gradlew test

# 代码检查
./gradlew lint
```

---

## 开发路线图

### 优先级定义

| 层级 | 描述 | 触发条件 |
|-----|------|---------|
| P0 | 基础设施与规划，必须在代码工作前完成 | 立即 |
| P1 | 核心应用结构、模拟数据流、无真实 Core 的 UI 脚手架 | P0 完成后 |
| P2 | 真实 Reader-Core 集成、主要阅读管道、持久化 | P1 + Core 桥接决策后 |
| P3 | 高级功能（WebView、JS、WebDAV、TTS、本地书籍） | P2 完成后 |

### 详细路线图

**文件**: [ANDROID_CAPABILITY_ROADMAP.md](file:///workspace/docs/PLANNING/ANDROID_CAPABILITY_ROADMAP.md)

#### P0: 基础设施与规划（当前阶段）
- 创建 Gradle Kotlin DSL 项目骨架
- 创建应用模块和 Compose 应用壳
- 创建占位页面

#### P1: 本地模型与模拟数据流（UI-First）
- 定义与 Core DTO 匹配的 Kotlin 领域模型
- 定义 FakeCoreBridge 接口和模拟实现
- 创建 SourceManagementScreen
- 创建 SearchScreen、BookDetailScreen、TOCScreen、ReaderScreen
- 连接搜索→详情→目录→阅读器导航

#### P2: 真实集成与阅读管道
- 使用 OkHttp 实现 HTTPClient 适配器
- 实现真实的 BookSourceRepository
- 连接真实的 HTTP 获取 + Core 解析
- 实现阅读进度存储
- 实现阅读器字体/主题设置
- 实现章节内容缓存

#### P3: 高级功能
- 实现 Android WebView 运行时适配器
- 实现 QuickJS/Hermes JS 适配器
- 实现 Cookie/登录适配器
- 实现本地文件选择器 + TXT 阅读器
- 实现 EPUB 解析器
- 实现 TTS 适配器 + 播放 UI
- 实现 WebDAV 客户端适配器
- 实现备份/恢复 UI + WorkManager 调度
- 实现进度云同步管理器

---

## 决策记录

### 待决策事项（BD - Blocking Decisions）

**文件**: [ANDROID_BLOCKERS_AND_DECISIONS.md](file:///workspace/docs/PLANNING/ANDROID_BLOCKERS_AND_DECISIONS.md)

| ID | 问题 | 推荐方案 | 状态 |
|----|-----|---------|------|
| BD-005 | Room vs DataStore（或混合） | 混合：Room 用于结构化数据，DataStore 用于设置，文件用于缓存 | 开放 |
| BD-006 | HTTP 客户端选择 OkHttp vs kTor | OkHttp | 开放 |
| BD-007 | Swift Core ↔ Kotlin 桥接策略 | Strategy A: JSON 级别契约 | 开放 |
| BD-008 | 是否允许真实 HTTP 获取的网络访问 | 推迟到用户决策 | 开放 |
| BD-009 | JS 引擎选择 QuickJS vs Hermes | QuickJS | 开放 |
| BD-010 | WebDAV 客户端库 vs 手动 OkHttp | OkHttp 手动（无额外库） | 开放 |
| BD-026 | 是否支持基于 WebView 的 JS 书源 | 是，使用 Android WebView | 开放 |
| BD-027 | 是否支持 WebDAV 备份/同步 | 是，使用上述策略 | 开放 |

---

## 参考文档

- [Reader-Core Integration Contract](file:///workspace/docs/design/CORE_BRIDGE_DESIGN.md)
- [Android Data Layer Design](file:///workspace/docs/design/DATA_LAYER_DESIGN.md)
- [Android HTTP Adapter Design](file:///workspace/docs/design/HTTP_ADAPTER_DESIGN.md)
- [Android WebView / JS Platform Adapter Design](file:///workspace/docs/design/WEBVIEW_JS_ADAPTER_DESIGN.md)
- [Android WebDAV Three-Way Design](file:///workspace/docs/design/WEBDAV_DESIGN.md)
- [Android Capability Roadmap](file:///workspace/docs/PLANNING/ANDROID_CAPABILITY_ROADMAP.md)

---

*本文档将随着项目发展持续更新。*
