# 素材库清单（Asset Library Inventory）

## 当前纳入内容（Included Assets）

| 中文名称（English Name） | 数量（Count） | 说明（Notes） |
|---|---:|---|
| UI 设计图（UI Design Screens） | 29 | 当前所有 `UI设计图.png` 已按页面组进入 `fixture.json`。 |
| 书籍封面素材（Book Cover Assets） | 6 | 当前书架封面图已进入素材库。 |
| fixture 图标 token（Fixture Icon Tokens） | 54 | 从当前所有页面 `fixture.json` 扫描得出。 |
| 补齐图标（Supplemented Icons） | 27 | 补齐 Shell、阅读模块、状态、Compose 语义追溯和缺少公共登记的图标 token。 |
| 本地总图标 token（Total Local Icon Tokens） | 81 | 以 `icons.js` 中 `ReaderAssetIcons.icons` 为准；Figma 图标主组件已用 `lucide-static@1.21.0` 开源 SVG 补齐为 `Icon/*`。 |
| 验证截图（Validation Screenshots） | 64 | 作为验证素材集合登记，不在预览页逐张加载。 |

## 补齐图标（Supplemented Icons）

- 返回（Back）：`back`
- 更多（More）：`more`
- 右箭头（Chevron）：`chevron`
- 左箭头（Chevron Left）：`chevron-left`
- 关闭（Close）：`close`
- 目录（Directory）：`directory`
- 朗读（TTS）：`tts`
- 自动翻页（Auto Page）：`auto-page`
- 替换（Replace）：`replace`
- 暂停（Pause）：`pause`
- 停止（Stop）：`stop`
- 打开书籍（Book Open）：`book-open`
- 来源栈（Source Stack）：`source-stack`
- 进度（Progress）：`progress`
- 音量（Volume）：`volume`
- 齿轮（Gear）：`gear`
- 清除（Clear）：`clear`
- 离线（Offline）：`offline`
- 闪光 / 智能提示（Sparkle）：`sparkle`
- 文件夹不可用（Folder Off）：`folder-off`
- 夜间模式（Night Mode）：`night-mode`
- 当前位置（Current Location）：`current-location`
- 权限（Permission）：`permission`
- 代码（Code）：`code`
- 帮助（Help）：`help`
- 信号（Signal）：`signal`
- Wi-Fi（Wi-Fi）：`wifi`

## 准入规则（Acceptance Rules）

- 新增图标必须先进入 `icons.js`。
- 新增 UI 源图必须先进入 `fixture.json` 和 `fixture.js`。
- 页面 renderer 不能再临时新增同义图标 token。
- 如果页面必须使用局部图标，必须在素材库中标注为页面专属。
- `preview.html` 必须通过 manifest 验证。
- `FrontendInputAssetLibraryInventoryTest` 必须通过，确保 `fixture.json`、`fixture.js`、`icons.js`、实际文件、manifest 和验证报告同步。

## 裁切与占位规则（Crop and Placeholder Rules）

| 素材类型（Asset Type） | 裁切规则（Crop Rule） | 占位规则（Placeholder Rule） |
|---|---|---|
| 书架封面（Bookshelf Cover） | 默认 2:3；优先 contain 或 center crop，不能裁掉标题主体。 | 使用语义占位封面，显示书名首字或 `book-open` 图标。 |
| 详情封面（Detail Cover） | 可放大但仍保持原始比例，不为了填满 Hero 拉伸。 | 使用同一封面占位策略，保留书名和作者文本。 |
| 搜索 / 列表小封面（List Cover） | 小尺寸优先 contain，允许留背景，不裁关键内容。 | 使用小封面占位，不影响列表行高度。 |
| 状态插图（State Illustration） | 不参与 P0 布局，不压缩主动作。 | 无图时使用状态图标 + 标题 + 主动作。 |
| UI 设计图（UI Screen） | 只作为源证据，不作为页面整屏背景。 | 缺失时页面不能进入正式输入件完成状态。 |
| 图标（Icon） | 只使用语义 token，不在页面内临时画同义图。 | 先补 `icons.js`，再进入页面 renderer。 |
