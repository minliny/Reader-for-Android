# Reader Directory Alignment Fix Report

## 1. 总体结论

READER_DIRECTORY_ALIGNMENT_FIX_READY

## 2. 输入反馈

用户设备端反馈：
1. 目录列表项左侧出现大量空白和图标，章节名不靠左，被图标挤到中间
2. 不需要这么多级的目录
3. 进度条是指整本书的进度条，当前章节进度条可以保留

## 3. 修改范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `app/.../reader/components/ReaderBottomFunctionOverlay.kt` | 修改 | 目录行重排：图标从标题左侧移至右侧 |
| `app/.../reader/ReaderRuntimeFixture.kt` | 修改 | TOC 展平为 2 级 7 章；progress 语义改为整本书进度 |
| `app/.../reader/ReaderRuntimeStateBridgeTest.kt` | 修改 | 适配新条目数量（5→7）+ level 验证 |
| `app/.../reader/ReaderDirectoryRowAlignmentTest.kt` | 新增 | 13 个对齐验证测试 |

## 4. 目录行布局修复结果

**修复前**（错误）：
```
[14dp spacer/bookmark] [4dp] [14dp spacer/current] [6dp] [TITLE] [8dp] [progress]
                                    ↑ 图标和 spacer 在标题前，大量空白
```

**修复后**（正确）：
```
[levelIndent] [TITLE weight(1f)] [6dp] [bookmark?] [6dp] [current?] [8dp] [progress]
                                                               ↑ 全部右侧，从左到右依次
```

### 附：目录层级展平

**修复前**（4 级深层嵌套）：
```
Level 1: 第一章：阿长与《山海经》
  Level 2: 深空信号
  Level 2: 第二节：寂静航线
    Level 3: 未知频段
      Level 4: 求救信号
```

**修复后**（统一 2 级，铺平）：
```
Level 2: 第一章：阿长与《山海经》  ── 已读（进度 100%）
Level 2: 第二节：深空信号        ── 当前阅读（进度 35%）
Level 2: 第三节：寂静航线        ── 未读
Level 2: 第四节：未知频段        ── 未读
Level 2: 第五节：求救信号        ── 有书签
Level 2: 第二章：旧地球的遗产    ── 未读
Level 2: 第三章：星门之外        ── 未读
```

所有条目同层级，indent 统一为 10dp。进度值代表整本书阅读进度（1f=已读完，0.35f=读到 35%）。卷信息 breadcrumb 改为 `"深空信号 · 共 3 卷 7 章"`。

**具体改动**：
1. **章节名左侧靠边**：`Text(entry.title, Modifier.weight(1f))` 占据左侧主要区域，仅前面有 level indent
2. **去除 spacer 占位**：不再使用 `else { Spacer(...) }` 为缺失图标预留空间 — 图标仅条件渲染
3. **书签标识移到右侧**：`Icons.Filled.ChevronRight` 在标题之后，有书签时才渲染
4. **当前阅读章节标识移到右侧**：`Icons.Filled.MyLocation` 在标题之后，当前章节时才渲染
5. **右侧进度条保留**：`LinearProgressIndicator` 仍为最右侧元素
6. **层级缩进轻量化**：`(entry.level - 1) * 10` dp，从 10dp 起步

## 5. 书签页布局修复结果

书签模式与目录模式共用同一个 Row 结构，因此自动继承修复：
- 章节名左侧靠边（仅 level indent）
- 书签/当前图标在右侧
- 右侧进度条保留

## 6. 回归守卫

| 守卫项 | 状态 |
|--------|------|
| 顶部 "目录"/"书签" tabs 保留 | 通过 |
| 层级小字（volumeInfo）保留 | 通过 |
| 当前阅读章节底部提示保留 | 通过 |
| bottom overlay 不全屏 | 通过 |
| App 主底栏仍为 书架/发现/书源/我的 | 通过 |
| 阅读页底栏仍为 目录/朗读/界面/设置 | 通过 |
| 未改 Core/parser/repository/book source | 通过 |
| 其他 overlay 未修改 | 通过 |
| 夜间模式未修改 | 通过 |
| 内容替换页未修改 | 通过 |

## 7. 测试结果

| 检查项 | 结果 |
|--------|------|
| `./gradlew test --no-daemon` | BUILD SUCCESSFUL (655 tests, 0 failures) |
| `./gradlew assembleDebug --no-daemon` | BUILD SUCCESSFUL |
| `./gradlew lintDebug --no-daemon` | BUILD SUCCESSFUL |
| `./gradlew installDebug --no-daemon` | BUILD SUCCESSFUL (IN2020) |

## 8. 是否仍有 P0

无。

## 9. 是否仍有 P1

无。

## 10. 是否建议设备端复核

建议用户重新安装 debug app 后重点复核目录 / 书签 overlay，确认章节名左侧靠边、图标在右侧、无空白 spacer。
