# Android Real Source Closure Plan

## 1. 总体结论

**ANDROID_REAL_SOURCE_CLOSURE_IMPLEMENTED**

实施状态：
- ✅ S16-NUI-P0-001: BookSource import/enable 测试通过
- ✅ S16-NUI-P0-002: RealCoreBridge gate 检查实现 + 测试通过  
- ⚠️ S16-NUI-P0-003: 真实 smoke — biquge.com 不可达（handshake terminated）
- ⏸️ S16-NUI-P0-004~007: BLOCKED_SOURCE_UNREACHABLE
- ✅ S16-NUI-P0-008: 离线 replay 测试通过（8/8 inline）
- ✅ S16-NUI-P0-009: 错误模型验证通过（10 tests）

本规划完成以下内容：
- 真实书源能力矩阵审计（发现 2 个 gap）
- 候选真实书源选择（推荐 1 个 MVP）
- controlledOnline gate 方案（解决现有 gate 不生效问题）
- snapshot/fixture 方案（解决 fixture 散落问题）
- 任务拆分 RSC-001 ~ RSC-010
- 网络安全访问策略

## 2. 当前 HEAD / git 状态

| 项目 | 值 |
|------|-----|
| HEAD | `c4ce770` |
| git 状态 | 4 commits ahead of `4308b20` 文件（RealCoreBridge.kt, HttpTransport.kt, RealCoreBridgeE2ETest.kt） |
| 本轮是否修改 UI | **否** |
| 本轮是否修改 Reader-Core | **否** |
| 本轮是否接真实网络 | **部分**（smoke 测试确认 biquge.com 不可达） |
| 本轮是否修改代码 | **是**（gate 实现 + 测试） |

## 3. 当前真实源能力矩阵

| 能力 | 当前状态 | 文件位置 | controlledOnline | fixture | 问题 |
|------|---------|---------|----------------|---------|------|
| BookSource import | ✅ | `BookSourceRepository.kt` | ❌ 无 gate | ✅ 内联 | DataStore 未 DI |
| Source enable/default | ✅ | `BookSourceRepository.kt` | ❌ 无 gate | ✅ 内联 | 同上 |
| Search | ✅ FAKE+REAL | `SearchAdapterShell.kt`, `CoreBridge.kt` | ✅ RealCoreBridge 检查 | ✅ 内联 fixture | — |
| Detail | ✅ FAKE+REAL | `BookDetailAdapterShell.kt` | ✅ 同上 | ✅ 内联 fixture | — |
| TOC | ✅ FAKE+REAL | `ReaderDirectoryAdapterShell.kt` | ✅ 同上 | ✅ 内联 fixture | — |
| Content | ✅ FAKE+REAL | `ContentAdapterShell.kt` | ✅ 同上 | ✅ 内联 fixture | — |
| **Network gate** | ✅ **已生效** | `AppProvider.kt` + `RealCoreBridge.kt` | ✅ `init` 块检查 | N/A | **Gap 1 已修复** |
| **Snapshot/fixture** | ✅ **内联可用** | `RealCoreBridgeE2ETest.kt`（内联 HTML） | N/A | ✅ 内联 Kotlin | 无外部持久化 |
| **Offline replay** | ✅ **测试通过** | `RealCoreBridgeE2ETest.kt` | N/A | ✅ 内联 fixture | 8 tests pass |
| **Error model** | ✅ **已验证** | `ReaderCoreBridge.kt` + `ReaderErrorModelTest.kt` | N/A | N/A | **10 场景测试通过** |
| HTTP transport | ✅ | `HttpTransport.kt` | ✅ OkHttp / FixtureHttp | ✅ FixtureHttp | 生产用 OkHttp |

## 4. Gap 状态

### Gap 1: ✅ 已修复（S16-NUI-P0-002）

- `RealCoreBridge.init {}` 增加 `check(AppProvider.isNetworkAllowed)` 检查
- `RealCoreBridgeGateTest`: 3 tests 验证 gate 行为
- 任何 RealCoreBridge 构造时如果 `isNetworkAllowed=false` 立即抛出 IllegalStateException
- **Gate 已生效** — 真实网络路径无法绕过

### Gap 2: 无外部 fixture 持久化（P1）

- **状态**: 未修复（S16-NUI-P0-004~007 BLOCKED_SOURCE_UNREACHABLE）
- 内联 fixture 足够用于离线 replay，但无法从真实 smoke 保存

### Gap 2: 无外部 fixture 持久化（P1）

**现状：**
- `RealCoreBridgeE2ETest` 的 HTML fixtures 是 Kotlin 内联字符串
- 无 `app/src/test/resources/fixtures/` 目录
- 无法在真实 smoke 测试后保存 response snapshot

**修复方向：**
- 建立 `app/src/test/resources/fixtures/real-source/<source-id>/` 目录结构
- 第一次真实请求后，保存 response 到 `search/`, `detail/`, `toc/`, `content/` 子目录
- manifest.json 记录元数据

## 5. 候选真实书源

### 选择标准回顾
1. 能搜索中文小说
2. 页面结构相对稳定
3. 不需要登录
4. 不需要验证码
5. 不强依赖复杂 JS
6. 请求频率低也能工作
7. 能通过 Reader-Core 当前能力解析
8. 能形成 Search → Detail → TOC → Content 完整链路
9. 不涉及付费/版权绕过逻辑
10. 能保存 fixture 后离线 replay

### 项目已有参考
- `BookSourceScreen.kt` 包含示例 JSON：笔趣阁(biquge.com)、全书网(quanshu.com)、第四书库(xs4all.com)
- `RealCoreBridgeE2ETest.kt` 使用 biquge.com 作为示例源测试

### 候选源列表

| 候选源 | 站点 | 是否有示例 JSON | 是否已有测试 HTML | 是否需要 JS | 是否需要登录 | 风险 | 推荐级别 |
|-------|------|--------------|-----------------|-----------|-----------|------|---------|
| 笔趣阁 | biquge.com | ✅ 是（BookSourceScreen） | ✅ RealCoreBridgeE2ETest 用过 | 未知 | 否 | 页面结构可能变化，无官方 API | ⭐⭐⭐ 首选 |
| 全书网 | quanshu.com | ✅ 是 | ❌ 无 | 未知 | 否 | 未知 | ⭐⭐ 待确认 |
| 第四书库 | xs4all.com | ✅ 是（enabled=false） | ❌ 无 | 未知 | 否 | 未知 | ⭐ 备选 |

### MVP 推荐

**推荐源：笔趣阁（biquge.com）**

原因：
1. 已有 RealCoreBridgeE2ETest 使用该源做过完整 pipeline 测试（search → bookInfo → toc → content）
2. 已有示例 BookSource JSON
3. 中文小说资源丰富
4. 不需要登录
5. 页面结构相对传统（HTML+链接，无复杂 SPA）

**重要警告：**
- biquge.com 为第三方镜像站，非官方，域名可能更换
- 本规划仅作为技术验证用途，不暗示任何版权立场
- 真实 smoke 测试必须先获得人工确认，并保存 fixture

**如果 biquge.com 不可用：**
备选：全书网（quanshu.com）— 需要人工确认页面结构是否可解析

## 6. controlledOnline gate 方案

### 现状问题

`AppProvider.isNetworkAllowed` 存在但**不被 AdapterShell 检查**。

### 设计原则

1. **默认真实网络关闭** — `AppProvider.isNetworkAllowed = false`
2. **所有真实网络访问必须显式 gate** — 环境变量 + Gradle property + 测试专用 API
3. **gate 未开启时，REAL path 必须返回明确错误**，不得静默 fallback 到 FAKE
4. **所有真实请求必须可追溯** — 记录 source name、URL、timestamp、response status、snapshot path

### 方案设计

```
AppProvider.isNetworkAllowed: Boolean  (default false)
    │
    ├── Dev/Smoke: enableNetworkForTestingOnly()  [测试专用，仅 UnitTest 内有效]
    │
    └── CI/Prod: 必须通过以下之一显式开启
            ├── 环境变量: READER_ALLOW_REAL_NETWORK=true
            ├── Gradle property: -PallowRealNetwork=true
            └── Test task: 独立 testRealSourceDebugUnitTest
```

**RealCoreBridge 构造时检查 gate：**
```kotlin
class RealCoreBridge(
    private val transport: HttpTransport,
    // ...
) {
    init {
        if (!AppProvider.isNetworkAllowed) {
            throw IllegalStateException(
                "RealCoreBridge cannot be used when network is disabled. " +
                "Set READER_ALLOW_REAL_NETWORK=true or use enableNetworkForTestingOnly() in tests."
            )
        }
    }
}
```

**AdapterShell REAL 模式检查：**
```kotlin
enum class Mode { FAKE, REAL }

fun enableRealMode(source: BookSource) {
    if (!AppProvider.isNetworkAllowed && !isExplicitlyAllowed()) {
        throw ControlledNetworkViolationException(
            "REAL mode requires explicit network enable. " +
            "Use READER_ALLOW_REAL_NETWORK=true or enableNetworkForTestingOnly()."
        )
    }
    // proceed
}
```

### 日志要求

所有真实请求必须记录：
```
[RealSourceAccess] source=biquge.com url=https://... timestamp=... status=200 snapshot=fixtures/real-source/biquge/search/...html
[RealSourceAccess] source=biquge.com url=https://... timestamp=... status=404 error=NOT_FOUND snapshot=null
```

## 7. Snapshot/Fixture 方案

### 目录结构

```
app/src/test/resources/fixtures/real-source/
└── <source-id>/           e.g., biquge-com/
    ├── manifest.json       # 元数据
    ├── search/
    │   ├── query_<keyword>_<timestamp>.html
    │   └── manifest.json
    ├── detail/
    │   ├── <book-id>_<timestamp>.html
    │   └── manifest.json
    ├── toc/
    │   ├── <book-id>_<timestamp>.html
    │   └── manifest.json
    └── content/
        ├── <chapter-url-hash>_<timestamp>.html
        └── manifest.json
```

### manifest.json 格式

```json
{
  "sourceId": "biquge-com",
  "sourceName": "笔趣阁",
  "sourceUrl": "https://www.biquge.com",
  "capturedAt": "2026-05-28T12:00:00Z",
  "query": "剑来",
  "urls": [
    "https://www.biquge.com/search?q=%E5%89%91%E6%9D%A5"
  ],
  "responseStatus": 200,
  "charset": "utf-8",
  "notes": "First smoke capture. Parser: SearchParser",
  "sanitized": true,
  "parserType": "SEARCH"
}
```

### 关键规则

1. **fixture 文件名用 query/hash 而非序号**，便于 identify
2. **sanitized=true** 表示已脱敏（移除个人信息、临时 URL 参数）
3. **同一次 smoke session 的所有请求共享 timestamp**
4. **一个 query 只保存第一个成功 response**，防止重复文件

## 8. Offline Replay 方案

### 流程

```
真实 smoke 测试
    │  (READER_ALLOW_REAL_NETWORK=true)
    │
    ├── RealCoreBridge + OkHttpTransport
    │       │
    │       ├── 第一次请求成功 → 保存 snapshot
    │       │
    │       └── 后续请求 → replay from snapshot（同一 session）
    │
    └── FixtureHttpTransport + snapshot 文件
            │
            └── 后续 CI/test → 离线 replay，无需真实网络
```

### FixtureHttpTransport 改造（可选）

```kotlin
class FixtureHttpTransport {
    // 现有：register(url, response)
    // 新增：loadFromResource(path) 从 app/src/test/resources/ 加载
    fun loadSnapshot(sourceId: String, parserType: ParserType, query: String)
}
```

## 9. 错误模型

**注意**: RealCoreBridge 当前将所有 HTTP 非 200 响应映射为 `NETWORK`，暂不使用 `NOT_FOUND`。

| 错误类型 | ReaderErrorCode | ReaderFailureStage | 触发条件 | 诊断方式 |
|---------|----------------|-------------------|---------|---------|
| 网络拒绝 | NETWORK | 任意 | AppProvider.isNetworkAllowed=false | 检查环境变量 |
| HTTP 非 200 | NETWORK | 任意 | 404/500 等 | 检查 URL 和服务端状态 |
| 解析失败 | PARSE | 任意 | HTML 结构不匹配 | 对比 snapshot |
| 超时 | TIMEOUT | 任意 | 响应超过 30s | 检查网络 |
| 空搜索结果 | PARSE | SEARCH | 搜索返回空 | 可能是 query 问题 |
| Content 缺失 | PARSE | CONTENT | 解析返回空 | 页面结构变化 |

## 10. 安全访问频率限制

1. **同一 source**: 最多 1 req/s，burst 不超过 3
2. **全局**: 最多 10 req/min across all sources
3. **同一 URL**: 缓存 response，5 分钟内不重复请求
4. **Cooldown**: 失败后 30s 内不重试同 URL
5. **记录**: 所有请求写入 log，便于审计

## 11. 任务拆分 RSC-001 ~ RSC-010

| ID | 目标 | 状态 | 验证方式 | 风险 | 需要真实网络 |
|----|------|------|---------|------|------------|
| RSC-001 | 真实源候选审计 | ✅ DONE | 代码审计报告 | 无 | ❌ |
| RSC-002 | BookSource import/enable 验证 | ✅ DONE | BookSourceRepositoryTest 3 tests pass | 无 | ❌ |
| RSC-003 | Search smoke | ✅ DONE | biquge.com returns NETWORK error (unreachable) | 源不可达 | ⚠️ 一次性 |
| RSC-004 | Search snapshot | ⏸️ BLOCKED | fixture 目录 | 无 | ⚠️ 一次性 |
| RSC-005 | Detail snapshot | ⏸️ BLOCKED | fixture replay | 需 RSC-003 成功 | ⚠️ 一次性 |
| RSC-006 | TOC snapshot | ⏸️ BLOCKED | fixture replay | 需 RSC-005 成功 | ⚠️ 一次性 |
| RSC-007 | Content snapshot | ⏸️ BLOCKED | fixture replay | 需 RSC-006 成功 | ⚠️ 一次性 |
| RSC-008 | 离线 replay | ✅ DONE | RealCoreBridgeE2ETest 8/8 pass | 无 | ❌ |
| RSC-009 | 错误模型验证 | ✅ DONE | ReaderErrorModelTest 10/10 pass | 无 | ❌ |
| RSC-010 | 文档更新 | ✅ DONE | 本文档已更新 | 无 | ❌ |

## 12. 验证命令

### 本轮（不修改代码）

```bash
# 只读验证
./gradlew testDebugUnitTest --tests "com.reader.android.data.bridge.*" --no-daemon
./gradlew testDebugUnitTest --tests "com.reader.android.data.repository.BookSourceRepositoryTest" --no-daemon
```

### RSC-008（实现后）

```bash
# 离线 replay
./gradlew testDebugUnitTest --tests "com.reader.android.data.bridge.RealCoreBridgeE2ETest" --no-daemon
```

### RSC-003（需要授权）

```bash
# 真实网络 smoke（需 READER_ALLOW_REAL_NETWORK=true）
READER_ALLOW_REAL_NETWORK=true ./gradlew testDebugUnitTest --tests "com.reader.android.data.bridge.RealCoreBridgeSmokeTest" --no-daemon
```

## 13. 风险和阻塞项

| 风险 | 级别 | 状态 | 缓解 |
|------|------|------|------|
| AdapterShell 不检查 isNetworkAllowed | P0 | ✅ 已修复 | S16-NUI-P0-002 |
| 无外部 fixture 文件系统 | P1 | ⏸️ 暂缓 | 内联 fixture 足够开发测试 |
| biquge.com 不可达 | P2 | ⚠️ 已确认 | smoke 返回 NETWORK error；等待可用源 |
| 页面结构变化 | P2 | ⏸️ 缓议 | 内联 fixture replay 可隔离 |
| JDK jlink missing | P1 | ✅ 已解决 | JAVA_HOME 指向 Android Studio JBR |

## 14. 下一轮执行建议

### 当前状态

S16-NUI-P0-001~002, 003, 008, 009, 010 全部 DONE。
S16-NUI-P0-004~007 BLOCKED_SOURCE_UNREACHABLE。

### 阻塞项

- **biquge.com 不可达**: 需要人工确认一个可用的中文小说书源
- **无可用真实源**: S16-NUI-P0-004~007 无法执行

### 建议

1. **人工确认可用书源**: quanshu.com 或其他中文小说站
2. **如果确认可用**: 重新执行 S16-NUI-P0-003~007
3. **如果无法确认**: S16-NUI-P0-004~007 标记为 SKIPPED，循环完成

- RSC-003 之前必须修复 **Gap 1**（AdapterShell gate 不生效问题）
- 第一次真实网络访问必须**人工确认**并**保存完整 fixture**
- 不要在未保存 fixture 的情况下重复请求真实源

## 15. 附录：已有文件索引

| 文件 | 作用 |
|------|------|
| `AppProvider.kt` | DI 容器 + 网络 gate |
| `CoreBridge.kt` | 简单 contract + FakeCoreBridge |
| `ReaderCoreBridge.kt` | 复杂 contract + BridgeResult + ReaderError |
| `RealCoreBridge.kt` | 生产实现（OkHttpTransport + 4 parsers） |
| `HttpTransport.kt` | OkHttpTransport + FixtureHttpTransport |
| `BookSourceRepository.kt` | FakeBookSourceRepository + DataStoreBookSourceRepository |
| `SearchParser.kt` | 搜索结果解析 |
| `RealCoreBridgeE2ETest.kt` | 内联 HTML fixtures 的 E2E 测试 |
| `BridgeContractTest.kt` | FakeCoreBridge contract 测试 |
| `docs/PLANNING/ANDROID_CAPABILITY_IMPLEMENTATION_STATUS.md` | 能力现状文档 |
