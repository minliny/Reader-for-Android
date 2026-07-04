# Android Frontend Demo Alignment Spec

Status: `ANDROID_FRONTEND_DEMO_ALIGNMENT_BASELINE`

Date: 2026-07-03

Scope: 对齐 `Reader UI/frontend-demo/` 原型中的组件、文字、范围、大小、字体、样式、层级关系和相对位置，并映射到当前 Android Compose 组件。本文档是后续 Android 前端开发的验收基线，不代表 demo 的 HTML/CSS/DOM 可以直接复制到 Android。

## Source Of Truth

| 类型 | 原型来源 | Android 映射 |
|---|---|---|
| 路由与 shell | `Reader UI/frontend-demo/route-contract.js` | `app/src/main/kotlin/com/reader/ui/shell/ReaderUiState.kt` |
| 启动切片 | `Reader UI/docs/ui-handoff/FRONTEND_DEVELOPMENT_SLICE_MATRIX.md` | `AppShell`、`ReaderUiReducer`、各 route composable |
| 基础 token | `Reader UI/frontend-demo/tokens.css` | `ReaderTheme.kt` |
| 映射表 | `Reader UI/frontend-demo/route-contract.js` + 当前 Android `app/src/main/kotlin/com/reader/ui/**` | `docs/design/ANDROID_FRONTEND_DEMO_MAPPING_TABLE.md` |
| 开发计划 | 当前 Android mapping/spec 审计结果 | `docs/design/ANDROID_FRONTEND_DEVELOPMENT_PLAN.md` |
| shell/书架/搜索/RSS | `frontend-demo/styles/00-foundation.css`、`01-shell-layout.css` | `AppShell.kt`、`FloatingPillTabBar.kt`、`BookshelfScreen.kt` |
| 阅读正文与控制层 | `frontend-demo/styles/01-shell-layout.css`、`02-main-library.css`、`03-reader.css` | `ImmersiveReadingScreen.kt` + `ReaderControlScreen.kt`（top overlay / brightness rail / bottom sheet / full-page panel / utility panel / module nav） |
| 设置/书源 | `frontend-demo/styles/04-settings-source.css`、`05-flow-adaptive.css` | Settings main tab and source import partially rewritten; subpages deferred |

## Alignment Rules

- 单位：demo 中视觉尺寸按 `px -> dp` 在 mdpi 下 1:1 映射；字号按 `px -> sp` 1:1 映射。
- 原型边界：继承 token、结构、状态语义和 motion ID，不复制 CSS selector、DOM 层级或 web hit area 实现。
- 原生边界：状态、导航、back stack、safe area、键盘 inset、无障碍语义必须由 Android 原生实现。
- 组件验收顺序：先 Slice 1 AppShell/Main Tabs，再 Slice 2 Bookshelf to Immersive Reading，再 Slice 3 Reader Control Layer。
- 当前 Android 状态标签：
  - `Aligned`: 已有 Compose 组件承载，结构和主要尺寸一致。
  - `Partial`: 方向正确但缺子结构、状态或精确样式。
  - `Gap`: 当前仍是占位或 Material3 默认 UI。
  - `Deferred`: 原型已定义，但不属于当前启动切片。

## Global Frame And Tokens

| 项 | Demo 规格 | Android 要求 | 当前状态 |
|---|---:|---|---|
| Phone frame | 390 x 844 | 不固定设备尺寸，但 compact portrait 以此验收截图 | `Partial` |
| Safe area | top 24, bottom 14, horizontal 16 | 使用系统 inset；视觉间距按 token 对齐 | `Partial` |
| Top bar | min-height 58 | 主 tab 顶栏 58dp，含 status bar padding | `Aligned` |
| Main nav | min-height 68 | 四等分 floating pill bottom nav | `Aligned` |
| Reader sheet | min-height token 284, current quick sheet 330 | `ReaderControlScreen` bottom sheet 330；不能出现在 immersive entry final state | `Partial` |
| Font sans | system, SF Pro/PingFang/Microsoft YaHei | Android `FontFamily.Default`，中文 fallback 由系统承担 | `Aligned` |
| Font serif | Songti/STSong/Noto Serif CJK | Android `FontFamily.Serif`，后续可接 Noto Serif CJK | `Partial` |
| Paper | `#f8f4ec` body bg, `#fff8f4/#fff8f1` paper | `ReaderTheme.Paper/PaperBright` | `Aligned` |
| Surface | `rgba(255,252,248,.9)` | `#fffcf8` approximation plus alpha surfaces | `Aligned` |
| Ink | `#1f1b17`, reader ink `#2b241d` | `onBackground`, `readerInk` | `Aligned` |
| Primary | `#366179`, dark `#274f66`, accent `#f48b13` | `ReaderTheme` primary tokens | `Aligned` |
| Radius | 4/6/8/12/24/pill | `ReaderShapes.xs/sm/md/lg/xl/pill` | `Aligned` |
| Shadow | soft `0 8 26 rgba(...)`, elevated `0 18 46 rgba(...)` | Compose elevation approximates; not pixel-perfect | `Partial` |
| Z order | content 0, overlay 10, nav 20, sheet 30, module nav 40, dialog 60, keyboard 70 | Use route/overlay hosts and `Box` layering | `Partial` |

## Component Inventory

| 组件 | Demo 结构范围 | Android 目标 | 当前状态 |
|---|---|---|---|
| AppShell/MainTabShell | full-screen active route + floating main nav | `AppShell` + single reducer state | `Aligned` for Slice 1 |
| Main tab nav | 书架/发现/RSS/设置, 4 equal items | `FloatingPillTabBar` | `Aligned` |
| Bookshelf top bar | route title + search/more actions | `BookshelfTopBar` | `Partial`, motion timing evidence missing |
| Continue reading card | cover button + text block + action button | `ContinueReadingCard` | `Aligned` main geometry |
| Bookshelf section head | `我的书架` + cover/list/filter/settings buttons | `BookshelfSectionHeader` | `Partial`, display settings screen implemented as pushed prototype |
| Book grid/card | 3 columns cover grid, optional list view | `BookGrid` / `BookCard` | `Partial`, cover/list state wired; cover asset missing |
| Book focus menu | backdrop + focused cover + 4 actions | missing | `Deferred` Slice 4 overlay |
| Bookshelf empty | visual + heading/body + action rows + hints | `EmptyState` | `Partial`, visual evidence missing |
| Search | search entry, history/suggestion/results state | `SearchScreen` | `Partial`, custom Compose shell/search/results; visual proof and persisted history missing |
| Source import | source preview/import flow | `ImportBookSourceScreen` | `Partial`, custom preview/import shell; batch import behavior and device proof pending |
| Discover/RSS | main tabs with real cards/chips/lists | `DiscoverScreen` and `RssScreen` main tabs; RSS subroutes placeholder | `Partial` |
| Settings | settings shell rows/cards/chips/switches | `SettingsScreen` | `Partial`, main tab rows/cards/switches; bookshelf/search subpage prototype implemented; persistence deferred |
| Immersive reading | text-only reading layer, no controls | `ImmersiveReadingScreen` | `Aligned` for Slice 2 |
| Reader control layer | top info, bottom sheet, module nav, brightness rail | `ReaderControlScreen` (top overlay + brightness rail + bottom sheet + module nav + full/utility panels) | `Partial` |

## App Shell And Main Tabs

Structure:

- Root is a single native state machine: active tab, current route, back stack, reader context, reduced motion.
- Main tab route contains content surface plus bottom floating nav.
- `immersive-reading` route is full-screen and hides main nav.
- Search/import are pushed routes, not main tabs.

Text:

- Main tab labels are exactly `书架`, `发现`, `RSS`, `设置`.
- Search/reader/source routes must use route IDs from the demo contract: `book-search`, `immersive-reading`, `source-import-preview`.

Size and position:

- Bottom nav is aligned to bottom center, after navigation bar inset.
- Demo nav uses left/right `safe-area-horizontal - 2`, so compact portrait visual margin is 14.
- Nav min-height 68, internal padding 7 vertical and 8 horizontal.
- Four items keep equal width before/after active state changes.

Style:

- Nav background `rgba(255,252,248,.92)`, 1 hairline border, radius 24, soft elevated shadow.
- Active item fills the whole item with `#274f66`; inactive label/icon color `#6b625a`.
- Item structure is 30 icon shell, 3 gap, 18 label row. Label is 11sp, weight 800.

Relationship:

- Tab switch mutates active tab only; it must not push a route.
- Back from pushed routes returns to the previous route or active tab.
- Reduced motion collapses movement to instant state/color changes.

Current Android:

- `AppShell`, `ReaderUiReducer`, `FloatingPillTabBar` match the Slice 1 structure.
- Discover, RSS, Settings, and the bookshelf management subroutes now have partial native implementations. Remaining work is visual/device proof, real repositories/persistence, overlay/focus discipline, and deferred reader control surfaces.

## Bookshelf Top Bar

Structure:

- Demo route `bookshelf` renders `MainTabShell` with title `书架` and actions `search`, `more`.
- Android now renders title `书架` and actions `search`, `more`.

Text:

- Target title for the bookshelf route should be `书架`.
- Search action is icon-only with content description `搜索`.
- More action opens a bookshelf menu containing `批量管理`, `分组管理`, `本地书导入`.

Size and position:

- Top bar min-height 58.
- Content horizontal padding 20.
- Top extra padding 6 after status bar inset.
- Action touch boxes are 44 x 44.
- Action gap is 18.

Style:

- Title uses serif 29sp, weight 700.
- Action icons use current ink color, no visible circular fill in default state.

Current Android gap:

- The top action geometry and icon set are aligned.
- `more` opens `BookshelfMoreLayer`; its routes include native batch management, group management, local import, and reachable source import prototypes.
- Motion adapter evidence for `dropdown.trigger.press` / `dropdown.menu.expand/collapse` is still missing.

## Bookshelf Content Layout

Structure:

1. Continue reading card.
2. Bookshelf section container.
3. Section header `我的书架`.
4. Optional sort/filter popover.
5. Book grid or list view.
6. Overlay hosts for book focus and bookshelf more menu.

Range:

- The content is below the top bar and above the floating nav.
- Grid bottom padding must reserve nav overlap; current Android uses 112.
- Demo uses `fd-phone-content` gap and card stack inside the tab shell.

Current Android gap:

- Current `BookshelfScreen` mirrors the demo structure: top bar, `.fd-phone-content`, continue card, shelf section, section header, filter popover, grid/empty, and more layer.
- Cover/list/filter/more state is owned by `BookshelfChromeState` in `BookshelfViewModel`.
- Display settings, batch management, group management, and local import are pushed prototypes; book focus remains deferred to overlay/focus work. Source import is reachable with custom preview UI, while batch import behavior is still deferred.

## Continue Reading Card

Structure:

- Three columns: cover button, text block, action button.
- Cover click enters reader via `reader.entry.coverToImmersive`.
- Action button enters reader via `reader.entry.actionToImmersive`.

Text:

- Label: `继续阅读`, sans 13sp, weight 900, primary color.
- Title: book title, serif 20sp, line-height 1.2, max 2 lines.
- Author: sans 14sp, line-height 1.2, max 2 lines.
- Action: demo text `阅读`; Android currently displays `继续阅读`.

Size and position:

- Grid columns: 62, flexible, 82.
- Min-height 100.
- Padding 10 vertical and 16 horizontal.
- Column gap 14.
- Cover: width 62, aspect 4:5, radius 6.
- Action button: min 74 x 40, horizontal padding 14, pill shape.

Style:

- Card border is 1 hairline, radius 8, surface background, soft shadow.
- Primary action background is `#366179`, text white, 13sp weight 800.

Current Android:

- Geometry, callback split, and action text `阅读` are aligned.

## Bookshelf Section Header

Structure:

- Left: heading `我的书架`.
- Right: four icon-only buttons: cover view, list view, filter, display settings.
- Filter button owns `aria-expanded` equivalent state and opens the popover.

Text:

- Heading 15sp, line-height 1.2, weight 900.
- Buttons are icon-only; accessible labels carry the text.

Size and position:

- Header min-height 38.
- Horizontal arrangement: space-between, gap 12.
- Button boxes 34 x 34, circular.
- Right-side button gap 8.

Style:

- Inactive icon color `#6f6962`.
- Active icon color primary `#366179`.
- No filled background by default.

Current Android:

- `BookshelfSectionHeader` is present with 38dp min-height and 34dp action boxes.
- Cover/list buttons are wired to `BookshelfChromeState.viewMode`.
- Filter button opens `BookshelfFilterPopover` from `BookshelfFilterState`.
- Display settings routes to `BookshelfSearchSettingsScreen`, a pushed prototype with local segment/switch/select state.

## Book Grid And Book Card

Structure:

- Cover view: 3-column grid.
- List view: 1-column list, 48dp cover column, title and meta text.
- Book card contains cover button, title, author.
- Cover click route is `immersive-reading`.

Text:

- Title: serif 15sp, line-height 1.22, max 2 lines in cover grid.
- Author: sans 12sp, line-height 1.25, max 1 line.

Size and position:

- Cover grid columns: repeat 3.
- Grid gap: 16 vertical, 30 horizontal.
- Book card gap: 6.
- Cover aspect: 4:5, width fills grid cell.
- Cover radius: 8.
- List view row min-height: 66.
- List view cover width: 48, radius 6, row gap 2 x 10.

Style:

- Cover background `rgba(255,255,255,.58)`.
- Cover shadow demo is `0 10 18 rgba(52,38,26,.13)`.
- Focused cover outline is 3px primary at 0.82 alpha, offset 3, stronger shadow.

Current Android:

- Grid count, gaps, cover aspect and text styles are largely aligned.
- Actual cover images are not rendered yet; first-letter cover boxes are acceptable only for real/opt-in fixture data.
- List view and focused cover menu are missing.

## Bookshelf Empty State

Structure:

- Empty card centered in bookshelf section.
- Visual area, heading, body, primary/secondary action rows, optional hints.

Text:

- Heading: 19sp, line-height 1.25, weight 900.
- Body: 13sp, line-height 1.55, max width 280.
- Action title: 13sp, line-height 1.2, weight 900.
- Action meta: 10sp, line-height 1.2.
- Hint chip: 11sp, weight 800.

Size and position:

- Empty state min-height 350.
- Padding 28 vertical and 18 horizontal.
- Gap 14.
- Visual area 112 x 92.
- Center icon block 62 x 62.
- Action row min-height 58, grid 32 icon + text, gap 10.
- Hint chip min-height 30, icon 14, horizontal padding 10.

Style:

- Empty container uses surface, hairline border, radius 8, soft shadow.
- Visual icon block uses primary-tinted background and border.
- Primary empty action fills primary; secondary actions are primary-tinted cards.

Current Android:

- Container, visual area, heading/body typography are partially aligned.
- Action rows and hint chips are missing.
- Visual shape is simplified and should be updated only after structure is restored.

## Search Route

Structure:

- Back/top shell plus search entry.
- Idle state has history rows and suggestion chips.
- Results state has result rows with cover, text, state pill and action button.

Text:

- Search entry: 14sp.
- Section title: 16sp.
- History title: 13sp; meta 11sp; mark/action 12sp weight 800.
- Suggestion chip: 13sp weight 800.
- Result title: 14sp; meta 11sp.
- Result state pill: 10sp weight 800.
- Result action: 11sp weight 900.

Size and position:

- Search entry min-height 44, grid 24 icon + text, gap 10, horizontal padding 14, pill radius.
- Search state/results padding 16, vertical gaps 12 or 10.
- History row min-height 52, grid 20 + text + 40, padding 8 x 2.
- Suggestions are 2 columns, gap 8, chip min-height 38.
- Result row min-height 86, grid 46 cover + flexible + 58 state + 64 action, gap 8, padding 10, radius 12.
- Result cover 46 x 64, radius 4.

Style:

- Search entry background `rgba(255,255,255,.58)`, border hairline.
- Result rows use `rgba(255,252,248,.78)`, hairline border.
- Primary actions use primary fill.

Current Android:

- `SearchScreen` now uses a custom Compose back shell, pill search entry, idle history row, suggestion chips, state pages, and result rows instead of Material3 `Scaffold` / `OutlinedTextField` / `ListItem`.
- Result rows use 46 x 64 cover tiles, 58dp status pills, and 64dp primary actions, with row clicks still routed through `reader.entry.actionToImmersive`.
- Device proof captured for idle/search-state routes: `screenshots/02-book-search-device.png`, `screenshots/03-book-search-state-device.png`.
- Remaining gaps: persisted search history, exact "N 个书源可用" metadata, result-row screenshot with real imported-source results, and fine-grained search/filter motion IDs.

## Source Import And Settings Source Rows

Structure:

- Source import is a route/sheet flow from bookshelf more or source management, not a main tab.
- Demo settings/source rows use a dense row system with leading icon, title/meta, and trailing action/value/switch.

Text:

- Settings section title: 13sp weight 900.
- Row title: 13sp weight 800.
- Row meta: 10sp, up to 2 lines.
- Control/action text: 11sp.

Size and position:

- Settings row min-height 58.
- Row grid: 28 icon + flexible text + auto trailing.
- Row gap 10, horizontal padding 12.
- Source row default min-height 64; compact 52; mini 42.
- Search box height 40; chip height 34; action row height 42.
- Switch size 38 x 22, thumb 18, offset 16.

Style:

- Rows are grouped inside surface cards with hairline separators.
- Leading icon box is 28 x 28 circle with primary tint.
- Active chips use primary fill, inactive chips use muted warm fill.

Current Android:

- `ImportBookSourceScreen` now uses a custom pushed-route shell with back action, source-origin card, JSON input panel, source stats, conflict segment, group row, preview list, and bottom cancel/confirm actions.
- Single-source Legado JSON is parsed locally for preview and still imports through `SourceApi.importBookSource(json)`.
- Batch JSON arrays are previewed but the confirm action remains disabled because the current Android import bridge is still single-source.
- Device proof captured for route entry and empty preview: `screenshots/05-bookshelf-more-device.png`, `screenshots/06-source-import-preview-device.png`.
- Remaining gaps: parsed-preview screenshot with real pasted JSON, real success/error e2e import evidence, persisted conflict/group choices, and batch import behavior.

## Settings Main Tab

Structure:

- Main `settings` tab stays inside `MainTabShell`; settings subpages are pushed routes or deferred flows, not extra main tabs.
- Top bar title `设置`.
- Sections use 13sp heading, dense 58dp rows, 28dp leading icon boxes, flexible title/meta column, and trailing value/switch/action/chevron controls.
- Main rows mirror the demo settings home: 通用设置, 书架与搜索设置, 书源管理, 同步与备份, 关于与反馈.

Current Android:

- `SettingsScreen` implements the main settings tab with section rows, segmented theme choice, switch controls, metric cards, and permission/status badges.
- `BookBatchManagementScreen`, `GroupManagementScreen`, `LocalImportScreen`, and `BookshelfSearchSettingsScreen` replace the bookshelf management placeholders with custom Compose batch selection, group list/assignment, local import, and bookshelf/search settings prototypes.
- `SettingsGeneralScreen`, `AboutFeedbackScreen`, `SyncBackupScreen`, `WebDavConfigScreen`, and `SourceManagementScreen` replace the settings subpage placeholders with custom Compose prototypes.
- `减少动态效果` is wired to `ReaderUiIntent.SetReducedMotion`; `通用设置`, `书架与搜索设置`, `书源管理`, `同步与备份`, and `关于与反馈` route to native pushed prototypes.
- Device proof captured for the main tab: `screenshots/07-settings-main-device.png`.
- Device proof target for bookshelf management routes: `screenshots/48-book-batch-management-device.png`, `screenshots/49-group-management-device.png`, `screenshots/50-local-import-device.png`, `screenshots/51-bookshelf-search-settings-device.png`.
- Device proof target for settings subpages: `screenshots/52-settings-general-device.png`, `screenshots/53-about-feedback-device.png`, `screenshots/54-sync-backup-device.png`, `screenshots/55-webdav-config-device.png`, `screenshots/56-source-management-device.png`.
- Remaining gaps: settings persistence, real WebDAV test/save/restore behavior, permission intents, source detection/edit/log behavior, dropdown/dialog motion IDs, real file picker, group/book mutation persistence, and action-route device proof.

## Discover And RSS Tabs

Discover:

- Main tab route `discover` is part of the four-tab shell.
- The main tab uses the demo source bar, entry chips, filter row, ranking header, book list, and RSS entry.
- Book rows enter `immersive-reading` through `reader.entry.actionToImmersive` with a `fixture://discover` context until real source/detail data is wired.
- Do not turn Discover subroutes into main tabs.
- Full source switching, real feed/ranking data, refresh/loading/error states, filter menus, and source-management flows remain deferred.

RSS:

- Top bar grid is title + actions, gap 10.
- RSS top actions have 34dp pills, 6dp gap.
- Home content is subscription-source first: search entry, mode chips, source overview, filter chips, source rows, and recent unread article rows.
- Source overview rows use 32 icon + flexible text + unread count + status badge, min-height 62, gap 8, padding 9 x 10.
- Mode chips min-height 30, horizontal padding 10, 12sp weight 850.
- Source strip item width 138, min-height 58, grid 26 icon + text, padding 8.
- `rss-search`, `rss-subscription-management`, `rss-detail`, and `rss-original` are pushed routes, not main tabs.
- `rss-search` has its own search panel with scope chips and result rows; it must not reuse book search semantics or route IDs.
- `rss-subscription-management` is a source-management surface: action grid, status filters, source rows, batch row, and refresh/reminder settings.
- `rss-source-edit` is the source-edit surface: group tabs, source-rule field rows, top debug action, and bottom debug/save actions.
- `rss-source-debug` is the rule-debug surface: source header, categorized result rows, warning highlight, and bottom edit/done actions.
- `rss-source-import` is the source-import surface: URL panel, import option chips, preview entries, detail drill-in, and result confirmation.
- `rss-rule-subscription` is the rule-subscription surface: mode row, subscription list, open/create actions, detail change preview, edit fields, test results, and apply-update confirmation.
- `rss-source-groups` is the RSS group-management surface: group rows, enabled switches, create/rename actions, and group-edit field rows.
- `rss-source-actions` is the source operation surface: source card, 4-column action grid, source-variable list, source-login flow, WebView login preview, cookie extraction, pin/disable confirmations, read-record list, and record-clear confirmation.
- `rss-source-batch` and `rss-source-export` are source batch/export surfaces: selected-source summary, batch source rows, export option panel, export preview rows, export-detail preview, export-result confirmation, and batch-disable confirmation.
- `rss-detail` has its own reading surface: source card, title/summary card, inline read/star/source actions, body card, original-link card, and bottom actions.
- `rss-original` is the original-link preview surface: original URL header, WebView preview card, return-to-detail action, and system-browser confirmation route.

Current Android:

- `DiscoverScreen` replaces the previous Discover placeholder with a custom Compose top bar, source row, horizontal entry chips, filter controls, demo ranking rows, and RSS handoff row.
- Discover source row currently routes to the existing source import flow; RSS row switches to the RSS tab.
- Device proof captured for the main Discover tab and Discover-to-reader entry: `screenshots/08-discover-main-device.png`, `screenshots/09-discover-reader-entry-device.png`.
- `RssScreen` replaces the previous RSS placeholder with a custom Compose top bar, refresh/manage pills, search entry, mode chips, local source filter chips, source overview list, source strip, rule-subscription list, and article rows.
- `RssDetailScreen` replaces the previous RSS reading placeholder with a custom Compose source card, title/summary, read/star/source actions, article body, original-link card, and bottom actions.
- `RssSearchScreen` replaces the previous RSS search placeholder with a custom Compose search panel, scope chips, result section, source-management action, and article rows.
- `RssSubscriptionManagementScreen` replaces the previous subscription-management placeholder with a custom Compose action grid, management filters, source rows, batch row, and refresh/reminder switches.
- `RssSourceEditScreen` replaces the previous source-edit placeholder with custom Compose group tabs, source-rule field rows, top debug action, and bottom debug/save actions.
- `RssSourceDebugScreen` replaces the previous rule-debug placeholder with a custom Compose debug panel, parsed result rows, warning highlight, and edit/done actions.
- `RssSourceImportScreen`, `RssSourceImportDetailScreen`, and `RssSourceImportResultScreen` replace the previous import placeholder with custom Compose URL/options, preview list, import detail, and result confirmation surfaces.
- `RssRuleSubscriptionScreen`, `RssRuleSubscriptionDetailScreen`, `RssRuleSubscriptionEditScreen`, `RssRuleSubscriptionTestScreen`, and `RssRuleSubscriptionApplyScreen` replace the previous rule-subscription placeholder with custom Compose subscription list, detail, edit, test, and apply-confirmation surfaces.
- `RssSourceGroupsScreen` and `RssSourceGroupEditScreen` replace the previous RSS groups placeholder with custom Compose group rows, enable switches, create/rename actions, and edit fields.
- `RssSourceActionsScreen`, `RssRefreshingScreen`, `RssSourceVarsScreen`, `RssSourceLoginScreen`, `RssSourceLoginWebScreen`, `RssSourceLoginCookieScreen`, `RssSourceConfirmScreen`, and `RssReadRecordScreen` replace the source-action placeholder with custom Compose source operation, refresh-result, variable, login, WebView-preview, cookie, confirmation, and read-record surfaces.
- `RssSourceBatchScreen`, `RssSourceExportScreen`, and `RssSourceExportDetailScreen` replace the batch/export placeholders with custom Compose batch summary, source selection rows, export option panel, export preview list, export detail, export result confirmation, and batch-disable confirmation.
- `RssOriginalScreen` replaces the original placeholder with custom Compose original-link header, webpage preview, return-to-detail action, and browser-open confirmation route.
- `rss-refreshing` is only a lightweight pushed refresh-result page, not the full RSS main-state refresh. Real OPML/import/export execution, subscription sync, source CRUD, persisted filters, group membership persistence, search backend/history, article repository integration, refresh/error/empty states, export artifact/share, executable source debug/login, in-app WebView, copy/share, and real browser intents remain deferred.
- Device proof captured for the main RSS tab and subscription-management route entry: `screenshots/10-rss-main-device.png`, `screenshots/11-rss-management-route-device.png`.
- Device proof captured for RSS article detail: `screenshots/12-rss-detail-device.png`. The original-link route changed after the earlier placeholder screenshot and needs refreshed device proof below.
- Device proof captured for RSS search and search-result-to-detail routing: `screenshots/14-rss-search-device.png`, `screenshots/15-rss-search-result-detail-device.png`.
- Device proof captured for RSS subscription management and source-edit route entry: `screenshots/16-rss-subscription-management-device.png`, `screenshots/17-rss-source-edit-route-device.png`.
- Device proof captured for RSS source edit and source debug: `screenshots/18-rss-source-edit-device.png`, `screenshots/19-rss-source-debug-device.png`.
- Device proof captured for RSS source import, import detail, and import result: `screenshots/20-rss-source-import-device.png`, `screenshots/21-rss-source-import-detail-device.png`, `screenshots/22-rss-source-import-result-device.png`.
- Device proof target for RSS rule subscription, detail, edit, test, and apply confirmation: `screenshots/23-rss-rule-subscription-device.png`, `screenshots/24-rss-rule-subscription-detail-device.png`, `screenshots/25-rss-rule-subscription-edit-device.png`, `screenshots/26-rss-rule-subscription-test-device.png`, `screenshots/27-rss-rule-subscription-apply-device.png`.
- Device proof target for RSS source groups and group edit: `screenshots/28-rss-source-groups-device.png`, `screenshots/29-rss-source-group-edit-device.png`.
- Device proof target for RSS source actions, refreshing, variables, login, login WebView preview, cookie extraction, login clear, pin, disable, read-record, and record-clear: `screenshots/30-rss-source-actions-device.png`, `screenshots/31-rss-refreshing-device.png`, `screenshots/32-rss-source-vars-device.png`, `screenshots/33-rss-source-login-device.png`, `screenshots/34-rss-source-login-web-device.png`, `screenshots/35-rss-source-login-cookie-device.png`, `screenshots/36-rss-source-login-clear-device.png`, `screenshots/37-rss-source-pin-device.png`, `screenshots/38-rss-source-disable-device.png`, `screenshots/39-rss-read-record-device.png`, `screenshots/40-rss-record-clear-device.png`.
- Device proof target for RSS source batch/export, export detail, export result, and batch-disable confirmation: `screenshots/41-rss-source-batch-device.png`, `screenshots/42-rss-source-export-device.png`, `screenshots/43-rss-source-export-detail-device.png`, `screenshots/44-rss-source-export-result-device.png`, `screenshots/45-rss-source-batch-disable-device.png`.
- Device proof target for RSS original preview and browser confirmation: `screenshots/46-rss-original-device.png`, `screenshots/47-rss-original-browser-device.png`.

## Immersive Reading

Structure:

- Full-screen route `immersive-reading`.
- Text-only layer: no top bar, no tab bar, no reader control sheet on entry.
- Loading/error states can use native minimal surfaces, but ready state must preserve reading geometry.

Text:

- Chapter title: serif 23sp, line-height 1.25, centered.
- Body: serif 18sp, line-height 1.96.
- Reader ink: `#2b241d`.

Size and position:

- Reading layer top inset 72.
- Horizontal inset 32.
- Bottom inset 48.
- Title bottom gap 24.

Style:

- Background follows paper/reader surface.
- Letter spacing is 0.
- Body should not be compressed by control overlays during immersive state.

Current Android:

- `ImmersiveReadingScreen` aligns to Slice 2 final state.
- Control overlay lives in a separate `ReaderControlScreen` route, reached by middle tap; immersive entry stays text-only.

## Reader Control Layer

Structure:

- Appears only after middle tap in reader, not automatically after entry.
- Overlay preserves mounted reading surface.
- Contains top reader info, bottom sheet, right brightness rail, and module navigation.

Size and position:

- Bottom sheet left/right 12, bottom 18, height 330 in current quick layout.
- Sheet radius 24, hairline border, elevated shadow.
- Grabber width 42, height 4, top 9.
- Main control area left 12, right `12 + rail 38 + gap 14`, top 28, bottom 110.
- Control main rows: actions and chapter panel, gap 6.
- Actions grid: 3 columns, gap 8, padding 9 x 8.
- Chapter step buttons: 34 x 34.
- Progress touch area height 30, visible rail height 5, thumb 12 with 3 border.
- Brightness rail width 38, right 12, vertical padding 12, internal bar 8 x 92.

Style:

- Control sheet background `rgba(255,250,244,.98)`.
- Internal panels use `rgba(255,252,248,.62)`.
- Module panel uses sans font and small dense controls.

Current Android:

- Implemented in `ReaderControlScreen`: top overlay (title + close), bottom sheet (grabber + actions grid + chapter panel + progress rail), right brightness rail with adjustable `--reader-brightness-dim` (0..0.32), full-page panels (directory/tts/appearance/settings), utility panels (cache/debug), and `readerModuleNav` slot. Reachable only via middle tap from immersive route; not auto-opened on entry.

## Overlay And Relative Layering

Layer order:

1. Reading/content surface.
2. Non-modal overlay/state host.
3. Main nav.
4. Bottom sheet.
5. Reader module nav.
6. Dialog/focus menu.
7. Keyboard.

Rules:

- Hidden overlays must not keep hit areas.
- Back closes the topmost overlay before route pop.
- Focus returns to trigger or the newest valid target.
- Keyboard uses native inset, not a fake fixed-height blocker.

Current Android:

- Route back stack is implemented.
- Overlay focus/menu/sheet discipline is not implemented yet.

## Current Gap Order

1. Device visual evidence: refresh compact portrait checks for bookshelf, management routes, RSS routes 23-47, bookshelf routes 48-51, and settings routes 52-56 when ADB is online.
2. Real data/persistence: connect bookshelf batch/group/local-import/settings prototypes to repositories, file picker, settings store, and actual mutation results.
3. Book grid: render real cover images and complete cover/list item press/focus behavior.
4. Source import: complete batch JSON import behavior and success/error proof.
5. Settings runtime behavior: wire persistence, WebDAV test/save/restore, permission intents, source detection/edit/log, and dialog/sheet overlays.
6. Reader control layer: structure implemented in `ReaderControlScreen`; remaining work is visual evidence (compact portrait screenshots), motion timing adapters, and full/utility panel content depth.

## Component Development Checklist

For each component implementation:

1. Name the demo route and CSS/source function used as prototype.
2. Declare the native component owner and state owner.
3. Map dimensions to `dp` and text to `sp`.
4. Use `ReaderTheme` tokens first; add tokens only when the demo has a named value or repeated pattern.
5. Preserve route and motion IDs.
6. Add a reducer or component test when navigation/state semantics change.
7. Capture at least one compact portrait screenshot for visual review.
8. Run `./gradlew testDebugUnitTest` or a narrower relevant proof plus `git diff --check`.
