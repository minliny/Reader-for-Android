# Android Capability Implementation Status

## 1. 总体结论

ANDROID_CAPABILITY_CORE_FLOW_READY

（Fake/fixture 环境下 Search → Detail → TOC → Content → Progress → Cache 完整可跑通）

## 2. 状态

| 项目 | 值 |
|------|-----|
| HEAD | `3a1ceed` |
| git | 干净（`app/build 2/` 可忽略） |
| 本轮是否修改 UI | 否 |
| 本轮是否修改 Reader-Core | 否 |
| 本轮是否接真实网络 | 否 |

## 3. 能力矩阵

| 能力 | 当前状态 | 文件位置 | 测试覆盖 | 问题 | 下一步 |
|------|---------|---------|---------|------|------|
| **Search** | ✅ Fake+Real bridge | `SearchAdapterShell.kt`, `CoreBridge.kt` | ✅ | - | - |
| **Detail** | ✅ Fake+Real bridge | `BookDetailAdapterShell.kt` | ✅ | - | - |
| **TOC** | ✅ Fake+Real bridge | `CoreTocToReaderTocMapper.kt`, `ReaderDirectoryAdapterShell.kt` | ✅ | - | - |
| **Content** | ✅ Fake+Real bridge | `ContentAdapterShell.kt` | ✅ | - | - |
| **BookSource** | ✅ 导入/启用/禁用 | `BookSourceRepository.kt`, `DataStoreBookSourceRepository.kt`, `FakeBookSourceRepository` | ✅ | DataStore 需 Android Context | - |
| **Bookshelf** | ✅ 加入/移除 | `BookshelfActionAdapter.kt` | ✅ | In-memory only | Room/DS |
| **Progress** | ✅ Save/Read | `ReaderProgressSaveAdapter.kt`, `ReadingProgress.kt` (Room) | ✅ | Room runtime 未实例化 | DI |
| **Cache** | ✅ Read/Write | `ReaderCacheSaveAdapter.kt`, `ChapterCache.kt` (Room) | ✅ | Room runtime 未实例化 | DI |
| **Bookmark** | ✅ 存储+动作 | `BookmarkEntity.kt`, `ReaderBookmarkActionAdapter.kt` | ✅ | Room runtime 未实例化 | DI |
| **Error model** | ✅ BridgeResult+ReaderError | `ReaderCoreBridge.kt`, `ReaderErrorCode` | ✅ | - | - |
| **Fake/Real gate** | ✅ Mode.FAKE 默认 | 各 AdapterShell | ✅ | - | - |

## 4. P0 能力缺口

| P0 | 状态 | 说明 |
|----|------|------|
| P0-1: Search→Detail→TOC→Content | ✅ | CoreBridge 统一 contract |
| P0-2: BookSource 导入/启用 | ✅ | 两个 Repository 实现 |
| P0-3: Content 缓存 | ✅ | In-memory adapter + Room entity |
| P0-4: Bookshelf 加入/打开 | ✅ | In-memory adapter |
| P0-5: Progress 保存/恢复 | ✅ | In-memory adapter + Room entity |
| P0-6: Error 模型统一 | ✅ | BridgeResult<>, ReaderError |
| P0-7: 真实网络默认关闭 | ✅ | Mode.FAKE |

**无 P0 缺口。**

## 5. P1 缺口

| P1 | 说明 | 影响 |
|----|------|------|
| Room DAO runtime 实例化 | ReadingProgressDao/BookmarkDao/CachedChapterDao 无运行时实例 | Progress/Cache/Bookmark 为 in-memory 模式 |
| DataStore 需 Context | DataStoreBookSourceRepository 需 Android Context | 单元测试用 FakeBookSourceRepository |
| Controlled online gate | 无全局网络开关 | 仅各 AdapterShell 的 Mode.FAKE/REAL |

## 6. 测试覆盖

| 类别 | 数量 | 状态 |
|------|------|------|
| 单元测试总文件 | 150 | ~893 tests |
| Bridge contract | ✅ | CoreBridge interface test |
| Repository | ✅ | FakeBookSourceRepository used in tests |
| Search adapter | ✅ | SearchFacadeAdapterTest |
| Detail adapter | ✅ | BookDetailFacadeAdapterTest |
| TOC adapter | ✅ | CoreTocToReaderTocMapperTest |
| Content adapter | ✅ | ReaderContentFacadeAdapterTest |
| Progress adapter | ✅ | ReaderProgressCacheLocalStateAdapterTest |
| Cache adapter | ✅ | ReaderCacheFlowTest |
| Bookmark adapter | ✅ | ReaderBookmarkStorageFoundationTest |
| E2E fake flow | ✅ | All adapter shells work with FakeCoreBridge |

## 7. 验证结果

| 命令 | 结果 |
|------|------|
| `./gradlew test` | ~893 tests, 2 pre-existing UI failures |

## 8. 后续

1. **P1 优先**: Room DAO DI 实例化（需 App 级别的 Database 初始化）
2. **然后**: 连接真实网络 gate
3. **最后**: UI 复核
