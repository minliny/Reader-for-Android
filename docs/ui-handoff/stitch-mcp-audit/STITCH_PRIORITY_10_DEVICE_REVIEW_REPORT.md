# Stitch Priority 10 Device Review Report

## 1. 总体结论

STITCH_PRIORITY_10_DEVICE_REVIEW_NOT_READY

## 2. 当前状态

| 项目 | 值 |
|------|-----|
| HEAD | `13f6b15` |
| git status | 干净 |
| 是否 push | 否 |
| installDebug | 未执行（设备未连接） |
| 测试 | BUILD SUCCESSFUL |
| assembleDebug | BUILD SUCCESSFUL |
| lintDebug | BUILD SUCCESSFUL |

## 3. P0 发现：App Shell 未接入

**Root cause**: `ReaderRouteHost.kt:348` 默认渲染 `ReaderPrototypeGallery()`。

`StitchAppShell()` 已实现（`ui/stitch/StitchAppShell.kt`），包含 `StitchBottomNav`（书架/发现/书源/我的），但**未被 `MainActivity` → `ReaderAndroidApp` → `AppNavigation` → `ReaderRouteHost` 链路调用**。

**影响**：
- App 启动仍进入 Prototype Gallery
- Priority 10 页面全部不可从正式 App 入口触达
- 只能在 Prototype Gallery → Reader 分组看到阅读页相关 overlay

**严重程度**: P0 — 阻塞设备端人工复核。

## 4. 代码存在但未接入的页面

| Screen | 实现文件 | 入口状态 |
|--------|---------|---------|
| App Shell (StitchBottomNav) | `StitchAppShell.kt` | ❌ 未接入 MainActivity |
| 发现 - 首页 | `StitchAppShell.kt` placeholder | ❌ |
| 书源管理 - 列表 | `StitchAppShell.kt` placeholder | ❌ |
| 我的 - 首页 | `StitchAppShell.kt` placeholder | ❌ |
| 搜索 - 首页 | `StitchScreens.kt` | ❌ 无 route |
| 搜索结果 | `StitchScreens.kt` | ❌ 无 route |
| 书籍详情 | `StitchScreens.kt` | ❌ 无 route |
| 内容替换 Overlay | `StitchScreens.kt` | ❌ 无 route |
| 全局 Error | `StitchComponents.kt` | ❌ 无调用 |

## 5. 可触达页面

仅阅读页控制层通过 Prototype Gallery → Reader 分组可触达（已使用 Stitch 颜色的 `ReaderNativeComponents`）。

## 6. Material3 风格残留

| 位置 | 问题 | 严重程度 |
|------|------|---------|
| App Shell | Prototype Gallery 仍是 Material3 默认风格 | P0 |
| 底栏 | 未应用 StitchBottomNav，使用旧 NavigationBar | P0 |

## 7. 发现问题

| # | 页面 | 问题 | 严重程度 | 阻塞 |
|---|------|------|---------|------|
| P0-1 | App Shell | StitchAppShell 未接入默认入口，App 启动仍是 Prototype Gallery | P0 | 是 |
| P0-2 | 搜索/详情/Error | StitchScreens 存在但无 route 接入，无法从 App 触达 | P0 | 是 |

## 8. P0

| # | 描述 |
|---|------|
| P0-1 | `MainActivity` → `ReaderRouteHost` 默认路由仍是 `ReaderPrototypeGallery()`，`StitchAppShell` 未接入 |
| P0-2 | 搜索/详情/Error Stitch 页面无 route，不可触达 |

## 9. P1

无。

## 10. 是否允许进入下一步

不允许。需先修复 P0-1（接入 StitchAppShell 为默认入口）和 P0-2（添加 route）。修复后可继续设备复核。

## 11. 建议修复

1. 在 `ReaderRouteHost` 中将默认路由从 `ReaderPrototypeGallery()` 改为 `StitchAppShell()`
2. 为搜索/详情/Error 添加 route 入口
3. Prototype Gallery 保留为 debug/dev 二级入口
