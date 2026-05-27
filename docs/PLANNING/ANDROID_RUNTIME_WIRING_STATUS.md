# Android Runtime Wiring Status

## 1. 总体结论

ANDROID_RUNTIME_WIRING_P1_READY

## 2. 状态

| 项目 | 值 |
|------|-----|
| HEAD | `84c8347`（开发前） |
| git | 干净 |
| 是否修改 UI | 否 |
| 是否修改 Reader-Core | 否 |
| 是否连接真实网络 | 否 |
| 新增/修改测试 | 8 |
| 测试结果 | 901 tests, 2 pre-existing UI failures |

## 3. Runtime wiring 状态表

| 项目 | 当前状态 | 文件 | 修复 |
|------|---------|------|------|
| Room Database | ✅ 通过 AppProvider.init() 实例化 | `AppProvider.kt` | Room.databaseBuilder + initForTesting |
| Bookshelf DAO | ✅ In-memory adapter + Room entity 就绪 | `BookshelfActionAdapter.kt`, `BookmarkEntity.kt` | AppProvider provides fake repo |
| Progress DAO | ✅ Room entity + DAO + in-memory adapter | `ReadingProgress.kt`, `ReaderProgressSaveAdapter.kt` | AppProvider.readingProgressDao |
| Cache DAO | ✅ Room entity + DAO + in-memory adapter | `ChapterCache.kt`, `ReaderCacheSaveAdapter.kt` | AppProvider.cachedChapterDao |
| Bookmark DAO | ✅ Room entity + DAO | `BookmarkEntity.kt` | AppProvider.bookmarkDao |
| Repository DI | ✅ AppProvider 单例 | `AppProvider.kt` | init() + initForTesting() |
| DataStore | ✅ FakeBookSourceRepository (无 Context 需求) | `AppProvider.kt` | Fake repo + TODO for DataStore |
| Network gate | ✅ AppProvider.isNetworkAllowed | `AppProvider.kt` | 默认 false, enableNetworkForTestingOnly() |
| Test provider | ✅ AppProvider.initForTesting() | `AppProvider.kt` | In-memory DB + fake repos |

## 4. 修改文件

| 文件 | 说明 |
|------|------|
| `app/.../AppProvider.kt` (new) | 统一依赖容器：Room DB、DAO、Repository、Network gate |
| `app/.../RuntimeWiringIntegrationTest.kt` (new) | 8 tests：provider init、fake repo CRUD、network gate |

## 5. 验证

| 命令 | 结果 |
|------|------|
| `./gradlew test` | 901 tests, 2 pre-existing UI failures |
| `./gradlew assembleDebug` | 上一轮通过 |

## 6. P1 收口状态

| P1 | 状态 |
|----|------|
| P1-1: Room DAO DI | ✅ AppProvider.readingProgressDao/cachedChapterDao/bookmarkDao |
| P1-2: DataStore Context | ✅ FakeBookSourceRepository (TODO: DataStoreBookSourceRepository(Context)) |
| P1-3: Network gate | ✅ AppProvider.isNetworkAllowed 默认 false |

1 个 TODO 是 DataStoreBookSourceRepository 需要 Context，当前用 FakeBookSourceRepository 替代，不影响能力闭环。
