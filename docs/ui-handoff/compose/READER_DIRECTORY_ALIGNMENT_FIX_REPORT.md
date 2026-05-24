# Reader Directory Alignment Fix Report

## 1. 总体结论

READER_DIRECTORY_ALIGNMENT_FIX_READY

## 2. 输入反馈

用户设备端反馈：
- 目录列表项左侧出现大量空白和图标
- 章节名不靠左，被图标挤到中间
- 期望：章节信息左侧靠边，所有状态图标在右侧，最右侧是常驻滑动进度条
- 从右侧进度条往左依次是书签标识、当前阅读章节标识

## 3. 修改范围

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `app/.../reader/components/ReaderBottomFunctionOverlay.kt` | 修改 | 目录行重排：图标从标题左侧移至右侧 |
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
