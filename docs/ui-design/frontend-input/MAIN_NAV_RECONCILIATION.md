# 主导航差异收敛清单（Main Navigation Reconciliation）

本文把本地设计输入件和当前 Android Compose 代码之间的主导航差异明确成实施清单。本文不改产品逻辑，只约束后续真实前端实现时必须先统一的结构。

## 目标结构（Target Structure）

本地设计输入件固定主导航四项：

| 顺序（Order） | 标签（Label） | Type | Shell | 入口说明（Entry Notes） |
| ---: | --- | --- | --- | --- |
| 1 | 书架 | `bookshelf` | `MainTabShell` | 书架根页、继续阅读、我的书架。 |
| 2 | 发现 | `discover` | `MainTabShell` | 发现内容、书源内容聚合、榜单和推荐。 |
| 3 | RSS | `rss` | `MainTabShell` | RSS 订阅流、订阅源筛选和订阅管理入口。 |
| 4 | 设置 | `settings` | `MainTabShell` | 设置首页、书源管理、同步备份、隐私权限和关于反馈入口。 |

证据文件：

- `docs/ui-design/frontend-input/contracts.d.ts`：`MainNavType = "bookshelf" | "discover" | "rss" | "settings"`。
- `docs/ui-design/02-主标签页/*/frontend-input/fixture.json`：四个根页的 `bottomNav[]` 均使用 `书架 / 发现 / RSS / 设置`。
- `docs/ui-design/frontend-input/component-library/COMPONENT_LIBRARY.md`：`MainNav` / `MainNavItem` 固定四项。

## 当前 Android 差异（Current Android Differences）

| 文件（File） | 当前结构（Current Structure） | 差异（Difference） | 后续动作（Next Action） |
| --- | --- | --- | --- |
| `app/src/main/kotlin/com/reader/android/ui/AppNavigation.kt` | `AppScreen.Bookshelf / Discover / Sources / Mine` | 第 3、4 项是 `书源 / 我的`，不是 `RSS / 设置`。 | 将主 tab 定义收敛为 `Bookshelf / Discover / Rss / Settings`。 |
| `app/src/main/kotlin/com/reader/android/ui/ReaderRouteHost.kt` | `mainTabRoutes = appScreens.map { it.route }`，底栏调用 `StitchBottomNav` | 底栏跟随旧 `appScreens`，且 `StitchBottomNav` 内部又硬编码旧标签。 | 改成使用统一 `MainTabShell` 数据源；底栏不得有第二套硬编码列表。 |
| `app/src/main/kotlin/com/reader/android/ui/stitch/StitchAppShell.kt` | `StitchBottomNav` 硬编码 `书架 / 发现 / 书源 / 我的` | 与 `contracts.d.ts` 和 fixtures 冲突。 | 作为过渡原型隔离；真实实现中删除或改为读取 `appScreens`。 |
| `app/src/main/kotlin/com/reader/android/ui/booksource/*` | 书源管理作为主入口 `sources` 存在 | 本地输入件把书源管理放在设置链路。 | 保留功能，但入口挂到设置首页的 `书源管理`。 |
| `app/src/main/kotlin/com/reader/android/ui/settings/MineScreen.kt` | `我的` 主入口承载设置、备份、关于等 | 本地输入件没有 `我的` 主 tab。 | 将 `MineScreen` 内容拆入设置首页或设置二级入口。 |
| `app/src/main/kotlin/com/reader/android/ui/discover/RssScreens.kt` | RSS 是发现链路下的子路由 | 本地输入件要求 RSS 独立主 tab。 | 建立 RSS 根路由，保留订阅详情和订阅管理作为 RSS 子路由。 |

## 建议路由归并（Route Reconciliation）

| 当前路由（Current Route） | 目标归属（Target Owner） | 处理方式（Handling） |
| --- | --- | --- |
| `ReaderRoutes.BOOKSHELF` | 书架（Bookshelf） | 保留为第 1 个主 tab。 |
| `ReaderRoutes.DISCOVER` | 发现（Discover） | 保留为第 2 个主 tab。 |
| `ReaderRoutes.SOURCES` | 设置 > 书源管理（Settings > Source Management） | 不再作为主 tab，功能进入设置链路。 |
| `ReaderRoutes.MINE` | 设置（Settings） | 不再作为主 tab，内容拆入设置首页。 |
| `ReaderRoutes.RSS_LIST` | RSS（RSS） | 提升为第 3 个主 tab 或新增 `ReaderRoutes.RSS` 根页包装。 |
| `ReaderRoutes.GLOBAL_SETTINGS` | 设置（Settings） | 可作为第 4 个主 tab 的真实内容，或由设置首页进入二级通用设置。 |
| `ReaderRoutes.SOURCE_DETAIL / SOURCE_EDIT / SOURCE_IMPORT` | 设置 > 书源管理（Settings > Source Management） | 保留二级栈，不出现在主导航。 |
| `ReaderRoutes.WEBDAV_CONFIG / BACKUP_SETTINGS / ABOUT` | 设置链路（Settings Flow） | 保留二级栈，从设置首页进入。 |

## 实施顺序（Implementation Order）

1. 改 `AppScreen`（AppScreen Update）：主 tab 改为 `Bookshelf / Discover / Rss / Settings`，图标 token 对应 `bookshelf / discover / rss / settings`。
2. 改路由分组（Route Group Update）：`AppRouteGroups` 把 RSS 子路由归入 `rss`，把书源、备份、关于、隐私等归入 `settings`。
3. 改底栏数据源（Bottom Nav Source）：`ReaderRouteHost` 的底栏只读取 `appScreens` 或统一 MainTab model；不得继续让 `StitchBottomNav` 内部硬编码标签。
4. 建设置首页（Settings Root）：把当前 `MineScreen` 中的备份、WebDAV、关于等入口与本地 `02-主标签页/设置` fixture 对齐。
5. 建 RSS 根页（RSS Root）：把 `RssListScreen`、订阅管理和详情入口放入 RSS 主 tab 下。
6. 回归验证（Regression）：确认四个主 tab 的顺序、选中态和路由栈稳定；二级页返回不改变主 tab 顺序。

## 验收标准（Acceptance Criteria）

- 主导航顺序必须是 `书架 / 发现 / RSS / 设置`。
- 主导航 active 状态只改变背景、图标颜色和文字颜色，不改变按钮尺寸、间距和位置。
- Android 代码中不得同时存在两套主导航标签来源。
- 书源管理不再作为主 tab，但必须能从设置首页进入。
- RSS 不再只是发现页里的入口，必须有独立主 tab 状态。
- 状态页、二级页和阅读页不得显示错误的主导航高亮。

## 非目标（Non-goals）

- 本文不决定真实产品是否保留 `我的` 概念；若保留，只能作为设置页内分组，不作为主 tab。
- 本文不改后端、同步、书源或 RSS 业务逻辑。
- 本文不要求立即删除 `ui/stitch/*`，但真实前端实现不得继续以其硬编码底栏为准。
