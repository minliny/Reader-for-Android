# Reader 公共组件库

## 边界

本组件库先完成“可作为所有 UI 设计图前端输入件的公共约束”。它不是最终 React/Vue/Compose 代码库，但必须能约束后续页面转换的结构、命名、状态和验收。

## Foundations

- `tokens.css`：全局颜色、字体、圆角、阴影、focus。
- `rl-` 前缀：公共组件库命名空间。
- 页面专属前缀：允许继续使用 `bs-`、`rc-` 等，但不得重复定义公共控件语义。

## App Shell

- `IconButton`：搜索、更多、返回、排序、设置。
- `AppFrame`：手机画布、页面背景和安全区边界。
- `StatusBar`：时间、电量、系统图标。
- `AppTopBar`：标题、搜索、更多或返回。
- `SearchBar`：书籍搜索、内容搜索、书源搜索。
- `MainNav` / `MainNavItem`：公共主导航四项，固定为 `书架 / 发现 / RSS / 设置`。

当前缺口：
- 横向页面 `换源` 需要独立 landscape frame。

## Basic Controls

- `Chip`：分组、筛选、状态切换。
- `ProgressBar`：书籍进度、阅读百分比。
- `ProgressSlider`：章节进度。
- `QuickAction`：阅读控制层快捷入口。
- `Badge`：设置行状态提示，如 `可用`、`待授权`、`未设置`。
- `Switch`：二级设置页开关，设置首页仅可作为状态预览，不直接执行破坏性操作。
- `PrimaryActionButton`：空态、导入、重试的主行动作。
- `SecondaryActionButton`：导入、管理、切换等次级行动作。
- `TextActionButton`：低权重跳转，如换个分组。

当前缺口：
- `Checkbox`、`SegmentedControl`、`Stepper`、`TextField` 尚未完成。
- disabled、pressed、focused 状态还没有完整状态矩阵。

## Cards & Rows

- `BookCover`
- `BookCard`
- `BookRow`
- `ListRow`
- `SettingRow`

当前缺口：
- `SourceListItem`、`RSSSourceItem`、`SearchResultItem`、`NoteCard` 尚未完成。
- 行内 badge、开关、危险操作、二级说明的规则还未完整。

## Sheets & Panels

- `BottomSheet`
- `ReaderModuleNav`：阅读控制层四模块入口，active 时只允许背景加深、中心图标反转和文字变色，不允许改变按钮相对位置。
- `BrightnessSlider`
- `Panel`

当前缺口：
- `SortFilterSheet`、`BookActionSheet`、`TocPanel`、`AppearancePanel`、`TTSPanel`、`SourceSwitchPanel` 只是可由现有组件派生，尚未单独成规格。

## States

- `LoadingState`
- `EmptyState`
- `ErrorState`
- `PermissionState`
- `EmptyStateCard`：插画、标题、原因说明、主操作、可选次操作。

当前缺口：
- Offline、unknown、partial-loading、sync-conflict 还未规格化。

## 复用判断

可以直接作为其他页面框架组件的部分：
- 顶部图标按钮。
- 公共主导航。
- Chip 分组。
- 图书封面和图书卡。
- 进度条和进度滑杆。
- 阅读底表外壳。
- 快捷操作。
- 模块导航。
- 亮度滑杆。
- 加载、空、错误、权限状态块。

不能直接覆盖的页面：
- RSS、发现、设置链路的复杂列表页。
- 书籍详情、书籍目录、书籍搜索。
- 排序筛选底表、书籍操作底表。
- 阅读外观、朗读、目录与书签、换源完整页。

## 准入规则

后续每转换一个页面：
1. 先列出页面使用到的公共组件。
2. 缺组件时先扩展公共库，再写页面。
3. 页面完成后必须加入 `manifest.json`。
4. 页面必须提供 `fixture.json`、`fixture.js`、`render.js`、`preview.html`、`state-matrix.html` 或说明为什么不需要状态矩阵。
5. 运行 `validate-frontend-inputs.js` 通过后，才算转化完成。
